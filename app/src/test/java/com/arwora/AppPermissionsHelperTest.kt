package com.arwora

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.arwora.utils.AppPermissionsHelper
import com.arwora.utils.ManagedPermissionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppPermissionsHelperTest {

    @Test
    fun buildPermissionListIncludesAllEightExpectedPermissions() {
        val permissions = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            micGranted = true,
            storageGranted = false,
            notificationsGranted = true,
            listenerGranted = false,
            overlayGranted = true,
            cameraGranted = true,
            wirelessAdbGranted = false,
            usageStatsGranted = true
        )

        assertEquals(8, permissions.size)
        assertEquals(
            listOf(
                ManagedPermissionCategory.MICROPHONE,
                ManagedPermissionCategory.STORAGE,
                ManagedPermissionCategory.NOTIFICATIONS,
                ManagedPermissionCategory.NOTIFICATION_LISTENER,
                ManagedPermissionCategory.OVERLAY,
                ManagedPermissionCategory.CAMERA,
                ManagedPermissionCategory.WIRELESS_ADB,
                ManagedPermissionCategory.USAGE_STATS
            ),
            permissions.map { it.category }
        )

        assertTrue(permissions.any { it.id == "camera" && it.permissionKey == "CAMERA" && it.isGranted })
        assertTrue(permissions.any { it.id == "wireless_adb" && it.permissionKey == "WIRELESS_ADB" && !it.isGranted })
        assertTrue(permissions.any { it.id == "usage_stats" && it.permissionKey == "PACKAGE_USAGE_STATS" && it.isGranted })
    }

    @Test
    fun usesModernApi33PlusPermissionKeysOnAndroid13AndAbove() {
        val permissions = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            micGranted = false,
            storageGranted = true,
            notificationsGranted = true,
            listenerGranted = true,
            overlayGranted = false,
            cameraGranted = false,
            wirelessAdbGranted = true,
            usageStatsGranted = false
        )

        val storage = permissions.first { it.category == ManagedPermissionCategory.STORAGE }
        val notifications = permissions.first { it.category == ManagedPermissionCategory.NOTIFICATIONS }
        val camera = permissions.first { it.category == ManagedPermissionCategory.CAMERA }
        val wirelessAdb = permissions.first { it.category == ManagedPermissionCategory.WIRELESS_ADB }
        val usageStats = permissions.first { it.category == ManagedPermissionCategory.USAGE_STATS }

        assertEquals("READ_MEDIA_IMAGES / VIDEO", storage.permissionKey)
        assertEquals("POST_NOTIFICATIONS", notifications.permissionKey)
        assertEquals("CAMERA", camera.permissionKey)
        assertEquals("WIRELESS_ADB", wirelessAdb.permissionKey)
        assertEquals("PACKAGE_USAGE_STATS", usageStats.permissionKey)
        assertTrue(storage.isGranted)
        assertTrue(notifications.isGranted)
        assertTrue(wirelessAdb.isGranted)
        assertFalse(camera.isGranted)
        assertFalse(usageStats.isGranted)
        assertEquals(4, permissions.count { it.isGranted })
    }

    @Test
    fun usesLegacyPermissionKeysBelowAndroid13() {
        val permissions = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.S,
            micGranted = true,
            storageGranted = true,
            notificationsGranted = false,
            listenerGranted = false,
            overlayGranted = false
        )

        val storage = permissions.first { it.category == ManagedPermissionCategory.STORAGE }
        val notifications = permissions.first { it.category == ManagedPermissionCategory.NOTIFICATIONS }
        val mic = permissions.first { it.category == ManagedPermissionCategory.MICROPHONE }
        val listener = permissions.first { it.category == ManagedPermissionCategory.NOTIFICATION_LISTENER }
        val overlay = permissions.first { it.category == ManagedPermissionCategory.OVERLAY }

        assertEquals("READ_EXTERNAL_STORAGE", storage.permissionKey)
        assertEquals("APP_NOTIFICATIONS", notifications.permissionKey)
        assertEquals("RECORD_AUDIO", mic.permissionKey)
        assertEquals("BIND_NOTIFICATION_LISTENER_SERVICE", listener.permissionKey)
        assertEquals("SYSTEM_ALERT_WINDOW", overlay.permissionKey)
        assertEquals(2, permissions.count { it.isGranted })
    }

    @Test
    fun createSettingsIntentMapsNewCategoriesToExpectedSystemScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cameraIntent = AppPermissionsHelper.createSettingsIntent(
            context,
            ManagedPermissionCategory.CAMERA
        )
        val wirelessAdbIntent = AppPermissionsHelper.createSettingsIntent(
            context,
            ManagedPermissionCategory.WIRELESS_ADB
        )
        val usageStatsIntent = AppPermissionsHelper.createSettingsIntent(
            context,
            ManagedPermissionCategory.USAGE_STATS
        )

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, cameraIntent.action)
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, wirelessAdbIntent.action)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, usageStatsIntent.action)
    }

    @Test
    fun runtimeAndSystemMissingHelpersPrioritizeMissingPermissionsCorrectly() {
        val listApi34 = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            micGranted = true,
            storageGranted = false,
            notificationsGranted = false,
            listenerGranted = true,
            overlayGranted = false,
            cameraGranted = false,
            wirelessAdbGranted = false,
            usageStatsGranted = false
        )

        val missingRuntime = AppPermissionsHelper.buildMissingRuntimePermissions(
            permissions = listApi34,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        )
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.CAMERA
            ),
            missingRuntime
        )

        assertEquals(
            ManagedPermissionCategory.USAGE_STATS,
            AppPermissionsHelper.findNextMissingSystemCategory(listApi34)
        )

        val allGranted = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            micGranted = true,
            storageGranted = true,
            notificationsGranted = true,
            listenerGranted = true,
            overlayGranted = true,
            cameraGranted = true,
            wirelessAdbGranted = true,
            usageStatsGranted = true
        )
        assertTrue(
            AppPermissionsHelper.buildMissingRuntimePermissions(
                allGranted,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ).isEmpty()
        )
        assertNull(AppPermissionsHelper.findNextMissingSystemCategory(allGranted))
    }
}
