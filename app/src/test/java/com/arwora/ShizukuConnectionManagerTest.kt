package com.arwora

import com.arwora.utils.ShizukuConnectionManager
import com.arwora.utils.ShizukuConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuConnectionManagerTest {

    @Test
    fun officialShizukuPackageAndStoreUrlsMatchSpec() {
        assertEquals("moe.shizuku.privileged.api", ShizukuConnectionManager.SHIZUKU_PACKAGE_NAME)
        assertEquals(
            "market://details?id=moe.shizuku.privileged.api",
            ShizukuConnectionManager.SHIZUKU_PLAY_STORE_MARKET_URI
        )
        assertEquals(
            "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api",
            ShizukuConnectionManager.SHIZUKU_PLAY_STORE_WEB_URI
        )
    }

    @Test
    fun resolvesNotInstalledWhenMissingAndBinderInactive() {
        val state = ShizukuConnectionManager.resolveConnectionState(
            isInstalled = false,
            isBinderAlive = false,
            hasPermission = false
        )
        assertEquals(ShizukuConnectionState.NOT_INSTALLED, state)
    }

    @Test
    fun resolvesInactiveWhenInstalledButServiceStopped() {
        val state = ShizukuConnectionManager.resolveConnectionState(
            isInstalled = true,
            isBinderAlive = false,
            hasPermission = false
        )
        assertEquals(ShizukuConnectionState.INACTIVE, state)
    }

    @Test
    fun resolvesPermissionRequiredWhenBinderAliveWithoutGrant() {
        val state = ShizukuConnectionManager.resolveConnectionState(
            isInstalled = true,
            isBinderAlive = true,
            hasPermission = false
        )
        assertEquals(ShizukuConnectionState.PERMISSION_REQUIRED, state)
    }

    @Test
    fun resolvesConnectedWhenBinderAliveAndPermissionGranted() {
        val state = ShizukuConnectionManager.resolveConnectionState(
            isInstalled = true,
            isBinderAlive = true,
            hasPermission = true
        )
        assertEquals(ShizukuConnectionState.CONNECTED, state)
    }
}
