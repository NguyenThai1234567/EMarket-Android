package com.example.do_an_di_dong.util;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;

/**
 * Bộ lắng nghe nhập liệu có độ trễ (Debounce TextWatcher) gắn vào các ô EditText tìm kiếm trên toàn ứng dụng.
 * Giúp trì hoãn việc lọc danh sách RecyclerView hoặc gọi API tìm kiếm cho đến khi người dùng ngừng gõ (mặc định 280ms),
 * tránh spam request liên tục và không làm đứt gãy tổ hợp dấu tiếng Việt (Telex/VNI) khi đang gõ dở từ.
 */
public final class DebouncedTextWatcher implements TextWatcher {

    public interface OnDebouncedTextChanged {
        void onTextChanged(@NonNull String text);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long delayMs;
    private final OnDebouncedTextChanged callback;
    private Runnable pendingRunnable;

    /**
     * Khởi tạo bộ lắng nghe với thời gian trễ tùy chỉnh (delayMs) và callback nhận chuỗi từ khóa sau khi ngừng gõ.
     */
    public DebouncedTextWatcher(long delayMs, @NonNull OnDebouncedTextChanged callback) {
        this.delayMs = Math.max(0L, delayMs);
        this.callback = callback;
    }

    /**
     * Hàm tiện ích tạo nhanh DebouncedTextWatcher chuẩn cho các ô tìm kiếm với độ trễ 280ms.
     */
    public static DebouncedTextWatcher search(@NonNull OnDebouncedTextChanged callback) {
        return new DebouncedTextWatcher(280L, callback);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    /**
     * Kích hoạt mỗi khi ký tự trong EditText thay đổi: hủy lịch gọi callback cũ (nếu có)
     * và đặt lịch chạy callback mới trên Main Looper sau khoảng thời gian `delayMs`.
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        final String text = s != null ? s.toString().trim() : "";
        pendingRunnable = () -> callback.onTextChanged(text);
        handler.postDelayed(pendingRunnable, delayMs);
    }

    @Override
    public void afterTextChanged(Editable s) {}
}

