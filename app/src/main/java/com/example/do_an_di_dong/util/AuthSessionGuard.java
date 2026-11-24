package com.example.do_an_di_dong.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.realtime.SignalRRealtimeClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.ui.auth.LoginActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bộ bảo vệ phiên đăng nhập thời gian thực (Singleton gắn với vòng đời Foreground của ứng dụng):
 * - Duy trì kết nối SignalR tới `systemLogHub` khi người dùng đã đăng nhập và app đang hiển thị trên màn hình.
 * - Lắng nghe sự kiện `forceLogout` / `userStatusChanged` từ Admin, hoặc lỗi HTTP 401/403 từ ApiClient
 *   để lập tức xóa JWT token, xóa session và điều hướng cưỡng bức về LoginActivity.
 * - Tự động gọi DataRepository kiểm tra lại trạng thái tài khoản mỗi khi người dùng mở lại app từ chế độ chạy ngầm.
 */
public class AuthSessionGuard {
    private static final String TAG = "AuthSessionGuard";
    private static final long KICK_DEBOUNCE_MS = 2_000L;

    private static volatile AuthSessionGuard instance;

    private final Context appContext;
    private final SignalRRealtimeClient systemHubClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isKickingOut = new AtomicBoolean(false);

    private int startedActivityCount = 0;
    private WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private int connectedUserId = -1;

    private AuthSessionGuard(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.systemHubClient = new SignalRRealtimeClient(this.appContext, "systemLogHub");
    }

