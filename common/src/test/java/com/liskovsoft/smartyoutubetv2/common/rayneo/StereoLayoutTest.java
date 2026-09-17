package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StereoLayoutTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private StereoLayout layout(View child) {
        StereoLayout root = new StereoLayout(context);
        root.addView(child, new ViewGroup.LayoutParams(-1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        return root;
    }

    @Test public void measuresOneBusinessTreeAtEyeWidth() {
        View child = new View(context);
        StereoLayout root = layout(child);
        assertEquals(1280, root.getMeasuredWidth());
        assertEquals(640, child.getMeasuredWidth());
        assertEquals(480, child.getMeasuredHeight());
        assertEquals(1, root.getChildCount());
    }

    @Test public void drawsWholeContentInBothEyes() {
        View child = new View(context) {
            @Override protected void onDraw(Canvas canvas) {
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                canvas.drawRect(0, 0, 320, 480, paint);
                paint.setColor(Color.BLUE);
                canvas.drawRect(320, 0, 640, 480, paint);
            }
        };
        StereoLayout root = layout(child);
        Bitmap bitmap = Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        assertEquals(Color.RED, bitmap.getPixel(100, 100));
        assertEquals(Color.BLUE, bitmap.getPixel(500, 100));
        for (int x = 0; x < 640; x++) {
            assertEquals(bitmap.getPixel(x, 100), bitmap.getPixel(x + 640, 100));
        }
    }

    @Test public void bitmapReplayDrawsBusinessContentOnceAndRefreshesBothEyes() {
        int[] draws = {0}, color = {Color.RED};
        StereoLayout root = layout(new View(context) {
            @Override protected void onDraw(Canvas canvas) { draws[0]++; canvas.drawColor(color[0]); }
        });
        root.setBitmapReplay(true);
        Bitmap bitmap = Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        assertEquals(1,draws[0]);
        assertEquals(Color.RED,bitmap.getPixel(100,100));
        assertEquals(Color.RED,bitmap.getPixel(740,100));
        color[0]=Color.BLUE; root.getChildAt(0).invalidate();
        root.draw(new Canvas(bitmap));
        assertEquals(2,draws[0]);
        assertEquals(Color.BLUE,bitmap.getPixel(100,100));
        assertEquals(Color.BLUE,bitmap.getPixel(740,100));
    }

    @Test public void preservesOriginalEventAndDragOriginAcrossEyeBoundary() {
        final float[] lastX = {-1};
        View child = new View(context);
        child.setOnTouchListener((v, event) -> { lastX[0] = event.getX(); return true; });
        StereoLayout root = layout(child);
        MotionEvent down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 700, 100, 0);
        root.dispatchTouchEvent(down);
        assertEquals(60, lastX[0], 0);
        assertEquals(700, down.getX(), 0);
        MotionEvent move = MotionEvent.obtain(1, 2, MotionEvent.ACTION_MOVE, 630, 100, 0);
        root.dispatchTouchEvent(move);
        assertEquals(-10, lastX[0], 0);
        assertEquals(630, move.getX(), 0);
        down.recycle();
        move.recycle();
    }

    @Test public void rejectsUnverifiedDeviceAndDisplayShapes() {
        assertTrue(RayNeo.isSupported("RayNeo", "ARGF20", "MercuryLiteXR", 1280, 480));
        assertFalse(RayNeo.isSupported("RayNeo", "ARGF20", "MercuryLiteXR", 640, 480));
        assertFalse(RayNeo.isSupported("Other", "ARGF20", "MercuryLiteXR", 1280, 480));
        assertFalse(RayNeo.isSupported("RayNeo", "Unknown", "Unknown", 1280, 480));
    }
}
