package com.example.do_an_di_dong.data.api;

import com.example.do_an_di_dong.model.*;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Giao diện Retrofit định nghĩa toàn bộ các endpoint REST Web API giao tiếp với EMarket Core Backend & AI Service.
 * Được khởi tạo thông qua ApiClient.getEMarketService() / getAiService() và được gọi bởi DataRepository
 * cùng các Activity/Fragment/ViewModel cho 11 phân hệ nghiệp vụ (Auth, Chi nhánh/Kho, Dashboard, AI, Sản phẩm,
 * Tồn kho/Nhập hàng/Công nợ, Bán hàng POS/PayOS, Báo giá, Chi phí, Khách hàng CRM, Nhân sự RBAC).
 */
public interface ApiService {

    // =========================================================================
    // 1. NHÓM ĐĂNG NHẬP & XÁC THỰC (AUTHENTICATION)
    // =========================================================================

    /** Đăng nhập bằng tài khoản nội bộ (username/password), trả về JWT token và thông tin phân quyền User. */
    @POST("api/admin/user-management/auth/login")
    Call<LoginResponse> loginRest(@Body LoginRequest request);

    /** Đăng nhập thông qua Google Sign-In bằng ID Token nhận từ Google SDK. */
    @POST("api/admin/user-management/auth/google-login")
    Call<LoginResponse> googleLogin(@Body ExternalLoginRequest request);

    /** Đăng nhập qua nhà cung cấp xác thực bên ngoài (External OAuth Provider). */
    @POST("api/admin/user-management/auth/external-login")
    Call<LoginResponse> externalLogin(@Body ExternalLoginRequest request);

    /** Yêu cầu Backend gửi mã OTP khôi phục mật khẩu về email của người dùng. */
    @POST("api/admin/user-management/auth/request-otp")
    Call<ResponseBody> requestOtpRestApi(@Body Map<String, String> body);

    /** Xác nhận mã OTP và đặt lại mật khẩu mới cho tài khoản. */
    @POST("api/admin/user-management/auth/reset-password")
    Call<ResponseBody> resetPasswordRestApi(@Body Map<String, String> body);

    /** Đăng xuất phiên làm việc hiện tại trên Backend. */
    @POST("Admin/Login/SignOut")
    Call<ResponseBody> signOut();

    // =========================================================================
    // 2. NHÓM CHI NHÁNH & KHO HÀNG (BRANCHES & WAREHOUSES)
    // =========================================================================

    /** Lấy danh sách toàn bộ chi nhánh trong hệ thống để hiển thị bộ lọc chi nhánh (Branch Selector). */
    @GET("api/admin/user-management/branches")
    Call<List<Branch>> getAllBranches();

    /** Lấy thông tin chi tiết của một chi nhánh theo ID. */
    @GET("api/admin/user-management/branches/{id}")
    Call<Branch> getBranchById(@Path("id") int id);

    /** Tìm chi nhánh gần vị trí GPS hiện tại nhất trong bán kính maxDist. */
    @GET("api/admin/user-management/branches/nearest")
    Call<Branch> getNearestBranch(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("maxDist") double maxDist
    );

    /** Lấy danh sách tất cả kho hàng trên toàn hệ thống. */
    @GET("api/admin/inventory/warehouses/all")
    Call<List<Warehouse>> getAllWarehouses();

    /** Lọc danh sách kho hàng theo tên tìm kiếm và mã chi nhánh (branchId). */
    @GET("api/admin/inventory/warehouses/all")
    Call<List<Warehouse>> getWarehousesFiltered(
            @Query("name") String name,
            @Query("branchId") Integer branchId
    );

    /** Lấy danh sách các kho trực thuộc một chi nhánh cụ thể. */
    @GET("api/admin/inventory/warehouses/by-branch/{branchId}")
    Call<List<Warehouse>> getWarehousesByBranchId(@Path("branchId") int branchId);

    /** Lấy thông tin chi tiết của một kho hàng theo ID. */
    @GET("api/admin/inventory/warehouses/{id}")
    Call<Warehouse> getWarehouseDetail(@Path("id") int id);

    /** Tạo mới một kho hàng từ dữ liệu WarehouseDTO. */
    @POST("api/admin/inventory/warehouses")
    Call<ResponseBody> createWarehouseApi(@Body WarehouseDTO dto);

    /** Cập nhật thông tin kho hàng hiện có. */
    @POST("api/admin/inventory/warehouses/update")
    Call<ResponseBody> updateWarehouseApi(@Body WarehouseDTO dto);

    /** Xóa kho hàng theo ID. */
    @POST("api/admin/inventory/warehouses/delete/{id}")
    Call<ResponseBody> deleteWarehouseApi(@Path("id") int id);

