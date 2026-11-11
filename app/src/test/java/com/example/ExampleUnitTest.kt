package com.example

import com.example.data.Deposit
import com.example.data.Item
import com.example.data.Matcher
import com.example.data.Parsed
import com.example.data.SmsParser
import com.example.network.GatewayApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    /**
     * Section 3 & 9 Requirement:
     * Parity test running 32 diverse bank SMS samples through `SmsParser.kt` and verifying
     * 100% identical `(amountMinor, reason)` output with Python's `reference/sms_parse.py`.
     */
    @Test
    fun parityWithPythonSmsParse_32Samples() {
        val cases: List<Pair<String, Parsed>> = listOf(
            "بانک ملت\nواریز به حساب 0123456789\nمبلغ: 2,500,000 ریال\nمانده: 18,400,000 ریال\n1405/08/20-15:30" to Parsed(2_500_000L, ""),
            "بانک سامان\nواریز به حساب\nمبلغ 485,000 تومان\nموجودی: 9,200,000 تومان" to Parsed(4_850_000L, ""),
            "بانک ملی\nبرداشت از حساب\nمبلغ: 500,000 ریال\nمانده: 1,200,000 ریال" to Parsed(null, "debit"),
            "واریز به حساب شما مبلغ ۱٬۲۵۰٬۰۰۰ ریال مرجع ۱۲۳۴۵۶ تاریخ ۱۴۰۵/۰۸/۲۰ ساعت ۱۴:۳۵" to Parsed(null, "several_amounts"),
            "حساب 010203040506 واریز 750,000 تومان کارمزد: 1,200 تومان مانده: 5,000,000 تومان" to Parsed(7_500_000L, ""),
            "کارت 603799******1234 واریز 3,000,000 ریال 14:20" to Parsed(3_000_000L, ""),
            "کارت 6037-9975-1234-5678 خرید 250,000 ریال" to Parsed(null, "debit"),
            "انتقال از حساب شما مبلغ 900,000 ریال پایا" to Parsed(null, "debit"),
            "واريز به سپرده شما +4,500,000 ريال موجودي 10,000,000 ريال" to Parsed(null, "several_amounts"),
            "حساب شما -1,200,000 ریال مانده: 4,000,000 ریال" to Parsed(null, "debit"),
            "بستانکار: 650,000 تومان\nباقیمانده: 2,100,000 تومان" to Parsed(6_500_000L, ""),
            "بدهکار: 650,000 تومان\nباقیمانده: 2,100,000 تومان" to Parsed(null, "debit"),
            "دریافت وجه 12500000 ریال از شبا" to Parsed(12_500_000L, ""),
            "ایداع مبلغ ٨٥٠,٠٠٠ ریال در حساب ***1234" to Parsed(850_000L, ""),
            "افزایش موجودی 99,000 تومان همراه09123456789" to Parsed(null, "no_credit_amount"),
            "واریز 100,000 ریال و واریز 200,000 ریال در یک پیامک" to Parsed(null, "several_amounts"),
            "واریز و برداشت همزمان 500,000 ریال بدون علامت" to Parsed(null, "mixed_direction"),
            "واریز و برداشت همزمان +500,000 ریال با علامت مثبت" to Parsed(500_000L, ""),
            "واریز و برداشت همزمان -500,000 ریال با علامت منفی" to Parsed(null, "debit"),
            "فقط متن بدون هیچ عددی واریز شد" to Parsed(null, "no_credit_amount"),
            "   \n  \u200c  " to Parsed(null, "empty"),
            "deposit 1,500,000 rial bal: 9,000,000" to Parsed(1_500_000L, ""),
            "withdraw 1,500,000 rial bal: 9,000,000" to Parsed(null, "debit"),
            "credit: 420,000 fee: 5,000 balance: 1,000,000" to Parsed(420_000L, ""),
            "debit: 420,000 fee: 5,000 balance: 1,000,000" to Parsed(null, "debit"),
            "variz 88,000 toman" to Parsed(88_000L, ""),
            "واریز 100,000 تومان معادل 1,000,000 ریال" to Parsed(null, "several_amounts"),
            "پرداخت قبض 120,000 ریال" to Parsed(null, "debit"),
            "ساتنا واریز 50,000,000 ریال" to Parsed(null, "mixed_direction"),
            "پایا +25,000,000 ریال مانده: 90,000,000" to Parsed(25_000_000L, ""),
            "کسر از حساب 40,000 ریال" to Parsed(null, "debit"),
            "واریز به حساب 09988776655 مبلغ 3,333,330 ریال 2026-11-10 09:15:44" to Parsed(3_333_330L, "")
        )

        assertEquals(32, cases.size)
        for ((index, pair) in cases.withIndex()) {
            val (rawSms, expected) = pair
            val actual = SmsParser.parse(rawSms)
            assertEquals("Mismatch on sample #$index: $rawSms", expected, actual)
        }
    }

    @Test
    fun matcher_oneDepositOneMatchingOrder_pairsCorrectly() {
        val dep = Deposit(id = 10L, amountMinor = 5_000_070L, at = 2000L)
        val ord = Item(kind = "order", publicId = "ORD123", dueMinor = 5_000_070L, at = 1500L)

        val (pairs, ambiguous) = Matcher.plan(listOf(dep), listOf(ord))
        assertEquals(1, pairs.size)
        assertEquals(dep, pairs[0].first)
        assertEquals(ord, pairs[0].second)
        assertTrue(ambiguous.isEmpty())
    }

    @Test
    fun matcher_oneDepositTwoOrdersWithSameAmount_marksAmbiguousAndApprovesNothing() {
        val dep = Deposit(id = 11L, amountMinor = 5_000_000L, at = 3000L)
        val ord1 = Item(kind = "order", publicId = "ORD1", dueMinor = 5_000_000L, at = 1000L)
        val ord2 = Item(kind = "order", publicId = "ORD2", dueMinor = 5_000_000L, at = 2000L)

        val (pairs, ambiguous) = Matcher.plan(listOf(dep), listOf(ord1, ord2))
        assertTrue(pairs.isEmpty())
        assertEquals(1, ambiguous.size)
        assertEquals(dep, ambiguous[0])
    }

    @Test
    fun matcher_depositOlderThanOrder_doesNotMatch() {
        val oldDeposit = Deposit(id = 12L, amountMinor = 5_000_070L, at = 1000L)
        val newerOrder = Item(kind = "order", publicId = "ORD999", dueMinor = 5_000_070L, at = 2000L)

        val (pairs, ambiguous) = Matcher.plan(listOf(oldDeposit), listOf(newerOrder))
        assertTrue(pairs.isEmpty())
        assertTrue(ambiguous.isEmpty())
    }

    @Test
    fun fingerprint_isDeterministicAcrossPersianArabicAndLatinDigits() {
        val fpPersian = SmsParser.fingerprint("98200033", "واریز ۱٬۲۰۰٬۰۰۰ ریال")
        val fpArabic = SmsParser.fingerprint("98200033", "واریز ١،٢٠٠،٠٠٠ ریال")
        val fpLatin = SmsParser.fingerprint("98200033", "واریز 1,200,000 ریال")
        assertEquals(fpPersian, fpLatin)
        assertEquals(fpArabic, fpLatin)
        assertEquals(64, fpLatin.length)
    }

    @Test
    fun hmacSignature_isDeterministicAndValidHex() {
        val sig1 = GatewayApiClient.computeApprovalHmac(
            token = "my_secret_gateway_token_123456",
            kind = "order",
            publicId = "ORD123",
            fingerprint = "abcd1234",
            amountMinor = 5_000_070L,
            ts = 1780000001000L
        )
        val sig2 = GatewayApiClient.computeApprovalHmac(
            token = "my_secret_gateway_token_123456",
            kind = "order",
            publicId = "ORD123",
            fingerprint = "abcd1234",
            amountMinor = 5_000_070L,
            ts = 1780000001000L
        )
        assertEquals(sig1, sig2)
        assertEquals(64, sig1.length)
    }

    @Test
    fun gatewayValidation_enforcesHttpsAndMin20CharToken() {
        assertNotNull(
            GatewayApiClient.validateInputConfig(
                "http://example.com/panel",
                "12345678901234567890"
            )
        )
        assertNotNull(
            GatewayApiClient.validateInputConfig(
                "https://example.com/panel",
                "short_token"
            )
        )
        assertNull(
            GatewayApiClient.validateInputConfig(
                "https://example.com/panel",
                "valid_secret_token_at_least_20_chars"
            )
        )
        assertEquals(
            "https://example.com/panel/sms-pending",
            GatewayApiClient.buildPendingUrl("https://example.com/panel/sms-in")
        )
        assertEquals(
            "https://example.com/panel/sms-approve",
            GatewayApiClient.buildApproveUrl("https://example.com/panel/sms-in/")
        )
    }
}
