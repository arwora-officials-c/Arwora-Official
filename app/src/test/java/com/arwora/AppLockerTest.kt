package com.arwora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arwora.utils.AppLockerManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLockerManager.init(context)
        AppLockerManager.setLockerEnabled(context, false)
        AppLockerManager.unlockAllTools(context)
        AppLockerManager.clearUnlockSessions()
    }

    @Test
    fun testPinSetupAndVerification() {
        assertFalse(AppLockerManager.isLockerEnabled.value)
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Set PIN
        AppLockerManager.setPin(context, "1234", "Favorite color?", "blue")
        assertTrue(AppLockerManager.hasPin.value)

        // Verify correct PIN
        assertTrue(AppLockerManager.verifyPin(context, "1234"))

        // Verify incorrect PIN
        assertFalse(AppLockerManager.verifyPin(context, "9999"))
    }

    @Test
    fun testToolLockingAndSessionUnlock() {
        AppLockerManager.setPin(context, "4321")
        AppLockerManager.setLockerEnabled(context, true)
        assertTrue(AppLockerManager.isLockerEnabled.value)

        // Tool is initially unlocked
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Lock wifi_controller
        AppLockerManager.toggleToolLock(context, "wifi_ctrl")
        assertTrue(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Unlock session temporarily
        AppLockerManager.unlockSession("wifi_controller")
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Clear unlock session
        AppLockerManager.clearUnlockSessions()
        assertTrue(AppLockerManager.isToolLockedByRoute("wifi_controller"))
    }

    @Test
    fun testSecurityAnswerRecovery() {
        AppLockerManager.setPin(context, "1111", "Pet name?", "Fluffy")
        assertTrue(AppLockerManager.verifyPin(context, "1111"))

        // Reset with correct answer
        val success = AppLockerManager.resetPinWithSecurityAnswer(context, "Fluffy", "2222")
        assertTrue(success)
        assertTrue(AppLockerManager.verifyPin(context, "2222"))
        assertFalse(AppLockerManager.verifyPin(context, "1111"))

        // Attempt reset with wrong answer
        val failed = AppLockerManager.resetPinWithSecurityAnswer(context, "Spot", "3333")
        assertFalse(failed)
        assertTrue(AppLockerManager.verifyPin(context, "2222"))
    }

    @Test
    fun testSelfPackageExclusion() {
        // Verify self-packages cannot be locked
        assertTrue(AppLockerManager.isSelfPackageName("com.arworahyper.toolkit"))
        assertTrue(AppLockerManager.isSelfPackageName("com.arwora"))
        assertTrue(AppLockerManager.isSelfPackageName("com.arworahyper.toolkit.sub"))
        assertFalse(AppLockerManager.isSelfPackageName("com.google.android.apps.photos"))
        assertFalse(AppLockerManager.isSelfPackageName("com.android.gallery3d"))

        // Attempting to lock self-package must return false and not add to locked list
        val result = AppLockerManager.toggleAppLock(context, "com.arworahyper.toolkit")
        assertFalse(result)
        assertFalse(AppLockerManager.isAppLocked("com.arworahyper.toolkit"))
        assertFalse(AppLockerManager.isAppLocked("com.arwora"))

        // Service isSelfPackage helper
        assertTrue(com.arwora.service.AppLockerService.isSelfPackage("com.arworahyper.toolkit", context))
        assertTrue(com.arwora.service.AppLockerService.isSelfPackage("com.arwora", context))
        assertFalse(com.arwora.service.AppLockerService.isSelfPackage("com.google.android.apps.photos", context))
    }

    @Test
    fun testExternalAppLockingAndForegroundSession() {
        AppLockerManager.setPin(context, "5555")
        AppLockerManager.setLockerEnabled(context, true)
        AppLockerManager.setTimeoutMinutes(context, 0) // Immediate timeout

        val albumsPackage = "com.google.android.apps.photos"
        val willLock = AppLockerManager.toggleAppLock(context, albumsPackage)
        assertTrue(willLock)
        assertTrue(AppLockerManager.isAppLocked(albumsPackage))

        // Initially locked
        assertFalse(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Session unlock
        AppLockerManager.unlockSession(albumsPackage)
        assertTrue(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Transitioning to self-package (Toolkit) does NOT revoke immediate session
        AppLockerManager.onForegroundPackageChanged("com.arworahyper.toolkit")
        assertTrue(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Transitioning away to another app revokes immediate session
        AppLockerManager.onForegroundPackageChanged("com.android.calculator2")
        assertFalse(AppLockerManager.isPackageUnlocked(albumsPackage))
    }
}
