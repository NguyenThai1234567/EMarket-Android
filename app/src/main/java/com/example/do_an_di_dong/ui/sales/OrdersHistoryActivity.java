package com.example.do_an_di_dong.ui.sales;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ActivityOrdersHistoryBinding;
import com.example.do_an_di_dong.model.Order;
import com.example.do_an_di_dong.ui.common.AppToast;
import com.example.do_an_di_dong.ui.product.BarcodeScanActivity;

import java.util.List;

/**
 * Màn hình Lịch sử Đơn hàng (OrdersHistoryActivity).
 * - Vai trò: Hiển thị danh sách đơn hàng có phân trang (infinite scroll), hỗ trợ lọc theo trạng thái,
 *   tìm kiếm theo mã đơn/tên khách hàng (có debounce) và quét mã vạch/QR trên hóa đơn để tra cứu nhanh.
 * - Liên kết: Layout `activity_orders_history.xml` (ActivityOrdersHistoryBinding),
 *   ViewModel `OrdersViewModel`, Adapter `OrderAdapter` và đồng bộ thời gian thực qua `ScreenRealtimeSync`.
 */
public class OrdersHistoryActivity extends AppCompatActivity {
    private ActivityOrdersHistoryBinding binding;
    private OrdersViewModel viewModel;
    private OrderAdapter adapter;

