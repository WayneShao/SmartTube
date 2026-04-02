# RayNeo X3 Pro — Accessibility Service Navigation Approach
## Status: Partially Validated (Draft)

> 与 `RAYNEO_APP_PORTING_PLAYBOOK_2026-03-18.md` 和 `X3PRO_INPUT_CHALLENGES.md` 并行。
> 本文记录"无障碍服务全局导航"方案的设计思路、已验证项、未验证项，以及对 Smali
> 场景的适用性分析。**尚未完整测试，请结合实测日志判断结论可靠性。**

---

## 核心适配要点（Android TV APP 移植到 AR 眼镜的本质问题）

将任意 Android TV APP 移植到 RayNeo X3 Pro（或类似双目 AR 眼镜）时，
本质上只有**两个核心问题**，其余所有细节都是这两点的衍生：

### 要点一：双目视图

**目标：让 APP 以为自己运行在一块 640×480 的屏幕上，同时高性能地复制出一个完全相同的画面填满另一只眼睛。**

RayNeo X3 Pro 的物理显示是 1280×480（左右各 640×480），Android 系统报告的也是
这个分辨率。普通 TV APP 按 1280×480 布局会导致内容被拉伸或错位，左右眼各看到
画面的一半。

解决方案分两层：

1. **约束布局尺寸**：在 `android.R.id.content` 与 APP 的根 View 之间插入一个
   `RayNeoStereoLayout`，把根 View 的 `MeasureSpec` 强制固定为 640×480。
   APP 的所有 View 测量、布局、触摸坐标从此只看到 640×480，不感知物理分辨率。

2. **高性能镜像**：`RayNeoStereoLayout.dispatchDraw()` 先正常画左眼（x=0），
   再 `canvas.translate(640, 0)` 重放同一份 display list 画右眼。
   HWUI 的 display list 机制保证 GPU 侧只录制一次，右眼是纯 GPU 复合，
   无额外 CPU 开销——这是"高性能"的来源。

```
物理屏 1280×480
┌──────────────────────────────────────┐
│  左眼 (0,0)~(640,480)                │  右眼 (640,0)~(1280,480)             │
│  APP 根 View，受约束为 640×480        │  dispatchDraw 平移后 GPU 复合         │
└──────────────────────────────────────┘
         ↑ APP 完全不知道右眼的存在，只处理 640×480 坐标系
```

**全局生效的关键**：包裹逻辑必须同时覆盖：
- 调用 `setContentView()` 的 Activity（主界面、播放界面等）
- 只用 Fragment 事务、从不调 `setContentView()` 的 Activity（登录、账号选择等）

做法：在基类 Activity 的 `setContentView()` **和** `onStart()` 里都调用
`ensureStereoWrapper()`，后者在 `super.onStart()` 之后执行
（此时 `FragmentActivity` 已经 `execPendingActions()`，Fragment View 已挂载）。

---

### 要点二：右镜腿触摸板 → 遥控器按键

**目标：把右镜腿触摸板的滑动/点击事件直接映射成 Android TV 遥控器的 D-pad 按键，让 APP 完全不需要适配触摸输入。**

Mercury（RayNeo 的系统层）将触摸板事件转发给 APP，但以
`SOURCE_TOUCHSCREEN | SOURCE_MOUSE`（0x5002）的形式到达——这产生了核心难题：

| 问题 | 根因 | 影响 |
|---|---|---|
| 手势识别 | 原始 `MotionEvent` 而非 `KeyEvent` | APP 收到触摸事件，不知道该怎么处理 |
| touch mode | `SOURCE_TOUCHSCREEN` 使系统进入 touch mode | `View.requestFocus()` 对大量 TV 控件静默失败 |
| 输入坐标 | 触摸板坐标 ≠ 屏幕坐标 | 无法用坐标做命中测试 |

**最终解决路径**（两步）：

**第一步：手势识别 → 意图**
在基类 Activity 的 `dispatchTouchEvent()` 里拦截所有触摸板事件，
识别出方向（上下左右）、点击、双击，转换为"导航意图"。
不直接注入 `KeyEvent`，因为 touch mode 会阻断后续的 `requestFocus()`。

**第二步：意图 → 焦点移动（绕过 touch mode）**
通过 `AccessibilityService` 执行导航：
- 方向 → `AccessibilityNodeInfo.focusSearch()` + `performAction(ACTION_FOCUS)`
- 点击 → `performAction(ACTION_CLICK)`
- 返回 → `performGlobalAction(GLOBAL_ACTION_BACK)`

A11y 框架在 `ViewRootImpl` 层执行 action 时会临时清除 touch mode，
`requestFocus()` 因此对**所有** `focusable=true` 的 View 均有效，
无需逐界面逐控件打补丁。

---

