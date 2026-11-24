package com.example.do_an_di_dong.ui.inventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemPurchaseOrderBinding;
import com.example.do_an_di_dong.model.PurchaseOrder;
import com.example.do_an_di_dong.util.Formatters;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách Đơn nhập hàng (PurchaseOrder) lên RecyclerView trong {@link PurchaseOrdersActivity}.
 * Gắn với layout item {@code item_purchase_order.xml} thông qua {@link ItemPurchaseOrderBinding}.
 */
public class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {
    public interface OnPurchaseOrderActionListener {
        void onPurchaseOrderClick(PurchaseOrder po);
    }

    private List<PurchaseOrder> purchaseOrders = new ArrayList<>();
    private OnPurchaseOrderActionListener listener;

    public void setOnPurchaseOrderActionListener(OnPurchaseOrderActionListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách đơn nhập hàng mới từ LiveData và render lại toàn bộ RecyclerView.
     */
    public void setPurchaseOrders(List<PurchaseOrder> list) {
        this.purchaseOrders = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng đơn nhập hàng từ layout {@code item_purchase_order.xml}.
     */
    @NonNull
    @Override
    public POViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPurchaseOrderBinding binding = ItemPurchaseOrderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new POViewHolder(binding);
    }

    /**
     * Gắn dữ liệu đơn nhập hàng tại vị trí {@code position} vào ViewHolder và bắt sự kiện chạm mở BottomSheet chi tiết PO.
     */
    @Override
    public void onBindViewHolder(@NonNull POViewHolder holder, int position) {
        PurchaseOrder po = purchaseOrders.get(position);
        holder.bind(po);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPurchaseOrderClick(po);
        });
    }

    @Override
    public int getItemCount() {
        return purchaseOrders.size();
    }

    static class POViewHolder extends RecyclerView.ViewHolder {
        final ItemPurchaseOrderBinding binding;

        public POViewHolder(ItemPurchaseOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu chi tiết của một {@link PurchaseOrder} (mã PO, ngày, NCC, kho/chi nhánh, tổng tiền, trạng thái) lên View.
         */
        public void bind(PurchaseOrder po) {
            Context ctx = binding.getRoot().getContext();

            binding.tvPurchaseOrderCode.setText("#PO-" + po.getPurchaseOrderId());

            String dateStr = Formatters.dateTime(po.getOrderDate());
            binding.tvPoDate.setText(dateStr.isEmpty() ? "--/--/----" : dateStr);

            String supplier = po.getSupplierName();
            binding.tvSupplierName.setText(supplier != null && !supplier.isEmpty() ? supplier : "NCC #" + po.getSupplierId());

            // Ghép chuỗi hiển thị tên Kho nhận hàng kèm Chi nhánh (fallback về ID kho nếu thiếu tên)
            String warehouse = po.getWarehouseName() != null ? po.getWarehouseName().trim() : "";
            String branch = po.getBranchName() != null ? po.getBranchName().trim() : "";
            if (!warehouse.isEmpty() && !branch.isEmpty()) {
                binding.tvPoWarehouseBranch.setText(warehouse + " • " + branch);
            } else if (!warehouse.isEmpty()) {
                binding.tvPoWarehouseBranch.setText(warehouse);
            } else if (!branch.isEmpty()) {
                binding.tvPoWarehouseBranch.setText(branch);
            } else {
                binding.tvPoWarehouseBranch.setText("Kho #" + po.getWarehouseId());
            }

            binding.tvTotalAmount.setText(Formatters.currency(po.getTotalAmount()));

            int detailCount = po.getDetails() != null ? po.getDetails().size() : 0;
            if (detailCount > 0) {
                binding.tvPoItemCount.setVisibility(View.VISIBLE);
                binding.tvPoItemCount.setText(detailCount + " mặt hàng");
            } else {
                binding.tvPoItemCount.setVisibility(View.GONE);
            }

            String notes = po.getNotes() != null ? po.getNotes().trim() : "";
            if (!notes.isEmpty()) {
                binding.tvPoNotes.setVisibility(View.VISIBLE);
                binding.tvPoNotes.setText("Ghi chú: " + notes);
            } else {
                binding.tvPoNotes.setVisibility(View.GONE);
            }

            // Phân loại màu sắc badge theo trạng thái phiếu nhập (Đã nhập kho / Đã hủy / Chờ duyệt)
            String status = po.getStatus();
            if ("Completed".equalsIgnoreCase(status) || "Received".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status)) {
                binding.tvStatus.setText("Đã nhập kho");
                binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvStatus.setTextColor(ctx.getColor(R.color.on_success_container));
            } else if ("Cancelled".equalsIgnoreCase(status) || "Canceled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
                binding.tvStatus.setText("Đã hủy");
                binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvStatus.setTextColor(ctx.getColor(R.color.on_error_container));
            } else {
                binding.tvStatus.setText("Chờ duyệt");
                binding.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvStatus.setTextColor(ctx.getColor(R.color.on_warning_container));
            }
        }
    }
}
