package com.example.do_an_di_dong.data.api;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * Máy chủ HTTP nội bộ chạy ngầm ngay trong ứng dụng Android (mặc định ở cổng 8088).
 * Đóng vai trò công cụ Debug & Giám sát toàn bộ lưu lượng REST API (Request, Response, Header, cURL, phân tích lỗi)
 * từ ApiClient thông qua OkHttp Interceptor. Cho phép lập trình viên mở trình duyệt trên máy tính
 * (qua lệnh `adb forward tcp:8088 tcp:8088` hoặc IP LAN Wi-Fi) để theo dõi log API trực tiếp theo thời gian thực.
 */
public class NetworkInspectorServer {
    private static final String TAG = "NetworkInspector";
    private static final NetworkInspectorServer INSTANCE = new NetworkInspectorServer();
    private static final int MAX_LOGS = 200;

    public static NetworkInspectorServer getInstance() {
        return INSTANCE;
    }

    public static class NetworkLogEntry {
        public String id;
        public String timestamp;
        public String timeFormatted;
        public String method;
        public String url;
        public String path;
        public Map<String, String> requestHeaders = new ConcurrentHashMap<>();
        public Map<String, String> queryParams = new ConcurrentHashMap<>();
        public Object requestBody;

        public Integer statusCode;
        public String statusMessage;
        public Long durationMs;
        public Object responseBody;
        public Map<String, String> responseHeaders = new ConcurrentHashMap<>();

        public boolean isError = false;
        public String errorCategory = "PENDING";
        public String errorSummary;
        public String errorDetails;
        public String curlCommand;
    }

    private ServerSocket serverSocket;
    private int actualPort = 8088;
    private volatile boolean isRunning = false;
    private volatile boolean isStarting = false;
    private final Object logsLock = new Object();
    private final List<NetworkLogEntry> logs = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private NetworkInspectorServer() {}