**两个要点的关系**：相互独立，可分开移植。双目视图是纯渲染问题；
触摸板导航是纯输入问题。两者都不需要修改 APP 的业务逻辑。

---

## 背景：为什么需要这个方案

原方案（`RayNeoGestureHandler` + `LeanbackActivity.dispatchTouchEvent`）的根本缺陷：

temple touchpad 的 `MotionEvent` 携带 `SOURCE_TOUCHSCREEN`（0x5002），
使 Android 进入 **touch mode**。Touch mode 下 `View.requestFocus()` 对
`focusable=true` 但 `focusableInTouchMode=false` 的 View 静默失败。
这导致每发现一个"光标进不去"的界面，就要逐 View 加 `setFocusableInTouchMode(true)` 补丁——
无法一劳永逸。

---

## 核心思路

用 `AccessibilityService` 接管导航：

```
temple touchpad MotionEvent
        ↓  (RayNeoGestureHandler 手势识别，逻辑不变)
    fireKey(keyCode)
        ↓
  RayNeoA11yService.navigate() / click() / back()
        ↓
  AccessibilityNodeInfo.performAction(ACTION_FOCUS / ACTION_CLICK)
  performGlobalAction(GLOBAL_ACTION_BACK)
```

Accessibility 框架在执行 `performAction` 时，`ViewRootImpl` 会在系统层
**临时清除 touch mode** 再调用 `requestFocus()`，完成后恢复。
因此任何 `focusable=true` 的 View 都可以被聚焦，无需逐 View 打补丁。

---

## 关键实现细节

### 1. 服务自动启用（绕过 Mercury 的自动清除）

Mercury 每次启动都会清除无障碍服务列表。解决方案：

**一次性授权（只做一次，重装 APK 也不需要重做）：**
```bash
adb shell pm grant <packageName> android.permission.WRITE_SECURE_SETTINGS
```

**每次 `Activity.onCreate()` 自动重注册：**
```java
String svcFqn = getPackageName() + "/...RayNeoA11yService";
String current = Settings.Secure.getString(cr, ENABLED_ACCESSIBILITY_SERVICES);
if (current == null || !current.contains(svcFqn)) {
    Settings.Secure.putString(cr, ENABLED_ACCESSIBILITY_SERVICES,
            current.isEmpty() ? svcFqn : current + ":" + svcFqn);
    Settings.Secure.putInt(cr, ACCESSIBILITY_ENABLED, 1);
}
```

`WRITE_SECURE_SETTINGS` 不是运行时权限，ADB grant 一次永久有效（卸载重装需重做）。

### 2. 导航实现

```java
// 方向导航 — 等效 DPAD 按键，绕过 touch mode
public boolean navigate(int viewFocusDirection) {
    AccessibilityNodeInfo root    = getRootInActiveWindow();
    AccessibilityNodeInfo focused = root.findFocus(FOCUS_INPUT);
    AccessibilityNodeInfo next    = focused.focusSearch(viewFocusDirection);
    return next.performAction(ACTION_FOCUS);   // 系统层清除 touch mode 后执行
}

// 确认/点击 — 等效 DPAD_CENTER
public boolean click() {
    return focused.performAction(ACTION_CLICK);
}

// 返回 — 等效 BACK 键，100% 可靠
public boolean back() {
    return performGlobalAction(GLOBAL_ACTION_BACK);
}
```

`focusSearch(direction)` 走的是与 `View.focusSearch()` 同一条调用链，
包括 Leanback 的自定义 `OnFocusSearchListener`——侧边栏的方向键逻辑也适用。

### 3. 兜底层级（三层）

| 优先级 | 策略 | 适用场景 |
|---|---|---|
| 1 | A11y `performAction` | 所有方向键、CENTER、BACK |
| 2 | `tryOpenBrowseSidebar` | A11y 找不到下一节点（侧边栏 INVISIBLE 时） |
| 3 | `dispatchKeyEvent` 注入 | MENU 键；A11y 服务未连接时全部兜底 |

### 4. 双目显示全局覆盖

`ensureStereoWrapper()` 从两处调用：
- `setContentView(int)` ← 覆盖正常调用 setContentView 的 Activity
- `onStart()` ← 覆盖通过 Fragment 事务添加 UI、从不调 setContentView 的 Activity
  （`SignInActivity`、账号选择等）

`FragmentActivity.onStart()` 内部调用 `execPendingActions()`，
所以 `super.onStart()` 返回时 Fragment 的 View 已挂载，包裹时机正确。

---

## 已验证项 ✅

- A11y 服务成功连接（`onServiceConnected` 触发）
- `WRITE_SECURE_SETTINGS` ADB 授权 + 程序内自注册流程可用
- 所有方向手势均走 A11y 路径，`performAction(FOCUS) ok=true`
- 服务未连接时自动回落 Strategy C（注入 KeyEvent），不崩溃
- `ensureStereoWrapper` 代码逻辑正确（`onStart` 时 Fragment View 已存在）

