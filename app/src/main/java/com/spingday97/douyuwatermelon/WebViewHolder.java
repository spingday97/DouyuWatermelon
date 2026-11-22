package com.spingday97.douyuwatermelon;

import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

public class WebViewHolder {
    public static WebView webView;
    public static boolean isFloating = false;

    public static void detachFromParent() {
        if (webView == null) return;
        ViewParent parent = webView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(webView);
        }
    }
}
