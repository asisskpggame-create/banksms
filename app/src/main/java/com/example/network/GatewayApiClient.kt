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

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

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
     * `GET /<PANEL_PATH>/sms-pending?token=...` (plus `X-Token` header)
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
            ?.newBuilder()
            ?.addQueryParameter("token", cleanToken)
            ?.build()
            ?: return@withContext PendingFetchResult.PermanentError(null, "فرمت آدرس سرور نامعتبر است.")

        val request = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/json")
            .header("X-Token", cleanToken)
            .get()
            .build()

        try {
            val phoneBeforeMs = System.currentTimeMillis()
            okHttpClient.newCall(request).execute().use { response ->
                val phoneAfterMs = System.currentTimeMillis()
                val phoneMidpointMs = (phoneBeforeMs + phoneAfterMs) / 2L
                val code = response.code
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                val errMsg = json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("detail")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("message")?.takeIf { it.isNotEmpty() }

                when {
                    code in 200..299 && json != null -> {
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
                    code == 401 -> PendingFetchResult.Unauthorized401(
                        errMsg ?: "توکن نامعتبر است (خطای ۴۰۱)."
                    )
                    code == 404 || code == 405 -> PendingFetchResult.NotFound404FallbackToSmsIn(
                        errMsg ?: "سرور روی نسخهٔ کلاسیک /sms-in است؛ ارسال مستقیم به /sms-in انجام می‌شود."
                    )
                    code == 429 -> PendingFetchResult.RetryableError(
                        429,
                        errMsg ?: "محدودیت نرخ سرور (۴۲۹)؛ چند دقیقه بعد دوباره تلاش می‌شود."
                    )
                    else -> PendingFetchResult.RetryableError(
                        code,
                        errMsg ?: "خطای موقت در دریافت لیست سفارش‌های منتظر (کد $code)."
                    )
                }
            }
        } catch (e: IOException) {
            PendingFetchResult.RetryableError(
                null,
                "عدم دسترسی به شبکه (${e.javaClass.simpleName})."
            )
        } catch (e: Exception) {
            PendingFetchResult.RetryableError(
                null,
                "خطای ارتباط با سرور: ${e.localizedMessage ?: "نامشخص"}"
            )
        }
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
            okHttpClient.newCall(request).execute().use { response ->
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
                message = "خطای شبکه در ارسال تأییدیه (${e.javaClass.simpleName}) — در صف تلاش مجدد."
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
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                val serverErrorMsg = json?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("detail")?.takeIf { it.isNotEmpty() }
                    ?: json?.optString("message")?.takeIf { it.isNotEmpty() }

                when {
                    code in 200..299 -> {
                        val status = json?.optString("status", "new") ?: "new"
                        val approved = json?.optInt("approved", 0) ?: 0
                        GatewayResult.Success(code, status, approved)
                    }
                    code == 401 -> GatewayResult.PermanentFailure(
                        httpCode = 401,
                        isTokenError = true,
                        message = serverErrorMsg ?: "توکن نامعتبر است (خطای ۴۰۱). لطفاً توکن را در تنظیمات بررسی کنید."
                    )
                    code == 404 -> GatewayResult.PermanentFailure(
                        httpCode = 404,
                        isTokenError = false,
                        message = serverErrorMsg ?: "درگاه پیامک سمت سرور خاموش است یا مسیر اشتباه است (خطای ۴۰۴)."
                    )
                    code == 400 -> GatewayResult.PermanentFailure(
                        httpCode = 400,
                        isTokenError = false,
                        message = serverErrorMsg ?: "متن پیامک نامعتبر است (خطای ۴۰۰)."
                    )
                    code == 429 -> GatewayResult.RetryableFailure(
                        httpCode = 429,
                        message = serverErrorMsg ?: "محدودیت نرخ سرور (خطای ۴۲۹)؛ چند دقیقه بعد دوباره تلاش می‌شود."
                    )
                    else -> GatewayResult.RetryableFailure(
                        httpCode = code,
                        message = serverErrorMsg ?: "خطای موقت سرور (کد $code) — در صف تلاش مجدد قرار گرفت."
                    )
                }
            }
        } catch (e: IOException) {
            GatewayResult.RetryableFailure(
                httpCode = null,
                message = "عدم دسترسی به شبکه یا سرور (${e.javaClass.simpleName})."
            )
        } catch (e: Exception) {
            GatewayResult.RetryableFailure(
                httpCode = null,
                message = "خطای ارتباط با سرور: ${e.localizedMessage ?: "نامشخص"}"
            )
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
}
