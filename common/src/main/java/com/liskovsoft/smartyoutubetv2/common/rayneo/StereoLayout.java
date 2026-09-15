package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/** One measured content tree, replayed in two clipped eye regions. */
public final class StereoLayout extends FrameLayout {
    private float gestureEyeOffset;

    public StereoLayout(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY), heightSpec);
        setMeasuredDimension(width, getMeasuredHeight());
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // FrameLayout receives a logical eye region, never the full stereo width.
        super.onLayout(changed, 0, 0, (right - left) / 2, bottom - top);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int eyeWidth = getWidth() / 2;
        for (int eye = 0; eye < 2; eye++) {
            int save = canvas.save();
            canvas.translate(eye * eyeWidth, 0);
            canvas.clipRect(0, 0, eyeWidth, getHeight());
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            gestureEyeOffset = event.getX() >= getWidth() / 2f ? getWidth() / 2f : 0;
        }
        MotionEvent logical = MotionEvent.obtain(event);
        logical.offsetLocation(-gestureEyeOffset, 0);
        try {
            return super.dispatchTouchEvent(logical);
        } finally {
            logical.recycle();
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                gestureEyeOffset = 0;
            }
        }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        MotionEvent logical = MotionEvent.obtain(event);
        if (logical.getX() >= getWidth() / 2f) logical.offsetLocation(-getWidth() / 2f, 0);
        try {
            return super.dispatchGenericMotionEvent(logical);
        } finally {
            logical.recycle();
        }
    }

    public static void invalidateAncestor(View view) {
        for (android.view.ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof StereoLayout) {
                ((StereoLayout) parent).postInvalidateOnAnimation();
                return;
            }
        }
    }
}
