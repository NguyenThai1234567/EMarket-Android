package com.example.do_an_di_dong.util;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hộp thoại xem trước hình ảnh toàn màn hình (Fullscreen Lightbox Dialog) dùng cho màn hình Sản phẩm, Banner, Chi tiết đơn hàng:
 * - Tích hợp ViewPager2 hỗ trợ vuốt trái / phải mượt mà qua danh sách nhiều ảnh.
 * - Tích hợp ScaleGestureDetector & GestureDetector hỗ trợ chạm đúp (Double-tap) hoặc dùng 2 ngón tay (Pinch-to-zoom) để phóng to / thu nhỏ.
 * - Tự động chuẩn hóa đường dẫn ảnh tương đối thành URL đầy đủ thông qua `ApiClient.getFullImageUrl()` và tải ảnh bằng Glide.
 */
public final class ImagePreviewDialog {

    private ImagePreviewDialog() {}

    /**
     * Mở hộp thoại xem toàn màn hình cho một hình ảnh đơn lẻ (gọi khi người dùng bấm vào ảnh đại diện sản phẩm/banner).
     */
    public static void showSingle(Context context, String imageUrl, String title) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            AppToast.show(context, "Không có ảnh để hiển thị", AppToast.LENGTH_SHORT);
            return;
        }
        showGallery(context, Collections.singletonList(imageUrl), 0, title);
    }

    /**
     * Mở hộp thoại thư viện nhiều ảnh toàn màn hình tại vị trí `initialPosition`.
     * Tự động ghép Base URL của Server cho các đường dẫn ảnh tương đối và dựng giao diện ViewPager2 kèm bộ đếm trang.
     */
    public static void showGallery(Context context, List<String> rawUrls, int initialPosition, String title) {
        if (context == null || rawUrls == null || rawUrls.isEmpty()) {
            AppToast.show(context, "Không có hình ảnh để xem", AppToast.LENGTH_SHORT);
            return;
        }

        // Chuẩn hóa danh sách đường dẫn ảnh thông qua ApiClient.getFullImageUrl và loại bỏ URL trùng lặp
        final List<String> fullUrls = new ArrayList<>();
        for (String u : rawUrls) {
            if (u != null && !u.trim().isEmpty()) {
                String resolved = ApiClient.getFullImageUrl(u.trim());
                if (!resolved.isEmpty() && !fullUrls.contains(resolved)) {
                    fullUrls.add(resolved);
                }
            }
        }
        if (fullUrls.isEmpty()) {
            AppToast.show(context, "Đường dẫn ảnh không hợp lệ", AppToast.LENGTH_SHORT);
            return;
        }

        final int safeInitial = Math.max(0, Math.min(initialPosition, fullUrls.size() - 1));

        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F20F172A")));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.parseColor("#F50B1120"));

        // 1. ViewPager2 vuốt trái phải
        final ViewPager2 viewPager = new ViewPager2(context);
        viewPager.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        viewPager.setAdapter(new FullscreenImageAdapter(fullUrls));
        root.addView(viewPager);

        // 2. Top Bar (Tiêu đề + nút Đóng)
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(context, 16);
        topBar.setPadding(pad, dp(context, 20), pad, pad);

        TextView tvTitle = new TextView(context);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);
        tvTitle.setText(title != null && !title.isEmpty() ? title : "Xem chi tiết hình ảnh");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setMaxLines(1);
        topBar.addView(tvTitle);

        TextView btnClose = new TextView(context);
        btnClose.setText("✕ Đóng");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setCornerRadius(dp(context, 20));
        closeBg.setColor(Color.parseColor("#33FFFFFF"));
        closeBg.setStroke(dp(context, 1), Color.parseColor("#4DFFFFFF"));
        btnClose.setBackground(closeBg);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(btnClose);

        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        ));

        // 3. Bottom Bar (Bộ đếm trang "1 / N" + Hướng dẫn vuốt)
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(pad, pad, pad, dp(context, 28));

        final TextView tvCounter = new TextView(context);
        tvCounter.setText((safeInitial + 1) + " / " + fullUrls.size());
        tvCounter.setTextColor(Color.WHITE);
        tvCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tvCounter.setTypeface(Typeface.DEFAULT_BOLD);
        tvCounter.setPadding(dp(context, 16), dp(context, 6), dp(context, 16), dp(context, 6));
        GradientDrawable counterBg = new GradientDrawable();
        counterBg.setCornerRadius(dp(context, 16));
        counterBg.setColor(Color.parseColor("#801E293B"));
        counterBg.setStroke(dp(context, 1), Color.parseColor("#4D94A3B8"));
        tvCounter.setBackground(counterBg);
        bottomBar.addView(tvCounter);

        TextView tvHint = new TextView(context);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintLp.topMargin = dp(context, 8);
        tvHint.setLayoutParams(hintLp);
        tvHint.setText(fullUrls.size() > 1
                ? "Vuốt sang trái / phải để chuyển ảnh • Chạm đúp để phóng to"
                : "Chạm đúp hoặc dùng 2 ngón tay để phóng to / thu nhỏ");
        tvHint.setTextColor(Color.parseColor("#CBD5E1"));
        tvHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        bottomBar.addView(tvHint);

        root.addView(bottomBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        ));

        // Cập nhật chỉ số trang "x / N" mỗi khi người dùng vuốt chuyển trang trên ViewPager2
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvCounter.setText((position + 1) + " / " + fullUrls.size());
            }
        });

        viewPager.setCurrentItem(safeInitial, false);

        dialog.setContentView(root);
        dialog.show();
    }

    /**
     * Adapter cho ViewPager2 quản lý từng trang ảnh và nạp ảnh từ URL bằng thư viện Glide.
     */
    private static class FullscreenImageAdapter extends RecyclerView.Adapter<FullscreenImageAdapter.PageHolder> {
        private final List<String> urls;

        FullscreenImageAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(imageView);

            return new PageHolder(container, imageView);
        }

        /**
         * Đặt lại tỷ lệ zoom về 1.0x và tải ảnh tương ứng với vị trí trang bằng Glide.
         */
        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.resetZoom();
            String url = urls.get(position);
            Glide.with(holder.imageView.getContext())
                    .load(url)
                    .placeholder(R.drawable.ic_products)
                    .error(R.drawable.ic_products)
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class PageHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;
            private float scaleFactor = 1.0f;

            /**
             * Khởi tạo ViewHolder cho từng trang ảnh, gắn bộ nhận diện cử chỉ 2 ngón (Pinch-to-zoom từ 1.0x đến 4.0x)
             * và chạm đúp (Double-tap để phóng to 2.4x hoặc thu về 1.0x).
             */
            PageHolder(@NonNull View itemView, final ImageView imageView) {
                super(itemView);
                this.imageView = imageView;

                final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(
                        itemView.getContext(),
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                scaleFactor *= detector.getScaleFactor();
                                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 4.0f));
                                imageView.setScaleX(scaleFactor);
                                imageView.setScaleY(scaleFactor);
                                return true;
                            }
                        }
                );

                final GestureDetector gestureDetector = new GestureDetector(
                        itemView.getContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                if (scaleFactor > 1.05f) {
                                    resetZoom();
                                } else {
                                    scaleFactor = 2.4f;
                                    imageView.animate().scaleX(scaleFactor).scaleY(scaleFactor).setDuration(180).start();
                                }
                                return true;
                            }
                        }
                );

                imageView.setOnTouchListener((v, event) -> {
                    scaleDetector.onTouchEvent(event);
                    gestureDetector.onTouchEvent(event);
                    // Khi ảnh đang được phóng to (>1.05x), chặn ViewPager2 bắt sự kiện vuốt để người dùng thao tác zoom
                    if (scaleFactor > 1.05f && v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                });
            }

            /**
             * Đưa tỷ lệ phóng đại và vị trí dịch chuyển của ImageView về trạng thái mặc định ban đầu (1.0x).
             */
            void resetZoom() {
                scaleFactor = 1.0f;
                imageView.setScaleX(1.0f);
                imageView.setScaleY(1.0f);
                imageView.setTranslationX(0f);
                imageView.setTranslationY(0f);
            }
        }
    }

    /**
     * Chuyển đổi đơn vị dp sang pixel theo mật độ màn hình thiết bị.
     */
    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}

