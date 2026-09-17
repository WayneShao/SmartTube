package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** One decoder texture, two eye viewports, one swap. UI stays in its original window. */
final class OesVideoView extends GLSurfaceView implements GLSurfaceView.Renderer {
    interface Output {
        void ready(Surface surface);
        void unavailable();
        void failed();
    }
    private final Output output;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] matrix = new float[16];
    private volatile boolean closed;
    private volatile Surface surface;
    private SurfaceTexture texture;
    private int textureId, program, positionLoc, uvLoc, matrixLoc, samplerLoc;
    private int width, height;
    private float[] geometry;
    private int[] clip;
    private boolean hasFrame;
    private boolean hadContext;
    private long consumed, lastStats;

    OesVideoView(Context context, Output output) {
        super(context);
        this.output = output;
        setFocusable(false);
        setClickable(false);
        setEGLContextClientVersion(2);
        setEGLConfigChooser((egl, display) -> {
            // GLSurfaceView may fail before Renderer.onSurfaceCreated. Scope recovery to
            // this view's own GL thread, not the application's global exception handler.
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler original = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((owner, error) -> {
                if (error instanceof RuntimeException) fail((RuntimeException) error);
                else if (original != null) original.uncaughtException(owner,error);
            });
            int[] attributes = {0x3024,8,0x3023,8,0x3022,8,0x3021,0,
                    0x3025,0,0x3026,0,0x3040,4,0x3033,4,0x3038};
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display,attributes,null,0,count) || count[0] == 0)
                throw new IllegalStateException("No GLES2 window config");
            EGLConfig[] configs = new EGLConfig[count[0]];
            if (!egl.eglChooseConfig(display,attributes,configs,configs.length,count))
                throw new IllegalStateException("EGL config query failed");
            return configs[0];
        });
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    void geometry(float[] values, int[] scissor) {
        if (closed) return;
        queueEvent(() -> { geometry = values; clip = scissor; requestRender(); });
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (closed) return;
        if (hadContext) {
            // Old IDs belonged to the lost context. Main-thread fallback unbinds the player
            // before Java handles are released; do not release its active decoder Surface here.
            textureId = program = 0;
            fail(new IllegalStateException("EGL context recreated; use TextureView for this session"));
            return;
        }
        hadContext = true;
        int epoch = generation.incrementAndGet();
        // A new context owns new GL names. Never delete old-context IDs in the new context.
        releaseJavaHandles();
        program = textureId = 0; hasFrame = false; framePending.set(false);
        try {
            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            if (extensions == null || !extensions.contains("GL_OES_EGL_image_external")) throw new IllegalStateException("OES unavailable");
            int vs = shader(GLES20.GL_VERTEX_SHADER,
                    "attribute vec2 position; attribute vec2 uv; uniform mat4 transform; varying vec2 tex; void main(){gl_Position=vec4(position,0.,1.); tex=(transform*vec4(uv,0.,1.)).xy;}");
            int fs = shader(GLES20.GL_FRAGMENT_SHADER,
                    "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES video; varying vec2 tex; void main(){gl_FragColor=texture2D(video,tex);}");
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs); GLES20.glLinkProgram(program);
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
            int[] ok = new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            if (ok[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            positionLoc = GLES20.glGetAttribLocation(program,"position");
            uvLoc = GLES20.glGetAttribLocation(program,"uv");
            matrixLoc = GLES20.glGetUniformLocation(program,"transform");
            samplerLoc = GLES20.glGetUniformLocation(program,"video");
            int[] ids = new int[1]; GLES20.glGenTextures(1,ids,0); textureId = ids[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            texture = new SurfaceTexture(textureId);
            texture.setOnFrameAvailableListener(value -> {
                if (!closed && value == texture) { framePending.set(true); requestRender(); }
            }, main);
            surface = new Surface(texture);
            announce(epoch);
            Log.i("SmartTubeOES","context ready renderer=" + GLES20.glGetString(GLES20.GL_RENDERER));
        } catch (RuntimeException error) { fail(error); }
    }

    private static int shader(int kind, String source) {
        int shader = GLES20.glCreateShader(kind);
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if (ok[0] == 0) {
            String message = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    private void announce(int epoch) {
        Surface ready = surface;
        main.post(() -> {
            if (!closed && epoch == generation.get() && ready != null && ready.isValid()
                    && ready == surface && isAttachedToWindow()) output.ready(ready);
        });
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width; this.height = height;
        if (!closed) announce(generation.get());
    }

    @Override public void onDrawFrame(GL10 gl) {
        if (closed) return;
        try {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0,0,0,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (texture == null) return;
            if (framePending.getAndSet(false)) {
                texture.updateTexImage(); texture.getTransformMatrix(matrix); hasFrame = true; consumed++;
                if (consumed == 1) Log.i("SmartTubeOES","first texture frame");
            }
            if (!hasFrame || geometry == null || clip == null || width <= 0 || height <= 0) return;
            vertices.position(0); vertices.put(geometry); vertices.position(0);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glUniform1i(samplerLoc,0);
            GLES20.glUniformMatrix4fv(matrixLoc,1,false,matrix,0);
            GLES20.glVertexAttribPointer(positionLoc,2,GLES20.GL_FLOAT,false,16,vertices);
            vertices.position(2); GLES20.glVertexAttribPointer(uvLoc,2,GLES20.GL_FLOAT,false,16,vertices);
            GLES20.glEnableVertexAttribArray(positionLoc); GLES20.glEnableVertexAttribArray(uvLoc);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            for (int eye = 0; eye < 2; eye++) {
                GLES20.glViewport(eye * width / 2,0,width / 2,height);
                GLES20.glScissor(eye * width / 2 + clip[0],height-clip[3],clip[2]-clip[0],clip[3]-clip[1]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastStats >= 10000) {
                lastStats = now;
                int error = GLES20.glGetError();
                if (error != GLES20.GL_NO_ERROR) throw new IllegalStateException("GL error " + error);
                Log.i("SmartTubeOES","frames=" + consumed + " timeMs=" + now);
            }
        } catch (RuntimeException error) { fail(error); }
    }

    private void fail(RuntimeException error) {
        Log.e("SmartTubeOES","renderer failure; restoring TextureView",error);
        main.post(() -> { if (!closed) { close(); output.failed(); } });
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        generation.incrementAndGet();
        output.unavailable();
        super.surfaceDestroyed(holder);
        // Keep Java handles while context survives; onSurfaceChanged re-announces them.
    }

    void close() {
        if (closed) return;
        closed = true; generation.incrementAndGet();
        output.unavailable();
        queueEvent(() -> {
            releaseJavaHandles();
            if (textureId != 0) GLES20.glDeleteTextures(1,new int[]{textureId},0);
            if (program != 0) GLES20.glDeleteProgram(program);
            textureId = program = 0;
        });
    }

    private void releaseJavaHandles() {
        if (texture != null) texture.setOnFrameAvailableListener(null);
        if (surface != null) { surface.release(); surface = null; }
        if (texture != null) { texture.release(); texture = null; }
    }

    @Override protected void onDetachedFromWindow() {
        close();
        super.onDetachedFromWindow();
        releaseJavaHandles(); // GL thread joined; queueEvent may have been skipped on exit.
        Log.i("SmartTubeOES","detached and released");
    }
}
