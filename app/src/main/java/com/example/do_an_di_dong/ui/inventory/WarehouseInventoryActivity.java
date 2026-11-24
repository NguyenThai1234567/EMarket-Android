package com.example.do_an_di_dong.ui.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityWarehouseInventoryBinding;
import com.example.do_an_di_dong.model.InventoryItem;
import com.example.do_an_di_dong.model.StockMovement;
import com.example.do_an_di_dong.model.Warehouse;
import com.example.do_an_di_dong.ui.common.AppNavigationShell;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.ui.product.ProductDetailActivity;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.Formatters;
import com.google.android.material.tabs.TabLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Kho bãi & Biến động kho (Tab chính thứ 4) - Kiến trúc Zero-Fragment (0 Fragment).
 * Tích hợp trực tiếp 2 Tab: Tồn kho thực tế & Biến động kho trong cùng 1 Activity.
 * - Layout XML: activity_warehouse_inventory.xml (ActivityWarehouseInventoryBinding)
 * - Adapter: InventoryItemAdapter (Tab 1 - Tồn kho), StockMovementAdapter (Tab 2 - Biến động kho)
 * - Data layer: DataRepository (gọi API tồn kho, danh sách kho, lịch sử biến động phân trang) & ScreenRealtimeSync.
 */
public class WarehouseInventoryActivity extends AppCompatActivity {

    private ActivityWarehouseInventoryBinding binding;
    private DataRepository repository;
    private InventoryItemAdapter inventoryAdapter;
    private StockMovementAdapter movementAdapter;

