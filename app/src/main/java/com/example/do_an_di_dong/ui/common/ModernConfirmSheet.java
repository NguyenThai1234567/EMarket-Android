package com.example.do_an_di_dong.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.do_an_di_dong.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * Hộp thoại xác nhận và thông báo chuẩn Material 3 BottomSheet dùng chung toàn ứng dụng.
 * Thay thế hoàn toàn {@code AlertDialog} truyền thống, gắn với layout {@code R.layout.sheet_confirm_dialog}
 * và kết hợp {@link BottomSheetHelper} để hiển thị đồng bộ theo 4 sắc thái nghiệp vụ (DANGER, WARNING, PRIMARY, INFO).
 */
public final class ModernConfirmSheet {

    public enum Tone {
        DANGER,
        WARNING,
        PRIMARY,
        INFO
    }

    private ModernConfirmSheet() {}

    /**
     * Hiển thị BottomSheet xác nhận thao tác nguy hiểm/không thể hoàn tác (tông màu đỏ DANGER).
     * Kích hoạt khi người dùng bấm Xóa dữ liệu, Hủy đơn hàng hoặc Đăng xuất; chạy {@code onConfirm} nếu bấm nút xác nhận.
     */
    public static void confirmDanger(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String confirmLabel,
            @NonNull Runnable onConfirm
    ) {
        show(context, Tone.DANGER, title, message, "Hủy bỏ", null, confirmLabel, onConfirm);
    }

    /**
     * Hiển thị BottomSheet xác nhận kèm cảnh báo (tông màu vàng cam WARNING).
     * Kích hoạt trước các thao tác nhạy cảm (VD: lệch tồn kho, vượt hạn mức) và thực thi {@code onConfirm} khi đồng ý.
     */
    public static void confirmWarning(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String confirmLabel,
            @NonNull Runnable onConfirm
    ) {
        show(context, Tone.WARNING, title, message, "Hủy bỏ", null, confirmLabel, onConfirm);
    }

    /**
     * Hiển thị BottomSheet xác nhận thao tác nghiệp vụ tiêu chuẩn (tông màu chính PRIMARY).
     * Kích hoạt khi cần người dùng chốt thao tác quan trọng (VD: duyệt phiếu, hoàn tất thanh toán) và gọi {@code onConfirm}.
     */
    public static void confirmPrimary(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String confirmLabel,
            @NonNull Runnable onConfirm
    ) {
        show(context, Tone.PRIMARY, title, message, "Hủy bỏ", null, confirmLabel, onConfirm);
    }

    /**
     * Hiển thị BottomSheet thông báo thông tin đơn thuần chỉ có 1 nút đóng (tông màu xanh INFO).
     * Dùng khi cần thông báo kết quả hoặc hướng dẫn nhanh cho người dùng mà không cần callback xử lý tiếp.
     */
    public static void notice(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String buttonLabel
    ) {
        show(context, Tone.INFO, title, message, null, null, buttonLabel, null);
    }

    /**
     * Hiển thị BottomSheet cảnh báo lỗi hoặc từ chối quyền truy cập chỉ có 1 nút đóng (tông màu đỏ DANGER).
     * Kích hoạt khi phát hiện vi phạm điều kiện nghiệp vụ hoặc lỗi nghiêm trọng cần người dùng chú ý.
     */
    public static void noticeDanger(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String buttonLabel
    ) {
        show(context, Tone.DANGER, title, message, null, null, buttonLabel, null);
    }

    /**
     * Hiển thị BottomSheet thông báo có kèm tối đa 2 nút hành động tùy chỉnh (Nút phụ và Nút chính).
     * Kích hoạt khi muốn gợi ý bước tiếp theo sau một sự kiện (VD: Tạo đơn xong hỏi muốn Xem chi tiết hay Đóng).
     */
    public static void noticeWithAction(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @Nullable String secondaryLabel,
            @Nullable Runnable onSecondary,
            @NonNull String primaryLabel,
            @Nullable Runnable onPrimary
    ) {
        show(context, Tone.PRIMARY, title, message, secondaryLabel, onSecondary, primaryLabel, onPrimary);
    }

