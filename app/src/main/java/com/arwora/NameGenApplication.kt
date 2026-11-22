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

class NameGenApplication : Application() {
    
    lateinit var database: AppDatabase
        private set
        
    lateinit var historyRepository: HistoryRepository
        private set
        
    lateinit var favoriteRepository: FavoriteRepository
        private set
        
    lateinit var settingsRepository: SettingsRepository
        private set
        
    lateinit var savedTextRepository: SavedTextRepository
        private set

    lateinit var downloadHistoryRepository: com.arwora.data.repository.DownloadHistoryRepository
        private set

    lateinit var notificationRepository: com.arwora.data.repository.NotificationRepository
        private set

    lateinit var sharedViewModel: com.arwora.ui.MainViewModel
        private set

    val appManagerViewModel: com.arwora.ui.AppManagerViewModel by lazy {
        com.arwora.ui.AppManagerViewModel(this, database.appHistoryDao())
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CrashGuard", "Caught uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            // If the crash is on a non-main worker/background thread or IO dispatch, log and prevent hard app termination
            if (thread.name != "main") {
                android.util.Log.w("CrashGuard", "Suppressed background thread crash to keep app alive")
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
        
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "namegen_database"
        ).fallbackToDestructiveMigration().build()
        
        historyRepository = HistoryRepository(database.historyDao())
        favoriteRepository = FavoriteRepository(database.favoriteDao())
        savedTextRepository = SavedTextRepository(database.savedTextDao())
        settingsRepository = SettingsRepository(dataStore)
        downloadHistoryRepository = com.arwora.data.repository.DownloadHistoryRepository(database.downloadHistoryDao())
        notificationRepository = com.arwora.data.repository.NotificationRepository(database.notificationDao())
        sharedViewModel = com.arwora.ui.MainViewModel(historyRepository, favoriteRepository, settingsRepository, savedTextRepository)

        // Defer heavy extraction engines (yt-dlp/FFmpeg), audio synthesis, and Wi-Fi monitor off cold-start main thread
        StartupDefer.schedulePostFirstFrame(this) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.arwora.utils.MediaDownloadUtils.saveAppId(this@NameGenApplication)
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this@NameGenApplication)
                    com.yausername.ffmpeg.FFmpeg.getInstance().init(this@NameGenApplication)
                } catch (e: Exception) {
                    android.util.Log.e("NameGenApplication", "Failed to initialize YoutubeDL engine", e)
                }
            }
            com.arwora.utils.SoundEffectManager.init(this@NameGenApplication)
            com.arwora.feature.system.battery.service.BatteryMonitorManager.init(this@NameGenApplication)
            com.arwora.utils.NativeAdbManager.init(this@NameGenApplication)
            com.arwora.utils.ShizukuBridge.init(this@NameGenApplication, autoRequestIfNeeded = false)
            com.arwora.utils.AdbShellExecutor.init(this@NameGenApplication)
            com.arwora.utils.WirelessPairingManager.init(this@NameGenApplication)
            com.arwora.service.WifiAutoPairMonitor.syncWithPreference(this@NameGenApplication)
            val adbCreds = com.arwora.utils.EncryptedAdbCredentialStore.loadState(this@NameGenApplication)
            if (adbCreds.isPaired && !adbCreds.userStopped && !com.arwora.utils.NativeAdbManager.isAdbReady()) {
                com.arwora.service.AdbConnectionService.startPersistentSession(this@NameGenApplication)
            }
        }
    }
}
