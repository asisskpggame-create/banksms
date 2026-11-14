package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RegisteredBankCard(
    val id: String,
    val bankCode: String,
    val bankTitle: String,
    val senderNumber: String
)

data class GatewaySettings(
    val endpointUrl: String = "",
    val gatewayToken: String = "",
    val serviceEnabled: Boolean = true,
    val autoApproveInApp: Boolean = true, // پیش‌فرض: روشن (سرور هم سقف و قفل‌های ایمنی را اعمال می‌کند)
    val localMaxToman: Long = 0L,          // ۰ یعنی استفاده از auto_max_toman سرور
    val filterBanksEnabled: Boolean = false,
    val selectedBankCodes: Set<String> = SmsParser.KNOWN_BANKS.map { it.code }.toSet(),
    val registeredBankCards: List<RegisteredBankCard> = DEFAULT_REGISTERED_BANK_CARDS,
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

    override fun toString(): String =
        "GatewaySettings(endpointUrl=$endpointUrl, gatewayToken=***, serviceEnabled=$serviceEnabled, " +
            "autoApproveInApp=$autoApproveInApp, localMaxToman=$localMaxToman, " +
            "filterBanksEnabled=$filterBanksEnabled, selectedBankCodes=${selectedBankCodes.size} banks, " +
            "registeredBankCards=${registeredBankCards.size})"

    companion object {
        val DEFAULT_REGISTERED_BANK_CARDS: List<RegisteredBankCard> = listOf(
            RegisteredBankCard(
                id = "default_mehr_iran",
                bankCode = "mehr",
                bankTitle = "بانک قرض الحسنه",
                senderNumber = "B.QMEHRIRAN"
            )
        )
    }
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
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // Verify keyset can actually read without throwing
            encrypted.contains(KEY_ENDPOINT_URL)
            encrypted
        } catch (_: Throwable) {
            appContext.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    private inline fun safeEdit(block: SharedPreferences.Editor.() -> Unit) {
        runCatching {
            val editor = prefs.edit()
            editor.block()
            editor.apply()
        }
    }

    private fun parseRegisteredBankCards(rawJson: String?): List<RegisteredBankCard> {
        if (rawJson == null) return GatewaySettings.DEFAULT_REGISTERED_BANK_CARDS
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val code = obj.optString("bankCode").trim()
                    val title = obj.optString("bankTitle").trim()
                    val sender = obj.optString("senderNumber").trim()
                    if (code.isNotEmpty() && sender.isNotEmpty()) {
                        add(
                            RegisteredBankCard(
                                id = id.ifEmpty { "${code}_${sender}_$i" },
                                bankCode = code,
                                bankTitle = title.ifEmpty { code },
                                senderNumber = sender
                            )
                        )
                    }
                }
            }
        }.getOrDefault(GatewaySettings.DEFAULT_REGISTERED_BANK_CARDS)
    }

    private fun serializeRegisteredBankCards(cards: List<RegisteredBankCard>): String {
        val arr = JSONArray()
        for (c in cards) {
            val obj = JSONObject()
                .put("id", c.id)
                .put("bankCode", c.bankCode)
                .put("bankTitle", c.bankTitle)
                .put("senderNumber", c.senderNumber)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun readSettings(): GatewaySettings {
        val defaultBanks = runCatching {
            SmsParser.KNOWN_BANKS.map { it.code }.toSet()
        }.getOrDefault(emptySet())
        return try {
            val savedBanks = prefs.getStringSet(KEY_SELECTED_BANKS, null) ?: defaultBanks
            val cards = parseRegisteredBankCards(prefs.getString(KEY_REGISTERED_BANK_CARDS, null))
            runCatching {
                SmsParser.updateCustomBankSenders(
                    cards.associate { it.senderNumber to it.bankCode }
                )
            }
            GatewaySettings(
                endpointUrl = prefs.getString(KEY_ENDPOINT_URL, "") ?: "",
                gatewayToken = prefs.getString(KEY_GATEWAY_TOKEN, "") ?: "",
                serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true),
                autoApproveInApp = prefs.getBoolean(KEY_AUTO_APPROVE_IN_APP, true),
                localMaxToman = prefs.getLong(KEY_LOCAL_MAX_TOMAN, 0L),
                filterBanksEnabled = prefs.getBoolean(KEY_FILTER_BANKS_ENABLED, false),
                selectedBankCodes = savedBanks,
                registeredBankCards = cards,
                lastTokenError = prefs.getString(KEY_LAST_TOKEN_ERROR, null),
                lastClockOffsetMs = prefs.getLong(KEY_LAST_CLOCK_OFFSET_MS, 0L),
                lastServerWindowHours = prefs.getInt(KEY_LAST_SERVER_WINDOW_HOURS, 24),
                lastServerMaxToman = prefs.getLong(KEY_LAST_SERVER_MAX_TOMAN, 0L)
            )
        } catch (_: Throwable) {
            GatewaySettings(selectedBankCodes = defaultBanks)
        }
    }

    fun addRegisteredBankCard(bankCode: String, bankTitle: String, senderNumber: String): RegisteredBankCard {
        val cleanSender = senderNumber.trim()
        val current = readSettings().registeredBankCards.toMutableList()
        val existingIdx = current.indexOfFirst {
            it.senderNumber.equals(cleanSender, ignoreCase = true)
        }
        val card = RegisteredBankCard(
            id = "${bankCode}_${cleanSender.lowercase()}_${System.currentTimeMillis()}",
            bankCode = bankCode,
            bankTitle = bankTitle,
            senderNumber = cleanSender
        )
        if (existingIdx >= 0) {
            current[existingIdx] = card
        } else {
            current.add(0, card)
        }
        safeEdit {
            putString(KEY_REGISTERED_BANK_CARDS, serializeRegisteredBankCards(current))
        }
        _settingsFlow.value = readSettings()
        return card
    }

    fun removeRegisteredBankCard(cardId: String) {
        val updated = readSettings().registeredBankCards.filterNot { it.id == cardId }
        safeEdit {
            putString(KEY_REGISTERED_BANK_CARDS, serializeRegisteredBankCards(updated))
        }
        _settingsFlow.value = readSettings()
    }

    fun saveEndpointAndToken(endpointUrl: String, token: String, localMaxToman: Long = readSettings().localMaxToman) {
        val cleanUrl = com.example.network.GatewayApiClient.cleanCredentialString(endpointUrl)
        val cleanToken = com.example.network.GatewayApiClient.cleanCredentialString(token)
        safeEdit {
            putString(KEY_ENDPOINT_URL, cleanUrl)
            putString(KEY_GATEWAY_TOKEN, cleanToken)
            putLong(KEY_LOCAL_MAX_TOMAN, maxOf(0L, localMaxToman))
            putString(KEY_LAST_TOKEN_ERROR, null)
        }
        _settingsFlow.value = readSettings()
    }

    fun setServiceEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_SERVICE_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setAutoApproveInApp(enabled: Boolean) {
        safeEdit { putBoolean(KEY_AUTO_APPROVE_IN_APP, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setLocalMaxToman(maxToman: Long) {
        safeEdit { putLong(KEY_LOCAL_MAX_TOMAN, maxOf(0L, maxToman)) }
        _settingsFlow.value = readSettings()
    }

    fun updateServerSyncMeta(clockOffsetMs: Long, matchWindowHours: Int, autoMaxToman: Long) {
        safeEdit {
            putLong(KEY_LAST_CLOCK_OFFSET_MS, clockOffsetMs)
            putInt(KEY_LAST_SERVER_WINDOW_HOURS, maxOf(1, matchWindowHours))
            putLong(KEY_LAST_SERVER_MAX_TOMAN, maxOf(0L, autoMaxToman))
        }
        _settingsFlow.value = readSettings()
    }

    fun setFilterBanksEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_FILTER_BANKS_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun toggleBankSelection(bankCode: String) {
        val current = readSettings().selectedBankCodes.toMutableSet()
        if (current.contains(bankCode)) current.remove(bankCode) else current.add(bankCode)
        safeEdit { putStringSet(KEY_SELECTED_BANKS, current) }
        _settingsFlow.value = readSettings()
    }

    fun selectAllBanks(selectAll: Boolean) {
        val updated = if (selectAll) SmsParser.KNOWN_BANKS.map { it.code }.toSet() else emptySet()
        safeEdit { putStringSet(KEY_SELECTED_BANKS, updated) }
        _settingsFlow.value = readSettings()
    }

    fun recordTokenError(errorMsg: String?) {
        safeEdit { putString(KEY_LAST_TOKEN_ERROR, errorMsg) }
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
        private const val KEY_REGISTERED_BANK_CARDS = "registered_bank_cards_json"
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
