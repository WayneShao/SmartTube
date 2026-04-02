# RayNeo X3 Pro 第三方应用交互适配：完整技术指南

> 本文档总结了将 Android TV 应用 BBLL 移植到 RayNeo X3 Pro AR 眼镜过程中，在交互适配环节遇到的所有问题、尝试过的方案、已验证的结论，以及对后续实现的建议。目标读者是从零开始的开发者。

---

## 一、X3 Pro 的输入系统架构

### 1.1 硬件
X3 Pro 有左右两个镜腿，各有一个 Cypress 触控板：
- 右镜腿：`cyttsp5_mt`（`/dev/input/event2`），主交互
- 左镜腿：`cyttsp6_mt`（`/dev/input/event4`），系统媒体控制
- 触控板参数：MT 协议，`ABS_MT_POSITION_X/Y` 范围 0-255，支持多点触控

### 1.2 系统软件层（Mercury）
X3 Pro 运行的是 RayNeo 定制 Android 12（代号 Mercury），其 Launcher 进程 `com.ffalconxr.mercury.launcher` 包含以下关键服务：

- **`InputMonitorService`**：全局输入事件监控器，拦截所有触控和按键事件
- **`GlobalKeyEventHandler`**：全局按键事件处理器，决定按键是否传递给前台应用
- **`WatchGestureDetector`**：手势识别器，将镜腿触控转换为系统动作
- **`BackgroundAppManager`**：后台应用管理器，会主动 force-stop 第三方应用

### 1.3 事件流向
```
镜腿触控板 (cyttsp5_mt)
  → Linux Input 子系统 (getevent 可见)
  → Android InputDispatcher
  → Mercury InputMonitorService (拦截)
  → Mercury GlobalKeyEventHandler (处理/消费)
  → 只有 KEYCODE_BACK 偶尔传给前台应用
  → 前台应用的 dispatchTouchEvent 收不到原始触控
```

**核心问题：Mercury 系统在应用之前拦截并消费了镜腿触控事件，第三方应用无法通过标准 Android API 获取这些事件。**

---

## 二、官方文档说了什么（vs 实际情况）

### 文档描述（来自 RAYNEO_APP_PORTING_PLAYBOOK）
- 右镜腿：单击=确认，双击=返回，上下左右滑=焦点切换
- 应用应依赖"焦点移动结果"，而不是硬编码方向
- 声明 `<meta-data android:name="com.rayneo.mercury.app" android:value="true" />` 标记为眼镜应用

### 实际测试结果
- 声明 `com.rayneo.mercury.app` meta-data **只影响应用在眼镜桌面的可见性**，对事件分发无帮助
- 第三方应用**不会自动**收到 D-pad KeyEvent（上下左右确认）
- 系统将大部分镜腿操作解释为 BACK 键或系统手势，不传给应用
- 通过 `adb shell input keyevent KEYCODE_DPAD_DOWN` 可以正常控制应用焦点，证明应用本身支持 D-pad 导航，**问题纯粹在事件分发**

---

## 三、尝试过的方案及结果

### 3.1 标准 Android 方式获取触控事件

| 方式 | 代码位置 | 结果 |
|------|----------|------|
| 重写 `Activity.dispatchTouchEvent()` | MainActivity | ❌ 完全收不到镜腿事件 |
| 重写 `Activity.dispatchGenericMotionEvent()` | MainActivity | ❌ 收不到 |
| 重写 `Activity.onKeyDown/onKeyUp()` | MainActivity | ⚠️ 偶尔收到 BACK 键 |
| `WindowManager.addView()` 全屏透明覆盖层 | TempleGestureHelper | ❌ 只收到 DOWN+UP 无 MOVE |
| 添加 View 到 DecorView | TempleGestureHelper | ❌ 同上 |

**结论：标准 Android API 无法获取 X3 Pro 镜腿触控事件。**

### 3.2 网友 mod 版的 RayneoMirrorHelper（唯一成功方式）

一个网友制作的 mod 版 BBLL（`BBLL_x3_mod.apk`，包名 `com.V2.blb5`）能成功接收镜腿事件。它包含一个自写的 `com.xx.blbl.x3.RayneoMirrorHelper` 类，其中的 `MirrorController` 能收到完整的触控序列（ACTION_DOWN → ACTION_MOVE × N → ACTION_UP，source=0x5002）。

#### RayneoMirrorHelper 的工作原理

