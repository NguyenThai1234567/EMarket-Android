package com.example.do_an_di_dong.ui.product;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemProductImageBinding;
import com.example.do_an_di_dong.model.ProductImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách ảnh thu nhỏ (thumbnail) nằm ngang trong màn hình chi tiết sản phẩm.
 * - Vai trò: Render từng ảnh ProductImage, bắt sự kiện bấm xem ảnh lớn hoặc bấm nút xóa ảnh.
 * - Gắn với: Layout item_product_image.xml (ItemProductImageBinding) và ProductDetailActivity.
 */
public class ProductImagesAdapter extends RecyclerView.Adapter<ProductImagesAdapter.ImageViewHolder> {

    public interface OnImageDeleteListener {
        void onImageDelete(ProductImage image);
    }

    public interface OnImageClickListener {
        void onImageClick(ProductImage image, int position);
    }

    private List<ProductImage> list = new ArrayList<>();
    private final OnImageDeleteListener deleteListener;
    private OnImageClickListener clickListener;

    public ProductImagesAdapter(OnImageDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setOnImageClickListener(OnImageClickListener clickListener) {
        this.clickListener = clickListener;
    }

    /**
     * Cập nhật danh sách ảnh bộ sưu tập mới từ LiveData và vẽ lại RecyclerView.
     */
    public void setList(List<ProductImage> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng ô ảnh thu nhỏ từ layout item_product_image.xml.
     */
    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductImageBinding binding = ItemProductImageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ImageViewHolder(binding);
    }

    /**
     * Gắn dữ liệu ảnh tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        holder.bind(list.get(position), position);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        final ItemProductImageBinding binding;

        ImageViewHolder(ItemProductImageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Tải ảnh thumbnail bằng Glide và gán callback khi người dùng bấm vào ảnh hoặc bấm nút xóa.
         */
        void bind(ProductImage item, int position) {
            Glide.with(binding.getRoot().getContext())
                    .load(item.getFullImageUrl())
                    .placeholder(R.drawable.bg_card)
                    .into(binding.ivProductGalleryThumb);

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onImageClick(item, position);
                }
            });

            binding.btnDeleteGalleryImg.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onImageDelete(item);
                }
            });
        }
    }
}
