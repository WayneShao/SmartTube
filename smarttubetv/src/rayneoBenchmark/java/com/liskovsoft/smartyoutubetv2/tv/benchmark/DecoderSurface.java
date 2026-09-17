// Experimental renderer port from WayneShao/moonlight-android d01a5a8b. Benchmark only.
package com.liskovsoft.smartyoutubetv2.tv.benchmark;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceHolder;

/** Immutable holder passed to Moonlight's existing decoder callbacks for one GL generation. */
final class DecoderSurface implements SurfaceHolder {
    private final Surface surface;
    private final Rect frame;
    DecoderSurface(Surface surface,int width,int height) {
        this.surface=surface; frame=new Rect(0,0,width,height);
    }
    @Override public Surface getSurface() { return surface; }
    @Override public Rect getSurfaceFrame() { return new Rect(frame); }
    @Override public boolean isCreating() { return false; }
    @Override public void addCallback(Callback callback) { throw new UnsupportedOperationException("Callbacks owned by StereoVideoView"); }
    @Override public void removeCallback(Callback callback) {}
    @Override public void setFixedSize(int width,int height) { throw new UnsupportedOperationException("Set stream size before decoder creation"); }
    @Override public void setSizeFromLayout() {}
    @Override public void setType(int type) {}
    @Override public void setFormat(int format) {}
    @Override public void setKeepScreenOn(boolean keep) {}
    @Override public Canvas lockCanvas() { throw new UnsupportedOperationException("Decoder-only surface"); }
    @Override public Canvas lockCanvas(Rect dirty) { return lockCanvas(); }
    @Override public void unlockCanvasAndPost(Canvas canvas) { throw new UnsupportedOperationException("Decoder-only surface"); }
}
