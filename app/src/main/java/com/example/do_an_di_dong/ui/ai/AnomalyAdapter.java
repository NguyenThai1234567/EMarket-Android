package com.example.do_an_di_dong.ui.ai;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemAnomalyBinding;
import com.example.do_an_di_dong.model.Anomaly;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách cảnh báo bất thường về doanh số/sản lượng (Sales Anomalies)
 * trong Tab Cố vấn AI của màn hình AIAssistantActivity.
 * Gắn với layout item_anomaly.xml (ItemAnomalyBinding) và model Anomaly.
 */
public class AnomalyAdapter extends RecyclerView.Adapter<AnomalyAdapter.AnomalyViewHolder> {
    private List<Anomaly> list = new ArrayList<>();

    /**
     * Cập nhật danh sách cảnh báo bất thường mới từ API và làm mới toàn bộ RecyclerView.
     */
    public void setList(List<Anomaly> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng dòng cảnh báo bất thường bằng ViewBinding (item_anomaly.xml).
     */
    @NonNull
    @Override
    public AnomalyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAnomalyBinding binding = ItemAnomalyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AnomalyViewHolder(binding);
    }

    /**
     * Gắn dữ liệu của phần tử Anomaly tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull AnomalyViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class AnomalyViewHolder extends RecyclerView.ViewHolder {
        final ItemAnomalyBinding binding;

        public AnomalyViewHolder(ItemAnomalyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Đổ dữ liệu tên danh mục, loại phát hiện bất thường và mô tả chi tiết lên giao diện item.
         * Có xử lý chuỗi mặc định (fallback) nếu dữ liệu từ Backend trả về bị rỗng/null.
         */
        public void bind(Anomaly item) {
            String cat = item.getCategoryName();
            binding.tvCategoryName.setText(cat != null && !cat.isEmpty() ? cat : "Nhóm danh mục");

            String type = item.getDetectionType();
            binding.tvDetectionType.setText(type != null && !type.isEmpty() ? type : "Bất thường");

            String details = item.getDetails();
            binding.tvDetails.setText(details != null && !details.isEmpty() ? details : "Phát hiện biến động doanh thu hoặc sản lượng bất thường.");
        }
    }
}
