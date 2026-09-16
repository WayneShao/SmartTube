package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;

/** Application-level binding; upstream callers keep using MessageHelpers. */
public final class RayNeoMessagePresenter implements MessageHelpers.MessagePresenter {
    private static final RayNeoMessagePresenter INSTANCE = new RayNeoMessagePresenter();

    private RayNeoMessagePresenter() {}

    public static void install(Context context) {
        if (RayNeo.isEnabled(context)) MessageHelpers.setMessagePresenter(INSTANCE);
    }

    @Override public boolean show(Context context, String message, boolean isLong) {
        return RayNeoWindow.showMessage(context, message, isLong);
    }

    @Override public void cancel() {
        RayNeoWindow.dismissMessages();
    }
}
