package com.example.util

import android.Manifest
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
import com.example.data.SmsParser

object NotificationHelper {

    private const val CHANNEL_ID = "asis_gateway_alerts"
    private const val NOTIFICATION_ID_TOKEN_ERROR = 4010
    private const val NOTIFICATION_ID_AMBIGUOUS_BASE = 5000
    private const val NOTIFICATION_ID_MANUAL_BASE = 6000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun canPostNotification(context: Context): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
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
        if (!canPostNotification(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_asis_shield)
            .setContentTitle("خطای ۴۰۱: توکن یا امضای درگاه نامعتبر است")
            .setContentText(errorDetail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorDetail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TOKEN_ERROR, notification)
        }
    }

    fun notifyAmbiguousDeposit(context: Context, depositId: Long, amountMinor: Long) {
        if (!canPostNotification(context)) return
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

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_AMBIGUOUS_BASE + (depositId % 500).toInt(), notification)
        }
    }

    fun notifyManualApprovalNeeded(
        context: Context,
        depositId: Long,
        amountMinor: Long,
        matchedLabel: String,
        whyNote: String
    ) {
        if (!canPostNotification(context)) return
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

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_MANUAL_BASE + (depositId % 500).toInt(), notification)
        }
    }
}
