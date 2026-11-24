package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Model thực thể Người dùng (User DTO / Entity) ánh xạ trực tiếp từ JSON response của Backend API (qua Gson @SerializedName).
 * Đóng vai trò hạt nhân xử lý logic phân quyền RBAC (Role-Based Access Control): nhận diện Admin, Nhà cung cấp (Supplier),
 * kiểm tra trạng thái khoá tài khoản và quyết định quyền truy cập vào từng phân hệ nghiệp vụ trên AppNavigationShell / UsersActivity.
 */
public class User {
    @SerializedName(value = "userId", alternate = {"UserId", "id", "Id"})
    private int userId;

    @SerializedName(value = "username", alternate = {"Username", "userName", "UserName"})
    private String username;

    @SerializedName(value = "fullName", alternate = {"FullName", "name", "Name"})
    private String fullName;

    @SerializedName(value = "email", alternate = {"Email", "mail", "Mail", "userEmail", "UserEmail"})
    private String email;

    @SerializedName(value = "phone", alternate = {"Phone"})
    private String phone;

    @SerializedName(value = "image", alternate = {"Image", "userImg", "UserImg", "avatar", "Avatar"})
    private String image;

    @SerializedName(value = "status", alternate = {"Status"})
    private String status;

    @SerializedName(value = "branchId", alternate = {"BranchId"})
    private Integer branchId;

    @SerializedName(value = "supplierId", alternate = {"SupplierId"})
    private Integer supplierId;

    @SerializedName(value = "roleId", alternate = {"RoleId"})
    private int roleId;

    @SerializedName(value = "roleName", alternate = {"RoleName"})
    private String roleName;

    @SerializedName(value = "roles", alternate = {"Roles"})
    private List<Role> roles;

    @SerializedName(value = "permissions", alternate = {"Permissions"})
    private List<Permission> permissions;

    @SerializedName(value = "isAdmin", alternate = {"IsAdmin"})
    private boolean isAdmin;

