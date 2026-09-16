package com.liskovsoft.smartyoutubetv2.common.rayneo;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class RayNeoWindowTest {
    private Activity activity;

    @Before public void setUp() {
        ShadowBuild.setManufacturer("RayNeo");
        ShadowBuild.setModel("ARGF20");
        ShadowBuild.setDevice("MercuryLiteXR");
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        shadowOf(activity.getWindowManager().getDefaultDisplay()).setRealWidth(1280);
        shadowOf(activity.getWindowManager().getDefaultDisplay()).setRealHeight(480);
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    }

    private StereoLayout content(android.view.Window window) {
        ViewGroup content = window.findViewById(android.R.id.content);
        return (StereoLayout) content.getChildAt(0);
    }

    private void measure(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
    }

    @Test public void repeatInstallationDoesNotDuplicateBusinessContent() {
        View view = new View(activity);
        view.setId(12345);
        activity.setContentView(view);
        RayNeoWindow.install(activity);
        StereoLayout first = content(activity.getWindow());
        RayNeoWindow.install(activity);
        assertSame(first, content(activity.getWindow()));
        assertSame(view, activity.findViewById(12345));
        activity.setContentView(new View(activity));
        RayNeoWindow.install(activity);
        assertNotSame(first, content(activity.getWindow()));
    }

    @Test public void dialogKeepsButtonsDismissListenerAndRightEyeOutsideCancel() {
        int[] dismisses = {0};
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Title").setMessage("Message")
                .setPositiveButton("OK", (d, which) -> {}).create();
        dialog.setOnDismissListener(d -> dismisses[0]++);
        dialog.show();
        RayNeoWindow.install(dialog);
        StereoLayout root = content(dialog.getWindow());
        measure(root);
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        assertTrue(root.getChildAt(0).getWidth() < 640);
        MotionEvent down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 641, 1, 0);
        MotionEvent up = MotionEvent.obtain(1, 20, MotionEvent.ACTION_UP, 641, 1, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
        assertEquals(1, dismisses[0]);
        down.recycle(); up.recycle();
    }

    @Test public void nonCancellableDialogDoesNotCancelOnOutsideTap() {
        AlertDialog dialog = new AlertDialog.Builder(activity).setMessage("Working").setCancelable(false).create();
        dialog.show();
        RayNeoWindow.install(dialog, false);
        StereoLayout root = content(dialog.getWindow());
        measure(root);
        MotionEvent event = MotionEvent.obtain(1, 1, MotionEvent.ACTION_UP, 1, 1, 0);
        root.dispatchTouchEvent(event);
        assertTrue(dialog.isShowing());
        event.recycle(); dialog.dismiss();
    }

    @Test public void loadingOverlayRootIsInsideStereoRatherThanDecor() {
        activity.setContentView(new FrameLayout(activity));
        RayNeoWindow.install(activity);
        assertSame(content(activity.getWindow()), RayNeoWindow.overlayRoot(activity));
        View indicator = new View(activity);
        RayNeoWindow.overlayRoot(activity).addView(indicator,
                new FrameLayout.LayoutParams(40, 40, android.view.Gravity.CENTER));
        measure(content(activity.getWindow()));
        assertEquals(300, indicator.getLeft());
        assertEquals(220, indicator.getTop());
        RayNeoWindow.overlayRoot(activity).removeView(indicator);
        assertNull(indicator.getParent());
    }

    @Test public void applicationContextNoticeUsesFocusedStereoWindow() {
        org.robolectric.android.controller.ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true);
        Activity owner = controller.get();
        shadowOf(owner.getWindowManager().getDefaultDisplay()).setRealWidth(1280);
        shadowOf(owner.getWindowManager().getDefaultDisplay()).setRealHeight(480);
        owner.setContentView(new View(owner));
        RayNeoWindow.install(owner);
        owner.getWindow().getCallback().onWindowFocusChanged(true);
        StereoLayout root = content(owner.getWindow());
        measure(root);
        RayNeoMessagePresenter.install(owner);
        MessageHelpers.showMessage(owner.getApplicationContext(), "network notice");
        assertEquals(2, root.getChildCount());
        android.widget.TextView notice = (android.widget.TextView) root.getChildAt(1);
        assertEquals("network notice", notice.getText().toString());
        MessageHelpers.cancelToasts();
        assertEquals(View.GONE, notice.getVisibility());
        MessageHelpers.setMessagePresenter(null);
        controller.pause().stop().destroy();
    }
}
