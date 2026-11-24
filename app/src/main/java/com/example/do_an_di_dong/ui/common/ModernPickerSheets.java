package com.example.do_an_di_dong.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.Customer;
import com.example.do_an_di_dong.model.Product;
import com.example.do_an_di_dong.model.ProductCategoryDTO;
import com.example.do_an_di_dong.model.ProductPagedResponse;
import com.example.do_an_di_dong.model.SupplierDTO;
import com.example.do_an_di_dong.model.Warehouse;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bộ chọn dữ liệu (Picker) và Menu hành động (Action Sheet) dùng chung toàn hệ thống chuẩn Material 3.
 * Thiết kế theo kiến trúc Zero-Fragment: sử dụng trực tiếp {@link BottomSheetDialog} kết hợp các layout XML:
 * - {@code sheet_search_picker.xml} và {@code item_picker_option.xml}: Tìm kiếm và chọn Sản phẩm, Nhà cung cấp, Khách hàng, Danh mục, Kho hàng.
 * - {@code sheet_supplier_form.xml} và {@code sheet_customer_form.xml}: Form thêm mới / cập nhật nhanh Nhà cung cấp và Khách hàng CRM.
 * - {@code sheet_modern_actions.xml}: Menu danh sách hành động thay thế AlertDialog truyền thống.
 * Kết nối trực tiếp với {@link DataRepository} để gọi API/Room lấy dữ liệu và lưu trữ.
 */
public final class ModernPickerSheets {

    private ModernPickerSheets() {}

    public interface OnProductPickedListener {
        void onProductPicked(Product product);
    }

    public interface OnSupplierPickedListener {
        void onSupplierPicked(SupplierDTO supplier);
    }

    public interface OnCustomerPickedListener {
        void onCustomerPicked(Customer customer);
    }

    public interface OnCategoryPickedListener {
        void onCategoryPicked(ProductCategoryDTO category);
    }

    public interface OnWarehousePickedListener {
        void onWarehousePicked(Warehouse warehouse);
    }

    public static class ActionItem {
        public final String title;
        public final String subtitle;
        public final int badgeResId;
        public final int textColorResId;
        public final Runnable onClick;

        public ActionItem(String title, String subtitle, int badgeResId, int textColorResId, Runnable onClick) {
            this.title = title;
            this.subtitle = subtitle;
            this.badgeResId = badgeResId;
            this.textColorResId = textColorResId;
            this.onClick = onClick;
        }
    }

    private static class OptionRow {
        final String avatarText;
        final String title;
        final String badge;
        final String subtitle;
        final String trailing;
        final Runnable onClick;

        OptionRow(String avatarText, String title, String badge, String subtitle, String trailing, Runnable onClick) {
            this.avatarText = avatarText;
            this.title = title;
            this.badge = badge;
            this.subtitle = subtitle;
            this.trailing = trailing;
            this.onClick = onClick;
        }
    }

    /**
     * Adapter nội bộ hiển thị danh sách kết quả tìm kiếm trong BottomSheet (gắn với layout {@code item_picker_option.xml}).
     */
    private static class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.VH> {
        private List<OptionRow> rows = new ArrayList<>();

        /**
         * Cập nhật danh sách dòng hiển thị và làm mới RecyclerView khi dữ liệu tải xong hoặc khi người dùng gõ từ khóa lọc.
         */
        void setRows(List<OptionRow> list) {
            this.rows = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        /**
         * Khởi tạo ViewHolder từ layout XML {@code item_picker_option.xml} cho từng dòng kết quả.
         */
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker_option, parent, false);
            return new VH(v);
        }

