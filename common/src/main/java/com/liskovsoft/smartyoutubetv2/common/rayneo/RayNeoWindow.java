package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.appcompat.view.WindowCallbackWrapper;

/** Public Window APIs only: content, input, and pending gestures share window ownership. */
public final class RayNeoWindow extends WindowCallbackWrapper {
    private static java.lang.ref.WeakReference<RayNeoWindow> foreground = new java.lang.ref.WeakReference<>(null);
    private final Window window;
    private final TempleInput input;
    private StereoLayout root;
    private FocusTarget target;
    private FocusNavigator navigator;

    private RayNeoWindow(Window window) {
        super(window.getCallback());
        this.window = window;
        input = new TempleInput(window.getContext(), this::action, () -> {
            clearTarget();
            if (active() && navigator.prepare() != null) target = new FocusTarget(root);
        });
    }

    public static void install(Activity activity) {
        if (RayNeo.isEnabled(activity)) install(activity.getWindow(), false);
    }

    /** Returns false when the caller should retain its normal system message path. */
    public static boolean showMessage(Context context, int messageResId) {
        return showMessage(context, context.getText(messageResId), false);
    }

    public static boolean showMessage(Context context, CharSequence text, boolean isLong) {
        if (!RayNeo.isEnabled(context)) return false;
        RayNeoWindow activeWindow = foreground.get();
        if (activeWindow != null && activeWindow.active()) {
            activeWindow.root.showMessage(text, isLong ? 3500 : 2000);
            return true;
        }
        Context owner = context;
        while (owner instanceof ContextWrapper && !(owner instanceof Activity)) {
            Context base = ((ContextWrapper) owner).getBaseContext();
            if (base == owner) break;
            owner = base;
        }
        if (!(owner instanceof Activity)) return false;
        Window.Callback callback = ((Activity) owner).getWindow().getCallback();
        if (!(callback instanceof RayNeoWindow)) return false;
        RayNeoWindow stereo = (RayNeoWindow) callback;
        if (!stereo.active()) return false;
        stereo.root.showMessage(text, isLong ? 3500 : 2000);
        return true;
    }

    public static void dismissMessages() {
        RayNeoWindow current = foreground.get();
        if (current != null && current.root != null) current.root.dismissMessage();
    }

    public static ViewGroup overlayRoot(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content.getChildCount() > 0 && content.getChildAt(0) instanceof StereoLayout) {
            return (ViewGroup) content.getChildAt(0);
        }
        return (ViewGroup) content.getRootView();
    }

    public static void install(Dialog dialog) {
        install(dialog, true);
    }

    public static void install(Dialog dialog, boolean cancelOnOutside) {
        if (RayNeo.isEnabled(dialog.getContext()) && dialog.getWindow() != null) {
            install(dialog.getWindow(), true);
            ViewGroup content = dialog.getWindow().findViewById(android.R.id.content);
            StereoLayout stereo = (StereoLayout) content.getChildAt(0);
            if (cancelOnOutside) stereo.setOnTouchListener((view, event) -> {
                View panel = stereo.getChildAt(0);
                boolean outside = event.getX() < panel.getLeft() || event.getX() >= panel.getRight()
                        || event.getY() < panel.getTop() || event.getY() >= panel.getBottom();
                if (outside && event.getActionMasked() == MotionEvent.ACTION_UP) dialog.cancel();
                return outside;
            });
        }
    }

    private static void install(Window window, boolean dialog) {
        ViewGroup content = window.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        if (content.getChildAt(0) instanceof StereoLayout) return;
        RayNeoWindow callback = window.getCallback() instanceof RayNeoWindow
                ? (RayNeoWindow) window.getCallback() : new RayNeoWindow(window);
        callback.input.cancel();
        callback.clearTarget();
        if (callback.navigator != null) callback.navigator.close();
        StereoLayout stereo = new StereoLayout(window.getContext());
        FrameLayout logical = new FrameLayout(window.getContext());
        while (content.getChildCount() != 0) {
            View child = content.getChildAt(0);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            content.removeViewAt(0);
            logical.addView(child, params);
        }
        FrameLayout.LayoutParams logicalParams = new FrameLayout.LayoutParams(-1, -1);
        if (dialog) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            window.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            logicalParams.width = (int) (metrics.widthPixels / 2f * .94f);
            logicalParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            logicalParams.gravity = Gravity.CENTER;
            logical.setBackground(window.getDecorView().getBackground());
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.FILL);
        }
        stereo.addView(logical, logicalParams);
        callback.root = stereo;
        window.setCallback(callback);
        content.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        callback.navigator = new FocusNavigator(stereo, callback::dispatchNavigationKey);
        if (callback.active()) foreground = new java.lang.ref.WeakReference<>(callback);
        stereo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                callback.navigator.close();
                callback.navigator = new FocusNavigator(stereo, callback::dispatchNavigationKey);
            }
            @Override public void onViewDetachedFromWindow(View view) {
                callback.input.cancel();
                callback.clearTarget();
                callback.navigator.close();
            }
        });
    }

    private boolean active() {
        return root != null && root.isAttachedToWindow() && root.hasWindowFocus();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return (active() && input.handle(event)) || super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return (active() && input.handle(event)) || super.dispatchGenericMotionEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) {
            input.cancel(); clearTarget();
            if (navigator != null) navigator.cancel();
            if (root != null) root.dismissMessage();
        }
        else if (navigator != null) {
            foreground = new java.lang.ref.WeakReference<>(this);
            navigator.prepare();
        }
        super.onWindowFocusChanged(focused);
    }

    private boolean dispatchNavigationKey(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    private void action(int keyCode) {
        if (!active()) return;
        boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_MENU;
        if (confirm && (target == null || !target.valid(root))) { clearTarget(); return; }
        clearTarget();
        navigator.send(keyCode);
    }

    private void clearTarget() {
        if (target != null) { target.close(); target = null; }
    }
}
