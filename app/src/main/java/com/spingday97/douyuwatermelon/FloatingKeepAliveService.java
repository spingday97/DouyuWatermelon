package com.spingday97.douyuwatermelon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class FloatingKeepAliveService extends Service {
    private static final String CHANNEL_ID = "wm_keep_alive";
    private WindowManager windowManager;
    private FrameLayout floatingRoot;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotif();
        showFloatingWindow();
    }

    private void startForegroundNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "后台挂机保活", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("斗鱼大西瓜后台挂机中")
                .setContentText("悬浮小窗保活运行中，点击返回主界面")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1001, notif);
        }
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        int w = dp(104);
        int h = dp(38);
        params = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(140);

        floatingRoot = new FrameLayout(this);

        // Attach live WebView underneath the compact badge so rAF continues running at full speed
        WebViewHolder.detachFromParent();
        if (WebViewHolder.webView != null) {
            WebViewHolder.webView.setVisibility(View.VISIBLE);
            WebViewHolder.webView.onResume();
            floatingRoot.addView(WebViewHolder.webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        // Compact pill overlay on top
        TextView badge = new TextView(this);
        badge.setText("🍉 挂机中 ↗");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setGravity(Gravity.CENTER);
        badge.getPaint().setFakeBoldText(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F0181B24"));
        bg.setCornerRadius(dp(19));
        bg.setStroke(dp(1), Color.parseColor("#FF6A3D"));
        badge.setBackground(bg);

        floatingRoot.addView(badge, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        badge.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - touchX);
                        int dy = Math.round(event.getRawY() - touchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        params.x = initX + dx;
                        params.y = initY + dy;
                        if (floatingRoot != null && windowManager != null) {
                            windowManager.updateViewLayout(floatingRoot, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            restoreMainActivity();
                        }
                        return true;
                }
                return false;
            }
        });

        WebViewHolder.isFloating = true;
        windowManager.addView(floatingRoot, params);
    }

    private void restoreMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        stopSelf();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        WebViewHolder.isFloating = false;
        WebViewHolder.detachFromParent();
        if (windowManager != null && floatingRoot != null) {
            try {
                windowManager.removeView(floatingRoot);
            } catch (Throwable ignored) {}
            floatingRoot = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
