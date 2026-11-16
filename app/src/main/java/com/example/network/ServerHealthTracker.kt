package com.example.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServerConnectionLevel {
    GREEN,
    YELLOW,
    RED
}

data class ServerConnectionTelemetry(
    val isChecking: Boolean = false,
    val lastPingOk: Boolean? = null,
    val latencyMs: Long? = null,
    val httpCode: Int? = null,
    val lastCheckedAtMs: Long? = null,
    val lastErrorMessage: String? = null
)

data class ServerConnectionStatusUi(
    val level: ServerConnectionLevel,
    val titleFa: String,
    val detailFa: String,
    val latencyMs: Long? = null,
    val hostLabel: String? = null,
    val lastCheckedAtMs: Long? = null,
    val isChecking: Boolean = false,
    val needsConfiguration: Boolean = false
)

data class PostErrorSyncState(
    val isSyncing: Boolean = false,
    val needsRecoverySync: Boolean = false,
    val serverRecoveredPendingSync: Boolean = false,
    val triggerReasonFa: String? = null,
    val lastRecoveryAtMs: Long? = null,
    val lastRecoveryRecoveredCount: Int = 0,
    val lastRecoveryRemainingErrors: Int = 0,
    val recoveryCycleCount: Int = 0
)

/**
 * Shared real-time tracker for server reachability, latency, and post-error connection recovery
 * across foreground probes (`AsisViewModel`), `InternetWatchService`, and `ForwardWorker`.
 */
object ServerHealthTracker {

    private val _telemetryFlow = MutableStateFlow(ServerConnectionTelemetry())
    val telemetryFlow: StateFlow<ServerConnectionTelemetry> = _telemetryFlow.asStateFlow()

    private val _postErrorSyncFlow = MutableStateFlow(PostErrorSyncState())
    val postErrorSyncFlow: StateFlow<PostErrorSyncState> = _postErrorSyncFlow.asStateFlow()

    @Volatile
    private var hadFailureSinceLastRecovery: Boolean = false

    fun setChecking(checking: Boolean) {
        _telemetryFlow.update { it.copy(isChecking = checking) }
    }

    fun recordSuccess(httpCode: Int, latencyMs: Long) {
        _telemetryFlow.value = ServerConnectionTelemetry(
            isChecking = false,
            lastPingOk = true,
            latencyMs = latencyMs.coerceAtLeast(1L),
            httpCode = httpCode,
            lastCheckedAtMs = System.currentTimeMillis(),
            lastErrorMessage = null
        )
        if (hadFailureSinceLastRecovery) {
            hadFailureSinceLastRecovery = false
            _postErrorSyncFlow.update {
                it.copy(
                    needsRecoverySync = true,
                    serverRecoveredPendingSync = true,
                    triggerReasonFa = "رفع خطای سرور و برقراری اتصال"
                )
            }
        }
    }

    fun recordFailure(httpCode: Int?, errorMessage: String, latencyMs: Long? = null) {
        hadFailureSinceLastRecovery = true
        _telemetryFlow.value = ServerConnectionTelemetry(
            isChecking = false,
            lastPingOk = false,
            latencyMs = latencyMs,
            httpCode = httpCode,
            lastCheckedAtMs = System.currentTimeMillis(),
            lastErrorMessage = errorMessage
        )
        _postErrorSyncFlow.update {
            it.copy(
                needsRecoverySync = true,
                serverRecoveredPendingSync = false
            )
        }
    }

    fun consumeServerRecoveredTrigger(): String? {
        val current = _postErrorSyncFlow.value
        if (!current.serverRecoveredPendingSync) return null
        _postErrorSyncFlow.update { it.copy(serverRecoveredPendingSync = false) }
        return current.triggerReasonFa ?: "رفع خطای سرور و اتصال مجدد"
    }

    fun onRecoverySyncStarted(reasonFa: String) {
        _postErrorSyncFlow.update {
            it.copy(
                isSyncing = true,
                serverRecoveredPendingSync = false,
                triggerReasonFa = reasonFa
            )
        }
    }

    fun onRecoverySyncCompleted(
        recoveredCount: Int,
        remainingErrorCount: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (remainingErrorCount > 0) {
            hadFailureSinceLastRecovery = true
        }
        _postErrorSyncFlow.update { prev ->
            prev.copy(
                isSyncing = false,
                needsRecoverySync = remainingErrorCount > 0,
                serverRecoveredPendingSync = false,
                lastRecoveryAtMs = nowMs,
                lastRecoveryRecoveredCount = recoveredCount,
                lastRecoveryRemainingErrors = remainingErrorCount,
                recoveryCycleCount = prev.recoveryCycleCount + 1
            )
        }
    }

    fun reset() {
        hadFailureSinceLastRecovery = false
        _telemetryFlow.value = ServerConnectionTelemetry()
        _postErrorSyncFlow.value = PostErrorSyncState()
    }
}
