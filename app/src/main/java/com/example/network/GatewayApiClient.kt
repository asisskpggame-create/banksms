package com.example.network

import com.example.data.Item
import com.example.data.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class GatewayResult {
    data class Success(
        val httpCode: Int,
        val status: String,
        val approved: Int
    ) : GatewayResult()

    data class PermanentFailure(
        val httpCode: Int?,
        val isTokenError: Boolean,
        val message: String
    ) : GatewayResult()

    data class RetryableFailure(
        val httpCode: Int?,
        val message: String
    ) : GatewayResult()
}

sealed class PendingFetchResult {
    data class Success(
        val serverNowMs: Long,
        val clockOffsetMs: Long,
        val matchWindowHours: Int,
        val autoMaxToman: Long,
        val items: List<Item>
    ) : PendingFetchResult()

    data class NotFound404FallbackToSmsIn(val message: String) : PendingFetchResult()
    data class Unauthorized401(val message: String) : PendingFetchResult()
    data class PermanentError(val httpCode: Int?, val message: String) : PendingFetchResult()
    data class RetryableError(val httpCode: Int?, val message: String) : PendingFetchResult()
}

sealed class ApproveResult {
    data class Approved(
        val httpCode: Int,
        val status: String // "approved" | "duplicate"
    ) : ApproveResult()

    data class NotFound404FallbackToSmsIn(
        val message: String
    ) : ApproveResult()

    data class Conflict409(
        val reason: String
    ) : ApproveResult()

    data class Unauthorized401(
        val message: String
    ) : ApproveResult()

    data class RetryableError(
        val httpCode: Int?,
        val message: String
    ) : ApproveResult()
}

/**
 * Implements both `APP_SIDE_APPROVAL_SPEC.md` (`GET /sms-pending`, `POST /sms-approve` with HMAC-SHA256)
 * and `API_CONTRACT.md` (`POST /sms-in` for `تست اتصال` and automatic fallback if server only has `/sms-in`).
 */
object GatewayApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val INVISIBLE_MARKS = setOf(
        '\u200b', '\u200c', '\u200d', '\u200e', '\u200f',
        '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
        '\u2066', '\u2067', '\u2068', '\u2069', '\u00a0', '\ufeff'
    )

    private val defaultOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    internal var testClientOverride: OkHttpClient? = null

    private fun client(): OkHttpClient = testClientOverride ?: defaultOkHttpClient

    /** Strips invisible directional marks (e.g. when copy-pasted from Telegram) and whitespace. */
    fun cleanCredentialString(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return buildString(raw.length) {
            for (ch in raw) {
                if (ch !in INVISIBLE_MARKS && !ch.isISOControl()) {
                    append(ch)
                }
            }
        }.trim()
    }

    fun validateInputConfig(rawEndpointUrl: String, rawToken: String): String? {
        val url = cleanCredentialString(rawEndpointUrl)
        val token = cleanCredentialString(rawToken)
        if (url.isEmpty()) {
            return "آدرس سرور تنظیم نشده است."
        }
        if (url.startsWith("http://", ignoreCase = true)) {
            return "خطای امنیتی: آدرس سرور حتماً باید با https:// شروع شود (پروتکل غیررمزنگاری‌شده http مجاز نیست)."
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            return "آدرس سرور باید با https:// آغاز شود."
        }
        if (token.length < 20) {
            return "توکن درگاه (SMS_GATEWAY_TOKEN) باید حداقل ۲۰ کاراکتر باشد."
        }
        return null
    }

    fun extractBasePanelUrl(rawEndpointUrl: String): String {
        var trimmed = cleanCredentialString(rawEndpointUrl).trimEnd('/')
        if (trimmed.isEmpty()) return ""
        for (suffix in listOf("/sms-in", "/sms-pending", "/sms-approve")) {
            if (trimmed.endsWith(suffix, ignoreCase = true)) {
                trimmed = trimmed.dropLast(suffix.length).trimEnd('/')
            }
        }
        return trimmed
    }

    fun normalizeUrl(rawEndpointUrl: String): String {
        val base = extractBasePanelUrl(rawEndpointUrl)
        return if (base.isEmpty()) "" else "$base/sms-in"
    }

    fun buildPendingUrl(rawEndpointUrl: String): String {
        val base = extractBasePanelUrl(rawEndpointUrl)
        return if (base.isEmpty()) "" else "$base/sms-pending"
    }

    fun buildApproveUrl(rawEndpointUrl: String): String {
        val base = extractBasePanelUrl(rawEndpointUrl)
        return if (base.isEmpty()) "" else "$base/sms-approve"
    }

    /**
     * Computes `sig = HMAC_SHA256(key=token, msg = kind|public_id|fingerprint|amount_minor|ts)`
     * per Section 5 of `APP_SIDE_APPROVAL_SPEC.md`.
     */
    fun computeApprovalHmac(
        token: String,
        kind: String,
        publicId: String,
        fingerprint: String,
        amountMinor: Long,
        ts: Long
    ): String {
        val cleanToken = cleanCredentialString(token)
        val message = "$kind|$publicId|$fingerprint|$amountMinor|$ts"
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(cleanToken.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * `GET /<PANEL_PATH>/sms-pending` (token sent via `X-Token` header)
     * Returns waiting orders/top-ups and `server_now_ms` to compute clock offset.
     */
    suspend fun fetchPendingItems(
        endpointUrl: String,
        token: String
    ): PendingFetchResult = withContext(Dispatchers.IO) {
        val validationError = validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            return@withContext PendingFetchResult.PermanentError(null, validationError)
        }

        val cleanToken = cleanCredentialString(token)
        val rawUrl = buildPendingUrl(endpointUrl)
        val httpUrl = rawUrl.toHttpUrlOrNull()
            ?: return@withContext PendingFetchResult.PermanentError(null, "فرمت آدرس سرور نامعتبر است.")

        val request = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/json")
            .header("X-Token", cleanToken)
            .get()
            .build()

        try {
            val phoneBeforeMs = System.currentTimeMillis()
            client().newCall(request).execute().use { response ->
                val phoneAfterMs = System.currentTimeMillis()
                val latencyMs = (phoneAfterMs - phoneBeforeMs).coerceAtLeast(1L)
                val phoneMidpointMs = (phoneBeforeMs + phoneAfterMs) / 2L
                val code = response.code
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                val errMsg = json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("detail")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("message")?.takeIf { it.isNotEmpty() }

                when {
                    code in 200..299 && json != null -> {
                        ServerHealthTracker.recordSuccess(httpCode = code, latencyMs = latencyMs)
                        val serverNowMs = json.optLong("server_now_ms", phoneMidpointMs)
                        val offsetMs = serverNowMs - phoneMidpointMs
                        val matchWindowHours = json.optInt("match_window_hours", 24).coerceAtLeast(1)
                        val autoMaxToman = json.optLong("auto_max_toman", 0L).coerceAtLeast(0L)
                        val itemsArr = json.optJSONArray("items") ?: JSONArray()
                        val parsedItems = mutableListOf<Item>()
                        for (i in 0 until itemsArr.length()) {
                            val obj = itemsArr.optJSONObject(i) ?: continue
                            val kind = obj.optString("kind", "order")
                            val publicId = obj.optString("public_id", "")
                            val dueMinor = obj.optLong("due_minor", 0L)
                            val createdMs = obj.optLong("created_ms", 0L)
                            val early = obj.optBoolean("early", false)
                            if (publicId.isNotEmpty() && dueMinor > 0L) {
                                parsedItems.add(
                                    Item(
                                        kind = kind,
                                        publicId = publicId,
                                        dueMinor = dueMinor,
                                        at = createdMs,
                                        early = early
                                    )
                                )
                            }
                        }
                        PendingFetchResult.Success(
                            serverNowMs = serverNowMs,
                            clockOffsetMs = offsetMs,
                            matchWindowHours = matchWindowHours,
                            autoMaxToman = autoMaxToman,
                            items = parsedItems
                        )
                    }
                    code == 401 -> {
                        val msg = errMsg ?: "توکن نامعتبر است (خطای ۴۰۱)."
                        ServerHealthTracker.recordFailure(httpCode = 401, errorMessage = msg, latencyMs = latencyMs)
                        PendingFetchResult.Unauthorized401(msg)
                    }
                    code == 404 || code == 405 -> {
                        // Server is reachable (classic /sms-in mode)
                        ServerHealthTracker.recordSuccess(httpCode = code, latencyMs = latencyMs)
                        PendingFetchResult.NotFound404FallbackToSmsIn(
                            errMsg ?: "سرور روی نسخهٔ کلاسیک /sms-in است؛ ارسال مستقیم به /sms-in انجام می‌شود."
                        )
                    }
                    code == 429 -> {
                        val msg = errMsg ?: "محدودیت نرخ سرور (۴۲۹)؛ چند دقیقه بعد دوباره تلاش می‌شود."
                        ServerHealthTracker.recordFailure(httpCode = 429, errorMessage = msg, latencyMs = latencyMs)
                        PendingFetchResult.RetryableError(429, msg)
                    }
                    else -> {
                        val msg = errMsg ?: "خطای موقت در دریافت لیست سفارش‌های منتظر (کد $code)."
                        ServerHealthTracker.recordFailure(httpCode = code, errorMessage = msg, latencyMs = latencyMs)
                        PendingFetchResult.RetryableError(code, msg)
                    }
                }
            }
        } catch (e: IOException) {
            val msg = describeNetworkException(e)
            ServerHealthTracker.recordFailure(httpCode = null, errorMessage = msg)
            PendingFetchResult.RetryableError(null, msg)
        } catch (e: Exception) {
            val msg = "خطای ارتباط با سرور: ${e.localizedMessage ?: "نامشخص"}"
            ServerHealthTracker.recordFailure(httpCode = null, errorMessage = msg)
            PendingFetchResult.RetryableError(null, msg)
        }
    }

    fun describeNetworkException(e: Throwable): String = when (e) {
        is java.net.UnknownHostException ->
            "عدم اتصال به اینترنت یا خطای DNS (آدرس سرور یافت نشد)"
        is java.net.SocketTimeoutException ->
            "پایان مهلت اتصال به سرور (Timeout / کندی یا قطعی اینترنت)"
        is java.net.ConnectException ->
            "عدم امکان برقراری اتصال با سرور (اینترنت قطع است یا سرور پاسخ نمی‌دهد)"
        is javax.net.ssl.SSLException ->
            "خطای امنیتی گواهی SSL/HTTPS در ارتباط با سرور"
        else ->
            "خطای شبکه و عدم دسترسی به سرور (${e.javaClass.simpleName})"
    }

    /**
     * `POST /<PANEL_PATH>/sms-approve`
     * Sends matched deposit + order/top-up approval with HMAC-SHA256 signature.
     */
    suspend fun sendApproveRequest(
        endpointUrl: String,
        token: String,
        kind: String,
        publicId: String,
        fingerprint: String,
        amountMinor: Long,
        bank: String,
        rawSms: String,
        smsReceivedMs: Long,
        tsMs: Long
    ): ApproveResult = withContext(Dispatchers.IO) {
        val cleanToken = cleanCredentialString(token)
        val finalUrl = buildApproveUrl(endpointUrl)
        val sig = computeApprovalHmac(
            token = cleanToken,
            kind = kind,
            publicId = publicId,
            fingerprint = fingerprint,
            amountMinor = amountMinor,
            ts = tsMs
        )

        val payloadJson = JSONObject().apply {
            put("token", cleanToken)
            put("kind", kind)
            put("public_id", publicId)
            put("fingerprint", fingerprint)
            put("amount_minor", amountMinor)
            put("bank", bank.take(32))
            put("sms", rawSms.take(SmsParser.MAX_SMS_LENGTH))
            put("sms_received_ms", smsReceivedMs)
            put("ts", tsMs)
            put("sig", sig)
        }.toString()

        val request = try {
            Request.Builder()
                .url(finalUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Token", cleanToken)
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext ApproveResult.RetryableError(
                null,
                "آدرس نامعتبر: ${e.localizedMessage ?: finalUrl}"
            )
        }

        try {
            client().newCall(request).execute().use { response ->
                val code = response.code
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                val status = json?.optString("status", "") ?: ""
                val reason = json?.optString("reason")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("detail")?.takeIf { it.isNotEmpty() }

                when {
                    code in 200..299 -> {
                        ApproveResult.Approved(
                            httpCode = code,
                            status = status.ifEmpty { "approved" }
                        )
                    }
                    code == 409 -> {
                        ApproveResult.Conflict409(
                            reason = reason ?: "سرور تأیید خودکار را رد کرد (۴۰۹ Conflict) — لطفاً دستی بررسی کنید."
                        )
                    }
                    code == 401 -> {
                        ApproveResult.Unauthorized401(
                            message = reason ?: "توکن یا امضای HMAC نامعتبر است (خطای ۴۰۱)."
                        )
                    }
                    code == 404 || code == 405 -> {
                        ApproveResult.NotFound404FallbackToSmsIn(
                            message = reason ?: "سرور روی نسخهٔ کلاسیک /sms-in است؛ ارسال مستقیم به /sms-in انجام می‌شود."
                        )
                    }
                    code == 429 -> {
                        ApproveResult.RetryableError(
                            httpCode = 429,
                            message = reason ?: "سقف نرخ تأیید در ساعت پر شده است (۴۲۹)؛ تلاش مجدد با تأخیر."
                        )
                    }
                    else -> {
                        ApproveResult.RetryableError(
                            httpCode = code,
                            message = reason ?: "خطای موقت سرور در sms-approve (کد $code)."
                        )
                    }
                }
            }
        } catch (e: IOException) {
            ApproveResult.RetryableError(
                httpCode = null,
                message = describeNetworkException(e)
            )
        } catch (e: Exception) {
            ApproveResult.RetryableError(
                httpCode = null,
                message = "خطای ارتباط: ${e.localizedMessage ?: "نامشخص"}"
            )
        }
    }

    /**
     * `POST /<PANEL_PATH>/sms-in`
     */
    suspend fun sendSms(
        endpointUrl: String,
        token: String,
        smsText: String,
        bankCode: String
    ): GatewayResult = withContext(Dispatchers.IO) {
        val validationError = validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            return@withContext GatewayResult.PermanentFailure(
                httpCode = null,
                isTokenError = cleanCredentialString(token).length < 20,
                message = validationError
            )
        }

        val cleanToken = cleanCredentialString(token)
        val finalUrl = normalizeUrl(endpointUrl)
        val trimmedSms = smsText.take(SmsParser.MAX_SMS_LENGTH)
        if (trimmedSms.isBlank()) {
            return@withContext GatewayResult.PermanentFailure(
                httpCode = 400,
                isTokenError = false,
                message = "متن پیامک خالی است."
            )
        }

        val payloadJson = JSONObject().apply {
            put("token", cleanToken)
            put("sms", trimmedSms)
            put("bank", bankCode.trim())
        }.toString()

        val request = try {
            Request.Builder()
                .url(finalUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Token", cleanToken)
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext GatewayResult.PermanentFailure(
                httpCode = null,
                isTokenError = false,
                message = "فرمت آدرس سرور نامعتبر است: ${e.localizedMessage ?: finalUrl}"
            )
        }

        try {
            val startedMs = System.currentTimeMillis()
            client().newCall(request).execute().use { response ->
                val latencyMs = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
                val code = response.code
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                val serverErrorMsg = json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("detail")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("message")?.takeIf { it.isNotEmpty() }

                when {
                    code in 200..299 -> {
                        ServerHealthTracker.recordSuccess(httpCode = code, latencyMs = latencyMs)
                        val status = json?.optString("status", "new") ?: "new"
                        val approved = json?.optInt("approved", 0) ?: 0
                        GatewayResult.Success(code, status, approved)
                    }
                    code == 401 -> {
                        val msg = serverErrorMsg ?: "توکن نامعتبر است (خطای ۴۰۱). لطفاً توکن را در تنظیمات بررسی کنید."
                        ServerHealthTracker.recordFailure(httpCode = 401, errorMessage = msg, latencyMs = latencyMs)
                        GatewayResult.PermanentFailure(httpCode = 401, isTokenError = true, message = msg)
                    }
                    code == 404 -> {
                        val msg = serverErrorMsg ?: "درگاه پیامک سمت سرور خاموش است یا مسیر اشتباه است (خطای ۴۰۴)."
                        ServerHealthTracker.recordFailure(httpCode = 404, errorMessage = msg, latencyMs = latencyMs)
                        GatewayResult.PermanentFailure(httpCode = 404, isTokenError = false, message = msg)
                    }
                    code == 400 -> {
                        ServerHealthTracker.recordSuccess(httpCode = 400, latencyMs = latencyMs)
                        GatewayResult.PermanentFailure(
                            httpCode = 400,
                            isTokenError = false,
                            message = serverErrorMsg ?: "متن پیامک نامعتبر است (خطای ۴۰۰)."
                        )
                    }
                    code == 429 -> {
                        val msg = serverErrorMsg ?: "محدودیت نرخ سرور (خطای ۴۲۹)؛ چند دقیقه بعد دوباره تلاش می‌شود."
                        ServerHealthTracker.recordFailure(httpCode = 429, errorMessage = msg, latencyMs = latencyMs)
                        GatewayResult.RetryableFailure(httpCode = 429, message = msg)
                    }
                    else -> {
                        val msg = serverErrorMsg ?: "خطای موقت سرور (کد $code) — در صف تلاش مجدد قرار گرفت."
                        ServerHealthTracker.recordFailure(httpCode = code, errorMessage = msg, latencyMs = latencyMs)
                        GatewayResult.RetryableFailure(httpCode = code, message = msg)
                    }
                }
            }
        } catch (e: IOException) {
            val msg = describeNetworkException(e)
            ServerHealthTracker.recordFailure(httpCode = null, errorMessage = msg)
            GatewayResult.RetryableFailure(httpCode = null, message = msg)
        } catch (e: Exception) {
            val msg = "خطای ارتباط با سرور: ${e.localizedMessage ?: "نامشخص"}"
            ServerHealthTracker.recordFailure(httpCode = null, errorMessage = msg)
            GatewayResult.RetryableFailure(httpCode = null, message = msg)
        }
    }

    suspend fun testConnection(
        endpointUrl: String,
        token: String
    ): GatewayResult {
        return sendSms(
            endpointUrl = endpointUrl,
            token = token,
            smsText = SmsParser.TEST_MARK,
            bankCode = ""
        )
    }

    /**
     * Lightweight real-time server probe used by the Home top status widget.
     * Probes `GET /sms-pending`, updates `ServerHealthTracker`, and falls back to `/sms-in` test
     * if the server returns 404/405.
     */
    suspend fun pingServer(endpointUrl: String, token: String): Boolean {
        val validationError = validateInputConfig(endpointUrl, token)
        if (validationError != null) {
            ServerHealthTracker.recordFailure(httpCode = null, errorMessage = validationError)
            return false
        }
        ServerHealthTracker.setChecking(true)
        return when (val pendingRes = fetchPendingItems(endpointUrl, token)) {
            is PendingFetchResult.Success -> true
            is PendingFetchResult.NotFound404FallbackToSmsIn -> {
                testConnection(endpointUrl, token) is GatewayResult.Success
            }
            else -> false
        }
    }
}
