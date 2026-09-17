package com.liskovsoft.smartyoutubetv2.tv.benchmark;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.video.VideoListener;
import com.liskovsoft.smartyoutubetv2.common.rayneo.StereoLayout;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.TextureViewWrapper;

/** Opt-in component comparison, deliberately separate from the normal app/package. */
public final class VideoBenchmarkActivity extends Activity implements SurfaceHolder.Callback {
    private SimpleExoPlayer player;
    private StereoVideoView oes;
    private TextureViewWrapper texture;
    private final Handler handler = new Handler();
    private String mode;
    private final Runnable stats = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            com.google.android.exoplayer2.decoder.DecoderCounters counters = player.getVideoDecoderCounters();
            if (counters != null) counters.ensureUpdated();
            Log.i("RayNeoBench", "mode=" + mode + " state=" + player.getPlaybackState()
                    + " position=" + player.getCurrentPosition() + " format=" + player.getVideoFormat()
                    + " rendered=" + (counters == null ? -1 : counters.renderedOutputBufferCount)
                    + " dropped=" + (counters == null ? -1 : counters.droppedBufferCount));
            handler.postDelayed(this, 10000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        mode = getIntent().getStringExtra("mode");
        if (!"texture".equals(mode) && !"oes".equals(mode)) { finish(); return; }
        player = ExoPlayerFactory.newSimpleInstance(this);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.addVideoListener(new VideoListener() {
            @Override public void onRenderedFirstFrame() { Log.i("RayNeoBench", "first-frame mode=" + mode); }
        });
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(android.graphics.Color.BLACK);
        if ("oes".equals(mode)) {
            oes = new StereoVideoView(this, 1920, 1080, false, this);
            screen.addView(oes, new FrameLayout.LayoutParams(-1, -1));
        } else {
            StereoLayout stereo = new StereoLayout(this);
            FrameLayout logical = new FrameLayout(this);
            texture = new TextureViewWrapper(this, logical);
            FrameLayout.LayoutParams rect = new FrameLayout.LayoutParams(640, 360);
            rect.topMargin = 60;
            logical.addView(texture.getSurfaceView(), rect);
            stereo.addView(logical, new FrameLayout.LayoutParams(-1, -1));
            screen.addView(stereo, new FrameLayout.LayoutParams(-1, -1));
            texture.setSurfaceHolderCallback(this);
        }
        setContentView(screen);
        String sample = new java.io.File(getExternalFilesDir(null), "motion-1080p30.mp4").toURI().toString();
        player.prepare(new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(this, "RayNeoBenchmark"))
                .createMediaSource(Uri.parse(sample)));
        player.setPlayWhenReady(true);
        handler.post(stats);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (player != null) player.setVideoSurface(holder.getSurface());
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) player.setVideoSurface(null);
    }
    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        if (texture != null) texture.setSurfaceHolderCallback(null);
        if (oes != null) oes.close();
        super.onStop();
        finish();
    }
}
