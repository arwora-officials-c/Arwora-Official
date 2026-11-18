package com.arwora

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.service.AdbPairingNotifier
import com.arwora.service.AdbPairingReceiver
import com.arwora.ui.components.WirelessPairingWizard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbNotificationPairingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ready notification includes adb_pairing_channel port text and key_pairing_code RemoteInput`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = AdbPairingNotifier.buildReadyNotification(
            context = context,
            host = "192.168.1.42",
            port = 41835
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AdbPairingNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        assertEquals(AdbPairingNotifier.CHANNEL_ID, notification.channelId)
        assertEquals(
            "Wireless Debugging Pairing Ready",
            notification.extras.getString(Notification.EXTRA_TITLE)
        )
        assertEquals(
            "Pairing port detected (41835). Enter the 6-digit code from Developer Options below.",
            notification.extras.getString(Notification.EXTRA_TEXT)
        )
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)

        val actionCount = NotificationCompat.getActionCount(notification)
        assertTrue(actionCount >= 1)
        val replyAction = NotificationCompat.getAction(notification, 0)
        assertEquals("Enter 6-Digit Code", replyAction?.title?.toString())
        val remoteInputs = replyAction?.remoteInputs
        assertNotNull(remoteInputs)
        assertEquals(1, remoteInputs!!.size)
        assertEquals("key_pairing_code", remoteInputs[0].resultKey)
        assertEquals("Enter 6-Digit Code", remoteInputs[0].label?.toString())
    }

    @Test
    fun `pairing progress success and failure notifications expose expected states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pairingNotif = AdbPairingNotifier.buildPairingProgressNotification(context)
        assertEquals("Pairing with arwora...", pairingNotif.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(pairingNotif.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))

        val successNotif = AdbPairingNotifier.buildSuccessConnectingNotification(
            context = context,
            connected = false
        )
        assertEquals(
            "Paired Successfully! Connecting ADB...",
            successNotif.extras.getString(Notification.EXTRA_TEXT)
        )
        assertTrue(successNotif.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))

        val failureNotif = AdbPairingNotifier.buildFailureNotification(context, "Connection refused")
        assertEquals(
            "Pairing Failed — Tap to Retry",
            failureNotif.extras.getString(Notification.EXTRA_TITLE)
        )
        val retryAction = NotificationCompat.getAction(failureNotif, 0)
        assertEquals("Pairing Failed — Tap to Retry", retryAction?.title?.toString())
    }

    @Test
    fun `extractSixDigitReplyCode validates Direct Reply text accurately`() {
        assertEquals("482910", AdbPairingReceiver.extractSixDigitReplyCode("482910"))
        assertEquals("649201", AdbPairingReceiver.extractSixDigitReplyCode(" 649 201 "))
        assertEquals("123456", AdbPairingReceiver.extractSixDigitReplyCode("code: 123456"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("12345"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("abcdef"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("   "))
    }

    @Test
    fun `mode 1 ui displays Start Notification Pairing primary pill button alongside manual fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WirelessPairingManager.selectPairingMode(context, WirelessPairingMode.NOTIFICATION_MANUAL)

        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WirelessPairingWizard()
                }
            }
        }

        composeTestRule.onNodeWithTag("pairing_card_manual").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_start_notification_pairing_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Notification Pairing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manual Input Fallback").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_host_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_code_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_port_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wireless_pair_manually_button").performScrollTo().assertIsDisplayed()
    }
}
