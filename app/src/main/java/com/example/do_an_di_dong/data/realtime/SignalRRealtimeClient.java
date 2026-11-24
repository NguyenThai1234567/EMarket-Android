package com.example.do_an_di_dong.data.realtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.util.AppConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Client kết nối WebSocket theo giao thức ASP.NET SignalR v2 thuần (không phụ thuộc thư viện ngoài).
 * Thực hiện đầy đủ quy trình bắt tay 3 bước: /signalr/negotiate -> mở WebSocket /signalr/connect -> gọi /signalr/start.
 * Phục vụ ScreenRealtimeSync và RealtimeNotificationManager nhận sự kiện realtime từ Backend (orderHub, notificationHub).
 */
public class SignalRRealtimeClient {
    private static final String TAG = "SignalRRealtimeClient";
    private static final long RECONNECT_DELAY_MS = 15_000L;

    public interface HubMessageListener {
        void onHubMethod(String hubName, String methodName, JSONArray args);
    }

    private final Context appContext;
    private final String hubName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger generationCounter = new AtomicInteger(0);

    private WebSocket webSocket;
    private volatile boolean shouldBeConnected = false;
    private volatile boolean isConnected = false;
    private Map<String, String> activeQueryParams = Collections.emptyMap();
    private HubMessageListener messageListener;
    private Runnable pendingReconnect;

