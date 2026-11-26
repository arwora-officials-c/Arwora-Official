package com.arwora.feature.home.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

interface WeatherHttpClient {
    suspend fun get(url: String): String
}

interface WeatherLocationResolver {
    fun hasLocationPermission(): Boolean
    suspend fun resolveDeviceCoordinates(): WeatherCoordinates?
}

interface WeatherCacheStore {
    suspend fun load(): WeatherSnapshot?
    suspend fun save(snapshot: WeatherSnapshot)
}

class OkHttpWeatherHttpClient(
    private val client: OkHttpClient = sharedClient
) : WeatherHttpClient {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ArworaHyperToolkit/1.0")
            .get()
            .build()

        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                runCatching { call.cancel() }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("HTTP ${res.code}"))
                            }
                            return
                        }
                        val body = res.body?.string()
                        if (body.isNullOrBlank()) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("Empty weather response"))
                            }
                            return
                        }
                        if (continuation.isActive) {
                            continuation.resume(body)
                        }
                    }
                }
            })
        }
    }

    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}

class AndroidWeatherLocationResolver(
    private val context: Context
) : WeatherLocationResolver {

    override fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    override suspend fun resolveDeviceCoordinates(): WeatherCoordinates? {
        if (!hasLocationPermission()) return null

        val location = fetchBestAvailableLocation() ?: return null
        val locality = resolveLocalityName(location.latitude, location.longitude)
            ?: "Current Location"

        return WeatherCoordinates(
            latitude = location.latitude,
            longitude = location.longitude,
            localityName = locality,
            source = WeatherLocationSource.GPS
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBestAvailableLocation(): Location? = withContext(Dispatchers.IO) {
        val fusedResult = withTimeoutOrNull(3_000L) {
            runCatching {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                val current = suspendCancellableCoroutine<Location?> { cont ->
                    cont.invokeOnCancellation { cts.cancel() }
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
                if (current != null) return@runCatching current

                suspendCancellableCoroutine<Location?> { cont ->
                    fusedClient.lastLocation
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }.getOrNull()
        }
        if (fusedResult != null) return@withContext fusedResult

        // Fallback to framework LocationManager (works without Play Services / in emulators)
        runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@runCatching null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers
                .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    private suspend fun resolveLocalityName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        withTimeoutOrNull(2_500L) {
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
                address?.locality
                    ?: address?.subAdminArea
                    ?: address?.adminArea
            }.getOrNull()
        }
    }
}

class PrefsWeatherCacheStore(
    private val context: Context
) : WeatherCacheStore {

    override suspend fun load(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_LAST_SNAPSHOT, null) ?: return@runCatching null
            val obj = JSONObject(raw)
            val code = obj.getInt("weatherCode")
            val condition = WmoWeatherMapper.map(code)
            WeatherSnapshot(
                temperatureCelsius = obj.getInt("temperatureCelsius"),
                highTempCelsius = obj.getInt("highTempCelsius"),
                lowTempCelsius = obj.getInt("lowTempCelsius"),
                humidityPercent = obj.getInt("humidityPercent"),
                windSpeedKmh = obj.getInt("windSpeedKmh"),
                weatherCode = code,
                conditionLabel = obj.optString("conditionLabel", condition.label),
                conditionType = runCatching {
                    WeatherConditionType.valueOf(obj.getString("conditionType"))
                }.getOrDefault(condition.type),
                isDay = obj.optBoolean("isDay", true),
                localityName = obj.getString("localityName"),
                locationSource = runCatching {
                    WeatherLocationSource.valueOf(obj.getString("locationSource"))
                }.getOrDefault(WeatherLocationSource.DEFAULT_CITY),
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                fetchedAtMillis = obj.getLong("fetchedAtMillis")
            )
        }.getOrNull()
    }

    override suspend fun save(snapshot: WeatherSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("temperatureCelsius", snapshot.temperatureCelsius)
                put("highTempCelsius", snapshot.highTempCelsius)
                put("lowTempCelsius", snapshot.lowTempCelsius)
                put("humidityPercent", snapshot.humidityPercent)
                put("windSpeedKmh", snapshot.windSpeedKmh)
                put("weatherCode", snapshot.weatherCode)
                put("conditionLabel", snapshot.conditionLabel)
                put("conditionType", snapshot.conditionType.name)
                put("isDay", snapshot.isDay)
                put("localityName", snapshot.localityName)
                put("locationSource", snapshot.locationSource.name)
                put("latitude", snapshot.latitude)
                put("longitude", snapshot.longitude)
                put("fetchedAtMillis", snapshot.fetchedAtMillis)
            }.toString()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SNAPSHOT, json)
                .apply()
        }
        Unit
    }

    companion object {
        private const val PREFS_NAME = "arwora_weather_cache"
        private const val KEY_LAST_SNAPSHOT = "last_weather_snapshot"
    }
}

