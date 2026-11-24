package com.example.do_an_di_dong.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.api.ApiService;
import com.example.do_an_di_dong.data.database.AppDatabase;
import com.example.do_an_di_dong.data.database.ProductEntity;
import com.example.do_an_di_dong.model.*;
import com.example.do_an_di_dong.util.ApiErrorFormatter;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lớp DataRepository trung tâm (Repository Pattern) điều phối toàn bộ dữ liệu của ứng dụng.
 * - Kết nối trực tiếp với tầng Network (ApiClient / ApiService), tầng Local DB (Room AppDatabase) và Session (UserSessionManager).
 * - Phục vụ dữ liệu cho toàn bộ các ViewModel và Activity/Fragment (Login, Dashboard, POS, Inventory, Purchase, Debt, Order, Quotation, Expense, CRM, User, AI).
 * - Chuẩn hóa RESTful 100%, tự động lấy ApiService động từ ApiClient để hỗ trợ chuyển đổi Endpoint tức thì và fallback Room SQLite khi mất mạng.
 */
public class DataRepository {
    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Khởi tạo DataRepository với ApplicationContext, kết nối Room Database và tạo ThreadPool 4 luồng xử lý tác vụ nền (I/O, SQLite, Multipart).
     */
    public DataRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        this.executorService = Executors.newFixedThreadPool(4);
    }

    private ApiService emarketService() {
        return ApiClient.getEMarketService(context);
    }

    private ApiService aiService() {
        return ApiClient.getAiService(context);
    }

    /**
     * Interface callback bất đồng bộ chuẩn để trả kết quả (onSuccess) hoặc thông báo lỗi (onError) từ Repository về ViewModel/UI.
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /**
     * Kiểm tra trạng thái kết nối mạng của thiết bị để quyết định gọi REST API hay chuyển sang chế độ Offline (Room SQLite).
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private String extractServerError(Response<?> response, String defaultErrorMsg) {
        return ApiErrorFormatter.fromHttpResponse(response, defaultErrorMsg);
    }

    private String extractNetworkError(String defaultContextMsg, Throwable t) {
        return ApiErrorFormatter.fromNetworkFailure(defaultContextMsg, t);
    }

    /**
     * Hàm tiện ích xử lý phản hồi HTTP chuẩn (ResponseBody) cho các API thao tác (POST/PUT/DELETE).
     * Tự động bóc tách JSON để kiểm tra cờ nghiệp vụ "success"/"Success" từ Backend ASP.NET Core trước khi báo thành công.
     */
    private void handleStandardResponse(Response<ResponseBody> response, String defaultErrorMsg, ApiCallback<Boolean> callback) {
        if (response.isSuccessful()) {
            try {
                if (response.body() != null) {
                    String raw = response.body().string();
                    // Kiểm tra trường hợp HTTP 200 nhưng payload JSON báo lỗi nghiệp vụ (success: false)
                    if (raw != null && raw.trim().startsWith("{")) {
                        JSONObject json = new JSONObject(raw);
                        if (json.has("success") && !json.optBoolean("success", true)) {
                            String msg = json.optString("message", json.optString("Message", defaultErrorMsg));
                            callback.onError(ApiErrorFormatter.sanitizeBusinessMessage(msg, defaultErrorMsg));
                            return;
                        }
                        if (json.has("Success") && !json.optBoolean("Success", true)) {
                            String msg = json.optString("Message", json.optString("message", defaultErrorMsg));
                            callback.onError(ApiErrorFormatter.sanitizeBusinessMessage(msg, defaultErrorMsg));
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
            callback.onSuccess(true);
        } else {
            callback.onError(extractServerError(response, defaultErrorMsg));
        }
    }

    // =========================================================================
    // 1. AUTHENTICATION & PASSWORD RECOVERY
    // =========================================================================

    /**
     * Xác thực đăng nhập bằng tài khoản/mật khẩu (kích hoạt khi bấm nút Đăng nhập ở LoginActivity).
     * Nhận username, password; nếu thành công sẽ lưu JWT Token vào ApiClient, lưu thông tin User/RBAC vào UserSessionManager và trả về LoginResponse.
     */
    public void login(String username, String password, ApiCallback<LoginResponse> callback) {
        final String cleanUser = username != null ? username.trim() : "";
        final String cleanPass = password != null ? password.trim() : "";
        LoginRequest req = new LoginRequest(cleanUser, cleanPass);
        emarketService().loginRest(req).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginRes = response.body();
                    if (loginRes.isSuccess()) {
                        // Lưu JWT Token và phiên người dùng (kèm Role/Branch phục vụ phân quyền RBAC)
                        if (loginRes.getToken() != null && !loginRes.getToken().isEmpty()) {
                            ApiClient.saveToken(context, loginRes.getToken());
                        }
                        if (loginRes.getUser() != null) {
                            if (cleanUser.contains("@")) {
                                loginRes.getUser().setEmail(cleanUser);
                            }
                            UserSessionManager.saveUserSession(context, loginRes.getUser());
                        }
                        callback.onSuccess(loginRes);
                    } else {
                        callback.onError(ApiErrorFormatter.sanitizeBusinessMessage(
                                loginRes.getMessage(), "Sai tài khoản hoặc mật khẩu."));
                    }
                } else {
                    callback.onError(extractServerError(response, "Đăng nhập thất bại"));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Không thể kết nối máy chủ", t));
            }
        });
    }

    public void externalLogin(String provider, String email, String idToken, ApiCallback<LoginResponse> callback) {
        externalLogin(provider, email, idToken, null, callback);
    }

    /**
     * Đăng nhập thông qua nhà cung cấp bên thứ ba (Google Sign-In tại LoginActivity).
     * Gửi idToken và email lên Backend để xác thực, sau đó lưu JWT Token và phiên User vào bộ nhớ cục bộ.
     */
    public void externalLogin(String provider, String email, String idToken, String fullName, ApiCallback<LoginResponse> callback) {
        final String cleanEmail = email != null ? email.trim() : "";
        final String cleanFullName = fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null;
        ExternalLoginRequest request = new ExternalLoginRequest(provider, cleanEmail, idToken, cleanFullName);
        emarketService().googleLogin(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginRes = response.body();
                    if (loginRes.isSuccess()) {
                        // Lưu JWT Token và thông tin phiên làm việc sau khi xác thực OAuth thành công
                        if (loginRes.getToken() != null) {
                            ApiClient.saveToken(context, loginRes.getToken());
                        }
                        if (loginRes.getUser() != null) {
                            if (cleanEmail.contains("@")) {
                                loginRes.getUser().setEmail(cleanEmail);
                            }
                            UserSessionManager.saveUserSession(context, loginRes.getUser());
                        }
                        callback.onSuccess(loginRes);
                    } else {
                        callback.onError(ApiErrorFormatter.sanitizeBusinessMessage(
                                loginRes.getMessage(), "Đăng nhập Google thất bại."));
                    }
                } else {
                    callback.onError(extractServerError(response, "Đăng nhập Google thất bại"));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng khi đăng nhập Google", t));
            }
        });
    }

    /**
     * Gửi yêu cầu cấp mã OTP khôi phục mật khẩu về email người dùng (màn hình Quên mật khẩu).
     */
    public void requestOtpRestApi(String email, ApiCallback<Boolean> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("Email", email != null ? email.trim() : "");
        emarketService().requestOtpRestApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Không thể gửi mã OTP"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xác nhận mã OTP và đặt lại mật khẩu mới cho tài khoản (khi người dùng submit form đổi mật khẩu).
     */
    public void resetPasswordRestApi(String email, String otp, String newPassword, ApiCallback<Boolean> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("Email", email != null ? email.trim() : "");
        body.put("Otp", otp != null ? otp.trim() : "");
        body.put("NewPassword", newPassword != null ? newPassword.trim() : "");
        emarketService().resetPasswordRestApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Đặt lại mật khẩu thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 2. BRANCHES & WAREHOUSES
    // =========================================================================

    /**
     * Tải danh sách chi nhánh cho các Spinner lọc chi nhánh (Dashboard, Kho, Đơn hàng...).
     * Áp dụng chiến lược Cache-then-Network: trả ngay dữ liệu cache trong SharedPreferences để UI mượt, sau đó gọi API cập nhật lại cache.
     */
    public void getBranches(ApiCallback<List<Branch>> callback) {
        // Phản hồi nhanh từ cache cục bộ trước khi gọi API
        final List<Branch> cached = UserSessionManager.getCachedBranches(context);
        if (!cached.isEmpty()) {
            callback.onSuccess(cached);
        }
        emarketService().getAllBranches().enqueue(new Callback<List<Branch>>() {
            @Override
            public void onResponse(Call<List<Branch>> call, Response<List<Branch>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Branch> list = response.body();
                    UserSessionManager.saveBranches(context, list);
                    callback.onSuccess(list);
                } else if (cached.isEmpty()) {
                    callback.onError("Không thể tải danh sách chi nhánh.");
                }
            }

            @Override
            public void onFailure(Call<List<Branch>> call, Throwable t) {
                if (cached.isEmpty()) {
                    callback.onError(extractNetworkError("Lỗi kết nối máy chủ", t));
                }
            }
        });
    }

    /**
     * Tải toàn bộ danh sách kho hàng (dùng cho bộ lọc kho ở màn hình Tồn kho, Nhập hàng).
     * Trả dữ liệu từ cache trước (nếu có) và đồng bộ lại từ server.
     */
    public void getAllWarehouses(ApiCallback<List<Warehouse>> callback) {
        final List<Warehouse> cached = UserSessionManager.getCachedWarehouses(context);
        if (!cached.isEmpty()) {
            callback.onSuccess(cached);
        }
        emarketService().getAllWarehouses().enqueue(new Callback<List<Warehouse>>() {
            @Override
            public void onResponse(Call<List<Warehouse>> call, Response<List<Warehouse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Warehouse> list = response.body();
                    UserSessionManager.saveWarehouses(context, list);
                    callback.onSuccess(list);
                } else if (cached.isEmpty()) {
                    callback.onError("Không thể tải danh sách kho hàng.");
                }
            }

            @Override
            public void onFailure(Call<List<Warehouse>> call, Throwable t) {
                if (cached.isEmpty()) {
                    callback.onError(extractNetworkError("Lỗi kết nối máy chủ", t));
                }
            }
        });
    }

    /**
     * Lọc danh sách kho hàng theo tên kho hoặc mã chi nhánh (branchId).
     * Nếu API lọc trả về rỗng hoặc lỗi, tự động fallback về getAllWarehouses để đảm bảo UI luôn có dữ liệu.
     */
    public void getFilteredWarehouses(String warehouseName, Integer branchId, ApiCallback<List<Warehouse>> callback) {
        // Nếu chỉ lọc theo branchId mà không có từ khóa tên kho, ưu tiên gọi endpoint chuyên biệt theo chi nhánh
        if (branchId != null && branchId > 0 && (warehouseName == null || warehouseName.trim().isEmpty())) {
            emarketService().getWarehousesByBranchId(branchId).enqueue(new Callback<List<Warehouse>>() {
                @Override
                public void onResponse(Call<List<Warehouse>> call, Response<List<Warehouse>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        callback.onSuccess(response.body());
                    } else {
                        getAllWarehouses(callback);
                    }
                }

                @Override
                public void onFailure(Call<List<Warehouse>> call, Throwable t) {
                    getAllWarehouses(callback);
                }
            });
            return;
        }
        emarketService().getWarehousesFiltered(warehouseName, branchId).enqueue(new Callback<List<Warehouse>>() {
            @Override
            public void onResponse(Call<List<Warehouse>> call, Response<List<Warehouse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    getAllWarehouses(callback);
                }
            }

            @Override
            public void onFailure(Call<List<Warehouse>> call, Throwable t) {
                getAllWarehouses(callback);
            }
        });
    }

    // =========================================================================
    // 3. DASHBOARD & ANALYTICS
    // =========================================================================

    public void getDashboardSummary(String fromDate, String toDate, String groupBy, ApiCallback<DashboardOverview> callback) {
        getDashboardOverview(null, fromDate, toDate, groupBy, callback);
    }

    /**
     * Lấy dữ liệu tổng quan Dashboard (doanh thu, lợi nhuận, biểu đồ theo ngày/tháng) cho màn hình DashboardFragment.
     * Có cơ chế fallback: nếu endpoint getDashboardSummary không trả về summary hợp lệ, tự động gọi sang endpoint getOverview.
     */
    public void getDashboardOverview(Integer branchId, String fromDate, String toDate, String groupBy, ApiCallback<DashboardOverview> callback) {
        final Integer effectiveBranch = (branchId != null && branchId > 0) ? branchId : null;
        emarketService().getDashboardSummary(effectiveBranch, fromDate, toDate, groupBy).enqueue(new Callback<DashboardOverview>() {
            @Override
            public void onResponse(Call<DashboardOverview> call, Response<DashboardOverview> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSummary() != null) {
                    callback.onSuccess(response.body());
                } else {
                    // Fallback sang endpoint getOverview nếu endpoint chính không có dữ liệu summary
                    emarketService().getOverview(effectiveBranch, fromDate, toDate, groupBy).enqueue(new Callback<DashboardOverview>() {
                        @Override
                        public void onResponse(Call<DashboardOverview> call2, Response<DashboardOverview> res2) {
                            if (res2.isSuccessful() && res2.body() != null) {
                                callback.onSuccess(res2.body());
                            } else {
                                callback.onError(extractServerError(res2, "Không thể tải dữ liệu tổng quan"));
                            }
                        }

                        @Override
                        public void onFailure(Call<DashboardOverview> call2, Throwable t) {
                            callback.onError(extractNetworkError("Lỗi kết nối báo cáo", t));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<DashboardOverview> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối báo cáo", t));
            }
        });
    }

    /**
     * Lấy chỉ số hiệu suất hoạt động của các chi nhánh (so sánh doanh thu/đơn hàng giữa các chi nhánh trên Dashboard).
     */
    public void getBranchPerformance(Integer branchId, ApiCallback<List<BranchPerformance>> callback) {
        emarketService().getBranchPerformance(branchId, null, null).enqueue(new Callback<List<BranchPerformance>>() {
            @Override
            public void onResponse(Call<List<BranchPerformance>> call, Response<List<BranchPerformance>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải hiệu suất chi nhánh"));
                }
            }

            @Override
            public void onFailure(Call<List<BranchPerformance>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải dữ liệu thống kê tổng quan kho hàng (tổng tồn, cảnh báo sắp hết hạn/hết hàng) trong khoảng dayBacks ngày gần nhất.
     */
    public void getWarehouseDashboard(int dayBacks, Integer branchId, Integer warehouseId, ApiCallback<WarehouseDashboard> callback) {
        emarketService().getWarehouseDashboard(dayBacks, branchId, warehouseId).enqueue(new Callback<WarehouseDashboard>() {
            @Override
            public void onResponse(Call<WarehouseDashboard> call, Response<WarehouseDashboard> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải báo cáo kho"));
                }
            }

            @Override
            public void onFailure(Call<WarehouseDashboard> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 4. PRODUCTS, CATEGORIES, LOTS & SUPPLIERS
    // =========================================================================

    public void searchProductsSimple(String keyword, ApiCallback<List<Product>> callback) {
        searchProducts(keyword, null, false, callback);
    }

    public void searchProductsSimple(String keyword, boolean ignoreBranchFilter, ApiCallback<List<Product>> callback) {
        searchProducts(keyword, null, ignoreBranchFilter, callback);
    }

    public void searchProducts(String keyword, Integer categoryId, ApiCallback<List<Product>> callback) {
        searchProducts(keyword, categoryId, false, callback);
    }

    /**
     * Tìm kiếm danh sách sản phẩm theo từ khóa và danh mục (phục vụ màn hình bán hàng POS, Quản lý sản phẩm, Tạo đơn nhập/báo giá).
     * Tự động gắn branchId của phiên hiện tại (trừ khi ignoreBranchFilter = true), lưu cache vào Room SQLite khi có mạng và tự động fallback tìm kiếm Offline trong Room khi mất mạng hoặc lỗi server.
     */
    public void searchProducts(String keyword, Integer categoryId, boolean ignoreBranchFilter, ApiCallback<List<Product>> callback) {
        // Xác định chi nhánh làm việc hiện tại của User để lọc tồn kho sản phẩm tương ứng
        Integer branchId = ignoreBranchFilter ? null : UserSessionManager.getCurrentBranchId(context);
        Integer effectiveBranch = (!ignoreBranchFilter && branchId != null && branchId > 0) ? branchId : null;
        if (isNetworkAvailable()) {
            emarketService().searchProducts(keyword, categoryId, effectiveBranch, null, null).enqueue(new Callback<List<Product>>() {
                @Override
                public void onResponse(Call<List<Product>> call, Response<List<Product>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<Product> products = response.body();
                        callback.onSuccess(products);
                        // Đồng bộ danh sách sản phẩm xuống Room SQLite để dự phòng khi mất mạng
                        cacheProductsOffline(products);
                    } else {
                        searchProductsOffline(keyword, callback);
                    }
                }

                @Override
                public void onFailure(Call<List<Product>> call, Throwable t) {
                    searchProductsOffline(keyword, callback);
                }
            });
        } else {
            // Mất kết nối mạng: chuyển sang truy vấn trực tiếp từ Room SQLite
            searchProductsOffline(keyword, callback);
        }
    }

    /**
     * Lấy danh sách sản phẩm có phân trang (start, length) và bộ lọc đa chiều (từ khóa, danh mục, chi nhánh, nhà cung cấp, kho).
     * Đồng thời cập nhật dữ liệu trang hiện tại vào Room Database để làm giàu dữ liệu offline.
     */
    public void getProductsPaged(int start, int length, String keyword, Integer categoryId, Integer branchId, Integer supplierId, Integer warehouseId, ApiCallback<ProductPagedResponse> callback) {
        emarketService().getProductsPaged(start, length, keyword, categoryId, branchId, supplierId, warehouseId).enqueue(new Callback<ProductPagedResponse>() {
            @Override
            public void onResponse(Call<ProductPagedResponse> call, Response<ProductPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    if (response.body().getData() != null) {
                        cacheProductsOffline(response.body().getData());
                    }
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách sản phẩm"));
                }
            }

            @Override
            public void onFailure(Call<ProductPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lưu danh sách sản phẩm xuống bảng cục bộ (Room SQLite) trên luồng nền (ExecutorService) để phục vụ tra cứu Offline.
     */
    private void cacheProductsOffline(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        executorService.execute(() -> {
            List<ProductEntity> entities = new ArrayList<>();
            for (Product p : products) {
                entities.add(new ProductEntity(
                        p.getProductId(),
                        p.getName(),
                        p.getSku(),
                        p.getPrice(),
                        p.getStockQuantity(),
                        p.getImageUrl()
                ));
            }
            database.productDao().insertProducts(entities);
        });
    }

    /**
     * Tìm kiếm sản phẩm trong Room SQLite khi thiết bị offline hoặc API gặp sự cố, trả kết quả về Main Thread qua mainHandler.
     */
    private void searchProductsOffline(String keyword, ApiCallback<List<Product>> callback) {
        executorService.execute(() -> {
            List<ProductEntity> entities = database.productDao().searchProductsOffline(keyword != null ? keyword : "");
            final List<Product> products = new ArrayList<>();
            for (ProductEntity e : entities) {
                Product p = new Product();
                p.setProductId(e.getProductId());
                p.setName(e.getName());
                p.setSku(e.getSku());
                p.setPrice(e.getPrice());
                p.setStockQuantity(e.getStockQuantity());
                p.setImageUrl(e.getImageUrl());
                products.add(p);
            }
            mainHandler.post(() -> callback.onSuccess(products));
        });
    }

    /**
     * Tra cứu nhanh sản phẩm theo mã SKU/Barcode trực tiếp từ Room SQLite (dùng khi quét mã vạch ở chế độ Offline).
     */
    public void getProductBySkuOffline(String sku, ApiCallback<Product> callback) {
        executorService.execute(() -> {
            ProductEntity entity = database.productDao().getProductBySku(sku);
            if (entity != null) {
                final Product p = new Product();
                p.setProductId(entity.getProductId());
                p.setName(entity.getName());
                p.setSku(entity.getSku());
                p.setPrice(entity.getPrice());
                p.setStockQuantity(entity.getStockQuantity());
                p.setImageUrl(entity.getImageUrl());
                mainHandler.post(() -> callback.onSuccess(p));
            } else {
                mainHandler.post(() -> callback.onError("Không tìm thấy sản phẩm có mã " + sku));
            }
        });
    }

    /**
     * Lấy thông tin chi tiết của một sản phẩm theo ID từ server (hiển thị trên màn hình Chi tiết/Chỉnh sửa sản phẩm).
     */
    public void getProductById(int productId, ApiCallback<Product> callback) {
        emarketService().getProductById(productId).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> call, Response<Product> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Không tìm thấy sản phẩm"));
                }
            }

            @Override
            public void onFailure(Call<Product> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Tạo mới sản phẩm và chỉ nhận kết quả thành công/thất bại (Boolean).
     */
    public void createProduct(ProductDTO dto, ApiCallback<Boolean> callback) {
        createProductWithId(dto, new ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                callback.onSuccess(true);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Gửi yêu cầu tạo mới sản phẩm lên Backend và bóc tách ID sản phẩm vừa tạo từ JSON phản hồi (dùng để upload ảnh đính kèm ngay sau khi tạo).
     */
    public void createProductWithId(ProductDTO dto, ApiCallback<Integer> callback) {
        emarketService().createProductApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    int newId = 0;
                    try {
                        if (response.body() != null) {
                            String raw = response.body().string();
                            // Đọc JSON trả về để kiểm tra cờ lỗi nghiệp vụ và trích xuất productId mới
                            if (raw != null && raw.trim().startsWith("{")) {
                                JSONObject json = new JSONObject(raw);
                                if ((json.has("success") && !json.optBoolean("success", true))
                                        || (json.has("Success") && !json.optBoolean("Success", true))) {
                                    String msg = json.optString("message", json.optString("Message", "Tạo sản phẩm thất bại."));
                                    callback.onError(msg);
                                    return;
                                }
                                newId = json.optInt("productId", json.optInt("ProductId", json.optInt("id", 0)));
                            }
                        }
                    } catch (Exception ignored) {}
                    callback.onSuccess(newId);
                } else {
                    callback.onError(extractServerError(response, "Lỗi tạo sản phẩm"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Cập nhật thông tin sản phẩm hiện có (khi người dùng bấm Lưu ở màn hình sửa sản phẩm).
     */
    public void updateProduct(ProductDTO dto, ApiCallback<Boolean> callback) {
        emarketService().updateProductApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Lỗi cập nhật sản phẩm", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Xóa sản phẩm theo ID khỏi hệ thống.
     */
    public void deleteProduct(int productId, ApiCallback<Boolean> callback) {
        emarketService().deleteProductApi(productId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Lỗi xóa sản phẩm", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Đọc dữ liệu nhị phân từ Uri ảnh trong thiết bị (ContentResolver) và đóng gói thành MultipartBody.Part để upload lên server.
     */
    private MultipartBody.Part buildMultipartFromUri(Uri uri, String partName) throws Exception {
        String fileName = "upload_" + System.currentTimeMillis() + ".jpg";
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null || mimeType.isEmpty()) mimeType = "image/jpeg";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) fileName = name;
                }
            }
        } catch (Exception ignored) {}

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("Không thể đọc tệp ảnh đã chọn.");
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        byte[] bytes = buffer.toByteArray();
        RequestBody reqFile = RequestBody.create(MediaType.parse(mimeType), bytes);
        return MultipartBody.Part.createFormData(partName, fileName, reqFile);
    }

    /**
     * Phân tích JSON phản hồi từ các API upload file để lấy đường dẫn URL ảnh trên server (hỗ trợ nhiều key: url, Image, imageUrl, fileUrl).
     */
    private String extractUploadedUrl(Response<ResponseBody> response) throws Exception {
        if (!response.isSuccessful() || response.body() == null) {
            throw new Exception(extractServerError(response, "Tải ảnh thất bại"));
        }
        String raw = response.body().string();
        JSONObject json = new JSONObject(raw);
        if (json.has("success") && !json.optBoolean("success", true)) {
            throw new Exception(ApiErrorFormatter.sanitizeBusinessMessage(
                    json.optString("message", "Tải ảnh thất bại"), "Tải ảnh thất bại"));
        }
        String url = json.optString("url", json.optString("Image", json.optString("imageUrl", json.optString("fileUrl", ""))));
        if (url == null || url.isEmpty()) {
            throw new Exception("Máy chủ không trả về đường dẫn ảnh.");
        }
        return url;
    }

    /**
     * Tải ảnh sản phẩm lên server trên luồng nền (ExecutorService) và trả về đường dẫn URL ảnh sau khi upload thành công.
     */
    public void uploadProductImage(Uri imageUri, Integer productId, ApiCallback<String> callback) {
        executorService.execute(() -> {
            try {
                MultipartBody.Part filePart = buildMultipartFromUri(imageUri, "file");
                String pidStr = (productId != null && productId > 0) ? String.valueOf(productId) : "0";
                RequestBody pidBody = RequestBody.create(MediaType.parse("text/plain"), pidStr);
                Response<ResponseBody> response = emarketService().uploadProductImage(filePart, pidBody).execute();
                final String url = extractUploadedUrl(response);
                mainHandler.post(() -> callback.onSuccess(url));
            } catch (java.io.FileNotFoundException e) {
                mainHandler.post(() -> callback.onError("Không thể đọc tệp ảnh đã chọn."));
            } catch (java.io.IOException e) {
                mainHandler.post(() -> callback.onError(extractNetworkError("Lỗi tải ảnh sản phẩm", e)));
            } catch (Exception e) {
                String friendly = ApiErrorFormatter.sanitizeBusinessMessage(e.getMessage(), "Lỗi tải ảnh sản phẩm.");
                mainHandler.post(() -> callback.onError(friendly));
            }
        });
    }

    /**
     * Tải ảnh đại diện (Avatar) của nhân sự lên server; nếu endpoint upload-avatar lỗi thì tự động fallback sang endpoint upload ảnh sản phẩm.
     */
    public void uploadUserAvatar(Uri imageUri, Integer userId, ApiCallback<String> callback) {
        executorService.execute(() -> {
            try {
                MultipartBody.Part filePart = buildMultipartFromUri(imageUri, "file");
                String uidStr = (userId != null && userId > 0) ? String.valueOf(userId) : "0";
                RequestBody uidBody = RequestBody.create(MediaType.parse("text/plain"), uidStr);
                Response<ResponseBody> response = emarketService().uploadUserAvatar(filePart, uidBody).execute();
                final String url = extractUploadedUrl(response);
                mainHandler.post(() -> callback.onSuccess(url));
            } catch (Exception e) {
                // Fallback to product upload endpoint if upload-avatar fails
                uploadProductImage(imageUri, 0, callback);
            }
        });
    }

    /**
     * Tải ảnh chứng từ thanh toán công nợ nhà cung cấp lên server; tự động fallback sang uploadProductImage nếu endpoint chính gặp lỗi.
     */
    public void uploadDebtProof(Uri imageUri, Integer debtId, ApiCallback<String> callback) {
        executorService.execute(() -> {
            try {
                MultipartBody.Part filePart = buildMultipartFromUri(imageUri, "file");
                String didStr = (debtId != null && debtId > 0) ? String.valueOf(debtId) : "0";
                RequestBody didBody = RequestBody.create(MediaType.parse("text/plain"), didStr);
                Response<ResponseBody> response = emarketService().uploadDebtProof(filePart, didBody).execute();
                final String url = extractUploadedUrl(response);
                mainHandler.post(() -> callback.onSuccess(url));
            } catch (Exception e) {
                uploadProductImage(imageUri, 0, callback);
            }
        });
    }

    /**
     * Tìm sản phẩm bằng mã vạch (khi quét Camera Barcode ở màn hình POS hoặc Kho).
     * Xử lý linh hoạt cả cấu trúc JSON mảng trực tiếp lẫn JSON Object bọc trong trường "data"/"Data", nếu không khớp sẽ fallback sang searchProducts theo từ khóa.
     */
    public void searchProductsByBarcode(String barcode, ApiCallback<List<Product>> callback) {
        Integer branchId = UserSessionManager.getCurrentBranchId(context);
        Integer effectiveBranch = (branchId != null && branchId > 0) ? branchId : null;
        emarketService().searchProductsByBarcode(barcode, effectiveBranch).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String raw = response.body().string();
                        Gson gson = new Gson();
                        JsonElement el = JsonParser.parseString(raw);
                        Type listType = new TypeToken<List<Product>>() {}.getType();
                        if (el.isJsonArray()) {
                            List<Product> list = gson.fromJson(el, listType);
                            callback.onSuccess(list != null ? list : new ArrayList<>());
                            return;
                        } else if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("data") && obj.get("data").isJsonArray()) {
                                List<Product> list = gson.fromJson(obj.get("data"), listType);
                                callback.onSuccess(list != null ? list : new ArrayList<>());
                                return;
                            }
                            if (obj.has("Data") && obj.get("Data").isJsonArray()) {
                                List<Product> list = gson.fromJson(obj.get("Data"), listType);
                                callback.onSuccess(list != null ? list : new ArrayList<>());
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                // Fallback sang tìm kiếm sản phẩm thông thường nếu API barcode không trả về kết quả hợp lệ
                searchProducts(barcode, null, callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                searchProducts(barcode, null, callback);
            }
        });
    }

    /**
     * Kích hoạt sản phẩm vào kho với ngưỡng tồn kho tối thiểu (minStock) và tối đa (maxStock).
     */
    public void activeProduct(int productId, int minStock, int maxStock, ApiCallback<Boolean> callback) {
        emarketService().activeProductApi(productId, minStock, maxStock).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Kích hoạt sản phẩm thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    public void getProductCategories(ApiCallback<List<ProductCategoryDTO>> callback) {
        getAllCategories(callback);
    }

    /**
     * Tải toàn bộ danh mục sản phẩm (hiển thị trên Spinner lọc danh mục hoặc form thêm/sửa sản phẩm).
     */
    public void getAllCategories(ApiCallback<List<ProductCategoryDTO>> callback) {
        emarketService().getAllCategories().enqueue(new Callback<List<ProductCategoryDTO>>() {
            @Override
            public void onResponse(Call<List<ProductCategoryDTO>> call, Response<List<ProductCategoryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh mục"));
                }
            }

            @Override
            public void onFailure(Call<List<ProductCategoryDTO>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối danh mục", t));
            }
        });
    }

    /**
     * Lấy danh sách các lô hàng (ProductLot - hạn sử dụng, số lượng tồn theo lô) của một sản phẩm cụ thể.
     */
    public void getProductLotsByProductId(int productId, ApiCallback<List<ProductLot>> callback) {
        emarketService().getProductLotsByProductId(productId).enqueue(new Callback<List<ProductLot>>() {
            @Override
            public void onResponse(Call<List<ProductLot>> call, Response<List<ProductLot>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải lô hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<ProductLot>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy danh sách hình ảnh phụ của sản phẩm theo productId (hiển thị trong bộ sưu tập ảnh chi tiết sản phẩm).
     */
    public void getProductImages(int productId, ApiCallback<List<ProductImage>> callback) {
        emarketService().getProductImages(productId).enqueue(new Callback<List<ProductImage>>() {
            @Override
            public void onResponse(Call<List<ProductImage>> call, Response<List<ProductImage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải ảnh sản phẩm"));
                }
            }

            @Override
            public void onFailure(Call<List<ProductImage>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lưu thông tin bản ghi hình ảnh sản phẩm (sau khi đã upload file ảnh lên server thành công).
     */
    public void createProductImage(ProductImage dto, ApiCallback<Boolean> callback) {
        emarketService().createProductImageApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Thêm ảnh sản phẩm thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa hình ảnh phụ của sản phẩm theo ID bản ghi ảnh.
     */
    public void deleteProductImage(int id, ApiCallback<Boolean> callback) {
        emarketService().deleteProductImageApi(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa ảnh thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải danh sách toàn bộ Nhà cung cấp (dùng cho màn hình Quản lý NCC, Tạo phiếu nhập hàng, Lọc công nợ).
     * Sau khi tải xong sẽ đối chiếu thêm danh sách sản phẩm để xác định chính xác cờ CanBeDeleted cho từng nhà cung cấp.
     */
    public void getAllSuppliers(ApiCallback<List<SupplierDTO>> callback) {
        emarketService().getAllSuppliers().enqueue(new Callback<List<SupplierDTO>>() {
            @Override
            public void onResponse(Call<List<SupplierDTO>> call, Response<List<SupplierDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    resolveSupplierCanBeDeletedIfMissing(response.body(), callback);
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải nhà cung cấp"));
                }
            }

            @Override
            public void onFailure(Call<List<SupplierDTO>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Web API /api/admin/product/suppliers khi không truyền name gọi GetAllSupplierAsync()
     * nên CanBeDeleted luôn là false (khác với Web /Admin/Supplier/GetfilteredSupplier).
     * Khi mọi phần tử đều false, đối chiếu thêm bảng Products (cùng quy tắc !_db.Products.Any(p => p.supplier_id == id))
     * để đồng bộ chính xác trạng thái cho phép xóa giống Web.
     */
    private void resolveSupplierCanBeDeletedIfMissing(final List<SupplierDTO> suppliers, final ApiCallback<List<SupplierDTO>> callback) {
        if (suppliers == null || suppliers.isEmpty()) {
            callback.onSuccess(suppliers != null ? suppliers : new ArrayList<>());
            return;
        }
        for (SupplierDTO s : suppliers) {
            if (s != null && s.isCanBeDeleted()) {
                callback.onSuccess(suppliers);
                return;
            }
        }
        emarketService().getAllProducts().enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> call, Response<List<Product>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Tập hợp tất cả supplierId đã gắn với ít nhất 1 sản phẩm để khóa nút Xóa
                    java.util.Set<Integer> linkedSupplierIds = new java.util.HashSet<>();
                    for (Product p : response.body()) {
                        if (p != null && p.getSupplierId() > 0) {
                            linkedSupplierIds.add(p.getSupplierId());
                        }
                    }
                    for (SupplierDTO s : suppliers) {
                        if (s != null) {
                            s.setCanBeDeleted(!linkedSupplierIds.contains(s.getSupplierId()));
                        }
                    }
                }
                callback.onSuccess(suppliers);
            }

            @Override
            public void onFailure(Call<List<Product>> call, Throwable t) {
                callback.onSuccess(suppliers);
            }
        });
    }

    /**
     * Thêm mới một nhà cung cấp vào hệ thống (khi submit form Thêm nhà cung cấp).
     */
    public void createSupplier(SupplierDTO dto, ApiCallback<Boolean> callback) {
        emarketService().createSupplierApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Tạo nhà cung cấp thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật thông tin nhà cung cấp hiện có.
     */
    public void updateSupplier(SupplierDTO dto, ApiCallback<Boolean> callback) {
        emarketService().updateSupplierApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật nhà cung cấp thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa nhà cung cấp theo supplierId (chỉ xóa được khi nhà cung cấp chưa phát sinh sản phẩm, đơn nhập hoặc công nợ).
     */
    public void deleteSupplier(int supplierId, ApiCallback<Boolean> callback) {
        emarketService().deleteSupplierApi(supplierId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Không thể xóa nhà cung cấp (có thể đã phát sinh sản phẩm, đơn nhập hoặc công nợ liên quan)", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 5. INVENTORY, STOCK MOVEMENTS, PURCHASES & SUPPLIER DEBTS
    // =========================================================================

    /**
     * Tải danh sách tồn kho thực tế theo kho (warehouseId), chi nhánh (branchId) hoặc sản phẩm (productId) cho màn hình Quản lý kho.
     */
    public void getInventoryItems(Integer warehouseId, Integer branchId, Integer productId, ApiCallback<List<InventoryItem>> callback) {
        emarketService().getInventoryItems(warehouseId, branchId, productId).enqueue(new Callback<List<InventoryItem>>() {
            @Override
            public void onResponse(Call<List<InventoryItem>> call, Response<List<InventoryItem>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải tồn kho thực tế"));
                }
            }

            @Override
            public void onFailure(Call<List<InventoryItem>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    public void getStockMovementsPaged(int start, int length, Integer warehouseId, String type, String fromDate, String toDate, String keyword, ApiCallback<StockMovementPagedResponse> callback) {
        getStockMovementsPaged(start, length, warehouseId, null, type, fromDate, toDate, keyword, callback);
    }

    /**
     * Truy vấn lịch sử biến động kho (nhập/xuất/điều chỉnh) có phân trang và lọc theo kho, chi nhánh, loại giao dịch, khoảng ngày, từ khóa.
     */
    public void getStockMovementsPaged(int start, int length, Integer warehouseId, Integer branchId, String type, String fromDate, String toDate, String keyword, ApiCallback<StockMovementPagedResponse> callback) {
        // Chuẩn hóa các tham số bộ lọc ("ALL" hoặc <= 0 sẽ truyền null để Backend lấy tất cả)
        String effectiveType = (type != null && !type.equalsIgnoreCase("ALL") && !type.trim().isEmpty()) ? type : null;
        Integer effectiveWarehouseId = (warehouseId != null && warehouseId > 0) ? warehouseId : null;
        Integer effectiveBranchId = (branchId != null && branchId > 0) ? branchId : null;
        emarketService().getStockMovementsPaged(start, length, effectiveWarehouseId, effectiveBranchId, effectiveType, fromDate, toDate, keyword).enqueue(new Callback<StockMovementPagedResponse>() {
            @Override
            public void onResponse(Call<StockMovementPagedResponse> call, Response<StockMovementPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải biến động kho"));
                }
            }

            @Override
            public void onFailure(Call<StockMovementPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Gửi phiếu điều chỉnh/kiểm kê tồn kho (tăng/giảm số lượng thực tế tại kho kèm lý do điều chỉnh).
     */
    public void adjustStock(StockAdjustmentRequest request, ApiCallback<Boolean> callback) {
        emarketService().adjustStockApi(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Điều chỉnh kho thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tìm kiếm và phân trang danh sách đơn nhập hàng (PurchaseOrder) theo từ khóa, nhà cung cấp, chi nhánh, kho, trạng thái đơn và thanh toán.
     */
    public void searchPurchasesPaged(int start, int length, String keyword, Integer supplierId, Integer branchId, Integer warehouseId, String status, String paymentStatus, String fromDate, String toDate, ApiCallback<PurchasePagedResponse> callback) {
        emarketService().searchPurchasesPaged(start, length, keyword, supplierId, branchId, warehouseId, status, paymentStatus, fromDate, toDate).enqueue(new Callback<PurchasePagedResponse>() {
            @Override
            public void onResponse(Call<PurchasePagedResponse> call, Response<PurchasePagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải đơn nhập hàng"));
                }
            }

            @Override
            public void onFailure(Call<PurchasePagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tìm kiếm nhanh danh sách đơn nhập hàng không phân trang theo từ khóa.
     */
    public void searchPurchases(String keyword, ApiCallback<List<PurchaseOrder>> callback) {
        emarketService().searchPurchases(keyword, null, null, null, null, null, null, null).enqueue(new Callback<List<PurchaseOrder>>() {
            @Override
            public void onResponse(Call<List<PurchaseOrder>> call, Response<List<PurchaseOrder>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải đơn nhập hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<PurchaseOrder>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy thông tin chi tiết một phiếu nhập hàng (bao gồm danh sách mặt hàng, lô hàng, nhà cung cấp) theo ID.
     */
    public void getPurchaseById(int id, ApiCallback<PurchaseOrder> callback) {
        emarketService().getPurchaseById(id).enqueue(new Callback<PurchaseOrder>() {
            @Override
            public void onResponse(Call<PurchaseOrder> call, Response<PurchaseOrder> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Không thể lấy chi tiết phiếu nhập"));
                }
            }

            @Override
            public void onFailure(Call<PurchaseOrder> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tạo mới phiếu nhập hàng từ nhà cung cấp (khi bấm Lưu ở màn hình Tạo phiếu nhập).
     */
    public void createPurchase(PurchaseOrder po, ApiCallback<Boolean> callback) {
        emarketService().createPurchaseRest(po).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Tạo phiếu nhập hàng thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật phiếu nhập hàng: ưu tiên gọi phương thức PUT chuẩn, nếu lỗi HTTP hoặc mạng sẽ fallback sang endpoint POST updatePurchaseRest.
     */
    public void updatePurchase(PurchaseOrder po, ApiCallback<Boolean> callback) {
        emarketService().updatePurchasePut(po).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    handleStandardResponse(response, "Cập nhật phiếu nhập hàng thất bại", callback);
                } else {
                    // Fallback sang endpoint POST nếu endpoint PUT trả về lỗi
                    emarketService().updatePurchaseRest(po).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call2, Response<ResponseBody> res2) {
                            handleStandardResponse(res2, "Cập nhật phiếu nhập hàng thất bại", callback);
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call2, Throwable t2) {
                            callback.onError(extractNetworkError("Lỗi mạng", t2));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                emarketService().updatePurchaseRest(po).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call2, Response<ResponseBody> res2) {
                        handleStandardResponse(res2, "Cập nhật phiếu nhập hàng thất bại", callback);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call2, Throwable t2) {
                        callback.onError(extractNetworkError("Lỗi mạng", t2));
                    }
                });
            }
        });
    }

    /**
     * Cập nhật trạng thái phiếu nhập hàng (VD: Duyệt nhập kho / Hoàn thành / Hủy).
     * Khi chuyển trạng thái hoàn thành, Backend sẽ tự động cộng tồn kho theo lô và ghi nhận công nợ nhà cung cấp.
     */
    public void updatePurchaseStatus(int purchaseOrderId, String status, String notes, ApiCallback<Boolean> callback) {
        // Backend /purchase/{id}/status và /purchase/status bị trùng [HttpPost]/[HttpPut] Route attribute (HTTP 500 Ambiguous Action).
        // Đồng thời gửi kèm Details/ProductLots sẽ kích hoạt CreateProductLotAsync dễ lỗi SqlDateTime overflow.
        // Vì vậy gửi payload chỉ chứa Header + Status (Details = null, OrderDate = null) qua PUT api/admin/inventory/purchase:
        // PurchaseService.UpdatePurchaseAsync giữ nguyên chi tiết, total_amount, cập nhật công nợ và kích hoạt trigger nhập kho an toàn.
        PurchaseOrder statusDto = new PurchaseOrder();
        statusDto.setPurchaseOrderId(purchaseOrderId);
        statusDto.setStatus(status);
        statusDto.setPaymentStatus(null);
        statusDto.setOrderDate(null);
        statusDto.setDetails(null);
        int currentUserId = UserSessionManager.getCurrentUserId(context);
        if (currentUserId > 0) {
            statusDto.setUserId(currentUserId);
        }
        if (notes != null && !notes.trim().isEmpty()) {
            statusDto.setNotes(notes.trim());
        } else {
            statusDto.setNotes(null);
        }
        updatePurchase(statusDto, callback);
    }

    /**
     * Xóa phiếu nhập hàng theo ID (chỉ áp dụng với phiếu chưa duyệt nhập kho).
     */
    public void deletePurchase(int purchaseOrderId, ApiCallback<Boolean> callback) {
        emarketService().deletePurchaseRest(purchaseOrderId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Xóa phiếu nhập hàng thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy danh sách công nợ nhà cung cấp có phân trang và lọc theo từ khóa, nhà cung cấp, trạng thái nợ, khoảng thời gian.
     * Tự động fallback sang getDebtsListPaged nếu endpoint getDebtsPaged đầu tiên không khả dụng.
     */
    public void getDebtsPaged(final int start, final int length, final String keyword, final Integer supplierId, final String status, final String fromDate, final String toDate, final ApiCallback<SupplierDebtPagedResponse> callback) {
        emarketService().getDebtsPaged(start, length, keyword, supplierId, status, fromDate, toDate).enqueue(new Callback<SupplierDebtPagedResponse>() {
            @Override
            public void onResponse(Call<SupplierDebtPagedResponse> call, Response<SupplierDebtPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    // Fallback sang endpoint dự phòng getDebtsListPaged
                    emarketService().getDebtsListPaged(start, length, keyword, supplierId, status, fromDate, toDate).enqueue(new Callback<SupplierDebtPagedResponse>() {
                        @Override
                        public void onResponse(Call<SupplierDebtPagedResponse> call2, Response<SupplierDebtPagedResponse> res2) {
                            if (res2.isSuccessful() && res2.body() != null) {
                                callback.onSuccess(res2.body());
                            } else {
                                callback.onError(extractServerError(res2, "Lỗi tải công nợ nhà cung cấp"));
                            }
                        }

                        @Override
                        public void onFailure(Call<SupplierDebtPagedResponse> call2, Throwable t) {
                            callback.onError(extractNetworkError("Lỗi mạng", t));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<SupplierDebtPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải toàn bộ danh sách công nợ nhà cung cấp không phân trang.
     */
    public void getFilteredDebts(ApiCallback<List<SupplierDebt>> callback) {
        emarketService().getFilteredDebts(null, null, null, null, null).enqueue(new Callback<List<SupplierDebt>>() {
            @Override
            public void onResponse(Call<List<SupplierDebt>> call, Response<List<SupplierDebt>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải công nợ NCC"));
                }
            }

            @Override
            public void onFailure(Call<List<SupplierDebt>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy số liệu thống kê tổng quan công nợ (tổng nợ, đã trả, còn lại) để hiển thị lên thẻ KPI đầu màn hình Công nợ.
     */
    public void getDebtSummary(Integer supplierId, String fromDate, String toDate, ApiCallback<DebtSummary> callback) {
        emarketService().getDebtSummary(supplierId, fromDate, toDate).enqueue(new Callback<DebtSummary>() {
            @Override
            public void onResponse(Call<DebtSummary> call, Response<DebtSummary> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải thống kê công nợ"));
                }
            }

            @Override
            public void onFailure(Call<DebtSummary> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật thông tin khoản công nợ nhà cung cấp (hạn thanh toán, ghi chú, trạng thái).
     */
    public void updateSupplierDebtApi(SupplierDebt dto, ApiCallback<Boolean> callback) {
        emarketService().updateSupplierDebtApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật công nợ thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy lịch sử các lần thanh toán trả nợ (SupplierPayment) của một khoản công nợ theo debtId.
     */
    public void getPaymentsByDebtId(int debtId, ApiCallback<List<SupplierPayment>> callback) {
        emarketService().getPaymentsByDebtId(debtId).enqueue(new Callback<List<SupplierPayment>>() {
            @Override
            public void onResponse(Call<List<SupplierPayment>> call, Response<List<SupplierPayment>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải lịch sử thanh toán nợ"));
                }
            }

            @Override
            public void onFailure(Call<List<SupplierPayment>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tạo phiếu thanh toán trả nợ cho nhà cung cấp (kèm số tiền trả, phương thức thanh toán và ảnh chứng từ nếu có).
     */
    public void createSupplierPaymentApi(SupplierPayment paymentDto, ApiCallback<Boolean> callback) {
        emarketService().createSupplierPaymentApi(paymentDto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Tạo thanh toán công nợ thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 6. SALES, ORDERS & PAYOS VIETQR
    // =========================================================================

    /**
     * Tải danh sách đơn bán hàng (Order) có phân trang và lọc theo nhân viên, chi nhánh, trạng thái, khoảng ngày, từ khóa.
     */
    public void getOrdersPaged(int start, int length, Integer userId, Integer branchId, String status, String fromDate, String toDate, String keyword, ApiCallback<OrderPagedResponse> callback) {
        emarketService().getOrdersPaged(start, length, userId, branchId, status, fromDate, toDate, keyword).enqueue(new Callback<OrderPagedResponse>() {
            @Override
            public void onResponse(Call<OrderPagedResponse> call, Response<OrderPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách đơn hàng"));
                }
            }

            @Override
            public void onFailure(Call<OrderPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối đơn hàng", t));
            }
        });
    }

    /**
     * Lấy toàn bộ danh sách đơn bán hàng không phân trang.
     */
    public void getAllOrders(ApiCallback<List<Order>> callback) {
        emarketService().getAllOrders().enqueue(new Callback<List<Order>>() {
            @Override
            public void onResponse(Call<List<Order>> call, Response<List<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách đơn hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<Order>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối đơn hàng", t));
            }
        });
    }

    /**
     * Tải danh sách các mặt hàng chi tiết (OrderDetail) bên trong một đơn hàng theo orderId.
     */
    public void getOrderDetails(int orderId, ApiCallback<List<OrderDetail>> callback) {
        emarketService().getOrderDetails(orderId).enqueue(new Callback<List<OrderDetail>>() {
            @Override
            public void onResponse(Call<List<OrderDetail>> call, Response<List<OrderDetail>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải chi tiết đơn hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<OrderDetail>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật trạng thái đơn bán hàng (VD: Hoàn thành, Đã hủy, Đang xử lý) từ màn hình Chi tiết đơn hàng.
     */
    public void updateOrderStatus(int orderId, String status, ApiCallback<Boolean> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        body.put("connectionId", "");
        emarketService().updateOrderStatusApi(orderId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật trạng thái đơn hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Xóa đơn hàng theo orderId khỏi hệ thống.
     */
    public void deleteOrder(int orderId, ApiCallback<Boolean> callback) {
        emarketService().deleteOrderApi(orderId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa đơn hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Thực hiện thanh toán (Checkout) giỏ hàng tại quầy POS (PosFragment).
     * Gửi danh sách sản phẩm, khách hàng, điểm thưởng/giảm giá, phương thức thanh toán lên server để trừ tồn kho, tích điểm và tạo hóa đơn.
     */
    public void checkoutOrder(CheckoutRequest request, ApiCallback<CheckoutResult> callback) {
        emarketService().checkoutOrder(request).enqueue(new Callback<CheckoutResult>() {
            @Override
            public void onResponse(Call<CheckoutResult> call, Response<CheckoutResult> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Thanh toán đơn hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<CheckoutResult> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối khi thanh toán", t));
            }
        });
    }

    /**
     * Lấy lịch sử giao dịch thanh toán (Payment) gắn với một đơn bán hàng theo orderId.
     */
    public void getPaymentsByOrder(int orderId, ApiCallback<List<Payment>> callback) {
        emarketService().getPaymentsByOrder(orderId).enqueue(new Callback<List<Payment>>() {
            @Override
            public void onResponse(Call<List<Payment>> call, Response<List<Payment>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải lịch sử thanh toán"));
                }
            }

            @Override
            public void onFailure(Call<List<Payment>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Thêm mới một dòng mặt hàng (OrderDetail) vào đơn hàng đã tồn tại.
     */
    public void createOrderDetailApi(OrderDetail dto, ApiCallback<Boolean> callback) {
        emarketService().createOrderDetailApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Thêm mặt hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật số lượng hoặc đơn giá của một dòng mặt hàng (OrderDetail) trong đơn hàng.
     */
    public void updateOrderDetailApi(OrderDetail dto, ApiCallback<Boolean> callback) {
        emarketService().updateOrderDetailApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật mặt hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa một dòng mặt hàng (OrderDetail) khỏi đơn hàng theo ID chi tiết.
     */
    public void deleteOrderDetailApi(int id, ApiCallback<Boolean> callback) {
        emarketService().deleteOrderDetailApi(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa mặt hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Khởi tạo link thanh toán và mã VietQR động qua cổng PayOS (khi thu ngân chọn thanh toán Chuyển khoản QR tại POS).
     */
    public void createPayOSLink(CreateQrRequest qrRequest, ApiCallback<CreateQrResponse> callback) {
        emarketService().createPayOSLink(qrRequest).enqueue(new Callback<CreateQrResponse>() {
            @Override
            public void onResponse(Call<CreateQrResponse> call, Response<CreateQrResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Tạo mã VietQR PayOS thất bại"));
                }
            }

            @Override
            public void onFailure(Call<CreateQrResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Kiểm tra trạng thái giao dịch PayOS theo orderCode (polling định kỳ khi khách đang quét mã QR để tự động chốt đơn khi tiền vào).
     */
    public void checkPayOSStatus(long orderCode, ApiCallback<PayOSStatusResponse> callback) {
        emarketService().checkPayOSStatus(orderCode).enqueue(new Callback<PayOSStatusResponse>() {
            @Override
            public void onResponse(Call<PayOSStatusResponse> call, Response<PayOSStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Kiểm tra trạng thái PayOS thất bại"));
                }
            }

            @Override
            public void onFailure(Call<PayOSStatusResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 7. QUOTATIONS
    // =========================================================================

    public void getQuotations(ApiCallback<List<Quotation>> callback) {
        getQuotations(null, callback);
    }

    /**
     * Lấy danh sách phiếu báo giá (Quotation) theo trạng thái (không phân trang).
     */
    public void getQuotations(String status, ApiCallback<List<Quotation>> callback) {
        emarketService().getQuotations(null, null, status, null, null).enqueue(new Callback<List<Quotation>>() {
            @Override
            public void onResponse(Call<List<Quotation>> call, Response<List<Quotation>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải báo giá"));
                }
            }

            @Override
            public void onFailure(Call<List<Quotation>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy danh sách phiếu báo giá có phân trang và lọc theo từ khóa, chi nhánh, trạng thái, khoảng ngày.
     */
    public void getQuotationsPaged(int start, int length, String keyword, Integer branchId, String status, String fromDate, String toDate, ApiCallback<QuotationPagedResponse> callback) {
        emarketService().getQuotationsPaged(start, length, keyword, branchId, status, fromDate, toDate).enqueue(new Callback<QuotationPagedResponse>() {
            @Override
            public void onResponse(Call<QuotationPagedResponse> call, Response<QuotationPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải báo giá"));
                }
            }

            @Override
            public void onFailure(Call<QuotationPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải chi tiết một phiếu báo giá (bao gồm thông tin khách hàng và danh sách sản phẩm báo giá) theo ID.
     */
    public void getQuotationDetail(int id, ApiCallback<Quotation> callback) {
        emarketService().getQuotationDetail(id).enqueue(new Callback<Quotation>() {
            @Override
            public void onResponse(Call<Quotation> call, Response<Quotation> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Không tìm thấy chi tiết báo giá"));
                }
            }

            @Override
            public void onFailure(Call<Quotation> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lưu phiếu báo giá (dùng chung cho cả tạo mới và cập nhật phiếu báo giá).
     */
    public void saveQuotation(QuotationSaveRequest request, ApiCallback<Boolean> callback) {
        emarketService().saveQuotationApi(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Lưu báo giá thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    public void updateQuotation(QuotationSaveRequest request, ApiCallback<Boolean> callback) {
        saveQuotation(request, callback);
    }

    /**
     * Thay đổi trạng thái của phiếu báo giá (VD: Gửi báo giá, Khách đồng ý, Từ chối).
     */
    public void changeQuotationStatus(int quotationId, String status, ApiCallback<Boolean> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("Status", status);
        emarketService().changeQuotationStatusApi(quotationId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật trạng thái báo giá thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Chuyển đổi một phiếu báo giá đã được duyệt thành Đơn bán hàng chính thức (tự động gắn userId của nhân viên đang đăng nhập).
     */
    public void convertQuotationToOrder(int quotationId, ApiCallback<Boolean> callback) {
        int userId = UserSessionManager.getCurrentUserId(context);
        emarketService().convertQuotationToOrderApi(quotationId, userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Chuyển báo giá thành đơn hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa phiếu báo giá theo quotationId.
     */
    public void deleteQuotation(int quotationId, ApiCallback<Boolean> callback) {
        emarketService().deleteQuotationApi(quotationId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa báo giá thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 8. EXPENSES & CASHBOOK
    // =========================================================================

    /**
     * Lấy danh sách phiếu chi / chi phí vận hành (Expense) có phân trang và lọc theo chi nhánh, danh mục chi, khoảng ngày, trạng thái duyệt.
     */
    public void getExpensesPaged(int start, int length, Integer branchId, Integer categoryId, String fromDate, String toDate, String status, ApiCallback<ExpensePagedResponse> callback) {
        emarketService().getExpensesPaged(start, length, branchId, categoryId, fromDate, toDate, status).enqueue(new Callback<ExpensePagedResponse>() {
            @Override
            public void onResponse(Call<ExpensePagedResponse> call, Response<ExpensePagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải chi phí"));
                }
            }

            @Override
            public void onFailure(Call<ExpensePagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải thống kê tổng hợp sổ quỹ/chi phí (tổng chi đã duyệt, chờ duyệt, từ chối) cho màn hình Quản lý chi phí.
     */
    public void getExpenseSummary(Integer branchId, String fromDate, String toDate, ApiCallback<ExpenseSummary> callback) {
        emarketService().getExpenseSummary(branchId, fromDate, toDate).enqueue(new Callback<ExpenseSummary>() {
            @Override
            public void onResponse(Call<ExpenseSummary> call, Response<ExpenseSummary> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải tổng hợp chi phí"));
                }
            }

            @Override
            public void onFailure(Call<ExpenseSummary> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy danh sách toàn bộ danh mục khoản chi (ExpenseCategory - tiền điện, mặt bằng, lương...) để hiển thị trong Spinner khi lập phiếu chi.
     */
    public void getAllExpenseCategories(ApiCallback<List<ExpenseCategory>> callback) {
        emarketService().getAllExpenseCategories().enqueue(new Callback<List<ExpenseCategory>>() {
            @Override
            public void onResponse(Call<List<ExpenseCategory>> call, Response<List<ExpenseCategory>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh mục chi phí"));
                }
            }

            @Override
            public void onFailure(Call<List<ExpenseCategory>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tạo mới một danh mục chi phí (CategoryName, Description, IsActive = true).
     */
    public void createExpenseCategory(String name, String description, ApiCallback<Boolean> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("CategoryName", name);
        body.put("Description", description != null ? description : "");
        body.put("IsActive", true);
        emarketService().createExpenseCategoryApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Tạo danh mục chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa danh mục chi phí theo categoryId.
     */
    public void deleteExpenseCategory(int categoryId, ApiCallback<Boolean> callback) {
        emarketService().deleteExpenseCategoryApi(categoryId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Xóa danh mục chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lập phiếu chi mới từ đối tượng Expense DTO (tự động điền userId của người lập phiếu từ UserSessionManager nếu chưa có).
     */
    public void createExpense(Expense dto, ApiCallback<Boolean> callback) {
        if (dto.getUserId() <= 0) {
            dto.setUserId(UserSessionManager.getCurrentUserId(context));
        }
        emarketService().createExpenseApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Tạo phiếu chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Duyệt hoặc từ chối phiếu chi (cập nhật Status, RejectReason nếu từ chối, và PaymentMethod).
     */
    public void updateExpenseStatus(int expenseId, String status, String rejectReason, String paymentMethod, ApiCallback<Boolean> callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ExpenseId", expenseId);
        payload.put("Status", status);
        if (rejectReason != null) payload.put("RejectReason", rejectReason);
        if (paymentMethod != null) payload.put("PaymentMethod", paymentMethod);
        emarketService().updateExpenseStatusApi(payload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Cập nhật trạng thái phiếu chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa phiếu chi theo expenseId.
     */
    public void deleteExpense(int expenseId, ApiCallback<Boolean> callback) {
        emarketService().deleteExpenseApi(expenseId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Xóa phiếu chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 9. CUSTOMERS & CRM
    // =========================================================================

    /**
     * Lấy danh sách khách hàng (Customer) có phân trang và tìm kiếm theo từ khóa (tên, số điện thoại, email).
     */
    public void getCustomersPaged(int start, int length, String keyword, ApiCallback<CustomerPagedResponse> callback) {
        emarketService().getCustomersPaged(start, length, keyword).enqueue(new Callback<CustomerPagedResponse>() {
            @Override
            public void onResponse(Call<CustomerPagedResponse> call, Response<CustomerPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách khách hàng"));
                }
            }

            @Override
            public void onFailure(Call<CustomerPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tìm kiếm nhanh 20 khách hàng đầu tiên theo từ khóa (phục vụ ô chọn khách hàng tại quầy POS hoặc lập Báo giá).
     */
    public void searchCustomers(String keyword, ApiCallback<List<Customer>> callback) {
        getCustomersPaged(0, 20, keyword, new ApiCallback<CustomerPagedResponse>() {
            @Override
            public void onSuccess(CustomerPagedResponse result) {
                if (result != null && result.getData() != null) {
                    callback.onSuccess(result.getData());
                } else {
                    callback.onSuccess(new ArrayList<>());
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Tải toàn bộ danh sách khách hàng trong hệ thống CRM.
     */
    public void getAllCustomers(ApiCallback<List<Customer>> callback) {
        emarketService().getAllCustomers().enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> call, Response<List<Customer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải khách hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<Customer>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Thêm mới hồ sơ khách hàng (khi bấm Lưu ở form tạo khách hàng tại CRM hoặc tạo nhanh tại POS).
     */
    public void createCustomer(Customer customer, ApiCallback<Boolean> callback) {
        emarketService().createCustomerApi(customer).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Tạo khách hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Cập nhật thông tin hồ sơ khách hàng hiện có.
     */
    public void updateCustomer(Customer customer, ApiCallback<Boolean> callback) {
        emarketService().updateCustomerApi(customer).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật khách hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Xóa khách hàng khỏi hệ thống theo customerId.
     */
    public void deleteCustomer(int customerId, ApiCallback<Boolean> callback) {
        emarketService().deleteCustomerApi(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa khách hàng thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối", t));
            }
        });
    }

    /**
     * Tải danh sách chương trình/hạng thẻ tích điểm thân thiết (LoyaltyProgram).
     * Bóc tách linh hoạt cả trường hợp JSON trả về là mảng trực tiếp hoặc Object bọc trong "data"/"Data"/"items"/"Items".
     */
    public void getAllLoyaltyApi(ApiCallback<List<LoyaltyProgram>> callback) {
        emarketService().getAllLoyaltyApi().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String raw = response.body().string();
                        Gson gson = new Gson();
                        JsonElement el = JsonParser.parseString(raw);
                        Type listType = new TypeToken<List<LoyaltyProgram>>() {}.getType();
                        if (el.isJsonArray()) {
                            List<LoyaltyProgram> list = gson.fromJson(el, listType);
                            callback.onSuccess(list != null ? list : new ArrayList<>());
                            return;
                        } else if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            String[] keys = {"data", "Data", "items", "Items"};
                            for (String k : keys) {
                                if (obj.has(k) && obj.get(k).isJsonArray()) {
                                    List<LoyaltyProgram> list = gson.fromJson(obj.get(k), listType);
                                    callback.onSuccess(list != null ? list : new ArrayList<>());
                                    return;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    callback.onSuccess(new ArrayList<>());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải chương trình tích điểm"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy thông tin điểm thưởng/hạng thành viên của một khách hàng cụ thể theo customerId.
     */
    public void getCustomerLoyalty(int customerId, ApiCallback<List<LoyaltyProgram>> callback) {
        getAllLoyaltyApi(new ApiCallback<List<LoyaltyProgram>>() {
            @Override
            public void onSuccess(List<LoyaltyProgram> result) {
                List<LoyaltyProgram> filtered = new ArrayList<>();
                if (result != null) {
                    for (LoyaltyProgram lp : result) {
                        if (lp != null && (lp.getCustomerId() == customerId || lp.getCustomerId() == 0)) {
                            filtered.add(lp);
                        }
                    }
                }
                callback.onSuccess(filtered);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Lấy thống kê số lượng khách hàng (Tổng khách hàng, Khách VIP, Khách mới trong N ngày) để hiển thị trên thẻ thống kê CRM.
     */
    public void getCustomerStats(int days, ApiCallback<Map<String, Integer>> callback) {
        emarketService().getCustomerStats(days).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Map<String, Integer> stats = new HashMap<>();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.has("data") && json.optJSONObject("data") != null) {
                            json = json.optJSONObject("data");
                        }
                        stats.put("TotalCustomers", json.optInt("TotalCustomers", json.optInt("totalCustomers", 0)));
                        stats.put("VipCustomers", json.optInt("VipCustomers", json.optInt("vipCustomers", 0)));
                        stats.put("NewCustomersSinceDate", json.optInt("NewCustomersSinceDate", json.optInt("newCustomersSinceDate", json.optInt("newCustomers", 0))));
                    } catch (Exception ignored) {}
                }
                callback.onSuccess(stats);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onSuccess(new HashMap<>());
            }
        });
    }

    // =========================================================================
    // 10. USERS & ROLES MANAGEMENT
    // =========================================================================

    /**
     * Tải danh sách tài khoản nhân sự (User) có phân trang và tìm kiếm theo từ khóa; tự động fallback sang getUsersSearchPaged nếu endpoint chính không trả về dữ liệu.
     */
    public void getUsersPaged(final int start, final int length, final String keyword, final ApiCallback<UserPagedResponse> callback) {
        emarketService().getUsersPaged(start, length, keyword).enqueue(new Callback<UserPagedResponse>() {
            @Override
            public void onResponse(Call<UserPagedResponse> call, Response<UserPagedResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    callback.onSuccess(response.body());
                } else {
                    // Fallback sang endpoint tìm kiếm nhân sự dự phòng
                    emarketService().getUsersSearchPaged(start, length, keyword).enqueue(new Callback<UserPagedResponse>() {
                        @Override
                        public void onResponse(Call<UserPagedResponse> call2, Response<UserPagedResponse> res2) {
                            if (res2.isSuccessful() && res2.body() != null) {
                                callback.onSuccess(res2.body());
                            } else {
                                callback.onError(extractServerError(res2, "Lỗi tải danh sách nhân sự"));
                            }
                        }

                        @Override
                        public void onFailure(Call<UserPagedResponse> call2, Throwable t) {
                            callback.onError(extractNetworkError("Lỗi mạng", t));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<UserPagedResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy toàn bộ danh sách tài khoản nhân sự trong hệ thống.
     */
    public void getAllUsers(ApiCallback<List<User>> callback) {
        emarketService().getAllUsers().enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách nhân sự"));
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tạo mới tài khoản nhân sự từ đối tượng User DTO (bao gồm gán RoleId và BranchId cho phân quyền RBAC).
     */
    public void createUser(User dto, ApiCallback<Boolean> callback) {
        emarketService().createUserApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Tạo tài khoản nhân sự thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật thông tin tài khoản nhân sự (họ tên, vai trò RBAC, chi nhánh, trạng thái khóa/mở).
     */
    public void updateUser(User dto, ApiCallback<Boolean> callback) {
        emarketService().updateUserApi(dto).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Cập nhật nhân sự thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Xóa tài khoản nhân sự theo userId.
     */
    public void deleteUser(int userId, ApiCallback<Boolean> callback) {
        emarketService().deleteUserApi(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError(extractServerError(response, "Xóa nhân sự thất bại"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy thông tin chi tiết của một nhân sự theo userId (dùng để kiểm tra trạng thái tài khoản hoặc xem hồ sơ cá nhân).
     */
    public void getUserById(int userId, ApiCallback<User> callback) {
        emarketService().getUserById(userId).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Không thể kiểm tra trạng thái tài khoản"));
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải danh sách tất cả các Vai trò (Role - Admin, Manager, Cashier, Warehouse...) phục vụ phân quyền RBAC khi tạo/sửa nhân sự.
     */
    public void getAllRoles(ApiCallback<List<Role>> callback) {
        emarketService().getAllRoles().enqueue(new Callback<List<Role>>() {
            @Override
            public void onResponse(Call<List<Role>> call, Response<List<Role>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải danh sách vai trò"));
                }
            }

            @Override
            public void onFailure(Call<List<Role>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    // =========================================================================
    // 11. AI ASSISTANT & FORECASTING
    // =========================================================================

    /**
     * Gửi câu hỏi (prompt) của người dùng tới dịch vụ AI Chatbot (aiService) và nhận câu trả lời tư vấn quản trị/bán hàng.
     */
    public void askAi(String prompt, String username, ApiCallback<ChatResponse> callback) {
        ChatRequest request = new ChatRequest(prompt, username);
        aiService().askAi(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Trợ lý AI không phản hồi"));
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                callback.onError(extractNetworkError("Không thể kết nối máy chủ AI", t));
            }
        });
    }

    /**
     * Lấy danh sách gợi ý nhập hàng tự động bằng AI (ReplenishmentAdvice) cho chi nhánh dựa trên tốc độ bán và tồn kho hiện tại.
     */
    public void getReplenishmentAdvice(int branchId, ApiCallback<List<ReplenishmentAdvice>> callback) {
        emarketService().getReplenishmentAdvice(branchId).enqueue(new Callback<List<ReplenishmentAdvice>>() {
            @Override
            public void onResponse(Call<List<ReplenishmentAdvice>> call, Response<List<ReplenishmentAdvice>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải gợi ý nhập hàng"));
                }
            }

            @Override
            public void onFailure(Call<List<ReplenishmentAdvice>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy danh sách các cảnh báo bất thường (Anomaly - đột biến doanh thu, tồn kho lệch, chi phí tăng) do AI phát hiện tại chi nhánh.
     */
    public void getAnomalies(int branchId, ApiCallback<List<Anomaly>> callback) {
        emarketService().getAnomalies(branchId).enqueue(new Callback<List<Anomaly>>() {
            @Override
            public void onResponse(Call<List<Anomaly>> call, Response<List<Anomaly>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải cảnh báo bất thường"));
                }
            }

            @Override
            public void onFailure(Call<List<Anomaly>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Lấy các nhận định chuyên sâu về xu hướng sản phẩm (Insight - mặt hàng bán chạy, hàng tồn đọng vốn) từ mô hình AI.
     */
    public void getProductInsights(int branchId, ApiCallback<List<Insight>> callback) {
        emarketService().getProductInsights(branchId).enqueue(new Callback<List<Insight>>() {
            @Override
            public void onResponse(Call<List<Insight>> call, Response<List<Insight>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải phân tích sản phẩm"));
                }
            }

            @Override
            public void onFailure(Call<List<Insight>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Tải kết quả dự báo nhu cầu tồn kho (InventoryForecast - mô hình Prophet) theo chi nhánh.
     */
    public void getInventoryForecast(int branchId, ApiCallback<List<InventoryForecast>> callback) {
        emarketService().getInventoryForecast(branchId).enqueue(new Callback<List<InventoryForecast>>() {
            @Override
            public void onResponse(Call<List<InventoryForecast>> call, Response<List<InventoryForecast>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải dự báo tồn kho"));
                }
            }

            @Override
            public void onFailure(Call<List<InventoryForecast>> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Phân tích rủi ro tài chính theo lô hàng (LotFinancialRisk - các lô sắp hết hạn có nguy cơ thất thoát vốn).
     * Hỗ trợ parse JSON linh hoạt từ mảng hoặc các thuộc tính Details/data/items.
     */
    public void getLotFinancialRisk(int branchId, ApiCallback<List<LotFinancialRisk>> callback) {
        emarketService().getLotFinancialRisk(branchId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String raw = response.body().string();
                        Gson gson = new Gson();
                        JsonElement el = JsonParser.parseString(raw);
                        Type listType = new TypeToken<List<LotFinancialRisk>>() {}.getType();
                        if (el.isJsonArray()) {
                            List<LotFinancialRisk> list = gson.fromJson(el, listType);
                            callback.onSuccess(list != null ? list : new ArrayList<>());
                            return;
                        } else if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            String[] keys = {"Details", "details", "data", "Data", "items", "Items"};
                            for (String k : keys) {
                                if (obj.has(k) && obj.get(k).isJsonArray()) {
                                    List<LotFinancialRisk> list = gson.fromJson(obj.get(k), listType);
                                    callback.onSuccess(list != null ? list : new ArrayList<>());
                                    return;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    callback.onSuccess(new ArrayList<>());
                } else {
                    callback.onError(extractServerError(response, "Lỗi tải rủi ro lô hàng"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    public void requestForgotPasswordOtp(String email, ApiCallback<Boolean> callback) {
        requestOtpRestApi(email, callback);
    }

    public void resetPasswordWithOtp(String email, String otp, String newPassword, ApiCallback<Boolean> callback) {
        resetPasswordRestApi(email, otp, newPassword, callback);
    }

    public void getFilteredBranches(String name, String address, ApiCallback<List<Branch>> callback) {
        getBranches(callback);
    }

    public void getStockMovements(ApiCallback<List<StockMovement>> callback) {
        getStockMovements(null, null, null, null, callback);
    }

    /**
     * Lấy nhanh 100 bản ghi biến động kho mới nhất theo bộ lọc kho, chi nhánh, loại giao dịch và từ khóa.
     */
    public void getStockMovements(Integer warehouseId, Integer branchId, String type, String keyword, ApiCallback<List<StockMovement>> callback) {
        getStockMovementsPaged(0, 100, warehouseId, branchId, type, null, null, keyword, new ApiCallback<StockMovementPagedResponse>() {
            @Override
            public void onSuccess(StockMovementPagedResponse result) {
                callback.onSuccess(result != null && result.getData() != null ? result.getData() : new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Tạo phiếu chi mới từ cấu trúc Map key-value động (tự động chèn UserId của phiên hiện tại nếu chưa có).
     */
    public void createExpense(Map<String, Object> body, ApiCallback<Boolean> callback) {
        if (!body.containsKey("UserId") && !body.containsKey("userId")) {
            body.put("UserId", UserSessionManager.getCurrentUserId(context));
        }
        emarketService().createExpenseMapApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Tạo phiếu chi thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    public void updateExpenseStatus(int expenseId, String status, ApiCallback<Boolean> callback) {
        updateExpenseStatus(expenseId, status, null, "Cash", callback);
    }

    /**
     * Tạo mới tài khoản nhân sự bằng payload Map key-value động.
     */
    public void createUser(Map<String, Object> body, ApiCallback<Boolean> callback) {
        emarketService().createUserMapApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Tạo tài khoản nhân sự thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    /**
     * Cập nhật thông tin tài khoản nhân sự theo userId bằng payload Map key-value động.
     */
    public void updateUser(int userId, Map<String, Object> body, ApiCallback<Boolean> callback) {
        body.put("userId", userId);
        body.put("UserId", userId);
        emarketService().updateUserMapApi(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Cập nhật nhân sự thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi mạng", t));
            }
        });
    }

    public void getSalesAnomalies(int branchId, ApiCallback<List<Anomaly>> callback) {
        getAnomalies(branchId, callback);
    }

    public void getSmartInsights(int branchId, ApiCallback<List<Insight>> callback) {
        getProductInsights(branchId, callback);
    }

    public void getLotRiskAnalysis(int branchId, ApiCallback<List<LotFinancialRisk>> callback) {
        getLotFinancialRisk(branchId, callback);
    }

    /**
     * Kích hoạt tiến trình chạy phân tích AI tổng hợp trên Backend cho chi nhánh hiện tại (khi người dùng bấm nút Chạy phân tích AI).
     */
    public void runAiAnalysis(int branchId, String mode, ApiCallback<Boolean> callback) {
        int bId = branchId > 0 ? branchId : UserSessionManager.getCurrentBranchId(context);
        Map<String, Object> body = new HashMap<>();
        body.put("mode", mode != null ? mode : "system");
        body.put("Mode", mode != null ? mode : "system");
        body.put("branchId", bId);
        body.put("BranchId", bId);
        emarketService().runAiAnalysis(bId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Chạy phân tích AI thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối máy chủ AI", t));
            }
        });
    }

    public void runProphetPredictions(ApiCallback<Boolean> callback) {
        runProphet(callback);
    }

    /**
     * Kích hoạt thuật toán dự báo chuỗi thời gian Facebook Prophet trên server cho chi nhánh hiện tại để tính toán lại nhu cầu tồn kho.
     */
    public void runProphet(ApiCallback<Boolean> callback) {
        int bId = UserSessionManager.getCurrentBranchId(context);
        if (bId <= 0) bId = 1;
        Map<String, Object> body = new HashMap<>();
        body.put("mode", "system");
        body.put("branchId", bId);
        emarketService().runProphet(bId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                handleStandardResponse(response, "Chạy thuật toán dự báo thất bại", callback);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(extractNetworkError("Lỗi kết nối thuật toán Prophet", t));
            }
        });
    }
}
