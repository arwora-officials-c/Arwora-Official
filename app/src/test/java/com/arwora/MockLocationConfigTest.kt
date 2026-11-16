package com.arwora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.arwora.utils.FakeGpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MockLocationConfigTest {

    @Test
    fun manifestDeclaresMockAndLocationPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(
            "Manifest must declare ACCESS_MOCK_LOCATION so app appears in Developer Options",
            permissions.contains("android.permission.ACCESS_MOCK_LOCATION")
        )
        assertTrue(
            "Manifest must declare ACCESS_FINE_LOCATION",
            permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
        )
        assertTrue(
            "Manifest must declare ACCESS_COARSE_LOCATION",
            permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    @Test
    fun validateCoordinatesChecksBoundsAndFiniteValues() {
        assertTrue(FakeGpsManager.validateCoordinates(35.6586, 139.7454))
        assertTrue(FakeGpsManager.validateCoordinates(-90.0, -180.0))
        assertTrue(FakeGpsManager.validateCoordinates(90.0, 180.0))
        assertFalse(FakeGpsManager.validateCoordinates(90.1, 0.0))
        assertFalse(FakeGpsManager.validateCoordinates(-90.1, 0.0))
        assertFalse(FakeGpsManager.validateCoordinates(0.0, 180.1))
        assertFalse(FakeGpsManager.validateCoordinates(Double.NaN, 10.0))
        assertFalse(FakeGpsManager.validateCoordinates(10.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun startMockLocationRegistersTestProviderAndStopsCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FakeGpsManager.stopMockLocation(context)
        FakeGpsManager.clearError()

        val started = FakeGpsManager.startMockLocation(context, 40.7580, -73.9855)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (started) {
            assertTrue(FakeGpsManager.isMockingActive.value)
            assertTrue(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            FakeGpsManager.stopMockLocation(context)
            assertFalse(FakeGpsManager.isMockingActive.value)
        } else {
            assertFalse(FakeGpsManager.isMockingActive.value)
            assertNotNull(FakeGpsManager.lastError.value)
            assertTrue(
                FakeGpsManager.lastError.value!!.contains("Developer Options", ignoreCase = true)
            )
        }
        // Keep Shadows referenced cleanly if needed in future SDK matrix tests
        assertNotNull(Shadows.shadowOf(lm))
    }
}
