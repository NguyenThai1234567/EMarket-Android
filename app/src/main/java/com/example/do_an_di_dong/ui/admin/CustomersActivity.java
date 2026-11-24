package com.example.do_an_di_dong.ui.admin;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityCustomersBinding;
import com.example.do_an_di_dong.model.Customer;
import com.example.do_an_di_dong.model.LoyaltyProgram;
import com.example.do_an_di_dong.ui.common.AppToast;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.util.Formatters;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Quản lý Khách hàng & CRM Loyalty (CustomersActivity).
 * - Gắn với layout: activity_customers.xml (ViewBinding: ActivityCustomersBinding).
 * - Phối hợp cùng: CustomersViewModel (quản lý state LiveData), CustomerAdapter (hiển thị RecyclerView)
 *   và DataRepository (gọi REST API xóa khách hàng, lấy lịch sử điểm thưởng).
 * - Chức năng chính: Hiển thị KPI thống kê, tìm kiếm/lọc khách hàng theo hạng thẻ & điểm thưởng,
 *   thêm/sửa/xóa hồ sơ CRM, xem lịch sử tích/tiêu điểm và gọi điện/nhắn tin SMS CSKH.
 */
public class CustomersActivity extends AppCompatActivity {
    private ActivityCustomersBinding binding;
    private CustomersViewModel viewModel;
    private CustomerAdapter adapter;
    private DataRepository repository;
    private final List<Customer> allCustomers = new ArrayList<>();

    private String currentSearch = "";
    private String currentChipMode = "ALL"; // ALL, POINTS, VIP

    /**
     * Khởi tạo màn hình, gắn ViewBinding, thiết lập RecyclerView/Adapter,
     * đăng ký các sự kiện tương tác UI (tìm kiếm, lọc chip, thêm mới) và lắng nghe LiveData từ ViewModel.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        viewModel = new ViewModelProvider(this).get(CustomersViewModel.class);
        adapter = new CustomerAdapter();
        adapter.setListener(this::showCustomerActionSheet);
        adapter.setPointsListener(this::showLoyaltyHistorySheet);
        adapter.setCallListener(this::dialCustomer);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefreshCustomers.setOnClickListener(v -> viewModel.fetchCustomers());
        binding.rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCustomers.setAdapter(adapter);

        // Mở BottomSheet thêm mới khách hàng; sau khi lưu thành công sẽ gọi lại fetchCustomers() để làm mới danh sách
        binding.fabAddCustomer.setOnClickListener(v -> {
            ModernPickerSheets.showCustomerFormSheet(this, null, () -> viewModel.fetchCustomers());
        });

        // Lắng nghe ô tìm kiếm có debounce để tránh lọc liên tục trên mỗi ký tự gõ
        binding.etSearchCustomer.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> {
            currentSearch = q;
            applyFilters();
        }));

        // Lọc nhanh danh sách theo Chip: Tất cả (ALL), Có điểm thưởng (POINTS), hoặc Khách VIP (VIP)
        binding.chipGroupCustomerFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipCustomerHasPoints) {
                currentChipMode = "POINTS";
            } else if (checkedId == R.id.chipCustomerVip) {
                currentChipMode = "VIP";
            } else {
                currentChipMode = "ALL";
            }
            applyFilters();
        });

        // Quan sát LiveData danh sách khách hàng từ ViewModel để cập nhật bộ nhớ tạm, tính lại KPI và lọc hiển thị
        viewModel.getCustomers().observe(this, list -> {
            allCustomers.clear();
            if (list != null) {
                allCustomers.addAll(list);
            }
            updateSummaryKpis();
            applyFilters();
        });

        // Quan sát thống kê CRM (số khách mới trong 30 ngày) trả về từ API stats
        viewModel.getStats().observe(this, stats -> {
            if (stats != null && !stats.isEmpty()) {
                updateSummaryKpisFromStats(stats);
            }
        });

        // Điều khiển ẩn/hiện khung loading skeleton hoặc nội dung chính theo trạng thái tải
        viewModel.getLoading().observe(this, isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) {
                binding.layoutLoading.setVisibility(View.VISIBLE);
                binding.layoutContent.setVisibility(View.GONE);
            } else {
                binding.layoutLoading.setVisibility(View.GONE);
                binding.layoutContent.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getError().observe(this, msg -> {
            binding.layoutLoading.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);
            if (msg != null && !msg.isEmpty()) {
                AppToast.show(this, msg, AppToast.Type.ERROR);
            }
        });

        // Kích hoạt tải dữ liệu khách hàng lần đầu khi mở màn hình
        viewModel.fetchCustomers();
    }

    /**
     * Tính toán và hiển thị các chỉ số KPI tổng quan trên đầu màn hình
     * (Tổng số khách hàng & Số khách hàng đã phát sinh điểm thưởng).
     * Được gọi mỗi khi danh sách allCustomers được cập nhật từ ViewModel.
     */
    private void updateSummaryKpis() {
        int total = allCustomers.size();
        int withPoints = 0;
        for (Customer c : allCustomers) {
            if (c.getPointBalance() > 0 || c.getPointEarnedTotal() > 0) {
                withPoints++;
            }
        }
        binding.tvStatTotalCustomers.setText(String.valueOf(total));
        binding.tvStatPointsCustomers.setText(String.valueOf(withPoints));
    }

