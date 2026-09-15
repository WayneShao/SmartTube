package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Touchpad-relative cursor motion and deferred click, owned by a single window. */
public final class TempleInput {
    public interface ActionListener { void onAction(int keyCode); }
    public interface MoveListener { void onMove(float dx, float dy); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActionListener actions;
    private final MoveListener moves;
    private final Runnable captureTarget;
    private final float slop;
    private boolean down, moved, pendingTap, secondTap, hovering;
    private int deviceId = Integer.MIN_VALUE;
    private float startX, startY, lastX, lastY;
    private long downTime, lastUpTime;
    private final Runnable confirm;

    public TempleInput(Context context, ActionListener actions, MoveListener moves) {
        this(context, actions, moves, () -> {});
    }

    public TempleInput(Context context, ActionListener actions, MoveListener moves, Runnable captureTarget) {
        this.actions = actions;
        this.moves = moves;
        this.captureTarget = captureTarget;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        confirm = () -> {
            pendingTap = false;
            actions.onAction(KeyEvent.KEYCODE_DPAD_CENTER);
        };
    }

    public boolean handle(MotionEvent event) {
        InputDevice device = event.getDevice();
        String name = device == null ? "" : device.getName();
        if (!"cyttsp5_mt".equals(name) && !"cyttsp6_mt".equals(name)) return false;
        if (deviceId != event.getDeviceId()) {
            cancel();
            deviceId = event.getDeviceId();
        }
        handleTouchpad(event);
        return true;
    }

    void handleTouchpad(MotionEvent event) {
        int action = event.getActionMasked();
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) {
            cancel();
            return;
        }
        if (!down && action == MotionEvent.ACTION_HOVER_MOVE) {
            if (hovering) moves.onMove(event.getX() - lastX, event.getY() - lastY);
            hovering = true;
            lastX = event.getX();
            lastY = event.getY();
            return;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            hovering = false;
            return;
        }
        if (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_BUTTON_PRESS && !down)) {
            if (down) return; // Touch and generic paths may report the same press.
            secondTap = pendingTap && event.getEventTime() - lastUpTime <= ViewConfiguration.getDoubleTapTimeout();
            handler.removeCallbacks(confirm);
            if (pendingTap && !secondTap) actions.onAction(KeyEvent.KEYCODE_DPAD_CENTER);
            pendingTap = false;
            down = true;
            moved = false;
            startX = lastX = event.getX();
            startY = lastY = event.getY();
            downTime = event.getEventTime();
            captureTarget.run();
        } else if (down && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE)) {
            float x = event.getX(), y = event.getY();
            if (moved || Math.hypot(x - startX, y - startY) > slop) {
                moved = true;
                moves.onMove(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
            }
        } else if (down && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE)) {
            down = false;
            if (moved) {
                if (event.getX() != lastX || event.getY() != lastY) {
                    moves.onMove(event.getX() - lastX, event.getY() - lastY);
                }
            } else if (event.getEventTime() - downTime >= ViewConfiguration.getLongPressTimeout()) {
                actions.onAction(KeyEvent.KEYCODE_MENU);
            } else if (secondTap) {
                actions.onAction(KeyEvent.KEYCODE_BACK);
            } else {
                pendingTap = true;
                lastUpTime = event.getEventTime();
                handler.postDelayed(confirm, ViewConfiguration.getDoubleTapTimeout());
            }
            secondTap = false;
        }
    }

    public void cancel() {
        handler.removeCallbacks(confirm);
        down = moved = pendingTap = secondTap = hovering = false;
        deviceId = Integer.MIN_VALUE;
    }
}
