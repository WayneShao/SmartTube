package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CursorInteractionTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private StereoLayout layout(View child) {
        StereoLayout root = new StereoLayout(context);
        root.addView(child, new FrameLayout.LayoutParams(-1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        return root;
    }

    @Test public void deferredTargetKeepsCoordinatesWhileCursorMoves() {
        StereoLayout root = layout(new View(context));
        CursorTarget tap = new CursorTarget(root);
        root.cursor().move(100, 100);
        assertEquals(320, tap.x, 0);
        assertEquals(240, tap.y, 0);
        assertTrue(tap.valid(root));
        tap.close();
    }

    @Test public void replacementAndReboundTextCancelPendingClick() {
        FrameLayout panel = new FrameLayout(context);
        TextView item = new TextView(context);
        item.setText("first");
        panel.addView(item, new FrameLayout.LayoutParams(-1, -1));
        StereoLayout root = layout(panel);
        CursorTarget tap = new CursorTarget(root);
        item.setText("replacement");
        assertFalse(tap.valid(root));
        tap.close();
        tap = new CursorTarget(root);
        panel.removeAllViews();
        assertFalse(tap.valid(root));
        tap.close();
    }

    @Test public void longPressHitsPointerCardRatherThanOldFocus() {
        FrameLayout panel = new FrameLayout(context);
        View left = new View(context), right = new View(context);
        left.setFocusable(true);
        right.setFocusable(true);
        int[] clicks = {0, 0};
        left.setOnLongClickListener(v -> { clicks[0]++; return true; });
        right.setOnLongClickListener(v -> { clicks[1]++; return true; });
        panel.addView(left, new FrameLayout.LayoutParams(300, -1));
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(300, -1);
        rightParams.leftMargin = 320;
        panel.addView(right, rightParams);
        StereoLayout root = layout(panel);
        left.requestFocus();
        root.cursor().move(100, 0);
        CursorTarget tap = new CursorTarget(root);
        assertTrue(tap.longClick());
        assertArrayEquals(new int[]{0, 1}, clicks);
        tap.close();
    }

    @Test public void continuedOutwardSwipeScrollsOffscreenContentIntoReach() {
        ScrollView scroll = new ScrollView(context);
        FrameLayout content = new FrameLayout(context);
        content.setMinimumHeight(1400); // ScrollView measures its child with an unspecified height.
        View laterItem = new View(context);
        FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(-1, 100);
        itemParams.topMargin = 600;
        content.addView(laterItem, itemParams);
        scroll.addView(content, new ScrollView.LayoutParams(-1, 1400));
        StereoLayout root = layout(scroll);
        root.cursor().move(0, 1000);
        CursorScroll.move(root, 0, 300);
        assertEquals(300, scroll.getScrollY());
        root.cursor().move(0, -129);
        assertSame(laterItem, CursorTarget.hit(root, root.cursor().x(), root.cursor().y()));
    }
}
