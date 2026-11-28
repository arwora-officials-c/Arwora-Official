package com.arwora.feature.home.weather

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure JSON parsers and serializers for Pro Weather forecasts, Air Quality,
 * Historical Archive ("On This Day"), RainViewer Radar, and Geocoding results.
 */
object WeatherProParser {

    fun parseProWeatherJson(
        forecastBody: String,
        coords: WeatherCoordinates,
        fetchedAtMillis: Long,
        airQuality: AirQualitySnapshot? = null,
        lastYearHistory: HistoricalWeatherDay? = null,
        rainViewerRadarPath: String? = null,
        savedCities: List<SavedWeatherCity> = emptyList(),
        activeCityId: String = ProWeatherBundle.CURRENT_LOCATION_CITY_ID
    ): ProWeatherBundle {
        val root = JSONObject(forecastBody)
        val snapshot = WeatherRepository.parseOpenMeteoResponse(forecastBody, coords, fetchedAtMillis)
        val hourlyPoints = parseHourlyList(root.optJSONObject("hourly"))
        val dailyPoints = parseDailyList(root.optJSONObject("daily"))

        val enrichedCurrent = if (hourlyPoints.isNotEmpty() && snapshot.soilMoisture == 0.25) {
            snapshot
        } else snapshot

        return ProWeatherBundle(
            current = enrichedCurrent,
            hourly24 = hourlyPoints,
            daily14 = dailyPoints,
            airQuality = airQuality,
            lastYearHistory = lastYearHistory,
            savedCities = savedCities,
            activeCityId = activeCityId,
            rainViewerRadarPath = rainViewerRadarPath,
            fetchedAtMillis = fetchedAtMillis,
            isFromOfflineCache = false
        )
    }

    fun parseHourlyList(hourly: JSONObject?): List<HourlyWeatherPoint> {
        if (hourly == null) return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m")
        val feels = hourly.optJSONArray("apparent_temperature")
        val hums = hourly.optJSONArray("relative_humidity_2m")
        val rainProbs = hourly.optJSONArray("precipitation_probability")
        val precips = hourly.optJSONArray("precipitation")
        val winds = hourly.optJSONArray("wind_speed_10m")
        val uvs = hourly.optJSONArray("uv_index")
        val codes = hourly.optJSONArray("weather_code")
        val isDays = hourly.optJSONArray("is_day")

        val list = ArrayList<HourlyWeatherPoint>(24)
        val limit = minOf(times.length(), 24)
        for (i in 0 until limit) {
            val iso = times.optString(i, "")
            val hourInt = extractHourFromIso(iso)
            val code = codes?.optInt(i, 0) ?: 0
            val cond = WmoWeatherMapper.map(code)
            val temp = temps?.optDouble(i, 25.0)?.roundToInt() ?: 25
            list.add(
                HourlyWeatherPoint(
                    isoTime = iso,
                    hourLabel = formatHourLabel(hourInt, i == 0),
                    hourOfDay = hourInt,
                    temperatureCelsius = temp,
                    feelsLikeCelsius = feels?.optDouble(i, temp.toDouble())?.roundToInt() ?: temp,
                    precipitationProbabilityPercent = rainProbs?.optInt(i, 0)?.coerceIn(0, 100) ?: 0,
                    precipitationMm = precips?.optDouble(i, 0.0)?.coerceAtLeast(0.0) ?: 0.0,
                    windSpeedKmh = winds?.optDouble(i, 0.0)?.roundToInt()?.coerceAtLeast(0) ?: 0,
                    humidityPercent = hums?.optInt(i, 65)?.coerceIn(0, 100) ?: 65,
                    uvIndex = uvs?.optDouble(i, 0.0)?.coerceAtLeast(0.0) ?: 0.0,
                    weatherCode = code,
                    conditionLabel = cond.label,
                    conditionType = cond.type,
                    isDay = (isDays?.optInt(i, 1) ?: 1) == 1
                )
            )
        }
        return list
    }

