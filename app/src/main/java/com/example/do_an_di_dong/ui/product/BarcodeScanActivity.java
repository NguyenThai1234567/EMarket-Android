package com.example.do_an_di_dong.ui.product;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.do_an_di_dong.databinding.ActivityBarcodeScanBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Màn hình quét mã vạch thời gian thực bằng Camera (BarcodeScanActivity).
 * - Vai trò: Sử dụng CameraX kết hợp Google ML Kit Barcode Scanning để nhận diện mã vạch sản phẩm
 *   và trả chuỗi mã vạch ("scanned_barcode") về cho màn hình gọi (ProductEditorActivity, POS, Tìm kiếm...).
 * - Gắn với: Layout activity_barcode_scan.xml (ActivityBarcodeScanBinding).
 */
public class BarcodeScanActivity extends AppCompatActivity {
    private static final String TAG = "BarcodeScanActivity";
    private static final int PERMISSION_CAMERA_REQUEST = 1001;

    private ActivityBarcodeScanBinding binding;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private boolean isScanningActive = true;

    /**
     * Khởi tạo màn hình quét mã vạch, tạo luồng xử lý ảnh riêng (cameraExecutor),
     * khởi tạo ML Kit BarcodeScanner và kiểm tra quyền truy cập Camera.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBarcodeScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Kiểm tra ứng dụng đã được cấp quyền sử dụng Camera hay chưa.
     */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Hiển thị hộp thoại hệ thống xin quyền truy cập Camera từ người dùng.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST);
    }

    /**
     * Nhận kết quả phản hồi khi người dùng Cấp quyền hoặc Từ chối quyền Camera.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                com.example.do_an_di_dong.util.AppToast.show(this, "Quyền Camera là bắt buộc để quét mã vạch.", Toast.LENGTH_LONG);
                finish();
            }
        }
    }

    /**
     * Thiết lập CameraX: gắn luồng hiển thị Preview lên màn hình và gắn bộ phân tích khung hình (ImageAnalysis)
     * vào vòng đời của Activity.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                // Chỉ giữ khung hình mới nhất (STRATEGY_KEEP_ONLY_LATEST) để tránh nghẽn hàng đợi phân tích
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (!isScanningActive) {
                            imageProxy.close();
                            return;
                        }
                        processImageProxy(imageProxy);
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khởi tạo Camera: ", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Chuyển đổi mỗi khung hình Camera (ImageProxy) sang InputImage và đưa vào ML Kit để tìm mã vạch.
     * Bắt buộc đóng imageProxy khi hoàn tất để CameraX gửi khung hình tiếp theo.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            // Ngay khi đọc được mã vạch đầu tiên hợp lệ: khóa cờ isScanningActive để không bắn kết quả trùng
                            if (rawValue != null && isScanningActive) {
                                isScanningActive = false; // Stop further scans
                                handleBarcodeFound(rawValue);
                                break;
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Quét mã vạch lỗi: ", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    /**
     * Đóng gói chuỗi mã vạch quét được vào Intent kết quả (key "scanned_barcode") và đóng màn hình.
     */
    private void handleBarcodeFound(String barcode) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("scanned_barcode", barcode);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /**
     * Giải phóng tài nguyên luồng Camera và đóng bộ quét ML Kit khi Activity bị hủy để tránh rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanner.close();
    }
}
