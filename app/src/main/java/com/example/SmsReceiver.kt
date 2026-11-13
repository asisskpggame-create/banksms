package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AppDatabase
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
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

        if (!settingsRepo.shouldProcessBank(bank, rawBody)) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).logDao()
                val normalized = SmsParser.normalize(rawBody)
                val fp = SmsParser.fingerprint(bank, rawBody)

                // Check local duplicate by fingerprint
                if (dao.getByFingerprint(fp) != null) {
                    return@launch
                }

                val parsed = SmsParser.parse(rawBody)
                val detectedBank = SmsParser.detectBank(bank, rawBody)
                val initialStatus = if (parsed.amountMinor == null) {
                    SmsStatus.IGNORED
                } else {
                    SmsStatus.NEW
                }

                val record = SmsRecord(
                    fingerprint = fp,
                    bank = bank,
                    bankTitle = detectedBank.titleFa,
                    body = rawBody, // Raw unmodified SMS text preserved per Section 2
                    normalized = normalized,
                    amountMinor = parsed.amountMinor,
                    reason = parsed.reason,
                    status = initialStatus.name,
                    receivedMs = receivedAtMs,
                    storedMs = storedAtMs,
                    decidedMs = if (initialStatus == SmsStatus.IGNORED) storedAtMs else null
                )

                val rowId = dao.insertIgnoreDuplicate(record)
                if (rowId != -1L && initialStatus == SmsStatus.NEW) {
                    ForwardWorker.runSweepNow(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
