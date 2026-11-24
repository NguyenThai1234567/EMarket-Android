package com.example.do_an_di_dong.ui.dashboard;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivitySupplierDebtBinding;
import com.example.do_an_di_dong.databinding.DialogSupplierDebtEditBinding;
import com.example.do_an_di_dong.databinding.DialogSupplierPaymentBinding;
import com.example.do_an_di_dong.model.DebtSummary;
import com.example.do_an_di_dong.model.SupplierDebt;
import com.example.do_an_di_dong.model.SupplierPayment;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Quản lý Công nợ Nhà cung cấp (Supplier Debt) và Danh mục Nhà cung cấp.
 * Cho phép xem thống kê nợ, lọc theo NCC/trạng thái, thanh toán nợ (kèm upload ảnh chứng từ),
 * gia hạn ngày đáo hạn và xem lịch sử trả nợ.
 * Gắn với layout {@code activity_supplier_debt.xml}, {@link SupplierDebtViewModel}, {@link SupplierDebtAdapter} và {@link DataRepository}.
 */
public class SupplierDebtActivity extends AppCompatActivity implements SupplierDebtAdapter.OnDebtActionListener {

    private ActivitySupplierDebtBinding binding;
    private SupplierDebtViewModel viewModel;
    private SupplierDebtAdapter adapter;
    private DataRepository repository;

    private Integer selectedSupplierId = null;
    private String currentStatusFilter = null;

    private ActivityResultLauncher<String> pickProofLauncher;
    private Uri pendingProofUri = null;
    private ImageView activeProofPreview = null;
    private TextView activeProofStatus = null;

