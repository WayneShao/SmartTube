package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.app.Application;
import android.graphics.SurfaceTexture;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF) // These Surface tests do not load Android-only TLS JNI on the host.
public class TextureViewWrapperTest {
    static class Callback implements SurfaceHolder.Callback {
        final List<SurfaceHolder> created = new ArrayList<>(), changed = new ArrayList<>(), destroyed = new ArrayList<>();
        @Override public void surfaceCreated(SurfaceHolder holder) { created.add(holder); }
        @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { changed.add(holder); }
        @Override public void surfaceDestroyed(SurfaceHolder holder) { destroyed.add(holder); }
    }

    @Test public void usesOneHolderUntilDestructionAndFreshHolderOnRecreation() {
        TextureViewWrapper wrapper = new TextureViewWrapper(RuntimeEnvironment.getApplication(),
                new FrameLayout(RuntimeEnvironment.getApplication()));
        TextureView view = (TextureView) wrapper.getSurfaceView();
        TextureView.SurfaceTextureListener listener = view.getSurfaceTextureListener();
        Callback callback = new Callback();
        wrapper.setSurfaceHolderCallback(callback);
        SurfaceTexture texture = new SurfaceTexture(0);
        listener.onSurfaceTextureAvailable(texture, 640, 360);
        listener.onSurfaceTextureSizeChanged(texture, 320, 240);
        assertEquals(1, callback.created.size());
        assertEquals(2, callback.changed.size());
        assertSame(callback.created.get(0), callback.changed.get(0));
        assertSame(callback.created.get(0), callback.changed.get(1));
        assertTrue(listener.onSurfaceTextureDestroyed(texture));
        assertSame(callback.created.get(0), callback.destroyed.get(0));
        SurfaceTexture next = new SurfaceTexture(0);
        listener.onSurfaceTextureAvailable(next, 640, 360);
        assertNotSame(callback.created.get(0), callback.created.get(1));
        listener.onSurfaceTextureDestroyed(next);
        texture.release(); next.release();
    }

    @Test public void lateRegistrationAndOwnerReplacementUseCurrentHolder() {
        TextureViewWrapper wrapper = new TextureViewWrapper(RuntimeEnvironment.getApplication(),
                new FrameLayout(RuntimeEnvironment.getApplication()));
        TextureView.SurfaceTextureListener listener = ((TextureView) wrapper.getSurfaceView()).getSurfaceTextureListener();
        SurfaceTexture texture = new SurfaceTexture(0);
        listener.onSurfaceTextureAvailable(texture, 640, 360);
        Callback first = new Callback(), second = new Callback();
        wrapper.setSurfaceHolderCallback(first);
        wrapper.setSurfaceHolderCallback(first);
        assertEquals(1, first.created.size());
        wrapper.setSurfaceHolderCallback(second);
        assertEquals(1, first.destroyed.size());
        assertSame(first.created.get(0), second.created.get(0));
        wrapper.setSurfaceHolderCallback(null);
        assertEquals(1, second.destroyed.size());
        listener.onSurfaceTextureDestroyed(texture);
        assertEquals(1, second.destroyed.size());
        texture.release();
    }
}
