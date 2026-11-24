package com.example.do_an_di_dong.ui.pos;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.CartItem;
import com.example.do_an_di_dong.model.CheckoutRequest;
import com.example.do_an_di_dong.model.CheckoutResult;
import com.example.do_an_di_dong.model.CreateQrRequest;
import com.example.do_an_di_dong.model.CreateQrResponse;
import com.example.do_an_di_dong.model.Customer;
import com.example.do_an_di_dong.model.PayOSStatusResponse;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.Promotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel quản lý toàn bộ trạng thái và nghiệp vụ bán hàng tại quầy (POS).
 * - Vai trò: Lưu trữ danh sách giỏ hàng, kiểm soát tồn kho khả dụng, tính toán tổng tiền/chiết khấu hạng thẻ/đổi điểm tích lũy,
 *   và giao tiếp với DataRepository để tìm kiếm sản phẩm, tạo mã QR PayOS và chốt đơn (Checkout).
 * - Gắn kết: Được sử dụng trực tiếp bởi PosActivity và gọi dữ liệu thông qua DataRepository.
 */
public class PosViewModel extends AndroidViewModel {

    private final DataRepository repository;
    private final MutableLiveData<List<CartItem>> cartLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Double> rawTotalAmountLiveData = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> finalPayableAmountLiveData = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalDiscountLiveData = new MutableLiveData<>(0.0);
    private final MutableLiveData<Integer> totalQuantityLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<List<Product>> suggestionsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<CheckoutResult> checkoutSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    private final MutableLiveData<Customer> selectedCustomerLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Promotion> selectedPromotionLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> usePointsLiveData = new MutableLiveData<>(false);

    private final List<CartItem> cartItems = new ArrayList<>();
    // Cache số lượng tồn kho khả dụng theo productId để kiểm tra nhanh khi tăng số lượng món trong giỏ
    private final Map<Integer, Integer> productStockMap = new HashMap<>();

    /**
     * Khởi tạo PosViewModel và cung cấp instance của DataRepository.
     * - Kích hoạt: Tự động gọi bởi ViewModelProvider trong PosActivity.onCreate().
     */
    public PosViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<CartItem>> getCart() { return cartLiveData; }
    public LiveData<Double> getRawTotalAmount() { return rawTotalAmountLiveData; }
    public LiveData<Double> getFinalPayableAmount() { return finalPayableAmountLiveData; }
    public LiveData<Double> getTotalDiscount() { return totalDiscountLiveData; }
    public LiveData<Integer> getTotalQuantity() { return totalQuantityLiveData; }
    public LiveData<List<Product>> getSuggestions() { return suggestionsLiveData; }
    public LiveData<CheckoutResult> getCheckoutSuccess() { return checkoutSuccessLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getLoading() { return loadingLiveData; }

    public LiveData<Customer> getSelectedCustomer() { return selectedCustomerLiveData; }
    public LiveData<Promotion> getSelectedPromotion() { return selectedPromotionLiveData; }
    public LiveData<Boolean> getUsePoints() { return usePointsLiveData; }

    /**
     * Gắn khách hàng thành viên vào đơn hàng hiện tại và tính lại chiết khấu theo hạng thẻ.
     * - Kích hoạt: Khi thu ngân chọn một khách hàng từ BottomSheet trong PosActivity.
     */
    public void selectCustomer(Customer customer) {
        selectedCustomerLiveData.setValue(customer);
        recalculate();
    }

    /**
     * Xóa khách hàng khỏi đơn hàng, tắt chế độ dùng điểm tích lũy và tính lại tổng tiền.
     * - Kích hoạt: Khi thu ngân bấm nút đóng (X) trên Chip khách hàng hoặc khi làm mới giỏ hàng.
     */
    public void clearCustomer() {
        selectedCustomerLiveData.setValue(null);
        usePointsLiveData.setValue(false);
        recalculate();
    }

    /**
     * Áp dụng chương trình khuyến mãi cho đơn hàng và tính lại tổng tiền.
     * - Kích hoạt: Khi chọn mã khuyến mãi (nếu có).
     */
    public void selectPromotion(Promotion promo) {
        selectedPromotionLiveData.setValue(promo);
        recalculate();
    }

    /**
     * Gỡ bỏ chương trình khuyến mãi hiện tại và tính lại tổng tiền.
     * - Kích hoạt: Khi bỏ chọn khuyến mãi hoặc khi dọn sạch giỏ hàng.
     */
    public void clearPromotion() {
        selectedPromotionLiveData.setValue(null);
        recalculate();
    }

    /**
     * Bật hoặc tắt chế độ sử dụng điểm tích lũy (Loyalty Points) để trừ tiền trực tiếp vào hóa đơn.
     * - Kích hoạt: Khi thu ngân gạt công tắc (switchPosUsePoints) trên màn hình POS.
     */
    public void setUsePoints(boolean use) {
        usePointsLiveData.setValue(use);
        recalculate();
    }

    /**
     * Tìm kiếm sản phẩm theo từ khóa (tên, mã SKU hoặc barcode) thông qua DataRepository.
     * - Kích hoạt: Khi thu ngân gõ vào ô tìm kiếm trên PosActivity (sau khi debounce 300ms).
     * - Kết quả: Đẩy danh sách Product tìm được vào suggestionsLiveData để hiển thị gợi ý.
     */
    public void searchProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            suggestionsLiveData.setValue(new ArrayList<>());
            return;
        }

