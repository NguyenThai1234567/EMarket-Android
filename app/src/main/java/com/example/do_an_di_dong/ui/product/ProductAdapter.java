package com.example.do_an_di_dong.ui.product;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemProductBinding;
import com.example.do_an_di_dong.model.Product;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho RecyclerView hiển thị danh sách sản phẩm trong màn hình ProductsActivity.
 * Gắn với layout từng dòng: item_product.xml (thông qua ItemProductBinding).
 * Chịu trách nhiệm render thông tin sản phẩm, định dạng giá tiền VNĐ, đổi màu badge cảnh báo tồn kho
 * và tải ảnh sản phẩm qua thư viện Glide.
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private List<Product> products = new ArrayList<>();
    private OnProductClickListener listener;

    /**
     * Cập nhật danh sách sản phẩm mới từ ViewModel và yêu cầu RecyclerView vẽ lại toàn bộ danh sách.
     */
    public void setProducts(List<Product> products) {
        this.products = (products != null) ? products : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    /**
     * Khởi tạo ViewHolder cho một dòng sản phẩm bằng cách inflate layout item_product.xml.
     */
    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductBinding binding = ItemProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ProductViewHolder(binding);
    }

    /**
     * Gắn dữ liệu của sản phẩm tại vị trí position vào ViewHolder và bắt sự kiện click để mở chi tiết.
     */
    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product p = products.get(position);
        holder.bind(p);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        private final ItemProductBinding binding;

        public ProductViewHolder(ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu chi tiết của 1 đối tượng Product lên các View (Tên, SKU, Giá bán, Tồn kho, Ảnh).
         */
        public void bind(Product product) {
            binding.tvProductName.setText(product.getName());
            binding.tvProductSku.setText("SKU: " + (product.getSku() != null ? product.getSku() : "N/A"));
            
            NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            double price = product.getPrice();
            binding.tvProductPrice.setText(vnFormat.format(price));
            
            // Phân loại ngưỡng tồn kho để đổi nhãn và màu sắc cảnh báo (Đỏ: <= 0, Vàng: <= 5, Xanh: > 5)
            int stock = product.getStockQuantity();
            if (stock <= 0) {
                binding.tvStockCount.setText("Hết hàng");
                binding.tvStockCount.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvStockCount.setTextColor(binding.tvStockCount.getContext().getColor(R.color.on_error_container));
            } else if (stock <= 5) {
                binding.tvStockCount.setText("Sắp hết: " + stock);
                binding.tvStockCount.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvStockCount.setTextColor(binding.tvStockCount.getContext().getColor(R.color.on_warning_container));
            } else {
                binding.tvStockCount.setText("Tồn: " + stock);
                binding.tvStockCount.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvStockCount.setTextColor(binding.tvStockCount.getContext().getColor(R.color.on_success_container));
            }

            // Load Image with Glide
            Glide.with(itemView.getContext())
                    .load(product.getFullImageUrl())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(binding.imgProduct);
        }
    }
}
