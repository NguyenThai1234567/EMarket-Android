package com.example.do_an_di_dong.ui.pos;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemPosCartBinding;
import com.example.do_an_di_dong.model.CartItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách các món hàng trong giỏ hàng trên màn hình bán hàng POS.
 * - Vai trò: Render từng dòng sản phẩm trong giỏ (tên, đơn giá, số lượng, thành tiền) và bắt các sự kiện
 *   tăng/giảm/nhập số lượng hoặc xóa món để báo ngược về PosActivity / PosViewModel.
 * - Gắn kết: Sử dụng layout item_pos_cart.xml (ItemPosCartBinding), được gắn vào RecyclerView rvPosCart trong PosActivity.
 */
public class PosCartAdapter extends RecyclerView.Adapter<PosCartAdapter.CartViewHolder> {

    /**
     * Interface callback giao tiếp từ Adapter lên PosActivity khi người dùng thay đổi số lượng hoặc xóa món.
     */
    public interface OnCartItemChangeListener {
        void onQuantityChanged(CartItem item, int newQuantity);
        void onItemRemoved(CartItem item);
    }

    private final List<CartItem> items = new ArrayList<>();
    private OnCartItemChangeListener listener;
    private final NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public void setListener(OnCartItemChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật toàn bộ danh sách món trong giỏ hàng và làm mới giao diện RecyclerView.
     * - Kích hoạt: Khi cartLiveData trong PosViewModel phát ra danh sách giỏ hàng mới.
     */
    public void setItems(List<CartItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<CartItem> getItems() {
        return items;
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng sản phẩm trong giỏ bằng ViewBinding (ItemPosCartBinding).
     * - Kích hoạt: Tự động gọi bởi RecyclerView khi cần tạo mới View cho một dòng.
     */
    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPosCartBinding binding = ItemPosCartBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CartViewHolder(binding);
    }

    /**
     * Đổ dữ liệu của món hàng tại vị trí position vào CartViewHolder.
     * - Kích hoạt: Tự động gọi bởi RecyclerView khi cuộn hoặc làm mới danh sách.
     */
    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class CartViewHolder extends RecyclerView.ViewHolder {
        private final ItemPosCartBinding binding;

        public CartViewHolder(ItemPosCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Gắn thông tin món hàng (CartItem) lên các TextView và thiết lập sự kiện cho các nút thao tác số lượng.
         * - Kích hoạt: Được gọi từ onBindViewHolder().
         */
        public void bind(CartItem item) {
            binding.tvItemName.setText(item.getProductName());
            binding.tvItemPrice.setText(vnFormat.format(item.getPrice()));
            binding.tvQuantity.setText(String.valueOf(item.getQuantity()));
            binding.tvSubtotal.setText(vnFormat.format(item.getSubtotal()));

            // Bấm trực tiếp vào con số lượng: mở hộp thoại QuantityInputDialog để nhập nhanh số lượng lớn bằng bàn phím số
            binding.tvQuantity.setOnClickListener(v -> {
                com.example.do_an_di_dong.util.QuantityInputDialog.show(
                        binding.getRoot().getContext(),
                        item.getProductName(),
                        item.getQuantity(),
                        1,
                        999999,
                        null,
                        newQty -> {
                            if (listener != null) {
                                listener.onQuantityChanged(item, newQty);
                            }
                        }
                );
            });

            binding.btnPlus.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuantityChanged(item, item.getQuantity() + 1);
                }
            });

            // Khi bấm nút trừ (-): nếu số lượng > 1 thì giảm đi 1, nếu đang là 1 thì xóa luôn món đó khỏi giỏ
            binding.btnMinus.setOnClickListener(v -> {
                if (listener != null) {
                    if (item.getQuantity() > 1) {
                        listener.onQuantityChanged(item, item.getQuantity() - 1);
                    } else {
                        listener.onItemRemoved(item);
                    }
                }
            });

            binding.btnRemoveItem.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemRemoved(item);
                }
            });
        }
    }
}
