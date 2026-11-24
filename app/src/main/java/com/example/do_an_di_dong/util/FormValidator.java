package com.example.do_an_di_dong.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.do_an_di_dong.R;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bộ kiểm tra dữ liệu biểu mẫu (Declarative Form Validator) theo phong cách Builder Pattern.
 * Gắn trực tiếp với các `TextInputLayout` và `EditText` trên các màn hình thêm/sửa dữ liệu (Sản phẩm,
 * Khách hàng, Nhà cung cấp, Nhân viên, Đăng nhập, Khuyến mãi...).
 * Tự động hiển thị lỗi inline dưới ô nhập, focus vào ô lỗi đầu tiên, và tự xóa thông báo lỗi ngay khi người dùng gõ lại.
 */
public final class FormValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_VN_PATTERN =
            Pattern.compile("^(?:0|\\+84)[0-9]{8,10}$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9._-]{3,}$");

    private static final Pattern OTP_PATTERN =
            Pattern.compile("^[0-9]{4,8}$");

    private static final int TAG_WATCHER_ATTACHED = 0x7f0b1001;

    private interface Rule {
        boolean check();
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<TextInputLayout> trackedTils = new ArrayList<>();
    private final List<EditText> trackedInputs = new ArrayList<>();
    private View firstInvalidView = null;

    private FormValidator() {}

    /**
     * Khởi tạo một phiên kiểm tra biểu mẫu mới (thường gọi khi người dùng bấm nút Lưu / Xác nhận).
     */
    public static FormValidator create() {
        return new FormValidator();
    }

    /**
     * Thêm quy tắc bắt buộc nhập (không được để trống) cho một ô EditText.
     */
    public FormValidator required(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra độ dài tối thiểu của chuỗi nhập vào (VD: mật khẩu tối thiểu 6 ký tự).
     */
    public FormValidator minLength(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            int minLen,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            if (val.length() < minLen) {
                setError(til, input, fieldLabel + " phải có tối thiểu " + minLen + " ký tự");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra tên đăng nhập (không chứa khoảng trắng, tối thiểu 3 ký tự chữ/số/._-).
     */
    public FormValidator username(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                return false;
            }
            if (val.contains(" ")) {
                setError(til, input, fieldLabel + " không được chứa khoảng trắng");
                return false;
            }
            if (!USERNAME_PATTERN.matcher(val).matches()) {
                setError(til, input, fieldLabel + " tối thiểu 3 ký tự (chữ, số, '.', '_' hoặc '-')");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra định dạng địa chỉ Email hợp lệ.
     */
    public FormValidator email(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            if (!isValidEmail(val)) {
                setError(til, input, fieldLabel + " không đúng định dạng (VD: ten@gmail.com)");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra số điện thoại Việt Nam (bắt đầu bằng 0 hoặc +84, theo sau là 8-10 chữ số).
     */
    public FormValidator phone(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            if (!isValidPhone(val)) {
                setError(til, input, fieldLabel + " không hợp lệ (VD: 0912345678 hoặc +84912345678)");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra đường dẫn URL bắt đầu bằng http:// hoặc https:// (hỗ trợ cả IP nội bộ / localhost).
     */
    public FormValidator httpUrl(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            if (!isValidHttpUrl(val)) {
                setError(til, input, fieldLabel + " phải bắt đầu bằng http:// hoặc https://");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra giá trị số thực nằm trong khoảng [minValue, maxValue] (dùng cho giá bán, phần trăm giảm giá...).
     */
    public FormValidator doubleRange(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            double minValue,
            double maxValue,
            boolean required,
            @Nullable String customMessage
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            Double parsed = parseNumberOrNull(val);
            if (parsed == null) {
                setError(til, input, fieldLabel + " phải là số hợp lệ");
                return false;
            }
            if (parsed < minValue || parsed > maxValue) {
                String msg = (customMessage != null && !customMessage.isEmpty())
                        ? customMessage
                        : (minValue > 0
                        ? fieldLabel + " phải lớn hơn hoặc bằng " + formatBound(minValue)
                        : fieldLabel + " không hợp lệ");
                setError(til, input, msg);
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc bắt buộc giá trị số nhập vào phải lớn hơn 0 (dùng cho số tiền chi phí, đơn giá, số lượng nhập...).
     */
    public FormValidator greaterThanZero(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                return false;
            }
            Double parsed = parseNumberOrNull(val);
            if (parsed == null || parsed <= 0) {
                setError(til, input, fieldLabel + " phải lớn hơn 0");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra số nguyên nằm trong khoảng [minValue, maxValue] (dùng cho số lượng tồn kho, điểm thưởng...).
     */
    public FormValidator intRange(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            int minValue,
            int maxValue,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            Integer parsed = parseIntOrNull(val);
            if (parsed == null) {
                setError(til, input, fieldLabel + " phải là số nguyên hợp lệ");
                return false;
            }
            if (parsed < minValue || parsed > maxValue) {
                setError(til, input, fieldLabel + " phải từ " + minValue + " đến " + maxValue);
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra chuỗi ngày tháng theo đúng định dạng chuẩn ISO "yyyy-MM-dd".
     */
    public FormValidator isoDate(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel,
            boolean required
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                if (required) {
                    setError(til, input, "Vui lòng chọn " + fieldLabel.toLowerCase(Locale.ROOT));
                    return false;
                }
                return true;
            }
            if (!isValidIsoDate(val)) {
                setError(til, input, fieldLabel + " phải đúng định dạng yyyy-MM-dd");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra mã xác thực OTP (gồm từ 4 đến 8 chữ số).
     */
    public FormValidator otp(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull String fieldLabel
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String val = textOf(input);
            if (val.isEmpty()) {
                setError(til, input, "Vui lòng nhập " + fieldLabel.toLowerCase(Locale.ROOT));
                return false;
            }
            if (!OTP_PATTERN.matcher(val).matches()) {
                setError(til, input, fieldLabel + " gồm 4 đến 8 chữ số");
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc đối chiếu 2 ô nhập phải khớp nhau (VD: Xác nhận mật khẩu mới phải khớp với Mật khẩu mới).
     */
    public FormValidator matches(
            @Nullable TextInputLayout til,
            @Nullable EditText confirmInput,
            @Nullable EditText sourceInput,
            @NonNull String errorMessage
    ) {
        registerAutoClear(til, confirmInput);
        rules.add(() -> {
            if (!textOf(confirmInput).equals(textOf(sourceInput))) {
                setError(til, confirmInput, errorMessage);
                return false;
            }
            return true;
        });
        return this;
    }

    /**
     * Thêm quy tắc kiểm tra tùy chỉnh thông qua lambda `CustomCheck` (trả về chuỗi lỗi nếu vi phạm, hoặc null nếu hợp lệ).
     */
    public FormValidator custom(
            @Nullable TextInputLayout til,
            @Nullable EditText input,
            @NonNull CustomCheck check
    ) {
        registerAutoClear(til, input);
        rules.add(() -> {
            String error = check.validate(textOf(input));
            if (error != null && !error.trim().isEmpty()) {
                setError(til, input, error);
                return false;
            }
            return true;
        });
        return this;
    }

    public interface CustomCheck {
        @Nullable String validate(@NonNull String trimmedValue);
    }

    /**
     * Thực thi toàn bộ danh sách quy tắc đã đăng ký: xóa các lỗi cũ, chạy từng Rule,
     * tự động đưa con trỏ (requestFocus) tới ô bị lỗi đầu tiên và trả về true nếu tất cả đều hợp lệ.
     */
    public boolean validate() {
        firstInvalidView = null;
        for (TextInputLayout til : trackedTils) {
            if (til != null) til.setError(null);
        }
        for (EditText et : trackedInputs) {
            if (et != null) et.setError(null);
        }

        boolean allValid = true;
        for (Rule rule : rules) {
            if (!rule.check()) {
                allValid = false;
            }
        }
        if (!allValid && firstInvalidView != null) {
            firstInvalidView.requestFocus();
        }
        return allValid;
    }

    /**
     * Gắn TextWatcher một lần duy nhất vào EditText để tự động xóa thông báo lỗi đỏ ngay khi người dùng sửa nội dung.
     */
    private void registerAutoClear(@Nullable TextInputLayout til, @Nullable EditText input) {
        if (til != null && !trackedTils.contains(til)) {
            trackedTils.add(til);
        }
        if (input != null && !trackedInputs.contains(input)) {
            trackedInputs.add(input);
        }
        if (input == null || Boolean.TRUE.equals(input.getTag(TAG_WATCHER_ATTACHED))) {
            return;
        }
        input.setTag(TAG_WATCHER_ATTACHED, Boolean.TRUE);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (til != null && til.getError() != null) {
                    til.setError(null);
                }
                if (input.getError() != null) {
                    input.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Hiển thị chuỗi thông báo lỗi lên TextInputLayout (ưu tiên) hoặc EditText, và ghi nhận ô lỗi đầu tiên để focus.
     */
    private void setError(@Nullable TextInputLayout til, @Nullable EditText input, @NonNull String message) {
        if (til != null) {
            if (til.getError() == null) {
                til.setError(message);
            }
            if (firstInvalidView == null) {
                firstInvalidView = input != null ? input : til;
            }
        } else if (input != null) {
            if (input.getError() == null) {
                input.setError(message);
            }
            if (firstInvalidView == null) {
                firstInvalidView = input;
            }
        }
    }

    /**
     * Lấy chuỗi văn bản đã cắt khoảng trắng 2 đầu từ EditText an toàn (trả về "" nếu null).
     */
    @NonNull
    public static String textOf(@Nullable EditText input) {
        if (input == null || input.getText() == null) return "";
        return input.getText().toString().trim();
    }

    /**
     * Kiểm tra chuỗi truyền vào có khớp biểu thức chính quy Email hay không.
     */
    public static boolean isValidEmail(@Nullable String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Làm sạch các ký tự phân cách (dấu cách, dấu chấm, gạch ngang, ngoặc đơn) rồi kiểm tra định dạng SĐT Việt Nam.
     */
    public static boolean isValidPhone(@Nullable String phone) {
        if (phone == null) return false;
        String cleaned = phone.trim().replaceAll("[\\s.\\-()]", "");
        return PHONE_VN_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Chuẩn hóa số điện thoại bằng cách loại bỏ khoảng trắng và dấu phân cách trước khi gửi lên API.
     */
    @NonNull
    public static String normalizePhone(@Nullable String phone) {
        if (phone == null) return "";
        return phone.trim().replaceAll("[\\s.\\-()]", "");
    }

    /**
     * Kiểm tra chuỗi URL hợp lệ (chấp nhận cả domain công cộng lẫn các dải IP nội bộ 10.x, 127.x, 192.168.x, 172.x, localhost).
     */
    public static boolean isValidHttpUrl(@Nullable String url) {
        if (url == null) return false;
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false;
        }
        return android.util.Patterns.WEB_URL.matcher(url.trim()).matches()
                || trimmed.startsWith("http://10.")
                || trimmed.startsWith("https://10.")
                || trimmed.startsWith("http://127.")
                || trimmed.startsWith("https://127.")
                || trimmed.startsWith("http://192.168.")
                || trimmed.startsWith("https://192.168.")
                || trimmed.startsWith("http://172.")
                || trimmed.startsWith("https://172.")
                || trimmed.startsWith("https://localhost")
                || trimmed.startsWith("http://localhost");
    }

    /**
     * Kiểm tra tính hợp lệ nghiêm ngặt (lenient = false) của chuỗi ngày tháng theo mẫu "yyyy-MM-dd".
     */
    public static boolean isValidIsoDate(@Nullable String raw) {
        if (raw == null || raw.trim().length() != 10) return false;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setLenient(false);
        try {
            sdf.parse(raw.trim());
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Bóc tách số thực Double từ chuỗi tiền tệ đã định dạng (loại bỏ dấu phẩy, khoảng trắng, ký hiệu ₫/đ).
     * Trả về null nếu không phải số hợp lệ.
     */
    @Nullable
    public static Double parseNumberOrNull(@Nullable String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().replaceAll("[,\\s₫đ]", "");
        if (cleaned.isEmpty()) return null;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Bóc tách số nguyên Integer từ chuỗi có dấu phẩy phân cách hàng nghìn. Trả về null nếu lỗi định dạng.
     */
    @Nullable
    public static Integer parseIntOrNull(@Nullable String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().replaceAll("[,\\s]", "");
        if (cleaned.isEmpty()) return null;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Định dạng mốc giới hạn số (minValue/maxValue) có dấu phẩy phân cách để hiển thị trong câu thông báo lỗi.
     */
    private static String formatBound(double value) {
        if (value == (long) value) {
            return String.format(Locale.US, "%,d", (long) value);
        }
        return String.valueOf(value);
    }
}

