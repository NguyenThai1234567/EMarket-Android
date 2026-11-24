package com.example.do_an_di_dong.ui.ai;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemInsightBinding;
import com.example.do_an_di_dong.model.Insight;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách phân tích xu hướng sản phẩm thông minh (Smart Insights)
 * trong Tab Cố vấn AI của AIAssistantActivity.
 * Gắn với layout item_insight.xml (ItemInsightBinding) và model Insight.
 */
public class InsightAdapter extends RecyclerView.Adapter<InsightAdapter.InsightViewHolder> {
    private List<Insight> list = new ArrayList<>();

    /**
     * Cập nhật danh sách nhận định/xu hướng sản phẩm từ API và vẽ lại RecyclerView.
     */
    public void setList(List<Insight> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng Insight từ layout item_insight.xml.
     */
    @NonNull
    @Override
    public InsightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInsightBinding binding = ItemInsightBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new InsightViewHolder(binding);
    }

    /**
     * Gắn dữ liệu đối tượng Insight tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull InsightViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class InsightViewHolder extends RecyclerView.ViewHolder {
        final ItemInsightBinding binding;

        public InsightViewHolder(ItemInsightBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị tên sản phẩm, phân loại nhãn xu hướng (Star/Trending hay Cảnh báo) và mô tả chi tiết.
         */
        public void bind(Insight item) {
            String name = item.getProductName();
            binding.tvProductName.setText(name != null && !name.isEmpty() ? name : "Sản phẩm #" + item.getProductId());

            String type = item.getType();
            if (type == null) type = "Star";

            // Phân nhánh đổi màu badge: Xanh lá cho sản phẩm bán chạy (Star/Trending), Vàng cam cho các cảnh báo khác
            if ("Star".equalsIgnoreCase(type) || "Trending".equalsIgnoreCase(type)) {
                binding.tvInsightType.setText("Bán chạy (Star)");
                binding.tvInsightType.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvInsightType.setTextColor(binding.tvInsightType.getContext().getColor(R.color.on_success_container));
            } else {
                binding.tvInsightType.setText("Cảnh báo (" + type + ")");
                binding.tvInsightType.setBackgroundResource(R.drawable.bg_badge_pending);
                binding.tvInsightType.setTextColor(binding.tvInsightType.getContext().getColor(R.color.on_warning_container));
            }

            String desc = item.getDescription();
            binding.tvDescription.setText(desc != null && !desc.isEmpty() ? desc : "Sản phẩm có xu hướng tiêu thụ tốt.");
        }
    }
}
