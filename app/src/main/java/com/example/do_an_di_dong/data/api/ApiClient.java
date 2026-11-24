package com.example.do_an_di_dong.data.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.do_an_di_dong.model.ExternalLoginRequest;
import com.example.do_an_di_dong.model.LoginResponse;
import com.example.do_an_di_dong.util.AppConfig;
import com.example.do_an_di_dong.util.AuthSessionGuard;
import com.example.do_an_di_dong.util.UserSessionManager;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Quản lý khởi tạo và cấu hình Retrofit / OkHttpClient trung tâm cho toàn ứng dụng.
 * Phục vụ các Repository (DataRepository, AuthRepository...) và các Activity/ViewModel gọi REST API
 * tới EMarket Core Backend (ASP.NET), AI Assistant Service và kết nối WebSocket SignalR.
 * Tự động đính kèm JWT Bearer Token, đồng bộ Cookie, xử lý lỗi 401/403 (Force Logout) và gắn NetworkInspectorServer.
 */
public class ApiClient {
    private static final String PREF_NAME = "EMarketPrefs";
    private static final String KEY_TOKEN = "jwt_token";

    public static final String DEFAULT_EMARKET_BASE_URL = AppConfig.PRESET_CUSTOM_DOMAIN;
    public static final String EMULATOR_EMARKET_BASE_URL = AppConfig.PRESET_EMULATOR_LOCAL;
    public static final String KEY_CUSTOM_BASE_URL = "custom_base_url";

    private static Retrofit emarketRetrofit = null;
    private static String currentEmarketBaseUrl = null;

    private static Retrofit aiRetrofit = null;
    private static String currentAiBaseUrl = null;

    private static final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

