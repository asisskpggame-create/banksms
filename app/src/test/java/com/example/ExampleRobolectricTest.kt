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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
}
