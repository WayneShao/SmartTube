package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import androidx.leanback.widget.BaseGridView;
import androidx.recyclerview.widget.RecyclerView;

/** Restores per-window focus and supplies the normal ViewRoot DPAD fallback for local dispatch. */
final class FocusNavigator implements ViewTreeObserver.OnGlobalFocusChangeListener, ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnGlobalLayoutListener {
    interface Dispatch { boolean key(KeyEvent event); }
    private final StereoLayout root;
    private final Dispatch dispatch;
    private final FocusRecovery recovery;
    private boolean recoveryRequested;
    private boolean closed;
    private Runnable observation;
    private Runnable pending;
    private FocusTarget pendingTarget;

    FocusNavigator(StereoLayout root, Dispatch dispatch) {
        this.root = root;
        this.dispatch = dispatch;
        recovery = new FocusRecovery(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(this);
        root.getViewTreeObserver().addOnPreDrawListener(this);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        recovery.remember(root.findFocus());
    }

    private boolean usable(View view) { return recovery.usable(view); }

    @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        recovery.remember(oldFocus);
        recovery.remember(newFocus);
        recoveryRequested = true;
        root.postInvalidateOnAnimation();
    }

    @Override public void onGlobalLayout() { recoveryRequested = true; }

    @Override public boolean onPreDraw() {
        if (!closed && recoveryRequested && root.hasWindowFocus() && root.isAttachedToWindow()
                && !root.isLayoutRequested()) {
            recoveryRequested = false;
            if (!recovery.valid(root.findFocus())) prepare();
        }
        recovery.remember(root.findFocus());
        return true;
    }

    View prepare() { return closed ? null : recovery.prepare(); }

    void send(int code) {
        if (closed) return;
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
        FocusTarget ownership = new FocusTarget(root);
        String scroll = scrollState(before);
        int direction = direction(code);
        try {
            boolean handled = dispatch.key(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0));
            // Local dispatch does not pass through ViewRootImpl's unhandled-key focus traversal.
            if (!handled && direction != 0 && before != null && root.findFocus() == before && usable(before)
                    && ownership.valid(root) && scroll.equals(scrollState(before))) {
                View next = before.focusSearch(direction);
                // Leanback can return a non-focusable fragment container whose descendants own focus.
                // Let its requestFocus() perform the normal child-focus/header transition callbacks.
                if (recovery.inside(next) && next != before && next.hasFocusable()) next.requestFocus(direction);
            }
            dispatch.key(new KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0));
        } finally {
            ownership.close();
        }
        recovery.remember(root.findFocus());
        if (direction != 0) observeResult();
        root.postInvalidateOnAnimation();
        android.util.Log.i("SmartTubeRayNeoInput", "key=" + code + " focus="
                + (root.findFocus() == null ? "none" : root.findFocus().getClass().getSimpleName()));
    }

    // Inspect after key dispatch/layout. Recovery never replays the original key.
    private void observeResult() {
        if (observation != null) root.removeCallbacks(observation);
        long deadline = SystemClock.uptimeMillis() + 400;
        observation = () -> {
            if (closed || !root.isAttachedToWindow() || !root.hasWindowFocus()) {
                observation = null;
                return;
            }
            if (root.isLayoutRequested() || recovery.waiting(root)) {
                if (SystemClock.uptimeMillis() < deadline) root.postOnAnimation(observation);
                else observation = null; // Later layout/data events can request recovery anew.
                return;
            }
            observation = null;
            if (!recovery.valid(root.findFocus())) {
                View restored = prepare();
                android.util.Log.i("SmartTubeRayNeoInput", "focus recovery="
                        + (restored == null ? "waiting for content" : restored.getClass().getSimpleName()));
            }
        };
        root.postOnAnimation(observation);
    }

    private static String scrollState(View view) {
        for (View node = view; node != null;
             node = node.getParent() instanceof View ? (View) node.getParent() : null) {
            if (node instanceof RecyclerView) {
                RecyclerView grid = (RecyclerView) node;
                int selected = grid instanceof BaseGridView ? ((BaseGridView) grid).getSelectedPosition() : -1;
                return selected + ":" + grid.computeVerticalScrollOffset()
                        + ":" + grid.computeHorizontalScrollOffset() + ":" + grid.getScrollState();
            }
            if (node instanceof ListView) {
                ListView list = (ListView) node;
                return list.getFirstVisiblePosition() + ":"
                        + (list.getChildCount() == 0 ? 0 : list.getChildAt(0).getTop());
            }
        }
        return view == null ? "none" : view.getScrollX() + ":" + view.getScrollY();
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
        recoveryRequested = false;
        if (observation != null) { root.removeCallbacks(observation); observation = null; }
        if (pending != null) { root.removeCallbacks(pending); pending = null; }
        if (pendingTarget != null) { pendingTarget.close(); pendingTarget = null; }
    }
    void close() {
        closed = true;
        cancel();
        recovery.close();
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
            root.getViewTreeObserver().removeOnPreDrawListener(this);
            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }
}
