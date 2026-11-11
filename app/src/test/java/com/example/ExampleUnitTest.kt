package com.example

import com.example.data.SmsParser
import com.example.network.GatewayApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseRialDeposit_extractsExactRialAmountAndIgnoresBalance() {
        val sms = """
            بانک ملت
            واریز به حساب 0123456789
            مبلغ: ۲,۵۰۰,۰۰۰ ریال
            مانده: ۱۸,۴۰۰,۰۰۰ ریال
            1405/08/20-15:30
        """.trimIndent()

        val parsed = SmsParser.parse(sms, sender = "98200033")
        assertEquals("mellat", parsed.bankCode)
        assertEquals(2_500_000L, parsed.amountMinor)
        assertEquals("", parsed.reason)
    }

    @Test
    fun parseTomanDeposit_multipliesByTenToRials() {
        val sms = """
            بانک سامان
            واریز به حساب
            مبلغ 485,000 تومان
            موجودی: 9,200,000 تومان
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertEquals("saman", parsed.bankCode)
        assertEquals(4_850_000L, parsed.amountMinor)
        assertEquals("", parsed.reason)
    }

    @Test
    fun parseDebitSms_returnsNullAmountAndDebitReason() {
        val sms = """
            بانک ملی
            برداشت از حساب
            مبلغ: 500,000 ریال
            مانده: 1,200,000 ریال
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNull(parsed.amountMinor)
        assertEquals("debit", parsed.reason)
    }

    @Test
    fun parseTestConnectionPayload_recognizesTestMark() {
        val parsed = SmsParser.parse("تست اتصال")
        assertNull(parsed.amountMinor)
        assertEquals("test", parsed.reason)
    }

    @Test
    fun gatewayValidation_enforcesHttpsAndMin20CharToken() {
        val httpErr = GatewayApiClient.validateInputConfig(
            "http://example.com/panel/sms-in",
            "12345678901234567890"
        )
        assertNotNull(httpErr)

        val shortTokenErr = GatewayApiClient.validateInputConfig(
            "https://example.com/panel/sms-in",
            "short_token"
        )
        assertNotNull(shortTokenErr)

        val valid = GatewayApiClient.validateInputConfig(
            "https://example.com/panel/sms-in",
            "valid_secret_token_at_least_20_chars"
        )
        assertNull(valid)
        assertEquals(
            "https://example.com/panel/sms-in",
            GatewayApiClient.normalizeUrl("https://example.com/panel/")
        )
    }

    @Test
    fun fingerprint_isDeterministicAcrossPersianAndLatinDigits() {
        val fp1 = SmsParser.fingerprint("mellat", "واریز ۱۰۰,۰۰۰ ریال")
        val fp2 = SmsParser.fingerprint("MELLAT", "واریز 100,000 ریال")
        assertEquals(fp1, fp2)
        assertTrue(fp1.length == 64)
    }
}