    /**
     * Hàm lõi khởi tạo, cấu hình giao diện theo {@link Tone} và hiển thị {@link BottomSheetDialog} từ layout {@code sheet_confirm_dialog.xml}.
     * Nhận vào tiêu đề, nội dung, nhãn và callback cho nút phụ/nút chính; tự động ẩn nút phụ nếu {@code secondaryLabel} rỗng.
     */
    private static void show(
            @NonNull Context context,
            @NonNull Tone tone,
            @NonNull String title,
            @NonNull String message,
            @Nullable String secondaryLabel,
            @Nullable Runnable onSecondary,
            @NonNull String primaryLabel,
            @Nullable Runnable onPrimary
    ) {
        // Kiểm tra an toàn vòng đời Activity để tránh lỗi WindowLeaked / BadTokenException khi màn hình đang đóng
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing() || act.isDestroyed()) {
                return;
            }
        }

        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_confirm_dialog, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        FrameLayout flIconBox = view.findViewById(R.id.flConfirmIconBox);
        ImageView ivIcon = view.findViewById(R.id.ivConfirmIcon);
        TextView tvTitle = view.findViewById(R.id.tvConfirmTitle);
        TextView tvMessage = view.findViewById(R.id.tvConfirmMessage);
        MaterialButton btnCancel = view.findViewById(R.id.btnConfirmCancel);
        MaterialButton btnOk = view.findViewById(R.id.btnConfirmOk);

        @DrawableRes int boxBg;
        @DrawableRes int iconRes;
        @ColorRes int accentColor;
        // Rẽ nhánh cấu hình bộ icon, nền hộp icon và màu nhấn (accent color) cho nút chính theo mức độ cảnh báo
        switch (tone) {
            case DANGER:
                boxBg = R.drawable.bg_icon_box_rose;
                iconRes = R.drawable.ic_warning;
                accentColor = R.color.error;
                break;
            case WARNING:
                boxBg = R.drawable.bg_icon_box_amber;
                iconRes = R.drawable.ic_warning;
                accentColor = R.color.warning;
                break;
            case INFO:
                boxBg = R.drawable.bg_icon_box_blue;
                iconRes = R.drawable.ic_info;
                accentColor = R.color.primary;
                break;
            case PRIMARY:
            default:
                boxBg = R.drawable.bg_icon_box_indigo;
                iconRes = R.drawable.ic_info;
                accentColor = R.color.primary;
                break;
        }

        int resolvedAccent = ContextCompat.getColor(context, accentColor);
        flIconBox.setBackgroundResource(boxBg);
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(resolvedAccent);

        tvTitle.setText(title);
        tvMessage.setText(message);

        btnOk.setText(primaryLabel);
        btnOk.setBackgroundTintList(ColorStateList.valueOf(resolvedAccent));
        btnOk.setOnClickListener(v -> {
            sheet.dismiss();
            if (onPrimary != null) {
                onPrimary.run();
            }
        });

        // Nếu chế độ thông báo đơn (không có nút Hủy/nút phụ), ẩn nút phụ và xóa margin trái để nút chính giãn full chiều ngang
        if (secondaryLabel == null || secondaryLabel.trim().isEmpty()) {
            btnCancel.setVisibility(View.GONE);
            if (btnOk.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams lp =
                        (android.widget.LinearLayout.LayoutParams) btnOk.getLayoutParams();
                lp.setMarginStart(0);
                btnOk.setLayoutParams(lp);
            }
        } else {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setText(secondaryLabel);
            btnCancel.setOnClickListener(v -> {
                sheet.dismiss();
                if (onSecondary != null) {
                    onSecondary.run();
                }
            });
        }

        sheet.show();
    }
}

