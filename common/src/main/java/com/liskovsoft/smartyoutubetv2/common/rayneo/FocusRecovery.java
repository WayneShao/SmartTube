package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.view.View;
import android.database.DataSetObserver;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import androidx.leanback.widget.BaseGridView;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Resolves current navigation state; old references are hints, never click targets. */
final class FocusRecovery {
    private final StereoLayout root;
    private WeakReference<View> previous = new WeakReference<>(null);
    private final List<WeakReference<View>> regions = new ArrayList<>();
    private WeakReference<ListView> previousList = new WeakReference<>(null);
    private ListAdapter previousAdapter;
    private int previousRow = -1;
    private long previousId;
    private boolean dataChanged;
    private final DataSetObserver dataObserver = new DataSetObserver() {
        @Override public void onChanged() { dataChanged = true; }
        @Override public void onInvalidated() { dataChanged = true; }
    };

    FocusRecovery(StereoLayout root) { this.root = root; }

    boolean inside(View view) {
        if (view == null || view == root || !view.isAttachedToWindow() || !view.isShown()
                || !view.isEnabled()) return false;
        for (View parent = view; parent != null; parent = parent(parent)) {
            if (!parent.isEnabled()) return false;
            if (parent != view && parent instanceof ViewGroup
                    && ((ViewGroup) parent).getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) return false;
            if (parent == root) return true;
        }
        return false;
    }

    boolean usable(View view) { return inside(view) && view.isFocusable(); }

    boolean valid(View view) {
        if (!usable(view)) return false;
        if (view instanceof ListView) {
            ListView list = (ListView) view;
            return enabled(list.getAdapter(), list.getSelectedItemPosition());
        }
        if (view instanceof BaseGridView) return false;
        return onSelectedPath(view);
    }

    private boolean onSelectedPath(View view) {
        View child = view;
        for (View node = parent(view); node != null && node != root; child = node, node = parent(node)) {
            if (node instanceof BaseGridView) {
                BaseGridView grid = (BaseGridView) node;
                if (grid.getChildAdapterPosition(child) != grid.getSelectedPosition()) return false;
            }
        }
        return true;
    }

    void remember(View view) {
        if (!usable(view)) return;
        previous = new WeakReference<>(view);
        View scope = parent(view);
        for (View node = view; node != null && node != root; node = parent(node)) {
            if (node instanceof ListView || node instanceof BaseGridView) { scope = node; break; }
        }
        if (scope != root && inside(scope)) {
            regions.clear();
            for (View node = scope; node != null && node != root; node = parent(node)) {
                regions.add(new WeakReference<>(node));
            }
        }
        if (view instanceof ListView) {
            ListView list = (ListView) view;
            if (enabled(list.getAdapter(), list.getSelectedItemPosition())) {
                previousList = new WeakReference<>(list);
                if (previousAdapter != list.getAdapter()) {
                    if (previousAdapter != null) previousAdapter.unregisterDataSetObserver(dataObserver);
                    previousAdapter = list.getAdapter();
                    previousAdapter.registerDataSetObserver(dataObserver);
                }
                dataChanged = false;
                previousRow = list.getSelectedItemPosition();
                previousId = list.getSelectedItemId();
            }
        }
    }

    View prepare() {
        View current = root.findFocus();
        if (valid(current) || (usable(current) && current instanceof ListView && onSelectedPath(current))) return focus(current);
        View scope = root;
        for (WeakReference<View> hint : regions) {
            if (inside(hint.get()) && onSelectedPath(hint.get())) { scope = hint.get(); break; }
        }
        // Live component selection takes priority over a stale leaf reference.
        View selected = selected(scope);
        if (selected != null) return focus(selected);
        // A live grid selection can be waiting for a holder/layout. Do not jump elsewhere.
        if (waiting(scope)) return null;
        View old = previous.get();
        if (usable(old) && descendant(scope, old)) return focus(old);
        if (scope != root && usable(scope)) {
            View restored = focus(scope);
            if (restored != null) return restored;
        }
        for (View candidate : scope.getFocusables(View.FOCUS_FORWARD)) {
            if (!usable(candidate)) continue;
            View restored = focus(candidate);
            if (restored != null) return restored;
        }
        return null;
    }

