package com.example.do_an_di_dong.ui.sales;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Order;
import com.example.do_an_di_dong.model.OrderPagedResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel quản lý trạng thái và dữ liệu danh sách đơn hàng (OrdersViewModel).
 * - Vai trò: Tải dữ liệu đơn hàng theo phân trang (offset/limit), chuẩn hóa từ khóa quét mã vạch/QR,
 *   lọc kết hợp phía client/server theo trạng thái & từ khóa, và làm mới ngầm (silent refresh) khi có sự kiện Realtime.
 * - Liên kết: Được sử dụng bởi `OrdersHistoryActivity`, gọi dữ liệu thông qua `DataRepository`.
 */
public class OrdersViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 50;

    private final DataRepository repository;
    private final MutableLiveData<List<Order>> ordersLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emptyLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> lastPageLiveData = new MutableLiveData<>(false);

    private final List<Order> accumulatedOrders = new ArrayList<>();
    private int currentStart = 0;
    private String currentStatusFilter = "ALL";
    private String currentKeyword = "";
    private int requestSeq = 0;

    /**
     * Khởi tạo ViewModel và instance DataRepository để truy xuất API / Room DB.
     */
    public OrdersViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<Order>> getOrders() {
        return ordersLiveData;
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
     * Kích hoạt tải danh sách đơn hàng từ trang đầu tiên (gọi qua refreshOrders).
     */
    public void fetchOrders() {
        refreshOrders();
    }

    /**
     * Đặt lại bộ đếm phân trang (currentStart = 0), xóa danh sách tích lũy cũ và tăng requestSeq
     * để hủy bỏ kết quả của các request bất đồng bộ cũ đang chạy dở, sau đó tải trang đầu tiên.
     */
    public void refreshOrders() {
        requestSeq++;
        currentStart = 0;
        accumulatedOrders.clear();
        loadingLiveData.setValue(false);
        lastPageLiveData.setValue(false);
        errorLiveData.setValue(null);
        loadNextPage();
    }

    /**
     * Tải trang đơn hàng tiếp theo từ DataRepository (50 bản ghi/trang).
     * Được gọi khi khởi tạo, khi đổi bộ lọc/từ khóa, hoặc khi người dùng cuộn chạm đáy RecyclerView.
     */
    public void loadNextPage() {
        if (Boolean.TRUE.equals(loadingLiveData.getValue()) || Boolean.TRUE.equals(lastPageLiveData.getValue())) {
            return;
        }

        final int seq = requestSeq;
        loadingLiveData.setValue(true);

        // Normalize keyword for backend (strip QR/Receipt prefixes like EMARKET-ORD:, #ORD-, ORD-)
        String normalizedKeyword = normalizeOrderKeyword(currentKeyword);
        String keywordParam = normalizedKeyword.isEmpty() ? null : normalizedKeyword;

        // Only pass exact backend status when it is single-valued ("Pending" or "Cancelled").
        // For "Completed" (which includes Completed/Delivered/Paid) or "Processing" (Processing/Shipping/Shipped),
        // fetch broadly and filter accurately in matchesFilter() so no valid order is ever missed.
        String statusParam = null;
        if ("Pending".equalsIgnoreCase(currentStatusFilter) || "Cancelled".equalsIgnoreCase(currentStatusFilter)) {
            statusParam = currentStatusFilter;
        }

        // Gọi DataRepository lấy danh sách đơn hàng phân trang từ API (hoặc fallback offline)
        repository.getOrdersPaged(currentStart, PAGE_SIZE, null, null, statusParam, null, null, keywordParam, new DataRepository.ApiCallback<OrderPagedResponse>() {
            @Override
            public void onSuccess(OrderPagedResponse result) {
                // Bỏ qua nếu response này thuộc về phiên request cũ trước khi người dùng đổi bộ lọc
                if (seq != requestSeq) return;
                loadingLiveData.postValue(false);
                List<Order> rawItems = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();

                // Lọc lại theo trạng thái gộp và chống trùng lặp (deduplicate) theo orderId trước khi đưa vào danh sách tích lũy
                for (Order item : rawItems) {
                    if (matchesStatusAndKeyword(item, currentStatusFilter, currentKeyword, normalizedKeyword)) {
                        boolean exists = false;
                        for (Order existing : accumulatedOrders) {
                            if (existing.getOrderId() == item.getOrderId() && item.getOrderId() > 0) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            accumulatedOrders.add(item);
                        }
                    }
                }

                ordersLiveData.postValue(new ArrayList<>(accumulatedOrders));

                // Cập nhật vị trí offset trang tiếp theo và kiểm tra điều kiện chạm trang cuối
                currentStart += rawItems.size();
                int total = (result != null) ? result.getTotal() : 0;

                boolean isLast = rawItems.isEmpty() || (total > 0 && currentStart >= total) || rawItems.size() < PAGE_SIZE;
                lastPageLiveData.postValue(isLast);
                emptyLiveData.postValue(accumulatedOrders.isEmpty());
            }

            @Override
            public void onError(String message) {
                if (seq != requestSeq) return;
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
                emptyLiveData.postValue(accumulatedOrders.isEmpty());
            }
        });
    }

    /**
     * Chuẩn hóa chuỗi tìm kiếm hoặc mã quét từ hóa đơn (cắt bỏ các tiền tố như `EMARKET-ORD:`, `#ORD-`, `ORD-`, `DH-`, `#`)
     * để lấy đúng số ID hoặc mã đơn thuần túy gửi lên Backend.
     */
    public static String normalizeOrderKeyword(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.startsWith("EMARKET-ORD:")) {
            s = s.substring("EMARKET-ORD:".length()).trim();
        } else if (upper.startsWith("EMARKET-ORD-")) {
            s = s.substring("EMARKET-ORD-".length()).trim();
        } else if (upper.startsWith("#ORD-")) {
            s = s.substring("#ORD-".length()).trim();
        } else if (upper.startsWith("ORD-")) {
            s = s.substring("ORD-".length()).trim();
        } else if (upper.startsWith("DH-") || upper.startsWith("DH")) {
            String candidate = upper.startsWith("DH-") ? s.substring(3).trim() : s.substring(2).trim();
            if (candidate.matches("\\d+")) {
                s = candidate;
            }
        } else if (upper.startsWith("#") && s.substring(1).trim().matches("\\d+")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    /**
     * Kiểm tra một đơn hàng có khớp với bộ lọc trạng thái (nhóm các trạng thái tương đương)
     * và từ khóa tìm kiếm (so khớp trên OrderId, OrderCode, CustomerName) hay không.
     */
    private boolean matchesStatusAndKeyword(Order order, String statusFilter, String rawKey, String normKey) {
        if (order == null) return false;

        // 1. Check status filter
        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            String st = order.getStatus() != null ? order.getStatus().trim() : "Pending";
            if ("Completed".equalsIgnoreCase(statusFilter) || "Delivered".equalsIgnoreCase(statusFilter)) {
                if (!("Completed".equalsIgnoreCase(st) || "Delivered".equalsIgnoreCase(st) || "Paid".equalsIgnoreCase(st))) {
                    return false;
                }
            } else if ("Processing".equalsIgnoreCase(statusFilter)) {
                if (!("Processing".equalsIgnoreCase(st) || "Shipping".equalsIgnoreCase(st) || "Shipped".equalsIgnoreCase(st))) {
                    return false;
                }
            } else if ("Cancelled".equalsIgnoreCase(statusFilter)) {
                if (!"Cancelled".equalsIgnoreCase(st)) {
                    return false;
                }
            } else if ("Pending".equalsIgnoreCase(statusFilter)) {
                if (!"Pending".equalsIgnoreCase(st)) {
                    return false;
                }
            }
        }

        // 2. Check keyword filter
        if (normKey != null && !normKey.isEmpty()) {
            String q = normKey.toLowerCase(Locale.ROOT);
            String rawLower = rawKey != null ? rawKey.toLowerCase(Locale.ROOT) : q;
            String idStr = String.valueOf(order.getOrderId());
            String code = order.getOrderCode() != null ? order.getOrderCode().toLowerCase(Locale.ROOT) : ("#ord-" + idStr);
            String cust = order.getCustomerName() != null ? order.getCustomerName().toLowerCase(Locale.ROOT) : "khách vãng lai";

            boolean match = idStr.contains(q)
                    || code.contains(q)
                    || code.contains(rawLower)
                    || cust.contains(q)
                    || cust.contains(rawLower);
            if (!match) return false;
        }

        return true;
    }

    /**
     * Cập nhật bộ lọc trạng thái đơn hàng khi người dùng chọn Chip trên giao diện.
     * Nếu trạng thái mới khác trạng thái hiện tại thì tự động tải lại danh sách.
     */
    public void setStatusFilter(String status) {
        String target = (status == null || status.isEmpty()) ? "ALL" : status;
        if (!isEqual(this.currentStatusFilter, target)) {
            this.currentStatusFilter = target;
            refreshOrders();
        }
    }

    private boolean isSilentRefreshing = false;

    /**
     * Cập nhật từ khóa tìm kiếm (sau khi đã debounce 300ms ở Activity) và tải lại danh sách nếu từ khóa thay đổi.
     */
    public void setSearchKeyword(String keyword) {
        String cleanKey = (keyword != null) ? keyword.trim() : "";
        if (!cleanKey.equalsIgnoreCase(currentKeyword)) {
            this.currentKeyword = cleanKey;
            refreshOrders();
        }
    }

    /**
     * Làm mới danh sách đơn hàng chạy ngầm (không bật spinner loading toàn màn hình).
     * Được kích hoạt bởi `ScreenRealtimeSync` khi có sự kiện thay đổi dữ liệu đơn hàng từ Server.
     */
    public void refreshOrdersSilently() {
        if (isSilentRefreshing || Boolean.TRUE.equals(loadingLiveData.getValue())) {
            return;
        }
        isSilentRefreshing = true;

        final int seq = requestSeq;
        final String statusSnap = currentStatusFilter;
        final String rawSnap = currentKeyword;
        final String statusParam = (statusSnap != null && !"ALL".equalsIgnoreCase(statusSnap)) ? statusSnap : null;
        final String keywordParam = (rawSnap != null && !rawSnap.isEmpty()) ? normalizeOrderKeyword(rawSnap) : null;
        // Tải lại toàn bộ số lượng item người dùng đã cuộn tới (tối thiểu bằng PAGE_SIZE)
        final int fetchLength = Math.max(PAGE_SIZE, accumulatedOrders.size());

        repository.getOrdersPaged(0, fetchLength, null, null, statusParam, null, null, keywordParam, new DataRepository.ApiCallback<OrderPagedResponse>() {
            @Override
            public void onSuccess(OrderPagedResponse result) {
                isSilentRefreshing = false;
                if (seq != requestSeq) return;

                List<Order> raw = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();
                List<Order> filtered = new ArrayList<>();
                for (Order item : raw) {
                    if (item != null && matchesStatusAndKeyword(item, statusSnap, rawSnap, keywordParam)) {
                        filtered.add(item);
                    }
                }

                accumulatedOrders.clear();
                accumulatedOrders.addAll(filtered);
                currentStart = raw.size();
                ordersLiveData.postValue(new ArrayList<>(accumulatedOrders));

                int total = result != null ? result.getTotal() : raw.size();
                lastPageLiveData.postValue(raw.isEmpty() || raw.size() >= total);
                emptyLiveData.postValue(accumulatedOrders.isEmpty());
            }

            @Override
            public void onError(String message) {
                isSilentRefreshing = false;
            }
        });
    }

    /**
     * So sánh 2 chuỗi không phân biệt hoa thường và an toàn với giá trị null.
     */
    private boolean isEqual(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}
