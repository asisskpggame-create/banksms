package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SettingsRepo
import com.example.data.SmsParser

object NotificationHelper {

    private const val CHANNEL_ID = "asis_gateway_alerts"
    private const val CHANNEL_SERVER_ERRORS = "asis_server_errors"
    private const val CHANNEL_SENSITIVE_SMS = "asis_sensitive_sms"

    private const val NOTIFICATION_ID_TOKEN_ERROR = 4010
    private const val NOTIFICATION_ID_SERVER_ERROR = 5030
    private const val NOTIFICATION_ID_AMBIGUOUS_BASE = 5000
    private const val NOTIFICATION_ID_MANUAL_BASE = 6000
    private const val NOTIFICATION_ID_SENSITIVE_SMS_BASE = 7000
    private const val NOTIFICATION_ID_TEST_ALERT = 8000

    private const val SERVER_ERROR_COOLDOWN_MS = 90_000L

    @Volatile
    private var lastServerErrorSignature: String? = null

    @Volatile
    private var lastServerErrorNotifiedAtMs: Long = 0L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val gatewayChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
        }
        val serverErrorChannel = NotificationChannel(
            CHANNEL_SERVER_ERRORS,
            context.getString(R.string.notif_channel_server_errors_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_server_errors_desc)
            enableVibration(true)
        }
        val sensitiveSmsChannel = NotificationChannel(
            CHANNEL_SENSITIVE_SMS,
            context.getString(R.string.notif_channel_sensitive_sms_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_sensitive_sms_desc)
            enableVibration(true)
        }
        manager.createNotificationChannels(
            listOf(gatewayChannel, serverErrorChannel, sensitiveSmsChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun postIfPermitted(context: Context, id: Int, notification: Notification): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyTokenError(context: Context, errorDetail: String) {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!settings.notifyOnServerConnectionError) return

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVER_ERRORS)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle("خطای ۴۰۱: توکن یا امضای درگاه نامعتبر است")
            .setContentText(errorDetail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorDetail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        postIfPermitted(context, NOTIFICATION_ID_TOKEN_ERROR, notification)
    }

    fun notifyServerConnectionError(
        context: Context,
        errorTitle: String,
        errorDetail: String,
        httpCode: Int? = null,
        forceImmediate: Boolean = false
    ): Boolean {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!forceImmediate && !settings.notifyOnServerConnectionError) {
            return false
        }

        val nowMs = System.currentTimeMillis()
        val signature = "${httpCode ?: "net"}|$errorTitle|$errorDetail"
        if (!forceImmediate &&
            lastServerErrorSignature == signature &&
            (nowMs - lastServerErrorNotifiedAtMs) < SERVER_ERROR_COOLDOWN_MS
        ) {
            return false
        }

        ensureChannel(context)
        val title = if (httpCode != null && httpCode > 0) {
            "⚠️ خطای اتصال به سرور (کد $httpCode)"
        } else {
            "⚠️ $errorTitle"
        }
        val body = if (errorDetail.isNotBlank()) errorDetail else errorTitle

        val notification = NotificationCompat.Builder(context, CHANNEL_SERVER_ERRORS)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        val posted = postIfPermitted(context, NOTIFICATION_ID_SERVER_ERROR, notification)
        if (posted && !forceImmediate) {
            lastServerErrorSignature = signature
            lastServerErrorNotifiedAtMs = nowMs
        }
        return posted
    }

    fun cancelServerErrorNotification(context: Context) {
        lastServerErrorSignature = null
        lastServerErrorNotifiedAtMs = 0L
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SERVER_ERROR)
        }
    }

    fun resetServerErrorThrottleForTests() {
        lastServerErrorSignature = null
        lastServerErrorNotifiedAtMs = 0L
    }

    fun notifySensitiveBankSms(
        context: Context,
        smsId: Long,
        bankTitle: String,
        categoryTitleFa: String,
        amountMinor: Long?,
        smsBody: String,
        bypassPrefs: Boolean = false
    ): Boolean {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!bypassPrefs && !settings.notifyOnSensitiveBankSms) {
            return false
        }

        ensureChannel(context)
        val amountLine = amountMinor?.let {
            "مبلغ: ${SmsParser.formatToman(it)} تومان (${SmsParser.formatRial(it)} ریال)"
        }
        val title = "🔔 $bankTitle • $categoryTitleFa"
        val cleanSnippet = smsBody.trim().replace("\n", " ").take(240)
        val fullMessage = buildString {
            if (amountLine != null) {
                append(amountLine)
                append('\n')
            }
            append(cleanSnippet)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SENSITIVE_SMS)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle(title)
            .setContentText(amountLine ?: cleanSnippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        val safeId = NOTIFICATION_ID_SENSITIVE_SMS_BASE + ((smsId.coerceAtLeast(1L) % 900L).toInt())
        return postIfPermitted(context, safeId, notification)
    }

    fun sendTestPushAlert(context: Context): Boolean {
        ensureChannel(context)
        val body = "اعلان‌های لحظه‌ای خطای اتصال به سرور و پیامک‌های بانکی حساس با موفقیت فعال هستند."
        val notification = NotificationCompat.Builder(context, CHANNEL_SENSITIVE_SMS)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle("✅ تست اعلان لحظه‌ای ASiS")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        return postIfPermitted(context, NOTIFICATION_ID_TEST_ALERT, notification)
    }

    fun notifyAmbiguousDeposit(context: Context, depositId: Long, amountMinor: Long) {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!settings.notifyOnAmbiguousOrManual) return

        ensureChannel(context)
        val tomanStr = SmsParser.formatToman(amountMinor)
        val msg = "واریز $tomanStr تومان رسید، اما چند سفارش منتظر با همین مبلغ وجود دارد. لطفاً دستی بررسی و تأیید کنید."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle("⚠️ ابهام در تطبیق واریز ($tomanStr تومان)")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        postIfPermitted(context, NOTIFICATION_ID_AMBIGUOUS_BASE + (depositId % 500).toInt(), notification)
    }

    fun notifyManualApprovalNeeded(
        context: Context,
        depositId: Long,
        amountMinor: Long,
        matchedLabel: String,
        whyNote: String
    ) {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!settings.notifyOnAmbiguousOrManual) return

        ensureChannel(context)
        val tomanStr = SmsParser.formatToman(amountMinor)
        val msg = "پیامک بانک ($tomanStr تومان) با $matchedLabel می‌خواند ($whyNote). برای زدن دکمهٔ تأیید وارد برنامه شوید."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle("💬 تطبیق واریز با $matchedLabel")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        postIfPermitted(context, NOTIFICATION_ID_MANUAL_BASE + (depositId % 500).toInt(), notification)
    }
}

