package com.example.do_an_di_dong.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.do_an_di_dong.MainActivity;
import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.api.ApiClient;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityLoginBinding;
import com.example.do_an_di_dong.model.LoginResponse;
import com.example.do_an_di_dong.ui.common.ServerSettingsDialog;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Màn hình Đăng nhập (LoginActivity) - Điểm vào xác thực của ứng dụng.
 * Gắn với layout: activity_login.xml (thông qua ActivityLoginBinding).
 * Phối hợp với: DataRepository (gọi API login/OTP/OAuth), ApiClient (quản lý JWT token),
 * và UserSessionManager (lưu session, phân quyền RBAC để điều hướng màn hình đích).
 */
public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private DataRepository repository;
    private SharedPreferences prefs;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private static final String PREFS_LOGIN = "LoginPrefs";
    private static final String KEY_SAVED_USERNAME = "saved_username";

    /**
     * Khởi tạo màn hình đăng nhập khi Activity được tạo.
     * Kiểm tra cờ force_login (bị kick/hết phiên), tự động bỏ qua đăng nhập nếu JWT token
     * và session còn hợp lệ, điền sẵn username cũ và gắn sự kiện cho các nút bấm.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        prefs = getSharedPreferences(PREFS_LOGIN, MODE_PRIVATE);
        initGoogleSignIn();

        // Nếu bị buộc đăng xuất từ màn hình khác (VD: token hết hạn 401 hoặc bị khóa tài khoản)
        if (getIntent().getBooleanExtra("force_login", false)) {
            ApiClient.clearToken(this);
            UserSessionManager.clearSession(this);
            String kickReason = getIntent().getStringExtra("kick_reason");
            if (kickReason != null && !kickReason.trim().isEmpty()) {
                com.example.do_an_di_dong.util.AppToast.show(this, kickReason, Toast.LENGTH_LONG);
                final String reasonMsg = kickReason.trim();
                binding.getRoot().post(() -> com.example.do_an_di_dong.ui.common.ModernConfirmSheet.noticeDanger(
                        LoginActivity.this,
                        "Phiên làm việc đã kết thúc",
                        reasonMsg,
                        "Đã hiểu"
                ));
            }
        }

        // Tự động đăng nhập (Auto-login) nếu đã có JWT token và thông tin User hợp lệ trong máy
        String token = ApiClient.getToken(this);
        com.example.do_an_di_dong.model.User sessionUser = UserSessionManager.getCurrentUser(this);
        if (token != null && !token.isEmpty() && sessionUser != null && sessionUser.getUserId() > 0) {
            navigateAfterLogin();
            return;
        } else if (token != null && !token.isEmpty()) {
            ApiClient.clearToken(this);
            UserSessionManager.clearSession(this);
        }

        // Pre-fill last username if available
        String savedUsername = prefs.getString(KEY_SAVED_USERNAME, "");
        if (!savedUsername.isEmpty() && binding.etEmail.getText() != null && binding.etEmail.getText().length() == 0) {
            binding.etEmail.setText(savedUsername);
        }

        setupTextWatchers();

        binding.btnLogin.setOnClickListener(v -> handlePasswordLogin());
        binding.btnGoogleLogin.setOnClickListener(v -> startGoogleSignIn());
        binding.btnServerSettings.setOnClickListener(v -> ServerSettingsDialog.show(this));
        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    /**
     * Lắng nghe sự kiện gõ phím trên ô Email và Mật khẩu để tự động xóa thông báo lỗi đỏ
     * ngay khi người dùng bắt đầu nhập lại.
     */
    private void setupTextWatchers() {
        binding.etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (binding.tilEmail.getError() != null) binding.tilEmail.setError(null);
            }
        });

        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (binding.tilPassword.getError() != null) binding.tilPassword.setError(null);
            }
        });
    }

    /**
     * Kiểm tra tính hợp lệ (không rỗng) của chuỗi tài khoản/email.
     */
    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            binding.tilEmail.setError("Tài khoản hoặc email không được để trống");
            return false;
        }
        binding.tilEmail.setError(null);
        return true;
    }

    /**
     * Kiểm tra tính hợp lệ (không rỗng) của chuỗi mật khẩu.
     */
    private boolean validatePassword(String password) {
        if (password.isEmpty()) {
            binding.tilPassword.setError("Mật khẩu không được để trống");
            return false;
        }
        binding.tilPassword.setError(null);
        return true;
    }

    /**
     * Kích hoạt khi người dùng bấm nút "Đăng nhập".
     * Kiểm tra dữ liệu đầu vào bằng FormValidator trước khi gọi API đăng nhập.
     */
    private void handlePasswordLogin() {
        boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                .required(binding.tilEmail, binding.etEmail, "Tài khoản hoặc email")
                .custom(binding.tilEmail, binding.etEmail, val ->
                        (val.contains("@") && !com.example.do_an_di_dong.util.FormValidator.isValidEmail(val))
                                ? "Email không đúng định dạng (VD: ten@gmail.com)"
                                : null)
                .required(binding.tilPassword, binding.etPassword, "Mật khẩu")
                .validate();
        if (!valid) {
            return;
        }
        String username = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etEmail);
        String password = com.example.do_an_di_dong.util.FormValidator.textOf(binding.etPassword);
        executePasswordLogin(username, password);
    }

    /**
     * Thực thi gọi API đăng nhập bằng username/password qua DataRepository.
     * Nếu thành công: lưu session người dùng, lưu username vào SharedPreferences và chuyển màn hình.
     * Nếu thất bại: phân tích mã lỗi (401, lỗi kết nối mạng/server) để hiển thị thông báo dễ hiểu.
     */
    private void executePasswordLogin(final String username, final String password) {
        binding.layoutProgress.setVisibility(View.VISIBLE);
        ApiClient.clearToken(this);
        UserSessionManager.clearSession(this);

        // Gọi API đăng nhập bất đồng bộ qua DataRepository
        repository.login(username, password, new DataRepository.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                binding.layoutProgress.setVisibility(View.GONE);

                String fullName = "Quản trị viên";
                if (result != null && result.getUser() != null) {
                    if (username.contains("@")) {
                        result.getUser().setEmail(username.trim());
                    }
                    // Lưu thông tin user & quyền hạn (RBAC) vào SharedPreferences session
                    UserSessionManager.saveUserSession(LoginActivity.this, result.getUser());
                    if (result.getUser().getFullName() != null && !result.getUser().getFullName().isEmpty()) {
                        fullName = result.getUser().getFullName();
                    }
                }

                prefs.edit()
                        .putString("user_fullname", fullName)
                        .putString(KEY_SAVED_USERNAME, username)
                        .apply();

                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT);
                navigateAfterLogin();
            }

            @Override
            public void onError(String message) {
                binding.layoutProgress.setVisibility(View.GONE);
                String safeMsg = message != null ? message : "Đăng nhập thất bại.";
                String lower = safeMsg.toLowerCase(java.util.Locale.ROOT);
                String userFriendlyMessage = safeMsg;
                // Phân loại lỗi xác thực (401) và lỗi mạng/cấu hình IP Server
                if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("không chính xác")) {
                    userFriendlyMessage = "Tài khoản hoặc mật khẩu không chính xác.";
                } else if (lower.contains("connectexception")
                        || lower.contains("timeout")
                        || lower.contains("unable to resolve host")
                        || lower.contains("không thể kết nối")
                        || lower.contains("quá thời gian chờ")
                        || lower.contains("chứng chỉ")) {
                    userFriendlyMessage = "Không thể kết nối máy chủ. Nhấn biểu tượng Bánh răng góc phải để kiểm tra cấu hình Server.";
                }
                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, userFriendlyMessage, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Hiển thị BottomSheetDialog nhập Email/Username khi người dùng bấm "Quên mật khẩu".
     */
    private void showForgotPasswordDialog() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        com.google.android.material.button.MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Khôi phục mật khẩu (OTP)");
        tvSubtitle.setText("Hệ thống sẽ gửi mã xác thực OTP gồm 6 chữ số về email đăng ký của bạn");
        tilField1.setHint("Email hoặc Tên đăng nhập *");
        btnSubmit.setText("Nhận mã OTP");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .required(tilField1, etField1, "Email hoặc Tên đăng nhập")
                    .custom(tilField1, etField1, val ->
                            (val.contains("@") && !com.example.do_an_di_dong.util.FormValidator.isValidEmail(val))
                                    ? "Email không đúng định dạng (VD: ten@gmail.com)"
                                    : null)
                    .validate();
            if (!valid) {
                return;
            }
            String identifier = com.example.do_an_di_dong.util.FormValidator.textOf(etField1);
            sheet.dismiss();
            requestForgotOtp(identifier);
        });

        sheet.show();
    }

    /**
     * Gọi API yêu cầu Backend gửi mã OTP 6 số về email của tài khoản cần khôi phục.
     * Khi gửi thành công sẽ mở tiếp hộp thoại nhập mã OTP và mật khẩu mới.
     */
    private void requestForgotOtp(final String identifier) {
        binding.layoutProgress.setVisibility(View.VISIBLE);
        repository.requestForgotPasswordOtp(identifier, new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                binding.layoutProgress.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, "Đã gửi mã OTP! Vui lòng kiểm tra email.", Toast.LENGTH_SHORT);
                showResetPasswordWithOtpDialog(identifier);
            }

            @Override
            public void onError(String message) {
                binding.layoutProgress.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, message, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Hiển thị BottomSheetDialog bước 2 của luồng quên mật khẩu:
     * Nhập mã OTP 6 số + Mật khẩu mới và gọi API xác nhận đổi mật khẩu.
     */
    private void showResetPasswordWithOtpDialog(final String identifier) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_simple_input, null);
        sheet.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvSimpleSheetTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvSimpleSheetSubtitle);
        TextInputLayout tilField1 = view.findViewById(R.id.tilSimpleField1);
        TextInputEditText etField1 = view.findViewById(R.id.etSimpleField1);
        TextInputLayout tilField2 = view.findViewById(R.id.tilSimpleField2);
        TextInputEditText etField2 = view.findViewById(R.id.etSimpleField2);
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btnSimpleCancel);
        com.google.android.material.button.MaterialButton btnSubmit = view.findViewById(R.id.btnSimpleSubmit);

        tvTitle.setText("Đặt lại mật khẩu mới");
        tvSubtitle.setText("Tài khoản xác thực: " + identifier);
        tilField1.setHint("Mã OTP (6 chữ số) *");
        etField1.setInputType(InputType.TYPE_CLASS_NUMBER);
        tilField2.setVisibility(View.VISIBLE);
        tilField2.setHint("Mật khẩu mới (tối thiểu 6 ký tự) *");
        btnSubmit.setText("Đổi mật khẩu");

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSubmit.setOnClickListener(v -> {
            boolean valid = com.example.do_an_di_dong.util.FormValidator.create()
                    .otp(tilField1, etField1, "Mã OTP")
                    .minLength(tilField2, etField2, "Mật khẩu mới", 6, true)
                    .validate();
            if (!valid) {
                return;
            }
            String otp = com.example.do_an_di_dong.util.FormValidator.textOf(etField1);
            String newPass = com.example.do_an_di_dong.util.FormValidator.textOf(etField2);
            sheet.dismiss();

            binding.layoutProgress.setVisibility(View.VISIBLE);
            repository.resetPasswordWithOtp(identifier, otp, newPass, new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    binding.layoutProgress.setVisibility(View.GONE);
                    com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, "Đặt lại mật khẩu thành công! Hãy đăng nhập.", Toast.LENGTH_LONG);
                }

                @Override
                public void onError(String message) {
                    binding.layoutProgress.setVisibility(View.GONE);
                    com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, message, Toast.LENGTH_LONG);
                }
            });
        });

        sheet.show();
    }

    /**
     * Cấu hình GoogleSignInClient (yêu cầu Email, Profile, ID Token) và đăng ký
     * ActivityResultLauncher để hứng kết quả trả về từ popup chọn tài khoản Google.
     */
    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(getString(R.string.google_web_client_id))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData())
        );
    }

    /**
     * Kích hoạt khi người dùng bấm nút "Đăng nhập bằng Google".
     * Kiểm tra Google Play Services trên thiết bị, đăng xuất phiên Google cục bộ cũ
     * để luôn hiển thị popup cho phép chọn lại tài khoản Google.
     */
    private void startGoogleSignIn() {
        int playStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (playStatus != ConnectionResult.SUCCESS) {
            if (GoogleApiAvailability.getInstance().isUserResolvableError(playStatus)) {
                android.app.Dialog errorDialog = GoogleApiAvailability.getInstance().getErrorDialog(this, playStatus, 9001);
                if (errorDialog != null) {
                    errorDialog.show();
                    return;
                }
            }
            com.example.do_an_di_dong.util.AppToast.show(
                    this,
                    "Thiết bị hoặc máy ảo chưa có Google Play Services để đăng nhập Google.",
                    Toast.LENGTH_LONG
            );
            return;
        }

        binding.layoutProgress.setVisibility(View.VISIBLE);
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            binding.layoutProgress.setVisibility(View.GONE);
            if (!isFinishing() && !isDestroyed()) {
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
            }
        });
    }

    /**
     * Xử lý Intent kết quả trả về sau khi người dùng chọn tài khoản Google từ popup hệ thống.
     * Trích xuất email, idToken, displayName để gửi lên Backend xác thực, hoặc bắt các mã lỗi OAuth (Mã 10, 12500).
     */
    private void handleGoogleSignInResult(Intent data) {
        if (data == null) {
            return;
        }
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getEmail() == null || account.getEmail().trim().isEmpty()) {
                com.example.do_an_di_dong.util.AppToast.show(
                        this,
                        "Không lấy được địa chỉ email từ tài khoản Google.",
                        Toast.LENGTH_LONG
                );
                return;
            }
            performGoogleLogin(account.getEmail(), account.getIdToken(), account.getDisplayName());
        } catch (ApiException e) {
            int code = e.getStatusCode();
            if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED || code == CommonStatusCodes.CANCELED) {
                return;
            }
            String errMsg;
            if (code == CommonStatusCodes.DEVELOPER_ERROR) {
                errMsg = "Lỗi cấu hình OAuth (Mã 10): kiểm tra Package Name & SHA-1 trên Google Cloud Console.";
            } else if (code == GoogleSignInStatusCodes.SIGN_IN_FAILED) {
                errMsg = "Đăng nhập Google thất bại (Mã 12500): kiểm tra OAuth Consent Screen hoặc thêm Gmail vào Test users.";
            } else if (code == CommonStatusCodes.NETWORK_ERROR) {
                errMsg = "Lỗi mạng khi kết nối Google Play Services.";
            } else if (code == GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS) {
                return;
            } else {
                errMsg = "Đăng nhập Google thất bại (Mã " + code + ").";
            }
            com.example.do_an_di_dong.util.AppToast.show(this, errMsg, Toast.LENGTH_LONG);
        }
    }

    /**
     * Gửi thông tin tài khoản Google (email, idToken) lên Backend qua API externalLogin.
     * Nhận về JWT token của hệ thống, lưu session người dùng và điều hướng vào ứng dụng.
     */
    private void performGoogleLogin(String email, String idToken, String displayName) {
        final String targetEmail = email != null ? email.trim() : "";
        if (targetEmail.isEmpty()) return;

        binding.layoutProgress.setVisibility(View.VISIBLE);
        ApiClient.clearToken(this);
        UserSessionManager.clearSession(this);

        repository.externalLogin("Google", targetEmail, idToken, displayName, new DataRepository.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                binding.layoutProgress.setVisibility(View.GONE);

                String fullName = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : "Google User";
                if (result != null && result.getUser() != null) {
                    if (result.getUser().getEmail() == null || result.getUser().getEmail().trim().isEmpty()) {
                        result.getUser().setEmail(targetEmail);
                    }
                    UserSessionManager.saveUserSession(LoginActivity.this, result.getUser());
                    if (result.getUser().getFullName() != null && !result.getUser().getFullName().isEmpty()) {
                        fullName = result.getUser().getFullName();
                    }
                }

                prefs.edit()
                        .putString("user_fullname", fullName)
                        .putString(KEY_SAVED_USERNAME, targetEmail)
                        .apply();

                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, "Xin chào " + fullName, Toast.LENGTH_SHORT);
                navigateAfterLogin();
            }

            @Override
            public void onError(String message) {
                binding.layoutProgress.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(LoginActivity.this, "Đăng nhập Google thất bại: " + message, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Điều hướng người dùng sau khi đăng nhập thành công.
     * Dựa vào quyền RBAC trong UserSessionManager để mở màn hình phù hợp
     * (VD: Admin/Manager vào Dashboard MainActivity, Thu ngân vào PosActivity, Thủ kho vào Kho) rồi đóng LoginActivity.
     */
    private void navigateAfterLogin() {
        com.example.do_an_di_dong.util.AuthSessionGuard.getInstance(this).onLoginSuccess();
        // Xác định Activity trang chủ tương ứng với Role của user
        Class<?> target = UserSessionManager.getLandingActivityClass(this);
        Intent intent = new Intent(LoginActivity.this, target);
        startActivity(intent);
        finish();
    }
}