    /**
     * Cập nhật chỉ số KPI "Khách hàng mới" từ Map thống kê trả về của API.
     * Hỗ trợ đọc nhiều tên key (camelCase / PascalCase) để tương thích với các phiên bản response Backend.
     */
    private void updateSummaryKpisFromStats(Map<String, Object> stats) {
        Object newCountObj = stats.get("newCustomers");
        if (newCountObj == null) newCountObj = stats.get("NewCustomers");
        if (newCountObj == null) newCountObj = stats.get("newCustomersCount");
        if (newCountObj instanceof Number) {
            binding.tvStatNewCustomers.setText(String.valueOf(((Number) newCountObj).intValue()));
        }
    }

    /**
     * Lọc danh sách khách hàng trên client (in-memory) kết hợp giữa chế độ Chip (ALL / POINTS / VIP)
     * và từ khóa tìm kiếm (theo tên, SĐT, email, hạng thẻ), sau đó đổ dữ liệu vào CustomerAdapter.
     */
    private void applyFilters() {
        List<Customer> filtered = new ArrayList<>();
        String q = currentSearch.toLowerCase(Locale.ROOT);

        for (Customer c : allCustomers) {
            // Bước 1: Lọc theo tiêu chí Chip đang chọn (có điểm thưởng hoặc đạt hạng VIP / tích lũy >= 500 điểm)
            if ("POINTS".equals(currentChipMode)) {
                if (c.getPointBalance() <= 0 && c.getPointEarnedTotal() <= 0) continue;
            } else if ("VIP".equals(currentChipMode)) {
                String tier = c.getTierName();
                boolean isHighTier = "Kim Cương".equalsIgnoreCase(tier)
                        || "Vàng".equalsIgnoreCase(tier)
                        || "Hạng VIP".equalsIgnoreCase(tier)
                        || "Khách sỉ".equalsIgnoreCase(tier)
                        || "Đối tác".equalsIgnoreCase(tier)
                        || c.getPointEarnedTotal() >= 500;
                if (!isHighTier) continue;
            }

            // Bước 2: Lọc theo từ khóa tìm kiếm đa trường (Tên, SĐT, Email, Hạng thẻ)
            if (!q.isEmpty()) {
                String name = c.getCustomerName() != null ? c.getCustomerName().toLowerCase(Locale.ROOT) : "";
                String phone = c.getPhone() != null ? c.getPhone().toLowerCase(Locale.ROOT) : "";
                String email = c.getEmail() != null ? c.getEmail().toLowerCase(Locale.ROOT) : "";
                String tier = c.getTierName() != null ? c.getTierName().toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !phone.contains(q) && !email.contains(q) && !tier.contains(q)) {
                    continue;
                }
            }
            filtered.add(c);
        }

