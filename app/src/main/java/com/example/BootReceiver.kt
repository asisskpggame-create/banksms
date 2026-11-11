package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the network watcher and flushes any queued/retry SMS messages after device reboot
 * or app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            InternetWatchService.register(appContext)
            ForwardWorker.enqueuePendingAll(appContext)
        }
    }
}
