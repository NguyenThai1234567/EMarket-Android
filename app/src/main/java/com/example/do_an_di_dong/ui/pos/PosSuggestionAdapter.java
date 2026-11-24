package com.example.do_an_di_dong.ui.pos;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemPosProductSuggestionBinding;
import com.example.do_an_di_dong.model.Product;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách sản phẩm gợi ý (Autocomplete dropdown) khi thu ngân tìm kiếm trên màn hình POS.
 * - Vai trò: Render nhanh tên sản phẩm, mã SKU và giá bán; bắt sự kiện khi thu ngân chạm vào dòng gợi ý
 *   hoặc nút thêm nhanh (+) để đưa sản phẩm vào giỏ hàng.
 * - Gắn kết: Sử dụng layout item_pos_product_suggestion.xml (ItemPosProductSuggestionBinding), gắn vào rvPosSuggestions trong PosActivity.
 */
public class PosSuggestionAdapter extends RecyclerView.Adapter<PosSuggestionAdapter.SuggestionViewHolder> {

    /**
     * Interface callback báo về PosActivity khi thu ngân chọn một sản phẩm từ danh sách gợi ý.
     */
    public interface OnProductSelectedListener {
        void onProductSelected(Product product);
    }

    private final List<Product> products = new ArrayList<>();
    private OnProductSelectedListener listener;
    private final NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public PosSuggestionAdapter() {}

    public PosSuggestionAdapter(OnProductSelectedListener listener) {
        this.listener = listener;
    }

    public void setListener(OnProductSelectedListener listener) {
        this.listener = listener;
    }

    public void setList(List<Product> newProducts) {
        setProducts(newProducts);
    }

    /**
     * Cập nhật danh sách sản phẩm gợi ý mới nhận từ kết quả tìm kiếm và làm mới RecyclerView.
     * - Kích hoạt: Khi suggestionsLiveData trong PosViewModel trả về kết quả tìm kiếm từ khóa.
     */
    public void setProducts(List<Product> newProducts) {
        this.products.clear();
        if (newProducts != null) {
            this.products.addAll(newProducts);
        }
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng gợi ý sản phẩm bằng ViewBinding (ItemPosProductSuggestionBinding).
     * - Kích hoạt: Tự động gọi bởi RecyclerView khi cần tạo mới View.
     */
    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPosProductSuggestionBinding binding = ItemPosProductSuggestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SuggestionViewHolder(binding);
    }

    /**
     * Gắn dữ liệu sản phẩm tại vị trí position vào SuggestionViewHolder.
     * - Kích hoạt: Tự động gọi bởi RecyclerView khi hiển thị từng dòng gợi ý.
     */
    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        Product product = products.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class SuggestionViewHolder extends RecyclerView.ViewHolder {
        private final ItemPosProductSuggestionBinding binding;

        public SuggestionViewHolder(ItemPosProductSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị tên, mã SKU, giá tiền của sản phẩm gợi ý và lắng nghe sự kiện click để thêm ngay vào giỏ hàng.
         * - Kích hoạt: Được gọi từ onBindViewHolder().
         */
        public void bind(Product product) {
            binding.tvSuggestName.setText(product.getName());
            binding.tvSuggestSku.setText("SKU: " + (product.getSku() != null ? product.getSku() : "—"));
            binding.tvSuggestPrice.setText(vnFormat.format(product.getPrice()));

            // Cho phép bấm vào bất kỳ đâu trên dòng gợi ý hoặc nút Quick Add để thêm sản phẩm vào giỏ
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onProductSelected(product);
            });

            binding.btnQuickAdd.setOnClickListener(v -> {
                if (listener != null) listener.onProductSelected(product);
            });
        }
    }
}
