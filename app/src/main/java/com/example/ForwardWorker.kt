package com.example

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.AppDatabase
import com.example.data.Deposit
import com.example.data.LogDao
import com.example.data.Matcher
import com.example.data.RetryAttemptEventEntity
import com.example.data.SettingsRepo
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.ApproveResult
import com.example.network.BatchRetryPolicy
import com.example.network.BatchRetryTracker
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import com.example.network.PendingFetchResult
import com.example.network.ServerHealthTracker
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Implements `MatcherWorker` (`APP_SIDE_APPROVAL_SPEC.md`) with:
 * - Immediate coroutine execution (`runSweepNow` / `runManualApproveNow`) so VPN connections
 *   (e.g. v2rayNG / Nekobox) or Android `NET_CAPABILITY_VALIDATED` delays never block SMS matching.
 * - Background `WorkManager` exponential backoff retry when network is temporarily unreachable.
 * - Automatic fallback to classic `POST /<PANEL_PATH>/sms-in` (`API_CONTRACT.md`) if the server
 *   returns 404/405 on `/sms-pending` or `/sms-approve`.
 */
class ForwardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val manualApproveId = inputData.getLong(KEY_MANUAL_APPROVE_ID, -1L)
            val isBatchRetry = inputData.getBoolean(KEY_BATCH_RETRY_MODE, false)
            val isRecoverySync = inputData.getBoolean(KEY_RECOVERY_SYNC_MODE, false)
            val isLogCleanup = inputData.getBoolean(KEY_LOG_CLEANUP_MODE, false)
            val includeAuth401 = inputData.getBoolean(KEY_RECOVERY_INCLUDE_401, false)
            val recoveryReason = inputData.getString(KEY_RECOVERY_REASON)
                ?: "همگام‌سازی خودکار اتصال پس از خطا"
            val targetIds = inputData.getLongArray(KEY_BATCH_TARGET_IDS)?.toList()
            val outcome = when {
                isLogCleanup -> {
                    runOldLogsCleanupNow(applicationContext, forceRun15Day = false)
                    WorkOutcome.SUCCESS
                }
                manualApproveId > 0L -> executeManualApproveLocked(applicationContext, manualApproveId)
                isRecoverySync -> executeRecoverySyncLocked(applicationContext, includeAuth401, recoveryReason)
                isBatchRetry -> executeBatchRetryLocked(applicationContext, targetIds)
                else -> executeSweepLocked(applicationContext)
            }
            when (outcome) {
                WorkOutcome.SUCCESS -> Result.success()
                WorkOutcome.RETRY -> Result.retry()
                WorkOutcome.FAILURE -> Result.failure()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    enum class WorkOutcome { SUCCESS, RETRY, FAILURE }
    private enum class ApprovalStepOutcome { DONE, RETRY, ABORT_401 }

    companion object {
        const val KEY_MANUAL_APPROVE_ID = "manual_approve_id"
        const val KEY_BATCH_RETRY_MODE = "batch_retry_mode"
        const val KEY_BATCH_TARGET_IDS = "batch_target_ids"
        const val KEY_RECOVERY_SYNC_MODE = "recovery_sync_mode"
        const val KEY_RECOVERY_INCLUDE_401 = "recovery_include_401"
        const val KEY_RECOVERY_REASON = "recovery_reason"
        const val KEY_LOG_CLEANUP_MODE = "log_cleanup_mode"
        private const val UNIQUE_SWEEP_WORK_NAME = "asis_matcher_pending_sweep"
        private const val UNIQUE_BATCH_RETRY_WORK_NAME = "asis_batch_retry_worker"
        private const val UNIQUE_RECOVERY_SYNC_WORK_NAME = "asis_post_error_recovery_sync"
        private const val UNIQUE_LOG_CLEANUP_WORK_NAME = "asis_auto_log_cleanup_15d"
        private const val KEEP_DAYS = 30L
        private val workerMutex = Mutex()

        /**
         * Runs the matcher sweep immediately on `Dispatchers.IO` (protected by `workerMutex`),
         * and schedules a backup `WorkManager` retry only if a retryable error occurs.
         */
        suspend fun runSweepNow(context: Context): WorkOutcome = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val outcome = runCatching { executeSweepLocked(appCtx) }
                .getOrElse { WorkOutcome.RETRY }
            if (outcome == WorkOutcome.RETRY) {
                enqueueMatcherSweep(appCtx)
            }
            outcome
        }

        /**
         * Executes an automated or user-triggered batch retry for failed/retry-pending SMS items,
         * processing up to `BatchRetryPolicy.DEFAULT_BATCH_SIZE` records per batch and scheduling
         * the next exponential-backoff `WorkManager` batch if any retryable failures remain.
         */
        suspend fun runBatchRetryNow(
            context: Context,
            targetIds: List<Long>? = null
        ): WorkOutcome = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            runCatching { executeBatchRetryLocked(appCtx, targetIds) }
                .getOrElse { WorkOutcome.RETRY }
        }

        /**
         * Runs post-error recovery connection sync: probes connectivity/server readiness, resets
         * backoff timers on RETRY records (and eligible FAILED records), and immediately dispatches
         * pending confirmations once the connection has recovered.
         */
        suspend fun runRecoverySyncNow(
            context: Context,
            includeAuth401: Boolean = false,
            reasonFa: String = "همگام‌سازی اتصال بعد از خطاها"
        ): WorkOutcome = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            runCatching { executeRecoverySyncLocked(appCtx, includeAuth401, reasonFa) }
                .getOrElse { WorkOutcome.RETRY }
        }

        /**
         * Runs manual approval of a single record immediately on `Dispatchers.IO`,
         * and schedules a backup `WorkManager` retry if a network error occurs.
         */
        suspend fun runManualApproveNow(context: Context, recordId: Long): WorkOutcome = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val outcome = runCatching { executeManualApproveLocked(appCtx, recordId) }
                .getOrElse { WorkOutcome.RETRY }
            if (outcome == WorkOutcome.RETRY) {
                enqueueManualApprove(appCtx, recordId)
            }
            outcome
        }

        suspend fun runOldLogsCleanupNow(
            context: Context,
            forceRun15Day: Boolean = false
        ): Int = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            workerMutex.withLock {
                val dao = AppDatabase.getInstance(appCtx).logDao()
                val settingsRepo = SettingsRepo.getInstance(appCtx)
                performLogRetentionCleanupLocked(
                    dao = dao,
                    settingsRepo = settingsRepo,
                    nowMs = System.currentTimeMillis(),
                    forceRun15Day = forceRun15Day
                )
            }
        }

        private suspend fun performLogRetentionCleanupLocked(
            dao: LogDao,
            settingsRepo: SettingsRepo,
            nowMs: Long = System.currentTimeMillis(),
            forceRun15Day: Boolean = false
        ): Int {
            val settings = settingsRepo.readSettings()
            val use15DayRule = forceRun15Day || settings.autoClearOldLogsEnabled
            val retentionDays = if (use15DayRule) {
                com.example.data.GatewaySettings.AUTO_CLEAR_LOGS_DAYS
            } else {
                KEEP_DAYS
            }
            val cutoffMs = nowMs - TimeUnit.DAYS.toMillis(retentionDays)
            val deletedSms = dao.deleteOlderThan(cutoffMs)
            dao.deleteOldRetryEvents(cutoffMs)
            if (use15DayRule && (deletedSms > 0 || forceRun15Day)) {
                settingsRepo.recordAutoLogCleanup(deletedSms, nowMs)
            }
            return deletedSms
        }

        private suspend fun executeSweepLocked(appContext: Context): WorkOutcome = workerMutex.withLock {
            val db = AppDatabase.getInstance(appContext)
            val dao = db.logDao()
            val settingsRepo = SettingsRepo.getInstance(appContext)
            val settings = settingsRepo.readSettings()

            // 1. Delete old SMS records & retry telemetry (15 days when enabled, else 30-day cap)
            performLogRetentionCleanupLocked(dao = dao, settingsRepo = settingsRepo)
            dao.releaseStaleClaims()

            val activeDeposits = dao.getActiveDepositsForMatching()
            if (activeDeposits.isEmpty()) {
                return@withLock WorkOutcome.SUCCESS
            }

            if (!settings.isConfigured) {
                val nowMs = System.currentTimeMillis()
                for (rec in activeDeposits) {
                    val nextAttempt = (rec.attempts + 1).coerceAtLeast(1)
                    dao.update(
                        rec.copy(
                            status = SmsStatus.RETRY.name,
                            lastError = "در انتظار ثبت آدرس سرور و توکن در تنظیمات",
                            nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAttempt, nowMs)
                        )
                    )
                }
                refreshBatchRetrySchedule(appContext, dao)
                return@withLock WorkOutcome.RETRY
            }

            // 2. Fetch waiting items from `GET /sms-pending`
            val pendingResult = GatewayApiClient.fetchPendingItems(
                endpointUrl = settings.endpointUrl,
                token = settings.gatewayToken
            )

            val pendingSuccess = when (pendingResult) {
                is PendingFetchResult.Success -> {
                    NotificationHelper.cancelServerErrorNotification(appContext)
                    pendingResult
                }

                is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                    // Server is running classic `/sms-in` without `/sms-pending` -> forward directly to `POST /sms-in`
                    val fallbackOutcome = forwardDirectlyViaSmsIn(appContext, activeDeposits, dao, settingsRepo)
                    refreshBatchRetrySchedule(appContext, dao)
                    return@withLock fallbackOutcome
                }

                is PendingFetchResult.Unauthorized401 -> {
                    val failMs = System.currentTimeMillis()
                    settingsRepo.recordTokenError(pendingResult.message)
                    NotificationHelper.notifyTokenError(appContext, pendingResult.message)
                    for (rec in activeDeposits) {
                        val wasRetry = rec.attempts > 0 || rec.statusEnum == SmsStatus.RETRY
                        dao.update(
                            rec.copy(
                                status = SmsStatus.FAILED.name,
                                httpCode = 401,
                                lastError = pendingResult.message,
                                nextRetryAtMs = null
                            )
                        )
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failMs,
                                    succeeded = false,
                                    httpCode = 401,
                                    isAutoRetry = true
                                )
                            )
                        }
                    }
                    refreshBatchRetrySchedule(appContext, dao)
                    return@withLock WorkOutcome.FAILURE
                }

                is PendingFetchResult.PermanentError -> {
                    val nowMs = System.currentTimeMillis()
                    NotificationHelper.notifyServerConnectionError(
                        context = appContext,
                        errorTitle = "خطای ارتباط با سرور درگاه",
                        errorDetail = pendingResult.message,
                        httpCode = pendingResult.httpCode
                    )
                    for (rec in activeDeposits) {
                        val wasRetry = rec.attempts > 0 || rec.statusEnum == SmsStatus.RETRY
                        val nextAtt = rec.attempts + 1
                        dao.update(
                            rec.copy(
                                status = SmsStatus.RETRY.name,
                                httpCode = pendingResult.httpCode,
                                lastError = pendingResult.message,
                                attempts = nextAtt,
                                nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, nowMs)
                            )
                        )
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = nowMs,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = true
                                )
                            )
                        }
                    }
                    refreshBatchRetrySchedule(appContext, dao)
                    return@withLock WorkOutcome.FAILURE
                }

                is PendingFetchResult.RetryableError -> {
                    val nowMs = System.currentTimeMillis()
                    NotificationHelper.notifyServerConnectionError(
                        context = appContext,
                        errorTitle = "خطای ارتباط با سرور درگاه",
                        errorDetail = pendingResult.message,
                        httpCode = pendingResult.httpCode
                    )
                    for (rec in activeDeposits) {
                        val wasRetry = rec.attempts > 0 || rec.statusEnum == SmsStatus.RETRY
                        val nextAtt = rec.attempts + 1
                        dao.update(
                            rec.copy(
                                status = SmsStatus.RETRY.name,
                                httpCode = pendingResult.httpCode,
                                lastError = pendingResult.message,
                                attempts = nextAtt,
                                nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, nowMs)
                            )
                        )
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = nowMs,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = true
                                )
                            )
                        }
                    }
                    refreshBatchRetrySchedule(appContext, dao)
                    return@withLock WorkOutcome.RETRY
                }
            }

            // Update saved server metadata & clock offset
            val clockOffsetMs = pendingSuccess.clockOffsetMs
            settingsRepo.updateServerSyncMeta(
                clockOffsetMs = clockOffsetMs,
                matchWindowHours = pendingSuccess.matchWindowHours,
                autoMaxToman = pendingSuccess.autoMaxToman
            )
            settingsRepo.recordTokenError(null)

            val nowMs = System.currentTimeMillis()
            if (pendingSuccess.items.isNotEmpty()) {
                dao.upsertServerWaitingInvoices(
                    pendingSuccess.items.map { item ->
                        com.example.data.PendingInvoiceEntity(
                            publicId = item.publicId,
                            kind = item.kind,
                            dueMinor = item.dueMinor,
                            createdMs = item.at,
                            syncedAtMs = nowMs,
                            early = item.early,
                            status = "WAITING"
                        )
                    }
                )
            }

            // 3. Expire `NEW` and `UNKNOWN_DEPOSIT` deposits older than `match_window_hours` (using server clock)
            val windowMs = TimeUnit.HOURS.toMillis(pendingSuccess.matchWindowHours.toLong().coerceAtLeast(1L))
            val phoneCutoffMs = (pendingSuccess.serverNowMs - windowMs) - clockOffsetMs
            dao.expireOldNewDeposits(cutoffMs = phoneCutoffMs, nowMs = nowMs)

            // Re-read active deposits after expiration
            val freshDeposits = dao.getActiveDepositsForMatching()
            if (freshDeposits.isEmpty()) {
                return@withLock WorkOutcome.SUCCESS
            }

            // Combine server items with any local waiting invoices in the database
            val dbWaitingInvoices = dao.getWaitingInvoices()
            val combinedItems = (
                pendingSuccess.items + dbWaitingInvoices.map { inv ->
                    com.example.data.Item(
                        kind = inv.kind,
                        publicId = inv.publicId,
                        dueMinor = inv.dueMinor,
                        at = inv.createdMs,
                        early = inv.early
                    )
                }
            ).distinctBy { it.publicId }

            if (combinedItems.isEmpty()) {
                // No waiting invoices exist on server or local DB -> mark deposits as UNKNOWN_DEPOSIT («واریز ناشناس»)
                // while keeping them eligible for automatic matching if the server sends an invoice request within the window
                for (rec in freshDeposits) {
                    if (rec.statusEnum == SmsStatus.NEW) {
                        val tomanStr = rec.amountMinor?.let { com.example.data.SmsParser.formatToman(it) } ?: "0"
                        dao.update(
                            rec.copy(
                                status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                reason = "unknown_deposit",
                                lastError = "واریز ناشناس: هیچ فاکتور منتظری با مبلغ $tomanStr تومان در سرور/دیتابیس یافت نشد (در صورت استعلام جدید سرور خودکار تطبیق داده می‌شود)."
                            )
                        )
                    }
                }
                return@withLock WorkOutcome.RETRY
            }

            val byId = freshDeposits.associateBy { it.id }
            val matcherDeposits = freshDeposits.mapNotNull { rec ->
                val amt = rec.amountMinor ?: return@mapNotNull null
                // Allow 60-second grace tolerance so deposits arriving within 1 minute of invoice creation always match
                Deposit(
                    id = rec.id,
                    amountMinor = amt,
                    at = rec.receivedMs + clockOffsetMs + 60_000L
                )
            }

            // 4. Plan matches locally via `Matcher.plan`
            val (pairs, ambiguousList) = Matcher.plan(matcherDeposits, combinedItems)

            // 5. Handle ambiguous deposits (`I > D`)
            for (amb in ambiguousList) {
                val rec = byId[amb.id] ?: continue
                dao.update(
                    rec.copy(
                        status = SmsStatus.AMBIGUOUS.name,
                        reason = "same_amount",
                        decidedMs = nowMs,
                        lastError = "چند سفارش منتظر با همین مبلغ وجود دارد؛ بررسی دستی لازم است."
                    )
                )
                NotificationHelper.notifyAmbiguousDeposit(
                    context = appContext,
                    depositId = rec.id,
                    amountMinor = amb.amountMinor
                )
            }

            // 5b. Classify unmatched deposits via SmartAmountMatcher (`MANUAL` vs `UNKNOWN_DEPOSIT`)
            val pairedDepositIds = pairs.map { it.first.id }.toSet()
            val ambiguousDepositIds = ambiguousList.map { it.id }.toSet()
            val waitingInvoiceEntities = combinedItems.map { item ->
                com.example.data.PendingInvoiceEntity(
                    publicId = item.publicId,
                    kind = item.kind,
                    dueMinor = item.dueMinor,
                    createdMs = item.at,
                    early = item.early,
                    status = "WAITING"
                )
            }

            for (rec in freshDeposits) {
                if (rec.id in pairedDepositIds || rec.id in ambiguousDepositIds) continue
                val amt = rec.amountMinor ?: continue
                val decision = com.example.data.SmartAmountMatcher.evaluateDepositAgainstInvoices(
                    amountMinor = amt,
                    depositAtServerMs = rec.receivedMs + clockOffsetMs,
                    waitingInvoices = waitingInvoiceEntities
                )
                when (decision) {
                    is com.example.data.SmartMatchDecision.NeedsManualReview -> {
                        dao.update(
                            rec.copy(
                                status = SmsStatus.MANUAL.name,
                                matchedKind = decision.candidateInvoice.kind,
                                matchedId = decision.candidateInvoice.publicId,
                                reason = "manual_review_needed",
                                decidedMs = nowMs,
                                lastError = decision.reasonFa
                            )
                        )
                    }
                    is com.example.data.SmartMatchDecision.UnknownDeposit -> {
                        if (rec.statusEnum != SmsStatus.UNKNOWN_DEPOSIT) {
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                    reason = "unknown_deposit",
                                    lastError = decision.reasonFa
                                )
                            )
                        }
                    }
                    else -> Unit
                }
            }

            // 6. Process matched pairs `(dep, item)`
            val updatedSettings = settingsRepo.readSettings()
            val maxToman = updatedSettings.effectiveMaxToman
            val limitMinor = if (maxToman > 0L) maxToman * 10L else 0L
            var shouldRetryAny = false

            for ((dep, item) in pairs) {
                val rec = byId[dep.id] ?: continue
                val withinLimit = limitMinor == 0L || item.dueMinor <= limitMinor
                val autoApprove = updatedSettings.autoApproveInApp && withinLimit

                if (!autoApprove) {
                    val whyNote = if (!updatedSettings.autoApproveInApp) {
                        "تأیید خودکار خاموش است"
                    } else {
                        "مبلغ بالاتر از سقف تأیید خودکار است"
                    }
                    val updatedRec = rec.copy(
                        status = SmsStatus.MANUAL.name,
                        matchedKind = item.kind,
                        matchedId = item.publicId,
                        decidedMs = nowMs,
                        lastError = "تطبیق با ${if (item.kind == "topup") "شارژ" else "سفارش"} ${item.publicId} ($whyNote)"
                    )
                    dao.update(updatedRec)
                    NotificationHelper.notifyManualApprovalNeeded(
                        context = appContext,
                        depositId = rec.id,
                        amountMinor = dep.amountMinor,
                        matchedLabel = updatedRec.matchedLabelFa() ?: item.publicId,
                        whyNote = whyNote
                    )
                    continue
                }

                val approveOutcome = claimAndSendApproval(
                    appContext = appContext,
                    rec = rec,
                    itemKind = item.kind,
                    itemPublicId = item.publicId,
                    clockOffsetMs = clockOffsetMs,
                    dao = dao,
                    settingsRepo = settingsRepo
                )
                if (approveOutcome == ApprovalStepOutcome.RETRY) {
                    shouldRetryAny = true
                } else if (approveOutcome == ApprovalStepOutcome.ABORT_401) {
                    return@withLock WorkOutcome.FAILURE
                }
            }

            // deposits that matched nothing yet stay active (NEW / UNKNOWN_DEPOSIT) until `match_window_hours` expires them
            refreshBatchRetrySchedule(appContext, dao)
            val stillWaiting = dao.getActiveDepositsForMatching().any {
                it.statusEnum == SmsStatus.NEW || it.statusEnum == SmsStatus.UNKNOWN_DEPOSIT
            }
            if (shouldRetryAny || stillWaiting) WorkOutcome.RETRY else WorkOutcome.SUCCESS
        }

        private suspend fun executeManualApproveLocked(
            appContext: Context,
            recordId: Long
        ): WorkOutcome = workerMutex.withLock {
            val dao = AppDatabase.getInstance(appContext).logDao()
            val settingsRepo = SettingsRepo.getInstance(appContext)
            val rec = dao.getById(recordId) ?: return@withLock WorkOutcome.SUCCESS
            val settings = settingsRepo.readSettings()
            if (!settings.isConfigured) {
                dao.update(
                    rec.copy(
                        lastError = "ابتدا آدرس سرور و توکن را در صفحهٔ تنظیمات ذخیره کنید."
                    )
                )
                return@withLock WorkOutcome.FAILURE
            }

            val kind = rec.matchedKind ?: "order"
            val publicId = rec.matchedId
            if (publicId.isNullOrBlank()) {
                return@withLock WorkOutcome.SUCCESS
            }

            val outcome = claimAndSendApproval(
                appContext = appContext,
                rec = rec,
                itemKind = kind,
                itemPublicId = publicId,
                clockOffsetMs = settings.lastClockOffsetMs,
                dao = dao,
                settingsRepo = settingsRepo
            )
            when (outcome) {
                ApprovalStepOutcome.DONE -> WorkOutcome.SUCCESS
                ApprovalStepOutcome.RETRY -> WorkOutcome.RETRY
                ApprovalStepOutcome.ABORT_401 -> WorkOutcome.FAILURE
            }
        }

        private suspend fun forwardDirectlyViaSmsIn(
            appContext: Context,
            deposits: List<SmsRecord>,
            dao: LogDao,
            settingsRepo: SettingsRepo,
            isBatchRetryContext: Boolean = false,
            isAutoRetry: Boolean = true
        ): WorkOutcome {
            val settings = settingsRepo.readSettings()
            var shouldRetry = false

            for (entry in deposits) {
                val wasRetry = isBatchRetryContext || entry.attempts > 0 || entry.statusEnum == SmsStatus.RETRY
                val res = GatewayApiClient.sendSms(
                    endpointUrl = settings.endpointUrl,
                    token = settings.gatewayToken,
                    smsText = entry.body,
                    bankCode = entry.bank
                )
                val nowMs = System.currentTimeMillis()
                when (res) {
                    is GatewayResult.Success -> {
                        val fallbackPublicId = "SMS-${entry.id}"
                        dao.update(
                            entry.copy(
                                status = SmsStatus.MATCHED.name,
                                httpCode = res.httpCode,
                                matchedKind = "sms-in",
                                matchedId = "${res.status} (approved=${res.approved})",
                                decidedMs = nowMs,
                                lastError = null,
                                nextRetryAtMs = null
                            )
                        )
                        if (entry.amountMinor != null && entry.amountMinor > 0L) {
                            dao.recordApprovedInvoice(
                                publicId = fallbackPublicId,
                                kind = "sms-in",
                                dueMinor = entry.amountMinor,
                                smsId = entry.id,
                                bankTitle = entry.bankTitle,
                                matchedAtMs = nowMs
                            )
                            NotificationHelper.notifyInvoiceApproved(
                                context = appContext,
                                publicId = fallbackPublicId,
                                kind = "sms-in",
                                amountMinor = entry.amountMinor,
                                bankTitle = entry.bankTitle,
                                recordId = entry.id
                            )
                        }
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = entry.id,
                                    attemptedAtMs = nowMs,
                                    succeeded = true,
                                    httpCode = res.httpCode,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                        }
                        settingsRepo.recordTokenError(null)
                        NotificationHelper.cancelServerErrorNotification(appContext)
                    }

                    is GatewayResult.PermanentFailure -> {
                        val keepForLater = res.isTokenError || res.httpCode == 404
                        val nextAtt = entry.attempts + 1
                        dao.update(
                            entry.copy(
                                status = if (res.isTokenError || !keepForLater) SmsStatus.FAILED.name else SmsStatus.RETRY.name,
                                httpCode = res.httpCode,
                                decidedMs = nowMs,
                                attempts = nextAtt,
                                lastError = res.message,
                                nextRetryAtMs = if (keepForLater && !res.isTokenError) {
                                    BatchRetryPolicy.computeNextRetryAtMs(nextAtt, nowMs)
                                } else {
                                    null
                                }
                            )
                        )
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = entry.id,
                                    attemptedAtMs = nowMs,
                                    succeeded = false,
                                    httpCode = res.httpCode,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                        }
                        if (res.isTokenError) {
                            settingsRepo.recordTokenError(res.message)
                            NotificationHelper.notifyTokenError(appContext, res.message)
                            return WorkOutcome.FAILURE
                        } else {
                            NotificationHelper.notifyServerConnectionError(
                                context = appContext,
                                errorTitle = "خطا در ارسال پیامک به سرور",
                                errorDetail = res.message,
                                httpCode = res.httpCode
                            )
                        }
                    }

                    is GatewayResult.RetryableFailure -> {
                        val nextAtt = entry.attempts + 1
                        dao.update(
                            entry.copy(
                                status = SmsStatus.RETRY.name,
                                httpCode = res.httpCode,
                                attempts = nextAtt,
                                lastError = res.message,
                                nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, nowMs)
                            )
                        )
                        if (wasRetry) {
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = entry.id,
                                    attemptedAtMs = nowMs,
                                    succeeded = false,
                                    httpCode = res.httpCode,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                        }
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = "خطا در ارسال پیامک به سرور",
                            errorDetail = res.message,
                            httpCode = res.httpCode
                        )
                        shouldRetry = true
                        if (res.httpCode == 429) break
                    }
                }
            }
            return if (shouldRetry) WorkOutcome.RETRY else WorkOutcome.SUCCESS
        }

        private suspend fun claimAndSendApproval(
            appContext: Context,
            rec: SmsRecord,
            itemKind: String,
            itemPublicId: String,
            clockOffsetMs: Long,
            dao: LogDao,
            settingsRepo: SettingsRepo,
            isRetryContext: Boolean = false,
            isAutoRetry: Boolean = true
        ): ApprovalStepOutcome {
            val amountMinor = rec.amountMinor ?: return ApprovalStepOutcome.DONE
            val settings = settingsRepo.readSettings()
            val wasRetryRecord = isRetryContext || rec.attempts > 0 || rec.statusEnum == SmsStatus.RETRY

            val claimedRows = dao.claimForApproval(rec.id, itemKind, itemPublicId)
            if (claimedRows == 0 && rec.statusEnum != SmsStatus.CLAIMED) {
                return ApprovalStepOutcome.DONE
            }

            val currentBeforeCall = dao.getById(rec.id) ?: rec
            val serverAlignedReceivedMs = currentBeforeCall.receivedMs + clockOffsetMs
            val serverAlignedNowMs = System.currentTimeMillis() + clockOffsetMs

            val result = GatewayApiClient.sendApproveRequest(
                endpointUrl = settings.endpointUrl,
                token = settings.gatewayToken,
                kind = itemKind,
                publicId = itemPublicId,
                fingerprint = currentBeforeCall.fingerprint,
                amountMinor = amountMinor,
                bank = currentBeforeCall.bank,
                rawSms = currentBeforeCall.body,
                smsReceivedMs = serverAlignedReceivedMs,
                tsMs = serverAlignedNowMs
            )

            val nowMs = System.currentTimeMillis()
            return when (result) {
                is ApproveResult.Approved -> {
                    dao.update(
                        currentBeforeCall.copy(
                            status = SmsStatus.MATCHED.name,
                            matchedKind = itemKind,
                            matchedId = itemPublicId,
                            decidedMs = nowMs,
                            httpCode = result.httpCode,
                            lastError = null,
                            nextRetryAtMs = null
                        )
                    )
                    dao.recordApprovedInvoice(
                        publicId = itemPublicId,
                        kind = itemKind,
                        dueMinor = amountMinor,
                        smsId = rec.id,
                        bankTitle = currentBeforeCall.bankTitle,
                        matchedAtMs = nowMs
                    )
                    NotificationHelper.notifyInvoiceApproved(
                        context = appContext,
                        publicId = itemPublicId,
                        kind = itemKind,
                        amountMinor = amountMinor,
                        bankTitle = currentBeforeCall.bankTitle,
                        recordId = rec.id
                    )
                    if (wasRetryRecord) {
                        dao.insertRetryAttemptEvent(
                            RetryAttemptEventEntity(
                                smsRecordId = rec.id,
                                attemptedAtMs = nowMs,
                                succeeded = true,
                                httpCode = result.httpCode,
                                isAutoRetry = isAutoRetry
                            )
                        )
                    }
                    settingsRepo.recordTokenError(null)
                    NotificationHelper.cancelServerErrorNotification(appContext)
                    ApprovalStepOutcome.DONE
                }

                is ApproveResult.NotFound404FallbackToSmsIn -> {
                    val fallbackOutcome = forwardDirectlyViaSmsIn(
                        appContext = appContext,
                        deposits = listOf(currentBeforeCall),
                        dao = dao,
                        settingsRepo = settingsRepo,
                        isBatchRetryContext = wasRetryRecord,
                        isAutoRetry = isAutoRetry
                    )
                    when (fallbackOutcome) {
                        WorkOutcome.SUCCESS -> ApprovalStepOutcome.DONE
                        WorkOutcome.RETRY -> ApprovalStepOutcome.RETRY
                        WorkOutcome.FAILURE -> ApprovalStepOutcome.ABORT_401
                    }
                }

                is ApproveResult.Conflict409 -> {
                    val updated = currentBeforeCall.copy(
                        status = SmsStatus.MANUAL.name,
                        matchedKind = itemKind,
                        matchedId = itemPublicId,
                        decidedMs = nowMs,
                        httpCode = 409,
                        lastError = result.reason,
                        nextRetryAtMs = null
                    )
                    dao.update(updated)
                    NotificationHelper.notifyManualApprovalNeeded(
                        context = appContext,
                        depositId = rec.id,
                        amountMinor = amountMinor,
                        matchedLabel = updated.matchedLabelFa() ?: itemPublicId,
                        whyNote = "رد توسط قفل ایمنی سرور ۴۰۹: ${result.reason}"
                    )
                    ApprovalStepOutcome.DONE
                }

                is ApproveResult.Unauthorized401 -> {
                    dao.update(
                        currentBeforeCall.copy(
                            status = SmsStatus.FAILED.name,
                            matchedKind = itemKind,
                            matchedId = itemPublicId,
                            httpCode = 401,
                            lastError = result.message,
                            decidedMs = nowMs,
                            nextRetryAtMs = null
                        )
                    )
                    if (wasRetryRecord) {
                        dao.insertRetryAttemptEvent(
                            RetryAttemptEventEntity(
                                smsRecordId = rec.id,
                                attemptedAtMs = nowMs,
                                succeeded = false,
                                httpCode = 401,
                                isAutoRetry = isAutoRetry
                            )
                        )
                    }
                    settingsRepo.recordTokenError(result.message)
                    NotificationHelper.notifyTokenError(appContext, result.message)
                    ApprovalStepOutcome.ABORT_401
                }

                is ApproveResult.RetryableError -> {
                    dao.update(
                        currentBeforeCall.copy(
                            status = SmsStatus.RETRY.name,
                            matchedKind = itemKind,
                            matchedId = itemPublicId,
                            httpCode = result.httpCode,
                            lastError = result.message,
                            nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(
                                attempts = currentBeforeCall.attempts.coerceAtLeast(1),
                                nowMs = nowMs
                            )
                        )
                    )
                    if (wasRetryRecord) {
                        dao.insertRetryAttemptEvent(
                            RetryAttemptEventEntity(
                                smsRecordId = rec.id,
                                attemptedAtMs = nowMs,
                                succeeded = false,
                                httpCode = result.httpCode,
                                isAutoRetry = isAutoRetry
                            )
                        )
                    }
                    NotificationHelper.notifyServerConnectionError(
                        context = appContext,
                        errorTitle = "خطای تأیید سفارش در سرور",
                        errorDetail = result.message,
                        httpCode = result.httpCode
                    )
                    ApprovalStepOutcome.RETRY
                }
            }
        }

        private suspend fun executeBatchRetryLocked(
            appContext: Context,
            targetIds: List<Long>? = null
        ): WorkOutcome = workerMutex.withLock {
            BatchRetryTracker.onBatchStarted()
            val db = AppDatabase.getInstance(appContext)
            val dao = db.logDao()
            val settingsRepo = SettingsRepo.getInstance(appContext)
            val settings = settingsRepo.readSettings()
            val nowMs = System.currentTimeMillis()

            dao.releaseStaleClaims()

            val batchCandidates = if (!targetIds.isNullOrEmpty()) {
                dao.markIdsForImmediateBatchRetry(targetIds, nowMs)
                dao.getRecordsByIds(targetIds).take(BatchRetryPolicy.DEFAULT_BATCH_SIZE)
            } else {
                dao.getRetryPendingRecords()
                    .filter { rec ->
                        BatchRetryPolicy.isAutoRetryEligible(rec) &&
                            rec.statusEnum == SmsStatus.RETRY &&
                            (rec.nextRetryAtMs == null || rec.nextRetryAtMs <= nowMs)
                    }
                    .take(BatchRetryPolicy.DEFAULT_BATCH_SIZE)
            }

            if (batchCandidates.isEmpty()) {
                val nextAt = refreshBatchRetrySchedule(appContext, dao)
                BatchRetryTracker.onBatchCompleted(
                    processedCount = 0,
                    succeededCount = 0,
                    failedCount = 0,
                    nextScheduledWorkAtMs = nextAt,
                    nowMs = nowMs
                )
                return@withLock WorkOutcome.SUCCESS
            }

            if (!settings.isConfigured) {
                val failTime = System.currentTimeMillis()
                for (rec in batchCandidates) {
                    val nextAtt = rec.attempts + 1
                    dao.update(
                        rec.copy(
                            status = SmsStatus.RETRY.name,
                            attempts = nextAtt,
                            lastError = "در انتظار ثبت آدرس سرور و توکن در تنظیمات",
                            nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, failTime)
                        )
                    )
                }
                val nextAt = refreshBatchRetrySchedule(appContext, dao)
                BatchRetryTracker.onBatchCompleted(
                    processedCount = batchCandidates.size,
                    succeededCount = 0,
                    failedCount = batchCandidates.size,
                    nextScheduledWorkAtMs = nextAt,
                    nowMs = failTime
                )
                return@withLock WorkOutcome.RETRY
            }

            var succeededCount = 0
            var failedCount = 0
            val isAutoRetry = targetIds.isNullOrEmpty()

            val directApprovalItems = batchCandidates.filter {
                !it.matchedId.isNullOrBlank() && it.matchedKind != "sms-in"
            }
            val sweepMatchingItems = batchCandidates.filterNot {
                !it.matchedId.isNullOrBlank() && it.matchedKind != "sms-in"
            }

            for (rec in directApprovalItems) {
                val kind = rec.matchedKind ?: "order"
                val publicId = rec.matchedId ?: continue
                when (
                    claimAndSendApproval(
                        appContext = appContext,
                        rec = rec,
                        itemKind = kind,
                        itemPublicId = publicId,
                        clockOffsetMs = settings.lastClockOffsetMs,
                        dao = dao,
                        settingsRepo = settingsRepo,
                        isRetryContext = true,
                        isAutoRetry = isAutoRetry
                    )
                ) {
                    ApprovalStepOutcome.DONE -> {
                        val updated = dao.getById(rec.id)
                        if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                    }
                    ApprovalStepOutcome.RETRY,
                    ApprovalStepOutcome.ABORT_401 -> failedCount++
                }
            }

            if (sweepMatchingItems.isNotEmpty()) {
                when (val pendingResult = GatewayApiClient.fetchPendingItems(settings.endpointUrl, settings.gatewayToken)) {
                    is PendingFetchResult.Success -> {
                        NotificationHelper.cancelServerErrorNotification(appContext)
                        settingsRepo.recordTokenError(null)
                        val clockOffsetMs = pendingResult.clockOffsetMs
                        settingsRepo.updateServerSyncMeta(
                            clockOffsetMs = clockOffsetMs,
                            matchWindowHours = pendingResult.matchWindowHours,
                            autoMaxToman = pendingResult.autoMaxToman
                        )
                        val batchNow = System.currentTimeMillis()
                        if (pendingResult.items.isNotEmpty()) {
                            dao.upsertServerWaitingInvoices(
                                pendingResult.items.map { item ->
                                    com.example.data.PendingInvoiceEntity(
                                        publicId = item.publicId,
                                        kind = item.kind,
                                        dueMinor = item.dueMinor,
                                        createdMs = item.at,
                                        syncedAtMs = batchNow,
                                        early = item.early,
                                        status = "WAITING"
                                    )
                                }
                            )
                        }
                        val dbWaiting = dao.getWaitingInvoices()
                        val combinedItems = (
                            pendingResult.items + dbWaiting.map { inv ->
                                com.example.data.Item(
                                    kind = inv.kind,
                                    publicId = inv.publicId,
                                    dueMinor = inv.dueMinor,
                                    at = inv.createdMs,
                                    early = inv.early
                                )
                            }
                        ).distinctBy { it.publicId }

                        if (combinedItems.isEmpty()) {
                            for (rec in sweepMatchingItems) {
                                val nextAtt = rec.attempts + 1
                                dao.update(
                                    rec.copy(
                                        status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                        reason = "unknown_deposit",
                                        attempts = nextAtt,
                                        nextRetryAtMs = null,
                                        lastError = "واریز ناشناس: فاکتور منتظر با این مبلغ در سرور موجود نیست."
                                    )
                                )
                            }
                        } else {
                            val byId = sweepMatchingItems.associateBy { it.id }
                            val deposits = sweepMatchingItems.mapNotNull { rec ->
                                val amt = rec.amountMinor ?: return@mapNotNull null
                                Deposit(rec.id, amt, rec.receivedMs + clockOffsetMs + 60_000L)
                            }
                            val (pairs, _) = Matcher.plan(deposits, combinedItems)
                            val pairedIds = pairs.map { it.first.id }.toSet()
                            for ((dep, item) in pairs) {
                                val rec = byId[dep.id] ?: continue
                                when (
                                    claimAndSendApproval(
                                        appContext = appContext,
                                        rec = rec,
                                        itemKind = item.kind,
                                        itemPublicId = item.publicId,
                                        clockOffsetMs = clockOffsetMs,
                                        dao = dao,
                                        settingsRepo = settingsRepo,
                                        isRetryContext = true,
                                        isAutoRetry = isAutoRetry
                                    )
                                ) {
                                    ApprovalStepOutcome.DONE -> {
                                        val updated = dao.getById(rec.id)
                                        if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                                    }
                                    ApprovalStepOutcome.RETRY,
                                    ApprovalStepOutcome.ABORT_401 -> failedCount++
                                }
                            }
                            for (rec in sweepMatchingItems) {
                                if (rec.id !in pairedIds) {
                                    dao.update(
                                        rec.copy(
                                            status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                            reason = "unknown_deposit",
                                            nextRetryAtMs = null,
                                            lastError = "واریز ناشناس: پس از بررسی با سرور، فاکتور منطبق یافت نشد."
                                        )
                                    )
                                }
                            }
                        }
                    }

                    is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                        forwardDirectlyViaSmsIn(
                            appContext = appContext,
                            deposits = sweepMatchingItems,
                            dao = dao,
                            settingsRepo = settingsRepo,
                            isBatchRetryContext = true,
                            isAutoRetry = isAutoRetry
                        )
                        for (rec in sweepMatchingItems) {
                            val updated = dao.getById(rec.id)
                            if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                        }
                    }

                    is PendingFetchResult.Unauthorized401 -> {
                        val failTime = System.currentTimeMillis()
                        settingsRepo.recordTokenError(pendingResult.message)
                        NotificationHelper.notifyTokenError(appContext, pendingResult.message)
                        for (rec in sweepMatchingItems) {
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.FAILED.name,
                                    httpCode = 401,
                                    lastError = pendingResult.message,
                                    nextRetryAtMs = null
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = 401,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                            failedCount++
                        }
                    }

                    is PendingFetchResult.PermanentError -> {
                        val failTime = System.currentTimeMillis()
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = "خطای ارسال دسته‌ای به سرور",
                            errorDetail = pendingResult.message,
                            httpCode = pendingResult.httpCode
                        )
                        for (rec in sweepMatchingItems) {
                            val nextAtt = rec.attempts + 1
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.RETRY.name,
                                    httpCode = pendingResult.httpCode,
                                    lastError = pendingResult.message,
                                    attempts = nextAtt,
                                    nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, failTime)
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                            failedCount++
                        }
                    }

                    is PendingFetchResult.RetryableError -> {
                        val failTime = System.currentTimeMillis()
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = "خطای ارسال دسته‌ای به سرور",
                            errorDetail = pendingResult.message,
                            httpCode = pendingResult.httpCode
                        )
                        for (rec in sweepMatchingItems) {
                            val nextAtt = rec.attempts + 1
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.RETRY.name,
                                    httpCode = pendingResult.httpCode,
                                    lastError = pendingResult.message,
                                    attempts = nextAtt,
                                    nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, failTime)
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = isAutoRetry
                                )
                            )
                            failedCount++
                        }
                    }
                }
            }

            val nextScheduledAt = refreshBatchRetrySchedule(appContext, dao)
            BatchRetryTracker.onBatchCompleted(
                processedCount = batchCandidates.size,
                succeededCount = succeededCount,
                failedCount = failedCount,
                nextScheduledWorkAtMs = nextScheduledAt,
                nowMs = System.currentTimeMillis()
            )
            if (failedCount > 0) WorkOutcome.RETRY else WorkOutcome.SUCCESS
        }

        private suspend fun executeRecoverySyncLocked(
            appContext: Context,
            includeAuth401: Boolean,
            reasonFa: String
        ): WorkOutcome = workerMutex.withLock {
            ServerHealthTracker.onRecoverySyncStarted(reasonFa)
            val db = AppDatabase.getInstance(appContext)
            val dao = db.logDao()
            val settingsRepo = SettingsRepo.getInstance(appContext)
            val settings = settingsRepo.readSettings()
            val nowMs = System.currentTimeMillis()

            performLogRetentionCleanupLocked(dao = dao, settingsRepo = settingsRepo, nowMs = nowMs)
            dao.releaseStaleClaims()

            if (!settings.isConfigured || !settings.serviceEnabled) {
                val remaining = dao.getRecoveryCandidateCount()
                ServerHealthTracker.onRecoverySyncCompleted(
                    recoveredCount = 0,
                    remainingErrorCount = remaining,
                    nowMs = nowMs
                )
                return@withLock if (remaining > 0 && settings.autoSyncAfterErrors) {
                    schedulePostErrorRecoveryWorker(appContext, delayMs = 20_000L)
                    WorkOutcome.RETRY
                } else {
                    WorkOutcome.SUCCESS
                }
            }

            // Reset backoff timers & rescue recoverable FAILED items before processing
            dao.prepareErrorRecordsForRecoverySync(includeAuth401 = includeAuth401, nowMs = nowMs)

            val retryItems = dao.getRetryPendingRecords()
                .filter { it.statusEnum == SmsStatus.RETRY }
            val activeItems = dao.getActiveDepositsForMatching()
            val candidates = (retryItems + activeItems).distinctBy { it.id }

            if (candidates.isEmpty()) {
                val pingOk = GatewayApiClient.pingServer(settings.endpointUrl, settings.gatewayToken)
                val remaining = dao.getRecoveryCandidateCount()
                if (pingOk) {
                    settingsRepo.recordTokenError(null)
                    NotificationHelper.cancelServerErrorNotification(appContext)
                    cancelPostErrorRecoveryWorker(appContext)
                } else if (remaining > 0 && settings.autoSyncAfterErrors) {
                    schedulePostErrorRecoveryWorker(appContext, delayMs = 20_000L)
                }
                ServerHealthTracker.onRecoverySyncCompleted(
                    recoveredCount = 0,
                    remainingErrorCount = remaining,
                    nowMs = System.currentTimeMillis()
                )
                return@withLock if (pingOk) WorkOutcome.SUCCESS else WorkOutcome.RETRY
            }

            BatchRetryTracker.onBatchStarted()
            var succeededCount = 0
            var failedCount = 0

            val directApprovalItems = candidates.filter {
                !it.matchedId.isNullOrBlank() && it.matchedKind != "sms-in"
            }
            val sweepMatchingItems = candidates.filterNot {
                !it.matchedId.isNullOrBlank() && it.matchedKind != "sms-in"
            }

            for (rec in directApprovalItems) {
                val kind = rec.matchedKind ?: "order"
                val publicId = rec.matchedId ?: continue
                when (
                    claimAndSendApproval(
                        appContext = appContext,
                        rec = rec,
                        itemKind = kind,
                        itemPublicId = publicId,
                        clockOffsetMs = settings.lastClockOffsetMs,
                        dao = dao,
                        settingsRepo = settingsRepo,
                        isRetryContext = true,
                        isAutoRetry = true
                    )
                ) {
                    ApprovalStepOutcome.DONE -> {
                        val updated = dao.getById(rec.id)
                        if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                    }
                    ApprovalStepOutcome.RETRY,
                    ApprovalStepOutcome.ABORT_401 -> failedCount++
                }
            }

            if (sweepMatchingItems.isNotEmpty()) {
                when (val pendingResult = GatewayApiClient.fetchPendingItems(settings.endpointUrl, settings.gatewayToken)) {
                    is PendingFetchResult.Success -> {
                        NotificationHelper.cancelServerErrorNotification(appContext)
                        settingsRepo.recordTokenError(null)
                        val clockOffsetMs = pendingResult.clockOffsetMs
                        settingsRepo.updateServerSyncMeta(
                            clockOffsetMs = clockOffsetMs,
                            matchWindowHours = pendingResult.matchWindowHours,
                            autoMaxToman = pendingResult.autoMaxToman
                        )
                        val batchNow = System.currentTimeMillis()
                        if (pendingResult.items.isNotEmpty()) {
                            dao.upsertServerWaitingInvoices(
                                pendingResult.items.map { item ->
                                    com.example.data.PendingInvoiceEntity(
                                        publicId = item.publicId,
                                        kind = item.kind,
                                        dueMinor = item.dueMinor,
                                        createdMs = item.at,
                                        syncedAtMs = batchNow,
                                        early = item.early,
                                        status = "WAITING"
                                    )
                                }
                            )
                        }
                        val dbWaiting = dao.getWaitingInvoices()
                        val combinedItems = (
                            pendingResult.items + dbWaiting.map { inv ->
                                com.example.data.Item(
                                    kind = inv.kind,
                                    publicId = inv.publicId,
                                    dueMinor = inv.dueMinor,
                                    at = inv.createdMs,
                                    early = inv.early
                                )
                            }
                        ).distinctBy { it.publicId }

                        if (combinedItems.isEmpty()) {
                            for (rec in sweepMatchingItems) {
                                if (rec.statusEnum == SmsStatus.RETRY || rec.statusEnum == SmsStatus.NEW) {
                                    dao.update(
                                        rec.copy(
                                            status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                            reason = "unknown_deposit",
                                            nextRetryAtMs = null,
                                            lastError = "واریز ناشناس: پس از اتصال مجدد، فاکتور منتظر با این مبلغ در سرور یافت نشد."
                                        )
                                    )
                                }
                            }
                        } else {
                            val byId = sweepMatchingItems.associateBy { it.id }
                            val deposits = sweepMatchingItems.mapNotNull { rec ->
                                val amt = rec.amountMinor ?: return@mapNotNull null
                                Deposit(rec.id, amt, rec.receivedMs + clockOffsetMs + 60_000L)
                            }
                            val (pairs, _) = Matcher.plan(deposits, combinedItems)
                            val pairedIds = pairs.map { it.first.id }.toSet()
                            for ((dep, item) in pairs) {
                                val rec = byId[dep.id] ?: continue
                                when (
                                    claimAndSendApproval(
                                        appContext = appContext,
                                        rec = rec,
                                        itemKind = item.kind,
                                        itemPublicId = item.publicId,
                                        clockOffsetMs = clockOffsetMs,
                                        dao = dao,
                                        settingsRepo = settingsRepo,
                                        isRetryContext = true,
                                        isAutoRetry = true
                                    )
                                ) {
                                    ApprovalStepOutcome.DONE -> {
                                        val updated = dao.getById(rec.id)
                                        if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                                    }
                                    ApprovalStepOutcome.RETRY,
                                    ApprovalStepOutcome.ABORT_401 -> failedCount++
                                }
                            }
                            for (rec in sweepMatchingItems) {
                                if (rec.id !in pairedIds && rec.statusEnum != SmsStatus.UNKNOWN_DEPOSIT) {
                                    dao.update(
                                        rec.copy(
                                            status = SmsStatus.UNKNOWN_DEPOSIT.name,
                                            reason = "unknown_deposit",
                                            nextRetryAtMs = null,
                                            lastError = "واریز ناشناس: پس از بازیابی اتصال، فاکتور منطبق یافت نشد."
                                        )
                                    )
                                }
                            }
                        }
                    }

                    is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                        forwardDirectlyViaSmsIn(
                            appContext = appContext,
                            deposits = sweepMatchingItems,
                            dao = dao,
                            settingsRepo = settingsRepo,
                            isBatchRetryContext = true,
                            isAutoRetry = true
                        )
                        for (rec in sweepMatchingItems) {
                            val updated = dao.getById(rec.id)
                            if (updated?.statusEnum == SmsStatus.MATCHED) succeededCount++ else failedCount++
                        }
                    }

                    is PendingFetchResult.Unauthorized401 -> {
                        val failTime = System.currentTimeMillis()
                        settingsRepo.recordTokenError(pendingResult.message)
                        NotificationHelper.notifyTokenError(appContext, pendingResult.message)
                        for (rec in sweepMatchingItems) {
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.FAILED.name,
                                    httpCode = 401,
                                    lastError = pendingResult.message,
                                    nextRetryAtMs = null
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = 401,
                                    isAutoRetry = true
                                )
                            )
                            failedCount++
                        }
                    }

                    is PendingFetchResult.PermanentError -> {
                        val failTime = System.currentTimeMillis()
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = "خطای همگام‌سازی پس از اتصال",
                            errorDetail = pendingResult.message,
                            httpCode = pendingResult.httpCode
                        )
                        for (rec in sweepMatchingItems) {
                            val nextAtt = rec.attempts + 1
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.RETRY.name,
                                    httpCode = pendingResult.httpCode,
                                    lastError = pendingResult.message,
                                    attempts = nextAtt,
                                    nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, failTime)
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = true
                                )
                            )
                            failedCount++
                        }
                    }

                    is PendingFetchResult.RetryableError -> {
                        val failTime = System.currentTimeMillis()
                        NotificationHelper.notifyServerConnectionError(
                            context = appContext,
                            errorTitle = "خطای همگام‌سازی پس از اتصال",
                            errorDetail = pendingResult.message,
                            httpCode = pendingResult.httpCode
                        )
                        for (rec in sweepMatchingItems) {
                            val nextAtt = rec.attempts + 1
                            dao.update(
                                rec.copy(
                                    status = SmsStatus.RETRY.name,
                                    httpCode = pendingResult.httpCode,
                                    lastError = pendingResult.message,
                                    attempts = nextAtt,
                                    nextRetryAtMs = BatchRetryPolicy.computeNextRetryAtMs(nextAtt, failTime)
                                )
                            )
                            dao.insertRetryAttemptEvent(
                                RetryAttemptEventEntity(
                                    smsRecordId = rec.id,
                                    attemptedAtMs = failTime,
                                    succeeded = false,
                                    httpCode = pendingResult.httpCode,
                                    isAutoRetry = true
                                )
                            )
                            failedCount++
                        }
                    }
                }
            }

            val finishMs = System.currentTimeMillis()
            val remainingErrors = dao.getRecoveryCandidateCount()
            val nextScheduledAt = refreshBatchRetrySchedule(appContext, dao)
            BatchRetryTracker.onBatchCompleted(
                processedCount = candidates.size,
                succeededCount = succeededCount,
                failedCount = failedCount,
                nextScheduledWorkAtMs = nextScheduledAt,
                nowMs = finishMs
            )
            if (succeededCount > 0) {
                settingsRepo.recordErrorRecoverySync(succeededCount, finishMs)
            }
            if (failedCount == 0 && remainingErrors == 0) {
                NotificationHelper.cancelServerErrorNotification(appContext)
                cancelPostErrorRecoveryWorker(appContext)
            } else if (settings.autoSyncAfterErrors && remainingErrors > 0) {
                schedulePostErrorRecoveryWorker(appContext, delayMs = 20_000L)
            }
            ServerHealthTracker.onRecoverySyncCompleted(
                recoveredCount = succeededCount,
                remainingErrorCount = remainingErrors,
                nowMs = finishMs
            )
            if (failedCount > 0) WorkOutcome.RETRY else WorkOutcome.SUCCESS
        }

        private suspend fun refreshBatchRetrySchedule(appContext: Context, dao: LogDao): Long? {
            val pending = dao.getRetryPendingRecords()
            val earliest = BatchRetryPolicy.earliestAutoRetryAtMs(pending)
            BatchRetryTracker.updateNextScheduledWork(earliest)
            if (earliest != null) {
                val delayMs = (earliest - System.currentTimeMillis()).coerceAtLeast(1_000L)
                scheduleDelayedBatchRetry(appContext, delayMs)
                if (SettingsRepo.getInstance(appContext).readSettings().autoSyncAfterErrors) {
                    schedulePostErrorRecoveryWorker(appContext, delayMs = delayMs.coerceAtLeast(15_000L))
                }
            } else {
                runCatching {
                    WorkManager.getInstance(appContext.applicationContext)
                        .cancelUniqueWork(UNIQUE_BATCH_RETRY_WORK_NAME)
                }
                if (pending.isEmpty()) {
                    cancelPostErrorRecoveryWorker(appContext)
                }
            }
            return earliest
        }

        fun scheduleDelayedBatchRetry(context: Context, delayMs: Long) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setInitialDelay(delayMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_BATCH_RETRY_MODE to true))
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15L,
                        TimeUnit.SECONDS
                    )
                    .addTag(UNIQUE_BATCH_RETRY_WORK_NAME)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_BATCH_RETRY_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun schedulePostErrorRecoveryWorker(
            context: Context,
            includeAuth401: Boolean = false,
            delayMs: Long = 1_500L,
            reasonFa: String = "بازگشت اتصال و همگام‌سازی خطاها"
        ) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            KEY_RECOVERY_SYNC_MODE to true,
                            KEY_RECOVERY_INCLUDE_401 to includeAuth401,
                            KEY_RECOVERY_REASON to reasonFa
                        )
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15L,
                        TimeUnit.SECONDS
                    )
                    .addTag(UNIQUE_RECOVERY_SYNC_WORK_NAME)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_RECOVERY_SYNC_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun cancelPostErrorRecoveryWorker(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_RECOVERY_SYNC_WORK_NAME)
            }
        }

        fun schedulePeriodicLogCleanupWorker(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<ForwardWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_LOG_CLEANUP_MODE to true))
                    .addTag(UNIQUE_LOG_CLEANUP_WORK_NAME)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_LOG_CLEANUP_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun cancelPeriodicLogCleanupWorker(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_LOG_CLEANUP_WORK_NAME)
            }
        }

        fun enqueueMatcherSweep(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30L,
                        TimeUnit.SECONDS
                    )
                    .addTag(UNIQUE_SWEEP_WORK_NAME)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_SWEEP_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun enqueueManualApprove(context: Context, recordId: Long) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setInputData(workDataOf(KEY_MANUAL_APPROVE_ID to recordId))
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30L,
                        TimeUnit.SECONDS
                    )
                    .addTag("asis_manual_approve_$recordId")
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "asis_manual_approve_$recordId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun enqueuePendingAll(context: Context) = enqueueMatcherSweep(context)
    }
}
