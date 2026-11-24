package com.example.do_an_di_dong.ui.common;

import android.content.Context;

/**
 * Lớp cầu nối (Adapter/Bridge) giữ tương thích ngược cho các màn hình trong package ui.*
 * gọi `com.example.do_an_di_dong.ui.common.AppToast`, ủy quyền toàn bộ xử lý sang `util.AppToast`.
 */
public final class AppToast {

    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    public static final int LENGTH_SHORT = com.example.do_an_di_dong.util.AppToast.LENGTH_SHORT;
    public static final int LENGTH_LONG = com.example.do_an_di_dong.util.AppToast.LENGTH_LONG;

    private AppToast() {}

    /**
     * Hiển thị thông báo ngắn (LENGTH_SHORT) bằng cách chuyển tiếp sang util.AppToast.
     */
    public static void show(Context context, CharSequence message) {
        com.example.do_an_di_dong.util.AppToast.show(context, message);
    }

    /**
     * Hiển thị thông báo với thời lượng tùy chỉnh (LENGTH_SHORT hoặc LENGTH_LONG).
     */
    public static void show(Context context, CharSequence message, int duration) {
        com.example.do_an_di_dong.util.AppToast.show(context, message, duration);
    }

    /**
     * Hiển thị thông báo theo phân loại Type (lỗi ERROR sẽ tự động hiển thị lâu hơn với LENGTH_LONG).
     */
    public static void show(Context context, CharSequence message, Type type) {
        int duration = (type == Type.ERROR) ? LENGTH_LONG : LENGTH_SHORT;
        com.example.do_an_di_dong.util.AppToast.show(context, message, duration);
    }
}

