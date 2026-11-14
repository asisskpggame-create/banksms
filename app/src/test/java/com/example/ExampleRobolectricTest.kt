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
        }
    }
}
