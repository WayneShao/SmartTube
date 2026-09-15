package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;

/** Route app-owned foreground notices through the focused stereo window, including dialogs. */
public final class RayNeoMessages {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private RayNeoMessages() {}
    public static void showMessage(Context context, String message) { showMessage(context, message, false); }
    public static void showMessage(Context context, int resource) {
        if (context != null) showMessage(context, context.getString(resource));
    }
    public static void showMessage(Context context, int resource, Object... args) {
        if (context != null) showMessage(context, context.getString(resource, args));
    }
    public static void showMessage(Context context, String template, Object... args) {
        showMessage(context, String.format(template, args));
    }
    public static void showMessage(Context context, String tag, Throwable error) {
        showMessage(context, tag + ": %s", com.liskovsoft.sharedutils.helpers.Helpers.toString(error));
    }
    public static void showMessage(Context context, String message, boolean isLong) {
        if (context == null || message == null || message.isEmpty()) return;
        Context app = context.getApplicationContext();
        Runnable show = () -> {
            if (!RayNeoWindow.showMessage(app, message, isLong)) MessageHelpers.showMessage(app, message, isLong);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) show.run(); else MAIN.post(show);
    }
    public static void showLongMessage(Context context, String message) { showMessage(context, message, true); }
    public static void showLongMessage(Context context, int resource) {
        if (context != null) showLongMessage(context, context.getString(resource));
    }
    public static void showLongMessage(Context context, String template, Object... args) {
        showLongMessage(context, String.format(template, args));
    }
    public static void cancelToasts() {
        Runnable cancel = () -> { RayNeoWindow.dismissMessages(); MessageHelpers.cancelToasts(); };
        if (Looper.myLooper() == Looper.getMainLooper()) cancel.run(); else MAIN.post(cancel);
    }
}
