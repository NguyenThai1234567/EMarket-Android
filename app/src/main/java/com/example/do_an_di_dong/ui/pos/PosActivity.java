package com.example.do_an_di_dong.ui.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityPosBinding;
import com.example.do_an_di_dong.databinding.DialogInvoiceReceiptBinding;
import com.example.do_an_di_dong.databinding.DialogPosPaymentBinding;
import com.example.do_an_di_dong.model.CartItem;
import com.example.do_an_di_dong.model.CheckoutResult;
import com.example.do_an_di_dong.model.CreateQrRequest;
import com.example.do_an_di_dong.model.CreateQrResponse;
import com.example.do_an_di_dong.model.Customer;
import com.example.do_an_di_dong.model.PayOSStatusResponse;
import com.example.do_an_di_dong.ui.common.AppNavigationShell;
import com.example.do_an_di_dong.ui.product.BarcodeScanActivity;
import com.example.do_an_di_dong.util.MoneyTextWatcher;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Bán hàng tại quầy POS (Tab chính thứ 2) - Kiến trúc Zero-Fragment (0 Fragment).
 * - Vai trò: Điều phối toàn bộ luồng thu ngân tại quầy: tìm kiếm/quét mã vạch sản phẩm, quản lý giỏ hàng,
 *   áp dụng khách hàng thân thiết (loyalty points), thanh toán (Tiền mặt hoặc VietQR PayOS polling tự động) và xuất hóa đơn nhiệt.
 * - Gắn kết:
 *   + Layout XML: activity_pos.xml (ActivityPosBinding), dialog_pos_payment.xml, dialog_invoice_receipt.xml.
 *   + ViewModel: PosViewModel (quản lý state giỏ hàng, tính tiền/chiết khấu, gọi API thanh toán).
 *   + Adapter: PosCartAdapter (danh sách món trong giỏ), PosSuggestionAdapter (gợi ý tìm kiếm nhanh).
 */
public class PosActivity extends AppCompatActivity {

    private ActivityPosBinding binding;
    private PosViewModel viewModel;
    private PosCartAdapter cartAdapter;
    private PosSuggestionAdapter suggestionAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final Handler payosPollHandler = new Handler(Looper.getMainLooper());
    private Runnable payosPollRunnable;

