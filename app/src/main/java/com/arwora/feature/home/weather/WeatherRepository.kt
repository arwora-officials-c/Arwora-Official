package com.arwora.feature.home.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.arwora.NameGenApplication
import com.arwora.data.db.WeatherAlertPreferencesDao
import com.arwora.data.db.WeatherAlertPreferencesEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
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
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
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
            WeatherProParser.snapshotFromJson(JSONObject(raw))
        }.getOrNull()
    }

    override suspend fun save(snapshot: WeatherSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val json = WeatherProParser.snapshotToJson(snapshot).toString()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SNAPSHOT, json)
                .apply()
            WeatherAppWidgetProvider.requestWidgetUpdate(context)
        }
        Unit
    }

    companion object {
        const val PREFS_NAME = "arwora_weather_cache"
        private const val KEY_LAST_SNAPSHOT = "last_weather_snapshot"
    }
}

class WeatherRepository(
    private val context: Context,
    private val httpClient: WeatherHttpClient = OkHttpWeatherHttpClient(),
    private val locationResolver: WeatherLocationResolver = AndroidWeatherLocationResolver(context),
    private val cacheStore: WeatherCacheStore = PrefsWeatherCacheStore(context),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val weatherAlertDao: WeatherAlertPreferencesDao? = resolveDefaultWeatherAlertDao(context)
) {
    private val prefs by lazy {
        context.getSharedPreferences(PrefsWeatherCacheStore.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasLocationPermission(): Boolean = locationResolver.hasLocationPermission()

    fun nowMillis(): Long = clock()

    suspend fun getCachedWeather(): WeatherSnapshot? = cacheStore.load()

    fun isCacheFresh(snapshot: WeatherSnapshot): Boolean =
        (clock() - snapshot.fetchedAtMillis) < CACHE_TTL_MILLIS

    suspend fun fetchLiveWeather(): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val activeCity = getActiveCitySelection()
            val coords = if (activeCity != null && !activeCity.isCurrentLocation) {
                WeatherCoordinates(
                    latitude = activeCity.latitude,
                    longitude = activeCity.longitude,
                    localityName = activeCity.name,
                    source = WeatherLocationSource.DEFAULT_CITY
                )
            } else {
                resolveCoordinatesWithFallbacks()
            }
            val url = buildOpenMeteoUrl(coords.latitude, coords.longitude)
            val body = httpClient.get(url)
            val snapshot = parseOpenMeteoResponse(body, coords, clock())
            cacheStore.save(snapshot)
            snapshot
        }.onFailure { e ->
            Log.w(TAG, "Failed to fetch live weather: ${e.message}")
        }
    }

    suspend fun loadCachedProWeather(): ProWeatherBundle? = withContext(Dispatchers.IO) {
        val cities = getSavedCities()
        val raw = prefs.getString(KEY_LAST_PRO_BUNDLE, null) ?: return@withContext null
        WeatherProParser.deserializeProBundle(raw, savedCities = cities, markOffline = true)
    }

    suspend fun fetchProWeather(cityOverride: SavedWeatherCity? = null): Result<ProWeatherBundle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetCity = cityOverride ?: getActiveCitySelection()
                val coords = if (targetCity != null && !targetCity.isCurrentLocation) {
                    WeatherCoordinates(
                        latitude = targetCity.latitude,
                        longitude = targetCity.longitude,
                        localityName = targetCity.name,
                        source = WeatherLocationSource.DEFAULT_CITY
                    )
                } else {
                    resolveCoordinatesWithFallbacks()
                }
                val activeCityId = targetCity?.id ?: ProWeatherBundle.CURRENT_LOCATION_CITY_ID
                val now = clock()
                val (archiveDateIso, archivePretty) = WeatherProParser.oneYearAgoIsoDate(now)

                coroutineScope {
                    val forecastDeferred = async {
                        httpClient.get(buildProOpenMeteoUrl(coords.latitude, coords.longitude))
                    }
                    val aqiDeferred = async {
                        runCatching {
                            val aqRaw = httpClient.get(buildAirQualityUrl(coords.latitude, coords.longitude))
                            WeatherProParser.parseAirQualityJson(aqRaw)
                        }.getOrNull()
                    }
                    val radarDeferred = async {
                        runCatching {
                            val rvRaw = httpClient.get(RAINVIEWER_MAPS_URL)
                            WeatherProParser.parseRainViewerLatestPath(rvRaw)
                        }.getOrNull()
                    }
                    val archiveDeferred = async {
                        runCatching {
                            val archRaw = httpClient.get(
                                buildHistoricalArchiveUrl(coords.latitude, coords.longitude, archiveDateIso)
                            )
                            WeatherProParser.parseHistoricalArchiveJson(archRaw, archivePretty)
                        }.getOrNull()
                    }

                    val forecastBody = forecastDeferred.await()
                    val bundle = WeatherProParser.parseProWeatherJson(
                        forecastBody = forecastBody,
                        coords = coords,
                        fetchedAtMillis = now,
                        airQuality = aqiDeferred.await(),
                        lastYearHistory = archiveDeferred.await(),
                        rainViewerRadarPath = radarDeferred.await(),
                        savedCities = getSavedCities(),
                        activeCityId = activeCityId
                    )
                    cacheStore.save(bundle.current)
                    prefs.edit()
                        .putString(KEY_LAST_PRO_BUNDLE, WeatherProParser.serializeProBundle(bundle))
                        .apply()
                    bundle
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch pro weather: ${e.message}")
            }
        }

    suspend fun searchCities(query: String): Result<List<SavedWeatherCity>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleaned = query.trim()
            if (cleaned.length < 2) return@runCatching emptyList()
            val encoded = Uri.encode(cleaned)
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json"
            val raw = httpClient.get(url)
            WeatherProParser.parseGeocodingResults(raw)
        }
    }

    fun getSavedCities(): List<SavedWeatherCity> {
        val raw = prefs.getString(KEY_SAVED_CITIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SavedWeatherCity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    subtitle = o.optString("subtitle", ""),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    isCurrentLocation = false
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveFavoriteCity(city: SavedWeatherCity): List<SavedWeatherCity> {
        val current = getSavedCities().toMutableList()
        current.removeAll { it.id == city.id || (it.name.equals(city.name, ignoreCase = true) && it.subtitle == city.subtitle) }
        current.add(0, city.copy(isCurrentLocation = false))
        val trimmed = current.take(MAX_SAVED_CITIES)
        persistSavedCities(trimmed)
        return trimmed
    }

    fun removeFavoriteCity(cityId: String): List<SavedWeatherCity> {
        val updated = getSavedCities().filterNot { it.id == cityId }
        persistSavedCities(updated)
        if (getActiveCityId() == cityId) {
            setActiveCityId(ProWeatherBundle.CURRENT_LOCATION_CITY_ID)
        }
        return updated
    }

    private fun persistSavedCities(list: List<SavedWeatherCity>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("subtitle", c.subtitle)
                put("latitude", c.latitude)
                put("longitude", c.longitude)
            })
        }
        prefs.edit().putString(KEY_SAVED_CITIES, arr.toString()).apply()
    }

    fun getActiveCityId(): String =
        prefs.getString(KEY_ACTIVE_CITY_ID, ProWeatherBundle.CURRENT_LOCATION_CITY_ID)
            ?: ProWeatherBundle.CURRENT_LOCATION_CITY_ID

    fun setActiveCityId(cityId: String) {
        prefs.edit().putString(KEY_ACTIVE_CITY_ID, cityId).apply()
    }

    fun getActiveCitySelection(): SavedWeatherCity? {
        val activeId = getActiveCityId()
        if (activeId == ProWeatherBundle.CURRENT_LOCATION_CITY_ID) return null
        return getSavedCities().firstOrNull { it.id == activeId }
    }

    fun observeAlertSettings(): Flow<SmartWeatherAlertSettings> {
        val dao = weatherAlertDao ?: return flowOf(readLegacyPrefsAlertSettings())
        return dao.observePreferences().map { entity ->
            entity?.toDomain() ?: readLegacyPrefsAlertSettings()
        }
    }

    suspend fun getAlertSettingsSuspend(): SmartWeatherAlertSettings = withContext(Dispatchers.IO) {
        val dao = weatherAlertDao
        if (dao != null) {
            val existing = runCatching { dao.getPreferences() }.getOrNull()
            if (existing != null) {
                return@withContext existing.toDomain()
            }
            val seeded = readLegacyPrefsAlertSettings()
            runCatching {
                dao.upsertPreferences(WeatherAlertPreferencesEntity.fromDomain(seeded, clock()))
            }
            return@withContext seeded
        }
        readLegacyPrefsAlertSettings()
    }

    suspend fun saveAlertSettingsSuspend(settings: SmartWeatherAlertSettings) = withContext(Dispatchers.IO) {
        persistLegacyPrefsAlertSettings(settings)
        weatherAlertDao?.let { dao ->
            runCatching {
                dao.upsertPreferences(WeatherAlertPreferencesEntity.fromDomain(settings, clock()))
            }
        }
        Unit
    }

    fun getAlertSettings(): SmartWeatherAlertSettings = readLegacyPrefsAlertSettings()

    private fun readLegacyPrefsAlertSettings(): SmartWeatherAlertSettings = SmartWeatherAlertSettings(
        brishtiAlertEnabled = prefs.getBoolean("alert_brishti", true),
        kaporShukanoAlertEnabled = prefs.getBoolean("alert_kapor", true),
        bazarAlertEnabled = prefs.getBoolean("alert_bazar", false),
        bikeRideAlertEnabled = prefs.getBoolean("alert_bike", true),
        extremeWarningEnabled = prefs.getBoolean("alert_extreme", true)
    )

    private fun persistLegacyPrefsAlertSettings(settings: SmartWeatherAlertSettings) {
        prefs.edit()
            .putBoolean("alert_brishti", settings.brishtiAlertEnabled)
            .putBoolean("alert_kapor", settings.kaporShukanoAlertEnabled)
            .putBoolean("alert_bazar", settings.bazarAlertEnabled)
            .putBoolean("alert_bike", settings.bikeRideAlertEnabled)
            .putBoolean("alert_extreme", settings.extremeWarningEnabled)
            .apply()
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
        const val MAX_SAVED_CITIES = 10
        private const val IP_LOOKUP_URL = "https://ipwho.is/"

        fun resolveDefaultWeatherAlertDao(context: Context): WeatherAlertPreferencesDao? =
            runCatching {
                (context.applicationContext as? NameGenApplication)
                    ?.database
                    ?.weatherAlertPreferencesDao()
            }.getOrNull()
        private const val RAINVIEWER_MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"
        private const val KEY_LAST_PRO_BUNDLE = "last_pro_weather_bundle"
        private const val KEY_SAVED_CITIES = "saved_weather_cities"
        private const val KEY_ACTIVE_CITY_ID = "active_weather_city_id"

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
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
                    "precipitation,is_day,weather_code,wind_speed_10m,wind_direction_10m," +
                    "uv_index,surface_pressure,visibility" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                latitude,
                longitude
            )

        fun buildProOpenMeteoUrl(latitude: Double, longitude: Double): String =
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
                    "precipitation,is_day,weather_code,wind_speed_10m,wind_direction_10m," +
                    "uv_index,surface_pressure,visibility,soil_moisture_0_to_1cm" +
                    "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m," +
                    "precipitation_probability,precipitation,weather_code,wind_speed_10m,uv_index,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
                    "precipitation_probability_max,precipitation_sum,wind_speed_10m_max,uv_index_max,sunrise,sunset" +
                    "&timezone=auto&forecast_days=14",
                latitude,
                longitude
            )

        fun buildAirQualityUrl(latitude: Double, longitude: Double): String =
            String.format(
                Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=us_aqi,pm10,pm2_5",
                latitude,
                longitude
            )

        fun buildHistoricalArchiveUrl(latitude: Double, longitude: Double, isoDate: String): String =
            String.format(
                Locale.US,
                "https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f" +
                    "&start_date=%s&end_date=%s&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto",
                latitude,
                longitude,
                isoDate,
                isoDate
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
            val feelsLike = current.optDouble("apparent_temperature", temp.toDouble()).roundToInt()
            val uvIndex = current.optDouble("uv_index", 0.0).coerceAtLeast(0.0)
            val pressure = current.optDouble("surface_pressure", 1013.0).roundToInt()
            val dewPoint = current.optDouble("dew_point_2m", (temp - 2).toDouble()).roundToInt()
            val visibilityMeters = current.optDouble("visibility", 10_000.0)
            val visibilityKm = (visibilityMeters / 1000.0).coerceAtLeast(0.1)
            val windDir = current.optInt("wind_direction_10m", 0)
            val precipMm = current.optDouble("precipitation", 0.0).coerceAtLeast(0.0)
            val soilMoisture = current.optDouble("soil_moisture_0_to_1cm", 0.25).coerceIn(0.0, 1.0)

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
                fetchedAtMillis = fetchedAtMillis,
                feelsLikeCelsius = feelsLike,
                uvIndex = uvIndex,
                pressureHpa = pressure,
                dewPointCelsius = dewPoint,
                visibilityKm = visibilityKm,
                windDirectionDeg = windDir,
                precipitationMm = precipMm,
                soilMoisture = soilMoisture
            )
        }
    }
}
