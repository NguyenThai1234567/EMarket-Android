package com.example.do_an_di_dong.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemOrderDetailBinding;
import com.example.do_an_di_dong.model.OrderDetail;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách các mặt hàng (line items) trong một đơn hàng (`OrderDetailActivity`).
 * - Vai trò: Hiển thị tên sản phẩm, đơn giá x số lượng và thành tiền của từng món trong đơn.
 * - Liên kết: Layout `item_order_detail.xml` (ItemOrderDetailBinding) và model `OrderDetail`.
 */
public class OrderDetailAdapter extends RecyclerView.Adapter<OrderDetailAdapter.DetailViewHolder> {
    private List<OrderDetail> details = new ArrayList<>();

    /**
     * Cập nhật danh sách mặt hàng của đơn và vẽ lại RecyclerView.
     */
    public void setDetails(List<OrderDetail> details) {
        this.details = (details != null) ? details : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder từ layout `item_order_detail.xml`.
     */
    @NonNull
    @Override
    public DetailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrderDetailBinding binding = ItemOrderDetailBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DetailViewHolder(binding);
    }

    /**
     * Gắn dữ liệu mặt hàng tại vị trí `position` lên ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull DetailViewHolder holder, int position) {
        holder.bind(details.get(position));
    }

    @Override
    public int getItemCount() {
        return details.size();
    }

    static class DetailViewHolder extends RecyclerView.ViewHolder {
        final ItemOrderDetailBinding binding;

        public DetailViewHolder(ItemOrderDetailBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu tên sản phẩm, đơn giá, số lượng và tính thành tiền của dòng sản phẩm theo định dạng tiền tệ VNĐ.
         */
        public void bind(OrderDetail item) {
            NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

            String name = item.getProductName();
            binding.tvProductName.setText(name != null && !name.isEmpty() ? name : "Sản phẩm #" + item.getProductId());

            binding.tvUnitPriceQuantity.setText(vnFormat.format(item.getUnitPrice()) + " x " + item.getQuantity());

            // Ưu tiên lấy totalPrice từ Backend trả về, nếu bằng 0 thì tự tính = đơn giá * số lượng
            double total = item.getTotalPrice() > 0 ? item.getTotalPrice() : (item.getUnitPrice() * item.getQuantity());
            binding.tvLineTotal.setText(vnFormat.format(total));
        }
    }
}
