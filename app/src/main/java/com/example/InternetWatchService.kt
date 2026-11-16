package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.AppDatabase
import com.example.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors internet connectivity changes in real time.
 * Whenever internet connection becomes available, wakes up `ForwardWorker` both via WorkManager
 * (`NetworkType.CONNECTED`) and immediate post-error recovery sync when `autoSyncAfterErrors` is enabled.
 */
object InternetWatchService {

    private val isRegistered = AtomicBoolean(false)
    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingReconnectJob: Job? = null

    fun checkCurrentlyOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val activeNetwork = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    fun register(context: Context) {
        val appContext = context.applicationContext ?: context
        _isOnlineFlow.value = checkCurrentlyOnline(appContext)

        if (!isRegistered.compareAndSet(false, true)) return

        val cm = runCatching {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull() ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val wasOffline = !_isOnlineFlow.value
                        _isOnlineFlow.value = true
                        onNetworkRestored(appContext, wasOffline = wasOffline)
                    }

                    override fun onLost(network: Network) {
                        _isOnlineFlow.value = checkCurrentlyOnline(appContext)
                    }
                }
            )
        }
    }

    internal fun onNetworkRestored(context: Context, wasOffline: Boolean = true) {
        val appContext = context.applicationContext ?: context
        val settings = SettingsRepo.getInstance(appContext).readSettings()
        if (!settings.autoSyncAfterErrors) {
            ForwardWorker.enqueuePendingAll(appContext)
            return
        }

        ForwardWorker.schedulePostErrorRecoveryWorker(
            context = appContext,
            delayMs = 500L,
            reasonFa = "بازگشت اتصال اینترنت"
        )

        pendingReconnectJob?.cancel()
        pendingReconnectJob = watchScope.launch {
            if (wasOffline) {
                delay(700L)
            }
            val latestSettings = SettingsRepo.getInstance(appContext).readSettings()
            if (!latestSettings.autoSyncAfterErrors || !latestSettings.isConfigured || !latestSettings.serviceEnabled) {
                return@launch
            }
            val dao = AppDatabase.getInstance(appContext).logDao()
            val hasRecoverable = dao.getRecoveryCandidateCount() > 0 ||
                dao.getActiveDepositsForMatching().isNotEmpty()
            if (hasRecoverable || wasOffline) {
                ForwardWorker.runRecoverySyncNow(
                    context = appContext,
                    includeAuth401 = false,
                    reasonFa = "بازگشت اتصال اینترنت"
                )
            }
        }
    }
}
