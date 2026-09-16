package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class GridFocusRecoveryTest {
    private Activity activity;
    private StereoLayout root;
    private FocusNavigator navigation;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true).get();
        root = new StereoLayout(activity); root.setFocusableInTouchMode(true);
        activity.setContentView(root);
    }
    @After public void cleanup() { if (navigation != null) navigation.close(); }

    private final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return 20; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            Button button = new Button(activity);
            RecyclerView.LayoutParams params = ((RecyclerView) parent).getLayoutManager().generateDefaultLayoutParams();
            params.width = 100; params.height = 60;
            button.setLayoutParams(params);
            return new RecyclerView.ViewHolder(button) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((Button) holder.itemView).setText("Card " + position);
        }
    }

    private VerticalGridView grid() {
        VerticalGridView grid = new VerticalGridView(activity);
        grid.setAdapter(new Cards());
        root.addView(grid, new FrameLayout.LayoutParams(400, 300));
        layout();
        return grid;
    }

    private void layout() {
        for (int i = 0; i < 3; i++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1280, 480);
            root.getViewTreeObserver().dispatchOnGlobalLayout();
            root.getViewTreeObserver().dispatchOnPreDraw();
        }
    }

    @Test public void currentGridSelectionSurvivesLostLeafFocus() {
        VerticalGridView grid = grid();
        grid.setSelectedPosition(2); layout();
        grid.findViewHolderForAdapterPosition(2).itemView.requestFocusFromTouch();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        root.requestFocus(); layout();
        assertSame(grid.findViewHolderForAdapterPosition(2).itemView, root.findFocus());
    }

    @Test public void pendingOffscreenSelectionWaitsForItsOwnHolder() {
        VerticalGridView grid = grid();
        grid.findViewHolderForAdapterPosition(0).itemView.requestFocusFromTouch();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        root.requestFocus(); grid.setSelectedPosition(15);
        assertNull(grid.findViewHolderForAdapterPosition(15));
        assertNull(navigation.prepare());
        assertSame(root, root.findFocus());
        layout();
        assertSame(grid.findViewHolderForAdapterPosition(15).itemView, root.findFocus());
    }

    @Test public void blockedGridIsNotForcedToRegainFocusDuringHeaderTransition() {
        VerticalGridView grid = grid();
        grid.findViewHolderForAdapterPosition(0).itemView.requestFocusFromTouch();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        grid.setFocusSearchDisabled(true); root.requestFocus(); layout();
        assertNull(navigation.prepare()); assertSame(root, root.findFocus());
        grid.setFocusSearchDisabled(false); layout();
        assertTrue(grid.hasFocus());
    }

    @Test public void nestedGridUsesSelectedOuterRowAndSelectedInnerCard() {
        VerticalGridView outer = new VerticalGridView(activity);
        outer.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return 3; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                HorizontalGridView inner = new HorizontalGridView(activity);
                RecyclerView.LayoutParams params = ((RecyclerView) parent).getLayoutManager().generateDefaultLayoutParams();
                params.width = 500; params.height = 100;
                inner.setLayoutParams(params);
                inner.setAdapter(new Cards());
                return new RecyclerView.ViewHolder(inner) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((BaseGridView) holder.itemView).setSelectedPosition(position + 1);
            }
        });
        root.addView(outer, new FrameLayout.LayoutParams(600, 400));
        outer.setSelectedPosition(1); layout(); root.requestFocus();
        navigation = new FocusNavigator(root, activity::dispatchKeyEvent);
        navigation.prepare(); layout();
        HorizontalGridView inner = (HorizontalGridView) outer.findViewHolderForAdapterPosition(1).itemView;
        assertEquals(2, inner.getSelectedPosition());
        assertSame(inner.findViewHolderForAdapterPosition(2).itemView, root.findFocus());
        // Remembered inner grid remains attached, but the outer selection moves elsewhere.
        root.requestFocus(); outer.setSelectedPosition(2); layout();
        HorizontalGridView next = (HorizontalGridView) outer.findViewHolderForAdapterPosition(2).itemView;
        assertEquals(2, outer.getSelectedPosition());
        assertSame(next.findViewHolderForAdapterPosition(3).itemView, root.findFocus());
    }
}
