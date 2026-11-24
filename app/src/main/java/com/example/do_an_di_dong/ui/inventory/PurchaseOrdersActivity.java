package com.example.do_an_di_dong.ui.inventory;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityPurchaseOrdersBinding;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.model.PurchaseOrder;
import com.example.do_an_di_dong.model.SupplierDTO;
import com.example.do_an_di_dong.model.Warehouse;
import com.example.do_an_di_dong.ui.common.BottomSheetHelper;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.ui.product.BarcodeScanActivity;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.FormValidator;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.QuantityInputDialog;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Quản lý Đơn nhập hàng (Purchase Orders - PO) & Lập phiếu nhập kho (Clone 1:1 của create_purchase_order_dialog.dart).
 * Hỗ trợ lọc theo Nhà cung cấp/Trạng thái, quét mã vạch sản phẩm, lập PO mới (chờ duyệt hoặc nhập kho trực tiếp),
 * xem chi tiết PO và duyệt/hủy/xóa phiếu nhập.
 * Gắn với layout {@code activity_purchase_orders.xml}, {@link PurchaseOrdersViewModel}, {@link PurchaseOrderAdapter} và {@link DataRepository}.
 */
public class PurchaseOrdersActivity extends AppCompatActivity {

    private ActivityPurchaseOrdersBinding binding;
    private PurchaseOrdersViewModel viewModel;
    private PurchaseOrderAdapter adapter;
    private DataRepository repository;

    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<SupplierDTO> suppliers = new ArrayList<>();
    private final List<ProductCategoryDTO> categories = new ArrayList<>();
    private Integer selectedFilterSupplierId = null;
    private String selectedFilterStatus = null;
    private boolean isOpeningDetail = false;

    private enum PoActionMode {
        APPROVE,
        CANCEL,
        DELETE
    }

    /**
     * Model nội bộ đại diện cho một dòng sản phẩm nháp trong BottomSheet lập đơn nhập hàng (số lượng, giá vốn, NSX, HSD).
     */
    private static class PoDraftLine {
        final Product product;
        int quantity;
        double unitPrice;
        Date manufacturingDate;
        Date expiryDate;

        /**
         * Khởi tạo dòng sản phẩm nhập nháp với NSX mặc định là hôm nay và HSD mặc định là +1 năm.
         */
        PoDraftLine(Product product, int quantity, double unitPrice) {
            this.product = product;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            Calendar now = Calendar.getInstance();
            this.manufacturingDate = now.getTime();
            Calendar exp = Calendar.getInstance();
            exp.add(Calendar.YEAR, 1);
            this.expiryDate = exp.getTime();
        }
    }

    private final List<PoDraftLine> activeDraftLines = new ArrayList<>();
    private Runnable activeDraftRefreshCallback;

