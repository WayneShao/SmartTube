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
