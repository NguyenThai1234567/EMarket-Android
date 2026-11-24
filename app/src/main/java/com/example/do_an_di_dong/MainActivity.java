package com.example.do_an_di_dong;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.do_an_di_dong.databinding.ActivityMainBinding;
import com.example.do_an_di_dong.model.DashboardOverview;
import com.example.do_an_di_dong.model.DashboardTrend;
import com.example.do_an_di_dong.ui.admin.CustomersActivity;
import com.example.do_an_di_dong.ui.admin.ExpensesActivity;
import com.example.do_an_di_dong.ui.admin.UsersActivity;
import com.example.do_an_di_dong.ui.ai.AIAssistantActivity;
import com.example.do_an_di_dong.ui.common.AppNavigationShell;
import com.example.do_an_di_dong.ui.common.ServerSettingsDialog;
import com.example.do_an_di_dong.ui.dashboard.DashboardViewModel;
import com.example.do_an_di_dong.ui.dashboard.SupplierDebtActivity;
import com.example.do_an_di_dong.ui.inventory.PurchaseOrdersActivity;
import com.example.do_an_di_dong.ui.inventory.WarehouseInventoryActivity;
import com.example.do_an_di_dong.ui.pos.PosActivity;
import com.example.do_an_di_dong.ui.sales.OrdersHistoryActivity;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Tổng quan Điều hành (Dashboard) - Kiến trúc Zero-Fragment (0 Fragment).
 * Gắn với layout: activity_main.xml (ActivityMainBinding) và DashboardViewModel.
 * Hiển thị KPI doanh thu, đơn hàng, tồn kho thấp, biểu đồ xu hướng (MPAndroidChart)
 * và các lối tắt (Quick Hub) được ẩn/hiện tự động theo phân quyền RBAC của người dùng.
 */
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private DashboardViewModel viewModel;
    private int selectedDays = 30;

    /**
     * Khởi tạo màn hình Dashboard.
     * Kiểm tra quyền RBAC xem user có được phép vào Dashboard không (nếu không sẽ chuyển hướng sang màn hình phù hợp),
     * sau đó khởi tạo ViewModel, thanh điều hướng, bộ lọc ngày và gọi tải dữ liệu tổng quan.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Chốt chặn RBAC: Nếu role không có quyền xem Dashboard (VD: Thu ngân, Thủ kho), đẩy về màn hình chính của role đó
        if (!UserSessionManager.canAccessDashboard(this)) {
            Class<?> landing = UserSessionManager.getLandingActivityClass(this);
            if (!landing.equals(MainActivity.class)) {
                startActivity(new Intent(this, landing));
                finish();
                return;
            }
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setupHeaderAndNavigation();
        setupDateFilterChips();
        setupRbacShortcuts();
        observeViewModel();

        binding.btnRetry.setOnClickListener(v -> loadData());
        binding.btnServerConfigError.setOnClickListener(v -> ServerSettingsDialog.show(this));
        binding.swipeRefreshDashboard.setOnRefreshListener(this::loadData);

        loadData();
    }

    /**
     * Được gọi mỗi khi người dùng quay lại màn hình Dashboard.
     * Kiểm tra lại quyền RBAC, cập nhật lại các nút lối tắt và đồng bộ Drawer/BottomNavigation/Chi nhánh hiện tại.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!UserSessionManager.canAccessDashboard(this)) {
            Class<?> landing = UserSessionManager.getLandingActivityClass(this);
            if (!landing.equals(MainActivity.class)) {
                startActivity(new Intent(this, landing));
                finish();
                return;
            }
        }
        setupRbacShortcuts();
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_dashboard,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> loadData()
        );
    }

    /**
     * Thiết lập thông tin Header (lời chào họ tên + chức vụ, ngày hiện tại, nút Trợ lý AI theo quyền)
     * và gắn kết khung điều hướng chung AppNavigationShell (Drawer + BottomNav + chọn Chi nhánh).
     */
    private void setupHeaderAndNavigation() {
        String fullName = UserSessionManager.getCurrentUserFullName(this);
        String roleName = UserSessionManager.getRoleName(this);
        binding.tvUserWelcome.setText("Chào " + fullName + " (" + roleName + ")");

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi", "VN"));
        binding.tvDate.setText(sdf.format(new Date()));

        // Chỉ hiển thị nút Trợ lý AI trên Header nếu tài khoản có quyền sử dụng AI Chat
        boolean canAi = UserSessionManager.canAccessAiChat(this);
        binding.btnHeaderAI.setVisibility(canAi ? View.VISIBLE : View.GONE);
        if (canAi) {
            binding.btnHeaderAI.setOnClickListener(v -> startActivity(new Intent(this, AIAssistantActivity.class)));
        }

        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_dashboard,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> loadData()
        );
    }

    /**
     * Lắng nghe sự kiện chọn Chip lọc khoảng thời gian (Hôm nay / 7 ngày / 30 ngày / 90 ngày)
     * và yêu cầu DashboardViewModel tải lại số liệu theo số ngày tương ứng.
     */
    private void setupDateFilterChips() {
        binding.chipGroupDateRange.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipDays1) {
                selectedDays = 1;
                binding.tvChartRangeBadge.setText("Hôm nay");
            } else if (checkedId == R.id.chipDays7) {
                selectedDays = 7;
                binding.tvChartRangeBadge.setText("7 ngày");
            } else if (checkedId == R.id.chipDays90) {
                selectedDays = 90;
                binding.tvChartRangeBadge.setText("90 ngày");
            } else {
                selectedDays = 30;
                binding.tvChartRangeBadge.setText("30 ngày");
            }
            viewModel.fetchDashboardSummary(selectedDays);
        });
    }

    /**
     * Kích hoạt tải dữ liệu tổng quan từ ViewModel (khi mới mở màn hình, đổi chi nhánh, bấm Thử lại hoặc kéo SwipeRefresh).
     */
    private void loadData() {
        binding.layoutError.setVisibility(View.GONE);
        if (!binding.swipeRefreshDashboard.isRefreshing()) {
            binding.swipeRefreshDashboard.setVisibility(View.GONE);
            binding.layoutLoading.setVisibility(View.VISIBLE);
        }
        viewModel.fetchDashboardSummary(selectedDays);
    }

    /**
     * Đăng ký lắng nghe (Observe) các LiveData từ DashboardViewModel để cập nhật giao diện
     * khi có dữ liệu thống kê mới hoặc hiển thị màn hình lỗi khi gọi API thất bại.
     */
    private void observeViewModel() {
        viewModel.getDashboardData().observe(this, this::updateUi);
        viewModel.getError().observe(this, msg -> {
            binding.swipeRefreshDashboard.setRefreshing(false);
            if (msg != null) {
                showError(msg);
            }
        });
    }

    /**
     * Chuyển đổi giao diện sang trạng thái lỗi và biên dịch thông báo lỗi kỹ thuật (mạng, 401, 403)
     * sang câu thông báo tiếng Việt thân thiện với người dùng.
     */
    private void showError(String message) {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.swipeRefreshDashboard.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);

        String raw = message != null ? message.trim() : "";
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        String friendlyMessage;
        if (lower.contains("connectexception") || lower.contains("timeout") || lower.contains("unable to resolve host")
                || lower.contains("không thể kết nối") || lower.contains("quá thời gian chờ")) {
            friendlyMessage = "Không thể kết nối mạng tới máy chủ Backend. Vui lòng kiểm tra Wifi/4G hoặc cấu hình Server API.";
        } else if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("hết hạn")) {
            friendlyMessage = "Bạn không có quyền truy cập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        } else if (lower.contains("403") || lower.contains("không có quyền")) {
            friendlyMessage = "Bạn không có quyền xem báo cáo tổng quan.";
        } else {
            friendlyMessage = !raw.isEmpty() ? raw : "Đã xảy ra lỗi khi tải dữ liệu tổng quan.";
        }
        binding.tvErrorMessage.setText(friendlyMessage);
    }

    /**
     * Đổ dữ liệu DashboardOverview nhận từ API lên 4 thẻ KPI (Doanh thu, Đơn hàng, Sắp hết hàng, Tổng sản phẩm)
     * và gọi vẽ lại biểu đồ đường xu hướng doanh thu.
     */
    private void updateUi(DashboardOverview data) {
        binding.swipeRefreshDashboard.setRefreshing(false);
        if (data == null) {
            showError("Dữ liệu trả về trống.");
            return;
        }

        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.swipeRefreshDashboard.setVisibility(View.VISIBLE);

        double totalSales = 0;
        int totalOrders = 0;
        int lowStock = 0;
        int totalProducts = 0;

        if (data.getSummary() != null) {
            totalSales = data.getSummary().getTotalSales();
            totalOrders = data.getSummary().getTotalOrders();
            lowStock = data.getSummary().getLowStockProducts();
            totalProducts = data.getSummary().getTotalProducts();
        }

        // Định dạng tiền tệ VNĐ và hiển thị các chỉ số tổng hợp lên thẻ KPI
        binding.tvRevenue.setText(Formatters.currency(totalSales));
        binding.tvOrders.setText(totalOrders + " đơn");
        binding.tvLowStock.setText(lowStock + " mã");
        binding.tvNewCustomers.setText(totalProducts + " sản phẩm");

        if (data.getTrend() != null) {
            setupChart(data.getTrend());
        }
    }

    /**
     * Cấu hình và vẽ biểu đồ đường (LineChart - thư viện MPAndroidChart) thể hiện xu hướng doanh thu theo ngày.
     * Nhận vào đối tượng DashboardTrend chứa danh sách nhãn ngày (labels) và mảng doanh thu (sales).
     */
    private void setupChart(DashboardTrend trend) {
        List<Entry> entries = new ArrayList<>();
        List<Double> sales = trend.getSales();

        if (sales == null || sales.isEmpty()) {
            binding.lineChart.clear();
            return;
        }

        // Chuyển đổi mảng doanh thu thành danh sách tọa độ điểm (x: chỉ số ngày, y: doanh thu)
        for (int i = 0; i < sales.size(); i++) {
            Double val = sales.get(i);
            entries.add(new Entry(i, val == null ? 0f : val.floatValue()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Doanh thu");
        dataSet.setColor(Color.parseColor("#4F46E5"));
        dataSet.setLineWidth(2.5f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCircleColor(Color.parseColor("#4F46E5"));
        dataSet.setCircleRadius(3.5f);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#818CF8"));
        dataSet.setFillAlpha(35);

        LineData lineData = new LineData(dataSet);
        binding.lineChart.setData(lineData);
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.getLegend().setEnabled(false);
        binding.lineChart.setScaleEnabled(false);
        binding.lineChart.setDragEnabled(true);

        XAxis xAxis = binding.lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-25);
        xAxis.setTextColor(Color.parseColor("#64748B"));
        xAxis.setTextSize(10f);

        List<String> labels = trend.getLabels();
        if (labels != null && !labels.isEmpty()) {
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        }

        binding.lineChart.getAxisLeft().setDrawGridLines(true);
        binding.lineChart.getAxisLeft().setGridColor(Color.parseColor("#F1F5F9"));
        binding.lineChart.getAxisLeft().setTextColor(Color.parseColor("#64748B"));
        binding.lineChart.getAxisLeft().setTextSize(10f);
        binding.lineChart.getAxisRight().setEnabled(false);

        binding.lineChart.animateY(450);
        binding.lineChart.invalidate();
    }

    /**
     * Phân quyền hiển thị (RBAC) cho khu vực Thao tác nhanh (Quick Hub):
     * Kiểm tra từng quyền nghiệp vụ (Bán hàng, Kho, Chi phí, NCC, Khách hàng, Nhân sự, AI)
     * để ẩn/hiện các thẻ Card và các nút Pill tương ứng.
     */
    private void setupRbacShortcuts() {
        boolean canSales = UserSessionManager.canAccessSales(this);
        boolean canInventory = UserSessionManager.canAccessInventory(this);
        boolean canExpenses = UserSessionManager.canAccessExpenses(this);
        boolean canSuppliers = UserSessionManager.canAccessSuppliers(this);
        boolean canCustomers = UserSessionManager.canAccessCustomers(this);
        boolean canEmployees = UserSessionManager.canAccessEmployees(this);
        boolean canAi = UserSessionManager.canAccessAiChat(this);

        if (binding.tvOpenMenuHint != null) {
            binding.tvOpenMenuHint.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));
        }

        // Cấu hình 2 hàng thẻ lớn (POS, Nhập kho, Sổ chi phí, Công nợ NCC)
        configureHubAction(binding.cardHubPos, canSales, PosActivity.class, true);
        configureHubAction(binding.cardHubPurchase, canInventory, PurchaseOrdersActivity.class, false);
        boolean row1Visible = syncHubRow(binding.layoutHubRow1, binding.cardHubPos, binding.cardHubPurchase);

        configureHubAction(binding.cardHubExpense, canExpenses, ExpensesActivity.class, false);
        configureHubAction(binding.cardHubSupplier, canSuppliers, SupplierDebtActivity.class, false);
        boolean row2Visible = syncHubRow(binding.layoutHubRow2, binding.cardHubExpense, binding.cardHubSupplier);

        // Cấu hình dải nút Pill ngang (Lịch sử đơn, Khách hàng, Nhân sự, Trợ lý AI)
        configureHubAction(binding.pillHubOrders, canSales, OrdersHistoryActivity.class, false);
        configureHubAction(binding.pillHubCustomers, canCustomers, CustomersActivity.class, false);
        configureHubAction(binding.pillHubUsers, canEmployees, UsersActivity.class, false);
        configureHubAction(binding.pillHubAi, canAi, AIAssistantActivity.class, false);

        boolean anyPillVisible = canSales || canCustomers || canEmployees || canAi;
        if (binding.hsvQuickPills != null) {
            binding.hsvQuickPills.setVisibility(anyPillVisible ? View.VISIBLE : View.GONE);
        }
        if (binding.layoutQuickHubSection != null) {
            binding.layoutQuickHubSection.setVisibility(
                    (row1Visible || row2Visible || anyPillVisible) ? View.VISIBLE : View.GONE
            );
        }
    }

    /**
     * Hàm tiện ích gắn quyền RBAC và sự kiện chuyển màn hình cho một nút/thẻ trong Quick Hub.
     * Nếu không có quyền (hasPermission = false) thì ẩn hoàn toàn View khỏi giao diện.
     */
    private void configureHubAction(View view, boolean hasPermission, Class<?> targetActivity, boolean isMainTab) {
        if (view == null) return;
        if (!hasPermission) {
            view.setVisibility(View.GONE);
            view.setOnClickListener(null);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(v -> {
            if (isMainTab) {
                AppNavigationShell.navigateMainTab(this, targetActivity);
            } else {
                startActivity(new Intent(this, targetActivity));
            }
        });
    }

    /**
     * Căn chỉnh lại khoảng cách lề (margin) và trạng thái ẩn/hiện của một hàng chứa 2 thẻ Card trong Quick Hub.
     * Giúp thẻ còn lại tự động giãnเต็ม chiều ngang nếu thẻ bên cạnh bị ẩn do không có quyền.
     */
    private boolean syncHubRow(View row, View leftCard, View rightCard) {
        boolean leftVisible = leftCard != null && leftCard.getVisibility() == View.VISIBLE;
        boolean rightVisible = rightCard != null && rightCard.getVisibility() == View.VISIBLE;
        boolean anyVisible = leftVisible || rightVisible;
        int halfGapPx = (leftVisible && rightVisible)
                ? Math.round(6f * getResources().getDisplayMetrics().density)
                : 0;
        if (leftCard != null && leftCard.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) leftCard.getLayoutParams();
            if (lp.getMarginEnd() != halfGapPx) {
                lp.setMarginEnd(halfGapPx);
                leftCard.setLayoutParams(lp);
            }
        }
        if (rightCard != null && rightCard.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) rightCard.getLayoutParams();
            if (lp.getMarginStart() != halfGapPx) {
                lp.setMarginStart(halfGapPx);
                rightCard.setLayoutParams(lp);
            }
        }
        if (row != null) {
            row.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
        }
        return anyVisible;
    }

    /**
     * Thực hiện đăng xuất tài khoản hiện tại thông qua AppNavigationShell.
     */
    public void signOut() {
        AppNavigationShell.signOut(this);
    }
}
