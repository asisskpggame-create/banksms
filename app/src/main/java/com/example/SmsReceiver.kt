package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AppDatabase
import com.example.data.SensitiveSmsReason
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Implements Section 1 & 2 of `APP_SIDE_APPROVAL_SPEC.md`:
 * - Uses `Telephony.Sms.Intents.getMessagesFromIntent(intent)` and concatenates all PDUs in order:
 *   `messages.joinToString("") { it.messageBody ?: "" }`.
 * - Preserves raw unmodified text in `body`; normalization is performed only on a copy.
 * - Stores `bank = (messages[0].originatingAddress ?: "").take(32)`.
 * - Stores both `receivedMs = messages[0].timestampMillis` and `storedMs = System.currentTimeMillis()`.
 * - Deduplicates locally via Room unique `fingerprint` index.
 * - If `amountMinor == null` -> stores `status = IGNORED` (`+reason`).
 * - If `amountMinor != null` -> stores `status = NEW` and wakes up `ForwardWorker.enqueueMatcherSweep`.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settingsRepo = SettingsRepo.getInstance(context)
        val settings = settingsRepo.readSettings()
        if (!settings.serviceEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val firstMsg = messages[0] ?: return
        val rawBody = messages.joinToString("") { it?.messageBody ?: "" }
        if (rawBody.isEmpty()) return

        val bank = (firstMsg.originatingAddress ?: "").take(32)
        val receivedAtMs = firstMsg.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val storedAtMs = System.currentTimeMillis()

        // personal mobile numbers are never banks: ignore (and do not store) those SMS entirely
        if (SmsParser.isPersonalSender(bank)) return

        if (!settingsRepo.shouldProcessBank(bank, rawBody)) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ingestIncomingSms(
                    appContext = appContext,
                    sender = bank,
                    rawBody = rawBody,
                    receivedAtMs = receivedAtMs,
                    storedAtMs = storedAtMs,
                    sendPushAlerts = true
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Processes a real incoming bank SMS, stores it in Room (if not from a personal mobile
         * sender and not a duplicate), dispatches real-time sensitive SMS push notifications if
         * configured, and enqueues `ForwardWorker.enqueueMatcherSweep` when a deposit is recorded.
         */
        suspend fun ingestIncomingSms(
            appContext: Context,
            sender: String,
            rawBody: String,
            receivedAtMs: Long = System.currentTimeMillis(),
            storedAtMs: Long = System.currentTimeMillis(),
            sendPushAlerts: Boolean = true
        ): Boolean {
            if (rawBody.isBlank()) return false
            val bank = sender.trim().take(32)

            // personal mobile numbers are never banks: ignore (and do not store) those SMS entirely
            if (SmsParser.isPersonalSender(bank)) return false

            val settingsRepo = SettingsRepo.getInstance(appContext)
            val settings = settingsRepo.readSettings()
            if (!settings.serviceEnabled) return false
            if (!settingsRepo.shouldProcessBank(bank, rawBody)) return false

            val dao = AppDatabase.getInstance(appContext).logDao()
            val normalized = SmsParser.normalize(rawBody)
            val fp = SmsParser.fingerprint(bank, rawBody)

            // Check local duplicate by fingerprint
            if (dao.getByFingerprint(fp) != null) {
                return false
            }

            val parsed = SmsParser.parse(rawBody)
            // Promotional / link SMS are never stored in logs
            if (parsed.reason == "promo") {
                return false
            }

            val detectedBank = SmsParser.detectBank(bank, rawBody)
            val isKnownBank = detectedBank.senderKeywords.isNotEmpty() || detectedBank.bodyKeywords.isNotEmpty()
            // Ignore non-deposit messages from unknown/promotional shortcodes that are not Iranian banks
            if (parsed.amountMinor == null && !isKnownBank) {
                return false
            }

            val waitingInvoices = dao.getWaitingInvoices()
            var initialStatus = SmsStatus.NEW
            var matchedKind: String? = null
            var matchedId: String? = null
            var effectiveReason = parsed.reason
            var decisionNote: String? = null

            if (parsed.amountMinor == null) {
                initialStatus = SmsStatus.IGNORED
            } else if (waitingInvoices.isNotEmpty()) {
                val depositServerMs = receivedAtMs + settings.lastClockOffsetMs
                when (val decision = com.example.data.SmartAmountMatcher.evaluateDepositAgainstInvoices(
                    amountMinor = parsed.amountMinor,
                    depositAtServerMs = depositServerMs,
                    waitingInvoices = waitingInvoices
                )) {
                    is com.example.data.SmartMatchDecision.ExactMatch -> {
                        initialStatus = SmsStatus.NEW
                        matchedKind = decision.invoice.kind
                        matchedId = decision.invoice.publicId
                    }
                    is com.example.data.SmartMatchDecision.AmbiguousSameAmount -> {
                        initialStatus = SmsStatus.AMBIGUOUS
                        effectiveReason = "same_amount"
                        decisionNote = decision.reasonFa
                    }
                    is com.example.data.SmartMatchDecision.NeedsManualReview -> {
                        initialStatus = SmsStatus.MANUAL
                        matchedKind = decision.candidateInvoice.kind
                        matchedId = decision.candidateInvoice.publicId
                        effectiveReason = "manual_review_needed"
                        decisionNote = decision.reasonFa
                    }
                    is com.example.data.SmartMatchDecision.UnknownDeposit -> {
                        initialStatus = SmsStatus.UNKNOWN_DEPOSIT
                        effectiveReason = "unknown_deposit"
                        decisionNote = decision.reasonFa
                    }
                    com.example.data.SmartMatchDecision.NoActiveInvoicesInDb -> {
                        initialStatus = SmsStatus.NEW
                    }
                }
            }

            val record = SmsRecord(
                fingerprint = fp,
                bank = bank,
                bankTitle = detectedBank.titleFa,
                body = rawBody, // Raw unmodified SMS text preserved per Section 2
                normalized = normalized,
                amountMinor = parsed.amountMinor,
                reason = effectiveReason,
                status = initialStatus.name,
                matchedKind = matchedKind,
                matchedId = matchedId,
                receivedMs = receivedAtMs,
                storedMs = storedAtMs,
                decidedMs = if (initialStatus == SmsStatus.IGNORED) storedAtMs else null,
                lastError = decisionNote
            )

            val rowId = dao.insertIgnoreDuplicate(record)
            if (rowId != -1L && sendPushAlerts) {
                val sensitiveAlert = SmsParser.classifySensitiveBankSms(
                    rawText = rawBody,
                    parsed = parsed,
                    status = initialStatus,
                    minTomanThreshold = settings.sensitiveSmsMinToman,
                    includeReviewAlerts = settings.notifyOnAmbiguousOrManual
                )
                if (sensitiveAlert != null) {
                    when (sensitiveAlert.reason) {
                        SensitiveSmsReason.HIGH_VALUE_DEPOSIT,
                        SensitiveSmsReason.DEBIT_OR_WITHDRAWAL,
                        SensitiveSmsReason.SECURITY_OR_OTP -> {
                            if (settings.notifyOnSensitiveBankSms) {
                                NotificationHelper.notifySensitiveBankSms(
                                    context = appContext,
                                    smsId = rowId,
                                    bankTitle = detectedBank.titleFa,
                                    categoryTitleFa = sensitiveAlert.categoryTitleFa,
                                    amountMinor = sensitiveAlert.amountMinor,
                                    smsBody = rawBody
                                )
                            }
                        }

                        SensitiveSmsReason.AMBIGUOUS_OR_NEEDS_REVIEW -> {
                            val isDirectReviewState = initialStatus == SmsStatus.UNKNOWN_DEPOSIT ||
                                initialStatus == SmsStatus.MANUAL ||
                                initialStatus == SmsStatus.AMBIGUOUS ||
                                parsed.reason in listOf("mixed_direction", "several_amounts")
                            if (isDirectReviewState && settings.notifyOnAmbiguousOrManual) {
                                NotificationHelper.notifySensitiveBankSms(
                                    context = appContext,
                                    smsId = rowId,
                                    bankTitle = detectedBank.titleFa,
                                    categoryTitleFa = sensitiveAlert.categoryTitleFa,
                                    amountMinor = sensitiveAlert.amountMinor,
                                    smsBody = rawBody,
                                    bypassPrefs = true
                                )
                            }
                        }
                    }
                }
            }

            if (rowId != -1L && parsed.amountMinor != null) {
                // WorkManager, not a network call inside goAsync(): the system ends a receiver after ~10 s
                ForwardWorker.enqueueMatcherSweep(appContext)
                return true
            }
            return false
        }

        /**
         * Uses `READ_SMS` permission to scan recent inbox messages within the active matching
         * window so deposits received before permissions were granted are also captured and
         * forwarded via `ForwardWorker`.
         */
        suspend fun syncRecentInboxSms(appContext: Context, lookbackHours: Int = 24): Int {
            val hasReadSms = androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.READ_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasReadSms) return 0

            val cutoffMs = System.currentTimeMillis() - (lookbackHours.coerceAtLeast(1) * 3_600_000L)
            var newlyEnqueued = 0

            runCatching {
                val projection = arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                )
                val selection = "${Telephony.Sms.DATE} >= ?"
                val selectionArgs = arrayOf(cutoffMs.toString())
                val sortOrder = "${Telephony.Sms.DATE} ASC LIMIT 100"

                appContext.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                    while (cursor.moveToNext()) {
                        val address = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                        val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                        val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                        if (ingestIncomingSms(appContext, address, body, receivedAtMs = dateMs, sendPushAlerts = false)) {
                            newlyEnqueued++
                        }
                    }
                }
            }
            return newlyEnqueued
        }
    }
}
