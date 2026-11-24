package com.example.do_an_di_dong.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemPaymentBinding;
import com.example.do_an_di_dong.model.Payment;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách lịch sử thanh toán của đơn hàng trong `OrderDetailActivity`.
 * - Vai trò: Hiển thị phương thức thanh toán, thời gian giao dịch và số tiền đã thanh toán của từng đợt.
 * - Liên kết: Layout `item_payment.xml` (ItemPaymentBinding) và model `Payment`.
 */
public class OrderPaymentAdapter extends RecyclerView.Adapter<OrderPaymentAdapter.PaymentViewHolder> {
    private List<Payment> payments = new ArrayList<>();

    /**
     * Cập nhật danh sách các giao dịch thanh toán và thông báo cho RecyclerView vẽ lại.
     */
    public void setPayments(List<Payment> payments) {
        this.payments = (payments != null) ? payments : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder từ layout `item_payment.xml`.
     */
    @NonNull
    @Override
    public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPaymentBinding binding = ItemPaymentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PaymentViewHolder(binding);
    }

    /**
     * Gắn dữ liệu thanh toán tại vị trí `position` vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
        holder.bind(payments.get(position));
    }

    @Override
    public int getItemCount() {
        return payments.size();
    }

    static class PaymentViewHolder extends RecyclerView.ViewHolder {
        final ItemPaymentBinding binding;

        public PaymentViewHolder(ItemPaymentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Định dạng và hiển thị thông tin một giao dịch thanh toán (phương thức, chuỗi ngày giờ ISO rút gọn, số tiền VNĐ).
         */
        public void bind(Payment payment) {
            NumberFormat vnFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

            String method = payment.getPaymentMethod();
            if (method == null || method.isEmpty()) method = "Tiền mặt / Chuyển khoản";
            binding.tvPaymentMethod.setText("Phương thức: " + method);

            // Chuẩn hóa chuỗi thời gian ISO-8601 (cắt chữ 'T' và lấy đến phút yyyy-MM-dd HH:mm)
            String date = payment.getPaymentDate();
            if (date != null && date.contains("T")) {
                date = date.replace("T", " ");
                if (date.length() > 16) date = date.substring(0, 16);
            }
            binding.tvPaymentDate.setText(date != null ? date : "—");

            binding.tvPaymentAmount.setText("+ " + vnFormat.format(payment.getAmount()));
        }
    }
}
