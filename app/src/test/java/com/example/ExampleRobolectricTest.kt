package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and verify SettingsRepo persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ASiS pannel", appName)

        val repo = SettingsRepo.getInstance(context)
        repo.saveEndpointAndToken("https://my-panel.example.com/secret-path", "my_super_secret_gateway_token_12345")
        val saved = repo.readSettings()
        assertTrue(saved.isConfigured)
        assertFalse(saved.autoApproveInApp) // Default OFF per Section 7
        assertEquals("https://my-panel.example.com/secret-path/sms-in", saved.smsInUrl)
        assertEquals("https://my-panel.example.com/secret-path/sms-pending", saved.smsPendingUrl)
        assertEquals("https://my-panel.example.com/secret-path/sms-approve", saved.smsApproveUrl)
    }
}
