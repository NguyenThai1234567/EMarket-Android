package com.example.do_an_di_dong.util;

import android.util.Log;

import org.json.JSONObject;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import retrofit2.Response;

/**
 * Tiện ích chuẩn hóa thông báo lỗi HTTP/Network dùng chung cho toàn bộ ứng dụng.
 * Gắn chặt với tầng DataRepository và các callback Retrofit (ApiCallback): ghi log kỹ thuật đầy đủ
 * ra Logcat để debug, nhưng bóc tách JSON lỗi từ ASP.NET / AI Service thành thông báo tiếng Việt
 * thân thiện hiển thị lên UI (AppToast / Dialog), không lộ mã lỗi thô hay stacktrace.
 */
public final class ApiErrorFormatter {
    private static final String TAG = "ApiErrorFormatter";

    private ApiErrorFormatter() {
    }

    /**
     * Phân tích phản hồi lỗi HTTP từ Retrofit (khi !response.isSuccessful()).
     * Kích hoạt tại các callback onResponse() trong DataRepository và các Activity/Fragment gọi API.
     * Nhận vào đối tượng Response<?> và thông báo mặc định, trả về chuỗi thông báo lỗi tiếng Việt đã làm sạch.
     */
    public static String fromHttpResponse(Response<?> response, String defaultErrorMsg) {
        String baseMsg = (defaultErrorMsg != null && !defaultErrorMsg.trim().isEmpty())
                ? defaultErrorMsg.trim()
                : "Thao tác không thành công";

        if (response == null) {
            return baseMsg + ". Vui lòng thử lại.";
        }

        int code = response.code();
        String url = response.raw() != null && response.raw().request() != null
                ? response.raw().request().url().encodedPath()
                : "";
        String rawBody = readErrorBodySafely(response);
        Log.w(TAG, "HTTP " + code + " [" + url + "] body=" + rawBody);

        // Bóc tách message từ JSON body của Backend và lọc bỏ chuỗi lỗi kỹ thuật (.NET/SQL)
        String extracted = extractJsonOrCleanText(rawBody);
        String sanitized = filterTechnicalNoise(extracted);

        // Rẽ nhánh theo mã HTTP 401: phân biệt lỗi sai thông tin đăng nhập với lỗi hết hạn JWT token
        if (code == 401) {
            String lowerUrl = url != null ? url.toLowerCase(Locale.ROOT) : "";
            if (lowerUrl.contains("/auth/login")
                    || lowerUrl.contains("/google-login")
                    || lowerUrl.contains("/external-login")) {
                return sanitized != null ? sanitized : "Tài khoản hoặc mật khẩu không chính xác.";
            }
            return "Bạn không có quyền truy cập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        }

        // Rẽ nhánh mã HTTP 403 (RBAC / khóa tài khoản): kiểm tra xem tài khoản bị khóa hay thiếu quyền Role
        if (code == 403) {
            String combinedLower = ((extracted != null ? extracted : "") + " " + rawBody).toLowerCase(Locale.ROOT);
            if (AuthSessionGuard.isLockedOrInactiveStatus(combinedLower)
                    || combinedLower.contains("revoked")) {
                return "Tài khoản của bạn đã bị khóa hoặc thu hồi quyền truy cập.";
            }
            if (sanitized != null) {
                return sanitized;
            }
            return "Bạn không có quyền truy cập chức năng này.";
        }

        if (code == 400 || code == 422) {
            return sanitized != null ? sanitized : baseMsg + ". Vui lòng kiểm tra lại thông tin đã nhập.";
        }

        if (code == 404) {
            return sanitized != null ? sanitized : "Không tìm thấy dữ liệu hoặc chức năng được yêu cầu.";
        }

        if (code == 409) {
            return sanitized != null ? sanitized : "Dữ liệu bị xung đột hoặc đã tồn tại trong hệ thống.";
        }

        if (code == 429) {
            return "Hệ thống đang quá tải hoặc nhận quá nhiều yêu cầu. Vui lòng đợi ít giây rồi thử lại.";
        }

        if (code == 502 || code == 503 || code == 504) {
            return "Máy chủ tạm thời quá tải hoặc đang khởi động. Vui lòng thử lại sau ít phút.";
        }

        if (code >= 500) {
            if (sanitized != null) {
                return sanitized;
            }
            return baseMsg + ". Hệ thống máy chủ đang gặp sự cố, vui lòng thử lại sau.";
        }

        return sanitized != null ? sanitized : baseMsg + ". Vui lòng thử lại.";
    }

    /**
     * Chuyển đổi ngoại lệ tầng mạng (Throwable) thành thông báo tiếng Việt dễ hiểu.
     * Kích hoạt trong callback onFailure() của Retrofit khi đứt mạng, timeout, sai IP/Domain hoặc lỗi SSL.
     * Nhận vào ngữ cảnh thao tác và Throwable, trả về câu thông báo lỗi tương ứng.
     */
    public static String fromNetworkFailure(String defaultContextMsg, Throwable t) {
        Log.w(TAG, "Network failure [" + defaultContextMsg + "]: " + (t != null ? t.getMessage() : "unknown"), t);

        // Kiểm tra chuỗi nguyên nhân (cause chain) để phân loại lỗi Timeout, SSL hay mất kết nối tới Server
        if (hasCause(t, SocketTimeoutException.class)
                || hasCause(t, InterruptedIOException.class)
                || containsIgnoreCase(t, "timeout")) {
            return "Kết nối tới máy chủ quá thời gian chờ. Vui lòng thử lại.";
        }
        if (hasCause(t, SSLHandshakeException.class) || hasCause(t, SSLException.class)) {
            return "Lỗi kết nối bảo mật HTTPS với máy chủ. Vui lòng kiểm tra cấu hình Server.";
        }
        if (hasCause(t, ConnectException.class)
                || hasCause(t, UnknownHostException.class)
                || containsIgnoreCase(t, "unable to resolve host")
                || containsIgnoreCase(t, "failed to connect")
                || containsIgnoreCase(t, "connection refused")
                || containsIgnoreCase(t, "network is unreachable")) {
            return "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng hoặc cấu hình Server.";
        }

        if (defaultContextMsg != null && !defaultContextMsg.trim().isEmpty()) {
            return defaultContextMsg.trim() + ". Vui lòng kiểm tra kết nối mạng và thử lại.";
        }
        return "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.";
    }

    /**
     * Làm sạch thông báo lỗi nghiệp vụ trả về từ Backend, nếu chứa từ khóa kỹ thuật sẽ dùng câu fallback.
     * Được gọi khi xử lý message trong body JSON của các API trả về success=false.
     */
    public static String sanitizeBusinessMessage(String msg, String fallback) {
        String clean = filterTechnicalNoise(msg);
        return clean != null ? clean : fallback;
    }

    /**
     * Lọc và thay thế các thông báo lỗi kỹ thuật thô từ Gemini/Groq/Gateway (429 Quota, API Key, 502)
     * đôi khi bị lọt vào nội dung câu trả lời của Trợ lý AI (AiAssistantActivity).
     */
    public static String filterAiAnswerText(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("resource_exhausted") || (lower.contains("quota") && lower.contains("429"))) {
            return "⚠️ Hệ thống AI đang có lượng người hỏi lớn hoặc chạm hạn ngạch tạm thời. Bạn vui lòng chờ 15–30 giây rồi hỏi lại nhé.";
        }
        if (lower.contains("api key") && (lower.contains("invalid") || lower.contains("not found") || lower.contains("chưa cấu hình"))) {
            return "⚠️ Cấu hình API Key của Trợ lý AI cần được kiểm tra lại trên máy chủ.";
        }
        if (lower.contains("bad gateway") || lower.contains("502 web server received")) {
            return "⚠️ Máy chủ AI đang khởi động hoặc tạm thời chưa sẵn sàng. Vui lòng thử lại sau ít giây.";
        }
        return trimmed;
    }

