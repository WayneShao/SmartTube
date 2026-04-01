package com.liskovsoft.smartyoutubetv2.tv.ui.rayneo;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Stereo (dual-eye) wrapper for RayNeo X3 Pro.
 *
 * Layout contract:
 *   - This ViewGroup fills the full physical screen (1280 × 480).
 *   - Its FIRST child (the real app content) is measured and laid out at
 *     exactly {@link RayNeoConfig#SINGLE_EYE_WIDTH} × {@link RayNeoConfig#SINGLE_EYE_HEIGHT}
 *     in the left-eye region (x = 0).
 *   - {@link #dispatchDraw} renders the child twice: once at x = 0 (left eye)
 *     and once translated to x = SINGLE_EYE_WIDTH (right eye).
 *     Because HWUI replays display lists, the GPU work for the child is
 *     recorded once and composited in two positions – no redundant CPU drawing.
 *
 * Mercury trigger:
 *   A 1 × 1 INVISIBLE view is placed at leftMargin = SINGLE_EYE_WIDTH.
 *   This satisfies the Mercury input-forwarding heuristic that checks for a
 *   view positioned in the right-eye region, causing Mercury to route
 *   temple-touchpad MotionEvents to this application instead of consuming
 *   them as system gestures.
 */
public class RayNeoStereoLayout extends FrameLayout {

    private static final int EYE_W = RayNeoConfig.SINGLE_EYE_WIDTH;
    private static final int EYE_H = RayNeoConfig.SINGLE_EYE_HEIGHT;

    public RayNeoStereoLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        // Mercury trigger: a positioned-but-invisible view in the right-eye area.
        View trigger = new View(context);
        trigger.setVisibility(View.INVISIBLE);
        LayoutParams lp = new LayoutParams(1, 1);
        lp.leftMargin = EYE_W;
        addView(trigger, lp);
    }

    /**
     * Measure the first real child (index 0 = app content, index 1 = trigger)
     * at exactly EYE_W × EYE_H. We ourselves report the full physical width.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Measure the trigger view normally (1×1).
        if (getChildCount() > 1) {
            measureChild(getChildAt(1), widthMeasureSpec, heightMeasureSpec);
        }
        // Constrain the app-content child to one eye.
        if (getChildCount() > 0) {
            int contentW = MeasureSpec.makeMeasureSpec(EYE_W, MeasureSpec.EXACTLY);
            int contentH = MeasureSpec.makeMeasureSpec(EYE_H, MeasureSpec.EXACTLY);
            getChildAt(0).measure(contentW, contentH);
        }
        // Report the full physical screen size to our parent.
        int fullWidth  = MeasureSpec.getSize(widthMeasureSpec);
        int fullHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
                fullWidth  > 0 ? fullWidth  : EYE_W * 2,
                fullHeight > 0 ? fullHeight : EYE_H
        );
    }

    /**
     * Place the app-content child at (0, 0) and the trigger at (EYE_W, 0).
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() > 0) {
            View content = getChildAt(0);
            content.layout(0, 0, EYE_W, EYE_H);
        }
        if (getChildCount() > 1) {
            View trigger = getChildAt(1);
            trigger.layout(EYE_W, 0, EYE_W + 1, 1);
        }
    }

    /**
     * Draw the app-content child into both eye regions.
     *
     * The child's display list is built by the first {@code super.dispatchDraw}
     * call and replayed (not rebuilt) for the second draw, so this is a
     * GPU-side blit rather than a full re-render.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Left eye – draw normally at x = 0.
        super.dispatchDraw(canvas);

        // Right eye – translate canvas and replay.
        canvas.save();
        canvas.translate(EYE_W, 0);
        // Draw only the app-content child (index 0), not the trigger again.
        if (getChildCount() > 0) {
            drawChild(canvas, getChildAt(0), getDrawingTime());
        }
        canvas.restore();
    }
}
