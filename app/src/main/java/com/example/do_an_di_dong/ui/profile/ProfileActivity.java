package com.example.do_an_di_dong.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityProfileBinding;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.ui.common.AppNavigationShell;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.ui.common.ServerSettingsDialog;
import com.example.do_an_di_dong.util.AppConfig;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Màn hình Tài khoản & Trung tâm Quản trị (Tab chính thứ 5) - Kiến trúc Zero-Fragment (0 Fragment).
 * - Gắn với layout: activity_profile.xml (ViewBinding: ActivityProfileBinding) và sheet_change_password.xml.
 * - Phối hợp cùng: UserSessionManager (đọc/ghi phiên đăng nhập & phân quyền RBAC),
 *   AppNavigationShell (đồng bộ Drawer, BottomNavigation, chọn chi nhánh) và DataRepository (upload avatar, cập nhật hồ sơ).
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private DataRepository repository;
    private ActivityResultLauncher<String> pickAvatarLauncher;

    /**
     * Khởi tạo màn hình Hồ sơ cá nhân, đăng ký launcher chọn ảnh đại diện,
     * thiết lập thanh điều hướng chung (AppNavigationShell) và hiển thị thông tin người dùng hiện tại.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        pickAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadNewAvatar(uri);
                    }
                }
        );

        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_profile,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> bindProfileData()
        );

        bindProfileData();
        setupNavigationActions();
    }

    /**
     * Làm mới lại giao diện điều hướng và thông tin hồ sơ mỗi khi người dùng quay lại màn hình này.
     */
    @Override
    protected void onResume() {
        super.onResume();
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_profile,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                (branchId, branchName) -> bindProfileData()
        );
        bindProfileData();
    }

    /**
     * Đọc thông tin phiên đăng nhập từ UserSessionManager và hiển thị lên giao diện:
     * Họ tên, vai trò, chi nhánh làm việc (có phân quyền chuyển đổi chi nhánh cho Admin),
     * username, email, SĐT, ảnh đại diện và địa chỉ máy chủ Backend hiện tại.
     */
    private void bindProfileData() {
        User currentUser = UserSessionManager.getCurrentUser(this);
        String fullName = UserSessionManager.getCurrentUserFullName(this);
        String roleName = UserSessionManager.getRoleName(this);
        String branchName = UserSessionManager.getCurrentBranchName(this);

        binding.tvProfileFullName.setText(fullName);
        binding.tvProfileRoleBadge.setText(roleName);

        // Rẽ nhánh phân quyền RBAC: Đối tác NCC không gắn chi nhánh; Admin được phép bấm chuyển chi nhánh; Nhân viên cố định theo chi nhánh
        boolean isSupplier = UserSessionManager.isSupplier(this);
        boolean canSwitchBranch = UserSessionManager.canSwitchBranch(this);
        if (isSupplier) {
            binding.tvProfileBranch.setText("Đối tác Nhà cung cấp");
            binding.tvProfileBranch.setTextColor(getColor(R.color.text_primary));
            binding.rowSwitchBranch.setClickable(false);
            binding.rowSwitchBranch.setOnClickListener(null);
        } else if (canSwitchBranch) {
            binding.tvProfileBranch.setText(branchName + " ▾");
            binding.tvProfileBranch.setTextColor(getColor(R.color.primary));
            binding.rowSwitchBranch.setClickable(true);
            binding.rowSwitchBranch.setOnClickListener(v ->
                    AppNavigationShell.showBranchSelectorDialog(this, (branchId, newName) -> bindProfileData()));
        } else {
            binding.tvProfileBranch.setText(branchName);
            binding.tvProfileBranch.setTextColor(getColor(R.color.text_primary));
            binding.rowSwitchBranch.setClickable(false);
            binding.rowSwitchBranch.setOnClickListener(null);
        }

        String username = (currentUser != null && currentUser.getUsername() != null && !currentUser.getUsername().isEmpty())
                ? "@" + currentUser.getUsername()
                : (currentUser != null && currentUser.getEmail() != null && !currentUser.getEmail().isEmpty())
                        ? "@" + currentUser.getEmail().split("@")[0]
                        : "@user";
        binding.tvProfileUsername.setText(username);

        int userId = UserSessionManager.getCurrentUserId(this);
        binding.tvProfileUserId.setText("#" + userId);

        String emailInfo = UserSessionManager.getUserEmail(this);
        if (emailInfo == null || emailInfo.trim().isEmpty()) {
            emailInfo = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail().trim() : "";
        }
        if (emailInfo.isEmpty()) {
            emailInfo = "Chưa cập nhật";
        }
        binding.tvProfileEmail.setText(emailInfo);

        String phoneInfo = (currentUser != null && currentUser.getPhone() != null && !currentUser.getPhone().isEmpty())
                ? currentUser.getPhone()
                : "Chưa cập nhật";
        binding.tvProfilePhone.setText(phoneInfo);

        String initial = (fullName != null && !fullName.trim().isEmpty())
                ? fullName.trim().substring(0, 1).toUpperCase()
                : "A";
        binding.tvProfileAvatar.setText(initial);

        String imageUrl = UserSessionManager.getCurrentUserImage(this);
        AppNavigationShell.renderAvatarImage(this, binding.ivProfileAvatar, binding.tvProfileAvatar, imageUrl);

        binding.flProfileAvatar.setOnClickListener(v -> showAvatarOptions());

        binding.tvCurrentServerUrl.setText(AppConfig.getEmarketBaseUrl(this));
    }

    /**
     * Hiển thị ActionSheet khi người dùng chạm vào ảnh đại diện:
     * Cho phép phóng to xem ảnh hiện tại (nếu có) hoặc chọn ảnh mới từ thư viện thiết bị.
     */
    private void showAvatarOptions() {
        String currentImg = UserSessionManager.getCurrentUserImage(this);
        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();
        if (currentImg != null && !currentImg.trim().isEmpty()) {
            actions.add(new ModernPickerSheets.ActionItem(
                    "Xem ảnh đại diện toàn màn hình",
                    "Phóng to ảnh đại diện hiện tại",
                    R.drawable.bg_badge_info,
                    R.color.primary,
                    () -> ImagePreviewDialog.showSingle(this, currentImg, UserSessionManager.getCurrentUserFullName(this))
            ));
        }
        actions.add(new ModernPickerSheets.ActionItem(
                "Tải ảnh đại diện mới từ thiết bị",
                "Chọn ảnh từ thư viện để cập nhật hồ sơ nhân sự",
                R.drawable.bg_badge_success,
                R.color.success_text,
                () -> pickAvatarLauncher.launch("image/*")
        ));
        ModernPickerSheets.showActionSheet(
                this,
                "Ảnh đại diện cá nhân",
                UserSessionManager.getCurrentUserFullName(this),
                actions
        );
    }

    /**
     * Tải file ảnh đại diện mới từ Uri thiết bị lên máy chủ qua DataRepository.
     * Sau khi upload thành công: lưu URL ảnh mới vào UserSessionManager, gọi API updateUser để đồng bộ DB,
     * và cập nhật lại avatar trên màn hình cũng như trong Navigation Drawer.
     */
    private void uploadNewAvatar(Uri uri) {
        final int userId = UserSessionManager.getCurrentUserId(this);
        AppToast.show(this, "Đang tải ảnh đại diện lên máy chủ...");
        repository.uploadUserAvatar(uri, userId, new DataRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String uploadedUrl) {
                // Lưu đường dẫn ảnh mới vào Session cục bộ và đồng bộ ngược lên hồ sơ User trên Backend
                UserSessionManager.setCurrentUserImage(ProfileActivity.this, uploadedUrl);
                User current = UserSessionManager.getCurrentUser(ProfileActivity.this);
                if (current != null && userId > 0) {
                    Map<String, Object> body = new HashMap<>();
                    body.put("userId", userId);
                    body.put("username", current.getUsername() != null ? current.getUsername() : UserSessionManager.getUsername(ProfileActivity.this));
                    body.put("fullName", current.getFullName() != null ? current.getFullName() : UserSessionManager.getCurrentUserFullName(ProfileActivity.this));
                    body.put("email", current.getEmail() != null ? current.getEmail() : "");
                    body.put("phone", current.getPhone() != null ? current.getPhone() : "");
                    body.put("image", uploadedUrl);
                    body.put("status", current.getStatus() != null ? current.getStatus() : "Active");
                    repository.updateUser(userId, body, new DataRepository.ApiCallback<Boolean>() {
                        @Override public void onSuccess(Boolean r) {}
                        @Override public void onError(String m) {}
                    });
                }
                bindProfileData();
                AppNavigationShell.bindDrawer(ProfileActivity.this, binding.drawerLayout, null);
                AppToast.show(ProfileActivity.this, "Đã cập nhật ảnh đại diện thành công!");
            }

            @Override
            public void onError(String message) {
                AppToast.show(ProfileActivity.this, "Lỗi tải ảnh đại diện: " + message);
            }
        });
    }

    /**
     * Gắn sự kiện click cho các mục cài đặt hệ thống trên màn hình Hồ sơ:
     * Cấu hình URL máy chủ Backend, Đổi mật khẩu và Đăng xuất tài khoản.
     */
    private void setupNavigationActions() {
        binding.rowServerSettings.setOnClickListener(v -> {
            ServerSettingsDialog.show(this, this::bindProfileData);
        });

        binding.rowChangePassword.setOnClickListener(v -> showChangePasswordSheet());
        binding.btnProfileLogout.setOnClickListener(v -> AppNavigationShell.confirmSignOut(this));
    }

    /**
     * Hiển thị BottomSheet đổi mật khẩu (sheet_change_password.xml) và kiểm tra tính hợp lệ của dữ liệu nhập
     * (bắt buộc nhập mật khẩu cũ, mật khẩu mới tối thiểu 6 ký tự, không trùng mật khẩu cũ và khớp xác nhận).
     */
    private void showChangePasswordSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_change_password, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        TextInputLayout tilOld = view.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = view.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = view.findViewById(R.id.tilConfirmPassword);

        TextInputEditText etOld = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancelChangePassword);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitChangePassword);

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            // Kiểm tra ràng buộc mật khẩu bằng FormValidator trước khi hoàn tất
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .required(tilOld, etOld, "Mật khẩu hiện tại")
                    .minLength(tilNew, etNew, "Mật khẩu mới", 6, true)
                    .custom(tilNew, etNew, val -> {
                        String oldVal = com.example.do_an_di_dong.util.FormValidator.textOf(etOld);
                        return !oldVal.isEmpty() && val.equals(oldVal) ? "Mật khẩu mới không được trùng mật khẩu cũ" : null;
                    })
                    .matches(tilConfirm, etConfirm, etNew, "Xác nhận mật khẩu mới không khớp")
                    .validate();
            if (!valid) {
                return;
            }
            sheet.dismiss();
            AppToast.show(this, "Đã cập nhật mật khẩu thành công!");
        });

        sheet.show();
    }
}
