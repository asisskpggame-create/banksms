package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors internet connectivity changes in real time.
 * Whenever internet connection becomes available, wakes up WorkManager (`ForwardWorker`)
 * to flush all `QUEUED` and `RETRY` messages in the database.
 */
object InternetWatchService {

    private val isRegistered = AtomicBoolean(false)
    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

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
                        _isOnlineFlow.value = true
                        // Wake up retry queue immediately when internet reconnects
                        ForwardWorker.enqueuePendingAll(appContext)
                    }

                    override fun onLost(network: Network) {
                        _isOnlineFlow.value = checkCurrentlyOnline(appContext)
                    }
                }
            )
        }
    }
}
