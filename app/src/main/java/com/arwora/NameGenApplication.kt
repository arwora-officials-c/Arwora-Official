package com.arwora

import android.app.Application
import android.os.StrictMode
import androidx.room.Room
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.feature.common.startup.StartupDefer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NameGenApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmupStarted = AtomicBoolean(false)

    companion object {
        private val deferredEnginesScheduled = AtomicBoolean(false)
    }

    val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "namegen_database"
        ).fallbackToDestructiveMigration().build()
    }

    val historyRepository: HistoryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HistoryRepository(database.historyDao())
    }

    val favoriteRepository: FavoriteRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FavoriteRepository(database.favoriteDao())
    }

    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(dataStore)
    }

    val savedTextRepository: SavedTextRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SavedTextRepository(database.savedTextDao())
    }

    val downloadHistoryRepository: com.arwora.data.repository.DownloadHistoryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.arwora.data.repository.DownloadHistoryRepository(database.downloadHistoryDao())
    }

    val notificationRepository: com.arwora.data.repository.NotificationRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.arwora.data.repository.NotificationRepository(database.notificationDao())
    }

    val sharedViewModel: com.arwora.ui.MainViewModel by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.arwora.ui.MainViewModel(historyRepository, favoriteRepository, settingsRepository, savedTextRepository)
    }

    val appManagerViewModel: com.arwora.ui.AppManagerViewModel by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        com.arwora.ui.AppManagerViewModel(this, database.appHistoryDao())
    }

    fun warmupAsync() {
        if (!warmupStarted.compareAndSet(false, true)) return
        appScope.launch {
            runCatching { settingsRepository.prewarm() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CrashGuard", "Caught uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            // Always propagate fatal JVM Errors or main-thread crashes; only isolate recoverable background worker exceptions
            if (throwable !is Error && thread.name != "main") {
                android.util.Log.w("CrashGuard", "Suppressed recoverable background thread exception to keep app alive")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }

        // Defer heavy extraction engines (yt-dlp/FFmpeg), audio synthesis, and connectivity/ADB bridges off cold-start main thread
        if (deferredEnginesScheduled.compareAndSet(false, true)) {
            StartupDefer.schedulePostFirstFrame(this) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val appCtx = applicationContext
                    try {
                        com.arwora.utils.MediaDownloadUtils.saveAppId(appCtx)
                        com.yausername.youtubedl_android.YoutubeDL.getInstance().init(appCtx)
                        com.yausername.ffmpeg.FFmpeg.getInstance().init(appCtx)
                    } catch (e: Exception) {
                        android.util.Log.e("NameGenApplication", "Failed to initialize YoutubeDL engine", e)
                    }
                    runCatching { com.arwora.utils.SoundEffectManager.init(appCtx) }
                    runCatching { com.arwora.feature.system.battery.service.BatteryMonitorManager.init(appCtx) }
                    runCatching { com.arwora.utils.NativeAdbManager.init(appCtx) }
                    runCatching { com.arwora.utils.ShizukuBridge.init(appCtx, autoRequestIfNeeded = false) }
                    runCatching { com.arwora.utils.AdbShellExecutor.init(appCtx) }
                    runCatching { com.arwora.utils.WirelessPairingManager.init(appCtx) }
                    runCatching { com.arwora.service.WifiAutoPairMonitor.syncWithPreference(appCtx) }
                    runCatching {
                        val adbCreds = com.arwora.utils.EncryptedAdbCredentialStore.loadState(appCtx)
                        if (adbCreds.isPaired && !adbCreds.userStopped && !com.arwora.utils.NativeAdbManager.isAdbReady()) {
                            com.arwora.service.AdbConnectionService.startPersistentSession(appCtx)
                        }
                    }
                }
            }
        }
    }

    override fun onTerminate() {
        appScope.coroutineContext.cancelChildren()
        super.onTerminate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            com.arwora.ui.AppManagerViewModel.clearMemoryCache()
        }
    }
}
