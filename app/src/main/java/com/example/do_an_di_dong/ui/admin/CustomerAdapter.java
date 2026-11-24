package com.example.do_an_di_dong.ui.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.databinding.ItemCustomerBinding;
import com.example.do_an_di_dong.model.Customer;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách khách hàng trên RecyclerView của màn hình CustomersActivity.
 * - Gắn với layout item: item_customer.xml (ViewBinding: ItemCustomerBinding).
 * - Chịu trách nhiệm render thông tin hồ sơ, ảnh đại diện (Glide), huy hiệu hạng thẻ thành viên,
 *   điểm tích lũy và chuyển tiếp các sự kiện click (xem menu thao tác, xem lịch sử điểm, gọi điện) về Activity.
 */
public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    public interface OnCustomerClickListener {
        void onCustomerClick(Customer customer);
    }

    public interface OnPointsClickListener {
        void onPointsClick(Customer customer);
    }

    public interface OnCallClickListener {
        void onCallClick(Customer customer);
    }

    private List<Customer> list = new ArrayList<>();
    private OnCustomerClickListener listener;
    private OnPointsClickListener pointsListener;
    private OnCallClickListener callListener;

    public void setListener(OnCustomerClickListener listener) {
        this.listener = listener;
    }

    public void setPointsListener(OnPointsClickListener pointsListener) {
        this.pointsListener = pointsListener;
    }

    public void setCallListener(OnCallClickListener callListener) {
        this.callListener = callListener;
    }

    /**
     * Cập nhật danh sách khách hàng mới (sau khi tải API hoặc lọc tìm kiếm) và làm mới toàn bộ RecyclerView.
     */
    public void setList(List<Customer> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder bằng cách inflate layout item_customer.xml cho từng dòng khách hàng.
     */
    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCustomerBinding binding = ItemCustomerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CustomerViewHolder(binding);
    }

    /**
     * Gắn dữ liệu của khách hàng tại vị trí position vào ViewHolder và đăng ký sự kiện chạm vào cả dòng.
     */
    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        final Customer item = list.get(position);
        holder.bind(item, pointsListener, callListener);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCustomerClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class CustomerViewHolder extends RecyclerView.ViewHolder {
        final ItemCustomerBinding binding;

        public CustomerViewHolder(ItemCustomerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu chi tiết của một Customer lên các View trong item_customer.xml:
         * Xử lý ảnh đại diện / chữ cái đầu, màu sắc huy hiệu hạng thẻ, ưu đãi giảm giá POS và mốc điểm lên hạng.
         */
        public void bind(Customer item, OnPointsClickListener pointsListener, OnCallClickListener callListener) {
            Context ctx = binding.getRoot().getContext();
            String name = item.getCustomerName();
            binding.tvCustomerName.setText(name != null ? name : "Khách hàng #" + item.getCustomerId());

            // Initial & Avatar
            String initial = (name != null && !name.trim().isEmpty())
                    ? name.trim().substring(0, 1).toUpperCase(Locale.ROOT)
                    : "K";
            binding.tvCustomerInitial.setText(initial);

            // Ghép base URL của server với đường dẫn ảnh tương đối và tải ảnh tròn bằng Glide
            String rawImg = item.getUserImg();
            String resolvedAvatar = (rawImg != null && !rawImg.trim().isEmpty())
                    ? ApiClient.getFullImageUrl(ctx, rawImg.trim())
                    : null;
            if (resolvedAvatar != null && !resolvedAvatar.isEmpty()) {
                binding.ivCustomerAvatar.setVisibility(View.VISIBLE);
                Glide.with(ctx)
                        .load(resolvedAvatar)
                        .circleCrop()
                        .error(R.drawable.bg_icon_box_indigo)
                        .into(binding.ivCustomerAvatar);
            } else {
                binding.ivCustomerAvatar.setVisibility(View.GONE);
            }

            // Phone & Email
            String phone = (item.getPhone() != null && !item.getPhone().trim().isEmpty()) ? item.getPhone().trim() : "Chưa có SĐT";
            String email = (item.getEmail() != null && !item.getEmail().trim().isEmpty()) ? item.getEmail().trim() : "";
            binding.tvPhone.setText(email.isEmpty() ? ("📞 " + phone) : ("📞 " + phone + " • " + email));

            // Tier badge & discount rate: Phân nhánh màu sắc huy hiệu theo cấp bậc hạng thẻ thành viên
            String tier = item.getTierName();
            binding.tvCustomerType.setText("💎 " + tier);
            int discountPercent = (int) Math.round(item.getDiscountRate() * 100);
            if ("Kim Cương".equalsIgnoreCase(tier) || "Hạng VIP".equalsIgnoreCase(tier) || "Khách sỉ".equalsIgnoreCase(tier) || "Đối tác".equalsIgnoreCase(tier)) {
                binding.tvCustomerType.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvCustomerType.setTextColor(ctx.getColor(R.color.on_success_container));
            } else if ("Vàng".equalsIgnoreCase(tier) || "Bạc".equalsIgnoreCase(tier)) {
                binding.tvCustomerType.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvCustomerType.setTextColor(ctx.getColor(R.color.on_warning_container));
            } else {
                binding.tvCustomerType.setBackgroundResource(R.drawable.bg_badge_neutral);
                binding.tvCustomerType.setTextColor(ctx.getColor(R.color.text_secondary));
            }

            NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
            int balance = item.getPointBalance();
            int lifetime = item.getPointEarnedTotal();

            binding.tvLoyaltyPoints.setText("⭐ " + nf.format(balance) + " điểm ▸");
            binding.tvLifetimePoints.setText("Tích lũy: " + nf.format(lifetime) + " đ");

            // Hiển thị % chiết khấu trực tiếp trên hóa đơn POS nếu đã có hạng, ngược lại tính số điểm còn thiếu để đạt mốc 200 / 500 / 1000 điểm
            if (discountPercent > 0) {
                binding.tvEmail.setText("Ưu đãi hạng " + tier + ": Giảm " + discountPercent + "% hóa đơn POS");
                binding.tvEmail.setTextColor(ctx.getColor(R.color.primary));
            } else {
                int nextMilestone = lifetime < 200 ? (200 - lifetime) : (lifetime < 500 ? (500 - lifetime) : (1000 - lifetime));
                if (nextMilestone > 0) {
                    binding.tvEmail.setText("Còn " + nf.format(nextMilestone) + " điểm để lên hạng tiếp theo");
                } else {
                    binding.tvEmail.setText("Mã KH: #" + item.getCustomerId());
                }
                binding.tvEmail.setTextColor(ctx.getColor(R.color.text_secondary));
            }

            View.OnClickListener openPoints = v -> {
                if (pointsListener != null) pointsListener.onPointsClick(item);
            };
            binding.layoutLoyaltyPill.setOnClickListener(openPoints);
            binding.btnViewPointsHistory.setOnClickListener(openPoints);

            binding.btnCallCustomer.setOnClickListener(v -> {
                if (callListener != null) callListener.onCallClick(item);
            });
        }
    }
}
