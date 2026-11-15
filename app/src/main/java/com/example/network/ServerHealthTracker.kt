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

/**
 * Shared real-time tracker for server reachability and latency across foreground probes
 * (`AsisViewModel`) and background sweeps (`ForwardWorker`).
 */
object ServerHealthTracker {

    private val _telemetryFlow = MutableStateFlow(ServerConnectionTelemetry())
    val telemetryFlow: StateFlow<ServerConnectionTelemetry> = _telemetryFlow.asStateFlow()

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
    }

    fun recordFailure(httpCode: Int?, errorMessage: String, latencyMs: Long? = null) {
        _telemetryFlow.value = ServerConnectionTelemetry(
            isChecking = false,
            lastPingOk = false,
            latencyMs = latencyMs,
            httpCode = httpCode,
            lastCheckedAtMs = System.currentTimeMillis(),
            lastErrorMessage = errorMessage
        )
    }

    fun reset() {
        _telemetryFlow.value = ServerConnectionTelemetry()
    }
}
