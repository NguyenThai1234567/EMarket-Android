package com.example.do_an_di_dong.ui.sales;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemOrderBinding;
import com.example.do_an_di_dong.model.Order;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách các đơn hàng trong RecyclerView (`OrdersHistoryActivity`).
 * - Vai trò: Ánh xạ từng đối tượng `Order` lên giao diện dòng đơn hàng (mã đơn, khách hàng, ngày tạo,
 *   nhãn phương thức thanh toán, tổng tiền và badge màu theo trạng thái).
 * - Liên kết: Layout `item_order.xml` (ItemOrderBinding) và callback `OnOrderClickListener`.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    private List<Order> orders = new ArrayList<>();
    private OnOrderClickListener listener;

    /**
     * Cập nhật danh sách đơn hàng mới từ LiveData và thông báo cho RecyclerView vẽ lại giao diện.
     */
    public void setOrders(List<Order> orders) {
        this.orders = (orders != null) ? orders : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.listener = listener;
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng đơn hàng bằng ViewBinding (`item_order.xml`).
     */
    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrderBinding binding = ItemOrderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new OrderViewHolder(binding);
    }

    /**
     * Gắn dữ liệu đơn hàng tại vị trí `position` vào ViewHolder và bắt sự kiện click để mở chi tiết đơn.
     */
    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOrderClick(order);
            }
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        final ItemOrderBinding binding;

        public OrderViewHolder(ItemOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị chi tiết một đối tượng `Order` lên các TextView trong `item_order.xml`,
         * đồng thời định dạng tiền tệ VNĐ và đổi màu badge theo trạng thái đơn hàng.
         */
        public void bind(Order order) {
            Context ctx = binding.getRoot().getContext();

            // Order Code
            String code = order.getOrderCode();
            binding.tvOrderCode.setText(code != null && !code.isEmpty() ? code : "#ORD-" + order.getOrderId());

            // Customer Name
            String customer = order.getCustomerName();
            binding.tvCustomerName.setText(customer != null && !customer.isEmpty() ? customer : "Khách vãng lai");

            // Date
            binding.tvOrderDate.setText(Formatters.dateTime(order.getOrderDate()));

            // Payment Method Badge
            String pm = order.getPaymentMethod();
            if ("Transfer".equalsIgnoreCase(pm) || "BankTransfer".equalsIgnoreCase(pm) || "Banking".equalsIgnoreCase(pm)) {
                binding.tvPaymentMethodBadge.setText("💳 Chuyển khoản");
            } else if ("Card".equalsIgnoreCase(pm)) {
                binding.tvPaymentMethodBadge.setText("💳 Thẻ POS");
            } else {
                binding.tvPaymentMethodBadge.setText("💵 Tiền mặt");
            }

            // Total Amount
            binding.tvTotalAmount.setText(MoneyTextWatcher.formatCurrency(order.getTotalAmount()));

            // Status Badge
            String status = order.getStatus();
            if (status == null) status = "Pending";

            if ("Delivered".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status) || "Paid".equalsIgnoreCase(status)) {
                binding.tvOrderStatus.setText("Hoàn thành");
                binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvOrderStatus.setTextColor(ctx.getColor(R.color.on_success_container));
            } else if ("Shipped".equalsIgnoreCase(status) || "Shipping".equalsIgnoreCase(status) || "Processing".equalsIgnoreCase(status)) {
                binding.tvOrderStatus.setText("Đang xử lý");
                binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvOrderStatus.setTextColor(ctx.getColor(R.color.on_warning_container));
            } else if ("Cancelled".equalsIgnoreCase(status)) {
                binding.tvOrderStatus.setText("Đã hủy");
                binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvOrderStatus.setTextColor(ctx.getColor(R.color.on_error_container));
            } else {
                binding.tvOrderStatus.setText("Chờ duyệt");
                binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvOrderStatus.setTextColor(ctx.getColor(R.color.on_warning_container));
            }
        }
    }
}
