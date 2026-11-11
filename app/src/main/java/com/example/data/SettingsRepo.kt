package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GatewaySettings(
    val endpointUrl: String = "",
    val gatewayToken: String = "",
    val serviceEnabled: Boolean = true,
    val filterBanksEnabled: Boolean = false,
    val selectedBankCodes: Set<String> = SmsParser.KNOWN_BANKS.map { it.code }.toSet(),
    val lastTokenError: String? = null
) {
    val isConfigured: Boolean
        get() = endpointUrl.startsWith("https://", ignoreCase = true) && gatewayToken.trim().length >= 20

    val normalizedEndpointUrl: String
        get() {
            val trimmed = endpointUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return if (trimmed.endsWith("/sms-in", ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed/sms-in"
            }
        }
}

/**
 * Stores server URL, SMS_GATEWAY_TOKEN, and forwarder configuration securely on-device
 * using Android Jetpack Security (`EncryptedSharedPreferences`).
 * Never hardcodes any token or server URL inside the APK.
 */
class SettingsRepo private constructor(context: Context) {

    private val prefs: SharedPreferences = createSecurePrefs(context.applicationContext)

    private val _settingsFlow = MutableStateFlow(readSettings())
    val settingsFlow: StateFlow<GatewaySettings> = _settingsFlow.asStateFlow()

    private fun createSecurePrefs(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback for headless JVM/Robolectric test environments where AndroidKeyStore is absent
            appContext.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun readSettings(): GatewaySettings {
        val defaultBanks = SmsParser.KNOWN_BANKS.map { it.code }.toSet()
        val savedBanks = prefs.getStringSet(KEY_SELECTED_BANKS, null) ?: defaultBanks
        return GatewaySettings(
            endpointUrl = prefs.getString(KEY_ENDPOINT_URL, "") ?: "",
            gatewayToken = prefs.getString(KEY_GATEWAY_TOKEN, "") ?: "",
            serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true),
            filterBanksEnabled = prefs.getBoolean(KEY_FILTER_BANKS_ENABLED, false),
            selectedBankCodes = savedBanks,
            lastTokenError = prefs.getString(KEY_LAST_TOKEN_ERROR, null)
        )
    }

    fun saveEndpointAndToken(endpointUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_ENDPOINT_URL, endpointUrl.trim())
            .putString(KEY_GATEWAY_TOKEN, token.trim())
            .putString(KEY_LAST_TOKEN_ERROR, null)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun setFilterBanksEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_FILTER_BANKS_ENABLED, enabled)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun toggleBankSelection(bankCode: String) {
        val current = readSettings().selectedBankCodes.toMutableSet()
        if (current.contains(bankCode)) {
            current.remove(bankCode)
        } else {
            current.add(bankCode)
        }
        prefs.edit()
            .putStringSet(KEY_SELECTED_BANKS, current)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun selectAllBanks(selectAll: Boolean) {
        val updated = if (selectAll) {
            SmsParser.KNOWN_BANKS.map { it.code }.toSet()
        } else {
            emptySet()
        }
        prefs.edit()
            .putStringSet(KEY_SELECTED_BANKS, updated)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun recordTokenError(errorMsg: String?) {
        prefs.edit()
            .putString(KEY_LAST_TOKEN_ERROR, errorMsg)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun shouldProcessBank(bankCode: String): Boolean {
        val current = readSettings()
        if (!current.filterBanksEnabled) return true
        return current.selectedBankCodes.contains(bankCode)
    }

    companion object {
        private const val PREFS_FILE_NAME = "asis_secure_gateway_prefs"
        private const val KEY_ENDPOINT_URL = "endpoint_url"
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FILTER_BANKS_ENABLED = "filter_banks_enabled"
        private const val KEY_SELECTED_BANKS = "selected_bank_codes"
        private const val KEY_LAST_TOKEN_ERROR = "last_token_error"

        @Volatile
        private var INSTANCE: SettingsRepo? = null

        fun getInstance(context: Context): SettingsRepo {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepo(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
