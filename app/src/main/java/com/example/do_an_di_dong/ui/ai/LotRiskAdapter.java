package com.example.do_an_di_dong.ui.ai;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemLotRiskBinding;
import com.example.do_an_di_dong.model.LotFinancialRisk;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.MoneyTextWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách phân tích rủi ro tài chính theo từng lô hàng cận/quá hạn sử dụng (FEFO/Expiry Risk)
 * trong Tab Dự báo của màn hình AIAssistantActivity.
 * Gắn với layout item_lot_risk.xml (ItemLotRiskBinding) và model LotFinancialRisk.
 */
public class LotRiskAdapter extends RecyclerView.Adapter<LotRiskAdapter.LotRiskViewHolder> {
    private List<LotFinancialRisk> list = new ArrayList<>();

    /**
     * Cập nhật danh sách lô hàng có rủi ro thất thoát vốn và làm mới RecyclerView.
     */
    public void setList(List<LotFinancialRisk> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng lô hàng rủi ro từ layout item_lot_risk.xml.
     */
    @NonNull
    @Override
    public LotRiskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLotRiskBinding binding = ItemLotRiskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LotRiskViewHolder(binding);
    }

    /**
     * Gắn dữ liệu rủi ro lô hàng tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull LotRiskViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class LotRiskViewHolder extends RecyclerView.ViewHolder {
        final ItemLotRiskBinding binding;

        public LotRiskViewHolder(ItemLotRiskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị mã lô, tồn kho, giá trị vốn có nguy cơ thiệt hại (VNĐ), số ngày còn lại trước khi hết hạn
         * và gắn nhãn mức độ nghiêm trọng (Đã hết hạn / Nguy cấp / Cảnh báo).
         */
        public void bind(LotFinancialRisk item) {
            String lot = item.getLotCode();
            binding.tvLotCode.setText((lot != null && !lot.isEmpty() ? "Lô #" + lot : "Lô hàng")
                    + " • Tồn: " + item.getQuantity());

            String name = item.getProductName();
            binding.tvProductName.setText(name != null ? name : "Sản phẩm #" + item.getProductId());

            // Định dạng giá trị tiền vốn có nguy cơ mất trắng của lô hàng sang chuỗi tiền tệ
            binding.tvRiskValue.setText(MoneyTextWatcher.formatCurrency(item.getRiskValue()));

            String exp = Formatters.date(item.getExpiryDate());
            int days = item.getDaysRemaining();
            String daysText = days < 0 ? ("Đã quá hạn " + Math.abs(days) + " ngày") : ("Còn " + days + " ngày");
            binding.tvExpiryDate.setText("HSD: " + exp + " (" + daysText + ")");

            // Phân loại mức độ rủi ro theo trạng thái Backend hoặc số ngày hạn sử dụng còn lại (<= 7 ngày là Nguy cấp)
            String status = item.getStatus() != null ? item.getStatus().trim() : "";
            if ("EXPIRED".equalsIgnoreCase(status) || days < 0) {
                binding.tvRiskStatus.setText("Đã hết hạn");
                binding.tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvRiskStatus.setTextColor(binding.tvRiskStatus.getContext().getColor(R.color.on_error_container));
            } else if ("DANGER".equalsIgnoreCase(status)
                    || "Critical".equalsIgnoreCase(status)
                    || "High".equalsIgnoreCase(status)
                    || days <= 7) {
                binding.tvRiskStatus.setText("Nguy cấp");
                binding.tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvRiskStatus.setTextColor(binding.tvRiskStatus.getContext().getColor(R.color.on_error_container));
            } else {
                binding.tvRiskStatus.setText("Cảnh báo");
                binding.tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvRiskStatus.setTextColor(binding.tvRiskStatus.getContext().getColor(R.color.on_warning_container));
            }
        }
    }
}
