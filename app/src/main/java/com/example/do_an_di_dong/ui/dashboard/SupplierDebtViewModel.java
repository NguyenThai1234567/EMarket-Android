package com.example.do_an_di_dong.ui.dashboard;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.DebtSummary;
import com.example.do_an_di_dong.model.SupplierDebt;
import com.example.do_an_di_dong.model.SupplierDebtPagedResponse;
import com.example.do_an_di_dong.model.SupplierPayment;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel quản lý dữ liệu Công nợ Nhà cung cấp (phân trang, tìm kiếm, lọc trạng thái, thống kê tổng quan)
 * và thực hiện các thao tác thanh toán công nợ / cập nhật hạn nợ.
 * Phục vụ cho {@link SupplierDebtActivity} và giao tiếp với {@link DataRepository}.
 */
public class SupplierDebtViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 20;

    private final DataRepository repository;
    private final MutableLiveData<List<SupplierDebt>> debtsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<DebtSummary> summaryLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emptyLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> lastPageLiveData = new MutableLiveData<>(false);

    private final List<SupplierDebt> accumulatedDebts = new ArrayList<>();
    private int currentStart = 0;
    private String currentKeyword = "";
    private Integer currentSupplierId = null;
    private String currentStatus = null;

    /**
     * Khởi tạo ViewModel và DataRepository để gọi các API công nợ nhà cung cấp.
     */
    public SupplierDebtViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<SupplierDebt>> getDebts() {
        return debtsLiveData;
    }

    public LiveData<DebtSummary> getSummary() {
        return summaryLiveData;
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
     * Tải mới toàn bộ danh sách công nợ từ trang đầu và đồng thời cập nhật thống kê tổng quan (KPI Summary).
     */
    public void fetchDebts() {
        refreshDebts();
        fetchSummary();
    }

    /**
     * Gọi API lấy số liệu tổng hợp công nợ (Tổng nợ, Đã trả, Còn lại, Tỷ lệ thanh toán) theo NCC đang lọc.
     */
    public void fetchSummary() {
        repository.getDebtSummary(currentSupplierId, null, null, new DataRepository.ApiCallback<DebtSummary>() {
            @Override
            public void onSuccess(DebtSummary result) {
                summaryLiveData.postValue(result);
            }

            @Override
            public void onError(String message) {
                // Non-critical error
            }
        });
    }

    /**
     * Reset vị trí phân trang về 0 và xóa danh sách tích lũy để tải lại dữ liệu từ trang đầu tiên.
     */
    public void refreshDebts() {
        currentStart = 0;
        accumulatedDebts.clear();
        lastPageLiveData.setValue(false);
        loadNextPage();
    }

    /**
     * Tải trang danh sách công nợ tiếp theo từ API theo từ khóa, ID nhà cung cấp và trạng thái thanh toán hiện tại.
     */
    public void loadNextPage() {
        if (Boolean.TRUE.equals(loadingLiveData.getValue()) || Boolean.TRUE.equals(lastPageLiveData.getValue())) {
            return;
        }

        loadingLiveData.setValue(true);
        String keywordParam = currentKeyword.isEmpty() ? null : currentKeyword;

        // Gọi API lấy danh sách công nợ có phân trang
        repository.getDebtsPaged(currentStart, PAGE_SIZE, keywordParam, currentSupplierId, currentStatus, null, null, new DataRepository.ApiCallback<SupplierDebtPagedResponse>() {
            @Override
            public void onSuccess(SupplierDebtPagedResponse result) {
                loadingLiveData.postValue(false);
                List<SupplierDebt> newItems = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();

                accumulatedDebts.addAll(newItems);
                debtsLiveData.postValue(new ArrayList<>(accumulatedDebts));

                currentStart += newItems.size();
                int total = (result != null) ? result.getTotal() : 0;

                // Kiểm tra đã chạm trang cuối cùng hay chưa để dừng infinite scroll
                boolean isLast = newItems.isEmpty() || accumulatedDebts.size() >= total || newItems.size() < PAGE_SIZE;
                lastPageLiveData.postValue(isLast);

                emptyLiveData.postValue(accumulatedDebts.isEmpty());
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
                emptyLiveData.postValue(accumulatedDebts.isEmpty());
            }
        });
    }

    /**
     * Cập nhật từ khóa tìm kiếm công nợ và tải lại danh sách nếu từ khóa thay đổi.
     */
    public void setSearchKeyword(String keyword) {
        String cleanKey = (keyword != null) ? keyword.trim() : "";
        if (!cleanKey.equalsIgnoreCase(currentKeyword)) {
            this.currentKeyword = cleanKey;
            refreshDebts();
        }
    }

    /**
     * Áp dụng bộ lọc theo Nhà cung cấp và Trạng thái nợ (Unpaid/Partial/Paid), sau đó tải lại danh sách và KPI.
     */
    public void setFilters(Integer supplierId, String status) {
        this.currentSupplierId = supplierId;
        this.currentStatus = status;
        refreshDebts();
        fetchSummary();
    }

    /**
     * Lấy danh sách lịch sử các lần thanh toán của một khoản công nợ theo {@code debtId}.
     */
    public void getPaymentsByDebtId(int debtId, DataRepository.ApiCallback<List<SupplierPayment>> callback) {
        repository.getPaymentsByDebtId(debtId, callback);
    }

    /**
     * Gửi yêu cầu tạo phiếu thanh toán công nợ mới lên API; khi thành công tự động làm mới danh sách nợ và KPI.
     */
    public void createSupplierPayment(SupplierPayment dto, DataRepository.ApiCallback<Boolean> callback) {
        repository.createSupplierPaymentApi(dto, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                callback.onSuccess(result);
                refreshDebts();
                fetchSummary();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Gửi yêu cầu cập nhật thông tin khoản nợ (VD: ngày đáo hạn DueDate) lên API và làm mới lại dữ liệu hiển thị.
     */
    public void updateSupplierDebt(SupplierDebt dto, DataRepository.ApiCallback<Boolean> callback) {
        repository.updateSupplierDebtApi(dto, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                callback.onSuccess(result);
                refreshDebts();
                fetchSummary();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
