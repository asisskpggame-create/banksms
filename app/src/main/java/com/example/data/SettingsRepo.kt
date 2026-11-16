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

enum class AppThemeMode(
    val storageKey: String,
    val titleFa: String,
    val subtitleFa: String
) {
    DARK_NAVY(
        storageKey = "dark_navy",
        titleFa = "سرمه‌ای تیره",
        subtitleFa = "پالت استاندارد با عمق رنگی متعادل"
    ),
    MIDNIGHT_OLED(
        storageKey = "midnight_oled",
        titleFa = "مشکی خالص (Midnight OLED)",
        subtitleFa = "پس‌زمینهٔ #000000 برای کاهش مصرف باتری در نمایشگرهای AMOLED"
    );

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DARK_NAVY
    }
}

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
    val lastServerMaxToman: Long = 0L,
    val notifyOnServerConnectionError: Boolean = true,
    val notifyOnSensitiveBankSms: Boolean = true,
    val notifyOnAmbiguousOrManual: Boolean = true,
    val notifyOnApprovedInvoice: Boolean = true,
    val sensitiveSmsMinToman: Long = DEFAULT_SENSITIVE_SMS_MIN_TOMAN,
    val themeMode: AppThemeMode = AppThemeMode.DARK_NAVY,
    val customFilterRulesEnabled: Boolean = true,
    val customFilterRules: List<CustomFilterRule> = emptyList(),
    val blockedByCustomFiltersCount: Long = 0L,
    val autoSyncAfterErrors: Boolean = true,
    val lastErrorRecoverySyncAtMs: Long? = null,
    val lastErrorRecoverySyncedCount: Int = 0,
    val autoClearOldLogsEnabled: Boolean = false,
    val lastAutoLogCleanupAtMs: Long? = null,
    val lastAutoLogClearedCount: Int = 0,
    val hapticMasterEnabled: Boolean = true,
    val hapticOnServerHandshake: Boolean = true,
    val hapticHandshakePattern: HandshakeHapticPattern = HandshakeHapticPattern.DOUBLE_PULSE,
    val hapticHandshakeIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val hapticOnFailedSync: Boolean = true,
    val hapticFailedSyncPattern: FailedSyncHapticPattern = FailedSyncHapticPattern.TRIPLE_ALERT,
    val hapticFailedSyncIntensity: HapticIntensity = HapticIntensity.STRONG,
    val hapticCooldownEnabled: Boolean = true
) {
    val isMidnightOled: Boolean
        get() = themeMode == AppThemeMode.MIDNIGHT_OLED

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
        const val DEFAULT_SENSITIVE_SMS_MIN_TOMAN = 5_000_000L
        const val AUTO_CLEAR_LOGS_DAYS = 15L
        const val DEFAULT_KEEP_LOGS_DAYS = 30L

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
                lastServerMaxToman = prefs.getLong(KEY_LAST_SERVER_MAX_TOMAN, 0L),
                notifyOnServerConnectionError = prefs.getBoolean(KEY_NOTIFY_SERVER_ERROR, true),
                notifyOnSensitiveBankSms = prefs.getBoolean(KEY_NOTIFY_SENSITIVE_SMS, true),
                notifyOnAmbiguousOrManual = prefs.getBoolean(KEY_NOTIFY_AMBIGUOUS_MANUAL, true),
                notifyOnApprovedInvoice = prefs.getBoolean(KEY_NOTIFY_APPROVED_INVOICE, true),
                sensitiveSmsMinToman = prefs.getLong(
                    KEY_SENSITIVE_SMS_MIN_TOMAN,
                    GatewaySettings.DEFAULT_SENSITIVE_SMS_MIN_TOMAN
                ),
                themeMode = AppThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null)),
                customFilterRulesEnabled = prefs.getBoolean(KEY_CUSTOM_FILTERS_ENABLED, true),
                customFilterRules = parseCustomFilterRules(prefs.getString(KEY_CUSTOM_FILTER_RULES, null)),
                blockedByCustomFiltersCount = prefs.getLong(KEY_BLOCKED_BY_CUSTOM_FILTERS, 0L),
                autoSyncAfterErrors = prefs.getBoolean(KEY_AUTO_SYNC_AFTER_ERRORS, true),
                lastErrorRecoverySyncAtMs = prefs.getLong(KEY_LAST_RECOVERY_SYNC_AT_MS, -1L).takeIf { it > 0L },
                lastErrorRecoverySyncedCount = prefs.getInt(KEY_LAST_RECOVERY_SYNC_COUNT, 0),
                autoClearOldLogsEnabled = prefs.getBoolean(KEY_AUTO_CLEAR_OLD_LOGS, false),
                lastAutoLogCleanupAtMs = prefs.getLong(KEY_LAST_LOG_CLEANUP_AT_MS, -1L).takeIf { it > 0L },
                lastAutoLogClearedCount = prefs.getInt(KEY_LAST_LOG_CLEANUP_COUNT, 0),
                hapticMasterEnabled = prefs.getBoolean(KEY_HAPTIC_MASTER_ENABLED, true),
                hapticOnServerHandshake = prefs.getBoolean(KEY_HAPTIC_HANDSHAKE_ENABLED, true),
                hapticHandshakePattern = HandshakeHapticPattern.fromStorageKey(
                    prefs.getString(KEY_HAPTIC_HANDSHAKE_PATTERN, null)
                ),
                hapticHandshakeIntensity = HapticIntensity.fromStorageKey(
                    prefs.getString(KEY_HAPTIC_HANDSHAKE_INTENSITY, null)
                ),
                hapticOnFailedSync = prefs.getBoolean(KEY_HAPTIC_FAILED_SYNC_ENABLED, true),
                hapticFailedSyncPattern = FailedSyncHapticPattern.fromStorageKey(
                    prefs.getString(KEY_HAPTIC_FAILED_SYNC_PATTERN, null)
                ),
                hapticFailedSyncIntensity = HapticIntensity.fromStorageKey(
                    prefs.getString(KEY_HAPTIC_FAILED_SYNC_INTENSITY, null)
                ),
                hapticCooldownEnabled = prefs.getBoolean(KEY_HAPTIC_COOLDOWN_ENABLED, true)
            )
        } catch (_: Throwable) {
            GatewaySettings(selectedBankCodes = defaultBanks)
        }
    }

    private fun parseCustomFilterRules(rawJson: String?): List<CustomFilterRule> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pattern = obj.optString("pattern").trim()
                    if (pattern.isEmpty()) continue
                    add(
                        CustomFilterRule(
                            id = obj.optString("id").ifBlank { "rule_$i" },
                            name = obj.optString("name").trim(),
                            pattern = pattern,
                            target = FilterTarget.fromStorageKey(obj.optString("target")),
                            action = FilterAction.fromStorageKey(obj.optString("action")),
                            enabled = obj.optBoolean("enabled", true),
                            hitCount = obj.optLong("hitCount", 0L),
                            createdAtMs = obj.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeCustomFilterRules(rules: List<CustomFilterRule>): String {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("pattern", r.pattern)
                    .put("target", r.target.storageKey)
                    .put("action", r.action.storageKey)
                    .put("enabled", r.enabled)
                    .put("hitCount", r.hitCount)
                    .put("createdAtMs", r.createdAtMs)
            )
        }
        return arr.toString()
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

    fun setNotifyOnServerConnectionError(enabled: Boolean) {
        safeEdit { putBoolean(KEY_NOTIFY_SERVER_ERROR, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setNotifyOnSensitiveBankSms(enabled: Boolean) {
        safeEdit { putBoolean(KEY_NOTIFY_SENSITIVE_SMS, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setNotifyOnAmbiguousOrManual(enabled: Boolean) {
        safeEdit { putBoolean(KEY_NOTIFY_AMBIGUOUS_MANUAL, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setSensitiveSmsMinToman(minToman: Long) {
        safeEdit { putLong(KEY_SENSITIVE_SMS_MIN_TOMAN, maxOf(0L, minToman)) }
        _settingsFlow.value = readSettings()
    }

    fun setThemeMode(mode: AppThemeMode) {
        safeEdit { putString(KEY_THEME_MODE, mode.storageKey) }
        _settingsFlow.value = readSettings()
    }

    fun setMidnightOledEnabled(enabled: Boolean) {
        setThemeMode(if (enabled) AppThemeMode.MIDNIGHT_OLED else AppThemeMode.DARK_NAVY)
    }

    fun setCustomFilterRulesEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_CUSTOM_FILTERS_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun addCustomFilterRule(
        name: String,
        pattern: String,
        target: FilterTarget,
        action: FilterAction
    ): CustomFilterRule? {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return null
        val cleanName = name.trim()
        val existing = readSettings().customFilterRules.toMutableList()
        val newRule = CustomFilterRule(
            id = "flt_${System.currentTimeMillis()}_${existing.size}",
            name = cleanName,
            pattern = cleanPattern,
            target = target,
            action = action,
            enabled = true,
            hitCount = 0L,
            createdAtMs = System.currentTimeMillis()
        )
        existing.add(0, newRule)
        safeEdit { putString(KEY_CUSTOM_FILTER_RULES, serializeCustomFilterRules(existing)) }
        _settingsFlow.value = readSettings()
        return newRule
    }

    fun toggleCustomFilterRule(ruleId: String) {
        val updated = readSettings().customFilterRules.map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = !rule.enabled) else rule
        }
        safeEdit { putString(KEY_CUSTOM_FILTER_RULES, serializeCustomFilterRules(updated)) }
        _settingsFlow.value = readSettings()
    }

    fun removeCustomFilterRule(ruleId: String) {
        val updated = readSettings().customFilterRules.filterNot { it.id == ruleId }
        safeEdit { putString(KEY_CUSTOM_FILTER_RULES, serializeCustomFilterRules(updated)) }
        _settingsFlow.value = readSettings()
    }

    fun recordCustomFilterBlocked(ruleId: String) {
        val current = readSettings()
        val updatedRules = current.customFilterRules.map { rule ->
            if (rule.id == ruleId) rule.copy(hitCount = rule.hitCount + 1L) else rule
        }
        safeEdit {
            putString(KEY_CUSTOM_FILTER_RULES, serializeCustomFilterRules(updatedRules))
            putLong(KEY_BLOCKED_BY_CUSTOM_FILTERS, current.blockedByCustomFiltersCount + 1L)
        }
        _settingsFlow.value = readSettings()
    }

    fun setAutoSyncAfterErrors(enabled: Boolean) {
        safeEdit { putBoolean(KEY_AUTO_SYNC_AFTER_ERRORS, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun recordErrorRecoverySync(syncedCount: Int, atMs: Long = System.currentTimeMillis()) {
        safeEdit {
            putLong(KEY_LAST_RECOVERY_SYNC_AT_MS, atMs)
            putInt(KEY_LAST_RECOVERY_SYNC_COUNT, syncedCount.coerceAtLeast(0))
        }
        _settingsFlow.value = readSettings()
    }

    fun setNotifyOnApprovedInvoice(enabled: Boolean) {
        safeEdit { putBoolean(KEY_NOTIFY_APPROVED_INVOICE, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setAutoClearOldLogsEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_AUTO_CLEAR_OLD_LOGS, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun recordAutoLogCleanup(clearedCount: Int, atMs: Long = System.currentTimeMillis()) {
        safeEdit {
            putLong(KEY_LAST_LOG_CLEANUP_AT_MS, atMs)
            putInt(KEY_LAST_LOG_CLEANUP_COUNT, clearedCount.coerceAtLeast(0))
        }
        _settingsFlow.value = readSettings()
    }

    fun setHapticMasterEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_HAPTIC_MASTER_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticOnServerHandshake(enabled: Boolean) {
        safeEdit { putBoolean(KEY_HAPTIC_HANDSHAKE_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticHandshakePattern(pattern: HandshakeHapticPattern) {
        safeEdit { putString(KEY_HAPTIC_HANDSHAKE_PATTERN, pattern.storageKey) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticHandshakeIntensity(intensity: HapticIntensity) {
        safeEdit { putString(KEY_HAPTIC_HANDSHAKE_INTENSITY, intensity.storageKey) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticOnFailedSync(enabled: Boolean) {
        safeEdit { putBoolean(KEY_HAPTIC_FAILED_SYNC_ENABLED, enabled) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticFailedSyncPattern(pattern: FailedSyncHapticPattern) {
        safeEdit { putString(KEY_HAPTIC_FAILED_SYNC_PATTERN, pattern.storageKey) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticFailedSyncIntensity(intensity: HapticIntensity) {
        safeEdit { putString(KEY_HAPTIC_FAILED_SYNC_INTENSITY, intensity.storageKey) }
        _settingsFlow.value = readSettings()
    }

    fun setHapticCooldownEnabled(enabled: Boolean) {
        safeEdit { putBoolean(KEY_HAPTIC_COOLDOWN_ENABLED, enabled) }
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
        private const val KEY_NOTIFY_SERVER_ERROR = "notify_server_error"
        private const val KEY_NOTIFY_SENSITIVE_SMS = "notify_sensitive_sms"
        private const val KEY_NOTIFY_AMBIGUOUS_MANUAL = "notify_ambiguous_manual"
        private const val KEY_NOTIFY_APPROVED_INVOICE = "notify_approved_invoice"
        private const val KEY_SENSITIVE_SMS_MIN_TOMAN = "sensitive_sms_min_toman"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_CUSTOM_FILTERS_ENABLED = "custom_filters_enabled"
        private const val KEY_CUSTOM_FILTER_RULES = "custom_filter_rules_json"
        private const val KEY_BLOCKED_BY_CUSTOM_FILTERS = "blocked_by_custom_filters_count"
        private const val KEY_AUTO_SYNC_AFTER_ERRORS = "auto_sync_after_errors"
        private const val KEY_LAST_RECOVERY_SYNC_AT_MS = "last_recovery_sync_at_ms"
        private const val KEY_LAST_RECOVERY_SYNC_COUNT = "last_recovery_sync_count"
        private const val KEY_AUTO_CLEAR_OLD_LOGS = "auto_clear_old_logs_enabled"
        private const val KEY_LAST_LOG_CLEANUP_AT_MS = "last_log_cleanup_at_ms"
        private const val KEY_LAST_LOG_CLEANUP_COUNT = "last_log_cleanup_count"
        private const val KEY_HAPTIC_MASTER_ENABLED = "haptic_master_enabled"
        private const val KEY_HAPTIC_HANDSHAKE_ENABLED = "haptic_handshake_enabled"
        private const val KEY_HAPTIC_HANDSHAKE_PATTERN = "haptic_handshake_pattern"
        private const val KEY_HAPTIC_HANDSHAKE_INTENSITY = "haptic_handshake_intensity"
        private const val KEY_HAPTIC_FAILED_SYNC_ENABLED = "haptic_failed_sync_enabled"
        private const val KEY_HAPTIC_FAILED_SYNC_PATTERN = "haptic_failed_sync_pattern"
        private const val KEY_HAPTIC_FAILED_SYNC_INTENSITY = "haptic_failed_sync_intensity"
        private const val KEY_HAPTIC_COOLDOWN_ENABLED = "haptic_cooldown_enabled"

        @Volatile
        private var INSTANCE: SettingsRepo? = null

        fun getInstance(context: Context): SettingsRepo {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepo(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
