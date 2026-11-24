package com.example.do_an_di_dong.ui.common;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.api.NetworkInspectorServer;
import com.example.do_an_di_dong.util.AppConfig;
import com.example.do_an_di_dong.util.AuthSessionGuard;
import com.example.do_an_di_dong.util.FormValidator;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog cấu hình địa chỉ máy chủ EMarket Core API (.NET Backend) & AI Assistant Service.
 * Gắn với layout dialog_server_settings.xml, tương tác với AppConfig, ApiClient, NetworkInspectorServer và AuthSessionGuard.
 * Cho phép đổi nhanh Base URL (Cloud / Azure / Máy ảo 10.0.2.2 / USB Localhost) ngay khi đang chấm đồ án mà không cần build lại app.
 */
public class ServerSettingsDialog {

    /**
     * Mở hộp thoại cấu hình máy chủ mà không cần truyền callback xử lý sau khi lưu.
     */
    public static void show(Context context) {
        show(context, null);
    }

    /**
     * Hiển thị hộp thoại cấu hình URL máy chủ (kích hoạt từ màn hình Đăng nhập hoặc menu Drawer "Cấu hình máy chủ").
     * Hiển thị trạng thái cổng Web API Inspector, cho phép chọn chip Preset URL, kiểm tra định dạng HTTP/HTTPS và lưu vào AppConfig.
     */
    public static void show(Context context, Runnable onSavedCallback) {
        if (context == null) return;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_server_settings, null);
        EditText etEmarket = view.findViewById(R.id.etEmarketBaseUrl);
        EditText etAi = view.findViewById(R.id.etAiBaseUrl);

        TextView chipPrimary = view.findViewById(R.id.chipPresetPrimaryDomain);
        TextView chipAzure = view.findViewById(R.id.chipPresetAzureBackup);
        TextView chipLocal = view.findViewById(R.id.chipPresetLocalEmulator);
        TextView chipLocalHost = view.findViewById(R.id.chipPresetLocalHost);

        TextView chipAiAzure = view.findViewById(R.id.chipPresetAiAzure);
        TextView chipAiLocalHost = view.findViewById(R.id.chipPresetAiLocalHost);
        TextView chipAiLocal = view.findViewById(R.id.chipPresetAiLocal);

