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
    val autoApproveInApp: Boolean = false, // پیش‌فرض: خاموش طبق بخش ۷ مشخصات
    val localMaxToman: Long = 0L,          // ۰ یعنی استفاده از auto_max_toman سرور
    val filterBanksEnabled: Boolean = false,
    val selectedBankCodes: Set<String> = SmsParser.KNOWN_BANKS.map { it.code }.toSet(),
    val lastTokenError: String? = null,
    val lastClockOffsetMs: Long = 0L,
    val lastServerWindowHours: Int = 24,
    val lastServerMaxToman: Long = 0L
) {
    val isConfigured: Boolean
        get() = endpointUrl.startsWith("https://", ignoreCase = true) && gatewayToken.trim().length >= 20

    /**
     * Returns the base panel URL without trailing `/sms-in`, `/sms-pending`, or `/sms-approve`
     * so all 3 endpoints (`/sms-in`, `/sms-pending`, `/sms-approve`) are constructed cleanly.
     */
    val basePanelUrl: String
        get() {
            var trimmed = endpointUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            for (suffix in listOf("/sms-in", "/sms-pending", "/sms-approve")) {
                if (trimmed.endsWith(suffix, ignoreCase = true)) {
                    trimmed = trimmed.dropLast(suffix.length).trimEnd('/')
                }
            }
            return trimmed
        }

    val smsInUrl: String
        get() = basePanelUrl.takeIf { it.isNotEmpty() }?.let { "$it/sms-in" } ?: ""

    val smsPendingUrl: String
        get() = basePanelUrl.takeIf { it.isNotEmpty() }?.let { "$it/sms-pending" } ?: ""

    val smsApproveUrl: String
        get() = basePanelUrl.takeIf { it.isNotEmpty() }?.let { "$it/sms-approve" } ?: ""

    /** Effective max Toman ceiling (local override if > 0, else server's `auto_max_toman`). */
    val effectiveMaxToman: Long
        get() = if (localMaxToman > 0L) localMaxToman else lastServerMaxToman
}

/**
 * Stores server URL, SMS_GATEWAY_TOKEN, and App-Side Approval settings securely on-device
 * using Android Jetpack Security (`EncryptedSharedPreferences`).
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
            autoApproveInApp = prefs.getBoolean(KEY_AUTO_APPROVE_IN_APP, false),
            localMaxToman = prefs.getLong(KEY_LOCAL_MAX_TOMAN, 0L),
            filterBanksEnabled = prefs.getBoolean(KEY_FILTER_BANKS_ENABLED, false),
            selectedBankCodes = savedBanks,
            lastTokenError = prefs.getString(KEY_LAST_TOKEN_ERROR, null),
            lastClockOffsetMs = prefs.getLong(KEY_LAST_CLOCK_OFFSET_MS, 0L),
            lastServerWindowHours = prefs.getInt(KEY_LAST_SERVER_WINDOW_HOURS, 24),
            lastServerMaxToman = prefs.getLong(KEY_LAST_SERVER_MAX_TOMAN, 0L)
        )
    }

    fun saveEndpointAndToken(endpointUrl: String, token: String, localMaxToman: Long = readSettings().localMaxToman) {
        prefs.edit()
            .putString(KEY_ENDPOINT_URL, endpointUrl.trim())
            .putString(KEY_GATEWAY_TOKEN, token.trim())
            .putLong(KEY_LOCAL_MAX_TOMAN, maxOf(0L, localMaxToman))
            .putString(KEY_LAST_TOKEN_ERROR, null)
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _settingsFlow.value = readSettings()
    }

    fun setAutoApproveInApp(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_APPROVE_IN_APP, enabled).apply()
        _settingsFlow.value = readSettings()
    }

    fun setLocalMaxToman(maxToman: Long) {
        prefs.edit().putLong(KEY_LOCAL_MAX_TOMAN, maxOf(0L, maxToman)).apply()
        _settingsFlow.value = readSettings()
    }

    fun updateServerSyncMeta(clockOffsetMs: Long, matchWindowHours: Int, autoMaxToman: Long) {
        prefs.edit()
            .putLong(KEY_LAST_CLOCK_OFFSET_MS, clockOffsetMs)
            .putInt(KEY_LAST_SERVER_WINDOW_HOURS, maxOf(1, matchWindowHours))
            .putLong(KEY_LAST_SERVER_MAX_TOMAN, maxOf(0L, autoMaxToman))
            .apply()
        _settingsFlow.value = readSettings()
    }

    fun setFilterBanksEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_BANKS_ENABLED, enabled).apply()
        _settingsFlow.value = readSettings()
    }

    fun toggleBankSelection(bankCode: String) {
        val current = readSettings().selectedBankCodes.toMutableSet()
        if (current.contains(bankCode)) current.remove(bankCode) else current.add(bankCode)
        prefs.edit().putStringSet(KEY_SELECTED_BANKS, current).apply()
        _settingsFlow.value = readSettings()
    }

    fun selectAllBanks(selectAll: Boolean) {
        val updated = if (selectAll) SmsParser.KNOWN_BANKS.map { it.code }.toSet() else emptySet()
        prefs.edit().putStringSet(KEY_SELECTED_BANKS, updated).apply()
        _settingsFlow.value = readSettings()
    }

    fun recordTokenError(errorMsg: String?) {
        prefs.edit().putString(KEY_LAST_TOKEN_ERROR, errorMsg).apply()
        _settingsFlow.value = readSettings()
    }

    fun shouldProcessBank(senderOrBankCode: String, body: String): Boolean {
        val current = readSettings()
        if (!current.filterBanksEnabled) return true
        val detected = SmsParser.detectBank(senderOrBankCode, body)
        return current.selectedBankCodes.contains(detected.code)
    }

    companion object {
        private const val PREFS_FILE_NAME = "asis_secure_gateway_prefs"
        private const val KEY_ENDPOINT_URL = "endpoint_url"
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_APPROVE_IN_APP = "auto_approve_in_app"
        private const val KEY_LOCAL_MAX_TOMAN = "local_max_toman"
        private const val KEY_FILTER_BANKS_ENABLED = "filter_banks_enabled"
        private const val KEY_SELECTED_BANKS = "selected_bank_codes"
        private const val KEY_LAST_TOKEN_ERROR = "last_token_error"
        private const val KEY_LAST_CLOCK_OFFSET_MS = "last_clock_offset_ms"
        private const val KEY_LAST_SERVER_WINDOW_HOURS = "last_server_window_hours"
        private const val KEY_LAST_SERVER_MAX_TOMAN = "last_server_max_toman"

        @Volatile
        private var INSTANCE: SettingsRepo? = null

        fun getInstance(context: Context): SettingsRepo {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepo(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
