package com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.WebBrowserPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class WebBrowserFragment extends Fragment implements WebBrowserView {
    private WebView mWebView;
    private WebBrowserPresenter mWebBrowserPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebBrowserPresenter = WebBrowserPresenter.instance(getContext());
        mWebBrowserPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.webbrowser, null);
        } catch (Exception e) { // Failed to load WebView provider: No WebView installed
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mWebView = (WebView) view.findViewById(R.id.webview);
        if (com.liskovsoft.smartyoutubetv2.common.rayneo.RayNeo.isEnabled(requireContext())) {
            WebView original = mWebView;
            ViewGroup parent = (ViewGroup) original.getParent();
            int index = parent.indexOfChild(original);
            mWebView = new io.github.wayneshao.rayneo.ime.EmbeddedWebView(requireContext(),
                    com.liskovsoft.smartyoutubetv2.common.rayneo.RayNeoKeyboard.host());
            mWebView.setId(original.getId());
            ViewGroup.LayoutParams params = original.getLayoutParams();
            parent.removeView(original); original.destroy(); parent.addView(mWebView, index, params);
            mWebView.setWebViewClient(new android.webkit.WebViewClient() {
                // The original view had no client: clicked navigation opened an external handler.
                @Override public boolean shouldOverrideUrlLoading(WebView web, String url) {
                    try {
                        web.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                        return true;
                    } catch (android.content.ActivityNotFoundException unavailable) { return false; }
                }
                @Override public void onPageFinished(WebView web, String url) {
                    super.onPageFinished(web, url);
                    io.github.wayneshao.rayneo.ime.EmbeddedWebView.pageFinished(web);
                }
            });
        }
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // No caching
        //webSettings.setAppCacheEnabled(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mWebBrowserPresenter.onViewInitialized();
    }

    @Override public void onDestroyView() {
        if (mWebView instanceof io.github.wayneshao.rayneo.ime.EmbeddedWebView) {
            mWebView.destroy(); mWebView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void loadUrl(String url) {
        mWebView.loadUrl(url);
    }
}