        // Cập nhật danh sách lên RecyclerView và chuyển đổi trạng thái Empty View nếu không có kết quả
        adapter.setList(filtered);
        if (filtered.isEmpty()) {
            binding.rvCustomers.setVisibility(View.GONE);
            binding.layoutEmptyCustomers.setVisibility(View.VISIBLE);
        } else {
            binding.layoutEmptyCustomers.setVisibility(View.GONE);
            binding.rvCustomers.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Mở trình quay số điện thoại của hệ điều hành (ACTION_DIAL) với SĐT của khách hàng.
     * Kích hoạt khi bấm nút gọi nhanh trên dòng khách hàng hoặc trong menu thao tác.
     */
    private void dialCustomer(Customer customer) {
        String phone = customer.getPhone();
        if (phone == null || phone.trim().isEmpty()) {
            AppToast.show(this, "Khách hàng chưa cập nhật số điện thoại!", AppToast.Type.WARNING);
            return;
        }
        // Đóng gói Implicit Intent chuyển sang ứng dụng Điện thoại mặc định của Android
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim()));
        startActivity(intent);
    }

    /**
     * Mở ứng dụng nhắn tin SMS của thiết bị với nội dung mẫu thông báo số dư điểm tích lũy hiện tại.
     * Kích hoạt khi chọn thao tác "Nhắn tin SMS" trong ActionSheet của khách hàng.
     */
    private void smsCustomer(Customer customer) {
        String phone = customer.getPhone();
        if (phone == null || phone.trim().isEmpty()) {
            AppToast.show(this, "Khách hàng chưa cập nhật số điện thoại!", AppToast.Type.WARNING);
            return;
        }
        // Đóng gói Intent mở trình nhắn tin SMS kèm nội dung soạn sẵn (sms_body)
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + phone.trim()));
        intent.putExtra("sms_body", "Chào " + customer.getCustomerName() + ", Siêu thị EMarket thông báo điểm tích lũy hiện tại của Quý khách là " + customer.getPointBalance() + " điểm.");
        startActivity(intent);
    }

    /**
     * Hiển thị BottomSheet chi tiết Điểm thưởng & Lịch sử Tích / Tiêu điểm (giống 100% Flutter).
     * Kích hoạt khi người dùng bấm vào huy hiệu điểm thưởng trên item hoặc chọn từ menu thao tác.
     * Cơ chế: Hiển thị ngay dữ liệu lịch sử điểm đã cache trong ViewModel, đồng thời gọi API làm mới ngầm.
     */
    private void showLoyaltyHistorySheet(final Customer customer) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (18 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(getColor(R.color.surface));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("⭐ Chi tiết Điểm thưởng & Hạng thẻ");
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(getColor(R.color.text_primary));
        root.addView(tvTitle);

        // Tính phần trăm chiết khấu theo hạng thẻ (discountRate * 100) để hiển thị phụ đề
        TextView tvSub = new TextView(this);
        tvSub.setText(customer.getCustomerName() + " • Hạng " + customer.getTierName()
                + " (Ưu đãi -" + (int) Math.round(customer.getDiscountRate() * 100) + "%)");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(getColor(R.color.primary));
        tvSub.setTypeface(null, Typeface.BOLD);
        tvSub.setPadding(0, (int) (4 * density), 0, (int) (12 * density));
        root.addView(tvSub);

        // 2 KPI Summary Boxes (Số dư khả dụng vs Tổng tích lũy)
        NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
        LinearLayout kpiRow = new LinearLayout(this);
        kpiRow.setOrientation(LinearLayout.HORIZONTAL);
        kpiRow.setWeightSum(2f);

        LinearLayout boxBalance = new LinearLayout(this);
        boxBalance.setOrientation(LinearLayout.VERTICAL);
        boxBalance.setBackgroundResource(R.drawable.bg_badge_pending);
        boxBalance.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd((int) (6 * density));
        boxBalance.setLayoutParams(lp1);

        TextView tvBalLabel = new TextView(this);
        tvBalLabel.setText("Số dư điểm khả dụng");
        tvBalLabel.setTextSize(11f);
        tvBalLabel.setTextColor(getColor(R.color.text_secondary));
        boxBalance.addView(tvBalLabel);

        final TextView tvBalValue = new TextView(this);
        tvBalValue.setText("⭐ " + nf.format(customer.getPointBalance()) + " điểm");
        tvBalValue.setTextSize(16f);
        tvBalValue.setTypeface(null, Typeface.BOLD);
        tvBalValue.setTextColor(getColor(R.color.on_warning_container));
        boxBalance.addView(tvBalValue);

        LinearLayout boxLifetime = new LinearLayout(this);
        boxLifetime.setOrientation(LinearLayout.VERTICAL);
        boxLifetime.setBackgroundResource(R.drawable.bg_badge_success);
        boxLifetime.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMarginStart((int) (6 * density));
        boxLifetime.setLayoutParams(lp2);

        TextView tvLifeLabel = new TextView(this);
        tvLifeLabel.setText("Tổng điểm tích lũy");
        tvLifeLabel.setTextSize(11f);
        tvLifeLabel.setTextColor(getColor(R.color.text_secondary));
        boxLifetime.addView(tvLifeLabel);

        final TextView tvLifeValue = new TextView(this);
        tvLifeValue.setText("🏆 " + nf.format(customer.getPointEarnedTotal()) + " điểm");
        tvLifeValue.setTextSize(16f);
        tvLifeValue.setTypeface(null, Typeface.BOLD);
        tvLifeValue.setTextColor(getColor(R.color.on_success_container));
        boxLifetime.addView(tvLifeValue);

        kpiRow.addView(boxBalance);
        kpiRow.addView(boxLifetime);
        root.addView(kpiRow);

        // Conversion rule note
        TextView tvRule = new TextView(this);
        tvRule.setText("Quy tắc: Mua 1.000 ₫ = +1 điểm • Đổi 1 điểm = Giảm 100 ₫ khi thanh toán POS.");
        tvRule.setTextSize(11f);
        tvRule.setTextColor(getColor(R.color.text_secondary));
        tvRule.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
        root.addView(tvRule);

        // Section Header
        TextView tvHistHeader = new TextView(this);
        tvHistHeader.setText("Lịch sử biến động điểm thưởng");
        tvHistHeader.setTextSize(14f);
        tvHistHeader.setTypeface(null, Typeface.BOLD);
        tvHistHeader.setTextColor(getColor(R.color.text_primary));
        tvHistHeader.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
        root.addView(tvHistHeader);

        // Scrollable list container
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (260 * density));
        scrollView.setLayoutParams(scrollLp);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        root.addView(scrollView);

        // Close button
        MaterialButton btnClose = new MaterialButton(this);
        btnClose.setText("Đóng");
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (12 * density);
        btnClose.setLayoutParams(btnLp);
        btnClose.setOnClickListener(v -> sheet.dismiss());
        root.addView(btnClose);

        sheet.setContentView(root);

        // Render cached records immediately, then refresh from server
        List<LoyaltyProgram> cached = viewModel.getCachedLoyaltyForCustomer(customer.getCustomerId());
        renderLoyaltyRows(listContainer, cached, density, nf);

        // Gọi API lấy lịch sử điểm mới nhất của khách hàng từ server; nếu lỗi mạng vẫn giữ nguyên danh sách cache
        repository.getCustomerLoyalty(customer.getCustomerId(), new DataRepository.ApiCallback<List<LoyaltyProgram>>() {
            @Override
            public void onSuccess(List<LoyaltyProgram> result) {
                if (result != null && !result.isEmpty()) {
                    renderLoyaltyRows(listContainer, result, density, nf);
                }
            }

            @Override
            public void onError(String message) {
                // Keep cached view
            }
        });

        sheet.show();
    }

    /**
     * Dựng động danh sách các dòng giao dịch tích/tiêu điểm bên trong BottomSheet lịch sử điểm.
     * Đảo ngược danh sách để hiển thị giao dịch mới nhất lên đầu và tô màu xanh (+) / đỏ (-) theo biến động điểm.
     */
    private void renderLoyaltyRows(LinearLayout container, List<LoyaltyProgram> records, float density, NumberFormat nf) {
        container.removeAllViews();
        if (records == null || records.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Khách hàng chưa có giao dịch tích/tiêu điểm nào.");
            tvEmpty.setTextSize(13f);
            tvEmpty.setTextColor(getColor(R.color.text_secondary));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, (int) (32 * density), 0, (int) (32 * density));
            container.addView(tvEmpty);
            return;
        }

        List<LoyaltyProgram> sorted = new ArrayList<>(records);
        Collections.reverse(sorted);

        for (LoyaltyProgram lp : sorted) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_subtle_box);
            int p = (int) (10 * density);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = (int) (6 * density);
            row.setLayoutParams(rowLp);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Xác định điểm biến động ròng (net >= 0 là tích điểm, net < 0 là đổi/trừ điểm) và tạo mô tả tương ứng
            int net = lp.getNetPoints();
            String title;
            if (lp.getDescription() != null && !lp.getDescription().trim().isEmpty()) {
                title = lp.getDescription().trim();
            } else if (lp.getOrderId() != null && lp.getOrderId() > 0) {
                title = (net >= 0 ? "Tích điểm đơn hàng #ORD-" : "Đổi điểm đơn hàng #ORD-") + lp.getOrderId();
            } else {
                title = net >= 0 ? "Cộng điểm thưởng thành viên" : "Trừ điểm đổi ưu đãi";
            }

            TextView tvDesc = new TextView(this);
            tvDesc.setText(title);
            tvDesc.setTextSize(13f);
            tvDesc.setTypeface(null, Typeface.BOLD);
            tvDesc.setTextColor(getColor(R.color.text_primary));
            left.addView(tvDesc);

            String dateStr = lp.getCreatedAt() != null ? Formatters.dateTime(lp.getCreatedAt()) : "Giao dịch POS";
            TextView tvDate = new TextView(this);
            tvDate.setText(dateStr);
            tvDate.setTextSize(11f);
            tvDate.setTextColor(getColor(R.color.text_secondary));
            left.addView(tvDate);

            TextView tvDelta = new TextView(this);
            String sign = net >= 0 ? "+" : "";
            tvDelta.setText(sign + nf.format(net) + " điểm");
            tvDelta.setTextSize(14f);
            tvDelta.setTypeface(null, Typeface.BOLD);
            tvDelta.setTextColor(getColor(net >= 0 ? R.color.success : R.color.error));

            row.addView(left);
            row.addView(tvDelta);
            container.addView(row);
        }
    }

    /**
     * Hiển thị menu BottomSheet các thao tác nghiệp vụ trên một khách hàng khi bấm vào dòng khách hàng:
     * Xem lịch sử điểm, Gọi điện, Nhắn SMS, Sửa hồ sơ, hoặc Xóa khách hàng (có kiểm tra ràng buộc toàn vẹn dữ liệu).
     */
    private void showCustomerActionSheet(final Customer customer) {
        String title = customer.getCustomerName() + " (" + customer.getTierName() + ")";
        String subtitle = "SĐT: " + (customer.getPhone() != null ? customer.getPhone() : "—")
                + " • Khả dụng: " + customer.getPointBalance() + " điểm (Tích lũy: " + customer.getPointEarnedTotal() + ")";

        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();
        actions.add(new ModernPickerSheets.ActionItem(
                "⭐ Xem chi tiết & Lịch sử điểm thưởng",
                "Tra cứu số dư khả dụng, hạng thẻ và lịch sử tích/tiêu điểm từng đơn hàng",
                R.drawable.bg_badge_pending,
                R.color.warning,
                () -> showLoyaltyHistorySheet(customer)
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "📞 Gọi điện chăm sóc khách hàng",
                "Mở trình gọi điện tới số " + (customer.getPhone() != null ? customer.getPhone() : "—"),
                R.drawable.bg_badge_success,
                R.color.success,
                () -> dialCustomer(customer)
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "💬 Nhắn tin SMS thông báo điểm thưởng",
                "Gửi tin nhắn thông báo số dư điểm tích lũy và ưu đãi hạng thẻ",
                R.drawable.bg_badge_neutral,
                R.color.primary,
                () -> smsCustomer(customer)
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "✎ Cập nhật hồ sơ khách hàng",
                "Chỉnh sửa họ tên, số điện thoại, email và nhóm khách hàng",
                R.drawable.bg_badge_neutral,
                R.color.primary,
                () -> ModernPickerSheets.showCustomerFormSheet(this, customer, () -> viewModel.fetchCustomers())
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "🗑 Xóa hồ sơ khách hàng",
                customer.getPointEarnedTotal() > 0
                        ? "Khách hàng đã có lịch sử tích điểm (" + customer.getPointEarnedTotal() + " đ) — không thể xóa"
                        : "Xóa khách hàng chưa phát sinh giao dịch khỏi danh bạ CRM",
                R.drawable.bg_badge_error,
                R.color.error,
                () -> {
                    // Ràng buộc nghiệp vụ: Chặn xóa khách hàng đã từng tích điểm để bảo toàn khóa ngoại với lịch sử hóa đơn POS
                    if (customer.getPointEarnedTotal() > 0 || customer.getPointBalance() > 0) {
                        AppToast.show(CustomersActivity.this,
                                "Không thể xóa khách hàng đã có điểm tích lũy để bảo toàn lịch sử hóa đơn!",
                                AppToast.Type.WARNING);
                        return;
                    }
                    // Gọi API xóa hồ sơ khách hàng và tải lại danh sách khi thành công
                    repository.deleteCustomer(customer.getCustomerId(), new DataRepository.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            AppToast.show(CustomersActivity.this, "Đã xóa hồ sơ khách hàng!", AppToast.Type.SUCCESS);
                            viewModel.fetchCustomers();
                        }

                        @Override
                        public void onError(String message) {
                            AppToast.show(CustomersActivity.this, message, AppToast.Type.ERROR);
                        }
                    });
                }
        ));

        ModernPickerSheets.showActionSheet(this, title, subtitle, actions);
    }
}
