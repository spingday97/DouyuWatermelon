package com.spingday97.douyuwatermelon;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WatermelonScript {
    private static volatile String cachedAssetScript = null;

    public static String getScript(Context context) {
        if (context == null) return "";
        String custom = context.getSharedPreferences("wm_prefs", Context.MODE_PRIVATE)
                .getString("custom_script", null);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        if (cachedAssetScript != null) {
            return cachedAssetScript;
        }
        try (InputStream is = context.getAssets().open("watermelon.js");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            cachedAssetScript = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            return cachedAssetScript;
        } catch (Throwable t) {
            Log.e("DouyuWatermelon", "load watermelon.js failed", t);
            return "";
        }
    }
}
