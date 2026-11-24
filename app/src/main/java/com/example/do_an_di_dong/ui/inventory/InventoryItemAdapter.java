package com.example.do_an_di_dong.ui.inventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.databinding.ItemInventoryBinding;
import com.example.do_an_di_dong.model.InventoryItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách mặt hàng tồn kho (Tab 1 trong WarehouseInventoryActivity).
 * - Layout item XML: item_inventory.xml (ItemInventoryBinding)
 * - Model: InventoryItem (chứa thông tin tồn kho, giá vốn/bán, mã lô và định giá).
 */
public class InventoryItemAdapter extends RecyclerView.Adapter<InventoryItemAdapter.ViewHolder> {

    private final List<InventoryItem> items = new ArrayList<>();
    private final List<InventoryItem> originalItems = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private OnInventoryItemClickListener listener;

    public interface OnInventoryItemClickListener {
        void onItemClick(InventoryItem item);
    }

    public void setOnInventoryItemClickListener(OnInventoryItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật toàn bộ danh sách tồn kho mới từ API vào cả danh sách gốc (originalItems) và danh sách hiển thị (items).
     */
    public void setItems(List<InventoryItem> newItems) {
        this.originalItems.clear();
        this.items.clear();
        if (newItems != null) {
            this.originalItems.addAll(newItems);
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /**
     * Lọc nhanh danh sách tồn kho trên bộ nhớ client theo tên sản phẩm, mã SKU/Barcode hoặc mã lô (batchCode).
     */
    public void filter(String query) {
        items.clear();
        if (query == null || query.trim().isEmpty()) {
            items.addAll(originalItems);
        } else {
            String lower = query.toLowerCase().trim();
            for (InventoryItem item : originalItems) {
                boolean matchesName = item.getProductName() != null && item.getProductName().toLowerCase().contains(lower);
                boolean matchesBarcode = item.getBarcode() != null && item.getBarcode().toLowerCase().contains(lower);
                boolean matchesBatch = item.getBatchCode() != null && item.getBatchCode().toLowerCase().contains(lower);
                if (matchesName || matchesBarcode || matchesBatch) {
                    items.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public List<InventoryItem> getItems() {
        return items;
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng tồn kho từ layout ItemInventoryBinding.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInventoryBinding binding = ItemInventoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * Gắn dữ liệu của mặt hàng tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemInventoryBinding binding;

        ViewHolder(ItemInventoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu InventoryItem lên các View: ảnh thumbnail (Glide), SKU, mã lô, đơn giá,
         * cảnh báo màu sắc theo ngưỡng tồn kho và tổng giá trị tồn của mặt hàng.
         */
        void bind(InventoryItem item) {
            Context context = binding.getRoot().getContext();

            // Product Name
            binding.tvProductName.setText(item.getProductName());

            // Image Thumbnail
            String fullImageUrl = ApiClient.getFullImageUrl(item.getProductImage());
            if (fullImageUrl != null && !fullImageUrl.isEmpty()) {
                Glide.with(context)
                        .load(fullImageUrl)
                        .placeholder(R.drawable.ic_inventory)
                        .error(R.drawable.ic_inventory)
                        .centerCrop()
                        .into(binding.ivProductThumb);
            } else {
                binding.ivProductThumb.setImageResource(R.drawable.ic_inventory);
            }

            // SKU
            if (item.getBarcode() != null && !item.getBarcode().trim().isEmpty()) {
                binding.tvBarcode.setText("SKU: " + item.getBarcode());
                binding.tvBarcode.setVisibility(View.VISIBLE);
            } else {
                binding.tvBarcode.setVisibility(View.GONE);
            }

            // Batch Code
            if (item.getBatchCode() != null && !item.getBatchCode().trim().isEmpty()) {
                binding.tvBatchCode.setText("Lô: " + item.getBatchCode());
                binding.tvBatchCode.setVisibility(View.VISIBLE);
            } else {
                binding.tvBatchCode.setVisibility(View.GONE);
            }

            // Unit Price & Unit
            String unitStr = (item.getUnit() != null && !item.getUnit().trim().isEmpty()) ? item.getUnit() : "";
            if (item.getPrice() != null && item.getPrice() > 0) {
                String priceText = currencyFormat.format(item.getPrice());
                if (!unitStr.isEmpty()) {
                    priceText += " / " + unitStr;
                }
                binding.tvUnitPrice.setText(priceText);
                binding.tvUnitPrice.setVisibility(View.VISIBLE);
            } else {
                binding.tvUnitPrice.setVisibility(View.GONE);
            }

            // Quantity & Unit
            binding.tvQuantity.setText(String.valueOf(item.getQuantity()));
            if (!unitStr.isEmpty()) {
                binding.tvUnit.setText(unitStr);
                binding.tvUnit.setVisibility(View.VISIBLE);
            } else {
                binding.tvUnit.setVisibility(View.GONE);
            }

            // Phân loại trạng thái tồn kho theo ngưỡng: <= 0 (Hết - Đỏ), <= 5 (Sắp hết - Vàng), > 5 (Có sẵn - Xanh)
            if (item.getQuantity() <= 0) {
                binding.tvQuantity.setTextColor(ContextCompat.getColor(context, R.color.error));
                binding.tvStatusBadge.setText("Hết");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_error_container));
            } else if (item.getQuantity() <= 5) {
                binding.tvQuantity.setTextColor(ContextCompat.getColor(context, R.color.warning));
                binding.tvStatusBadge.setText("Sắp hết");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_secondary_container));
            } else {
                binding.tvQuantity.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                binding.tvStatusBadge.setText("Có sẵn");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_success_container));
            }

            // Tính và hiển thị tổng giá trị tồn kho của dòng sản phẩm (quantity * price)
            double itemValuation = item.getTotalValuation();
            if (itemValuation > 0) {
                binding.tvTotalValuation.setText(currencyFormat.format(itemValuation));
                binding.tvTotalValuation.setVisibility(View.VISIBLE);
            } else {
                binding.tvTotalValuation.setVisibility(View.GONE);
            }

            // Click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
