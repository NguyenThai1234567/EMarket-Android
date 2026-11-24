package com.example.do_an_di_dong.ui.ai;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.databinding.ItemInventoryForecastBinding;
import com.example.do_an_di_dong.model.InventoryForecast;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị kết quả dự báo tồn kho sau 30 ngày (từ mô hình AI Prophet)
 * trong Tab Dự báo của màn hình AIAssistantActivity.
 * Gắn với layout item_inventory_forecast.xml (ItemInventoryForecastBinding) và model InventoryForecast.
 */
public class InventoryForecastAdapter extends RecyclerView.Adapter<InventoryForecastAdapter.ForecastViewHolder> {
    private List<InventoryForecast> list = new ArrayList<>();

    /**
     * Nạp danh sách dữ liệu dự báo tồn kho mới và cập nhật giao diện RecyclerView.
     */
    public void setList(List<InventoryForecast> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder cho từng mặt hàng dự báo bằng ViewBinding (item_inventory_forecast.xml).
     */
    @NonNull
    @Override
    public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInventoryForecastBinding binding = ItemInventoryForecastBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ForecastViewHolder(binding);
    }

    /**
     * Gắn dữ liệu dự báo tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ForecastViewHolder extends RecyclerView.ViewHolder {
        final ItemInventoryForecastBinding binding;

        public ForecastViewHolder(ItemInventoryForecastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Hiển thị tên sản phẩm, số lượng tồn hiện tại, lượng tồn dự kiến sau 30 ngày
         * và đánh dấu màu trạng thái rủi ro đứt hàng.
         */
        public void bind(InventoryForecast item) {
            String name = item.getProductName();
            binding.tvProductName.setText(name != null ? name : "Sản phẩm #" + item.getProductId());

            binding.tvCurrentStock.setText("Tồn hiện tại: " + item.getCurrentStock() + " cái");
            binding.tvPredictedStock.setText("Dự báo sau 30 ngày: " + item.getPredictedStockIn30Days() + " cái");

            // Cảnh báo đỏ nếu cờ isOutOfStockRisk bật (dự báo thiếu hụt hàng trong 30 ngày tới), ngược lại hiển thị xanh an toàn
            if (item.isOutOfStockRisk()) {
                binding.tvRiskStatus.setText("Nguy cơ đứt hàng");
                binding.tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_error);
                binding.tvRiskStatus.setTextColor(binding.tvRiskStatus.getContext().getColor(R.color.on_error_container));
            } else {
                binding.tvRiskStatus.setText("Tồn kho an toàn");
                binding.tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvRiskStatus.setTextColor(binding.tvRiskStatus.getContext().getColor(R.color.on_success_container));
            }
        }
    }
}