        MaterialButton btnReset = view.findViewById(R.id.btnResetServerDefaults);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveServerConfig);

        TextView tvInspectorStatus = view.findViewById(R.id.tvInspectorStatus);
        TextView tvInspectorLanUrl = view.findViewById(R.id.tvInspectorLanUrl);
        TextView tvInspectorAdbCmd = view.findViewById(R.id.tvInspectorAdbCmd);

        // Kiểm tra và hiển thị thông tin kết nối tới Web API Inspector nội bộ (cổng 8088) để theo dõi log HTTP trên trình duyệt PC
        NetworkInspectorServer inspector = NetworkInspectorServer.getInstance();
        inspector.start();
        int port = inspector.getActualPort();
        boolean running = inspector.isRunning();
        String lanIp = NetworkInspectorServer.getDeviceLanIp();

        if (tvInspectorStatus != null) {
            tvInspectorStatus.setText(running
                    ? "📡 Web API Inspector đang bật tại cổng " + port
                    : "📡 Web API Inspector đang khởi động ở cổng " + port + "...");
        }
        if (tvInspectorLanUrl != null && lanIp != null && !lanIp.isEmpty()) {
            String lanUrl = "http://" + lanIp + ":" + port;
            tvInspectorLanUrl.setVisibility(View.VISIBLE);
            tvInspectorLanUrl.setText("• Trình duyệt cùng Wi-Fi LAN: " + lanUrl);
            tvInspectorLanUrl.setOnClickListener(v -> copyText(context, "Inspector LAN URL", lanUrl));
        }
        if (tvInspectorAdbCmd != null) {
            String adbCmd = "adb forward tcp:" + port + " tcp:" + port;
            tvInspectorAdbCmd.setText("• USB / Máy ảo: " + adbCmd + " → http://127.0.0.1:" + port);
            tvInspectorAdbCmd.setOnClickListener(v -> copyText(context, "ADB Forward Command", adbCmd));
        }

        etEmarket.setText(AppConfig.getEmarketBaseUrl(context));
        etAi.setText(AppConfig.getAiBaseUrl(context));

        FormValidator urlValidator = FormValidator.create()
                .httpUrl(null, etEmarket, "EMarket Core API URL", true)
                .httpUrl(null, etAi, "AI Assistant Service URL", true);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .create();

        chipPrimary.setOnClickListener(v -> etEmarket.setText(AppConfig.PRESET_CUSTOM_DOMAIN));
        chipAzure.setOnClickListener(v -> etEmarket.setText(AppConfig.PRESET_AZURE_LEGACY));
        chipLocal.setOnClickListener(v -> etEmarket.setText(AppConfig.PRESET_EMULATOR_LOCAL));
        if (chipLocalHost != null) {
            chipLocalHost.setOnClickListener(v -> etEmarket.setText(AppConfig.PRESET_USB_LOCAL));
        }

        chipAiAzure.setOnClickListener(v -> etAi.setText(AppConfig.PRESET_AI_AZURE));
        if (chipAiLocalHost != null) {
            chipAiLocalHost.setOnClickListener(v -> etAi.setText(AppConfig.PRESET_AI_USB_LOCAL));
        }
        chipAiLocal.setOnClickListener(v -> etAi.setText(AppConfig.PRESET_AI_LOCAL));

        // Nút Khôi phục mặc định: trả URL về cấu hình gốc và làm mới Retrofit instance trong ApiClient
        btnReset.setOnClickListener(v -> {
            String prevEmarketUrl = AppConfig.getEmarketBaseUrl(context);
            AppConfig.resetToDefaults(context);
            ApiClient.refreshClients(context);
            etEmarket.setText(AppConfig.getEmarketBaseUrl(context));
            etAi.setText(AppConfig.getAiBaseUrl(context));
            if (handleEmarketServerChange(context, prevEmarketUrl)) {
                dialog.dismiss();
                return;
            }
            com.example.do_an_di_dong.util.AppToast.show(context, "Đã khôi phục cấu hình máy chủ mặc định.", Toast.LENGTH_SHORT);
        });

        // Nút Lưu cấu hình: kiểm tra hợp lệ URL, lưu vào SharedPreferences (AppConfig) và khởi tạo lại Retrofit Client
        btnSave.setOnClickListener(v -> {
            if (!urlValidator.validate()) {
                return;
            }
            String prevEmarketUrl = AppConfig.getEmarketBaseUrl(context);
            String emarketUrl = FormValidator.textOf(etEmarket);
            String aiUrl = FormValidator.textOf(etAi);
            AppConfig.updateServerUrls(context, emarketUrl, aiUrl);
            ApiClient.refreshClients(context);
            dialog.dismiss();
            if (handleEmarketServerChange(context, prevEmarketUrl)) {
                return;
            }
            com.example.do_an_di_dong.util.AppToast.show(context, "Đã cập nhật địa chỉ máy chủ thành công!", Toast.LENGTH_SHORT);
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
        });

        dialog.show();
    }

    /**
     * Kiểm tra nếu người dùng đổi sang địa chỉ máy chủ EMarket Core khác trong lúc đang đăng nhập:
     * bắt buộc thu hồi JWT cũ và kích hoạt force logout để người dùng đăng nhập lại lấy JWT từ máy chủ mới.
     */
    private static boolean handleEmarketServerChange(Context context, String previousEmarketUrl) {
        String currentEmarketUrl = AppConfig.getEmarketBaseUrl(context);
        if (previousEmarketUrl == null || previousEmarketUrl.equalsIgnoreCase(currentEmarketUrl)) {
            return false;
        }
        String token = ApiClient.getToken(context);
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        if (UserSessionManager.getCurrentUser(context) != null) {
            AuthSessionGuard.getInstance(context).triggerForceLogout(
                    "Đã đổi máy chủ EMarket Core. Vui lòng đăng nhập lại để lấy JWT mới từ máy chủ vừa chọn.");
            return true;
        }
        ApiClient.clearToken(context);
        UserSessionManager.clearSession(context);
        return false;
    }

    /**
     * Sao chép nhanh địa chỉ URL hoặc câu lệnh ADB vào bộ nhớ tạm (Clipboard) khi người dùng chạm vào dòng hướng dẫn Inspector.
     */
    private static void copyText(Context context, String label, String value) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            com.example.do_an_di_dong.util.AppToast.show(context, "Đã sao chép: " + value, Toast.LENGTH_SHORT);
        }
    }
}

