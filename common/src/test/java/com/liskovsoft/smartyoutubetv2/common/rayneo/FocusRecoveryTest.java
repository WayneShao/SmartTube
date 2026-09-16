package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import org.junit.After;
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
public class FocusRecoveryTest {
    private Activity activity;
    private StereoLayout root;
    private FrameLayout area;
    private FocusNavigator navigation;
    private int directions;
    private org.robolectric.android.controller.ActivityController<Activity> controller;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true);
        activity = controller.get();
        root = new StereoLayout(activity);
        root.setFocusableInTouchMode(true);
        area = new FrameLayout(activity);
        root.addView(area, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
    }

    @After public void cleanup() { if (navigation != null) navigation.close(); }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        root.getViewTreeObserver().dispatchOnGlobalLayout();
        root.getViewTreeObserver().dispatchOnPreDraw();
    }

    private Button button(FrameLayout parent) {
        Button button = new Button(activity);
        parent.addView(button, new FrameLayout.LayoutParams(180, 70));
        return button;
    }

    private ListView list(FrameLayout parent, Items items, int selected) {
        ListView list = new ListView(activity);
        list.setAdapter(items);
        parent.addView(list, new FrameLayout.LayoutParams(300, 350));
        list.setSelection(selected);
        return list;
    }

    private final class Items extends BaseAdapter {
        long[] ids = {10, 20, 30, 40};
        int disabled = -1;
        boolean stable = true;
        @Override public int getCount() { return ids.length; }
        @Override public Object getItem(int p) { return ids[p]; }
        @Override public long getItemId(int p) { return ids[p]; }
        @Override public boolean hasStableIds() { return stable; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { return p != disabled; }
        @Override public View getView(int p, View old, ViewGroup parent) {
            TextView view = old instanceof TextView ? (TextView) old : new TextView(activity);
            view.setText("Item " + ids[p]); view.setMinHeight(60); return view;
        }
    }

    @Test public void consumedDirectionThatLosesFocusRecoversLiveSelectionWithoutReplay() {
        Button old = button(area);
        ListView[] replacement = {null};
        layout(); old.requestFocusFromTouch();
        navigation = new FocusNavigator(root, event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                directions++;
                area.removeView(old);
                replacement[0] = list(area, new Items(), 2);
                root.requestFocus();
            }
            return true;
        });
        navigation.send(KeyEvent.KEYCODE_DPAD_RIGHT);
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
        assertSame(replacement[0], root.findFocus());
        assertEquals(2, replacement[0].getSelectedItemPosition());
        assertEquals(1, directions);
    }

    @Test public void layoutReplacementRecoversInSurvivingContentAreaNotSidebar() {
        area.removeAllViews();
        Button sidebar = button(area);
        FrameLayout content = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(350, 400);
        params.leftMargin = 230; area.addView(content, params);
        Button old = button(content);
        layout(); old.requestFocusFromTouch();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        content.removeView(old);
        Button replacement = button(content);
        root.requestFocus();
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); layout();
        assertSame(replacement, root.findFocus());
        assertNotSame(sidebar, root.findFocus());
    }

    @Test public void stableIdFindsMovedItemWhenTouchModeClearsRow() {
        Items items = new Items();
        ListView list = list(area, items, 2);
        layout(); list.requestFocusFromTouch(); list.setSelection(2); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        list.onTouchModeChanged(true);
        items.ids = new long[]{40, 10, 20, 30};
        items.notifyDataSetChanged();
        navigation.prepare();
        assertEquals(3, list.getSelectedItemPosition());
        assertEquals(30, list.getSelectedItemId());
    }

    @Test public void liveSelectionWinsOverRememberedRowAndDisabledRowsAreRejected() {
        Items items = new Items();
        ListView list = list(area, items, 2);
        layout(); list.requestFocusFromTouch(); list.setSelection(2); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        list.setSelection(1);
        assertSame(list, navigation.prepare());
        assertEquals(1, list.getSelectedItemPosition());
        items.disabled = 1;
        navigation.prepare();
        assertNotEquals(1, list.getSelectedItemPosition());
    }

    @Test public void handledBoundaryKeepsFocusAndDoesNotRepeatDirection() {
        Button selected = button(area);
        layout(); selected.requestFocusFromTouch();
        navigation = new FocusNavigator(root, event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) directions++;
            return true;
        });
        navigation.send(KeyEvent.KEYCODE_DPAD_LEFT);
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600)); layout();
        assertSame(selected, root.findFocus()); assertEquals(1, directions);
    }

    @Test public void emptyContentRecoversWhenItemsArriveButClosedNavigatorDoesNotStealFocus() {
        Button old = button(area);
        layout(); old.requestFocusFromTouch();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        area.removeAllViews(); root.requestFocus(); layout();
        Button loaded = button(area);
        layout();
        assertSame(loaded, root.findFocus());
        navigation.close();
        area.removeAllViews(); button(area); root.requestFocus(); layout();
        assertSame(root, root.findFocus());
    }

    @Test public void logicalRowMovementDoesNotAlsoRunUnhandledFocusTraversal() {
        Button outside = button(area);
        int[] searches = {0};
        ListView list = new ListView(activity) {
            @Override public View focusSearch(int direction) { searches[0]++; return outside; }
        };
        list.setAdapter(new Items());
        area.addView(list, new FrameLayout.LayoutParams(300, 350));
        layout(); list.requestFocusFromTouch(); list.setSelection(1); layout();
        navigation = new FocusNavigator(root, event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) { directions++; list.setSelection(2); }
            return false;
        });
        navigation.send(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(2, list.getSelectedItemPosition());
        assertSame(list, root.findFocus());
        assertEquals(0, searches[0]); assertEquals(1, directions);
    }

    @Test public void nonStableDataChangeDoesNotReuseObsoleteRow() {
        Items items = new Items(); items.stable = false;
        ListView list = new ListView(activity); list.setAdapter(items);
        area.addView(list, new FrameLayout.LayoutParams(300, 350));
        layout(); list.requestFocusFromTouch(); list.setSelection(2); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        list.onTouchModeChanged(true);
        items.ids = new long[]{50, 60, 70, 80}; items.notifyDataSetChanged();
        assertEquals(-1, list.getSelectedItemPosition());
        navigation.prepare();
        assertEquals(0, list.getSelectedItemPosition());
    }

    @Test public void lostWindowOwnershipCancelsQueuedRecovery() {
        Button old = button(area);
        layout(); old.requestFocusFromTouch();
        navigation = new FocusNavigator(root, event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                area.removeAllViews(); button(area); root.requestFocus();
            }
            return true;
        });
        navigation.send(KeyEvent.KEYCODE_DPAD_RIGHT);
        controller.windowFocusChanged(false);
        root.requestFocus();
        assertFalse(root.hasWindowFocus());
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)); layout();
        assertSame(root, root.findFocus());
    }

    @Test public void recoveredListCanMoveAndConfirmExactlyOnce() {
        Items items = new Items();
        ListView list = list(area, items, 1);
        layout(); list.requestFocusFromTouch(); list.setSelection(1); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        list.onTouchModeChanged(true); root.requestFocus(); layout();
        assertSame(list, root.findFocus());
        int[] clicks = {0}, position = {-1};
        list.setOnItemClickListener((parent, view, p, id) -> { clicks[0]++; position[0] = p; });
        navigation.send(KeyEvent.KEYCODE_DPAD_DOWN);
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
        assertEquals(2, list.getSelectedItemPosition());
        navigation.send(KeyEvent.KEYCODE_DPAD_CENTER);
        layout(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); layout();
        assertEquals(1, clicks[0]); assertEquals(2, position[0]);
    }

    @Test public void replacingWholeListKeepsItsSurvivingContentRegion() {
        ListView sidebar = list(area, new Items(), 0);
        FrameLayout content = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(300, 400);
        params.leftMargin = 330; area.addView(content, params);
        ListView old = list(content, new Items(), 2);
        layout(); old.requestFocusFromTouch(); old.setSelection(2); layout();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        content.removeView(old);
        ListView replacement = list(content, new Items(), 1);
        root.requestFocus(); layout();
        assertSame(replacement, root.findFocus());
        assertNotSame(sidebar, root.findFocus());
        assertEquals(1, replacement.getSelectedItemPosition());
    }
}
