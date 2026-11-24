package com.example.do_an_di_dong.ui.product;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemProductLotBinding;
import com.example.do_an_di_dong.model.ProductLot;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách lô hàng (ProductLot) của một sản phẩm trong màn hình chi tiết sản phẩm.
 * - Vai trò: Hiển thị mã lô (batchCode), giá nhập (costPrice), ngày sản xuất (NSX) và hạn sử dụng (HSD).
 * - Gắn với: Layout item_product_lot.xml (ItemProductLotBinding) và ProductDetailActivity.
 */
public class ProductLotsAdapter extends RecyclerView.Adapter<ProductLotsAdapter.LotViewHolder> {

    private List<ProductLot> list = new ArrayList<>();

    /**
     * Cập nhật danh sách lô hàng mới và thông báo cho RecyclerView vẽ lại danh sách.
     */
    public void setList(List<ProductLot> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng lô hàng từ layout item_product_lot.xml.
     */
    @NonNull
    @Override
    public LotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductLotBinding binding = ItemProductLotBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LotViewHolder(binding);
    }

    /**
     * Đổ dữ liệu của lô hàng tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull LotViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class LotViewHolder extends RecyclerView.ViewHolder {
        final ItemProductLotBinding binding;

        LotViewHolder(ItemProductLotBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Định dạng tiền tệ cho giá nhập và định dạng ngày tháng cho NSX/HSD trước khi hiển thị lên TextView.
         */
        void bind(ProductLot lot) {
            binding.tvLotBatchCode.setText("Mã lô: " + lot.getBatchCode());
            binding.tvLotCostPrice.setText("Giá nhập: " + MoneyTextWatcher.formatCurrency(lot.getCostPrice()));
            String mfg = lot.getManufacturingDate();
            String exp = lot.getExpiryDate();
            binding.tvLotMfgDate.setText("NSX: " + (mfg != null && !mfg.isEmpty() ? Formatters.date(mfg) : "—"));
            binding.tvLotExpDate.setText("HSD: " + (exp != null && !exp.isEmpty() ? Formatters.date(exp) : "—"));
        }
    }
}
