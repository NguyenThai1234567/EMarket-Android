package com.example.do_an_di_dong.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Lớp quản trị cơ sở dữ liệu cục bộ SQLite thông qua thư viện Jetpack Room (Singleton).
 * Lưu trữ dữ liệu bộ nhớ đệm (hiện tại là bảng products) phục vụ chế độ Offline Fallback
 * cho DataRepository khi thiết bị mất kết nối mạng tới EMarket Backend.
 */
@Database(entities = {ProductEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance = null;

    /**
     * Cung cấp đối tượng DAO để thực thi các truy vấn CRUD trên bảng products.
     */
    public abstract ProductDao productDao();

    /**
     * Khởi tạo hoặc lấy instance duy nhất (Singleton) của Room Database "emarket_db".
     * Được gọi bởi DataRepository trong các luồng background khi đồng bộ hoặc đọc sản phẩm offline.
     */
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            // Khởi tạo file SQLite "emarket_db" và tự động xóa/tạo lại bảng nếu nâng version mà chưa khai báo Migration
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "emarket_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
