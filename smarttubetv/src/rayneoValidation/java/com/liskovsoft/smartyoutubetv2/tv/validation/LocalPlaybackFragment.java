package com.liskovsoft.smartyoutubetv2.tv.validation;

/** Actual production player; only the video output selection is controllable in this test build. */
public final class LocalPlaybackFragment extends com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment {
    @Override protected boolean useOesSurface() {
        return !getActivity().getIntent().getBooleanExtra("textureBaseline", false) && super.useOesSurface();
    }
}
