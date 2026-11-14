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
        }
    }
}