    // =========================================================================
    // 3. NHÓM BÁO CÁO TỔNG QUAN (DASHBOARD)
    // =========================================================================

    /** Lấy số liệu tổng hợp doanh thu, đơn hàng, lợi nhuận trên Dashboard theo chi nhánh và khoảng thời gian. */
    @GET("api/admin/dashboard/summary")
    Call<DashboardOverview> getDashboardSummary(
            @Query("branchId") Integer branchId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("groupBy") String groupBy
    );

    /** Lấy dữ liệu tổng quan Dashboard (KPIs và chuỗi dữ liệu biểu đồ doanh thu). */
    @GET("api/admin/dashboard/overview")
    Call<DashboardOverview> getOverview(
            @Query("branchId") Integer branchId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("groupBy") String groupBy
    );

    /** Lấy báo cáo so sánh hiệu suất kinh doanh giữa các chi nhánh. */
    @GET("api/admin/dashboard/branch-performance")
    Call<List<BranchPerformance>> getBranchPerformance(
            @Query("branchId") Integer branchId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy dữ liệu biểu đồ cơ cấu tồn kho theo chi nhánh. */
    @GET("api/admin/dashboard/stock-chart")
    Call<ResponseBody> getStockChart(@Query("branchId") Integer branchId);

    /** Lấy số liệu thống kê xuất-nhập-tồn dành riêng cho Dashboard Kho hàng. */
    @GET("api/admin/dashboard/warehouse")
    Call<WarehouseDashboard> getWarehouseDashboard(
            @Query("dayBacks") int dayBacks,
            @Query("branchId") Integer branchId,
            @Query("warehouseId") Integer warehouseId
    );

    /** Lấy báo cáo tổng hợp công nợ nhà cung cấp trên Dashboard tài chính. */
    @GET("api/admin/dashboard/debt")
    Call<DebtDashboard> getDebtDashboard(
            @Query("branchId") Integer branchId,
            @Query("supplierId") Integer supplierId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    // =========================================================================
    // 4. NHÓM PHÂN TÍCH AI & TRỢ LÝ AI (AI ANALYSIS & ASSISTANT)
    // =========================================================================

    /** Gửi câu hỏi của người dùng tới dịch vụ AI Chatbot và nhận câu trả lời tư vấn quản trị. */
    @POST("api/chat/ask")
    Call<ChatResponse> askAi(@Body ChatRequest request);

    /** Kích hoạt tiến trình phân tích dữ liệu kinh doanh bằng AI cho chi nhánh được chọn. */
    @POST("api/admin/ai-analysis/run-analysis/{branchId}")
    Call<ResponseBody> runAiAnalysis(
            @Path("branchId") int branchId,
            @Body Map<String, Object> body
    );

    /** Kích hoạt mô hình chuỗi thời gian Prophet để dự báo nhu cầu tiêu thụ và tồn kho cho chi nhánh. */
    @POST("api/admin/ai-analysis/run-prophet/{branchId}")
    Call<ResponseBody> runProphet(
            @Path("branchId") int branchId,
            @Body Map<String, Object> body
    );

    /** Lấy danh sách gợi ý nhập hàng bổ sung (Replenishment Advice) do AI tính toán cho chi nhánh. */
    @GET("api/admin/ai-analysis/replenishment-advice/{branchId}")
    Call<List<ReplenishmentAdvice>> getReplenishmentAdvice(@Path("branchId") int branchId);

    /** Lấy danh sách sản phẩm đề xuất đẩy mạnh bán hàng theo phân tích AI. */
    @GET("api/admin/ai-analysis/recommendations/{branchId}")
    Call<List<Product>> getRecommendations(@Path("branchId") int branchId);

    /** Lấy danh sách các điểm bất thường (Anomalies) về doanh số hoặc tồn kho tại chi nhánh. */
    @GET("api/admin/ai-analysis/anomalies/{branchId}")
    Call<List<Anomaly>> getAnomalies(@Path("branchId") int branchId);

    /** Lấy các nhận định chuyên sâu (Insights) về xu hướng sản phẩm từ AI. */
    @GET("api/admin/ai-analysis/insights/{branchId}")
    Call<List<Insight>> getProductInsights(@Path("branchId") int branchId);

    /** Lấy kết quả dự báo tồn kho (Inventory Forecast) trong kỳ tới của chi nhánh. */
    @GET("api/admin/ai-analysis/inventory-forecast/{branchId}")
    Call<List<InventoryForecast>> getInventoryForecast(@Path("branchId") int branchId);

    /** Lấy danh sách sản phẩm được dự báo có sức mua cao nhất tại chi nhánh. */
    @GET("api/admin/ai-analysis/top-predicted/{branchId}")
    Call<List<Product>> getTopPredictedProducts(@Path("branchId") int branchId);

    /** Đánh giá rủi ro tài chính đối với các lô hàng sắp hết hạn hoặc chậm luân chuyển tại chi nhánh. */
    @GET("api/admin/ai-analysis/lot-financial-risk/{branchId}")
    Call<ResponseBody> getLotFinancialRisk(@Path("branchId") int branchId);

    // =========================================================================
    // 5. NHÓM SẢN PHẨM, DANH MỤC, NHÀ CUNG CẤP & LÔ HÀNG (PRODUCTS)
    // =========================================================================

    /** Lấy toàn bộ danh sách sản phẩm (dùng cho đồng bộ cache Room SQLite và danh mục POS). */
    @GET("api/admin/product/products")
    Call<List<Product>> getAllProducts();

    /** Lấy danh sách sản phẩm có phân trang (start, length) kèm bộ lọc từ khóa, danh mục, chi nhánh, NCC, kho. */
    @GET("api/admin/product/products/paged")
    Call<ProductPagedResponse> getProductsPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("categoryId") Integer categoryId,
            @Query("branchId") Integer branchId,
            @Query("supplierId") Integer supplierId,
            @Query("warehouseId") Integer warehouseId
    );

    /** Tìm kiếm danh sách sản phẩm không phân trang theo bộ lọc đa điều kiện. */
    @GET("api/admin/product/products/search")
    Call<List<Product>> searchProducts(
            @Query("keyword") String keyword,
            @Query("categoryId") Integer categoryId,
            @Query("branchId") Integer branchId,
            @Query("supplierId") Integer supplierId,
            @Query("warehouseId") Integer warehouseId
    );

    /** Tìm kiếm nhanh sản phẩm theo từ khóa và chi nhánh (dùng cho ô autocomplete chọn sản phẩm). */
    @GET("api/admin/product/products/search-simple")
    Call<List<Product>> searchProductsSimple(
            @Query("keyword") String keyword,
            @Query("branchId") Integer branchId
    );

    /** Tra cứu sản phẩm tức thì bằng mã vạch (Barcode/SKU) khi quét camera tại màn hình bán hàng POS hoặc kiểm kho. */
    @GET("api/admin/product/products/barcode/{barcode}")
    Call<ResponseBody> searchProductsByBarcode(
            @Path("barcode") String barcode,
            @Query("branchId") Integer branchId
    );

    /** Tải file ảnh sản phẩm (Multipart Form-Data) lên máy chủ và gắn với productId. */
    @Multipart
    @POST("api/admin/product/products/upload-image")
    Call<ResponseBody> uploadProductImage(
            @Part MultipartBody.Part file,
            @Part("productId") RequestBody productId
    );

    /** Lấy thông tin chi tiết của một sản phẩm theo ID. */
    @GET("api/admin/product/products/{id}")
    Call<Product> getProductById(@Path("id") int id);

    /** Tạo mới một sản phẩm trong danh mục hàng hóa. */
    @POST("api/admin/product/products")
    Call<ResponseBody> createProductApi(@Body ProductDTO dto);

    /** Cập nhật thông tin sản phẩm hiện có. */
    @POST("api/admin/product/products/update")
    Call<ResponseBody> updateProductApi(@Body ProductDTO dto);

    /** Xóa sản phẩm theo ID. */
    @POST("api/admin/product/products/delete/{id}")
    Call<ResponseBody> deleteProductApi(@Path("id") int id);

    /** Kích hoạt sản phẩm và thiết lập định mức tồn kho tối thiểu (minStock) / tối đa (maxStock). */
    @POST("api/admin/product/products/active")
    Call<ResponseBody> activeProductApi(
            @Query("productId") int productId,
            @Query("minStock") int minStock,
            @Query("maxStock") int maxStock
    );

    /** Lấy danh sách cảnh báo các sản phẩm sắp hết hàng (tồn kho chạm ngưỡng minStock). */
    @GET("api/admin/product/products/low-stock-alerts")
    Call<List<Product>> getLowStockAlerts(@Query("top") int top);

    /** Lấy danh sách các lô hàng (ProductLot - hạn sử dụng, số lượng lô) của một sản phẩm cụ thể. */
    @GET("api/admin/product/lots/by-product/{productId}")
    Call<List<ProductLot>> getProductLotsByProductId(@Path("productId") int productId);

    /** Lấy toàn bộ danh sách lô hàng trong kho. */
    @GET("api/admin/product/lots")
    Call<List<ProductLot>> getAllProductLots();

    /** Lấy danh sách bộ sưu tập hình ảnh phụ của một sản phẩm. */
    @GET("api/admin/product/products/{productId}/images")
    Call<List<ProductImage>> getProductImages(@Path("productId") int productId);

    /** Thêm bản ghi hình ảnh mới cho sản phẩm. */
    @POST("api/admin/product/products/images")
    Call<ResponseBody> createProductImageApi(@Body ProductImage dto);

    /** Xóa một hình ảnh của sản phẩm theo ID ảnh. */
    @POST("api/admin/product/products/images/delete/{id}")
    Call<ResponseBody> deleteProductImageApi(@Path("id") int id);

    /** Lấy toàn bộ danh mục nhóm sản phẩm (Categories). */
    @GET("api/admin/product/categories")
    Call<List<ProductCategoryDTO>> getAllCategories();

    /** Tạo mới một danh mục nhóm sản phẩm. */
    @POST("api/admin/product/categories")
    Call<ResponseBody> createProductCategoryApi(@Body ProductCategoryDTO dto);

    /** Cập nhật thông tin danh mục nhóm sản phẩm. */
    @POST("api/admin/product/categories/update")
    Call<ResponseBody> updateProductCategoryApi(@Body ProductCategoryDTO dto);

    /** Xóa danh mục nhóm sản phẩm theo ID. */
    @POST("api/admin/product/categories/delete/{id}")
    Call<ResponseBody> deleteProductCategoryApi(@Path("id") int id);

    /** Lấy danh sách tất cả nhà cung cấp (Suppliers). */
    @GET("api/admin/product/suppliers")
    Call<List<SupplierDTO>> getAllSuppliers();

    /** Tìm kiếm nhà cung cấp theo tên. */
    @GET("api/admin/product/suppliers")
    Call<List<SupplierDTO>> getAllSuppliersFiltered(@Query("name") String name);

    /** Lấy thông tin chi tiết nhà cung cấp theo ID. */
    @GET("api/admin/product/suppliers/{id}")
    Call<SupplierDTO> getSupplierById(@Path("id") int id);

    /** Thêm mới một nhà cung cấp. */
    @POST("api/admin/product/suppliers")
    Call<ResponseBody> createSupplierApi(@Body SupplierDTO dto);

    /** Cập nhật thông tin nhà cung cấp. */
    @POST("api/admin/product/suppliers/update")
    Call<ResponseBody> updateSupplierApi(@Body SupplierDTO dto);

    /** Xóa nhà cung cấp theo ID. */
    @POST("api/admin/product/suppliers/delete/{id}")
    Call<ResponseBody> deleteSupplierApi(@Path("id") int id);

    // =========================================================================
    // 6. NHÓM TỒN KHO, KIỂM KÊ, PHIẾU NHẬP & CÔNG NỢ NCC (INVENTORY)
    // =========================================================================

    /** Lấy danh sách tồn kho chi tiết theo từng kho, chi nhánh hoặc mã sản phẩm. */
    @GET("api/admin/inventory/items")
    Call<List<InventoryItem>> getInventoryItems(
            @Query("warehouseId") Integer warehouseId,
            @Query("branchId") Integer branchId,
            @Query("productId") Integer productId
    );

    /** Lấy lịch sử thẻ kho / biến động xuất-nhập-tồn (Stock Movements) có phân trang và lọc theo loại/ngày. */
    @GET("api/admin/inventory/stock-movements")
    Call<StockMovementPagedResponse> getStockMovementsPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("warehouseId") Integer warehouseId,
            @Query("branchId") Integer branchId,
            @Query("type") String type,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("keyword") String keyword
    );

