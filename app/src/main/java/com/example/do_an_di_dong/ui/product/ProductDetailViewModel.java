package com.example.do_an_di_dong.ui.product;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductImage;
import com.example.do_an_di_dong.model.ProductLot;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel quản lý trạng thái và nghiệp vụ cho màn hình chi tiết sản phẩm (ProductDetailActivity).
 * - Vai trò: Tải thông tin sản phẩm, danh sách lô hàng (ProductLot), danh sách ảnh phụ (ProductImage),
 *   thêm/xóa ảnh bộ sưu tập và xử lý xóa sản phẩm (bao gồm xóa tuần tự các ảnh liên kết trước).
 * - Gắn với: ProductDetailActivity và DataRepository.
 */
public class ProductDetailViewModel extends AndroidViewModel {
    private final DataRepository repository;
    private final MutableLiveData<Product> productLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ProductLot>> lotsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ProductImage>> imagesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> deleteSuccessLiveData = new MutableLiveData<>();

    public ProductDetailViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<Product> getProduct() { return productLiveData; }
    public LiveData<List<ProductLot>> getLots() { return lotsLiveData; }
    public LiveData<List<ProductImage>> getImages() { return imagesLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<Boolean> getLoading() { return loadingLiveData; }
    public LiveData<Boolean> getDeleteSuccess() { return deleteSuccessLiveData; }

    /**
     * Tải đồng thời thông tin chi tiết sản phẩm, danh sách lô hàng và bộ sưu tập ảnh theo productId.
     * Kích hoạt khi mở màn hình ProductDetailActivity hoặc khi quay lại từ màn hình chỉnh sửa.
     */
    public void loadProduct(int productId) {
        loadingLiveData.postValue(true);

        // Gọi DataRepository lấy thông tin sản phẩm và đẩy kết quả vào LiveData cho UI
        repository.getProductById(productId, new DataRepository.ApiCallback<Product>() {
            @Override
            public void onSuccess(Product result) {
                productLiveData.postValue(result);
                loadingLiveData.postValue(false);
            }

            @Override
            public void onError(String message) {
                errorLiveData.postValue(message);
                loadingLiveData.postValue(false);
            }
        });

        loadLots(productId);
        loadImages(productId);
    }

    /**
     * Tải danh sách các lô hàng (ProductLot) của sản phẩm để hiển thị hạn sử dụng và giá nhập.
     */
    public void loadLots(int productId) {
        repository.getProductLotsByProductId(productId, new DataRepository.ApiCallback<List<ProductLot>>() {
            @Override
            public void onSuccess(List<ProductLot> result) {
                lotsLiveData.postValue(result != null ? result : new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                // Non-fatal
            }
        });
    }

    /**
     * Tải danh sách ảnh bộ sưu tập (ProductImage) của sản phẩm từ server/local DB.
     */
    public void loadImages(int productId) {
        repository.getProductImages(productId, new DataRepository.ApiCallback<List<ProductImage>>() {
            @Override
            public void onSuccess(List<ProductImage> result) {
                imagesLiveData.postValue(result != null ? result : new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                // Non-fatal
            }
        });
    }

    /**
     * Thêm một ảnh mới vào bộ sưu tập của sản phẩm, sau đó tự động tải lại danh sách ảnh để làm mới UI.
     */
    public void addProductImage(int productId, String imageUrl, DataRepository.ApiCallback<Boolean> callback) {
        ProductImage img = new ProductImage(productId, imageUrl);
        repository.createProductImage(img, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadImages(productId);
                if (callback != null) callback.onSuccess(true);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    /**
     * Xóa một ảnh phụ khỏi bộ sưu tập theo imageId và tải lại danh sách ảnh của sản phẩm.
     */
    public void deleteProductImage(int imageId, int productId, DataRepository.ApiCallback<Boolean> callback) {
        repository.deleteProductImage(imageId, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadImages(productId);
                if (callback != null) callback.onSuccess(true);
            }

            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

    /**
     * Bắt đầu quy trình xóa sản phẩm:
     * Truy vấn danh sách ảnh phụ hiện có để xóa hết các bản ghi khóa ngoại trước khi xóa sản phẩm chính.
     */
    public void deleteProduct(int productId) {
        loadingLiveData.postValue(true);
        repository.getProductImages(productId, new DataRepository.ApiCallback<List<ProductImage>>() {
            @Override
            public void onSuccess(List<ProductImage> result) {
                List<ProductImage> imagesToDelete = (result != null) ? result : new ArrayList<>();
                deleteImagesThenProduct(productId, imagesToDelete, 0);
            }

            @Override
            public void onError(String message) {
                // Fallback: nếu không gọi được API lấy danh sách ảnh mới, dùng danh sách ảnh đang cache trong LiveData
                List<ProductImage> cached = imagesLiveData.getValue();
                if (cached != null && !cached.isEmpty()) {
                    deleteImagesThenProduct(productId, new ArrayList<>(cached), 0);
                } else {
                    executeDeleteProduct(productId);
                }
            }
        });
    }

    /**
     * Đệ quy bất đồng bộ xóa từng ảnh trong danh sách; khi đã xóa hết ảnh (index >= size) mới gọi xóa sản phẩm.
     */
    private void deleteImagesThenProduct(final int productId, final List<ProductImage> images, final int index) {
        // Điều kiện dừng đệ quy: đã xóa xong toàn bộ ảnh phụ -> tiến hành xóa sản phẩm chính
        if (images == null || index >= images.size()) {
            executeDeleteProduct(productId);
            return;
        }
        ProductImage img = images.get(index);
        if (img == null || img.getImageId() <= 0) {
            deleteImagesThenProduct(productId, images, index + 1);
            return;
        }
        repository.deleteProductImage(img.getImageId(), new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                deleteImagesThenProduct(productId, images, index + 1);
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue("Không thể xóa ảnh sản phẩm trước khi xóa sản phẩm: "
                        + (message != null ? message : "Lỗi không xác định"));
            }
        });
    }

    /**
     * Gọi DataRepository thực thi lệnh xóa sản phẩm sau khi đã dọn sạch ảnh phụ, cập nhật deleteSuccessLiveData.
     */
    private void executeDeleteProduct(int productId) {
        repository.deleteProduct(productId, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadingLiveData.postValue(false);
                deleteSuccessLiveData.postValue(true);
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
            }
        });
    }
}
