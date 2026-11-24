package com.example.do_an_di_dong.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.do_an_di_dong.MainActivity;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Branch;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.ui.admin.CustomersActivity;
import com.example.do_an_di_dong.ui.admin.ExpensesActivity;
import com.example.do_an_di_dong.ui.admin.UsersActivity;
import com.example.do_an_di_dong.ui.ai.AIAssistantActivity;
import com.example.do_an_di_dong.ui.auth.LoginActivity;
import com.example.do_an_di_dong.ui.dashboard.SupplierDebtActivity;
import com.example.do_an_di_dong.ui.inventory.PurchaseOrdersActivity;
import com.example.do_an_di_dong.ui.inventory.WarehouseInventoryActivity;
import com.example.do_an_di_dong.ui.pos.PosActivity;
import com.example.do_an_di_dong.ui.product.ProductsActivity;
import com.example.do_an_di_dong.ui.profile.ProfileActivity;
import com.example.do_an_di_dong.ui.sales.OrdersHistoryActivity;
import com.example.do_an_di_dong.util.AppToast;
import com.example.do_an_di_dong.util.ImagePreviewDialog;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ khung điều hướng dùng chung cho toàn bộ kiến trúc Zero-Fragment (0 Fragment).
 * Gắn trực tiếp với các layout có chứa DrawerLayout, BottomNavigationView và sheet_confirm_logout.xml;
 * phối hợp với UserSessionManager, DataRepository và ApiClient để quản lý BottomNavigationView 5 tab,
 * AppDrawer trượt ngang, chọn Chi nhánh làm việc và ẩn/hiện menu theo phân quyền RBAC.
 */
public final class AppNavigationShell {

    private AppNavigationShell() {}

    public interface OnBranchChangedListener {
        void onBranchChanged(int branchId, String branchName);
    }