## 未验证项 ⚠️

- 登录界面（`SignInActivity`）双目镜像是否生效
- 账号选择界面双目镜像是否生效
- 登录界面 A11y 导航（光标能否移动到登录按钮）
- A11y `navigate()` 在侧边栏 INVISIBLE 时是否返回 false（触发 Strategy B）
- 视频播放界面的 A11y 导航行为
- Mercury 重启后自注册是否在每次都成功触发

---

## 对 Smali 场景的适用性

> **结论：这个方案比原方案更适合 Smali，不需要找所有输入点。**

### 为什么原方案不适合 Smali

原方案（逐 View 打 `setFocusableInTouchMode` + `dispatchTouchEvent` 拦截）
需要在 Smali 里找到：
- 所有 `View.setFocusable` / `requestFocus` 的调用点
- 每个有问题的界面的 ViewHolder / Presenter 类
- 手势识别注入到 Activity 的 `dispatchTouchEvent`

这些分散在数百个 Smali 文件里，极难定位。

### 为什么 A11y 方案适合 Smali

只需修改/注入**三个地方**：

#### 1. 新增 `RayNeoA11yService.smali`（全新文件，不改原有代码）
完整的新类，Smali 注入即可，不需要理解原有逻辑。

#### 2. 新增 `RayNeoGestureHandler.smali`（全新文件）
手势识别逻辑独立，通过静态单例调用 A11y 服务。

#### 3. Hook 基类 Activity 的两个方法（只改两处）

找到 APK 的基类 Activity（通常是 `LeanbackActivity` 或类似名称），
在 Smali 里注入：

**`dispatchTouchEvent(Landroid/view/MotionEvent;)Z`** — 在方法头部插入：
```smali
invoke-static {p1}, Lcom/your/pkg/RayNeoGestureHandler;->handleMotionEvent(Landroid/view/MotionEvent;)Z
move-result v0
if-eqz v0, :not_consumed
const/4 v0, 0x1
return v0
:not_consumed
```

**`onCreate(Landroid/os/Bundle;)V`** — 在 `super.onCreate` 之后插入：
```smali
invoke-static {}, Lcom/your/pkg/RayNeoConfig;->isEnabled()Z
move-result v0
if-eqz v0, :skip_rayneo
invoke-virtual {p0}, Lcom/your/pkg/LeanbackActivity;->ensureA11yServiceEnabled()V
:skip_rayneo
```

**`setContentView(I)V` 和 `onStart()V`** — 各插入一行
`ensureStereoWrapper()` 调用（双目镜像）。

#### 与直接找输入点相比

| | 直接找输入点 | A11y 服务方案 |
|---|---|---|
| 需要改动的 Smali 文件数 | 数十个（每个界面的 Presenter/ViewHolder） | 1 个基类 Activity + 2 个新文件 |
| 需要理解原有代码逻辑 | 是（每个界面都要分析） | 否（只需找基类 Activity） |
| 漏改某个界面的风险 | 高 | 无（全局覆盖） |
| 维护成本 | 每次 APK 更新都要重新定位 | 只需重新 hook 基类 Activity |

### 定位基类 Activity 的方法

```bash
# 反编译后搜索 dispatchTouchEvent 的定义
grep -r "dispatchTouchEvent" smali/ --include="*.smali" -l

# 或搜索 AccessibilityService / leanback 相关
grep -r "leanback" smali/ --include="*.smali" -l | head -20
```

通常基类 Activity 同时包含 `dispatchKeyEvent`、`dispatchTouchEvent`、
`onResume` 等方法，容易定位。

---

## 已知限制

1. **MENU 键**：A11y `performGlobalAction` 无 MENU 等价项，仍需 KeyEvent 注入兜底。
   对 Smali 场景：如果目标 APK 本身没有 MENU 逻辑，可忽略。

2. **`GLOBAL_ACTION_DPAD_*`**（API 33）：Mercury/Android 12 是 API 31/32，
   不可用。只能用 `focusSearch` + `ACTION_FOCUS` 替代。

3. **`focusSearch` 在 INVISIBLE 容器内失效**：Leanback 侧边栏隐藏时
   `focusSearch(FOCUS_LEFT)` 可能返回 null。需 Strategy B（`startHeadersTransition`）兜底。
   Smali 场景若目标不是 Leanback 应用，此限制不存在。

4. **ADB 授权依赖**：`WRITE_SECURE_SETTINGS` 需要 ADB 手动执行一次。
   对用户发布场景需要说明。Smali patch 发布时可附带授权脚本。
