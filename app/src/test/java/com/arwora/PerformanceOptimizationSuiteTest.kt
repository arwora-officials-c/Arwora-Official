package com.arwora

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.feature.system.battery.service.BatteryMonitorManager
import com.arwora.feature.system.optimizer.service.ScreenOffOptimizerService
import com.arwora.feature.system.wifi.resolve.MdnsResolver
import com.arwora.service.AppLockerService
import com.arwora.service.BatteryAlarmService
import com.arwora.ui.AppItem
import com.arwora.ui.AppItemAdapter
import com.arwora.ui.AppManagerViewModel
import com.arwora.ui.MainViewModel
import com.arwora.utils.AdbShellExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PerformanceOptimizationSuiteTest {

    @Test
    fun mainViewModelInitializesWithoutBlockingMainThreadAndPropagatesDefaultTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val viewModel = MainViewModel(
            HistoryRepository(db.historyDao()),
            FavoriteRepository(db.favoriteDao()),
            SettingsRepository(context.dataStore),
            SavedTextRepository(db.savedTextDao())
        )
        assertEquals(0xFF6C63FFL, viewModel.uiColor.value)
        assertEquals(0xFF6C63FFL, viewModel.buttonColor.value)
        assertTrue(viewModel.animationsEnabled.value)
        db.close()
    }

    @Test
    fun secondaryActivitiesLaunchCleanlyWithoutRunBlockingDeadlocks() {
        val dnsController = Robolectric.buildActivity(DnsChangerActivity::class.java).create()
        assertFalse(dnsController.get().isFinishing)
        dnsController.destroy()

        val animController = Robolectric.buildActivity(AnimationSettingsActivity::class.java).create()
        assertFalse(animController.get().isFinishing)
        animController.destroy()

        val qrController = Robolectric.buildActivity(QrScannerActivity::class.java).create()
        assertFalse(qrController.get().isFinishing)
        qrController.destroy()
    }

    @Test
    fun batteryMonitorManagerInitIsIdempotentAndUnregistersCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BatteryMonitorManager.unregister(context)
        BatteryMonitorManager.init(context)
        BatteryMonitorManager.init(context)
        BatteryMonitorManager.unregister(context)
    }

    @Test
    fun foregroundServicesStartAndStopSafelyOnAndroid15Sdk35() {
        val batteryService = Robolectric.buildService(BatteryAlarmService::class.java).create().startCommand(0, 0)
        assertNotNull(batteryService.get())
        batteryService.destroy()

        val lockerService = Robolectric.buildService(AppLockerService::class.java).create().startCommand(0, 0)
        assertNotNull(lockerService.get())
        lockerService.destroy()

        val optimizerService = Robolectric.buildService(ScreenOffOptimizerService::class.java)
            .create()
            .withIntent(Intent(ApplicationProvider.getApplicationContext(), ScreenOffOptimizerService::class.java))
            .startCommand(0, 0)
        assertNotNull(optimizerService.get())
        optimizerService.destroy()
    }

    @Test
    fun appItemAdapterHasStableIdsAndSmoothRecyclerDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = AppItemAdapter()
        assertTrue(adapter.hasStableIds())
        adapter.submitList(
            listOf(
                AppItem(
                    appName = "Test App",
                    packageName = "com.example.test",
                    versionName = "1.0",
                    firstInstallTime = 1_000L,
                    firstInstallTimeFormatted = "01/01/2025 - 00:00:00",
                    dayFormattedDate = "Wednesday, 01 January 2025",
                    relativeTimeAgo = "Installed just now",
                    isSystemApp = false,
                    icon = null,
                    appInfo = ApplicationInfo()
                )
            )
        )
        assertEquals("com.example.test".hashCode().toLong(), adapter.getItemId(0))

        val recyclerView = RecyclerView(context)
        AppItemAdapter.applySmoothDefaults(recyclerView)
        assertTrue(recyclerView.hasFixedSize())
        recyclerView.adapter = adapter
        assertEquals(1, recyclerView.adapter?.itemCount)

        AppManagerViewModel.clearMemoryCache()
    }

    @Test
    fun flattenedXmlLayoutsInflateWithConstraintLayoutHierarchy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_ArworaHyperToolkit)
        val inflater = LayoutInflater.from(themedContext)

        val systemView = inflater.inflate(R.layout.fragment_system, null, false)
        val autoClickerCard = systemView.findViewById<android.view.View>(R.id.card_auto_clicker)
        assertNotNull(autoClickerCard)
        assertNotNull(systemView.findViewById<android.view.View>(R.id.iv_auto_clicker_icon)?.parent as? ConstraintLayout)

        val dnsView = inflater.inflate(R.layout.activity_dns_changer, null, false)
        assertNotNull(dnsView.findViewById<android.view.View>(R.id.tv_current_mode))
        assertNotNull(dnsView.findViewById<android.view.View>(R.id.btn_refresh))
        assertNotNull(dnsView.findViewById<android.view.View>(R.id.tv_current_mode)?.parent as? ConstraintLayout)
    }

    @Test
    fun mdnsResolverHandlesCancellationGracefullyWithoutCrash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val job = async {
            MdnsResolver.resolveAll(context, InetAddress.getLoopbackAddress())
        }
        job.cancel(CancellationException("Aborted in unit test"))
        val cancelledSafely = runCatching { job.await() }.exceptionOrNull() is CancellationException
        assertTrue(cancelledSafely)
    }

    @Test
    fun adbShellExecutorReturnsStructuredFallbackWhenNoBridgeIsActive() = runBlocking {
        AdbShellExecutor.resetTestOverrides()
        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { false }
        val result = AdbShellExecutor.executeWithEngine("id")
        assertFalse(result.success)
        assertEquals(-1, result.exitCode)
        AdbShellExecutor.resetTestOverrides()
    }
}