    @SerializedName(value = "isSupplier", alternate = {"IsSupplier"})
    private boolean isSupplier;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return (fullName != null && !fullName.isEmpty()) ? fullName : username; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public List<Role> getRoles() { return roles; }
    public void setRoles(List<Role> roles) { this.roles = roles; }
    public List<Permission> getPermissions() { return permissions; }
    public void setPermissions(List<Permission> permissions) { this.permissions = permissions; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    /**
     * Kiểm tra tài khoản hiện tại có phải là Đối tác Nhà cung cấp (Supplier Portal) hay không.
     * Kết hợp kiểm tra cờ isSupplier, roleId == 6, supplierId > 0 hoặc tên Role chứa từ khoá nhà cung cấp/đối tác.
     */
    public boolean isSupplier() {
        if (isSupplier || roleId == 6 || (supplierId != null && supplierId > 0)) {
            return true;
        }
        if (roles != null) {
            for (Role r : roles) {
                if (r == null) continue;
                if (r.getRoleId() == 6) return true;
                String rn = r.getName() != null ? r.getName().toLowerCase() : "";
                if (rn.contains("nhà cung cấp") || rn.contains("nha cung cap")
                        || rn.contains("cung cấp") || rn.contains("cung cap")
                        || rn.contains("ncc") || rn.contains("supplier")
                        || rn.contains("đối tác") || rn.contains("doi tac")
                        || rn.contains("partner")) {
                    return true;
                }
            }
        }
        return false;
    }
    public void setSupplier(boolean supplier) { isSupplier = supplier; }

    /**
     * Kiểm tra tài khoản nhân viên có đang bị khoá/vô hiệu hoá hay không (dựa trên chuỗi status từ Backend).
     */
    public boolean isLocked() {
        if (status == null) return false;
        String s = status.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("locked") || s.equals("inactive") || s.equals("false")
                || s.equals("0") || s.equals("bị khóa");
    }

    /**
     * Nhận diện tên Role có phải là nhóm Quản trị viên hệ thống (System Admin / Quản trị) hay không.
     */
    public static boolean isAdminRoleName(String name) {
        if (name == null) return false;
        String rn = name.trim().toLowerCase(java.util.Locale.ROOT);
        return rn.equals("system admin") || rn.equals("admin")
                || rn.contains("system admin") || rn.contains("quản trị")
                || rn.contains("quan tri");
    }

    /**
     * Kiểm tra một đối tượng Role có phải là vai trò Nhà cung cấp (roleId == 6 hoặc tên khớp mẫu) hay không.
     */
    public static boolean isSupplierRole(Role role) {
        if (role == null) return false;
        if (role.getRoleId() == 6) return true;
        return isSupplierRoleName(role.getName());
    }

    /**
     * Nhận diện tên Role có thuộc nhóm Nhà cung cấp / Đối tác (hỗ trợ cả tiếng Việt có dấu, không dấu và tiếng Anh).
     */
    public static boolean isSupplierRoleName(String name) {
        if (name == null) return false;
        String rn = name.trim().toLowerCase(java.util.Locale.ROOT);
        return rn.contains("nhà cung cấp") || rn.contains("nha cung cap")
                || rn.contains("cung cấp") || rn.contains("cung cap")
                || rn.contains("ncc") || rn.contains("supplier")
                || rn.contains("đối tác") || rn.contains("doi tac")
                || rn.contains("partner");
    }

    /**
     * Xác định roleId chính thức của User khi sửa/phân quyền nhân viên.
     * Nếu trường roleId gốc bằng 0, tự động trích xuất roleId từ danh sách roles (ưu tiên role Admin nếu user là Admin).
     */
    public int getResolvedRoleId() {
        if (roleId > 0) return roleId;
        if (roles != null && !roles.isEmpty()) {
            if (isAdmin) {
                for (Role r : roles) {
                    if (r != null && isAdminRoleName(r.getName())) {
                        return r.getRoleId();
                    }
                }
            }
            for (Role r : roles) {
                if (r != null && r.getRoleId() > 0) {
                    return r.getRoleId();
                }
            }
        }
        return 0;
    }

    /**
     * Trả về tên vai trò chính để hiển thị trên giao diện (Drawer, Profile, danh sách Nhân sự).
     * Ưu tiên thứ tự: Nhà cung cấp -> Quản trị viên -> Role khớp roleId -> Role đầu tiên trong mảng -> mặc định "Nhân viên".
     */
    public String getPrimaryRoleName() {
        if (isSupplier()) return "Đối tác Nhà cung cấp";
        if (isAdmin) {
            if (roles != null) {
                for (Role r : roles) {
                    if (r != null && isAdminRoleName(r.getName()) && !r.getName().trim().isEmpty()) {
                        return r.getName().trim();
                    }
                }
            }
            return "Quản trị viên (System Admin)";
        }
        if (roleId > 0 && roles != null) {
            for (Role r : roles) {
                if (r != null && r.getRoleId() == roleId && r.getName() != null && !r.getName().trim().isEmpty()) {
                    return r.getName().trim();
                }
            }
        }
        if (roles != null) {
            for (Role r : roles) {
                if (r != null && r.getName() != null && !r.getName().trim().isEmpty()) {
                    return r.getName().trim();
                }
            }
        }
        if (roleName != null && !roleName.trim().isEmpty()) {
            return roleName.trim();
        }
        return "Nhân viên";
    }

    /**
     * Hàm lõi kiểm tra phân quyền RBAC theo tên Module hoặc tên Permission (không phân biệt hoa thường).
     * - Nhà cung cấp (isSupplier): luôn trả về false đối với các module nội bộ cửa hàng.
     * - Quản trị viên (isAdmin): luôn có toàn quyền (true).
     * - Nhân viên thường: đối chiếu từ khoá với trường module/name trong danh sách permissions và tên roles.
     */
    public boolean hasPermission(String moduleOrPermission) {
        if (isSupplier()) return false;
        if (isAdmin) return true;
        if (moduleOrPermission == null || moduleOrPermission.trim().isEmpty()) return true;

        String target = moduleOrPermission.trim().toLowerCase();
        // Quét trong danh sách quyền chi tiết (Permissions) được Backend trả về trong JWT/Login response
        if (permissions != null) {
            for (Permission p : permissions) {
                if (p == null) continue;
                if (p.getModule() != null && p.getModule().toLowerCase().contains(target)) return true;
                if (p.getName() != null && p.getName().toLowerCase().contains(target)) return true;
            }
        }
        // Fallback kiểm tra theo tên vai trò (Roles) nếu Backend gán quyền theo nhóm Role
        if (roles != null) {
            for (Role r : roles) {
                if (r != null && r.getName() != null && r.getName().toLowerCase().contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Kiểm tra User có ít nhất 1 quyền trong danh sách các từ khoá Module/Permission truyền vào hay không (toán tử OR).
     */
    public boolean hasAnyPermission(String... modulesOrPermissions) {
        if (isSupplier()) return false;
        if (isAdmin) return true;
        if (modulesOrPermissions == null || modulesOrPermissions.length == 0) return true;
        for (String perm : modulesOrPermissions) {
            if (hasPermission(perm)) return true;
        }
        return false;
    }

    /**
     * Chỉ Quản trị viên hệ thống (không phải Nhà cung cấp) mới được phép chuyển đổi chi nhánh làm việc.
     */
    public boolean canSwitchBranch() {
        return isAdmin && !isSupplier();
    }

    /**
     * Các hàm kiểm tra quyền truy cập từng phân hệ chức năng cụ thể (Dashboard, POS, Sản phẩm, Kho, CRM, NCC, Báo giá, Sổ quỹ, Nhân sự, AI).
     */
    public boolean canAccessDashboard() {
        return !isSupplier() && (isAdmin || hasAnyPermission("DashboardModule", "ReportModule", "dashboard", "report", "tổng quan"));
    }

    public boolean canAccessSales() {
        return !isSupplier() && (isAdmin || hasAnyPermission("SalesModule", "sales", "pos", "order", "bán hàng", "thu ngân"));
    }

    public boolean canAccessProducts() {
        return !isSupplier() && (isAdmin || hasAnyPermission("ProductModule", "product", "hàng hóa", "sản phẩm"));
    }

    public boolean canAccessInventory() {
        return !isSupplier() && (isAdmin || hasAnyPermission("InventoryModule", "inventory", "warehouse", "purchase", "stock", "kho", "nhập hàng"));
    }

    public boolean canAccessCustomers() {
        return !isSupplier() && (isAdmin || hasAnyPermission("CustomerModule", "customer", "sales", "khách hàng", "crm", "bán hàng"));
    }

    public boolean canAccessSuppliers() {
        return isSupplier() || isAdmin || hasAnyPermission("ProductModule", "InventoryModule", "SupplierModule", "supplier", "nhà cung cấp", "ncc", "kho");
    }

    public boolean canAccessQuotations() {
        return !isSupplier() && (isAdmin || hasAnyPermission("QuotationModule", "quotation", "sales", "báo giá", "bán hàng"));
    }

    public boolean canAccessExpenses() {
        return !isSupplier() && (isAdmin || hasAnyPermission("ExpenseModule", "CashbookModule", "AccountingModule", "expense", "accounting", "cash", "kế toán", "sổ quỹ", "chi phí"));
    }

    public boolean canAccessEmployees() {
        return !isSupplier() && (isAdmin || hasAnyPermission("UserModule", "EmployeeModule", "HRModule", "user", "employee", "hrm", "nhân sự"));
    }

    public boolean canAccessAiChat() {
        return !isSupplier();
    }
}
