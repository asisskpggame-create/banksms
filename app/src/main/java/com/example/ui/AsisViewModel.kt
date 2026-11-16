package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ForwardWorker
import com.example.InternetWatchService
import com.example.data.AppDatabase
import com.example.data.AppThemeMode
import com.example.data.FilterAction
import com.example.data.FilterTarget
import com.example.data.GatewaySettings
import com.example.data.Item
import com.example.data.SettingsRepo
import com.example.data.SmsFilterEngine
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import com.example.network.BatchRetryPolicy
import com.example.network.BatchRetryState
import com.example.network.BatchRetryTracker
import com.example.network.PendingFetchResult
import com.example.network.PostErrorSyncState
import com.example.network.RetryHealthAnalytics
import com.example.network.RetryHealthSummary
import com.example.network.ServerConnectionLevel
import com.example.network.ServerConnectionStatusUi
import com.example.network.ServerConnectionTelemetry
import com.example.network.ServerHealthTracker
import com.example.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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

    val allInvoices: StateFlow<List<com.example.data.PendingInvoiceEntity>> = logDao.observeAllInvoices()
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

    val batchRetryState: StateFlow<BatchRetryState> = BatchRetryTracker.stateFlow
    val postErrorSyncState: StateFlow<PostErrorSyncState> = ServerHealthTracker.postErrorSyncFlow

    val totalLogsCount: StateFlow<Int> = allLogs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val oldLogs15DaysCount: StateFlow<Int> = allLogs
        .map { logs ->
            val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(GatewaySettings.AUTO_CLEAR_LOGS_DAYS)
            logs.count { it.storedMs < cutoffMs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val retryHealthSummary: StateFlow<RetryHealthSummary> = combine(
        allLogs,
        logDao.observeRecentRetryEvents(
            System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(8)
        )
    ) { logs, events ->
        RetryHealthAnalytics.compute7DaySummary(logs = logs, events = events)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RetryHealthAnalytics.compute7DaySummary(emptyList(), emptyList())
    )

    val serverStatusState: StateFlow<ServerConnectionStatusUi> = combine(
        isOnlineState,
        settingsState,
        ServerHealthTracker.telemetryFlow
    ) { isOnline, settings, telemetry ->
        computeServerConnectionStatus(
            isOnline = isOnline,
            settings = settings,
            telemetry = telemetry
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        computeServerConnectionStatus(
            isOnline = isOnlineState.value,
            settings = settingsState.value,
            telemetry = ServerHealthTracker.telemetryFlow.value
        )
    )

    init {
        runCatching { InternetWatchService.register(appContext) }
        viewModelScope.launch {
            runCatching { logDao.deletePromoLogs() }
            if (settingsRepo.readSettings().autoClearOldLogsEnabled) {
                runCatching {
                    ForwardWorker.schedulePeriodicLogCleanupWorker(appContext)
                    ForwardWorker.runOldLogsCleanupNow(appContext, forceRun15Day = false)
                }
            }
        }
        // Re-probe & auto-sync after errors immediately whenever network transitions back online
        viewModelScope.launch {
            var previousOnline: Boolean? = null
            isOnlineState.collect { onlineNow ->
                if (onlineNow && previousOnline == false) {
                    val current = settingsRepo.readSettings()
                    if (current.autoSyncAfterErrors && current.isConfigured) {
                        syncConnectionAfterErrorsInternal(
                            includeAuth401 = false,
                            reasonFa = "بازگشت اتصال اینترنت",
                            showFeedbackSnackbar = false
                        )
                    } else {
                        probeServerSilently()
                    }
                }
                previousOnline = onlineNow
            }
        }
        // Automatically sync pending/failed items when server recovers from a prior error
        viewModelScope.launch {
            ServerHealthTracker.postErrorSyncFlow.collect { syncState ->
                if (syncState.serverRecoveredPendingSync && !syncState.isSyncing) {
                    val triggerReason = ServerHealthTracker.consumeServerRecoveredTrigger()
                        ?: return@collect
                    val current = settingsRepo.readSettings()
                    if (current.autoSyncAfterErrors && current.isConfigured && current.serviceEnabled && isOnlineState.value) {
                        val hasRecoverable = logDao.getRecoveryCandidateCount() > 0 ||
                            logDao.getActiveDepositsForMatching().isNotEmpty()
                        if (hasRecoverable) {
                            ForwardWorker.runRecoverySyncNow(
                                context = appContext,
                                includeAuth401 = false,
                                reasonFa = triggerReason
                            )
                        }
                    }
                }
            }
        }
        // Immediate startup server check + 15-second live sync & connectivity probe loop
        viewModelScope.launch {
            probeServerSilently()
            while (isActive) {
                delay(15_000L)
                runCatching {
                    val current = settingsRepo.readSettings()
                    if (current.isConfigured && isOnlineState.value) {
                        val pingOk = GatewayApiClient.pingServer(current.endpointUrl, current.gatewayToken)
                        if (pingOk && current.serviceEnabled) {
                            val hasRecoveryWork = current.autoSyncAfterErrors && logDao.getRecoveryCandidateCount() > 0
                            if (hasRecoveryWork) {
                                ForwardWorker.runRecoverySyncNow(
                                    context = appContext,
                                    includeAuth401 = false,
                                    reasonFa = "همگام‌سازی خودکار صف خطاها"
                                )
                            } else {
                                ForwardWorker.runSweepNow(appContext)
                            }
                        }
                    }
                }
            }
        }
        // Monitor live server/network connection state and send real-time push alerts on disconnects or errors
        viewModelScope.launch {
            var previousLevel: ServerConnectionLevel? = null
            var hadConnectionError = false
            combine(
                isOnlineState,
                settingsState,
                ServerHealthTracker.telemetryFlow
            ) { online, settings, telemetry ->
                Triple(online, settings, telemetry)
            }.collect { (online, settings, telemetry) ->
                val uiStatus = computeServerConnectionStatus(online, settings, telemetry)
                if (uiStatus.level == ServerConnectionLevel.GREEN) {
                    if (hadConnectionError || previousLevel == ServerConnectionLevel.RED) {
                        NotificationHelper.cancelServerErrorNotification(appContext)
                    }
                    hadConnectionError = false
                } else if (settings.isConfigured && settings.serviceEnabled) {
                    val hasPingFailure = telemetry.lastPingOk == false && !telemetry.isChecking && telemetry.httpCode != 401
                    val wentOffline = !online && previousLevel != null && previousLevel != ServerConnectionLevel.RED
                    if (wentOffline || hasPingFailure) {
                        hadConnectionError = true
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = uiStatus.titleFa,
                            errorDetail = uiStatus.detailFa,
                            httpCode = if (!online) null else telemetry.httpCode
                        )
                    }
                }
                previousLevel = uiStatus.level
            }
        }
        // Automated foreground batch-processing retry ticker: fires when countdown reaches 00:00
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                runCatching {
                    val current = settingsRepo.readSettings()
                    if (!current.isConfigured || !current.serviceEnabled || !isOnlineState.value) {
                        return@runCatching
                    }
                    if (BatchRetryTracker.stateFlow.value.isBatchRunning ||
                        ServerHealthTracker.postErrorSyncFlow.value.isSyncing
                    ) {
                        return@runCatching
                    }
                    val dueAt = BatchRetryPolicy.earliestAutoRetryAtMs(allLogs.value)
                    if (dueAt != null && dueAt <= System.currentTimeMillis()) {
                        ForwardWorker.runBatchRetryNow(appContext)
                    }
                }
            }
        }
    }

    private suspend fun probeServerSilently() {
        runCatching {
            val current = settingsRepo.readSettings()
            if (current.isConfigured && isOnlineState.value) {
                GatewayApiClient.pingServer(current.endpointUrl, current.gatewayToken)
            }
        }
    }

    private suspend fun syncConnectionAfterErrorsInternal(
        includeAuth401: Boolean,
        reasonFa: String,
        showFeedbackSnackbar: Boolean
    ) {
        val current = settingsRepo.readSettings()
        if (!current.isConfigured) {
            if (showFeedbackSnackbar) {
                _snackbarMessage.value = "ابتدا آدرس سرور و توکن را در تنظیمات وارد کنید."
            }
            return
        }
        if (!InternetWatchService.checkCurrentlyOnline(appContext) && !isOnlineState.value) {
            if (showFeedbackSnackbar) {
                _snackbarMessage.value = "اتصال اینترنت برقرار نیست؛ پس از وصل شدن خودکار همگام‌سازی می‌شود."
            }
            if (current.autoSyncAfterErrors) {
                ForwardWorker.schedulePostErrorRecoveryWorker(
                    context = appContext,
                    includeAuth401 = includeAuth401,
                    reasonFa = reasonFa
                )
            }
            return
        }
        if (showFeedbackSnackbar) {
            _snackbarMessage.value = "در حال بررسی اتصال و همگام‌سازی خطاها..."
        }
        val ok = GatewayApiClient.pingServer(current.endpointUrl, current.gatewayToken)
        if (!ok) {
            if (showFeedbackSnackbar) {
                _snackbarMessage.value = "سرور هنوز در دسترس نیست؛ صف خطاها در حالت انتظار باقی ماند."
            }
            if (current.autoSyncAfterErrors) {
                ForwardWorker.schedulePostErrorRecoveryWorker(
                    context = appContext,
                    includeAuth401 = includeAuth401,
                    delayMs = 20_000L,
                    reasonFa = reasonFa
                )
            }
            return
        }
        if (!current.serviceEnabled) {
            if (showFeedbackSnackbar) {
                _snackbarMessage.value = "اتصال سرور برقرار است؛ برای ارسال پیامک‌ها سرویس اصلی را روشن کنید."
            }
            return
        }
        val outcome = ForwardWorker.runRecoverySyncNow(
            context = appContext,
            includeAuth401 = includeAuth401,
            reasonFa = reasonFa
        )
        if (showFeedbackSnackbar) {
            val syncState = ServerHealthTracker.postErrorSyncFlow.value
            _snackbarMessage.value = when {
                syncState.lastRecoveryRecoveredCount > 0 && syncState.lastRecoveryRemainingErrors == 0 ->
                    "اتصال برقرار شد و ${syncState.lastRecoveryRecoveredCount} مورد با موفقیت همگام‌سازی گردید."
                syncState.lastRecoveryRecoveredCount > 0 ->
                    "${syncState.lastRecoveryRecoveredCount} مورد همگام‌سازی شد • ${syncState.lastRecoveryRemainingErrors} مورد همچنان در صف خطا است."
                outcome == ForwardWorker.WorkOutcome.SUCCESS ->
                    "اتصال سرور پایدار است و خطای معلقی وجود ندارد."
                else ->
                    "بررسی انجام شد؛ برخی موارد نیازمند تلاش مجدد هستند."
            }
        }
    }

    fun checkServerConnectionNow() {
        viewModelScope.launch {
            syncConnectionAfterErrorsInternal(
                includeAuth401 = false,
                reasonFa = "بررسی لحظه‌ای اتصال و همگام‌سازی",
                showFeedbackSnackbar = false
            )
        }
    }

    fun syncConnectionAfterErrors(
        includeAuth401: Boolean = false,
        reasonFa: String = "همگام‌سازی دستی اتصال پس از خطا"
    ) {
        viewModelScope.launch {
            syncConnectionAfterErrorsInternal(
                includeAuth401 = includeAuth401,
                reasonFa = reasonFa,
                showFeedbackSnackbar = true
            )
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun setServiceEnabled(enabled: Boolean) {
        settingsRepo.setServiceEnabled(enabled)
        if (enabled) {
            viewModelScope.launch {
                probeServerSilently()
                ForwardWorker.runSweepNow(appContext)
            }
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

    fun setNotifyOnServerConnectionError(enabled: Boolean) {
        settingsRepo.setNotifyOnServerConnectionError(enabled)
        if (!enabled) {
            NotificationHelper.cancelServerErrorNotification(appContext)
        }
        _snackbarMessage.value = if (enabled) {
            "اعلان لحظه‌ای خطاهای اتصال به سرور فعال شد."
        } else {
            "اعلان خطاهای اتصال به سرور غیرفعال شد."
        }
    }

    fun setNotifyOnSensitiveBankSms(enabled: Boolean) {
        settingsRepo.setNotifyOnSensitiveBankSms(enabled)
        _snackbarMessage.value = if (enabled) {
            "اعلان لحظه‌ای پیامک‌های بانکی حساس فعال شد."
        } else {
            "اعلان پیامک‌های بانکی حساس غیرفعال شد."
        }
    }

    fun setNotifyOnAmbiguousOrManual(enabled: Boolean) {
        settingsRepo.setNotifyOnAmbiguousOrManual(enabled)
        _snackbarMessage.value = if (enabled) {
            "اعلان واریزهای مبهم و نیازمند تأیید دستی فعال شد."
        } else {
            "اعلان واریزهای مبهم و تأیید دستی غیرفعال شد."
        }
    }

    fun setNotifyOnApprovedInvoice(enabled: Boolean) {
        settingsRepo.setNotifyOnApprovedInvoice(enabled)
        _snackbarMessage.value = if (enabled) {
            "اعلان و اعلام مبلغ فاکتورهای تأییدشده فعال شد."
        } else {
            "اعلان فاکتورهای تأییدشده غیرفعال شد."
        }
    }

    fun setAutoClearOldLogsEnabled(enabled: Boolean) {
        settingsRepo.setAutoClearOldLogsEnabled(enabled)
        if (enabled) {
            ForwardWorker.schedulePeriodicLogCleanupWorker(appContext)
            viewModelScope.launch {
                val deleted = ForwardWorker.runOldLogsCleanupNow(appContext, forceRun15Day = false)
                _snackbarMessage.value = if (deleted > 0) {
                    "پاکسازی خودکار ۱۵ روزه فعال شد و $deleted لاگ قدیمی برای بهینه‌سازی حافظه حذف گردید."
                } else {
                    "پاکسازی خودکار لاگ‌های قدیمی‌تر از ۱۵ روز برای بهینه‌سازی حافظه فعال شد."
                }
            }
        } else {
            ForwardWorker.cancelPeriodicLogCleanupWorker(appContext)
            _snackbarMessage.value = "پاکسازی خودکار ۱۵ روزه لاگ‌ها غیرفعال شد."
        }
    }

    fun clearLogsOlderThan15DaysNow() {
        viewModelScope.launch {
            val deleted = ForwardWorker.runOldLogsCleanupNow(appContext, forceRun15Day = true)
            _snackbarMessage.value = if (deleted > 0) {
                "$deleted لاگ قدیمی‌تر از ۱۵ روز برای بهینه‌سازی حافظه پاک شد."
            } else {
                "هیچ لاگ قدیمی‌تر از ۱۵ روز برای پاکسازی یافت نشد."
            }
        }
    }

    fun setSensitiveSmsMinToman(minToman: Long) {
        val clean = minToman.coerceAtLeast(0L)
        settingsRepo.setSensitiveSmsMinToman(clean)
        _snackbarMessage.value = if (clean == 0L) {
            "آستانه اعلان حساس روی «همه واریزهای بانکی» تنظیم شد."
        } else {
            "آستانه واریز کلان روی ${SmsParser.formatRial(clean)} تومان تنظیم شد."
        }
    }

    fun sendTestPushNotification() {
        if (!NotificationHelper.hasNotificationPermission(appContext)) {
            _snackbarMessage.value = "ابتدا مجوز ارسال اعلان (Notification) را فعال کنید."
            return
        }
        val posted = NotificationHelper.sendTestPushAlert(appContext)
        _snackbarMessage.value = if (posted) {
            "اعلان آزمایشی لحظه‌ای ارسال شد."
        } else {
            "ارسال اعلان با خطا مواجه شد؛ مجوز اعلان‌های سیستم را بررسی کنید."
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepo.setThemeMode(mode)
        _snackbarMessage.value = when (mode) {
            AppThemeMode.MIDNIGHT_OLED ->
                "حالت مشکی خالص (Midnight OLED) برای صرفه‌جویی باتری AMOLED فعال شد."
            AppThemeMode.DARK_NAVY ->
                "تم سرمه‌ای تیره (پیش‌فرض) فعال شد."
        }
    }

    fun setMidnightOledEnabled(enabled: Boolean) {
        setThemeMode(if (enabled) AppThemeMode.MIDNIGHT_OLED else AppThemeMode.DARK_NAVY)
    }

    fun setCustomFilterRulesEnabled(enabled: Boolean) {
        settingsRepo.setCustomFilterRulesEnabled(enabled)
        _snackbarMessage.value = if (enabled) {
            "فیلترهای سفارشی پیامک برای کاهش بار سرور فعال شدند."
        } else {
            "فیلترهای سفارشی موقتاً غیرفعال شدند."
        }
    }

    fun addCustomFilterRule(
        name: String,
        pattern: String,
        target: FilterTarget,
        action: FilterAction
    ) {
        val created = settingsRepo.addCustomFilterRule(name, pattern, target, action)
        _snackbarMessage.value = if (created != null) {
            "قانون فیلتر «${created.displayTitle()}» ذخیره شد."
        } else {
            "لطفاً عبارت یا مقدار معتبر برای قانون وارد کنید."
        }
    }

    fun toggleCustomFilterRule(ruleId: String) {
        settingsRepo.toggleCustomFilterRule(ruleId)
    }

    fun removeCustomFilterRule(ruleId: String) {
        settingsRepo.removeCustomFilterRule(ruleId)
        _snackbarMessage.value = "قانون فیلتر حذف شد."
    }

    fun setAutoSyncAfterErrors(enabled: Boolean) {
        settingsRepo.setAutoSyncAfterErrors(enabled)
        _snackbarMessage.value = if (enabled) {
            "همگام‌سازی خودکار اتصال بعد از خطاها فعال شد."
        } else {
            "همگام‌سازی خودکار اتصال بعد از خطاها غیرفعال شد."
        }
        if (enabled) {
            viewModelScope.launch {
                val hasCandidates = logDao.getRecoveryCandidateCount() > 0
                if (hasCandidates && settingsRepo.readSettings().isConfigured && isOnlineState.value) {
                    ForwardWorker.runRecoverySyncNow(
                        context = appContext,
                        includeAuth401 = false,
                        reasonFa = "فعال‌سازی همگام‌سازی خودکار"
                    )
                }
            }
        } else {
            ForwardWorker.cancelPostErrorRecoveryWorker(appContext)
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
        viewModelScope.launch {
            val current = settingsRepo.readSettings()
            if (current.autoSyncAfterErrors && current.serviceEnabled && logDao.getRecoveryCandidateCount() > 0) {
                ForwardWorker.runRecoverySyncNow(
                    context = appContext,
                    includeAuth401 = true,
                    reasonFa = "به‌روزرسانی تنظیمات و توکن"
                )
            } else {
                probeServerSilently()
                ForwardWorker.runSweepNow(appContext)
            }
        }
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
                    val current = settingsRepo.readSettings()
                    if (current.autoSyncAfterErrors && current.serviceEnabled && logDao.getRecoveryCandidateCount() > 0) {
                        ForwardWorker.runRecoverySyncNow(
                            context = appContext,
                            includeAuth401 = true,
                            reasonFa = "تأیید اتصال sms-in"
                        )
                    }
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
                    val current = settingsRepo.readSettings()
                    if (current.autoSyncAfterErrors && current.serviceEnabled && logDao.getRecoveryCandidateCount() > 0) {
                        ForwardWorker.runRecoverySyncNow(
                            context = appContext,
                            includeAuth401 = true,
                            reasonFa = "تأیید اتصال sms-pending"
                        )
                    }
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
            if (parsed.reason == "promo") {
                _snackbarMessage.value = "پیامک تبلیغاتی یا حاوی لینک تشخیص داده شد و در لاگ ثبت نمی‌شود."
                return@launch
            }
            val filterMatch = SmsFilterEngine.evaluate(
                sender = bank,
                rawBody = rawBody,
                amountMinor = parsed.amountMinor,
                settings = settingsRepo.readSettings()
            )
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
                reason = if (filterMatch != null) "custom_filter"
                else if (parsed.amountMinor != null) "test_deposit_not_sent"
                else parsed.reason,
                status = initialStatus.name,
                receivedMs = now,
                storedMs = now,
                decidedMs = now,
                lastError = filterMatch?.reasonDetailFa
            )

            val id = logDao.insertIgnoreDuplicate(record)
            if (id == -1L) {
                _snackbarMessage.value = "پیامک تکراری نادیده گرفته شد."
            } else if (filterMatch != null) {
                _snackbarMessage.value = "تست با قانون فیلتر سفارشی مسدود شد (${filterMatch.rule.displayTitle()})."
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
                val updated = logDao.getById(record.id)
                if (updated?.statusEnum == SmsStatus.MATCHED) {
                    val priceToman = updated.amountMinor?.let { SmsParser.formatToman(it) } ?: "۰"
                    _snackbarMessage.value = "✅ فاکتور ${updated.matchedId ?: finalPublicId} با مبلغ $priceToman تومان ثبت و تأیید شد."
                } else if (!updated?.lastError.isNullOrBlank()) {
                    _snackbarMessage.value = "نتیجهٔ تأیید: ${updated?.lastError}"
                }
            }
        }
    }

    fun retrySingleLog(entry: SmsRecord) {
        viewModelScope.launch {
            val isDeposit = entry.amountMinor != null && entry.reason != "test_deposit_not_sent"
            if (!isDeposit) {
                logDao.update(entry.copy(status = SmsStatus.IGNORED.name, lastError = null))
                return@launch
            }

            _snackbarMessage.value = "در حال تلاش مجدد برای ارسال به سرور..."
            if (!entry.matchedId.isNullOrBlank() && entry.matchedKind != "sms-in") {
                logDao.update(
                    entry.copy(
                        status = SmsStatus.RETRY.name,
                        lastError = null
                    )
                )
                ForwardWorker.runManualApproveNow(appContext, entry.id)
            } else {
                logDao.update(
                    entry.copy(
                        status = SmsStatus.NEW.name,
                        lastError = null
                    )
                )
                ForwardWorker.runSweepNow(appContext)
            }

            val updated = logDao.getById(entry.id)
            when {
                updated?.statusEnum == SmsStatus.MATCHED -> {
                    _snackbarMessage.value = "ارسال مجدد با موفقیت انجام شد و واریز تأیید گردید."
                }
                !updated?.lastError.isNullOrBlank() -> {
                    _snackbarMessage.value = "خطا در ارسال مجدد: ${updated?.lastError}"
                }
                else -> {
                    _snackbarMessage.value = "بررسی مجدد با سرور انجام شد (در انتظار سفارش متناظر)."
                }
            }
        }
    }

    fun retryAllFailedLogs() {
        viewModelScope.launch {
            val pendingIds = logDao.getRetryPendingRecords().map { it.id }
            val resetCount = logDao.resetFailedAndErrorLogsForRetry()
            _snackbarMessage.value = if (resetCount > 0) {
                "در حال پردازش دسته‌ای برای ارسال مجدد $resetCount پیامک..."
            } else {
                "در حال بررسی و ارسال مجدد صف به سرور..."
            }
            if (pendingIds.isNotEmpty()) {
                ForwardWorker.runBatchRetryNow(appContext, pendingIds)
            } else {
                ForwardWorker.runSweepNow(appContext)
            }
        }
    }

    fun retrySelectedFailedBatch(selectedIds: List<Long>) {
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            logDao.markIdsForImmediateBatchRetry(selectedIds)
            _snackbarMessage.value = "در حال همگام‌سازی دسته‌ای ${selectedIds.size} پیامک انتخاب‌شده..."
            ForwardWorker.runBatchRetryNow(appContext, selectedIds)
            val state = batchRetryState.value
            _snackbarMessage.value = when {
                state.lastBatchSucceededCount > 0 && state.lastBatchFailedCount == 0 ->
                    "همگام‌سازی دسته‌ای موفق بود (${state.lastBatchSucceededCount} مورد تأیید شد)."
                state.lastBatchSucceededCount > 0 ->
                    "نتیجهٔ دسته: ${state.lastBatchSucceededCount} موفق • ${state.lastBatchFailedCount} نیازمند تلاش مجدد."
                state.lastBatchProcessedCount > 0 ->
                    "خطا در ارسال دسته‌ای؛ زمانبندی تلاش مجدد خودکار بعدی ثبت شد."
                else ->
                    "بررسی دسته‌ای انجام شد."
            }
        }
    }

    fun onSmsPermissionsGranted() {
        viewModelScope.launch {
            val windowHours = settingsRepo.readSettings().lastServerWindowHours
            val synced = com.example.SmsReceiver.syncRecentInboxSms(appContext, windowHours)
            if (synced > 0) {
                _snackbarMessage.value = "$synced پیامک واریز اخیر از صندوق ورودی خوانده و در صف تطبیق قرار گرفت."
            }
            ForwardWorker.runSweepNow(appContext)
        }
    }

    fun flushQueueNow() {
        viewModelScope.launch {
            val windowHours = settingsRepo.readSettings().lastServerWindowHours
            com.example.SmsReceiver.syncRecentInboxSms(appContext, windowHours)
            _snackbarMessage.value = "در حال بررسی سفارش‌های منتظر از سرور و تطبیق پیامک‌ها..."
            ForwardWorker.runSweepNow(appContext)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logDao.clearAllLogs()
            logDao.clearAllInvoices()
            logDao.clearAllRetryEvents()
            _snackbarMessage.value = "تمام لاگ‌ها، آمارها و فاکتورهای موقت پاک شدند."
        }
    }

    /**
     * Registers an invoice request (from server inquiry or manual entry) into `pending_invoices`,
     * immediately checks the SMS logs (`NEW`, `RETRY`, `UNKNOWN_DEPOSIT`) for a matching deposit amount,
     * and if found, approves the invoice in < 1 minute (or queues `RETRY` if offline).
     */
    fun registerInvoiceAndVerifyLogs(publicId: String, amountToman: Long, kind: String = "order") {
        val cleanId = publicId.trim().ifEmpty { "INV-${(1000..9999).random()}" }
        if (amountToman <= 0L) {
            _snackbarMessage.value = "لطفاً مبلغ فاکتور (به تومان) را به درستی وارد کنید."
            return
        }
        val dueMinor = amountToman * 10L
        val nowMs = System.currentTimeMillis()
        viewModelScope.launch {
            val settings = settingsRepo.readSettings()
            val invoice = com.example.data.PendingInvoiceEntity(
                publicId = cleanId,
                kind = kind,
                dueMinor = dueMinor,
                createdMs = nowMs + settings.lastClockOffsetMs,
                syncedAtMs = nowMs,
                status = "WAITING"
            )
            logDao.upsertInvoice(invoice)

            // Check existing SMS logs (`NEW`, `RETRY`, `UNKNOWN_DEPOSIT`) immediately
            val activeDeposits = logDao.getActiveDepositsForMatching()
            val checkResult = com.example.data.SmartAmountMatcher.verifyServerInvoiceInLogs(
                invoice = invoice,
                activeDeposits = activeDeposits,
                clockOffsetMs = settings.lastClockOffsetMs
            )

            when (checkResult) {
                is com.example.data.InvoiceLogCheckResult.MatchedInLog -> {
                    val rec = checkResult.depositRecord
                    if (settings.isConfigured) {
                        logDao.update(
                            rec.copy(
                                status = SmsStatus.NEW.name,
                                matchedKind = kind,
                                matchedId = cleanId,
                                lastError = null
                            )
                        )
                        _snackbarMessage.value = "تطبیق هوشمند در لاگ یافت شد (${SmsParser.formatToman(dueMinor)} تومان)؛ در حال ارسال تأیید فاکتور $cleanId به سرور..."
                        ForwardWorker.runManualApproveNow(appContext, rec.id)
                        val updated = logDao.getById(rec.id)
                        if (updated?.statusEnum == SmsStatus.MATCHED) {
                            _snackbarMessage.value = "✅ فاکتور $cleanId با مبلغ ${SmsParser.formatToman(dueMinor)} تومان ثبت و تأیید شد."
                        }
                    } else {
                        logDao.update(
                            rec.copy(
                                status = SmsStatus.MATCHED.name,
                                matchedKind = kind,
                                matchedId = cleanId,
                                decidedMs = nowMs,
                                reason = "",
                                lastError = null
                            )
                        )
                        logDao.recordApprovedInvoice(
                            publicId = cleanId,
                            kind = kind,
                            dueMinor = dueMinor,
                            smsId = rec.id,
                            bankTitle = rec.bankTitle,
                            matchedAtMs = nowMs
                        )
                        NotificationHelper.notifyInvoiceApproved(
                            context = appContext,
                            publicId = cleanId,
                            kind = kind,
                            amountMinor = dueMinor,
                            bankTitle = rec.bankTitle,
                            recordId = rec.id
                        )
                        _snackbarMessage.value = "✅ فاکتور $cleanId با قیمت ${SmsParser.formatToman(dueMinor)} تومان در لاگ مطابقت داشت و ثبت و تأیید شد!"
                    }
                }
                else -> {
                    _snackbarMessage.value = "فاکتور $cleanId (${SmsParser.formatToman(dueMinor)} تومان) ثبت شد؛ به محض دریافت پیامک واریز یا استعلام سرور، خودکار تطبیق و تأیید می‌شود."
                    ForwardWorker.runSweepNow(appContext)
                }
            }
        }
    }

    fun removeInvoice(publicId: String) {
        viewModelScope.launch {
            logDao.deleteInvoiceByPublicId(publicId)
            _snackbarMessage.value = "فاکتور $publicId حذف شد."
        }
    }

    fun markDepositAsPersonalIgnored(record: SmsRecord) {
        viewModelScope.launch {
            logDao.update(
                record.copy(
                    status = SmsStatus.IGNORED.name,
                    reason = "personal_unrelated",
                    decidedMs = System.currentTimeMillis(),
                    lastError = "واریز شخصی / متفرقه (توسط کاربر از صف تطبیق خارج شد)"
                )
            )
            _snackbarMessage.value = "این واریز به عنوان «واریز شخصی / غیرمرتبط» علامت‌گذاری شد."
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

    fun addRegisteredBankCard(bankCode: String, bankTitle: String, senderNumber: String) {
        val cleanSender = senderNumber.trim()
        if (bankCode.isBlank() || cleanSender.isBlank()) {
            _snackbarMessage.value = "لطفاً هم بانک و هم سرشماره پیامک را مشخص کنید."
            return
        }
        settingsRepo.addRegisteredBankCard(bankCode, bankTitle, cleanSender)
        _snackbarMessage.value = "$bankTitle با سرشماره $cleanSender ثبت شد."
    }

    fun removeRegisteredBankCard(cardId: String) {
        settingsRepo.removeRegisteredBankCard(cardId)
        _snackbarMessage.value = "بانک از لیست حذف شد."
    }

    fun loadReceivedSmsSenders(): List<String> {
        val inboxSenders = runCatching {
            val result = LinkedHashSet<String>()
            appContext.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(android.provider.Telephony.Sms.ADDRESS),
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                var scannedRows = 0
                while (cursor.moveToNext() && scannedRows < 600 && result.size < 150) {
                    scannedRows++
                    val addr = if (addrIdx >= 0) cursor.getString(addrIdx)?.trim().orEmpty() else ""
                    if (addr.isNotEmpty()) {
                        result.add(addr)
                    }
                }
            }
            result.toList()
        }.getOrDefault(emptyList())

        val fallbackSampleSenders = listOf(
            "B.QMEHRIRAN",
            "+98200033",
            "+98600060",
            "+98200070",
            "+98200084210",
            "+98200090",
            "+98200069",
            "+98200040",
            "+98200014",
            "+9820000",
            "HamraheMan",
            "+989999987641",
            "+989373534023",
            "+989998622172",
            "+9830009014",
            "+989229012524",
            "MCI Club",
            "MCI Offers",
            "+9850003000300",
            "+9850003223",
            "+9820008580",
            "E-Charge",
            ".IRANCELL.",
            "+989019932340"
        )
        val combined = (inboxSenders + fallbackSampleSenders).distinct()
        return combined.sortedWith(
            compareBy<String>(
                { sender ->
                    val detected = SmsParser.detectBank(sender, "")
                    val hasVisualSpec = BankVisualCatalog.findByCode(detected.code) != null
                    if (hasVisualSpec) 0 else 1
                },
                { sender ->
                    if (SmsParser.isPersonalSender(sender)) 1 else 0
                }
            )
        )
    }

    companion object {
        fun computeServerConnectionStatus(
            isOnline: Boolean,
            settings: GatewaySettings,
            telemetry: ServerConnectionTelemetry
        ): ServerConnectionStatusUi {
            val hostLabel = runCatching {
                java.net.URI(GatewayApiClient.extractBasePanelUrl(settings.endpointUrl)).host
            }.getOrNull()?.takeIf { it.isNotBlank() }

            if (!isOnline) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.RED,
                    titleFa = "آفلاین • اینترنت قطع است",
                    detailFa = "عدم دسترسی به اینترنت؛ پیامک‌ها تا اتصال مجدد در صف امن نگهداری می‌شوند.",
                    latencyMs = null,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = false,
                    needsConfiguration = !settings.isConfigured
                )
            }

            if (!settings.isConfigured) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.YELLOW,
                    titleFa = "نیازمند تنظیم اتصال سرور",
                    detailFa = "آدرس HTTPS پنل و توکن درگاه را در بخش تنظیمات ثبت کنید.",
                    latencyMs = null,
                    hostLabel = null,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = false,
                    needsConfiguration = true
                )
            }

            if (!settings.lastTokenError.isNullOrBlank() || telemetry.httpCode == 401) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.RED,
                    titleFa = "قطع ارتباط • خطای توکن (۴۰۱)",
                    detailFa = settings.lastTokenError ?: telemetry.lastErrorMessage
                        ?: "توکن درگاه با سرور مطابقت ندارد.",
                    latencyMs = telemetry.latencyMs,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = telemetry.isChecking,
                    needsConfiguration = true
                )
            }

            if (telemetry.lastPingOk == false) {
                val isRateLimit = telemetry.httpCode == 429
                return ServerConnectionStatusUi(
                    level = if (isRateLimit) ServerConnectionLevel.YELLOW else ServerConnectionLevel.RED,
                    titleFa = if (isRateLimit) {
                        "اتصال محدود • سقف درخواست (۴۲۹)"
                    } else {
                        "آفلاین • عدم پاسخ‌گویی سرور"
                    },
                    detailFa = telemetry.lastErrorMessage ?: "سرور در دسترس نیست یا پاسخ نمی‌دهد.",
                    latencyMs = telemetry.latencyMs,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = telemetry.isChecking,
                    needsConfiguration = false
                )
            }

            if (telemetry.isChecking && telemetry.lastPingOk == null) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.YELLOW,
                    titleFa = "در حال بررسی اتصال به سرور...",
                    detailFa = hostLabel?.let { "برقراری ارتباط با $it" } ?: "ارسال پینگ اولیه به درگاه",
                    latencyMs = telemetry.latencyMs,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = true,
                    needsConfiguration = false
                )
            }

            if (!settings.serviceEnabled) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.YELLOW,
                    titleFa = "آنلاین • سرویس تطبیق متوقف است",
                    detailFa = "ارتباط اینترنت برقرار است ولی سرویس پردازش پیامک موقتاً متوقف شده است.",
                    latencyMs = telemetry.latencyMs,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = telemetry.isChecking,
                    needsConfiguration = false
                )
            }

            if (telemetry.lastPingOk == null) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.YELLOW,
                    titleFa = "در حال همگام‌سازی اولیه با سرور...",
                    detailFa = hostLabel?.let { "بررسی وضعیت درگاه $it" } ?: "بررسی دسترسی سرور در حال انجام است",
                    latencyMs = null,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = true,
                    needsConfiguration = false
                )
            }

            val latency = telemetry.latencyMs ?: 0L
            if (latency > 2000L) {
                return ServerConnectionStatusUi(
                    level = ServerConnectionLevel.YELLOW,
                    titleFa = "اتصال ناپایدار یا کند به سرور",
                    detailFa = "زمان پاسخ سرور بالا است (${latency} ms) • وضعیت تحت نظر است.",
                    latencyMs = latency,
                    hostLabel = hostLabel,
                    lastCheckedAtMs = telemetry.lastCheckedAtMs,
                    isChecking = telemetry.isChecking,
                    needsConfiguration = false
                )
            }

            return ServerConnectionStatusUi(
                level = ServerConnectionLevel.GREEN,
                titleFa = "آنلاین • متصل به سرور",
                detailFa = if (hostLabel != null) {
                    "ارتباط پایدار با $hostLabel"
                } else {
                    "سرور آمادهٔ دریافت و تأیید آنی واریزی‌ها است"
                },
                latencyMs = latency,
                hostLabel = hostLabel,
                lastCheckedAtMs = telemetry.lastCheckedAtMs,
                isChecking = telemetry.isChecking,
                needsConfiguration = false
            )
        }

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