    private View selected(View node) {
        if (node != root && !inside(node)) return null;
        if (node instanceof ListView && enabled(((ListView) node).getAdapter(),
                ((ListView) node).getSelectedItemPosition())) return node;
        if (node instanceof BaseGridView) {
            BaseGridView grid = (BaseGridView) node;
            if (grid.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS || busy(grid)) return null;
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(grid.getSelectedPosition());
            if (holder != null && inside(holder.itemView) && holder.itemView.hasFocusable()) {
                View nested = selected(holder.itemView);
                return nested != null ? nested : (waiting(holder.itemView) ? null : holder.itemView);
            }
            return null;
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            if (group.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) return null;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = selected(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    boolean waiting(View node) {
        if (node == null) return false;
        if (node instanceof BaseGridView) {
            BaseGridView grid = (BaseGridView) node;
            return grid.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    || busy(grid) || (grid.getAdapter() != null && grid.getAdapter().getItemCount() > 0
                    && grid.getSelectedPosition() >= 0
                    && (grid.findViewHolderForAdapterPosition(grid.getSelectedPosition()) == null
                    || waiting(grid.findViewHolderForAdapterPosition(grid.getSelectedPosition()).itemView)));
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.isShown() && waiting(child)) return true;
            }
        }
        return false;
    }

    private boolean busy(BaseGridView grid) {
        return grid.isComputingLayout() || grid.hasPendingAdapterUpdates()
                || grid.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
    }

    private View focus(View chosen) {
        if (chosen instanceof BaseGridView) {
            View child = selected(chosen);
            return child == null ? null : focus(child);
        }
        if (!inside(chosen) || !chosen.hasFocusable()) return null;
        if (root.isInTouchMode() && chosen.isFocusable()) {
            boolean wasFocusable = chosen.isFocusableInTouchMode();
            chosen.setFocusableInTouchMode(true);
            chosen.requestFocusFromTouch();
            chosen.setFocusableInTouchMode(wasFocusable);
        } else chosen.requestFocusFromTouch();
        View focused = root.findFocus();
        if (!usable(focused) || !descendant(chosen, focused)) return null;
        if (focused instanceof ListView) {
            ListView list = (ListView) focused;
            ListAdapter adapter = list.getAdapter();
            if (!enabled(adapter, list.getSelectedItemPosition())) {
                int row = -1;
                if (previousList.get() == list && previousAdapter == adapter && adapter != null) {
                    if (adapter.hasStableIds()) {
                        for (int i = 0; i < adapter.getCount(); i++) {
                            if (adapter.getItemId(i) == previousId && adapter.isEnabled(i)) { row = i; break; }
                        }
                    } else if (!dataChanged && enabled(adapter, previousRow)) row = previousRow;
                }
                if (row < 0) row = firstEnabled(adapter, Math.max(0, list.getFirstVisiblePosition()));
                if (row < 0) row = firstEnabled(adapter, 0);
                if (row < 0) return null;
                list.setSelection(row);
            }
        }
        remember(focused);
        return focused;
    }

    private static int firstEnabled(ListAdapter adapter, int start) {
        if (adapter != null) for (int i = start; i < adapter.getCount(); i++) if (adapter.isEnabled(i)) return i;
        return -1;
    }

    void close() {
        if (previousAdapter != null) previousAdapter.unregisterDataSetObserver(dataObserver);
        previousAdapter = null;
    }

    private static boolean enabled(ListAdapter adapter, int row) {
        return adapter != null && row >= 0 && row < adapter.getCount() && adapter.isEnabled(row);
    }

    private static boolean descendant(View ancestor, View view) {
        for (View node = view; node != null; node = parent(node)) if (node == ancestor) return true;
        return false;
    }

    private static View parent(View view) {
        return view.getParent() instanceof View ? (View) view.getParent() : null;
    }
}