    fun parseDailyList(daily: JSONObject?): List<DailyWeatherPoint> {
        if (daily == null) return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val highs = daily.optJSONArray("temperature_2m_max")
        val lows = daily.optJSONArray("temperature_2m_min")
        val rainProbs = daily.optJSONArray("precipitation_probability_max")
        val rainSums = daily.optJSONArray("precipitation_sum")
        val windMax = daily.optJSONArray("wind_speed_10m_max")
        val uvMax = daily.optJSONArray("uv_index_max")
        val codes = daily.optJSONArray("weather_code")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")

        val list = ArrayList<DailyWeatherPoint>(14)
        val limit = minOf(times.length(), 14)
        for (i in 0 until limit) {
            val isoDate = times.optString(i, "")
            val code = codes?.optInt(i, 0) ?: 0
            val cond = WmoWeatherMapper.map(code)
            list.add(
                DailyWeatherPoint(
                    isoDate = isoDate,
                    dayLabel = formatDayLabel(isoDate, i),
                    highTempCelsius = highs?.optDouble(i, 28.0)?.roundToInt() ?: 28,
                    lowTempCelsius = lows?.optDouble(i, 20.0)?.roundToInt() ?: 20,
                    precipitationProbabilityMax = rainProbs?.optInt(i, 0)?.coerceIn(0, 100) ?: 0,
                    precipitationSumMm = rainSums?.optDouble(i, 0.0)?.coerceAtLeast(0.0) ?: 0.0,
                    windSpeedMaxKmh = windMax?.optDouble(i, 0.0)?.roundToInt()?.coerceAtLeast(0) ?: 0,
                    uvIndexMax = uvMax?.optDouble(i, 0.0)?.coerceAtLeast(0.0) ?: 0.0,
                    weatherCode = code,
                    conditionLabel = cond.label,
                    conditionType = cond.type,
                    sunriseLabel = extractTimePart(sunrises?.optString(i, "06:00") ?: "06:00"),
                    sunsetLabel = extractTimePart(sunsets?.optString(i, "18:00") ?: "18:00")
                )
            )
        }
        return list
    }

    fun parseAirQualityJson(jsonBody: String): AirQualitySnapshot? = runCatching {
        val root = JSONObject(jsonBody)
        val current = root.optJSONObject("current") ?: return null
        val aqi = current.optInt("us_aqi", 40).coerceAtLeast(0)
        val pm10 = current.optDouble("pm10", 20.0).coerceAtLeast(0.0)
        val pm25 = current.optDouble("pm2_5", 10.0).coerceAtLeast(0.0)
        val label = when {
            aqi >= 150 -> "Unhealthy"
            aqi >= 100 -> "Moderate Dust"
            aqi >= 50 -> "Fair"
            else -> "Clean"
        }
        AirQualitySnapshot(usAqi = aqi, pm10 = pm10, pm25 = pm25, dustLevelLabel = label)
    }.getOrNull()

