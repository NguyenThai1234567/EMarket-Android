package com.example.do_an_di_dong.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.databinding.ItemChatMessageBinding;
import com.example.do_an_di_dong.model.ChatMessage;
import com.example.do_an_di_dong.util.AppToast;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter quản lý danh sách tin nhắn hội thoại giữa Người dùng và Trợ lý AI trong Tab Chat của AIAssistantActivity.
 * Gắn với layout item_chat_message.xml (ItemChatMessageBinding), model ChatMessage
 * và sử dụng MarkdownMessageRenderer để vẽ nội dung trả lời dạng Markdown/Bảng của AI.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();

    /**
     * Thêm một tin nhắn mới (của User hoặc AI) vào cuối danh sách và thông báo RecyclerView chèn dòng mới.
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /**
     * Khởi tạo ViewHolder cho bong bóng chat từ layout item_chat_message.xml.
     */
    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    /**
     * Gắn dữ liệu tin nhắn tại vị trí position vào ViewHolder.
     */
    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        public ChatViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Phân nhánh hiển thị bong bóng chat bên phải (nếu là User) hoặc bên trái (nếu là AI).
         * Với tin nhắn AI: gọi MarkdownMessageRenderer để dựng giao diện bảng/danh sách và hỗ trợ nút Copy vào Clipboard.
         */
        public void bind(ChatMessage message) {
            if (message.isUser()) {
                binding.layoutUserMessage.setVisibility(View.VISIBLE);
                binding.layoutAiMessage.setVisibility(View.GONE);
                binding.tvUserMessage.setText(message.getText());
            } else {
                binding.layoutUserMessage.setVisibility(View.GONE);
                binding.layoutAiMessage.setVisibility(View.VISIBLE);
                String rawText = message.getText() != null ? message.getText() : "";
                // Render văn bản Markdown của AI (bảng biểu, in đậm, danh sách) thành các View native Android
                MarkdownMessageRenderer.render(binding.layoutAiMarkdownBody, rawText);
                // Chỉ hiện nút Sao chép đối với các câu trả lời dài (>= 40 ký tự hoặc có xuống dòng)
                boolean showCopy = rawText.length() >= 40 || rawText.contains("\n");
                binding.btnCopyAiMessage.setVisibility(showCopy ? View.VISIBLE : View.GONE);
                binding.btnCopyAiMessage.setOnClickListener(v -> {
                    Context ctx = v.getContext();
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("EMarket AI", rawText));
                        AppToast.show(ctx, "Đã sao chép nội dung trả lời", Toast.LENGTH_SHORT);
                    }
                });
            }
        }
    }
}
