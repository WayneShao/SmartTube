package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Dialog;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.view.WindowCallbackWrapper;
import io.github.wayneshao.rayneo.ime.EmbeddedInput;
import io.github.wayneshao.rayneo.ime.KeyboardSurface;

/** Input-only adapter: the independent keyboard already renders both eyes. */
final class KeyboardWindow extends WindowCallbackWrapper {
    private final Dialog dialog;
    private final KeyboardSurface surface;
    private final TempleInput input;
    private final FocusNavigator navigator;
    private FocusTarget target;

    private KeyboardWindow(Dialog dialog, KeyboardSurface surface) {
        super(dialog.getWindow().getCallback());
        this.dialog = dialog; this.surface = surface;
        navigator = new FocusNavigator(surface, this::dispatchOriginal);
        input = new TempleInput(dialog.getContext(), this::action, () -> {
            clearTarget();
            if (active() && navigator.prepare() != null) target = new FocusTarget(surface);
        });
        surface.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { }
            public void onViewDetachedFromWindow(View view) { cancel(); navigator.close(); }
        });
    }
    static void install(Dialog dialog, KeyboardSurface surface) {
        if (!(dialog.getWindow().getCallback() instanceof KeyboardWindow))
            dialog.getWindow().setCallback(new KeyboardWindow(dialog, surface));
    }
    private boolean active() { return dialog.isShowing() && surface.isAttachedToWindow() && surface.hasWindowFocus(); }
    private void clearTarget() { if (target != null) { target.close(); target = null; } }
    private void cancel() { input.cancel(); navigator.cancel(); clearTarget(); }
    private boolean motion(MotionEvent event) {
        if (!active()) return false;
        android.view.InputDevice device = event.getDevice();
        if (device != null && "cyttsp6_mt".equals(device.getName())) { cancel(); return true; }
        if (EmbeddedInput.motion(dialog, event)) { cancel(); return true; }
        return input.handle(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return motion(event) || super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return motion(event) || super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (EmbeddedInput.key(dialog, event)) { cancel(); return true; }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) cancel();
        super.onWindowFocusChanged(focused);
    }
    private boolean dispatchOriginal(KeyEvent event) { return super.dispatchKeyEvent(event); }
    private void action(int key) {
        if (!active()) return;
        // MENU has no meaning in this window. The module owns all multi-finger actions.
        if (key == KeyEvent.KEYCODE_MENU) { clearTarget(); return; }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER && (target == null || !target.valid(surface))) { clearTarget(); return; }
        clearTarget(); navigator.send(key);
    }
}
