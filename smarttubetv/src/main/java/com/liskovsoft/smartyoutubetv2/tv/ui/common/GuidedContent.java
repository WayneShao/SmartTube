package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.common.rayneo.RayNeoWindow;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Install an owned content tree before an asynchronous guided Fragment transaction. */
public final class GuidedContent {
    private GuidedContent() {}

    public static int install(FragmentActivity activity) {
        activity.setContentView(R.layout.activity_guided_content);
        RayNeoWindow.install(activity); // Idempotent when MotherActivity already wrapped it.
        return R.id.guided_content;
    }
}
