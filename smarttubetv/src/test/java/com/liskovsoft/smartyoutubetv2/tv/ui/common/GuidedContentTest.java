package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.common.rayneo.StereoLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowBuild;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GuidedContentTest {
    public static class Host extends FragmentActivity {
        @Override protected void onCreate(Bundle saved) {
            super.onCreate(saved);
            shadowOf(getWindowManager().getDefaultDisplay()).setRealWidth(1280);
            shadowOf(getWindowManager().getDefaultDisplay()).setRealHeight(480);
            int container = GuidedContent.install(this);
            if (saved == null) getSupportFragmentManager().beginTransaction()
                    .replace(container, new Page(), "page").commit();
        }
    }
    public static class Page extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
            Button button = new Button(requireContext());
            button.setText("Continue"); return button;
        }
    }

    @Test public void asyncFragmentAndRestoredFragmentStayInsideOneStereoRoot() {
        ShadowBuild.setManufacturer("RayNeo"); ShadowBuild.setModel("ARGF20"); ShadowBuild.setDevice("MercuryLiteXR");
        ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup();
        verify(controller.get());
        controller.recreate();
        verify(controller.get());
        controller.pause().stop().destroy();
    }

    private void verify(Host activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        ViewGroup content = activity.findViewById(android.R.id.content);
        assertEquals(1, content.getChildCount());
        assertTrue(content.getChildAt(0) instanceof StereoLayout);
        View root = content.getChildAt(0);
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 480);
        Fragment page = activity.getSupportFragmentManager().findFragmentByTag("page");
        assertNotNull(page);
        assertEquals(640, page.requireView().getWidth());
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
    }
}
