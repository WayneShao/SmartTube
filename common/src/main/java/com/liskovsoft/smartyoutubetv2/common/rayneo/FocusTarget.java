package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

/** A delayed confirm belongs to the original focused content and adapter. */
final class FocusTarget {
    final StereoLayout owner;
    final View view;
    private final Object tag;
    private final String text;
    private RecyclerView recycler;
    private RecyclerView.Adapter recyclerAdapter;
    private AdapterView<?> list;
    private Adapter listAdapter;
    private int listPosition;
    private long listId;
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

    FocusTarget(StereoLayout root) {
        owner = root;
        view = root.findFocus();
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
                listPosition = list.getSelectedItemPosition();
                listId = list.getSelectedItemId();
                if (listAdapter != null) listAdapter.registerDataSetObserver(listObserver);
                break;
            }
        }
    }

    boolean valid(StereoLayout current) {
        return !changed && owner == current && view != null && owner.findFocus() == view && belongsToOwner(view)
                && view.getTag() == tag
                && (text == null || text.contentEquals(((TextView) view).getText()))
                && (recycler == null || recycler.getAdapter() == recyclerAdapter)
                && (list == null || (list.getAdapter() == listAdapter
                    && list.getSelectedItemPosition() == listPosition && list.getSelectedItemId() == listId));
    }

    void close() {
        if (recyclerAdapter != null) recyclerAdapter.unregisterAdapterDataObserver(recyclerObserver);
        if (listAdapter != null) listAdapter.unregisterDataSetObserver(listObserver);
        recyclerAdapter = null;
        listAdapter = null;
        changed = true;
    }

    private boolean belongsToOwner(View view) {
        if (!view.isShown() || !view.isEnabled()) return false;
        for (View ancestor = view; ancestor != null; ancestor = parent(ancestor)) {
            if (ancestor == owner) return true;
        }
        return false;
    }

    private static View parent(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }
}
