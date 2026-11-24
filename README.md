# EMarket Android — Hệ thống Quản lý Bán hàng & Kho vận Đa chi nhánh

Ứng dụng Android Native (Java 17) kết nối với hệ thống **EMarket Core API (ASP.NET)** và **AI Assistant Service**, hỗ trợ vận hành bán lẻ toàn diện theo phân quyền RBAC.

## Tính năng chính (18 Màn hình)

- **Xác thực & Điều hướng:** Đăng nhập JWT, Google Sign-In (OAuth 2.0), Quên mật khẩu Email OTP, Phân quyền RBAC, Chuyển đổi chi nhánh, Cổng đối tác Nhà cung cấp.
- **Dashboard & Báo cáo:** Biểu đồ doanh thu theo mốc thời gian (MPAndroidChart), KPI tổng quan.
- **Bán hàng tại quầy (POS):** Tìm kiếm/quét mã vạch, giỏ hàng, tính hạng thẻ & đổi điểm, thanh toán Tiền mặt / VietQR PayOS (polling tự động), hóa đơn nhiệt.
- **Sản phẩm & Quét mã:** Danh mục phân trang, cache offline SQLite, chi tiết sản phẩm & lô hàng FEFO, tạo/sửa sản phẩm, quét Barcode bằng CameraX + ML Kit.
- **Kho vận & Tài chính:** Quản lý tồn kho, biến động kho realtime, kiểm kê cân bằng kho, đơn nhập hàng (PO), công nợ & thanh toán NCC, sổ quỹ & đối soát két cuối ca.
- **Trợ lý AI & Dự báo:** Chat AI RAG (Markdown + nhập giọng nói), cố vấn nhập hàng/bất thường, dự báo tồn kho & rủi ro lô hàng.

## Công nghệ sử dụng

- **Ngôn ngữ & SDK:** Android Native Java 17 (`minSdk 26`, `targetSdk 37`), Material Design 3, ViewBinding.
- **Kiến trúc:** MVVM + Zero-Fragment (`Activity` - `ViewModel` - `Adapter` - `DataRepository`).
- **Mạng & Realtime:** Retrofit 2, OkHttp 3, Gson, WebSocket SignalR v2 (`orderHub`, `systemLogHub`), Embedded Network Inspector (Port 8088).
- **Lưu trữ Offline:** Room Database (`emarket_db`), SharedPreferences.
- **Phần cứng & Tiện ích:** CameraX, Google ML Kit Barcode Scanning, Google Play Services Auth, MPAndroidChart, Glide.

## Cấu hình & Chạy thử

1. Mở thư mục dự án bằng **Android Studio** và chờ Gradle Sync.
2. Đảm bảo có file `local.properties` trỏ đúng `sdk.dir` trên máy.
3. Chạy app trên máy ảo hoặc thiết bị thật; có thể đổi URL Backend/AI ngay trong giao diện **Cấu hình Máy chủ** ở màn hình Đăng nhập hoặc Drawer.
