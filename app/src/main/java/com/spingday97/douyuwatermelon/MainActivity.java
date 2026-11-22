package com.spingday97.douyuwatermelon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String GAME_URL =
            "https://www.douyu.com/pages/vibe-lab-act202607-game/home?dykeepalive=1&isAnchorSide=0&rid=217331&isLandscape=0";
    private static final String LOGIN_URL = "https://passport.douyu.com/";

    private FrameLayout webContainer;
    private WebView webView;
    private boolean keepScreenOn = true;
    private boolean scriptEnabled = true;
    private TextView btnKeepScreen;
    private TextView btnScript;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#14171F"));
        root.setFitsSystemWindows(true);

        // Top compact control bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#1C212B"));
        int padH = dp(8);
        int padV = dp(6);
        bar.setPadding(padH, padV, padH, padV);

        TextView title = new TextView(this);
        title.setText("🍉");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleLp);

        bar.addView(makeBtn("活动页", "#FF6A3D", v -> webView.loadUrl(GAME_URL)));
        bar.addView(makeBtn("登录", "#2D3648", v -> webView.loadUrl(LOGIN_URL)));
        bar.addView(makeBtn("悬浮", "#2563EB", v -> enterFloatingMode()));
        btnKeepScreen = makeBtn("常亮:开", "#2D6A4F", v -> toggleKeepScreen());
        bar.addView(btnKeepScreen);
        btnScript = makeBtn("脚本:开", "#2D6A4F", v -> toggleScript());
        bar.addView(btnScript);
        bar.addView(makeBtn("刷新", "#2D3648", v -> webView.reload()));

        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applyKeepScreen();

        boolean isNew = (WebViewHolder.webView == null);
        if (isNew) {
            webView = new WebView(getApplicationContext());
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setDatabaseEnabled(true);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);

            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    return !(url.startsWith("http://") || url.startsWith("https://"));
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    CookieManager.getInstance().flush();
                    if (scriptEnabled && url != null && url.contains("douyu.com")) {
                        injectWatermelon();
                        handler.postDelayed(() -> injectWatermelon(), 1500);
                        handler.postDelayed(() -> injectWatermelon(), 3500);
                    }
                }
            });
            WebViewHolder.webView = webView;
        } else {
            webView = WebViewHolder.webView;
        }

        attachWebView();
        if (isNew) {
            webView.loadUrl(GAME_URL);
        }
    }

    private void attachWebView() {
        stopService(new Intent(this, FloatingKeepAliveService.class));
        WebViewHolder.isFloating = false;
        WebViewHolder.detachFromParent();
        if (webView != null && webContainer != null) {
            webView.setVisibility(View.VISIBLE);
            webView.onResume();
            webContainer.addView(webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void enterFloatingMode() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予「悬浮窗 / 出现在其他应用上层」权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        WebViewHolder.detachFromParent();
        startForegroundService(new Intent(this, FloatingKeepAliveService.class));
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (WebViewHolder.isFloating || (webView != null && webView.getParent() != webContainer)) {
            attachWebView();
        }
    }

    private TextView makeBtn(String text, String bgHex, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setPadding(dp(7), dp(4), dp(7), dp(4));
        tv.setBackgroundColor(Color.parseColor(bgHex));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void toggleKeepScreen() {
        keepScreenOn = !keepScreenOn;
        applyKeepScreen();
        btnKeepScreen.setText(keepScreenOn ? "常亮:开" : "常亮:关");
        btnKeepScreen.setBackgroundColor(Color.parseColor(keepScreenOn ? "#2D6A4F" : "#4B5563"));
    }

    private void applyKeepScreen() {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void toggleScript() {
        scriptEnabled = !scriptEnabled;
        btnScript.setText(scriptEnabled ? "脚本:开" : "脚本:关");
        btnScript.setBackgroundColor(Color.parseColor(scriptEnabled ? "#2D6A4F" : "#4B5563"));
        if (scriptEnabled) {
            injectWatermelon();
            Toast.makeText(this, "已注入挂机脚本", Toast.LENGTH_SHORT).show();
        } else {
            webView.reload();
        }
    }

    private void injectWatermelon() {
        if (!scriptEnabled || webView == null) return;
        String js = WatermelonScript.getScript();
        if (js != null && !js.isEmpty()) {
            webView.evaluateJavascript(js, null);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
