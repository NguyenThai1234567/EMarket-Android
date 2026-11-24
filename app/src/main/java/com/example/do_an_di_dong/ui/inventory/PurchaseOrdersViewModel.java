package com.example.do_an_di_dong.ui.inventory;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.PurchaseOrder;
import com.example.do_an_di_dong.model.PurchasePagedResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel quản lý danh sách Đơn nhập hàng (Purchase Orders - PO), tìm kiếm và phân trang.
 * Kết nối trực tiếp với {@link PurchaseOrdersActivity} và lấy dữ liệu qua {@link DataRepository}.
 */
public class PurchaseOrdersViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_AUTO_SCAN_PAGES = 5;

    private final DataRepository repository;
    private final MutableLiveData<List<PurchaseOrder>> purchaseOrdersLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emptyLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> lastPageLiveData = new MutableLiveData<>(false);

    private final List<PurchaseOrder> accumulatedPurchases = new ArrayList<>();
    private int currentStart = 0;
    private String currentKeyword = "";
    private Integer currentSupplierId = null;
    private String currentStatus = null;
    private int requestSeq = 0;
    private int autoScanPages = 0;

    /**
     * Khởi tạo ViewModel và instance DataRepository phục vụ truy vấn API đơn nhập hàng.
     */
    public PurchaseOrdersViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<PurchaseOrder>> getPurchaseOrders() {
        return purchaseOrdersLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public LiveData<Boolean> getEmpty() {
        return emptyLiveData;
    }

    public LiveData<Boolean> getLastPage() {
        return lastPageLiveData;
    }

    /**
     * Kích hoạt tải danh sách đơn nhập hàng từ trang đầu tiên (gọi khi khởi tạo màn hình hoặc sau khi tạo/cập nhật PO).
     */
    public void fetchPurchaseOrders() {
        refreshPurchaseOrders();
    }

    /**
     * Làm mới toàn bộ danh sách PO (reset vị trí phân trang về 0, xóa cache tích lũy) khi kéo SwipeRefresh hoặc đổi bộ lọc.
     */
    public void refreshPurchaseOrders() {
        currentStart = 0;
        autoScanPages = 0;
        accumulatedPurchases.clear();
        loadingLiveData.setValue(false);
        lastPageLiveData.setValue(false);
        loadNextPage();
    }

    /**
     * Tải trang dữ liệu PO tiếp theo từ API và kết hợp lọc client-side.
     * Kích hoạt khi làm mới danh sách hoặc khi người dùng cuộn RecyclerView gần tới cuối danh sách.
     */
    public void loadNextPage() {
        if (Boolean.TRUE.equals(loadingLiveData.getValue()) || Boolean.TRUE.equals(lastPageLiveData.getValue())) {
            return;
        }

        // Tăng sequence ID để bỏ qua kết quả của các request cũ nếu người dùng gõ tìm kiếm/đổi filter liên tục
        final int seq = ++requestSeq;
        loadingLiveData.setValue(true);

        String trimmedKeyword = currentKeyword != null ? currentKeyword.trim() : "";
        // Backend GetPurchaseOrdersDataTableAsync only searches non-integer keyword against notes;
        // send null for text queries and filter client-side across code, supplier, warehouse, branch, notes.
        String keywordParam = (!trimmedKeyword.isEmpty() && trimmedKeyword.matches("\\d+")) ? trimmedKeyword : null;

        // Backend filters exact status strings, missing DB 'Received'/'Approved' POs when filtering 'Completed'.
        String statusParam = ("Pending".equalsIgnoreCase(currentStatus) || "Cancelled".equalsIgnoreCase(currentStatus))
                ? currentStatus
                : null;

        // Gọi API phân trang tìm kiếm đơn nhập hàng
        repository.searchPurchasesPaged(
                currentStart,
                PAGE_SIZE,
                keywordParam,
                currentSupplierId,
                null,
                null,
                statusParam,
                null,
                null,
                null,
                new DataRepository.ApiCallback<PurchasePagedResponse>() {
                    @Override
                    public void onSuccess(PurchasePagedResponse result) {
                        if (seq != requestSeq) return;
                        List<PurchaseOrder> newItems = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();
                        String lowerKeyword = trimmedKeyword.toLowerCase(java.util.Locale.ROOT);

                        // Lọc bổ sung phía client để đồng bộ các biến thể trạng thái (Completed/Received/Approved) và từ khóa tiếng Việt
                        for (PurchaseOrder po : newItems) {
                            if (po == null) continue;
                            if (currentSupplierId != null && currentSupplierId > 0 && po.getSupplierId() > 0 && po.getSupplierId() != currentSupplierId) {
                                continue;
                            }
                            if (currentStatus != null && !currentStatus.isEmpty()) {
                                String st = po.getStatus() != null ? po.getStatus().trim() : "";
                                if ("Completed".equalsIgnoreCase(currentStatus)) {
                                    if (!"Completed".equalsIgnoreCase(st)
                                            && !"Received".equalsIgnoreCase(st)
                                            && !"Approved".equalsIgnoreCase(st)) {
                                        continue;
                                    }
                                } else if ("Cancelled".equalsIgnoreCase(currentStatus)) {
                                    if (!"Cancelled".equalsIgnoreCase(st)
                                            && !"Canceled".equalsIgnoreCase(st)
                                            && !"Rejected".equalsIgnoreCase(st)) {
                                        continue;
                                    }
                                } else if (!currentStatus.equalsIgnoreCase(st)) {
                                    continue;
                                }
                            }
                            if (!lowerKeyword.isEmpty() && !matchesKeyword(po, lowerKeyword)) {
                                continue;
                            }
                            accumulatedPurchases.add(po);
                        }

                        // Cập nhật LiveData để UI render lại danh sách và thống kê KPI
                        purchaseOrdersLiveData.postValue(new ArrayList<>(accumulatedPurchases));

                        currentStart += newItems.size();
                        int total = (result != null) ? result.getTotal() : 0;

                        boolean isLast = newItems.isEmpty() || currentStart >= total || newItems.size() < PAGE_SIZE;
                        lastPageLiveData.postValue(isLast);

                        // Tự động quét tiếp tối đa MAX_AUTO_SCAN_PAGES trang nếu lọc client-side làm trang hiện tại bị rỗng
                        if (accumulatedPurchases.isEmpty() && !isLast && autoScanPages < MAX_AUTO_SCAN_PAGES) {
                            autoScanPages++;
                            loadingLiveData.setValue(false);
                            loadNextPage();
                            return;
                        }

                        loadingLiveData.postValue(false);
                        emptyLiveData.postValue(accumulatedPurchases.isEmpty());
                    }

                    @Override
                    public void onError(String message) {
                        if (seq != requestSeq) return;
                        loadingLiveData.postValue(false);
                        errorLiveData.postValue(message);
                        emptyLiveData.postValue(accumulatedPurchases.isEmpty());
                    }
                }
        );
    }

    /**
     * Cập nhật từ khóa tìm kiếm (khi người dùng nhập vào ô tìm kiếm có debounce) và tải lại danh sách.
     */
    public void setSearchKeyword(String keyword) {
        String cleanKey = (keyword != null) ? keyword.trim() : "";
        if (!cleanKey.equalsIgnoreCase(currentKeyword)) {
            this.currentKeyword = cleanKey;
            refreshPurchaseOrders();
        }
    }

    /**
     * Áp dụng bộ lọc theo ID Nhà cung cấp và Trạng thái đơn nhập, sau đó làm mới danh sách.
     */
    public void setFilters(Integer supplierId, String status) {
        this.currentSupplierId = supplierId;
        this.currentStatus = status;
        refreshPurchaseOrders();
    }

    /**
     * Kiểm tra một đơn nhập hàng có khớp với từ khóa tìm kiếm trên các trường mã PO, tên NCC, kho, chi nhánh, người tạo hoặc ghi chú.
     */
    private boolean matchesKeyword(PurchaseOrder po, String lowerKw) {
        String code = "#po-" + po.getPurchaseOrderId();
        if (code.contains(lowerKw) || String.valueOf(po.getPurchaseOrderId()).contains(lowerKw)) {
            return true;
        }
        return containsIgnoreCase(po.getSupplierName(), lowerKw)
                || containsIgnoreCase(po.getWarehouseName(), lowerKw)
                || containsIgnoreCase(po.getBranchName(), lowerKw)
                || containsIgnoreCase(po.getUserName(), lowerKw)
                || containsIgnoreCase(po.getNotes(), lowerKw);
    }

    /**
     * Hỗ trợ kiểm tra chuỗi con không phân biệt hoa thường (null-safe).
     */
    private boolean containsIgnoreCase(String value, String lowerKw) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(lowerKw);
    }
}
