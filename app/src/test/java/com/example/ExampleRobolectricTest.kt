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

        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(
                store,
                AsisViewModel.provideFactory(app)
            )[AsisViewModel::class.java]
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
        } finally {
            store.clear()
        }
    }

    @Test
    fun `launch MainActivity and instantiate AsisViewModel via default ViewModelProvider`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertNotNull(activity)

            val vmViaSavedState = androidx.lifecycle.ViewModelProvider(activity)[AsisViewModel::class.java]
            assertNotNull(vmViaSavedState.settingsState.value)

            // Force Room openHelper + query execution to catch any schema/KSP/migration/SQL issues
            val db = AppDatabase.getInstance(activity.applicationContext)
            val writable = db.openHelper.writableDatabase
            assertTrue(writable.isOpen)
        } finally {
            controller.destroy()
        }
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
            assertEquals(com.example.data.SmsStatus.RETRY.name, retriedRec?.status)
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

    @Test
    fun `verify automated batch retry backoff policy, countdown formatting, and selective DB batch retry`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val dao = db.logDao()
        com.example.network.BatchRetryTracker.resetForTests()

        // 1. Exponential backoff checks
        assertEquals(15_000L, com.example.network.BatchRetryPolicy.backoffMsForAttempt(1))
        assertEquals(30_000L, com.example.network.BatchRetryPolicy.backoffMsForAttempt(2))
        assertEquals(60_000L, com.example.network.BatchRetryPolicy.backoffMsForAttempt(3))
        assertEquals(120_000L, com.example.network.BatchRetryPolicy.backoffMsForAttempt(4))
        assertEquals(300_000L, com.example.network.BatchRetryPolicy.backoffMsForAttempt(6))
        org.junit.Assert.assertNull(com.example.network.BatchRetryPolicy.computeNextRetryAtMs(8))
        assertEquals("01:05", com.example.network.BatchRetryPolicy.formatCountdownMmSs(65_000L))
        assertEquals("00:15", com.example.network.BatchRetryPolicy.formatCountdownMmSs(14_200L))

        kotlinx.coroutines.runBlocking {
            dao.clearAllLogs()
            val nowMs = System.currentTimeMillis()
            val id1 = dao.insertIgnoreDuplicate(
                com.example.data.SmsRecord(
                    fingerprint = "fp_batch_1",
                    bank = "98200033",
                    bankTitle = "بانک ملت",
                    body = "واریز 10,000,000 ریال",
                    normalized = "واریز 10,000,000 ریال",
                    amountMinor = 10_000_000L,
                    reason = "",
                    status = com.example.data.SmsStatus.FAILED.name,
                    attempts = 1,
                    httpCode = 503,
                    lastError = "Timeout",
                    nextRetryAtMs = null
                )
            )
            val id2 = dao.insertIgnoreDuplicate(
                com.example.data.SmsRecord(
                    fingerprint = "fp_batch_2",
                    bank = "98200070",
                    bankTitle = "بانک تجارت",
                    body = "واریز 20,000,000 ریال",
                    normalized = "واریز 20,000,000 ریال",
                    amountMinor = 20_000_000L,
                    reason = "",
                    status = com.example.data.SmsStatus.RETRY.name,
                    attempts = 2,
                    httpCode = 502,
                    lastError = "Bad Gateway",
                    nextRetryAtMs = nowMs + 45_000L
                )
            )

            val pending = dao.getRetryPendingRecords()
            assertEquals(2, pending.size)
            assertEquals(nowMs + 45_000L, com.example.network.BatchRetryPolicy.earliestAutoRetryAtMs(pending))

            // Manually trigger selective retry only on id1
            val updatedRows = dao.markIdsForImmediateBatchRetry(listOf(id1), nowMs)
            assertEquals(1, updatedRows)
            val rec1 = dao.getById(id1)
            val rec2 = dao.getById(id2)
            assertEquals(com.example.data.SmsStatus.RETRY.name, rec1?.status)
            org.junit.Assert.assertNull(rec1?.lastError)
            assertEquals(nowMs + 45_000L, rec2?.nextRetryAtMs)

            // Execute batch retry worker when server is unconfigured -> reschedules backoff & updates tracker
            SettingsRepo.getInstance(app).saveEndpointAndToken("", "")
            val outcome = ForwardWorker.runBatchRetryNow(app, listOf(id1))
            assertEquals(ForwardWorker.WorkOutcome.RETRY, outcome)
            val trackerState = com.example.network.BatchRetryTracker.stateFlow.value
            org.junit.Assert.assertFalse(trackerState.isBatchRunning)
            assertEquals(1, trackerState.lastBatchProcessedCount)
            assertEquals(1, trackerState.lastBatchFailedCount)
            assertNotNull(trackerState.nextScheduledWorkAtMs)
        }
    }

    @Test
    fun `verify 7-day auto-retry health analytics, Room telemetry persistence, and trend delta`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        val dao = db.logDao()

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowNoonMs = cal.timeInMillis
        val dayMs = 86_400_000L

        // Empty state -> NO_DATA with 7 chronological days
        val emptySummary = com.example.network.RetryHealthAnalytics.compute7DaySummary(
            logs = emptyList(),
            events = emptyList(),
            nowMs = nowNoonMs
        )
        assertEquals(7, emptySummary.days.size)
        assertEquals(0, emptySummary.totalAttempts7d)
        assertEquals(com.example.network.ConnectivityHealthTier.NO_DATA, emptySummary.healthTier)
        org.junit.Assert.assertNull(emptySummary.overallSuccessRatePercent)

        kotlinx.coroutines.runBlocking {
            dao.clearAllLogs()
            dao.clearAllRetryEvents()

            // Insert explicit retry telemetry events into Room across the last 7 days:
            // Day -5 (early window): 1 success, 1 failure (50%)
            // Day -1 (yesterday, recent window): 3 successes, 0 failures (100%)
            // Day 0 (today, recent window): 4 successes, 0 failures (100%)
            dao.insertRetryAttemptEvents(
                listOf(
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 101L,
                        attemptedAtMs = nowNoonMs - 5L * dayMs,
                        succeeded = false,
                        httpCode = 502,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 101L,
                        attemptedAtMs = nowNoonMs - 5L * dayMs + 60_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 102L,
                        attemptedAtMs = nowNoonMs - 1L * dayMs,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 103L,
                        attemptedAtMs = nowNoonMs - 1L * dayMs + 30_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 104L,
                        attemptedAtMs = nowNoonMs - 1L * dayMs + 60_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 105L,
                        attemptedAtMs = nowNoonMs,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 106L,
                        attemptedAtMs = nowNoonMs + 10_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 107L,
                        attemptedAtMs = nowNoonMs + 20_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 108L,
                        attemptedAtMs = nowNoonMs + 30_000L,
                        succeeded = true,
                        httpCode = 200,
                        isAutoRetry = true
                    ),
                    // Out-of-window (10 days ago) should be ignored by sinceMs query & 7-day aggregator
                    com.example.data.RetryAttemptEventEntity(
                        smsRecordId = 99L,
                        attemptedAtMs = nowNoonMs - 10L * dayMs,
                        succeeded = false,
                        httpCode = 500,
                        isAutoRetry = true
                    )
                )
            )

            // Include a legacy retried SmsRecord without explicit events + one WITH explicit event (id = 101L)
            val legacyRecords = listOf(
                com.example.data.SmsRecord(
                    id = 101L, // already represented in events -> must not double-count
                    fingerprint = "fp_event_covered",
                    bank = "98200033",
                    bankTitle = "بانک ملت",
                    body = "واریز 1,000,000 ریال",
                    normalized = "واریز 1,000,000 ریال",
                    amountMinor = 1_000_000L,
                    reason = "",
                    status = com.example.data.SmsStatus.MATCHED.name,
                    attempts = 2,
                    decidedMs = nowNoonMs
                ),
                com.example.data.SmsRecord(
                    id = 202L, // legacy record -> contributes 1 failure today
                    fingerprint = "fp_legacy_failed",
                    bank = "98200070",
                    bankTitle = "بانک تجارت",
                    body = "واریز 5,000,000 ریال",
                    normalized = "واریز 5,000,000 ریال",
                    amountMinor = 5_000_000L,
                    reason = "",
                    status = com.example.data.SmsStatus.FAILED.name,
                    attempts = 1,
                    decidedMs = nowNoonMs
                )
            )

            val recentEvents = dao.getRecentRetryEvents(nowNoonMs - 7L * dayMs)
            assertEquals(9, recentEvents.size)

            val summary = com.example.network.RetryHealthAnalytics.compute7DaySummary(
                logs = legacyRecords,
                events = recentEvents,
                nowMs = nowNoonMs
            )

            // Total 7d = 9 explicit events + 1 legacy failure = 10; 8 succeeded, 2 failed => 80% (GOOD)
            assertEquals(10, summary.totalAttempts7d)
            assertEquals(8, summary.succeededAttempts7d)
            assertEquals(2, summary.failedAttempts7d)
            assertEquals(80f, summary.overallSuccessRatePercent ?: -1f, 0.01f)
            assertEquals(com.example.network.ConnectivityHealthTier.GOOD, summary.healthTier)
            // Trend delta: recent 3 days = 7/8 = 87.5% minus early 4 days = 1/2 = 50% -> +37.5%
            assertEquals(37.5f, summary.trendDeltaPercent ?: -1f, 0.01f)

            // Verify day buckets alignment
            assertEquals(50f, summary.days[1].successRatePercent ?: -1f, 0.01f) // day -5
            assertEquals(100f, summary.days[5].successRatePercent ?: -1f, 0.01f) // yesterday
            assertEquals("امروز", summary.days[6].dayLabel)
            assertEquals(80f, summary.days[6].successRatePercent ?: -1f, 0.01f) // today (4/5)

            // Verify prune & clear
            val pruned = dao.deleteOldRetryEvents(nowNoonMs - 7L * dayMs)
            assertEquals(1, pruned)
            dao.clearAllRetryEvents()
            assertEquals(0, dao.getRecentRetryEvents(0L).size)
        }
    }

    @Test
    fun `verify Dark Navy and Midnight Black OLED theme mode persistence and toggling`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = SettingsRepo.getInstance(app)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(
                store,
                AsisViewModel.provideFactory(app)
            )[AsisViewModel::class.java]

            // Default starts cleanly in DARK_NAVY
            repo.setThemeMode(com.example.data.AppThemeMode.DARK_NAVY)
            assertEquals(com.example.data.AppThemeMode.DARK_NAVY, repo.readSettings().themeMode)
            org.junit.Assert.assertFalse(repo.readSettings().isMidnightOled)

            // Toggle to MIDNIGHT_OLED via ViewModel
            vm.setThemeMode(com.example.data.AppThemeMode.MIDNIGHT_OLED)
            assertEquals(com.example.data.AppThemeMode.MIDNIGHT_OLED, vm.settingsState.value.themeMode)
            assertTrue(vm.settingsState.value.isMidnightOled)
            assertEquals(com.example.data.AppThemeMode.MIDNIGHT_OLED, repo.readSettings().themeMode)

            // Toggle back via boolean helper
            vm.setMidnightOledEnabled(false)
            assertEquals(com.example.data.AppThemeMode.DARK_NAVY, vm.settingsState.value.themeMode)
            org.junit.Assert.assertFalse(repo.readSettings().isMidnightOled)
        } finally {
            store.clear()
        }
    }

    @Test
    fun `verify custom SMS filtering rules CRUD, normalization matching, and SmsReceiver enforcement`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = SettingsRepo.getInstance(app)
        val db = AppDatabase.getInstance(app)
        val dao = db.logDao()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(
                store,
                AsisViewModel.provideFactory(app)
            )[AsisViewModel::class.java]

            repo.setServiceEnabled(true)
            repo.setFilterBanksEnabled(false)
            repo.setCustomFilterRulesEnabled(true)

            // Clear any pre-existing custom rules
            repo.readSettings().customFilterRules.forEach { repo.removeCustomFilterRule(it.id) }

            // 1. Add rules: keyword ("سود"), sender ("98500099"), min amount ("1000" Toman)
            vm.addCustomFilterRule(
                name = "حذف سود بانکی",
                pattern = "سود",
                target = com.example.data.FilterTarget.BODY_KEYWORD,
                action = com.example.data.FilterAction.IGNORE_LOG_ONLY
            )
            vm.addCustomFilterRule(
                name = "بلاک سرشماره تبلیغاتی",
                pattern = "98500099",
                target = com.example.data.FilterTarget.SENDER_NUMBER,
                action = com.example.data.FilterAction.DROP_SILENTLY
            )
            vm.addCustomFilterRule(
                name = "مبالغ خرد زیر ۱۰۰۰ تومان",
                pattern = "1000",
                target = com.example.data.FilterTarget.MIN_AMOUNT_TOMAN,
                action = com.example.data.FilterAction.IGNORE_LOG_ONLY
            )

            val rulesAfterAdd = repo.readSettings().customFilterRules
            assertEquals(3, rulesAfterAdd.size)

            val keywordRule = rulesAfterAdd.first { it.target == com.example.data.FilterTarget.BODY_KEYWORD }
            val senderRule = rulesAfterAdd.first { it.target == com.example.data.FilterTarget.SENDER_NUMBER }

            // Verify Arabic/Persian normalization in SmsFilterEngine
            val arabicMatch = com.example.data.SmsFilterEngine.evaluate(
                sender = "98200033",
                rawBody = "بانك ملت واريز سود سپرده 50,000 ريال",
                amountMinor = null,
                settings = repo.readSettings()
            )
            assertNotNull(arabicMatch)
            assertEquals(keywordRule.id, arabicMatch?.rule?.id)

            kotlinx.coroutines.runBlocking {
                dao.clearAllLogs()
                dao.clearAllInvoices()
                val initialBlocked = repo.readSettings().blockedByCustomFiltersCount

                // 2. IGNORE_LOG_ONLY keyword rule blocks server dispatch and logs IGNORED + custom_filter
                val interestSms = "بانک ملت\nواریز سود ماهیانه\nمبلغ: 500,000ریال"
                val interestQueued = SmsReceiver.ingestIncomingSms(
                    appContext = app,
                    sender = "98200033",
                    rawBody = interestSms,
                    sendPushAlerts = false
                )
                org.junit.Assert.assertFalse(interestQueued)
                val interestFp = com.example.data.SmsParser.fingerprint("98200033", interestSms)
                val storedInterest = dao.getByFingerprint(interestFp)
                assertNotNull(storedInterest)
                assertEquals(com.example.data.SmsStatus.IGNORED.name, storedInterest?.status)
                assertEquals("custom_filter", storedInterest?.reason)

                // 3. DROP_SILENTLY sender rule drops without storing in Room or dispatching
                val spamSms = "بانک سامان\nواریز مبلغ: 2,000,000ریال"
                val spamQueued = SmsReceiver.ingestIncomingSms(
                    appContext = app,
                    sender = "98500099",
                    rawBody = spamSms,
                    sendPushAlerts = false
                )
                org.junit.Assert.assertFalse(spamQueued)
                val spamFp = com.example.data.SmsParser.fingerprint("98500099", spamSms)
                org.junit.Assert.assertNull(dao.getByFingerprint(spamFp))

                // 4. MIN_AMOUNT_TOMAN rule blocks 500 Toman (5,000 Rial) deposit, allows 50,000 Toman (500,000 Rial)
                val microSms = "بانک تجارت\nواریز به حساب\nمبلغ: 5,000 ریال"
                val microQueued = SmsReceiver.ingestIncomingSms(
                    appContext = app,
                    sender = "98200070",
                    rawBody = microSms,
                    sendPushAlerts = false
                )
                org.junit.Assert.assertFalse(microQueued)
                val microFp = com.example.data.SmsParser.fingerprint("98200070", microSms)
                assertEquals("custom_filter", dao.getByFingerprint(microFp)?.reason)

                val normalSms = "بانک تجارت\nواریز به حساب\nمبلغ: 500,000 ریال"
                val normalQueued = SmsReceiver.ingestIncomingSms(
                    appContext = app,
                    sender = "98200070",
                    rawBody = normalSms,
                    sendPushAlerts = false
                )
                assertTrue(normalQueued)
                val normalFp = com.example.data.SmsParser.fingerprint("98200070", normalSms)
                assertEquals(com.example.data.SmsStatus.NEW.name, dao.getByFingerprint(normalFp)?.status)

                // Verify blocked counter incremented by 3
                assertEquals(initialBlocked + 3L, repo.readSettings().blockedByCustomFiltersCount)

                // 5. Disabling a rule allows previously matched SMS through
                vm.toggleCustomFilterRule(senderRule.id)
                val afterDisableSms = "بانک سامان\nواریز به حساب\nمبلغ: 250,000 ریال"
                assertTrue(
                    SmsReceiver.ingestIncomingSms(
                        appContext = app,
                        sender = "98500099",
                        rawBody = afterDisableSms,
                        sendPushAlerts = false
                    )
                )

                // 6. Clean up custom rules
                repo.readSettings().customFilterRules.forEach { vm.removeCustomFilterRule(it.id) }
                assertEquals(0, repo.readSettings().customFilterRules.size)
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `verify post-error connection recovery sync, ServerHealthTracker trigger, and ForwardWorker recovery`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = SettingsRepo.getInstance(app)
        val db = AppDatabase.getInstance(app)
        val dao = db.logDao()

        com.example.network.ServerHealthTracker.reset()
        com.example.network.BatchRetryTracker.resetForTests()

        // 1. ServerHealthTracker failure -> recovery signal (tested before ViewModel collector consumes it)
        com.example.network.ServerHealthTracker.recordFailure(httpCode = 502, errorMessage = "Bad Gateway")
        assertTrue(com.example.network.ServerHealthTracker.postErrorSyncFlow.value.needsRecoverySync)
        org.junit.Assert.assertNull(com.example.network.ServerHealthTracker.consumeServerRecoveredTrigger())

        com.example.network.ServerHealthTracker.recordSuccess(httpCode = 200, latencyMs = 110L)
        assertEquals(
            "رفع خطای سرور و برقراری اتصال",
            com.example.network.ServerHealthTracker.consumeServerRecoveredTrigger()
        )
        // One-shot trigger consumed
        org.junit.Assert.assertNull(com.example.network.ServerHealthTracker.consumeServerRecoveredTrigger())

        // 2. Settings persistence & toggle via AsisViewModel
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(
                store,
                AsisViewModel.provideFactory(app)
            )[AsisViewModel::class.java]
            assertTrue(repo.readSettings().autoSyncAfterErrors)
            vm.setAutoSyncAfterErrors(false)
            org.junit.Assert.assertFalse(repo.readSettings().autoSyncAfterErrors)
            vm.setAutoSyncAfterErrors(true)
            assertTrue(repo.readSettings().autoSyncAfterErrors)

            repo.saveEndpointAndToken(
                "https://recovery-test.example.com/panel",
                "valid_recovery_gateway_token_123456"
            )
            repo.setServiceEnabled(true)
            repo.setAutoApproveInApp(true)

            val mockHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.toString()
                    val now = System.currentTimeMillis()
                    val jsonBody = when {
                        url.endsWith("/sms-pending") -> """
                            {
                              "server_now_ms": $now,
                              "match_window_hours": 24,
                              "auto_max_toman": 0,
                              "items": [
                                {"kind": "order", "public_id": "ORD-REC-503", "due_minor": 1000000, "created_ms": ${now - 5000}, "early": false},
                                {"kind": "order", "public_id": "ORD-REC-401", "due_minor": 2000000, "created_ms": ${now - 4000}, "early": false}
                              ]
                            }
                        """.trimIndent()
                        url.endsWith("/sms-approve") -> """{"ok":true,"status":"approved"}"""
                        else -> """{"ok":true,"status":"new","approved":1}"""
                    }
                    okhttp3.Response.Builder()
                        .request(req)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.Companion.run { jsonBody.toResponseBody(null) })
                        .build()
                }
                .build()

            try {
                com.example.network.GatewayApiClient.testClientOverride = mockHttpClient
                kotlinx.coroutines.runBlocking {
                    dao.clearAllLogs()
                    dao.clearAllInvoices()
                    dao.clearAllRetryEvents()
                    val now = System.currentTimeMillis()

                    val rec503Id = dao.insertIgnoreDuplicate(
                        com.example.data.SmsRecord(
                            fingerprint = "fp_rec_503",
                            bank = "98200033",
                            bankTitle = "بانک ملت",
                            body = "بانک ملت واریز 1,000,000 ریال",
                            normalized = "بانک ملت واریز 1,000,000 ریال",
                            amountMinor = 1_000_000L,
                            reason = "",
                            status = com.example.data.SmsStatus.RETRY.name,
                            attempts = 8, // exhausted during long outage
                            httpCode = 503,
                            lastError = "Service Unavailable",
                            receivedMs = now - 30_000L,
                            nextRetryAtMs = now + 240_000L // far in the future
                        )
                    )
                    val rec401Id = dao.insertIgnoreDuplicate(
                        com.example.data.SmsRecord(
                            fingerprint = "fp_rec_401",
                            bank = "98200070",
                            bankTitle = "بانک تجارت",
                            body = "بانک تجارت واریز 2,000,000 ریال",
                            normalized = "بانک تجارت واریز 2,000,000 ریال",
                            amountMinor = 2_000_000L,
                            reason = "",
                            status = com.example.data.SmsStatus.FAILED.name,
                            attempts = 1,
                            httpCode = 401,
                            lastError = "Unauthorized token",
                            receivedMs = now - 25_000L,
                            nextRetryAtMs = null
                        )
                    )

                    // Step A: Network recovery (includeAuth401 = false) recovers 503 RETRY item immediately even though attempts==8 & nextRetryAtMs was +240s,
                    // while keeping 401 token failure untouched.
                    val outcomeNet = ForwardWorker.runRecoverySyncNow(
                        context = app,
                        includeAuth401 = false,
                        reasonFa = "بازگشت اتصال اینترنت"
                    )
                    assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcomeNet)
                    assertEquals(com.example.data.SmsStatus.MATCHED.name, dao.getById(rec503Id)?.status)
                    assertEquals(com.example.data.SmsStatus.FAILED.name, dao.getById(rec401Id)?.status)
                    assertEquals(1, com.example.network.ServerHealthTracker.postErrorSyncFlow.value.lastRecoveryRecoveredCount)
                    assertEquals(1, com.example.network.ServerHealthTracker.postErrorSyncFlow.value.lastRecoveryRemainingErrors)

                    // Step B: Credential / token recovery (includeAuth401 = true) also recovers the 401 FAILED record.
                    val outcomeAuth = ForwardWorker.runRecoverySyncNow(
                        context = app,
                        includeAuth401 = true,
                        reasonFa = "اصلاح توکن"
                    )
                    assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcomeAuth)
                    assertEquals(com.example.data.SmsStatus.MATCHED.name, dao.getById(rec401Id)?.status)
                    assertEquals(0, com.example.network.ServerHealthTracker.postErrorSyncFlow.value.lastRecoveryRemainingErrors)
                    assertEquals(1, repo.readSettings().lastErrorRecoverySyncedCount)
                }
            } finally {
                com.example.network.GatewayApiClient.testClientOverride = null
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `verify 15-day automatic log cleanup preserves recent logs and approved invoices`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = SettingsRepo.getInstance(app)
        val db = AppDatabase.getInstance(app)
        val dao = db.logDao()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(
                store,
                AsisViewModel.provideFactory(app)
            )[AsisViewModel::class.java]

            kotlinx.coroutines.runBlocking {
                dao.clearAllLogs()
                dao.clearAllInvoices()
                dao.clearAllRetryEvents()

                val nowMs = System.currentTimeMillis()
                val dayMs = 86_400_000L

                val oldSmsId = dao.insertIgnoreDuplicate(
                    com.example.data.SmsRecord(
                        fingerprint = "fp_old_20d",
                        bank = "98200033",
                        bankTitle = "بانک ملت",
                        body = "واریز قدیمی ۲۰ روز پیش",
                        normalized = "واریز قدیمی ۲۰ روز پیش",
                        amountMinor = 12_000_000L,
                        reason = "",
                        status = com.example.data.SmsStatus.MATCHED.name,
                        receivedMs = nowMs - 20L * dayMs,
                        storedMs = nowMs - 20L * dayMs
                    )
                )
                val recentSmsId = dao.insertIgnoreDuplicate(
                    com.example.data.SmsRecord(
                        fingerprint = "fp_recent_5d",
                        bank = "98200070",
                        bankTitle = "بانک تجارت",
                        body = "واریز ۵ روز پیش",
                        normalized = "واریز ۵ روز پیش",
                        amountMinor = 25_000_000L,
                        reason = "",
                        status = com.example.data.SmsStatus.MATCHED.name,
                        receivedMs = nowMs - 5L * dayMs,
                        storedMs = nowMs - 5L * dayMs
                    )
                )

                dao.insertRetryAttemptEvents(
                    listOf(
                        com.example.data.RetryAttemptEventEntity(
                            smsRecordId = oldSmsId,
                            attemptedAtMs = nowMs - 20L * dayMs,
                            succeeded = false,
                            httpCode = 503
                        ),
                        com.example.data.RetryAttemptEventEntity(
                            smsRecordId = recentSmsId,
                            attemptedAtMs = nowMs - 3L * dayMs,
                            succeeded = true,
                            httpCode = 200
                        )
                    )
                )

                // Approved invoice older than 15 days must NEVER be deleted by log cleanup
                dao.recordApprovedInvoice(
                    publicId = "ORD-KEEP-20D",
                    kind = "order",
                    dueMinor = 15_000_000L,
                    smsId = oldSmsId,
                    bankTitle = "بانک ملت",
                    matchedAtMs = nowMs - 20L * dayMs
                )

                assertEquals(1, dao.countLogsOlderThan(nowMs - 15L * dayMs))

                // Enable 15-day auto cleanup in SettingsRepo and run cleanup deterministically
                repo.setAutoClearOldLogsEnabled(true)
                assertTrue(repo.readSettings().autoClearOldLogsEnabled)

                val deletedCount = ForwardWorker.runOldLogsCleanupNow(app, forceRun15Day = false)
                assertEquals(1, deletedCount)
                org.junit.Assert.assertNull(dao.getById(oldSmsId))
                assertNotNull(dao.getById(recentSmsId))
                assertEquals(0, dao.countLogsOlderThan(nowMs - 15L * dayMs))
                assertEquals(1, dao.getRecentRetryEvents(0L).size)
                assertEquals(1, repo.readSettings().lastAutoLogClearedCount)
                assertNotNull(repo.readSettings().lastAutoLogCleanupAtMs)

                // Verify ViewModel toggle schedules/cancels WorkManager cleanly
                vm.setAutoClearOldLogsEnabled(true)
                assertTrue(vm.settingsState.value.autoClearOldLogsEnabled)

                // Verify approved invoice survived 15-day cleanup intact with its price
                val preservedInvoice = dao.getInvoiceByPublicId("ORD-KEEP-20D")
                assertNotNull(preservedInvoice)
                assertEquals("MATCHED", preservedInvoice?.status)
                assertEquals(15_000_000L, preservedInvoice?.dueMinor)

                // When autoClearOldLogsEnabled is false, 20-day log falls under 30-day default and isn't deleted automatically
                vm.setAutoClearOldLogsEnabled(false)
                val secondOldId = dao.insertIgnoreDuplicate(
                    com.example.data.SmsRecord(
                        fingerprint = "fp_old_20d_disabled",
                        bank = "98200033",
                        bankTitle = "بانک ملت",
                        body = "واریز ۲۰ روز پیش هنگام خاموش بودن حذف ۱۵ روزه",
                        normalized = "واریز ۲۰ روز پیش هنگام خاموش بودن حذف ۱۵ روزه",
                        amountMinor = 9_000_000L,
                        reason = "",
                        status = com.example.data.SmsStatus.IGNORED.name,
                        receivedMs = nowMs - 20L * dayMs,
                        storedMs = nowMs - 20L * dayMs
                    )
                )
                val deletedWhenDisabled = ForwardWorker.runOldLogsCleanupNow(app, forceRun15Day = false)
                assertEquals(0, deletedWhenDisabled)
                assertNotNull(dao.getById(secondOldId))

                // Manual forceRun15Day still cleans >15d logs on user click
                val forcedDeleted = ForwardWorker.runOldLogsCleanupNow(app, forceRun15Day = true)
                assertEquals(1, forcedDeleted)
                org.junit.Assert.assertNull(dao.getById(secondOldId))
            }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `verify approved invoice persistence, non-overwrite guard, price reporting, and notification`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = app.applicationContext
        val repo = SettingsRepo.getInstance(context)
        val db = AppDatabase.getInstance(context)
        val dao = db.logDao()

        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        nm.cancelAll()

        repo.setNotifyOnApprovedInvoice(true)
        repo.saveEndpointAndToken(
            "https://invoice-test.example.com/panel",
            "valid_invoice_gateway_token_123456"
        )
        repo.setServiceEnabled(true)
        repo.setAutoApproveInApp(true)

        val nowMs = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            dao.clearAllLogs()
            dao.clearAllInvoices()
            dao.clearAllRetryEvents()

            // 1. Direct recordApprovedInvoice persistence & upsertServerWaitingInvoices guard
            dao.recordApprovedInvoice(
                publicId = "ORD-901",
                kind = "order",
                dueMinor = 35_000_000L,
                smsId = 10L,
                bankTitle = "بانک ملت",
                matchedAtMs = nowMs - 2_000L
            )
            val inv901 = dao.getInvoiceByPublicId("ORD-901")
            assertNotNull(inv901)
            assertEquals("MATCHED", inv901?.status)
            assertEquals(35_000_000L, inv901?.dueMinor)
            assertEquals("بانک ملت", inv901?.bankTitle)

            // Server re-sending WAITING for same publicId must NOT overwrite MATCHED record
            dao.upsertServerWaitingInvoices(
                listOf(
                    com.example.data.PendingInvoiceEntity(
                        publicId = "ORD-901",
                        kind = "order",
                        dueMinor = 35_000_000L,
                        createdMs = nowMs - 2_000L,
                        status = "WAITING"
                    )
                )
            )
            assertEquals("MATCHED", dao.getInvoiceByPublicId("ORD-901")?.status)

            // 2. End-to-end ForwardWorker sweep approves ORD-902 (42,000,000 Rial = 4,200,000 Toman),
            // persists MATCHED invoice + price, and fires price notification.
            val mockHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.toString()
                    val now = System.currentTimeMillis()
                    val jsonBody = when {
                        url.endsWith("/sms-pending") -> """
                            {
                              "server_now_ms": $now,
                              "match_window_hours": 24,
                              "auto_max_toman": 0,
                              "items": [
                                {"kind": "order", "public_id": "ORD-902", "due_minor": 42000000, "created_ms": ${now - 10000}, "early": false}
                              ]
                            }
                        """.trimIndent()
                        url.endsWith("/sms-approve") -> """{"ok":true,"status":"approved"}"""
                        else -> """{"ok":true}"""
                    }
                    okhttp3.Response.Builder()
                        .request(req)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.Companion.run { jsonBody.toResponseBody(null) })
                        .build()
                }
                .build()

            try {
                com.example.network.GatewayApiClient.testClientOverride = mockHttpClient
                val recId = dao.insertIgnoreDuplicate(
                    com.example.data.SmsRecord(
                        fingerprint = "fp_approve_902",
                        bank = "98200021",
                        bankTitle = "بانک سامان",
                        body = "بانک سامان\nواریز مبلغ: 42,000,000 ریال",
                        normalized = "بانک سامان واریز مبلغ: 42,000,000 ریال",
                        amountMinor = 42_000_000L,
                        reason = "",
                        status = com.example.data.SmsStatus.NEW.name,
                        receivedMs = nowMs
                    )
                )

                val outcome = ForwardWorker.runSweepNow(context)
                assertEquals(ForwardWorker.WorkOutcome.SUCCESS, outcome)

                val matchedSms = dao.getById(recId)
                assertEquals(com.example.data.SmsStatus.MATCHED.name, matchedSms?.status)
                assertEquals("ORD-902", matchedSms?.matchedId)

                val inv902 = dao.getInvoiceByPublicId("ORD-902")
                assertNotNull(inv902)
                assertEquals("MATCHED", inv902?.status)
                assertEquals(42_000_000L, inv902?.dueMinor)
                assertEquals("بانک سامان", inv902?.bankTitle)
                assertNotNull(inv902?.matchedAtMs)

                // Verify notification includes invoice ID & Toman price (4,200,000 تومان)
                val notifTexts = shadowNm.allNotifications.map { n ->
                    val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
                    "$title | $text"
                }
                assertTrue(
                    "Expected notification reporting ORD-902 and its Toman price, got: $notifTexts",
                    notifTexts.any { it.contains("ORD-902") && it.contains("4,200,000") }
                )

                // Disabling notifyOnApprovedInvoice suppresses approved invoice notification
                repo.setNotifyOnApprovedInvoice(false)
                org.junit.Assert.assertFalse(
                    com.example.util.NotificationHelper.notifyInvoiceApproved(
                        context = context,
                        publicId = "ORD-903",
                        kind = "order",
                        amountMinor = 10_000_000L,
                        bankTitle = "بانک ملت",
                        recordId = 903L
                    )
                )
            } finally {
                com.example.network.GatewayApiClient.testClientOverride = null
            }
        }
    }
}
