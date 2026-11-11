package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ForwardWorker
import com.example.InternetWatchService
import com.example.data.AppDatabase
import com.example.data.GatewaySettings
import com.example.data.LogEntry
import com.example.data.LogStatus
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class TestConnectionUiState {
    data object Idle : TestConnectionUiState()
    data object Loading : TestConnectionUiState()
    data class Success(val httpCode: Int, val status: String) : TestConnectionUiState()
    data class Error(val httpCode: Int?, val message: String) : TestConnectionUiState()
}

class AsisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val logDao = AppDatabase.getInstance(appContext).logDao()
    private val settingsRepo = SettingsRepo.getInstance(appContext)

    val settingsState: StateFlow<GatewaySettings> = settingsRepo.settingsFlow
    val isOnlineState: StateFlow<Boolean> = InternetWatchService.isOnlineFlow

    val allLogs: StateFlow<List<LogEntry>> = logDao.observeAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueCount: StateFlow<Int> = logDao.observeQueueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentCount: StateFlow<Int> = logDao.observeSentCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = logDao.observeFailedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSentLog: StateFlow<LogEntry?> = logDao.observeLastSentLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _testConnectionState = MutableStateFlow<TestConnectionUiState>(TestConnectionUiState.Idle)
    val testConnectionState: StateFlow<TestConnectionUiState> = _testConnectionState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        InternetWatchService.register(appContext)
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun setServiceEnabled(enabled: Boolean) {
        settingsRepo.setServiceEnabled(enabled)
        if (enabled) {
            ForwardWorker.enqueuePendingAll(appContext)
            _snackbarMessage.value = "سرویس فورواردر پیامک فعال شد."
        } else {
            _snackbarMessage.value = "سرویس فورواردر پیامک موقتاً متوقف شد."
        }
    }

    fun saveSettings(endpointUrl: String, token: String) {
        val validationError = GatewayApiClient.validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            _snackbarMessage.value = validationError
            return
        }
        settingsRepo.saveEndpointAndToken(endpointUrl, token)
        _snackbarMessage.value = "تنظیمات با رمزنگاری AES-256 ذخیره شد."
        // Flush any pending messages now that settings are updated
        ForwardWorker.enqueuePendingAll(appContext)
    }

    fun runTestConnection(endpointUrl: String, token: String) {
        val validationError = GatewayApiClient.validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            _testConnectionState.value = TestConnectionUiState.Error(null, validationError)
            return
        }
        // Save valid credentials first so user doesn't lose them
        settingsRepo.saveEndpointAndToken(endpointUrl, token)
        _testConnectionState.value = TestConnectionUiState.Loading

        viewModelScope.launch {
            when (val result = GatewayApiClient.testConnection(endpointUrl, token)) {
                is GatewayResult.Success -> {
                    _testConnectionState.value = TestConnectionUiState.Success(
                        httpCode = result.httpCode,
                        status = result.status
                    )
                    // Also record in logs for transparency
                    val now = System.currentTimeMillis()
                    val parsed = SmsParser.parse(SmsParser.TEST_MARK, sender = "APP_TEST", explicitBankCode = "test")
                    logDao.insertLog(
                        LogEntry(
                            sender = "تست اتصال",
                            bankCode = "test",
                            bankTitle = "تست اتصال درگاه",
                            body = SmsParser.TEST_MARK,
                            amountRial = null,
                            parseReason = "test",
                            fingerprint = parsed.fingerprint,
                            status = LogStatus.SENT.name,
                            httpCode = result.httpCode,
                            serverStatus = result.status,
                            approvedCount = result.approved,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                is GatewayResult.PermanentFailure -> {
                    _testConnectionState.value = TestConnectionUiState.Error(
                        httpCode = result.httpCode,
                        message = result.message
                    )
                }

                is GatewayResult.RetryableFailure -> {
                    _testConnectionState.value = TestConnectionUiState.Error(
                        httpCode = result.httpCode,
                        message = result.message
                    )
                }
            }
        }
    }

    fun resetTestConnectionState() {
        _testConnectionState.value = TestConnectionUiState.Idle
    }

    /**
     * Allows user to simulate an incoming bank SMS directly from the app to verify
     * parsing, queueing, and server delivery (`sms_deposits`) per the ROADMAP checklist.
     */
    fun enqueueSimulatedBankSms(sender: String, body: String) {
        val trimmed = body.trim().take(SmsParser.MAX_SMS_LENGTH)
        if (trimmed.isEmpty()) {
            _snackbarMessage.value = "متن پیامک تستی نمی‌تواند خالی باشد."
            return
        }
        viewModelScope.launch {
            val parsed = SmsParser.parse(rawText = trimmed, sender = sender)
            val now = System.currentTimeMillis()
            val entry = LogEntry(
                sender = sender.ifBlank { "98200033" },
                bankCode = parsed.bankCode,
                bankTitle = parsed.bankTitleFa,
                body = trimmed,
                amountRial = parsed.amountMinor,
                parseReason = parsed.reason,
                fingerprint = parsed.fingerprint,
                status = LogStatus.QUEUED.name,
                createdAt = now,
                updatedAt = now
            )
            val logId = logDao.insertLog(entry)
            ForwardWorker.enqueueForLog(appContext, logId)
            _snackbarMessage.value = "پیامک در صف قرار گرفت و ارسال به سرور آغاز شد."
        }
    }

    fun retrySingleLog(entry: LogEntry) {
        viewModelScope.launch {
            val updated = entry.copy(
                status = LogStatus.QUEUED.name,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            logDao.updateLog(updated)
            ForwardWorker.enqueueForLog(appContext, entry.id)
            _snackbarMessage.value = "تلاش مجدد برای ارسال پیامک آغاز شد."
        }
    }

    fun flushQueueNow() {
        ForwardWorker.enqueuePendingAll(appContext)
        _snackbarMessage.value = "صف ارسال بیدار شد؛ در حال ارسال پیامک‌های در انتظار..."
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logDao.clearAllLogs()
            _snackbarMessage.value = "تمام لاگ‌ها پاک شدند."
        }
    }

    fun setFilterBanksEnabled(enabled: Boolean) {
        settingsRepo.setFilterBanksEnabled(enabled)
    }

    fun toggleBankSelection(bankCode: String) {
        settingsRepo.toggleBankSelection(bankCode)
    }

    fun selectAllBanks(selectAll: Boolean) {
        settingsRepo.selectAllBanks(selectAll)
    }
}
