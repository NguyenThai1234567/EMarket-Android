package com.example.do_an_di_dong.ui.inventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemStockMovementBinding;
import com.example.do_an_di_dong.model.StockMovement;
import com.example.do_an_di_dong.util.Formatters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách lịch sử biến động kho / thẻ kho (Tab 2 trong WarehouseInventoryActivity).
 * - Layout item XML: item_stock_movement.xml (ItemStockMovementBinding)
 * - Model: StockMovement (chứa loại biến động IN/OUT/ADJUST, số lượng, lô, kho, chứng từ gốc và người thực hiện).
 */
public class StockMovementAdapter extends RecyclerView.Adapter<StockMovementAdapter.MovementViewHolder> {

    public interface OnMovementClickListener {
        void onMovementClick(StockMovement movement);
    }

    private List<StockMovement> movements = new ArrayList<>();
    private OnMovementClickListener listener;

    public void setOnMovementClickListener(OnMovementClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách lịch sử biến động kho sau khi tải API hoặc lọc và vẽ lại RecyclerView.
     */
    public void setMovements(List<StockMovement> movements) {
        this.movements = (movements != null) ? movements : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo MovementViewHolder từ layout ItemStockMovementBinding.
     */
    @NonNull
    @Override
    public MovementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStockMovementBinding binding = ItemStockMovementBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MovementViewHolder(binding);
    }

    /**
     * Gắn dữ liệu biến động kho tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull MovementViewHolder holder, int position) {
        holder.bind(movements.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return movements.size();
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        final ItemStockMovementBinding binding;

        public MovementViewHolder(ItemStockMovementBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ thông tin một bản ghi biến động kho lên giao diện: tên SP + SKU, badge phân loại (Nhập/Xuất/Kiểm kê),
         * số lượng biến động (+/-), kho & số lô, lý do, người thực hiện kèm mã chứng từ tham chiếu.
         */
        public void bind(StockMovement item, OnMovementClickListener listener) {
            Context ctx = binding.getRoot().getContext();

            String name = item.getProductName();
            String sku = item.getBarcode() != null && !item.getBarcode().isEmpty() ? (" (" + item.getBarcode() + ")") : "";
            binding.tvProductName.setText((name != null && !name.isEmpty() ? name : "Sản phẩm #" + item.getProductId()) + sku);

            int qty = item.getQuantity();
            String rawType = item.getType() != null ? item.getType().trim() : "";
            String upper = rawType.toUpperCase(Locale.ROOT);

            // Phân tích chuỗi type và dấu của số lượng để phân loại bản ghi thành Kiểm kê, Nhập kho hay Xuất kho
            boolean isAdjust = upper.contains("ADJUST") || upper.contains("AUDIT") || upper.contains("KIỂM");
            boolean isIn = !isAdjust && (upper.contains("IN") || upper.contains("NHẬP")
                    || upper.contains("IMPORT") || upper.contains("RETURN") || upper.contains("PURCHASE") || qty > 0);
            boolean isOut = !isAdjust && !isIn && (upper.contains("OUT") || upper.contains("XUẤT")
                    || upper.contains("SALE") || upper.contains("ISSUE") || upper.contains("EXPORT") || qty < 0);

            if (isIn) {
                binding.tvQuantity.setText((qty > 0 ? "+" : "") + qty);
                binding.tvQuantity.setTextColor(ctx.getColor(R.color.success));
                binding.tvMovementBadge.setText("NHẬP KHO");
                binding.tvMovementBadge.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvMovementBadge.setTextColor(ctx.getColor(R.color.success_text));
            } else if (isOut) {
                binding.tvQuantity.setText(qty < 0 ? String.valueOf(qty) : ("-" + qty));
                binding.tvQuantity.setTextColor(ctx.getColor(R.color.error));
                binding.tvMovementBadge.setText("XUẤT KHO");
                binding.tvMovementBadge.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvMovementBadge.setTextColor(ctx.getColor(R.color.error_text));
            } else {
                binding.tvQuantity.setText((qty >= 0 ? "+" : "") + qty);
                binding.tvQuantity.setTextColor(ctx.getColor(R.color.primary));
                binding.tvMovementBadge.setText("KIỂM KÊ / Đ.CHỈNH");
                binding.tvMovementBadge.setBackgroundResource(R.drawable.bg_badge_info);
                binding.tvMovementBadge.setTextColor(ctx.getColor(R.color.primary));
            }

            String whName = item.getWarehouseName() != null && !item.getWarehouseName().isEmpty()
                    ? item.getWarehouseName()
                    : ("Kho #" + (item.getWarehouseId() > 0 ? item.getWarehouseId() : 1));
            String batch = item.getBatchNumber() != null && !item.getBatchNumber().isEmpty()
                    ? (" • Lô: " + item.getBatchNumber())
                    : (item.getLotId() != null && item.getLotId() > 0 ? (" • Lô #" + item.getLotId()) : "");
            binding.tvWarehouseBadge.setText(whName + batch);

            if (item.getReason() != null && !item.getReason().trim().isEmpty()) {
                binding.tvMovementReason.setVisibility(View.VISIBLE);
                binding.tvMovementReason.setText("Lý do: " + item.getReason().trim());
            } else {
                binding.tvMovementReason.setVisibility(View.GONE);
            }

            String actor = item.getUserName() != null && !item.getUserName().isEmpty() ? item.getUserName() : "Hệ thống";
            String ref = "";
            if (item.getReferenceType() != null && !item.getReferenceType().isEmpty()) {
                ref = " • " + item.getReferenceType() + (item.getReferenceId() != null && item.getReferenceId() > 0 ? (" #" + item.getReferenceId()) : "");
            }
            binding.tvMovementType.setText("Thực hiện: " + actor + ref);

            String date = item.getCreatedDate();
            binding.tvCreatedDate.setText(date != null && !date.isEmpty() ? Formatters.dateTime(date) : "—");

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMovementClick(item);
                }
            });
        }
    }
}
