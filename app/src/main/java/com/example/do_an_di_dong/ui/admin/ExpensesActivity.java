package com.example.do_an_di_dong.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityExpensesBinding;
import com.example.do_an_di_dong.model.Expense;
import com.example.do_an_di_dong.model.ExpenseCategory;
import com.example.do_an_di_dong.model.Order;
import com.example.do_an_di_dong.model.OrderPagedResponse;
import com.example.do_an_di_dong.ui.common.AppToast;
import com.example.do_an_di_dong.ui.common.ModernConfirmSheet;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.util.CashReconciliationStore;
import com.example.do_an_di_dong.util.FormValidator;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Quản lý Sổ quỹ & Chi phí vận hành (ExpensesActivity).
 * Gắn với layout activity_expenses.xml, phối hợp cùng ExpensesViewModel, ExpenseAdapter, DataRepository
 * và CashReconciliationStore để thực hiện: xem/lọc danh sách phiếu chi, lập phiếu chi mới, tạo hạng mục chi phí,
 * phê duyệt/từ chối/xóa phiếu chi và đối soát két tiền mặt cuối ca (Cash Reconciliation).
 */
public class ExpensesActivity extends AppCompatActivity {

    private ActivityExpensesBinding binding;
    private ExpensesViewModel viewModel;
    private ExpenseAdapter adapter;
    private DataRepository repository;
    private double approvedCashOnlyAmount = 0;
    private final List<ExpenseCategory> cachedCategories = new ArrayList<>();

    /**
     * Khởi tạo màn hình, ánh xạ ViewBinding, thiết lập RecyclerView, gắn sự kiện cho các nút/chip lọc,
     * đăng ký observe LiveData từ ExpensesViewModel và kích hoạt tải danh mục + danh sách phiếu chi ban đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExpensesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        viewModel = new ViewModelProvider(this).get(ExpensesViewModel.class);

        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new ExpenseAdapter();
        adapter.setOnExpenseClickListener(this::showExpenseActionsSheet);

        binding.rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        binding.rvExpenses.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.fetchExpenses());

        binding.fabCreateExpense.setOnClickListener(v -> showCreateExpenseSheet());
        binding.btnCashReconciliation.setOnClickListener(v -> showCashReconciliationSheet());
        binding.btnReconHistoryHeader.setOnClickListener(v -> showReconciliationHistorySheet());

        // Wire Status Filter Chips (All / Approved / Pending / Rejected)
        binding.chipGroupStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipApproved) {
                viewModel.setFilter(null, null, "Approved");
            } else if (checkedId == R.id.chipPending) {
                viewModel.setFilter(null, null, "Pending");
            } else if (checkedId == R.id.chipRejected) {
                viewModel.setFilter(null, null, "Rejected");
            } else {
                viewModel.setFilter(null, null, null);
            }
        });

        // Lắng nghe danh sách phiếu chi từ ViewModel để cập nhật RecyclerView và tính lại tổng tiền mặt đã chi trong ngày
        viewModel.getExpenses().observe(this, list -> {
            binding.swipeRefresh.setRefreshing(false);
            adapter.setExpenses(list);
            binding.layoutEmpty.setVisibility((list == null || list.isEmpty()) ? View.VISIBLE : View.GONE);
            binding.rvExpenses.setVisibility((list == null || list.isEmpty()) ? View.GONE : View.VISIBLE);
            approvedCashOnlyAmount = calculateTodayApprovedCashExpenses(getActiveBranchFilter());
            recalculateLocalSummaryFallback();
        });

        // Lắng nghe số liệu thống kê từ API Summary; nếu API trả về 0 hoặc rỗng thì dùng hàm cộng tổng cục bộ làm fallback
        viewModel.getSummary().observe(this, summary -> {
            approvedCashOnlyAmount = calculateTodayApprovedCashExpenses(getActiveBranchFilter());
            if (summary != null && (summary.getTotalApprovedAmount() > 0 || summary.getTotalPendingAmount() > 0 || summary.getTotalRejectedAmount() > 0)) {
                binding.tvApprovedAmount.setText(Formatters.currency(summary.getTotalApprovedAmount()));
                binding.tvPendingAmount.setText(Formatters.currency(summary.getTotalPendingAmount()));
                binding.tvRejectedAmount.setText(Formatters.currency(summary.getTotalRejectedAmount()));
            } else {
                recalculateLocalSummaryFallback();
            }
        });

        viewModel.getLoading().observe(this, isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) {
                binding.swipeRefresh.setRefreshing(true);
            }
        });

        viewModel.getError().observe(this, msg -> {
            binding.swipeRefresh.setRefreshing(false);
            if (msg != null && !msg.isEmpty()) {
                AppToast.show(this, msg, AppToast.Type.ERROR);
            }
        });

        loadExpenseCategories(null);
        viewModel.fetchExpenses();
    }

    /**
     * Lấy ID chi nhánh hiện tại từ phiên đăng nhập (UserSessionManager) để lọc dữ liệu theo chi nhánh.
     * Trả về null nếu người dùng không bị giới hạn ở một chi nhánh cụ thể (branchId <= 0).
     */
    private Integer getActiveBranchFilter() {
        int branchId = UserSessionManager.getCurrentBranchId(this);
        return branchId > 0 ? branchId : null;
    }