    // Launcher nhận kết quả mã vạch trả về từ màn hình camera BarcodeScanActivity
    private final ActivityResultLauncher<Intent> barcodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("scanned_barcode");
                    if (barcode != null && !barcode.isEmpty()) {
                        viewModel.addByBarcode(barcode);
                    }
                }
            });

    /**
     * Khởi tạo màn hình POS khi Activity được tạo.
     * - Kích hoạt: Lifecycle onCreate của Android.
     * - Thực hiện: Gắn ViewBinding, khởi tạo PosViewModel, cấu hình thanh điều hướng chung (Drawer + BottomNav),
     *   thiết lập RecyclerView, ô tìm kiếm, sự kiện nút bấm và đăng ký lắng nghe LiveData.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(PosViewModel.class);

        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_pos,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                null
        );

        setupRecyclerViews();
        setupSearchAndScanner();
        setupCustomerAndLoyalty();
        setupActions();
        observeViewModel();
    }

    /**
     * Đồng bộ lại trạng thái thanh điều hướng và badge chi nhánh mỗi khi quay lại màn hình POS.
     * - Kích hoạt: Lifecycle onResume khi Activity hiển thị lại trên foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        AppNavigationShell.setup(
                this,
                binding.drawerLayout,
                binding.bottomNavigation,
                R.id.nav_pos,
                binding.btnOpenDrawer,
                binding.tvBranchBadge,
                null
        );
    }

    /**
     * Khởi tạo và gắn Adapter cho 2 danh sách: Giỏ hàng hiện tại (rvPosCart) và Gợi ý tìm kiếm (rvPosSuggestions).
     * - Kích hoạt: Gọi 1 lần trong onCreate().
     * - Luồng dữ liệu: Bắt callback tăng/giảm/xóa món từ PosCartAdapter và chọn sản phẩm từ PosSuggestionAdapter để đẩy vào PosViewModel.
     */
    private void setupRecyclerViews() {
        cartAdapter = new PosCartAdapter();
        cartAdapter.setListener(new PosCartAdapter.OnCartItemChangeListener() {
            @Override
            public void onQuantityChanged(CartItem item, int newQuantity) {
                viewModel.updateItemQuantity(item, newQuantity);
            }

            @Override
            public void onItemRemoved(CartItem item) {
                viewModel.removeItem(item);
            }
        });

        binding.rvPosCart.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPosCart.setAdapter(cartAdapter);

        suggestionAdapter = new PosSuggestionAdapter(product -> {
            viewModel.addProductToCart(product);
            binding.etPosSearch.setText("");
            binding.rvPosSuggestions.setVisibility(View.GONE);
        });

        binding.rvPosSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPosSuggestions.setAdapter(suggestionAdapter);
    }

    /**
     * Thiết lập sự kiện cho cụm tìm kiếm sản phẩm (có debounce 300ms) và nút mở Camera quét mã vạch.
     * - Kích hoạt: Gọi 1 lần trong onCreate().
     */
    private void setupSearchAndScanner() {
        // Đóng gói Intent mở BarcodeScanActivity để quét mã vạch sản phẩm qua Camera
        binding.btnPosBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            barcodeLauncher.launch(intent);
        });

        binding.btnPosClearSearch.setOnClickListener(v -> {
            binding.etPosSearch.setText("");
            binding.rvPosSuggestions.setVisibility(View.GONE);
            binding.btnPosClearSearch.setVisibility(View.GONE);
        });

        // Debounce 300ms khi gõ từ khóa tìm kiếm để tránh spam truy vấn DB/API liên tục mỗi ký tự
        binding.etPosSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnPosClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    String query = s.toString().trim();
                    if (!query.isEmpty()) {
                        viewModel.searchProducts(query);
                    } else {
                        binding.rvPosSuggestions.setVisibility(View.GONE);
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Thiết lập sự kiện chọn khách hàng thành viên, bỏ chọn khách hàng (trên Chip) và bật/tắt dùng điểm tích lũy.
     * - Kích hoạt: Gọi 1 lần trong onCreate().
     */
    private void setupCustomerAndLoyalty() {
        binding.btnPosSelectCustomer.setOnClickListener(v -> showSelectCustomerDialog());
        binding.chipPosCustomer.setOnCloseIconClickListener(v -> viewModel.clearCustomer());
        binding.switchPosUsePoints.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setUsePoints(isChecked));
    }

    /**
     * Thiết lập sự kiện cho nút Xóa toàn bộ giỏ hàng (kèm hộp thoại xác nhận) và nút Thanh toán (Checkout).
     * - Kích hoạt: Gọi 1 lần trong onCreate().
     */
    private void setupActions() {
        binding.btnPosClearCart.setOnClickListener(v ->
                com.example.do_an_di_dong.ui.common.ModernConfirmSheet.confirmDanger(
                        this,
                        "Xóa giỏ hàng",
                        "Bạn có chắc chắn muốn dọn sạch toàn bộ món hàng trong giỏ?",
                        "Xóa hết",
                        () -> viewModel.clearCart()
                )
        );

        binding.btnPosCheckout.setOnClickListener(v -> {
            List<CartItem> items = viewModel.getCart().getValue();
            if (items == null || items.isEmpty()) {
                com.example.do_an_di_dong.util.AppToast.show(this, "Giỏ hàng đang trống! Vui lòng chọn sản phẩm.", Toast.LENGTH_SHORT);
                return;
            }
            showPaymentDialog();
        });
    }

    /**
     * Đăng ký lắng nghe (Observe) toàn bộ LiveData từ PosViewModel để tự động cập nhật giao diện theo mô hình MVVM.
     * - Kích hoạt: Gọi 1 lần trong onCreate().
     * - Nhận dữ liệu: Danh sách giỏ hàng, gợi ý tìm kiếm, tổng tiền phải trả, tổng số món, khách hàng đã chọn, trạng thái loading và thông báo lỗi.
     */
    private void observeViewModel() {
        // Cập nhật danh sách giỏ hàng và ẩn/hiện trạng thái giỏ hàng trống
        viewModel.getCart().observe(this, items -> {
            cartAdapter.setItems(items);
            boolean isEmpty = items == null || items.isEmpty();
            binding.layoutEmptyCart.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvPosCart.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.btnPosCheckout.setEnabled(!isEmpty);
        });

        viewModel.getSuggestions().observe(this, suggestions -> {
            if (suggestions != null && !suggestions.isEmpty() && binding.etPosSearch.getText().length() > 0) {
                suggestionAdapter.setProducts(suggestions);
                binding.rvPosSuggestions.setVisibility(View.VISIBLE);
            } else {
                binding.rvPosSuggestions.setVisibility(View.GONE);
            }
        });

        viewModel.getFinalPayableAmount().observe(this, payable -> {
            binding.tvPosTotalAmount.setText(MoneyTextWatcher.formatCurrency(payable != null ? payable : 0.0));
        });

        viewModel.getTotalQuantity().observe(this, qty -> {
            binding.tvPosItemCount.setText("Số lượng món: " + (qty != null ? qty : 0));
        });

        // Khi gắn khách hàng: ẩn nút chọn, hiện Chip tên + số điểm tích lũy và cho phép bật Switch đổi điểm
        viewModel.getSelectedCustomer().observe(this, customer -> {
            if (customer != null) {
                binding.btnPosSelectCustomer.setVisibility(View.GONE);
                binding.chipPosCustomer.setVisibility(View.VISIBLE);
                binding.chipPosCustomer.setText(customer.getFullName() + " (" + customer.getLoyaltyPoints() + " pts)");
                binding.switchPosUsePoints.setVisibility(View.VISIBLE);
            } else {
                binding.btnPosSelectCustomer.setVisibility(View.VISIBLE);
                binding.chipPosCustomer.setVisibility(View.GONE);
                binding.switchPosUsePoints.setVisibility(View.GONE);
                binding.switchPosUsePoints.setChecked(false);
            }
        });

        viewModel.getLoading().observe(this, isLoading -> {
            binding.progressPosLoading.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                com.example.do_an_di_dong.util.AppToast.show(this, error, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Mở BottomSheet chọn khách hàng thành viên và cập nhật khách hàng được chọn vào PosViewModel.
     * - Kích hoạt: Khi thu ngân bấm nút chọn khách hàng (btnPosSelectCustomer).
     */
    private void showSelectCustomerDialog() {
        com.example.do_an_di_dong.ui.common.ModernPickerSheets.showCustomerPicker(this, customer -> {
            if (customer != null) {
                viewModel.selectCustomer(customer);
            }
        });
    }

    /**
     * Hiển thị hộp thoại thanh toán (DialogPosPaymentBinding) hỗ trợ 2 phương thức: Tiền mặt (Cash) và Chuyển khoản VietQR PayOS (BankTransfer).
     * - Kích hoạt: Khi thu ngân bấm nút Thanh toán (btnPosCheckout) và giỏ hàng có ít nhất 1 món.
     * - Xử lý: Chuyển tab phương thức, gợi ý mệnh giá tiền mặt, tính tiền thừa thời gian thực, tạo mã QR PayOS và xác nhận thanh toán.
     */
    private void showPaymentDialog() {
        double payableAmount = viewModel.getFinalPayableAmount().getValue() != null ? viewModel.getFinalPayableAmount().getValue() : 0.0;

        DialogPosPaymentBinding payBinding = DialogPosPaymentBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(payBinding.getRoot())
                .setCancelable(false)
                .create();

        payBinding.tvPosPayableAmount.setText(MoneyTextWatcher.formatCurrency(payableAmount));

        final String[] paymentMethod = {"Cash"};
        final long[] currentPayosOrderCode = {0};

        // Rẽ nhánh tab thanh toán: Tab 0 = Tiền mặt (dừng polling QR), Tab 1 = Chuyển khoản (gọi API tạo mã VietQR PayOS)
        payBinding.tabPosPaymentMethods.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    paymentMethod[0] = "Cash";
                    stopPayosPolling();
                    payBinding.layoutCashPayment.setVisibility(View.VISIBLE);
                    payBinding.layoutQrPayment.setVisibility(View.GONE);
                } else {
                    paymentMethod[0] = "BankTransfer";
                    payBinding.layoutCashPayment.setVisibility(View.GONE);
                    payBinding.layoutQrPayment.setVisibility(View.VISIBLE);
                    loadVietQrPayOS(payBinding, (long) payableAmount, currentPayosOrderCode, dialog);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        payBinding.btnDenom50k.setOnClickListener(v -> setCashAmount(payBinding, 50000, payableAmount));
        payBinding.btnDenom100k.setOnClickListener(v -> setCashAmount(payBinding, 100000, payableAmount));
        payBinding.btnDenom200k.setOnClickListener(v -> setCashAmount(payBinding, 200000, payableAmount));
        payBinding.btnDenom500k.setOnClickListener(v -> setCashAmount(payBinding, 500000, payableAmount));

        // Công thức tính tiền thừa trả khách khi thu ngân nhập số tiền khách đưa: Tiền thừa = Tiền khách đưa - Tổng phải trả
        payBinding.etCustomerCash.addTextChangedListener(new MoneyTextWatcher(payBinding.etCustomerCash, cleanVal -> {
            double givenCash = cleanVal.isEmpty() ? 0 : Double.parseDouble(cleanVal);
            double change = givenCash - payableAmount;
            if (change >= 0) {
                payBinding.tvPosChangeAmount.setText(MoneyTextWatcher.formatCurrency(change));
                payBinding.tvPosChangeAmount.setTextColor(getColor(R.color.on_primary_container));
            } else {
                payBinding.tvPosChangeAmount.setText("Chưa đủ tiền mặt");
                payBinding.tvPosChangeAmount.setTextColor(getColor(R.color.error));
            }
        }));

        payBinding.btnCheckPayosStatus.setOnClickListener(v -> {
            if (currentPayosOrderCode[0] > 0) {
                checkPayosManualStatus(payBinding, currentPayosOrderCode[0], payableAmount, dialog);
            } else {
                com.example.do_an_di_dong.util.AppToast.show(this, "Chưa khởi tạo mã đơn PayOS", Toast.LENGTH_SHORT);
            }
        });

        payBinding.btnCancelPosPayment.setOnClickListener(v -> {
            stopPayosPolling();
            dialog.dismiss();
        });

        // Kiểm tra điều kiện hợp lệ (tiền khách đưa >= tiền hàng nếu trả tiền mặt) trước khi gọi API chốt đơn
        payBinding.btnConfirmPosPayment.setOnClickListener(v -> {
            if (payableAmount <= 0) {
                com.example.do_an_di_dong.util.AppToast.show(this, "Tổng tiền thanh toán không hợp lệ!", Toast.LENGTH_SHORT);
                return;
            }
            if ("Cash".equals(paymentMethod[0])) {
                boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                        .custom(payBinding.tilCustomerCash, payBinding.etCustomerCash, val -> {
                            String cashStr = payBinding.etCustomerCash.getText() != null
                                    ? payBinding.etCustomerCash.getText().toString().replaceAll("[^0-9]", "")
                                    : "";
                            double given = cashStr.isEmpty() ? 0 : Double.parseDouble(cashStr);
                            return given < payableAmount ? "Số tiền khách đưa chưa đủ để thanh toán!" : null;
                        })
                        .validate();
                if (!valid) {
                    com.example.do_an_di_dong.util.AppToast.show(this, "Số tiền khách đưa chưa đủ để thanh toán!", Toast.LENGTH_SHORT);
                    return;
                }
                String cashStr = payBinding.etCustomerCash.getText() != null ? payBinding.etCustomerCash.getText().toString().replaceAll("[^0-9]", "") : "";
                double givenCash = cashStr.isEmpty() ? 0 : Double.parseDouble(cashStr);
                executeCheckout("Cash", givenCash, givenCash - payableAmount, dialog);
            } else {
                executeCheckout("BankTransfer", payableAmount, 0, dialog);
            }
        });

        dialog.show();
    }

    /**
     * Điền nhanh mệnh giá tiền mặt (50k, 100k, 200k, 500k) vào ô tiền khách đưa và tính ngay tiền thừa.
     * - Kích hoạt: Khi thu ngân bấm các nút mệnh giá nhanh trong dialog thanh toán.
     */
    private void setCashAmount(DialogPosPaymentBinding payBinding, long amount, double payableAmount) {
        payBinding.etCustomerCash.setText(String.valueOf(amount));
        double change = amount - payableAmount;
        if (change >= 0) {
            payBinding.tvPosChangeAmount.setText(MoneyTextWatcher.formatCurrency(change));
            payBinding.tvPosChangeAmount.setTextColor(getColor(R.color.on_primary_container));
        } else {
            payBinding.tvPosChangeAmount.setText("Chưa đủ tiền mặt");
            payBinding.tvPosChangeAmount.setTextColor(getColor(R.color.error));
        }
    }

    /**
     * Gọi API qua PosViewModel để tạo link thanh toán VietQR PayOS, hiển thị ảnh QR bằng Glide và kích hoạt polling kiểm tra trạng thái.
     * - Kích hoạt: Khi thu ngân chuyển sang tab "Chuyển khoản" trong dialog thanh toán.
     * - Tham số: payBinding (view dialog), amount (số tiền cần thu), orderCodeHolder (mảng 1 phần tử lưu mã đơn PayOS trả về), parentDialog.
     */
    private void loadVietQrPayOS(DialogPosPaymentBinding payBinding, long amount, long[] orderCodeHolder, AlertDialog parentDialog) {
        stopPayosPolling();
        payBinding.pbQrLoading.setVisibility(View.VISIBLE);
        payBinding.ivPayosQrCode.setVisibility(View.GONE);
        payBinding.tvQrError.setVisibility(View.GONE);
        payBinding.tvQrStatusMessage.setText("Đang khởi tạo mã giao dịch VietQR PayOS...");

        CreateQrRequest request = new CreateQrRequest((int) amount, "POS " + System.currentTimeMillis());

        viewModel.createPayOSLink(request, new DataRepository.ApiCallback<CreateQrResponse>() {
            @Override
            public void onSuccess(CreateQrResponse response) {
                if (isFinishing() || isDestroyed()) return;
                payBinding.pbQrLoading.setVisibility(View.GONE);

                if (response != null && response.isSuccess() && response.getQrCode() != null) {
                    payBinding.ivPayosQrCode.setVisibility(View.VISIBLE);
                    orderCodeHolder[0] = response.getOrderCode();
                    payBinding.tvQrOrderCode.setText("Mã GD PayOS: #" + response.getOrderCode());
                    payBinding.tvQrStatusMessage.setText("Đang chờ khách chuyển khoản... Hệ thống sẽ tự động tạo đơn khi nhận tiền.");

                    // Nếu PayOS trả về chuỗi raw EMVCo QR (không phải URL ảnh), bọc qua api.qrserver.com để render thành ảnh QR
                    String qrString = response.getQrCode();
                    String renderUrl;
                    try {
                        if (qrString.startsWith("http")) {
                            renderUrl = qrString;
                        } else {
                            renderUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + URLEncoder.encode(qrString, "UTF-8");
                        }
                        Glide.with(PosActivity.this).load(renderUrl).into(payBinding.ivPayosQrCode);
                    } catch (Exception e) {
                        Glide.with(PosActivity.this).load(qrString).into(payBinding.ivPayosQrCode);
                    }

                    startPayosPolling(payBinding, response.getOrderCode(), amount, parentDialog);
                } else {
                    String msg = response != null && response.getMessage() != null ? response.getMessage() : "Lỗi khởi tạo cổng PayOS";
                    payBinding.tvQrError.setText(msg);
                    payBinding.tvQrError.setVisibility(View.VISIBLE);
                    payBinding.tvQrStatusMessage.setText("Không thể tải mã VietQR");
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                payBinding.pbQrLoading.setVisibility(View.GONE);
                payBinding.tvQrError.setText("Lỗi kết nối PayOS: " + message);
                payBinding.tvQrError.setVisibility(View.VISIBLE);
                payBinding.tvQrStatusMessage.setText("Lỗi mạng khi gọi PayOS");
            }
        });
    }

    /**
     * Vòng lặp Polling định kỳ (mỗi 2.5 giây) gọi API kiểm tra trạng thái đơn PayOS; tự động chốt đơn ngay khi khách chuyển khoản thành công.
     * - Kích hoạt: Tự động chạy ngay sau khi tạo mã VietQR PayOS thành công trong loadVietQrPayOS().
     */
    private void startPayosPolling(DialogPosPaymentBinding payBinding, long orderCode, double payableAmount, AlertDialog parentDialog) {
        stopPayosPolling();
        payosPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed() || parentDialog == null || !parentDialog.isShowing()) {
                    stopPayosPolling();
                    return;
                }

                viewModel.checkPayOSStatus(orderCode, new DataRepository.ApiCallback<PayOSStatusResponse>() {
                    @Override
                    public void onSuccess(PayOSStatusResponse response) {
                        if (isFinishing() || isDestroyed() || parentDialog == null || !parentDialog.isShowing()) return;

                        // Khi cổng PayOS báo đã nhận tiền (isPaid == true): dừng polling, khóa nút và tự động gọi executeCheckout sau 800ms
                        if (response != null && response.isPaid()) {
                            stopPayosPolling();
                            payBinding.tvQrStatusMessage.setText("✓ ĐÃ NHẬN TIỀN QUA VIETQR PAYOS! Đang tạo đơn hàng...");
                            payBinding.tvQrStatusMessage.setTextColor(getColor(R.color.on_primary_container));
                            payBinding.btnConfirmPosPayment.setEnabled(false);
                            payBinding.btnCancelPosPayment.setEnabled(false);

                            com.example.do_an_di_dong.util.AppToast.show(PosActivity.this, "Xác nhận đã nhận tiền PayOS! Tự động tạo đơn hàng...", Toast.LENGTH_SHORT);

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (isFinishing() || !parentDialog.isShowing()) return;
                                executeCheckout("BankTransfer", payableAmount, 0, parentDialog);
                            }, 800);
                        } else {
                            payosPollHandler.postDelayed(payosPollRunnable, 2500);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed() || parentDialog == null || !parentDialog.isShowing()) return;
                        payosPollHandler.postDelayed(payosPollRunnable, 3000);
                    }
                });
            }
        };
        payosPollHandler.postDelayed(payosPollRunnable, 2500);
    }

    /**
     * Hủy callback polling PayOS trên Handler để tránh rò rỉ bộ nhớ (memory leak) hoặc gọi API ngầm khi đã đóng dialog.
     * - Kích hoạt: Khi đổi sang tab Tiền mặt, đóng dialog thanh toán, chốt đơn, hoặc khi Activity bị hủy (onDestroy).
     */
    private void stopPayosPolling() {
        if (payosPollRunnable != null) {
            payosPollHandler.removeCallbacks(payosPollRunnable);
            payosPollRunnable = null;
        }
    }

    /**
     * Kiểm tra trạng thái thanh toán PayOS thủ công theo yêu cầu của thu ngân.
     * - Kích hoạt: Khi thu ngân bấm nút "Kiểm tra trạng thái" (btnCheckPayosStatus) trên màn hình QR.
     */
    private void checkPayosManualStatus(DialogPosPaymentBinding payBinding, long orderCode, double payableAmount, AlertDialog parentDialog) {
        payBinding.tvQrStatusMessage.setText("Đang kiểm tra với cổng PayOS...");
        viewModel.checkPayOSStatus(orderCode, new DataRepository.ApiCallback<PayOSStatusResponse>() {
            @Override
            public void onSuccess(PayOSStatusResponse response) {
                if (isFinishing() || isDestroyed()) return;
                if (response != null && response.isPaid()) {
                    stopPayosPolling();
                    payBinding.tvQrStatusMessage.setText("✓ ĐÃ NHẬN TIỀN QUA PAYOS! Đang tạo đơn hàng...");
                    payBinding.tvQrStatusMessage.setTextColor(getColor(R.color.on_primary_container));
                    com.example.do_an_di_dong.util.AppToast.show(PosActivity.this, "Xác nhận đã thanh toán PayOS thành công!", Toast.LENGTH_SHORT);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isFinishing() || !parentDialog.isShowing()) return;
                        executeCheckout("BankTransfer", payableAmount, 0, parentDialog);
                    }, 600);
                } else {
                    payBinding.tvQrStatusMessage.setText("Chưa nhận được tiền (Trạng thái: PENDING)");
                    com.example.do_an_di_dong.util.AppToast.show(PosActivity.this, "Chưa phát hiện giao dịch thành công cho đơn #" + orderCode, Toast.LENGTH_SHORT);
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                payBinding.tvQrStatusMessage.setText("Lỗi kiểm tra: " + message);
            }
        });
    }

    /**
     * Thực thi chốt đơn hàng: chụp lại snapshot dữ liệu giỏ hàng hiện tại, gọi PosViewModel.checkout() để lưu đơn xuống Backend/Room,
     * sau đó xóa giỏ hàng và mở hóa đơn nhiệt (Thermal Receipt).
     * - Kích hoạt: Khi thu ngân bấm xác nhận thanh toán hoặc khi PayOS polling báo đã nhận tiền.
     */
    private void executeCheckout(String paymentMethod, double givenCash, double change, AlertDialog parentDialog) {
        stopPayosPolling();

        // Chụp snapshot dữ liệu giỏ hàng và tổng tiền trước khi gọi clearCart() để truyền sang biên lai hóa đơn
        List<CartItem> cartSnapshot = new ArrayList<>();
        if (viewModel.getCart().getValue() != null) {
            cartSnapshot.addAll(viewModel.getCart().getValue());
        }
        double rawTotal = viewModel.getRawTotalAmount().getValue() != null ? viewModel.getRawTotalAmount().getValue() : 0.0;
        double discount = viewModel.getTotalDiscount().getValue() != null ? viewModel.getTotalDiscount().getValue() : 0.0;
        double payable = viewModel.getFinalPayableAmount().getValue() != null ? viewModel.getFinalPayableAmount().getValue() : 0.0;
        Customer customerSnapshot = viewModel.getSelectedCustomer().getValue();

        viewModel.checkout(paymentMethod, new DataRepository.ApiCallback<CheckoutResult>() {
            @Override
            public void onSuccess(CheckoutResult result) {
                if (isFinishing() || isDestroyed()) return;
                if (parentDialog != null && parentDialog.isShowing()) {
                    parentDialog.dismiss();
                }
                viewModel.clearCart();
                showThermalReceipt(result, paymentMethod, givenCash, change, cartSnapshot, rawTotal, discount, payable, customerSnapshot);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                com.example.do_an_di_dong.util.AppToast.show(PosActivity.this, "Lỗi thanh toán: " + message, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Hiển thị hộp thoại Hóa đơn in nhiệt (DialogInvoiceReceiptBinding) kèm mã vạch Code39 của đơn hàng và tính năng chia sẻ hóa đơn.
     * - Kích hoạt: Tự động gọi trong callback onSuccess của executeCheckout() sau khi tạo đơn hàng thành công.
     */
    private void showThermalReceipt(CheckoutResult result, String paymentMethod, double givenCash, double change,
                                    List<CartItem> items, double rawTotal, double discount, double payable, Customer customer) {
        DialogInvoiceReceiptBinding receiptBinding = DialogInvoiceReceiptBinding.inflate(LayoutInflater.from(this));
        AlertDialog receiptDialog = new AlertDialog.Builder(this)
                .setView(receiptBinding.getRoot())
                .setCancelable(false)
                .create();

        int realOrderId = result != null ? result.getOrderId() : 0;
        receiptBinding.tvReceiptOrderCode.setText("Mã đơn: #ORD-" + realOrderId);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        receiptBinding.tvReceiptDateTime.setText("Ngày: " + sdf.format(new Date()));

        receiptBinding.tvReceiptCustomer.setText("Khách hàng: " + (customer != null ? customer.getFullName() : "Khách vãng lai"));
        String cashierName = UserSessionManager.getCurrentUserFullName(this);
        receiptBinding.tvReceiptCashier.setText("Thu ngân: " + cashierName);

        // Render động từng dòng sản phẩm (item_receipt_row.xml) vào danh sách trên hóa đơn nhiệt
        receiptBinding.layoutReceiptItemsList.removeAllViews();
        if (items != null) {
            for (CartItem item : items) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_receipt_row, receiptBinding.layoutReceiptItemsList, false);
                TextView tvName = row.findViewById(R.id.tvReceiptItemName);
                TextView tvQty = row.findViewById(R.id.tvReceiptItemQty);
                TextView tvPrice = row.findViewById(R.id.tvReceiptItemPrice);

                tvName.setText(item.getProductName());
                tvQty.setText(String.valueOf(item.getQuantity()));
                tvPrice.setText(MoneyTextWatcher.formatCurrency(item.getSubtotal()));

                receiptBinding.layoutReceiptItemsList.addView(row);
            }
        }

        receiptBinding.tvReceiptSubtotal.setText(MoneyTextWatcher.formatCurrency(rawTotal));
        if (discount > 0) {
            receiptBinding.rowReceiptDiscount.setVisibility(View.VISIBLE);
            receiptBinding.tvReceiptDiscount.setText("-" + MoneyTextWatcher.formatCurrency(discount));
        } else {
            receiptBinding.rowReceiptDiscount.setVisibility(View.GONE);
        }
        receiptBinding.tvReceiptTotal.setText(MoneyTextWatcher.formatCurrency(payable));

        receiptBinding.tvReceiptPaymentMethodLabel.setText("Phương thức: " + ("Cash".equals(paymentMethod) ? "Tiền mặt" : "Chuyển khoản VietQR PayOS"));
        receiptBinding.tvReceiptCustomerCash.setText(MoneyTextWatcher.formatCurrency(givenCash));
        receiptBinding.tvReceiptChange.setText(MoneyTextWatcher.formatCurrency(change));

        // Sinh ảnh Bitmap mã vạch chuẩn Code39 từ mã đơn hàng để in lên phần chân hóa đơn
        String qrPayload = "EMARKET-ORD:" + realOrderId;
        receiptBinding.tvReceiptQrCodeText.setText(qrPayload);
        android.graphics.Bitmap barcodeBmp = com.example.do_an_di_dong.ui.sales.OrderDetailActivity.generateCode39Barcode("ORD-" + realOrderId, 440, 110);
        if (barcodeBmp != null) {
            receiptBinding.ivReceiptBarcode.setImageBitmap(barcodeBmp);
        }

        receiptBinding.btnFinishReceipt.setOnClickListener(v -> {
            receiptDialog.dismiss();
            com.example.do_an_di_dong.ui.common.AppToast.show(this, "Đã hoàn tất đơn hàng #" + realOrderId, com.example.do_an_di_dong.ui.common.AppToast.Type.SUCCESS);
        });

        // Đóng gói Intent ACTION_SEND để chia sẻ thông tin hóa đơn dạng văn bản qua các ứng dụng khác (Zalo, SMS, Email...)
        receiptBinding.btnShareReceipt.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Hóa đơn EMarket #ORD-" + realOrderId);
            share.putExtra(Intent.EXTRA_TEXT, "Hóa đơn siêu thị EMarket #ORD-" + realOrderId + " - Tổng thanh toán: " + MoneyTextWatcher.formatCurrency(payable));
            startActivity(Intent.createChooser(share, "Chia sẻ hóa đơn"));
        });

        receiptDialog.show();
    }

    /**
     * Dọn dẹp tài nguyên và dừng tiến trình polling PayOS khi Activity bị hủy.
     * - Kích hoạt: Lifecycle onDestroy của Android.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPayosPolling();
    }
}

