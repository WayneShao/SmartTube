package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.database.DataSetObserver;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

/** A delayed pointer action must still address the same content at the same coordinates. */
final class CursorTarget {
    final StereoLayout owner;
    final float x, y;
    final View view;
    private final Object tag;
    private final String text;
    private RecyclerView recycler;
    private RecyclerView.Adapter recyclerAdapter;
    private AdapterView<?> list;
    private Adapter listAdapter;
    private boolean changed;
    private final RecyclerView.AdapterDataObserver recyclerObserver = new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged() { changed = true; }
        @Override public void onItemRangeChanged(int start, int count) { changed = true; }
        @Override public void onItemRangeChanged(int start, int count, Object payload) { changed = true; }
        @Override public void onItemRangeInserted(int start, int count) { changed = true; }
        @Override public void onItemRangeRemoved(int start, int count) { changed = true; }
        @Override public void onItemRangeMoved(int from, int to, int count) { changed = true; }
    };
    private final DataSetObserver listObserver = new DataSetObserver() {
        @Override public void onChanged() { changed = true; }
        @Override public void onInvalidated() { changed = true; }
    };

    CursorTarget(StereoLayout root) {
        owner = root;
        x = root.cursor().x();
        y = root.cursor().y();
        view = hit(root, x, y);
        tag = view == null ? null : view.getTag();
        text = view instanceof TextView ? ((TextView) view).getText().toString() : null;
        for (View candidate = view; candidate != null; candidate = parent(candidate)) {
            if (candidate instanceof RecyclerView) {
                recycler = (RecyclerView) candidate;
                recyclerAdapter = recycler.getAdapter();
                if (recyclerAdapter != null) recyclerAdapter.registerAdapterDataObserver(recyclerObserver);
                break;
            }
            if (candidate instanceof AdapterView) {
                list = (AdapterView<?>) candidate;
                listAdapter = list.getAdapter();
                if (listAdapter != null) listAdapter.registerDataSetObserver(listObserver);
                break;
            }
        }
    }

    boolean valid(StereoLayout current) {
        return !changed && owner == current && view != null && hit(owner, x, y) == view
                && view.getTag() == tag
                && (text == null || text.contentEquals(((TextView) view).getText()))
                && (recycler == null || recycler.getAdapter() == recyclerAdapter)
                && (list == null || list.getAdapter() == listAdapter);
    }

    void close() {
        if (recyclerAdapter != null) recyclerAdapter.unregisterAdapterDataObserver(recyclerObserver);
        if (listAdapter != null) listAdapter.unregisterDataSetObserver(listObserver);
        recyclerAdapter = null;
        listAdapter = null;
        changed = true;
    }

    boolean longClick() {
        for (View candidate = view; candidate != null && candidate != owner; candidate = parent(candidate)) {
            if (candidate.isLongClickable() && candidate.performLongClick()) return true;
        }
        return false;
    }

    boolean focus() {
        for (View candidate = view; candidate != null && candidate != owner; candidate = parent(candidate)) {
            if (candidate.isFocusable() && candidate.requestFocus()) return true;
        }
        return false;
    }

    private static View parent(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    static View hit(View root, float x, float y) {
        if (root.getVisibility() != View.VISIBLE || x < 0 || y < 0 || x >= root.getWidth() || y >= root.getHeight()) return null;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                float[] point = {x + root.getScrollX() - child.getLeft(), y + root.getScrollY() - child.getTop()};
                if (!child.getMatrix().isIdentity()) {
                    android.graphics.Matrix inverse = new android.graphics.Matrix();
                    if (!child.getMatrix().invert(inverse)) continue;
                    inverse.mapPoints(point);
                }
                View result = hit(child, point[0], point[1]);
                if (result != null) return result;
            }
        }
        return root;
    }
}
