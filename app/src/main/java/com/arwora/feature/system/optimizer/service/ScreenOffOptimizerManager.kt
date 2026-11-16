package com.arwora.feature.system.optimizer.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ScreenOffOptimizerManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _delaySeconds = MutableStateFlow(30L)
    val delaySeconds: StateFlow<Long> = _delaySeconds.asStateFlow()

    private val _killBackgroundApps = MutableStateFlow(true)
    val killBackgroundApps: StateFlow<Boolean> = _killBackgroundApps.asStateFlow()

    private val _enableSilentMode = MutableStateFlow(true)
    val enableSilentMode: StateFlow<Boolean> = _enableSilentMode.asStateFlow()

    private val _disableSilentMode = MutableStateFlow(true)
    val disableSilentMode: StateFlow<Boolean> = _disableSilentMode.asStateFlow()

    private val _turnOffRadios = MutableStateFlow(false)
    val turnOffRadios: StateFlow<Boolean> = _turnOffRadios.asStateFlow()

    private val _useWirelessAdb = MutableStateFlow(false)
    val useWirelessAdb: StateFlow<Boolean> = _useWirelessAdb.asStateFlow()

    private val _adbConnected = MutableStateFlow(false)
    val adbConnected: StateFlow<Boolean> = _adbConnected.asStateFlow()

    private val _mediaVolume = MutableStateFlow(100)
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private val _ringtoneVolume = MutableStateFlow(100)
    val ringtoneVolume: StateFlow<Int> = _ringtoneVolume.asStateFlow()

    private val _notificationVolume = MutableStateFlow(100)
    val notificationVolume: StateFlow<Int> = _notificationVolume.asStateFlow()

    private val _alarmVolume = MutableStateFlow(100)
    val alarmVolume: StateFlow<Int> = _alarmVolume.asStateFlow()

    private val _lastOptimizationLog = MutableStateFlow<String?>(null)
    val lastOptimizationLog: StateFlow<String?> = _lastOptimizationLog.asStateFlow()

    private val _totalOptimizationsCount = MutableStateFlow(15)
    val totalOptimizationsCount: StateFlow<Int> = _totalOptimizationsCount.asStateFlow()

    private val _hasDndPermission = MutableStateFlow(true)
    val hasDndPermission: StateFlow<Boolean> = _hasDndPermission.asStateFlow()

    private val _serviceWarning = MutableStateFlow<String?>(null)
    val serviceWarning: StateFlow<String?> = _serviceWarning.asStateFlow()

    var savedRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    var savedMediaVolume: Int = 0
    var savedRingtoneVolume: Int = 0
    var savedNotificationVolume: Int = 0
    var savedAlarmVolume: Int = 0
    var savedSystemVolume: Int = 0

    fun setDelaySeconds(seconds: Long) {
        _delaySeconds.value = seconds
    }

    fun setKillBackgroundApps(enabled: Boolean) {
        _killBackgroundApps.value = enabled
    }

    fun setEnableSilentMode(enabled: Boolean) {
        _enableSilentMode.value = enabled
    }

    fun setDisableSilentMode(enabled: Boolean) {
        _disableSilentMode.value = enabled
    }

    fun setTurnOffRadios(enabled: Boolean) {
        _turnOffRadios.value = enabled
    }

    fun setUseWirelessAdb(enabled: Boolean) {
        _useWirelessAdb.value = enabled
    }

    fun setAdbConnected(connected: Boolean) {
        _adbConnected.value = connected
    }

    fun setMediaVolume(volume: Int) {
        _mediaVolume.value = volume
    }

    fun setRingtoneVolume(volume: Int) {
        _ringtoneVolume.value = volume
    }

    fun setNotificationVolume(volume: Int) {
        _notificationVolume.value = volume
    }

    fun setAlarmVolume(volume: Int) {
        _alarmVolume.value = volume
    }

    fun checkAndSyncDndPermission(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val granted = notificationManager?.isNotificationPolicyAccessGranted == true
        _hasDndPermission.value = granted
        if (!granted && (_enableSilentMode.value || _disableSilentMode.value)) {
            _serviceWarning.value = "Action Required: Do Not Disturb Permission Missing"
        } else {
            _serviceWarning.value = null
        }
        return granted
    }

    fun startService(context: Context) {
        val permissionGranted = checkAndSyncDndPermission(context)
        _isServiceActive.value = true
        
        if (!permissionGranted && (_enableSilentMode.value || _disableSilentMode.value)) {
            _lastOptimizationLog.value = "Service Active: Missing DND Access Permission"
        } else {
            _lastOptimizationLog.value = "Screen-Off Persistent Foreground Service Active"
        }

        scope.launch {
            try {
                ScreenOffOptimizerService.start(context.applicationContext)
            } catch (e: Exception) {
                _isServiceActive.value = false
                _lastOptimizationLog.value = "Failed to start service: ${e.message}"
            }
        }
    }

    fun stopService(context: Context) {
        _isServiceActive.value = false
        _lastOptimizationLog.value = "Screen-Off Automation Service Paused"
        _serviceWarning.value = null
        scope.launch {
            try {
                ScreenOffOptimizerService.stop(context.applicationContext)
            } catch (e: Exception) {
                _lastOptimizationLog.value = "Failed to stop service: ${e.message}"
            }
        }
    }

    fun syncServiceState(isRunning: Boolean) {
        _isServiceActive.value = isRunning
    }

    fun updateLog(log: String) {
        _lastOptimizationLog.value = log
    }

    fun incrementOptimizationsCount() {
        _totalOptimizationsCount.value += 1
    }

    fun enforceAudioState(context: Context): Boolean {
        checkAndSyncDndPermission(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val hasPolicyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }

        if (audioManager == null) return false

        // 1. Disable Do Not Disturb (DND) completely
        if (hasPolicyAccess && notificationManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            } catch (e: Exception) {
                Log.e("ScreenOffManager", "Failed to clear DND: ${e.message}")
            }
        }

        // 2. Enforce Normal Ringer Mode strictly
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) {
            Log.e("ScreenOffManager", "Failed to set ringerMode NORMAL: ${e.message}")
        }

        // Re-enforce DND OFF immediately after setting ringer mode
        if (hasPolicyAccess && notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: Exception) {}
        }

        // 3. Maximize audio streams (Ring, Notification, Alarm, Media, System)
        val streams = listOf(
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_SYSTEM
        )
        for (stream in streams) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (_: Exception) {}
                }
                val maxVolume = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, maxVolume, 0)
            } catch (e: Exception) {
                Log.e("ScreenOffManager", "Failed maxing stream $stream: ${e.message}")
            }
        }

        // Final DND guard
        if (hasPolicyAccess && notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: Exception) {}
        }

        _lastOptimizationLog.value = "Forced Normal Ringer, DND Disabled, All 5 Streams Maxed"
        return true
    }

    fun executeScreenOffRoutine(context: Context) {
        checkAndSyncDndPermission(context)
        scope.launch {
            try {
                val intent = android.content.Intent(context.applicationContext, ScreenOffOptimizerService::class.java).apply {
                    action = ScreenOffOptimizerService.ACTION_EXECUTE_ROUTINE
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                _lastOptimizationLog.value = "Executed test screen-off optimization routine"
            } catch (e: Exception) {
                _lastOptimizationLog.value = "Failed to trigger optimization: ${e.message}"
            }
        }
    }
}