    /**
     * Khởi tạo OkHttpClient.Builder bỏ qua kiểm tra chứng chỉ SSL (Trust All Certificates).
     * Kích hoạt khi tạo kết nối tới máy chủ EMarket hoặc AI (hỗ trợ môi trường dev/local IIS Express dùng self-signed cert).
     * Trả về đối tượng OkHttpClient.Builder đã cấu hình SSL và timeout cơ bản.
     */
    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            // Cấu hình TrustManager chấp nhận mọi chứng chỉ SSL để không bị chặn khi gọi HTTPS local/dev
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.connectTimeout(20, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Đính kèm các HTTP Header vận chuyển (Host cho IIS Express và Authorization Bearer Token).
     * Được gọi tự động trong Interceptor của mọi request REST API và SignalR.
     */
    private static void applyTransportHeaders(Request.Builder builder, HttpUrl reqUrl, String token) {
        // Ghi đè Host header thành localhost:54446 để tránh lỗi HTTP 400 Bad Request từ IIS Express khi gọi qua IP LAN/Emulator
        if (reqUrl.port() == 54446 && !"localhost".equalsIgnoreCase(reqUrl.host())) {
            builder.header("Host", "localhost:54446");
        }
        // Đính kèm JWT Token vào header Authorization để xác thực quyền truy cập RBAC trên Backend
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    /**
     * Kiểm tra đường dẫn URL có thuộc nhóm API xác thực (đăng nhập, quên mật khẩu, OTP) hay không.
     * Dùng để loại trừ các endpoint này khỏi cơ chế tự động đăng xuất khi gặp mã lỗi 401/403.
     */
    private static boolean isAuthEndpointPath(String encodedPath) {
        if (encodedPath == null) return false;
        String lower = encodedPath.toLowerCase(Locale.ROOT);
        return lower.contains("/api/auth/login")
                || lower.contains("/external-login")
                || lower.contains("/google-login")
                || lower.contains("/forgot-password")
                || lower.contains("/verify-otp")
                || lower.contains("/reset-password");
    }

    /**
     * Kiểm tra phản hồi HTTP từ Backend để phát hiện lỗi hết hạn phiên (401) hoặc khóa tài khoản (403).
     * Được kích hoạt ngay sau khi nhận Response trong OkHttp Interceptor của EMarket Service.
     */
    private static void handleAuthFailureResponse(Context appCtx, HttpUrl reqUrl, String token, Response response) {
        if (appCtx == null || token == null || token.isEmpty() || isAuthEndpointPath(reqUrl.encodedPath())) {
            return;
        }
        int code = response.code();
        // Nếu HTTP 401 Unauthorized: Token hết hạn hoặc không hợp lệ -> kích hoạt AuthSessionGuard ép đăng xuất về màn hình Login
        if (code == 401) {
            android.util.Log.w("ApiClient", "HTTP 401 on " + reqUrl.encodedPath() + " -> triggering force logout");
            AuthSessionGuard.getInstance(appCtx).triggerForceLogout(
                    "Bạn không có quyền truy cập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            return;
        }
        // Nếu HTTP 403 Forbidden: Kiểm tra nội dung lỗi xem tài khoản có bị Admin khóa/thu hồi quyền hay không
        if (code == 403) {
            try {
                String bodyLower = response.peekBody(4096).string().toLowerCase(Locale.ROOT);
                if (bodyLower.contains("khóa")
                        || bodyLower.contains("khoá")
                        || bodyLower.contains("locked")
                        || bodyLower.contains("inactive")
                        || bodyLower.contains("ngừng hoạt động")
                        || bodyLower.contains("vô hiệu")
                        || bodyLower.contains("revoked")
                        || bodyLower.contains("banned")
                        || bodyLower.contains("disabled")) {
                    AuthSessionGuard.getInstance(appCtx).triggerForceLogout(
                            "Tài khoản của bạn đã bị khóa hoặc thu hồi quyền truy cập.");
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Tạo OkHttpClient chuyên dụng cho kết nối Realtime SignalR (WebSocket & Negotiate).
     * Được gọi bởi SignalRRealtimeClient khi thiết lập kết nối tới các Hub (orderHub, notificationHub...).
     * Trả về OkHttpClient có readTimeout = 0 (giữ kết nối mở liên tục) và tự động gửi ping mỗi 25 giây.
     */
    public static OkHttpClient createRealtimeOkHttpClient(Context context) {
        final Context appCtx = context != null ? context.getApplicationContext() : null;
        return getUnsafeOkHttpClientBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder();
                    String token = appCtx != null ? getToken(appCtx) : null;
                    applyTransportHeaders(builder, original.url(), token);
                    return chain.proceed(builder.build());
                })
                .build();
    }

    /**
     * Cung cấp instance Singleton của ApiService kết nối tới EMarket Core Backend.
     * Được gọi bởi DataRepository và các Activity/ViewModel khi cần thực hiện các nghiệp vụ bán hàng, kho, nhân sự.
     * Tự động khởi tạo lại Retrofit nếu người dùng đổi Base URL trong phần cài đặt máy chủ (ServerSettingsDialog).
     */
    public static synchronized ApiService getEMarketService(Context context) {
        NetworkInspectorServer.getInstance().start();
        String baseUrl = AppConfig.getEmarketBaseUrl(context);
        // Khởi tạo mới hoặc tái tạo Retrofit khi Base URL thay đổi
        if (emarketRetrofit == null || !baseUrl.equals(currentEmarketBaseUrl)) {
            currentEmarketBaseUrl = baseUrl;
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            final Context appCtx = context != null ? context.getApplicationContext() : null;
            OkHttpClient client = getUnsafeOkHttpClientBuilder()
                    .cookieJar(new CookieJar() {
                        @Override
                        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                            cookieStore.put(url.host(), cookies);
                        }

                        @Override
                        public List<Cookie> loadForRequest(HttpUrl url) {
                            List<Cookie> cookies = cookieStore.get(url.host());
                            return cookies != null ? cookies : new ArrayList<>();
                        }
                    })
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            String token = appCtx != null ? getToken(appCtx) : null;
                            Request original = chain.request();
                            HttpUrl reqUrl = original.url();
                            Request.Builder builder = original.newBuilder();
                            builder.addHeader("Accept", "application/json");
                            // Fix lỗi IIS Express trả về HTTP 400 Bad Request - Invalid Hostname
                            // khi gọi qua 10.0.2.2:54446 (Emulator) hoặc IP LAN 192.168.x.x:54446
                            applyTransportHeaders(builder, reqUrl, token);
                            Response response = chain.proceed(builder.build());
                            handleAuthFailureResponse(appCtx, reqUrl, token, response);
                            return response;
                        }
                    })
                    .addInterceptor(NetworkInspectorServer.getInstance().createInterceptor())
                    .addInterceptor(logging)
                    .build();

            emarketRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return emarketRetrofit.create(ApiService.class);
    }

    /**
     * Cung cấp instance Singleton của ApiService kết nối tới máy chủ AI Assistant.
     * Được gọi khi người dùng chat với trợ lý AI hoặc chạy phân tích dự báo tồn kho/doanh thu.
     * Cấu hình timeout dài hơn (read 90s, write 60s) để chờ mô hình AI xử lý xong.
     */
    public static synchronized ApiService getAiService(Context context) {
        NetworkInspectorServer.getInstance().start();
        String aiUrl = AppConfig.getAiBaseUrl(context);
        if (aiRetrofit == null || !aiUrl.equals(currentAiBaseUrl)) {
            currentAiBaseUrl = aiUrl;
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            final Context appCtx = context != null ? context.getApplicationContext() : null;
            OkHttpClient client = getUnsafeOkHttpClientBuilder()
                    .readTimeout(90, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            String token = appCtx != null ? getToken(appCtx) : null;
                            Request.Builder builder = chain.request().newBuilder();
                            if (token != null && !token.isEmpty()) {
                                builder.addHeader("Authorization", "Bearer " + token);
                            }
                            builder.addHeader("Accept", "application/json");
                            return chain.proceed(builder.build());
                        }
                    })
                    .addInterceptor(NetworkInspectorServer.getInstance().createInterceptor())
                    .addInterceptor(logging)
                    .build();

            aiRetrofit = new Retrofit.Builder()
                    .baseUrl(aiUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return aiRetrofit.create(ApiService.class);
    }

    /**
     * Xóa các instance Retrofit hiện tại để buộc khởi tạo lại với Base URL mới.
     * Kích hoạt khi người dùng lưu cấu hình địa chỉ máy chủ mới trong ServerSettingsDialog.
     */
    public static synchronized void refreshClients(Context context) {
        emarketRetrofit = null;
        aiRetrofit = null;
        currentEmarketBaseUrl = AppConfig.getEmarketBaseUrl(context);
        currentAiBaseUrl = AppConfig.getAiBaseUrl(context);
    }

    /**
     * Lưu chuỗi JWT Token vào SharedPreferences sau khi đăng nhập thành công.
     */
    public static void saveToken(Context context, String token) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    /**
     * Đọc chuỗi JWT Token hiện tại từ SharedPreferences để đính kèm vào Header Authorization.
     */
    public static String getToken(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, null);
    }

    /**
     * Xóa JWT Token trong SharedPreferences và dọn sạch Cookie phiên làm việc.
     * Kích hoạt khi người dùng bấm Đăng xuất hoặc bị buộc đăng xuất do lỗi 401/403.
     */
    public static void clearToken(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(KEY_TOKEN).apply();
        }
        cookieStore.clear();
    }

