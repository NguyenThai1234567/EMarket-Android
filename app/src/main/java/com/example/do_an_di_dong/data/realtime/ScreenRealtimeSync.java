package com.example.do_an_di_dong.data.realtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.do_an_di_dong.util.UserSessionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ điều khiển đồng bộ dữ liệu thời gian thực (Realtime) gắn theo vòng đời từng màn hình (Activity/Fragment).
 * Kết hợp lắng nghe sự kiện WebSocket từ SignalR ("orderHub") và cơ chế Polling dự phòng định kỳ mỗi 12 giây.
 * Chỉ duy trì kết nối khi màn hình đang hiển thị (onResume/onStart) để tiết kiệm tài nguyên và băng thông.
 */
public class ScreenRealtimeSync {
    private static final long POLL_INTERVAL_MS = 12_000L;
    private static final long COALESCE_MS = 350L;

    private final Context appContext;
    private final SignalRRealtimeClient orderHubClient;
    private final Runnable onSyncRequested;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean active = false;
    private boolean disposed = false;
    private int currentBranchId = 0;
    private boolean currentIsAdmin = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active || disposed) return;
            triggerSyncNow();
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable coalescedSyncRunnable;

    /**
     * Khởi tạo bộ đồng bộ Realtime cho màn hình với callback onSyncRequested (thường là hàm gọi lại API tải danh sách).
     */
    public ScreenRealtimeSync(@NonNull Context context, @NonNull Runnable onSyncRequested) {
        this.appContext = context.getApplicationContext();
        this.orderHubClient = new SignalRRealtimeClient(this.appContext, "orderHub");
        this.onSyncRequested = onSyncRequested;
        this.coalescedSyncRunnable = () -> {
            if (active && !disposed) {
                this.onSyncRequested.run();
            }
        };
    }

    /**
     * Bật hoặc tắt chế độ lắng nghe Realtime và Polling tùy theo trạng thái hiển thị của màn hình và phân quyền người dùng.
     * Kích hoạt trong onResume() (bật) hoặc onPause() (tắt) của các màn hình Đơn hàng, Dashboard, Kho...
     * Nhận vào trạng thái active mong muốn, branchId hiện tại và cờ quyền Admin (RBAC).
     */
    public void setActive(boolean shouldBeActive, int branchId, boolean isAdmin) {
        if (disposed) return;

        // Kiểm tra phiên đăng nhập hợp lệ trước khi cho phép mở kết nối đồng bộ
        boolean hasSession = UserSessionManager.getCurrentUser(appContext) != null;
        boolean effectiveActive = shouldBeActive && hasSession;

        if (!effectiveActive) {
            stopInternal();
            return;
        }

        boolean contextChanged = !active || currentBranchId != branchId || currentIsAdmin != isAdmin;
        active = true;
        currentBranchId = branchId;
        currentIsAdmin = isAdmin;

        // Khởi động lại chu kỳ Polling 12s nếu trạng thái hoặc phạm vi chi nhánh (branchId / quyền Admin) thay đổi
        if (contextChanged) {
            mainHandler.removeCallbacks(pollRunnable);
            mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }

        // Đóng gói thông tin chi nhánh và quyền Admin gửi lên SignalR Hub để Backend phân nhóm nhận thông báo
        Map<String, String> params = new LinkedHashMap<>();
        params.put("branchId", String.valueOf(Math.max(0, branchId)));
        params.put("isAdmin", String.valueOf(isAdmin));

        // Kết nối tới orderHub và kích hoạt tải lại dữ liệu khi nhận sự kiện "orderChanged" từ máy chủ
        orderHubClient.connect(params, (hub, method, args) -> {
            if (!active || disposed) return;
            if ("orderchanged".equalsIgnoreCase(method)) {
                triggerSyncNow();
            }
        });
    }

    /**
     * Hủy hoàn toàn bộ đồng bộ và giải phóng kết nối SignalR cùng các Handler callback.
     * Kích hoạt trong onDestroy() của Activity hoặc onDestroyView() của Fragment.
     */
    public void destroy() {
        disposed = true;
        stopInternal();
    }

    /**
     * Tạm dừng chu kỳ Polling, hủy các yêu cầu đồng bộ đang chờ và đóng kết nối WebSocket SignalR.
     */
    private void stopInternal() {
        active = false;
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.removeCallbacks(coalescedSyncRunnable);
        orderHubClient.disconnect();
    }

    /**
     * Gom nhóm (Debounce/Coalesce trong 350ms) các yêu cầu làm mới dữ liệu liên tiếp thành 1 lần gọi duy nhất trên Main Thread.
     * Tránh hiện tượng spam gọi REST API khi Backend bắn nhiều sự kiện SignalR cùng lúc với chu kỳ Polling.
     */
    private void triggerSyncNow() {
        if (!active || disposed) return;
        mainHandler.removeCallbacks(coalescedSyncRunnable);
        mainHandler.postDelayed(coalescedSyncRunnable, COALESCE_MS);
    }
}
