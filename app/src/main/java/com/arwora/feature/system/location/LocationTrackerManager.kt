package com.arwora.feature.system.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.arwora.data.db.LocationLogEntity
import com.arwora.data.repository.LocationLogRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

object LocationTrackerManager {
    private const val TAG = "LocationTrackerManager"
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    private var lastRecordedLocation: Location? = null
    private var locationEntryTime: Long = 0L
    private const val STABILITY_RADIUS_METERS = 50f
    private const val STABILITY_DURATION_MILLIS = 5 * 60 * 1000L // 5 minutes

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context, repository: LocationLogRepository) {
        if (isTracking) return
        isTracking = true

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000L)
                .setMinUpdateIntervalMillis(30000L)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    processLocationFix(context, repository, location)
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i(TAG, "Periodic location tracking started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking: ${e.message}", e)
            isTracking = false
        }
    }

    private fun processLocationFix(context: Context, repository: LocationLogRepository, location: Location) {
        val currentTime = System.currentTimeMillis()
        if (lastRecordedLocation == null) {
            lastRecordedLocation = location
            locationEntryTime = currentTime
            return
        }

        val distance = lastRecordedLocation!!.distanceTo(location)
        if (distance <= STABILITY_RADIUS_METERS) {
            if (currentTime - locationEntryTime >= STABILITY_DURATION_MILLIS) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestampStr = dateFormat.format(Date(currentTime))

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository.insert(
                            LocationLogEntity(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = timestampStr,
                                duration = "5+ minutes",
                                address = "Lat: ${String.format(Locale.US, "%.5f", location.latitude)}, Lng: ${String.format(Locale.US, "%.5f", location.longitude)}"
                            )
                        )
                        Log.i(TAG, "Recorded stable location log (5+ minutes at same spot): ${location.latitude}, ${location.longitude}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving location log: ${e.message}")
                    }
                }
                locationEntryTime = currentTime
            }
        } else {
            lastRecordedLocation = location
            locationEntryTime = currentTime
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        try {
            locationCallback?.let {
                fusedLocationClient?.removeLocationUpdates(it)
            }
            isTracking = false
            Log.i(TAG, "Location tracking stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking: ${e.message}")
        }
    }
}
