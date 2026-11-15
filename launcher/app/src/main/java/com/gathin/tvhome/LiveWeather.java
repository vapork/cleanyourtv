package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LiveWeather {
    private static final String PREFS_NAME = "live_weather_prefs";
    private static final String KEY_TEXT = "weather_text";
    private static final String KEY_TIME = "weather_time";
    private static final long REFRESH_INTERVAL_MS = 30 * 60_000L; // 30 mins
    private static final int TIMEOUT_MS = 5000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile String cachedText = null;
    private static volatile long lastFetchTime = 0;

    private LiveWeather() {}

    static void bind(TextView view) {
        if (view == null) return;
        Context context = view.getContext().getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (cachedText == null) {
            cachedText = prefs.getString(KEY_TEXT, null);
            lastFetchTime = prefs.getLong(KEY_TIME, 0);
        }

        if (cachedText != null && !cachedText.isEmpty()) {
            view.setText(cachedText);
            view.setVisibility(View.VISIBLE);
        }

        long now = System.currentTimeMillis();
        if (cachedText == null || now - lastFetchTime > REFRESH_INTERVAL_MS) {
            executor.execute(() -> fetchWeather(context, view));
        }
    }

    private static void fetchWeather(Context context, TextView targetView) {
        try {
            double lat = 40.7895;
            double lon = -74.0565;

            // Try detecting location via IP
            try {
                String ipJson = httpGet("https://ipinfo.io/json");
                if (ipJson != null) {
                    JSONObject obj = new JSONObject(ipJson);
                    String loc = obj.optString("loc", "");
                    if (loc.contains(",")) {
                        String[] parts = loc.split(",");
                        lat = Double.parseDouble(parts[0].trim());
                        lon = Double.parseDouble(parts[1].trim());
                    }
                }
            } catch (Exception ignored) {
                // Keep default or fallback coords
            }

            String weatherUrl = String.format(java.util.Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit&timezone=auto",
                    lat, lon);

            String weatherJson = httpGet(weatherUrl);
            if (weatherJson == null) return;

            JSONObject root = new JSONObject(weatherJson);
            JSONObject current = root.getJSONObject("current");
            double temp = current.getDouble("temperature_2m");
            int code = current.getInt("weather_code");
            boolean isDay = current.optInt("is_day", 1) == 1;

            String icon = getIcon(code, isDay);
            String resultText = "· " + Math.round(temp) + "°F " + icon;

            cachedText = resultText;
            lastFetchTime = System.currentTimeMillis();

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_TEXT, resultText).putLong(KEY_TIME, lastFetchTime).apply();

            mainHandler.post(() -> {
                if (targetView != null) {
                    targetView.setText(resultText);
                    targetView.setVisibility(View.VISIBLE);
                }
            });
        } catch (Exception e) {
            // Silently retain cached value or stay hidden
        }
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static String getIcon(int code, boolean isDay) {
        switch (code) {
            case 0: return isDay ? "☀️" : "🌙";
            case 1:
            case 2: return isDay ? "⛅" : "☁️";
            case 3: return "☁️";
            case 45:
            case 48: return "🌫️";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57: return "🌦️";
            case 61:
            case 63:
            case 65:
            case 66:
            case 67:
            case 80:
            case 81:
            case 82: return "🌧️";
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86: return "🌨️";
            case 95:
            case 96:
            case 99: return "⛈️";
            default: return isDay ? "☀️" : "🌙";
        }
    }
}
