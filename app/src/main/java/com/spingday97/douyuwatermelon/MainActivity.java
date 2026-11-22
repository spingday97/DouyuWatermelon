package com.spingday97.douyuwatermelon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.PopupMenu;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String PREF_NAME = "wm_prefs";
    private static final String KEY_RID = "room_id";
    private static final String DEFAULT_RID = "217331";
    private static final String LOGIN_URL = "https://passport.douyu.com/";

    private FrameLayout webContainer;
    private WebView webView;
    private boolean keepScreenOn = true;
    private boolean scriptEnabled = true;
    private TextView btnKeepScreen;
    private TextView btnScript;
    private TextView btnRoom;
    private String currentRid = DEFAULT_RID;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String getGameUrl() {
        return "https://www.douyu.com/pages/vibe-lab-act202607-game/home?dykeepalive=1&isAnchorSide=0&rid="
                + currentRid + "&isLandscape=0";
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentRid = prefs.getString(KEY_RID, DEFAULT_RID);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#14171F"));
        root.setFitsSystemWindows(true);

        // Top compact control bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
bar.setBackgroundColor(Color.parseColor("#161922"));
bar.setPadding(dp(10), dp(8), dp(10), dp(8));

btnRoom = makePill("\uD83C\uDF49 房:" + currentRid, "#242938", "#353C50", v -> showRoomDialog());
bar.addView(btnRoom);
bar.addView(makePill("活动", "#FF6A3D", null, v -> webView.loadUrl(getGameUrl())));
bar.addView(makePill("悬浮", "#2563EB", null, v -> enterFloatingMode()));
btnKeepScreen = makePill("\u25CF 常亮",
keepScreenOn ? "#1F3B2E" : "#242938",
keepScreenOn ? "#2D6A4F" : "#353C50",
v -> toggleKeepScreen());
bar.addView(btnKeepScreen);

View spacer = new View(this);
spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
bar.addView(spacer);

TextView btnMore = makePill("\u22EF", "#242938", "#353C50", null);
((LinearLayout.LayoutParams) btnMore.getLayoutParams()).rightMargin = 0;
btnMore.setOnClickListener(v -> {
PopupMenu popup = new PopupMenu(this, v);
popup.getMenu().add(0, 1, 0, "登录斗鱼账号");
popup.getMenu().add(0, 2, 1, "刷新页面");
popup.getMenu().add(0, 3, 2, "挂机脚本：" + (scriptEnabled ? "已开启" : "已关闭"));
popup.getMenu().add(0, 4, 3, "诊断与导入脚本");
popup.setOnMenuItemClickListener(item -> {
int id = item.getItemId();
if (id == 1) { webView.loadUrl(LOGIN_URL); return true; }
if (id == 2) { webView.reload(); return true; }
if (id == 3) { toggleScript(); return true; }
if (id == 4) { runDiag(); return true; }
return false;
});
popup.show();
});
bar.addView(btnMore);

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
            WebView.setWebContentsDebuggingEnabled(true);
            webView = new WebView(getApplicationContext());
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setDatabaseEnabled(true);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            String ua = ws.getUserAgentString();
            if (ua != null) {
                ws.setUserAgentString(ua.replace("; wv)", ")"));
            }

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
            webView.loadUrl(getGameUrl());
        }
    }

    private void showRoomDialog() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(currentRid);
        et.setSelection(et.getText().length());
        et.setHint("输入斗鱼直播间号");

        new AlertDialog.Builder(this)
                .setTitle("切换直播间")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String rid = et.getText().toString().trim();
                    if (!rid.isEmpty()) {
                        currentRid = rid;
                        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                                .edit().putString(KEY_RID, currentRid).apply();
                        btnRoom.setText("房:" + currentRid);
                        webView.loadUrl(getGameUrl());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

   private void runDiag() {
String jsLen = String.valueOf(WatermelonScript.getScript(this).length());
boolean hasCustom = getSharedPreferences("wm_prefs", MODE_PRIVATE).contains("custom_script");
String src = hasCustom ? "自定义" : "内置";
if (webView == null) {
showDiagDialog("WebView=null, jsLen=" + jsLen + " (" + src + ")");
return;
}
webView.evaluateJavascript(
"(function(){try{return 'wm9='+!!window.__WM9__+',cc='+!!(window.cc&&window.cc.director)+',ifr='+document.querySelectorAll('iframe').length+',path='+location.pathname;}catch(e){return 'err:'+e;}})()",
val -> showDiagDialog("jsLen=" + jsLen + " (" + src + ")" + (char) 10 + val)
);
}

private void showDiagDialog(String msg) {
new AlertDialog.Builder(this)
.setTitle("诊断与脚本")
.setMessage(msg)
.setPositiveButton("导入本地脚本", (d, w) -> {
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
intent.addCategory(Intent.CATEGORY_OPENABLE);
intent.setType("*/*");
startActivityForResult(intent, 1002);
})
.setNeutralButton("恢复内置脚本", (d, w) -> {
getSharedPreferences("wm_prefs", MODE_PRIVATE).edit().remove("custom_script").apply();
Toast.makeText(this, "已恢复内置脚本", Toast.LENGTH_SHORT).show();
if (scriptEnabled && webView != null) {
webView.reload();
}
})
.setNegativeButton("关闭", null)
.show();
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

   private TextView makePill(String text, String bgHex, String strokeHex, View.OnClickListener listener) {
TextView tv = new TextView(this);
tv.setText(text);
tv.setTextColor(Color.WHITE);
tv.setTextSize(12);
tv.setPadding(dp(10), dp(6), dp(10), dp(6));
GradientDrawable gd = new GradientDrawable();
gd.setColor(Color.parseColor(bgHex));
gd.setCornerRadius(dp(18));
if (strokeHex != null) {
gd.setStroke(dp(1), Color.parseColor(strokeHex));
}
tv.setBackground(gd);
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT);
lp.rightMargin = dp(6);
tv.setLayoutParams(lp);
tv.setOnClickListener(listener);
return tv;
   }

    private void toggleKeepScreen() {
        keepScreenOn = !keepScreenOn;
        applyKeepScreen();
      ;GradientDrawable gd = new GradientDrawable();
gd.setColor(Color.parseColor(keepScreenOn ? "#1F3B2E" : "#242938"));
gd.setCornerRadius(dp(18));
gd.setStroke(dp(1), Color.parseColor(keepScreenOn ? "#2D6A4F" : "#353C50"));
btnKeepScreen.setBackground(gd);
        Toast.makeText(this, keepScreenOn ? "已开启屏幕常亮" : "已关闭屏幕常亮", Toast.LENGTH_SHORT).show();
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
        // btnScript.setBackgroundColor(Color.parseColor(scriptEnabled ? "#2D6A4F" : "#4B5563"));
        if (scriptEnabled) {
            injectWatermelon();
            Toast.makeText(this, "已开启并注入挂机脚本", Toast.LENGTH_SHORT).show();
        } else {
            webView.reload();
        }
    }

    private void injectWatermelon() {
        if (!scriptEnabled || webView == null) return;
        String js = WatermelonScript.getScript(this);
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

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
super.onActivityResult(requestCode, resultCode, data);
if (requestCode == 1002 && resultCode == RESULT_OK && data != null && data.getData() != null) {
Uri uri = data.getData();
try (InputStream is = getContentResolver().openInputStream(uri);
BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
StringBuilder sb = new StringBuilder();
String line;
while ((line = reader.readLine()) != null) {
sb.append(line).append((char) 10);
}
String script = sb.toString();
if (script.trim().isEmpty()) {
Toast.makeText(this, "脚本内容为空", Toast.LENGTH_SHORT).show();
return;
}
getSharedPreferences("wm_prefs", MODE_PRIVATE).edit().putString("custom_script", script).apply();
Toast.makeText(this, "已导入本地脚本 (" + script.length() + " chars)", Toast.LENGTH_SHORT).show();
if (scriptEnabled && webView != null) {
injectWatermelon();
}
} catch (Exception e) {
Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
}
}
}
}
