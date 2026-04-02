package com.liskovsoft.smartyoutubetv2.tv.ui.rayneo;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Accessibility service that enables the RayNeo X3 Pro temple touchpad to navigate
 * Android UI without touch-mode restrictions.
 *
 * <h3>Why an AccessibilityService?</h3>
 * Temple touchpad events carry {@code SOURCE_TOUCHSCREEN}, which puts Android into
 * touch mode. In touch mode, {@code View.requestFocus()} silently fails for views
 * that are {@code focusable=true} but not {@code focusableInTouchMode=true}. The
 * Accessibility framework bypasses this restriction: when an accessibility action
 * is dispatched through {@code ViewRootImpl.performAccessibilityAction()} the system
 * temporarily clears touch mode before calling {@code requestFocus()}, then restores
 * it. This means {@link AccessibilityNodeInfo#ACTION_FOCUS} works on ANY focusable
 * view without per-view {@code focusableInTouchMode} patches.
 *
 * <h3>Enabling without user interaction</h3>
 * The service is enabled programmatically using {@code Settings.Secure} (requires
 * {@code WRITE_SECURE_SETTINGS}, granted once via ADB). See
 * {@code LeanbackActivity.ensureA11yServiceEnabled()}.
 *
 * <h3>Usage (called from RayNeoGestureHandler)</h3>
 * <pre>
 *   RayNeoA11yService svc = RayNeoA11yService.getInstance();
 *   if (svc != null) svc.navigate(View.FOCUS_DOWN);
 * </pre>
 */
public class RayNeoA11yService extends AccessibilityService {

    private static final String TAG = "RayNeoA11y";

    /** Singleton reference, set when the service connects. Thread-safe via volatile. */
    private static volatile RayNeoA11yService sInstance;

    public static RayNeoA11yService getInstance() {
        return sInstance;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onServiceConnected() {
        sInstance = this;
        Log.d(TAG, "onServiceConnected — A11y navigation active");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        Log.d(TAG, "onDestroy — A11y navigation inactive");
    }

    // -------------------------------------------------------------------------
    // Public navigation API
    // -------------------------------------------------------------------------

    /**
     * Move INPUT focus in the given direction.
     *
     * @param viewFocusDirection one of {@link android.view.View#FOCUS_UP},
     *                           {@link android.view.View#FOCUS_DOWN},
     *                           {@link android.view.View#FOCUS_LEFT},
     *                           {@link android.view.View#FOCUS_RIGHT}
     * @return {@code true} if focus was successfully moved
     */
    public boolean navigate(int viewFocusDirection) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.d(TAG, "navigate: no root window");
            return false;
        }
        AccessibilityNodeInfo focused = null;
        AccessibilityNodeInfo next    = null;
        try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                Log.d(TAG, "navigate: no INPUT-focused node");
                return false;
            }
            next = focused.focusSearch(viewFocusDirection);
            Log.d(TAG, "navigate dir=" + dirName(viewFocusDirection)
                    + " from=" + nodeDesc(focused) + " → " + nodeDesc(next));
            if (next == null || next.equals(focused)) {
                return false;
            }
            boolean ok = next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Log.d(TAG, "navigate performAction(FOCUS) ok=" + ok);
            return ok;
        } finally {
            if (focused != null) focused.recycle();
            if (next    != null) next.recycle();
            root.recycle();
        }
    }

    /**
     * Click (ACTION_CLICK) on the currently INPUT-focused node.
     * Equivalent to pressing DPAD_CENTER / SELECT on a remote control.
     *
     * @return {@code true} if the click was handled
     */
    public boolean click() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = null;
        try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                Log.d(TAG, "click: no INPUT-focused node");
                return false;
            }
            boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "click on=" + nodeDesc(focused) + " ok=" + ok);
            return ok;
        } finally {
            if (focused != null) focused.recycle();
            root.recycle();
        }
    }

    /**
     * Simulate the Back button via {@link #performGlobalAction}.
     * This is identical to pressing the hardware Back key and does not require
     * {@code INJECT_EVENTS}.
     */
    public boolean back() {
        boolean ok = performGlobalAction(GLOBAL_ACTION_BACK);
        Log.d(TAG, "back() ok=" + ok);
        return ok;
    }

    // -------------------------------------------------------------------------
    // Mandatory AccessibilityService overrides
    // -------------------------------------------------------------------------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op — we only act on gesture input from RayNeoGestureHandler.
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String nodeDesc(AccessibilityNodeInfo node) {
        if (node == null) return "null";
        CharSequence cls  = node.getClassName();
        String       rid  = node.getViewIdResourceName();
        CharSequence text = node.getText();
        return (cls  != null ? cls.toString().replaceAll(".*\\.", "") : "?")
             + (rid  != null ? "#" + rid.replaceAll(".*:", "") : "")
             + (text != null && text.length() > 0 ? "[" + text + "]" : "");
    }

    private static String dirName(int dir) {
        switch (dir) {
            case android.view.View.FOCUS_UP:    return "UP";
            case android.view.View.FOCUS_DOWN:  return "DOWN";
            case android.view.View.FOCUS_LEFT:  return "LEFT";
            case android.view.View.FOCUS_RIGHT: return "RIGHT";
            default: return String.valueOf(dir);
        }
    }
}
