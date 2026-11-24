package com.example.do_an_di_dong.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object (DAO) định nghĩa các câu lệnh SQL truy vấn bảng "products" trong Room SQLite.
 * Được gọi bởi DataRepository để lưu cache sản phẩm khi có mạng và truy xuất/tìm kiếm sản phẩm
 * cho màn hình POS / Danh sách sản phẩm khi mất kết nối tới Backend.
 */
@Dao
public interface ProductDao {
    /**
     * Lấy toàn bộ danh sách sản phẩm đang lưu trong bộ nhớ đệm SQLite cục bộ.
     * Kích hoạt khi gọi API lấy danh sách sản phẩm thất bại (Offline Fallback).
     */
    @Query("SELECT * FROM products")
    List<ProductEntity> getAllProducts();

    /**
     * Tìm nhanh một sản phẩm trong SQLite theo mã SKU hoặc mã vạch (Barcode).
     * Kích hoạt khi quét mã vạch tại màn hình bán hàng POS trong trạng thái mất mạng.
     */
    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    ProductEntity getProductBySku(String sku);

    /**
     * Tìm kiếm sản phẩm ngoại tuyến theo từ khóa khớp một phần tên sản phẩm hoặc mã SKU.
     * Kích hoạt khi người dùng gõ tìm kiếm sản phẩm trên thanh Search lúc không có kết nối server.
     */
    @Query("SELECT * FROM products WHERE name LIKE '%' || :keyword || '%' OR sku LIKE '%' || :keyword || '%'")
    List<ProductEntity> searchProductsOffline(String keyword);

    /**
     * Thêm mới hoặc ghi đè (Upsert theo khóa chính productId) danh sách sản phẩm vào SQLite.
     * Kích hoạt ngay sau khi DataRepository tải thành công danh sách sản phẩm từ REST API về máy.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProducts(List<ProductEntity> products);

    /**
     * Xóa sạch toàn bộ dữ liệu sản phẩm trong bảng cache cục bộ.
     */
    @Query("DELETE FROM products")
    void deleteAllProducts();
}