    public int getActualPort() {
        return actualPort;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Quét danh sách card mạng trên thiết bị Android để lấy địa chỉ IPv4 LAN (ưu tiên giao diện wlan/eth/ap).
     * Dùng để in đường dẫn truy cập Web Dashboard qua mạng Wi-Fi nội bộ ra Logcat khi khởi động server.
     */
    public static String getDeviceLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallbackIp = null;
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                String name = iface.getName() != null ? iface.getName().toLowerCase(Locale.US) : "";
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) {
                            return host;
                        }
                        if (fallbackIp == null) {
                            fallbackIp = host;
                        }
                    }
                }
            }
            return fallbackIp;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Khởi chạy ServerSocket lắng nghe kết nối HTTP trên luồng nền (thử lần lượt các cổng 8088, 8089, 8090, 8888).
     * Được gọi tự động bởi ApiClient.getEMarketService() và ApiClient.getAiService() khi ứng dụng bắt đầu gọi API.
     */
    public synchronized void start() {
        if (isRunning || isStarting) return;
        isStarting = true;

        executor.execute(() -> {
            ServerSocket boundSocket = null;
            try {
                // Thử bind lần lượt các cổng dự phòng nếu cổng 8088 mặc định đang bị chiếm dụng
                int[] candidatePorts = {8088, 8089, 8090, 8888};
                for (int port : candidatePorts) {
                    try {
                        ServerSocket ss = new ServerSocket();
                        ss.setReuseAddress(true);
                        ss.bind(new InetSocketAddress(port));
                        boundSocket = ss;
                        serverSocket = ss;
                        actualPort = port;
                        isRunning = true;
                        break;
                    } catch (IOException e) {
                        Log.d(TAG, "Port " + port + " busy: " + e.getMessage());
                    }
                }
            } finally {
                isStarting = false;
            }

            if (!isRunning || boundSocket == null) {
                Log.w(TAG, "⚠️ [INSPECTOR] Không thể mở cổng 8088-8888");
                return;
            }

            String lanIp = getDeviceLanIp();
            Log.i(TAG, "======================================================================");
            Log.i(TAG, "🚀 [EMARKET NETWORK INSPECTOR] Bộ giám sát API & hứng lỗi Realtime");
            Log.i(TAG, "👉 Chạy lệnh: adb forward tcp:" + actualPort + " tcp:" + actualPort);
            Log.i(TAG, "👉 Trình duyệt PC (ADB): http://127.0.0.1:" + actualPort);
            if (lanIp != null) {
                Log.i(TAG, "👉 Trình duyệt cùng Wi-Fi LAN: http://" + lanIp + ":" + actualPort);
            }
            Log.i(TAG, "======================================================================");

            // Vòng lặp chấp nhận kết nối HTTP từ trình duyệt và đẩy sang thread pool xử lý
            while (isRunning && !boundSocket.isClosed()) {
                try {
                    Socket client = boundSocket.accept();
                    executor.execute(() -> handleClient(client));
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Accept error: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Xử lý từng kết nối HTTP từ trình duyệt gửi vào cổng Inspector.
     * Định tuyến các đường dẫn: "/" trả về giao diện HTML Dashboard, "/api/logs" trả về JSON danh sách log,
     * và "/api/clear" để xóa toàn bộ lịch sử log đang lưu trong bộ nhớ.
     */
    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String fullPath = parts.length > 1 ? parts[1] : "/";
            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;

            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && "content-length".equalsIgnoreCase(line.substring(0, colon).trim())) {
                    try {
                        contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (contentLength > 0 && contentLength <= 65536) {
                char[] skipBuf = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int n = reader.read(skipBuf, totalRead, contentLength - totalRead);
                    if (n < 0) break;
                    totalRead += n;
                }
            }

            // Định tuyến các endpoint nội bộ phục vụ giao diện Web Dashboard giám sát mạng
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(out, 200, "OK", "text/plain; charset=utf-8", "");
            } else if ("/favicon.ico".equals(path)) {
                sendResponse(out, 204, "No Content", "text/plain; charset=utf-8", "");
            } else if ("/".equals(path) || "/index.html".equals(path)) {
                sendResponse(out, 200, "OK", "text/html; charset=utf-8", buildWebDashboardHtml());
            } else if ("/api/logs".equals(path)) {
                String json;
                synchronized (logsLock) {
                    json = gson.toJson(new ArrayList<>(logs));
                }
                sendResponse(out, 200, "OK", "application/json; charset=utf-8", json);
            } else if ("/api/clear".equals(path)) {
                synchronized (logsLock) {
                    logs.clear();
                }
                sendResponse(out, 200, "OK", "application/json; charset=utf-8", "{\"success\":true}");
            } else {
                sendResponse(out, 404, "Not Found", "text/plain; charset=utf-8", "Not found");
            }
            try {
                socket.shutdownOutput();
            } catch (IOException ignored) {
            }
        } catch (Exception e) {
            Log.d(TAG, "Client handler closed: " + e.getMessage());
        }
    }

    /**
     * Đóng gói và ghi phản hồi HTTP chuẩn (kèm CORS headers) ra luồng OutputStream của Socket.
     */
    private void sendResponse(OutputStream out, int code, String status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: *\r\n"
                + "Cache-Control: no-cache, no-store, must-revalidate\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        if (bytes.length > 0) {
            out.write(bytes);
        }
        out.flush();
    }

    /**
     * Tạo OkHttp Interceptor để gắn vào chuỗi xử lý của ApiClient.
     * Tự động chặn bắt thời gian thực thi, ghi nhận thông tin Request trước khi gửi và cập nhật Response/Exception sau khi hoàn tất.
     */
    public Interceptor createInterceptor() {
        return chain -> {
            Request request = chain.request();
            long startNs = System.nanoTime();
            NetworkLogEntry entry = null;
            try {
                entry = logRequest(request);
            } catch (Throwable t) {
                Log.w(TAG, "logRequest skipped: " + t.getMessage());
            }

            try {
                Response response = chain.proceed(request);
                long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                if (entry != null) {
                    try {
                        logResponse(entry, response, durationMs);
                    } catch (Throwable t) {
                        Log.w(TAG, "logResponse skipped: " + t.getMessage());
                    }
                }
                return response;
            } catch (Exception e) {
                long durationMs = (System.nanoTime() - startNs) / 1_000_000;
                if (entry != null) {
                    try {
                        logError(entry, e, durationMs);
                    } catch (Throwable t) {
                        Log.w(TAG, "logError skipped: " + t.getMessage());
                    }
                }
                throw e;
            }
        };
    }

    /**
     * Trích xuất thông tin Request (URL, Method, Headers, Query Params, Body, lệnh cURL) và đẩy vào đầu danh sách log.
     * Giới hạn tối đa MAX_LOGS (200 bản ghi gần nhất) để tránh tràn bộ nhớ RAM.
     */
    private NetworkLogEntry logRequest(Request request) {
        NetworkLogEntry entry = new NetworkLogEntry();
        Date now = new Date();
        entry.id = now.getTime() + "_" + System.nanoTime();
        entry.timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(now);
        entry.timeFormatted = new SimpleDateFormat("HH:mm:ss", Locale.US).format(now);
        entry.method = request.method().toUpperCase(Locale.US);
        entry.url = request.url().toString();
        entry.path = request.url().encodedPath();

        Headers headers = request.headers();
        for (int i = 0; i < headers.size(); i++) {
            entry.requestHeaders.put(headers.name(i), headers.value(i));
        }

        HttpUrl url = request.url();
        Set<String> paramNames = url.queryParameterNames();
        for (String name : paramNames) {
            String v = url.queryParameter(name);
            if (v != null) entry.queryParams.put(name, v);
        }

        String rawReqBody = readRequestBody(request.body());
        entry.requestBody = parseJsonOrString(rawReqBody);
        entry.curlCommand = generateCurl(request, rawReqBody);

        // Lưu bản ghi vào đầu danh sách và cắt bỏ các log cũ nếu vượt quá giới hạn MAX_LOGS
        synchronized (logsLock) {
            logs.add(0, entry);
            while (logs.size() > MAX_LOGS) {
                logs.remove(logs.size() - 1);
            }
        }
        return entry;
    }

    /**
     * Cập nhật kết quả phản hồi HTTP (Status Code, thời gian xử lý, Headers, Body) vào bản ghi log tương ứng.
     * Nếu mã trạng thái >= 400 sẽ tự động đánh dấu lỗi và phân loại nguyên nhân.
     */
    private void logResponse(NetworkLogEntry entry, Response response, long durationMs) {
        entry.statusCode = response.code();
        entry.statusMessage = response.message() != null && !response.message().isEmpty()
                ? response.message()
                : getStatusMessage(response.code());
        entry.durationMs = durationMs;

        Headers headers = response.headers();
        for (int i = 0; i < headers.size(); i++) {
            entry.responseHeaders.put(headers.name(i), headers.value(i));
        }

        String rawBody = readResponseBody(response);
        entry.responseBody = parseJsonOrString(rawBody);

        if (response.code() >= 400) {
            entry.isError = true;
            categorizeHttpError(entry, response.code(), rawBody);
        } else {
            entry.isError = false;
            entry.errorCategory = "SUCCESS";
        }
    }

    /**
     * Ghi nhận và chẩn đoán nguyên nhân lỗi kết nối tầng mạng (Timeout, Cleartext HTTP bị chặn, hoặc Server chưa bật)
     * khi OkHttp ném ra IOException trước khi nhận được phản hồi từ Backend.
     */
    private void logError(NetworkLogEntry entry, Exception e, long durationMs) {
        entry.durationMs = durationMs;
        entry.isError = true;
        entry.statusCode = 0;
        entry.statusMessage = "NETWORK ERR";

        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.toLowerCase(Locale.US).contains("timeout")) {
            entry.errorCategory = "TIMEOUT";
            entry.errorSummary = "Quá thời gian chờ phản hồi (Timeout)";
            entry.errorDetails = "Chi tiết: " + msg;
        } else if (msg.toLowerCase(Locale.US).contains("cleartext")) {
            entry.errorCategory = "CLEARTEXT_BLOCKED";
            entry.errorSummary = "Android chặn kết nối HTTP không mã hóa (Cleartext)";
            entry.errorDetails = "Chi tiết: " + msg;
        } else if (msg.toLowerCase(Locale.US).contains("refused") || msg.toLowerCase(Locale.US).contains("failed to connect")) {
            entry.errorCategory = "CANNOT_CONNECT_SERVER";
            entry.errorSummary = "Không thể kết nối đến máy chủ Backend (" + e.getClass().getSimpleName() + ")";
            entry.errorDetails = "Chi tiết: " + msg + "\nKiểm tra xem Backend EMarket đã bật chưa hoặc cấu hình URL trong ServerSettingsDialog.";
        } else {
            entry.errorCategory = "NETWORK_ERROR";
            entry.errorSummary = "Lỗi kết nối mạng (" + e.getClass().getSimpleName() + ")";
            entry.errorDetails = msg;
        }
    }

    /**
     * Phân loại mã lỗi HTTP (400, 401, 403, 404, 409, 500...) thành thông báo tiếng Việt dễ hiểu trên Web Dashboard.
     */
    private void categorizeHttpError(NetworkLogEntry entry, int status, String rawBody) {
        String snippet = rawBody != null && rawBody.length() > 300 ? rawBody.substring(0, 300) + "..." : rawBody;
        switch (status) {
            case 400:
                entry.errorCategory = "VALIDATION_ERROR";
                entry.errorSummary = "Dữ liệu gửi lên không hợp lệ hoặc sai Hostname (400 Bad Request)";
                entry.errorDetails = snippet != null && !snippet.isEmpty() ? snippet : "Server ASP.NET từ chối do dữ liệu không khớp ModelState.";
                break;
            case 401:
                entry.errorCategory = "AUTH_EXPIRED";
                entry.errorSummary = "Chưa đăng nhập hoặc phiên làm việc đã hết hạn (401 Unauthorized)";
                entry.errorDetails = "Token JWT bị thiếu, không hợp lệ hoặc hết hạn.";
                break;
            case 403:
                entry.errorCategory = "FORBIDDEN";
                entry.errorSummary = "Không đủ quyền hạn truy cập API này (403 Forbidden)";
                entry.errorDetails = snippet;
                break;
            case 404:
                entry.errorCategory = "NOT_FOUND";
                entry.errorSummary = "Không tìm thấy đường dẫn API trên máy chủ (404 Not Found)";
                entry.errorDetails = "Đường dẫn [" + entry.path + "] không tồn tại trên Backend EMarket.";
                break;
            case 409:
                entry.errorCategory = "CONFLICT";
                entry.errorSummary = "Xung đột dữ liệu (409 Conflict)";
                entry.errorDetails = snippet;
                break;
            case 500:
            case 502:
            case 503:
                entry.errorCategory = "SERVER_CRASH";
                entry.errorSummary = "Máy chủ Backend gặp sự cố nội bộ (" + status + " Server Error)";
                entry.errorDetails = snippet;
                break;
            default:
                entry.errorCategory = "HTTP_ERROR";
                entry.errorSummary = "Lỗi HTTP mã " + status;
                entry.errorDetails = snippet;
                break;
        }
    }

    /**
     * Đọc nội dung RequestBody sang chuỗi UTF-8 để hiển thị log (bỏ qua nếu là Multipart upload file hoặc payload > 256KB).
     */
    private String readRequestBody(RequestBody body) {
        if (body == null) return null;
        if (body.isOneShot()) return "[OneShot Stream]";
        try {
            long len = body.contentLength();
            if (len > 262144) {
                return "[Large Payload: " + len + " bytes]";
            }
            MediaType mt = body.contentType();
            if (mt != null && "multipart".equalsIgnoreCase(mt.type())) {
                return "[Multipart FormData: " + (len >= 0 ? len + " bytes" : "stream") + "]";
            }
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            return "[Binary/FormData Payload]";
        }
    }

    /**
     * Đọc bản sao (clone buffer tối đa 512KB) của ResponseBody mà không làm tiêu hao luồng dữ liệu trả về cho Retrofit/Gson.
     */
    private String readResponseBody(Response response) {
        ResponseBody body = response.body();
        if (body == null) return null;
        try {
            BufferedSource source = body.source();
            source.request(1024 * 512); // Peek up to 512KB
            Buffer buffer = source.getBuffer().clone();
            return buffer.readUtf8();
        } catch (Exception e) {
            return "[Không thể đọc Response Body]";
        }
    }

    /**
     * Thử parse chuỗi raw thành đối tượng JsonElement để format đẹp trên giao diện web; nếu không phải JSON thì giữ nguyên chuỗi.
     */
    private Object parseJsonOrString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return JsonParser.parseString(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * Sinh câu lệnh cURL tương ứng với Request để lập trình viên có thể copy chạy thử lại trên Terminal/Postman.
     */
    private String generateCurl(Request request, String rawBody) {
        StringBuilder sb = new StringBuilder("curl -X ")
                .append(request.method().toUpperCase(Locale.US))
                .append(" \"").append(request.url()).append("\"");
        Headers headers = request.headers();
        for (int i = 0; i < headers.size(); i++) {
            sb.append(" -H \"").append(headers.name(i)).append(": ").append(headers.value(i)).append("\"");
        }
        if (rawBody != null && !rawBody.isEmpty()) {
            sb.append(" -d \"").append(rawBody.replace("\"", "\\\"")).append("\"");
        }
        return sb.toString();
    }

    /**
     * Ánh xạ mã HTTP status code sang chuỗi mô tả chuẩn (OK, Bad Request, Unauthorized...).
     */
    private String getStatusMessage(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 500: return "Internal Server Error";
            default: return "HTTP " + code;
        }
    }

    /**
     * Trả về mã nguồn trang đơn (SPA HTML/CSS/JS) của giao diện Web Dashboard giám sát API.
     * Tự động gọi AJAX `/api/logs` mỗi 800ms để cập nhật danh sách request theo thời gian thực.
     */
    private String buildWebDashboardHtml() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"vi\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>EMarket Android Java - API & Error Live Monitor</title>\n"
                + "  <style>\n"
                + "    :root { --bg-dark:#0f172a; --bg-card:#1e293b; --bg-hover:#334155; --border-color:#334155; --text-main:#f8fafc; --text-muted:#94a3b8; --primary:#6366f1; --success:#10b981; --warning:#f59e0b; --danger:#ef4444; --info:#0ea5e9; }\n"
                + "    * { box-sizing:border-box; margin:0; padding:0; }\n"
                + "    body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; background:var(--bg-dark); color:var(--text-main); height:100vh; display:flex; flex-direction:column; overflow:hidden; }\n"
                + "    header { background:var(--bg-card); border-bottom:1px solid var(--border-color); padding:12px 20px; display:flex; align-items:center; justify-content:space-between; gap:16px; }\n"
                + "    .header-title { display:flex; align-items:center; gap:12px; font-weight:700; font-size:1.1rem; color:#fff; }\n"
                + "    .status-pulse { width:10px; height:10px; border-radius:50%; background:var(--success); box-shadow:0 0 10px var(--success); animation:pulse 1.5s infinite; }\n"
                + "    @keyframes pulse { 0%{transform:scale(0.95);opacity:0.8;} 50%{transform:scale(1.2);opacity:1;} 100%{transform:scale(0.95);opacity:0.8;} }\n"
                + "    .header-controls { display:flex; align-items:center; gap:12px; }\n"
                + "    input[type='text'] { background:#0f172a; border:1px solid var(--border-color); border-radius:6px; padding:7px 12px; color:#fff; font-size:0.85rem; width:260px; outline:none; }\n"
                + "    .btn { background:#334155; color:#fff; border:none; border-radius:6px; padding:7px 14px; font-size:0.85rem; font-weight:600; cursor:pointer; }\n"
                + "    .btn-danger { background:rgba(239,68,68,0.2); color:#f87171; border:1px solid rgba(239,68,68,0.4); }\n"
                + "    .filter-btn { font-size:0.78rem; padding:5px 10px; border-radius:20px; cursor:pointer; background:#1e293b; border:1px solid var(--border-color); color:var(--text-muted); }\n"
                + "    .filter-btn.active { background:var(--primary); color:#fff; border-color:var(--primary); }\n"
                + "    .main-container { display:flex; flex:1; overflow:hidden; }\n"
                + "    .sidebar { width:440px; border-right:1px solid var(--border-color); display:flex; flex-direction:column; background:#0f172a; }\n"
                + "    .sidebar-filter-bar { padding:10px 14px; border-bottom:1px solid var(--border-color); display:flex; gap:8px; background:#141e33; }\n"
                + "    .log-list { flex:1; overflow-y:auto; list-style:none; }\n"
                + "    .log-item { padding:12px 14px; border-bottom:1px solid rgba(51,65,85,0.4); cursor:pointer; display:flex; flex-direction:column; gap:6px; }\n"
                + "    .log-item:hover { background:var(--bg-hover); }\n"
                + "    .log-item.active { background:#1e293b; border-left:4px solid var(--primary); }\n"
                + "    .log-item-header { display:flex; align-items:center; justify-content:space-between; }\n"
                + "    .badge { font-size:0.72rem; font-weight:700; padding:2px 7px; border-radius:4px; text-transform:uppercase; }\n"
                + "    .badge-get { background:rgba(14,165,233,0.2); color:#0ea5e9; }\n"
                + "    .badge-post { background:rgba(16,185,129,0.2); color:#10b981; }\n"
                + "    .badge-put { background:rgba(245,158,11,0.2); color:#f59e0b; }\n"
                + "    .badge-delete { background:rgba(239,68,68,0.2); color:#ef4444; }\n"
                + "    .status-badge { font-size:0.72rem; font-weight:700; padding:2px 8px; border-radius:4px; }\n"
                + "    .status-2xx { background:rgba(16,185,129,0.2); color:var(--success); }\n"
                + "    .status-4xx { background:rgba(245,158,11,0.2); color:var(--warning); }\n"
                + "    .status-5xx, .status-err { background:rgba(239,68,68,0.25); color:var(--danger); font-weight:800; }\n"
                + "    .status-pending { background:#334155; color:#cbd5e1; }\n"
                + "    .log-item-path { font-size:0.88rem; font-weight:600; color:#e2e8f0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }\n"
                + "    .log-item-footer { display:flex; align-items:center; justify-content:space-between; font-size:0.75rem; color:var(--text-muted); }\n"
                + "    .log-item-err-tag { color:var(--danger); font-weight:600; font-size:0.74rem; }\n"
                + "    .detail-view { flex:1; display:flex; flex-direction:column; background:var(--bg-card); overflow-y:auto; }\n"
                + "    .empty-state { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; color:var(--text-muted); gap:12px; }\n"
                + "    .detail-header { padding:16px 20px; border-bottom:1px solid var(--border-color); display:flex; justify-content:space-between; align-items:flex-start; background:#141e33; }\n"
                + "    .detail-url-title { font-size:1.0rem; font-weight:700; word-break:break-all; margin-bottom:8px; }\n"
                + "    .detail-meta-tags { display:flex; gap:10px; align-items:center; font-size:0.8rem; color:var(--text-muted); }\n"
                + "    .error-alert-box { margin:16px 20px 0 20px; padding:14px 16px; background:rgba(239,68,68,0.12); border:1px solid rgba(239,68,68,0.4); border-radius:8px; color:#fca5a5; }\n"
                + "    .error-alert-title { font-weight:700; font-size:0.95rem; margin-bottom:6px; color:#ef4444; }\n"
                + "    .error-alert-desc { font-size:0.85rem; line-height:1.4; color:#fee2e2; white-space:pre-wrap; }\n"
                + "    .tab-bar { display:flex; border-bottom:1px solid var(--border-color); padding:0 20px; background:#1e293b; margin-top:14px; }\n"
                + "    .tab-btn { padding:10px 16px; background:none; border:none; color:var(--text-muted); font-size:0.85rem; font-weight:600; cursor:pointer; border-bottom:2px solid transparent; }\n"
                + "    .tab-btn.active { color:var(--primary); border-bottom:2px solid var(--primary); }\n"
                + "    .tab-content { padding:20px; flex:1; }\n"
                + "    .code-box { background:#090d16; border:1px solid var(--border-color); border-radius:8px; padding:14px; font-family:Consolas,monospace; font-size:0.82rem; line-height:1.5; color:#cbd5e1; white-space:pre-wrap; word-break:break-all; max-height:420px; overflow-y:auto; }\n"
                + "    .section-label { font-size:0.82rem; font-weight:700; text-transform:uppercase; color:var(--text-muted); margin:14px 0 6px 0; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header>\n"
                + "    <div class='header-title'>\n"
                + "      <div class='status-pulse'></div>\n"
                + "      <span>EMarket Android Java - Network & Error Monitor</span>\n"
                + "      <span style='font-size:0.75rem;background:#334155;padding:2px 8px;border-radius:12px;color:#94a3b8;'>Port " + actualPort + " (Live)</span>\n"
                + "    </div>\n"
                + "    <div class='header-controls'>\n"
                + "      <input type='text' id='searchInput' placeholder='Tìm theo đường dẫn, status hoặc từ khóa...'>\n"
                + "      <button class='btn btn-danger' onclick='clearAllLogs()'>Xóa logs</button>\n"
                + "    </div>\n"
                + "  </header>\n"
                + "  <div class='main-container'>\n"
                + "    <div class='sidebar'>\n"
                + "      <div class='sidebar-filter-bar'>\n"
                + "        <button class='filter-btn active' data-filter='ALL' onclick=\"setFilter('ALL')\">Tất cả (<span id='countAll'>0</span>)</button>\n"
                + "        <button class='filter-btn' data-filter='ERROR' onclick=\"setFilter('ERROR')\">Chỉ Lỗi (<span id='countError' style='color:#ef4444;'>0</span>)</button>\n"
                + "        <button class='filter-btn' data-filter='2XX' onclick=\"setFilter('2XX')\">2xx Thành công</button>\n"
                + "      </div>\n"
                + "      <ul class='log-list' id='logList'></ul>\n"
                + "    </div>\n"
                + "    <div class='detail-view' id='detailView'>\n"
                + "      <div class='empty-state'><div style='font-size:3rem;opacity:0.4;'>📡</div><p>Bấm vào một request ở cột bên trái để xem chi tiết.</p></div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "  <script>\n"
                + "    let logs = [];\n"
                + "    let selectedId = null;\n"
                + "    let currentFilter = 'ALL';\n"
                + "    let currentTab = 'RESPONSE';\n"
                + "    let lastHash = '';\n"
                + "    async function pollLogs() {\n"
                + "      try {\n"
                + "        const res = await fetch('/api/logs');\n"
                + "        const data = await res.json();\n"
                + "        const hash = data.map(x => x.id + ':' + x.statusCode).join('|');\n"
                + "        if (hash !== lastHash) {\n"
                + "          lastHash = hash;\n"
                + "          logs = data;\n"
                + "          renderList();\n"
                + "          if (selectedId) {\n"
                + "            const item = logs.find(l => l.id === selectedId);\n"
                + "            if (item) renderDetail(item);\n"
                + "          }\n"
                + "        }\n"
                + "      } catch (e) {}\n"
                + "    }\n"
                + "    setInterval(pollLogs, 800);\n"
                + "    pollLogs();\n"
                + "    function setFilter(f) {\n"
                + "      currentFilter = f;\n"
                + "      document.querySelectorAll('.filter-btn').forEach(b => b.classList.toggle('active', b.dataset.filter === f));\n"
                + "      renderList();\n"
                + "    }\n"
                + "    document.getElementById('searchInput').addEventListener('input', renderList);\n"
                + "    function getFilteredLogs() {\n"
                + "      const q = document.getElementById('searchInput').value.trim().toLowerCase();\n"
                + "      return logs.filter(item => {\n"
                + "        if (currentFilter === 'ERROR' && !item.isError) return false;\n"
                + "        if (currentFilter === '2XX' && (item.statusCode < 200 || item.statusCode >= 300)) return false;\n"
                + "        if (q) {\n"
                + "          return (item.url||'').toLowerCase().includes(q) || String(item.statusCode||'').includes(q) || (item.errorSummary||'').toLowerCase().includes(q);\n"
                + "        }\n"
                + "        return true;\n"
                + "      });\n"
                + "    }\n"
                + "    function renderList() {\n"
                + "      const listEl = document.getElementById('logList');\n"
                + "      const filtered = getFilteredLogs();\n"
                + "      document.getElementById('countAll').innerText = logs.length;\n"
                + "      document.getElementById('countError').innerText = logs.filter(l => l.isError).length;\n"
                + "      listEl.innerHTML = '';\n"
                + "      if (!filtered.length) { listEl.innerHTML = '<li style=\"padding:30px;text-align:center;color:#64748b;font-size:0.85rem;\">Chưa có request nào</li>'; return; }\n"
                + "      filtered.forEach(item => {\n"
                + "        const li = document.createElement('li');\n"
                + "        li.className = 'log-item' + (selectedId === item.id ? ' active' : '');\n"
                + "        li.onclick = () => { selectedId = item.id; renderList(); renderDetail(item); };\n"
                + "        let sc = 'status-pending', st = 'PENDING...';\n"
                + "        if (item.statusCode !== null && item.statusCode !== undefined) {\n"
                + "          if (item.statusCode === 0) { sc = 'status-err'; st = 'NETWORK ERR'; }\n"
                + "          else if (item.statusCode >= 200 && item.statusCode < 300) { sc = 'status-2xx'; st = item.statusCode + ' ' + (item.statusMessage||'OK'); }\n"
                + "          else if (item.statusCode >= 400 && item.statusCode < 500) { sc = 'status-4xx'; st = item.statusCode + ' ' + (item.statusMessage||'BAD'); }\n"
                + "          else { sc = 'status-5xx'; st = item.statusCode + ' ' + (item.statusMessage||'ERR'); }\n"
                + "        }\n"
                + "        li.innerHTML = `<div class='log-item-header'><span class='badge badge-${(item.method||'get').toLowerCase()}'>${item.method}</span><span class='status-badge ${sc}'>${st}</span></div><div class='log-item-path' title='${item.url}'>${item.path}</div><div class='log-item-footer'><span>${item.timeFormatted||''}</span>${item.isError ? `<span class='log-item-err-tag'>⚠️ ${item.errorCategory}</span>` : ''}<span>${item.durationMs != null ? item.durationMs + ' ms' : ''}</span></div>`;\n"
                + "        listEl.appendChild(li);\n"
                + "      });\n"
                + "    }\n"
                + "    function renderDetail(item) {\n"
                + "      const c = document.getElementById('detailView');\n"
                + "      if (!item) return;\n"
                + "      const sb = item.isError ? `<span class='status-badge status-err'>${item.statusCode||0} ${item.statusMessage||'FAILED'}</span>` : `<span class='status-badge status-2xx'>${item.statusCode||200} ${item.statusMessage||'OK'}</span>`;\n"
                + "      const errBox = item.isError ? `<div class='error-alert-box'><div class='error-alert-title'>⚠️ PHÂN TÍCH NGUYÊN NHÂN: [${item.errorCategory}]</div><div class='error-alert-desc'><strong>${escapeHtml(item.errorSummary||'')}</strong>\\n${escapeHtml(item.errorDetails||'')}</div></div>` : '';\n"
                + "      c.innerHTML = `<div class='detail-header'><div style='flex:1;'><div class='detail-url-title'><span class='badge badge-${(item.method||'get').toLowerCase()}'>${item.method}</span> ${escapeHtml(item.url)}</div><div class='detail-meta-tags'>${sb}<span>⏱ ${item.durationMs != null ? item.durationMs + ' ms' : ''}</span><span>🕒 ${item.timeFormatted||''}</span></div></div></div>${errBox}<div class='tab-bar'><button class='tab-btn ${currentTab==='RESPONSE'?'active':''}' onclick=\"switchTab('RESPONSE')\">Response Data</button><button class='tab-btn ${currentTab==='REQUEST'?'active':''}' onclick=\"switchTab('REQUEST')\">Request Payload</button><button class='tab-btn ${currentTab==='HEADERS'?'active':''}' onclick=\"switchTab('HEADERS')\">Headers & Params</button><button class='tab-btn ${currentTab==='CURL'?'active':''}' onclick=\"switchTab('CURL')\">cURL</button></div><div class='tab-content'>${renderTabContent(item)}</div>`;\n"
                + "    }\n"
                + "    function switchTab(t) { currentTab = t; const item = logs.find(l => l.id === selectedId); if (item) renderDetail(item); }\n"
                + "    function renderTabContent(item) {\n"
                + "      if (currentTab === 'RESPONSE') return `<div class='section-label'>Response Body</div><div class='code-box'>${escapeHtml(item.responseBody ? JSON.stringify(item.responseBody, null, 2) : 'No Content')}</div>`;\n"
                + "      if (currentTab === 'REQUEST') return `<div class='section-label'>Request Body</div><div class='code-box'>${escapeHtml(item.requestBody ? JSON.stringify(item.requestBody, null, 2) : 'Empty')}</div>`;\n"
                + "      if (currentTab === 'HEADERS') return `<div class='section-label'>Request Headers</div><div class='code-box'>${escapeHtml(JSON.stringify(item.requestHeaders||{}, null, 2))}</div><div class='section-label'>Query Params</div><div class='code-box'>${escapeHtml(JSON.stringify(item.queryParams||{}, null, 2))}</div><div class='section-label'>Response Headers</div><div class='code-box'>${escapeHtml(JSON.stringify(item.responseHeaders||{}, null, 2))}</div>`;\n"
                + "      return `<div class='section-label'>cURL</div><div class='code-box'>${escapeHtml(item.curlCommand||'')}</div>`;\n"
                + "    }\n"
                + "    function escapeHtml(s) { return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }\n"
                + "    async function clearAllLogs() { await fetch('/api/clear', {method:'POST'}); logs=[]; selectedId=null; lastHash=''; renderList(); }\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>";
    }
}

