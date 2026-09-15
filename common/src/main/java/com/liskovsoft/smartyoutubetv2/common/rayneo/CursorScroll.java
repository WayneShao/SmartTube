package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

/** Continuing an outward swipe at the eye edge scrolls the nearest eligible list. */
final class CursorScroll {
    static void move(StereoLayout root, float dx, float dy) {
        float oldX = root.cursor().x(), oldY = root.cursor().y();
        root.cursor().move(dx, dy);
        float overflowX = dx - (root.cursor().x() - oldX);
        float overflowY = dy - (root.cursor().y() - oldY);
        if (overflowX == 0 && overflowY == 0) return;
        boolean vertical = Math.abs(overflowY) >= Math.abs(overflowX);
        float distance = vertical ? overflowY : overflowX;
        View candidate = closest(root, root, vertical, distance > 0 ? 1 : -1, new float[]{Float.MAX_VALUE});
        if (candidate == null) return;
        int pixels = Math.round(distance);
        if (vertical && candidate instanceof AbsListView) ((AbsListView) candidate).scrollListBy(pixels);
        else candidate.scrollBy(vertical ? 0 : pixels, vertical ? pixels : 0);
    }

    private static View closest(StereoLayout root, View view, boolean vertical, int direction, float[] best) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() == 0 || view.getHeight() == 0) return null;
        View result = null;
        if (vertical ? view.canScrollVertically(direction) : view.canScrollHorizontally(direction)) {
            android.graphics.Rect bounds = new android.graphics.Rect();
            if (view.getGlobalVisibleRect(bounds)) {
                int[] origin = new int[2];
                root.getLocationOnScreen(origin);
                float x = root.cursor().x() + origin[0], y = root.cursor().y() + origin[1];
                float distance = Math.max(0, Math.max(bounds.left - x, x - bounds.right))
                        + Math.max(0, Math.max(bounds.top - y, y - bounds.bottom));
                if (distance <= best[0]) { best[0] = distance; result = view; }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = closest(root, group.getChildAt(i), vertical, direction, best);
                if (child != null) result = child;
            }
        }
        return result;
    }
}
