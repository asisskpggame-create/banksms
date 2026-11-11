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
import com.example.data.LogEntry
import com.example.data.LogStatus
import com.example.data.SettingsRepo
import com.example.network.GatewayApiClient
import com.example.network.GatewayResult
import com.example.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that forwards queued bank SMS messages to the ASiS server
 * following `spec/API_CONTRACT.md` retry/backoff rules:
 * - 2xx -> SENT (removed from active retry queue)
 * - 401 -> FAILED (Invalid token, no automatic retry, user notified)
 * - 404 / 400 -> FAILED (Permanent error, logged, no automatic retry)
 * - 429 / 5xx / Network error -> RETRY with exponential backoff
 */
class ForwardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.logDao()
        val settingsRepo = SettingsRepo.getInstance(applicationContext)
        val settings = settingsRepo.readSettings()

        val targetLogId = inputData.getLong(KEY_LOG_ID, -1L)
        val logsToProcess: List<LogEntry> = if (targetLogId > 0L) {
            listOfNotNull(dao.getLogById(targetLogId)).filter {
                it.statusEnum == LogStatus.QUEUED || it.statusEnum == LogStatus.RETRY
            }
        } else {
            dao.getPendingLogs()
        }

        if (logsToProcess.isEmpty()) {
            return Result.success()
        }

        // If user hasn't configured URL/token yet, keep items in queue to retry when configured
        if (!settings.isConfigured) {
            val now = System.currentTimeMillis()
            for (entry in logsToProcess) {
                dao.updateLog(
                    entry.copy(
                        status = LogStatus.RETRY.name,
                        errorMessage = "در انتظار تکمیل آدرس سرور و توکن در صفحهٔ تنظیمات",
                        updatedAt = now
                    )
                )
            }
            return Result.retry()
        }

        var shouldRetryQueue = false

        for (entry in logsToProcess) {
            val result = GatewayApiClient.sendSms(
                endpointUrl = settings.endpointUrl,
                token = settings.gatewayToken,
                smsText = entry.body,
                bankCode = entry.bankCode
            )
            val now = System.currentTimeMillis()

            when (result) {
                is GatewayResult.Success -> {
                    dao.updateLog(
                        entry.copy(
                            status = LogStatus.SENT.name,
                            httpCode = result.httpCode,
                            serverStatus = result.status,
                            approvedCount = result.approved,
                            errorMessage = null,
                            updatedAt = now
                        )
                    )
                    settingsRepo.recordTokenError(null)
                }

                is GatewayResult.PermanentFailure -> {
                    dao.updateLog(
                        entry.copy(
                            status = LogStatus.FAILED.name,
                            httpCode = result.httpCode,
                            errorMessage = result.message,
                            updatedAt = now
                        )
                    )
                    if (result.isTokenError) {
                        settingsRepo.recordTokenError(result.message)
                        NotificationHelper.notifyTokenError(applicationContext, result.message)
                        // Stop processing further items with a bad 401 token to avoid triggering 429 rate-limiter
                        return Result.failure()
                    }
                }

                is GatewayResult.RetryableFailure -> {
                    dao.updateLog(
                        entry.copy(
                            status = LogStatus.RETRY.name,
                            httpCode = result.httpCode,
                            errorMessage = result.message,
                            retryCount = entry.retryCount + 1,
                            updatedAt = now
                        )
                    )
                    shouldRetryQueue = true
                    if (result.httpCode == 429) {
                        // Respect server rate limit immediately and wait for backoff
                        break
                    }
                }
            }
        }

        return if (shouldRetryQueue) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_LOG_ID = "log_id"
        private const val UNIQUE_SWEEP_WORK_NAME = "asis_forward_pending_sweep"

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun enqueueForLog(context: Context, logId: Long) {
            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setConstraints(networkConstraints())
                .setInputData(workDataOf(KEY_LOG_ID to logId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .addTag("sms_forward_$logId")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "asis_forward_log_$logId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueuePendingAll(context: Context) {
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
}
