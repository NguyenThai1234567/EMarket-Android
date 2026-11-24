package com.example.do_an_di_dong;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.do_an_di_dong.data.api.NetworkInspectorServer;
import com.example.do_an_di_dong.ui.auth.LoginActivity;

/**
 * Application trung tâm của EMarket Android Java (tương đương hàm main() khởi tạo Global Context ở Backend):
 * 1. Khởi chạy NetworkInspectorServer (cổng 8088) ngay khi mở ứng dụng để lắng nghe và debug API Realtime.
 * 2. Gắn ActivityLifecycleCallbacks kết nối với AuthSessionGuard để kiểm tra hạn JWT/phiên đăng nhập khi chuyển màn hình.
 * 3. Tự động xử lý Edge-to-Edge WindowInsets (Status Bar, Camera Cutout, Navigation Bar, Bàn phím IME)
 *    cho toàn bộ 20 Activity trên Android 15/16/17 (targetSdk 37), tránh bị lẹm giao diện vào đồng hồ/pin/camera.
 */
public class EMarketApplication extends Application {

    /**
     * Hàm khởi tạo chạy một lần duy nhất khi tiến trình ứng dụng Android được hệ điều hành nạp lên.
     * Kích hoạt server giám sát mạng nội bộ và đăng ký bộ lắng nghe vòng đời (Lifecycle) cho toàn bộ Activity.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Khởi động máy chủ giám sát API Realtime (Port 8088) 
        NetworkInspectorServer.getInstance().start();

        // 2. Tự động căn chỉnh Safe Area (Status Bar + Camera Cutout + Navigation Bar) và kiểm tra phiên JWT cho mọi Activity
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                applySafeWindowInsets(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                // Uỷ quyền cho AuthSessionGuard kiểm tra trạng thái phiên đăng nhập khi Activity hiển thị
                com.example.do_an_di_dong.util.AuthSessionGuard
                        .getInstance(EMarketApplication.this)
                        .onActivityStarted(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                // Xác thực lại token/phiên khi người dùng quay lại màn hình từ nền (background)
                com.example.do_an_di_dong.util.AuthSessionGuard
                        .getInstance(EMarketApplication.this)
                        .onActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                com.example.do_an_di_dong.util.AuthSessionGuard
                        .getInstance(EMarketApplication.this)
                        .onActivityStopped(activity);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    /**
     * Tính toán và áp dụng khoảng đệm (padding) an toàn cho View gốc (android.R.id.content) của từng Activity.
     * Được gọi tự động trong onActivityCreated() để giao diện không bị thanh trạng thái, tai thỏ hoặc bàn phím ảo che khuất.
     */
    private void applySafeWindowInsets(@NonNull Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        // Phân biệt màn hình LoginActivity (nền tối gradient) với các màn hình nghiệp vụ (nền sáng) để đổi màu icon Status Bar
        boolean isLogin = activity instanceof LoginActivity;
        content.setBackgroundColor(activity.getColor(isLogin ? R.color.login_gradient_start : R.color.surface));

        if (activity.getWindow() != null) {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), content);
            if (controller != null) {
                controller.setAppearanceLightStatusBars(!isLogin);
                controller.setAppearanceLightNavigationBars(!isLogin);
            }
        }

        final int fallbackStatusBar = getStatusBarHeight(activity);

        // Áp dụng ngay khoảng đệm an toàn cho khung hình đầu tiên
        if (content.getPaddingTop() < fallbackStatusBar) {
            content.setPadding(content.getPaddingLeft(), fallbackStatusBar, content.getPaddingRight(), content.getPaddingBottom());
        }

        // Lắng nghe sự kiện thay đổi kích thước vùng hệ thống (ẩn/hiện bàn phím ảo IME hoặc xoay màn hình) để cập nhật padding động
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            int top = Math.max(systemBars.top, fallbackStatusBar);
            int bottom = Math.max(systemBars.bottom, ime.bottom);

            if (v.getPaddingLeft() != systemBars.left
                    || v.getPaddingTop() != top
                    || v.getPaddingRight() != systemBars.right
                    || v.getPaddingBottom() != bottom) {
                v.setPadding(systemBars.left, top, systemBars.right, bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Truy vấn chiều cao thanh Status Bar từ tài nguyên hệ thống Android (fallback tối thiểu 36dp).
     * Trả về giá trị pixel để tránh hiện tượng giật khung hình trước khi WindowInsets kịp phản hồi.
     */
    private int getStatusBarHeight(@NonNull Activity activity) {
        int result = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        int minPx = (int) (36 * activity.getResources().getDisplayMetrics().density);
        return Math.max(result, minPx);
    }
}

