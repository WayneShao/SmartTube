package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** Opt in only to the independent X3 Pro display shape validated for this port. */
public final class RayNeo {
    private RayNeo() {}

    public static boolean isSupported(String manufacturer, String model, String device, int width, int height) {
        return "RayNeo".equalsIgnoreCase(manufacturer)
                && "ARGF20".equals(model) && "MercuryLiteXR".equals(device)
                && width == 1280 && height == 480;
    }

    public static boolean isEnabled(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 29) return false;
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return false;
        DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getRealMetrics(metrics);
        return isSupported(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, metrics.widthPixels, metrics.heightPixels);
    }
}
