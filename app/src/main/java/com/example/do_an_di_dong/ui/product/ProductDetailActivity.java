package com.example.do_an_di_dong.ui.product;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityProductDetailBinding;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.model.ProductImage;
import com.example.do_an_di_dong.model.SupplierDTO;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.ui.inventory.StockAdjustmentActivity;
import com.example.do_an_di_dong.ui.inventory.WarehouseInventoryActivity;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình chi tiết sản phẩm (ProductDetailActivity).
 * - Vai trò: Hiển thị toàn bộ thông tin sản phẩm, trạng thái tồn kho, danh sách lô hàng (ProductLot),
 *   bộ sưu tập ảnh (ProductImage) và điều hướng sang sửa sản phẩm, điều chỉnh kho hoặc xem biến động kho.
 * - Gắn với: Layout activity_product_detail.xml (ActivityProductDetailBinding), ProductDetailViewModel,
 *   ProductLotsAdapter, ProductImagesAdapter, BannerCarouselAdapter và DataRepository.
 */
public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "PRODUCT_ID";

    private ActivityProductDetailBinding binding;
    private ProductDetailViewModel viewModel;
    private DataRepository repository;
    private int productId;
    private Product currentProduct;
    private List<ProductImage> currentGalleryImages = new ArrayList<>();
    private final List<String> allImageUrls = new ArrayList<>();

    private ProductLotsAdapter lotsAdapter;
    private ProductImagesAdapter imagesAdapter;
    private BannerCarouselAdapter bannerAdapter;
    private ActivityResultLauncher<String> pickGalleryImageLauncher;

    /**
     * Khởi tạo màn hình chi tiết sản phẩm khi Activity được tạo.
     * Nhận productId từ Intent, khởi tạo ViewModel/Adapter/Observer và gọi tải dữ liệu sản phẩm.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);

        // Đọc ID sản phẩm từ Intent với nhiều key dự phòng để tương thích các luồng gọi khác nhau
        productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        if (productId <= 0) {
            productId = getIntent().getIntExtra("product_id", -1);
        }
        if (productId <= 0) {
            productId = getIntent().getIntExtra("productId", -1);
        }
        if (productId <= 0) {
            productId = getIntent().getIntExtra("id", -1);
        }
        if (productId <= 0) {
            AppToast.show(this, "Không tìm thấy thông tin sản phẩm!");
            finish();
            return;
        }

        // Đăng ký launcher chọn ảnh từ bộ nhớ máy để upload thẳng vào thư viện ảnh sản phẩm
        pickGalleryImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::uploadPickedGalleryImage
        );

        viewModel = new ViewModelProvider(this).get(ProductDetailViewModel.class);

        setupViews();
        setupObservers();

        viewModel.loadProduct(productId);
    }

    /**
     * Tự động làm mới dữ liệu sản phẩm khi người dùng quay lại màn hình này
     * (ví dụ: sau khi chỉnh sửa thông tin ở ProductEditorActivity hoặc điều chỉnh tồn kho).
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (productId > 0 && viewModel != null && currentProduct != null) {
            viewModel.loadProduct(productId);
        }
    }

    /**
     * Cấu hình các thành phần giao diện: ViewPager2 banner ảnh, RecyclerView lô hàng & ảnh phụ,
     * cùng sự kiện bấm cho các nút thao tác (Sửa, Xóa, Kiểm kê, Tồn kho).
     */
    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Top Swipeable Banner Carousel
        bannerAdapter = new BannerCarouselAdapter();
        binding.vpProductImages.setAdapter(bannerAdapter);
        binding.vpProductImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int total = Math.max(1, allImageUrls.size());
                binding.tvImagePageIndicator.setText((position + 1) + " / " + total);
            }
        });

        // Lots RecyclerView
        binding.rvProductLots.setLayoutManager(new LinearLayoutManager(this));
        lotsAdapter = new ProductLotsAdapter();
        binding.rvProductLots.setAdapter(lotsAdapter);

        // Images RecyclerView (Horizontal)
        binding.rvProductImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        imagesAdapter = new ProductImagesAdapter(this::confirmDeleteImage);
        // Khi bấm vào ảnh thu nhỏ ở danh sách ngang: đồng bộ vị trí trên banner chính và mở popup phóng to
        imagesAdapter.setOnImageClickListener((img, pos) -> {
            int targetIdx = 0;
            if (img != null && img.getImageUrl() != null) {
                for (int i = 0; i < allImageUrls.size(); i++) {
                    if (allImageUrls.get(i).equalsIgnoreCase(img.getImageUrl())
                            || allImageUrls.get(i).equalsIgnoreCase(img.getFullImageUrl())) {
                        targetIdx = i;
                        break;
                    }
                }
            }
            binding.vpProductImages.setCurrentItem(targetIdx, true);
            openFullscreenGallery(targetIdx);
        });
        binding.rvProductImages.setAdapter(imagesAdapter);

        binding.btnAddImage.setOnClickListener(v -> showAddImageOptions());

        // Đóng gói Intent chuyển sang màn hình sửa sản phẩm (ProductEditorActivity)
        binding.btnEditProduct.setOnClickListener(v -> {
            if (currentProduct != null) {
                Intent intent = new Intent(this, ProductEditorActivity.class);
                intent.putExtra("PRODUCT_ID", currentProduct.getProductId());
                intent.putExtra("product_id", currentProduct.getProductId());
                startActivity(intent);
            }
        });

        // Xác nhận xóa sản phẩm: cảnh báo rõ nếu có ảnh thư viện cần xóa cascade trước
        binding.btnDeleteProduct.setOnClickListener(v -> {
            String msg = currentGalleryImages != null && !currentGalleryImages.isEmpty()
                    ? "Sản phẩm đang có " + currentGalleryImages.size()
                            + " ảnh thư viện. Hệ thống sẽ xóa toàn bộ ảnh liên quan trước khi xóa sản phẩm. Bạn có chắc chắn?"
                    : "Bạn có chắc chắn muốn xóa sản phẩm này khỏi hệ thống?";
            com.example.do_an_di_dong.ui.common.ModernConfirmSheet.confirmDanger(
                    this,
                    "Xác nhận xóa sản phẩm",
                    msg,
                    "Xóa",
                    () -> viewModel.deleteProduct(productId)
            );
        });

        // Chuyển sang màn hình điều chỉnh/kiểm kê tồn kho cho sản phẩm hiện tại
        binding.btnStockAdjust.setOnClickListener(v -> {
            Intent intent = new Intent(this, StockAdjustmentActivity.class);
            if (currentProduct != null) {
                intent.putExtra("PRODUCT_ID", currentProduct.getProductId());
            }
            startActivity(intent);
        });

        // Chuyển sang màn hình tra cứu tồn kho theo từng kho hàng
        binding.btnStockMovements.setOnClickListener(v -> {
            Intent intent = new Intent(this, WarehouseInventoryActivity.class);
            if (currentProduct != null) {
                intent.putExtra("PRODUCT_ID", currentProduct.getProductId());
                intent.putExtra("PRODUCT_NAME", currentProduct.getName());
            }
            startActivity(intent);
        });
    }

    /**
     * Lắng nghe các LiveData từ ProductDetailViewModel để tự động cập nhật UI
     * khi thông tin sản phẩm, lô hàng, danh sách ảnh hoặc trạng thái tải/xóa thay đổi.
     */
    private void setupObservers() {
        viewModel.getProduct().observe(this, product -> {
            if (product != null) {
                currentProduct = product;
                renderProduct(product);
                rebuildCarouselImages();
            }
        });

        viewModel.getLots().observe(this, lots -> {
            lotsAdapter.setList(lots);
            binding.tvEmptyLots.setVisibility((lots == null || lots.isEmpty()) ? View.VISIBLE : View.GONE);
            binding.rvProductLots.setVisibility((lots != null && !lots.isEmpty()) ? View.VISIBLE : View.GONE);
        });

        viewModel.getImages().observe(this, images -> {
            currentGalleryImages = images != null ? images : new ArrayList<>();
            imagesAdapter.setList(currentGalleryImages);
            binding.tvEmptyImages.setVisibility(currentGalleryImages.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvProductImages.setVisibility(!currentGalleryImages.isEmpty() ? View.VISIBLE : View.GONE);
            rebuildCarouselImages();
        });

        viewModel.getLoading().observe(this, loading -> {
            binding.layoutLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            binding.layoutContent.setVisibility(Boolean.TRUE.equals(loading) ? View.GONE : View.VISIBLE);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                AppToast.show(this, error);
            }
        });

        viewModel.getDeleteSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                AppToast.show(this, "Đã xóa sản phẩm thành công!");
                finish();
            }
        });
    }

    /**
     * Tổng hợp danh sách URL ảnh cho Banner ViewPager2:
     * Gộp ảnh đại diện chính của sản phẩm và danh sách ảnh bộ sưu tập (loại bỏ URL trùng lặp).
     */
    private void rebuildCarouselImages() {
        allImageUrls.clear();
        if (currentProduct != null && currentProduct.getImageUrl() != null && !currentProduct.getImageUrl().trim().isEmpty()) {
            allImageUrls.add(currentProduct.getImageUrl().trim());
        }
        // Duyệt ảnh phụ trong gallery và chỉ thêm vào carousel nếu chưa trùng với ảnh đại diện
        if (currentGalleryImages != null) {
            for (ProductImage img : currentGalleryImages) {
                if (img != null && img.getImageUrl() != null && !img.getImageUrl().trim().isEmpty()) {
                    String u = img.getImageUrl().trim();
                    boolean exists = false;
                    for (String existing : allImageUrls) {
                        if (existing.equalsIgnoreCase(u)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        allImageUrls.add(u);
                    }
                }
            }
        }
        bannerAdapter.notifyDataSetChanged();
        int total = Math.max(1, allImageUrls.size());
        int current = Math.min(binding.vpProductImages.getCurrentItem(), total - 1);
        binding.tvImagePageIndicator.setText((Math.max(0, current) + 1) + " / " + total);
    }

    /**
     * Mở hộp thoại xem ảnh toàn màn hình (ImagePreviewDialog) tại vị trí ảnh đang chọn.
     */
    private void openFullscreenGallery(int startPosition) {
        if (allImageUrls.isEmpty()) {
            AppToast.show(this, "Sản phẩm này chưa có hình ảnh để phóng to.");
            return;
        }
        String title = currentProduct != null && currentProduct.getName() != null
                ? currentProduct.getName()
                : "Hình ảnh sản phẩm";
        ImagePreviewDialog.showGallery(this, allImageUrls, startPosition, title);
    }

    /**
     * Đổ dữ liệu chi tiết của đối tượng Product lên các TextView và tính toán màu sắc cảnh báo tồn kho.
     */
    private void renderProduct(Product p) {
        binding.tvProductName.setText(p.getName());
        binding.tvProductPrice.setText(MoneyTextWatcher.formatCurrency(p.getPrice()));
        binding.tvProductId.setText("#" + p.getProductId());
        binding.tvProductSku.setText(p.getBarcode() != null && !p.getBarcode().isEmpty() ? p.getBarcode() : "—");
        binding.tvProductUnit.setText(p.getUnit() != null && !p.getUnit().isEmpty() ? p.getUnit() : "Cái");
        binding.tvProductMinMaxStock.setText(p.getMinStock() + " - " + p.getMaxStock());

        // Nếu DTO trả về chỉ có categoryId mà chưa join tên danh mục, gọi API phụ để tra tên
        if (p.getCategoryName() != null && !p.getCategoryName().isEmpty()) {
            binding.tvProductCategory.setText(p.getCategoryName());
        } else if (p.getCategoryId() > 0) {
            binding.tvProductCategory.setText("Danh mục #" + p.getCategoryId());
            resolveCategoryLabel(p.getCategoryId());
        } else {
            binding.tvProductCategory.setText("Chưa phân loại");
        }

        // Tương tự: tra cứu tên nhà cung cấp từ supplierId nếu dữ liệu trả về chưa có sẵn tên
        if (p.getSupplierName() != null && !p.getSupplierName().isEmpty()) {
            binding.tvProductSupplier.setText(p.getSupplierName());
        } else if (p.getSupplierId() > 0) {
            binding.tvProductSupplier.setText("NCC #" + p.getSupplierId());
            resolveSupplierLabel(p.getSupplierId());
        } else {
            binding.tvProductSupplier.setText("Chưa gán NCC");
        }

        if (p.getDescription() != null && !p.getDescription().trim().isEmpty()) {
            binding.tvProductDescription.setText(p.getDescription().trim());
        } else {
            binding.tvProductDescription.setText("Chưa có mô tả chi tiết cho sản phẩm này.");
        }

        // Đánh giá ngưỡng tồn kho thực tế so với định mức tối thiểu (minStock) để đổi màu badge cảnh báo
        int stock = p.getActualStock();
        binding.tvStockBadge.setText("Tồn kho: " + stock + " " + (p.getUnit() != null ? p.getUnit() : ""));
        if (stock <= 0) {
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_error);
            binding.tvStockBadge.setTextColor(getResources().getColor(R.color.on_error_container, getTheme()));
        } else if (stock <= p.getMinStock()) {
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            binding.tvStockBadge.setTextColor(getResources().getColor(R.color.on_secondary_container, getTheme()));
        } else {
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_success);
            binding.tvStockBadge.setTextColor(getResources().getColor(R.color.on_primary_container, getTheme()));
        }
    }

    /**
     * Tra cứu tên danh mục theo ID từ DataRepository và cập nhật lên giao diện khi hoàn tất.
     */
    private void resolveCategoryLabel(final int catId) {
        repository.getAllCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                if (result == null) return;
                for (ProductCategoryDTO c : result) {
                    if (c.getCategoryId() == catId) {
                        binding.tvProductCategory.setText(c.getName());
                        return;
                    }
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Tra cứu tên nhà cung cấp theo ID từ DataRepository và cập nhật lên giao diện khi hoàn tất.
     */
    private void resolveSupplierLabel(final int supId) {
        repository.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
            @Override
            public void onSuccess(List<SupplierDTO> result) {
                if (result == null) return;
                for (SupplierDTO s : result) {
                    if (s.getSupplierId() == supId) {
                        binding.tvProductSupplier.setText(s.getName());
                        return;
                    }
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Hiển thị BottomSheet cho phép người dùng chọn cách thêm ảnh mới:
     * tải file ảnh từ thiết bị hoặc dán đường dẫn URL trực tiếp.
     */
    private void showAddImageOptions() {
        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();
        actions.add(new ModernPickerSheets.ActionItem(
                "Tải ảnh từ thư viện thiết bị",
                "Chọn ảnh thực tế trong máy để tải lên bộ sưu tập sản phẩm",
                R.drawable.bg_badge_success,
                R.color.success_text,
                () -> pickGalleryImageLauncher.launch("image/*")
        ));
        actions.add(new ModernPickerSheets.ActionItem(
                "Nhập đường dẫn URL ảnh",
                "Dán liên kết hình ảnh trực tiếp (https://...)",
                R.drawable.bg_badge_info,
                R.color.primary,
                this::showAddImageUrlDialog
        ));
        ModernPickerSheets.showActionSheet(
                this,
                "Thêm ảnh vào bộ sưu tập",
                "Ảnh thêm vào sẽ hỗ trợ vuốt trái/phải và phóng to toàn màn hình",
                actions
        );
    }

    /**
     * Xử lý callback sau khi người dùng chọn 1 ảnh từ bộ nhớ thiết bị:
     * Bước 1: Upload file ảnh lên server lấy URL. Bước 2: Lưu bản ghi ProductImage gắn với productId.
     */
    private void uploadPickedGalleryImage(Uri uri) {
        if (uri == null) return;
        AppToast.show(this, "Đang tải ảnh lên bộ sưu tập...");
        repository.uploadProductImage(uri, 0, new DataRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String uploadedUrl) {
                viewModel.addProductImage(productId, uploadedUrl, new DataRepository.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        AppToast.show(ProductDetailActivity.this, "Đã tải và thêm ảnh vào bộ sưu tập!");
                    }

                    @Override
                    public void onError(String message) {
                        AppToast.show(ProductDetailActivity.this, "Lỗi lưu ảnh bộ sưu tập: " + message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                AppToast.show(ProductDetailActivity.this, "Lỗi tải ảnh lên: " + message);
            }
        });
    }

    /**
     * Hiển thị BottomSheet nhập URL ảnh trực tiếp, kiểm tra định dạng HTTP/HTTPS và gọi ViewModel lưu ảnh.
     */
    private void showAddImageUrlDialog() {
        final com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        android.widget.TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        com.google.android.material.textfield.TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        com.google.android.material.textfield.TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        com.google.android.material.button.MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Thêm ảnh bộ sưu tập qua URL");
        tvSubtitle.setText("Nhập đường dẫn URL hình ảnh chất lượng cao của sản phẩm");
        tilField1.setHint("URL hình ảnh (https://...) *");
        btnSubmit.setText("Thêm ảnh");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            // Validate định dạng URL hợp lệ trước khi gọi API lưu
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .httpUrl(tilField1, etField1, "Đường dẫn hình ảnh", true)
                    .validate();
            if (!valid) {
                return;
            }
            String url = com.example.do_an_di_dong.util.FormValidator.textOf(etField1);
            sheet.dismiss();
            viewModel.addProductImage(productId, url, new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    AppToast.show(ProductDetailActivity.this, "Đã thêm ảnh sản phẩm!");
                }

                @Override
                public void onError(String message) {
                    AppToast.show(ProductDetailActivity.this, "Lỗi thêm ảnh: " + message);
                }
            });
        });

        sheet.show();
    }

    /**
     * Hiển thị hộp thoại xác nhận trước khi xóa một ảnh phụ khỏi bộ sưu tập của sản phẩm.
     */
    private void confirmDeleteImage(ProductImage image) {
        com.example.do_an_di_dong.ui.common.ModernConfirmSheet.confirmDanger(
                this,
                "Xác nhận xóa ảnh",
                "Bạn có chắc muốn xóa ảnh này khỏi bộ sưu tập?",
                "Xóa",
                () -> viewModel.deleteProductImage(image.getImageId(), productId, new DataRepository.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        AppToast.show(ProductDetailActivity.this, "Đã xóa ảnh!");
                    }

                    @Override
                    public void onError(String message) {
                        AppToast.show(ProductDetailActivity.this, "Lỗi xóa: " + message);
                    }
                })
        );
    }

    /**
     * Adapter nội bộ cho ViewPager2 hiển thị banner ảnh sản phẩm ở đầu màn hình (hỗ trợ vuốt ngang).
     */
    private class BannerCarouselAdapter extends RecyclerView.Adapter<BannerCarouselAdapter.BannerHolder> {
        /**
         * Khởi tạo ImageView full kích thước cho từng trang của ViewPager2.
         */
        @NonNull
        @Override
        public BannerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new BannerHolder(iv);
        }

        /**
         * Nạp ảnh từ URL vào trang banner bằng Glide; nếu chưa có ảnh nào thì hiển thị placeholder
         * và cho phép bấm vào để mở menu thêm ảnh.
         */
        @Override
        public void onBindViewHolder(@NonNull BannerHolder holder, int position) {
            if (allImageUrls.isEmpty()) {
                holder.imageView.setImageResource(R.drawable.bg_card);
                holder.imageView.setOnClickListener(v -> showAddImageOptions());
                return;
            }
            String rawUrl = allImageUrls.get(position);
            String fullUrl = ApiClient.getFullImageUrl(ProductDetailActivity.this, rawUrl);
            Glide.with(ProductDetailActivity.this)
                    .load(fullUrl)
                    .placeholder(R.drawable.bg_card)
                    .error(R.drawable.bg_card)
                    .centerCrop()
                    .into(holder.imageView);

            holder.imageView.setOnClickListener(v -> openFullscreenGallery(position));
        }

        @Override
        public int getItemCount() {
            return Math.max(1, allImageUrls.size());
        }

        class BannerHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;
            BannerHolder(ImageView itemView) {
                super(itemView);
                this.imageView = itemView;
            }
        }
    }
}
