package com.example.do_an_di_dong.ui.ai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.do_an_di_dong.R;
import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.databinding.ActivityAiAssistantBinding;
import com.example.do_an_di_dong.model.Anomaly;
import com.example.do_an_di_dong.model.ChatMessage;
import com.example.do_an_di_dong.model.Insight;
import com.example.do_an_di_dong.model.InventoryForecast;
import com.example.do_an_di_dong.model.LotFinancialRisk;
import com.example.do_an_di_dong.model.ReplenishmentAdvice;
import com.example.do_an_di_dong.ui.chat.ChatAdapter;
import com.example.do_an_di_dong.ui.chat.ChatViewModel;
import com.example.do_an_di_dong.util.UserSessionManager;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Trợ lý AI & Dự báo Thông minh (Hợp nhất 3 Tab - 0 Fragment).
 * Thay thế hoàn toàn FloatingChatManager, ChatFragment, AIAdvisorHubActivity và AIPredictionDashboardActivity.
 * - Layout: activity_ai_assistant.xml (ActivityAiAssistantBinding)
 * - Tương tác dữ liệu: ChatViewModel (Tab Chat AI) và DataRepository (Tab Cố vấn & Dự báo Prophet)
 * - Adapters: ChatAdapter, ReplenishmentAdapter, AnomalyAdapter, InsightAdapter, LotRiskAdapter, InventoryForecastAdapter
 */
public class AIAssistantActivity extends AppCompatActivity {
    private static final int PERMISSION_RECORD_AUDIO_REQUEST = 2001;

    private ActivityAiAssistantBinding binding;
    private DataRepository repository;
    private ChatViewModel chatViewModel;
    private ChatAdapter chatAdapter;

    private ReplenishmentAdapter replenishmentAdapter;
    private AnomalyAdapter anomalyAdapter;
    private InsightAdapter insightAdapter;

    private LotRiskAdapter lotRiskAdapter;
    private InventoryForecastAdapter forecastAdapter;

    private List<ReplenishmentAdvice> cachedAdvice = new ArrayList<>();
    private List<Anomaly> cachedAnomalies = new ArrayList<>();
    private List<Insight> cachedInsights = new ArrayList<>();

    private List<LotFinancialRisk> cachedLotRisks = new ArrayList<>();
    private List<InventoryForecast> cachedForecasts = new ArrayList<>();

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private String userName = "Manager";

