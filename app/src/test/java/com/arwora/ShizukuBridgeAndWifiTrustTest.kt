package com.arwora

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.EncryptedAdbCredentialStore
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.ShizukuBridge
import com.arwora.utils.ShizukuBridgeStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShizukuBridgeAndWifiTrustTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShizukuBridge.resetTestOverrides()
        AdbShellExecutor.resetTestOverrides()
        NativeAdbManager.wifiNetworkResolverOverride = null
        EncryptedAdbCredentialStore.resetCacheForTesting()
        EncryptedAdbCredentialStore.clearSession(context)
    }

    @After
    fun tearDown() {
        NativeAdbManager.wifiNetworkResolverOverride = null
        NativeAdbManager.dismissWifiTrustPrompt()
        ShizukuBridge.resetTestOverrides()
        AdbShellExecutor.resetTestOverrides()
        AdbShellExecutor.refreshEngineStatus(null)
    }

    @Test
    fun shizukuBridgeBinderReceivedWithGrantedPermissionSetsAuthorizedWithoutReprompt() {
        var permissionPromptCount = 0
        ShizukuBridge.binderProbeOverride = { true }
        ShizukuBridge.permissionProbeOverride = { true }
        ShizukuBridge.rationaleProbeOverride = { false }
        ShizukuBridge.requestPermissionInvokerOverride = { permissionPromptCount++ }

        ShizukuBridge.init(context, autoRequestIfNeeded = true)
        ShizukuBridge.onBinderReceived()

        assertEquals(1001, ShizukuBridge.REQUEST_CODE)
        assertTrue(ShizukuBridge.isBinderAlive.value)
        assertTrue(ShizukuBridge.isPermissionGranted.value)
        assertFalse(ShizukuBridge.shouldShowRationale.value)
        assertEquals(ShizukuBridgeStatus.AUTHORIZED, ShizukuBridge.status.value)
        assertEquals(PrivilegeEngine.SHIZUKU, AdbShellExecutor.activeEngine.value)
        assertEquals(0, permissionPromptCount)
    }

    @Test
    fun shizukuBridgePermissionResult1001ImmediatelyUpdatesStateAndBinderDeadResets() {
        var requestedCodes = mutableListOf<Int>()
        var grantedNow = false

        ShizukuBridge.binderProbeOverride = { true }
        ShizukuBridge.permissionProbeOverride = { grantedNow }
        ShizukuBridge.rationaleProbeOverride = { false }
        ShizukuBridge.requestPermissionInvokerOverride = { code -> requestedCodes.add(code) }

        ShizukuBridge.init(context, autoRequestIfNeeded = true)
        ShizukuBridge.onBinderReceived()

        assertEquals(listOf(ShizukuBridge.REQUEST_CODE), requestedCodes)
        assertEquals(ShizukuBridgeStatus.PERMISSION_REQUIRED, ShizukuBridge.status.value)
        assertFalse(ShizukuBridge.isPermissionGranted.value)

        // Simulate user granting permission in Shizuku dialog
        grantedNow = true
        ShizukuBridge.onPermissionResult(ShizukuBridge.REQUEST_CODE, PackageManager.PERMISSION_GRANTED)

        assertTrue(ShizukuBridge.isPermissionGranted.value)
        assertEquals(ShizukuBridgeStatus.AUTHORIZED, ShizukuBridge.status.value)
        assertEquals(PrivilegeEngine.SHIZUKU, AdbShellExecutor.activeEngine.value)

        // Simulate binder death
        ShizukuBridge.binderProbeOverride = { false }
        grantedNow = false
        ShizukuBridge.onBinderDead()

        assertFalse(ShizukuBridge.isBinderAlive.value)
        assertFalse(ShizukuBridge.isPermissionGranted.value)
        assertEquals(PrivilegeEngine.NONE, AdbShellExecutor.activeEngine.value)
    }

    @Test
    fun wifiTrustPromptsOncePerNewSsidAndPersistsAlwaysAllow() {
        var currentSsid = "\"MAHFUZ-5G\""
        NativeAdbManager.wifiNetworkResolverOverride = {
            currentSsid to "ssid:${currentSsid.removeSurrounding("\"")}"
        }

        // Initial check on untrusted MAHFUZ-5G triggers prompt state cleanly
        val initialTrust = NativeAdbManager.refreshWifiTrustState(context)
        assertNotNull(initialTrust)
        assertEquals("MAHFUZ-5G", initialTrust?.ssid)
        assertFalse(initialTrust?.isTrusted ?: true)

        val prompt = NativeAdbManager.requestWifiTrustPromptIfNeeded(context)
        assertNotNull(prompt)
        assertEquals("MAHFUZ-5G", prompt?.ssid)
        assertTrue(prompt?.requiresPrompt == true)
        assertNotNull(NativeAdbManager.pendingWifiTrustPrompt.value)

        // User confirms "Always allow on this network"
        NativeAdbManager.trustCurrentWifiNetwork(context, alwaysAllow = true)
        assertTrue(NativeAdbManager.isCurrentWifiTrusted.value)
        assertNull(NativeAdbManager.pendingWifiTrustPrompt.value)
        assertTrue(EncryptedAdbCredentialStore.isWifiNetworkTrusted(context, "ssid:MAHFUZ-5G"))

        // Second check on the same trusted Wi-Fi MUST NOT prompt again
        val secondCheck = NativeAdbManager.requestWifiTrustPromptIfNeeded(context)
        assertTrue(secondCheck?.isTrusted == true)
        assertFalse(secondCheck?.requiresPrompt == true)
        assertNull(NativeAdbManager.pendingWifiTrustPrompt.value)

        // Switching to a different Wi-Fi network detects untrusted state and prompts once
        currentSsid = "\"Office-WiFi\""
        val switchedTrust = NativeAdbManager.refreshWifiTrustState(context)
        assertEquals("Office-WiFi", switchedTrust?.ssid)
        assertFalse(NativeAdbManager.isCurrentWifiTrusted.value)

        val officePrompt = NativeAdbManager.requestWifiTrustPromptIfNeeded(context)
        assertEquals("Office-WiFi", officePrompt?.ssid)
        assertTrue(officePrompt?.requiresPrompt == true)
    }
}