        /**
         * Đổ dữ liệu từng mục (chữ cái đại diện, tiêu đề, nhãn phụ, thông tin cuối dòng) lên View và gán sự kiện click chọn mục.
         */
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            OptionRow row = rows.get(position);
            holder.tvAvatar.setText(row.avatarText != null && !row.avatarText.isEmpty() ? row.avatarText : "•");
            holder.tvTitle.setText(row.title != null ? row.title : "");
            if (row.badge != null && !row.badge.isEmpty()) {
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText(row.badge);
            } else {
                holder.tvBadge.setVisibility(View.GONE);
            }
            holder.tvSubtitle.setText(row.subtitle != null ? row.subtitle : "");
            if (row.trailing != null && !row.trailing.isEmpty()) {
                holder.tvTrailing.setVisibility(View.VISIBLE);
                holder.tvTrailing.setText(row.trailing);
            } else {
                holder.tvTrailing.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> {
                if (row.onClick != null) row.onClick.run();
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvAvatar;
            final TextView tvTitle;
            final TextView tvBadge;
            final TextView tvSubtitle;
            final TextView tvTrailing;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tvOptionAvatar);
                tvTitle = itemView.findViewById(R.id.tvOptionTitle);
                tvBadge = itemView.findViewById(R.id.tvOptionBadge);
                tvSubtitle = itemView.findViewById(R.id.tvOptionSubtitle);
                tvTrailing = itemView.findViewById(R.id.tvOptionTrailing);
            }
        }
    }

    // =========================================================================
    // 1. PRODUCT PICKER SHEET (HỖ TRỢ TÌM KIẾM LIVE + QUÉT CAMERA BARCODE)
    // =========================================================================

    /**
     * Mở BottomSheet chọn sản phẩm (mặc định lọc theo chi nhánh hiện tại của nhân viên đang đăng nhập).
     * Kích hoạt khi bấm nút thêm sản phẩm ở màn hình POS bán hàng hoặc tạo phiếu nhập/kiểm kho.
     * Nhận vào bộ lọc ID nhà cung cấp (nếu có), callback mở camera quét mã vạch và listener trả về {@link Product} được chọn.
     */
    public static void showProductPicker(
            final Context context,
            final Integer supplierIdFilter,
            final Runnable onScanBarcodeClicked,
            final OnProductPickedListener listener
    ) {
        showProductPicker(context, supplierIdFilter, false, onScanBarcodeClicked, listener);
    }

    /**
     * Mở BottomSheet tìm kiếm và chọn sản phẩm kèm tùy chọn lấy tồn kho trên toàn hệ thống chi nhánh.
     * Kích hoạt từ các màn hình nghiệp vụ bán hàng, nhập kho hoặc điều chuyển kho.
     * Nhận vào {@code supplierIdFilter} để lọc theo NCC, {@code includeAllBranches} để quyết định phạm vi chi nhánh,
     * và trả về đối tượng {@link Product} qua {@code listener} khi người dùng chạm chọn.
     */
    public static void showProductPicker(
            final Context context,
            final Integer supplierIdFilter,
            final boolean includeAllBranches,
            final Runnable onScanBarcodeClicked,
            final OnProductPickedListener listener
    ) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        MaterialButton btnScan = view.findViewById(R.id.btnPickerScanBarcode);
        ProgressBar progress = view.findViewById(R.id.progressPicker);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        tvTitle.setText("Chọn sản phẩm hàng hóa");
        tvSubtitle.setText("Chạm để chọn mặt hàng hoặc bấm Quét mã bằng Camera");
        etSearch.setHint("Tìm theo tên sản phẩm, SKU, mã vạch...");

        if (onScanBarcodeClicked != null) {
            btnScan.setVisibility(View.VISIBLE);
            btnScan.setOnClickListener(v -> {
                sheet.dismiss();
                onScanBarcodeClicked.run();
            });
        }

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final DataRepository repo = new DataRepository(context);
        final List<Product> allProducts = new ArrayList<>();

        // Lọc danh sách sản phẩm trực tiếp trên bộ nhớ (in-memory) theo từ khóa tên, mã SKU hoặc ID sản phẩm
        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final Product p : allProducts) {
                String name = p.getName().toLowerCase(Locale.ROOT);
                String sku = p.getSku().toLowerCase(Locale.ROOT);
                if (q.isEmpty() || name.contains(q) || sku.contains(q) || String.valueOf(p.getProductId()).equals(q)) {
                    String initial = !p.getName().isEmpty() ? p.getName().substring(0, 1).toUpperCase(Locale.ROOT) : "P";
                    String badge = !p.getSku().isEmpty() ? p.getSku() : ("SP #" + p.getProductId());
                    String sub = "Tồn kho: " + p.getActualStock() + (p.getUnit() != null && !p.getUnit().isEmpty() ? (" " + p.getUnit()) : "");
                    String price = Formatters.currency(p.getPrice());
                    rows.add(new OptionRow(initial, p.getName(), badge, sub, price, () -> {
                        sheet.dismiss();
                        if (listener != null) listener.onProductPicked(p);
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        // Cơ chế Fallback: Nếu API phân trang lỗi hoặc trả về rỗng, chuyển sang gọi hàm tìm kiếm đơn giản (hỗ trợ cache/Room)
        final Runnable loadAllProductsFallback = () -> repo.searchProductsSimple("", includeAllBranches, new DataRepository.ApiCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> list) {
                progress.setVisibility(View.GONE);
                allProducts.clear();
                if (list != null) allProducts.addAll(list);
                renderFilter.run();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                renderFilter.run();
            }
        });

        progress.setVisibility(View.VISIBLE);
        // Xác định branchId theo phiên đăng nhập hiện tại để chỉ hiển thị tồn kho của chi nhánh đang làm việc (trừ khi bật cờ includeAllBranches)
        Integer branchId = includeAllBranches ? null : UserSessionManager.getCurrentBranchId(context);
        Integer effectiveBranch = (!includeAllBranches && branchId != null && branchId > 0) ? branchId : null;

        // Gọi DataRepository lấy danh sách sản phẩm phân trang (tối đa 250 SP đầu tiên) theo chi nhánh và nhà cung cấp
        repo.getProductsPaged(0, 250, "", null, effectiveBranch, supplierIdFilter, null, new DataRepository.ApiCallback<ProductPagedResponse>() {
            @Override
            public void onSuccess(ProductPagedResponse result) {
                if (result != null && result.getData() != null && !result.getData().isEmpty()) {
                    progress.setVisibility(View.GONE);
                    allProducts.clear();
                    allProducts.addAll(result.getData());
                    renderFilter.run();
                } else if (supplierIdFilter != null) {
                    // Nếu NCC chưa gán sẵn SP, tải toàn bộ danh mục SP để vẫn chọn được hàng nhập
                    repo.getProductsPaged(0, 250, "", null, effectiveBranch, null, null, new DataRepository.ApiCallback<ProductPagedResponse>() {
                        @Override
                        public void onSuccess(ProductPagedResponse allRes) {
                            if (allRes != null && allRes.getData() != null && !allRes.getData().isEmpty()) {
                                progress.setVisibility(View.GONE);
                                allProducts.clear();
                                allProducts.addAll(allRes.getData());
                                renderFilter.run();
                            } else {
                                loadAllProductsFallback.run();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            loadAllProductsFallback.run();
                        }
                    });
                } else {
                    loadAllProductsFallback.run();
                }
            }

            @Override
            public void onError(String message) {
                loadAllProductsFallback.run();
            }
        });

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        sheet.show();
    }

    // =========================================================================
    // 2. SUPPLIER PICKER & FORM SHEET
    // =========================================================================

    /**
     * Mở BottomSheet tìm kiếm và chọn Nhà cung cấp (kèm nút "+ Thêm NCC" ngay trên đầu sheet).
     * Kích hoạt khi người dùng chọn nhà cung cấp trong màn hình tạo đơn nhập kho hoặc form sản phẩm.
     * Trả về đối tượng {@link SupplierDTO} được chọn thông qua {@code listener}.
     */
    public static void showSupplierPicker(final Context context, final OnSupplierPickedListener listener) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        ImageView imgIcon = view.findViewById(R.id.imgPickerIcon);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        MaterialButton btnAddNew = view.findViewById(R.id.btnPickerAddNew);
        ProgressBar progress = view.findViewById(R.id.progressPicker);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        imgIcon.setImageResource(R.drawable.ic_warehouse);
        tvTitle.setText("Chọn Nhà cung cấp");
        tvSubtitle.setText("Chọn đối tác cung ứng hoặc tạo nhanh NCC mới");
        etSearch.setHint("Tìm theo tên NCC, số điện thoại, địa chỉ...");

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final DataRepository repo = new DataRepository(context);
        final List<SupplierDTO> suppliers = new ArrayList<>();

        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final SupplierDTO s : suppliers) {
                String name = s.getName() != null ? s.getName() : ("NCC #" + s.getSupplierId());
                String phone = s.getPhone() != null ? s.getPhone() : "";
                String addr = s.getAddress() != null ? s.getAddress() : "";
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || phone.toLowerCase(Locale.ROOT).contains(q) || addr.toLowerCase(Locale.ROOT).contains(q)) {
                    String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "S";
                    String badge = !phone.isEmpty() ? phone : ("NCC #" + s.getSupplierId());
                    String sub = !addr.isEmpty() ? addr : (s.getEmail() != null ? s.getEmail() : "Đối tác cung ứng");
                    rows.add(new OptionRow(initial, name, badge, sub, "Chọn", () -> {
                        sheet.dismiss();
                        if (listener != null) listener.onSupplierPicked(s);
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        // Gọi API lấy toàn bộ danh sách nhà cung cấp và tự động nạp lại khi tạo mới NCC thành công
        final Runnable loadSuppliers = () -> {
            progress.setVisibility(View.VISIBLE);
            repo.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
                @Override
                public void onSuccess(List<SupplierDTO> result) {
                    progress.setVisibility(View.GONE);
                    suppliers.clear();
                    if (result != null) suppliers.addAll(result);
                    renderFilter.run();
                }

                @Override
                public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            });
        };

        btnAddNew.setVisibility(View.VISIBLE);
        btnAddNew.setText("+ Thêm NCC");
        btnAddNew.setOnClickListener(v -> showSupplierFormSheet(context, null, loadSuppliers));

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        loadSuppliers.run();
        sheet.show();
    }

    /**
     * Mở BottomSheet form thêm mới hoặc chỉnh sửa thông tin Nhà cung cấp (layout {@code sheet_supplier_form.xml}).
     * Nếu {@code existing} khác null và có {@code supplierId > 0}, form chuyển sang chế độ Cập nhật/Xóa; ngược lại là Thêm mới.
     * Khi lưu hoặc xóa thành công trên Server qua {@link DataRepository}, kích hoạt callback {@code onSaved} để màn hình cha làm mới danh sách.
     */
    public static void showSupplierFormSheet(final Context context, final SupplierDTO existing, final Runnable onSaved) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_supplier_form, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvSupplierSheetTitle);
        TextInputLayout tilName = view.findViewById(R.id.tilSupplierName);
        TextInputLayout tilPhone = view.findViewById(R.id.tilSupplierPhone);
        TextInputLayout tilEmail = view.findViewById(R.id.tilSupplierEmail);
        TextInputEditText etName = view.findViewById(R.id.etSupplierName);
        TextInputEditText etPhone = view.findViewById(R.id.etSupplierPhone);
        TextInputEditText etContact = view.findViewById(R.id.etSupplierContact);
        TextInputEditText etEmail = view.findViewById(R.id.etSupplierEmail);
        TextInputEditText etAddress = view.findViewById(R.id.etSupplierAddress);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteSupplier);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelSupplier);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveSupplier);

        com.example.do_an_di_dong.util.FormValidator validator = com.example.do_an_di_dong.util.FormValidator.create()
                .required(tilName, etName, "Tên nhà cung cấp")
                .phone(tilPhone, etPhone, "Số điện thoại nhà cung cấp", true)
                .email(tilEmail, etEmail, "Email giao dịch", false);

        final boolean isEdit = existing != null && existing.getSupplierId() > 0;
        if (isEdit) {
            tvTitle.setText("Cập nhật Nhà cung cấp");
            etName.setText(existing.getName());
            etPhone.setText(existing.getPhone());
            etContact.setText(existing.getContactPerson());
            etEmail.setText(existing.getEmail());
            etAddress.setText(existing.getAddress());

            if (btnDelete != null) {
                btnDelete.setVisibility(View.VISIBLE);
                if (!existing.isCanBeDeleted()) {
                    btnDelete.setAlpha(0.55f);
                }
                btnDelete.setOnClickListener(v -> {
                    // Ràng buộc toàn vẹn dữ liệu: Chặn xóa NCC nếu đang có sản phẩm liên kết (cờ canBeDeleted từ Backend)
                    if (!existing.isCanBeDeleted()) {
                        com.example.do_an_di_dong.util.AppToast.show(
                                context,
                                "Không thể xóa nhà cung cấp này vì đang có sản phẩm liên kết!",
                                com.example.do_an_di_dong.util.AppToast.Type.WARNING
                        );
                        return;
                    }
                    String supName = existing.getName() != null ? existing.getName() : ("NCC #" + existing.getSupplierId());
                    ModernConfirmSheet.confirmDanger(
                            context,
                            "Xác nhận xóa Nhà cung cấp",
                            "Bạn có chắc chắn muốn xóa nhà cung cấp \"" + supName + "\" khỏi hệ thống?",
                            "Xóa NCC",
                            () -> {
                                btnDelete.setEnabled(false);
                                btnSave.setEnabled(false);
                                new DataRepository(context).deleteSupplier(existing.getSupplierId(), new DataRepository.ApiCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        sheet.dismiss();
                                        com.example.do_an_di_dong.util.AppToast.show(
                                                context,
                                                "Đã xóa nhà cung cấp thành công!",
                                                com.example.do_an_di_dong.util.AppToast.Type.SUCCESS
                                        );
                                        if (onSaved != null) onSaved.run();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        btnDelete.setEnabled(true);
                                        btnSave.setEnabled(true);
                                        com.example.do_an_di_dong.util.AppToast.show(
                                                context,
                                                message,
                                                com.example.do_an_di_dong.util.AppToast.Type.ERROR
                                        );
                                    }
                                });
                            }
                    );
                });
            }
        }

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSave.setOnClickListener(v -> {
            if (!validator.validate()) {
                return;
            }
            String name = com.example.do_an_di_dong.util.FormValidator.textOf(etName);
            String phone = com.example.do_an_di_dong.util.FormValidator.normalizePhone(
                    com.example.do_an_di_dong.util.FormValidator.textOf(etPhone));
            String contact = com.example.do_an_di_dong.util.FormValidator.textOf(etContact);
            String email = com.example.do_an_di_dong.util.FormValidator.textOf(etEmail);
            String address = com.example.do_an_di_dong.util.FormValidator.textOf(etAddress);

            btnSave.setEnabled(false);
            btnSave.setText("Đang lưu...");

            SupplierDTO dto = new SupplierDTO(
                    isEdit ? existing.getSupplierId() : 0,
                    name,
                    email,
                    phone,
                    address,
                    contact.isEmpty() ? name : contact
            );

            DataRepository repo = new DataRepository(context);
            DataRepository.ApiCallback<Boolean> cb = new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    sheet.dismiss();
                    com.example.do_an_di_dong.util.AppToast.show(context, isEdit ? "Đã cập nhật nhà cung cấp!" : "Đã thêm nhà cung cấp mới!", Toast.LENGTH_SHORT);
                    if (onSaved != null) onSaved.run();
                }

                @Override
                public void onError(String message) {
                    btnSave.setEnabled(true);
                    btnSave.setText("Lưu NCC");
                    com.example.do_an_di_dong.util.AppToast.show(context, message, Toast.LENGTH_LONG);
                }
            };

            // Rẽ nhánh gọi API cập nhật (PUT) hoặc tạo mới (POST) Nhà cung cấp tùy theo chế độ form
            if (isEdit) {
                repo.updateSupplier(dto, cb);
            } else {
                repo.createSupplier(dto, cb);
            }
        });

        sheet.show();
    }

    /**
     * Mở BottomSheet danh sách Nhà cung cấp chuyên dụng cho thao tác rà soát và xóa NCC.
     * Hiển thị rõ trạng thái "Cho phép xóa" hoặc "Đang có SP liên kết" (Khóa) của từng NCC.
     * Khi xóa thành công một NCC, tự động gọi {@code onDeleted} và tải lại danh sách ngay trong sheet.
     */
    public static void showSupplierDeletePicker(final Context context, final Runnable onDeleted) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        ImageView imgIcon = view.findViewById(R.id.imgPickerIcon);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        ProgressBar progress = view.findViewById(R.id.progressPicker);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        imgIcon.setImageResource(R.drawable.ic_warehouse);
        tvTitle.setText("Xóa Nhà cung cấp");
        tvSubtitle.setText("Chỉ những nhà cung cấp chưa có sản phẩm liên kết mới được phép xóa");
        etSearch.setHint("Tìm nhà cung cấp cần xóa...");

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final DataRepository repo = new DataRepository(context);
        final List<SupplierDTO> suppliers = new ArrayList<>();

        final Runnable[] loadRef = new Runnable[1];
        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final SupplierDTO s : suppliers) {
                final String name = s.getName() != null ? s.getName() : ("NCC #" + s.getSupplierId());
                String phone = s.getPhone() != null ? s.getPhone() : "";
                String addr = s.getAddress() != null ? s.getAddress() : "";
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || phone.toLowerCase(Locale.ROOT).contains(q) || addr.toLowerCase(Locale.ROOT).contains(q)) {
                    String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "S";
                    boolean deletable = s.isCanBeDeleted();
                    String badge = deletable ? "Cho phép xóa" : "Đang có SP liên kết";
                    String sub = (!phone.isEmpty() ? phone : ("NCC #" + s.getSupplierId()))
                            + (!addr.isEmpty() ? (" • " + addr) : "");
                    String trailing = deletable ? "Xóa" : "Khóa";
                    rows.add(new OptionRow(initial, name, badge, sub, trailing, () -> {
                        if (!deletable) {
                            com.example.do_an_di_dong.util.AppToast.show(
                                    context,
                                    "Không thể xóa \"" + name + "\" vì đang có sản phẩm liên kết!",
                                    com.example.do_an_di_dong.util.AppToast.Type.WARNING
                            );
                            return;
                        }
                        ModernConfirmSheet.confirmDanger(
                                context,
                                "Xác nhận xóa Nhà cung cấp",
                                "Bạn có chắc chắn muốn xóa nhà cung cấp \"" + name + "\"? Thao tác này không thể hoàn tác.",
                                "Xóa NCC",
                                () -> {
                                    progress.setVisibility(View.VISIBLE);
                                    repo.deleteSupplier(s.getSupplierId(), new DataRepository.ApiCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean result) {
                                            progress.setVisibility(View.GONE);
                                            com.example.do_an_di_dong.util.AppToast.show(
                                                    context,
                                                    "Đã xóa nhà cung cấp \"" + name + "\"!",
                                                    com.example.do_an_di_dong.util.AppToast.Type.SUCCESS
                                            );
                                            if (onDeleted != null) onDeleted.run();
                                            if (loadRef[0] != null) loadRef[0].run();
                                        }

                                        @Override
                                        public void onError(String message) {
                                            progress.setVisibility(View.GONE);
                                            com.example.do_an_di_dong.util.AppToast.show(
                                                    context,
                                                    message,
                                                    com.example.do_an_di_dong.util.AppToast.Type.ERROR
                                            );
                                        }
                                    });
                                }
                        );
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        loadRef[0] = () -> {
            progress.setVisibility(View.VISIBLE);
            repo.getAllSuppliers(new DataRepository.ApiCallback<List<SupplierDTO>>() {
                @Override
                public void onSuccess(List<SupplierDTO> result) {
                    progress.setVisibility(View.GONE);
                    suppliers.clear();
                    if (result != null) suppliers.addAll(result);
                    renderFilter.run();
                }

                @Override
                public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    com.example.do_an_di_dong.util.AppToast.show(context, message, com.example.do_an_di_dong.util.AppToast.Type.ERROR);
                }
            });
        };

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        loadRef[0].run();
        sheet.show();
    }

    // =========================================================================
    // 3. CUSTOMER PICKER & FORM SHEET
    // =========================================================================

    /**
     * Mở BottomSheet tìm kiếm và chọn Khách hàng CRM (hiển thị hạng thành viên, số điểm tích lũy và nút "+ Thêm KH").
     * Kích hoạt tại màn hình POS khi thu ngân gắn khách hàng vào hóa đơn để tích/tiêu điểm.
     * Trả về đối tượng {@link Customer} được chọn qua {@code listener}.
     */
    public static void showCustomerPicker(final Context context, final OnCustomerPickedListener listener) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        ImageView imgIcon = view.findViewById(R.id.imgPickerIcon);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        MaterialButton btnAddNew = view.findViewById(R.id.btnPickerAddNew);
        ProgressBar progress = view.findViewById(R.id.progressPicker);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        imgIcon.setImageResource(R.drawable.ic_profile);
        tvTitle.setText("Chọn Khách hàng CRM");
        tvSubtitle.setText("Tìm theo tên, SĐT hoặc tạo nhanh hồ sơ khách hàng");
        etSearch.setHint("Nhập tên khách hàng hoặc số điện thoại...");

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final DataRepository repo = new DataRepository(context);
        final List<Customer> customers = new ArrayList<>();

        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final Customer c : customers) {
                String name = c.getCustomerName();
                String phone = c.getPhone() != null ? c.getPhone() : "";
                String email = c.getEmail() != null ? c.getEmail() : "";
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || phone.toLowerCase(Locale.ROOT).contains(q) || email.toLowerCase(Locale.ROOT).contains(q)) {
                    String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "K";
                    String badge = !phone.isEmpty() ? phone : ("KH #" + c.getCustomerId());
                    String sub = (c.getCustomerType() != null ? c.getCustomerType() : "Normal") + (!email.isEmpty() ? (" • " + email) : "");
                    String pts = c.getLoyaltyPoints() + " điểm";
                    rows.add(new OptionRow(initial, name, badge, sub, pts, () -> {
                        sheet.dismiss();
                        if (listener != null) listener.onCustomerPicked(c);
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        // Tải danh sách khách hàng từ API; nếu endpoint getAllCustomers lỗi thì fallback sang searchCustomers("")
        final Runnable loadCustomers = () -> {
            progress.setVisibility(View.VISIBLE);
            repo.getAllCustomers(new DataRepository.ApiCallback<List<Customer>>() {
                @Override
                public void onSuccess(List<Customer> result) {
                    progress.setVisibility(View.GONE);
                    customers.clear();
                    if (result != null) customers.addAll(result);
                    renderFilter.run();
                }

                @Override
                public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    repo.searchCustomers("", new DataRepository.ApiCallback<List<Customer>>() {
                        @Override
                        public void onSuccess(List<Customer> list) {
                            customers.clear();
                            if (list != null) customers.addAll(list);
                            renderFilter.run();
                        }

                        @Override
                        public void onError(String err) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });
        };

        btnAddNew.setVisibility(View.VISIBLE);
        btnAddNew.setText("+ Thêm KH");
        btnAddNew.setOnClickListener(v -> showCustomerFormSheet(context, null, loadCustomers));

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        loadCustomers.run();
        sheet.show();
    }

    /**
     * Mở BottomSheet form thêm mới hoặc cập nhật hồ sơ Khách hàng CRM (layout {@code sheet_customer_form.xml}).
     * Cho phép nhập Họ tên, SĐT, Email và chọn phân hạng khách hàng (Normal / VIP / Wholesale).
     * Khi lưu thành công qua {@link DataRepository}, gọi {@code onSaved} để làm mới danh sách khách hàng.
     */
    public static void showCustomerFormSheet(final Context context, final Customer existing, final Runnable onSaved) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_customer_form, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvCustomerSheetTitle);
        TextInputLayout tilName = view.findViewById(R.id.tilCustomerName);
        TextInputLayout tilPhone = view.findViewById(R.id.tilCustomerPhone);
        TextInputLayout tilEmail = view.findViewById(R.id.tilCustomerEmail);
        TextInputEditText etName = view.findViewById(R.id.etCustomerName);
        TextInputEditText etPhone = view.findViewById(R.id.etCustomerPhone);
        TextInputEditText etEmail = view.findViewById(R.id.etCustomerEmail);
        ChipGroup chipGroupTier = view.findViewById(R.id.chipGroupCustomerTier);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelCustomer);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveCustomer);

        com.example.do_an_di_dong.util.FormValidator validator = com.example.do_an_di_dong.util.FormValidator.create()
                .required(tilName, etName, "Họ và tên khách hàng")
                .phone(tilPhone, etPhone, "Số điện thoại khách hàng", true)
                .email(tilEmail, etEmail, "Email khách hàng", false);

        final boolean isEdit = existing != null && existing.getCustomerId() > 0;
        if (isEdit) {
            tvTitle.setText("Cập nhật Khách hàng CRM");
            etName.setText(existing.getCustomerName());
            etPhone.setText(existing.getPhone());
            etEmail.setText(existing.getEmail());
            String tier = existing.getCustomerType() != null ? existing.getCustomerType() : "Normal";
            if ("VIP".equalsIgnoreCase(tier)) {
                chipGroupTier.check(R.id.chipTierVip);
            } else if ("Wholesale".equalsIgnoreCase(tier) || "B2B".equalsIgnoreCase(tier)) {
                chipGroupTier.check(R.id.chipTierWholesale);
            } else {
                chipGroupTier.check(R.id.chipTierNormal);
            }
        }

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSave.setOnClickListener(v -> {
            if (!validator.validate()) {
                return;
            }
            String name = com.example.do_an_di_dong.util.FormValidator.textOf(etName);
            String phone = com.example.do_an_di_dong.util.FormValidator.normalizePhone(
                    com.example.do_an_di_dong.util.FormValidator.textOf(etPhone));
            String email = com.example.do_an_di_dong.util.FormValidator.textOf(etEmail);

            // Ánh xạ Chip được chọn trên giao diện sang mã phân hạng khách hàng lưu xuống DB
            String tier = "Normal";
            int checkedId = chipGroupTier.getCheckedChipId();
            if (checkedId == R.id.chipTierVip) tier = "VIP";
            else if (checkedId == R.id.chipTierWholesale) tier = "Wholesale";

            Customer customer = new Customer(name, phone, email.isEmpty() ? null : email);
            customer.setCustomerType(tier);
            if (isEdit) {
                // Giữ nguyên ID và số dư điểm tích lũy hiện tại khi cập nhật thông tin cá nhân của khách hàng
                customer.setCustomerId(existing.getCustomerId());
                customer.setPointBalance(existing.getPointBalance());
            }

            btnSave.setEnabled(false);
            btnSave.setText("Đang lưu...");

            DataRepository repo = new DataRepository(context);
            DataRepository.ApiCallback<Boolean> cb = new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    sheet.dismiss();
                    com.example.do_an_di_dong.util.AppToast.show(context, isEdit ? "Đã cập nhật hồ sơ khách hàng!" : "Đã thêm khách hàng mới!", Toast.LENGTH_SHORT);
                    if (onSaved != null) onSaved.run();
                }

                @Override
                public void onError(String message) {
                    btnSave.setEnabled(true);
                    btnSave.setText("Lưu Khách hàng");
                    com.example.do_an_di_dong.util.AppToast.show(context, message, Toast.LENGTH_LONG);
                }
            };

            if (isEdit) {
                repo.updateCustomer(customer, cb);
            } else {
                repo.createCustomer(customer, cb);
            }
        });

        sheet.show();
    }

    // =========================================================================
    // 4. CATEGORY & WAREHOUSE PICKER SHEETS
    // =========================================================================

    /**
     * Mở BottomSheet tìm kiếm và chọn Danh mục ngành hàng sản phẩm.
     * Kích hoạt khi thêm/sửa sản phẩm hoặc lọc danh mục hàng hóa trong kho.
     * Tải danh sách từ {@link DataRepository#getAllCategories} và trả kết quả {@link ProductCategoryDTO} qua {@code listener}.
     */
    public static void showCategoryPicker(final Context context, final OnCategoryPickedListener listener) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        ProgressBar progress = view.findViewById(R.id.progressPicker);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        tvTitle.setText("Chọn Danh mục sản phẩm");
        tvSubtitle.setText("Phân nhóm ngành hàng tiêu chuẩn trong kho");
        etSearch.setHint("Tìm tên danh mục...");

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final List<ProductCategoryDTO> categories = new ArrayList<>();
        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final ProductCategoryDTO c : categories) {
                String name = c.getName() != null ? c.getName() : ("Danh mục #" + c.getCategoryId());
                String desc = c.getDescription() != null && !c.getDescription().isEmpty() ? c.getDescription() : "Ngành hàng siêu thị EMarket";
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || desc.toLowerCase(Locale.ROOT).contains(q)) {
                    String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "C";
                    rows.add(new OptionRow(initial, name, "ID #" + c.getCategoryId(), desc, "Chọn", () -> {
                        sheet.dismiss();
                        if (listener != null) listener.onCategoryPicked(c);
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        progress.setVisibility(View.VISIBLE);
        new DataRepository(context).getAllCategories(new DataRepository.ApiCallback<List<ProductCategoryDTO>>() {
            @Override
            public void onSuccess(List<ProductCategoryDTO> result) {
                progress.setVisibility(View.GONE);
                categories.clear();
                if (result != null) categories.addAll(result);
                renderFilter.run();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        sheet.show();
    }

    /**
     * Mở BottomSheet tìm kiếm và chọn Kho hàng làm việc từ danh sách {@code warehouses} truyền vào.
     * Kích hoạt khi người dùng chọn kho đích/kho nguồn trong các màn hình Nhập kho, Kiểm kê hoặc Điều chuyển.
     * Trả về đối tượng {@link Warehouse} được chọn qua {@code listener}.
     */
    public static void showWarehousePicker(final Context context, final List<Warehouse> warehouses, final OnWarehousePickedListener listener) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_search_picker, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvPickerSubtitle);
        ImageView imgIcon = view.findViewById(R.id.imgPickerIcon);
        TextInputEditText etSearch = view.findViewById(R.id.etPickerSearch);
        TextView tvEmpty = view.findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = view.findViewById(R.id.rvPickerOptions);

        imgIcon.setImageResource(R.drawable.ic_warehouse);
        tvTitle.setText("Chọn Kho hàng làm việc");
        tvSubtitle.setText("Chọn kho để nhập hàng hoặc kiểm kê tồn kho");
        etSearch.setHint("Tìm tên kho hoặc địa điểm...");

        final OptionAdapter adapter = new OptionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);

        final List<Warehouse> list = warehouses != null ? warehouses : new ArrayList<>();
        final Runnable renderFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            List<OptionRow> rows = new ArrayList<>();
            for (final Warehouse w : list) {
                String name = w.getName() != null ? w.getName() : ("Kho #" + w.getWarehouseId());
                String loc = w.getLocation() != null && !w.getLocation().isEmpty() ? w.getLocation() : ("Chi nhánh #" + w.getBranchId());
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || loc.toLowerCase(Locale.ROOT).contains(q)) {
                    String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "K";
                    rows.add(new OptionRow(initial, name, "Kho #" + w.getWarehouseId(), loc, "Chọn", () -> {
                        sheet.dismiss();
                        if (listener != null) listener.onWarehousePicked(w);
                    }));
                }
            }
            adapter.setRows(rows);
            tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        };

        etSearch.addTextChangedListener(com.example.do_an_di_dong.util.DebouncedTextWatcher.search(q -> renderFilter.run()));

        renderFilter.run();
        sheet.show();
    }

    // =========================================================================
    // 5. MODERN ACTION SHEET (THAY THẾ ALERTDIALOG.SETITEMS)
    // =========================================================================

    /**
     * Mở BottomSheet hiển thị menu các hành động tùy chọn (layout {@code sheet_modern_actions.xml}).
     * Dùng để thay thế {@code AlertDialog.Builder.setItems()} khi bấm vào một dòng dữ liệu cần nhiều thao tác (VD: Xem, Sửa, Xóa, Đổi trạng thái).
     * Nhận vào danh sách {@link ActionItem}; khi người dùng bấm vào một thẻ hành động, sheet tự đóng và chạy callback {@code action.onClick}.
     */
    public static void showActionSheet(
            final Context context,
            final String title,
            final String subtitle,
            final List<ActionItem> actions
    ) {
        final BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_modern_actions, null);
        sheet.setContentView(view);
        BottomSheetHelper.prepare(sheet, view, true);

        TextView tvTitle = view.findViewById(R.id.tvActionSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvActionSheetSubtitle);
        LinearLayout container = view.findViewById(R.id.containerActionItems);

        tvTitle.setText(title != null ? title : "Thao tác");
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setVisibility(View.VISIBLE);
            tvSubtitle.setText(subtitle);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }

        // Khởi tạo động từng thẻ MaterialCardView cho mỗi ActionItem và gắn vào container
        float density = context.getResources().getDisplayMetrics().density;
        for (final ActionItem action : actions) {
            MaterialCardView card = new MaterialCardView(context);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = (int) (10 * density);
            card.setLayoutParams(cardLp);
            card.setRadius(14 * density);
            card.setCardElevation(0f);
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_container_lowest));
            card.setStrokeWidth((int) (1 * density));
            card.setStrokeColor(ContextCompat.getColor(context, R.color.outline_variant));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout inner = new LinearLayout(context);
            inner.setOrientation(LinearLayout.VERTICAL);
            int padH = (int) (16 * density);
            int padV = (int) (13 * density);
            inner.setPadding(padH, padV, padH, padV);

            TextView tvActionTitle = new TextView(context);
            tvActionTitle.setText(action.title);
            tvActionTitle.setTextSize(15f);
            tvActionTitle.setTypeface(tvActionTitle.getTypeface(), android.graphics.Typeface.BOLD);
            int colorRes = action.textColorResId != 0 ? action.textColorResId : R.color.text_primary;
            tvActionTitle.setTextColor(ContextCompat.getColor(context, colorRes));
            inner.addView(tvActionTitle);

            if (action.subtitle != null && !action.subtitle.isEmpty()) {
                TextView tvActionSub = new TextView(context);
                tvActionSub.setText(action.subtitle);
                tvActionSub.setTextSize(12f);
                tvActionSub.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                subLp.topMargin = (int) (2 * density);
                tvActionSub.setLayoutParams(subLp);
                inner.addView(tvActionSub);
            }

            card.addView(inner);
            card.setOnClickListener(v -> {
                sheet.dismiss();
                if (action.onClick != null) action.onClick.run();
            });
            container.addView(card);
        }

        sheet.show();
    }
}