    // Launcher nhận mã Barcode quét từ BarcodeScanActivity để tự động tìm và thêm sản phẩm vào phiếu nhập nháp
    private final ActivityResultLauncher<Intent> poBarcodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("scanned_barcode");
                    if (barcode != null && !barcode.trim().isEmpty()) {
                        handleScannedBarcodeForPo(barcode.trim());
                    }
                }
            });

    /**
     * Khởi tạo màn hình danh sách PO, cấu hình RecyclerView phân trang, bộ lọc NCC/Trạng thái,
     * ô tìm kiếm, lắng nghe LiveData từ ViewModel và tải trước dữ liệu danh mục tham chiếu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPurchaseOrdersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        viewModel = new ViewModelProvider(this).get(PurchaseOrdersViewModel.class);

        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new PurchaseOrderAdapter();
        adapter.setOnPurchaseOrderActionListener(this::showPurchaseOrderActionsSheet);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvPurchaseOrders.setLayoutManager(layoutManager);
        binding.rvPurchaseOrders.setAdapter(adapter);

        // Cuộn vô tận (Infinite Scroll): tự động gọi tải trang tiếp theo khi cuộn gần cuối danh sách
        binding.rvPurchaseOrders.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        binding.swipeRefreshPo.setOnRefreshListener(() -> viewModel.refreshPurchaseOrders());

        // "Lọc NCC ▾" button filters POs by Supplier (instead of opening supplier edit form!)
        binding.btnManageSuppliers.setOnClickListener(v -> {
            ModernPickerSheets.showSupplierPicker(this, supplier -> {
                selectedFilterSupplierId = supplier.getSupplierId();
                binding.btnManageSuppliers.setText("NCC: " + supplier.getName() + " ▾");
                binding.btnClearPoSupplier.setVisibility(View.VISIBLE);
                viewModel.setFilters(selectedFilterSupplierId, selectedFilterStatus);
            });
        });

        binding.btnClearPoSupplier.setOnClickListener(v -> {
            selectedFilterSupplierId = null;
            binding.btnManageSuppliers.setText("Lọc NCC ▾");
            binding.btnClearPoSupplier.setVisibility(View.GONE);
            viewModel.setFilters(null, selectedFilterStatus);
        });

        // Chuẩn hóa từ khóa tìm kiếm: cắt bỏ tiền tố "#PO-" hoặc "PO-" nếu người dùng nhập mã phiếu đầy đủ
        binding.etSearchPo.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> {
            if (q.toUpperCase(Locale.ROOT).startsWith("#PO-")) {
                q = q.substring(4).trim();
            } else if (q.toUpperCase(Locale.ROOT).startsWith("PO-")) {
                q = q.substring(3).trim();
            }
            viewModel.setSearchKeyword(q);
        }));

        binding.chipGroupPoStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipPoPending) {
                selectedFilterStatus = "Pending";
            } else if (id == R.id.chipPoCompleted) {
                selectedFilterStatus = "Completed";
            } else if (id == R.id.chipPoCancelled) {
                selectedFilterStatus = "Cancelled";
            } else {
                selectedFilterStatus = null;
            }
            viewModel.setFilters(selectedFilterSupplierId, selectedFilterStatus);
        });

        binding.btnCreatePo.setOnClickListener(v -> showCreatePurchaseOrderSheet());

        viewModel.getPurchaseOrders().observe(this, list -> {
            binding.swipeRefreshPo.setRefreshing(false);
            adapter.setPurchaseOrders(list);
            boolean empty = (list == null || list.isEmpty());
            binding.tvEmptyPo.setVisibility(empty ? View.VISIBLE : View.GONE);
            updatePoKpiSummary(list);
        });

        viewModel.getLoading().observe(this, isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) {
                if (adapter.getItemCount() == 0 && !binding.swipeRefreshPo.isRefreshing()) {
                    binding.layoutLoading.setVisibility(View.VISIBLE);
                    binding.layoutContent.setVisibility(View.GONE);
                }
            } else {
                binding.swipeRefreshPo.setRefreshing(false);
                binding.layoutLoading.setVisibility(View.GONE);
                binding.layoutContent.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getError().observe(this, msg -> {
            binding.swipeRefreshPo.setRefreshing(false);
            binding.layoutLoading.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);
            if (msg != null) {
                AppToast.show(this, msg);
            }
        });

        preloadReferenceData();
        viewModel.fetchPurchaseOrders();
    }

    /**
     * Tải trước danh sách Kho hàng, Nhà cung cấp và Danh mục sản phẩm từ API để sẵn sàng khi mở BottomSheet lập đơn nhập.
     */
    private void preloadReferenceData() {
        loadWarehouses(null);

        repository.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
            @Override
            public void onSuccess(List<SupplierDTO> result) {
                suppliers.clear();
                if (result != null) suppliers.addAll(result);
            }

            @Override
            public void onError(String message) {}
        });

        repository.getAllCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                categories.clear();
                if (result != null) categories.addAll(result);
            }

            @Override
            public void onError(String message) {}
        });
    }

    /**
     * Tính toán và cập nhật 3 chỉ số KPI trên đầu màn hình: Số phiếu Chờ duyệt, Đã nhập kho và Tổng số phiếu.
     */
    private void updatePoKpiSummary(List<PurchaseOrder> list) {
        int pendingCount = 0;
        int completedCount = 0;
        int totalCount = (list != null) ? list.size() : 0;
        if (list != null) {
            for (PurchaseOrder po : list) {
                if (po == null) continue;
                String st = po.getStatus() != null ? po.getStatus().trim() : "Pending";
                if ("Completed".equalsIgnoreCase(st)
                        || "Received".equalsIgnoreCase(st)
                        || "Approved".equalsIgnoreCase(st)) {
                    completedCount++;
                } else if (!"Cancelled".equalsIgnoreCase(st)
                        && !"Canceled".equalsIgnoreCase(st)
                        && !"Rejected".equalsIgnoreCase(st)) {
                    pendingCount++;
                }
            }
        }
        binding.tvPoKpiPending.setText(String.valueOf(pendingCount));
        binding.tvPoKpiCompleted.setText(String.valueOf(completedCount));
        binding.tvPoKpiTotal.setText(String.valueOf(totalCount));
    }

    /**
     * Xác định CategoryId hợp lệ cho sản phẩm khi đóng gói chi tiết PO gửi lên Backend (fallback về danh mục đầu tiên hoặc 1).
     */
    private int resolveCategoryId(Product product) {
        if (product != null && product.getCategoryId() > 0) {
            return product.getCategoryId();
        }
        if (!categories.isEmpty() && categories.get(0).getCategoryId() > 0) {
            return categories.get(0).getCategoryId();
        }
        return 1;
    }

    /**
     * Tải danh sách kho nhận hàng theo chi nhánh hiện tại của user; nếu chi nhánh chưa có kho riêng thì fallback tải toàn bộ kho hệ thống.
     */
    private void loadWarehouses(final Runnable onSuccessCallback) {
        Integer branchId = UserSessionManager.getCurrentBranchId(this);
        final Integer effectiveBranch = (branchId != null && branchId > 0) ? branchId : null;

        repository.getFilteredWarehouses(null, effectiveBranch, new DataRepository.ApiCallback<List<Warehouse>>() {
            @Override
            public void onSuccess(List<Warehouse> result) {
                // Fallback: nếu lọc theo chi nhánh hiện tại không có kho nào, gọi lại API lấy tất cả kho trên toàn hệ thống
                if ((result == null || result.isEmpty()) && effectiveBranch != null) {
                    repository.getFilteredWarehouses(null, null, new DataRepository.ApiCallback<List<Warehouse>>() {
                        @Override
                        public void onSuccess(List<Warehouse> allResult) {
                            warehouses.clear();
                            if (allResult != null) warehouses.addAll(allResult);
                            if (onSuccessCallback != null) onSuccessCallback.run();
                        }

                        @Override
                        public void onError(String message) {
                            if (onSuccessCallback != null) {
                                AppToast.show(PurchaseOrdersActivity.this, "Lỗi tải kho: " + message, AppToast.Type.ERROR);
                            }
                        }
                    });
                    return;
                }
                warehouses.clear();
                if (result != null) warehouses.addAll(result);
                if (onSuccessCallback != null) onSuccessCallback.run();
            }

            @Override
            public void onError(String message) {
                if (onSuccessCallback != null) {
                    AppToast.show(PurchaseOrdersActivity.this, "Lỗi tải kho: " + message, AppToast.Type.ERROR);
                }
            }
        });
    }

    /**
     * Chọn kho mặc định ưu tiên kho thuộc chi nhánh hiện tại của người dùng đăng nhập.
     */
    private Warehouse pickDefaultWarehouse(List<Warehouse> list) {
        if (list == null || list.isEmpty()) return null;
        int branchId = UserSessionManager.getCurrentBranchId(this);
        if (branchId > 0) {
            for (Warehouse w : list) {
                if (w != null && w.getBranchId() == branchId) {
                    return w;
                }
            }
        }
        return list.get(0);
    }

    /**
     * Mở rộng chiều cao hiển thị của BottomSheetDialog để không bị che khuất nội dung form.
     */
    private void expandBottomSheet(BottomSheetDialog dialog) {
        View content = dialog.findViewById(android.R.id.content);
        if (content != null) {
            BottomSheetHelper.prepare(dialog, content, true);
        }
    }

    /**
     * Tìm kiếm sản phẩm từ API dựa trên mã vạch vừa quét được và tự động thêm vào danh sách sản phẩm nhập nháp.
     */
    private void handleScannedBarcodeForPo(final String barcode) {
        repository.searchProductsSimple(barcode, true, new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                if (result != null && !result.isEmpty()) {
                    Product matched = null;
                    String cleanCode = barcode.trim();
                    // Ưu tiên khớp chính xác mã Barcode hoặc SKU trước khi lấy phần tử đầu tiên
                    for (Product p : result) {
                        if (p == null) continue;
                        if ((p.getBarcode() != null && p.getBarcode().trim().equalsIgnoreCase(cleanCode))
                                || (p.getSku() != null && p.getSku().trim().equalsIgnoreCase(cleanCode))) {
                            matched = p;
                            break;
                        }
                    }
                    addProductToDraftPo(matched != null ? matched : result.get(0));
                } else {
                    AppToast.show(PurchaseOrdersActivity.this, "Không tìm thấy sản phẩm có mã: " + barcode, Toast.LENGTH_LONG);
                }
            }

            @Override
            public void onError(String message) {
                AppToast.show(PurchaseOrdersActivity.this, "Lỗi tìm mã vạch: " + message, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Thêm một sản phẩm vào phiếu nhập nháp; nếu sản phẩm đã có trong danh sách thì cộng dồn số lượng thêm 1.
     */
    private void addProductToDraftPo(Product product) {
        if (product == null) return;
        for (PoDraftLine line : activeDraftLines) {
            if (line.product.getProductId() == product.getProductId()) {
                line.quantity += 1;
                if (activeDraftRefreshCallback != null) activeDraftRefreshCallback.run();
                AppToast.show(this, "Đã tăng số lượng [" + product.getName() + "] lên " + line.quantity, Toast.LENGTH_SHORT);
                return;
            }
        }
        // Công thức ước tính giá vốn nhập mặc định: ưu tiên OriginalPrice, nếu không có thì lấy 75% giá bán lẻ hoặc 50,000đ
        double defaultCost = product.getOriginalPrice() > 0
                ? product.getOriginalPrice()
                : (product.getPrice() > 0 ? Math.round(product.getPrice() * 0.75) : 50000);
        activeDraftLines.add(new PoDraftLine(product, 1, defaultCost));
        if (activeDraftRefreshCallback != null) activeDraftRefreshCallback.run();
        AppToast.show(this, "Đã thêm [" + product.getName() + "] vào phiếu nhập", Toast.LENGTH_SHORT);
    }

    // =========================================================================
    // 1. BOTTOM SHEET LẬP ĐƠN NHẬP HÀNG (PO) HIỆN ĐẠI
    // =========================================================================

    /**
     * Hiển thị BottomSheet lập đơn nhập hàng (PO) mới: cho phép chọn Nhà cung cấp, Kho nhận,
     * thêm sản phẩm bằng tìm kiếm hoặc quét mã vạch, chỉnh sửa số lượng/đơn giá/NSX/HSD từng dòng,
     * tùy chọn nhập kho trực tiếp (Completed) hoặc tạo phiếu chờ duyệt (Pending) và gửi lên API.
     */
    private void showCreatePurchaseOrderSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_create_purchase_order, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        final NestedScrollView scrollCreatePo = view.findViewById(R.id.scrollCreatePo);
        final MaterialCardView cardSupplier = view.findViewById(R.id.cardSelectPoSupplier);
        final MaterialCardView cardWarehouse = view.findViewById(R.id.cardSelectPoWarehouse);
        final TextView tvSupplier = view.findViewById(R.id.tvSelectedPoSupplier);
        final TextView tvWarehouse = view.findViewById(R.id.tvSelectedPoWarehouse);
        MaterialButton btnScanBarcode = view.findViewById(R.id.btnScanPoBarcode);
        MaterialButton btnAddProduct = view.findViewById(R.id.btnAddPoProduct);
        final TextView tvEmptyHint = view.findViewById(R.id.tvPoEmptyItemsHint);
        final LinearLayout containerItems = view.findViewById(R.id.containerPoDraftItems);
        final MaterialSwitch switchDirectComplete = view.findViewById(R.id.switchPoDirectComplete);
        final TextInputLayout tilNotes = view.findViewById(R.id.tilPoNotes);
        final TextInputEditText etNotes = view.findViewById(R.id.etPoNotes);
        final TextView tvValidationBanner = view.findViewById(R.id.tvPoValidationBanner);
        final TextView tvTotalAmount = view.findViewById(R.id.tvPoTotalAmount);
        final MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitCreatePo);

        final int defaultStrokeColor = ContextCompat.getColor(this, R.color.outline_variant);
        final int errorStrokeColor = ContextCompat.getColor(this, R.color.error);
        final int strokeNormalPx = Math.round(getResources().getDisplayMetrics().density);
        final int strokeErrorPx = Math.round(2f * getResources().getDisplayMetrics().density);

        final Runnable clearValidationVisuals = () -> {
            if (tvValidationBanner != null) {
                tvValidationBanner.setVisibility(View.GONE);
            }
            if (cardSupplier != null) {
                cardSupplier.setStrokeColor(defaultStrokeColor);
                cardSupplier.setStrokeWidth(strokeNormalPx);
            }
            if (cardWarehouse != null) {
                cardWarehouse.setStrokeColor(defaultStrokeColor);
                cardWarehouse.setStrokeWidth(strokeNormalPx);
            }
        };

        activeDraftLines.clear();
        // Theo luồng Flutter: NCC để người dùng tự chọn rõ ràng; Kho tự chọn theo chi nhánh hiện tại
        final SupplierDTO[] selectedSupplier = new SupplierDTO[]{null};
        final Warehouse[] selectedWarehouse = new Warehouse[]{pickDefaultWarehouse(warehouses)};

        tvSupplier.setText("Chạm để chọn Nhà cung cấp * ▾");
        if (selectedWarehouse[0] != null) {
            tvWarehouse.setText(selectedWarehouse[0].getName() + " ▾");
        } else {
            tvWarehouse.setText("Đang tải kho nhận hàng...");
            loadWarehouses(() -> {
                if (selectedWarehouse[0] == null && !warehouses.isEmpty()) {
                    selectedWarehouse[0] = pickDefaultWarehouse(warehouses);
                }
                tvWarehouse.setText(selectedWarehouse[0] != null
                        ? (selectedWarehouse[0].getName() + " ▾")
                        : "Chạm để chọn Kho nhận * ▾");
            });
        }

        final SimpleDateFormat displayDateFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Công thức tính tổng tiền phiếu nhập = Tổng(Số lượng * Đơn giá nhập) của tất cả các dòng sản phẩm nháp
        final Runnable updateTotalLabel = () -> {
            double sum = 0;
            for (PoDraftLine l : activeDraftLines) {
                sum += l.quantity * l.unitPrice;
            }
            tvTotalAmount.setText(Formatters.currency(sum));
        };

        // Callback vẽ lại danh sách các dòng sản phẩm nháp mỗi khi thêm/xóa/sửa sản phẩm
        activeDraftRefreshCallback = new Runnable() {
            @Override
            public void run() {
                containerItems.removeAllViews();
                tvEmptyHint.setVisibility(activeDraftLines.isEmpty() ? View.VISIBLE : View.GONE);
                if (!activeDraftLines.isEmpty() && tvValidationBanner != null) {
                    tvValidationBanner.setVisibility(View.GONE);
                }

                for (int i = 0; i < activeDraftLines.size(); i++) {
                    final int idx = i;
                    final PoDraftLine line = activeDraftLines.get(i);
                    View row = LayoutInflater.from(PurchaseOrdersActivity.this).inflate(R.layout.item_po_draft_line, containerItems, false);

                    TextView tvName = row.findViewById(R.id.tvPoLineProductName);
                    TextView tvSku = row.findViewById(R.id.tvPoLineSku);
                    View btnRemove = row.findViewById(R.id.btnRemovePoLine);
                    View btnMinus = row.findViewById(R.id.btnPoLineMinus);
                    final TextView tvQty = row.findViewById(R.id.tvPoLineQty);
                    View btnPlus = row.findViewById(R.id.btnPoLinePlus);
                    EditText etUnitPrice = row.findViewById(R.id.etPoLineUnitPrice);
                    final TextView tvSubtotal = row.findViewById(R.id.tvPoLineSubtotal);
                    final TextView tvMfgDate = row.findViewById(R.id.tvPoLineMfgDate);
                    final TextView tvExpDate = row.findViewById(R.id.tvPoLineExpDate);

                    tvName.setText(line.product.getName());
                    String sku = !line.product.getSku().isEmpty() ? line.product.getSku() : ("SP #" + line.product.getProductId());
                    tvSku.setText("Mã: " + sku + " • Tồn hiện tại: " + line.product.getActualStock());
                    tvQty.setText(String.valueOf(line.quantity));
                    etUnitPrice.setText(String.valueOf(Math.round(line.unitPrice)));
                    tvSubtotal.setText(Formatters.currency(line.quantity * line.unitPrice));
                    tvMfgDate.setText("NSX: " + displayDateFmt.format(line.manufacturingDate));
                    tvExpDate.setText("HSD: " + displayDateFmt.format(line.expiryDate));

                    btnRemove.setOnClickListener(v -> {
                        activeDraftLines.remove(idx);
                        run();
                    });

                    tvQty.setOnClickListener(v -> QuantityInputDialog.show(
                            PurchaseOrdersActivity.this,
                            line.product.getName(),
                            line.quantity,
                            1,
                            999999,
                            line.product.getUnit(),
                            newQty -> {
                                line.quantity = newQty;
                                tvQty.setText(String.valueOf(line.quantity));
                                tvSubtotal.setText(Formatters.currency(line.quantity * line.unitPrice));
                                updateTotalLabel.run();
                            }
                    ));

                    btnMinus.setOnClickListener(v -> {
                        if (line.quantity <= 1) {
                            activeDraftLines.remove(idx);
                            run();
                        } else {
                            line.quantity -= 1;
                            tvQty.setText(String.valueOf(line.quantity));
                            tvSubtotal.setText(Formatters.currency(line.quantity * line.unitPrice));
                            updateTotalLabel.run();
                        }
                    });

                    btnPlus.setOnClickListener(v -> {
                        line.quantity += 1;
                        tvQty.setText(String.valueOf(line.quantity));
                        tvSubtotal.setText(Formatters.currency(line.quantity * line.unitPrice));
                        updateTotalLabel.run();
                    });

                    etUnitPrice.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) {
                            try {
                                String clean = s != null ? s.toString().replaceAll("[^0-9]", "") : "0";
                                line.unitPrice = clean.isEmpty() ? 0 : Double.parseDouble(clean);
                            } catch (Exception e) {
                                line.unitPrice = 0;
                            }
                            tvSubtotal.setText(Formatters.currency(line.quantity * line.unitPrice));
                            updateTotalLabel.run();
                        }
                    });

                    // Chọn Ngày sản xuất (NSX): nếu HSD hiện tại nhỏ hơn NSX mới thì tự động đẩy HSD lên +1 năm
                    tvMfgDate.setOnClickListener(v -> showLineDatePicker(line.manufacturingDate, picked -> {
                        line.manufacturingDate = picked;
                        tvMfgDate.setText("NSX: " + displayDateFmt.format(picked));
                        if (normalizeStartOfDay(line.expiryDate).before(normalizeStartOfDay(picked))) {
                            Calendar expCal = Calendar.getInstance();
                            expCal.setTime(picked);
                            expCal.add(Calendar.YEAR, 1);
                            line.expiryDate = expCal.getTime();
                            tvExpDate.setText("HSD: " + displayDateFmt.format(line.expiryDate));
                        }
                    }));

                    tvExpDate.setOnClickListener(v -> showLineDatePicker(line.expiryDate, picked -> {
                        if (normalizeStartOfDay(picked).before(normalizeStartOfDay(line.manufacturingDate))) {
                            AppToast.show(PurchaseOrdersActivity.this,
                                    "Hạn sử dụng (HSD) phải sau hoặc bằng Ngày sản xuất (NSX)!",
                                    AppToast.Type.WARNING);
                            return;
                        }
                        line.expiryDate = picked;
                        tvExpDate.setText("HSD: " + displayDateFmt.format(picked));
                    }));

                    containerItems.addView(row);
                }

                updateTotalLabel.run();
            }
        };

        cardSupplier.setOnClickListener(v -> ModernPickerSheets.showSupplierPicker(this, s -> {
            selectedSupplier[0] = s;
            tvSupplier.setText(s.getName() + " ▾");
            tvSupplier.setTextColor(ContextCompat.getColor(this, R.color.primary));
            clearValidationVisuals.run();
        }));

        cardWarehouse.setOnClickListener(v -> {
            if (warehouses.isEmpty()) {
                loadWarehouses(() -> {
                    if (warehouses.isEmpty()) {
                        AppToast.show(PurchaseOrdersActivity.this, "Hệ thống chưa có kho hàng nào!", AppToast.Type.WARNING);
                        return;
                    }
                    ModernPickerSheets.showWarehousePicker(PurchaseOrdersActivity.this, warehouses, w -> {
                        selectedWarehouse[0] = w;
                        tvWarehouse.setText(w.getName() + " ▾");
                        tvWarehouse.setTextColor(ContextCompat.getColor(this, R.color.primary));
                        clearValidationVisuals.run();
                    });
                });
                return;
            }
            ModernPickerSheets.showWarehousePicker(this, warehouses, w -> {
                selectedWarehouse[0] = w;
                tvWarehouse.setText(w.getName() + " ▾");
                tvWarehouse.setTextColor(ContextCompat.getColor(this, R.color.primary));
                clearValidationVisuals.run();
            });
        });

        // Mở màn hình Camera quét mã vạch sản phẩm
        btnScanBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            poBarcodeLauncher.launch(intent);
        });

        btnAddProduct.setOnClickListener(v -> {
            Integer supId = selectedSupplier[0] != null ? selectedSupplier[0].getSupplierId() : null;
            ModernPickerSheets.showProductPicker(
                    this,
                    supId,
                    true,
                    () -> poBarcodeLauncher.launch(new Intent(this, BarcodeScanActivity.class)),
                    this::addProductToDraftPo
            );
        });

        // Validate dữ liệu form, đóng gói payload Chi tiết PO + Lô sản phẩm (ProductLots) và gọi API tạo đơn nhập hàng
        btnSubmit.setOnClickListener(v -> {
            clearValidationVisuals.run();
            boolean notesValid = FormValidator.create()
                    .custom(tilNotes, etNotes, value -> value.length() > 250 ? "Ghi chú tối đa 250 ký tự" : null)
                    .validate();
            if (!notesValid) {
                return;
            }
            if (selectedSupplier[0] == null) {
                String msg = "Vui lòng chọn Nhà cung cấp trước khi tạo đơn nhập!";
                cardSupplier.setStrokeColor(errorStrokeColor);
                cardSupplier.setStrokeWidth(strokeErrorPx);
                tvSupplier.setTextColor(errorStrokeColor);
                if (tvValidationBanner != null) {
                    tvValidationBanner.setText("⚠ " + msg);
                    tvValidationBanner.setVisibility(View.VISIBLE);
                }
                if (scrollCreatePo != null) scrollCreatePo.smoothScrollTo(0, 0);
                AppToast.show(this, msg, AppToast.Type.WARNING);
                return;
            }
            if (selectedWarehouse[0] == null) {
                String msg = "Vui lòng chọn Kho nhận hàng trước khi tạo đơn nhập!";
                cardWarehouse.setStrokeColor(errorStrokeColor);
                cardWarehouse.setStrokeWidth(strokeErrorPx);
                tvWarehouse.setTextColor(errorStrokeColor);
                if (tvValidationBanner != null) {
                    tvValidationBanner.setText("⚠ " + msg);
                    tvValidationBanner.setVisibility(View.VISIBLE);
                }
                if (scrollCreatePo != null) scrollCreatePo.smoothScrollTo(0, 0);
                AppToast.show(this, msg, AppToast.Type.WARNING);
                return;
            }
            if (activeDraftLines.isEmpty()) {
                String msg = "Vui lòng chọn hoặc quét ít nhất 1 sản phẩm nhập!";
                if (tvValidationBanner != null) {
                    tvValidationBanner.setText("⚠ " + msg);
                    tvValidationBanner.setVisibility(View.VISIBLE);
                }
                AppToast.show(this, msg, AppToast.Type.WARNING);
                return;
            }
            for (PoDraftLine line : activeDraftLines) {
                if (line.quantity <= 0) {
                    String msg = "Số lượng nhập của [" + line.product.getName() + "] phải lớn hơn 0!";
                    if (tvValidationBanner != null) {
                        tvValidationBanner.setText("⚠ " + msg);
                        tvValidationBanner.setVisibility(View.VISIBLE);
                    }
                    AppToast.show(this, msg, AppToast.Type.WARNING);
                    return;
                }
                if (line.unitPrice <= 0) {
                    String msg = "Đơn giá nhập của [" + line.product.getName() + "] phải lớn hơn 0 ₫!";
                    if (tvValidationBanner != null) {
                        tvValidationBanner.setText("⚠ " + msg);
                        tvValidationBanner.setVisibility(View.VISIBLE);
                    }
                    AppToast.show(this, msg, AppToast.Type.WARNING);
                    return;
                }
                if (normalizeStartOfDay(line.expiryDate).before(normalizeStartOfDay(line.manufacturingDate))) {
                    String msg = "HSD của [" + line.product.getName() + "] phải sau hoặc bằng NSX!";
                    if (tvValidationBanner != null) {
                        tvValidationBanner.setText("⚠ " + msg);
                        tvValidationBanner.setVisibility(View.VISIBLE);
                    }
                    AppToast.show(this, msg, AppToast.Type.WARNING);
                    return;
                }
            }

            double totalAmount = 0;
            String orderDateIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

            // Đóng gói danh sách chi tiết sản phẩm kèm thông tin lô hàng (ProductLots) theo đúng cấu trúc DTO của Backend
            List<Map<String, Object>> details = new ArrayList<>();
            for (PoDraftLine line : activeDraftLines) {
                double lineTotal = line.quantity * line.unitPrice;
                totalAmount += lineTotal;

                String mfgIso = formatIsoStartOfDay(line.manufacturingDate);
                String expIso = formatIsoStartOfDay(line.expiryDate);

                Map<String, Object> lot = new HashMap<>();
                lot.put("ProductId", line.product.getProductId());
                lot.put("CostPrice", line.unitPrice);
                lot.put("ManufacturingDate", mfgIso);
                lot.put("ExpiryDate", expIso);
                List<Map<String, Object>> lots = new ArrayList<>();
                lots.add(lot);

                int catId = resolveCategoryId(line.product);

                Map<String, Object> d = new HashMap<>();
                d.put("PurchaseOrderDetailId", 0);
                d.put("PurchaseOrderId", 0);
                d.put("ProductId", line.product.getProductId());
                d.put("ProductName", line.product.getName());
                d.put("CategoryId", catId);
                d.put("LotId", 0);
                d.put("Quantity", line.quantity);
                d.put("Unit", line.product.getUnit() != null && !line.product.getUnit().isEmpty() ? line.product.getUnit() : "Cái");
                d.put("UnitPrice", line.unitPrice);
                d.put("TotalPrice", lineTotal);
                d.put("ManufacturingDate", mfgIso);
                d.put("ExpiryDate", expIso);
                d.put("ProductLots", lots);
                details.add(d);
            }

            // Nếu bật switchDirectComplete thì đặt trạng thái "Completed" để Backend cộng tồn kho & ghi công nợ ngay lập tức
            boolean isDirect = switchDirectComplete.isChecked();
            String notes = FormValidator.textOf(etNotes);
            int userId = UserSessionManager.getCurrentUserId(this);
            int branchId = selectedWarehouse[0].getBranchId();
            if (branchId <= 0) {
                branchId = UserSessionManager.getCurrentBranchId(this);
            }

            PurchaseOrder po = new PurchaseOrder();
            po.setSupplierId(selectedSupplier[0].getSupplierId());
            po.setSupplierName(selectedSupplier[0].getName());
            po.setWarehouseId(selectedWarehouse[0].getWarehouseId());
            po.setWarehouseName(selectedWarehouse[0].getName());
            po.setUserId(userId > 0 ? userId : 1);
            po.setBranchId(branchId > 0 ? branchId : 1);
            po.setOrderDate(orderDateIso);
            po.setStatus(isDirect ? "Completed" : "Pending");
            po.setPaymentStatus("Unpaid");
            po.setTotalAmount(totalAmount);
            po.setPaidAmount(0);
            po.setDueAmount(totalAmount);
            po.setNotes(notes.isEmpty() ? "Nhập hàng PO qua ứng dụng di động" : notes);
            po.setDetails(details);

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Đang tạo...");

            repository.createPurchase(po, new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    sheet.dismiss();
                    AppToast.show(
                            PurchaseOrdersActivity.this,
                            isDirect ? "Đã tạo PO và hoàn tất nhập kho thành công!" : "Đã lập đơn nhập hàng (Chờ duyệt)!",
                            AppToast.Type.SUCCESS
                    );
                    viewModel.fetchPurchaseOrders();
                }

                @Override
                public void onError(String message) {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Tạo đơn nhập (PO)");
                    AppToast.show(PurchaseOrdersActivity.this, message, AppToast.Type.ERROR);
                }
            });
        });

        sheet.setOnDismissListener(d -> activeDraftRefreshCallback = null);
        activeDraftRefreshCallback.run();
        sheet.show();
    }

    private interface DatePickCallback {
        void onDatePicked(Date date);
    }

    /**
     * Hiển thị DatePickerDialog chọn ngày (NSX hoặc HSD) cho từng dòng sản phẩm trong phiếu nhập nháp.
     */
    private void showLineDatePicker(Date initialDate, final DatePickCallback callback) {
        Calendar cal = Calendar.getInstance();
        if (initialDate != null) cal.setTime(initialDate);
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 0, 0, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    callback.onDatePicked(picked.getTime());
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        picker.show();
    }

    /**
     * Chuẩn hóa đối tượng Date về thời điểm đầu ngày (00:00:00.000) để so sánh NSX và HSD chính xác theo ngày.
     */
    private Date normalizeStartOfDay(Date d) {
        Calendar cal = Calendar.getInstance();
        if (d != null) cal.setTime(d);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Định dạng Date sang chuỗi ISO-8601 đầu ngày ({@code yyyy-MM-dd'T'00:00:00}) để gửi lên API Backend.
     */
    private String formatIsoStartOfDay(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd'T'00:00:00", Locale.US).format(d != null ? d : new Date());
    }

    // =========================================================================
    // 2. CHI TIẾT & QUẢN LÝ TRẠNG THÁI ĐƠN NHẬP (PO) CHUẨN LOGIC KHO
    // =========================================================================

    /**
     * Tải thông tin chi tiết đầy đủ của đơn nhập hàng từ API theo ID trước khi mở BottomSheet chi tiết PO.
     * Kích hoạt khi người dùng chạm vào một dòng PO trên RecyclerView; có cờ {@code isOpeningDetail} chống bấm đúp.
     */
    private void showPurchaseOrderActionsSheet(final PurchaseOrder summaryPo) {
        if (summaryPo == null || isOpeningDetail) return;
        isOpeningDetail = true;
        repository.getPurchaseById(summaryPo.getPurchaseOrderId(), new DataRepository.ApiCallback<PurchaseOrder>() {
            @Override
            public void onSuccess(PurchaseOrder fullPo) {
                isOpeningDetail = false;
                if (isFinishing() || isDestroyed()) return;
                presentPoDetailDialog(fullPo != null ? fullPo : summaryPo);
            }

            @Override
            public void onError(String message) {
                isOpeningDetail = false;
                if (isFinishing() || isDestroyed()) return;
                // Fallback: nếu API lấy chi tiết lỗi thì vẫn hiển thị dialog dựa trên dữ liệu tóm tắt sẵn có
                presentPoDetailDialog(summaryPo);
            }
        });
    }

    /**
     * Hiển thị BottomSheet chi tiết đơn nhập hàng: thông tin chung, danh sách mặt hàng kèm HSD,
     * và các nút hành động tương ứng với trạng thái phiếu (Duyệt nhập kho / Từ chối hủy / Xóa phiếu nếu còn Pending).
     */
    private void presentPoDetailDialog(final PurchaseOrder po) {
        String rawStatus = po.getStatus() != null ? po.getStatus().trim() : "Pending";
        final boolean isCompleted = "Completed".equalsIgnoreCase(rawStatus)
                || "Received".equalsIgnoreCase(rawStatus)
                || "Approved".equalsIgnoreCase(rawStatus);
        final boolean isCancelled = "Cancelled".equalsIgnoreCase(rawStatus)
                || "Canceled".equalsIgnoreCase(rawStatus)
                || "Rejected".equalsIgnoreCase(rawStatus);
        final boolean isPending = !isCompleted && !isCancelled;

        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_purchase_order_detail, null);
        dialog.setContentView(dialogView);
        BottomSheetHelper.prepare(dialog, dialogView, true);

        TextView tvTitle = dialogView.findViewById(R.id.tvPoDetailTitle);
        TextView tvStatus = dialogView.findViewById(R.id.tvPoDetailStatus);
        TextView tvDate = dialogView.findViewById(R.id.tvPoDetailDate);
        View btnDeletePo = dialogView.findViewById(R.id.btnDeletePo);
        View btnClose = dialogView.findViewById(R.id.btnClosePoDetail);

        TextView tvSupplier = dialogView.findViewById(R.id.tvPoDetailSupplier);
        TextView tvWarehouse = dialogView.findViewById(R.id.tvPoDetailWarehouse);
        View layoutBranch = dialogView.findViewById(R.id.layoutPoDetailBranch);
        TextView tvBranch = dialogView.findViewById(R.id.tvPoDetailBranch);
        View layoutCreator = dialogView.findViewById(R.id.layoutPoDetailCreator);
        TextView tvCreator = dialogView.findViewById(R.id.tvPoDetailCreator);
        View layoutPayment = dialogView.findViewById(R.id.layoutPoDetailPayment);
        TextView tvPayment = dialogView.findViewById(R.id.tvPoDetailPayment);
        View layoutNotes = dialogView.findViewById(R.id.layoutPoDetailNotes);
        TextView tvNotes = dialogView.findViewById(R.id.tvPoDetailNotes);

        TextView tvItemCount = dialogView.findViewById(R.id.tvPoDetailItemCount);
        LinearLayout containerItems = dialogView.findViewById(R.id.containerPoDetailItems);
        TextView tvEmptyItems = dialogView.findViewById(R.id.tvPoDetailEmptyItems);
        TextView tvTotalAmount = dialogView.findViewById(R.id.tvPoDetailTotalAmount);

        View layoutPending = dialogView.findViewById(R.id.layoutPoPendingActions);
        View btnReject = dialogView.findViewById(R.id.btnRejectPo);
        View btnApprove = dialogView.findViewById(R.id.btnApprovePo);

        View layoutCompleted = dialogView.findViewById(R.id.layoutPoCompletedBanner);
        View btnCloseCompleted = dialogView.findViewById(R.id.btnCloseCompleted);

        View layoutCancelled = dialogView.findViewById(R.id.layoutPoCancelledBanner);
        View btnCloseCancelled = dialogView.findViewById(R.id.btnCloseCancelled);

        tvTitle.setText("Phiếu nhập #PO-" + po.getPurchaseOrderId());
        tvDate.setText(Formatters.dateTime(po.getOrderDate()));

        if (isCompleted) {
            tvStatus.setText("Đã nhập kho");
            tvStatus.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatus.setTextColor(getColor(R.color.success_text));
        } else if (isCancelled) {
            tvStatus.setText("Đã hủy đơn");
            tvStatus.setBackgroundResource(R.drawable.bg_badge_error);
            tvStatus.setTextColor(getColor(R.color.error_text));
        } else {
            tvStatus.setText("Chờ duyệt");
            tvStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            tvStatus.setTextColor(getColor(R.color.warning_text));
        }

        tvSupplier.setText(po.getSupplierName() != null && !po.getSupplierName().isEmpty()
                ? po.getSupplierName() : ("Nhà cung cấp #" + po.getSupplierId()));
        tvWarehouse.setText(po.getWarehouseName() != null && !po.getWarehouseName().isEmpty()
                ? po.getWarehouseName() : ("Kho #" + po.getWarehouseId()));

        if (po.getBranchName() != null && !po.getBranchName().trim().isEmpty()) {
            layoutBranch.setVisibility(View.VISIBLE);
            tvBranch.setText(po.getBranchName().trim());
        } else {
            layoutBranch.setVisibility(View.GONE);
        }

        if (po.getUserName() != null && !po.getUserName().trim().isEmpty()) {
            layoutCreator.setVisibility(View.VISIBLE);
            tvCreator.setText(po.getUserName().trim());
        } else {
            layoutCreator.setVisibility(View.GONE);
        }

        layoutPayment.setVisibility(View.VISIBLE);
        tvPayment.setText(formatPaymentStatus(po));

        if (po.getNotes() != null && !po.getNotes().trim().isEmpty()) {
            layoutNotes.setVisibility(View.VISIBLE);
            tvNotes.setText(po.getNotes().trim());
        } else {
            layoutNotes.setVisibility(View.GONE);
        }

        containerItems.removeAllViews();
        List<Map<String, Object>> details = po.getDetails();
        if (details != null && !details.isEmpty()) {
            tvItemCount.setText(details.size() + " mặt hàng");
            tvEmptyItems.setVisibility(View.GONE);

            // Render từng dòng sản phẩm trong phiếu nhập (hỗ trợ cả key camelCase và PascalCase từ JSON)
            for (int i = 0; i < details.size(); i++) {
                Map<String, Object> d = details.get(i);
                if (d == null) continue;
                Object pNameObj = getMapValue(d, "productName", "ProductName");
                Object pIdObj = getMapValue(d, "productId", "ProductId");
                String pName = pNameObj != null ? pNameObj.toString() : ("SP #" + pIdObj);
                int qty = parseObjInt(getMapValue(d, "quantity", "Quantity"), 1);
                Object unitObj = getMapValue(d, "unit", "Unit");
                String unit = (unitObj != null && !unitObj.toString().isEmpty()) ? unitObj.toString() : "Cái";
                double uPrice = parseObjDouble(getMapValue(d, "unitPrice", "UnitPrice"), 0);
                double total = parseObjDouble(getMapValue(d, "totalPrice", "TotalPrice"), qty * uPrice);

                View itemView = LayoutInflater.from(this).inflate(R.layout.item_po_detail_product, containerItems, false);
                TextView tvItemName = itemView.findViewById(R.id.tvPoDetailItemName);
                TextView tvItemSub = itemView.findViewById(R.id.tvPoDetailItemSub);
                TextView tvItemTotal = itemView.findViewById(R.id.tvPoDetailItemTotal);

                tvItemName.setText((i + 1) + ". " + pName);

                StringBuilder subInfo = new StringBuilder();
                subInfo.append(qty).append(" ").append(unit).append(" × ").append(Formatters.currency(uPrice));
                Object exp = getMapValue(d, "expiryDate", "ExpiryDate");
                if (exp != null && !exp.toString().isEmpty()) {
                    subInfo.append(" | HSD: ").append(exp.toString().split("T")[0]);
                }
                tvItemSub.setText(subInfo.toString());
                tvItemTotal.setText(Formatters.currency(total));

                containerItems.addView(itemView);
            }
        } else {
            tvItemCount.setText("0 mặt hàng");
            tvEmptyItems.setVisibility(View.VISIBLE);
        }

        tvTotalAmount.setText(Formatters.currency(po.getTotalAmount()));

        // Chỉ cho phép Duyệt, Hủy hoặc Xóa khi phiếu nhập đang ở trạng thái Chờ duyệt (Pending)
        if (isPending) {
            btnDeletePo.setVisibility(View.VISIBLE);
            layoutPending.setVisibility(View.VISIBLE);
            layoutCompleted.setVisibility(View.GONE);
            layoutCancelled.setVisibility(View.GONE);

            btnDeletePo.setOnClickListener(v -> {
                dialog.dismiss();
                showPoActionConfirmSheet(po, PoActionMode.DELETE);
            });

            btnReject.setOnClickListener(v -> {
                dialog.dismiss();
                showPoActionConfirmSheet(po, PoActionMode.CANCEL);
            });

            btnApprove.setOnClickListener(v -> {
                dialog.dismiss();
                showPoActionConfirmSheet(po, PoActionMode.APPROVE);
            });
        } else if (isCompleted) {
            btnDeletePo.setVisibility(View.GONE);
            layoutPending.setVisibility(View.GONE);
            layoutCompleted.setVisibility(View.VISIBLE);
            layoutCancelled.setVisibility(View.GONE);

            btnCloseCompleted.setOnClickListener(v -> dialog.dismiss());
        } else {
            btnDeletePo.setVisibility(View.GONE);
            layoutPending.setVisibility(View.GONE);
            layoutCompleted.setVisibility(View.GONE);
            layoutCancelled.setVisibility(View.VISIBLE);

            btnCloseCancelled.setOnClickListener(v -> dialog.dismiss());
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Hiển thị BottomSheet xác nhận lần cuối trước khi thực hiện Duyệt nhập kho (APPROVE), Hủy đơn (CANCEL) hoặc Xóa phiếu (DELETE).
     * Cho phép người dùng nhập thêm ghi chú duyệt hoặc lý do hủy đơn.
     */
    private void showPoActionConfirmSheet(final PurchaseOrder po, final PoActionMode mode) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_po_status_confirm, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        FrameLayout boxIcon = view.findViewById(R.id.boxPoConfirmIcon);
        ImageView imgIcon = view.findViewById(R.id.imgPoConfirmIcon);
        TextView tvTitle = view.findViewById(R.id.tvPoConfirmTitle);
        TextView tvCode = view.findViewById(R.id.tvPoConfirmCode);

        TextView tvSupplier = view.findViewById(R.id.tvPoConfirmSupplier);
        TextView tvWarehouse = view.findViewById(R.id.tvPoConfirmWarehouse);
        TextView tvAmount = view.findViewById(R.id.tvPoConfirmAmount);

        LinearLayout layoutNotice = view.findViewById(R.id.layoutPoConfirmNotice);
        ImageView imgNotice = view.findViewById(R.id.imgPoConfirmNoticeIcon);
        TextView tvNotice = view.findViewById(R.id.tvPoConfirmNotice);

        TextInputLayout tilNote = view.findViewById(R.id.tilPoConfirmNote);
        TextInputEditText etNote = view.findViewById(R.id.etPoConfirmNote);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelPoConfirm);
        MaterialButton btnProceed = view.findViewById(R.id.btnProceedPoConfirm);

        String supplierName = po.getSupplierName() != null && !po.getSupplierName().isEmpty()
                ? po.getSupplierName() : ("Nhà cung cấp #" + po.getSupplierId());
        String warehouseName = po.getWarehouseName() != null && !po.getWarehouseName().isEmpty()
                ? po.getWarehouseName() : ("Kho #" + po.getWarehouseId());
        int itemCount = po.getDetails() != null ? po.getDetails().size() : 0;

        tvSupplier.setText(supplierName);
        tvWarehouse.setText(warehouseName);
        tvAmount.setText(Formatters.currency(po.getTotalAmount()) + (itemCount > 0 ? " (" + itemCount + " mặt hàng)" : ""));

        if (mode == PoActionMode.APPROVE) {
            boxIcon.setBackgroundResource(R.drawable.bg_icon_box_emerald);
            imgIcon.setImageResource(R.drawable.ic_inventory);
            imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.emerald)));
            tvTitle.setText("Xác nhận duyệt nhập kho");
            tvCode.setText("Phiếu #PO-" + po.getPurchaseOrderId() + " • Chờ duyệt");

            layoutNotice.setBackgroundResource(R.drawable.bg_badge_success);
            imgNotice.setImageResource(R.drawable.ic_inventory);
            imgNotice.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_text)));
            tvNotice.setTextColor(ContextCompat.getColor(this, R.color.success_text));
            tvNotice.setText("Số lượng sản phẩm trong phiếu sẽ được cộng trực tiếp vào tồn kho và ghi nhận công nợ nhà cung cấp.");

            tilNote.setVisibility(View.VISIBLE);
            tilNote.setHint("Ghi chú duyệt đơn (Tùy chọn)");
            btnProceed.setText("Duyệt & Nhập kho");
            btnProceed.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.emerald)));
        } else if (mode == PoActionMode.CANCEL) {
            boxIcon.setBackgroundResource(R.drawable.bg_icon_box_amber);
            imgIcon.setImageResource(R.drawable.ic_close);
            imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.warning_text)));
            tvTitle.setText("Xác nhận hủy đơn nhập");
            tvCode.setText("Phiếu #PO-" + po.getPurchaseOrderId() + " • Chờ duyệt");

            layoutNotice.setBackgroundResource(R.drawable.bg_badge_pending);
            imgNotice.setImageResource(R.drawable.ic_clock);
            imgNotice.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.warning_text)));
            tvNotice.setTextColor(ContextCompat.getColor(this, R.color.warning_text));
            tvNotice.setText("Đơn nhập sẽ chuyển sang trạng thái Đã hủy và không cộng vào tồn kho. Không thể khôi phục sau khi hủy.");

            tilNote.setVisibility(View.VISIBLE);
            tilNote.setHint("Lý do hủy đơn (Tùy chọn)");
            btnProceed.setText("Xác nhận hủy");
            btnProceed.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
        } else {
            boxIcon.setBackgroundResource(R.drawable.bg_icon_box_rose);
            imgIcon.setImageResource(R.drawable.ic_delete);
            imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
            tvTitle.setText("Xác nhận xóa phiếu nhập");
            tvCode.setText("Phiếu #PO-" + po.getPurchaseOrderId() + " • Xóa vĩnh viễn");

            layoutNotice.setBackgroundResource(R.drawable.bg_badge_error);
            imgNotice.setImageResource(R.drawable.ic_delete);
            imgNotice.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_text)));
            tvNotice.setTextColor(ContextCompat.getColor(this, R.color.error_text));
            tvNotice.setText("Phiếu #PO-" + po.getPurchaseOrderId() + " đang ở trạng thái Chờ duyệt và sẽ bị xóa hoàn toàn khỏi hệ thống.");

            tilNote.setVisibility(View.GONE);
            btnProceed.setText("Xóa phiếu");
            btnProceed.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
        }

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnProceed.setOnClickListener(v -> {
            final String defaultProceedLabel = btnProceed.getText().toString();
            btnProceed.setEnabled(false);
            btnCancel.setEnabled(false);
            btnProceed.setText("Đang xử lý...");

            if (mode == PoActionMode.DELETE) {
                deletePendingPo(po.getPurchaseOrderId(), sheet, btnProceed, btnCancel, defaultProceedLabel);
            } else {
                String extraNote = FormValidator.textOf(etNote);
                String targetStatus = (mode == PoActionMode.APPROVE) ? "Completed" : "Cancelled";
                String mergedNote = buildStatusNote(po.getNotes(), mode, extraNote);
                updateStatus(po, targetStatus, mergedNote, sheet, btnProceed, btnCancel, defaultProceedLabel);
            }
        });

        sheet.show();
    }

    /**
     * Ghép ghi chú gốc của phiếu nhập với ghi chú duyệt hoặc lý do hủy đơn mới nhập thêm.
     */
    private String buildStatusNote(String originalNotes, PoActionMode mode, String extraNote) {
        String base = originalNotes != null ? originalNotes.trim() : "";
        String cleanExtra = extraNote != null ? extraNote.trim() : "";
        if (!cleanExtra.isEmpty()) {
            String prefix = (mode == PoActionMode.CANCEL) ? "Hủy: " : "Duyệt: ";
            return base.isEmpty() ? (prefix + cleanExtra) : (base + " | " + prefix + cleanExtra);
        }
        if (mode == PoActionMode.CANCEL) {
            return base.isEmpty() ? "Hủy đơn từ ứng dụng" : base;
        }
        return base.isEmpty() ? null : base;
    }

    /**
     * Chuyển đổi mã trạng thái thanh toán của PO (Paid/Partial/Unpaid) sang chuỗi tiếng Việt kèm số tiền đã trả.
     */
    private String formatPaymentStatus(PurchaseOrder po) {
        String ps = po.getPaymentStatus() != null ? po.getPaymentStatus().trim() : "";
        String label;
        if ("Paid".equalsIgnoreCase(ps)) {
            label = "Đã thanh toán";
        } else if ("Partial".equalsIgnoreCase(ps)) {
            label = "Thanh toán một phần";
        } else {
            label = "Chưa thanh toán";
        }
        if (po.getPaidAmount() > 0 && po.getDueAmount() > 0) {
            return label + " (Đã trả " + Formatters.currency(po.getPaidAmount()) + ")";
        }
        return label;
    }

    /**
     * Lấy giá trị từ Map chi tiết PO với 2 biến thể tên key (camelCase hoặc PascalCase).
     */
    private Object getMapValue(Map<String, Object> map, String key1, String key2) {
        if (map == null) return null;
        return map.get(key1) != null ? map.get(key1) : map.get(key2);
    }

    /**
     * Ép kiểu an toàn một Object bất kỳ từ JSON Map sang số nguyên {@code int}.
     */
    private int parseObjInt(Object o, int fallback) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) {
            try { return (int) Double.parseDouble(o.toString()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    /**
     * Ép kiểu an toàn một Object bất kỳ từ JSON Map sang số thực {@code double}.
     */
    private double parseObjDouble(Object o, double fallback) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) {
            try { return Double.parseDouble(o.toString()); } catch (Exception ignored) {}
        }
        return fallback;
    }

    /**
     * Gọi API cập nhật trạng thái đơn nhập hàng sang "Completed" (duyệt nhập kho) hoặc "Cancelled" (hủy đơn),
     * sau đó tải lại danh sách PO.
     */
    private void updateStatus(
            final PurchaseOrder po,
            final String status,
            final String mergedNote,
            final BottomSheetDialog sheet,
            final MaterialButton btnProceed,
            final MaterialButton btnCancel,
            final String defaultProceedLabel
    ) {
        final int poId = po.getPurchaseOrderId();
        repository.updatePurchaseStatus(poId, status, mergedNote, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (sheet != null && sheet.isShowing()) sheet.dismiss();
                AppToast.show(PurchaseOrdersActivity.this,
                        "Completed".equalsIgnoreCase(status)
                                ? "Đã duyệt PO và cộng tồn kho thành công!"
                                : "Đã hủy đơn đặt hàng nhập PO!",
                        AppToast.Type.SUCCESS);
                viewModel.fetchPurchaseOrders();
            }

            @Override
            public void onError(String message) {
                if (btnProceed != null) {
                    btnProceed.setEnabled(true);
                    btnProceed.setText(defaultProceedLabel);
                }
                if (btnCancel != null) btnCancel.setEnabled(true);
                AppToast.show(PurchaseOrdersActivity.this, message, AppToast.Type.ERROR);
            }
        });
    }

    /**
     * Gọi API xóa vĩnh viễn một phiếu nhập hàng đang ở trạng thái Chờ duyệt (Pending) và làm mới danh sách.
     */
    private void deletePendingPo(
            final int poId,
            final BottomSheetDialog sheet,
            final MaterialButton btnProceed,
            final MaterialButton btnCancel,
            final String defaultProceedLabel
    ) {
        repository.deletePurchase(poId, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (sheet != null && sheet.isShowing()) sheet.dismiss();
                AppToast.show(PurchaseOrdersActivity.this, "Đã xóa phiếu nhập #PO-" + poId + "!", AppToast.Type.SUCCESS);
                viewModel.fetchPurchaseOrders();
            }

            @Override
            public void onError(String message) {
                if (btnProceed != null) {
                    btnProceed.setEnabled(true);
                    btnProceed.setText(defaultProceedLabel);
                }
                if (btnCancel != null) btnCancel.setEnabled(true);
                AppToast.show(PurchaseOrdersActivity.this, message, AppToast.Type.ERROR);
            }
        });
    }
}