    /**
     * Đọc nội dung chuỗi từ response.errorBody() an toàn, bắt mọi ngoại lệ IO để không làm crash luồng xử lý lỗi.
     */
    private static String readErrorBodySafely(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                return raw != null ? raw.trim() : "";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Bóc tách thông điệp lỗi từ chuỗi JSON của Backend ASP.NET (duyệt đệ quy InnerException hoặc các trường message/detail).
     * Bỏ qua nếu body là trang lỗi HTML (bắt đầu bằng '<').
     */
    private static String extractJsonOrCleanText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("<")) {
            return null;
        }
        if (trimmed.startsWith("{")) {
            try {
                JSONObject json = new JSONObject(trimmed);
                String innerMsg = "";
                // Đào sâu tối đa 5 cấp InnerException của ASP.NET để lấy nguyên nhân gốc
                JSONObject cursor = json.optJSONObject("InnerException");
                int depth = 0;
                while (cursor != null && depth < 5) {
                    String candidate = cursor.optString("ExceptionMessage",
                            cursor.optString("Message", ""));
                    if (candidate != null && !candidate.trim().isEmpty()) {
                        innerMsg = candidate.trim();
                    }
                    cursor = cursor.optJSONObject("InnerException");
                    depth++;
                }
                if (!innerMsg.isEmpty()) {
                    return innerMsg;
                }
                String exMsg = json.optString("ExceptionMessage", "").trim();
                if (!exMsg.isEmpty()) {
                    return exMsg;
                }
                String[] keys = {"answer", "message", "Message", "title", "error_description", "detail"};
                for (String key : keys) {
                    String val = json.optString(key, "").trim();
                    if (!val.isEmpty()) {
                        return val;
                    }
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
        return trimmed.length() > 180 ? trimmed.substring(0, 180) + "..." : trimmed;
    }

    /**
     * Loại bỏ các chuỗi lỗi mặc định bằng tiếng Anh hoặc chứa vết exception/SQL/stacktrace của hệ thống.
     * Trả về null nếu phát hiện nhiễu kỹ thuật để hàm gọi tự fallback sang câu tiếng Việt chuẩn.
     */
    private static String filterTechnicalNoise(String rawMsg) {
        if (rawMsg == null) {
            return null;
        }
        String clean = rawMsg.trim();
        if (clean.isEmpty()) {
            return null;
        }

        String filteredAi = filterAiAnswerText(clean);
        if (!filteredAi.equals(clean)) {
            return filteredAi;
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.equals("an error has occurred.")
                || lower.equals("authorization has been denied for this request.")
                || lower.equals("unauthorized")
                || lower.equals("forbidden")
                || lower.equals("not found")
                || lower.equals("bad request")
                || lower.equals("internal server error")
                || lower.contains("exception")
                || lower.contains("stacktrace")
                || lower.contains("idx10")
                || lower.contains("multiple actions were found")
                || lower.contains("system.")
                || lower.contains("reference constraint")
                || lower.contains("sql")
                || lower.startsWith("error while ")) {
            return null;
        }

        // Strip trailing raw status hints like "(Mã: 401)" or "(401)"
        clean = clean.replaceAll("\\s*\\((?:Mã:\\s*)?\\d{3}\\)", "").trim();
        return clean.isEmpty() ? null : clean;
    }

    /**
     * Duyệt chuỗi getCause() tối đa 10 cấp để kiểm tra xem Throwable có bắt nguồn từ loại Exception chỉ định hay không.
     */
    private static boolean hasCause(Throwable t, Class<? extends Throwable> target) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 10) {
            if (target.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    /**
     * Kiểm tra message trong chuỗi getCause() của Throwable có chứa từ khóa needle (không phân biệt hoa thường) hay không.
     */
    private static boolean containsIgnoreCase(Throwable t, String needle) {
        Throwable cur = t;
        int depth = 0;
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        while (cur != null && depth < 10) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains(lowerNeedle)) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }
}
