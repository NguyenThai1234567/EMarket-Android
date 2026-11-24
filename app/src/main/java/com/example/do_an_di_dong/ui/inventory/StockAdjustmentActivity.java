package com.example.do_an_di_dong.ui.inventory;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityStockAdjustmentBinding;
import com.example.do_an_di_dong.model.InventoryItem;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.StockAdjustmentRequest;
import com.example.do_an_di_dong.model.Warehouse;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.ui.product.BarcodeScanActivity;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Kiểm kê & Cân bằng kho tương tác (Clone 1:1 của stocktaking_screen.dart).
 * Tích hợp Quét mã vạch Camera liên tục, chọn thêm SP từ danh mục, và nút cộng/trừ trực tiếp (-5, -1, +1, +5).
 * - Layout XML: activity_stock_adjustment.xml (ActivityStockAdjustmentBinding), sheet_confirm_stocktake.xml, item_stocktake_product.xml
 * - Adapter nội bộ: StocktakeAdapter
 * - Data layer: DataRepository (tải kho, tải tồn kho, tìm SP theo mã vạch và gửi chuỗi request adjustStock).
 */
public class StockAdjustmentActivity extends AppCompatActivity {

    private ActivityStockAdjustmentBinding binding;
    private DataRepository repository;
    private StocktakeAdapter adapter;

    private final List<Warehouse> warehouseList = new ArrayList<>();
    private Warehouse selectedWarehouse;

    private final List<InventoryItem> allItems = new ArrayList<>();
    private final Map<Integer, Integer> actualCounts = new HashMap<>();

    private String searchQuery = "";
    private String filterMode = "ALL"; // ALL, DISCREPANCY, LOW_STOCK
    private int focusProductId = -1;

