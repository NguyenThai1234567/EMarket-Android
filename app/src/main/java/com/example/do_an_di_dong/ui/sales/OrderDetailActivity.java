package com.example.do_an_di_dong.ui.sales;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityOrderDetailBinding;
import com.example.do_an_di_dong.databinding.DialogInvoiceReceiptBinding;
import com.example.do_an_di_dong.model.OrderDetail;
import com.example.do_an_di_dong.model.Payment;
import com.example.do_an_di_dong.ui.common.AppToast;
import com.example.do_an_di_dong.util.Formatters;
import com.example.do_an_di_dong.util.MoneyTextWatcher;
import com.example.do_an_di_dong.util.UserSessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình Chi tiết Đơn hàng (OrderDetailActivity).
 * - Vai trò: Hiển thị thông tin chung của đơn hàng, danh sách mặt hàng đã mua, lịch sử thanh toán,
 *   cho phép chuyển đổi trạng thái đơn hàng (Đang xử lý / Hoàn thành / Hủy đơn kèm lý do)
 *   và xem/chia sẻ hóa đơn in nhiệt (Thermal Receipt) có gắn mã vạch Code-39.
 * - Liên kết: Layout `activity_order_detail.xml` (ActivityOrderDetailBinding), Dialog `dialog_invoice_receipt.xml`,
 *   ViewModel `OrderDetailViewModel`, Adapter `OrderDetailAdapter` & `OrderPaymentAdapter`, và `DataRepository`.
 */
public class OrderDetailActivity extends AppCompatActivity {
    private ActivityOrderDetailBinding binding;
    private OrderDetailViewModel viewModel;
    private OrderDetailAdapter detailsAdapter;
    private OrderPaymentAdapter paymentsAdapter;

    private int orderId;
    private String orderCode;
    private String customerName;
    private String orderDate;
    private String currentStatus;
    private double totalAmount;
    private double discountAmount;
    private String paymentMethod;

