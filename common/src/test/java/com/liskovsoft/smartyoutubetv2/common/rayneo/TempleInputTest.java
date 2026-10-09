package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TempleInputTest {
    private final List<Integer> keys = new ArrayList<>();
    private final TempleInput input = new TempleInput(RuntimeEnvironment.getApplication(), keys::add,
            () -> {});

    private void event(long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        input.handleTouchpad(event);
        event.recycle();
    }

    private void pointers(long time, int action, int[] ids, float... xs) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = xs[i]; coords[i].y = 100;
            coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent e = MotionEvent.obtain(1,time,action,ids.length,properties,coords,0,0,1,1,0,0,
                android.view.InputDevice.SOURCE_TOUCHPAD,0);
        input.handleTouchpad(e); e.recycle();
    }

    private void beginPair() {
        pointers(1,MotionEvent.ACTION_DOWN,new int[]{4},100);
        pointers(30,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{4,9},100,200);
    }

    @Test public void twoFingerTapWaitsForBothReleasesAndEmitsOneMenu() {
        beginPair();
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        assertTrue(keys.isEmpty());
        pointers(100,MotionEvent.ACTION_UP,new int[]{9},200);
        pointers(101,MotionEvent.ACTION_BUTTON_RELEASE,new int[]{9},200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_MENU),keys);
    }

    @Test public void pointerOrderAndReverseLiftOrderDoNotChangeMenu() {
        beginPair();
        pointers(60,MotionEvent.ACTION_MOVE,new int[]{9,4},201,101);
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{9,4},201,101);
        pointers(90,MotionEvent.ACTION_UP,new int[]{4},101);
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_MENU),keys);
    }

    @Test public void twoFingerSwipeAndThirdFingerAreSuppressed() {
        beginPair();
        pointers(50,MotionEvent.ACTION_MOVE,new int[]{4,9},100,250);
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(90,MotionEvent.ACTION_UP,new int[]{9},200);
        assertTrue(keys.isEmpty()); input.cancel();
        beginPair();
        pointers(50,MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{4,9,12},100,200,300);
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(90,MotionEvent.ACTION_UP,new int[]{9},200);
        assertTrue(keys.isEmpty());
    }

    @Test public void twoFingerCancelWindowLossAndLongHoldDoNotEmit() {
        beginPair(); pointers(50,MotionEvent.ACTION_CANCEL,new int[]{4,9},100,200);
        pointers(90,MotionEvent.ACTION_UP,new int[]{9},200);
        assertTrue(keys.isEmpty()); input.cancel();
        beginPair(); input.cancel();
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(90,MotionEvent.ACTION_UP,new int[]{9},200);
        assertTrue(keys.isEmpty()); input.cancel();
        beginPair(); pointers(800,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(900,MotionEvent.ACTION_UP,new int[]{9},200);
        assertTrue(keys.isEmpty());
    }

    @Test public void pairCancelsPendingSingleTapAndNextSingleTapStillWorks() {
        event(1,1,MotionEvent.ACTION_DOWN,100,100);
        event(1,20,MotionEvent.ACTION_UP,100,100);
        pointers(100,MotionEvent.ACTION_DOWN,new int[]{4},100);
        pointers(130,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{4,9},100,200);
        pointers(170,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(190,MotionEvent.ACTION_UP,new int[]{9},200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_MENU),keys);
        event(1000,1000,MotionEvent.ACTION_DOWN,100,100);
        event(1000,1050,MotionEvent.ACTION_UP,100,100);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Arrays.asList(KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_DPAD_CENTER),keys);
    }

    @Test public void hoverNavigationResumesAfterTwoFingerMenu() {
        beginPair();
        pointers(70,MotionEvent.ACTION_POINTER_UP,new int[]{4,9},100,200);
        pointers(90,MotionEvent.ACTION_UP,new int[]{9},200);
        event(0,100,MotionEvent.ACTION_HOVER_MOVE,200,100);
        event(0,110,MotionEvent.ACTION_HOVER_EXIT,200,100);
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_MENU),keys);
        event(0,200,MotionEvent.ACTION_HOVER_ENTER,100,100);
        event(0,220,MotionEvent.ACTION_HOVER_MOVE,200,100);
        event(0,250,MotionEvent.ACTION_HOVER_EXIT,200,100);
        assertEquals(java.util.Arrays.asList(KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_DPAD_RIGHT),keys);
    }

    @Test public void oneTapConfirmsOnlyOnceAfterDoubleTapWindow() {
        event(1, 1, MotionEvent.ACTION_DOWN, 100, 100);
        event(1, 50, MotionEvent.ACTION_UP, 100, 100);
        assertTrue(keys.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_DPAD_CENTER), keys);
    }

    @Test public void doubleTapReturnsWithoutConfirmingUnderlyingItem() {
        event(1, 1, MotionEvent.ACTION_DOWN, 100, 100);
        event(1, 50, MotionEvent.ACTION_UP, 100, 100);
        event(150, 150, MotionEvent.ACTION_DOWN, 100, 100);
        event(150, 190, MotionEvent.ACTION_UP, 100, 100);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_BACK), keys);
    }

    @Test public void cancelledWindowDoesNotConfirmLater() {
        event(1, 1, MotionEvent.ACTION_DOWN, 100, 100);
        event(1, 50, MotionEvent.ACTION_UP, 100, 100);
        input.cancel();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertTrue(keys.isEmpty());
    }

    @Test public void swipePreservesDeliveredDirectionAndDoesNotBecomeTap() {
        event(1, 1, MotionEvent.ACTION_DOWN, 200, 100);
        event(1, 90, MotionEvent.ACTION_MOVE, 100, 100);
        event(1, 110, MotionEvent.ACTION_UP, 100, 100);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_DPAD_LEFT), keys);
    }

    @Test public void longPressOpensMenuWithoutConfirming() {
        event(1, 1, MotionEvent.ACTION_DOWN, 100, 100);
        event(1, 900, MotionEvent.ACTION_UP, 100, 100);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_MENU), keys);
    }

    @Test public void orphanReleaseAfterCancellationDoesNothing() {
        event(1, 1, MotionEvent.ACTION_DOWN, 100, 100);
        input.cancel();
        event(1, 50, MotionEvent.ACTION_UP, 100, 100);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertTrue(keys.isEmpty());
    }

    @Test public void hoverSessionMovesSelectionOnExitWithoutMouseMotion() {
        event(0, 1, MotionEvent.ACTION_HOVER_MOVE, 100, 100);
        event(0, 2, MotionEvent.ACTION_HOVER_MOVE, 120, 80);
        assertTrue(keys.isEmpty());
        event(0, 3, MotionEvent.ACTION_HOVER_EXIT, 0, 0);
        assertEquals(java.util.Collections.singletonList(KeyEvent.KEYCODE_DPAD_RIGHT), keys);
        input.cancel();
        event(0, 3, MotionEvent.ACTION_HOVER_MOVE, 300, 300);
    }

    @Test public void fourDirectionsMoveSelectionOncePerSwipe() {
        float[][] ends = {{100, 200}, {300, 200}, {200, 100}, {200, 300}};
        for (int i = 0; i < ends.length; i++) {
            long start = 1000 + i * 1000;
            event(start, start, MotionEvent.ACTION_DOWN, 200, 200);
            event(start, start + 50, MotionEvent.ACTION_MOVE, ends[i][0], ends[i][1]);
            assertEquals(i, keys.size());
            event(start, start + 90, MotionEvent.ACTION_UP, ends[i][0], ends[i][1]);
        }
        assertEquals(java.util.Arrays.asList(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN), keys);
    }
}
