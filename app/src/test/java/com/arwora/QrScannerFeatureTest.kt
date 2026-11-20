package com.arwora

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.scanner.QrScanCategory
import com.arwora.feature.scanner.QrScanParser
import com.arwora.ui.screens.HomeQrScannerCard
import com.arwora.ui.screens.QrResultBottomSheet
import com.arwora.ui.screens.allGlobalTools
import com.arwora.ui.theme.NameGenProTheme
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class QrScannerFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `parses escaped wifi qr payload accurately`() {
        val result = QrScanParser.parseRawPayload("WIFI:T:WPA;S:ArworaNet;P:Pass\\:123\\;;H:true;;")
        assertEquals(QrScanCategory.WIFI, result.category)
        assertNotNull(result.wifiDetails)
        assertEquals("ArworaNet", result.wifiDetails?.ssid)
        assertEquals("WPA/WPA2", result.wifiDetails?.securityType)
        assertEquals("Pass:123;", result.wifiDetails?.password)
        assertTrue(result.wifiDetails?.isHidden == true)
    }

    @Test
    fun `parses wpa3 and open wifi payloads`() {
        val wpa3 = QrScanParser.parseRawPayload("WIFI:T:SAE;S:Studio6E;P:SecretKey;H:false;;")
        assertEquals(QrScanCategory.WIFI, wpa3.category)
        assertEquals("WPA3", wpa3.wifiDetails?.securityType)

        val open = QrScanParser.parseRawPayload("WIFI:T:nopass;S:GuestWifi;;;")
        assertEquals(QrScanCategory.WIFI, open.category)
        assertEquals("Open", open.wifiDetails?.securityType)
        assertEquals("GuestWifi", open.wifiDetails?.ssid)
    }

    @Test
    fun `normalizes urls and classifies contact product and text codes`() {
        val url = QrScanParser.parseRawPayload("www.arwora.dev/docs")
        assertEquals(QrScanCategory.URL, url.category)
        assertEquals("https://www.arwora.dev/docs", url.normalizedUrl)

        val vcard = QrScanParser.parseRawPayload(
            "BEGIN:VCARD\nFN:Alice Doe\nTEL:+1234567890\nEMAIL:alice@arwora.dev\nORG:Arwora\nEND:VCARD"
        )
        assertEquals(QrScanCategory.CONTACT, vcard.category)
        assertEquals("Alice Doe", vcard.contactDetails?.name)
        assertEquals("+1234567890", vcard.contactDetails?.phone)
        assertEquals("alice@arwora.dev", vcard.contactDetails?.email)

        val product = QrScanParser.parseRawPayload(
            "8901234567890",
            formatLabel = QrScanParser.formatLabelForCode(Barcode.FORMAT_EAN_13)
        )
        assertEquals(QrScanCategory.PRODUCT, product.category)
        assertEquals("EAN-13", product.formatLabel)

        val dataMatrix = QrScanParser.parseRawPayload(
            "INV-2026-991",
            formatLabel = QrScanParser.formatLabelForCode(Barcode.FORMAT_DATA_MATRIX)
        )
        assertEquals(QrScanCategory.TEXT, dataMatrix.category)
        assertEquals("Data Matrix", dataMatrix.formatLabel)

        val geo = QrScanParser.parseRawPayload("geo:35.6586,139.7454")
        assertEquals(QrScanCategory.GEO, geo.category)

        val mecard = QrScanParser.parseRawPayload("MECARD:N:Bob Smith;TEL:+1987654321;EMAIL:bob@arwora.dev;ORG:Arwora;;")
        assertEquals(QrScanCategory.CONTACT, mecard.category)
        assertEquals("Bob Smith", mecard.contactDetails?.name)
        assertEquals("+1987654321", mecard.contactDetails?.phone)
        assertEquals("bob@arwora.dev", mecard.contactDetails?.email)

        val wifiSsidFirst = QrScanParser.parseRawPayload("WIFI:S:HomeNet;T:WPA;P:Secret123;;")
        assertEquals(QrScanCategory.WIFI, wifiSsidFirst.category)
        assertEquals("HomeNet", wifiSsidFirst.wifiDetails?.ssid)
        assertEquals("Secret123", wifiSsidFirst.wifiDetails?.password)
    }

    @Test
    fun `home qr scanner card renders and triggers open scanner callback`() {
        var opened = false
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                HomeQrScannerCard(onOpenScanner = { opened = true })
            }
        }

        composeTestRule.onNodeWithTag("home_qr_scanner_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("All QR & Barcode Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Wi-Fi, URLs, contacts & barcodes 100% offline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_open_qr_scanner_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Scanner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_open_qr_scanner_button").performClick()
        assertTrue(opened)
    }

    @Test
    fun `qr result bottom sheet exposes wifi copy share and scan again actions`() {
        var copied = false
        var shared = false
        var primaryTriggered = false
        var dismissed = false

        val wifiResult = QrScanParser.parseRawPayload("WIFI:T:WPA;S:ArworaOffice;P:UltraSecure99;;")

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                QrResultBottomSheet(
                    result = wifiResult,
                    onDismiss = { dismissed = true },
                    onCopy = { copied = true },
                    onShare = { shared = true },
                    onPrimaryAction = { primaryTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Wi-Fi Network Detected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network SSID: ArworaOffice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("qr_result_primary_action_button").performClick()
        composeTestRule.onNodeWithTag("qr_result_copy_button").performClick()
        composeTestRule.onNodeWithTag("qr_result_share_button").performClick()
        composeTestRule.onNodeWithTag("qr_result_scan_again_button").performClick()

        assertTrue(primaryTriggered)
        assertTrue(copied)
        assertTrue(shared)
        assertTrue(dismissed)
    }

    @Test
    fun `qr scanner activity is registered in manifest and searchable globally`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolved = context.packageManager.resolveActivity(
            Intent(context, QrScannerActivity::class.java),
            0
        )
        assertNotNull("QrScannerActivity must be registered in AndroidManifest.xml", resolved)
        assertTrue(allGlobalTools.any { it.title == "All QR & Barcode Scanner" })
    }
}
