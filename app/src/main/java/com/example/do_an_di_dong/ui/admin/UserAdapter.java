package com.example.do_an_di_dong.ui.admin;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.databinding.ItemUserBinding;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.util.ImagePreviewDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách tài khoản nhân sự trên RecyclerView của màn hình UsersActivity.
 * - Gắn với layout item: item_user.xml (ViewBinding: ItemUserBinding).
 * - Hỗ trợ lọc tìm kiếm cục bộ (theo họ tên, username, email, SĐT, tên vai trò),
 *   hiển thị ảnh đại diện (kèm fallback chữ cái đầu), phân màu huy hiệu vai trò RBAC và trạng thái khóa.
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    public interface OnUserActionListener {
        void onEditUser(User user);
        void onDeleteUser(User user);
    }

    private final List<User> allUsers = new ArrayList<>();
    private final List<User> filteredUsers = new ArrayList<>();
    private OnUserActionListener listener;

    public void setListener(OnUserActionListener listener) {
        this.listener = listener;
    }

    /**
     * Nạp danh sách nhân sự gốc từ API vào cả allUsers và filteredUsers, sau đó vẽ lại RecyclerView.
     */
    public void setUsers(List<User> list) {
        allUsers.clear();
        filteredUsers.clear();
        if (list != null) {
            allUsers.addAll(list);
            filteredUsers.addAll(list);
        }
        notifyDataSetChanged();
    }

    /**
     * Lọc danh sách nhân sự theo từ khóa tìm kiếm (khớp họ tên, username, email, SĐT hoặc tên vai trò).
     * Được kích hoạt mỗi khi người dùng gõ vào ô tìm kiếm trên UsersActivity.
     */
    public void filter(String query) {
        filteredUsers.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            String q = query.trim().toLowerCase(Locale.ROOT);
            for (User u : allUsers) {
                String name = u.getFullName() != null ? u.getFullName().toLowerCase(Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(Locale.ROOT) : "";
                String phone = u.getPhone() != null ? u.getPhone().toLowerCase(Locale.ROOT) : "";
                String roleName = u.getPrimaryRoleName().toLowerCase(Locale.ROOT);
                if (name.contains(q) || username.contains(q) || email.contains(q)
                        || phone.contains(q) || roleName.contains(q)) {
                    filteredUsers.add(u);
                }
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Khởi tạo ViewHolder bằng cách inflate layout item_user.xml.
     */
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserBinding binding = ItemUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new UserViewHolder(binding);
    }

    /**
     * Gắn dữ liệu nhân sự tại vị trí position lên item_user.xml:
     * Tải ảnh đại diện (nếu lỗi tự động fallback về chữ cái đầu), tô màu huy hiệu RBAC và bắt sự kiện sửa/xóa.
     */
    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User u = filteredUsers.get(position);
        Context context = holder.itemView.getContext();
        String name = u.getFullName();
        String initial = (name != null && !name.trim().isEmpty()) ? name.trim().substring(0, 1).toUpperCase() : "U";
        holder.binding.tvUserAvatar.setText(initial);

        // Tải ảnh đại diện từ server bằng Glide; nếu ảnh lỗi sẽ ẩn ImageView và hiện TextView chữ cái đầu
        String img = u.getImage();
        if (img != null && !img.trim().isEmpty()) {
            String fullUrl = ApiClient.getFullImageUrl(context, img.trim());
            holder.binding.ivUserAvatar.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(fullUrl)
                    .circleCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            holder.binding.ivUserAvatar.setVisibility(View.GONE);
                            holder.binding.tvUserAvatar.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.binding.ivUserAvatar.setVisibility(View.VISIBLE);
                            holder.binding.tvUserAvatar.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.binding.ivUserAvatar);

            holder.binding.flUserAvatar.setOnClickListener(v ->
                    ImagePreviewDialog.showSingle(context, img.trim(), name != null ? name : u.getUsername())
            );
        } else {
            holder.binding.ivUserAvatar.setVisibility(View.GONE);
            holder.binding.tvUserAvatar.setVisibility(View.VISIBLE);
            holder.binding.flUserAvatar.setOnClickListener(v -> {
                if (listener != null) listener.onEditUser(u);
            });
        }

        holder.binding.tvUserFullName.setText(name != null && !name.isEmpty() ? name : u.getUsername());

        // Phân nhánh màu sắc huy hiệu vai trò RBAC: Nhà cung cấp (Cam), Admin (Đỏ), Quản lý (Xanh lá), Nhân viên (Xanh dương)
        String primaryRole = u.getPrimaryRoleName();
        holder.binding.tvUserRoleBadge.setText(primaryRole);
        String roleLower = primaryRole != null ? primaryRole.toLowerCase(Locale.ROOT) : "";
        if (u.isSupplier()) {
            holder.binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            holder.binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.warning));
        } else if (u.isAdmin() || User.isAdminRoleName(primaryRole)) {
            holder.binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_error);
            holder.binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.error));
        } else if (roleLower.contains("quản lý") || roleLower.contains("manager")) {
            holder.binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_success);
            holder.binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.success));
        } else {
            holder.binding.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_info);
            holder.binding.tvUserRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.primary));
        }

        holder.binding.tvUserStatusBadge.setVisibility(u.isLocked() ? View.VISIBLE : View.GONE);

        String username = u.getUsername() != null ? "@" + u.getUsername() : "";
        String phone = u.getPhone() != null && !u.getPhone().isEmpty() ? " • " + u.getPhone() : "";
        String email = u.getEmail() != null && !u.getEmail().isEmpty() ? " • " + u.getEmail() : "";
        holder.binding.tvUserSubInfo.setText(username + phone + email);

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onEditUser(u);
        });
        holder.binding.btnDeleteUser.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteUser(u);
        });
    }

    @Override
    public int getItemCount() {
        return filteredUsers.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final ItemUserBinding binding;
        UserViewHolder(ItemUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

