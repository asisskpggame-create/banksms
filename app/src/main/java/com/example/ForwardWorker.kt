package com.example

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.AppDatabase
import com.example.data.Deposit
import com.example.data.Matcher
import com.example.data.SettingsRepo
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.ApproveResult
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import com.example.network.PendingFetchResult
import com.example.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Implements `MatcherWorker` (`APP_SIDE_APPROVAL_SPEC.md`) with automatic fallback to
 * classic `POST /<PANEL_PATH>/sms-in` (`API_CONTRACT.md`) if the server does not have `/sms-pending` yet.
 */
class ForwardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            executeWorkInternal()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private suspend fun executeWorkInternal(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.logDao()
        val settingsRepo = SettingsRepo.getInstance(applicationContext)
        val settings = settingsRepo.readSettings()

        // 1. Delete old SMS records (> 30 days)
        val thirtyDaysAgoMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
        dao.deleteOlderThan(thirtyDaysAgoMs)

        // Check if this is an explicit manual approval of a single `MANUAL` or `RETRY` record
        val manualApproveId = inputData.getLong(KEY_MANUAL_APPROVE_ID, -1L)
        if (manualApproveId > 0L) {
            return executeManualApprove(manualApproveId, dao, settingsRepo)
        }

        val activeDeposits = dao.getActiveDepositsForMatching()
        if (activeDeposits.isEmpty()) {
            return Result.success()
        }

        if (!settings.isConfigured) {
            for (rec in activeDeposits) {
                dao.update(
                    rec.copy(
                        status = SmsStatus.RETRY.name,
                        lastError = "در انتظار ثبت آدرس سرور و توکن در تنظیمات"
                    )
                )
            }
            return Result.retry()
        }

        // 2. Fetch waiting items from `GET /sms-pending`
        val pendingResult = GatewayApiClient.fetchPendingItems(
            endpointUrl = settings.endpointUrl,
            token = settings.gatewayToken
        )

        val pendingSuccess = when (pendingResult) {
            is PendingFetchResult.Success -> pendingResult

            is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                // Server is running classic `/sms-in` without `/sms-pending` -> forward directly to `POST /sms-in`!
                return forwardDirectlyViaSmsIn(activeDeposits, dao, settingsRepo)
            }

            is PendingFetchResult.Unauthorized401 -> {
                settingsRepo.recordTokenError(pendingResult.message)
                NotificationHelper.notifyTokenError(applicationContext, pendingResult.message)
                for (rec in activeDeposits) {
                    dao.update(
                        rec.copy(
                            status = SmsStatus.FAILED.name,
                            httpCode = 401,
                            lastError = pendingResult.message
                        )
                    )
                }
                return Result.failure()
            }

            is PendingFetchResult.PermanentError -> {
                for (rec in activeDeposits) {
                    dao.update(
                        rec.copy(
                            status = SmsStatus.RETRY.name,
                            httpCode = pendingResult.httpCode,
                            lastError = pendingResult.message
                        )
                    )
                }
                return Result.failure()
            }

            is PendingFetchResult.RetryableError -> {
                for (rec in activeDeposits) {
                    dao.update(
                        rec.copy(
                            status = SmsStatus.RETRY.name,
                            httpCode = pendingResult.httpCode,
                            lastError = pendingResult.message,
                            attempts = rec.attempts + 1
                        )
                    )
                }
                return Result.retry()
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

        // 3. Expire `NEW` deposits older than `match_window_hours` (using server clock)
        val windowMs = TimeUnit.HOURS.toMillis(pendingSuccess.matchWindowHours.toLong().coerceAtLeast(1L))
        val phoneCutoffMs = (pendingSuccess.serverNowMs - windowMs) - clockOffsetMs
        dao.expireOldNewDeposits(cutoffMs = phoneCutoffMs, nowMs = System.currentTimeMillis())

        // Re-read active deposits after expiration
        val freshDeposits = dao.getActiveDepositsForMatching()
        if (freshDeposits.isEmpty() || pendingSuccess.items.isEmpty()) {
            return Result.success()
        }

        val byId = freshDeposits.associateBy { it.id }
        val matcherDeposits = freshDeposits.mapNotNull { rec ->
            val amt = rec.amountMinor ?: return@mapNotNull null
            Deposit(
                id = rec.id,
                amountMinor = amt,
                at = rec.receivedMs + clockOffsetMs
            )
        }

        // 4. Plan matches locally via `Matcher.plan`
        val (pairs, ambiguousList) = Matcher.plan(matcherDeposits, pendingSuccess.items)

        // 5. Handle ambiguous deposits (`I > D`)
        val nowMs = System.currentTimeMillis()
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
                context = applicationContext,
                depositId = rec.id,
                amountMinor = amb.amountMinor
            )
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
                    context = applicationContext,
                    depositId = rec.id,
                    amountMinor = dep.amountMinor,
                    matchedLabel = updatedRec.matchedLabelFa() ?: item.publicId,
                    whyNote = whyNote
                )
                continue
            }

            val approveOutcome = claimAndSendApproval(
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
                return Result.failure()
            }
        }

        return if (shouldRetryAny) Result.retry() else Result.success()
    }

    /**
     * Automatic fallback to `POST /<PANEL_PATH>/sms-in` when the server only implements
     * the classic `sms_in.py` endpoint and returns 404 on `/sms-pending`.
     */
    private suspend fun forwardDirectlyViaSmsIn(
        deposits: List<SmsRecord>,
        dao: com.example.data.LogDao,
        settingsRepo: SettingsRepo
    ): Result {
        val settings = settingsRepo.readSettings()
        var shouldRetry = false

        for (entry in deposits) {
            val res = GatewayApiClient.sendSms(
                endpointUrl = settings.endpointUrl,
                token = settings.gatewayToken,
                smsText = entry.body,
                bankCode = entry.bank
            )
            val nowMs = System.currentTimeMillis()
            when (res) {
                is GatewayResult.Success -> {
                    dao.update(
                        entry.copy(
                            status = SmsStatus.MATCHED.name,
                            httpCode = res.httpCode,
                            matchedKind = "sms-in",
                            matchedId = "${res.status} (approved=${res.approved})",
                            decidedMs = nowMs,
                            lastError = null
                        )
                    )
                    settingsRepo.recordTokenError(null)
                }

                is GatewayResult.PermanentFailure -> {
                    dao.update(
                        entry.copy(
                            status = SmsStatus.FAILED.name,
                            httpCode = res.httpCode,
                            decidedMs = nowMs,
                            lastError = res.message
                        )
                    )
                    if (res.isTokenError) {
                        settingsRepo.recordTokenError(res.message)
                        NotificationHelper.notifyTokenError(applicationContext, res.message)
                        return Result.failure()
                    }
                }

                is GatewayResult.RetryableFailure -> {
                    dao.update(
                        entry.copy(
                            status = SmsStatus.RETRY.name,
                            httpCode = res.httpCode,
                            attempts = entry.attempts + 1,
                            lastError = res.message
                        )
                    )
                    shouldRetry = true
                    if (res.httpCode == 429) break
                }
            }
        }
        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun executeManualApprove(
        recordId: Long,
        dao: com.example.data.LogDao,
        settingsRepo: SettingsRepo
    ): Result {
        val rec = dao.getById(recordId) ?: return Result.success()
        val settings = settingsRepo.readSettings()
        if (!settings.isConfigured) {
            dao.update(
                rec.copy(
                    lastError = "ابتدا آدرس سرور و توکن را در صفحهٔ تنظیمات ذخیره کنید."
                )
            )
            return Result.failure()
        }

        val kind = rec.matchedKind ?: "order"
        val publicId = rec.matchedId
        if (publicId.isNullOrBlank()) {
            return Result.success()
        }

        val outcome = claimAndSendApproval(
            rec = rec,
            itemKind = kind,
            itemPublicId = publicId,
            clockOffsetMs = settings.lastClockOffsetMs,
            dao = dao,
            settingsRepo = settingsRepo
        )
        return when (outcome) {
            ApprovalStepOutcome.DONE -> Result.success()
            ApprovalStepOutcome.RETRY -> Result.retry()
            ApprovalStepOutcome.ABORT_401 -> Result.failure()
        }
    }

    private enum class ApprovalStepOutcome { DONE, RETRY, ABORT_401 }

    private suspend fun claimAndSendApproval(
        rec: SmsRecord,
        itemKind: String,
        itemPublicId: String,
        clockOffsetMs: Long,
        dao: com.example.data.LogDao,
        settingsRepo: SettingsRepo
    ): ApprovalStepOutcome {
        val amountMinor = rec.amountMinor ?: return ApprovalStepOutcome.DONE
        val settings = settingsRepo.readSettings()

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
                        lastError = null
                    )
                )
                settingsRepo.recordTokenError(null)
                ApprovalStepOutcome.DONE
            }

            is ApproveResult.Conflict409 -> {
                val updated = currentBeforeCall.copy(
                    status = SmsStatus.MANUAL.name,
                    matchedKind = itemKind,
                    matchedId = itemPublicId,
                    decidedMs = nowMs,
                    httpCode = 409,
                    lastError = result.reason
                )
                dao.update(updated)
                NotificationHelper.notifyManualApprovalNeeded(
                    context = applicationContext,
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
                        httpCode = 401,
                        lastError = result.message,
                        decidedMs = nowMs
                    )
                )
                settingsRepo.recordTokenError(result.message)
                NotificationHelper.notifyTokenError(applicationContext, result.message)
                ApprovalStepOutcome.ABORT_401
            }

            is ApproveResult.RetryableError -> {
                dao.update(
                    currentBeforeCall.copy(
                        status = SmsStatus.RETRY.name,
                        matchedKind = itemKind,
                        matchedId = itemPublicId,
                        httpCode = result.httpCode,
                        lastError = result.message
                    )
                )
                ApprovalStepOutcome.RETRY
            }
        }
    }

    companion object {
        const val KEY_MANUAL_APPROVE_ID = "manual_approve_id"
        private const val UNIQUE_SWEEP_WORK_NAME = "asis_matcher_pending_sweep"
        private const val KEEP_DAYS = 30L

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun enqueueMatcherSweep(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setConstraints(networkConstraints())
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
                    .setConstraints(networkConstraints())
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