    /**
     * Khởi tạo trọn bộ khung điều hướng (nút mở Drawer, badge chọn Chi nhánh, menu Drawer và BottomNavigationView).
     * Được các Activity chính gọi ngay trong onCreate() để đồng bộ giao diện và gắn callback khi đổi chi nhánh.
     */
    public static void setup(
            final Activity activity,
            final DrawerLayout drawerLayout,
            final BottomNavigationView bottomNav,
            final int activeNavId,
            final View btnOpenDrawer,
            final TextView tvBranchBadge,
            final OnBranchChangedListener branchListener
    ) {
        if (btnOpenDrawer != null && drawerLayout != null) {
            btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (tvBranchBadge != null) {
            updateBranchLabel(activity, tvBranchBadge);
            // Chỉ cho phép bấm đổi chi nhánh nếu tài khoản có quyền Admin (canSwitchBranch)
            if (UserSessionManager.canSwitchBranch(activity)) {
                tvBranchBadge.setClickable(true);
                tvBranchBadge.setOnClickListener(v -> showBranchSelectorDialog(activity, (branchId, branchName) -> {
                    updateBranchLabel(activity, tvBranchBadge);
                    bindDrawer(activity, drawerLayout, branchListener);
                    if (branchListener != null) {
                        branchListener.onBranchChanged(branchId, branchName);
                    }
                }));
            } else {
                tvBranchBadge.setClickable(false);
                tvBranchBadge.setOnClickListener(null);
            }
        }

        if (drawerLayout != null) {
            bindDrawer(activity, drawerLayout, branchListener);
        }

        if (bottomNav != null) {
            setupBottomNavigation(activity, bottomNav, activeNavId);
        }
    }

    /**
     * Cập nhật nhãn tên chi nhánh trên thanh Header của Activity.
     * Ẩn badge nếu là tài khoản Nhà cung cấp (Supplier), hoặc thêm mũi tên "▾" nếu là Admin được phép chuyển chi nhánh.
     */
    private static void updateBranchLabel(Activity activity, TextView tvBranchBadge) {
        if (UserSessionManager.isSupplier(activity)) {
            tvBranchBadge.setVisibility(View.GONE);
            return;
        }
        tvBranchBadge.setVisibility(View.VISIBLE);
        String branchName = UserSessionManager.getCurrentBranchName(activity);
        tvBranchBadge.setText(UserSessionManager.canSwitchBranch(activity) ? (branchName + " ▾") : branchName);
    }

    /**
     * Cấu hình thanh điều hướng 5 tab dưới đáy màn hình (BottomNavigationView) và đánh dấu tab hiện tại.
     * Khi người dùng chạm vào tab khác, hàm điều hướng sang Activity tương ứng mà không tạo chồng thêm instance mới.
     */
    public static void setupBottomNavigation(final Activity activity, final BottomNavigationView bottomNav, final int activeNavId) {
        applyRbacToBottomNav(activity, bottomNav);
        bottomNav.setOnItemSelectedListener(null);
        if (bottomNav.getMenu().findItem(activeNavId) != null) {
            bottomNav.setSelectedItemId(activeNavId);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == activeNavId) {
                return true;
            }
            // Ánh xạ ID menu được chọn sang Activity đích của 5 tab chính
            Class<?> target = null;
            if (id == R.id.nav_dashboard) {
                target = MainActivity.class;
            } else if (id == R.id.nav_pos) {
                target = PosActivity.class;
            } else if (id == R.id.nav_products) {
                target = ProductsActivity.class;
            } else if (id == R.id.nav_warehouse) {
                target = WarehouseInventoryActivity.class;
            } else if (id == R.id.nav_profile) {
                target = ProfileActivity.class;
            }

            if (target != null) {
                navigateMainTab(activity, target);
                return true;
            }
            return false;
        });
    }

    /**
     * Áp dụng phân quyền RBAC lên các tab của BottomNavigationView dựa trên quyền trong UserSessionManager.
     * Ẩn các tab nghiệp vụ mà nhân viên hoặc nhà cung cấp không được cấp quyền truy cập (riêng tab Cá nhân luôn hiển thị).
     */
    public static void applyRbacToBottomNav(Activity activity, BottomNavigationView bottomNav) {
        if (bottomNav == null) return;
        Menu menu = bottomNav.getMenu();
        boolean isSupplier = UserSessionManager.isSupplier(activity);

        boolean canDashboard = !isSupplier && UserSessionManager.canAccessDashboard(activity);
        boolean canPos = !isSupplier && UserSessionManager.canAccessSales(activity);
        boolean canProducts = !isSupplier && UserSessionManager.canAccessProducts(activity);
        boolean canWarehouse = !isSupplier && UserSessionManager.canAccessInventory(activity);

        if (menu.findItem(R.id.nav_dashboard) != null) menu.findItem(R.id.nav_dashboard).setVisible(canDashboard);
        if (menu.findItem(R.id.nav_pos) != null) menu.findItem(R.id.nav_pos).setVisible(canPos);
        if (menu.findItem(R.id.nav_products) != null) menu.findItem(R.id.nav_products).setVisible(canProducts);
        if (menu.findItem(R.id.nav_warehouse) != null) menu.findItem(R.id.nav_warehouse).setVisible(canWarehouse);
        if (menu.findItem(R.id.nav_profile) != null) menu.findItem(R.id.nav_profile).setVisible(true);
    }

    private static boolean avatarSyncAttempted = false;

    /**
     * Đổ dữ liệu thông tin người dùng (avatar, họ tên, username, vai trò, chi nhánh) và phân quyền RBAC vào menu trượt ngang (Drawer).
     * Gắn sự kiện mở xem ảnh đại diện, đổi chi nhánh, mở từng phân hệ chức năng, cài đặt Server và đăng xuất.
     */
    public static void bindDrawer(final Activity activity, final DrawerLayout drawerLayout, final OnBranchChangedListener branchListener) {
        if (drawerLayout == null) return;

        User user = UserSessionManager.getCurrentUser(activity);
        String fullName = UserSessionManager.getCurrentUserFullName(activity);
        String username = UserSessionManager.getUsername(activity);
        String roleName = UserSessionManager.getRoleName(activity);
        String branchName = UserSessionManager.getCurrentBranchName(activity);
        String imageUrl = UserSessionManager.getCurrentUserImage(activity);

        TextView tvAvatar = drawerLayout.findViewById(R.id.tvDrawerAvatar);
        ImageView ivAvatar = drawerLayout.findViewById(R.id.ivDrawerAvatar);
        View flAvatar = drawerLayout.findViewById(R.id.flDrawerAvatar);
        TextView tvName = drawerLayout.findViewById(R.id.tvDrawerUserName);
        TextView tvHandle = drawerLayout.findViewById(R.id.tvDrawerUserHandle);
        TextView tvRole = drawerLayout.findViewById(R.id.tvDrawerRoleBadge);
        TextView tvBranch = drawerLayout.findViewById(R.id.btnDrawerBranchSwitch);

        if (tvAvatar != null) {
            String initial = (fullName != null && !fullName.trim().isEmpty())
                    ? fullName.trim().substring(0, 1).toUpperCase()
                    : "U";
            tvAvatar.setText(initial);
        }
        renderAvatarImage(activity, ivAvatar, tvAvatar, imageUrl);

        if (flAvatar != null) {
            flAvatar.setOnClickListener(v -> {
                String currentImg = UserSessionManager.getCurrentUserImage(activity);
                if (currentImg != null && !currentImg.trim().isEmpty()) {
                    ImagePreviewDialog.showSingle(activity, currentImg, UserSessionManager.getCurrentUserFullName(activity));
                } else {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    navigateMainTab(activity, ProfileActivity.class);
                }
            });
        }

        // Nếu phiên local chưa có URL ảnh đại diện, tự động gọi API đồng bộ 1 lần từ Backend
        if ((imageUrl == null || imageUrl.trim().isEmpty()) && !avatarSyncAttempted) {
            avatarSyncAttempted = true;
            syncUserAvatarFromServer(activity, ivAvatar, tvAvatar, tvName, tvHandle);
        }

        boolean isSupplier = UserSessionManager.isSupplier(activity);
        boolean canSwitchBranch = UserSessionManager.canSwitchBranch(activity);

        if (tvName != null) tvName.setText(fullName);
        if (tvHandle != null) tvHandle.setText("@" + username);
        if (tvRole != null) tvRole.setText(roleName);
        if (tvBranch != null) {
            // Rẽ nhánh hiển thị nút Chi nhánh trên Drawer theo 3 nhóm: Nhà cung cấp, Admin (được đổi), Nhân viên (chỉ xem)
            if (isSupplier) {
                tvBranch.setText("Đối tác Nhà cung cấp");
                tvBranch.setClickable(false);
                tvBranch.setOnClickListener(null);
            } else if (canSwitchBranch) {
                tvBranch.setText(branchName + " ▾");
                tvBranch.setClickable(true);
                tvBranch.setOnClickListener(v -> showBranchSelectorDialog(activity, (branchId, newBranchName) -> {
                    tvBranch.setText(newBranchName + " ▾");
                    View topBadge = activity.findViewById(R.id.tvBranchBadge);
                    if (topBadge instanceof TextView) {
                        updateBranchLabel(activity, (TextView) topBadge);
                    }
                    if (branchListener != null) {
                        branchListener.onBranchChanged(branchId, newBranchName);
                    }
                }));
            } else {
                tvBranch.setText(branchName + " (Chỉ xem)");
                tvBranch.setClickable(false);
                tvBranch.setOnClickListener(null);
            }
        }

        // Phân quyền RBAC chặt chẽ tương đồng 1:1 với Flutter AppDrawer
        boolean canDashboard = UserSessionManager.canAccessDashboard(activity);
        boolean canSales = UserSessionManager.canAccessSales(activity);
        boolean canProducts = UserSessionManager.canAccessProducts(activity);
        boolean canInventory = UserSessionManager.canAccessInventory(activity);
        boolean canCustomers = UserSessionManager.canAccessCustomers(activity);
        boolean canSuppliers = UserSessionManager.canAccessSuppliers(activity);
        boolean canExpenses = UserSessionManager.canAccessExpenses(activity);
        boolean canEmployees = UserSessionManager.canAccessEmployees(activity);
        boolean canAi = UserSessionManager.canAccessAiChat(activity);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavSupplierPortal, isSupplier, SupplierDebtActivity.class, false);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavDashboard, canDashboard, MainActivity.class, true);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavPos, canSales, PosActivity.class, true);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavProducts, canProducts, ProductsActivity.class, true);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavWarehouse, canInventory, WarehouseInventoryActivity.class, true);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavOrders, canSales, OrdersHistoryActivity.class, false);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavCustomers, canCustomers, CustomersActivity.class, false);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavSuppliers, canSuppliers && !isSupplier, SupplierDebtActivity.class, false);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavPurchases, canInventory, PurchaseOrdersActivity.class, false);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavExpenses, canExpenses, ExpensesActivity.class, false);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavUsers, canEmployees, UsersActivity.class, false);

        bindDrawerItem(activity, drawerLayout, R.id.drawerNavAI, canAi, AIAssistantActivity.class, false);
        bindDrawerItem(activity, drawerLayout, R.id.drawerNavProfile, true, ProfileActivity.class, true);

        boolean showMainSection = canDashboard || canSales || canProducts || canInventory;
        boolean showSalesSection = canSales || canCustomers;
        boolean showSupplySection = (canSuppliers && !isSupplier) || canInventory;
        boolean showFinanceSection = canExpenses || canEmployees;

        setSectionVisibility(drawerLayout, R.id.tvDrawerSectionMain, 0, showMainSection);
        setSectionVisibility(drawerLayout, R.id.tvDrawerSectionSales, R.id.drawerDividerSales, showSalesSection);
        setSectionVisibility(drawerLayout, R.id.tvDrawerSectionSupply, R.id.drawerDividerSupply, showSupplySection);
        setSectionVisibility(drawerLayout, R.id.tvDrawerSectionFinance, R.id.drawerDividerFinance, showFinanceSection);
        setSectionVisibility(drawerLayout, R.id.tvDrawerSectionSystem, R.id.drawerDividerSystem, true);

        View btnServerSettings = drawerLayout.findViewById(R.id.drawerNavServerSettings);
        if (btnServerSettings != null) {
            btnServerSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                ServerSettingsDialog.show(activity);
            });
        }

        View btnLogout = drawerLayout.findViewById(R.id.btnDrawerLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                confirmSignOut(activity);
            });
        }
    }

    /**
     * Ẩn hoặc hiện tiêu đề nhóm mục (Section Header) và đường kẻ phân cách (Divider) trong Drawer
     * khi toàn bộ các mục con trong nhóm đó bị ẩn do không có quyền RBAC.
     */
    private static void setSectionVisibility(DrawerLayout drawerLayout, int headerId, int dividerId, boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        View header = drawerLayout.findViewById(headerId);
        if (header != null) header.setVisibility(state);
        if (dividerId != 0) {
            View divider = drawerLayout.findViewById(dividerId);
            if (divider != null) divider.setVisibility(state);
        }
    }

    /**
     * Cấu hình từng dòng mục menu trong Drawer: kiểm tra quyền hiển thị, tô màu nổi bật nếu đang ở màn hình đó,
     * và gắn sự kiện chuyển màn hình khi người dùng bấm chọn.
     */
    private static void bindDrawerItem(
            final Activity activity,
            final DrawerLayout drawerLayout,
            int viewId,
            boolean hasAccess,
            final Class<?> targetActivity,
            final boolean isMainTab
    ) {
        View view = drawerLayout.findViewById(viewId);
        if (view == null) return;

        // Nếu không có quyền RBAC thì ẩn hoàn toàn mục menu khỏi Drawer
        if (!hasAccess) {
            view.setVisibility(View.GONE);
            view.setOnClickListener(null);
            return;
        }
        view.setVisibility(View.VISIBLE);

        boolean isActive = activity.getClass().equals(targetActivity);
        View titleView = view.findViewWithTag("drawerTitle");
        View chevronView = view.findViewWithTag("drawerChevron");

        if (isActive) {
            view.setBackgroundResource(R.drawable.bg_drawer_item_active);
            if (titleView instanceof TextView) {
                ((TextView) titleView).setTextColor(activity.getColor(R.color.primary));
            }
            if (chevronView instanceof ImageView) {
                ((ImageView) chevronView).setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.primary)));
            }
        } else {
            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            view.setBackgroundResource(outValue.resourceId);
            if (titleView instanceof TextView) {
                ((TextView) titleView).setTextColor(activity.getColor(R.color.text_primary));
            }
            if (chevronView instanceof ImageView) {
                ((ImageView) chevronView).setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.text_muted)));
            }
        }

        view.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            if (activity.getClass().equals(targetActivity)) {
                return;
            }
            // Nếu là 1 trong 5 tab chính thì tái sử dụng Activity trong stack, ngược lại mở Activity con mới
            if (isMainTab) {
                navigateMainTab(activity, targetActivity);
            } else {
                Intent intent = new Intent(activity, targetActivity);
                activity.startActivity(intent);
            }
        });
    }

    /**
     * Điều hướng giữa 5 màn hình chính (BottomNav / Drawer chính) bằng cờ FLAG_ACTIVITY_REORDER_TO_FRONT.
     * Giúp đưa Activity đã tồn tại lên đầu stack mà không khởi tạo lại hay gây hiệu ứng nháy chuyển cảnh.
     */
    public static void navigateMainTab(Activity currentActivity, Class<?> targetActivity) {
        if (currentActivity.getClass().equals(targetActivity)) return;
        Intent intent = new Intent(currentActivity, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        currentActivity.startActivity(intent);
        currentActivity.overridePendingTransition(0, 0);
    }

    /**
     * Mở hộp thoại chọn Chi nhánh làm việc (chỉ dành cho Admin).
     * Ưu tiên đọc danh sách chi nhánh từ cache trong UserSessionManager; nếu chưa có mới gọi DataRepository tải từ API.
     */
    public static void showBranchSelectorDialog(final Activity activity, final OnBranchChangedListener listener) {
        if (!UserSessionManager.canSwitchBranch(activity)) {
            AppToast.show(activity, "Chỉ Quản trị viên mới được chuyển chi nhánh");
            return;
        }
        DataRepository repo = new DataRepository(activity);
        List<Branch> cached = UserSessionManager.getCachedBranches(activity);
        if (cached != null && !cached.isEmpty()) {
            presentBranchDialog(activity, cached, listener);
            return;
        }

        // Gọi API lấy danh sách chi nhánh nếu cache trống, sau đó lưu cache và hiển thị BottomSheet
        repo.getFilteredBranches(null, null, new DataRepository.ApiCallback<List<Branch>>() {
            @Override
            public void onSuccess(List<Branch> result) {
                List<Branch> branches = result != null ? result : new ArrayList<>();
                UserSessionManager.saveBranches(activity, branches);
                presentBranchDialog(activity, branches, listener);
            }

            @Override
            public void onError(String message) {
                AppToast.show(activity, "Không tải được danh sách chi nhánh: " + message);
            }
        });
    }

    /**
     * Tải và hiển thị ảnh đại diện bo tròn bằng thư viện Glide từ đường dẫn tương đối hoặc tuyệt đối.
     * Nếu URL rỗng hoặc tải ảnh lỗi (onLoadFailed), tự động ẩn ImageView và hiện chữ cái đầu tên người dùng (tvAvatar).
     */
    public static void renderAvatarImage(Activity activity, ImageView ivAvatar, TextView tvAvatar, String imageUrl) {
        if (ivAvatar == null) return;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            String fullUrl = ApiClient.getFullImageUrl(activity, imageUrl.trim());
            ivAvatar.setVisibility(View.VISIBLE);
            Glide.with(activity)
                    .load(fullUrl)
                    .circleCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            ivAvatar.setVisibility(View.GONE);
                            if (tvAvatar != null) tvAvatar.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            ivAvatar.setVisibility(View.VISIBLE);
                            if (tvAvatar != null) tvAvatar.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(ivAvatar);
        } else {
            ivAvatar.setVisibility(View.GONE);
            if (tvAvatar != null) tvAvatar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Đồng bộ ngầm ảnh đại diện của user hiện tại từ API danh sách người dùng (DataRepository.getAllUsers)
     * trong trường hợp response lúc đăng nhập chưa trả kèm trường image.
     */
    private static void syncUserAvatarFromServer(
            final Activity activity,
            final ImageView ivAvatar,
            final TextView tvAvatar,
            final TextView tvName,
            final TextView tvHandle
    ) {
        final int currentUserId = UserSessionManager.getCurrentUserId(activity);
        if (currentUserId <= 0) return;

        DataRepository repo = new DataRepository(activity);
        repo.getAllUsers(new DataRepository.ApiCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> list) {
                if (list == null || list.isEmpty() || activity.isFinishing() || activity.isDestroyed()) return;
                User matched = null;
                for (User u : list) {
                    if (u != null && u.getUserId() == currentUserId) {
                        matched = u;
                        break;
                    }
                }
                // Tìm thấy user khớp ID và có ảnh thì lưu vào SharedPreferences và cập nhật ngay lên giao diện
                if (matched != null && matched.getImage() != null && !matched.getImage().trim().isEmpty()) {
                    UserSessionManager.setCurrentUserImage(activity, matched.getImage().trim());
                    renderAvatarImage(activity, ivAvatar, tvAvatar, matched.getImage().trim());
                }
            }

            @Override
            public void onError(String message) {
                // Silent fallback to initials
            }
        });
    }

    /**
     * Xây dựng danh sách lựa chọn chi nhánh (gồm mục "Tất cả chi nhánh" có branchId = 0 và từng chi nhánh cụ thể)
     * rồi hiển thị qua ModernPickerSheets. Khi chọn sẽ cập nhật branchId vào UserSessionManager và báo callback để màn hình tải lại dữ liệu.
     */
    private static void presentBranchDialog(final Activity activity, final List<Branch> branches, final OnBranchChangedListener listener) {
        int currentId = UserSessionManager.getCurrentBranchId(activity);
        List<ModernPickerSheets.ActionItem> actions = new ArrayList<>();

        // Tuỳ chọn branchId = 0 cho phép Admin xem báo cáo/tồn kho gộp của toàn hệ thống
        actions.add(new ModernPickerSheets.ActionItem(
                (currentId == 0 ? "✓ " : "") + "Tất cả chi nhánh (Toàn hệ thống)",
                "Tổng hợp dữ liệu doanh thu, đơn hàng và tồn kho toàn chuỗi",
                R.drawable.bg_badge_neutral,
                currentId == 0 ? R.color.primary : R.color.text_primary,
                () -> {
                    UserSessionManager.setCurrentBranchId(activity, 0, "Tất cả chi nhánh");
                    AppToast.show(activity, "Đã chuyển sang: Tất cả chi nhánh");
                    if (listener != null) listener.onBranchChanged(0, "Tất cả chi nhánh");
                }
        ));

        for (final Branch b : branches) {
            final int bId = b.getBranchId();
            final String bName = b.getName() != null ? b.getName() : ("Chi nhánh #" + bId);
            String bAddr = b.getAddress() != null && !b.getAddress().isEmpty() ? b.getAddress() : ("Mã chi nhánh #" + bId);
            boolean active = (bId == currentId);

            actions.add(new ModernPickerSheets.ActionItem(
                    (active ? "✓ " : "") + bName,
                    bAddr,
                    R.drawable.bg_badge_neutral,
                    active ? R.color.primary : R.color.text_primary,
                    () -> {
                        UserSessionManager.setCurrentBranchId(activity, bId, bName);
                        AppToast.show(activity, "Đã chuyển sang: " + bName);
                        if (listener != null) listener.onBranchChanged(bId, bName);
                    }
            ));
        }

        ModernPickerSheets.showActionSheet(
                activity,
                "Chọn Chi nhánh làm việc",
                "Dữ liệu bán hàng, báo cáo và tồn kho sẽ tự động lọc theo chi nhánh",
                actions
        );
    }

    /**
     * Hiển thị BottomSheet xác nhận đăng xuất (layout sheet_confirm_logout.xml) khi người dùng bấm nút Đăng xuất.
     */
    public static void confirmSignOut(final Activity activity) {
        final BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_confirm_logout, null);
        sheet.setContentView(view);

        View btnCancel = view.findViewById(R.id.btnCancelLogout);
        View btnConfirm = view.findViewById(R.id.btnConfirmLogout);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> sheet.dismiss());
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                sheet.dismiss();
                signOut(activity);
            });
        }
        sheet.show();
    }

    /**
     * Thực thi đăng xuất hoàn toàn: xoá JWT token trong ApiClient, xoá dữ liệu phiên trong UserSessionManager,
     * và điều hướng về LoginActivity với cờ CLEAR_TASK để xoá sạch lịch sử màn hình cũ.
     */
    public static void signOut(final Activity activity) {
        avatarSyncAttempted = false;
        com.example.do_an_di_dong.util.AuthSessionGuard.getInstance(activity).onManualSignOut();
        ApiClient.clearToken(activity);
        UserSessionManager.clearSession(activity);
        AppToast.show(activity, "Đã đăng xuất.");
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.putExtra("force_login", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}