    private final List<Warehouse> warehouseList = new ArrayList<>();
    private final List<StockMovement> allMovements = new ArrayList<>();
    private Warehouse selectedWarehouse = null; // null = Tất cả kho hàng
    private String currentMovementFilter = "ALL";
    private String currentSearchQuery = "";
    private int filterProductId = -1;
    private final NumberFormat numberFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    /**
     * Khởi tạo màn hình, nạp layout ViewBinding, thiết lập thanh điều hướng (Drawer + BottomNav)
     * và tải danh sách kho hàng ban đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWarehouseInventoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);

        // Khởi tạo khung điều hướng chung; khi người dùng đổi chi nhánh trên header thì tải lại danh sách kho
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_warehouse,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> {
                    loadWarehouses();
                }
        );

        setupViews();
        handleIncomingIntent(getIntent());
        loadWarehouses();
    }

    /**
     * Nhận Intent mới khi Activity đang chạy (chế độ singleTop/clearTop) để lọc theo sản phẩm được truyền sang.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    /**
     * Đọc tham số PRODUCT_ID và PRODUCT_NAME từ Intent (khi điều hướng từ màn Chi tiết sản phẩm sang).
     * Nếu có PRODUCT_ID hợp lệ thì tự động điền ô tìm kiếm và chuyển sang Tab 2 (Biến động kho).
     */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        int pid = intent.getIntExtra("PRODUCT_ID", -1);
        String pName = intent.getStringExtra("PRODUCT_NAME");
        if (pid > 0) {
            filterProductId = pid;
            if (pName != null && !pName.isEmpty()) {
                binding.etSearch.setText(pName);
                currentSearchQuery = pName;
            }
            TabLayout.Tab movementTab = binding.tabLayout.getTabAt(1);
            if (movementTab != null) {
                movementTab.select();
            }
        }
    }

    private com.example.do_an_di_dong.data.realtime.ScreenRealtimeSync movementsRealtimeSync;
    private boolean isMovementsHostResumed = false;

    /**
     * Khi màn hình quay lại foreground: làm mới danh sách tồn kho & biến động kho (sau khi kiểm kê hoặc nhập hàng),
     * đồng thời bật kết nối đồng bộ Realtime cho tab Biến động kho.
     */
    @Override
    protected void onResume() {
        super.onResume();
        isMovementsHostResumed = true;
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_warehouse,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> {
                    loadWarehouses();
                }
        );
        if (!warehouseList.isEmpty()) {
            loadInventory();
            loadMovements();
        } else {
            loadWarehouses();
        }
        syncMovementsRealtimeState();
    }

    /**
     * Tạm dừng lắng nghe sự kiện Realtime khi người dùng rời khỏi màn hình để tiết kiệm tài nguyên.
     */
    @Override
    protected void onPause() {
        isMovementsHostResumed = false;
        syncMovementsRealtimeState();
        super.onPause();
    }

    /**
     * Giải phóng đối tượng đồng bộ Realtime khi Activity bị hủy hoàn toàn.
     */
    @Override
    protected void onDestroy() {
        if (movementsRealtimeSync != null) {
            movementsRealtimeSync.destroy();
            movementsRealtimeSync = null;
        }
        super.onDestroy();
    }

    /**
     * Quản lý bật/tắt kết nối Realtime (SSE/WebSocket polling) cho tab Biến động kho.
     * Chỉ kích hoạt khi Activity đang ở trạng thái Resumed và người dùng đang mở Tab 2 (Biến động kho).
     */
    private void syncMovementsRealtimeState() {
        boolean onMovementsTab = binding.tabLayout != null && binding.tabLayout.getSelectedTabPosition() == 1;
        boolean active = isMovementsHostResumed && onMovementsTab && !isFinishing() && !isDestroyed();
        if (movementsRealtimeSync == null) {
            if (!active) return;
            movementsRealtimeSync = new com.example.do_an_di_dong.data.realtime.ScreenRealtimeSync(
                    this,
                    () -> {
                        if (!isFinishing() && !isDestroyed()) {
                            loadMovements();
                        }
                    }
            );
        }
        // Truyền thông tin chi nhánh và quyền Admin (RBAC) để đăng ký kênh đồng bộ phù hợp
        int branchId = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(this);
        boolean isAdmin = com.example.do_an_di_dong.util.UserSessionManager.isAdmin(this);
        movementsRealtimeSync.setActive(active, branchId, isAdmin);
    }

    /**
     * Thiết lập các thành phần giao diện: sự kiện nút Đơn nhập hàng (PO), nút FAB Kiểm kê kho,
     * RecyclerView cho 2 tab, ô tìm kiếm có debounce, bộ lọc Chip và chuyển đổi TabLayout.
     */
    private void setupViews() {
        binding.btnPurchaseOrders.setOnClickListener(v -> {
            startActivity(new Intent(this, PurchaseOrdersActivity.class));
        });

        // Mở màn hình Kiểm kê & Điều chỉnh tồn kho, truyền kèm kho đang chọn nếu có
        binding.fabStockAdjustment.setOnClickListener(v -> {
            Intent intent = new Intent(this, StockAdjustmentActivity.class);
            if (selectedWarehouse != null && selectedWarehouse.getWarehouseId() > 0) {
                intent.putExtra("warehouse_id", selectedWarehouse.getWarehouseId());
                intent.putExtra("warehouse_name", selectedWarehouse.getName());
            }
            startActivity(intent);
        });

        // Tab 1 Adapter
        inventoryAdapter = new InventoryItemAdapter();
        binding.rvInventory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInventory.setAdapter(inventoryAdapter);
        binding.swipeRefresh.setOnRefreshListener(this::loadInventory);

        inventoryAdapter.setOnInventoryItemClickListener(this::showInventoryItemActions);

        // Tab 2 Adapter
        movementAdapter = new StockMovementAdapter();
        binding.rvMovements.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMovements.setAdapter(movementAdapter);
        binding.swipeRefreshMovements.setOnRefreshListener(this::loadMovements);

        movementAdapter.setOnMovementClickListener(this::showMovementDetailDialog);

        // Lắng nghe ô tìm kiếm (có debounce chống giật) để lọc đồng thời cả danh sách tồn kho và biến động kho
        binding.etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> {
            currentSearchQuery = q;
            if (currentSearchQuery.isEmpty()) {
                filterProductId = -1;
            }
            inventoryAdapter.filter(currentSearchQuery);
            applyMovementFilter();
            updateEmptyState();
        }));

        // Lọc lịch sử biến động kho theo phân loại: Nhập kho (IN), Xuất kho (OUT), Kiểm kê (ADJUST) hoặc Tất cả (ALL)
        binding.chipGroupMovementFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipMoveIn) {
                currentMovementFilter = "IN";
            } else if (checkedId == R.id.chipMoveOut) {
                currentMovementFilter = "OUT";
            } else if (checkedId == R.id.chipMoveAdjust) {
                currentMovementFilter = "ADJUST";
            } else {
                currentMovementFilter = "ALL";
            }
            if (binding.tabLayout.getSelectedTabPosition() == 1) {
                loadMovements();
            } else {
                applyMovementFilter();
            }
        });

        // Switch between Tab 1 (Inventory) and Tab 2 (Movements) inside same Activity (0 Fragment)
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.layoutTabInventory.setVisibility(View.VISIBLE);
                    binding.layoutTabMovements.setVisibility(View.GONE);
                    updateEmptyState();
                } else {
                    binding.layoutTabInventory.setVisibility(View.GONE);
                    binding.layoutTabMovements.setVisibility(View.VISIBLE);
                    if (allMovements.isEmpty()) {
                        loadMovements();
                    } else {
                        applyMovementFilter();
                    }
                }
                syncMovementsRealtimeState();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /**
     * Hiển thị BottomSheet menu thao tác nhanh khi người dùng chạm vào 1 mặt hàng trong Tab Tồn kho:
     * (1) Xem thẻ kho/biến động của SP, (2) Kiểm kê/điều chỉnh tồn nhanh, (3) Xem chi tiết sản phẩm.
     */
    private void showInventoryItemActions(final InventoryItem item) {
        if (item == null) return;
        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();

        actions.add(new ModernPickerSheets.ActionItem(
                "Xem thẻ kho / Biến động của SP này",
                "Chuyển sang tab Biến động kho và lọc theo " + item.getProductName(),
                R.drawable.bg_badge_info,
                R.color.primary,
                () -> {
                    filterProductId = item.getProductId();
                    binding.etSearch.setText(item.getProductName());
                    TabLayout.Tab tab = binding.tabLayout.getTabAt(1);
                    if (tab != null) tab.select();
                    applyMovementFilter();
                }
        ));

        // Đóng gói PRODUCT_ID và warehouse_id vào Intent để mở màn Kiểm kê đúng sản phẩm và kho đó
        actions.add(new ModernPickerSheets.ActionItem(
                "Kiểm kê / Điều chỉnh tồn nhanh",
                "Tồn hiện tại: " + item.getQuantity() + " " + (item.getUnit() != null ? item.getUnit() : ""),
                R.drawable.bg_badge_warning,
                R.color.warning_text,
                () -> {
                    Intent intent = new Intent(this, StockAdjustmentActivity.class);
                    intent.putExtra("PRODUCT_ID", item.getProductId());
                    if (item.getWarehouseId() > 0) {
                        intent.putExtra("warehouse_id", item.getWarehouseId());
                    } else if (selectedWarehouse != null && selectedWarehouse.getWarehouseId() > 0) {
                        intent.putExtra("warehouse_id", selectedWarehouse.getWarehouseId());
                    }
                    startActivity(intent);
                }
        ));

        actions.add(new ModernPickerSheets.ActionItem(
                "Xem chi tiết Sản phẩm",
                "Mở hồ sơ hàng hóa, lô hạn dùng và bộ sưu tập ảnh",
                R.drawable.bg_badge_success,
                R.color.success_text,
                () -> {
                    Intent intent = new Intent(this, ProductDetailActivity.class);
                    intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, item.getProductId());
                    startActivity(intent);
                }
        ));

        ModernPickerSheets.showActionSheet(
                this,
                item.getProductName(),
                "SKU: " + (item.getBarcode() != null ? item.getBarcode() : ("#" + item.getProductId()))
                        + " • Tồn: " + item.getQuantity(),
                actions
        );
    }

    /**
     * Hiển thị hộp thoại (BottomSheet) chi tiết một bản ghi biến động kho khi người dùng bấm vào dòng lịch sử ở Tab 2.
     * Cho phép bấm nút "Xem Sản phẩm" để nhảy sang màn hình ProductDetailActivity.
     */
    private void showMovementDetailDialog(StockMovement m) {
        if (m == null) return;
        String typeLabel = "Kiểm kê / Điều chỉnh";
        int q = m.getQuantity();
        String rawType = m.getType() != null ? m.getType().toUpperCase(Locale.ROOT) : "";
        if (rawType.contains("IN") || rawType.contains("NHẬP") || (!rawType.contains("ADJUST") && q > 0)) {
            typeLabel = "Nhập kho (+)";
        } else if (rawType.contains("OUT") || rawType.contains("XUẤT") || (!rawType.contains("ADJUST") && q < 0)) {
            typeLabel = "Xuất kho (-)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("• Sản phẩm: ").append(m.getProductName() != null ? m.getProductName() : ("#" + m.getProductId())).append("\n");
        if (m.getBarcode() != null && !m.getBarcode().isEmpty()) {
            sb.append("• Mã SKU/Barcode: ").append(m.getBarcode()).append("\n");
        }
        sb.append("• Phân loại: ").append(typeLabel).append("\n");
        sb.append("• Số lượng biến động: ").append(q > 0 ? ("+" + q) : String.valueOf(q)).append("\n");
        sb.append("• Kho hàng: ").append(m.getWarehouseName() != null ? m.getWarehouseName() : ("Kho #" + m.getWarehouseId())).append("\n");
        if (m.getBatchNumber() != null && !m.getBatchNumber().isEmpty()) {
            sb.append("• Số lô (Batch): ").append(m.getBatchNumber()).append("\n");
        }
        if (m.getReferenceType() != null && !m.getReferenceType().isEmpty()) {
            sb.append("• Chứng từ gốc: ").append(m.getReferenceType())
                    .append(m.getReferenceId() != null && m.getReferenceId() > 0 ? (" #" + m.getReferenceId()) : "").append("\n");
        }
        sb.append("• Người thực hiện: ").append(m.getUserName() != null ? m.getUserName() : "Hệ thống").append("\n");
        sb.append("• Thời gian: ").append(Formatters.dateTime(m.getCreatedDate())).append("\n");
        if (m.getReason() != null && !m.getReason().isEmpty()) {
            sb.append("• Lý do / Ghi chú: ").append(m.getReason());
        }

        com.example.do_an_di_dong.ui.common.ModernConfirmSheet.noticeWithAction(
                this,
                "Chi tiết Biến động kho #" + m.getMovementId(),
                sb.toString(),
                m.getProductId() > 0 ? "Xem Sản phẩm" : null,
                () -> {
                    if (m.getProductId() > 0) {
                        Intent intent = new Intent(this, ProductDetailActivity.class);
                        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, m.getProductId());
                        startActivity(intent);
                    }
                },
                "Đóng",
                null
        );
    }

    /**
     * Gọi DataRepository tải danh sách kho hàng thuộc chi nhánh hiện tại.
     * Có cơ chế fallback: nếu chi nhánh đang chọn chưa được gán kho nào, tự động tải toàn bộ kho trong hệ thống.
     */
    private void loadWarehouses() {
        binding.progressBar.setVisibility(View.VISIBLE);
        int curBranch = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(this);
        Integer effectiveBranch = curBranch > 0 ? curBranch : null;

        repository.getFilteredWarehouses(null, effectiveBranch, new DataRepository.ApiCallback<List<Warehouse>>() {
            @Override
            public void onSuccess(List<Warehouse> result) {
                // Fallback: nếu lọc theo chi nhánh trả về rỗng, gọi lại API không lọc branchId để lấy tất cả kho
                if ((result == null || result.isEmpty()) && effectiveBranch != null) {
                    repository.getFilteredWarehouses(null, null, new DataRepository.ApiCallback<List<Warehouse>>() {
                        @Override
                        public void onSuccess(List<Warehouse> allResult) {
                            bindWarehousesAndReload(allResult);
                        }

                        @Override
                        public void onError(String message) {
                            bindWarehousesAndReload(new ArrayList<>());
                        }
                    });
                    return;
                }
                bindWarehousesAndReload(result);
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);
                bindWarehousesAndReload(new ArrayList<>());
                AppToast.show(WarehouseInventoryActivity.this, "Lỗi tải kho: " + message);
            }
        });
    }

    /**
     * Đổ danh sách kho hàng vào Dropdown (AutoCompleteTextView), khôi phục lựa chọn kho trước đó (nếu có),
     * và kích hoạt tải dữ liệu Tồn kho + Biến động kho tương ứng.
     */
    private void bindWarehousesAndReload(List<Warehouse> result) {
        binding.progressBar.setVisibility(View.GONE);
        int prevSelectedId = selectedWarehouse != null ? selectedWarehouse.getWarehouseId() : 0;
        warehouseList.clear();

        // Option 0: Tất cả kho hàng (giống Flutter default)
        Warehouse allWh = new Warehouse(0, "Tất cả kho hàng (Toàn chuỗi)", "");
        warehouseList.add(allWh);
        if (result != null) {
            warehouseList.addAll(result);
        }

        List<String> names = new ArrayList<>();
        Warehouse restored = allWh;
        for (Warehouse w : warehouseList) {
            names.add(w.getName());
            if (prevSelectedId > 0 && w.getWarehouseId() == prevSelectedId) {
                restored = w;
            }
        }

        ArrayAdapter<String> warehouseAdapter = new ArrayAdapter<>(
                WarehouseInventoryActivity.this,
                android.R.layout.simple_dropdown_item_1line,
                names
        );
        binding.actvWarehouse.setAdapter(warehouseAdapter);

        selectedWarehouse = restored;
        binding.actvWarehouse.setText(selectedWarehouse.getName(), false);
        binding.tvSubtitle.setText("Kho: " + selectedWarehouse.getName());

        binding.actvWarehouse.setOnItemClickListener((parent, view, position, id) -> {
            selectedWarehouse = warehouseList.get(position);
            binding.tvSubtitle.setText("Kho: " + selectedWarehouse.getName());
            loadInventory();
            loadMovements();
        });

        loadInventory();
        loadMovements();
    }

    /**
     * Gọi API lấy danh sách tồn kho theo kho hoặc chi nhánh hiện tại.
     * Sau khi nhận dữ liệu: cập nhật Adapter, áp dụng bộ lọc từ khóa và tính tổng số mặt hàng, tổng tồn, tổng giá trị kho.
     */
    private void loadInventory() {
        binding.progressBar.setVisibility(View.VISIBLE);
        Integer whId = (selectedWarehouse != null && selectedWarehouse.getWarehouseId() > 0)
                ? selectedWarehouse.getWarehouseId()
                : null;
        int curBranch = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(this);
        Integer branchParam = (whId == null && curBranch > 0) ? curBranch : null;

        repository.getInventoryItems(whId, branchParam, null, new DataRepository.ApiCallback<List<InventoryItem>>() {
            @Override
            public void onSuccess(List<InventoryItem> items) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                List<InventoryItem> safeItems = items != null ? items : new ArrayList<>();
                inventoryAdapter.setItems(safeItems);
                if (!currentSearchQuery.isEmpty()) {
                    inventoryAdapter.filter(currentSearchQuery);
                }

                // Tính tổng số lượng tồn kho và tổng giá trị định giá (Valuation = sum(quantity * price))
                long totalQty = 0;
                double totalValuation = 0.0;
                for (InventoryItem item : safeItems) {
                    totalQty += item.getQuantity();
                    totalValuation += item.getTotalValuation();
                }

                binding.tvTotalItems.setText(numberFormat.format(safeItems.size()));
                binding.tvTotalQuantity.setText(numberFormat.format(totalQty));
                binding.tvTotalValuation.setText(Formatters.currency(totalValuation));
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                AppToast.show(WarehouseInventoryActivity.this, "Lỗi tải tồn kho: " + message);
                updateEmptyState();
            }
        });
    }

    /**
     * Tải lịch sử biến động kho từ Backend theo kho và nhóm phân loại (IN, OUT, ADJUST, ALL).
     * Gửi song song các truy vấn theo từng typeParam và gộp kết quả (khử trùng theo movementId) bằng AtomicInteger.
     */
    private void loadMovements() {
        final Integer whId = (selectedWarehouse != null && selectedWarehouse.getWarehouseId() > 0)
                ? selectedWarehouse.getWarehouseId()
                : null;
        final String keywordParam = (currentSearchQuery != null && !currentSearchQuery.trim().isEmpty() && filterProductId <= 0)
                ? currentSearchQuery.trim()
                : null;

        // Truy vấn theo đúng loại biến động trên backend để không bị giới hạn 100 dòng mới nhất (toàn đơn Sale) che khuất Import/Audit/Adjustment
        final String[] queryTypes;
        if ("IN".equals(currentMovementFilter)) {
            queryTypes = new String[]{"Import", "IN", "Return"};
        } else if ("OUT".equals(currentMovementFilter)) {
            queryTypes = new String[]{"Sale", "OUT", "ISSUE"};
        } else if ("ADJUST".equals(currentMovementFilter)) {
            queryTypes = new String[]{"ADJUSTMENT", "Audit", "Adjustment"};
        } else {
            queryTypes = new String[]{null, "Import", "Return", "ADJUSTMENT", "Audit"};
        }

        final java.util.Map<Integer, StockMovement> merged = new java.util.LinkedHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(queryTypes.length);
        final java.util.concurrent.atomic.AtomicBoolean anySuccess = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] lastError = new String[]{null};

        for (final String typeParam : queryTypes) {
            // Truyền branchId = null khi chọn "Tất cả kho hàng" để API không tự ép về duy nhất kho đầu tiên của chi nhánh
            repository.getStockMovementsPaged(0, 100, whId, null, typeParam, null, null, keywordParam,
                    new DataRepository.ApiCallback<com.example.do_an_di_dong.model.StockMovementPagedResponse>() {
                        @Override
                        public void onSuccess(com.example.do_an_di_dong.model.StockMovementPagedResponse result) {
                            anySuccess.set(true);
                            if (result != null && result.getData() != null) {
                                synchronized (merged) {
                                    for (StockMovement m : result.getData()) {
                                        if (m != null) {
                                            int key = m.getMovementId() > 0 ? m.getMovementId() : System.identityHashCode(m);
                                            merged.put(key, m);
                                        }
                                    }
                                }
                            }
                            if (pending.decrementAndGet() == 0) {
                                finishLoadMovements(merged, anySuccess.get(), lastError[0]);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            lastError[0] = message;
                            if (pending.decrementAndGet() == 0) {
                                finishLoadMovements(merged, anySuccess.get(), lastError[0]);
                            }
                        }
                    });
        }
    }

    /**
     * Hoàn tất quá trình gộp các truy vấn biến động kho: sắp xếp danh sách giảm dần theo thời gian tạo (và movementId)
     * rồi áp dụng bộ lọc hiển thị lên RecyclerView.
     */
    private void finishLoadMovements(java.util.Map<Integer, StockMovement> merged, boolean anySuccess, String lastError) {
        if (isFinishing() || isDestroyed()) return;
        binding.swipeRefreshMovements.setRefreshing(false);
        if (!anySuccess && lastError != null) {
            AppToast.show(WarehouseInventoryActivity.this, "Lỗi tải biến động kho: " + lastError, AppToast.Type.ERROR);
        }
        allMovements.clear();
        allMovements.addAll(merged.values());
        java.util.Collections.sort(allMovements, (a, b) -> {
            String da = a != null && a.getCreatedDate() != null ? a.getCreatedDate() : "";
            String db = b != null && b.getCreatedDate() != null ? b.getCreatedDate() : "";
            int cmp = db.compareTo(da);
            if (cmp != 0) return cmp;
            int ia = a != null ? a.getMovementId() : 0;
            int ib = b != null ? b.getMovementId() : 0;
            return Integer.compare(ib, ia);
        });
        applyMovementFilter();
    }

    /**
     * Lọc danh sách biến động kho tại client theo: kho đang chọn (hoặc thuộc chi nhánh hiện tại),
     * mã sản phẩm (nếu nhảy từ chi tiết SP sang), loại biến động (Chip IN/OUT/ADJUST) và từ khóa tìm kiếm.
     */
    private void applyMovementFilter() {
        List<StockMovement> filtered = new ArrayList<>();
        int selectedWhId = selectedWarehouse != null ? selectedWarehouse.getWarehouseId() : 0;
        int curBranch = com.example.do_an_di_dong.util.UserSessionManager.getCurrentBranchId(this);
        java.util.Set<Integer> branchWarehouseIds = new java.util.HashSet<>();
        if (selectedWhId == 0 && curBranch > 0) {
            for (Warehouse w : warehouseList) {
                if (w != null && w.getWarehouseId() > 0) {
                    branchWarehouseIds.add(w.getWarehouseId());
                }
            }
        }
        String q = currentSearchQuery != null ? currentSearchQuery.toLowerCase(Locale.ROOT).trim() : "";

        for (StockMovement m : allMovements) {
            if (m == null) continue;

            // Filter by warehouse if a specific warehouse is selected (and movement has warehouseId)
            if (selectedWhId > 0 && m.getWarehouseId() > 0 && m.getWarehouseId() != selectedWhId) {
                continue;
            }
            if (selectedWhId == 0 && !branchWarehouseIds.isEmpty() && m.getWarehouseId() > 0
                    && !branchWarehouseIds.contains(m.getWarehouseId())) {
                continue;
            }

            // Filter by productId if navigated from ProductDetail
            if (filterProductId > 0 && m.getProductId() > 0 && m.getProductId() != filterProductId) {
                continue;
            }

            // Filter by movement type chip
            String t = m.getType() != null ? m.getType().toUpperCase(Locale.ROOT) : "";
            int qty = m.getQuantity();
            boolean isAdjust = t.contains("ADJUST") || t.contains("AUDIT") || t.contains("KIỂM");
            boolean isIn = !isAdjust && (t.contains("IN") || t.contains("NHẬP")
                    || t.contains("IMPORT") || t.contains("RETURN") || t.contains("PURCHASE") || qty > 0);
            boolean isOut = !isAdjust && !isIn && (t.contains("OUT") || t.contains("XUẤT")
                    || t.contains("SALE") || t.contains("ISSUE") || t.contains("EXPORT") || qty < 0);

            if ("IN".equals(currentMovementFilter) && !isIn) continue;
            if ("OUT".equals(currentMovementFilter) && !isOut) continue;
            if ("ADJUST".equals(currentMovementFilter) && !isAdjust) continue;

            // Filter by search keyword
            if (!q.isEmpty() && filterProductId <= 0) {
                String pName = m.getProductName() != null ? m.getProductName().toLowerCase(Locale.ROOT) : "";
                String sku = m.getBarcode() != null ? m.getBarcode().toLowerCase(Locale.ROOT) : "";
                String reason = m.getReason() != null ? m.getReason().toLowerCase(Locale.ROOT) : "";
                String batch = m.getBatchNumber() != null ? m.getBatchNumber().toLowerCase(Locale.ROOT) : "";
                String whName = m.getWarehouseName() != null ? m.getWarehouseName().toLowerCase(Locale.ROOT) : "";
                if (!pName.contains(q) && !sku.contains(q) && !reason.contains(q) && !batch.contains(q) && !whName.contains(q)) {
                    continue;
                }
            }

            filtered.add(m);
        }

        movementAdapter.setMovements(filtered);
        updateEmptyState();
    }

    /**
     * Kiểm tra số lượng phần tử của Tab đang hiển thị để bật/tắt khung thông báo trống (Empty State).
     */
    private void updateEmptyState() {
        int tabPos = binding.tabLayout.getSelectedTabPosition();
        if (tabPos == 0) {
            boolean empty = inventoryAdapter.getItemCount() == 0;
            binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                binding.tvEmptyMessage.setText("Không tìm thấy mặt hàng nào trong kho.");
            }
        } else {
            boolean empty = movementAdapter.getItemCount() == 0;
            binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                binding.tvEmptyMessage.setText("Chưa có lịch sử biến động kho phù hợp bộ lọc.");
            }
        }
    }
}
