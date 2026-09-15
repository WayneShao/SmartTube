package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Cursor mode means focus navigation: one direction per completed touchpad swipe. */
public final class TempleInput {
    public interface ActionListener { void onAction(int keyCode); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActionListener actions;
    private final Runnable captureTarget;
    private final float slop;
    private boolean down, moved, pendingTap, secondTap, hovering, suppressed;
    private int deviceId = Integer.MIN_VALUE;
    private int lastAction = -1;
    private long lastEventTime = -1;
    private float startX, startY, lastX, lastY;
    private long downTime, lastUpTime;
    private final Runnable confirm;

    public TempleInput(Context context, ActionListener actions, Runnable captureTarget) {
        this.actions = actions;
        this.captureTarget = captureTarget;
        slop = Math.max(12, ViewConfiguration.get(context).getScaledTouchSlop());
        confirm = () -> { pendingTap = false; actions.onAction(KeyEvent.KEYCODE_DPAD_CENTER); };
    }

    public boolean handle(MotionEvent event) {
        InputDevice device = event.getDevice();
        String name = device == null ? "" : device.getName();
        if (!"cyttsp5_mt".equals(name) && !"cyttsp6_mt".equals(name)) return false;
        if (deviceId != event.getDeviceId()) { cancel(); deviceId = event.getDeviceId(); }
        if (lastAction == event.getActionMasked() && lastEventTime == event.getEventTime()) return true;
        lastAction = event.getActionMasked(); lastEventTime = event.getEventTime();
        if (android.util.Log.isLoggable("SmartTubeRayNeoInput", android.util.Log.DEBUG)) {
            android.util.Log.d("SmartTubeRayNeoInput", "device=" + name + " action=" + lastAction
                    + " x=" + event.getX() + " y=" + event.getY());
        }
        handleTouchpad(event);
        return true;
    }

    void handleTouchpad(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || event.getPointerCount() != 1 || action == MotionEvent.ACTION_POINTER_UP) {
            int currentDevice = deviceId;
            cancel(); deviceId = currentDevice; suppressed = true; return;
        }
        if (action == MotionEvent.ACTION_DOWN) suppressed = false;
        if (suppressed) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_HOVER_EXIT) suppressed = false;
            return;
        }
        if (!down && (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE)) {
            if (!hovering) { hovering = true; startX = event.getX(); startY = event.getY(); }
            lastX = event.getX(); lastY = event.getY();
            return;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            if (hovering && !down) { hovering = false; emitDirection(lastX - startX, lastY - startY); }
            return;
        }
        if (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_BUTTON_PRESS && !down)) {
            if (down) return;
            secondTap = pendingTap && event.getEventTime() - lastUpTime <= ViewConfiguration.getDoubleTapTimeout();
            handler.removeCallbacks(confirm);
            if (pendingTap && !secondTap) actions.onAction(KeyEvent.KEYCODE_DPAD_CENTER);
            pendingTap = false; down = true; moved = false; hovering = false;
            startX = lastX = event.getX(); startY = lastY = event.getY();
            downTime = event.getEventTime();
            captureTarget.run();
        } else if (down && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE)) {
            lastX = event.getX(); lastY = event.getY();
            moved |= Math.max(Math.abs(lastX - startX), Math.abs(lastY - startY)) > slop;
        } else if (down && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE)) {
            down = false; lastX = event.getX(); lastY = event.getY();
            moved |= Math.max(Math.abs(lastX - startX), Math.abs(lastY - startY)) > slop;
            if (moved) emitDirection(lastX - startX, lastY - startY);
            else if (event.getEventTime() - downTime >= ViewConfiguration.getLongPressTimeout()) actions.onAction(KeyEvent.KEYCODE_MENU);
            else if (secondTap) actions.onAction(KeyEvent.KEYCODE_BACK);
            else {
                pendingTap = true; lastUpTime = event.getEventTime();
                handler.postDelayed(confirm, ViewConfiguration.getDoubleTapTimeout());
            }
            secondTap = false;
        }
    }

    private void emitDirection(float dx, float dy) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) <= slop) return;
        handler.removeCallbacks(confirm); pendingTap = false;
        actions.onAction(Math.abs(dx) >= Math.abs(dy)
                ? (dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)
                : (dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP));
    }

    public void cancel() {
        handler.removeCallbacks(confirm);
        down = moved = pendingTap = secondTap = hovering = suppressed = false;
        deviceId = Integer.MIN_VALUE; lastAction = -1; lastEventTime = -1;
    }
}