    /**
     * Tính tổng phiếu chi TIỀN MẶT đã duyệt trong NGÀY HÔM NAY (không lấy nhầm tổng lịch sử nhiều ngày).
     */
    private double calculateTodayApprovedCashExpenses(Integer branchFilter) {
        List<Expense> all = viewModel.getAllFetchedExpenses();
        double cashAppSum = 0;
        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        for (Expense e : all) {
            if (branchFilter != null && e.getBranchId() > 0 && e.getBranchId() != branchFilter) {
                continue;
            }
            String st = e.getStatus() != null ? e.getStatus().trim() : "Pending";
            if ("Approved".equalsIgnoreCase(st)) {
                String pm = e.getPaymentMethod();
                // Chỉ khấu trừ vào két tiền mặt các khoản chi bằng Tiền mặt (Cash) phát sinh đúng trong ngày hôm nay
                boolean isCash = pm == null || pm.isEmpty() || "Cash".equalsIgnoreCase(pm) || "Tiền mặt".equalsIgnoreCase(pm);
                String dateStr = e.getExpenseDate();
                boolean isToday = dateStr != null && dateStr.startsWith(todayPrefix);
                if (isCash && isToday) {
                    cashAppSum += e.getAmount();
                }
            }
        }
        return cashAppSum;
    }

    /**
     * Tính toán lại tổng tiền theo 3 nhóm trạng thái (Approved / Pending / Rejected) trực tiếp từ danh sách phiếu chi đã tải.
     * Đóng vai trò cơ chế dự phòng (fallback) để đảm bảo 3 thẻ KPI luôn hiển thị đúng số liệu ngay cả khi API summary chưa phản hồi.
     */
    private void recalculateLocalSummaryFallback() {
        List<Expense> all = viewModel.getAllFetchedExpenses();
        double appSum = 0, pendSum = 0, rejSum = 0;

        for (Expense e : all) {
            String st = e.getStatus() != null ? e.getStatus().trim() : "Pending";
            if ("Approved".equalsIgnoreCase(st)) {
                appSum += e.getAmount();
            } else if ("Rejected".equalsIgnoreCase(st)) {
                rejSum += e.getAmount();
            } else {
                pendSum += e.getAmount();
            }
        }
        approvedCashOnlyAmount = calculateTodayApprovedCashExpenses(getActiveBranchFilter());
        binding.tvApprovedAmount.setText(Formatters.currency(appSum));
        binding.tvPendingAmount.setText(Formatters.currency(pendSum));
        binding.tvRejectedAmount.setText(Formatters.currency(rejSum));
    }

    /**
     * Gọi API tải danh sách hạng mục chi phí (ExpenseCategory) từ server và lưu vào bộ nhớ tạm cachedCategories.
     * Kích hoạt khi mở màn hình hoặc ngay sau khi người dùng tạo thêm hạng mục chi phí mới.
     */
    private void loadExpenseCategories(final Runnable onLoaded) {
        repository.getAllExpenseCategories(new DataRepository.ApiCallback<List<ExpenseCategory>>() {
            @Override
            public void onSuccess(List<ExpenseCategory> result) {
                cachedCategories.clear();
                if (result != null) {
                    cachedCategories.addAll(result);
                }
                if (onLoaded != null) onLoaded.run();
            }

            @Override
            public void onError(String message) {
                if (onLoaded != null) onLoaded.run();
            }
        });
    }

    /**
     * Hiển thị BottomSheet tạo phiếu chi mới (sheet_add_expense.xml) khi người dùng bấm nút FAB tạo phiếu chi.
     * Hỗ trợ chọn hạng mục động từ API (hoặc fallback các chip mặc định), chọn nhanh mệnh giá tiền, kiểm tra hợp lệ
     * và gửi request tạo phiếu chi lên DataRepository.
     */
    private void showCreateExpenseSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_add_expense, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        final ChipGroup chipGroupCat = view.findViewById(R.id.chipGroupExpenseCategory);
        TextInputLayout tilTitle = view.findViewById(R.id.tilExpenseTitle);
        TextInputLayout tilAmount = view.findViewById(R.id.tilExpenseAmount);
        final TextInputEditText etTitle = view.findViewById(R.id.etExpenseTitle);
        TextInputEditText etAmount = view.findViewById(R.id.etExpenseAmount);
        TextInputEditText etNote = view.findViewById(R.id.etExpenseNote);

