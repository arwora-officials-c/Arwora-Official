package com.arwora

import android.os.Build
import com.arwora.utils.AppPermissionsHelper
import com.arwora.utils.ManagedPermissionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionsHelperTest {

    @Test
    fun buildPermissionListIncludesFiveExpectedPermissionsAndExcludesCamera() {
        val permissions = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            micGranted = true,
            storageGranted = false,
            notificationsGranted = true,
            listenerGranted = false,
            overlayGranted = true
        )

        assertEquals(5, permissions.size)
        assertEquals(
            listOf(
                ManagedPermissionCategory.MICROPHONE,
                ManagedPermissionCategory.STORAGE,
                ManagedPermissionCategory.NOTIFICATIONS,
                ManagedPermissionCategory.NOTIFICATION_LISTENER,
                ManagedPermissionCategory.OVERLAY
            ),
            permissions.map { it.category }
        )

        // Strictly verify no camera permission check or UI entry exists
        assertFalse(permissions.any { it.id.contains("camera", ignoreCase = true) })
        assertFalse(permissions.any { it.title.contains("camera", ignoreCase = true) })
        assertFalse(permissions.any { it.permissionKey.contains("CAMERA", ignoreCase = true) })
        assertFalse(
            ManagedPermissionCategory.entries.any {
                it.name.contains("CAMERA", ignoreCase = true)
            }
        )
    }

    @Test
    fun usesModernApi33PlusPermissionKeysOnAndroid13AndAbove() {
        val permissions = AppPermissionsHelper.buildPermissionList(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            micGranted = false,
            storageGranted = true,
            notificationsGranted = true,
            listenerGranted = true,
            overlayGranted = false
        )

        val storage = permissions.first { it.category == ManagedPermissionCategory.STORAGE }
        val notifications = permissions.first { it.category == ManagedPermissionCategory.NOTIFICATIONS }

        assertEquals("READ_MEDIA_IMAGES / VIDEO", storage.permissionKey)
        assertEquals("POST_NOTIFICATIONS", notifications.permissionKey)
        assertTrue(storage.isGranted)
        assertTrue(notifications.isGranted)
        assertEquals(3, permissions.count { it.isGranted })
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
}
