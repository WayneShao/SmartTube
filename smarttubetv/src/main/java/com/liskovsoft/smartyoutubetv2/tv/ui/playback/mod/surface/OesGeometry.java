package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

/** Anchor corners already include the upstream zoom/rotation/flip/gravity transforms. */
final class OesGeometry {
    private OesGeometry() {}
    static float[] vertices(float[] corners, int eyeWidth, int height) {
        if (eyeWidth <= 0 || height <= 0) return null;
        float[] result = new float[16];
        for (int i = 0; i < 4; i++) {
            result[i * 4] = 2 * corners[i * 2] / eyeWidth - 1;
            result[i * 4 + 1] = 1 - 2 * corners[i * 2 + 1] / height;
            result[i * 4 + 2] = i % 2;
            result[i * 4 + 3] = i / 2;
        }
        return result;
    }
}
