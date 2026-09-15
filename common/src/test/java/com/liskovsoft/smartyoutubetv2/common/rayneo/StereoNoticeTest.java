package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StereoNoticeTest {
    private Activity activity;
    private StereoLayout root;
    private Button selected;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new StereoLayout(activity);
        selected = new Button(activity);
        selected.setBackgroundColor(Color.RED);
        root.addView(selected, new android.widget.FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        layout(); selected.requestFocusFromTouch();
    }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
    }

    @Test public void noticeIsRenderedInBothEyesWithoutTakingFocus() {
        root.showMessage("Press again to exit"); layout();
        TextView notice = (TextView) root.getChildAt(1);
        assertFalse(notice.isFocusable());
        assertSame(selected, root.findFocus());
        assertTrue("Notice must remain above focused content", notice.getZ() >= selected.getZ());
        Bitmap frame = Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(frame));
        int[] left = new int[640 * 480], right = new int[640 * 480];
        frame.getPixels(left, 0, 640, 0, 0, 640, 480);
        frame.getPixels(right, 0, 640, 640, 0, 640, 480);
        assertArrayEquals(left, right);
        assertNotEquals("Notice bounds: " + notice.getLeft() + "," + notice.getTop() + ","
                + notice.getRight() + "," + notice.getBottom(), Color.RED, frame.getPixel(320, 440));
    }

    @Test public void replacingNoticeResetsItsTimeoutWithoutAddingAnotherView() {
        root.showMessage("first");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        root.showMessage("second");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals(2, root.getChildCount());
        TextView notice = (TextView) root.getChildAt(1);
        assertEquals("second", notice.getText().toString());
        assertEquals(View.VISIBLE, notice.getVisibility());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertEquals(View.GONE, notice.getVisibility());
        assertSame(selected, root.findFocus());
    }

    @Test public void detachingWindowCancelsNotice() {
        root.showMessage("exit");
        TextView notice = (TextView) root.getChildAt(1);
        activity.setContentView(new View(activity));
        assertEquals(View.GONE, notice.getVisibility());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));
        assertEquals(View.GONE, notice.getVisibility());
    }
}
