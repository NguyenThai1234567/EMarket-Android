package com.example.do_an_di_dong.ui.admin;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Expense;
import com.example.do_an_di_dong.model.ExpensePagedResponse;
import com.example.do_an_di_dong.model.ExpenseSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel quản lý trạng thái dữ liệu và nghiệp vụ tải danh sách phiếu chi, thống kê tổng tiền (ExpenseSummary)
 * cho màn hình ExpensesActivity. Kết nối trực tiếp với DataRepository để gọi REST API và giữ bộ nhớ đệm (allFetchedExpenses)
 * phục vụ lọc nhanh theo trạng thái (All/Approved/Pending/Rejected) ngay trên client.
 */
public class ExpensesViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 100;

    private final DataRepository repository;
    private final MutableLiveData<List<Expense>> expensesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ExpenseSummary> summaryLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emptyLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> lastPageLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

    private final List<Expense> allFetchedExpenses = new ArrayList<>();
    private Integer currentBranchId = null;
    private Integer currentCategoryId = null;
    private String currentStatus = null; // null = ALL, "Approved", "Pending", "Rejected"
    private int requestSeq = 0;

    /**
     * Khởi tạo ViewModel và tiêm DataRepository để giao tiếp với tầng dữ liệu (API/Local).
     */
    public ExpensesViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<Expense>> getExpenses() { return expensesLiveData; }
    public LiveData<ExpenseSummary> getSummary() { return summaryLiveData; }
    public LiveData<Boolean> getLoading() { return loadingLiveData; }
    public LiveData<Boolean> getEmpty() { return emptyLiveData; }
    public LiveData<Boolean> getLastPage() { return lastPageLiveData; }
    public LiveData<String> getError() { return errorLiveData; }

    /**
     * Trả về bản sao danh sách toàn bộ phiếu chi đã tải từ server (chưa bị cắt bởi bộ lọc trạng thái UI).
     * Dùng cho ExpensesActivity tính tổng tiền mặt đã chi trong ngày và tính KPI tổng phòng khi API summary lỗi.
     */
    public List<Expense> getAllFetchedExpenses() {
        return new ArrayList<>(allFetchedExpenses);
    }

    /**
     * Kích hoạt tải đồng thời danh sách phiếu chi và thống kê tổng tiền từ server.
     * Được gọi khi mở màn hình, kéo swipe-to-refresh, hoặc sau khi tạo/duyệt/từ chối/xóa phiếu chi.
     */
    public void fetchExpenses() {
        refreshExpenses();
        fetchSummary();
    }

    /**
     * Gọi API lấy số liệu tổng hợp chi phí (tổng Đã duyệt, Chờ duyệt, Từ chối) theo chi nhánh hiện tại.
     * Kết quả trả về được đẩy vào summaryLiveData để UI cập nhật 3 thẻ KPI trên đầu màn hình.
     */
    public void fetchSummary() {
        repository.getExpenseSummary(currentBranchId, null, null, new DataRepository.ApiCallback<ExpenseSummary>() {
            @Override
            public void onSuccess(ExpenseSummary result) {
                if (result != null) {
                    summaryLiveData.postValue(result);
                }
            }

            @Override
            public void onError(String message) {
                // Non-fatal
            }
        });
    }

    /**
     * Gọi API lấy danh sách phiếu chi phân trang (tối đa PAGE_SIZE = 100 bản ghi) từ DataRepository.
     * Không truyền filter status lên backend để tải đủ dữ liệu gốc về allFetchedExpenses, giúp tính đúng tổng tiền
     * và lọc trạng thái không phân biệt hoa thường ở phía client.
     */
    public void refreshExpenses() {
        // Dùng bộ đếm requestSeq để chống race-condition: bỏ qua kết quả của request cũ nếu người dùng gọi liên tiếp
        requestSeq++;
        final int seq = requestSeq;
        loadingLiveData.setValue(true);

        // Fetch all expenses without backend status filter so we can compute accurate KPIs and filter case-insensitively
        repository.getExpensesPaged(0, PAGE_SIZE, currentBranchId, currentCategoryId, null, null, null, new DataRepository.ApiCallback<ExpensePagedResponse>() {
            @Override
            public void onSuccess(ExpensePagedResponse result) {
                if (seq != requestSeq) return;
                loadingLiveData.postValue(false);
                List<Expense> items = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();
                allFetchedExpenses.clear();
                allFetchedExpenses.addAll(items);
                applyLocalFilter();
            }

            @Override
            public void onError(String message) {
                if (seq != requestSeq) return;
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
                emptyLiveData.postValue(allFetchedExpenses.isEmpty());
            }
        });
    }

    /**
     * Lọc danh sách phiếu chi trong bộ nhớ đệm (allFetchedExpenses) theo trạng thái currentStatus
     * và phát dữ liệu mới qua expensesLiveData để RecyclerView hiển thị.
     */
    private void applyLocalFilter() {
        List<Expense> filtered = new ArrayList<>();
        for (Expense e : allFetchedExpenses) {
            if (currentStatus == null || currentStatus.isEmpty() || "ALL".equalsIgnoreCase(currentStatus)) {
                filtered.add(e);
            } else {
                String st = e.getStatus() != null ? e.getStatus().trim() : "Pending";
                if (currentStatus.equalsIgnoreCase(st)) {
                    filtered.add(e);
                }
            }
        }
        expensesLiveData.postValue(filtered);
        emptyLiveData.postValue(filtered.isEmpty());
        lastPageLiveData.postValue(true);
    }

    /**
     * Hàm giữ chỗ cho phân trang tiếp theo (hiện tại đã tải trọn gói PAGE_SIZE = 100 qua refreshExpenses).
     */
    public void loadNextPage() {
        // Handled by refreshExpenses with PAGE_SIZE = 100
    }

    /**
     * Thiết lập bộ lọc chi nhánh, danh mục và trạng thái khi người dùng bấm chọn các Chip trạng thái trên UI.
     * Nếu chỉ đổi trạng thái (branchId và categoryId giữ nguyên) và đã có dữ liệu đệm thì chỉ lọc cục bộ (applyLocalFilter),
     * không cần gọi lại API nhằm tăng tốc độ phản hồi UI.
     */
    public void setFilter(Integer branchId, Integer categoryId, String status) {
        boolean branchOrCatChanged = !isEqualInt(this.currentBranchId, branchId) || !isEqualInt(this.currentCategoryId, categoryId);
        this.currentBranchId = branchId;
        this.currentCategoryId = categoryId;
        this.currentStatus = status;

        if (branchOrCatChanged || allFetchedExpenses.isEmpty()) {
            refreshExpenses();
            fetchSummary();
        } else {
            applyLocalFilter();
        }
    }

    /**
     * So sánh an toàn null-safe cho 2 giá trị Integer (dùng để kiểm tra xem filter branchId/categoryId có thay đổi không).
     */
    private boolean isEqualInt(Integer a, Integer b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
