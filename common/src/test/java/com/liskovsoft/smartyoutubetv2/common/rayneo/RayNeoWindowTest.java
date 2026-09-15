package com.liskovsoft.smartyoutubetv2.common.rayneo;

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
}
