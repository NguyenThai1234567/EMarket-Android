package com.example.do_an_di_dong.ui.admin;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityUsersBinding;
import com.example.do_an_di_dong.model.Branch;
import com.example.do_an_di_dong.model.Role;
import com.example.do_an_di_dong.model.SupplierDTO;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.ui.common.ModernConfirmSheet;
import com.example.do_an_di_dong.ui.common.ModernPickerSheets;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Quản lý Tài khoản Nhân sự & Phân quyền RBAC (UsersActivity).
 * - Gắn với layout chính: activity_users.xml (ViewBinding: ActivityUsersBinding)
 *   và BottomSheet thêm/sửa nhân sự: sheet_user_form.xml.
 * - Phối hợp cùng: UserAdapter (hiển thị & lọc danh sách nhân viên) và DataRepository
 *   (CRUD tài khoản, upload ảnh đại diện, tải danh mục Vai trò Role, Chi nhánh Branch, Nhà cung cấp Supplier).
 */
public class UsersActivity extends AppCompatActivity {
    private ActivityUsersBinding binding;
    private UserAdapter adapter;
    private DataRepository repository;

    private ActivityResultLauncher<String> pickStaffAvatarLauncher;
    private Uri pendingAvatarUri;
    private ImageView activeAvatarPreview;
    private TextView activeAvatarInitial;
    private TextView activeAvatarHint;

    private final List<Role> cachedRoles = new ArrayList<>();
    private final List<Branch> cachedBranches = new ArrayList<>();
    private final List<SupplierDTO> cachedSuppliers = new ArrayList<>();