**文件结构**（位于 `smali_classes3/com/xx/blbl/x3/`）：
```
RayneoMirrorHelper.smali                    # 静态入口，管理 Controller 生命周期
RayneoMirrorHelper$MirrorController.smali    # 核心控制器，约 4000 行 smali
RayneoMirrorHelper$MirrorBitmapView.smali    # 位图镜像 View（双目用）
RayneoMirrorHelper$CursorView.smali          # 鼠标光标 View
RayneoMirrorHelper$HiddenViewState.smali     # 辅助类
RayneoMirrorHelper$MirrorController$1-10.smali  # 内部类（Runnable 等）
BridgeCommandServer*.smali                   # 蓝牙遥控桥接（可忽略）
BridgeGattServer*.smali                      # 蓝牙 GATT 服务（可忽略）
```

**初始化流程**（`MirrorController.install()`）：

1. 获取 `android.R.id.content`（DecorView 下的 FrameLayout）
2. 获取 content 的第一个子 View（应用的根布局）
3. 设置 content 的 `clipChildren=false`, `clipToPadding=false`
4. 将根布局设置为 640x480 并定位到左上角
5. 创建 `MirrorBitmapView`（640x480，leftMargin=640）用于右眼镜像
6. 创建两个 `CursorView`（左右各一个鼠标光标）
7. 创建 `statusView`（调试文本）
8. 将这些 View 添加到 content 容器中
9. 启动 Choreographer FrameCallback 进行持续渲染

**关键推测**：这个 `install()` 过程创建的视图层级结构可能触发了 Mercury 系统的某种识别机制，使其放行触控事件给该应用。具体是哪一步触发的尚未确定。

**手势识别流程**（`MirrorController.handleMotionEvent()`）：

```
收到 MotionEvent
  → 检查设备名称是否为镜腿触控板 (isTempleTouchDevice)
  → 检查 mouseModeEnabled 标志
  → 根据 action 分发：
    ACTION_DOWN: 记录起始位置和时间
    ACTION_MOVE: 计算 dx/dy，调用 moveCursorTo() 移动光标
                 如果移动量 > 8px，设置 cursorMovedSinceDown = true
    ACTION_UP:
      if cursorMovedSinceDown == false:  # 没移动 = 点击
        if 距上次 UP < 320ms:  # 双击
          → performGlobalBack()  # 调用 activity.onBackPressed()
        else:  # 单击
          → scheduleClickAtCursor()  # 延迟 220ms 后在光标位置合成触摸
      else:  # 有移动 = 滑动
        → 清零 lastTempleUpTime，什么都不做（原版只是移动了光标）
```

**钩子安装**（在 `MainActivity.smali` 中）：
```java
// onCreate 中，setContentView 之后：
RayneoMirrorHelper.install(this);

// onDestroy 中：
RayneoMirrorHelper.uninstall(this);

// 事件分发重写：
public boolean dispatchTouchEvent(MotionEvent ev) {
    if (RayneoMirrorHelper.handleMotionEvent(this, ev)) return true;
    return super.dispatchTouchEvent(ev);
}
public boolean dispatchGenericMotionEvent(MotionEvent ev) {
    if (RayneoMirrorHelper.handleGenericMotionEvent(this, ev)) return true;
    return super.dispatchGenericMotionEvent(ev);
}
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (RayneoMirrorHelper.handleKeyEvent(this, event)) return true;
    return super.onKeyDown(keyCode, event);
}
public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (RayneoMirrorHelper.handleKeyEvent(this, event)) return true;
    return super.onKeyUp(keyCode, event);
}
```

### 3.3 尝试将滑动映射为 D-pad 焦点移动

在成功接收到滑动事件后（通过保留 RayneoMirrorHelper），尝试了多种方式将滑动转换为 BBLL 可识别的焦点导航：

| 方式 | 代码 | 结果 |
|------|------|------|
| `Instrumentation().sendKeyDownUpSync(keyCode)` | 在 worker thread 中 | 日志显示 SUCCESS 但 UI 不动 |
| `Runtime.getRuntime().exec("input keyevent X")` | 在 worker thread 中 | 无反应（无 shell 权限） |
| `InputManager.injectInputEvent()` 反射 | 在 UI thread 中 | 无 INJECT_EVENTS 权限 |
| `activity.dispatchKeyEvent(new KeyEvent(...))` | 在 UI thread 中 | 被 handleKeyEvent 拦截，闪退 |
| `decorView.dispatchKeyEvent(new KeyEvent(...))` | 在 UI thread 中 | 同上 |
| `view.focusSearch(dir)` + `next.requestFocus()` | 在 UI thread 中 | `getCurrentFocus()` 返回 null |

