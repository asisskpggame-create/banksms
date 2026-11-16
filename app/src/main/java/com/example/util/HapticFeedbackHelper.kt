package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.FailedSyncHapticPattern
import com.example.data.HapticIntensity
import com.example.data.HapticWaveformSpec
import com.example.data.HandshakeHapticPattern
import com.example.data.SettingsRepo

/**
 * Hardware haptic vibration dispatcher for server handshakes and failed sync attempts,
 * respecting user intensity, waveform preset, master switch, and cooldown preferences.
 */
object HapticFeedbackHelper {

    private const val COOLDOWN_MS = 3_000L

    @Volatile
    private var lastHandshakeVibrationAtMs: Long = 0L

    @Volatile
    private var lastFailedSyncVibrationAtMs: Long = 0L

    fun triggerServerHandshake(
        context: Context,
        forceBypassCooldown: Boolean = false
    ): Boolean {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!settings.hapticMasterEnabled || !settings.hapticOnServerHandshake) {
            return false
        }
        val nowMs = System.currentTimeMillis()
        if (!forceBypassCooldown &&
            settings.hapticCooldownEnabled &&
            (nowMs - lastHandshakeVibrationAtMs) < COOLDOWN_MS
        ) {
            return false
        }

        val spec = HapticWaveformSpec.forHandshake(
            pattern = settings.hapticHandshakePattern,
            intensity = settings.hapticHandshakeIntensity
        )
        val vibrated = dispatchWaveform(context, spec)
        if (vibrated) {
            lastHandshakeVibrationAtMs = nowMs
        }
        return vibrated
    }

    fun triggerFailedSync(
        context: Context,
        forceBypassCooldown: Boolean = false
    ): Boolean {
        val settings = SettingsRepo.getInstance(context).readSettings()
        if (!settings.hapticMasterEnabled || !settings.hapticOnFailedSync) {
            return false
        }
        val nowMs = System.currentTimeMillis()
        if (!forceBypassCooldown &&
            settings.hapticCooldownEnabled &&
            (nowMs - lastFailedSyncVibrationAtMs) < COOLDOWN_MS
        ) {
            return false
        }

        val spec = HapticWaveformSpec.forFailedSync(
            pattern = settings.hapticFailedSyncPattern,
            intensity = settings.hapticFailedSyncIntensity
        )
        val vibrated = dispatchWaveform(context, spec)
        if (vibrated) {
            lastFailedSyncVibrationAtMs = nowMs
        }
        return vibrated
    }

    fun previewHandshake(
        context: Context,
        pattern: HandshakeHapticPattern,
        intensity: HapticIntensity
    ): Boolean {
        val spec = HapticWaveformSpec.forHandshake(pattern = pattern, intensity = intensity)
        return dispatchWaveform(context, spec)
    }

    fun previewFailedSync(
        context: Context,
        pattern: FailedSyncHapticPattern,
        intensity: HapticIntensity
    ): Boolean {
        val spec = HapticWaveformSpec.forFailedSync(pattern = pattern, intensity = intensity)
        return dispatchWaveform(context, spec)
    }

    fun resetCooldownsForTests() {
        lastHandshakeVibrationAtMs = 0L
        lastFailedSyncVibrationAtMs = 0L
    }

    @Suppress("DEPRECATION")
    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchWaveform(context: Context, spec: HapticWaveformSpec): Boolean {
        val vibrator = resolveVibrator(context.applicationContext) ?: return false
        if (!vibrator.hasVibrator()) return false

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(spec.timingsMs, spec.amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(spec.timingsMs, -1)
                }
                vibrator.vibrate(effect)
            } else {
                vibrator.vibrate(spec.timingsMs, -1)
            }
            true
        }.getOrDefault(false)
    }
}
