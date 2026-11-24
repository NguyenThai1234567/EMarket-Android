package com.example.do_an_di_dong.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Quản lý cấu hình địa chỉ máy chủ EMarket Core API & AI Assistant Service (lưu trong SharedPreferences).
 * Cung cấp Base URL động cho ApiClient (Retrofit) và SignalRRealtimeClient, đồng thời cho phép
 * người dùng chuyển đổi linh hoạt giữa Cloud (Domain/Azure) và Localhost (Emulator/USB) từ hộp thoại cấu hình Server trên LoginActivity.
 */
public class AppConfig {
    private static final String PREF_NAME = "EMarketServerConfig";
    private static final String KEY_EMARKET_BASE_URL = "emarket_base_url";
    private static final String KEY_AI_BASE_URL = "ai_base_url";
    private static final String KEY_USER_CONFIGURED = "user_configured_v2";

    // Production Cloud & Local Endpoints (giống Flutter AppConfig)
    public static final String PRESET_CUSTOM_DOMAIN = "https://nguyenhaphuongthai.id.vn/";
    public static final String PRESET_AZURE_LEGACY = "https://emarketsystem-e5afekgsdzbtcngf.japaneast-01.azurewebsites.net/";
    public static final String PRESET_EMULATOR_LOCAL = "http://10.0.2.2:54446/";
    public static final String PRESET_USB_LOCAL = "http://localhost:54446/";

    // AI Assistant Endpoints (port 5001 trên AIAssistantService chạy HTTPS theo launchSettings.json)
    public static final String PRESET_AI_AZURE = "https://emarket-assistant-cjdgcjegdxd8hnby.japaneast-01.azurewebsites.net/";
    public static final String PRESET_AI_LOCAL = "https://10.0.2.2:5001/";
    public static final String PRESET_AI_USB_LOCAL = "https://localhost:5001/";

    private static String cachedEmarketUrl = null;
    private static String cachedAiUrl = null;

    /**
     * Chuẩn hóa chuỗi URL máy chủ: tự động thêm giao thức http/https phù hợp theo port hoặc IP nội bộ,
     * và đảm bảo luôn kết thúc bằng dấu '/' theo yêu cầu bắt buộc của Retrofit BaseUrl.
     */
    public static String normalizeUrl(String rawUrl, String fallback) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return fallback;
        }
        String url = rawUrl.trim();
        // Tự động ép sang HTTPS cho cổng 5001 (AIAssistantService) hoặc phân biệt IP Local (HTTP) với Domain (HTTPS)
        if (url.toLowerCase().startsWith("http://") && url.contains(":5001")) {
            url = "https://" + url.substring(7);
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            String lower = url.toLowerCase();
            if (lower.contains(":5001") || lower.contains(":443") || lower.contains(":44365")) {
                url = "https://" + url;
            } else {
                boolean isLocal = lower.startsWith("localhost")
                        || lower.startsWith("127.")
                        || lower.startsWith("10.")
                        || lower.startsWith("192.168.")
                        || lower.startsWith("172.")
                        || lower.contains(":54446")
                        || lower.contains(":5000");
                url = (isLocal ? "http://" : "https://") + url;
            }
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    /**
     * Lấy Base URL của EMarket Core API từ SharedPreferences (có cache trên RAM).
     * Được gọi bởi ApiClient và SignalRRealtimeClient mỗi khi khởi tạo kết nối mạng.
     */
    public static String getEmarketBaseUrl(Context context) {
        if (context == null) {
            return cachedEmarketUrl != null ? cachedEmarketUrl : PRESET_CUSTOM_DOMAIN;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean userConfigured = prefs.getBoolean(KEY_USER_CONFIGURED, false);
        String saved = prefs.getString(KEY_EMARKET_BASE_URL, null);

        // Nếu chưa cấu hình thủ công mà dính URL localhost cũ thì tự động reset về Domain Production
        if (saved == null || saved.trim().isEmpty() || (!userConfigured && saved.contains("localhost"))) {
            cachedEmarketUrl = PRESET_CUSTOM_DOMAIN;
            prefs.edit().putString(KEY_EMARKET_BASE_URL, PRESET_CUSTOM_DOMAIN).apply();
        } else {
            cachedEmarketUrl = normalizeUrl(saved, PRESET_CUSTOM_DOMAIN);
        }
        return cachedEmarketUrl;
    }

    /**
     * Lấy Base URL của dịch vụ AI Assistant từ SharedPreferences.
     * Được sử dụng khi gọi API chat/phân tích AI trong AiAssistantActivity và DataRepository.
     */
    public static String getAiBaseUrl(Context context) {
        if (context == null) {
            return cachedAiUrl != null ? cachedAiUrl : PRESET_AI_AZURE;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean userConfigured = prefs.getBoolean(KEY_USER_CONFIGURED, false);
        String saved = prefs.getString(KEY_AI_BASE_URL, null);

        if (saved == null || saved.trim().isEmpty() || (!userConfigured && saved.contains("localhost"))) {
            cachedAiUrl = PRESET_AI_AZURE;
            prefs.edit().putString(KEY_AI_BASE_URL, PRESET_AI_AZURE).apply();
        } else {
            cachedAiUrl = normalizeUrl(saved, PRESET_AI_AZURE);
        }
        return cachedAiUrl;
    }

    /**
     * Lưu đồng thời cả 2 URL (Core API & AI Service) khi người dùng bấm "Lưu cấu hình"
     * trong dialog cài đặt máy chủ (LoginActivity). Đánh dấu cờ user_configured_v2 = true.
     */
    public static void updateServerUrls(Context context, String newEmarketUrl, String newAiUrl) {
        String emarket = normalizeUrl(newEmarketUrl, PRESET_CUSTOM_DOMAIN);
        String ai = normalizeUrl(newAiUrl, PRESET_AI_AZURE);
        cachedEmarketUrl = emarket;
        cachedAiUrl = ai;
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_EMARKET_BASE_URL, emarket)
                    .putString(KEY_AI_BASE_URL, ai)
                    .putBoolean(KEY_USER_CONFIGURED, true)
                    .apply();
        }
    }

    /**
     * Cập nhật riêng địa chỉ EMarket Core API và lưu vào SharedPreferences.
     */
    public static void updateEmarketBaseUrl(Context context, String newEmarketUrl) {
        String emarket = normalizeUrl(newEmarketUrl, PRESET_CUSTOM_DOMAIN);
        cachedEmarketUrl = emarket;
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_EMARKET_BASE_URL, emarket)
                    .putBoolean(KEY_USER_CONFIGURED, true)
                    .apply();
        }
    }

    /**
     * Khôi phục cả 2 địa chỉ máy chủ về mặc định Cloud Production khi người dùng bấm "Mặc định" trong cài đặt Server.
     */
    public static void resetToDefaults(Context context) {
        cachedEmarketUrl = PRESET_CUSTOM_DOMAIN;
        cachedAiUrl = PRESET_AI_AZURE;
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_EMARKET_BASE_URL, PRESET_CUSTOM_DOMAIN)
                    .putString(KEY_AI_BASE_URL, PRESET_AI_AZURE)
                    .putBoolean(KEY_USER_CONFIGURED, false)
                    .apply();
        }
    }
}

