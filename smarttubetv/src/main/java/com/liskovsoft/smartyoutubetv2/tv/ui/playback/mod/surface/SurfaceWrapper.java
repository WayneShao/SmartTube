package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.view.SurfaceHolder;
import android.view.View;

public interface SurfaceWrapper {
    default boolean supportsViewTransform() { return getSurfaceView() instanceof android.view.TextureView; }
    default void close() {}
    int SURFACE_NOT_CREATED = 0;
    int SURFACE_CREATED = 1;
    void setSurfaceHolderCallback(SurfaceHolder.Callback callback);
    View getSurfaceView();
}
