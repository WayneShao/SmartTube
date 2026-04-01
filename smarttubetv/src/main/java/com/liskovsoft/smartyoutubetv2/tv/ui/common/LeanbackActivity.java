package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.DoubleBackManager2;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.rayneo.RayNeoConfig;
import com.liskovsoft.smartyoutubetv2.tv.ui.rayneo.RayNeoGestureHandler;
import com.liskovsoft.smartyoutubetv2.tv.ui.rayneo.RayNeoStereoLayout;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;

/**
 * This parent class contains common methods that run in every activity such as search.
 */
public abstract class LeanbackActivity extends MotherActivity {
    private static final String TAG = LeanbackActivity.class.getSimpleName();
    private UriBackgroundManager mBackgroundManager;
    private ModeSyncManager mModeSyncManager;
    private DoubleBackManager2 mDoubleBackManager;
    private GlobalKeyTranslator mGlobalKeyTranslator;
    private final Runnable sOnFinish = () -> Utils.forceFinishTheApp(this);
    // RayNeo X3 Pro: gesture handler (null on non-X3Pro devices)
    private RayNeoGestureHandler mRayNeoGestureHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackgroundManager = new UriBackgroundManager(this);
        mModeSyncManager = ModeSyncManager.instance();
        mDoubleBackManager = new DoubleBackManager2(this);
        mGlobalKeyTranslator = this instanceof PlaybackActivity ?
                new PlayerKeyTranslator(this) :
                new GlobalKeyTranslator(this);
        mGlobalKeyTranslator.apply();
        if (RayNeoConfig.isEnabled()) {
            mRayNeoGestureHandler = new RayNeoGestureHandler(this);
        }
    }

    /**
     * RayNeo X3 Pro: ensure the app content is wrapped inside {@link RayNeoStereoLayout}
     * exactly once.  Safe to call multiple times; idempotent if already wrapped.
     *
     * <p>This is invoked from both {@link #setContentView} (activities that inflate a
     * layout resource) and {@link #onStart} (activities that add content via Fragment
     * transactions without ever calling {@code setContentView}, e.g.
     * {@code SignInActivity}, account-picker screens).  {@code FragmentActivity.onStart}
     * calls {@code execPendingActions()} internally, so all pending Fragment transactions
     * are committed before our {@code onStart} override runs — the child view is
     * therefore guaranteed to be present by that point.</p>
     */
    private void ensureStereoWrapper() {
        FrameLayout content = (FrameLayout) getWindow().getDecorView()
                .findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        // Idempotent: skip if already wrapped.
        if (content.getChildAt(0) instanceof RayNeoStereoLayout) return;
        View appRoot = content.getChildAt(0);
        content.removeView(appRoot);
        RayNeoStereoLayout stereo = new RayNeoStereoLayout(this);
        stereo.addView(appRoot, 0,
                new FrameLayout.LayoutParams(RayNeoConfig.SINGLE_EYE_WIDTH,
                                             RayNeoConfig.SINGLE_EYE_HEIGHT));
        content.addView(stereo,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /**
     * RayNeo X3 Pro: wrap the inflated layout inside {@link RayNeoStereoLayout}.
     * For activities that never call setContentView, see {@link #onStart}.
     */
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        if (RayNeoConfig.isEnabled()) {
            ensureStereoWrapper();
        }
    }

    @Override
    public boolean onSearchRequested() {
        SearchPresenter.instance(this).startSearch(null);
        return true;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, event);

        // MOD RayNeo X3 Pro: extra diagnostics — log focused view for injected DPAD events.
        if (RayNeoConfig.isEnabled() && event != null) {
            int kc = event.getKeyCode();
            if (kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN
                    || kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View focused = getWindow().getDecorView().findFocus();
                Log.d(TAG, "DPAD key=" + KeyEvent.keyCodeToString(kc)
                        + " action=" + event.getAction()
                        + " focused=" + (focused == null
                                ? "null"
                                : focused.getClass().getSimpleName()
                                  + "#" + Integer.toHexString(focused.getId())));
            }
        }

        KeyEvent newEvent = mGlobalKeyTranslator.translate(event);
        return super.dispatchKeyEvent(newEvent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // RayNeo X3 Pro: intercept temple-touchpad gestures and convert to D-pad KeyEvents.
        // dispatchTouchEvent and dispatchKeyEvent are independent dispatch chains, so
        // calling dispatchKeyEvent from here cannot loop back into this method.
        if (mRayNeoGestureHandler != null && mRayNeoGestureHandler.handleMotionEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    public UriBackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // RayNeo X3 Pro: activities that never call setContentView (e.g. SignInActivity,
        // account-picker) add their UI via Fragment transactions. FragmentActivity.onStart()
        // flushes all pending transactions before returning, so by the time we reach here
        // the Fragment's root view is already a child of android.R.id.content.
        if (RayNeoConfig.isEnabled()) {
            ensureStereoWrapper();
        }
        mBackgroundManager.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // PIP fix: While entering/exiting PIP mode only Pause/Resume is called

        mGlobalKeyTranslator.apply(); // adapt to state changes (like enter/exit from PIP mode)

        mModeSyncManager.restore(this);

        getViewManager().addTop(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBackgroundManager.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBackgroundManager.onDestroy();
    }

    @Override
    public void finish() {
        // user pressed back key
        if (!getViewManager().hasParentView(this)) {
            switch (getGeneralData().getAppExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishTheApp);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishTheApp();
                    break;
            }
        } else if (this instanceof PlaybackActivity) {
            switch (getGeneralData().getPlayerExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishReally);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishReally();
                    break;
            }
        } else if (this instanceof SearchTagsActivity) {
            switch (getGeneralData().getSearchExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishReally);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishReally();
                    break;
            }
        } else {
            finishReally();
        }
    }

    @Override
    public void finishReally() {
        // Mandatory line. Fix un-proper view order (especially for playback view).
        getViewManager().startParentView(this);
        super.finishReally();
    }

    private void finishTheApp() {
        Utils.properlyFinishTheApp(this);

        if (!RemoteControlData.instance(this).isConnectedBefore()) {
            getViewManager().addOnFinish(sOnFinish);
        }
    }
}