    /**
     * Khởi tạo màn hình Trợ lý AI, nạp thông tin phiên người dùng (chi nhánh, username),
     * thiết lập 3 tab chức năng (Chat, Cố vấn, Dự báo) và gọi API tải dữ liệu phân tích ban đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new DataRepository(this);
        String loginUser = UserSessionManager.getUsername(this);
        userName = (loginUser != null && !loginUser.isEmpty()) ? loginUser : "admin";

        binding.btnBack.setOnClickListener(v -> finish());
        // Kích hoạt chạy phân tích AI toàn diện trên Backend cho chi nhánh hiện tại rồi tải lại dữ liệu
        binding.btnRefreshAI.setOnClickListener(v -> {
            int bId = UserSessionManager.getCurrentBranchId(this);
            if (bId <= 0) bId = 1;
            com.example.do_an_di_dong.ui.common.AppToast.show(this, "Đang kích hoạt phân tích AI toàn diện...", com.example.do_an_di_dong.ui.common.AppToast.Type.INFO);
            repository.runAiAnalysis(bId, "system", new DataRepository.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    loadAdvisorData();
                    loadPredictionData();
                    com.example.do_an_di_dong.ui.common.AppToast.show(AIAssistantActivity.this, "Đã cập nhật dữ liệu phân tích AI mới nhất!", com.example.do_an_di_dong.ui.common.AppToast.Type.SUCCESS);
                }

                @Override
                public void onError(String message) {
                    loadAdvisorData();
                    loadPredictionData();
                }
            });
        });

        setupTabs();
        setupChatTab();
        setupAdvisorTab();
        setupPredictionTab();

        loadAdvisorData();
        loadPredictionData();
    }

    /**
     * Thiết lập sự kiện chuyển đổi giữa 3 Tab (0: Chat AI, 1: Cố vấn nhập hàng/bất thường, 2: Dự báo rủi ro/tồn kho)
     * bằng cách bật/tắt visibility của các khối layout tương ứng (không dùng Fragment để tối ưu hiệu năng).
     */
    private void setupTabs() {
        binding.tabLayoutAI.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                binding.layoutTabChat.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
                binding.layoutTabAdvisor.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                binding.layoutTabPrediction.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /**
     * Khởi tạo Tab Chat AI: cấu hình RecyclerView tin nhắn, lắng nghe LiveData phản hồi/lỗi từ ChatViewModel,
     * gắn sự kiện cho các Chip gợi ý câu hỏi nhanh và nút nhận diện giọng nói (Speech-to-Text).
     */
    private void setupChatTab() {
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        chatAdapter = new ChatAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(chatAdapter);

        chatAdapter.addMessage(new ChatMessage(
                "Xin chào " + userName + "! Tôi là Trợ lý AI phân tích EMarket ERP. Bạn cần tra cứu tồn kho, doanh thu hay rủi ro lô hàng hôm nay?",
                false
        ));

        // Lắng nghe câu trả lời từ AI Backend, lọc sạch chuỗi lỗi kỹ thuật (nếu có) trước khi render lên khung chat
        chatViewModel.getResponse().observe(this, chatResponse -> {
            binding.layoutThinking.setVisibility(View.GONE);
            if (chatResponse != null && chatResponse.getAnswer() != null) {
                String cleanAnswer = com.example.do_an_di_dong.util.ApiErrorFormatter.filterAiAnswerText(
                        chatResponse.getAnswer());
                chatAdapter.addMessage(new ChatMessage(cleanAnswer, false));
                binding.rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });

        // Xử lý fallback khi API AI gặp lỗi kết nối hoặc timeout: chuyển đổi sang thông báo thân thiện cho người dùng
        chatViewModel.getError().observe(this, msg -> {
            if (msg != null) {
                binding.layoutThinking.setVisibility(View.GONE);
                String friendly = com.example.do_an_di_dong.util.ApiErrorFormatter.sanitizeBusinessMessage(
                        msg, "Trợ lý AI tạm thời chưa phản hồi. Vui lòng thử lại sau.");
                chatAdapter.addMessage(new ChatMessage("⚠️ " + friendly, false));
                binding.rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                com.example.do_an_di_dong.util.AppToast.show(
                        this, friendly, com.example.do_an_di_dong.util.AppToast.Type.ERROR);
            }
        });

        binding.btnSend.setOnClickListener(v -> sendMessage());

        binding.chipPromptStock.setOnClickListener(v -> {
            binding.etMessage.setText("Báo cáo danh sách hàng sắp hết kho và đề xuất số lượng nhập?");
            sendMessage();
        });

        binding.chipPromptTopSales.setOnClickListener(v -> {
            binding.etMessage.setText("Phân tích top 5 sản phẩm có doanh thu và biên lợi nhuận cao nhất tuần này?");
            sendMessage();
        });

        binding.chipPromptExpiry.setOnClickListener(v -> {
            binding.etMessage.setText("Cảnh báo các lô hàng cận hạn sử dụng có nguy cơ thiệt hại vốn?");
            sendMessage();
        });

        setupSpeechToText();
        binding.btnVoiceMic.setOnClickListener(v -> startVoiceRecognition());
    }

    /**
     * Lấy nội dung câu hỏi từ ô nhập, đẩy tin nhắn của User vào ChatAdapter và gọi ChatViewModel gửi request lên AI Server.
     * Kích hoạt khi bấm nút Gửi hoặc chọn một Chip câu hỏi gợi ý.
     */
    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        chatAdapter.addMessage(new ChatMessage(text, true));
        binding.etMessage.setText("");
        binding.rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        binding.layoutThinking.setVisibility(View.VISIBLE);
        chatViewModel.sendMessage(text, userName);
    }

    /**
     * Khởi tạo Tab Cố vấn AI (Advisor): cấu hình RecyclerView dùng chung và sự kiện chuyển đổi Adapter
     * theo Chip được chọn (Đề xuất nhập hàng / Bất thường doanh số / Xu hướng tiêu thụ).
     */
    private void setupAdvisorTab() {
        replenishmentAdapter = new ReplenishmentAdapter();
        anomalyAdapter = new AnomalyAdapter();
        insightAdapter = new InsightAdapter();

        binding.rvAdvisorList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAdvisorList.setAdapter(replenishmentAdapter);

        // Chuyển đổi Adapter và nạp dữ liệu từ bộ nhớ đệm (cache) tương ứng với Chip phân loại
        binding.chipGroupAdvisor.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipAnomalies) {
                binding.rvAdvisorList.setAdapter(anomalyAdapter);
                anomalyAdapter.setList(cachedAnomalies);
            } else if (checkedId == R.id.chipInsights) {
                binding.rvAdvisorList.setAdapter(insightAdapter);
                insightAdapter.setList(cachedInsights);
            } else {
                binding.rvAdvisorList.setAdapter(replenishmentAdapter);
                replenishmentAdapter.setList(cachedAdvice);
            }
        });
    }

    /**
     * Gọi song song 3 API qua DataRepository để tải dữ liệu cho Tab Cố vấn theo branchId hiện tại:
     * đề xuất nhập hàng (Replenishment), bất thường doanh số (Anomalies), và phân tích xu hướng (Smart Insights).
     */
    private void loadAdvisorData() {
        int branchId = UserSessionManager.getCurrentBranchId(this);
        if (branchId <= 0) branchId = 1;

        repository.getReplenishmentAdvice(branchId, new DataRepository.ApiCallback<List<ReplenishmentAdvice>>() {
            @Override
            public void onSuccess(List<ReplenishmentAdvice> result) {
                cachedAdvice = result != null ? result : new ArrayList<>();
                if (binding.chipReplenishment.isChecked()) {
                    replenishmentAdapter.setList(cachedAdvice);
                }
            }
            @Override public void onError(String message) {}
        });

        repository.getSalesAnomalies(branchId, new DataRepository.ApiCallback<List<Anomaly>>() {
            @Override
            public void onSuccess(List<Anomaly> result) {
                cachedAnomalies = result != null ? result : new ArrayList<>();
                if (binding.chipAnomalies.isChecked()) {
                    anomalyAdapter.setList(cachedAnomalies);
                }
            }
            @Override public void onError(String message) {}
        });

        repository.getSmartInsights(branchId, new DataRepository.ApiCallback<List<Insight>>() {
            @Override
            public void onSuccess(List<Insight> result) {
                cachedInsights = result != null ? result : new ArrayList<>();
                if (binding.chipInsights.isChecked()) {
                    insightAdapter.setList(cachedInsights);
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Khởi tạo Tab Dự báo (Prediction): gắn Adapter cho danh sách Rủi ro lô hàng / Dự báo tồn kho 30 ngày,
     * và thiết lập hộp thoại xác nhận trước khi kích hoạt huấn luyện lại mô hình chuỗi thời gian AI Prophet.
     */
    private void setupPredictionTab() {
        lotRiskAdapter = new LotRiskAdapter();
        forecastAdapter = new InventoryForecastAdapter();

        binding.rvPredictions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPredictions.setAdapter(lotRiskAdapter);

        binding.chipGroupPrediction.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipForecast) {
                binding.rvPredictions.setAdapter(forecastAdapter);
                forecastAdapter.setList(cachedForecasts);
            } else {
                binding.rvPredictions.setAdapter(lotRiskAdapter);
                lotRiskAdapter.setList(cachedLotRisks);
            }
        });

        binding.btnRunProphet.setOnClickListener(v ->
                com.example.do_an_di_dong.ui.common.ModernConfirmSheet.confirmPrimary(
                        this,
                        "Kích hoạt huấn luyện AI Prophet",
                        "Chạy lại mô hình chuỗi thời gian Prophet trên toàn bộ dữ liệu giao dịch? Quá trình có thể mất vài giây.",
                        "Bắt đầu chạy",
                        this::triggerProphetTraining
                )
        );
    }

    /**
     * Gọi API qua DataRepository tải danh sách phân tích rủi ro tài chính theo lô hàng (LotRisk)
     * và dự báo tồn kho 30 ngày tới (InventoryForecast) cho chi nhánh hiện tại.
     */
    private void loadPredictionData() {
        int branchId = UserSessionManager.getCurrentBranchId(this);
        if (branchId <= 0) branchId = 1;

        repository.getLotRiskAnalysis(branchId, new DataRepository.ApiCallback<List<LotFinancialRisk>>() {
            @Override
            public void onSuccess(List<LotFinancialRisk> result) {
                cachedLotRisks = result != null ? result : new ArrayList<>();
                if (binding.chipLotRisk.isChecked()) {
                    lotRiskAdapter.setList(cachedLotRisks);
                }
            }
            @Override public void onError(String message) {}
        });

        repository.getInventoryForecast(branchId, new DataRepository.ApiCallback<List<InventoryForecast>>() {
            @Override
            public void onSuccess(List<InventoryForecast> result) {
                cachedForecasts = result != null ? result : new ArrayList<>();
                if (binding.chipForecast.isChecked()) {
                    forecastAdapter.setList(cachedForecasts);
                }
            }
            @Override public void onError(String message) {}
        });
    }

    /**
     * Gửi yêu cầu lên Backend kích hoạt tiến trình huấn luyện mô hình dự báo chuỗi thời gian Prophet.
     * Khi hoàn tất sẽ tự động tải lại dữ liệu dự báo mới nhất lên màn hình.
     */
    private void triggerProphetTraining() {
        binding.progressBarAI.setVisibility(View.VISIBLE);
        repository.runProphetPredictions(new DataRepository.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                binding.progressBarAI.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(AIAssistantActivity.this, "Huấn luyện mô hình AI Prophet hoàn tất!", Toast.LENGTH_LONG);
                loadPredictionData();
            }

            @Override
            public void onError(String message) {
                binding.progressBarAI.setVisibility(View.GONE);
                com.example.do_an_di_dong.util.AppToast.show(AIAssistantActivity.this, "Lỗi chạy Prophet: " + message, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Cấu hình bộ nhận diện giọng nói tiếng Việt (vi-VN) của Android.
     * Khi nhận diện thành công, văn bản đầu tiên khớp nhất sẽ được điền vào ô nhập tin nhắn chat.
     */
    private void setupSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                com.example.do_an_di_dong.util.AppToast.show(AIAssistantActivity.this, "Đang lắng nghe...", Toast.LENGTH_SHORT);
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                com.example.do_an_di_dong.util.AppToast.show(AIAssistantActivity.this, "Không nhận diện được giọng nói.", Toast.LENGTH_SHORT);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && binding != null) {
                    binding.etMessage.setText(matches.get(0));
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    /**
     * Kiểm tra quyền ghi âm (RECORD_AUDIO) và bắt đầu thu giọng nói khi người dùng bấm nút micro.
     * Nếu chưa có quyền sẽ bật popup xin quyền hệ thống.
     */
    private void startVoiceRecognition() {
        if (speechRecognizer == null) {
            com.example.do_an_di_dong.util.AppToast.show(this, "Thiết bị không hỗ trợ nhận dạng giọng nói.", Toast.LENGTH_SHORT);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechRecognizer.startListening(speechRecognizerIntent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO_REQUEST);
        }
    }

    /**
     * Callback nhận kết quả cấp quyền từ hệ thống: nếu người dùng đồng ý cấp quyền RECORD_AUDIO thì bắt đầu thu âm ngay.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_RECORD_AUDIO_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            speechRecognizer.startListening(speechRecognizerIntent);
        }
    }

    /**
     * Giải phóng tài nguyên SpeechRecognizer khi đóng Activity để tránh rò rỉ bộ nhớ (memory leak).
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}

