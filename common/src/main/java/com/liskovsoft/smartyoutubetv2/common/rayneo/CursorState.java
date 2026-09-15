package com.liskovsoft.smartyoutubetv2.common.rayneo;

/** A window-local cursor in single-eye pixels. No global pointer ownership. */
public final class CursorState {
    private float x, y;
    private int width, height;

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (this.width == 0 || this.height == 0) {
            x = width / 2f;
            y = height / 2f;
        }
        this.width = width;
        this.height = height;
        move(0, 0);
    }

    public void move(float dx, float dy) {
        x = Math.max(0, Math.min(Math.max(0, width - 1), x + dx));
        y = Math.max(0, Math.min(Math.max(0, height - 1), y + dy));
    }

    public float x() { return x; }
    public float y() { return y; }
}
