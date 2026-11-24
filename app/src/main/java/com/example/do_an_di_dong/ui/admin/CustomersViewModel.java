package com.example.do_an_di_dong.ui.admin;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Customer;
import com.example.do_an_di_dong.model.CustomerPagedResponse;
import com.example.do_an_di_dong.model.LoyaltyProgram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel quản lý dữ liệu và trạng thái cho màn hình Khách hàng (CustomersActivity).
 * - Kết nối với DataRepository để tải danh sách khách hàng (hỗ trợ phân trang & fallback),
 *   thống kê CRM 30 ngày, và toàn bộ lịch sử giao dịch điểm thưởng (LoyaltyProgram).
 * - Tự động tổng hợp (aggregate) số dư điểm khả dụng & tổng điểm tích lũy cho từng khách hàng
 *   trước khi phát dữ liệu qua LiveData lên UI.
 */
public class CustomersViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 200;

    private final DataRepository repository;
    private final MutableLiveData<List<Customer>> customersLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Object>> statsLiveData = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    private final List<Customer> allCustomers = new ArrayList<>();
    private final Map<Integer, List<LoyaltyProgram>> loyaltyMap = new HashMap<>();

    public CustomersViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<Customer>> getCustomers() {
        return customersLiveData;
    }

    public LiveData<Map<String, Object>> getStats() {
        return statsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    /**
     * Lấy nhanh danh sách lịch sử biến động điểm thưởng đã được cache trong bộ nhớ theo customerId.
     * Giúp BottomSheet chi tiết điểm hiển thị tức thì mà không phải chờ API.
     */
    public List<LoyaltyProgram> getCachedLoyaltyForCustomer(int customerId) {
        List<LoyaltyProgram> list = loyaltyMap.get(customerId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * Điểm vào kích hoạt tải dữ liệu khách hàng (ủy quyền cho refreshCustomers).
     */
    public void fetchCustomers() {
        refreshCustomers();
    }

    /**
     * Tải mới toàn bộ dữ liệu CRM từ máy chủ:
     * 1. Gọi API lấy thống kê khách hàng mới trong 30 ngày gần nhất.
     * 2. Gọi API lấy danh sách khách hàng phân trang (nếu lỗi sẽ fallback sang API getAllCustomers).
     * 3. Sau khi có danh sách khách hàng, tiếp tục gọi loadAndAggregateLoyalty() để ghép điểm thưởng.
     */
    public void refreshCustomers() {
        loadingLiveData.setValue(true);

        // 1. Load CRM Stats
        repository.getCustomerStats(30, new DataRepository.ApiCallback<Map<String, Integer>>() {
            @Override
            public void onSuccess(Map<String, Integer> result) {
                if (result != null) {
                    Map<String, Object> objMap = new HashMap<>(result);
                    statsLiveData.postValue(objMap);
                }
            }

            @Override
            public void onError(String message) {
                // Non-fatal
            }
        });

        // 2. Load Customers + All Loyalty Programs and merge
        repository.getCustomersPaged(0, PAGE_SIZE, null, new DataRepository.ApiCallback<CustomerPagedResponse>() {
            @Override
            public void onSuccess(CustomerPagedResponse result) {
                List<Customer> items = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();
                allCustomers.clear();
                allCustomers.addAll(items);
                loadAndAggregateLoyalty();
            }

            @Override
            public void onError(String message) {
                // Fallback sang endpoint lấy toàn bộ khách hàng ( hoặc Room cache) nếu API phân trang gặp sự cố
                // Fallback to getAllCustomers
                repository.getAllCustomers(new DataRepository.ApiCallback<List<Customer>>() {
                    @Override
                    public void onSuccess(List<Customer> fallback) {
                        allCustomers.clear();
                        if (fallback != null) allCustomers.addAll(fallback);
                        loadAndAggregateLoyalty();
                    }

                    @Override
                    public void onError(String err) {
                        loadingLiveData.postValue(false);
                        errorLiveData.postValue(err);
                    }
                });
            }
        });
    }

    /**
     * Tải toàn bộ bản ghi lịch sử điểm thưởng (LoyaltyProgram) từ Backend, gom nhóm theo customerId
     * và tính tổng số dư điểm khả dụng (netPoints) cũng như tổng điểm tích lũy trọn đời (positiveEarned)
     * để đồng bộ chính xác vào từng đối tượng Customer trước khi đẩy lên customersLiveData.
     */
    private void loadAndAggregateLoyalty() {
        repository.getAllLoyaltyApi(new DataRepository.ApiCallback<List<LoyaltyProgram>>() {
            @Override
            public void onSuccess(List<LoyaltyProgram> loyaltyList) {
                loyaltyMap.clear();
                Map<Integer, Integer> balanceByCust = new HashMap<>();
                Map<Integer, Integer> earnedByCust = new HashMap<>();

                // Gom nhóm giao dịch điểm theo từng khách hàng và cộng dồn số dư (netPoints) & tổng điểm cộng (positiveEarned)
                if (loyaltyList != null) {
                    for (LoyaltyProgram lp : loyaltyList) {
                        if (lp == null) continue;
                        int cid = lp.getCustomerId();
                        List<LoyaltyProgram> bucket = loyaltyMap.get(cid);
                        if (bucket == null) {
                            bucket = new ArrayList<>();
                            loyaltyMap.put(cid, bucket);
                        }
                        bucket.add(lp);

                        int currentBal = balanceByCust.containsKey(cid) ? balanceByCust.get(cid) : 0;
                        balanceByCust.put(cid, currentBal + lp.getNetPoints());

                        int currentEarned = earnedByCust.containsKey(cid) ? earnedByCust.get(cid) : 0;
                        earnedByCust.put(cid, currentEarned + lp.getPositiveEarned());
                    }
                }

                // Đối chiếu và cập nhật điểm khả dụng & tổng điểm tích lũy vào từng Customer (đảm bảo không âm và không thấp hơn số dư hiện tại)
                for (Customer c : allCustomers) {
                    int cid = c.getCustomerId();
                    if (balanceByCust.containsKey(cid)) {
                        int aggBalance = Math.max(0, balanceByCust.get(cid));
                        if (c.getPointBalance() <= 0 || aggBalance > 0) {
                            c.setPointBalance(aggBalance);
                        }
                    }
                    if (earnedByCust.containsKey(cid)) {
                        int aggEarned = earnedByCust.get(cid);
                        c.setPointEarnedTotal(Math.max(c.getPointEarnedTotal(), Math.max(aggEarned, c.getPointBalance())));
                    } else {
                        c.setPointEarnedTotal(Math.max(c.getPointEarnedTotal(), c.getPointBalance()));
                    }
                }

                loadingLiveData.postValue(false);
                customersLiveData.postValue(new ArrayList<>(allCustomers));
            }

            @Override
            public void onError(String message) {
                // Fallback an toàn: Nếu API điểm thưởng lỗi, vẫn trả danh sách khách hàng gốc về cho UI hiển thị
                // Still publish customers even if loyalty endpoint fails
                loadingLiveData.postValue(false);
                customersLiveData.postValue(new ArrayList<>(allCustomers));
            }
        });
    }
}