    private final ActivityResultLauncher<Intent> barcodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("scanned_barcode");
                    if (barcode != null && !barcode.trim().isEmpty()) {
                        handleScannedBarcode(barcode.trim());
                    }
                }
            });

    /**
     * Khởi tạo màn hình kiểm kê, đọc PRODUCT_ID cần focus (nếu mở từ màn Chi tiết sản phẩm hoặc Tồn kho)
     * và tải danh sách kho hàng.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStockAdjustmentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        focusProductId = getIntent().getIntExtra("PRODUCT_ID", -1);
        if (focusProductId <= 0) {
            focusProductId = getIntent().getIntExtra("product_id", -1);
        }

        setupViews();
        loadWarehouses();
    }

    /**
     * Thiết lập sự kiện cho các nút chức năng: chọn kho, quét mã vạch Camera, thêm SP ngoài tồn kho,
     * ô tìm kiếm, bộ lọc Chip (Tất cả / Chênh lệch / Sắp hết), khôi phục số đếm và nút Lưu cân bằng kho.
     */
    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new StocktakeAdapter();
        binding.rvStocktakeItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStocktakeItems.setAdapter(adapter);

        binding.btnSwitchWarehouse.setOnClickListener(v -> {
            if (warehouseList.isEmpty()) {
                com.example.do_an_di_dong.util.AppToast.show(this, "Đang tải danh sách kho...", Toast.LENGTH_SHORT);
                return;
            }
            ModernPickerSheets.showWarehousePicker(this, warehouseList, warehouse -> {
                selectedWarehouse = warehouse;
                binding.tvSelectedWarehouseSubtitle.setText("Kho: " + warehouse.getName());
                loadInventoryForWarehouse();
            });
        });

        // Mở màn hình Camera quét mã vạch để cộng nhanh số lượng kiểm kê thực tế (+1 mỗi lần quét)
        binding.btnScanStocktakeBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            barcodeLauncher.launch(intent);
        });

        binding.btnAddExternalProduct.setOnClickListener(v -> {
            ModernPickerSheets.showProductPicker(
                    this,
                    null,
                    true,
                    () -> barcodeLauncher.launch(new Intent(this, BarcodeScanActivity.class)),
                    this::addOrIncrementProductInStocktake
            );
        });

        binding.etSearchStocktake.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> {
            searchQuery = q;
            applyFilters();
        }));

        binding.chipGroupStocktakeFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFilterDiscrepancy) {
                filterMode = "DISCREPANCY";
            } else if (id == R.id.chipFilterLowStock) {
                filterMode = "LOW_STOCK";
            } else {
                filterMode = "ALL";
            }
            applyFilters();
        });

        // Khôi phục toàn bộ số lượng kiểm kê thực tế (actualCounts) quay về bằng số lượng tồn hệ thống ban đầu
        binding.btnResetCounts.setOnClickListener(v -> {
            for (InventoryItem item : allItems) {
                actualCounts.put(item.getProductId(), item.getQuantity());
            }
            applyFilters();
            updateSummaryFooter();
            com.example.do_an_di_dong.util.AppToast.show(this, "Đã khôi phục số lượng về tồn hệ thống", Toast.LENGTH_SHORT);
        });

        binding.btnSaveStockAdjustment.setOnClickListener(v -> showConfirmStocktakeSheet());
    }

    /**
     * Tải danh sách kho hàng của chi nhánh hiện tại từ DataRepository.
     * Tự động chọn kho khớp với warehouse_id truyền qua Intent (hoặc mặc định kho đầu tiên).
     */
    private void loadWarehouses() {
        binding.progressStocktake.setVisibility(View.VISIBLE);
        int intentWarehouseId = getIntent().getIntExtra("warehouse_id", -1);
        Integer branchId = UserSessionManager.getCurrentBranchId(this);
        Integer effectiveBranch = (branchId != null && branchId > 0) ? branchId : null;

        repository.getFilteredWarehouses(null, effectiveBranch, new DataRepository.ApiCallback<List<Warehouse>>() {
            @Override
            public void onSuccess(List<Warehouse> result) {
                warehouseList.clear();
                if (result != null && !result.isEmpty()) {
                    warehouseList.addAll(result);
                    selectedWarehouse = warehouseList.get(0);
                    if (intentWarehouseId > 0) {
                        for (Warehouse w : warehouseList) {
                            if (w.getWarehouseId() == intentWarehouseId) {
                                selectedWarehouse = w;
                                break;
                            }
                        }
                    }
                    binding.tvSelectedWarehouseSubtitle.setText("Kho: " + selectedWarehouse.getName());
                    loadInventoryForWarehouse();
                } else {
                    binding.progressStocktake.setVisibility(View.GONE);
                    binding.tvSelectedWarehouseSubtitle.setText("Chưa có kho hàng");
                    binding.tvStocktakeEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                binding.progressStocktake.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(StockAdjustmentActivity.this, "Lỗi tải danh sách kho: " + message, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Tải danh sách tồn kho của kho đang chọn, gộp (merge) các lô cùng productId thành tổng tồn hệ thống của từng SP,
     * và khởi tạo bảng đếm thực tế (actualCounts) bằng tồn hệ thống.
     */
    private void loadInventoryForWarehouse() {
        if (selectedWarehouse == null) return;
        binding.progressStocktake.setVisibility(View.VISIBLE);
        binding.tvStocktakeEmpty.setVisibility(View.GONE);

        repository.getInventoryItems(selectedWarehouse.getWarehouseId(), null, null, new DataRepository.ApiCallback<List<InventoryItem>>() {
            @Override
            public void onSuccess(List<InventoryItem> items) {
                binding.progressStocktake.setVisibility(View.GONE);
                allItems.clear();
                actualCounts.clear();

                if (items != null) {
                    // Gộp tồn kho theo productId trong cùng 1 kho để kiểm kê chuẩn xác
                    Map<Integer, InventoryItem> merged = new HashMap<>();
                    for (InventoryItem raw : items) {
                        int pid = raw.getProductId();
                        if (merged.containsKey(pid)) {
                            InventoryItem existing = merged.get(pid);
                            existing.setQuantity(existing.getQuantity() + raw.getQuantity());
                        } else {
                            InventoryItem copy = new InventoryItem();
                            copy.setInventoryId(raw.getInventoryId());
                            copy.setProductId(pid);
                            copy.setProductName(raw.getProductName());
                            copy.setBarcode(raw.getBarcode());
                            copy.setUnit(raw.getUnit());
                            copy.setWarehouseId(raw.getWarehouseId());
                            copy.setWarehouseName(raw.getWarehouseName());
                            copy.setQuantity(raw.getQuantity());
                            copy.setPrice(raw.getPrice());
                            merged.put(pid, copy);
                        }
                    }
                    allItems.addAll(merged.values());
                    for (InventoryItem item : allItems) {
                        actualCounts.put(item.getProductId(), item.getQuantity());
                    }
                }

                // Nếu mở từ Chi tiết sản phẩm mà SP chưa có trong kho này, tự động nạp SP đó vào đầu danh sách
                if (focusProductId > 0 && !actualCounts.containsKey(focusProductId)) {
                    repository.getProductById(focusProductId, new DataRepository.ApiCallback<Product>() {
                        @Override
                        public void onSuccess(Product p) {
                            if (p != null) {
                                addOrIncrementProductInStocktake(p);
                            }
                        }

                        @Override
                        public void onError(String message) {}
                    });
                }

                applyFilters();
                updateSummaryFooter();
            }

            @Override
            public void onError(String message) {
                binding.progressStocktake.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(StockAdjustmentActivity.this, "Lỗi tải tồn kho: " + message, Toast.LENGTH_SHORT);
                applyFilters();
            }
        });
    }

    /**
     * Xử lý mã vạch vừa quét từ BarcodeScanActivity:
     * Nếu SP đã có trong danh sách kiểm kê của kho thì tăng số lượng thực tế thêm +1;
     * nếu chưa có thì gọi API tìm kiếm sản phẩm trong danh mục chung để thêm mới vào phiên kiểm kê.
     */
    private void handleScannedBarcode(final String barcode) {
        String clean = barcode.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < allItems.size(); i++) {
            InventoryItem item = allItems.get(i);
            String itemCode = item.getBarcode() != null ? item.getBarcode().trim().toLowerCase(Locale.ROOT) : "";
            if (itemCode.equals(clean) || String.valueOf(item.getProductId()).equals(clean)) {
                int pid = item.getProductId();
                int currentActual = actualCounts.containsKey(pid) ? actualCounts.get(pid) : item.getQuantity();
                int nextActual = currentActual + 1;
                actualCounts.put(pid, nextActual);
                int diff = nextActual - item.getQuantity();
                String diffStr = diff >= 0 ? ("+" + diff) : String.valueOf(diff);
                com.example.do_an_di_dong.util.AppToast.show(this, "Đã quét [" + item.getProductName() + "] → Thực tế: " + nextActual + " (" + diffStr + ")", Toast.LENGTH_SHORT);
                applyFilters();
                updateSummaryFooter();
                return;
            }
        }

        // Nếu mã vạch chưa có trong danh sách tồn kho hiện tại, tìm trong danh mục sản phẩm chung
        repository.searchProductsSimple(barcode, true, new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                if (result != null && !result.isEmpty()) {
                    addOrIncrementProductInStocktake(result.get(0));
                } else {
                    com.example.do_an_di_dong.util.AppToast.show(StockAdjustmentActivity.this, "Không tìm thấy sản phẩm có mã vạch: " + barcode, Toast.LENGTH_LONG);
                }
            }

            @Override
            public void onError(String message) {
                com.example.do_an_di_dong.util.AppToast.show(StockAdjustmentActivity.this, "Không tìm thấy mã vạch: " + barcode, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Thêm một sản phẩm từ danh mục vào danh sách kiểm kê (với tồn hệ thống = 0, thực tế = 1),
     * hoặc cộng thêm +1 vào số đếm thực tế nếu sản phẩm đó đã có sẵn trong danh sách.
     */
    private void addOrIncrementProductInStocktake(Product product) {
        if (product == null) return;
        int pid = product.getProductId();
        for (InventoryItem existing : allItems) {
            if (existing.getProductId() == pid) {
                int current = actualCounts.containsKey(pid) ? actualCounts.get(pid) : existing.getQuantity();
                int next = current + 1;
                actualCounts.put(pid, next);
                applyFilters();
                updateSummaryFooter();
                com.example.do_an_di_dong.util.AppToast.show(this, "Đã cộng +1 cho: " + existing.getProductName() + " (Thực tế: " + next + ")", Toast.LENGTH_SHORT);
                return;
            }
        }

        InventoryItem newItem = new InventoryItem();
        newItem.setProductId(pid);
        newItem.setProductName(product.getName());
        newItem.setBarcode(product.getSku());
        newItem.setUnit(product.getUnit() != null ? product.getUnit() : "Cái");
        newItem.setWarehouseId(selectedWarehouse != null ? selectedWarehouse.getWarehouseId() : 1);
        newItem.setQuantity(0);
        newItem.setPrice(product.getPrice());

        allItems.add(0, newItem);
        actualCounts.put(pid, 1);
        applyFilters();
        updateSummaryFooter();
        binding.rvStocktakeItems.scrollToPosition(0);
        com.example.do_an_di_dong.util.AppToast.show(this, "Đã thêm [" + product.getName() + "] vào danh sách kiểm kê (+1)", Toast.LENGTH_SHORT);
    }

    /**
     * Lọc danh sách sản phẩm kiểm kê theo chế độ Chip (Tất cả / Có chênh lệch diff != 0 / Tồn thấp < 10)
     * kết hợp với từ khóa tìm kiếm (tên, mã SKU, ID).
     */
    private void applyFilters() {
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<InventoryItem> filtered = new ArrayList<>();

        for (InventoryItem item : allItems) {
            int pid = item.getProductId();
            int sysQty = item.getQuantity();
            int actQty = actualCounts.containsKey(pid) ? actualCounts.get(pid) : sysQty;
            int diff = actQty - sysQty;

            if ("DISCREPANCY".equals(filterMode) && diff == 0) {
                continue;
            }
            if ("LOW_STOCK".equals(filterMode) && actQty >= 10) {
                continue;
            }

            if (!q.isEmpty()) {
                String name = item.getProductName().toLowerCase(Locale.ROOT);
                String code = item.getBarcode() != null ? item.getBarcode().toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !code.contains(q) && !String.valueOf(pid).equals(q)) {
                    continue;
                }
            }

            filtered.add(item);
        }

        adapter.setItems(filtered);
        binding.tvStocktakeEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (filtered.isEmpty()) {
            if (allItems.isEmpty() && selectedWarehouse != null) {
                binding.tvStocktakeEmpty.setText("Kho \"" + selectedWarehouse.getName() + "\" chưa có mặt hàng tồn kho.\nHãy tạo Phiếu nhập hàng (PO) trước hoặc quét/chọn sản phẩm.");
            } else {
                binding.tvStocktakeEmpty.setText("Không có mặt hàng nào phù hợp bộ lọc.");
            }
        }
    }

    /**
     * Tính toán lại thống kê dưới thanh Footer: số mặt hàng có chênh lệch (act != sys),
     * tổng lượng tăng (+), tổng lượng giảm (-) và bật/tắt nút "Lưu cân bằng kho".
     */
    private void updateSummaryFooter() {
        int changedItems = 0;
        int totalPlus = 0;
        int totalMinus = 0;

        for (InventoryItem item : allItems) {
            int pid = item.getProductId();
            int sys = item.getQuantity();
            int act = actualCounts.containsKey(pid) ? actualCounts.get(pid) : sys;
            int diff = act - sys;
            if (diff != 0) {
                changedItems++;
                if (diff > 0) totalPlus += diff;
                else totalMinus += Math.abs(diff);
            }
        }

        if (changedItems == 0) {
            binding.tvChangedCount.setText("0 mặt hàng thay đổi");
            binding.tvChangedHint.setText("Chạm +/- hoặc Quét mã vạch để điều chỉnh");
            binding.btnSaveStockAdjustment.setEnabled(false);
            binding.btnSaveStockAdjustment.setText("Cân bằng kho");
            binding.btnResetCounts.setVisibility(View.GONE);
        } else {
            binding.tvChangedCount.setText(changedItems + " mặt hàng chênh lệch");
            binding.tvChangedHint.setText("Tăng: +" + totalPlus + "  •  Giảm: -" + totalMinus);
            binding.btnSaveStockAdjustment.setEnabled(true);
            binding.btnSaveStockAdjustment.setText("Lưu (" + changedItems + ")");
            binding.btnResetCounts.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hiển thị BottomSheet xác nhận cân bằng kho cho các mặt hàng có chênh lệch.
     * Người dùng chọn nhanh mẫu lý do (Chip) hoặc nhập lý do tùy chỉnh (validate tối thiểu 3 ký tự) trước khi gửi API.
     */
    private void showConfirmStocktakeSheet() {
        if (selectedWarehouse == null) {
            com.example.do_an_di_dong.util.AppToast.show(this, "Vui lòng chọn kho hàng!", Toast.LENGTH_SHORT);
            return;
        }

        final List<InventoryItem> changedList = new ArrayList<>();
        for (InventoryItem item : allItems) {
            int pid = item.getProductId();
            int sys = item.getQuantity();
            int act = actualCounts.containsKey(pid) ? actualCounts.get(pid) : sys;
            if (act != sys) {
                changedList.add(item);
            }
        }

        if (changedList.isEmpty()) return;

        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_confirm_stocktake, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvConfirmStocktakeTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvConfirmStocktakeSubtitle);
        ChipGroup chipGroupReason = view.findViewById(R.id.chipGroupStocktakeReason);
        TextInputLayout tilReason = view.findViewById(R.id.tilCustomReason);
        TextInputEditText etReason = view.findViewById(R.id.etCustomReason);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelConfirmStocktake);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitConfirmStocktake);

        tvTitle.setText("Xác nhận cân bằng " + changedList.size() + " mặt hàng");
        tvSubtitle.setText("Kho áp dụng: " + selectedWarehouse.getName());

        chipGroupReason.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipReasonPeriodic) {
                etReason.setText("Kiểm kê định kỳ tại " + selectedWarehouse.getName());
            } else if (id == R.id.chipReasonDamaged) {
                etReason.setText("Xuất hủy hàng hư hỏng / hết hạn sử dụng");
            } else if (id == R.id.chipReasonRestock) {
                etReason.setText("Nhập bổ sung hàng thực tế tại kho");
            } else if (id == R.id.chipReasonLoss) {
                etReason.setText("Điều chỉnh hao hụt / thất thoát kiểm kê");
            }
        });

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .minLength(tilReason, etReason, "Lý do điều chỉnh kho", 3, true)
                    .validate();
            if (!valid) {
                return;
            }
            String reason = com.example.do_an_di_dong.util.FormValidator.textOf(etReason);

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Đang đồng bộ...");

            submitAdjustmentsSequentially(changedList, 0, reason, sheet, btnSubmit);
        });

        sheet.show();
    }

    /**
     * Gửi tuần tự (đệ quy theo index) từng yêu cầu điều chỉnh tồn kho (StockAdjustmentRequest) lên Backend.
     * Mỗi request gửi lượng chênh lệch `diff = act - sys` kèm lý do ghi rõ số tồn trước và sau kiểm kê.
     */
    private void submitAdjustmentsSequentially(
            final List<InventoryItem> items,
            final int index,
            final String reason,
            final BottomSheetDialog sheet,
            final MaterialButton btnSubmit
    ) {
        if (index >= items.size()) {
            sheet.dismiss();
            com.example.do_an_di_dong.util.AppToast.show(this, "Đã cân bằng kho thành công cho " + items.size() + " mặt hàng!", Toast.LENGTH_LONG);
            finish();
            return;
        }

        InventoryItem item = items.get(index);
        int pid = item.getProductId();
        int sys = item.getQuantity();
        int act = actualCounts.containsKey(pid) ? actualCounts.get(pid) : sys;
        int diff = act - sys;

        int userId = UserSessionManager.getCurrentUserId(this);
        StockAdjustmentRequest req = new StockAdjustmentRequest(
                pid,
                selectedWarehouse.getWarehouseId(),
                diff,
                "ADJUSTMENT",
                reason + " (Hệ thống: " + sys + " -> Thực tế: " + act + ")",
                userId > 0 ? userId : 1
        );

        repository.adjustStock(req, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                submitAdjustmentsSequentially(items, index + 1, reason, sheet, btnSubmit);
            }

            @Override
            public void onError(String message) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Xác nhận & Cân bằng kho");
                String detail = (message != null && !message.isEmpty()) ? message : "Không thể điều chỉnh tồn kho.";
                // Bổ sung gợi ý nghiệp vụ nếu SP chưa từng có lô hàng (tồn = 0) mà bị Backend từ chối điều chỉnh
                if (detail.toLowerCase(Locale.ROOT).contains("lô hàng") && item.getQuantity() <= 0) {
                    detail = detail + "\n\nGợi ý: Mặt hàng có tồn hiện tại bằng 0 cần được tạo Đơn nhập hàng (PO) để khởi tạo lô trước khi kiểm kê điều chỉnh.";
                }
                com.example.do_an_di_dong.ui.common.ModernConfirmSheet.noticeDanger(
                        StockAdjustmentActivity.this,
                        "Lỗi điều chỉnh [" + item.getProductName() + "]",
                        detail,
                        "Đóng"
                );
            }
        });
    }

    // =========================================================================
    // ADAPTER CHO DANH SÁCH KIỂM KÊ TƯƠNG TÁC (-5, -1, +1, +5)
    // =========================================================================

    private class StocktakeAdapter extends RecyclerView.Adapter<StocktakeAdapter.VH> {
        private List<InventoryItem> list = new ArrayList<>();

        /**
         * Cập nhật danh sách mặt hàng hiển thị trong RecyclerView kiểm kê.
         */
        void setItems(List<InventoryItem> items) {
            this.list = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stocktake_product, parent, false);
            return new VH(v);
        }

        /**
         * Hiển thị thông tin kiểm kê của từng SP: tồn hệ thống, tồn thực tế, badge chênh lệch (Khớp kho / Thừa / Thiếu),
         * hộp thoại nhập số lượng trực tiếp khi chạm vào số thực tế, và các nút cộng/trừ nhanh (-5, -1, +1, +5).
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final InventoryItem item = list.get(position);
            final int pid = item.getProductId();
            final int sysQty = item.getQuantity();
            final int actQty = actualCounts.containsKey(pid) ? actualCounts.get(pid) : sysQty;
            final int diff = actQty - sysQty;

            String name = item.getProductName();
            holder.tvAvatar.setText(!name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "P");
            holder.tvName.setText(name);

            String sku = item.getBarcode() != null && !item.getBarcode().isEmpty()
                    ? item.getBarcode()
                    : ("SP #" + pid);
            holder.tvSku.setText(sku);

            String unit = item.getUnit() != null && !item.getUnit().isEmpty() ? (" " + item.getUnit()) : "";
            holder.tvSysQty.setText("Tồn hệ thống: " + sysQty + unit);
            holder.tvActQty.setText(String.valueOf(actQty));

            // Đổi màu viền Card và Badge theo độ lệch giữa thực tế và hệ thống (diff = actQty - sysQty)
            if (diff == 0) {
                holder.tvDiffBadge.setText("Khớp kho");
                holder.tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                holder.tvDiffBadge.setTextColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.text_secondary));
                holder.card.setStrokeColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.outline_variant));
            } else if (diff > 0) {
                holder.tvDiffBadge.setText("+" + diff + " Thừa");
                holder.tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_success);
                holder.tvDiffBadge.setTextColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.success));
                holder.card.setStrokeColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.success));
            } else {
                holder.tvDiffBadge.setText(diff + " Thiếu");
                holder.tvDiffBadge.setBackgroundResource(R.drawable.bg_badge_error);
                holder.tvDiffBadge.setTextColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.error));
                holder.card.setStrokeColor(ContextCompat.getColor(StockAdjustmentActivity.this, R.color.error));
            }

            // Bấm vào ô số lượng thực tế để mở Dialog nhập số bất kỳ từ bàn phím
            holder.tvActQty.setOnClickListener(v -> {
                com.example.do_an_di_dong.util.QuantityInputDialog.show(
                        StockAdjustmentActivity.this,
                        item.getProductName(),
                        actQty,
                        0,
                        999999,
                        item.getUnit(),
                        newQty -> updateCount(pid, newQty, holder.getBindingAdapterPosition())
                );
            });

            holder.btnMinus5.setOnClickListener(v -> updateCount(pid, Math.max(0, actQty - 5), holder.getBindingAdapterPosition()));
            holder.btnMinus1.setOnClickListener(v -> updateCount(pid, Math.max(0, actQty - 1), holder.getBindingAdapterPosition()));
            holder.btnPlus1.setOnClickListener(v -> updateCount(pid, actQty + 1, holder.getBindingAdapterPosition()));
            holder.btnPlus5.setOnClickListener(v -> updateCount(pid, actQty + 5, holder.getBindingAdapterPosition()));
        }

        /**
         * Cập nhật số đếm thực tế mới vào Map actualCounts, làm mới dòng giao diện và tính lại tổng chênh lệch ở Footer.
         */
        private void updateCount(int productId, int newCount, int adapterPos) {
            actualCounts.put(productId, newCount);
            if ("DISCREPANCY".equals(filterMode)) {
                applyFilters();
            } else if (adapterPos != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPos);
            } else {
                notifyDataSetChanged();
            }
            updateSummaryFooter();
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView tvAvatar;
            final TextView tvName;
            final TextView tvSku;
            final TextView tvSysQty;
            final TextView tvDiffBadge;
            final TextView btnMinus5;
            final TextView btnMinus1;
            final TextView tvActQty;
            final TextView btnPlus1;
            final TextView btnPlus5;

            VH(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardStocktakeItem);
                tvAvatar = itemView.findViewById(R.id.tvStocktakeAvatar);
                tvName = itemView.findViewById(R.id.tvStocktakeProductName);
                tvSku = itemView.findViewById(R.id.tvStocktakeSkuBadge);
                tvSysQty = itemView.findViewById(R.id.tvStocktakeSystemQty);
                tvDiffBadge = itemView.findViewById(R.id.tvStocktakeDiffBadge);
                btnMinus5 = itemView.findViewById(R.id.btnMinus5);
                btnMinus1 = itemView.findViewById(R.id.btnMinus1);
                tvActQty = itemView.findViewById(R.id.tvActualQtyValue);
                btnPlus1 = itemView.findViewById(R.id.btnPlus1);
                btnPlus5 = itemView.findViewById(R.id.btnPlus5);
            }
        }
    }
}
