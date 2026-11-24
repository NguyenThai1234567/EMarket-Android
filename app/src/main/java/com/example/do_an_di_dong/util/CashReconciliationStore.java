package com.example.do_an_di_dong.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Kho lưu trữ cục bộ (SharedPreferences + Gson JSON) cho nghiệp vụ Đối soát tiền mặt cuối ca (Cash Reconciliation).
 * Phục vụ màn hình `CashReconciliationActivity` và `ExpensesActivity` để lưu tiền đầu ca cùng tối đa 100 biên bản
 * đối soát gần nhất (do Backend không thiết kế bảng lưu lịch sử kiểm quỹ tiền mặt).
 */
public final class CashReconciliationStore {

    private static final String PREF_NAME = "EMarketCashReconPrefs";
    private static final String KEY_OPENING_CASH = "opening_cash";
    private static final String KEY_HISTORY_JSON = "cash_reconciliation_history_json";
    private static final int MAX_RECORDS = 100;

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Record>>() {}.getType();

    private CashReconciliationStore() {}

    public static class Record {
        public String id;
        public String timestamp;
        public String staffName;
        public String branchName;
        public double openingCash;
        public double expectedCash;
        public double actualCash;
        public double discrepancy;
        public String notes;
    }

    /**
     * Đọc số tiền mặt đầu ca đã lưu (mặc định 5.000.000 đ nếu chưa thiết lập).
     * Được gọi khi khởi tạo màn hình đối soát két tiền để tính tiền lý thuyết trong két.
     */
    public static long getSavedOpeningCash(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_OPENING_CASH)) {
            return prefs.getLong(KEY_OPENING_CASH, 5000000L);
        }
        // Fallback migrate old ExpensesActivity opening cash pref if present
        SharedPreferences legacy = context.getSharedPreferences("EMarketCashReconciliationPrefs", Context.MODE_PRIVATE);
        return legacy.getLong("opening_cash", 5000000L);
    }

    /**
     * Lưu số tiền mặt đầu ca mới vào SharedPreferences khi thu ngân/quản lý cập nhật trên giao diện.
     */
    public static void saveOpeningCash(Context context, long openingCash) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_OPENING_CASH, Math.max(0L, openingCash))
                .apply();
    }

    /**
     * Đọc danh sách lịch sử các lần chốt ca đối soát tiền mặt từ chuỗi JSON trong SharedPreferences.
     * Trả về danh sách Record để hiển thị lên RecyclerView lịch sử đối soát.
     */
    public static List<Record> getHistory(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_HISTORY_JSON, null);
            if (raw == null || raw.trim().isEmpty()) {
                return new ArrayList<>();
            }
            List<Record> list = GSON.fromJson(raw, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Thêm một biên bản đối soát mới vào đầu danh sách lịch sử (FIFO, giữ tối đa 100 bản ghi gần nhất)
     * và tuần tự hóa thành JSON lưu xuống SharedPreferences khi người dùng bấm "Lưu biên bản đối soát".
     */
    public static void saveRecord(Context context, Record record) {
        if (record == null) return;
        List<Record> history = getHistory(context);
        history.add(0, record);
        if (history.size() > MAX_RECORDS) {
            history = new ArrayList<>(history.subList(0, MAX_RECORDS));
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY_JSON, GSON.toJson(history))
                .apply();
    }

    /**
     * Xóa toàn bộ lịch sử đối soát tiền mặt đã lưu trong máy.
     */
    public static void clearHistory(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_HISTORY_JSON)
                .apply();
    }
}