    /**
     * Khởi tạo giao diện chi tiết đơn hàng, đọc dữ liệu tổng quan truyền qua Intent từ `OrdersHistoryActivity`,
     * thiết lập 2 RecyclerView (chi tiết mặt hàng & lịch sử thanh toán) và gọi ViewModel tải dữ liệu từ API.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(OrderDetailViewModel.class);

        // Giải nén thông tin đơn hàng từ Intent gửi sang
        orderId = getIntent().getIntExtra("order_id", 0);
        orderCode = getIntent().getStringExtra("order_code");
        customerName = getIntent().getStringExtra("customer_name");
        orderDate = getIntent().getStringExtra("order_date");
        currentStatus = getIntent().getStringExtra("order_status");
        totalAmount = getIntent().getDoubleExtra("total_amount", 0);
        discountAmount = getIntent().getDoubleExtra("discount_amount", 0);
        paymentMethod = getIntent().getStringExtra("payment_method");

        if (orderCode == null || orderCode.isEmpty()) {
            orderCode = "#ORD-" + orderId;
        }

        // Header and basic info setup
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvHeaderTitle.setText(orderCode);
        binding.tvOrderCode.setText(orderCode);
        binding.tvCustomerName.setText(customerName != null && !customerName.isEmpty() ? customerName : "Khách vãng lai");
        binding.tvOrderDate.setText(Formatters.dateTime(orderDate));
        binding.tvTotalAmount.setText(MoneyTextWatcher.formatCurrency(totalAmount));

        updateStatusUI(currentStatus);

        binding.btnChangeStatus.setOnClickListener(v -> showChangeStatusDialog(orderId));
        binding.btnViewReceipt.setOnClickListener(v -> showThermalReceipt());

        // Setup Details RecyclerView
        detailsAdapter = new OrderDetailAdapter();
        binding.rvOrderDetails.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOrderDetails.setAdapter(detailsAdapter);

        // Setup Payments RecyclerView
        paymentsAdapter = new OrderPaymentAdapter();
        binding.rvPayments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPayments.setAdapter(paymentsAdapter);

        // Observers
        viewModel.getLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                binding.layoutContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.getDetails().observe(this, details -> {
            if (details != null) {
                detailsAdapter.setDetails(details);
            }
        });

        viewModel.getPayments().observe(this, payments -> {
            if (payments != null && !payments.isEmpty()) {
                binding.tvEmptyPayments.setVisibility(View.GONE);
                binding.rvPayments.setVisibility(View.VISIBLE);
                paymentsAdapter.setPayments(payments);
            } else {
                binding.rvPayments.setVisibility(View.GONE);
                binding.tvEmptyPayments.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                AppToast.show(this, msg, AppToast.Type.ERROR);
            }
        });

        if (orderId > 0) {
            viewModel.loadOrderInfo(orderId);
        }
    }

    /**
     * Cập nhật badge trạng thái đơn hàng trên giao diện.
     * Nếu đơn đã ở trạng thái cuối (Hoàn thành hoặc Đã hủy) thì ẩn nút "Đổi trạng thái" để khóa thao tác.
     */
    private void updateStatusUI(String orderStatus) {
        currentStatus = orderStatus != null ? orderStatus : "Pending";
        if ("Delivered".equalsIgnoreCase(currentStatus)
                || "Completed".equalsIgnoreCase(currentStatus)
                || "Paid".equalsIgnoreCase(currentStatus)) {
            binding.tvOrderStatus.setText("Hoàn thành");
            binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_success);
            binding.tvOrderStatus.setTextColor(getColor(R.color.on_primary_container));
            binding.btnChangeStatus.setVisibility(View.GONE); // Final state locked
        } else if ("Cancelled".equalsIgnoreCase(currentStatus)) {
            binding.tvOrderStatus.setText("Đã hủy");
            binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_error);
            binding.tvOrderStatus.setTextColor(getColor(R.color.on_error_container));
            binding.btnChangeStatus.setVisibility(View.GONE); // Final state locked
        } else if ("Shipping".equalsIgnoreCase(currentStatus)
                || "Shipped".equalsIgnoreCase(currentStatus)
                || "Processing".equalsIgnoreCase(currentStatus)) {
            binding.tvOrderStatus.setText("Đang xử lý");
            binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_warning);
            binding.tvOrderStatus.setTextColor(getColor(R.color.on_secondary_container));
            binding.btnChangeStatus.setVisibility(View.VISIBLE);
        } else {
            binding.tvOrderStatus.setText("Chờ duyệt");
            binding.tvOrderStatus.setBackgroundResource(R.drawable.bg_badge_pending);
            binding.tvOrderStatus.setTextColor(getColor(R.color.on_secondary_container));
            binding.btnChangeStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hiển thị BottomSheet chọn trạng thái tiếp theo hợp lệ cho đơn hàng khi bấm nút `btnChangeStatus`.
     * Chặn thao tác nếu đơn hàng đã kết thúc (Completed/Cancelled).
     */
    private void showChangeStatusDialog(int orderId) {
        if ("Completed".equalsIgnoreCase(currentStatus)
                || "Delivered".equalsIgnoreCase(currentStatus)
                || "Paid".equalsIgnoreCase(currentStatus)
                || "Cancelled".equalsIgnoreCase(currentStatus)) {
            AppToast.show(this, "Đơn hàng đã ở trạng thái kết thúc, không thể thay đổi!", AppToast.Type.WARNING);
            return;
        }

        List<com.example.do_an_di_dong.ui.common.ModernPickerSheets.ActionItem> actions = new ArrayList<>();
        if (!"Processing".equalsIgnoreCase(currentStatus) && !"Shipping".equalsIgnoreCase(currentStatus)) {
            actions.add(new com.example.do_an_di_dong.ui.common.ModernPickerSheets.ActionItem(
                    "⏳ Đang xử lý / Đang giao hàng (Processing)",
                    "Cập nhật trạng thái đơn hàng đang được đóng gói và giao",
                    R.drawable.bg_badge_warning,
                    R.color.warning,
                    () -> executeUpdateStatus(orderId, "Processing")
            ));
        }
        actions.add(new com.example.do_an_di_dong.ui.common.ModernPickerSheets.ActionItem(
                "✓ Hoàn thành đơn hàng (Completed)",
                "Xác nhận đơn hàng đã hoàn tất giao hàng và thanh toán đủ",
                R.drawable.bg_badge_success,
                R.color.success,
                () -> executeUpdateStatus(orderId, "Completed")
        ));
        actions.add(new com.example.do_an_di_dong.ui.common.ModernPickerSheets.ActionItem(
                "✕ Hủy đơn hàng (Cancelled)",
                "Hủy đơn hàng và hoàn trả tồn kho (nếu có)",
                R.drawable.bg_badge_error,
                R.color.error,
                () -> promptCancellationReason(orderId)
        ));

        com.example.do_an_di_dong.ui.common.ModernPickerSheets.showActionSheet(
                this,
                "Cập nhật trạng thái " + orderCode,
                "Chọn trạng thái tiếp theo hợp lệ cho đơn hàng",
                actions
        );
    }

    /**
     * Hiển thị BottomSheet bắt buộc nhập lý do hủy đơn (tối thiểu 3 ký tự) trước khi gọi API hủy đơn hàng.
     */
    private void promptCancellationReason(final int orderId) {
        final com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        com.google.android.material.textfield.TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        com.google.android.material.textfield.TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        com.google.android.material.button.MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Xác nhận hủy đơn hàng " + orderCode);
        tvSubtitle.setText("Vui lòng nhập lý do hủy đơn bắt buộc để lưu nhật ký kiểm toán");
        tilField1.setHint("Lý do hủy đơn hàng *");
        btnSubmit.setText("Xác nhận hủy");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .minLength(tilField1, etField1, "Lý do hủy đơn hàng", 3, true)
                    .validate();
            if (!valid) {
                return;
            }
            sheet.dismiss();
            executeUpdateStatus(orderId, "Cancelled");
        });

        sheet.show();
    }

    /**
     * Gọi `DataRepository.updateOrderStatus` để cập nhật trạng thái đơn hàng lên Server và cập nhật lại UI khi thành công.
     */
    private void executeUpdateStatus(int orderId, String selectedStatus) {
        new DataRepository(this).updateOrderStatus(orderId, selectedStatus, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                AppToast.show(OrderDetailActivity.this, "Đã cập nhật trạng thái đơn hàng!", AppToast.Type.SUCCESS);
                updateStatusUI(selectedStatus);
            }

            @Override
            public void onError(String message) {
                AppToast.show(OrderDetailActivity.this, "Lỗi cập nhật: " + message, AppToast.Type.ERROR);
            }
        });
    }

    /**
     * Dựng và hiển thị Dialog Hóa đơn in nhiệt (Thermal Receipt) khi bấm nút Xem hóa đơn (`btnViewReceipt`).
     * Tự động tính lại tạm tính, chiết khấu, render danh sách món, sinh mã vạch Code-39 và hỗ trợ chia sẻ văn bản hóa đơn.
     */
    private void showThermalReceipt() {
        List<OrderDetail> details = viewModel.getDetails().getValue();
        if (details == null) details = new ArrayList<>();

        DialogInvoiceReceiptBinding receiptBinding = DialogInvoiceReceiptBinding.inflate(LayoutInflater.from(this));
        AlertDialog receiptDialog = new AlertDialog.Builder(this)
                .setView(receiptBinding.getRoot())
                .create();

        String branchName = UserSessionManager.getCurrentBranchName(this);
        receiptBinding.tvReceiptBranch.setText("Chi nhánh: " + (branchName != null && !branchName.isEmpty() ? branchName : "Chi nhánh Trung Tâm"));
        receiptBinding.tvReceiptOrderCode.setText("Mã đơn: " + orderCode);
        receiptBinding.tvReceiptDateTime.setText(Formatters.dateTime(orderDate));
        receiptBinding.tvReceiptCustomer.setText("KH: " + (customerName != null && !customerName.isEmpty() ? customerName : "Khách vãng lai"));
        String cashier = UserSessionManager.getFullName(this);
        receiptBinding.tvReceiptCashier.setText("TN: " + (cashier != null && !cashier.isEmpty() ? cashier : "Thu Ngân POS"));

        // Dynamic item rows
        receiptBinding.layoutReceiptItemsList.removeAllViews();
        double subtotal = 0;
        for (OrderDetail item : details) {
            subtotal += item.getSubtotal();
            View row = LayoutInflater.from(this).inflate(R.layout.item_receipt_row, receiptBinding.layoutReceiptItemsList, false);
            TextView tvName = row.findViewById(R.id.tvReceiptItemName);
            TextView tvQty = row.findViewById(R.id.tvReceiptItemQty);
            TextView tvPrice = row.findViewById(R.id.tvReceiptItemPrice);

            tvName.setText(item.getProductName() != null ? item.getProductName() : "Sản phẩm #" + item.getProductId());
            tvQty.setText(String.valueOf(item.getQuantity()));
            tvPrice.setText(MoneyTextWatcher.formatCurrency(item.getSubtotal()));

            receiptBinding.layoutReceiptItemsList.addView(row);
        }

        // Công thức tính tiền tạm tính (effectiveSubtotal) và tiền giảm giá (effectiveDiscount) hiển thị trên bill
        double effectiveSubtotal = subtotal > 0 ? subtotal : (totalAmount + discountAmount);
        double effectiveDiscount = discountAmount > 0 ? discountAmount : Math.max(0, effectiveSubtotal - totalAmount);

        receiptBinding.tvReceiptSubtotal.setText(MoneyTextWatcher.formatCurrency(effectiveSubtotal));
        receiptBinding.tvReceiptTotal.setText(MoneyTextWatcher.formatCurrency(totalAmount));

        if (effectiveDiscount > 0) {
            receiptBinding.rowReceiptDiscount.setVisibility(View.VISIBLE);
            receiptBinding.tvReceiptDiscount.setText("- " + MoneyTextWatcher.formatCurrency(effectiveDiscount));
        } else {
            receiptBinding.rowReceiptDiscount.setVisibility(View.GONE);
        }

        String pmDisplay = "Tiền mặt";
        List<Payment> payments = viewModel.getPayments().getValue();
        if (payments != null && !payments.isEmpty() && payments.get(0).getPaymentMethod() != null) {
            String pm = payments.get(0).getPaymentMethod();
            if ("Transfer".equalsIgnoreCase(pm) || "BankTransfer".equalsIgnoreCase(pm)) pmDisplay = "Chuyển khoản";
            else if ("Card".equalsIgnoreCase(pm)) pmDisplay = "Thẻ POS";
        } else if ("Transfer".equalsIgnoreCase(paymentMethod) || "BankTransfer".equalsIgnoreCase(paymentMethod)) {
            pmDisplay = "Chuyển khoản";
        }

        receiptBinding.tvReceiptPaymentMethodLabel.setText("Thanh toán (" + pmDisplay + "):");
        receiptBinding.tvReceiptCustomerCash.setText(MoneyTextWatcher.formatCurrency(totalAmount));
        receiptBinding.tvReceiptChange.setText("0 ₫");

        // Tạo payload định danh đơn hàng và vẽ Bitmap mã vạch Code-39 để máy quét POS có thể quét lại
        String qrPayload = "EMARKET-ORD:" + orderId;
        receiptBinding.tvReceiptQrCodeText.setText(qrPayload);
        Bitmap barcodeBmp = generateCode39Barcode("ORD-" + orderId, 440, 110);
        if (barcodeBmp != null) {
            receiptBinding.ivReceiptBarcode.setImageBitmap(barcodeBmp);
        }

        receiptBinding.btnFinishReceipt.setText("Đóng");
        receiptBinding.btnFinishReceipt.setOnClickListener(v -> receiptDialog.dismiss());

        // Đóng gói Intent ACTION_SEND để chia sẻ nhanh nội dung hóa đơn qua các ứng dụng nhắn tin/email
        receiptBinding.btnShareReceipt.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Hóa đơn EMarket " + orderCode);
            share.putExtra(Intent.EXTRA_TEXT,
                    "HÓA ĐƠN SIÊU THỊ EMARKET\n"
                    + "Mã đơn: " + orderCode + " (" + qrPayload + ")\n"
                    + "Khách hàng: " + (customerName != null ? customerName : "Khách vãng lai") + "\n"
                    + "Tổng thanh toán: " + MoneyTextWatcher.formatCurrency(totalAmount));
            startActivity(Intent.createChooser(share, "Chia sẻ hóa đơn"));
        });

        receiptDialog.show();
    }

    /**
     * Tạo mã vạch chuẩn Code-39 có thể quét trực tiếp bằng ML Kit BarcodeScanner (offline 100%).
     * Nhận vào chuỗi dữ liệu (VD: "ORD-123") và kích thước ảnh, trả về đối tượng Bitmap đen trắng.
     */
    public static Bitmap generateCode39Barcode(String data, int width, int height) {
        if (data == null || data.isEmpty()) return null;
        Map<Character, String> patterns = new HashMap<>();
        patterns.put('0', "nnnwwnwnn"); patterns.put('1', "wnnwnnnnw");
        patterns.put('2', "nnwwnnnnw"); patterns.put('3', "wnwwnnnnn");
        patterns.put('4', "nnnwwnnnw"); patterns.put('5', "wnnwwnnnn");
        patterns.put('6', "nnwwwnnnn"); patterns.put('7', "nnnwnnwnw");
        patterns.put('8', "wnnwnnwnn"); patterns.put('9', "nnwwnnwnn");
        patterns.put('A', "wnnnnwnnw"); patterns.put('B', "nnwnnwnnw");
        patterns.put('C', "wnwnnwnnn"); patterns.put('D', "nnnnwwnnw");
        patterns.put('E', "wnnnwwnnn"); patterns.put('F', "nnwnwwnnn");
        patterns.put('G', "nnnnnwwnw"); patterns.put('H', "wnnnnwwnn");
        patterns.put('I', "nnwnnwwnn"); patterns.put('J', "nnnnwwwnn");
        patterns.put('K', "wnnnnnnww"); patterns.put('L', "nnwnnnnww");
        patterns.put('M', "wnwnnnnwn"); patterns.put('N', "nnnnwnnww");
        patterns.put('O', "wnnnwnnwn"); patterns.put('P', "nnwnwnnwn");
        patterns.put('Q', "nnnnnnwww"); patterns.put('R', "wnnnnnwwn");
        patterns.put('S', "nnwnnnwwn"); patterns.put('T', "nnnnwnwwn");
        patterns.put('U', "wwnnnnnnw"); patterns.put('V', "nwwnnnnnw");
        patterns.put('W', "wwwnnnnnn"); patterns.put('X', "nwnnwnnnw");
        patterns.put('Y', "wwnnwnnnn"); patterns.put('Z', "nwwnwnnnn");
        patterns.put('-', "nwnnnnwnw"); patterns.put('.', "wwnnnnwnn");
        patterns.put(' ', "nwwnnnwnn"); patterns.put('*', "nwnnwnwnn");

        // Bọc ký tự '*' ở đầu và cuối theo quy chuẩn bắt buộc của mã vạch Code-39
        String clean = "*" + data.toUpperCase(Locale.ROOT) + "*";
        List<Integer> modules = new ArrayList<>();
        for (int i = 0; i < clean.length(); i++) {
            String pat = patterns.get(clean.charAt(i));
            if (pat == null) pat = patterns.get('-');
            for (int j = 0; j < pat.length(); j++) {
                int w = (pat.charAt(j) == 'w') ? 3 : 1;
                boolean isBar = (j % 2 == 0);
                for (int m = 0; m < w; m++) {
                    modules.add(isBar ? 1 : 0);
                }
            }
            modules.add(0); // inter-character narrow gap
        }

        int quietZone = 10;
        int totalModules = modules.size() + quietZone * 2;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);

        float moduleWidth = (float) width / totalModules;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i) == 1) {
                float left = (quietZone + i) * moduleWidth;
                float right = left + moduleWidth + 0.5f;
                canvas.drawRect(left, 4, right, height - 4, paint);
            }
        }
        return bmp;
    }
}
