package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextureViewWrapper implements SurfaceWrapper {
    private int mState = SURFACE_NOT_CREATED;
    private final TextureView mVideoSurface;
    private SurfaceHolder.Callback mMediaPlaybackCallback;
    private TextureViewSurfaceHolder mHolder;
    private int mSurfaceWidth, mSurfaceHeight;

    @SuppressLint("WrongConstant")
    public TextureViewWrapper(Context context, ViewGroup root) {
        mVideoSurface = (TextureView) LayoutInflater.from(context).inflate(
                R.layout.lb_video_texture, root, false);
        mVideoSurface.setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mHolder = new TextureViewSurfaceHolder(new Surface(surface));
                mSurfaceWidth = width;
                mSurfaceHeight = height;
                mState = SURFACE_CREATED;
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceCreated(mHolder);
                    mMediaPlaybackCallback.surfaceChanged(mHolder, android.graphics.PixelFormat.OPAQUE, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                mSurfaceWidth = width;
                mSurfaceHeight = height;
                if (mMediaPlaybackCallback != null && mHolder != null) {
                    mMediaPlaybackCallback.surfaceChanged(mHolder, android.graphics.PixelFormat.OPAQUE, width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mState = SURFACE_NOT_CREATED;
                TextureViewSurfaceHolder holder = mHolder;
                mHolder = null;
                if (holder != null) {
                    try {
                        if (mMediaPlaybackCallback != null) mMediaPlaybackCallback.surfaceDestroyed(holder);
                    } finally { holder.getSurface().release(); }
                }

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                com.liskovsoft.smartyoutubetv2.common.rayneo.StereoLayout.invalidateAncestor(mVideoSurface);
            }
        });
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        if (mMediaPlaybackCallback == callback) return;
        if (mMediaPlaybackCallback != null && mHolder != null) {
            mMediaPlaybackCallback.surfaceDestroyed(mHolder);
        }
        mMediaPlaybackCallback = callback;

        if (callback != null) {
            if (mState == SURFACE_CREATED && mHolder != null) {
                mMediaPlaybackCallback.surfaceCreated(mHolder);
                mMediaPlaybackCallback.surfaceChanged(mHolder, android.graphics.PixelFormat.OPAQUE,
                        mSurfaceWidth, mSurfaceHeight);
            }
        }
    }

    @Override
    public View getSurfaceView() {
        return mVideoSurface;
    }
}
