package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int container = com.liskovsoft.smartyoutubetv2.tv.ui.common.GuidedContent.install(this);

        if (null == savedInstanceState) {
            GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), container);
        }
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
