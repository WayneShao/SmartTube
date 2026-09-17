package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import com.liskovsoft.smartyoutubetv2.common.rayneo.StereoLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Arrays;

/** A logical video anchor preserves upstream layout; the GL output is a full-window sibling. */
final class OesSurfaceWrapper implements SurfaceWrapper, ViewTreeObserver.OnPreDrawListener {
    private final View anchor;
    private final Runnable fallback;
    private final Matrix anchorToGlobal = new Matrix(), outputToGlobal = new Matrix(), globalToOutput = new Matrix();
    private final float[] corners = new float[8];
    private final Rect visible = new Rect();
    private final int[] location = new int[2];
    private SurfaceHolder.Callback callback;
    private TextureViewSurfaceHolder holder;
    private OesVideoView output;
    private StereoLayout stereoRoot;
    private float[] previousVertices;
    private int[] previousClip;
    private boolean closed;

    OesSurfaceWrapper(Context context, Runnable fallback) {
        this.fallback = fallback;
        anchor = new View(context);
        anchor.setId(R.id.video_surface);
        anchor.setLayoutParams(new FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER));
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { anchor.post(install); }
            @Override public void onViewDetachedFromWindow(View view) { close(); }
        });
    }

    private final Runnable install = this::installOutput;
    private final Runnable readyTimeout = () -> { if (!closed && holder == null) fallback(); };

    private void fallback() { fallback.run(); }

    private void installOutput() {
        if (closed || !anchor.isAttachedToWindow()) return;
        StereoLayout stereo = StereoLayout.findAncestor(anchor);
        if (stereo == null || !(stereo.getParent() instanceof ViewGroup)) { fallback.run(); return; }
        stereoRoot = stereo;
        stereo.setBitmapReplay(true);
        output = new OesVideoView(anchor.getContext(),new OesVideoView.Output() {
            @Override public void ready(Surface surface) {
                if (closed || holder != null && holder.getSurface() == surface) return;
                anchor.removeCallbacks(readyTimeout);
                unavailable();
                holder = new TextureViewSurfaceHolder(surface);
                if (callback != null) announce(callback);
            }
            @Override public void unavailable() { unbind(); }
            @Override public void failed() { if (!closed) fallback.run(); }
        });
        ViewGroup host = (ViewGroup) stereo.getParent();
        host.addView(output,0,new FrameLayout.LayoutParams(-1,-1));
        anchor.getViewTreeObserver().addOnPreDrawListener(this);
        anchor.postDelayed(readyTimeout,5000);
    }

    private void announce(SurfaceHolder.Callback target) {
        target.surfaceCreated(holder);
        target.surfaceChanged(holder,android.graphics.PixelFormat.OPAQUE,anchor.getWidth(),anchor.getHeight());
    }

    private void unbind() {
        TextureViewSurfaceHolder old = holder;
        holder = null;
        if (old != null && callback != null) callback.surfaceDestroyed(old);
    }

    @Override public void setSurfaceHolderCallback(SurfaceHolder.Callback next) {
        if (callback == next) return;
        if (callback != null && holder != null) callback.surfaceDestroyed(holder);
        callback = next;
        if (callback != null && holder != null) announce(callback);
    }

    @Override public View getSurfaceView() { return anchor; }
    @Override public boolean supportsViewTransform() { return true; }

    @Override public boolean onPreDraw() {
        if (closed || output == null || output.getWidth() == 0 || output.getHeight() == 0) return true;
        int w = anchor.getWidth(), h = anchor.getHeight();
        corners[0]=0; corners[1]=h; corners[2]=w; corners[3]=h;
        corners[4]=0; corners[5]=0; corners[6]=w; corners[7]=0;
        anchorToGlobal.reset(); anchor.transformMatrixToGlobal(anchorToGlobal);
        outputToGlobal.reset(); output.transformMatrixToGlobal(outputToGlobal);
        if (!outputToGlobal.invert(globalToOutput)) return true;
        anchorToGlobal.mapPoints(corners); globalToOutput.mapPoints(corners);
        float[] vertices = OesGeometry.vertices(corners,output.getWidth()/2,output.getHeight());
        output.getLocationOnScreen(location);
        int[] clip = new int[4];
        if (anchor.isShown() && anchor.getGlobalVisibleRect(visible)) {
            clip[0]=Math.max(0,visible.left-location[0]);
            clip[1]=Math.max(0,visible.top-location[1]);
            clip[2]=Math.max(clip[0],Math.min(output.getWidth()/2,visible.right-location[0]));
            clip[3]=Math.max(clip[1],Math.min(output.getHeight(),visible.bottom-location[1]));
        }
        if (!Arrays.equals(vertices,previousVertices) || !Arrays.equals(clip,previousClip)) {
            previousVertices=vertices; previousClip=clip;
            output.geometry(vertices,clip);
        }
        return true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        anchor.removeCallbacks(install);
        anchor.removeCallbacks(readyTimeout);
        if (anchor.getViewTreeObserver().isAlive()) anchor.getViewTreeObserver().removeOnPreDrawListener(this);
        unbind(); callback = null;
        if (stereoRoot != null) { stereoRoot.setBitmapReplay(false); stereoRoot = null; }
        if (output != null) {
            output.close();
            if (output.getParent() instanceof ViewGroup) ((ViewGroup)output.getParent()).removeView(output);
            output = null;
        }
    }
}
