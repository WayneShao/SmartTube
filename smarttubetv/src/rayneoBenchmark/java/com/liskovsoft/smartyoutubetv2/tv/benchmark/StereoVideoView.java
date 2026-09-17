// Experimental renderer port from WayneShao/moonlight-android d01a5a8b. Benchmark only.
package com.liskovsoft.smartyoutubetv2.tv.benchmark;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Single decoder -> one OES texture update -> two viewports -> one EGL swap. */
public final class StereoVideoView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG="RayNeoVideo";
    private final Activity activity;
    private final SurfaceHolder.Callback callback;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final int videoWidth,videoHeight;
    private final boolean stretch;
    private final AtomicBoolean frameAvailable=new AtomicBoolean();
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] transform=new float[16];
    private volatile boolean closed;
    private boolean announced, hadContext, hasFrame;
    private boolean loggedStereo;
    private SurfaceTexture texture;
    private Surface decoderSurface;
    private DecoderSurface decoderHolder;
    private int program,textureId,positionLoc,texCoordLoc,matrixLoc,samplerLoc;
    private int[] eyeRect;
    private int outputWidth,outputHeight;
    private int sourceWidth,sourceHeight;
    private volatile long frames;
    private long statsTime;

    public StereoVideoView(Activity activity,int width,int height,boolean stretch,SurfaceHolder.Callback callback) {
        super(activity);
        this.activity=activity; this.callback=callback;
        videoWidth=width; videoHeight=height; this.stretch=stretch;
        sourceWidth=width; sourceHeight=height;
        // Bottom-left UV convention; SurfaceTexture's producer matrix includes crop/flip.
        vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}).position(0);
        setEGLContextClientVersion(2);
        setEGLContextFactory(new EGLContextFactory() {
            @Override public javax.microedition.khronos.egl.EGLContext createContext(
                    javax.microedition.khronos.egl.EGL10 egl,
                    javax.microedition.khronos.egl.EGLDisplay display,EGLConfig config) {
                Log.i(TAG,"egl.context.create begin version=2");
                javax.microedition.khronos.egl.EGLContext result=egl.eglCreateContext(display,config,
                        javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT,
                        new int[]{0x3098,2,javax.microedition.khronos.egl.EGL10.EGL_NONE});
                Log.i(TAG,"egl.context.create end error=0x"+Integer.toHexString(egl.eglGetError()));
                return result;
            }
            @Override public void destroyContext(javax.microedition.khronos.egl.EGL10 egl,
                    javax.microedition.khronos.egl.EGLDisplay display,javax.microedition.khronos.egl.EGLContext context) {
                Log.i(TAG,"egl.context.destroy begin");
                boolean ok=egl.eglDestroyContext(display,context);
                Log.i(TAG,"egl.context.destroy end ok="+ok+" error=0x"+Integer.toHexString(egl.eglGetError()));
            }
        });
        setEGLConfigChooser(8,8,8,0,0,0);
        setEGLWindowSurfaceFactory(new EGLWindowSurfaceFactory() {
            @Override public javax.microedition.khronos.egl.EGLSurface createWindowSurface(
                    javax.microedition.khronos.egl.EGL10 egl,
                    javax.microedition.khronos.egl.EGLDisplay display,EGLConfig config,Object window) {
                Log.i(TAG,"egl.window.create begin");
                javax.microedition.khronos.egl.EGLSurface result=egl.eglCreateWindowSurface(display,config,window,null);
                Log.i(TAG,"egl.window.create end error=0x"+Integer.toHexString(egl.eglGetError()));
                return result;
            }
            @Override public void destroySurface(javax.microedition.khronos.egl.EGL10 egl,
                    javax.microedition.khronos.egl.EGLDisplay display,javax.microedition.khronos.egl.EGLSurface surface) {
                Log.i(TAG,"egl.window.destroy begin");
                boolean ok=egl.eglDestroySurface(display,surface);
                Log.i(TAG,"egl.window.destroy end ok="+ok+" error=0x"+Integer.toHexString(egl.eglGetError()));
            }
        });
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Log.i(TAG,"renderer.create stream="+width+"x"+height+" stretch="+stretch+" mode=when-dirty SDR");
    }
    public SurfaceHolder decoderHolder() { return decoderHolder; }
    public void updateVideoSize(int width,int height) {
        if(closed || width<=0 || height<=0) return;
        queueEvent(()->{
            if(closed) return;
            sourceWidth=width; sourceHeight=height;
            eyeRect=stretch?new int[]{0,0,outputWidth/2,outputHeight}
                    :fit(width,height,outputWidth/2,outputHeight);
            Log.i(TAG,"video.size width="+width+" height="+height);
            requestRender();
        });
    }
    private static int[] fit(int w, int h, int ew, int eh) {
        float scale=Math.min((float)ew/w,(float)eh/h);
        int width=Math.round(w*scale), height=Math.round(h*scale);
        return new int[]{(ew-width)/2,(eh-height)/2,width,height};
    }
    private static void check(String stage) {
        int error=GLES20.glGetError();
        if(error!=GLES20.GL_NO_ERROR) throw new IllegalStateException(stage+" GL error=0x"+Integer.toHexString(error));
    }
    private static int shader(int type,String source) {
        int shader=GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] ok=new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException("shader: "+GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        Log.i(TAG,"egl.created vendor="+GLES20.glGetString(GLES20.GL_VENDOR)
                +" renderer="+GLES20.glGetString(GLES20.GL_RENDERER));
        if(closed) return;
        if(hadContext) { fail("EGL context recreated; ending stream instead of binding a stale decoder",null); return; }
        hadContext=true;
        try {
            Log.i(TAG,"gl.version="+GLES20.glGetString(GLES20.GL_VERSION)
                    +" extensions="+GLES20.glGetString(GLES20.GL_EXTENSIONS));
            Log.i(TAG,"shader.compile begin");
            int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 position; attribute vec2 uv; uniform mat4 transform; varying vec2 tex; void main(){gl_Position=vec4(position,0.,1.); tex=(transform*vec4(uv,0.,1.)).xy;}");
            int fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES video; varying vec2 tex; void main(){gl_FragColor=texture2D(video,tex);}");
            program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs);
            GLES20.glLinkProgram(program);
            int[] ok=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0) throw new IllegalStateException("link: "+GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
            positionLoc=GLES20.glGetAttribLocation(program,"position");
            texCoordLoc=GLES20.glGetAttribLocation(program,"uv");
            matrixLoc=GLES20.glGetUniformLocation(program,"transform");
            samplerLoc=GLES20.glGetUniformLocation(program,"video");
            Log.i(TAG,"shader.link ready program="+program);
            int[] ids=new int[1]; GLES20.glGenTextures(1,ids,0); textureId=ids[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            texture=new SurfaceTexture(textureId);
            texture.setDefaultBufferSize(videoWidth,videoHeight);
            texture.setOnFrameAvailableListener(st->{
                if(!closed) {frameAvailable.set(true); requestRender();}
            });
            decoderSurface=new Surface(texture);
            DecoderSurface created=new DecoderSurface(decoderSurface,videoWidth,videoHeight);
            check("oes.create");
            Log.i(TAG,"oes.ready decoder-buffer="+videoWidth+"x"+videoHeight);
            main.post(()->{
                if(closed || activity.isFinishing() || activity.isDestroyed()) return;
                decoderHolder=created;
                Log.i(TAG,"decoder.surfaceCreated begin");
                callback.surfaceCreated(created); announced=true;
                Log.i(TAG,"decoder.surfaceChanged bind begin");
                callback.surfaceChanged(created,android.graphics.PixelFormat.RGBA_8888,videoWidth,videoHeight);
                Log.i(TAG,"decoder.surfaceChanged bind end");
            });
        } catch(RuntimeException e) {fail("egl/oes initialization",e);}
    }
    @Override public void onSurfaceChanged(GL10 gl,int width,int height) {
        outputWidth=width; outputHeight=height;
        int[] fit=fit(sourceWidth,sourceHeight,width/2,height);
        eyeRect=stretch?new int[]{0,0,width/2,height}:fit;
        Log.i(TAG,"output.changed physical="+width+"x"+height+" eyeRect="
                +fit[0]+","+fit[1]+","+fit[2]+","+fit[3]);
    }
    @Override public void onDrawFrame(GL10 gl) {
        if(closed) return;
        try {
            GLES20.glClearColor(0,0,0,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if(texture==null) return;
            if(frameAvailable.getAndSet(false)) {
                texture.updateTexImage(); texture.getTransformMatrix(transform); hasFrame=true;
                frames++;
                if(frames==1) Log.i(TAG,"video.first-frame timestampNs="+texture.getTimestamp());
            }
            if(!hasFrame) return;
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glUniform1i(samplerLoc,0);
            GLES20.glUniformMatrix4fv(matrixLoc,1,false,transform,0);
            vertices.position(0); GLES20.glVertexAttribPointer(positionLoc,2,GLES20.GL_FLOAT,false,16,vertices);
            vertices.position(2); GLES20.glVertexAttribPointer(texCoordLoc,2,GLES20.GL_FLOAT,false,16,vertices);
            GLES20.glEnableVertexAttribArray(positionLoc); GLES20.glEnableVertexAttribArray(texCoordLoc);
            int eye=outputWidth/2;
            int[] rect=eyeRect;
            if(rect==null) return;
            for(int i=0;i<2;i++) {
                GLES20.glViewport(i*eye+rect[0],rect[1],rect[2],rect[3]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
            if(!loggedStereo) { loggedStereo=true; Log.i(TAG,"video.first-stereo-commands-recorded"); }
            long now=SystemClock.elapsedRealtime();
            if(now-statsTime>=10000) {
                check("draw.stereo"); statsTime=now;
                Log.i(TAG,"video.stats frames="+frames+" timeMs="+now);
            }
        } catch(RuntimeException e) {fail("video.frame",e);}
    }
    private void fail(String stage,Throwable cause) {
        Log.e(TAG,"renderer.failure stage="+stage,cause);
        main.post(()->{close(); if(!activity.isFinishing()) activity.finish();});
    }
    /** UI thread: original decoder receives destruction before its Surface is released. */
    public void close() {
        if(closed) return;
        closed=true;
        Log.i(TAG,"renderer.close announced="+announced+" frames="+frames);
        if(announced) {callback.surfaceDestroyed(decoderHolder); announced=false;}
        queueEvent(()->{
            releaseJavaHandles();
            if(textureId!=0) GLES20.glDeleteTextures(1,new int[]{textureId},0);
            if(program!=0) GLES20.glDeleteProgram(program);
            Log.i(TAG,"renderer.resources-released");
        });
    }
    private void releaseJavaHandles() {
        if(texture!=null) texture.setOnFrameAvailableListener(null);
        if(decoderSurface!=null) {decoderSurface.release(); decoderSurface=null;}
        if(texture!=null) {texture.release(); texture=null;}
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        close();
        super.surfaceDestroyed(holder);
        // Upstream Game intentionally ends a stopped stream; never reconnect an old decoder.
        if(!activity.isFinishing()) activity.finish();
    }
    @Override protected void onDetachedFromWindow() {
        close();
        super.onDetachedFromWindow(); // Joins GL thread, which can skip queued work when exiting.
        releaseJavaHandles(); // Post-join, idempotent fallback; never races updateTexImage().
        Log.i(TAG,"renderer.detached handles-released");
    }
}
