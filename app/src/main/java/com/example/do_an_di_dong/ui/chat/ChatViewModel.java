package com.example.do_an_di_dong.ui.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.do_an_di_dong.data.repository.DataRepository;
import com.example.do_an_di_dong.model.ChatResponse;

/**
 * ViewModel quản lý trạng thái và luồng gọi API hỏi đáp với Trợ lý AI (Tab Chat trong AIAssistantActivity).
 * Kết nối với DataRepository.askAi() và phát kết quả qua LiveData (response, error, loading) để UI tự động cập nhật.
 */
public class ChatViewModel extends AndroidViewModel {
    private final DataRepository repository;
    private final MutableLiveData<ChatResponse> response = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();

    public ChatViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DataRepository(application);
    }

    public LiveData<ChatResponse> getResponse() {
        return response;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    /**
     * Gửi câu hỏi (prompt) kèm username của người dùng hiện tại lên AI Backend thông qua DataRepository.
     * Được gọi khi người dùng bấm nút Gửi hoặc chọn Chip câu hỏi gợi ý ở AIAssistantActivity.
     */
    public void sendMessage(String prompt, String username) {
        loading.setValue(true);
        // Gọi bất đồng bộ API /ai/ask và đẩy kết quả hoặc thông báo lỗi về MainThread qua postValue()
        repository.askAi(prompt, username, new DataRepository.ApiCallback<ChatResponse>() {
            @Override
            public void onSuccess(ChatResponse result) {
                loading.postValue(false);
                response.postValue(result);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }
}