**根本原因分析**：
- `Instrumentation` / `InputManager`：应用进程没有 `INJECT_EVENTS` 权限，该权限仅授予 shell/system 用户
- `dispatchKeyEvent`：事件会经过 Activity 的 `onKeyDown` → `RayneoMirrorHelper.handleKeyEvent`，被拦截或造成异常
- `focusSearch`：在 RayneoMirrorHelper 的鼠标模式下，Android 的焦点系统未初始化，`getCurrentFocus()` 返回 null

**而 `adb shell input keyevent` 有效**，因为它以 shell 用户身份通过系统 InputManager 注入，拥有完整权限。

---

## 四、已完成的适配工作

以下改动已验证可用：

### 4.1 双目显示
- 自定义 `MirrorLayout`（继承 FrameLayout），在 `dispatchDraw()` 中将子 View 绘制两次（canvas.translate(640,0)）
- 布局 XML 用 MirrorLayout 包裹原始 ConstraintLayout，尺寸 1280x480 / 640x480
- 播放器从 SurfaceView 改为 TextureView（使视频参与 View 绘制管线，可被 MirrorLayout 复制）

### 4.2 Toast 适配
- 系统 Toast 在双目模式下不被 MirrorLayout 复制（因为 Toast 是独立 Window）
- 将 BBLL 的统一 Toast 入口 `m3.1/b.smali` 的 `r()` 方法重定向到自定义 `InAppToast`
- InAppToast 在 MirrorLayout 内部创建 View，自动被双目复制

### 4.3 协议弹窗
- 在 `MainActivity.smali` 的 `userAgree` 检查处，自动写入 `true` 并跳过弹窗

### 4.4 Manifest
- 声明 `com.rayneo.mercury.app` meta-data（桌面可见性）
- 固定 `screenOrientation="landscape"`

---

## 五、未解决的核心问题

### 问题定义
如何在 `MirrorController.handleMotionEvent()` 的滑动分支（ACTION_UP 且 `cursorMovedSinceDown=true`，对应 smali 中 `cond_9` 标签约第 2717 行）中，将滑动方向转换为 BBLL 可识别的 D-pad 焦点导航？

### 已有条件
- 滑动方向可以从 `cursorX - touchDownCursorX` 和 `cursorY - touchDownCursorY` 计算得到
- `performGlobalBack()`（双击返回）证明 MirrorController 可以成功调用 Activity 方法
- `dispatchClickAtCursor()` 能在指定坐标合成触摸事件并被应用识别
- `adb shell input keyevent` 能正常控制焦点

### 可能的解决方向

#### 方向 1：找到正确的 KeyEvent 注入方式
- 问题在于应用内注入的 KeyEvent 要么权限不足，要么被 RayneoMirrorHelper 自己拦截
- 如果能禁用 `handleKeyEvent` 的拦截（已尝试直接 return false），并找到不被系统拒绝的注入方式，即可解决
- 可能需要研究 `InputManager.injectInputEvent` 的 mode 参数（0=ASYNC, 1=WAIT_FOR_RESULT, 2=WAIT_FOR_FINISH）

#### 方向 2：利用 dispatchClickAt 模拟 D-pad
- `dispatchClickAtCursor()` 能在指定坐标合成触摸事件
- 如果知道下一个焦点元素的坐标，可以直接在那个位置合成触摸
- 但需要知道 RecyclerView 等容器内子项的布局位置

#### 方向 3：AccessibilityService
- 注册一个 AccessibilityService 可以获得 `performGlobalAction(GLOBAL_ACTION_BACK)` 等能力
- 也可能有 `AccessibilityNodeInfo.ACTION_SCROLL_FORWARD` 等
- 需要用户在系统设置中手动开启辅助功能权限

#### 方向 4：研究 Mercury SDK
- 查找 RayNeo 是否提供了官方的开发者 SDK
- 可能有类似 `MercuryGestureListener` 的回调接口
- 或者有注册"D-pad 模式"的 API

#### 方向 5：合成滑动事件替代按键
- 不发送 D-pad KeyEvent，而是在屏幕上合成滑动手势（MotionEvent 序列）
- RecyclerView 等可滚动视图对滑动手势有天然响应
- 但焦点高亮（当前选中项的视觉反馈）不会跟随滑动移动

---

## 六、触控板原始事件格式

通过 `adb shell getevent -lt` 抓取的右镜腿触控板事件示例：

