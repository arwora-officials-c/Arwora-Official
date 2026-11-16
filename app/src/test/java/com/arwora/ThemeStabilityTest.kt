package com.arwora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.bottomNavItems
import com.arwora.ui.components.indexOfMainTabRoute
import com.arwora.ui.navigation.Screen
import com.arwora.ui.theme.ThemeManager
import com.arwora.ui.theme.ThemeMode
import android.os.Looper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeStabilityTest {

    private fun awaitMainCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for theme state update", predicate())
    }

    @Test
    fun `theme manager resolves all theme modes deterministically`() {
        val systemDark = ThemeManager.getThemeConfig(darkModeState = null, whiteThemeState = false, isSystemDark = true)
        assertEquals(ThemeMode.SYSTEM, systemDark.mode)
        assertTrue(systemDark.isDark)
        assertFalse(systemDark.isLightStatusBar)

        val systemLight = ThemeManager.getThemeConfig(darkModeState = null, whiteThemeState = false, isSystemDark = false)
        assertEquals(ThemeMode.SYSTEM, systemLight.mode)
        assertFalse(systemLight.isDark)
        assertTrue(systemLight.isLightStatusBar)

        val dark = ThemeManager.getThemeConfig(darkModeState = true, whiteThemeState = false, isSystemDark = false)
        assertEquals(ThemeMode.DARK, dark.mode)
        assertTrue(dark.isDark)

        val light = ThemeManager.getThemeConfig(darkModeState = false, whiteThemeState = false, isSystemDark = true)
        assertEquals(ThemeMode.LIGHT, light.mode)
        assertFalse(light.isDark)

        val white = ThemeManager.getThemeConfig(darkModeState = true, whiteThemeState = true, isSystemDark = true)
        assertEquals(ThemeMode.WHITE, white.mode)
        assertFalse(white.isDark)
        assertTrue(white.isLightStatusBar)
    }

    @Test
    fun `bottom navigation tabs are unique and mapped consistently`() {
        assertEquals(5, bottomNavItems.size)
        assertEquals(listOf("Home", "Generate", "Social", "System", "Settings"), bottomNavItems.map { it.title })
        assertEquals(bottomNavItems.size, bottomNavItems.map { it.route }.distinct().size)
        assertEquals(0, indexOfMainTabRoute(Screen.Home.route))
        assertEquals(1, indexOfMainTabRoute(Screen.GenerateDashboard.route))
        assertEquals(2, indexOfMainTabRoute(Screen.Social.route))
        assertEquals(3, indexOfMainTabRoute(Screen.SystemDashboard.route))
        assertEquals(4, indexOfMainTabRoute(Screen.Settings.route))
        assertEquals(-1, indexOfMainTabRoute("non_existent_route"))
    }

    @Test
    fun `live accent preview updates theme colors without persisting until confirmed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val setRepo = SettingsRepository(context.dataStore)
        val viewModel = MainViewModel(
            HistoryRepository(db.historyDao()),
            FavoriteRepository(db.favoriteDao()),
            setRepo,
            SavedTextRepository(db.savedTextDao())
        )

        val initialSaved = runBlocking { setRepo.uiColorFlow.first() }
        val previewColor = 0xFF00C853L

        viewModel.setLiveAccentPreview(previewColor)
        awaitMainCondition { viewModel.uiColor.value == previewColor && viewModel.buttonColor.value == previewColor }
        // Disk DataStore value stays untouched during live drag preview
        assertEquals(initialSaved, runBlocking { setRepo.uiColorFlow.first() })

        // Dismiss clears preview cleanly
        viewModel.setLiveAccentPreview(null)
        awaitMainCondition { viewModel.uiColor.value == initialSaved }

        // Confirm atomically persists both accent keys
        val confirmedColor = 0xFF1E88E5L
        viewModel.setAccentColors(confirmedColor, confirmedColor)
        awaitMainCondition {
            runBlocking {
                setRepo.uiColorFlow.first() == confirmedColor &&
                    setRepo.buttonColorFlow.first() == confirmedColor
            }
        }

        viewModel.resetColors()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }
}
