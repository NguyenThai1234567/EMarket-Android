package com.example.do_an_di_dong.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Tiện ích định dạng tiền tệ VNĐ, rút gọn số liệu thống kê và chuẩn hóa chuỗi ngày tháng ISO-8601 từ Backend.
 * Được sử dụng rộng rãi trong toàn bộ các Adapter, Activity và Fragment (Dashboard, POS, Hóa đơn, Báo cáo) để hiển thị lên TextView.
 */
public class Formatters {
    private static final DecimalFormat VND_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        VND_FORMAT = new DecimalFormat("#,###", symbols);
    }

    /**
     * Định dạng số thực sang chuỗi tiền tệ VNĐ có dấu chấm phân cách hàng nghìn (VD: 150000 -> "150.000 đ").
     */
    public static String currency(double amount) {
        return VND_FORMAT.format(Math.round(amount)) + " đ";
    }

    /**
     * Định dạng đối tượng Double (nullable) sang chuỗi tiền tệ VNĐ, trả về "0 đ" nếu giá trị null.
     */
    public static String currency(Double amount) {
        if (amount == null) return "0 đ";
        return currency(amount.doubleValue());
    }

    /**
     * Định dạng tiền tệ rút gọn cho các thẻ KPI và biểu đồ trên Dashboard (VD: "1.25 tỷ", "45.5 tr", "500K").
     */
    public static String compactCurrency(double amount) {
        double abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (abs >= 1_000_000_000) {
            return String.format(Locale.US, "%s%.2f tỷ", sign, abs / 1_000_000_000.0);
        } else if (abs >= 1_000_000) {
            return String.format(Locale.US, "%s%.1f tr", sign, abs / 1_000_000.0);
        } else if (abs >= 1_000) {
            return String.format(Locale.US, "%s%.0fK", sign, abs / 1_000.0);
        }
        return currency(amount);
    }

    /**
     * Chuyển đổi chuỗi ngày giờ ISO-8601 từ Backend (VD: "2026-04-15T14:30:00")
     * sang định dạng ngày giờ Việt Nam "dd/MM/yyyy HH:mm".
     */
    public static String cleanDate(String rawIsoDate) {
        if (rawIsoDate == null || rawIsoDate.trim().isEmpty()) return "--/--/----";
        String s = rawIsoDate.trim();
        if (s.contains("T")) {
            String[] parts = s.split("T");
            String datePart = parts[0];
            String timePart = parts.length > 1 && parts[1].length() >= 5 ? parts[1].substring(0, 5) : "";
            String[] ymd = datePart.split("-");
            if (ymd.length == 3) {
                return ymd[2] + "/" + ymd[1] + "/" + ymd[0] + (timePart.isEmpty() ? "" : " " + timePart);
            }
        }
        return s;
    }

    /**
     * Hàm bí danh (alias) của cleanDate() để hiển thị ngày kèm giờ phút ("dd/MM/yyyy HH:mm").
     */
    public static String dateTime(String rawIsoDate) {
        return cleanDate(rawIsoDate);
    }

    /**
     * Cắt bỏ phần thời gian và chuyển chuỗi ISO-8601 ("yyyy-MM-dd...") sang định dạng chỉ có ngày "dd/MM/yyyy".
     */
    public static String date(String rawIsoDate) {
        if (rawIsoDate == null || rawIsoDate.trim().isEmpty()) return "--/--/----";
        String s = rawIsoDate.trim();
        String datePart = s.contains("T") ? s.split("T")[0] : (s.contains(" ") ? s.split(" ")[0] : s);
        String[] ymd = datePart.split("-");
        if (ymd.length == 3 && ymd[0].length() == 4) {
            return ymd[2] + "/" + ymd[1] + "/" + ymd[0];
        }
        return datePart;
    }
}