    /**
     * Khởi tạo client kết nối tới một SignalR Hub cụ thể trên Backend (VD: "orderHub" hoặc "notificationHub").
     */
    public SignalRRealtimeClient(@NonNull Context context, @NonNull String hubName) {
        this.appContext = context.getApplicationContext();
        this.hubName = hubName;
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Thiết lập kết nối tới SignalR Hub kèm theo các tham số truy vấn (như branchId, isAdmin, userId) và callback nhận tin nhắn.
     * Kích hoạt khi màn hình bắt đầu hiển thị hoặc khi dịch vụ thông báo ngầm khởi chạy.
     * Nếu kết nối đang mở với cùng bộ tham số thì giữ nguyên, ngược lại sẽ đóng kết nối cũ và bắt tay lại từ đầu.
     */
    public synchronized void connect(@Nullable Map<String, String> queryParams, @NonNull HubMessageListener listener) {
        Map<String, String> normalized = queryParams != null
                ? new LinkedHashMap<>(queryParams)
                : new LinkedHashMap<>();

        this.messageListener = listener;

        // Tránh tạo lại kết nối nếu socket hiện tại đang hoạt động với cùng bộ tham số queryParams
        if (shouldBeConnected && (isConnected || webSocket != null) && Objects.equals(this.activeQueryParams, normalized)) {
            return;
        }

        closeSocketInternal();
        cancelReconnectInternal();

        this.shouldBeConnected = true;
        this.activeQueryParams = normalized;
        // Tăng bộ đếm thế hệ (generation) để vô hiệu hóa các luồng kết nối/callback cũ còn sót lại
        int gen = generationCounter.incrementAndGet();
        startNegotiateAndConnect(gen, normalized);
    }

    /**
     * Ngắt kết nối WebSocket hiện tại và hủy mọi lịch trình tự động kết nối lại.
     * Kích hoạt khi người dùng rời màn hình, đăng xuất hoặc ứng dụng thu xuống nền.
     */
    public synchronized void disconnect() {
        shouldBeConnected = false;
        isConnected = false;
        messageListener = null;
        activeQueryParams = Collections.emptyMap();
        generationCounter.incrementAndGet();
        cancelReconnectInternal();
        closeSocketInternal();
    }

    /**
     * Thực hiện bước 1 & 2 của giao thức SignalR v2 trên luồng phụ:
     * Gọi HTTP GET /signalr/negotiate để lấy ConnectionToken, sau đó nâng cấp lên kết nối WebSocket tại /signalr/connect.
     */
    private void startNegotiateAndConnect(int generation, Map<String, String> queryParams) {
        new Thread(() -> {
            if (!shouldBeConnected || generation != generationCounter.get()) return;
            try {
                String baseUrl = normalizeBaseUrl(AppConfig.getEmarketBaseUrl(appContext));
                String connectionData = "[{\"name\":\"" + hubName + "\"}]";

                HttpUrl baseParsed = HttpUrl.parse(baseUrl);
                if (baseParsed == null) {
                    scheduleReconnect(generation);
                    return;
                }

                // Bước 1: Xây dựng URL và gọi API /signalr/negotiate để xin cấp ConnectionToken từ ASP.NET SignalR
                HttpUrl.Builder negotiateBuilder = baseParsed.newBuilder()
                        .addPathSegment("signalr")
                        .addPathSegment("negotiate")
                        .addQueryParameter("clientProtocol", "1.5")
                        .addQueryParameter("connectionData", connectionData)
                        .addQueryParameter("_", String.valueOf(System.currentTimeMillis()));
                appendQueryParams(negotiateBuilder, queryParams);

                OkHttpClient client = ApiClient.createRealtimeOkHttpClient(appContext);
                Request negotiateReq = new Request.Builder()
                        .url(negotiateBuilder.build())
                        .get()
                        .build();

                String connectionToken;
                try (Response res = client.newCall(negotiateReq).execute()) {
                    if (!res.isSuccessful() || res.body() == null) {
                        Log.w(TAG, "[" + hubName + "] Negotiate failed HTTP " + res.code());
                        scheduleReconnect(generation);
                        return;
                    }
                    String bodyStr = res.body().string();
                    JSONObject json = new JSONObject(bodyStr);
                    connectionToken = json.optString("ConnectionToken", "");
                }

                if (connectionToken.isEmpty() || !shouldBeConnected || generation != generationCounter.get()) {
                    scheduleReconnect(generation);
                    return;
                }

                // Bước 2: Chuyển đổi scheme http/https sang ws/wss và mở kết nối WebSocket kèm theo ConnectionToken
                String wsBase = baseUrl.startsWith("https://")
                        ? "wss://" + baseUrl.substring("https://".length())
                        : (baseUrl.startsWith("http://") ? "ws://" + baseUrl.substring("http://".length()) : baseUrl);

                StringBuilder wsUrl = new StringBuilder(wsBase)
                        .append("/signalr/connect?transport=webSockets&clientProtocol=1.5&connectionData=")
                        .append(urlEncode(connectionData))
                        .append("&connectionToken=")
                        .append(urlEncode(connectionToken))
                        .append("&tid=1");
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        wsUrl.append("&")
                                .append(urlEncode(entry.getKey()))
                                .append("=")
                                .append(urlEncode(entry.getValue()));
                    }
                }

                final String finalToken = connectionToken;
                Request wsReq = new Request.Builder().url(wsUrl.toString()).build();

                synchronized (SignalRRealtimeClient.this) {
                    if (!shouldBeConnected || generation != generationCounter.get()) return;
                    closeSocketInternal();
                    webSocket = client.newWebSocket(wsReq, new WebSocketListener() {
                        @Override
                        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                            if (!shouldBeConnected || generation != generationCounter.get()) {
                                ws.close(1000, "Stale generation");
                                return;
                            }
                            isConnected = true;
                            Log.d(TAG, "[" + hubName + "] WebSocket connected");
                            // Khi WebSocket mở thành công, gọi tiếp bước 3 (/signalr/start) để hoàn tất bắt tay
                            triggerSignalRStart(baseParsed, connectionData, finalToken, queryParams, generation);
                        }

                        @Override
                        public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                            if (!shouldBeConnected || generation != generationCounter.get()) return;
                            handleFrame(text);
                        }

                        @Override
                        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                            ws.close(1000, null);
                        }

                        @Override
                        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                            isConnected = false;
                            if (shouldBeConnected && generation == generationCounter.get()) {
                                scheduleReconnect(generation);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                            isConnected = false;
                            Log.w(TAG, "[" + hubName + "] WebSocket failure: " + t.getMessage());
                            if (shouldBeConnected && generation == generationCounter.get()) {
                                scheduleReconnect(generation);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                isConnected = false;
                Log.w(TAG, "[" + hubName + "] Connect error: " + e.getMessage());
                scheduleReconnect(generation);
            }
        }, "SignalR-" + hubName).start();
    }

    /**
     * Bước 3 của giao thức SignalR v2: Gọi HTTP GET /signalr/start ngay sau khi WebSocket mở.
     * Kích hoạt hàm Hub.OnConnected() trên máy chủ ASP.NET để đưa client vào đúng Group (theo branchId / Role).
     */
    private void triggerSignalRStart(
            HttpUrl baseParsed,
            String connectionData,
            String connectionToken,
            Map<String, String> queryParams,
            int generation
    ) {
        new Thread(() -> {
            if (!shouldBeConnected || generation != generationCounter.get()) return;
            try {
                HttpUrl.Builder startBuilder = baseParsed.newBuilder()
                        .addPathSegment("signalr")
                        .addPathSegment("start")
                        .addQueryParameter("transport", "webSockets")
                        .addQueryParameter("clientProtocol", "1.5")
                        .addQueryParameter("connectionToken", connectionToken)
                        .addQueryParameter("connectionData", connectionData)
                        .addQueryParameter("_", String.valueOf(System.currentTimeMillis()));
                appendQueryParams(startBuilder, queryParams);

                OkHttpClient client = ApiClient.createRealtimeOkHttpClient(appContext);
                Request req = new Request.Builder().url(startBuilder.build()).get().build();
                try (Response res = client.newCall(req).execute()) {
                    Log.d(TAG, "[" + hubName + "] /signalr/start HTTP " + res.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "[" + hubName + "] /signalr/start error: " + e.getMessage());
            }
        }, "SignalR-Start-" + hubName).start();
    }

    /**
     * Phân tích cú pháp khung dữ liệu JSON (Frame) nhận được từ WebSocket SignalR.
     * Bỏ qua các gói tin keep-alive rỗng ("{}"), trích xuất mảng thông điệp "M" (chứa Hub "H", Method "M", Arguments "A")
     * và đẩy callback về Main Thread để cập nhật giao diện.
     */
    private void handleFrame(String rawFrame) {
        if (rawFrame == null) return;
        String trimmed = rawFrame.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed)) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(trimmed);
            JSONArray messages = obj.optJSONArray("M");
            if (messages == null || messages.length() == 0) {
                return;
            }
            // Duyệt qua từng thông điệp RPC do Backend phát xuống và chuyển tiếp lên UI Thread
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.optJSONObject(i);
                if (msg == null) continue;
                final String hub = msg.optString("H", hubName);
                final String method = msg.optString("M", "");
                final JSONArray args = msg.optJSONArray("A") != null ? msg.optJSONArray("A") : new JSONArray();
                if (!method.isEmpty()) {
                    mainHandler.post(() -> {
                        HubMessageListener listener = this.messageListener;
                        if (shouldBeConnected && listener != null) {
                            listener.onHubMethod(hub, method, args);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "[" + hubName + "] Ignored non-dispatch frame: " + e.getMessage());
        }
    }

    /**
     * Lên lịch tự động kết nối lại sau 15 giây (RECONNECT_DELAY_MS) khi kết nối mạng bị đứt hoặc negotiate thất bại.
     */
    private synchronized void scheduleReconnect(int generation) {
        if (!shouldBeConnected || generation != generationCounter.get()) return;
        cancelReconnectInternal();
        final Map<String, String> paramsCopy = new LinkedHashMap<>(activeQueryParams);
        pendingReconnect = () -> {
            if (shouldBeConnected && generation == generationCounter.get()) {
                startNegotiateAndConnect(generation, paramsCopy);
            }
        };
        mainHandler.postDelayed(pendingReconnect, RECONNECT_DELAY_MS);
    }

    /**
     * Hủy tác vụ hẹn giờ kết nối lại đang chờ trong Main Handler.
     */
    private void cancelReconnectInternal() {
        if (pendingReconnect != null) {
            mainHandler.removeCallbacks(pendingReconnect);
            pendingReconnect = null;
        }
    }

    /**
     * Đóng WebSocket hiện tại một cách an toàn (mã 1000 Normal Closure) và đặt lại trạng thái kết nối.
     */
    private void closeSocketInternal() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client disconnected");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
        isConnected = false;
    }

    /**
     * Nối danh sách tham số query (branchId, isAdmin...) vào HttpUrl.Builder.
     */
    private static void appendQueryParams(HttpUrl.Builder builder, Map<String, String> queryParams) {
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Chuẩn hóa chuỗi Base URL bằng cách cắt bỏ khoảng trắng thừa và dấu gạch chéo '/' ở cuối.
     */
    private static String normalizeBaseUrl(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Mã hóa chuỗi tham số sang định dạng URL-encoded (UTF-8) để truyền an toàn trên thanh địa chỉ WebSocket.
     */
    private static String urlEncode(String val) {
        try {
            return URLEncoder.encode(val != null ? val : "", StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return val != null ? val : "";
        }
    }
}
