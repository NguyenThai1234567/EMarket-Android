package com.example.do_an_di_dong.ui.product;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.model.ProductPagedResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel quản lý dữ liệu và nghiệp vụ phân trang cho màn hình Danh mục Hàng hóa (ProductsActivity).
 * Phối hợp với DataRepository để tải danh sách sản phẩm phân trang (20 sản phẩm/trang),
 * lọc theo từ khóa/danh mục, tải danh sách Category và tra cứu nhanh bằng mã vạch (Barcode).
 */
public class ProductsViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 20;

    private final DataRepository repository;
    private final MutableLiveData<List<Product>> productsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Product> scannedProduct = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> initialLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadMoreLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emptyLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> lastPageLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<List<ProductCategoryDTO>> categoriesLiveData = new MutableLiveData<>();

    private final List<Product> accumulatedProducts = new ArrayList<>();
    private int currentStart = 0;
    private String currentKeyword = "";
    private Integer currentCategoryId = null;
    private boolean isFetching = false;
    private int requestSeq = 0;

    /**
     * Khởi tạo ProductsViewModel cùng với DataRepository.
     */
    public ProductsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<List<Product>> getProducts() { return productsLiveData; }
    public LiveData<Product> getScannedProduct() { return scannedProduct; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getInitialLoading() { return initialLoadingLiveData; }
    public LiveData<Boolean> getLoadMoreLoading() { return loadMoreLoadingLiveData; }
    public LiveData<Boolean> getEmpty() { return emptyLiveData; }
    public LiveData<Boolean> getLastPage() { return lastPageLiveData; }
    public LiveData<List<ProductCategoryDTO>> getCategories() { return categoriesLiveData; }

    /**
     * Tải lại danh sách sản phẩm từ đầu.
     */
    public void fetchProducts() {
        refreshProducts();
    }

    /**
     * Đặt lại (reset) toàn bộ bộ đếm phân trang (currentStart = 0), xóa danh sách tích lũy,
     * tăng requestSeq để hủy hiệu lực các request cũ đang chạy dở và bắt đầu tải trang đầu tiên.
     */
    public void refreshProducts() {
        requestSeq++;
        currentStart = 0;
        accumulatedProducts.clear();
        isFetching = false;
        lastPageLiveData.setValue(false);
        emptyLiveData.setValue(false);
        loadNextPage();
    }

    /**
     * Tải trang sản phẩm tiếp theo từ DataRepository (dùng cho cả lần tải đầu tiên và khi cuộn xuống đáy).
     * Tự động khử trùng lặp (de-duplicate), cộng dồn vào accumulatedProducts và kiểm tra điều kiện chạm trang cuối.
     */
    public void loadNextPage() {
        if (isFetching || Boolean.TRUE.equals(lastPageLiveData.getValue())) {
            return;
        }

        final int seq = requestSeq;
        isFetching = true;
        if (currentStart == 0) {
            initialLoadingLiveData.setValue(true);
        } else {
            loadMoreLoadingLiveData.setValue(true);
        }

        String keywordParam = currentKeyword.isEmpty() ? null : currentKeyword;

        // Gọi API lấy danh sách sản phẩm có phân trang (offset = currentStart, limit = PAGE_SIZE)
        repository.getProductsPaged(currentStart, PAGE_SIZE, keywordParam, currentCategoryId, null, null, null, new DataRepository.ApiCallback<ProductPagedResponse>() {
            @Override
            public void onSuccess(ProductPagedResponse result) {
                // Bỏ qua kết quả nếu đây là response của một request cũ đã bị hủy bởi thao tác tìm kiếm/làm mới mới hơn
                if (seq != requestSeq) return;
                isFetching = false;
                initialLoadingLiveData.postValue(false);
                loadMoreLoadingLiveData.postValue(false);

                List<Product> newItems = (result != null && result.getData() != null) ? result.getData() : new ArrayList<>();

                // De-duplicate items
                for (Product item : newItems) {
                    if (!hasProduct(accumulatedProducts, item.getProductId())) {
                        accumulatedProducts.add(item);
                    }
                }

                productsLiveData.postValue(new ArrayList<>(accumulatedProducts));

                // Cập nhật vị trí offset cho trang kế tiếp và kiểm tra xem đã hết dữ liệu trên Server chưa
                currentStart += newItems.size();
                int total = (result != null) ? result.getTotal() : 0;

                boolean isLast = newItems.isEmpty() || accumulatedProducts.size() >= total || newItems.size() < PAGE_SIZE;
                lastPageLiveData.postValue(isLast);

                emptyLiveData.postValue(accumulatedProducts.isEmpty());
            }

            @Override
            public void onError(String message) {
                if (seq != requestSeq) return;
                isFetching = false;
                initialLoadingLiveData.postValue(false);
                loadMoreLoadingLiveData.postValue(false);
                errorLiveData.postValue(message);
                emptyLiveData.postValue(accumulatedProducts.isEmpty());
            }
        });
    }

    /**
     * Kiểm tra xem một productId đã tồn tại trong danh sách tích lũy hay chưa để tránh trùng lặp khi phân trang.
     */
    private boolean hasProduct(List<Product> list, int productId) {
        for (Product p : list) {
            if (p.getProductId() == productId) return true;
        }
        return false;
    }

    /**
     * Cập nhật điều kiện lọc (từ khóa tìm kiếm + ID danh mục) và làm mới lại danh sách từ trang 1.
     */
    public void searchProducts(String keyword, Integer categoryId) {
        this.currentKeyword = (keyword != null) ? keyword.trim() : "";
        this.currentCategoryId = categoryId;
        refreshProducts();
    }

    /**
     * Gọi API lấy danh sách các Danh mục sản phẩm (Category) để hiển thị lên dải Chip lọc.
     */
    public void fetchCategories() {
        repository.getProductCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                categoriesLiveData.postValue(result);
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
            }
        });
    }

    /**
     * Tra cứu sản phẩm theo mã vạch (Barcode) vừa quét được.
     * Nếu tìm thấy chính xác theo Barcode sẽ hiển thị ngay và bắn Toast; nếu không tìm thấy sẽ fallback
     * sang tìm kiếm phân trang thông thường theo từ khóa mã vạch đó.
     */
    public void lookupBarcode(String rawBarcode) {
        String cleanBarcode = rawBarcode != null ? rawBarcode.trim() : "";
        if (cleanBarcode.isEmpty()) return;

        final int seq = ++requestSeq;
        this.currentKeyword = cleanBarcode;
        this.currentCategoryId = null;
        this.currentStart = 0;
        this.accumulatedProducts.clear();
        this.isFetching = true;
        lastPageLiveData.setValue(true);
        emptyLiveData.setValue(false);
        initialLoadingLiveData.setValue(true);

        // Gọi API tìm kiếm chuyên biệt theo Barcode, nếu rỗng/lỗi thì fallback về refreshProducts()
        repository.searchProductsByBarcode(cleanBarcode, new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                if (seq != requestSeq) return;
                if (result != null && !result.isEmpty()) {
                    isFetching = false;
                    initialLoadingLiveData.postValue(false);
                    accumulatedProducts.clear();
                    for (Product p : result) {
                        if (p != null && !hasProduct(accumulatedProducts, p.getProductId())) {
                            accumulatedProducts.add(p);
                        }
                    }
                    productsLiveData.postValue(new ArrayList<>(accumulatedProducts));
                    emptyLiveData.postValue(accumulatedProducts.isEmpty());
                    scannedProduct.postValue(accumulatedProducts.get(0));
                } else {
                    isFetching = false;
                    refreshProducts();
                }
            }

            @Override
            public void onError(String message) {
                if (seq != requestSeq) return;
                isFetching = false;
                refreshProducts();
            }
        });
    }
}