    /**
     * Khởi tạo hoặc lấy thực thể Singleton của AuthSessionGuard (Thread-safe Double-Checked Locking).
     */
    public static AuthSessionGuard getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AuthSessionGuard.class) {
                if (instance == null) {
                    instance = new AuthSessionGuard(context);
                }
            }
        }
        return instance;
    }

    /**
     * Được gọi từ Application.ActivityLifecycleCallbacks khi bất kỳ Activity nào bước vào onStart().
     * Nếu app vừa từ background quay lại foreground và có phiên đăng nhập, sẽ kết nối lại SignalR và kiểm tra trạng thái user.
     */
    public void onActivityStarted(@NonNull Activity activity) {
        boolean enteringForeground = startedActivityCount == 0;
        startedActivityCount++;
        currentActivityRef = new WeakReference<>(activity);

        if (activity instanceof LoginActivity) {
            return;
        }

        if (hasActiveSession()) {
            connectSystemHubIfNeeded();
            if (enteringForeground) {
                verifyAccountStatusOnce();
            }
        }
    }

    /**
     * Được gọi khi Activity bước vào onResume(). Cập nhật tham chiếu Activity hiện hành và đảm bảo SignalR luôn kết nối.
     */
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
        if (activity instanceof LoginActivity) {
            return;
        }
        if (startedActivityCount > 0 && hasActiveSession()) {
            connectSystemHubIfNeeded();
        }
    }

    /**
     * Được gọi khi Activity bước vào onStop(). Khi toàn bộ Activity đều dừng (app xuống background),
     * tự động ngắt kết nối SignalR để tiết kiệm tài nguyên.
     */
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivityCount = Math.max(0, startedActivityCount - 1);
        if (startedActivityCount == 0) {
            disconnectSystemHub();
        }
    }

    /**
     * Được gọi ngay sau khi đăng nhập thành công tại LoginActivity để kích hoạt kết nối SignalR canh giữ phiên.
     */
    public void onLoginSuccess() {
        isKickingOut.set(false);
        if (startedActivityCount > 0 && hasActiveSession()) {
            connectSystemHubIfNeeded();
        }
    }

    /**
     * Được gọi khi người dùng chủ động bấm nút Đăng xuất để đóng kết nối SignalR sạch sẽ.
     */
    public void onManualSignOut() {
        disconnectSystemHub();
    }

    /**
     * Cưỡng bức đăng xuất người dùng ngay lập tức khi phát hiện token hết hạn (401), tài khoản bị khóa (403),
     * hoặc nhận lệnh kick realtime từ SignalR.
     * Xóa sạch JWT Token, xóa UserSessionManager và đóng gói Intent cờ CLEAR_TASK đẩy về LoginActivity.
     */
    public void triggerForceLogout(@Nullable String reason) {
        if (!hasActiveSession()) {
            return;
        }
        // Chống gọi trùng lặp liên tiếp bằng AtomicBoolean khi nhiều API cùng trả về 401 một lúc
        if (!isKickingOut.compareAndSet(false, true)) {
            return;
        }

        final String finalReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Phiên đăng nhập đã hết hạn hoặc tài khoản của bạn đã bị khóa.";

        Log.w(TAG, "Force logout triggered: " + finalReason);
        disconnectSystemHub();
        ApiClient.clearToken(appContext);
        UserSessionManager.clearSession(appContext);

        // Chuyển về Main Thread để hiển thị thông báo hoặc mở LoginActivity đồng thời xóa toàn bộ Back Stack
        mainHandler.post(() -> {
            Activity current = currentActivityRef.get();
            if (current instanceof LoginActivity && !current.isFinishing() && !current.isDestroyed()) {
                AppToast.show(current, finalReason);
                return;
            }
            Intent intent = new Intent(appContext, LoginActivity.class);
            intent.putExtra("force_login", true);
            intent.putExtra("kick_reason", finalReason);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            appContext.startActivity(intent);
        });

        mainHandler.postDelayed(() -> isKickingOut.set(false), KICK_DEBOUNCE_MS);
    }

    /**
     * Kiểm tra chuỗi trạng thái tài khoản trả về từ Backend có thuộc diện bị khóa hoặc vô hiệu hóa hay không.
     */
    public static boolean isLockedOrInactiveStatus(@Nullable String status) {
        if (status == null) return false;
        String norm = status.trim().toLowerCase(Locale.ROOT);
        return norm.equals("locked")
                || norm.equals("inactive")
                || norm.equals("disabled")
                || norm.equals("banned")
                || norm.equals("false")
                || norm.equals("0")
                || norm.contains("khóa")
                || norm.contains("khoá")
                || norm.contains("ngừng");
    }

    /**
     * Kiểm tra ứng dụng hiện có đang lưu giữ cả JWT Token hợp lệ lẫn thông tin User trong phiên hay không.
     */
    private boolean hasActiveSession() {
        String token = ApiClient.getToken(appContext);
        User user = UserSessionManager.getCurrentUser(appContext);
        return token != null && !token.isEmpty() && user != null && user.getUserId() > 0;
    }

    /**
     * Thiết lập kết nối SignalR tới `systemLogHub` kèm theo tham số userId của người dùng đang đăng nhập.
     */
    private void connectSystemHubIfNeeded() {
        int userId = UserSessionManager.getCurrentUserId(appContext);
        if (userId <= 0) return;

        if (connectedUserId != userId) {
            systemHubClient.disconnect();
            connectedUserId = userId;
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("userId", String.valueOf(userId));
        systemHubClient.connect(params, this::handleSystemHubEvent);
    }

    /**
     * Ngắt kết nối khỏi `systemLogHub` và đặt lại trạng thái connectedUserId.
     */
    private void disconnectSystemHub() {
        connectedUserId = -1;
        systemHubClient.disconnect();
    }

    /**
     * Callback xử lý các sự kiện realtime bắn về từ `systemLogHub` (SignalR).
     * Bắt 2 sự kiện quan trọng: `forceLogout` và `userStatusChanged` để đá người dùng ra nếu tài khoản bị khóa.
     */
    private void handleSystemHubEvent(String hubName, String methodName, JSONArray args) {
        if (!hasActiveSession()) return;
        int currentUserId = UserSessionManager.getCurrentUserId(appContext);

        if ("forceLogout".equalsIgnoreCase(methodName)) {
            String serverReason = args != null && args.length() > 0 ? args.optString(0, "") : "";
            triggerForceLogout(serverReason.isEmpty()
                    ? "Tài khoản của bạn đã bị khóa hoặc buộc đăng xuất bởi quản trị viên."
                    : serverReason);
            return;
        }

        if ("userStatusChanged".equalsIgnoreCase(methodName) && args != null && args.length() > 0) {
            JSONObject payload = args.optJSONObject(0);
            if (payload == null) return;
            int targetUserId = payload.optInt("userId", payload.optInt("UserId", -1));
            String newStatus = payload.optString("status", payload.optString("Status", ""));
            if (targetUserId == currentUserId && isLockedOrInactiveStatus(newStatus)) {
                triggerForceLogout("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động.");
            }
        }
    }

    /**
     * Gọi API `getUserById` thông qua DataRepository để hậu kiểm trạng thái tài khoản ngay khi mở lại app.
     * Nếu phát hiện trạng thái trong DB đã chuyển sang bị khóa/ngừng hoạt động, lập tức kích hoạt force logout.
     */
    private void verifyAccountStatusOnce() {
        int userId = UserSessionManager.getCurrentUserId(appContext);
        if (userId <= 0) return;

        new DataRepository(appContext).getUserById(userId, new DataRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user != null && user.getUserId() == userId && isLockedOrInactiveStatus(user.getStatus())) {
                    triggerForceLogout("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động.");
                }
            }

            @Override
            public void onError(String message) {
                // Silent ignore on transient network issues; HTTP 401/403 handled globally by ApiClient.
            }
        });
    }
}

