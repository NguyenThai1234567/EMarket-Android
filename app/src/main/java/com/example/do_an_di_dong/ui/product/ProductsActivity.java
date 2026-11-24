package com.example.do_an_di_dong.ui.product;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ActivityProductsBinding;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.ui.common.AppNavigationShell;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Màn hình Danh mục Hàng hóa (Tab chính thứ 3) - Kiến trúc Zero-Fragment (0 Fragment).
 * Gắn với layout: activity_products.xml (ActivityProductsBinding), ProductsViewModel và ProductAdapter.
 * Hỗ trợ tìm kiếm debounce, lọc theo Chip danh mục, quét mã vạch (BarcodeScanActivity),
 * cuộn phân trang vô tận (infinite scroll) và điều hướng sang chi tiết/thêm mới sản phẩm.
 */
public class ProductsActivity extends AppCompatActivity {
    private ActivityProductsBinding binding;
    private ProductsViewModel viewModel;
    private ProductAdapter adapter;
    private ActivityResultLauncher<Intent> barcodeLauncher;

    private Integer selectedCategoryId = null;
    private String currentKeyword = "";
    private boolean suppressSearchWatcher = false;
    private boolean suppressCategoryTrigger = false;
    private boolean skipNextResumeRefresh = false;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    /**
     * Khởi tạo màn hình Danh sách sản phẩm:
     * Thiết lập ViewModel, RecyclerView + ProductAdapter, khung điều hướng AppNavigationShell,
     * sự kiện cuộn tải trang tiếp theo (load more) và sự kiện bấm vào sản phẩm / nút tạo mới.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ProductsViewModel.class);
        adapter = new ProductAdapter();

        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_products,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> loadData()
        );

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvProducts.setLayoutManager(layoutManager);
        binding.rvProducts.setAdapter(adapter);

        // Khi bấm vào 1 dòng sản phẩm: đóng gói productId vào Intent để mở màn hình Chi tiết sản phẩm
        adapter.setOnProductClickListener(product -> {
            if (product != null && product.getProductId() > 0) {
                Intent intent = new Intent(this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.getProductId());
                intent.putExtra("product_id", product.getProductId());
                intent.putExtra("productId", product.getProductId());
                startActivity(intent);
            }
        });

        // Nút nổi (FAB) góc dưới: mở màn hình thêm sản phẩm mới
        binding.fabCreateProduct.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductEditorActivity.class);
            startActivity(intent);
        });

        binding.swipeRefreshProducts.setOnRefreshListener(() -> viewModel.refreshProducts());

        // Bắt sự kiện cuộn RecyclerView để tự động tải trang tiếp theo (Infinite Scroll) khi gần chạm đáy
        binding.rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 4
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= 20) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        observeViewModel();
        setupSearchAndBarcode();

        binding.btnRetry.setOnClickListener(v -> loadData());
        loadData();
    }

    /**
     * Được gọi khi quay lại màn hình (VD: sau khi sửa/thêm sản phẩm hoặc từ màn hình quét mã vạch về).
     * Tự động làm mới danh sách sản phẩm trừ khi vừa nhận kết quả từ máy quét Barcode (skipNextResumeRefresh).
     */
    @Override
    protected void onResume() {
        super.onResume();
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_products,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> loadData()
        );
        if (skipNextResumeRefresh) {
            skipNextResumeRefresh = false;
        } else if (adapter.getItemCount() > 0) {
            viewModel.refreshProducts();
        }
    }

    /**
     * Lắng nghe các LiveData từ ProductsViewModel (danh sách sản phẩm, trạng thái loading đầu/loading thêm,
     * danh mục, sản phẩm quét mã vạch, và thông báo lỗi) để đồng bộ lên giao diện.
     */
    private void observeViewModel() {
        // Cập nhật RecyclerView hoặc hiển thị màn hình Trống (Empty State)
        viewModel.getProducts().observe(this, list -> {
            binding.layoutError.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);

            if (list == null || list.isEmpty()) {
                binding.rvProducts.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.rvProducts.setVisibility(View.VISIBLE);
                adapter.setProducts(list);
            }
        });

        viewModel.getInitialLoading().observe(this, isLoading -> {
            binding.swipeRefreshProducts.setRefreshing(false);
            if (Boolean.TRUE.equals(isLoading)) {
                if (adapter.getItemCount() == 0) {
                    binding.layoutLoading.setVisibility(View.VISIBLE);
                    binding.layoutContent.setVisibility(View.GONE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    binding.layoutError.setVisibility(View.GONE);
                }
            } else {
                binding.layoutLoading.setVisibility(View.GONE);
            }
        });

        viewModel.getLoadMoreLoading().observe(this, isLoadMore -> {
            binding.pbLoadMore.setVisibility(Boolean.TRUE.equals(isLoadMore) ? View.VISIBLE : View.GONE);
        });

        viewModel.getCategories().observe(this, this::populateCategories);

        viewModel.getScannedProduct().observe(this, product -> {
            if (product != null) {
                com.example.do_an_di_dong.util.AppToast.show(this, "Tìm thấy: " + product.getName(), Toast.LENGTH_SHORT);
            }
        });

        viewModel.getError().observe(this, msg -> {
            binding.swipeRefreshProducts.setRefreshing(false);
            if (msg != null && adapter.getItemCount() == 0) {
                showError(msg);
            } else if (msg != null) {
                com.example.do_an_di_dong.util.AppToast.show(this, "Lỗi tải thêm: " + msg, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Thiết lập ô tìm kiếm từ khóa có trễ (Debounce 350ms để tránh gọi API liên tục khi đang gõ)
     * và đăng ký ActivityResultLauncher nhận mã vạch trả về từ BarcodeScanActivity.
     */
    private void setupSearchAndBarcode() {
        // Debounce 350ms khi người dùng gõ vào ô tìm kiếm
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressSearchWatcher) return;
                currentKeyword = s != null ? s.toString().trim() : "";
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> triggerSearch();
                searchHandler.postDelayed(searchRunnable, 350);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Xử lý kết quả sau khi quét mã vạch từ Camera (BarcodeScanActivity)
        barcodeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String rawCode = result.getData().getStringExtra("scanned_barcode");
                        String barcode = rawCode != null ? rawCode.trim() : "";
                        if (!barcode.isEmpty()) {
                            skipNextResumeRefresh = true;
                            if (searchRunnable != null) {
                                searchHandler.removeCallbacks(searchRunnable);
                            }
                            selectedCategoryId = null;
                            if (binding.chipGroupCategories.getChildCount() > 0
                                    && binding.chipGroupCategories.getChildAt(0) instanceof Chip) {
                                suppressCategoryTrigger = true;
                                ((Chip) binding.chipGroupCategories.getChildAt(0)).setChecked(true);
                                suppressCategoryTrigger = false;
                            }
                            currentKeyword = barcode;
                            suppressSearchWatcher = true;
                            binding.etSearch.setText(barcode);
                            binding.etSearch.setSelection(barcode.length());
                            suppressSearchWatcher = false;
                            viewModel.lookupBarcode(barcode);
                        }
                    }
                }
        );

        binding.btnBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            barcodeLauncher.launch(intent);
        });
    }

    /**
     * Tải dữ liệu ban đầu cho màn hình: gọi lấy danh sách danh mục (Categories) và tìm kiếm trang sản phẩm đầu tiên.
     */
    private void loadData() {
        binding.layoutContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.VISIBLE);

        viewModel.fetchCategories();
        triggerSearch();
    }

    /**
     * Kích hoạt tìm kiếm sản phẩm trên ViewModel theo từ khóa và ID danh mục hiện tại.
     */
    private void triggerSearch() {
        viewModel.searchProducts(currentKeyword, selectedCategoryId);
    }

    /**
     * Hiển thị giao diện lỗi toàn màn hình (khi danh sách chưa có dữ liệu) kèm thông báo tiếng Việt dễ hiểu.
     */
    private void showError(String message) {
        binding.layoutLoading.setVisibility(View.GONE);
        binding.layoutContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);

        String raw = message != null ? message.trim() : "";
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        String friendlyMessage;
        if (lower.contains("connectexception") || lower.contains("timeout") || lower.contains("unable to resolve host")
                || lower.contains("không thể kết nối") || lower.contains("quá thời gian chờ")) {
            friendlyMessage = "Không thể kết nối mạng. Vui lòng kiểm tra Wifi/Mạng hoặc cấu hình Server API.";
        } else if (lower.contains("401") || lower.contains("hết hạn")) {
            friendlyMessage = "Bạn không có quyền truy cập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        } else if (lower.contains("403") || lower.contains("không có quyền")) {
            friendlyMessage = "Bạn không có quyền truy cập danh sách sản phẩm.";
        } else {
            friendlyMessage = !raw.isEmpty() ? raw : "Lỗi tải danh sách sản phẩm.";
        }
        binding.tvErrorMessage.setText(friendlyMessage);
    }

    /**
     * Sinh động (dynamic) các thẻ Chip danh mục hàng hóa vào ChipGroup từ danh sách trả về của API.
     * Khi người dùng chọn một Chip, tự động gán selectedCategoryId và lọc lại danh sách sản phẩm.
     */
    private void populateCategories(List<ProductCategoryDTO> categoryList) {
        binding.chipGroupCategories.removeAllViews();

        Chip allChip = new Chip(this);
        allChip.setText("Tất cả");
        allChip.setCheckable(true);
        allChip.setChecked(selectedCategoryId == null);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressCategoryTrigger) return;
            if (isChecked) {
                selectedCategoryId = null;
                triggerSearch();
            }
        });
        binding.chipGroupCategories.addView(allChip);

        if (categoryList != null) {
            for (ProductCategoryDTO cat : categoryList) {
                Chip chip = new Chip(this);
                chip.setText(cat.getName());
                chip.setCheckable(true);
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedCategoryId = cat.getCategoryId();
                        triggerSearch();
                    }
                });
                binding.chipGroupCategories.addView(chip);
            }
        }
    }

    /**
     * Dọn dẹp callback debounce tìm kiếm trong Handler khi Activity bị hủy để tránh rò rỉ bộ nhớ (Memory Leak).
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}

