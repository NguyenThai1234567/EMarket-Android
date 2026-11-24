package com.example.do_an_di_dong.ui.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemExpenseBinding;
import com.example.do_an_di_dong.model.Expense;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách phiếu chi (Expense) trên RecyclerView trong màn hình quản lý sổ quỹ (ExpensesActivity).
 * Gắn với layout item_expense.xml (thông qua ViewBinding ItemExpenseBinding), hiển thị thông tin hạng mục,
 * phương thức thanh toán, số tiền, trạng thái phê duyệt và banner lý do từ chối (nếu bị Rejected).
 */
public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ViewHolder> {

    private final List<Expense> items = new ArrayList<>();
    private OnExpenseClickListener listener;

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
    }

    public void setOnExpenseClickListener(OnExpenseClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật toàn bộ danh sách phiếu chi mới vào Adapter và làm mới giao diện RecyclerView.
     * Kích hoạt khi LiveData danh sách phiếu chi trong ExpensesViewModel thay đổi (lần đầu tải hoặc khi lọc theo trạng thái).
     */
    public void setExpenses(List<Expense> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng phiếu chi bằng cách inflate layout item_expense.xml.
     * Được RecyclerView tự động gọi khi cần tạo mới một item view trên màn hình.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExpenseBinding binding = ItemExpenseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * Gắn dữ liệu của phiếu chi tại vị trí position vào ViewHolder tương ứng.
     * Được RecyclerView gọi khi cuộn danh sách đến vị trí item đó.
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
        private final ItemExpenseBinding binding;

        ViewHolder(ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu từ đối tượng Expense lên các thành phần UI của một dòng phiếu chi.
         * Kích hoạt từ onBindViewHolder và bắt sự kiện click vào item để mở BottomSheet thao tác duyệt/từ chối/xóa.
         */
        void bind(Expense item) {
            Context context = binding.getRoot().getContext();

            // Category Name + Payment Method
            String catName = item.getCategoryName();
            String pm = item.getPaymentMethod();
            String pmLabel = ("Transfer".equalsIgnoreCase(pm) || "BankTransfer".equalsIgnoreCase(pm)) ? "💳 CK" : "💵 TM";
            binding.tvCategoryName.setText((catName != null && !catName.isEmpty() ? catName : "Khoản chi vận hành") + " • " + pmLabel);

            // Note / Description (keep clean, do not append reject reason here)
            String note = item.getNote();
            if (note != null && !note.trim().isEmpty()) {
                binding.tvNote.setText(note.trim());
            } else {
                binding.tvNote.setText("Phiếu chi #" + item.getExpenseId());
            }

            // Amount
            binding.tvAmount.setText("- " + MoneyTextWatcher.formatCurrency(item.getAmount()));

            // User & Branch
            String user = item.getUserName() != null && !item.getUserName().isEmpty() ? item.getUserName() : "Nhân viên";
            String branch = item.getBranchName() != null ? item.getBranchName() : "";
            if (!branch.isEmpty()) {
                binding.tvUserAndBranch.setText("Lập bởi: " + user + " • " + branch);
            } else {
                binding.tvUserAndBranch.setText("Lập bởi: " + user);
            }

            // Date
            binding.tvDate.setText(Formatters.date(item.getExpenseDate()));

            // Rẽ nhánh hiển thị màu sắc Badge theo trạng thái duyệt (APPROVED / REJECTED / PENDING) và bật/tắt khung lý do từ chối
            // Status Badge & Dedicated Rejection Reason Banner
            String status = item.getStatus() != null ? item.getStatus().trim().toUpperCase(Locale.ROOT) : "PENDING";
            if ("APPROVED".equals(status)) {
                binding.tvStatusBadge.setText("Đã duyệt");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_success_container));
                binding.layoutRejectReason.setVisibility(android.view.View.GONE);
            } else if ("REJECTED".equals(status)) {
                binding.tvStatusBadge.setText("Từ chối");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_error_container));

                String rejectReason = item.getRejectReason();
                String rejector = item.getRejectorName();
                String rejectorSuffix = (rejector != null && !rejector.trim().isEmpty())
                        ? " (" + rejector.trim() + ")"
                        : "";
                if (rejectReason != null && !rejectReason.trim().isEmpty()) {
                    binding.tvRejectReason.setText("Lý do từ chối" + rejectorSuffix + ": " + rejectReason.trim());
                } else {
                    binding.tvRejectReason.setText("Phiếu chi bị từ chối" + rejectorSuffix + " (chưa nhập lý do)");
                }
                binding.layoutRejectReason.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvStatusBadge.setText("Chờ duyệt");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_secondary_container));
                binding.layoutRejectReason.setVisibility(android.view.View.GONE);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpenseClick(item);
                }
            });
        }
    }
}
