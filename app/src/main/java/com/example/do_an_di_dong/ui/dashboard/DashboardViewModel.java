package com.example.do_an_di_dong.ui.dashboard;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.DashboardOverview;
import com.example.do_an_di_dong.util.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * ViewModel quản lý trạng thái và dữ liệu cho màn hình Tổng quan (MainActivity).
 * Kết nối với DataRepository để lấy số liệu thống kê (DashboardOverview) theo khoảng ngày
 * và chi nhánh hiện tại của phiên người dùng (UserSessionManager).
 */
public class DashboardViewModel extends AndroidViewModel {
    private final DataRepository repository;
    private final MutableLiveData<DashboardOverview> dashboardData = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private int currentDays = 30;

    /**
     * Khởi tạo DashboardViewModel và tạo instance DataRepository để gọi API.
     */
    public DashboardViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<DashboardOverview> getDashboardData() {
        return dashboardData;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    /**
     * Tải lại dữ liệu tổng quan với số ngày đang được chọn gần nhất (mặc định 30 ngày).
     */
    public void fetchDashboardSummary() {
        fetchDashboardSummary(currentDays);
    }

    /**
     * Tính toán khoảng thời gian (fromDate -> toDate), lấy ID chi nhánh hiện tại
     * và gọi API lấy dữ liệu tổng quan Dashboard (KPI + biểu đồ xu hướng theo ngày).
     * Kết quả trả về sẽ được đẩy vào LiveData dashboardData hoặc error để MainActivity cập nhật UI.
     */
    public void fetchDashboardSummary(int days) {
        this.currentDays = days > 0 ? days : 30;
        loading.setValue(true);

        // Tính mốc thời gian kết thúc (cuối ngày hôm nay) và ngày bắt đầu lùi về trước (days - 1) ngày
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String todayStr = sdf.format(cal.getTime());
        String toDate = todayStr + "T23:59:59";

        String fromDate;
        if (this.currentDays == 1) {
            fromDate = todayStr;
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -(this.currentDays - 1));
            fromDate = sdf.format(cal.getTime());
        }

        // Lấy chi nhánh đang chọn trong phiên; nếu <= 0 (Tất cả chi nhánh) thì truyền null lên API
        int branchIdVal = UserSessionManager.getCurrentBranchId(getApplication());
        Integer branchId = branchIdVal > 0 ? branchIdVal : null;

        // Gọi DataRepository lấy báo cáo tổng quan gom nhóm theo ngày ("day")
        repository.getDashboardOverview(branchId, fromDate, toDate, "day", new DataRepository.ApiCallback<DashboardOverview>() {
            @Override
            public void onSuccess(DashboardOverview result) {
                loading.postValue(false);
                dashboardData.postValue(result);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }
}