        // Gọi DataRepository tìm sản phẩm (ưu tiên API hoặc fallback Room DB tùy chế độ mạng)
        repository.searchProductsSimple(keyword.trim(), new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                suggestionsLiveData.postValue(result != null ? result : new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                suggestionsLiveData.postValue(new ArrayList<>());
            }
        });
    }

    /**
     * Thêm một sản phẩm vào giỏ hàng hoặc cộng dồn số lượng (+1) nếu sản phẩm đã có trong giỏ, có kiểm tra tồn kho.
     * - Kích hoạt: Khi thu ngân chọn sản phẩm từ danh sách gợi ý hoặc sau khi quét mã vạch thành công.
     */
    public void addProductToCart(Product product) {
        if (product == null) return;

        // Kiểm tra tồn kho khả dụng: chặn thêm nếu sản phẩm đã hết hàng (stock <= 0)
        int availableStock = product.getStockQuantity();
        if (availableStock <= 0) {
            errorLiveData.setValue("Sản phẩm [" + product.getName() + "] hiện đã hết tồn kho!");
            return;
        }
        productStockMap.put(product.getProductId(), availableStock);

        // Nếu sản phẩm đã có trong giỏ: kiểm tra không vượt quá tồn kho trước khi cộng thêm 1
        for (CartItem item : cartItems) {
            if (item.getProductId() == product.getProductId()) {
                if (item.getQuantity() + 1 > availableStock) {
                    errorLiveData.setValue("Vượt quá tồn kho khả dụng (" + availableStock + ") của [" + product.getName() + "]!");
                    return;
                }
                item.setQuantity(item.getQuantity() + 1);
                recalculate();
                return;
            }
        }

        cartItems.add(new CartItem(product.getProductId(), product.getName(), product.getSku(), product.getPrice(), 1));
        recalculate();
    }

    /**
     * Tra cứu sản phẩm theo mã vạch vừa quét từ Camera và tự động thêm sản phẩm đầu tiên khớp mã vào giỏ hàng.
     * - Kích hoạt: Khi BarcodeScanActivity trả kết quả mã vạch về cho PosActivity.
     */
    public void addByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return;

        loadingLiveData.setValue(true);
        repository.searchProductsSimple(barcode.trim(), new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                loadingLiveData.postValue(false);
                if (result != null && !result.isEmpty()) {
                    addProductToCart(result.get(0));
                } else {
                    errorLiveData.postValue("Không tìm thấy sản phẩm với mã: " + barcode);
                }
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
            }
        });
    }

    /**
     * Cập nhật số lượng mới cho một món hàng trong giỏ, đảm bảo không vượt quá số lượng tồn kho đã cache.
     * - Kích hoạt: Khi thu ngân bấm nút +/- hoặc nhập số lượng trực tiếp trên PosCartAdapter.
     */
    public void updateItemQuantity(CartItem item, int newQty) {
        // Đối chiếu với tồn kho khả dụng trong productStockMap trước khi cho phép cập nhật số lượng
        Integer maxStock = productStockMap.get(item.getProductId());
        if (maxStock != null && maxStock > 0 && newQty > maxStock) {
            errorLiveData.setValue("Số lượng vượt quá tồn kho khả dụng (" + maxStock + ")!");
            recalculate();
            return;
        }
        for (CartItem ci : cartItems) {
            if (ci.getProductId() == item.getProductId()) {
                ci.setQuantity(newQty);
                break;
            }
        }
        recalculate();
    }

    /**
     * Xóa hoàn toàn một món hàng khỏi giỏ và tính lại tổng tiền.
     * - Kích hoạt: Khi thu ngân bấm nút thùng rác hoặc giảm số lượng về 0 trên PosCartAdapter.
     */
    public void removeItem(CartItem item) {
        cartItems.removeIf(ci -> ci.getProductId() == item.getProductId());
        recalculate();
    }

    /**
     * Làm trống toàn bộ giỏ hàng, reset khách hàng và khuyến mãi về mặc định.
     * - Kích hoạt: Khi thu ngân xác nhận xóa giỏ hàng hoặc sau khi thanh toán đơn hàng thành công.
     */
    public void clearCart() {
        cartItems.clear();
        clearCustomer();
        clearPromotion();
        recalculate();
    }

    /**
     * Tính toán lại toàn bộ số liệu tài chính của giỏ hàng: Tổng tiền gốc, Tổng giảm giá (Khuyến mãi + Hạng thẻ + Điểm thưởng) và Số tiền khách phải trả.
     * - Kích hoạt: Tự động gọi sau mọi thao tác thay đổi giỏ hàng, đổi khách hàng, khuyến mãi hoặc bật/tắt dùng điểm.
     * - Cập nhật: Phát dữ liệu mới lên các LiveData để UI PosActivity tự động render lại.
     */
    public void recalculate() {
        double rawTotal = 0;
        int count = 0;
        for (CartItem ci : cartItems) {
            rawTotal += ci.getSubtotal();
            count += ci.getQuantity();
        }

        double discount = 0;

        // 1. Giảm giá từ chương trình khuyến mãi (nếu tổng tiền gốc đạt giá trị đơn tối thiểu)
        Promotion promo = selectedPromotionLiveData.getValue();
        if (promo != null && rawTotal >= promo.getMinOrderValue()) {
            discount += promo.getDiscountAmount();
        }

        // 2. Chiết khấu theo hạng thẻ khách hàng (Tier discount) & Quy đổi điểm tích lũy (1 điểm = 100 VNĐ, tối đa bằng số tiền còn lại)
        Customer customer = selectedCustomerLiveData.getValue();
        if (customer != null) {
            double tierDiscount = Math.round(rawTotal * customer.getDiscountRate());
            discount += tierDiscount;

            if (Boolean.TRUE.equals(usePointsLiveData.getValue()) && customer.getLoyaltyPoints() > 0) {
                // 1 point = 100 VND (or up to remaining bill)
                double pointDiscount = customer.getLoyaltyPoints() * 100.0;
                discount += Math.min(pointDiscount, Math.max(0, rawTotal - discount));
            }
        }

        // Tổng tiền thực thu không bao giờ âm
        double payable = Math.max(0, rawTotal - discount);

        cartLiveData.setValue(new ArrayList<>(cartItems));
        rawTotalAmountLiveData.setValue(rawTotal);
        totalDiscountLiveData.setValue(discount);
        finalPayableAmountLiveData.setValue(payable);
        totalQuantityLiveData.setValue(count);
    }

    /**
     * Yêu cầu DataRepository gọi API tạo link và mã VietQR trên cổng thanh toán PayOS.
     * - Kích hoạt: Khi thu ngân mở tab Chuyển khoản trong dialog thanh toán của PosActivity.
     */
    public void createPayOSLink(CreateQrRequest request, DataRepository.ApiCallback<CreateQrResponse> callback) {
        repository.createPayOSLink(request, callback);
    }

    /**
     * Yêu cầu DataRepository kiểm tra trạng thái thanh toán của mã đơn hàng PayOS.
     * - Kích hoạt: Được gọi định kỳ bởi cơ chế Polling 2.5s hoặc khi bấm nút kiểm tra thủ công trong PosActivity.
     */
    public void checkPayOSStatus(long orderCode, DataRepository.ApiCallback<PayOSStatusResponse> callback) {
        repository.checkPayOSStatus(orderCode, callback);
    }

    /**
     * Đóng gói dữ liệu đơn hàng (CheckoutRequest) và gửi xuống DataRepository để lưu hóa đơn, trừ tồn kho và cập nhật điểm thưởng.
     * - Kích hoạt: Khi thu ngân xác nhận thanh toán tiền mặt hoặc khi PayOS xác nhận đã nhận tiền chuyển khoản.
     * - Tham số: paymentMethod ("Cash" hoặc "BankTransfer"), callback trả về CheckoutResult (chứa orderId vừa tạo).
     */
    public void checkout(String paymentMethod, DataRepository.ApiCallback<CheckoutResult> callback) {
        if (cartItems.isEmpty()) {
            if (callback != null) callback.onError("Giỏ hàng đang trống!");
            return;
        }

        double total = finalPayableAmountLiveData.getValue() != null ? finalPayableAmountLiveData.getValue() : 0.0;
        double discount = totalDiscountLiveData.getValue() != null ? totalDiscountLiveData.getValue() : 0.0;
        Customer customer = selectedCustomerLiveData.getValue();
        // Nếu không chọn khách hàng thành viên, mặc định gán customerId = 1 (Khách lẻ/vãng lai)
        Integer customerId = (customer != null && customer.getCustomerId() > 0) ? customer.getCustomerId() : 1;

        // Công thức tích điểm thưởng cho đơn hàng mới: mỗi 1.000 VNĐ thanh toán thực tế được cộng 1 điểm
        int pointsEarned = (int) Math.floor(total / 1000.0);

        // Lấy thông tin định danh Thu ngân (userId) và Chi nhánh hiện tại (branchId) từ phiên đăng nhập UserSessionManager
        int currentUserId = com.example.do_an_di_dong.util.UserSessionManager.getCurrentUserId(getApplication());
        int currentBranchId = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(getApplication());

        CheckoutRequest request = new CheckoutRequest(customerId, total, paymentMethod, new ArrayList<>(cartItems));
        request.setUserId(currentUserId > 0 ? currentUserId : 1);
        request.setBranchId(currentBranchId > 0 ? currentBranchId : 1);
        request.setPointsEarned(pointsEarned);
        if (discount > 0) {
            request.setManualDiscount(discount);
            request.setManualDiscountReason(customer != null ? ("Ưu đãi hạng " + customer.getTierName()) : "Khuyến mãi POS");
        }

        if (Boolean.TRUE.equals(usePointsLiveData.getValue()) && customer != null) {
            request.setPointsUsed(customer.getLoyaltyPoints());
        }

        // Gọi DataRepository thực thi giao dịch tạo đơn hàng (API Backend / Offline Room fallback)
        loadingLiveData.setValue(true);
        repository.checkoutOrder(request, new DataRepository.ApiCallback<CheckoutResult>() {
            @Override
            public void onSuccess(CheckoutResult result) {
                loadingLiveData.postValue(false);
                if (result != null && result.isSuccess()) {
                    checkoutSuccessLiveData.postValue(result);
                    if (callback != null) callback.onSuccess(result);
                } else {
                    String msg = result != null && result.getMessage() != null ? result.getMessage() : "Thanh toán thất bại từ hệ thống";
                    errorLiveData.postValue(msg);
                    if (callback != null) callback.onError(msg);
                }
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
                if (callback != null) callback.onError(message);
            }
        });
    }
}
