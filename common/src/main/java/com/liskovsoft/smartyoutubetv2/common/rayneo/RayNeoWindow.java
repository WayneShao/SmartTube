package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
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
    private final Window window;
    private final TempleInput input;
    private StereoLayout root;
    private CursorTarget target;

    private RayNeoWindow(Window window) {
        super(window.getCallback());
        this.window = window;
        input = new TempleInput(window.getContext(), this::action, this::move, () -> {
            clearTarget();
            if (active()) target = new CursorTarget(root);
        });
    }

    public static void install(Activity activity) {
        if (RayNeo.isEnabled(activity)) install(activity.getWindow(), false);
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
        stereo.showCursor(window.getDecorView().hasWindowFocus());
        stereo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                callback.input.cancel();
                callback.clearTarget();
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
        if (!focused) { input.cancel(); clearTarget(); }
        if (root != null) root.showCursor(focused);
        super.onWindowFocusChanged(focused);
    }

    private void move(float dx, float dy) {
        if (!active()) return;
        CursorScroll.move(root, dx, dy);
        root.showCursor(true);
        MotionEvent hover = pointer(MotionEvent.ACTION_HOVER_MOVE, SystemClock.uptimeMillis(), root.cursor().x(), root.cursor().y());
        hover.setSource(InputDevice.SOURCE_MOUSE);
        try { super.dispatchGenericMotionEvent(hover); }
        finally { hover.recycle(); }
    }

    private MotionEvent pointer(int action, long downTime, float x, float y) {
        int[] rootLocation = new int[2];
        int[] decorLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        window.getDecorView().getLocationInWindow(decorLocation);
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x + rootLocation[0] - decorLocation[0],
                y + rootLocation[1] - decorLocation[1], 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        return event;
    }

    private void action(int keyCode) {
        if (!active()) return;
        if (keyCode != KeyEvent.KEYCODE_BACK && (target == null || !target.valid(root))) {
            clearTarget();
            return;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            StereoLayout owner = root;
            long time = SystemClock.uptimeMillis();
            MotionEvent down = pointer(MotionEvent.ACTION_DOWN, time, target.x, target.y);
            MotionEvent up = pointer(MotionEvent.ACTION_UP, time, target.x, target.y);
            clearTarget();
            try {
                super.dispatchTouchEvent(down);
                if (root == owner && active()) super.dispatchTouchEvent(up);
                else {
                    up.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(up);
                }
            } finally { down.recycle(); up.recycle(); }
        } else if (keyCode == KeyEvent.KEYCODE_MENU && target.longClick()) {
            clearTarget();
        } else {
            if (keyCode == KeyEvent.KEYCODE_MENU && !target.focus()) { clearTarget(); return; }
            clearTarget();
            long time = SystemClock.uptimeMillis();
            // Delegate to this Window's original Activity/Dialog, including its translators.
            super.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0));
            super.dispatchKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0));
        }
    }

    private void clearTarget() {
        if (target != null) { target.close(); target = null; }
    }
}