class WeatherRepository(
    private val context: Context,
    private val httpClient: WeatherHttpClient = OkHttpWeatherHttpClient(),
    private val locationResolver: WeatherLocationResolver = AndroidWeatherLocationResolver(context),
    private val cacheStore: WeatherCacheStore = PrefsWeatherCacheStore(context),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun hasLocationPermission(): Boolean = locationResolver.hasLocationPermission()

    suspend fun getCachedWeather(): WeatherSnapshot? = cacheStore.load()

    fun isCacheFresh(snapshot: WeatherSnapshot): Boolean =
        (clock() - snapshot.fetchedAtMillis) < CACHE_TTL_MILLIS

    suspend fun fetchLiveWeather(): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val coords = resolveCoordinatesWithFallbacks()
            val url = buildOpenMeteoUrl(coords.latitude, coords.longitude)
            val body = httpClient.get(url)
            val snapshot = parseOpenMeteoResponse(body, coords, clock())
            cacheStore.save(snapshot)
            snapshot
        }.onFailure { e ->
            Log.w(TAG, "Failed to fetch live weather: ${e.message}")
        }
    }

    private suspend fun resolveCoordinatesWithFallbacks(): WeatherCoordinates {
        val gpsCoords = runCatching { locationResolver.resolveDeviceCoordinates() }.getOrNull()
        if (gpsCoords != null) {
            return gpsCoords
        }

        val ipCoords = runCatching { fetchIpLocationFallback() }.getOrNull()
        if (ipCoords != null) {
            return ipCoords
        }

        return DEFAULT_CITY_COORDINATES
    }

    private suspend fun fetchIpLocationFallback(): WeatherCoordinates? {
        val jsonStr = httpClient.get(IP_LOOKUP_URL)
        val obj = JSONObject(jsonStr)
        if (!obj.optBoolean("success", true)) return null
        val lat = obj.optDouble("latitude", Double.NaN)
        val lon = obj.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val city = obj.optString("city").takeIf { it.isNotBlank() }
            ?: obj.optString("region").takeIf { it.isNotBlank() }
            ?: obj.optString("country").takeIf { it.isNotBlank() }
            ?: DEFAULT_CITY_COORDINATES.localityName
        return WeatherCoordinates(
            latitude = lat,
            longitude = lon,
            localityName = city,
            source = WeatherLocationSource.IP_LOOKUP
        )
    }

    companion object {
        private const val TAG = "WeatherRepository"
        const val CACHE_TTL_MILLIS = 15 * 60 * 1000L
        private const val IP_LOOKUP_URL = "https://ipwho.is/"

        val DEFAULT_CITY_COORDINATES = WeatherCoordinates(
            latitude = 25.0658,
            longitude = 91.3950,
            localityName = "Sunamganj",
            source = WeatherLocationSource.DEFAULT_CITY
        )

        fun buildOpenMeteoUrl(latitude: Double, longitude: Double): String =
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                latitude,
                longitude
            )

        fun parseOpenMeteoResponse(
            jsonBody: String,
            coords: WeatherCoordinates,
            fetchedAtMillis: Long
        ): WeatherSnapshot {
            val root = JSONObject(jsonBody)
            val current = root.getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val humidity = current.getInt("relative_humidity_2m").coerceIn(0, 100)
            val wind = current.getDouble("wind_speed_10m").roundToInt().coerceAtLeast(0)
            val code = current.getInt("weather_code")
            val isDay = current.optInt("is_day", 1) == 1

            val daily = root.optJSONObject("daily")
            val maxArr = daily?.optJSONArray("temperature_2m_max")
            val minArr = daily?.optJSONArray("temperature_2m_min")
            val high = if (maxArr != null && maxArr.length() > 0) maxArr.getDouble(0).roundToInt() else temp
            val low = if (minArr != null && minArr.length() > 0) minArr.getDouble(0).roundToInt() else temp

            val condition = WmoWeatherMapper.map(code)
            return WeatherSnapshot(
                temperatureCelsius = temp,
                highTempCelsius = high,
                lowTempCelsius = low,
                humidityPercent = humidity,
                windSpeedKmh = wind,
                weatherCode = code,
                conditionLabel = condition.label,
                conditionType = condition.type,
                isDay = isDay,
                localityName = coords.localityName,
                locationSource = coords.source,
                latitude = coords.latitude,
                longitude = coords.longitude,
                fetchedAtMillis = fetchedAtMillis
            )
        }
    }
}
