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
    private final List<Float> moves = new ArrayList<>();
    private final TempleInput input = new TempleInput(RuntimeEnvironment.getApplication(), keys::add,
            (dx, dy) -> { moves.add(dx); moves.add(dy); });

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
        assertTrue(keys.isEmpty());
        assertEquals(java.util.Arrays.asList(-100f, 0f), moves);
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

    @Test public void hoverMotionTracksBothAxesWithoutClick() {
        event(0, 1, MotionEvent.ACTION_HOVER_MOVE, 100, 100);
        event(0, 2, MotionEvent.ACTION_HOVER_MOVE, 120, 80);
        assertEquals(java.util.Arrays.asList(20f, -20f), moves);
        assertTrue(keys.isEmpty());
        input.cancel();
        event(0, 3, MotionEvent.ACTION_HOVER_MOVE, 300, 300);
        assertEquals(2, moves.size());
    }
}