    // Launcher nhận kết quả quét mã QR/Barcode từ BarcodeScanActivity
    private final ActivityResultLauncher<Intent> qrScanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String raw = result.getData().getStringExtra("scanned_barcode");
                    if (raw != null && !raw.trim().isEmpty()) {
                        handleScannedBillCode(raw.trim());
                    }
                }
            }
    );

    /**
     * Khởi tạo giao diện, cấu hình RecyclerView phân trang, đăng ký lắng nghe LiveData từ ViewModel
     * và thiết lập bộ lọc trạng thái / ô tìm kiếm. Kích hoạt khi màn hình được tạo lần đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrdersHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(OrdersViewModel.class);
        adapter = new OrderAdapter();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefreshOrders.setOnClickListener(v -> loadData());
        binding.btnScanOrderQr.setOnClickListener(v -> {
            Intent scanIntent = new Intent(this, BarcodeScanActivity.class);
            qrScanLauncher.launch(scanIntent);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvOrders.setLayoutManager(layoutManager);
        binding.rvOrders.setAdapter(adapter);

        // Lắng nghe sự kiện cuộn danh sách: khi cuộn gần chạm đáy (còn <= 3 item) thì tự động gọi tải trang tiếp theo
        binding.rvOrders.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3 && firstVisibleItemPosition >= 0) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        adapter.setOnOrderClickListener(this::openOrderDetail);

        // Quan sát LiveData danh sách đơn hàng để cập nhật RecyclerView hoặc hiển thị trạng thái rỗng
        viewModel.getOrders().observe(this, list -> {
            binding.layoutLoading.setVisibility(View.GONE);
            binding.layoutError.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);

            if (list == null || list.isEmpty()) {
                binding.rvOrders.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.rvOrders.setVisibility(View.VISIBLE);
                adapter.setOrders(list);
            }
        });

        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                showError(msg);
            }
        });

        // Debounce 300ms cho ô tìm kiếm để tránh spam request API khi người dùng đang gõ phím liên tục
        final Handler searchHandler = new Handler(Looper.getMainLooper());
        final Runnable[] searchRunnable = new Runnable[1];

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable[0] != null) {
                    searchHandler.removeCallbacks(searchRunnable[0]);
                }
                searchRunnable[0] = () -> viewModel.setSearchKeyword(s.toString().trim());
                searchHandler.postDelayed(searchRunnable[0], 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Lọc danh sách đơn hàng theo Chip trạng thái được chọn
        binding.chipGroupStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipPending) {
                viewModel.setStatusFilter("Pending");
            } else if (checkedId == R.id.chipProcessing) {
                viewModel.setStatusFilter("Processing");
            } else if (checkedId == R.id.chipCompleted) {
                viewModel.setStatusFilter("Completed");
            } else if (checkedId == R.id.chipCancelled) {
                viewModel.setStatusFilter("Cancelled");
            } else {
                viewModel.setStatusFilter("ALL");
            }
        });

        binding.btnRetry.setOnClickListener(v -> loadData());
    }

    private com.example.do_an_di_dong.data.realtime.ScreenRealtimeSync realtimeSync;

    /**
     * Khi quay lại màn hình (onResume), tự động làm mới dữ liệu và bật kênh đồng bộ Realtime (SSE/Polling).
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        syncOrdersRealtimeState(true);
    }

    /**
     * Khi tạm rời màn hình (onPause), tạm dừng kênh đồng bộ Realtime để tiết kiệm tài nguyên.
     */
    @Override
    protected void onPause() {
        syncOrdersRealtimeState(false);
        super.onPause();
    }

    /**
     * Giải phóng tài nguyên đồng bộ Realtime khi Activity bị hủy hoàn toàn.
     */
    @Override
    protected void onDestroy() {
        if (realtimeSync != null) {
            realtimeSync.destroy();
            realtimeSync = null;
        }
        super.onDestroy();
    }

    /**
     * Bật/tắt kết nối đồng bộ dữ liệu đơn hàng theo thời gian thực (Realtime).
     * Lấy thông tin chi nhánh (branchId) và quyền Admin từ session để giới hạn phạm vi lắng nghe sự kiện.
     */
    private void syncOrdersRealtimeState(boolean active) {
        if (realtimeSync == null) {
            if (!active) return;
            realtimeSync = new com.example.do_an_di_dong.data.realtime.ScreenRealtimeSync(
                    this,
                    () -> {
                        if (!isFinishing() && !isDestroyed()) {
                            viewModel.refreshOrdersSilently();
                        }
                    }
            );
        }
        // Lấy branchId và cờ phân quyền RBAC (isAdmin) từ phiên đăng nhập hiện tại
        int branchId = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(this);
        boolean isAdmin = com.example.do_an_di_dong.util.UserSessionManager.isAdmin(this);
        realtimeSync.setActive(active && !isFinishing() && !isDestroyed(), branchId, isAdmin);
    }

    /**
     * Xử lý chuỗi mã hóa đơn sau khi quét QR/Barcode thành công từ BarcodeScanActivity.
     * Chuẩn hóa mã, nếu khớp với đơn hàng đang có sẵn trên RAM thì mở ngay màn hình chi tiết,
     * ngược lại đặt từ khóa tìm kiếm để truy vấn từ Server/Local DB.
     */
    private void handleScannedBillCode(String rawCode) {
        String normalized = OrdersViewModel.normalizeOrderKeyword(rawCode);
        AppToast.show(this, "Đã quét mã hóa đơn: " + rawCode, AppToast.Type.INFO);

        // Reset filter to ALL so the scanned order is always found
        binding.chipAll.setChecked(true);
        binding.etSearch.setText(normalized);
        binding.etSearch.setSelection(normalized.length());

        // If the order is already in current list and normalized is an integer orderId, open immediately
        try {
            int scannedOrderId = Integer.parseInt(normalized);
            List<Order> current = viewModel.getOrders().getValue();
            if (current != null) {
                for (Order o : current) {
                    if (o.getOrderId() == scannedOrderId) {
                        openOrderDetail(o);
                        return;
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        }
        viewModel.setSearchKeyword(normalized);
    }

    /**
     * Đóng gói thông tin tổng quan của đơn hàng vào Intent và điều hướng sang màn hình OrderDetailActivity.
     * Được gọi khi người dùng bấm vào 1 dòng đơn hàng hoặc quét trúng mã đơn hàng đã tải.
     */
    private void openOrderDetail(Order order) {
        Intent intent = new Intent(this, OrderDetailActivity.class);
        intent.putExtra("order_id", order.getOrderId());
        intent.putExtra("order_code", order.getOrderCode());
        intent.putExtra("customer_name", order.getCustomerName());
        intent.putExtra("order_date", order.getOrderDate());
        intent.putExtra("order_status", order.getStatus());
        intent.putExtra("total_amount", order.getTotalAmount());
        intent.putExtra("discount_amount", order.getDiscountAmount());
        intent.putExtra("payment_method", order.getPaymentMethod());
        startActivity(intent);
    }

    /**
     * Hiển thị giao diện Loading và yêu cầu ViewModel tải lại danh sách đơn hàng từ đầu.
     */
    private void loadData() {
        binding.layoutContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.VISIBLE);
        viewModel.fetchOrders();
    }

    /**
     * Chuyển đổi thông báo lỗi kỹ thuật (timeout, 401, 403...) thành thông điệp tiếng Việt thân thiện
     * và hiển thị lên layout báo lỗi khi gọi API thất bại.
     */
    private void showError(String message) {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);

        String raw = message != null ? message.trim() : "";
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        String friendlyMessage;
        // Phân loại lỗi kết nối mạng và lỗi phân quyền HTTP (401/403)
        if (lower.contains("connectexception") || lower.contains("timeout") || lower.contains("unable to resolve host")
                || lower.contains("không thể kết nối") || lower.contains("quá thời gian chờ")) {
            friendlyMessage = "Không thể kết nối mạng. Vui lòng kiểm tra Wifi/Mạng hoặc cấu hình Server API.";
        } else if (lower.contains("401") || lower.contains("hết hạn")) {
            friendlyMessage = "Bạn không có quyền truy cập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        } else if (lower.contains("403") || lower.contains("không có quyền")) {
            friendlyMessage = "Bạn không có quyền truy cập danh sách đơn hàng.";
        } else {
            friendlyMessage = !raw.isEmpty() ? raw : "Lỗi tải danh sách đơn hàng.";
        }
        binding.tvErrorMessage.setText(friendlyMessage);
    }
}
