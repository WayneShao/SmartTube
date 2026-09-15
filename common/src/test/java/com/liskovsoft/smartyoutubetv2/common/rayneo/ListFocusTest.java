package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ListFocusTest {
    private Activity activity;
    private StereoLayout root;
    private ListView list;
    private FocusNavigator navigation;
    private int clicks, clicked = -1;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true).get();
        root = new StereoLayout(activity);
        list = new ListView(activity);
        list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1,
                new String[]{"A", "B", "C", "D"}));
        list.setOnItemClickListener((parent, view, position, id) -> { clicked = position; clicks++; });
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        list.requestFocusFromTouch(); list.setSelection(2); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        assertTrue(root.hasWindowFocus());
        assertEquals(2, list.getSelectedItemPosition());
    }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        root.getViewTreeObserver().dispatchOnPreDraw();
    }

    @Test public void firstConfirmRestoresRowAfterTouchModeClearsSelection() {
        list.onTouchModeChanged(true);
        assertEquals(-1, list.getSelectedItemPosition());
        assertTrue(list.hasFocus());
        navigation.send(KeyEvent.KEYCODE_DPAD_CENTER);
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
        assertEquals(2, clicked);
        assertEquals(1, clicks);
        navigation.close();
    }

    @Test public void changedRowOrAdapterRejectsPendingConfirm() {
        FocusTarget pending = new FocusTarget(root);
        list.setSelection(1); layout();
        assertFalse(pending.valid(root)); pending.close();
        pending = new FocusTarget(root);
        list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new String[]{"New"}));
        assertFalse(pending.valid(root)); pending.close(); navigation.close();
    }

    @Test public void cancellationPreventsDeferredClickAfterLayout() {
        list.onTouchModeChanged(true);
        navigation.send(KeyEvent.KEYCODE_DPAD_CENTER);
        navigation.cancel();
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
        assertEquals(0, clicks); navigation.close();
    }
}