```
# 滑动（从左到右）
/dev/input/event2: EV_ABS ABS_MT_TRACKING_ID 00000283
/dev/input/event2: EV_ABS ABS_MT_POSITION_X  00000016    # X=22
/dev/input/event2: EV_ABS ABS_MT_POSITION_Y  00000051    # Y=81
/dev/input/event2: EV_ABS ABS_MT_PRESSURE     00000026
/dev/input/event2: EV_SYN SYN_REPORT
/dev/input/event2: EV_ABS ABS_MT_POSITION_X  0000005e    # X=94 (移动中)
/dev/input/event2: EV_ABS ABS_MT_POSITION_Y  00000059
/dev/input/event2: EV_SYN SYN_REPORT
/dev/input/event2: EV_ABS ABS_MT_POSITION_X  000000b0    # X=176
/dev/input/event2: EV_SYN SYN_REPORT
/dev/input/event2: EV_ABS ABS_MT_TRACKING_ID  ffffffff   # 手指抬起
/dev/input/event2: EV_SYN SYN_REPORT

# 单击（短暂触摸，位移很小）
/dev/input/event2: EV_ABS ABS_MT_TRACKING_ID 00000286
/dev/input/event2: EV_ABS ABS_MT_POSITION_X  00000039
/dev/input/event2: EV_ABS ABS_MT_POSITION_Y  0000007b
/dev/input/event2: EV_ABS ABS_MT_PRESSURE     00000028
/dev/input/event2: EV_SYN SYN_REPORT
/dev/input/event2: EV_ABS ABS_MT_TRACKING_ID  ffffffff   # 约 60ms 后抬起
/dev/input/event2: EV_SYN SYN_REPORT

# 双击 = 两个间隔约 100ms 的单击
```

事件到达应用时（经过 Mercury 系统转换）：
- `source = 0x2002` 或 `0x5002`
- `toolType = TOOL_TYPE_MOUSE`
- 坐标被映射到屏幕坐标系（不是触控板的 0-255）

---

## 七、文件清单

```
apktool_out/
├── AndroidManifest.xml                          # 加了 rayneo meta-data
├── res/layout/activity_main.xml                 # MirrorLayout 包裹
├── smali/com/xx/blbl/ui/MainActivity.smali      # RayneoMirrorHelper 钩子 + 协议跳过
├── smali/com/xx/blbl/ui/view/exoplayer/
│   └── MyPlayerView.smali                       # SurfaceView→TextureView
├── smali/m3.1/b.smali                           # Toast→InAppToast 重定向
├── smali_classes2/com/xx/blbl/ui/view/
│   ├── MirrorLayout.smali                       # 双目渲染
│   ├── InAppToast*.smali                        # 应用内 Toast
│   └── TempleGestureHelper*.smali               # 手势识别（当前未使用）
├── smali_classes3/com/xx/blbl/x3/
│   ├── RayneoMirrorHelper.smali                 # 事件捕获入口
│   ├── RayneoMirrorHelper$MirrorController.smali # 核心控制器（需改造）
│   ├── RayneoMirrorHelper$MirrorBitmapView.smali # 位图镜像
│   ├── RayneoMirrorHelper$CursorView.smali      # 鼠标光标（需去掉或隐藏）
│   └── ...其他内部类和蓝牙桥接
└── smali/com/xx/blbl/network/                   # 播放修复（已完成）
```

---

## 八、构建与调试

```bash
# 工具
# apktool 2.10.0: /c/tools/apktool.jar
# baksmali 2.5.2: /c/tools/baksmali.jar
# jadx 1.5.1: /c/tools/jadx/
# Android SDK: $LOCALAPPDATA/Android/Sdk/

# 构建（必须 -f 强制重编译，否则 smali_classes3 的改动不生效）
java -jar /c/tools/apktool.jar b apktool_out -o BBLL_x3pro.apk -f

# 签名
$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/apksigner.bat sign \
  --ks debug.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey \
  BBLL_x3pro.apk

# 安装到眼镜（ADB 不稳定，经常断连）
adb install -r BBLL_x3pro.apk
adb shell svc wifi enable
adb shell am start -n com.xx.blbl/.ui.MainActivity

# 测试 D-pad 导航是否工作（绕过镜腿，直接验证应用）
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER
adb shell input keyevent KEYCODE_BACK

# 日志抓取（ADB 不稳定时存到眼镜本地）
adb shell 'logcat -d > /sdcard/log.txt'
adb shell 'cat /sdcard/log.txt | grep XXX'
```
