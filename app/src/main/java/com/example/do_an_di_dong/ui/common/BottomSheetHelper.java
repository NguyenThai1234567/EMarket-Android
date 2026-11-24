package com.example.do_an_di_dong.ui.common;

import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.do_an_di_dong.util.AppToast;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Tiện ích (Helper) dùng chung để chuẩn hóa giao diện và hành vi cho mọi {@link BottomSheetDialog} trong ứng dụng
 * (được gọi bởi {@link ModernPickerSheets}, {@link ModernConfirmSheet} và các màn hình dùng BottomSheet):
 * - Loại bỏ nền container mặc định của Material để bo góc custom XML không bị viền trắng.
 * - Mở rộng toàn phần ({@code STATE_EXPANDED}) ngay khi mở để không bị che khuất nút bấm bên dưới.
 * - Tự động tính toán và cộng thêm padding đáy theo thanh điều hướng hệ thống (Edge-to-Edge / Navigation Bar).
 * - Kết nối vòng đời Dialog với {@link AppToast} để thông báo Toast custom luôn hiển thị nổi phía trên mặt Sheet.
 */
public final class BottomSheetHelper {

    private BottomSheetHelper() {}

    /**
     * Chuẩn bị và gắn các listener xử lý WindowInsets, màu nền trong suốt và trạng thái mở rộng cho {@link BottomSheetDialog}.
     * Được gọi ngay sau lệnh {@code sheet.setContentView(view)} trước khi gọi {@code sheet.show()}.
     * Nhận vào đối tượng {@code sheet}, view gốc {@code contentView} của layout XML và cờ {@code expand} (true để mở full chiều cao).
     */
    public static void prepare(@NonNull BottomSheetDialog sheet, @NonNull View contentView, boolean expand) {
        // Gắn kết sheet với AppToast để khi gọi thông báo lỗi/thành công từ trong sheet, Toast sẽ vẽ đè lên Window của Dialog
        AppToast.attachDialog(sheet, contentView);

        final int baseBottomPadding = contentView.getPaddingBottom();
        final int baseLeftPadding = contentView.getPaddingLeft();
        final int baseTopPadding = contentView.getPaddingTop();
        final int baseRightPadding = contentView.getPaddingRight();

        // Lắng nghe WindowInsets để cộng bù chiều cao thanh điều hướng (Navigation Bar) vào padding đáy, tránh bị đè nút bấm
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(
                    baseLeftPadding,
                    baseTopPadding,
                    baseRightPadding,
                    baseBottomPadding + navBars.bottom
            );
            return insets;
        });

        sheet.setOnShowListener(dialog -> {
            AppToast.registerActiveDialog(sheet);
            Window window = sheet.getWindow();
            if (window != null) {
                window.setNavigationBarColor(Color.TRANSPARENT);
                View decor = window.getDecorView();
                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
                controller.setAppearanceLightNavigationBars(true);
            }

            // Xóa nền mặc định của khung design_bottom_sheet và ép mở rộng hoàn toàn (STATE_EXPANDED) nếu cờ expand = true
            FrameLayout bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                if (expand) {
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            }
            ViewCompat.requestApplyInsets(contentView);
        });
    }
}
