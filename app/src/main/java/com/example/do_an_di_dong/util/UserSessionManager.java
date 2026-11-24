package com.example.do_an_di_dong.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.do_an_di_dong.model.Branch;
import com.example.do_an_di_dong.model.User;
import com.example.do_an_di_dong.model.Warehouse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Trình quản lý phiên đăng nhập (Session Store) phía Client sử dụng SharedPreferences ("EMarketUserSession").
 * Lưu trữ đối tượng User (dạng JSON), chi nhánh đang chọn (branchId), cache danh sách Chi nhánh/Kho hàng,
 * đồng thời cung cấp các hàm kiểm tra phân quyền RBAC và điều hướng màn hình đích (Landing Activity) cho toàn bộ ứng dụng.
 */
public class UserSessionManager {
    private static final String PREF_NAME = "EMarketUserSession";
    private static final String KEY_USER_JSON = "key_user_json";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USERNAME = "key_username";
    private static final String KEY_USER_FULL_NAME = "key_user_full_name";
    private static final String KEY_USER_EMAIL = "key_user_email";
    private static final String KEY_USER_IMAGE = "key_user_image";
    private static final String KEY_BRANCH_ID = "key_branch_id";
    private static final String KEY_BRANCH_NAME = "key_branch_name";
    private static final String KEY_CACHED_BRANCHES = "key_cached_branches";
    private static final String KEY_CACHED_WAREHOUSES = "key_cached_warehouses";

    private static final Gson gson = new Gson();

    /**
     * Lưu thông tin User vào SharedPreferences ngay sau khi đăng nhập thành công hoặc đồng bộ hồ sơ cá nhân.
     * Xoá sạch các khoá định danh cũ trước khi ghi đè JSON User mới và gán chi nhánh mặc định (fallback về branchId = 1).
     */
    public static void saveUserSession(Context context, User user) {
        if (context == null || user == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_USER_FULL_NAME)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_IMAGE)
                .remove(KEY_USER_JSON);

