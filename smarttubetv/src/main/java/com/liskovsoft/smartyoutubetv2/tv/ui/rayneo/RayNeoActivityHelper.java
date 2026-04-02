package com.liskovsoft.smartyoutubetv2.tv.ui.rayneo;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Static helpers that apply RayNeo X3 Pro adaptations to any {@link Activity}.
 *
 * <p>Used by both {@code LeanbackActivity} and activities that extend
 * {@code MotherActivity} directly (e.g. {@code AppDialogActivity}), keeping
 * the implementation in one place.</p>
 */
public final class RayNeoActivityHelper {

    private static final String TAG = "RayNeoHelper";

    private RayNeoActivityHelper() {}

    // -------------------------------------------------------------------------
    // Stereo wrapper
    // -------------------------------------------------------------------------

    /**
     * Ensures the Activity's content is wrapped in {@link RayNeoStereoLayout}.
     * Idempotent — safe to call from both {@code setContentView} and {@code onStart}.
     */
    public static void ensureStereoWrapper(Activity activity) {
        FrameLayout content = (FrameLayout) activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        if (content.getChildAt(0) instanceof RayNeoStereoLayout) return;
        View appRoot = content.getChildAt(0);
        content.removeView(appRoot);
        RayNeoStereoLayout stereo = new RayNeoStereoLayout(activity);
        stereo.addView(appRoot, 0,
                new FrameLayout.LayoutParams(RayNeoConfig.SINGLE_EYE_WIDTH,
                                             RayNeoConfig.SINGLE_EYE_HEIGHT));
        content.addView(stereo,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // -------------------------------------------------------------------------
    // Accessibility service self-enable
    // -------------------------------------------------------------------------

    /**
     * Programmatically enables {@link RayNeoA11yService} via {@code Settings.Secure}.
     * Requires {@code WRITE_SECURE_SETTINGS} (granted once via ADB).
     */
    public static void ensureA11yServiceEnabled(Activity activity) {
        try {
            String svcFqn = activity.getPackageName()
                    + "/com.liskovsoft.smartyoutubetv2.tv.ui.rayneo.RayNeoA11yService";
            String current = Settings.Secure.getString(activity.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (current != null && current.contains(svcFqn)) return;
            String combined = (current == null || current.isEmpty())
                    ? svcFqn : current + ":" + svcFqn;
            Settings.Secure.putString(activity.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, combined);
            Settings.Secure.putInt(activity.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.d(TAG, "A11y service enabled for " + activity.getClass().getSimpleName());
        } catch (SecurityException e) {
            Log.d(TAG, "ensureA11yServiceEnabled: need WRITE_SECURE_SETTINGS — "
                    + "run: adb shell pm grant " + activity.getPackageName()
                    + " android.permission.WRITE_SECURE_SETTINGS");
        }
    }

    // -------------------------------------------------------------------------
    // Touch-event forwarding helper
    // -------------------------------------------------------------------------

    /**
     * Forward a {@link MotionEvent} to a {@link RayNeoGestureHandler}.
     * Returns {@code true} if the event was consumed (caller should skip super).
     * Null-safe.
     */
    public static boolean handleTouchEvent(RayNeoGestureHandler handler, MotionEvent event) {
        return handler != null && handler.handleMotionEvent(event);
    }
}