        View btn100k = view.findViewById(R.id.btnQuick100k);
        View btn200k = view.findViewById(R.id.btnQuick200k);
        View btn500k = view.findViewById(R.id.btnQuick500k);
        View btn1M = view.findViewById(R.id.btnQuick1M);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancelExpense);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitExpense);

        final int[] selectedCategoryId = new int[]{1};

        // Populate categories dynamically from API if available, plus "+ Thêm hạng mục" chip
        final Runnable populateCategoryChips = () -> {
            if (!cachedCategories.isEmpty()) {
                chipGroupCat.removeAllViews();
                for (int i = 0; i < cachedCategories.size(); i++) {
                    final ExpenseCategory cat = cachedCategories.get(i);
                    Chip chip = new Chip(this);
                    chip.setText(cat.getName() != null ? cat.getName() : ("Danh mục #" + cat.getCategoryId()));
                    chip.setCheckable(true);
                    if (i == 0) {
                        chip.setChecked(true);
                        selectedCategoryId[0] = cat.getCategoryId();
                        etTitle.setText("Chi phí " + cat.getName());
                    }
                    chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            selectedCategoryId[0] = cat.getCategoryId();
                            etTitle.setText("Chi phí " + cat.getName());
                        }
                    });
                    chipGroupCat.addView(chip);
                }

                // Add "+ Tạo danh mục mới" chip
                Chip addChip = new Chip(this);
                addChip.setText("+ Hạng mục mới");
                addChip.setCheckable(false);
                addChip.setOnClickListener(v -> promptCreateNewCategory(() -> loadExpenseCategories(() -> {
                    if (sheet.isShowing()) {
                        sheet.dismiss();
                        showCreateExpenseSheet();
                    }
                })));
                chipGroupCat.addView(addChip);
            } else {
                // Fallback khi chưa tải được danh mục từ API: sử dụng các chip cố định có sẵn trong XML
                chipGroupCat.setOnCheckedStateChangeListener((group, checkedIds) -> {
                    if (checkedIds.isEmpty()) return;
                    int id = checkedIds.get(0);
                    if (id == R.id.chipExpUtility) {
                        selectedCategoryId[0] = 1;
                        etTitle.setText("Chi phí Điện / Nước / Internet");
                    } else if (id == R.id.chipExpLogistics) {
                        selectedCategoryId[0] = 2;
                        etTitle.setText("Chi phí Vận chuyển / Giao hàng");
                    } else if (id == R.id.chipExpSupplies) {
                        selectedCategoryId[0] = 3;
                        etTitle.setText("Mua vật tư / Bao bì siêu thị");
                    } else if (id == R.id.chipExpMaintenance) {
                        selectedCategoryId[0] = 4;
                        etTitle.setText("Chi phí sửa chữa / Bảo trì thiết bị");
                    } else {
                        selectedCategoryId[0] = 5;
                        etTitle.setText("");
                    }
                });
            }
        };

        populateCategoryChips.run();

        btn100k.setOnClickListener(v -> etAmount.setText("100000"));
        btn200k.setOnClickListener(v -> etAmount.setText("200000"));
        btn500k.setOnClickListener(v -> etAmount.setText("500000"));
        btn1M.setOnClickListener(v -> etAmount.setText("1000000"));

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = FormValidator.create()
                    .minLength(tilTitle, etTitle, "Nội dung chi", 2, true)
                    .doubleRange(tilAmount, etAmount, "Số tiền chi", 1000.0, 1_000_000_000.0, true, "Số tiền chi tối thiểu 1.000 ₫")
                    .validate();
            if (!valid) {
                return;
            }

            String title = FormValidator.textOf(etTitle);
            Double parsedAmount = FormValidator.parseNumberOrNull(FormValidator.textOf(etAmount));
            double amount = parsedAmount != null ? parsedAmount : 0;
            String note = FormValidator.textOf(etNote);

            // Đóng gói payload tạo phiếu chi gắn với chi nhánh và nhân viên đang đăng nhập, gửi lên API
            int branchId = UserSessionManager.getCurrentBranchId(this);
            int userId = UserSessionManager.getCurrentUserId(this);
            String today = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

            String fullNote = note.isEmpty() ? title : (title + " — " + note);
            Map<String, Object> body = new HashMap<>();
            body.put("BranchId", branchId > 0 ? branchId : 1);
            body.put("CategoryId", selectedCategoryId[0]);
            body.put("Amount", amount);
            body.put("PaymentMethod", "Cash");
            body.put("ExpenseDate", today);
            body.put("UserId", userId > 0 ? userId : 1);
            body.put("Note", fullNote);

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Đang lưu...");

            repository.createExpense(body, new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    sheet.dismiss();
                    AppToast.show(ExpensesActivity.this, "Đã tạo phiếu chi sổ quỹ thành công!", AppToast.Type.SUCCESS);
                    viewModel.fetchExpenses();
                }

                @Override
                public void onError(String message) {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Lưu Phiếu Chi");
                    AppToast.show(ExpensesActivity.this, message, AppToast.Type.ERROR);
                }
            });
        });

        sheet.show();
    }

    /**
     * Hiển thị BottomSheet nhập tên hạng mục chi phí mới (sheet_simple_input.xml).
     * Kích hoạt khi người dùng bấm vào chip "+ Hạng mục mới" trong form tạo phiếu chi.
     * Khi tạo thành công sẽ gọi callback onCreated để tải lại danh sách hạng mục và mở lại form.
     */
    private void promptCreateNewCategory(final Runnable onCreated) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Thêm Hạng Mục Chi Phí Mới");
        tvSubtitle.setText("Tạo nhóm chi phí mới cho sổ quỹ siêu thị");
        tilField1.setHint("Tên hạng mục chi phí *");
        btnSubmit.setText("Tạo hạng mục");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = FormValidator.create()
                    .minLength(tilField1, etField1, "Tên hạng mục chi phí", 2, true)
                    .validate();
            if (!valid) {
                return;
            }
            String name = FormValidator.textOf(etField1);
            btnSubmit.setEnabled(false);
            repository.createExpenseCategory(name, "Danh mục chi phí vận hành", new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    sheet.dismiss();
                    AppToast.show(ExpensesActivity.this, "Đã tạo hạng mục: " + name, AppToast.Type.SUCCESS);
                    if (onCreated != null) onCreated.run();
                }

                @Override
                public void onError(String message) {
                    btnSubmit.setEnabled(true);
                    AppToast.show(ExpensesActivity.this, message, AppToast.Type.ERROR);
                }
            });
        });
        sheet.show();
    }

    /**
     * Hiển thị BottomSheet Đối soát quỹ tiền mặt cuối ca (sheet_cash_reconciliation.xml) khi bấm nút Đối soát két.
     * Tự động lấy quỹ đầu ca đã lưu, gọi API đơn hàng lấy doanh thu tiền mặt POS hôm nay, trừ đi tổng phiếu chi tiền mặt
     * đã duyệt trong ngày để tính Tiền mặt kỳ vọng trong két, so sánh với Tiền thực tế kiểm đếm và lưu biên bản chốt ca.
     */
    private void showCashReconciliationSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_cash_reconciliation, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        final TextView tvOpeningSummary = view.findViewById(R.id.tvReconOpeningSummary);
        final TextView tvTodayCashSales = view.findViewById(R.id.tvReconTodayCashSales);
        final TextView tvApproved = view.findViewById(R.id.tvReconApprovedExpenses);
        final TextView tvExpectedCash = view.findViewById(R.id.tvReconExpectedCash);
        final TextView tvDiffBadge = view.findViewById(R.id.tvReconDiffBadge);
        final TextInputLayout tilOpening = view.findViewById(R.id.tilReconOpeningCash);
        final TextInputEditText etOpening = view.findViewById(R.id.etReconOpeningCash);
        final TextInputLayout tilActual = view.findViewById(R.id.tilReconActualCash);
        final TextInputEditText etActual = view.findViewById(R.id.etReconActualCash);
        final View btnFillExpected = view.findViewById(R.id.btnFillExpectedCash);
        final TextInputLayout tilNotes = view.findViewById(R.id.tilReconNotes);
        final TextInputEditText etNotes = view.findViewById(R.id.etReconNotes);
        View btnViewHistory = view.findViewById(R.id.btnViewReconHistory);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelRecon);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmRecon);

        btnViewHistory.setOnClickListener(v -> showReconciliationHistorySheet());

        long savedOpening = CashReconciliationStore.getSavedOpeningCash(this);
        etOpening.setText(String.valueOf(savedOpening));

        final Integer branchFilter = getActiveBranchFilter();
        final double[] todayPosCashSales = new double[]{0.0};
        final double cashExpensesToSubtract = calculateTodayApprovedCashExpenses(branchFilter);
        approvedCashOnlyAmount = cashExpensesToSubtract;

        tvOpeningSummary.setText(Formatters.currency(savedOpening));
        tvApproved.setText("- " + Formatters.currency(cashExpensesToSubtract));
        tvTodayCashSales.setText("+ 0 ₫ (Đang tính...)");

        // Công thức đối soát két: Tiền kỳ vọng = Quỹ đầu ca + Doanh thu POS tiền mặt hôm nay - Chi phí tiền mặt đã duyệt hôm nay
        final Runnable updateDiff = () -> {
            double opening = parseDoubleSafe(etOpening.getText() != null ? etOpening.getText().toString() : "0");
            double expected = opening + todayPosCashSales[0] - cashExpensesToSubtract;

            tvOpeningSummary.setText(Formatters.currency(opening));
            tvExpectedCash.setText(Formatters.currency(expected));

            String actualText = etActual.getText() != null ? etActual.getText().toString().trim() : "";
            if (actualText.isEmpty()) {
                tvDiffBadge.setText("Nhập tiền thực tế");
                tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                tvDiffBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                return;
            }

            // Tính độ lệch (Chênh lệch = Thực tế - Kỳ vọng): khớp két nếu sai số < 1đ, dương là thừa tiền, âm là thiếu hụt
            double actual = parseDoubleSafe(actualText);
            double diff = actual - expected;

            if (Math.abs(diff) < 1) {
                tvDiffBadge.setText("✓ Khớp két 100%");
                tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_success);
                tvDiffBadge.setTextColor(ContextCompat.getColor(this, R.color.success));
            } else if (diff > 0) {
                tvDiffBadge.setText("Thừa +" + Formatters.currency(diff));
                tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_info);
                tvDiffBadge.setTextColor(ContextCompat.getColor(this, R.color.info));
            } else {
                tvDiffBadge.setText("Thiếu " + Formatters.currency(diff));
                tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_error);
                tvDiffBadge.setTextColor(ContextCompat.getColor(this, R.color.error));
            }
        };

        if (btnFillExpected != null) {
            btnFillExpected.setOnClickListener(v -> {
                double opening = parseDoubleSafe(etOpening.getText() != null ? etOpening.getText().toString() : "0");
                double expected = opening + todayPosCashSales[0] - cashExpensesToSubtract;
                long rounded = Math.max(0L, Math.round(expected));
                etActual.setText(String.valueOf(rounded));
                etActual.setSelection(etActual.getText() != null ? etActual.getText().length() : 0);
            });
        }

        // Fetch today's POS cash sales from Orders API scoped to the active branch
        final String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        repository.getOrdersPaged(0, 100, null, branchFilter, null, null, null, null, new DataRepository.ApiCallback<OrderPagedResponse>() {
            @Override
            public void onSuccess(OrderPagedResponse result) {
                double cashSales = 0;
                if (result != null && result.getData() != null) {
                    for (Order o : result.getData()) {
                        String st = o.getStatus() != null ? o.getStatus() : "";
                        boolean isCompleted = "Completed".equalsIgnoreCase(st) || "Delivered".equalsIgnoreCase(st) || "Paid".equalsIgnoreCase(st);
                        boolean isToday = o.getOrderDate() != null && o.getOrderDate().startsWith(todayPrefix);
                        String pm = o.getPaymentMethod();
                        boolean isCash = pm == null || pm.isEmpty() || "Cash".equalsIgnoreCase(pm) || "Tiền mặt".equalsIgnoreCase(pm);
                        if (isCompleted && isToday && isCash) {
                            cashSales += o.getTotalAmount();
                        }
                    }
                }
                todayPosCashSales[0] = cashSales;
                tvTodayCashSales.setText("+ " + Formatters.currency(cashSales));
                updateDiff.run();
            }

            @Override
            public void onError(String message) {
                tvTodayCashSales.setText("+ 0 ₫");
                updateDiff.run();
            }
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateDiff.run();
            }
        };
        etOpening.addTextChangedListener(watcher);
        etActual.addTextChangedListener(watcher);
        updateDiff.run();

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnConfirm.setOnClickListener(v -> {
            boolean valid = FormValidator.create()
                    .doubleRange(tilOpening, etOpening, "Quỹ tiền mặt đầu ca", 0.0, 10_000_000_000.0, true, "Quỹ đầu ca phải >= 0 ₫")
                    .doubleRange(tilActual, etActual, "Tiền mặt thực tế", 0.0, 10_000_000_000.0, true, "Vui lòng nhập tiền mặt thực tế kiểm đếm (>= 0 ₫)")
                    .custom(tilNotes, etNotes, value -> value.length() > 200 ? "Ghi chú tối đa 200 ký tự" : null)
                    .validate();
            if (!valid) {
                return;
            }

            Double openParsed = FormValidator.parseNumberOrNull(FormValidator.textOf(etOpening));
            Double actualParsed = FormValidator.parseNumberOrNull(FormValidator.textOf(etActual));
            double openingVal = openParsed != null ? openParsed : 0.0;
            double actualVal = actualParsed != null ? actualParsed : 0.0;
            double expectedVal = openingVal + todayPosCashSales[0] - cashExpensesToSubtract;
            double diffVal = actualVal - expectedVal;

            // Lưu quỹ đầu ca và biên bản đối soát vào bộ nhớ cục bộ (SharedPreferences qua CashReconciliationStore)
            CashReconciliationStore.saveOpeningCash(this, (long) openingVal);

            CashReconciliationStore.Record rec = new CashReconciliationStore.Record();
            rec.id = "CR-" + System.currentTimeMillis();
            rec.timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            String staff = UserSessionManager.getFullName(this);
            rec.staffName = (staff != null && !staff.trim().isEmpty()) ? staff.trim() : "Nhân viên";
            String branch = UserSessionManager.getCurrentBranchName(this);
            rec.branchName = (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "Chi nhánh";
            rec.openingCash = openingVal;
            rec.expectedCash = expectedVal;
            rec.actualCash = actualVal;
            rec.discrepancy = diffVal;
            String reconNote = FormValidator.textOf(etNotes);
            rec.notes = reconNote.isEmpty() ? null : reconNote;
            CashReconciliationStore.saveRecord(this, rec);

            sheet.dismiss();
            AppToast.show(this, "Đã lưu biên bản đối soát quỹ tiền mặt cuối ca!", AppToast.Type.SUCCESS);
        });

        sheet.show();
    }

    /**
     * Hiển thị BottomSheet Lịch sử đối soát két tiền mặt (sheet_cash_reconciliation_history.xml)
     * được đọc từ CashReconciliationStore trên thiết bị, cho phép xem lại hoặc xóa toàn bộ lịch sử chốt ca.
     */
    private void showReconciliationHistorySheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_cash_reconciliation_history, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        final TextView tvSubtitle = view.findViewById(R.id.tvReconHistorySubtitle);
        final TextView tvEmpty = view.findViewById(R.id.tvReconHistoryEmpty);
        final RecyclerView rv = view.findViewById(R.id.rvReconHistory);
        final MaterialButton btnClear = view.findViewById(R.id.btnClearReconHistory);
        View btnClose = view.findViewById(R.id.btnCloseReconHistory);

        rv.setLayoutManager(new LinearLayoutManager(this));
        final List<CashReconciliationStore.Record> records = new ArrayList<>(CashReconciliationStore.getHistory(this));
        final ReconciliationHistoryAdapter historyAdapter = new ReconciliationHistoryAdapter(records);
        rv.setAdapter(historyAdapter);

        final Runnable updateState = () -> {
            boolean empty = records.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
            btnClear.setVisibility(empty ? View.GONE : View.VISIBLE);
            tvSubtitle.setText(empty
                    ? "Chưa có phiên chốt ca nào được lưu trên thiết bị"
                    : ("Đã lưu " + records.size() + " phiên đối soát két gần nhất"));
        };
        updateState.run();

        btnClear.setOnClickListener(v -> ModernConfirmSheet.confirmDanger(
                this,
                "Xóa lịch sử đối soát?",
                "Thao tác này sẽ xóa toàn bộ lịch sử chốt két lưu cục bộ trên máy.",
                "Xóa hết",
                () -> {
                    CashReconciliationStore.clearHistory(this);
                    records.clear();
                    historyAdapter.notifyDataSetChanged();
                    updateState.run();
                    AppToast.show(this, "Đã xóa lịch sử đối soát két", AppToast.Type.INFO);
                }
        ));

        btnClose.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    /**
     * Adapter nội bộ hiển thị danh sách lịch sử các phiên đối soát két tiền mặt trong BottomSheet lịch sử.
     * Gắn với layout item_reconciliation_history.xml.
     */
    private class ReconciliationHistoryAdapter extends RecyclerView.Adapter<ReconciliationHistoryAdapter.VH> {
        private final List<CashReconciliationStore.Record> items;

        ReconciliationHistoryAdapter(List<CashReconciliationStore.Record> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reconciliation_history, parent, false);
            return new VH(v);
        }

        /**
         * Đổ dữ liệu một phiên chốt ca (nhân viên, thời gian, chi nhánh, tiền đầu ca/kỳ vọng/thực tế và chênh lệch) lên item view.
         */
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            CashReconciliationStore.Record r = items.get(position);
            h.tvStaff.setText(r.staffName != null ? r.staffName : "Nhân viên");
            h.tvTime.setText(r.timestamp != null ? r.timestamp : "");
            h.tvBranch.setText((r.branchName != null ? r.branchName : "Chi nhánh")
                    + " • Đầu ca: " + Formatters.currency(r.openingCash));
            h.tvExpected.setText("Kỳ vọng: " + Formatters.currency(r.expectedCash));
            h.tvActual.setText("Thực tế: " + Formatters.currency(r.actualCash));

            double diff = r.discrepancy;
            if (Math.abs(diff) < 1) {
                h.tvBadge.setText("✓ Khớp két");
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_success);
                h.tvBadge.setTextColor(ContextCompat.getColor(ExpensesActivity.this, R.color.success));
            } else if (diff > 0) {
                h.tvBadge.setText("Thừa +" + Formatters.currency(diff));
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_info);
                h.tvBadge.setTextColor(ContextCompat.getColor(ExpensesActivity.this, R.color.info));
            } else {
                h.tvBadge.setText("Thiếu " + Formatters.currency(diff));
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_error);
                h.tvBadge.setTextColor(ContextCompat.getColor(ExpensesActivity.this, R.color.error));
            }

            if (r.notes != null && !r.notes.trim().isEmpty()) {
                h.tvNotes.setVisibility(View.VISIBLE);
                h.tvNotes.setText("Ghi chú: " + r.notes.trim());
            } else {
                h.tvNotes.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvStaff;
            final TextView tvTime;
            final TextView tvBranch;
            final TextView tvExpected;
            final TextView tvActual;
            final TextView tvBadge;
            final TextView tvNotes;

            VH(@NonNull View v) {
                super(v);
                tvStaff = v.findViewById(R.id.tvReconItemStaff);
                tvTime = v.findViewById(R.id.tvReconItemTime);
                tvBranch = v.findViewById(R.id.tvReconItemBranch);
                tvExpected = v.findViewById(R.id.tvReconItemExpected);
                tvActual = v.findViewById(R.id.tvReconItemActual);
                tvBadge = v.findViewById(R.id.tvReconItemBadge);
                tvNotes = v.findViewById(R.id.tvReconItemNotes);
            }
        }
    }

    /**
     * Chuyển đổi chuỗi số tiền (có thể chứa ký tự định dạng tiền tệ) sang kiểu double an toàn, trả về 0 nếu lỗi.
     */
    private double parseDoubleSafe(String str) {
        try {
            String clean = str.replaceAll("[^0-9.]", "");
            return clean.isEmpty() ? 0 : Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Hiển thị ActionSheet các thao tác nghiệp vụ trên một phiếu chi khi người dùng bấm vào dòng phiếu chi đó.
     * Rẽ nhánh quyền thao tác theo trạng thái phiếu:
     * - Nếu Pending: cho phép Duyệt chi Tiền mặt, Duyệt chi Chuyển khoản, Từ chối hoặc Xóa.
     * - Nếu Rejected: chỉ cho phép Xóa phiếu.
     * - Nếu Approved: khóa phiếu theo quy tắc kiểm toán sổ quỹ, không cho sửa hoặc xóa.
     */
    private void showExpenseActionsSheet(final Expense expense) {
        String st = expense.getStatus() != null ? expense.getStatus().trim() : "Pending";
        String title = (expense.getCategoryName() != null && !expense.getCategoryName().isEmpty())
                ? expense.getCategoryName()
                : ("Phiếu chi #" + expense.getExpenseId());
        String rejectInfo = "";
        if ("Rejected".equalsIgnoreCase(st)) {
            String reason = expense.getRejectReason();
            String rejector = expense.getRejectorName();
            String rejectorSuffix = (rejector != null && !rejector.trim().isEmpty())
                    ? " (" + rejector.trim() + ")"
                    : "";
            if (reason != null && !reason.trim().isEmpty()) {
                rejectInfo = "\nLý do từ chối" + rejectorSuffix + ": " + reason.trim();
            } else {
                rejectInfo = "\nPhiếu chi bị từ chối" + rejectorSuffix + " (chưa có ghi chú lý do)";
            }
        }
        String subtitle = "Số tiền: " + Formatters.currency(expense.getAmount())
                + " • Trạng thái: " + ("Approved".equalsIgnoreCase(st) ? "Đã duyệt" : ("Rejected".equalsIgnoreCase(st) ? "Từ chối" : "Chờ duyệt"))
                + (expense.getNote() != null && !expense.getNote().isEmpty() ? "\nNội dung: " + expense.getNote() : "")
                + rejectInfo;

        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();

        // Rẽ nhánh trạng thái: Chỉ phiếu đang Chờ duyệt (Pending) mới được phép Phê duyệt hoặc Từ chối
        if ("Pending".equalsIgnoreCase(st)) {
            actions.add(new ModernPickerSheets.ActionItem(
                    "💵 Phê duyệt chi bằng Tiền mặt (Cash)",
                    "Duyệt phiếu chi hợp lệ và trừ vào quỹ tiền mặt tại quầy",
                    R.drawable.bg_badge_success,
                    R.color.success,
                    () -> updateStatus(expense.getExpenseId(), "Approved", null, "Cash")
            ));
            actions.add(new ModernPickerSheets.ActionItem(
                    "💳 Phê duyệt chi bằng Chuyển khoản (Transfer)",
                    "Duyệt phiếu chi hợp lệ qua tài khoản ngân hàng",
                    R.drawable.bg_badge_success,
                    R.color.primary,
                    () -> updateStatus(expense.getExpenseId(), "Approved", null, "Transfer")
            ));
            actions.add(new ModernPickerSheets.ActionItem(
                    "✕ Từ chối khoản chi (Rejected)",
                    "Từ chối thanh toán và ghi rõ lý do từ chối bắt buộc",
                    R.drawable.bg_badge_warning,
                    R.color.warning,
                    () -> promptRejectReason(expense.getExpenseId())
            ));
        }

        // Bảo toàn sổ quỹ: Chỉ cho xóa phiếu chưa duyệt/bị từ chối; phiếu Đã duyệt (Approved) bị khóa chống xóa
        if (!"Approved".equalsIgnoreCase(st)) {
            actions.add(new ModernPickerSheets.ActionItem(
                    "🗑 Xóa phiếu chi",
                    "Xóa phiếu chi chưa duyệt/bị từ chối khỏi hệ thống",
                    R.drawable.bg_badge_error,
                    R.color.error,
                    () -> ModernConfirmSheet.confirmDanger(
                            this,
                            "Xóa phiếu chi #" + expense.getExpenseId() + "?",
                            "Phiếu chi sẽ bị xóa khỏi hệ thống và không thể khôi phục.",
                            "Xóa phiếu",
                            () -> deleteExpense(expense.getExpenseId())
                    )
            ));
        } else {
            actions.add(new ModernPickerSheets.ActionItem(
                    "🔒 Phiếu chi đã được phê duyệt & hạch toán",
                    "Theo quy định kiểm toán sổ quỹ, phiếu chi Đã duyệt không thể tự ý xóa hoặc sửa trạng thái",
                    R.drawable.bg_badge_neutral,
                    R.color.text_secondary,
                    () -> AppToast.show(this, "Phiếu chi đã duyệt được khóa bảo toàn sổ quỹ!", AppToast.Type.INFO)
            ));
        }

        ModernPickerSheets.showActionSheet(this, title, subtitle, actions);
    }

    /**
     * Hiển thị BottomSheet bắt buộc nhập lý do khi quản lý chọn "Từ chối khoản chi".
     * Sau khi kiểm tra độ dài tối thiểu 3 ký tự sẽ gọi updateStatus để cập nhật trạng thái Rejected kèm lý do lên server.
     */
    private void promptRejectReason(final int expenseId) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Từ chối phiếu chi #" + expenseId);
        tvSubtitle.setText("Nhập lý do từ chối bắt buộc để thông báo cho nhân viên lập phiếu");
        tilField1.setHint("Lý do từ chối *");
        btnSubmit.setText("Xác nhận từ chối");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = FormValidator.create()
                    .minLength(tilField1, etField1, "Lý do từ chối", 3, true)
                    .validate();
            if (!valid) {
                return;
            }
            String reason = FormValidator.textOf(etField1);
            sheet.dismiss();
            updateStatus(expenseId, "Rejected", reason, null);
        });

        sheet.show();
    }

    /**
     * Gọi API qua DataRepository để cập nhật trạng thái phiếu chi (Approved hoặc Rejected) cùng phương thức thanh toán / lý do từ chối.
     * Khi thành công sẽ tự động gọi viewModel.fetchExpenses() để làm mới danh sách và các thẻ KPI.
     */
    private void updateStatus(int expenseId, String status, String rejectReason, String paymentMethod) {
        repository.updateExpenseStatus(expenseId, status, rejectReason, paymentMethod, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                AppToast.show(ExpensesActivity.this, "Đã cập nhật trạng thái phiếu chi!", AppToast.Type.SUCCESS);
                viewModel.fetchExpenses();
            }

            @Override
            public void onError(String message) {
                AppToast.show(ExpensesActivity.this, message, AppToast.Type.ERROR);
            }
        });
    }

    /**
     * Gọi API qua DataRepository để xóa một phiếu chi chưa được duyệt khỏi hệ thống.
     * Khi xóa thành công sẽ tải lại danh sách phiếu chi và thống kê qua ViewModel.
     */
    private void deleteExpense(int expenseId) {
        repository.deleteExpense(expenseId, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                AppToast.show(ExpensesActivity.this, "Đã xóa phiếu chi!", AppToast.Type.SUCCESS);
                viewModel.fetchExpenses();
            }

            @Override
            public void onError(String message) {
                AppToast.show(ExpensesActivity.this, message, AppToast.Type.ERROR);
            }
        });
    }
}