    fun parseRainViewerLatestPath(jsonBody: String): String? = runCatching {
        val root = JSONObject(jsonBody)
        val radar = root.optJSONObject("radar") ?: return null
        val past = radar.optJSONArray("past") ?: return null
        if (past.length() == 0) return null
        val latest = past.getJSONObject(past.length() - 1)
        latest.optString("path").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun parseHistoricalArchiveJson(jsonBody: String, dateLabel: String): HistoricalWeatherDay? = runCatching {
        val root = JSONObject(jsonBody)
        val daily = root.optJSONObject("daily") ?: return null
        val highs = daily.optJSONArray("temperature_2m_max") ?: return null
        val lows = daily.optJSONArray("temperature_2m_min") ?: return null
        if (highs.length() == 0 || lows.length() == 0) return null
        val code = daily.optJSONArray("weather_code")?.optInt(0, 0) ?: 0
        val rain = daily.optJSONArray("precipitation_sum")?.optDouble(0, 0.0) ?: 0.0
        val cond = WmoWeatherMapper.map(code)
        HistoricalWeatherDay(
            dateLabel = dateLabel,
            highTempCelsius = highs.getDouble(0).roundToInt(),
            lowTempCelsius = lows.getDouble(0).roundToInt(),
            precipitationSumMm = rain.coerceAtLeast(0.0),
            weatherCode = code,
            conditionLabel = cond.label,
            conditionType = cond.type
        )
    }.getOrNull()

    fun parseGeocodingResults(jsonBody: String): List<SavedWeatherCity> = runCatching {
        val root = JSONObject(jsonBody)
        val results = root.optJSONArray("results") ?: return emptyList()
        val list = ArrayList<SavedWeatherCity>(results.length())
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
            val admin = item.optString("admin1")
            val country = item.optString("country")
            val subtitle = listOf(admin, country).filter { it.isNotBlank() }.joinToString(", ")
            val lat = item.getDouble("latitude")
            val lon = item.getDouble("longitude")
            val id = item.optLong("id", (lat * 1000).roundToInt().toLong() * 31 + (lon * 1000).roundToInt()).toString()
            list.add(
                SavedWeatherCity(
                    id = id,
                    name = name,
                    subtitle = subtitle,
                    latitude = lat,
                    longitude = lon,
                    isCurrentLocation = false
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    fun serializeProBundle(bundle: ProWeatherBundle): String {
        return JSONObject().apply {
            put("current", snapshotToJson(bundle.current))
            put("hourly24", JSONArray().apply {
                bundle.hourly24.forEach { h ->
                    put(JSONObject().apply {
                        put("isoTime", h.isoTime)
                        put("hourLabel", h.hourLabel)
                        put("hourOfDay", h.hourOfDay)
                        put("temperatureCelsius", h.temperatureCelsius)
                        put("feelsLikeCelsius", h.feelsLikeCelsius)
                        put("precipitationProbabilityPercent", h.precipitationProbabilityPercent)
                        put("precipitationMm", h.precipitationMm)
                        put("windSpeedKmh", h.windSpeedKmh)
                        put("humidityPercent", h.humidityPercent)
                        put("uvIndex", h.uvIndex)
                        put("weatherCode", h.weatherCode)
                        put("conditionLabel", h.conditionLabel)
                        put("conditionType", h.conditionType.name)
                        put("isDay", h.isDay)
                    })
                }
            })
            put("daily14", JSONArray().apply {
                bundle.daily14.forEach { d ->
                    put(JSONObject().apply {
                        put("isoDate", d.isoDate)
                        put("dayLabel", d.dayLabel)
                        put("highTempCelsius", d.highTempCelsius)
                        put("lowTempCelsius", d.lowTempCelsius)
                        put("precipitationProbabilityMax", d.precipitationProbabilityMax)
                        put("precipitationSumMm", d.precipitationSumMm)
                        put("windSpeedMaxKmh", d.windSpeedMaxKmh)
                        put("uvIndexMax", d.uvIndexMax)
                        put("weatherCode", d.weatherCode)
                        put("conditionLabel", d.conditionLabel)
                        put("conditionType", d.conditionType.name)
                        put("sunriseLabel", d.sunriseLabel)
                        put("sunsetLabel", d.sunsetLabel)
                    })
                }
            })
            bundle.airQuality?.let { aq ->
                put("airQuality", JSONObject().apply {
                    put("usAqi", aq.usAqi)
                    put("pm10", aq.pm10)
                    put("pm25", aq.pm25)
                    put("dustLevelLabel", aq.dustLevelLabel)
                })
            }
            bundle.lastYearHistory?.let { hist ->
                put("lastYearHistory", JSONObject().apply {
                    put("dateLabel", hist.dateLabel)
                    put("highTempCelsius", hist.highTempCelsius)
                    put("lowTempCelsius", hist.lowTempCelsius)
                    put("precipitationSumMm", hist.precipitationSumMm)
                    put("weatherCode", hist.weatherCode)
                    put("conditionLabel", hist.conditionLabel)
                    put("conditionType", hist.conditionType.name)
                })
            }
            put("activeCityId", bundle.activeCityId)
            bundle.rainViewerRadarPath?.let { put("rainViewerRadarPath", it) }
            put("fetchedAtMillis", bundle.fetchedAtMillis)
        }.toString()
    }

    fun deserializeProBundle(
        raw: String,
        savedCities: List<SavedWeatherCity>,
        markOffline: Boolean = true
    ): ProWeatherBundle? = runCatching {
        val root = JSONObject(raw)
        val current = snapshotFromJson(root.getJSONObject("current"))
        val hourlyArr = root.optJSONArray("hourly24") ?: JSONArray()
        val hourly = ArrayList<HourlyWeatherPoint>(hourlyArr.length())
        for (i in 0 until hourlyArr.length()) {
            val o = hourlyArr.getJSONObject(i)
            val code = o.optInt("weatherCode", 0)
            hourly.add(
                HourlyWeatherPoint(
                    isoTime = o.optString("isoTime"),
                    hourLabel = o.optString("hourLabel"),
                    hourOfDay = o.optInt("hourOfDay", 12),
                    temperatureCelsius = o.optInt("temperatureCelsius", 25),
                    feelsLikeCelsius = o.optInt("feelsLikeCelsius", 25),
                    precipitationProbabilityPercent = o.optInt("precipitationProbabilityPercent", 0),
                    precipitationMm = o.optDouble("precipitationMm", 0.0),
                    windSpeedKmh = o.optInt("windSpeedKmh", 0),
                    humidityPercent = o.optInt("humidityPercent", 65),
                    uvIndex = o.optDouble("uvIndex", 0.0),
                    weatherCode = code,
                    conditionLabel = o.optString("conditionLabel", "Clear"),
                    conditionType = runCatching {
                        WeatherConditionType.valueOf(o.optString("conditionType"))
                    }.getOrDefault(WmoWeatherMapper.map(code).type),
                    isDay = o.optBoolean("isDay", true)
                )
            )
        }
        val dailyArr = root.optJSONArray("daily14") ?: JSONArray()
        val daily = ArrayList<DailyWeatherPoint>(dailyArr.length())
        for (i in 0 until dailyArr.length()) {
            val o = dailyArr.getJSONObject(i)
            val code = o.optInt("weatherCode", 0)
            daily.add(
                DailyWeatherPoint(
                    isoDate = o.optString("isoDate"),
                    dayLabel = o.optString("dayLabel"),
                    highTempCelsius = o.optInt("highTempCelsius", 28),
                    lowTempCelsius = o.optInt("lowTempCelsius", 20),
                    precipitationProbabilityMax = o.optInt("precipitationProbabilityMax", 0),
                    precipitationSumMm = o.optDouble("precipitationSumMm", 0.0),
                    windSpeedMaxKmh = o.optInt("windSpeedMaxKmh", 0),
                    uvIndexMax = o.optDouble("uvIndexMax", 0.0),
                    weatherCode = code,
                    conditionLabel = o.optString("conditionLabel", "Clear"),
                    conditionType = runCatching {
                        WeatherConditionType.valueOf(o.optString("conditionType"))
                    }.getOrDefault(WmoWeatherMapper.map(code).type),
                    sunriseLabel = o.optString("sunriseLabel", "06:00"),
                    sunsetLabel = o.optString("sunsetLabel", "18:00")
                )
            )
        }
        val aqObj = root.optJSONObject("airQuality")
        val aq = aqObj?.let {
            AirQualitySnapshot(
                usAqi = it.optInt("usAqi", 40),
                pm10 = it.optDouble("pm10", 20.0),
                pm25 = it.optDouble("pm25", 10.0),
                dustLevelLabel = it.optString("dustLevelLabel", "Clean")
            )
        }
        val histObj = root.optJSONObject("lastYearHistory")
        val hist = histObj?.let {
            val code = it.optInt("weatherCode", 0)
            HistoricalWeatherDay(
                dateLabel = it.optString("dateLabel"),
                highTempCelsius = it.optInt("highTempCelsius", 28),
                lowTempCelsius = it.optInt("lowTempCelsius", 20),
                precipitationSumMm = it.optDouble("precipitationSumMm", 0.0),
                weatherCode = code,
                conditionLabel = it.optString("conditionLabel", "Clear"),
                conditionType = runCatching {
                    WeatherConditionType.valueOf(it.optString("conditionType"))
                }.getOrDefault(WmoWeatherMapper.map(code).type)
            )
        }
        ProWeatherBundle(
            current = current,
            hourly24 = hourly,
            daily14 = daily,
            airQuality = aq,
            lastYearHistory = hist,
            savedCities = savedCities,
            activeCityId = root.optString("activeCityId", ProWeatherBundle.CURRENT_LOCATION_CITY_ID),
            rainViewerRadarPath = root.optString("rainViewerRadarPath").takeIf { it.isNotBlank() },
            fetchedAtMillis = root.optLong("fetchedAtMillis", current.fetchedAtMillis),
            isFromOfflineCache = markOffline
        )
    }.getOrNull()

    fun snapshotToJson(snapshot: WeatherSnapshot): JSONObject = JSONObject().apply {
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
        put("feelsLikeCelsius", snapshot.feelsLikeCelsius)
        put("uvIndex", snapshot.uvIndex)
        put("pressureHpa", snapshot.pressureHpa)
        put("dewPointCelsius", snapshot.dewPointCelsius)
        put("visibilityKm", snapshot.visibilityKm)
        put("windDirectionDeg", snapshot.windDirectionDeg)
        put("precipitationMm", snapshot.precipitationMm)
        put("soilMoisture", snapshot.soilMoisture)
    }

    fun snapshotFromJson(obj: JSONObject): WeatherSnapshot {
        val code = obj.getInt("weatherCode")
        val condition = WmoWeatherMapper.map(code)
        val temp = obj.getInt("temperatureCelsius")
        return WeatherSnapshot(
            temperatureCelsius = temp,
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
            fetchedAtMillis = obj.getLong("fetchedAtMillis"),
            feelsLikeCelsius = obj.optInt("feelsLikeCelsius", temp),
            uvIndex = obj.optDouble("uvIndex", 0.0),
            pressureHpa = obj.optInt("pressureHpa", 1013),
            dewPointCelsius = obj.optInt("dewPointCelsius", temp - 2),
            visibilityKm = obj.optDouble("visibilityKm", 10.0),
            windDirectionDeg = obj.optInt("windDirectionDeg", 0),
            precipitationMm = obj.optDouble("precipitationMm", 0.0),
            soilMoisture = obj.optDouble("soilMoisture", 0.25)
        )
    }

    fun formatRelativeMinutes(fetchedAtMillis: Long, nowMillis: Long): String {
        val diffMs = (nowMillis - fetchedAtMillis).coerceAtLeast(0L)
        val mins = diffMs / 60_000L
        return when {
            mins <= 0 -> "Just now"
            mins == 1L -> "Last updated 1 min ago"
            mins < 60L -> "Last updated $mins mins ago"
            else -> {
                val hrs = mins / 60L
                if (hrs == 1L) "Last updated 1 hr ago" else "Last updated $hrs hrs ago"
            }
        }
    }

    fun oneYearAgoIsoDate(nowMillis: Long): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.YEAR, -1)
        }
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(cal.timeInMillis))
        val pretty = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(cal.timeInMillis))
        return iso to pretty
    }

    private fun extractHourFromIso(iso: String): Int {
        val tIndex = iso.indexOf('T')
        if (tIndex >= 0 && tIndex + 3 <= iso.length) {
            return iso.substring(tIndex + 1, tIndex + 3).toIntOrNull() ?: 12
        }
        return 12
    }

    private fun formatHourLabel(hour24: Int, isNow: Boolean): String {
        if (isNow) return "Now"
        val suffix = if (hour24 >= 12) "PM" else "AM"
        val h12 = when (val m = hour24 % 12) {
            0 -> 12
            else -> m
        }
        return "$h12 $suffix"
    }

    private fun formatDayLabel(isoDate: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
            if (parsed != null) SimpleDateFormat("EEE, MMM d", Locale.US).format(parsed) else isoDate
        }.getOrDefault(isoDate)
    }

    private fun extractTimePart(iso: String): String {
        val idx = iso.indexOf('T')
        return if (idx >= 0 && idx + 6 <= iso.length) iso.substring(idx + 1, idx + 6) else iso.takeLast(5)
    }
}
