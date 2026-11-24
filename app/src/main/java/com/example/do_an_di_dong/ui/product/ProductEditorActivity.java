package com.example.do_an_di_dong.ui.product;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityProductEditorBinding;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.model.ProductDTO;
import com.example.do_an_di_dong.model.SupplierDTO;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.MoneyTextWatcher;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Màn hình Tạo mới / Cập nhật Sản phẩm (ProductEditorActivity).
 * - Vai trò: Cung cấp biểu mẫu nhập thông tin sản phẩm, chọn danh mục & nhà cung cấp,
 *   quét mã vạch qua Camera (BarcodeScanActivity), chọn/tải ảnh đại diện và lưu qua DataRepository.
 * - Gắn với: Layout activity_product_editor.xml (ActivityProductEditorBinding), DataRepository,
 *   BarcodeScanActivity và ModernPickerSheets.
 */
public class ProductEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "PRODUCT_ID";

    private ActivityProductEditorBinding binding;
    private DataRepository repository;
    private int productId = -1;
    private boolean isEditMode = false;

    private int selectedCategoryId = 1;
    private int selectedSupplierId = 1;
    private String currentImageUrl = null;
    private Uri selectedImageUri = null;

    // Nhận kết quả mã vạch trả về từ BarcodeScanActivity và điền tự động vào ô Barcode
    private final ActivityResultLauncher<Intent> barcodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("scanned_barcode");
                    if (barcode != null && !barcode.trim().isEmpty()) {
                        binding.etBarcode.setText(barcode.trim());
                        AppToast.show(this, "Đã nhận mã vạch: " + barcode.trim());
                    }
                }
            });

    // Nhận Uri ảnh chọn từ thư viện máy, hiển thị preview tạm thời (sẽ upload thật khi bấm Lưu)
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    binding.ivProductImagePreview.setPadding(0, 0, 0, 0);
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(binding.ivProductImagePreview);
                    binding.tvImageStatus.setText("Đã chọn ảnh mới từ thiết bị (sẽ tải lên khi Lưu)");
                }
            });

    /**
     * Khởi tạo màn hình thêm/sửa sản phẩm:
     * Kiểm tra productId trong Intent để xác định chế độ Thêm mới (isEditMode = false) hay Cập nhật (isEditMode = true).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        if (productId <= 0) {
            productId = getIntent().getIntExtra("product_id", -1);
        }
        isEditMode = productId > 0;

        setupViews();
        preloadDefaultCategoryAndSupplier();
        if (isEditMode) {
            loadExistingProduct(productId);
        }
    }

    /**
     * Thiết lập giao diện, bộ định dạng tiền tệ (MoneyTextWatcher), chọn ảnh, quét mã vạch,
     * BottomSheet chọn danh mục/nhà cung cấp và chip chọn nhanh đơn vị tính.
     */
    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        if (isEditMode) {
            binding.tvHeaderTitle.setText("Cập nhật sản phẩm #" + productId);
            binding.btnSaveProduct.setText("Lưu cập nhật");
        } else {
            binding.tvHeaderTitle.setText("Thêm sản phẩm mới");
            binding.btnSaveProduct.setText("Tạo sản phẩm");
        }

        binding.etPrice.addTextChangedListener(new MoneyTextWatcher(binding.etPrice));

        binding.btnPickProductImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.cardProductImagePreview.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                pickImageLauncher.launch("image/*");
            } else if (currentImageUrl != null && !currentImageUrl.trim().isEmpty()) {
                String pName = binding.etProductName.getText() != null ? binding.etProductName.getText().toString() : "Ảnh sản phẩm";
                ImagePreviewDialog.showSingle(this, currentImageUrl, pName);
            } else {
                pickImageLauncher.launch("image/*");
            }
        });

        // Mở màn hình Camera quét mã vạch (BarcodeScanActivity) và chờ nhận kết quả qua barcodeLauncher
        binding.btnScanProductBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            barcodeLauncher.launch(intent);
        });

        binding.cardSelectProductCategory.setOnClickListener(v -> {
            ModernPickerSheets.showCategoryPicker(this, category -> {
                selectedCategoryId = category.getCategoryId();
                binding.tvSelectedCategoryName.setText(category.getName() + " ▾");
            });
        });

        binding.cardSelectProductSupplier.setOnClickListener(v -> {
            ModernPickerSheets.showSupplierPicker(this, supplier -> {
                selectedSupplierId = supplier.getSupplierId();
                binding.tvSelectedSupplierName.setText(supplier.getName() + " ▾");
            });
        });

        binding.chipGroupUnits.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            if (chip != null) {
                binding.etUnit.setText(chip.getText().toString());
            }
        });

        binding.btnSaveProduct.setOnClickListener(v -> submitForm());
    }

    /**
     * Khi ở chế độ Thêm mới: tải danh sách danh mục và nhà cung cấp để chọn sẵn phần tử đầu tiên làm mặc định.
     */
    private void preloadDefaultCategoryAndSupplier() {
        if (isEditMode) return;
        repository.getAllCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                if (!isEditMode && result != null && !result.isEmpty()) {
                    ProductCategoryDTO first = result.get(0);
                    selectedCategoryId = first.getCategoryId();
                    binding.tvSelectedCategoryName.setText(first.getName() + " ▾");
                }
            }

            @Override
            public void onError(String message) {}
        });

        repository.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
            @Override
            public void onSuccess(List<SupplierDTO> result) {
                if (!isEditMode && result != null && !result.isEmpty()) {
                    SupplierDTO first = result.get(0);
                    selectedSupplierId = first.getSupplierId();
                    binding.tvSelectedSupplierName.setText(first.getName() + " ▾");
                }
            }

            @Override
            public void onError(String message) {}
        });
    }

    /**
     * Tải dữ liệu sản phẩm hiện có từ DataRepository (khi ở chế độ Sửa) và điền vào các ô nhập liệu.
     */
    private void loadExistingProduct(int id) {
        repository.getProductById(id, new DataRepository.ApiCallback<Product>() {
            @Override
            public void onSuccess(Product p) {
                if (p == null) return;
                binding.etProductName.setText(p.getName());
                binding.etBarcode.setText(p.getBarcode() != null ? p.getBarcode() : p.getSku());
                binding.etPrice.setText(String.valueOf((long) p.getPrice()));
                if (p.getUnit() != null && !p.getUnit().isEmpty()) {
                    binding.etUnit.setText(p.getUnit());
                }
                if (p.getDescription() != null) {
                    binding.etDescription.setText(p.getDescription());
                }
                binding.etMinStock.setText(String.valueOf(p.getMinStock()));
                binding.etMaxStock.setText(String.valueOf(p.getMaxStock()));

                if (p.getCategoryId() > 0) {
                    selectedCategoryId = p.getCategoryId();
                }
                if (p.getSupplierId() > 0) {
                    selectedSupplierId = p.getSupplierId();
                }

                if (p.getCategoryName() != null && !p.getCategoryName().isEmpty()) {
                    binding.tvSelectedCategoryName.setText(p.getCategoryName() + " ▾");
                } else {
                    resolveCategoryName(selectedCategoryId);
                }

                if (p.getSupplierName() != null && !p.getSupplierName().isEmpty()) {
                    binding.tvSelectedSupplierName.setText(p.getSupplierName() + " ▾");
                } else {
                    resolveSupplierName(selectedSupplierId);
                }

                currentImageUrl = p.getImageUrl();
                if (currentImageUrl != null && !currentImageUrl.trim().isEmpty()) {
                    Glide.with(ProductEditorActivity.this)
                            .load(ApiClient.getFullImageUrl(ProductEditorActivity.this, currentImageUrl))
                            .placeholder(R.drawable.ic_products)
                            .centerCrop()
                            .into(binding.ivProductImagePreview);
                    binding.tvImageStatus.setText("Nhấn vào ảnh để xem rõ hoặc chọn ảnh mới");
                }
            }

            @Override
            public void onError(String message) {
                AppToast.show(ProductEditorActivity.this, "Không tải được chi tiết SP: " + message);
            }
        });
    }

    /**
     * Tra cứu và hiển thị tên danh mục theo catId khi đối tượng Product chưa có sẵn trường categoryName.
     */
    private void resolveCategoryName(final int catId) {
        repository.getAllCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                if (result == null) return;
                for (ProductCategoryDTO c : result) {
                    if (c.getCategoryId() == catId) {
                        binding.tvSelectedCategoryName.setText(c.getName() + " ▾");
                        return;
                    }
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Tra cứu và hiển thị tên nhà cung cấp theo supId khi đối tượng Product chưa có sẵn trường supplierName.
     */
    private void resolveSupplierName(final int supId) {
        repository.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
            @Override
            public void onSuccess(List<SupplierDTO> result) {
                if (result == null) return;
                for (SupplierDTO s : result) {
                    if (s.getSupplierId() == supId) {
                        binding.tvSelectedSupplierName.setText(s.getName() + " ▾");
                        return;
                    }
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Kiểm tra tính hợp lệ của form, đóng gói ProductDTO và gọi API Tạo mới hoặc Cập nhật sản phẩm.
     * Kích hoạt khi người dùng bấm nút Lưu (btnSaveProduct).
     */
    private void submitForm() {
        // Kiểm tra ràng buộc dữ liệu: tên, đơn vị, giá bán > 0 và tồn tối đa >= tồn tối thiểu
        boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                .required(binding.tilProductName, binding.etProductName, "Tên sản phẩm")
                .required(binding.tilUnit, binding.etUnit, "Đơn vị tính")
                .doubleRange(binding.tilPrice, binding.etPrice, "Giá bán niêm yết", 1, Double.MAX_VALUE, true, "Giá bán phải lớn hơn 0!")
                .intRange(binding.tilMinStock, binding.etMinStock, "Tồn tối thiểu", 0, 999999, false)
                .intRange(binding.tilMaxStock, binding.etMaxStock, "Tồn tối đa", 0, 999999, false)
                .custom(binding.tilMaxStock, binding.etMaxStock, val -> {
                    int min = parseIntSafe(binding.etMinStock.getText() != null ? binding.etMinStock.getText().toString() : "10", 10);
                    int max = parseIntSafe(val.isEmpty() ? "200" : val, 200);
                    return max < min ? "Tồn tối đa phải lớn hơn hoặc bằng tồn tối thiểu" : null;
                })
                .validate();
        if (!valid) {
            return;
        }

        String name = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etProductName);
        String barcode = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etBarcode);
        String unit = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etUnit);
        if (unit.isEmpty()) unit = "Cái";
        String description = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etDescription);

        // Chuyển đổi chuỗi tiền tệ có dấu chấm phân cách về số thực (double)
        double price = MoneyTextWatcher.parseAmount(binding.etPrice);
        int minStock = parseIntSafe(binding.etMinStock.getText() != null ? binding.etMinStock.getText().toString() : "10", 10);
        int maxStock = parseIntSafe(binding.etMaxStock.getText() != null ? binding.etMaxStock.getText().toString() : "200", 200);

        final ProductDTO dto = new ProductDTO(
                isEditMode ? productId : 0,
                name,
                barcode,
                unit.isEmpty() ? "Cái" : unit,
                description,
                selectedCategoryId > 0 ? selectedCategoryId : 1,
                selectedSupplierId > 0 ? selectedSupplierId : 1,
                price,
                minStock,
                maxStock
        );
        if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
            dto.setImage(currentImageUrl);
        }

        binding.btnSaveProduct.setEnabled(false);
        binding.btnSaveProduct.setText("Đang lưu...");

        final DataRepository.ApiCallback<Boolean> finalCallback = new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                AppToast.show(ProductEditorActivity.this, isEditMode ? "Cập nhật sản phẩm thành công!" : "Đã tạo sản phẩm mới!");
                setResult(Activity.RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                binding.btnSaveProduct.setEnabled(true);
                binding.btnSaveProduct.setText(isEditMode ? "Lưu cập nhật" : "Tạo sản phẩm");
                AppToast.show(ProductEditorActivity.this, "Lỗi: " + message);
            }
        };

        // Rẽ nhánh lưu dữ liệu:
        // - Chế độ Sửa: nếu có chọn ảnh mới thì upload ảnh lấy URL trước rồi mới gọi updateProduct.
        // - Chế độ Tạo mới có ảnh: tạo sản phẩm trước để lấy newProductId, sau đó upload ảnh gắn với ID vừa tạo và cập nhật lại URL ảnh.
        if (isEditMode) {
            if (selectedImageUri != null) {
                repository.uploadProductImage(selectedImageUri, productId, new DataRepository.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String uploadedUrl) {
                        dto.setImage(uploadedUrl);
                        repository.updateProduct(dto, finalCallback);
                    }

                    @Override
                    public void onError(String message) {
                        binding.btnSaveProduct.setEnabled(true);
                        binding.btnSaveProduct.setText("Lưu cập nhật");
                        AppToast.show(ProductEditorActivity.this, "Lỗi tải ảnh: " + message);
                    }
                });
            } else {
                repository.updateProduct(dto, finalCallback);
            }
        } else {
            if (selectedImageUri != null) {
                repository.createProductWithId(dto, new DataRepository.ApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer newProductId) {
                        int targetId = (newProductId != null && newProductId > 0) ? newProductId : 0;
                        repository.uploadProductImage(selectedImageUri, targetId, new DataRepository.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String uploadedUrl) {
                                if (targetId > 0) {
                                    dto.setProductId(targetId);
                                    dto.setImage(uploadedUrl);
                                    repository.updateProduct(dto, new DataRepository.ApiCallback<Boolean>() {
                                        @Override public void onSuccess(Boolean r) { finalCallback.onSuccess(true); }
                                        @Override public void onError(String m) { finalCallback.onSuccess(true); }
                                    });
                                } else {
                                    finalCallback.onSuccess(true);
                                }
                            }

                            @Override
                            public void onError(String message) {
                                AppToast.show(ProductEditorActivity.this, "Đã tạo SP nhưng lỗi tải ảnh: " + message);
                                setResult(Activity.RESULT_OK);
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        finalCallback.onError(message);
                    }
                });
            } else {
                repository.createProduct(dto, finalCallback);
            }
        }
    }

    /**
     * Chuyển đổi chuỗi sang số nguyên an toàn, trả về giá trị mặc định (fallback) nếu chuỗi rỗng hoặc sai định dạng.
     */
    private int parseIntSafe(String str, int fallback) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