    /**
     * Khởi tạo Activity, đăng ký ActivityResultLauncher chọn ảnh chứng từ thanh toán,
     * thiết lập giao diện, đăng ký LiveData observers và tải dữ liệu công nợ ban đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySupplierDebtBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        viewModel = new ViewModelProvider(this).get(SupplierDebtViewModel.class);

        // Launcher nhận URI ảnh chứng từ từ thư viện thiết bị để preview và chờ upload khi xác nhận thanh toán
        pickProofLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pendingProofUri = uri;
                        if (activeProofPreview != null) {
                            activeProofPreview.setPadding(0, 0, 0, 0);
                            Glide.with(this).load(uri).centerCrop().into(activeProofPreview);
                        }
                        if (activeProofStatus != null) {
                            activeProofStatus.setText("Đã đính kèm ảnh chứng từ (Sẽ tải lên khi xác nhận)");
                        }
                    }
                }
        );

        setupViews();
        setupObservers();

        viewModel.fetchDebts();
    }

    /**
     * Thiết lập sự kiện cho các nút điều hướng, phân quyền hiển thị theo role Supplier,
     * cấu hình RecyclerView phân trang (infinite scroll), ô tìm kiếm có debounce và bộ lọc Chip trạng thái.
     */
    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> {
            if (isTaskRoot()) {
                startActivity(new android.content.Intent(this, com.example.do_an_di_dong.ui.profile.ProfileActivity.class));
            } else {
                finish();
            }
        });
        binding.btnRefresh.setOnClickListener(v -> viewModel.fetchDebts());

        binding.btnManageSuppliers.setOnClickListener(v -> showSupplierManagementActions());

        // Phân quyền RBAC: Nếu tài khoản đăng nhập là Nhà cung cấp (Supplier), ẩn chức năng quản lý & chọn lọc NCC khác
        if (com.example.do_an_di_dong.util.UserSessionManager.isSupplier(this)) {
            binding.tvSupplierDebtTitle.setText("Công nợ của tôi");
            binding.btnManageSuppliers.setVisibility(View.GONE);
            binding.layoutSupplierPickerRow.setVisibility(View.GONE);
        }

        binding.btnSelectSupplierFilter.setOnClickListener(v -> {
            ModernPickerSheets.showSupplierPicker(this, supplier -> {
                selectedSupplierId = supplier.getSupplierId();
                binding.btnSelectSupplierFilter.setText("NCC: " + supplier.getName() + " ▾");
                binding.btnClearSupplierFilter.setVisibility(View.VISIBLE);
                viewModel.setFilters(selectedSupplierId, currentStatusFilter);
            });
        });

        binding.btnClearSupplierFilter.setOnClickListener(v -> {
            selectedSupplierId = null;
            binding.btnSelectSupplierFilter.setText("Nhà phân phối: Tất cả NCC ▾");
            binding.btnClearSupplierFilter.setVisibility(View.GONE);
            viewModel.setFilters(null, currentStatusFilter);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvDebts.setLayoutManager(layoutManager);
        adapter = new SupplierDebtAdapter(this);
        binding.rvDebts.setAdapter(adapter);

        // Tự động tải trang công nợ tiếp theo khi cuộn gần cuối danh sách
        binding.rvDebts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    int visible = layoutManager.getChildCount();
                    int total = layoutManager.getItemCount();
                    int first = layoutManager.findFirstVisibleItemPosition();
                    if ((visible + first) >= total - 4 && first >= 0 && total >= 15) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        binding.swipeRefreshDebts.setOnRefreshListener(() -> viewModel.fetchDebts());

        // Search text watcher with debounce for Vietnamese typing
        final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] searchRunnable = new Runnable[1];

        binding.etSearchDebt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable[0] != null) {
                    searchHandler.removeCallbacks(searchRunnable[0]);
                }
                searchRunnable[0] = () -> viewModel.setSearchKeyword(s.toString().trim());
                searchHandler.postDelayed(searchRunnable[0], 350);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Chip group filter (preserves selectedSupplierId!)
        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipUnpaid) {
                currentStatusFilter = "Unpaid";
            } else if (id == R.id.chipPartial) {
                currentStatusFilter = "Partial";
            } else if (id == R.id.chipPaid) {
                currentStatusFilter = "Paid";
            } else {
                currentStatusFilter = null;
            }
            viewModel.setFilters(selectedSupplierId, currentStatusFilter);
        });
    }

    /**
     * Hiển thị BottomSheet menu quản lý danh mục Nhà cung cấp (Thêm mới, Chỉnh sửa, Xóa NCC).
     * Kích hoạt khi bấm nút "Quản lý NCC" trên thanh công cụ.
     */
    private void showSupplierManagementActions() {
        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();
        actions.add(new ModernPickerSheets.ActionItem(
                "+ Thêm Nhà phân phối / NCC mới",
                "Tạo hồ sơ nhà cung cấp mới vào hệ thống",
                R.drawable.bg_badge_success,
                R.color.success_text,
                () -> ModernPickerSheets.showSupplierFormSheet(this, null, () -> viewModel.fetchDebts())
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "Chỉnh sửa thông tin Nhà cung cấp",
                "Chọn nhà cung cấp từ danh sách để cập nhật SĐT, địa chỉ, MST",
                R.drawable.bg_badge_info,
                R.color.primary,
                () -> ModernPickerSheets.showSupplierPicker(this, supplier ->
                        ModernPickerSheets.showSupplierFormSheet(this, supplier, () -> viewModel.fetchDebts())
                )
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "Xóa Nhà cung cấp",
                "Chỉ xóa được nhà cung cấp chưa có sản phẩm hoặc chứng từ liên kết",
                R.drawable.bg_badge_error,
                R.color.error,
                () -> ModernPickerSheets.showSupplierDeletePicker(this, () -> viewModel.fetchDebts())
        ));
        ModernPickerSheets.showActionSheet(
                this,
                "Quản lý Danh mục Nhà Cung Cấp",
                "Thêm mới, chỉnh sửa hoặc xóa nhà cung cấp cho phép xóa",
                actions
        );
    }

    /**
     * Lắng nghe các LiveData từ {@link SupplierDebtViewModel} để cập nhật danh sách công nợ,
     * thẻ thống kê tổng quan, trạng thái loading, empty state và thông báo lỗi.
     */
    private void setupObservers() {
        viewModel.getDebts().observe(this, debts -> {
            binding.swipeRefreshDebts.setRefreshing(false);
            binding.pbLoading.setVisibility(View.GONE);
            adapter.setList(debts);
        });

        viewModel.getSummary().observe(this, this::updateSummaryWidgets);

        viewModel.getLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) {
                if (!binding.swipeRefreshDebts.isRefreshing()) {
                    binding.pbLoading.setVisibility(View.VISIBLE);
                }
            } else {
                binding.pbLoading.setVisibility(View.GONE);
                binding.swipeRefreshDebts.setRefreshing(false);
            }
        });

        viewModel.getEmpty().observe(this, empty -> {
            binding.layoutEmptyState.setVisibility(Boolean.TRUE.equals(empty) ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                AppToast.show(this, error);
            }
        });
    }

    /**
     * Cập nhật các thẻ KPI tổng quan công nợ (Tổng nợ, Đã trả, Còn lại, Tỷ lệ thanh toán %) trên đầu màn hình.
     */
    private void updateSummaryWidgets(DebtSummary summary) {
        if (summary == null) return;
        binding.tvWidgetTotalDebt.setText(MoneyTextWatcher.formatCurrency(summary.getTotalDebt()));
        binding.tvWidgetTotalPaid.setText(MoneyTextWatcher.formatCurrency(summary.getTotalPaid()));
        binding.tvWidgetRemaining.setText(MoneyTextWatcher.formatCurrency(summary.getTotalRemaining()));
        binding.tvWidgetPayRate.setText(summary.getPayRate() + "%");
    }

    // =========================================================================
    // SUPPLIER DEBT ACTIONS (PAY, EDIT DUE DATE, HISTORY)
    // =========================================================================

    /**
     * Mở Dialog ghi nhận thanh toán công nợ cho một khoản nợ cụ thể.
     * Kích hoạt khi người dùng bấm nút "Thanh toán" trên từng dòng công nợ trong {@link SupplierDebtAdapter}.
     */
    @Override
    public void onPayDebt(SupplierDebt debt) {
        if (debt == null) return;
        pendingProofUri = null;

        DialogSupplierPaymentBinding dialogBinding = DialogSupplierPaymentBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(false)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
        }

        activeProofPreview = dialogBinding.ivPaymentProofPreview;
        activeProofStatus = dialogBinding.tvPaymentProofStatus;

        dialogBinding.tvDialogSupplierName.setText("NCC: " + (debt.getSupplierName() != null ? debt.getSupplierName() : ""));
        final double remaining = debt.getUnpaidAmount();
        dialogBinding.tvDialogRemainingDebt.setText(MoneyTextWatcher.formatCurrency(remaining));

        dialogBinding.etPaymentAmount.addTextChangedListener(new MoneyTextWatcher(dialogBinding.etPaymentAmount));
        if (remaining > 0) {
            dialogBinding.etPaymentAmount.setText(String.valueOf((long) remaining));
        }

        // Full Pay Hint Button
        dialogBinding.btnFullPay.setOnClickListener(v -> {
            dialogBinding.etPaymentAmount.setText(String.valueOf((long) remaining));
        });

        dialogBinding.btnPickPaymentProof.setOnClickListener(v -> pickProofLauncher.launch("image/*"));
        dialogBinding.layoutAttachPaymentProof.setOnClickListener(v -> pickProofLauncher.launch("image/*"));

        dialogBinding.btnCancelPayment.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnConfirmPayment.setOnClickListener(v -> {
            if (remaining <= 0) {
                dialogBinding.tilPaymentAmount.setError("Công nợ đã được thanh toán đủ");
                return;
            }
            // Kiểm tra số tiền thanh toán phải > 0 và không vượt quá số nợ còn lại (cộng sai số làm tròn 0.01)
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .greaterThanZero(dialogBinding.tilPaymentAmount, dialogBinding.etPaymentAmount, "Số tiền thanh toán")
                    .custom(dialogBinding.tilPaymentAmount, dialogBinding.etPaymentAmount, val -> {
                        double amt = MoneyTextWatcher.parseAmount(dialogBinding.etPaymentAmount);
                        return amt > remaining + 0.01
                                ? "Số tiền trả vượt quá nợ còn lại (" + MoneyTextWatcher.formatCurrency(remaining) + ")"
                                : null;
                    })
                    .validate();
            if (!valid) {
                return;
            }
            double amount = MoneyTextWatcher.parseAmount(dialogBinding.etPaymentAmount);

            final String method = dialogBinding.rbCash.isChecked() ? "Cash" : "BankTransfer";
            final String note = dialogBinding.etPaymentNote.getText() != null ? dialogBinding.etPaymentNote.getText().toString().trim() : "";

            dialogBinding.btnConfirmPayment.setEnabled(false);
            dialogBinding.btnConfirmPayment.setText("Đang xử lý...");

            final DataRepository.ApiCallback<Boolean> paymentCallback = new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    dialog.dismiss();
                    AppToast.show(SupplierDebtActivity.this, "Đã ghi nhận thanh toán công nợ thành công!");
                }

                @Override
                public void onError(String message) {
                    dialogBinding.btnConfirmPayment.setEnabled(true);
                    dialogBinding.btnConfirmPayment.setText("Xác nhận trả nợ");
                    dialogBinding.tilPaymentAmount.setError("Thất bại: " + message);
                }
            };

            // Nếu có đính kèm ảnh chứng từ thì upload ảnh lên server lấy URL trước, sau đó mới gọi API tạo phiếu thanh toán
            if (pendingProofUri != null) {
                repository.uploadDebtProof(pendingProofUri, debt != null ? debt.getDebtId() : 0, new DataRepository.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String uploadedUrl) {
                        SupplierPayment payment = new SupplierPayment();
                        payment.setDebtId(debt.getDebtId());
                        payment.setAmount(amount);
                        payment.setPaymentMethod(method);
                        payment.setPaymentProof(uploadedUrl);
                        viewModel.createSupplierPayment(payment, paymentCallback);
                    }

                    @Override
                    public void onError(String message) {
                        dialogBinding.btnConfirmPayment.setEnabled(true);
                        dialogBinding.btnConfirmPayment.setText("Xác nhận trả nợ");
                        AppToast.show(SupplierDebtActivity.this, "Lỗi tải ảnh chứng từ: " + message);
                    }
                });
            } else {
                SupplierPayment payment = new SupplierPayment();
                payment.setDebtId(debt.getDebtId());
                payment.setAmount(amount);
                payment.setPaymentMethod(method);
                payment.setPaymentProof(note);
                viewModel.createSupplierPayment(payment, paymentCallback);
            }
        });

        dialog.show();
    }

    /**
     * Mở Dialog cập nhật/gia hạn ngày đáo hạn thanh toán (Due Date) cho khoản công nợ.
     * Kích hoạt khi người dùng bấm nút sửa hạn nợ trên item của {@link SupplierDebtAdapter}.
     */
    @Override
    public void onEditDueDate(SupplierDebt debt) {
        if (debt == null) return;

        DialogSupplierDebtEditBinding editBinding = DialogSupplierDebtEditBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(editBinding.getRoot())
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
        }

        editBinding.tvEditDialogSupplier.setText("NCC: " + (debt.getSupplierName() != null ? debt.getSupplierName() : ""));
        editBinding.etEditDueDate.setText(debt.getDueDate() != null ? debt.getDueDate() : "");

        Calendar calendar = Calendar.getInstance();
        editBinding.etEditDueDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                editBinding.etEditDueDate.setText(sdf.format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        editBinding.btnCancelEdit.setOnClickListener(v -> dialog.dismiss());

        editBinding.btnConfirmEdit.setOnClickListener(v -> {
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .isoDate(editBinding.tilEditDueDate, editBinding.etEditDueDate, "Ngày đáo hạn", true)
                    .validate();
            if (!valid) {
                return;
            }
            String newDueDate = com.example.do_an_di_dong.util.FormValidator.textOf(editBinding.etEditDueDate);

            debt.setDueDate(newDueDate);
            editBinding.btnConfirmEdit.setEnabled(false);
            editBinding.btnConfirmEdit.setText("Đang lưu...");

            // Gọi API cập nhật hạn thanh toán công nợ thông qua ViewModel
            viewModel.updateSupplierDebt(debt, new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    dialog.dismiss();
                    AppToast.show(SupplierDebtActivity.this, "Cập nhật hạn nợ thành công!");
                }

                @Override
                public void onError(String message) {
                    editBinding.btnConfirmEdit.setEnabled(true);
                    editBinding.btnConfirmEdit.setText("Lưu thay đổi");
                    editBinding.tilEditDueDate.setError("Lỗi: " + message);
                }
            });
        });

        dialog.show();
    }

    /**
     * Tải danh sách lịch sử các lần thanh toán của một khoản nợ khi người dùng bấm mở rộng "Lịch sử trả" trên từng dòng công nợ.
     */
    @Override
    public void onLoadHistory(SupplierDebt debt, SupplierDebtAdapter.DebtHistoryCallback callback) {
        if (debt == null) return;
        viewModel.getPaymentsByDebtId(debt.getDebtId(), new DataRepository.ApiCallback<List<SupplierPayment>>() {
            @Override
            public void onSuccess(List<SupplierPayment> result) {
                callback.onHistoryLoaded(result);
            }

            @Override
            public void onError(String message) {
                callback.onHistoryError(message);
            }
        });
    }
}
