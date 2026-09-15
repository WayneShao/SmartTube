package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.os.Bundle;
import android.util.DisplayMetrics;
import arte.programar.materialfile.ui.FilePickerActivity;

/** The file picker is an AppCompatActivity outside MotherActivity's hierarchy. */
public final class RayNeoFilePickerActivity extends FilePickerActivity {
    @Override protected void onCreate(Bundle state) {
        if (RayNeo.isEnabled(this)) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            metrics.widthPixels = 640;
            metrics.density = metrics.scaledDensity = 2f / 3f;
            metrics.densityDpi = 107;
        }
        super.onCreate(state);
        RayNeoWindow.install(this);
    }
}
