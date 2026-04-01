package com.liskovsoft.smartyoutubetv2.tv.ui.rayneo;

import android.os.Build;

/**
 * RayNeo X3 Pro adaptation configuration.
 *
 * Detection: X3 Pro reports MODEL="RayNeo X3 Pro" and runs the Mercury launcher
 * (com.ffalconxr.mercury.launcher). We detect by MODEL string; add more
 * fingerprints here if needed.
 */
public final class RayNeoConfig {

    /** Physical width of one eye panel in pixels. */
    public static final int SINGLE_EYE_WIDTH  = 640;
    /** Physical height of one eye panel in pixels. */
    public static final int SINGLE_EYE_HEIGHT = 480;

    /** Override flag – set to true in debug builds to force-enable on non-X3Pro devices. */
    private static Boolean sForceEnabled = null;

    private RayNeoConfig() {}

    /** Returns true when running on a RayNeo X3 Pro or when force-enabled for testing. */
    public static boolean isEnabled() {
        if (sForceEnabled != null) return sForceEnabled;
        return isX3ProDevice();
    }

    /** Force-enable or disable for testing on non-X3Pro hardware. */
    public static void setForceEnabled(boolean enabled) {
        sForceEnabled = enabled;
    }

    private static boolean isX3ProDevice() {
        // Build.MODEL = "ARGF20", Build.DEVICE = "MercuryLiteXR" on RayNeo X3 Pro.
        return Build.MODEL.equalsIgnoreCase("ARGF20")
                || Build.DEVICE.equalsIgnoreCase("MercuryLiteXR");
    }
}
