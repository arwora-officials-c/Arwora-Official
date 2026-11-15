package com.arwora

import com.arwora.utils.WirelessPairingManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionRefreshTest {

    @Test
    fun matchesPackageAcrossMultiEntryListenerSetting() {
        val settingValue = "com.android.launcher3/com.android.launcher3.notification.NotificationListener:" +
            "com.arworahyper.toolkit/com.arwora.service.NotificationHistoryService:" +
            "com.example.other/com.example.Service"
        assertTrue(
            WirelessPairingManager.isPackageInEnabledNotificationListeners(
                settingValue,
                "com.arworahyper.toolkit"
            )
        )
    }

    @Test
    fun rejectsPrefixCollidingOrMissingPackages() {
        val settingValue = "com.arworahyper.toolkit.clone/com.arwora.service.NotificationHistoryService"
        assertFalse(
            WirelessPairingManager.isPackageInEnabledNotificationListeners(
                settingValue,
                "com.arworahyper.toolkit"
            )
        )
        assertFalse(
            WirelessPairingManager.isPackageInEnabledNotificationListeners(
                null,
                "com.arworahyper.toolkit"
            )
        )
        assertFalse(
            WirelessPairingManager.isPackageInEnabledNotificationListeners(
                "",
                "com.arworahyper.toolkit"
            )
        )
    }

    @Test
    fun matchesBarePackageEntryWithoutComponentSlash() {
        val settingValue = "com.arworahyper.toolkit"
        assertTrue(
            WirelessPairingManager.isPackageInEnabledNotificationListeners(
                settingValue,
                "com.arworahyper.toolkit"
            )
        )
    }
}
