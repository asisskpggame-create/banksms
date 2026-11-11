package com.example.network

import com.example.data.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GatewayResult {
    data class Success(
        val httpCode: Int,
        val status: String, // "new" | "duplicate" | "ignored" | "test"
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

/**
 * Implements exact server contract from `spec/API_CONTRACT.md` and `reference/sms_in.py`:
 * POST https://YOUR-DOMAIN/<PANEL_PATH>/sms-in
 * Body: {"token": "...", "sms": "...", "bank": "..."}
 */
object GatewayApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun validateInputConfig(rawEndpointUrl: String, rawToken: String): String? {
        val url = rawEndpointUrl.trim()
        val token = rawToken.trim()
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

    fun normalizeUrl(rawEndpointUrl: String): String {
        val trimmed = rawEndpointUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/sms-in", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/sms-in"
        }
    }

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
                isTokenError = token.trim().length < 20,
                message = validationError
            )
        }

        val finalUrl = normalizeUrl(endpointUrl)
        val trimmedSms = smsText.trim().take(SmsParser.MAX_SMS_LENGTH)
        if (trimmedSms.isEmpty()) {
            return@withContext GatewayResult.PermanentFailure(
                httpCode = 400,
                isTokenError = false,
                message = "متن پیامک خالی است."
            )
        }

        val payloadJson = JSONObject().apply {
            put("token", token.trim())
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
                        GatewayResult.Success(
                            httpCode = code,
                            status = status,
                            approved = approved
                        )
                    }
                    code == 401 -> {
                        // 401: Wrong token — DO NOT RETRY automatically per spec/API_CONTRACT.md
                        GatewayResult.PermanentFailure(
                            httpCode = 401,
                            isTokenError = true,
                            message = serverErrorMsg ?: "توکن نامعتبر است (خطای ۴۰۱). لطفاً توکن را در تنظیمات بررسی کنید."
                        )
                    }
                    code == 404 -> {
                        // 404: SMS gateway disabled on server or wrong path — permanent error
                        GatewayResult.PermanentFailure(
                            httpCode = 404,
                            isTokenError = false,
                            message = serverErrorMsg ?: "درگاه پیامک سمت سرور خاموش است یا مسیر اشتباه است (خطای ۴۰۴)."
                        )
                    }
                    code == 400 -> {
                        GatewayResult.PermanentFailure(
                            httpCode = 400,
                            isTokenError = false,
                            message = serverErrorMsg ?: "متن پیامک نامعتبر است (خطای ۴۰۰)."
                        )
                    }
                    code == 429 -> {
                        // 429: Rate limit — wait and retry later
                        GatewayResult.RetryableFailure(
                            httpCode = 429,
                            message = serverErrorMsg ?: "محدودیت نرخ سرور (خطای ۴۲۹)؛ چند دقیقه بعد دوباره تلاش می‌شود."
                        )
                    }
                    else -> {
                        // Any other non-2xx (e.g., 500, 502, 503, 504) -> queue and retry with backoff
                        GatewayResult.RetryableFailure(
                            httpCode = code,
                            message = serverErrorMsg ?: "خطای موقت سرور (کد $code) — در صف تلاش مجدد قرار گرفت."
                        )
                    }
                }
            }
        } catch (e: IOException) {
            GatewayResult.RetryableFailure(
                httpCode = null,
                message = "عدم دسترسی به شبکه یا سرور (${e.javaClass.simpleName}) — در صف تلاش مجدد قرار گرفت."
            )
        } catch (e: Exception) {
            GatewayResult.RetryableFailure(
                httpCode = null,
                message = "خطای ارتباط با سرور: ${e.localizedMessage ?: "نامشخص"}"
            )
        }
    }

    /**
     * Sends exact immutable string `تست اتصال` per `spec/API_CONTRACT.md`.
     * Server responds with `{"ok": true, "status": "test"}` without storing a deposit.
     */
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
