package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import java.lang.ref.WeakReference;

/** Restores per-window focus and supplies the normal ViewRoot DPAD fallback for local dispatch. */
final class FocusNavigator implements ViewTreeObserver.OnGlobalFocusChangeListener, ViewTreeObserver.OnPreDrawListener {
    interface Dispatch { boolean key(KeyEvent event); }
    private final StereoLayout root;
    private final Dispatch dispatch;
    private WeakReference<View> previous = new WeakReference<>(null);
    private int previousRow = -1;
    private Object previousAdapter;
    private long previousItemId;
    private Runnable pending;
    private FocusTarget pendingTarget;

    FocusNavigator(StereoLayout root, Dispatch dispatch) {
        this.root = root;
        this.dispatch = dispatch;
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(this);
        root.getViewTreeObserver().addOnPreDrawListener(this);
        remember(root.findFocus());
    }

    private boolean usable(View view) {
        if (view == null || view == root || !view.isAttachedToWindow() || !view.isShown()
                || !view.isEnabled() || !view.isFocusable()) return false;
        for (View ancestor = view; ancestor != null;
             ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null) {
            if (ancestor == root) return true;
        }
        return false;
    }

    @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        remember(oldFocus);
        remember(newFocus);
        root.postInvalidateOnAnimation();
    }

    @Override public boolean onPreDraw() { remember(root.findFocus()); return true; }

    private void remember(View view) {
        if (!usable(view)) return;
        previous = new WeakReference<>(view);
        if (view instanceof ListView) {
            ListView list = (ListView) view;
            if (list.getSelectedItemPosition() >= 0) {
                previousRow = list.getSelectedItemPosition();
                previousAdapter = list.getAdapter();
                previousItemId = list.getSelectedItemId();
            }
        }
    }

    View prepare() {
        View chosen = root.findFocus();
        if (!usable(chosen)) chosen = previous.get();
        if (!usable(chosen)) {
            for (View candidate : root.getFocusables(View.FOCUS_FORWARD)) {
                if (usable(candidate)) { chosen = candidate; break; }
            }
        }
        if (!usable(chosen)) return null;
        if (root.isInTouchMode()) {
            boolean wasFocusable = chosen.isFocusableInTouchMode();
            chosen.setFocusableInTouchMode(true);
            chosen.requestFocusFromTouch();
            chosen.setFocusableInTouchMode(wasFocusable);
        } else chosen.requestFocus();
        if (!usable(chosen) || root.findFocus() != chosen) return null;
        if (chosen instanceof ListView) {
            ListView list = (ListView) chosen;
            android.widget.ListAdapter adapter = list.getAdapter();
            if (adapter == null || adapter.getCount() == 0) return null;
            if (list.getSelectedItemPosition() < 0) {
                int row = previousAdapter == adapter && previousRow >= 0 && previousRow < adapter.getCount()
                        && (!adapter.hasStableIds() || adapter.getItemId(previousRow) == previousItemId)
                        ? previousRow : Math.max(0, list.getFirstVisiblePosition());
                while (row < adapter.getCount() && !adapter.isEnabled(row)) row++;
                if (row == adapter.getCount()) return null;
                list.setSelection(row);
            }
        }
        remember(chosen);
        return chosen;
    }

    void send(int code) {
        if (code == KeyEvent.KEYCODE_BACK) { deliver(code); return; }
        View focus = prepare();
        if (focus == null) return;
        if (focus instanceof ListView && focus.isLayoutRequested()) {
            cancel();
            FocusTarget target = new FocusTarget(root);
            pendingTarget = target;
            long deadline = SystemClock.uptimeMillis() + 400;
            pending = () -> {
                if (!target.valid(root) || !root.hasWindowFocus() || SystemClock.uptimeMillis() > deadline) {
                    cancel(); return;
                }
                if (focus.isLayoutRequested()) { root.postOnAnimation(pending); return; }
                cancel();
                deliver(code);
            };
            root.postOnAnimation(pending);
        } else deliver(code);
    }

    private void deliver(int code) {
        long time = SystemClock.uptimeMillis();
        View before = root.findFocus();
        boolean handled = dispatch.key(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0));
        int direction = direction(code);
        // Local dispatch does not pass through ViewRootImpl's unhandled-key focus traversal.
        if (!handled && direction != 0 && before != null && root.findFocus() == before && usable(before)) {
            View next = before.focusSearch(direction);
            if (usable(next) && next != before) next.requestFocus(direction);
        }
        dispatch.key(new KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0));
        remember(root.findFocus());
        root.postInvalidateOnAnimation();
        android.util.Log.i("SmartTubeRayNeoInput", "key=" + code + " focus="
                + (root.findFocus() == null ? "none" : root.findFocus().getClass().getSimpleName()));
    }

    private static int direction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    void cancel() {
        if (pending != null) { root.removeCallbacks(pending); pending = null; }
        if (pendingTarget != null) { pendingTarget.close(); pendingTarget = null; }
    }
    void close() {
        cancel();
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
            root.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    }
}
