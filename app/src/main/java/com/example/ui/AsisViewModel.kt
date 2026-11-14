package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ForwardWorker
import com.example.InternetWatchService
import com.example.data.AppDatabase
import com.example.data.GatewaySettings
import com.example.data.Item
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import com.example.network.PendingFetchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class TestConnectionUiState {
    data object Idle : TestConnectionUiState()
    data object Loading : TestConnectionUiState()
    data class SuccessSmsIn(val httpCode: Int, val status: String) : TestConnectionUiState()
    data class SuccessPending(
        val itemsCount: Int,
        val clockOffsetMs: Long,
        val matchWindowHours: Int,
        val autoMaxToman: Long,
        val sampleItems: List<Item>
    ) : TestConnectionUiState()
    data class Error(val httpCode: Int?, val message: String) : TestConnectionUiState()
}

class AsisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext ?: application
    private val logDao = AppDatabase.getInstance(appContext).logDao()
    private val settingsRepo = SettingsRepo.getInstance(appContext)

    val settingsState: StateFlow<GatewaySettings> = settingsRepo.settingsFlow
    val isOnlineState: StateFlow<Boolean> = InternetWatchService.isOnlineFlow

    val allLogs: StateFlow<List<SmsRecord>> = logDao.observeAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueCount: StateFlow<Int> = logDao.observeQueueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentCount: StateFlow<Int> = logDao.observeMatchedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = logDao.observeActionNeededCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSentLog: StateFlow<SmsRecord?> = logDao.observeLastMatchedLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _testConnectionState = MutableStateFlow<TestConnectionUiState>(TestConnectionUiState.Idle)
    val testConnectionState: StateFlow<TestConnectionUiState> = _testConnectionState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        runCatching { InternetWatchService.register(appContext) }
        // Periodic 30-second matcher loop per Section 1 of APP_SIDE_APPROVAL_SPEC.md
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                runCatching {
                    val current = settingsRepo.readSettings()
                    if (current.serviceEnabled && current.isConfigured) {
                        ForwardWorker.runSweepNow(appContext)
                    }
                }
            }
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun setServiceEnabled(enabled: Boolean) {
        settingsRepo.setServiceEnabled(enabled)
        if (enabled) {
            viewModelScope.launch { ForwardWorker.runSweepNow(appContext) }
            _snackbarMessage.value = "سرویس تطبیق و تأیید پیامک فعال شد."
        } else {
            _snackbarMessage.value = "سرویس موقتاً متوقف شد."
        }
    }

    fun setAutoApproveInApp(enabled: Boolean) {
        settingsRepo.setAutoApproveInApp(enabled)
        if (enabled) {
            viewModelScope.launch { ForwardWorker.runSweepNow(appContext) }
            _snackbarMessage.value = "تأیید خودکار در اپ فعال شد."
        } else {
            _snackbarMessage.value = "تأیید خودکار خاموش شد (فقط تطبیق و اعلان برای تأیید دستی)."
        }
    }

    fun saveSettings(endpointUrl: String, token: String, localMaxTomanStr: String) {
        val validationError = GatewayApiClient.validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            _snackbarMessage.value = validationError
            return
        }
        val maxToman = localMaxTomanStr.trim().replace(",", "").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        settingsRepo.saveEndpointAndToken(endpointUrl, token, maxToman)
        _snackbarMessage.value = "تنظیمات با رمزنگاری AES-256 ذخیره شد."
        viewModelScope.launch { ForwardWorker.runSweepNow(appContext) }
    }

    fun runTestConnection(endpointUrl: String, token: String) {
        val validationError = GatewayApiClient.validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            _testConnectionState.value = TestConnectionUiState.Error(null, validationError)
            return
        }
        settingsRepo.saveEndpointAndToken(endpointUrl, token)
        _testConnectionState.value = TestConnectionUiState.Loading

        viewModelScope.launch {
            when (val result = GatewayApiClient.testConnection(endpointUrl, token)) {
                is GatewayResult.Success -> {
                    _testConnectionState.value = TestConnectionUiState.SuccessSmsIn(
                        httpCode = result.httpCode,
                        status = result.status
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

    /**
     * Second test button per Section 7: tests `GET /sms-pending` and displays clock offset & waiting orders.
     */
    fun runTestPendingEndpoint(endpointUrl: String, token: String) {
        val validationError = GatewayApiClient.validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            _testConnectionState.value = TestConnectionUiState.Error(null, validationError)
            return
        }
        settingsRepo.saveEndpointAndToken(endpointUrl, token)
        _testConnectionState.value = TestConnectionUiState.Loading

        viewModelScope.launch {
            when (val res = GatewayApiClient.fetchPendingItems(endpointUrl, token)) {
                is PendingFetchResult.Success -> {
                    settingsRepo.updateServerSyncMeta(
                        clockOffsetMs = res.clockOffsetMs,
                        matchWindowHours = res.matchWindowHours,
                        autoMaxToman = res.autoMaxToman
                    )
                    _testConnectionState.value = TestConnectionUiState.SuccessPending(
                        itemsCount = res.items.size,
                        clockOffsetMs = res.clockOffsetMs,
                        matchWindowHours = res.matchWindowHours,
                        autoMaxToman = res.autoMaxToman,
                        sampleItems = res.items.take(5)
                    )
                }
                is PendingFetchResult.Unauthorized401 -> {
                    _testConnectionState.value = TestConnectionUiState.Error(401, res.message)
                }
                is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                    _testConnectionState.value = TestConnectionUiState.Error(404, res.message)
                }
                is PendingFetchResult.PermanentError -> {
                    _testConnectionState.value = TestConnectionUiState.Error(res.httpCode, res.message)
                }
                is PendingFetchResult.RetryableError -> {
                    _testConnectionState.value = TestConnectionUiState.Error(res.httpCode, res.message)
                }
            }
        }
    }

    fun resetTestConnectionState() {
        _testConnectionState.value = TestConnectionUiState.Idle
    }

    /**
     * Simulates an incoming bank SMS through the exact Section 1 & 2 pipeline:
     * preserves raw body, computes fingerprint, deduplicates, parses deposit amount,
     * sets `NEW` or `IGNORED`, and triggers `MatcherWorker`.
     */
    fun enqueueSimulatedBankSms(sender: String, rawBody: String) {
        if (rawBody.isBlank()) {
            _snackbarMessage.value = "متن پیامک تستی نمی‌تواند خالی باشد."
            return
        }
        viewModelScope.launch {
            val bank = sender.ifBlank { "98200033" }.take(32)
            val now = System.currentTimeMillis()
            val normalized = SmsParser.normalize(rawBody)
            val fp = SmsParser.fingerprint(bank, rawBody)

            if (logDao.getByFingerprint(fp) != null) {
                _snackbarMessage.value = "این پیامک تکراری است (Fingerprint قبلاً در Room ثبت شده است)."
                return@launch
            }

            val parsed = SmsParser.parse(rawBody)
            val detected = SmsParser.detectBank(bank, rawBody)
            // a simulated SMS is only parsed and shown: it must never be sent to the server (it could approve a real order)
            val initialStatus = SmsStatus.IGNORED

            val record = SmsRecord(
                fingerprint = fp,
                bank = bank,
                bankTitle = detected.titleFa,
                body = rawBody,
                normalized = normalized,
                amountMinor = parsed.amountMinor,
                reason = if (parsed.amountMinor != null) "test_deposit_not_sent" else parsed.reason,
                status = initialStatus.name,
                receivedMs = now,
                storedMs = now,
                decidedMs = now
            )

            val id = logDao.insertIgnoreDuplicate(record)
            if (id == -1L) {
                _snackbarMessage.value = "پیامک تکراری نادیده گرفته شد."
            } else if (parsed.amountMinor == null) {
                _snackbarMessage.value = "پیامک غیر واریزی تشخیص داده شد (reason=${parsed.reason})."
            } else {
                _snackbarMessage.value = "پارس شد: ${SmsParser.formatToman(parsed.amountMinor)} تومان (تست؛ به سرور ارسال نمی‌شود)."
            }
        }
    }

    /**
     * One-tap manual approval for a `MANUAL` record (when `autoApproveInApp` is off or user confirms).
     * Dispatches `POST /sms-approve` with HMAC-SHA256 signature immediately.
     */
    fun approveRecordManually(record: SmsRecord, overridePublicId: String? = null, overrideKind: String? = null) {
        viewModelScope.launch {
            val finalPublicId = overridePublicId?.trim()?.takeIf { it.isNotEmpty() } ?: record.matchedId
            val finalKind = overrideKind?.trim()?.takeIf { it.isNotEmpty() } ?: record.matchedKind ?: "order"

            if (finalPublicId.isNullOrBlank()) {
                logDao.update(
                    record.copy(
                        status = SmsStatus.NEW.name,
                        lastError = null
                    )
                )
                _snackbarMessage.value = "در حال دریافت لیست سفارش‌های منتظر از سرور برای تطبیق..."
                ForwardWorker.runSweepNow(appContext)
            } else {
                logDao.update(
                    record.copy(
                        status = SmsStatus.MANUAL.name,
                        matchedKind = finalKind,
                        matchedId = finalPublicId,
                        lastError = null
                    )
                )
                _snackbarMessage.value = "در حال ارسال فرمان تأیید سفارش $finalPublicId به سرور..."
                ForwardWorker.runManualApproveNow(appContext, record.id)
            }
        }
    }

    fun retrySingleLog(entry: SmsRecord) {
        viewModelScope.launch {
            val nextStatus = if (entry.amountMinor != null && entry.reason != "test_deposit_not_sent") SmsStatus.NEW else SmsStatus.IGNORED
            logDao.update(
                entry.copy(
                    status = nextStatus.name,
                    lastError = null
                )
            )
            if (nextStatus == SmsStatus.NEW) {
                _snackbarMessage.value = "بررسی و تطبیق مجدد با سرور آغاز شد."
                ForwardWorker.runSweepNow(appContext)
            }
        }
    }

    fun flushQueueNow() {
        viewModelScope.launch {
            _snackbarMessage.value = "در حال بررسی سفارش‌های منتظر از سرور و تطبیق پیامک‌ها..."
            ForwardWorker.runSweepNow(appContext)
        }
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

    companion object {
        fun provideFactory(application: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AsisViewModel::class.java)) {
                        return AsisViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }

                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras
                ): T = create(modelClass)
            }
    }
}