    /** Gửi phiếu điều chỉnh/cân bằng số lượng tồn kho thực tế sau khi kiểm kê. */
    @POST("api/admin/inventory/stock/adjust")
    Call<ResponseBody> adjustStockApi(@Body StockAdjustmentRequest request);

    /** Lấy tổng số lượng tồn kho thực tế của một sản phẩm tại kho chỉ định. */
    @GET("api/admin/inventory/stock/total-actual")
    Call<Integer> getTotalStock(
            @Query("productId") int productId,
            @Query("warehouseId") int warehouseId
    );

    /** Lấy toàn bộ danh sách phiếu nhập hàng (Purchase Orders) từ nhà cung cấp. */
    @GET("api/admin/inventory/purchase/all")
    Call<List<PurchaseOrder>> getAllPurchases();

    /** Lấy danh sách phiếu nhập hàng có phân trang theo từ khóa và chi nhánh. */
    @GET("api/admin/inventory/purchase/paged")
    Call<PurchasePagedResponse> getPurchasesPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("branchId") Integer branchId
    );

    /** Tìm kiếm nâng cao danh sách phiếu nhập hàng có phân trang (theo NCC, kho, trạng thái duyệt, thanh toán, ngày). */
    @GET("api/admin/inventory/purchase/search")
    Call<PurchasePagedResponse> searchPurchasesPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("supplierId") Integer supplierId,
            @Query("branchId") Integer branchId,
            @Query("warehouseId") Integer warehouseId,
            @Query("status") String status,
            @Query("paymentStatus") String paymentStatus,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Tìm kiếm danh sách phiếu nhập hàng không phân trang theo bộ lọc chi tiết. */
    @GET("api/admin/inventory/purchase/search")
    Call<List<PurchaseOrder>> searchPurchases(
            @Query("keyword") String keyword,
            @Query("supplierId") Integer supplierId,
            @Query("branchId") Integer branchId,
            @Query("warehouseId") Integer warehouseId,
            @Query("status") String status,
            @Query("paymentStatus") String paymentStatus,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy chi tiết một phiếu nhập hàng và danh sách mặt hàng nhập theo ID phiếu. */
    @GET("api/admin/inventory/purchase/{id}")
    Call<PurchaseOrder> getPurchaseById(@Path("id") int id);

    /** Tạo phiếu nhập kho mới (PurchaseOrder). */
    @POST("api/admin/inventory/purchase")
    Call<ResponseBody> createPurchaseRest(@Body PurchaseOrder dto);

    /** Cập nhật phiếu nhập kho qua phương thức PUT. */
    @PUT("api/admin/inventory/purchase")
    Call<ResponseBody> updatePurchasePut(@Body PurchaseOrder dto);

    /** Cập nhật thông tin phiếu nhập kho qua phương thức POST. */
    @POST("api/admin/inventory/purchase/update")
    Call<ResponseBody> updatePurchaseRest(@Body PurchaseOrder dto);

    /** Duyệt hoặc chuyển trạng thái phiếu nhập hàng (VD: hoàn thành nhập kho để cộng tồn kho và ghi nhận công nợ). */
    @POST("api/admin/inventory/purchase/{id}/status")
    Call<ResponseBody> updatePurchaseStatus(
            @Path("id") int id,
            @Body Map<String, Object> body
    );

    /** Cập nhật trạng thái phiếu nhập thông qua payload Map. */
    @POST("api/admin/inventory/purchase/status")
    Call<ResponseBody> updatePurchaseStatusBody(@Body Map<String, Object> body);

    /** Xóa phiếu nhập hàng theo ID. */
    @POST("api/admin/inventory/purchase/delete/{id}")
    Call<ResponseBody> deletePurchaseRest(@Path("id") int id);

    /** Lấy toàn bộ danh sách khoản công nợ nhà cung cấp (Supplier Debts). */
    @GET("api/admin/inventory/debt/all")
    Call<List<SupplierDebt>> getAllDebts();

    /** Lấy tổng hợp số liệu công nợ (tổng nợ, đã trả, còn lại) theo nhà cung cấp và kỳ hạn. */
    @GET("api/admin/inventory/debt/summary")
    Call<DebtSummary> getDebtSummary(
            @Query("supplierId") Integer supplierId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy danh sách công nợ nhà cung cấp có phân trang. */
    @GET("api/admin/inventory/debt/paged")
    Call<SupplierDebtPagedResponse> getDebtsPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("supplierId") Integer supplierId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy danh sách công nợ phân trang qua endpoint /debt/list. */
    @GET("api/admin/inventory/debt/list")
    Call<SupplierDebtPagedResponse> getDebtsListPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("supplierId") Integer supplierId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lọc danh sách công nợ nhà cung cấp theo từ khóa, NCC, trạng thái thanh toán và khoảng ngày. */
    @GET("api/admin/inventory/debt/list")
    Call<List<SupplierDebt>> getFilteredDebts(
            @Query("keyword") String keyword,
            @Query("supplierId") Integer supplierId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Tải ảnh chụp chứng từ/ủy nhiệm chi thanh toán công nợ (Multipart) lên máy chủ. */
    @Multipart
    @POST("api/admin/inventory/debt/upload-proof")
    Call<ResponseBody> uploadDebtProof(
            @Part MultipartBody.Part file,
            @Part("debtId") RequestBody debtId
    );

    /** Lấy thông tin chi tiết một khoản công nợ nhà cung cấp theo ID. */
    @GET("api/admin/inventory/debt/{id}")
    Call<SupplierDebt> getDebtById(@Path("id") int id);

    /** Tra cứu khoản công nợ phát sinh từ một phiếu nhập hàng cụ thể (purchaseOrderId). */
    @GET("api/admin/inventory/debt/by-purchase-order/{purchaseOrderId}")
    Call<SupplierDebt> getDebtByPurchaseOrder(@Path("purchaseOrderId") int purchaseOrderId);

    /** Tạo mới bản ghi công nợ nhà cung cấp. */
    @POST("api/admin/inventory/debt/create")
    Call<ResponseBody> createSupplierDebtApi(@Body SupplierDebt dto);

    /** Cập nhật thông tin khoản công nợ nhà cung cấp. */
    @POST("api/admin/inventory/debt/update")
    Call<ResponseBody> updateSupplierDebtApi(@Body SupplierDebt dto);

    /** Lấy lịch sử các lần thanh toán trả nợ của một khoản công nợ (debtId). */
    @GET("api/admin/inventory/debt/{debtId}/payments")
    Call<List<SupplierPayment>> getPaymentsByDebtId(@Path("debtId") int debtId);

    /** Tạo phiếu thanh toán trả nợ cho nhà cung cấp (khấu trừ vào số dư công nợ). */
    @POST("api/admin/inventory/debt/payment")
    Call<ResponseBody> createSupplierPaymentApi(@Body SupplierPayment paymentDto);

    // =========================================================================
    // 7. NHÓM ĐƠN HÀNG, POS & PAYOS VIETQR (SALES & ORDERS)
    // =========================================================================

    /** Lấy toàn bộ danh sách đơn bán hàng trong hệ thống. */
    @GET("api/admin/sales/orders/all")
    Call<List<Order>> getAllOrders();

    /** Lấy danh sách đơn hàng có phân trang và lọc theo nhân viên, chi nhánh, trạng thái đơn, khoảng ngày. */
    @GET("api/admin/sales/orders")
    Call<OrderPagedResponse> getOrdersPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("userId") Integer userId,
            @Query("branchId") Integer branchId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("keyword") String keyword
    );

    /** Thanh toán tạo đơn hàng mới từ màn hình bán hàng POS (trừ tồn kho, áp dụng khuyến mãi và tích điểm hội viên). */
    @POST("api/admin/sales/orders/checkout")
    Call<CheckoutResult> checkoutOrder(@Body CheckoutRequest request);

    /** Lấy thông tin chi tiết của một đơn hàng theo ID. */
    @GET("api/admin/sales/orders/{id}")
    Call<Order> getOrderById(@Path("id") int id);

    /** Lấy danh sách các dòng sản phẩm chi tiết (OrderDetail) trong một đơn hàng. */
    @GET("api/admin/sales/orders/{orderId}/details")
    Call<List<OrderDetail>> getOrderDetails(@Path("orderId") int orderId);

    /** Cập nhật trạng thái đơn hàng (VD: xác nhận thanh toán, hoàn thành hoặc hủy đơn). */
    @POST("api/admin/sales/orders/{orderId}/status")
    Call<ResponseBody> updateOrderStatusApi(
            @Path("orderId") int orderId,
            @Body Map<String, String> statusBody
    );

    /** Xóa đơn hàng theo orderId. */
    @POST("api/admin/sales/orders/delete/{orderId}")
    Call<ResponseBody> deleteOrderApi(@Path("orderId") int orderId);

    /** Lấy lịch sử giao dịch thanh toán của một đơn hàng. */
    @GET("api/admin/sales/orders/{orderId}/payments")
    Call<List<Payment>> getPaymentsByOrder(@Path("orderId") int orderId);

    /** Khởi tạo link thanh toán chuyển khoản nhanh VietQR qua cổng PayOS cho đơn hàng tại quầy POS. */
    @POST("api/admin/sales/orders/create-payos-link")
    Call<CreateQrResponse> createPayOSLink(@Body CreateQrRequest qrRequest);

    /** Kiểm tra trạng thái thanh toán VietQR của mã đơn PayOS (orderCode) để tự động hoàn tất đơn POS khi khách chuyển khoản xong. */
    @GET("api/admin/sales/orders/check-payos-status/{orderCode}")
    Call<PayOSStatusResponse> checkPayOSStatus(@Path("orderCode") long orderCode);

    /** Thêm một dòng sản phẩm chi tiết vào đơn hàng hiện có. */
    @POST("api/admin/sales/orders/details/create")
    Call<ResponseBody> createOrderDetailApi(@Body OrderDetail dto);

    /** Cập nhật số lượng/đơn giá của một dòng chi tiết đơn hàng. */
    @POST("api/admin/sales/orders/details/update")
    Call<ResponseBody> updateOrderDetailApi(@Body OrderDetail dto);

    /** Xóa một dòng sản phẩm khỏi chi tiết đơn hàng. */
    @POST("api/admin/sales/orders/details/delete/{id}")
    Call<ResponseBody> deleteOrderDetailApi(@Path("id") int id);

    /** Lấy toàn bộ danh sách chương trình khuyến mãi/mã giảm giá. */
    @GET("api/admin/sales/promotions")
    Call<List<Promotion>> getAllPromotions();

    /** Lấy danh sách các chương trình khuyến mãi đang còn hiệu lực để áp dụng tại màn hình POS. */
    @GET("api/admin/sales/promotions/active")
    Call<List<Promotion>> getActivePromotions();

    // =========================================================================
    // 8. NHÓM BÁO GIÁ (QUOTATIONS)
    // =========================================================================

    /** Lấy danh sách phiếu báo giá theo từ khóa, chi nhánh, trạng thái và khoảng thời gian. */
    @GET("api/admin/quotation/list")
    Call<List<Quotation>> getQuotations(
            @Query("keyword") String keyword,
            @Query("branchId") Integer branchId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy danh sách phiếu báo giá có phân trang. */
    @GET("api/admin/quotation/paged")
    Call<QuotationPagedResponse> getQuotationsPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword,
            @Query("branchId") Integer branchId,
            @Query("status") String status,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy thông tin chi tiết một phiếu báo giá kèm danh sách mặt hàng chào giá. */
    @GET("api/admin/quotation/{id}")
    Call<Quotation> getQuotationDetail(@Path("id") int id);

    /** Lưu mới hoặc cập nhật thông tin phiếu báo giá (QuotationSaveRequest). */
    @POST("api/admin/quotation/save")
    Call<ResponseBody> saveQuotationApi(@Body QuotationSaveRequest request);

    /** Thay đổi trạng thái phê duyệt của phiếu báo giá (VD: Đã gửi, Khách đồng ý, Từ chối). */
    @POST("api/admin/quotation/{id}/status")
    Call<ResponseBody> changeQuotationStatusApi(
            @Path("id") int id,
            @Body Map<String, String> body
    );

    /** Chuyển đổi nhanh một phiếu báo giá đã được khách chốt sang đơn bán hàng chính thức. */
    @POST("api/admin/quotation/{id}/convert-to-order")
    Call<ResponseBody> convertQuotationToOrderApi(
            @Path("id") int id,
            @Query("userId") int userId
    );

    /** Xóa phiếu báo giá theo ID. */
    @POST("api/admin/quotation/delete/{id}")
    Call<ResponseBody> deleteQuotationApi(@Path("id") int id);

    // =========================================================================
    // 9. NHÓM CHI PHÍ & SỔ QUỸ (EXPENSES)
    // =========================================================================

    /** Lấy danh sách phiếu chi phí vận hành theo chi nhánh, danh mục chi, trạng thái và ngày. */
    @GET("api/admin/expense/list")
    Call<List<Expense>> getExpenses(
            @Query("branchId") Integer branchId,
            @Query("categoryId") Integer categoryId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("status") String status
    );

    /** Lấy danh sách phiếu chi phí có phân trang. */
    @GET("api/admin/expense/paged")
    Call<ExpensePagedResponse> getExpensesPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("branchId") Integer branchId,
            @Query("categoryId") Integer categoryId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate,
            @Query("status") String status
    );

    /** Lấy tổng hợp thống kê chi phí theo chi nhánh và kỳ báo cáo. */
    @GET("api/admin/expense/summary")
    Call<ExpenseSummary> getExpenseSummary(
            @Query("branchId") Integer branchId,
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );

    /** Lấy thông tin chi tiết một phiếu chi theo ID. */
    @GET("api/admin/expense/list/{id}")
    Call<Expense> getExpenseDetail(@Path("id") int id);

    /** Lấy danh sách các hạng mục chi phí (tiền điện, mặt bằng, vận chuyển...). */
    @GET("api/admin/expense/categories")
    Call<List<ExpenseCategory>> getAllExpenseCategories();

    /** Tạo mới một hạng mục chi phí. */
    @POST("api/admin/expense/categories")
    Call<ResponseBody> createExpenseCategoryApi(@Body Map<String, Object> body);

    /** Xóa hạng mục chi phí theo ID. */
    @POST("api/admin/expense/categories/delete/{id}")
    Call<ResponseBody> deleteExpenseCategoryApi(@Path("id") int id);

    /** Tạo phiếu chi mới từ đối tượng Expense. */
    @POST("api/admin/expense/create")
    Call<ResponseBody> createExpenseApi(@Body Expense dto);

    /** Tạo phiếu chi mới từ dữ liệu Map động. */
    @POST("api/admin/expense/create")
    Call<ResponseBody> createExpenseMapApi(@Body Map<String, Object> body);

    /** Cập nhật nội dung phiếu chi hiện có. */
    @POST("api/admin/expense/update")
    Call<ResponseBody> updateExpenseApi(@Body Expense dto);

    /** Phê duyệt hoặc cập nhật trạng thái phiếu chi. */
    @POST("api/admin/expense/update-status")
    Call<ResponseBody> updateExpenseStatusApi(@Body Map<String, Object> payload);

    /** Xóa phiếu chi theo ID. */
    @POST("api/admin/expense/delete/{id}")
    Call<ResponseBody> deleteExpenseApi(@Path("id") int id);

    // =========================================================================
    // 10. NHÓM KHÁCH HÀNG & HỘI VIÊN CRM (CUSTOMERS & LOYALTY)
    // =========================================================================

    /** Lấy toàn bộ danh sách khách hàng để tra cứu khi bán hàng POS hoặc quản lý CRM. */
    @GET("api/admin/customer")
    Call<List<Customer>> getAllCustomers();

    /** Tìm kiếm danh sách khách hàng có phân trang theo tên, số điện thoại hoặc mã khách hàng. */
    @GET("api/admin/customer/search")
    Call<CustomerPagedResponse> getCustomersPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword
    );

    /** Lấy hồ sơ chi tiết và điểm tích lũy của một khách hàng theo ID. */
    @GET("api/admin/customer/{id}")
    Call<Customer> getCustomerById(@Path("id") int id);

    /** Thêm mới hồ sơ khách hàng. */
    @POST("api/admin/customer/create")
    Call<ResponseBody> createCustomerApi(@Body Customer dto);

    /** Cập nhật thông tin hồ sơ khách hàng. */
    @POST("api/admin/customer/update")
    Call<ResponseBody> updateCustomerApi(@Body Customer dto);

    /** Xóa khách hàng theo ID. */
    @POST("api/admin/customer/delete/{id}")
    Call<ResponseBody> deleteCustomerApi(@Path("id") int id);

    /** Lấy danh sách hạng thẻ hội viên và điểm thưởng (Loyalty Program). */
    @GET("api/admin/customer/loyalty")
    Call<ResponseBody> getAllLoyaltyApi();

    /** Lấy thống kê tăng trưởng khách hàng mới trong số ngày gần nhất. */
    @GET("api/admin/customer/stats")
    Call<ResponseBody> getCustomerStats(@Query("days") int days);

    // =========================================================================
    // 11. NHÓM NHÂN SỰ & PHÂN QUYỀN (USERS & ROLES)
    // =========================================================================

    /** Lấy danh sách toàn bộ tài khoản nhân viên/quản trị viên trong hệ thống. */
    @GET("api/admin/user-management/users")
    Call<List<User>> getAllUsers();

    /** Lấy danh sách tài khoản nhân sự có phân trang. */
    @GET("api/admin/user-management/users/paged")
    Call<UserPagedResponse> getUsersPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword
    );

    /** Tìm kiếm tài khoản nhân sự có phân trang theo từ khóa. */
    @GET("api/admin/user-management/users/search")
    Call<UserPagedResponse> getUsersSearchPaged(
            @Query("start") int start,
            @Query("length") int length,
            @Query("keyword") String keyword
    );

    /** Lấy thông tin hồ sơ và quyền hạn (Role/Branch) của một nhân viên theo ID. */
    @GET("api/admin/user-management/users/{id}")
    Call<User> getUserById(@Path("id") int id);

    /** Tạo mới tài khoản nhân viên từ đối tượng User. */
    @POST("api/admin/user-management/users/create")
    Call<ResponseBody> createUserApi(@Body User dto);

    /** Tạo mới tài khoản nhân viên kèm mật khẩu và phân quyền qua payload Map. */
    @POST("api/admin/user-management/users/create")
    Call<ResponseBody> createUserMapApi(@Body Map<String, Object> body);

    /** Cập nhật thông tin tài khoản nhân viên từ đối tượng User. */
    @POST("api/admin/user-management/users/update")
    Call<ResponseBody> updateUserApi(@Body User dto);

    /** Cập nhật thông tin, trạng thái khóa/mở khóa hoặc đổi vai trò RBAC của nhân viên qua payload Map. */
    @POST("api/admin/user-management/users/update")
    Call<ResponseBody> updateUserMapApi(@Body Map<String, Object> body);

    /** Xóa tài khoản nhân viên qua phương thức POST. */
    @POST("api/admin/user-management/users/delete/{id}")
    Call<ResponseBody> deleteUserApi(@Path("id") int id);

    /** Xóa tài khoản nhân viên qua phương thức chuẩn HTTP DELETE. */
    @DELETE("api/admin/user-management/users/{id}")
    Call<ResponseBody> deleteUserRestApi(@Path("id") int id);

    /** Tải ảnh đại diện (Avatar) mới cho tài khoản nhân viên lên máy chủ. */
    @Multipart
    @POST("api/admin/user-management/users/upload-avatar")
    Call<ResponseBody> uploadUserAvatar(
            @Part MultipartBody.Part file,
            @Part("userId") RequestBody userId
    );

    /** Lấy danh sách các vai trò phân quyền (Roles: Admin, Manager, Staff, Warehouse...) trong hệ thống RBAC. */
    @GET("api/admin/user-management/roles")
    Call<List<Role>> getAllRoles();

    /** Lấy cấu hình tham số hệ thống chung từ Backend. */
    @GET("api/admin/system-config")
    Call<SystemConfig> getSystemConfig();
}