    public static String getBaseUrl(Context context) {
        return AppConfig.getEmarketBaseUrl(context);
    }

    public static String getBaseUrl() {
        return currentEmarketBaseUrl != null ? currentEmarketBaseUrl : AppConfig.PRESET_CUSTOM_DOMAIN;
    }

    /**
     * Cập nhật địa chỉ Base URL của máy chủ EMarket và làm mới toàn bộ Retrofit client.
     */
    public static void setBaseUrl(Context context, String newBaseUrl) {
        AppConfig.updateEmarketBaseUrl(context, newBaseUrl);
        refreshClients(context);
    }

    /**
     * Chuẩn hóa đường dẫn ảnh tương đối từ Backend (VD: "~/Uploads/..." hoặc "/images/...") thành URL tuyệt đối.
     * Được gọi bởi các Adapter/Activity trước khi truyền URL cho thư viện Glide tải ảnh sản phẩm, avatar, chứng từ.
     */
    public static String getFullImageUrl(Context context, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }
        String path = relativePath.trim();
        // Nếu đã là URL tuyệt đối (http/https) thì giữ nguyên
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        // Loại bỏ ký tự '~' hoặc '/' ở đầu đường dẫn tương đối của ASP.NET trước khi nối với Base URL
        if (path.startsWith("~")) {
            path = path.substring(1);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String baseUrl = context != null ? getBaseUrl(context) : getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + path;
    }

    public static String getFullImageUrl(String relativePath) {
        return getFullImageUrl(null, relativePath);
    }

    /**
     * Gọi API đăng nhập bằng tài khoản bên thứ ba (Google / External Provider).
     * Đóng gói thông tin provider, email, idToken vào ExternalLoginRequest và trả về Call<LoginResponse>.
     */
    public static Call<LoginResponse> externalLogin(Context context, String provider, String email, String idToken) {
        ExternalLoginRequest request = new ExternalLoginRequest(provider, email, idToken, null);
        return getEMarketService(context).externalLogin(request);
    }
}
