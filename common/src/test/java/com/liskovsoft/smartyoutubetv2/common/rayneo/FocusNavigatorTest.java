package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class FocusNavigatorTest {
    @Test public void fourSwipesMoveActualSelectedControlAndConfirmClicksSelection() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StereoLayout root = new StereoLayout(activity);
        FrameLayout panel = new FrameLayout(activity);
        Button[] buttons = new Button[4];
        int[] clicks = new int[4];
        for (int i = 0; i < 4; i++) {
            final int index = i;
            buttons[i] = new Button(activity);
            buttons[i].setId(100 + i);
            buttons[i].setText("Item " + i);
            buttons[i].setOnClickListener(v -> clicks[index]++);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(180, 100);
            params.leftMargin = (i % 2) * 250;
            params.topMargin = (i / 2) * 150;
            panel.addView(buttons[i], params);
        }
        buttons[0].setNextFocusRightId(101); buttons[1].setNextFocusDownId(103);
        buttons[3].setNextFocusLeftId(102); buttons[2].setNextFocusUpId(100);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        buttons[0].requestFocusFromTouch();
        FocusNavigator navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        TempleInput input = new TempleInput(activity, navigation::send, navigation::prepare);
        float[][] ends = {{300, 200}, {200, 300}, {100, 200}, {200, 100}};
        int[] expected = {1, 3, 2, 0};
        for (int i = 0; i < 4; i++) {
            long time = 1000 + i * 1000;
            MotionEvent down = MotionEvent.obtain(time, time, 0, 200, 200, 0);
            MotionEvent up = MotionEvent.obtain(time, time + 90, 1, ends[i][0], ends[i][1], 0);
            input.handleTouchpad(down); input.handleTouchpad(up);
            down.recycle(); up.recycle();
            assertSame("Swipe " + i, buttons[expected[i]], root.findFocus());
        }
        navigation.send(KeyEvent.KEYCODE_DPAD_CENTER);
        assertArrayEquals(new int[]{1, 0, 0, 0}, clicks);
        navigation.close();
    }

    @Test public void delayedConfirmRejectsChangedSelectionOrReplacedContent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StereoLayout root = new StereoLayout(activity);
        FrameLayout panel = new FrameLayout(activity);
        Button a = new Button(activity), b = new Button(activity);
        panel.addView(a, new FrameLayout.LayoutParams(200, 100));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(200, 100);
        params.leftMargin = 250;
        panel.addView(b, params); root.addView(panel);
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        a.requestFocusFromTouch();
        FocusTarget pending = new FocusTarget(root);
        b.requestFocus();
        assertFalse(pending.valid(root)); pending.close();
        pending = new FocusTarget(root);
        panel.removeView(b);
        assertFalse(pending.valid(root)); pending.close();
    }
}
