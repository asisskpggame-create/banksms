package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.SettingsRepo
import com.example.ui.AsisViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @Test
    fun `instantiate AsisViewModel and verify Room and SettingsRepo`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = app.applicationContext
        val appName = context.getString(R.string.app_name)
        assertEquals("ASiS pannel", appName)

        val repo = SettingsRepo.getInstance(context)
        repo.saveEndpointAndToken(
            "\u200Fhttps://my-panel.example.com/secret-path\u200E",
            "\u200Bmy_super_secret_gateway_token_12345\u200C"
        )
        val saved = repo.readSettings()
        assertTrue(saved.isConfigured)
        assertTrue(saved.autoApproveInApp)
        assertEquals("https://my-panel.example.com/secret-path/sms-in", saved.smsInUrl)

        val db = AppDatabase.getInstance(context)
        assertNotNull(db.logDao())

        val vm = AsisViewModel.provideFactory(app).create(AsisViewModel::class.java)
        assertNotNull(vm.settingsState.value)

        // Verify Mirza 33-bank catalog and default pre-populated "بانک قرض الحسنه" (B.QMEHRIRAN) card
        assertEquals(33, com.example.ui.BankVisualCatalog.MIRZA_BANKS.size)
        val initialCards = repo.readSettings().registeredBankCards
        assertTrue(initialCards.any { it.senderNumber == "B.QMEHRIRAN" && it.bankTitle == "بانک قرض الحسنه" })

        // Verify adding a custom bank sender card updates SmsParser custom sender detection
        vm.addRegisteredBankCard("mellat", "بانک ملت", "98300017")
        val updatedCards = repo.readSettings().registeredBankCards
        assertTrue(updatedCards.any { it.senderNumber == "98300017" && it.bankCode == "mellat" })
        assertEquals("mellat", com.example.data.SmsParser.detectBank("98300017", "").code)

        // Verify loadReceivedSmsSenders returns bank shortcodes prioritized first
        val receivedSenders = vm.loadReceivedSmsSenders()
        assertTrue(receivedSenders.isNotEmpty())
        assertTrue(receivedSenders.contains("B.QMEHRIRAN"))
        assertTrue(receivedSenders.contains("+98200033"))
        val firstDetected = com.example.data.SmsParser.detectBank(receivedSenders.first(), "")
        assertNotNull(com.example.ui.BankVisualCatalog.findByCode(firstDetected.code))

        // Verify WorkManager OneTimeWorkRequest builders do not throw IllegalArgumentException
        ForwardWorker.enqueueMatcherSweep(context)
        ForwardWorker.enqueueManualApprove(context, recordId = 1L)
    }

    @Test
    fun `launch MainActivity and instantiate AsisViewModel via default ViewModelProvider`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertNotNull(activity)

        val vmViaSavedState = androidx.lifecycle.ViewModelProvider(activity)[AsisViewModel::class.java]
        assertNotNull(vmViaSavedState.settingsState.value)

        // Force Room openHelper + query execution to catch any schema/KSP/migration/SQL issues
        val db = AppDatabase.getInstance(activity.applicationContext)
        val writable = db.openHelper.writableDatabase
        assertTrue(writable.isOpen)
    }

    @Test
    fun `verify SMS permissions, SmsReceiver registration, and forwarding ingestion`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager

        val pkgInfo = pm.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val requestedPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must be declared in AndroidManifest.xml",
            requestedPerms.contains(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        )

        val smsIntent = android.content.Intent(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        smsIntent.setPackage(app.packageName)
        val receivers = pm.queryBroadcastReceivers(smsIntent, 0)
        assertTrue(
            "SmsReceiver must be registered for SMS_RECEIVED_ACTION",
            receivers.any { it.activityInfo.name == SmsReceiver::class.java.name }
        )

        kotlinx.coroutines.runBlocking {
            val db = AppDatabase.getInstance(app)
            db.logDao().clearAllLogs()

            // Personal mobile number must be ignored and never stored
            val personalResult = SmsReceiver.ingestIncomingSms(
                appContext = app,
                sender = "09123456789",
                rawBody = "واریز 5,000,000 ریال به حساب شما"
            )
            org.junit.Assert.assertFalse(personalResult)

            // Promotional / link SMS must be ignored and never stored in logs
            val promoBody = "جایزه ویژه واریز 10,000,000 ریال! روی لینک https://example.com کلیک کنید"
            val promoResult = SmsReceiver.ingestIncomingSms(
                appContext = app,
                sender = "98200033",
                rawBody = promoBody
            )
            org.junit.Assert.assertFalse(promoResult)
            val promoFp = com.example.data.SmsParser.fingerprint("98200033", promoBody)
            org.junit.Assert.assertNull(db.logDao().getByFingerprint(promoFp))

            // Valid bank deposit SMS must be stored as NEW and trigger ForwardWorker
            val bankBody = "بانک ملت\nواریز به حساب 0123456789\nمبلغ: 50,000,000ریال\nمانده: 90,000,000ریال"
            val bankResult = SmsReceiver.ingestIncomingSms(
                appContext = app,
                sender = "98200033",
                rawBody = bankBody
            )
            assertTrue(bankResult)

            val fp = com.example.data.SmsParser.fingerprint("98200033", bankBody)
            val saved = db.logDao().getByFingerprint(fp)
            assertNotNull(saved)
            assertEquals("NEW", saved?.status)
            assertEquals(50_000_000L, saved?.amountMinor)
            assertEquals("بانک ملت", saved?.bankTitle)

            // Verify failure reason logging (e.g., no connection) and manual retry reset
            val offlineReason = com.example.network.GatewayApiClient.describeNetworkException(
                java.net.UnknownHostException("Unable to resolve host")
            )
            assertTrue(offlineReason.contains("عدم اتصال به اینترنت"))

            val recordId = saved!!.id
            db.logDao().update(
                saved.copy(
                    status = com.example.data.SmsStatus.FAILED.name,
                    attempts = 1,
                    httpCode = 0,
                    lastError = offlineReason
                )
            )
            val failedRec = db.logDao().getById(recordId)
            assertEquals(com.example.data.SmsStatus.FAILED.name, failedRec?.status)
            assertEquals(offlineReason, failedRec?.lastError)

            val resetCount = db.logDao().resetFailedAndErrorLogsForRetry()
            assertEquals(1, resetCount)
            val retriedRec = db.logDao().getById(recordId)
            assertEquals(com.example.data.SmsStatus.NEW.name, retriedRec?.status)
            org.junit.Assert.assertNull(retriedRec?.lastError)

            // Verify SmartAmountMatcher:
            // 1. Insert a waiting invoice in DB (ORD-701 for 2,500,000 Toman = 25,000,000 Rial)
            val nowMs = System.currentTimeMillis()
            db.logDao().upsertInvoice(
                com.example.data.PendingInvoiceEntity(
                    publicId = "ORD-701",
                    kind = "order",
                    dueMinor = 25_000_000L,
                    createdMs = nowMs - 10_000L,
                    status = "WAITING"
                )
            )

            // 2. Incoming unrelated deposit (80,000,000 Rial) -> marked as UNKNOWN_DEPOSIT («واریز ناشناس»)
            val unknownBody = "بانک ملت\nواریز به حساب\nمبلغ: 80,000,000ریال\nمانده: 170,000,000ریال"
            SmsReceiver.ingestIncomingSms(
                appContext = app,
                sender = "98200033",
                rawBody = unknownBody,
                receivedAtMs = nowMs
            )
            val unknownFp = com.example.data.SmsParser.fingerprint("98200033", unknownBody)
            val unknownSaved = db.logDao().getByFingerprint(unknownFp)
            assertNotNull(unknownSaved)
            assertEquals(com.example.data.SmsStatus.UNKNOWN_DEPOSIT.name, unknownSaved?.status)

            // 3. Incoming 10x discrepancy deposit (2,500,000 Rial instead of 25,000,000 Rial) -> marked as MANUAL («نیاز به بررسی دستی»)
            val manualBody = "بانک ملت\nواریز به حساب\nمبلغ: 2,500,000ریال\nمانده: 172,500,000ریال"
            SmsReceiver.ingestIncomingSms(
                appContext = app,
                sender = "98200033",
                rawBody = manualBody,
                receivedAtMs = nowMs
            )
            val manualFp = com.example.data.SmsParser.fingerprint("98200033", manualBody)
            val manualSaved = db.logDao().getByFingerprint(manualFp)
            assertNotNull(manualSaved)
            assertEquals(com.example.data.SmsStatus.MANUAL.name, manualSaved?.status)
            assertEquals("ORD-701", manualSaved?.matchedId)

            // 4. Server sends invoice inquiry for 80,000,000 Rial (ORD-702) within 1 minute -> matches the UNKNOWN_DEPOSIT in logs!
            val serverInquiryInvoice = com.example.data.PendingInvoiceEntity(
                publicId = "ORD-702",
                kind = "order",
                dueMinor = 80_000_000L,
                createdMs = nowMs + 20_000L,
                status = "WAITING"
            )
            val activeLogs = db.logDao().getActiveDepositsForMatching()
            val logCheck = com.example.data.SmartAmountMatcher.verifyServerInvoiceInLogs(
                invoice = serverInquiryInvoice,
                activeDeposits = activeLogs
            )
            assertTrue(logCheck is com.example.data.InvoiceLogCheckResult.MatchedInLog)
            assertEquals(
                unknownSaved?.id,
                (logCheck as com.example.data.InvoiceLogCheckResult.MatchedInLog).depositRecord.id
            )
        }
    }

    @Test
    fun `verify real-time Green Yellow Red server connection status computation`() {
        val configuredSettings = com.example.data.GatewaySettings(
            endpointUrl = "https://panel.example.com/sms-in",
            gatewayToken = "very_secure_token_1234567890",
            serviceEnabled = true
        )

        // 1. Offline device -> RED
        val offlineStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = false,
            settings = configuredSettings,
            telemetry = com.example.network.ServerConnectionTelemetry()
        )
        assertEquals(com.example.network.ServerConnectionLevel.RED, offlineStatus.level)

        // 2. Unconfigured server -> YELLOW
        val unconfiguredStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = true,
            settings = com.example.data.GatewaySettings(),
            telemetry = com.example.network.ServerConnectionTelemetry()
        )
        assertEquals(com.example.network.ServerConnectionLevel.YELLOW, unconfiguredStatus.level)

        // 3. Online + checking / slow latency (>2000ms) -> YELLOW
        val checkingStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = true,
            settings = configuredSettings,
            telemetry = com.example.network.ServerConnectionTelemetry(isChecking = true, lastPingOk = null)
        )
        assertEquals(com.example.network.ServerConnectionLevel.YELLOW, checkingStatus.level)

        val slowStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = true,
            settings = configuredSettings,
            telemetry = com.example.network.ServerConnectionTelemetry(lastPingOk = true, httpCode = 200, latencyMs = 2500L)
        )
        assertEquals(com.example.network.ServerConnectionLevel.YELLOW, slowStatus.level)

        // 4. Online + reachable server with fast latency -> GREEN
        val connectedStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = true,
            settings = configuredSettings,
            telemetry = com.example.network.ServerConnectionTelemetry(lastPingOk = true, httpCode = 200, latencyMs = 140L)
        )
        assertEquals(com.example.network.ServerConnectionLevel.GREEN, connectedStatus.level)
        assertEquals(140L, connectedStatus.latencyMs)

        // 5. Online device but server ping failed -> RED
        val serverDownStatus = AsisViewModel.computeServerConnectionStatus(
            isOnline = true,
            settings = configuredSettings,
            telemetry = com.example.network.ServerConnectionTelemetry(lastPingOk = false, httpCode = 502, lastErrorMessage = "Bad Gateway")
        )
        assertEquals(com.example.network.ServerConnectionLevel.RED, serverDownStatus.level)
    }

    @Test
    fun `verify notification settings persistence, sensitive SMS classification, and push notification dispatch`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = app.applicationContext

        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        com.example.util.NotificationHelper.resetServerErrorThrottleForTests()

        val repo = SettingsRepo.getInstance(context)
        repo.setNotifyOnServerConnectionError(true)
        repo.setNotifyOnSensitiveBankSms(true)
        repo.setNotifyOnAmbiguousOrManual(true)
        repo.setSensitiveSmsMinToman(5_000_000L)

        val settings = repo.readSettings()
        assertTrue(settings.notifyOnServerConnectionError)
        assertTrue(settings.notifyOnSensitiveBankSms)
        assertTrue(settings.notifyOnAmbiguousOrManual)
        assertEquals(5_000_000L, settings.sensitiveSmsMinToman)

        // 1. High-value deposit above 5,000,000 Toman threshold -> HIGH_VALUE_DEPOSIT
        val highDepositSms = "بانک ملت\nواریز به حساب 0123456\nمبلغ: 80,000,000ریال\nمانده: 120,000,000ریال"
        val highParsed = com.example.data.SmsParser.parse(highDepositSms)
        val highDetection = com.example.data.SmsParser.classifySensitiveBankSms(
            rawText = highDepositSms,
            parsed = highParsed,
            status = com.example.data.SmsStatus.NEW,
            minTomanThreshold = 5_000_000L
        )
        assertNotNull(highDetection)
        assertEquals(com.example.data.SensitiveSmsReason.HIGH_VALUE_DEPOSIT, highDetection?.reason)

        // 2. Small deposit below 5,000,000 Toman threshold -> null (no sensitive alert)
        val smallDepositSms = "بانک ملت\nواریز به حساب\nمبلغ: 2,000,000ریال\nمانده: 122,000,000ریال"
        val smallParsed = com.example.data.SmsParser.parse(smallDepositSms)
        val smallDetection = com.example.data.SmsParser.classifySensitiveBankSms(
            rawText = smallDepositSms,
            parsed = smallParsed,
            status = com.example.data.SmsStatus.NEW,
            minTomanThreshold = 5_000_000L
        )
        org.junit.Assert.assertNull(smallDetection)

        // 3. Debit SMS -> DEBIT_OR_WITHDRAWAL
        val debitSms = "بانک سامان\nبرداشت از حساب: 15,000,000 ریال\nمانده: 10,000,000 ریال"
        val debitParsed = com.example.data.SmsParser.parse(debitSms)
        val debitDetection = com.example.data.SmsParser.classifySensitiveBankSms(
            rawText = debitSms,
            parsed = debitParsed,
            status = com.example.data.SmsStatus.IGNORED,
            minTomanThreshold = 5_000_000L
        )
        assertNotNull(debitDetection)
        assertEquals(com.example.data.SensitiveSmsReason.DEBIT_OR_WITHDRAWAL, debitDetection?.reason)
        assertEquals(15_000_000L, debitDetection?.amountMinor)

        // 4. Security / OTP SMS -> SECURITY_OR_OTP
        val otpSms = "بانک ملی: رمز پویا 482910 محرمانه است"
        val otpParsed = com.example.data.SmsParser.parse(otpSms)
        val otpDetection = com.example.data.SmsParser.classifySensitiveBankSms(
            rawText = otpSms,
            parsed = otpParsed,
            status = com.example.data.SmsStatus.IGNORED,
            minTomanThreshold = 5_000_000L
        )
        assertNotNull(otpDetection)
        assertEquals(com.example.data.SensitiveSmsReason.SECURITY_OR_OTP, otpDetection?.reason)

        // 5. Verify NotificationHelper posts and cancels push notifications respecting preferences
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        nm.cancelAll()

        val errPosted = com.example.util.NotificationHelper.notifyServerConnectionError(
            context = context,
            errorTitle = "خطای ارتباط با سرور درگاه",
            errorDetail = "سرور پاسخ نمی‌دهد",
            httpCode = 503
        )
        assertTrue(errPosted)
        assertTrue(shadowNm.allNotifications.isNotEmpty())

        val smsPosted = com.example.util.NotificationHelper.notifySensitiveBankSms(
            context = context,
            smsId = 42L,
            bankTitle = "بانک ملت",
            categoryTitleFa = highDetection!!.categoryTitleFa,
            amountMinor = highDetection.amountMinor,
            smsBody = highDepositSms
        )
        assertTrue(smsPosted)
        assertEquals(2, shadowNm.allNotifications.size)

        // Disabling settings suppresses new alerts
        repo.setNotifyOnServerConnectionError(false)
        repo.setNotifyOnSensitiveBankSms(false)
        com.example.util.NotificationHelper.resetServerErrorThrottleForTests()
        org.junit.Assert.assertFalse(
            com.example.util.NotificationHelper.notifyServerConnectionError(
                context = context,
                errorTitle = "خطای سرور",
                errorDetail = "Timeout",
                httpCode = 504
            )
        )
        org.junit.Assert.assertFalse(
            com.example.util.NotificationHelper.notifySensitiveBankSms(
                context = context,
                smsId = 43L,
                bankTitle = "بانک ملت",
                categoryTitleFa = "تست",
                amountMinor = 50_000_000L,
                smsBody = highDepositSms
            )
        )
    }
}