    /**
     * Khởi tạo màn hình Quản lý nhân sự, đăng ký bộ chọn ảnh từ thư viện (ActivityResultLauncher),
     * cấu hình RecyclerView/UserAdapter, tìm kiếm debounce và tải trước dữ liệu danh mục (Roles/Branches/Suppliers).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        adapter = new UserAdapter();

        // Nhận Uri ảnh đại diện được chọn từ thư viện thiết bị để hiển thị preview trong BottomSheet trước khi upload
        pickStaffAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pendingAvatarUri = uri;
                        if (activeAvatarPreview != null) {
                            activeAvatarPreview.setVisibility(View.VISIBLE);
                            Glide.with(UsersActivity.this)
                                    .load(uri)
                                    .circleCrop()
                                    .into(activeAvatarPreview);
                        }
                        if (activeAvatarInitial != null) {
                            activeAvatarInitial.setVisibility(View.GONE);
                        }
                        if (activeAvatarHint != null) {
                            activeAvatarHint.setText("Đã chọn ảnh mới (sẽ tải lên khi Lưu)");
                        }
                    }
                }
        );

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> loadUsers());
        binding.swipeRefreshUsers.setOnRefreshListener(this::loadUsers);

        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUsers.setAdapter(adapter);

        adapter.setListener(new UserAdapter.OnUserActionListener() {
            @Override
            public void onEditUser(User user) {
                showUserFormSheet(user);
            }

            @Override
            public void onDeleteUser(User user) {
                confirmAndDeleteUser(user);
            }
        });

        binding.etSearchUser.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(
                q -> adapter.filter(q)
        ));

        binding.fabAddUser.setOnClickListener(v -> showUserFormSheet(null));

        preloadMetadata(null);
        loadUsers();
    }

    /**
     * Tải trước (preload) 3 danh mục nền tảng từ Backend: Vai trò (Roles), Chi nhánh (Branches)
     * và Nhà cung cấp (Suppliers) để sẵn sàng hiển thị trong BottomSheet tạo/sửa nhân sự.
     */
    private void preloadMetadata(final Runnable onUpdated) {
        repository.getAllRoles(new DataRepository.ApiCallback<List<Role>>() {
            @Override
            public void onSuccess(List<Role> roles) {
                cachedRoles.clear();
                if (roles != null) cachedRoles.addAll(roles);
                if (onUpdated != null) onUpdated.run();
            }

            @Override
            public void onError(String message) {
                if (onUpdated != null) onUpdated.run();
            }
        });

        repository.getBranches(new DataRepository.ApiCallback<List<Branch>>() {
            @Override
            public void onSuccess(List<Branch> branches) {
                cachedBranches.clear();
                if (branches != null) cachedBranches.addAll(branches);
                if (onUpdated != null) onUpdated.run();
            }

            @Override
            public void onError(String message) {
            }
        });

        repository.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
            @Override
            public void onSuccess(List<SupplierDTO> suppliers) {
                cachedSuppliers.clear();
                if (suppliers != null) cachedSuppliers.addAll(suppliers);
                if (onUpdated != null) onUpdated.run();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    /**
     * Gọi API lấy danh sách toàn bộ tài khoản người dùng trong hệ thống và đổ vào UserAdapter.
     * Được gọi khi mở màn hình, kéo SwipeRefresh, hoặc sau khi thêm/sửa/xóa tài khoản thành công.
     */
    private void loadUsers() {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.getAllUsers(new DataRepository.ApiCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> list) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshUsers.setRefreshing(false);
                adapter.setUsers(list);
                boolean empty = (list == null || list.isEmpty());
                binding.tvEmptyUsers.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.rvUsers.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefreshUsers.setRefreshing(false);
                AppToast.show(UsersActivity.this, message);
            }
        });
    }

    /**
     * Hiển thị BottomSheet thêm mới (existingUser == null) hoặc cập nhật nhân sự (existingUser != null).
     * Xử lý logic phân quyền RBAC động:
     * - Nếu chọn vai trò Supplier -> bắt buộc chọn Nhà cung cấp liên kết (ẩn chọn chi nhánh).
     * - Nếu chọn vai trò Admin -> toàn quyền hệ thống, không gắn cố định vào chi nhánh lẻ.
     * - Nếu chọn vai trò Nhân viên/Quản lý -> bắt buộc chọn Chi nhánh làm việc.
     */
    private void showUserFormSheet(final User existingUser) {
        final boolean isEdit = existingUser != null;
        pendingAvatarUri = null;

        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_user_form, null);
        sheet.setContentView(view);
        com.example.do_an_di_dong.ui.common.BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvUserSheetTitle);
        activeAvatarPreview = view.findViewById(R.id.ivStaffAvatarPreview);
        activeAvatarInitial = view.findViewById(R.id.tvStaffAvatarInitial);
        activeAvatarHint = view.findViewById(R.id.tvStaffAvatarHint);
        View flAvatarPicker = view.findViewById(R.id.flStaffAvatarPicker);
        MaterialButton btnPickAvatar = view.findViewById(R.id.btnPickStaffAvatar);

        TextInputLayout tilUsername = view.findViewById(R.id.tilStaffUsername);
        TextInputLayout tilFullName = view.findViewById(R.id.tilStaffFullName);
        TextInputLayout tilPhone = view.findViewById(R.id.tilStaffPhone);
        TextInputLayout tilEmail = view.findViewById(R.id.tilStaffEmail);
        TextInputLayout tilPassword = view.findViewById(R.id.tilStaffPassword);

        TextInputEditText etUsername = view.findViewById(R.id.etStaffUsername);
        TextInputEditText etFullName = view.findViewById(R.id.etStaffFullName);
        TextInputEditText etPhone = view.findViewById(R.id.etStaffPhone);
        TextInputEditText etEmail = view.findViewById(R.id.etStaffEmail);
        TextInputEditText etPassword = view.findViewById(R.id.etStaffPassword);
        final TextView tvRoleLoadingHint = view.findViewById(R.id.tvRoleLoadingHint);
        final ChipGroup chipGroupRole = view.findViewById(R.id.chipGroupStaffRole);
        final View layoutBranchPicker = view.findViewById(R.id.layoutStaffBranchPicker);
        final View tvAdminBranchNote = view.findViewById(R.id.tvStaffAdminBranchNote);
        final View cardBranchPicker = view.findViewById(R.id.cardStaffBranchPicker);
        final TextView tvSelectedBranch = view.findViewById(R.id.tvSelectedStaffBranch);
        final View layoutSupplierPicker = view.findViewById(R.id.layoutStaffSupplierPicker);
        final View cardSupplierPicker = view.findViewById(R.id.cardStaffSupplierPicker);
        final TextView tvSelectedSupplier = view.findViewById(R.id.tvSelectedStaffSupplier);
        final ChipGroup chipGroupStatus = view.findViewById(R.id.chipGroupStaffStatus);

        com.example.do_an_di_dong.util.FormValidator validator = com.example.do_an_di_dong.util.FormValidator.create()
                .required(tilFullName, etFullName, "Họ và tên")
                .phone(tilPhone, etPhone, "Số điện thoại", false)
                .email(tilEmail, etEmail, "Email nội bộ", false)
                .minLength(tilPassword, etPassword, "Mật khẩu", 6, !isEdit);
        if (!isEdit) {
            validator.username(tilUsername, etUsername, "Tên đăng nhập");
        }

        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteStaffUser);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelStaffUser);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveStaffUser);

        final Role[] selectedRole = new Role[]{null};
        final Branch[] selectedBranch = new Branch[]{null};
        final SupplierDTO[] selectedSupplier = new SupplierDTO[]{null};
        final Integer initialBranchId = isEdit && existingUser.getBranchId() != null
                ? existingUser.getBranchId()
                : UserSessionManager.getCurrentBranchId(this);
        final Integer initialSupplierId = isEdit ? existingUser.getSupplierId() : null;

        final Runnable resolveBranchAndSupplierDefaults = () -> {
            if (selectedBranch[0] == null && !cachedBranches.isEmpty()) {
                if (initialBranchId != null && initialBranchId > 0) {
                    for (Branch b : cachedBranches) {
                        if (b.getBranchId() == initialBranchId) {
                            selectedBranch[0] = b;
                            break;
                        }
                    }
                }
                if (selectedBranch[0] == null) {
                    selectedBranch[0] = cachedBranches.get(0);
                }
            }
            if (selectedSupplier[0] == null && initialSupplierId != null && initialSupplierId > 0 && !cachedSuppliers.isEmpty()) {
                for (SupplierDTO s : cachedSuppliers) {
                    if (s.getSupplierId() == initialSupplierId) {
                        selectedSupplier[0] = s;
                        break;
                    }
                }
            }
        };

        // Rẽ nhánh giao diện theo vai trò RBAC được chọn: Đối tác NCC (chọn Supplier), Admin (toàn hệ thống), Nhân viên (chọn Branch)
        final Runnable updateRoleDependentSections = () -> {
            resolveBranchAndSupplierDefaults.run();
            boolean supplierMode = User.isSupplierRole(selectedRole[0]);
            boolean adminMode = !supplierMode && selectedRole[0] != null && User.isAdminRoleName(selectedRole[0].getName());

            if (supplierMode) {
                layoutBranchPicker.setVisibility(View.GONE);
                layoutSupplierPicker.setVisibility(View.VISIBLE);
                if (selectedSupplier[0] != null) {
                    tvSelectedSupplier.setText(selectedSupplier[0].getName());
                } else if (initialSupplierId != null && initialSupplierId > 0) {
                    tvSelectedSupplier.setText("Nhà cung cấp #" + initialSupplierId);
                } else {
                    tvSelectedSupplier.setText("Nhấn để chọn Nhà cung cấp...");
                }
            } else if (adminMode) {
                layoutSupplierPicker.setVisibility(View.GONE);
                layoutBranchPicker.setVisibility(View.VISIBLE);
                tvAdminBranchNote.setVisibility(View.VISIBLE);
                cardBranchPicker.setVisibility(View.GONE);
            } else {
                layoutSupplierPicker.setVisibility(View.GONE);
                layoutBranchPicker.setVisibility(View.VISIBLE);
                tvAdminBranchNote.setVisibility(View.GONE);
                cardBranchPicker.setVisibility(View.VISIBLE);
                if (selectedBranch[0] != null) {
                    tvSelectedBranch.setText(selectedBranch[0].getName());
                } else {
                    tvSelectedBranch.setText("Nhấn để chọn chi nhánh...");
                }
            }
        };

        final Runnable populateRoleChips = () -> {
            if (cachedRoles.isEmpty()) {
                tvRoleLoadingHint.setVisibility(View.VISIBLE);
                tvRoleLoadingHint.setText("Đang tải danh sách vai trò từ máy chủ...");
                return;
            }
            tvRoleLoadingHint.setVisibility(View.GONE);
            if (chipGroupRole.getChildCount() > 0 && selectedRole[0] != null) {
                updateRoleDependentSections.run();
                return;
            }

            chipGroupRole.removeAllViews();
            Role targetRole = findBestMatchingRole(existingUser, cachedRoles);
            Chip targetChip = null;

            for (final Role role : cachedRoles) {
                Chip chip = new Chip(this);
                chip.setId(View.generateViewId());
                chip.setText(role.getName() != null ? role.getName() : ("Vai trò #" + role.getRoleId()));
                chip.setCheckable(true);
                chip.setTag(role);
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedRole[0] = role;
                        updateRoleDependentSections.run();
                    }
                });
                chipGroupRole.addView(chip);
                if (targetRole != null && role.getRoleId() == targetRole.getRoleId()) {
                    targetChip = chip;
                }
            }

            if (targetChip == null && chipGroupRole.getChildCount() > 0) {
                targetChip = (Chip) chipGroupRole.getChildAt(0);
            }
            if (targetChip != null) {
                chipGroupRole.check(targetChip.getId());
                selectedRole[0] = (Role) targetChip.getTag();
            }
            updateRoleDependentSections.run();
        };

        if (btnPickAvatar != null) {
            btnPickAvatar.setOnClickListener(v -> pickStaffAvatarLauncher.launch("image/*"));
        }

        if (isEdit) {
            tvTitle.setText("Cập nhật Nhân sự: " + existingUser.getUsername());
            etUsername.setText(existingUser.getUsername());
            etUsername.setEnabled(false);
            etFullName.setText(existingUser.getFullName());
            etPhone.setText(existingUser.getPhone());
            etEmail.setText(existingUser.getEmail());
            tilPassword.setHint("Mật khẩu mới (Để trống nếu giữ nguyên)");

            if (existingUser.isLocked()) {
                chipGroupStatus.check(R.id.chipStatusLocked);
            } else {
                chipGroupStatus.check(R.id.chipStatusActive);
            }

            String name = existingUser.getFullName();
            if (activeAvatarInitial != null) {
                String init = (name != null && !name.trim().isEmpty()) ? name.trim().substring(0, 1).toUpperCase() : "U";
                activeAvatarInitial.setText(init);
            }
            if (existingUser.getImage() != null && !existingUser.getImage().trim().isEmpty()) {
                if (activeAvatarPreview != null) {
                    activeAvatarPreview.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(ApiClient.getFullImageUrl(this, existingUser.getImage().trim()))
                            .circleCrop()
                            .into(activeAvatarPreview);
                }
                if (activeAvatarInitial != null) activeAvatarInitial.setVisibility(View.GONE);
                if (activeAvatarHint != null) activeAvatarHint.setText("Nhấn vào ảnh để xem rõ hoặc chọn ảnh mới");
                if (flAvatarPicker != null) {
                    flAvatarPicker.setOnClickListener(v -> {
                        if (pendingAvatarUri == null) {
                            ImagePreviewDialog.showSingle(this, existingUser.getImage().trim(), existingUser.getFullName());
                        } else {
                            pickStaffAvatarLauncher.launch("image/*");
                        }
                    });
                }
            } else if (flAvatarPicker != null) {
                flAvatarPicker.setOnClickListener(v -> pickStaffAvatarLauncher.launch("image/*"));
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                sheet.dismiss();
                confirmAndDeleteUser(existingUser);
            });
        } else {
            chipGroupStatus.check(R.id.chipStatusActive);
            if (flAvatarPicker != null) {
                flAvatarPicker.setOnClickListener(v -> pickStaffAvatarLauncher.launch("image/*"));
            }
        }

        cardBranchPicker.setOnClickListener(v -> {
            if (cachedBranches.isEmpty()) {
                AppToast.show(this, "Đang tải danh sách chi nhánh...", AppToast.Type.INFO);
                preloadMetadata(() -> {
                    if (sheet.isShowing()) updateRoleDependentSections.run();
                });
                return;
            }
            List<ModernPickerSheets.ActionItem> branchItems = new ArrayList<>();
            for (final Branch branch : cachedBranches) {
                branchItems.add(new ModernPickerSheets.ActionItem(
                        branch.getName() != null ? branch.getName() : ("Chi nhánh #" + branch.getBranchId()),
                        branch.getAddress(),
                        0,
                        0,
                        () -> {
                            selectedBranch[0] = branch;
                            tvSelectedBranch.setText(branch.getName());
                        }
                ));
            }
            ModernPickerSheets.showActionSheet(this, "Chọn chi nhánh làm việc", null, branchItems);
        });

        cardSupplierPicker.setOnClickListener(v -> ModernPickerSheets.showSupplierPicker(this, supplier -> {
            selectedSupplier[0] = supplier;
            tvSelectedSupplier.setText(supplier.getName());
        }));

        populateRoleChips.run();
        if (cachedRoles.isEmpty() || cachedBranches.isEmpty() || cachedSuppliers.isEmpty()) {
            preloadMetadata(() -> {
                if (sheet.isShowing()) {
                    populateRoleChips.run();
                    if (cachedRoles.isEmpty()) {
                        tvRoleLoadingHint.setText("Không tải được danh sách vai trò. Vui lòng thử mở lại.");
                    }
                }
            });
        }

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSave.setOnClickListener(v -> {
            if (!validator.validate()) {
                return;
            }
            if (selectedRole[0] == null) {
                AppToast.show(this, "Chưa tải được danh sách vai trò từ máy chủ. Vui lòng đợi hoặc thử lại!", AppToast.Type.WARNING);
                preloadMetadata(() -> {
                    if (sheet.isShowing()) populateRoleChips.run();
                });
                return;
            }

            // Kiểm tra ràng buộc RBAC trước khi gửi request: Tài khoản Supplier phải có supplierId, Nhân viên thường phải có branchId
            boolean supplierMode = User.isSupplierRole(selectedRole[0]);
            boolean adminMode = !supplierMode && User.isAdminRoleName(selectedRole[0].getName());
            Integer resolvedSupplierId = selectedSupplier[0] != null ? selectedSupplier[0].getSupplierId() : initialSupplierId;

            if (supplierMode && (resolvedSupplierId == null || resolvedSupplierId <= 0)) {
                AppToast.show(this, "Vui lòng chọn Nhà cung cấp liên kết cho tài khoản này!", AppToast.Type.WARNING);
                return;
            }
            if (!supplierMode && !adminMode && selectedBranch[0] == null) {
                AppToast.show(this, "Vui lòng chọn chi nhánh làm việc cho nhân viên!", AppToast.Type.WARNING);
                return;
            }

            String username = com.example.do_an_di_dong.util.FormValidator.textOf(etUsername);
            String fullName = com.example.do_an_di_dong.util.FormValidator.textOf(etFullName);
            String phone = com.example.do_an_di_dong.util.FormValidator.normalizePhone(
                    com.example.do_an_di_dong.util.FormValidator.textOf(etPhone));
            String email = com.example.do_an_di_dong.util.FormValidator.textOf(etEmail);
            String password = com.example.do_an_di_dong.util.FormValidator.textOf(etPassword);
            boolean isLocked = chipGroupStatus.getCheckedChipId() == R.id.chipStatusLocked;

            // Đóng gói payload JSON gửi lên API tạo/cập nhật User theo đúng cấu trúc phân quyền
            final Map<String, Object> body = new HashMap<>();
            if (isEdit) {
                body.put("userId", existingUser.getUserId());
            }
            body.put("username", username);
            body.put("fullName", fullName);
            body.put("email", email);
            body.put("phone", phone);
            body.put("roleId", selectedRole[0].getRoleId());
            body.put("isAdmin", adminMode);
            body.put("isSupplier", supplierMode);
            body.put("status", isLocked ? "Locked" : "Active");
            if (supplierMode) {
                body.put("supplierId", resolvedSupplierId);
                body.put("branchId", null);
            } else if (adminMode) {
                body.put("branchId", null);
                body.put("supplierId", null);
            } else {
                body.put("branchId", selectedBranch[0].getBranchId());
                body.put("supplierId", null);
            }
            if (isEdit && existingUser.getImage() != null && !existingUser.getImage().isEmpty()) {
                body.put("image", existingUser.getImage());
            }
            if (!password.isEmpty()) {
                body.put("password", password);
            }

            btnSave.setEnabled(false);
            btnSave.setText("Đang lưu...");

            final DataRepository.ApiCallback<Boolean> saveCallback = new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    // Nếu đang sửa chính tài khoản đang đăng nhập thì đồng bộ lại URL ảnh đại diện mới vào Session
                    if (isEdit && existingUser.getUserId() == UserSessionManager.getCurrentUserId(UsersActivity.this)) {
                        Object imgObj = body.get("image");
                        if (imgObj != null) {
                            UserSessionManager.setCurrentUserImage(UsersActivity.this, imgObj.toString());
                        }
                    }
                    sheet.dismiss();
                    AppToast.show(UsersActivity.this, isEdit ? "Đã cập nhật nhân sự!" : "Đã tạo tài khoản nhân sự!", AppToast.Type.SUCCESS);
                    loadUsers();
                }

                @Override
                public void onError(String message) {
                    btnSave.setEnabled(true);
                    btnSave.setText("Lưu Nhân Sự");
                    AppToast.show(UsersActivity.this, message, AppToast.Type.ERROR);
                }
            };

            // Nếu người dùng có chọn ảnh đại diện mới từ thiết bị -> Upload ảnh lên server trước, nhận URL rồi mới gọi API lưu User
            if (pendingAvatarUri != null) {
                int targetUserId = isEdit ? existingUser.getUserId() : 0;
                repository.uploadUserAvatar(pendingAvatarUri, targetUserId, new DataRepository.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String uploadedUrl) {
                        body.put("image", uploadedUrl);
                        if (isEdit) {
                            repository.updateUser(existingUser.getUserId(), body, saveCallback);
                        } else {
                            repository.createUser(body, saveCallback);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Lưu Nhân Sự");
                        AppToast.show(UsersActivity.this, "Lỗi tải ảnh đại diện: " + message, AppToast.Type.ERROR);
                    }
                });
            } else {
                if (isEdit) {
                    repository.updateUser(existingUser.getUserId(), body, saveCallback);
                } else {
                    repository.createUser(body, saveCallback);
                }
            }
        });

        sheet.show();
    }

    /**
     * Tìm đối tượng Role khớp nhất với tài khoản hiện tại trong danh sách Role tải từ server
     * để tự động check sẵn Chip vai trò khi mở form chỉnh sửa (hoặc chọn mặc định vai trò nhân viên khi tạo mới).
     */
    private Role findBestMatchingRole(User existingUser, List<Role> roles) {
        if (roles == null || roles.isEmpty()) return null;
        // Khi thêm mới: ưu tiên chọn sẵn một vai trò nhân viên thường (không phải Admin hay Supplier)
        if (existingUser == null) {
            for (Role r : roles) {
                if (!User.isAdminRoleName(r.getName()) && !User.isSupplierRole(r)) {
                    return r;
                }
            }
            return roles.get(0);
        }

        // Khi chỉnh sửa: đối chiếu theo cờ isSupplier, isAdmin, roleId hoặc tên vai trò của User
        if (existingUser.isSupplier()) {
            for (Role r : roles) {
                if (User.isSupplierRole(r)) return r;
            }
        }
        if (existingUser.isAdmin()) {
            for (Role r : roles) {
                if (User.isAdminRoleName(r.getName())) return r;
            }
        }

        int resolvedId = existingUser.getResolvedRoleId();
        if (resolvedId > 0) {
            for (Role r : roles) {
                if (r.getRoleId() == resolvedId) return r;
            }
        }

        if (existingUser.getRoles() != null) {
            for (Role ur : existingUser.getRoles()) {
                if (ur == null) continue;
                for (Role r : roles) {
                    if (ur.getRoleId() > 0 && r.getRoleId() == ur.getRoleId()) return r;
                    if (ur.getName() != null && r.getName() != null
                            && ur.getName().trim().equalsIgnoreCase(r.getName().trim())) {
                        return r;
                    }
                }
            }
        }

        String primaryName = existingUser.getPrimaryRoleName();
        if (primaryName != null && !primaryName.trim().isEmpty()) {
            String key = primaryName.trim().toLowerCase(Locale.ROOT);
            for (Role r : roles) {
                if (r.getName() != null && r.getName().trim().toLowerCase(Locale.ROOT).equals(key)) {
                    return r;
                }
            }
        }
        return roles.get(0);
    }

    /**
     * Hiển thị hộp thoại xác nhận nguy hiểm (ModernConfirmSheet) trước khi gọi API xóa tài khoản nhân sự.
     * Kích hoạt khi bấm nút thùng rác trên dòng nhân viên hoặc nút Xóa trong BottomSheet chỉnh sửa.
     */
    private void confirmAndDeleteUser(final User user) {
        String displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty())
                ? user.getFullName().trim()
                : (user.getUsername() != null ? user.getUsername() : ("#" + user.getUserId()));
        ModernConfirmSheet.confirmDanger(
                this,
                "Xác nhận xóa nhân sự?",
                "Bạn có chắc chắn muốn xóa tài khoản \"" + displayName + "\" (#" + user.getUserId() + ") khỏi hệ thống?",
                "Xóa tài khoản",
                () -> repository.deleteUser(user.getUserId(), new DataRepository.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        AppToast.show(UsersActivity.this, "Đã xóa tài khoản nhân sự!", AppToast.Type.SUCCESS);
                        loadUsers();
                    }

                    @Override
                    public void onError(String message) {
                        AppToast.show(UsersActivity.this, message, AppToast.Type.ERROR);
                    }
                })
        );
    }
}
