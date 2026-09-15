package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** One measured content tree, replayed in two clipped eye regions. */
public final class StereoLayout extends FrameLayout {
    private float gestureEyeOffset;
    private TextView message;
    private final Runnable hideMessage = this::dismissMessage;

    public StereoLayout(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
    }

    /** A transient message stays in the same stereo window and never enters focus navigation. */
    public void showMessage(CharSequence text) {
        removeCallbacks(hideMessage);
        if (message == null) {
            message = new TextView(getContext());
            message.setTextColor(Color.WHITE);
            message.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18);
            message.setGravity(Gravity.CENTER);
            message.setPadding(16, 10, 16, 10);
            message.setMaxLines(3);
            message.setEllipsize(android.text.TextUtils.TruncateAt.END);
            message.setFocusable(false);
            message.setFocusableInTouchMode(false);
            message.setClickable(false);
            message.setLongClickable(false);
            // Keep a transient notice above elevated/focused content without taking input focus.
            message.setElevation(8 * getResources().getDisplayMetrics().density);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(48, 48, 48));
            background.setCornerRadius(12);
            message.setBackground(background);
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.bottomMargin = 24;
            addView(message, params);
        }
        message.setMaxWidth(Math.max(1, getWidth() / 2 - 48));
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        postDelayed(hideMessage, 2000);
    }

    public void dismissMessage() {
        removeCallbacks(hideMessage);
        if (message != null) message.setVisibility(View.GONE);
    }

    @Override protected void onDetachedFromWindow() {
        dismissMessage();
        super.onDetachedFromWindow();
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