        editor.putInt(KEY_USER_ID, user.getUserId());
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            editor.putString(KEY_USERNAME, user.getUsername());
        }
        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
            editor.putString(KEY_USER_FULL_NAME, user.getFullName());
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            editor.putString(KEY_USER_EMAIL, user.getEmail());
        }
        if (user.getImage() != null && !user.getImage().isEmpty()) {
            editor.putString(KEY_USER_IMAGE, user.getImage());
        }
        editor.putInt(KEY_BRANCH_ID, user.getBranchId() != null ? user.getBranchId() : 1);
        editor.putString(KEY_USER_JSON, gson.toJson(user));
        editor.apply();
    }

    /**
     * Lấy email của người dùng hiện tại từ đối tượng User (hoặc fallback từ khoá KEY_USER_EMAIL trong SharedPreferences).
     */
    public static String getUserEmail(Context context) {
        if (context == null) return "";
        User user = getCurrentUser(context);
        if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    /**
     * Lấy tên đăng nhập (username) để hiển thị handle "@username" trên Drawer và trang Cá nhân.
     * Nếu Backend không trả về username riêng, tự động cắt phần tiền tố trước dấu "@" của email làm username.
     */
    public static String getUsername(Context context) {
        if (context == null) return "";
        User user = getCurrentUser(context);
        if (user != null && user.getUsername() != null && !user.getUsername().isEmpty()) {
            return user.getUsername();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_USERNAME, "");
        if (!saved.isEmpty()) return saved;
        if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
            return user.getEmail().split("@")[0];
        }
        return "";
    }

    /**
     * Lấy đường dẫn ảnh đại diện của user đang đăng nhập (ưu tiên từ object User, sau đó đến KEY_USER_IMAGE).
     */
    public static String getCurrentUserImage(Context context) {
        if (context == null) return null;
        User user = getCurrentUser(context);
        if (user != null && user.getImage() != null && !user.getImage().trim().isEmpty()) {
            return user.getImage().trim();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String img = prefs.getString(KEY_USER_IMAGE, null);
        if (img != null && !img.trim().isEmpty()) return img.trim();
        return null;
    }

    /**
     * Cập nhật riêng đường dẫn ảnh đại diện vào SharedPreferences khi đồng bộ ngầm hoặc sau khi người dùng đổi avatar.
     */
    public static void setCurrentUserImage(Context context, String imageUrl) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_IMAGE, imageUrl != null ? imageUrl.trim() : "").apply();
    }

    /**
     * Trả về ID số nguyên của người dùng đang đăng nhập (trả về 0 nếu chưa đăng nhập).
     */
    public static int getCurrentUserId(Context context) {
        if (context == null) return 0;
        User user = getCurrentUser(context);
        if (user != null && user.getUserId() > 0) {
            return user.getUserId();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_USER_ID, 0);
    }

    /**
     * Lấy mã chi nhánh (branchId) đang được chọn làm việc để truyền vào query lọc đơn hàng, tồn kho và báo cáo.
     * Giá trị 0 đại diện cho "Tất cả chi nhánh" (chế độ xem toàn chuỗi của Admin), mặc định trả về 1.
     */
    public static int getCurrentBranchId(Context context) {
        if (context == null) return 1;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_BRANCH_ID, 1);
    }

    /**
     * Lưu mã chi nhánh và tên chi nhánh mới vào SharedPreferences khi Admin chuyển đổi chi nhánh làm việc trên Header/Drawer.
     */
    public static void setCurrentBranchId(Context context, int branchId, String branchName) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_BRANCH_ID, branchId)
                .putString(KEY_BRANCH_NAME, branchName)
                .apply();
    }

    /**
     * Lấy tên chi nhánh hiện tại để hiển thị lên badge trên thanh Header và trong menu Drawer.
     */
    public static String getCurrentBranchName(Context context) {
        if (context == null) return "Chi nhánh";
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BRANCH_NAME, "Chi nhánh");
    }

    /**
     * Lấy họ và tên hiển thị của người dùng hiện tại (fallback về "Người dùng" nếu dữ liệu trống).
     */
    public static String getCurrentUserFullName(Context context) {
        if (context == null) return "Người dùng";
        User user = getCurrentUser(context);
        if (user != null && user.getFullName() != null && !user.getFullName().isEmpty()) {
            return user.getFullName();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_FULL_NAME, "Người dùng");
    }

    public static String getFullName(Context context) {
        return getCurrentUserFullName(context);
    }

    /**
     * Giải tuần tự (Deserialize) chuỗi JSON trong SharedPreferences thành đối tượng model User hoàn chỉnh (bao gồm Roles & Permissions).
     */
    public static User getCurrentUser(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_USER_JSON, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, User.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lưu cache danh sách Chi nhánh (List<Branch>) dưới dạng JSON vào SharedPreferences để mở nhanh popup chọn chi nhánh.
     */
    public static void saveBranches(Context context, List<Branch> branches) {
        if (context == null || branches == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CACHED_BRANCHES, gson.toJson(branches)).apply();
    }

    /**
     * Đọc danh sách Chi nhánh đã cache từ SharedPreferences (trả về danh sách rỗng nếu chưa có cache hoặc lỗi parse).
     */
    public static List<Branch> getCachedBranches(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CACHED_BRANCHES, null);
        if (json == null) return new ArrayList<>();
        try {
            Type listType = new TypeToken<List<Branch>>() {}.getType();
            List<Branch> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Lưu cache danh sách Kho hàng (List<Warehouse>) vào SharedPreferences để dùng lại ở các màn hình Kiểm kho / Nhập hàng.
     */
    public static void saveWarehouses(Context context, List<Warehouse> warehouses) {
        if (context == null || warehouses == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CACHED_WAREHOUSES, gson.toJson(warehouses)).apply();
    }

    /**
     * Đọc danh sách Kho hàng đã cache từ SharedPreferences.
     */
    public static List<Warehouse> getCachedWarehouses(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CACHED_WAREHOUSES, null);
        if (json == null) return new ArrayList<>();
        try {
            Type listType = new TypeToken<List<Warehouse>>() {}.getType();
            List<Warehouse> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Các hàm tiện ích (Static Facade) uỷ quyền kiểm tra phân quyền RBAC trực tiếp xuống đối tượng User hiện tại.
     */
    public static boolean hasPermission(Context context, String moduleOrPermission) {
        User user = getCurrentUser(context);
        return user != null && user.hasPermission(moduleOrPermission);
    }

    public static boolean hasAnyPermission(Context context, String... modulesOrPermissions) {
        User user = getCurrentUser(context);
        return user != null && user.hasAnyPermission(modulesOrPermissions);
    }

    public static boolean isAdmin(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.isAdmin();
    }

    public static boolean isSupplier(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.isSupplier();
    }

    public static boolean canSwitchBranch(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canSwitchBranch();
    }

    public static boolean canAccessDashboard(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessDashboard();
    }

    public static boolean canAccessSales(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessSales();
    }

    public static boolean canAccessProducts(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessProducts();
    }

    public static boolean canAccessInventory(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessInventory();
    }

    public static boolean canAccessCustomers(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessCustomers();
    }

    public static boolean canAccessSuppliers(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessSuppliers();
    }

    public static boolean canAccessQuotations(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessQuotations();
    }

    public static boolean canAccessExpenses(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessExpenses();
    }

    public static boolean canAccessEmployees(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessEmployees();
    }

    public static boolean canAccessAiChat(Context context) {
        User user = getCurrentUser(context);
        return user != null && user.canAccessAiChat();
    }

    /**
     * Xác định màn hình khởi đầu (Landing Activity) ngay sau khi đăng nhập dựa theo thứ tự ưu tiên phân quyền RBAC:
     * Nhà cung cấp -> Công nợ NCC; Quản trị/Báo cáo -> Dashboard (MainActivity); Thu ngân -> POS; Kho -> Tồn kho;
     * nếu tài khoản không có bất kỳ quyền nghiệp vụ nào thì đưa về trang Hồ sơ cá nhân (ProfileActivity).
     */
    public static Class<?> getLandingActivityClass(Context context) {
        User user = getCurrentUser(context);
        if (user == null) {
            return com.example.do_an_di_dong.MainActivity.class;
        }
        // Rẽ nhánh điều hướng màn hình đầu tiên theo vai trò và quyền hạn được cấp
        if (user.isSupplier()) {
            return com.example.do_an_di_dong.ui.dashboard.SupplierDebtActivity.class;
        }
        if (user.canAccessDashboard()) {
            return com.example.do_an_di_dong.MainActivity.class;
        }
        if (user.canAccessSales()) {
            return com.example.do_an_di_dong.ui.pos.PosActivity.class;
        }
        if (user.canAccessProducts()) {
            return com.example.do_an_di_dong.ui.product.ProductsActivity.class;
        }
        if (user.canAccessInventory()) {
            return com.example.do_an_di_dong.ui.inventory.WarehouseInventoryActivity.class;
        }
        if (user.canAccessCustomers()) {
            return com.example.do_an_di_dong.ui.admin.CustomersActivity.class;
        }
        if (user.canAccessSuppliers()) {
            return com.example.do_an_di_dong.ui.dashboard.SupplierDebtActivity.class;
        }
        if (user.canAccessExpenses()) {
            return com.example.do_an_di_dong.ui.admin.ExpensesActivity.class;
        }
        if (user.canAccessEmployees()) {
            return com.example.do_an_di_dong.ui.admin.UsersActivity.class;
        }
        return com.example.do_an_di_dong.ui.profile.ProfileActivity.class;
    }

    /**
     * Lấy tên hiển thị tiếng Việt của vai trò chính (VD: "Quản trị viên", "Đối tác Nhà cung cấp", "Thu ngân") để gắn lên badge.
     */
    public static String getRoleName(Context context) {
        User user = getCurrentUser(context);
        return user != null ? user.getPrimaryRoleName() : "Khách";
    }

    /**
     * Xoá toàn bộ dữ liệu phiên và cache chi nhánh/kho hàng trong SharedPreferences khi người dùng đăng xuất hoặc hết hạn phiên.
     */
    public static void clearSession(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
