package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import com.liskovsoft.smartyoutubetv2.common.rayneo.StereoLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TooltipStereoTest {
    @Test public void tooltipUsesAnchorStereoWindowAndRemovesOnlyItsOwnOverlay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StereoLayout root = new StereoLayout(activity);
        Button anchor = new Button(activity);
        root.addView(anchor, new FrameLayout.LayoutParams(120, 80));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        anchor.requestFocusFromTouch();
        TooltipPopup popup = new TooltipPopup(activity);
        popup.show(anchor, 60, 40, false, "Account");
        assertTrue(popup.isShowing());
        assertEquals(2, root.getChildCount());
        View tooltip = root.getChildAt(1);
        assertFalse(tooltip.isFocusable());
        assertSame(anchor, root.findFocus());
        root.showMessage("Independent notice");
        popup.hide();
        assertFalse(popup.isShowing());
        assertEquals(2, root.getChildCount());
        assertNotSame(tooltip, root.getChildAt(1));
        popup.show(anchor, 60, 40, false, "Again");
        popup.hide();
        assertEquals(2, root.getChildCount());
    }
}
