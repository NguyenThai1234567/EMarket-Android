package com.example.do_an_di_dong.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.do_an_di_dong.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Hệ thống hiển thị thông báo nổi (Custom Banner Toast) dùng chung trên toàn bộ Activity, Fragment và Dialog:
 * - Tự động ghi đè thông báo cũ ngay lập tức (không xếp hàng gây trễ như Toast mặc định của Android).
 * - Tự động nhận diện Dialog đang mở để vẽ banner nổi lên trên cửa sổ Dialog, không bị lớp nền che khuất.
 * - Tự động đổi màu vạch nhấn (Đỏ/Vàng/Xanh lá/Xanh dương) dựa trên ngữ nghĩa của chuỗi thông báo.
 */
public final class AppToast {

    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    private static final String TAG_OVERLAY = "EMARKET_CUSTOM_TOAST_OVERLAY";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<Dialog>> ACTIVE_DIALOGS = new ArrayList<>();
    private static WeakReference<View> sCurrentBannerRef;
    private static Runnable sDismissRunnable;
    private static Toast sFallbackToast;

    private AppToast() {}

    /**
     * Gắn vòng đời của Dialog/BottomSheetDialog vào danh sách theo dõi để mọi lệnh AppToast.show(...)
     * hiển thị nổi phía trên Dialog đang mở thay vì bị che phía sau Activity.
     */
    public static void attachDialog(final Dialog dialog, final View contentView) {
        if (dialog == null || contentView == null) return;
        registerActiveDialog(dialog);
        contentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                registerActiveDialog(dialog);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                unregisterActiveDialog(dialog);
            }
        });
    }

    /**
     * Đăng ký một Dialog vào đỉnh ngăn xếp ACTIVE_DIALOGS (dùng WeakReference để tránh rò rỉ bộ nhớ).
     */
    public static void registerActiveDialog(Dialog dialog) {
        if (dialog == null) return;
        for (int i = ACTIVE_DIALOGS.size() - 1; i >= 0; i--) {
            Dialog existing = ACTIVE_DIALOGS.get(i).get();
            if (existing == null) {
                ACTIVE_DIALOGS.remove(i);
            } else if (existing == dialog) {
                ACTIVE_DIALOGS.remove(i);
            }
        }
        ACTIVE_DIALOGS.add(new WeakReference<>(dialog));
    }

    /**
     * Gỡ Dialog khỏi danh sách ACTIVE_DIALOGS ngay khi Dialog đóng hoặc View bị tách khỏi cửa sổ.
     */
    public static void unregisterActiveDialog(Dialog dialog) {
        if (dialog == null) return;
        Iterator<WeakReference<Dialog>> it = ACTIVE_DIALOGS.iterator();
        while (it.hasNext()) {
            Dialog d = it.next().get();
            if (d == null || d == dialog) {
                it.remove();
            }
        }
    }

    /**
     * Hiển thị thông báo ngắn (LENGTH_SHORT) trên màn hình hiện tại.
     */
    public static void show(Context context, CharSequence message) {
        show(context, message, LENGTH_SHORT);
    }

    /**
     * Hiển thị thông báo kèm phân loại Type (tự động kéo dài thời gian hiển thị nếu là lỗi ERROR).
     */
    public static void show(Context context, CharSequence message, Type type) {
        show(context, message, type == Type.ERROR ? LENGTH_LONG : LENGTH_SHORT);
    }

    /**
     * Hiển thị thông báo từ String Resource ID với thời lượng chỉ định.
     */
    public static void show(Context context, int resId, int duration) {
        if (context == null) return;
        show(context, context.getString(resId), duration);
    }

    /**
     * Điểm vào chính để hiển thị thông báo: tự động đẩy việc vẽ UI về Main Thread (UI Thread)
     * nếu được gọi từ luồng phụ (callback background hoặc SignalR).
     */
    public static void show(final Context context, final CharSequence rawMessage, final int duration) {
        if (context == null || rawMessage == null) return;
        final String message = rawMessage.toString().trim();
        if (message.isEmpty()) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN_HANDLER.post(() -> showInternal(context, message, duration));
        } else {
            showInternal(context, message, duration);
        }
    }

    /**
     * Quyết định nơi gắn Banner thông báo: ưu tiên gắn lên cửa sổ Dialog đang mở trên cùng;
     * nếu không có Dialog thì gắn vào `android.R.id.content` của Activity; cuối cùng mới fallback về Toast hệ thống.
     */
    private static void showInternal(Context context, String message, int duration) {
        Dialog activeDialog = findTopShowingDialog();
        if (activeDialog != null && activeDialog.getWindow() != null) {
            View dialogRoot = activeDialog.getWindow().findViewById(android.R.id.content);
            if (!(dialogRoot instanceof ViewGroup)) {
                dialogRoot = activeDialog.getWindow().getDecorView();
            }
            if (dialogRoot instanceof ViewGroup) {
                showOverlayBanner((ViewGroup) dialogRoot, activeDialog.getContext(), message, duration, true);
                return;
            }
        }

        Activity activity = findActivity(context);
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            View root = activity.findViewById(android.R.id.content);
            if (root instanceof ViewGroup) {
                showOverlayBanner((ViewGroup) root, activity, message, duration, false);
                return;
            }
        }

        // Fallback: single-instance Toast that cancels previous one immediately
        try {
            if (sFallbackToast != null) {
                sFallbackToast.cancel();
            }
            sFallbackToast = Toast.makeText(context.getApplicationContext(), message, duration);
            sFallbackToast.show();
        } catch (Exception ignored) {}
    }

    /**
     * Tìm Dialog đang hiển thị gần nhất ở đỉnh ngăn xếp ACTIVE_DIALOGS và dọn dẹp các tham chiếu đã bị GC thu hồi.
     */
    private static Dialog findTopShowingDialog() {
        for (int i = ACTIVE_DIALOGS.size() - 1; i >= 0; i--) {
            Dialog d = ACTIVE_DIALOGS.get(i).get();
            if (d == null || !d.isShowing() || d.getWindow() == null) {
                if (d == null) {
                    ACTIVE_DIALOGS.remove(i);
                }
                continue;
            }
            return d;
        }
        return null;
    }

    /**
     * Dựng giao diện Banner thông báo bằng code (Programmatic UI), xóa ngay Banner cũ nếu đang hiển thị,
     * chạy hiệu ứng trượt/mờ (fade-in) và hẹn giờ tự động đóng sau 2.2s (SHORT) hoặc 3.4s (LONG).
     */
    private static void showOverlayBanner(final ViewGroup rootGroup, Context ctx, String message, int duration, boolean insideDialog) {
        // 1. Ghi đè thông báo cũ ngay lập tức
        if (sDismissRunnable != null) {
            MAIN_HANDLER.removeCallbacks(sDismissRunnable);
            sDismissRunnable = null;
        }
        if (sCurrentBannerRef != null) {
            View oldView = sCurrentBannerRef.get();
            if (oldView != null) {
                ViewParent parent = oldView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(oldView);
                }
            }
            sCurrentBannerRef = null;
        }
        View existing = rootGroup.findViewWithTag(TAG_OVERLAY);
        if (existing != null) {
            rootGroup.removeView(existing);
        }

        // 2. Phân loại sắc thái nhẹ nhàng (không màu mè, chỉ dùng viền & chấm trạng thái tinh tế)
        int accentColor = resolveAccentColor(message);

        LinearLayout banner = new LinearLayout(ctx);
        banner.setTag(TAG_OVERLAY);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(ctx, 14);
        int padV = dp(ctx, 11);
        banner.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 12));
        bg.setColor(Color.parseColor("#F21E293B")); // Slate 800 bán trong suốt sang trọng
        bg.setStroke(dp(ctx, 1), Color.parseColor("#334155"));
        banner.setBackground(bg);
        banner.setElevation(dp(ctx, 8));

        // Thanh nhấn trạng thái mảnh bên trái (3dp)
        View accentBar = new View(ctx);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 24));
        barLp.setMarginEnd(dp(ctx, 10));
        accentBar.setLayoutParams(barLp);
        GradientDrawable barDrawable = new GradientDrawable();
        barDrawable.setShape(GradientDrawable.RECTANGLE);
        barDrawable.setCornerRadius(dp(ctx, 2));
        barDrawable.setColor(accentColor);
        accentBar.setBackground(barDrawable);
        banner.addView(accentBar);

        // Khung chứa icon EMarket (26dp x 26dp)
        FrameLayout iconBox = new FrameLayout(ctx);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26));
        boxLp.setMarginEnd(dp(ctx, 10));
        iconBox.setLayoutParams(boxLp);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(dp(ctx, 7));
        iconBg.setColor(Color.parseColor("#FFFFFF"));
        iconBox.setBackground(iconBg);

        ImageView ivLogo = new ImageView(ctx);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20), Gravity.CENTER);
        ivLogo.setLayoutParams(logoLp);
        ivLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivLogo.setImageResource(R.drawable.emarket_logo_icon);
        iconBox.addView(ivLogo);
        banner.addView(iconBox);

        // Nội dung thông báo
        TextView tvMsg = new TextView(ctx);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvMsg.setLayoutParams(msgLp);
        tvMsg.setText(message);
        tvMsg.setTextColor(Color.parseColor("#F8FAFC"));
        tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tvMsg.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvMsg.setMaxLines(3);
        tvMsg.setEllipsize(TextUtils.TruncateAt.END);
        banner.addView(tvMsg);

        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (insideDialog ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL
        );
        int horizontalMargin = dp(ctx, 16);
        int verticalMargin = insideDialog ? dp(ctx, 20) : dp(ctx, 76); // Phía trên Dialog hoặc trên BottomNavigation
        containerLp.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);

        banner.setAlpha(0f);
        banner.setTranslationY(dp(ctx, 16));
        banner.setOnClickListener(v -> dismissBanner(banner));

        rootGroup.addView(banner, containerLp);
        sCurrentBannerRef = new WeakReference<>(banner);

        banner.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();

        long delayMs = (duration == LENGTH_LONG) ? 3400L : 2200L;
        sDismissRunnable = () -> dismissBanner(banner);
        MAIN_HANDLER.postDelayed(sDismissRunnable, delayMs);
    }

    /**
     * Ẩn Banner thông báo với hiệu ứng mờ dần (fade-out) và tháo View khỏi ViewGroup cha.
     */
    private static void dismissBanner(final View banner) {
        if (banner == null || banner.getParent() == null) return;
        banner.animate()
                .alpha(0f)
                .translationY(dp(banner.getContext(), 12))
                .setDuration(150)
                .withEndAction(() -> {
                    ViewParent parent = banner.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(banner);
                    }
                })
                .start();
    }

    /**
     * Tự động suy luận màu thanh nhấn trạng thái dựa trên từ khóa tiếng Việt trong nội dung thông báo:
     * Đỏ (Lỗi/Thất bại), Vàng cam (Cảnh báo/Vui lòng), Xanh ngọc (Thành công), Xanh dương (Thông tin chung).
     */
    private static int resolveAccentColor(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("lỗi") || lower.contains("thất bại") || lower.contains("không thể")
                || lower.contains("không hợp lệ") || lower.contains("từ chối") || lower.contains("đã hủy")
                || lower.contains("không có quyền") || lower.contains("hết hạn")
                || lower.contains("vượt quá") || lower.contains("hết hàng")) {
            return Color.parseColor("#EF4444"); // Rose/Red tinh tế
        }
        if (lower.contains("vui lòng") || lower.contains("cảnh báo") || lower.contains("chưa")
                || lower.contains("tối thiểu") || lower.contains("chú ý")) {
            return Color.parseColor("#F59E0B"); // Amber
        }
        if (lower.contains("thành công") || lower.contains("hoàn tất") || lower.contains("đã lưu")
                || lower.contains("đã duyệt") || lower.contains("đã thêm") || lower.contains("đã cập nhật")
                || lower.contains("đã xóa")) {
            return Color.parseColor("#10B981"); // Emerald
        }
        return Color.parseColor("#3B82F6"); // EMarket Blue
    }

    /**
     * Lần ngược chuỗi ContextWrapper để tìm thực thể Activity gốc phục vụ việc gắn Banner lên cửa sổ.
     */
    private static Activity findActivity(Context context) {
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) {
                return (Activity) cur;
            }
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return null;
    }

    /**
     * Quy đổi kích thước từ đơn vị dp sang pixel theo mật độ màn hình thiết bị.
     */
    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
