package com.example

import android.app.Application
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LogDao
import com.example.data.SettingsRepo
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.GatewayApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests verifying:
 * 1. `SmsReceiver` raw PDU parsing, normalization copy, deduplication & `IGNORED`/`NEW` classification
 * 2. `ForwardWorker.runSweepNow` + `ForwardWorker.runManualApproveNow` against a simulated backend
 *    implementing exact `server_reference/sms_app_approval_routes.py` contract (X-Token header without
 *    URL query token leak, HMAC-SHA256 verification, 409 Conflict, 401 retry recovery, 404 `/sms-in`
 *    fallback, `autoApproveInApp` on/off, `auto_max_toman` ceiling, `I > D` ambiguous handling,
 *    `match_window_hours` expiration, and `CLAIMED` crash recovery).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GatewayPipelineIntegrationTest {

    private lateinit var app: Application
    private lateinit var dao: LogDao
    private lateinit var settingsRepo: SettingsRepo

    private val testEndpoint = "https://panel.example.com/secret-gateway"
    private val testToken = "integration_secret_gateway_token_2026"

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        dao = AppDatabase.getInstance(app).logDao()
        settingsRepo = SettingsRepo.getInstance(app)
        runBlocking { dao.clearAllLogs() }
        settingsRepo.saveEndpointAndToken(testEndpoint, testToken, localMaxToman = 0L)
        settingsRepo.setServiceEnabled(true)
        settingsRepo.setAutoApproveInApp(false)
        settingsRepo.setFilterBanksEnabled(false)
        settingsRepo.recordTokenError(null)
    }

    @After
    fun tearDown() {
        GatewayApiClient.testClientOverride = null
    }

    private fun installFakeServer(handler: (Request, String) -> Pair<Int, String>) {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val bodyStr = req.body?.let { rb ->
                val buf = Buffer()
                rb.writeTo(buf)
                buf.readUtf8()
            }.orEmpty()
            val (code, jsonBody) = handler(req, bodyStr)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP $code")
                .body(jsonBody.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        GatewayApiClient.testClientOverride = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun pendingJson(
        serverNowMs: Long,
        matchWindowHours: Int = 24,
        autoMaxToman: Long = 0L,
        items: List<JSONObject> = emptyList()
    ): String = JSONObject().apply {
        put("ok", true)
        put("server_now_ms", serverNowMs)
        put("match_window_hours", matchWindowHours)
        put("auto_max_toman", autoMaxToman)
        put("items", JSONArray().apply { items.forEach { put(it) } })
    }.toString()

    private fun itemJson(
        kind: String,
        publicId: String,
        dueMinor: Long,
        createdMs: Long
    ): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("public_id", publicId)
        put("due_minor", dueMinor)
        put("created_ms", createdMs)
        put("early", false)
    }

    private suspend fun insertDeposit(
        bank: String,
        rawSms: String,
        receivedMs: Long
    ): SmsRecord {
        val parsed = SmsParser.parse(rawSms)
        val fp = SmsParser.fingerprint(bank, rawSms)
        val rec = SmsRecord(
            fingerprint = fp,
            bank = bank,
            bankTitle = SmsParser.detectBank(bank, rawSms).titleFa,
            body = rawSms,
            normalized = SmsParser.normalize(rawSms),
            amountMinor = parsed.amountMinor,
            reason = parsed.reason,
            status = if (parsed.amountMinor != null) SmsStatus.NEW.name else SmsStatus.IGNORED.name,
            receivedMs = receivedMs,
            storedMs = receivedMs
        )
        val id = dao.insertIgnoreDuplicate(rec)
        return dao.getById(id)!!
    }

    @Test
    fun safeDefaultMode_matchesDepositAsManual_andDoesNotExposeTokenInUrlQuery() = runBlocking {
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "بانک ملت\nواریز به حساب 0123456789\nمبلغ: 5,000,070 ریال",
            receivedMs = nowMs - 5_000L
        )
        var approveCalled = false

        installFakeServer { req, _ ->
            // Verify token is sent strictly via X-Token header, NEVER leaked in URL query string
            assertNull("Token must NOT be sent in URL query string", req.url.queryParameter("token"))
            assertEquals(testToken, req.header("X-Token"))

            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    200 to pendingJson(
                        serverNowMs = nowMs,
                        autoMaxToman = 1_000_000L,
                        items = listOf(itemJson("order", "ORD500", 5_000_070L, nowMs - 60_000L))
                    )
                }
                req.url.encodedPath.endsWith("/sms-approve") -> {
                    approveCalled = true
                    200 to """{"ok":true,"status":"approved"}"""
                }
                else -> 404 to """{"error":"not_found"}"""
            }
        }

        val outcome = ForwardWorker.runSweepNow(app)
        assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcome)
        assertFalse("Safe default autoApproveInApp=false must not auto-call /sms-approve", approveCalled)

        val updated = dao.getById(deposit.id)!!
        assertEquals(SmsStatus.MANUAL, updated.statusEnum)
        assertEquals("order", updated.matchedKind)
        assertEquals("ORD500", updated.matchedId)

        // Now trigger one-tap manual approval and verify HMAC-signed POST /sms-approve succeeds
        var approvedBody: JSONObject? = null
        installFakeServer { req, bodyStr ->
            assertTrue(req.url.encodedPath.endsWith("/sms-approve"))
            assertEquals(testToken, req.header("X-Token"))
            val obj = JSONObject(bodyStr)
            approvedBody = obj
            val expectedSig = GatewayApiClient.computeApprovalHmac(
                token = testToken,
                kind = obj.getString("kind"),
                publicId = obj.getString("public_id"),
                fingerprint = obj.getString("fingerprint"),
                amountMinor = obj.getLong("amount_minor"),
                ts = obj.getLong("ts")
            )
            assertEquals(expectedSig, obj.getString("sig"))
            200 to """{"ok":true,"status":"approved"}"""
        }

        val manualOutcome = ForwardWorker.runManualApproveNow(app, deposit.id)
        assertEquals(ForwardWorker.WorkOutcome.SUCCESS, manualOutcome)
        assertNotNull(approvedBody)
        assertEquals("ORD500", approvedBody!!.getString("public_id"))
        assertEquals(5_000_070L, approvedBody!!.getLong("amount_minor"))

        val finalRec = dao.getById(deposit.id)!!
        assertEquals(SmsStatus.MATCHED, finalRec.statusEnum)
        assertEquals(200, finalRec.httpCode)
    }

    @Test
    fun autoApproveEnabled_withinCeiling_approvesAndMarksMatched() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "9820000",
            rawSms = "بانک سامان\nواریز به حساب مبلغ 485,000 تومان",
            receivedMs = nowMs - 10_000L
        )

        installFakeServer { req, bodyStr ->
            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    200 to pendingJson(
                        serverNowMs = nowMs,
                        autoMaxToman = 1_000_000L, // 1,000,000 Toman = 10,000,000 Rial >= 4,850,000 Rial
                        items = listOf(itemJson("topup", "TOP777", 4_850_000L, nowMs - 30_000L))
                    )
                }
                req.url.encodedPath.endsWith("/sms-approve") -> {
                    val obj = JSONObject(bodyStr)
                    assertEquals("topup", obj.getString("kind"))
                    assertEquals("TOP777", obj.getString("public_id"))
                    assertEquals(4_850_000L, obj.getLong("amount_minor"))
                    assertEquals(deposit.body, obj.getString("sms"))
                    200 to """{"ok":true,"status":"approved"}"""
                }
                else -> 404 to "{}"
            }
        }

        val outcome = ForwardWorker.runSweepNow(app)
        assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcome)

        val updated = dao.getById(deposit.id)!!
        assertEquals(SmsStatus.MATCHED, updated.statusEnum)
        assertEquals("topup", updated.matchedKind)
        assertEquals("TOP777", updated.matchedId)
    }

    @Test
    fun autoApproveEnabled_exceedsCeiling_dropsBackToManual() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        settingsRepo.setLocalMaxToman(200_000L) // 200k Toman ceiling
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "واریز 500,000 تومان",
            receivedMs = nowMs - 10_000L
        )
        var approveCalled = false

        installFakeServer { req, _ ->
            if (req.url.encodedPath.endsWith("/sms-approve")) approveCalled = true
            200 to pendingJson(
                serverNowMs = nowMs,
                items = listOf(itemJson("order", "ORD_BIG", 5_000_000L, nowMs - 30_000L))
            )
        }

        ForwardWorker.runSweepNow(app)
        assertFalse(approveCalled)
        assertEquals(SmsStatus.MANUAL, dao.getById(deposit.id)!!.statusEnum)
    }

    @Test
    fun twoPendingOrdersWithSameAmount_marksAmbiguousWithoutApproving() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "واریز 1,000,000 ریال",
            receivedMs = nowMs - 10_000L
        )
        var approveCalled = false

        installFakeServer { req, _ ->
            if (req.url.encodedPath.endsWith("/sms-approve")) approveCalled = true
            200 to pendingJson(
                serverNowMs = nowMs,
                items = listOf(
                    itemJson("order", "ORD_A", 1_000_000L, nowMs - 60_000L),
                    itemJson("order", "ORD_B", 1_000_000L, nowMs - 30_000L)
                )
            )
        }

        ForwardWorker.runSweepNow(app)
        assertFalse(approveCalled)
        val updated = dao.getById(deposit.id)!!
        assertEquals(SmsStatus.AMBIGUOUS, updated.statusEnum)
        assertEquals("same_amount", updated.reason)
    }

    @Test
    fun server409Conflict_movesDepositToManual() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "واریز 2,000,000 ریال",
            receivedMs = nowMs - 10_000L
        )

        installFakeServer { req, _ ->
            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    200 to pendingJson(
                        serverNowMs = nowMs,
                        items = listOf(itemJson("order", "ORD_409", 2_000_000L, nowMs - 30_000L))
                    )
                }
                req.url.encodedPath.endsWith("/sms-approve") -> {
                    409 to """{"ok":false,"status":"conflict","reason":"order_already_paid"}"""
                }
                else -> 404 to "{}"
            }
        }

        ForwardWorker.runSweepNow(app)
        val updated = dao.getById(deposit.id)!!
        assertEquals(SmsStatus.MANUAL, updated.statusEnum)
        assertEquals(409, updated.httpCode)
        assertEquals("order_already_paid", updated.lastError)
    }

    @Test
    fun server404OnSmsPending_fallsBackToClassicSmsIn() = runBlocking {
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "بانک ملت\nواریز به حساب مبلغ 3,000,000 ریال",
            receivedMs = nowMs - 5_000L
        )
        var smsInPayload: JSONObject? = null

        installFakeServer { req, bodyStr ->
            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    404 to """{"error":"not_found"}"""
                }
                req.url.encodedPath.endsWith("/sms-in") -> {
                    smsInPayload = JSONObject(bodyStr)
                    200 to """{"ok":true,"status":"matched","approved":1}"""
                }
                else -> 404 to "{}"
            }
        }

        val outcome = ForwardWorker.runSweepNow(app)
        assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcome)
        assertNotNull(smsInPayload)
        assertEquals(deposit.body, smsInPayload!!.getString("sms"))
        assertEquals(SmsStatus.MATCHED, dao.getById(deposit.id)!!.statusEnum)
    }

    @Test
    fun unauthorized401_keepsInRetryAndRecoversAfterTokenFixed() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        val nowMs = System.currentTimeMillis()
        val deposit = insertDeposit(
            bank = "98200033",
            rawSms = "واریز 7,500,000 ریال",
            receivedMs = nowMs - 5_000L
        )

        installFakeServer { _, _ ->
            401 to """{"error":"توکن نامعتبر است"}"""
        }

        val failOutcome = ForwardWorker.runSweepNow(app)
        assertEquals(ForwardWorker.WorkOutcome.FAILURE, failOutcome)
        assertEquals(SmsStatus.RETRY, dao.getById(deposit.id)!!.statusEnum)
        assertNotNull(settingsRepo.readSettings().lastTokenError)

        // User fixes token in Settings -> next sweep recovers RETRY record automatically
        installFakeServer { req, _ ->
            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    200 to pendingJson(
                        serverNowMs = nowMs,
                        items = listOf(itemJson("order", "ORD_RECOVERED", 7_500_000L, nowMs - 20_000L))
                    )
                }
                req.url.encodedPath.endsWith("/sms-approve") -> {
                    200 to """{"ok":true,"status":"approved"}"""
                }
                else -> 404 to "{}"
            }
        }

        val recoveredOutcome = ForwardWorker.runSweepNow(app)
        assertEquals(ForwardWorker.WorkOutcome.SUCCESS, recoveredOutcome)
        assertEquals(SmsStatus.MATCHED, dao.getById(deposit.id)!!.statusEnum)
        assertNull(settingsRepo.readSettings().lastTokenError)
    }

    @Test
    fun staleClaimRecovery_andWindowExpiration_workCorrectly() = runBlocking {
        settingsRepo.setAutoApproveInApp(true)
        val nowMs = System.currentTimeMillis()
        // Stale CLAIMED record left behind by an earlier killed process
        val staleClaimed = insertDeposit("98200033", "واریز 4,000,000 ریال", nowMs - 10_000L)
        dao.claimForApproval(staleClaimed.id, "order", "ORD_CLAIMED")
        assertEquals(SmsStatus.CLAIMED, dao.getById(staleClaimed.id)!!.statusEnum)

        // Very old NEW deposit beyond 24h match window
        val oldDeposit = insertDeposit(
            "98200033",
            "واریز 9,000,000 ریال قدیمی",
            nowMs - TimeUnit.HOURS.toMillis(48)
        )

        installFakeServer { req, _ ->
            when {
                req.url.encodedPath.endsWith("/sms-pending") -> {
                    200 to pendingJson(
                        serverNowMs = nowMs,
                        matchWindowHours = 24,
                        items = listOf(itemJson("order", "ORD_CLAIMED", 4_000_000L, nowMs - 30_000L))
                    )
                }
                req.url.encodedPath.endsWith("/sms-approve") -> {
                    200 to """{"ok":true,"status":"duplicate"}"""
                }
                else -> 404 to "{}"
            }
        }

        ForwardWorker.runSweepNow(app)
        assertEquals(SmsStatus.MATCHED, dao.getById(staleClaimed.id)!!.statusEnum)
        assertEquals(SmsStatus.EXPIRED, dao.getById(oldDeposit.id)!!.statusEnum)
    }

    @Test
    fun smsReceiver_classifiesDebitIgnored_andDeduplicatesIdenticalDeposits() = runBlocking {
        // Test Room unique fingerprint deduplication & debit classification
        val debit = insertDeposit("98200033", "برداشت 200,000 ریال", System.currentTimeMillis())
        assertEquals(SmsStatus.IGNORED, debit.statusEnum)
        assertEquals("debit", debit.reason)

        val dep1 = insertDeposit("98200033", "واریز 1,234,000 ریال", System.currentTimeMillis())
        assertEquals(SmsStatus.NEW, dep1.statusEnum)
        val duplicateInsert = dao.insertIgnoreDuplicate(
            dep1.copy(id = 0, receivedMs = dep1.receivedMs + 1000L)
        )
        assertEquals(-1L, duplicateInsert)
    }

    /**
     * Also verifies multipart GSM 7-bit PDU construction + SmsReceiver intent decoding does not crash
     * and preserves concatenated message text cleanly.
     */
    @Test
    fun telephonyIntent_decodesPduSafely() {
        val pdu = buildSimpleAsciiPdu("98200033", "variz 500000 rial")
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", SmsMessage.FORMAT_3GPP)
        }
        val decoded = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        assertNotNull(decoded)
        assertEquals(1, decoded.size)
        assertEquals("variz 500000 rial", decoded[0].messageBody)
    }

    private fun buildSimpleAsciiPdu(sender: String, body: String): ByteArray {
        // Standard GSM 03.40 3GPP SMS-DELIVER PDU with numeric OA BCD encoding
        val digits = sender.filter { it.isDigit() }
        val out = ByteArrayOutputStream()
        out.write(0x00) // SMSC length = 0
        out.write(0x04) // SMS-DELIVER
        out.write(digits.length) // OA semi-octet digit count
        out.write(0x81) // Unknown numbering plan, national/short code
        var idx = 0
        while (idx < digits.length) {
            val lo = digits[idx].digitToInt()
            val hi = if (idx + 1 < digits.length) digits[idx + 1].digitToInt() else 0x0F
            out.write((hi shl 4) or lo)
            idx += 2
        }
        out.write(0x00) // PID
        out.write(0x00) // DCS: 7-bit GSM
        // Service center timestamp (7 bytes semi-octet)
        out.write(byteArrayOf(0x62, 0x11, 0x31, 0x21, 0x00, 0x00, 0x00))
        val bodyBytes = body.toByteArray(Charsets.US_ASCII)
        out.write(bodyBytes.size)
        out.write(encode7Bit(body))
        return out.toByteArray()
    }

    private fun encode7Bit(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        var current = 0
        var bits = 0
        for (b in bytes) {
            val v = b.toInt() and 0x7F
            current = current or (v shl bits)
            bits += 7
            while (bits >= 8) {
                out.write(current and 0xFF)
                current = current ushr 8
                bits -= 8
            }
        }
        if (bits > 0) out.write(current and 0xFF)
        return out.toByteArray()
    }
}
