package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AppDatabase
import com.example.data.LogEntry
import com.example.data.LogStatus
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for `android.provider.Telephony.SMS_RECEIVED`, reassembles multipart SMS bodies,
 * stores a `QUEUED` LogEntry in Room, and dispatches `ForwardWorker`.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settingsRepo = SettingsRepo.getInstance(context)
        val settings = settingsRepo.readSettings()
        if (!settings.serviceEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multipart SMS segments by originating sender address
        val groupedBySender = linkedMapOf<String, StringBuilder>()
        for (msg in messages) {
            if (msg == null) continue
            val sender = msg.displayOriginatingAddress ?: msg.originatingAddress ?: "unknown"
            val bodyPart = msg.displayMessageBody ?: msg.messageBody ?: ""
            groupedBySender.getOrPut(sender) { StringBuilder() }.append(bodyPart)
        }

        if (groupedBySender.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).logDao()
                for ((sender, bodyBuilder) in groupedBySender) {
                    val fullText = bodyBuilder.toString().trim().take(SmsParser.MAX_SMS_LENGTH)
                    if (fullText.isEmpty()) continue

                    val parsed = SmsParser.parse(rawText = fullText, sender = sender)
                    if (!settingsRepo.shouldProcessBank(parsed.bankCode)) {
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val logEntry = LogEntry(
                        sender = sender,
                        bankCode = parsed.bankCode,
                        bankTitle = parsed.bankTitleFa,
                        body = fullText,
                        amountRial = parsed.amountMinor,
                        parseReason = parsed.reason,
                        fingerprint = parsed.fingerprint,
                        status = LogStatus.QUEUED.name,
                        createdAt = now,
                        updatedAt = now
                    )
                    val logId = dao.insertLog(logEntry)
                    ForwardWorker.enqueueForLog(appContext, logId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
