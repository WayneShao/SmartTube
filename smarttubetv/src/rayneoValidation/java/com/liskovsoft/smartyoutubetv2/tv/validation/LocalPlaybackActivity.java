package com.liskovsoft.smartyoutubetv2.tv.validation;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.SubtitleView;
import java.util.Collections;

/** Local input and repeatable UI stimuli only; never included in the distributed OES app. */
public final class LocalPlaybackActivity extends PlaybackActivity {
    private final Handler handler = new Handler();
    private PlaybackFragment fragment;
    private boolean opened;
    private int attempts, tick;
    private final Runnable run = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (!fragment.isEngineInitialized()) {
                if (++attempts < 50) handler.postDelayed(this,100);
                else Log.e("SmartTubeValidation","engine initialization timeout");
                return;
            }
            if (!opened) {
                opened = true;
                Video video = new Video(); video.title = "Local 1080p30 comparison";
                fragment.setVideo(video);
                fragment.openUrlList(Collections.singletonList(new java.io.File(getExternalFilesDir(null),
                        "motion-1080p30.mp4").toURI().toString()));
                fragment.setPlayWhenReady(true);
            }
            boolean controls = getIntent().getBooleanExtra("controls",false);
            fragment.showOverlay(controls);
            if (getIntent().getBooleanExtra("subtitles",false)) {
                SubtitleView subtitles = findViewById(R.id.leanback_subtitles);
                subtitles.onCues(Collections.singletonList(new Cue("Subtitle validation " + tick)));
            }
            if (getIntent().getBooleanExtra("notice",false) && tick % 5 == 0) {
                com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(LocalPlaybackActivity.this,"OES overlay validation");
            }
            if (fragment.getPositionMs() > 37000) fragment.setPositionMs(0);
            if (tick++ % 5 == 0) Log.i("SmartTubeValidation","position=" + fragment.getPositionMs()
                    + " format=" + fragment.getVideoFormat() + " controls=" + controls);
            handler.postDelayed(this,1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fragment = (PlaybackFragment)getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
    }
    @Override protected void onResume() { super.onResume(); handler.post(run); }
    @Override protected void onPause() { handler.removeCallbacksAndMessages(null); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
