package com.example.do_an_di_dong.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.do_an_di_dong.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Hộp thoại nhập số lượng tương tác cao (Interactive Quantity Stepper) gắn với layout {@code R.layout.dialog_quantity_stepper}.
 * Phục vụ cho các luồng nghiệp vụ POS bán hàng, Nhập kho, Kiểm kê và Điều chuyển kho khi cần điều chỉnh số lượng mặt hàng nhanh:
 * - Chạm trực tiếp vào ô số để gõ bàn phím số tùy ý.
 * - Bấm nhanh các chip cộng thêm (+1, +5, +10, +50, +100).
 * - Nhấn giữ nút + hoặc - (Long-press) để tự động tăng/giảm liên tục kèm kiểm tra giới hạn tồn kho ({@code min} / {@code max}).
 */
public final class QuantityInputDialog {

    public interface OnQuantitySetListener {
        void onQuantitySet(int quantity);
    }

    private QuantityInputDialog() {}

    /**
     * Mở hộp thoại chọn số lượng với cấu hình giới hạn mặc định (tối thiểu 1, tối đa 999.999, không hiển thị đơn vị tính).
     * Kích hoạt khi người dùng chạm vào ô số lượng của một dòng sản phẩm trong giỏ hàng hoặc phiếu kho.
     * Trả về số lượng chốt cuối cùng qua {@code listener}.
     */
    public static void show(
            Context context,
            String productName,
            int currentQuantity,
            OnQuantitySetListener listener
    ) {
        show(context, productName, currentQuantity, 1, 999999, null, listener);
    }

