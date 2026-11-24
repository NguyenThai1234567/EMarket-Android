package com.example.do_an_di_dong.ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.databinding.ItemSupplierPaymentHistoryBinding;
import com.example.do_an_di_dong.model.SupplierPayment;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter con hiển thị danh sách lịch sử các đợt thanh toán ({@link SupplierPayment}) bên trong mỗi khoản công nợ.
 * Được nhúng trực tiếp vào RecyclerView con của {@link SupplierDebtAdapter} và gắn với layout {@code item_supplier_payment_history.xml}.
 */
public class SupplierPaymentHistoryAdapter extends RecyclerView.Adapter<SupplierPaymentHistoryAdapter.HistoryViewHolder> {

    private List<SupplierPayment> list = new ArrayList<>();

    public SupplierPaymentHistoryAdapter() {
    }

    /**
     * Cập nhật danh sách lịch sử thanh toán của khoản nợ và render lại RecyclerView con.
     */
    public void setList(List<SupplierPayment> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng lịch sử thanh toán từ layout {@code item_supplier_payment_history.xml}.
     */
    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSupplierPaymentHistoryBinding binding = ItemSupplierPaymentHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new HistoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        final ItemSupplierPaymentHistoryBinding binding;

        HistoryViewHolder(ItemSupplierPaymentHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị thông tin một lần thanh toán (số tiền, hình thức Tiền mặt/Chuyển khoản, thời gian)
         * và tự động nhận diện loại chứng từ đính kèm (Ảnh preview, Tài liệu PDF/Word/Excel, hoặc Ghi chú text).
         */
        void bind(SupplierPayment item) {
            Context ctx = binding.getRoot().getContext();
            binding.tvPaymentAmount.setText("+ " + MoneyTextWatcher.formatCurrency(item.getAmount()));
            String method = "Cash".equalsIgnoreCase(item.getPaymentMethod()) ? "Tiền mặt" : "Chuyển khoản";
            binding.tvPaymentMethod.setText(method);
            binding.tvPaymentDate.setText(item.getPaymentDate() != null ? Formatters.dateTime(item.getPaymentDate()) : "—");

            // Phân loại chuỗi paymentProof: nếu là đường dẫn ảnh thì load thumbnail bằng Glide, tài liệu thì mở Intent VIEW, còn lại hiển thị dạng ghi chú
            String proof = item.getPaymentProof() != null ? item.getPaymentProof().trim() : "";
            if (!proof.isEmpty()) {
                String lower = proof.toLowerCase(Locale.ROOT);
                boolean isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                        || lower.endsWith(".webp") || lower.endsWith(".gif")
                        || lower.contains("/uploads/supplierpayments/")
                        || lower.contains("/uploads/images/")
                        || (lower.startsWith("/uploads/") && !lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx"));
                boolean isDocument = !isImage && (lower.startsWith("http") || lower.startsWith("/uploads/")
                        || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".xlsx"));

                if (isImage) {
                    binding.tvPaymentNote.setVisibility(View.GONE);
                    binding.layoutPaymentProof.setVisibility(View.VISIBLE);
                    binding.tvPaymentProofLabel.setText("Xem ảnh chứng từ đính kèm");
                    binding.tvPaymentProofSub.setText("Chạm để xem toàn màn hình & phóng to");
                    String fullUrl = ApiClient.getFullImageUrl(ctx, proof);
                    Glide.with(ctx)
                            .load(fullUrl)
                            .placeholder(R.drawable.ic_payment)
                            .error(R.drawable.ic_payment)
                            .centerCrop()
                            .into(binding.ivPaymentProofThumb);
                    binding.layoutPaymentProof.setOnClickListener(v ->
                            ImagePreviewDialog.showSingle(ctx, proof, "Chứng từ thanh toán #" + item.getPaymentId())
                    );
                } else if (isDocument) {
                    binding.tvPaymentNote.setVisibility(View.GONE);
                    binding.layoutPaymentProof.setVisibility(View.VISIBLE);
                    binding.ivPaymentProofThumb.setImageResource(R.drawable.ic_orders);
                    binding.tvPaymentProofLabel.setText("Mở tài liệu chứng từ đính kèm");
                    binding.tvPaymentProofSub.setText(proof);
                    binding.layoutPaymentProof.setOnClickListener(v -> {
                        try {
                            // Đóng gói Intent mở trình duyệt hoặc ứng dụng đọc file bên ngoài theo URL đầy đủ
                            String fullUrl = ApiClient.getFullImageUrl(ctx, proof);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl));
                            ctx.startActivity(intent);
                        } catch (Exception e) {
                            AppToast.show(ctx, "Không thể mở tài liệu: " + proof);
                        }
                    });
                } else {
                    binding.layoutPaymentProof.setVisibility(View.GONE);
                    binding.tvPaymentNote.setVisibility(View.VISIBLE);
                    binding.tvPaymentNote.setText("Ghi chú / Mã GD: " + proof);
                }
            } else {
                binding.layoutPaymentProof.setVisibility(View.GONE);
                binding.tvPaymentNote.setVisibility(View.GONE);
            }
        }
    }
}