    /**
     * Mở hộp thoại chọn số lượng đầy đủ tham số, cho phép truyền giới hạn {@code min}, {@code max} (VD: giới hạn theo tồn kho khả dụng khi bán hàng)
     * và tên đơn vị tính {@code unit}.
     * Kích hoạt khi chạm vào số lượng sản phẩm trên Adapter/Activity; khi bấm Xác nhận sẽ kiểm tra biên an toàn và trả kết quả qua {@code listener}.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void show(
            Context context,
            String productName,
            int currentQuantity,
            final int min,
            final Integer max,
            String unit,
            final OnQuantitySetListener listener
    ) {
        if (context == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(context, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_quantity_stepper, null);
        dialog.setContentView(view);

        TextView tvProductName = view.findViewById(R.id.tvStepperProductName);
        EditText etValue = view.findViewById(R.id.etStepperValue);
        View btnMinus = view.findViewById(R.id.btnStepperMinus);
        View btnPlus = view.findViewById(R.id.btnStepperPlus);
        View btnClose = view.findViewById(R.id.btnCloseStepperDialog);
        View btnCancel = view.findViewById(R.id.btnStepperCancel);
        View btnConfirm = view.findViewById(R.id.btnStepperConfirm);
        TextView tvHelper = view.findViewById(R.id.tvStepperUnitHelper);

        View chipAdd1 = view.findViewById(R.id.chipAdd1);
        View chipAdd5 = view.findViewById(R.id.chipAdd5);
        View chipAdd10 = view.findViewById(R.id.chipAdd10);
        View chipAdd50 = view.findViewById(R.id.chipAdd50);
        View chipAdd100 = view.findViewById(R.id.chipAdd100);

        if (productName != null && !productName.isEmpty()) {
            tvProductName.setText(productName);
        } else {
            tvProductName.setText("Chọn số lượng mong muốn");
        }

        if (unit != null && !unit.isEmpty()) {
            tvHelper.setText("Đơn vị tính: " + unit + " • Nhấn giữ + / − để tăng giảm siêu tốc");
        } else {
            tvHelper.setText("Nhấn giữ + / − để tăng giảm siêu tốc hoặc gõ số trực tiếp");
        }

        final int[] qtyHolder = new int[]{Math.max(min, currentQuantity)};
        etValue.setText(String.valueOf(qtyHolder[0]));
        etValue.selectAll();

        // Kẹp giá trị (clamp) số lượng nằm trong ngưỡng [min, max] (đảm bảo không âm và không vượt tồn kho tối đa) rồi cập nhật lên EditText
        final Runnable[] updateDisplay = new Runnable[1];
        updateDisplay[0] = () -> {
            if (qtyHolder[0] < min) qtyHolder[0] = min;
            if (max != null && qtyHolder[0] > max) qtyHolder[0] = max;
            etValue.setText(String.valueOf(qtyHolder[0]));
            etValue.setSelection(etValue.getText().length());
        };

        etValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s == null || s.toString().trim().isEmpty()) return;
                try {
                    int parsed = Integer.parseInt(s.toString().trim());
                    qtyHolder[0] = parsed;
                } catch (Exception ignored) {}
            }
        });

        // Quick add chips
        chipAdd1.setOnClickListener(v -> {
            qtyHolder[0] += 1;
            updateDisplay[0].run();
        });
        chipAdd5.setOnClickListener(v -> {
            qtyHolder[0] += 5;
            updateDisplay[0].run();
        });
        chipAdd10.setOnClickListener(v -> {
            qtyHolder[0] += 10;
            updateDisplay[0].run();
        });
        chipAdd50.setOnClickListener(v -> {
            qtyHolder[0] += 50;
            updateDisplay[0].run();
        });
        chipAdd100.setOnClickListener(v -> {
            qtyHolder[0] += 100;
            updateDisplay[0].run();
        });

        // Stepper click & long press repeat
        setupRepeatButton(btnPlus, 1, qtyHolder, min, max, etValue);
        setupRepeatButton(btnMinus, -1, qtyHolder, min, max, etValue);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String raw = etValue.getText() != null ? etValue.getText().toString().trim() : "";
            int finalQty = qtyHolder[0];
            try {
                if (!raw.isEmpty()) finalQty = Integer.parseInt(raw);
            } catch (Exception ignored) {}

            // Ràng buộc biên lần cuối trước khi trả số lượng về màn hình nghiệp vụ (tránh trường hợp người dùng gõ tay số vượt tồn kho max)
            if (finalQty < min) finalQty = min;
            if (max != null && finalQty > max) finalQty = max;

            dialog.dismiss();
            if (listener != null) {
                listener.onQuantitySet(finalQty);
            }
        });

        dialog.show();
    }

    /**
     * Cấu hình nút bấm tăng/giảm (+/-) hỗ trợ cả chạm đơn (Click) lẫn nhấn giữ liên tục (Long-press).
     * Khi người dùng nhấn giữ nút, sử dụng {@link Handler} lặp lại mỗi 60ms để cộng/trừ một lượng {@code delta} (+1 hoặc -1),
     * tự động dừng lặp khi người dùng nhấc tay khỏi màn hình ({@code ACTION_UP} / {@code ACTION_CANCEL}).
     */
    @SuppressLint("ClickableViewAccessibility")
    private static void setupRepeatButton(
            View button,
            final int delta,
            final int[] qtyHolder,
            final int min,
            final Integer max,
            final EditText etValue
    ) {
        final Handler handler = new Handler(Looper.getMainLooper());
        // Chu kỳ lặp 60ms/lần khi nhấn giữ để tăng/giảm số lượng siêu tốc trên Main Thread
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                qtyHolder[0] += delta;
                if (qtyHolder[0] < min) qtyHolder[0] = min;
                if (max != null && qtyHolder[0] > max) qtyHolder[0] = max;
                etValue.setText(String.valueOf(qtyHolder[0]));
                etValue.setSelection(etValue.getText().length());
                handler.postDelayed(this, 60);
            }
        };

        button.setOnClickListener(v -> {
            qtyHolder[0] += delta;
            if (qtyHolder[0] < min) qtyHolder[0] = min;
            if (max != null && qtyHolder[0] > max) qtyHolder[0] = max;
            etValue.setText(String.valueOf(qtyHolder[0]));
            etValue.setSelection(etValue.getText().length());
        });

        button.setOnLongClickListener(v -> {
            handler.post(runnable);
            return true;
        });

        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(runnable);
            }
            return false;
        });
    }
}
