package com.arwora.feature.home.weather

import kotlin.math.roundToInt

enum class AdvisoryLevel {
    GOOD,
    MODERATE,
    CAUTION,
    DANGER
}

data class HealthComfortMeter(
    val id: String,
    val title: String,
    val levelLabel: String,
    val scorePercent: Int,
    val primaryMetric: String,
    val advice: String,
    val level: AdvisoryLevel
)

data class ActivitySuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val ratingLabel: String,
    val bestWindow: String,
    val reason: String,
    val level: AdvisoryLevel
)

data class OutfitAdvisory(
    val headline: String,
    val clothingItems: List<String>,
    val carryNote: String
)

data class FarmingAdvisory(
    val irrigationAction: String,
    val fertilizerAction: String,
    val rainNext48hMm: Double,
    val maxRainProbNext48h: Int,
    val summary: String,
    val level: AdvisoryLevel
)

data class SportsPlayAdvisory(
    val pitchDryness: String,
    val rainInterruptionRisk: String,
    val lightingCondition: String,
    val verdict: String,
    val level: AdvisoryLevel
)

enum class SmartAlertKind {
    BRISHTI,
    KAPOR_SHUKANO,
    BAZAR,
    BIKE_RIDE,
    EXTREME
}

data class TriggeredWeatherAlert(
    val kind: SmartAlertKind,
    val title: String,
    val message: String,
    val isHighPriority: Boolean
)

object WeatherAdvisoryEngine {

    fun windDirectionCompass(degrees: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((degrees % 360) + 360) % 360
        return dirs[((normalized + 22) / 45) % 8]
    }

    /**
     * Rothfusz Heat Index approximation in Celsius.
     */
    fun computeHeatIndexCelsius(tempC: Int, humidityPercent: Int): Int {
        if (tempC < 26) return tempC
        val tF = tempC * 9.0 / 5.0 + 32.0
        val r = humidityPercent.coerceIn(0, 100).toDouble()
        val hiF = -42.379 +
            2.04901523 * tF +
            10.14333127 * r -
            0.22475541 * tF * r -
            0.00683783 * tF * tF -
            0.05481717 * r * r +
            0.00122874 * tF * tF * r +
            0.00085282 * tF * r * r -
            0.00000199 * tF * tF * r * r
        return ((hiF - 32.0) * 5.0 / 9.0).roundToInt().coerceAtLeast(tempC)
    }

    fun computeHeatStrokeRisk(current: WeatherSnapshot): HealthComfortMeter {
        val hi = maxOf(current.feelsLikeCelsius, computeHeatIndexCelsius(current.temperatureCelsius, current.humidityPercent))
        return when {
            hi >= 41 -> HealthComfortMeter(
                id = "heat_stroke",
                title = "Heat-Stroke Risk Meter",
                levelLabel = "Danger",
                scorePercent = 94,
                primaryMetric = "Heat Index ${hi}°C",
                advice = "Avoid midday sun, drink ORS/electrolytes, and stay indoors in shade.",
                level = AdvisoryLevel.DANGER
            )
            hi >= 34 -> HealthComfortMeter(
                id = "heat_stroke",
                title = "Heat-Stroke Risk Meter",
                levelLabel = "High Caution",
                scorePercent = 72,
                primaryMetric = "Heat Index ${hi}°C",
                advice = "High thermal load. Carry water, take shade breaks, and avoid heavy afternoon exertion.",
                level = AdvisoryLevel.CAUTION
            )
            hi >= 29 -> HealthComfortMeter(
                id = "heat_stroke",
                title = "Heat-Stroke Risk Meter",
                levelLabel = "Moderate",
                scorePercent = 45,
                primaryMetric = "Heat Index ${hi}°C",
                advice = "Warm conditions; stay hydrated during outdoor walks or sports.",
                level = AdvisoryLevel.MODERATE
            )
            else -> HealthComfortMeter(
                id = "heat_stroke",
                title = "Heat-Stroke Risk Meter",
                levelLabel = "Low",
                scorePercent = 18,
                primaryMetric = "Heat Index ${hi}°C",
                advice = "Comfortable thermal stress. Normal hydration is sufficient.",
                level = AdvisoryLevel.GOOD
            )
        }
    }

    fun computeColdSkinDryness(current: WeatherSnapshot): HealthComfortMeter {
        val t = current.temperatureCelsius
        val h = current.humidityPercent
        val wind = current.windSpeedKmh
        val windChill = if (t <= 14 && wind >= 8) {
            (13.12 + 0.6215 * t - 11.37 * Math.pow(wind.toDouble(), 0.16) +
                0.3965 * t * Math.pow(wind.toDouble(), 0.16)).roundToInt()
        } else t

        return when {
            windChill <= 10 || h <= 28 -> HealthComfortMeter(
                id = "cold_skin",
                title = "Cold & Skin Dryness Level",
                levelLabel = "High Dryness",
                scorePercent = 82,
                primaryMetric = "Humidity $h% • Chill ${windChill}°C",
                advice = "Apply moisturizer/lip balm and wear warm layered outerwear against dry chill.",
                level = AdvisoryLevel.CAUTION
            )
            t <= 16 || h <= 42 -> HealthComfortMeter(
                id = "cold_skin",
                title = "Cold & Skin Dryness Level",
                levelLabel = "Mild Dryness",
                scorePercent = 52,
                primaryMetric = "Dew Point ${current.dewPointCelsius}°C • $h%",
                advice = "Slightly dry or cool breeze. Light lotion and a thin sweater recommended at night.",
                level = AdvisoryLevel.MODERATE
            )
            else -> HealthComfortMeter(
                id = "cold_skin",
                title = "Cold & Skin Dryness Level",
                levelLabel = "Balanced",
                scorePercent = 22,
                primaryMetric = "Dew Point ${current.dewPointCelsius}°C • $h%",
                advice = "Moisture levels support comfortable skin hydration.",
                level = AdvisoryLevel.GOOD
            )
        }
    }

    fun computeDustAllergyMeter(airQuality: AirQualitySnapshot?, windKmh: Int): HealthComfortMeter {
        val aqi = airQuality?.usAqi ?: if (windKmh > 25) 78 else 46
        val pm10 = airQuality?.pm10 ?: (if (windKmh > 25) 62.0 else 28.0)
        val pm25 = airQuality?.pm25 ?: (if (windKmh > 25) 26.0 else 12.0)

        return when {
            aqi >= 150 || pm10 >= 120 || pm25 >= 55 -> HealthComfortMeter(
                id = "dust_allergy",
                title = "Allergy / Dust (Dhulo) Meter",
                levelLabel = "Unhealthy • Wear Mask",
                scorePercent = 90,
                primaryMetric = "AQI $aqi • PM10 ${pm10.roundToInt()} µg/m³",
                advice = "Heavy dust/particulates in the air. Always wear a KN95/surgical mask outside.",
                level = AdvisoryLevel.DANGER
            )
            aqi >= 95 || pm10 >= 65 || pm25 >= 30 -> HealthComfortMeter(
                id = "dust_allergy",
                title = "Allergy / Dust (Dhulo) Meter",
                levelLabel = "Mask Advised",
                scorePercent = 66,
                primaryMetric = "AQI $aqi • PM2.5 ${pm25.roundToInt()} µg/m³",
                advice = "Moderate road dust & allergy risk for sensitive lungs. Mask recommended for biking.",
                level = AdvisoryLevel.CAUTION
            )
            else -> HealthComfortMeter(
                id = "dust_allergy",
                title = "Allergy / Dust (Dhulo) Meter",
                levelLabel = "Clean Air",
                scorePercent = 24,
                primaryMetric = "AQI $aqi • PM10 ${pm10.roundToInt()} µg/m³",
                advice = "Low dust & allergen load. No mask required for normal outdoor errands.",
                level = AdvisoryLevel.GOOD
            )
        }
    }

    fun computeSleepComfortAdvice(current: WeatherSnapshot, todayLowC: Int): HealthComfortMeter {
        val h = current.humidityPercent
        return when {
            todayLowC >= 27 || (todayLowC >= 25 && h >= 78) -> HealthComfortMeter(
                id = "sleep_comfort",
                title = "Sleep Comfort Advisor",
                levelLabel = "AC 25°C Recommended",
                scorePercent = 76,
                primaryMetric = "Night Low ${todayLowC}°C • $h% RH",
                advice = "Warm & humid night. Set AC to 25°C with light ceiling fan or sleep timer.",
                level = AdvisoryLevel.MODERATE
            )
            todayLowC in 21..26 -> HealthComfortMeter(
                id = "sleep_comfort",
                title = "Sleep Comfort Advisor",
                levelLabel = "Fan Only / Eco",
                scorePercent = 30,
                primaryMetric = "Night Low ${todayLowC}°C • $h% RH",
                advice = "Pleasant sleeping temperature. Medium fan or AC 26°C Eco saves power.",
                level = AdvisoryLevel.GOOD
            )
            else -> HealthComfortMeter(
                id = "sleep_comfort",
                title = "Sleep Comfort Advisor",
                levelLabel = "Natural Breeze / Blanket",
                scorePercent = 20,
                primaryMetric = "Night Low ${todayLowC}°C • $h% RH",
                advice = "Cool night ahead. Turn off AC, keep fan low or off, and use a light blanket.",
                level = AdvisoryLevel.GOOD
            )
        }
    }

    fun computeBestTimeActivities(
        current: WeatherSnapshot,
        hourly: List<HourlyWeatherPoint>
    ): List<ActivitySuggestion> {
        val next12 = hourly.take(12)
        val dryClearHours = next12.filter { it.precipitationProbabilityPercent < 30 && it.weatherCode < 51 }
        val coolDryHours = next12.filter { it.precipitationProbabilityPercent < 25 && it.temperatureCelsius <= 33 && it.windSpeedKmh <= 25 }

        // 1. Ghurte Jawa (Outing)
        val outingBest = coolDryHours.firstOrNull { it.hourOfDay in 15..20 } ?: dryClearHours.firstOrNull()
        val outingRainRisk = next12.maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
        val outingAdvice = when {
            current.conditionType == WeatherConditionType.THUNDERSTORM || outingRainRisk >= 75 -> ActivitySuggestion(
                id = "outing",
                title = "Ghurte Jawa (Outing)",
                subtitle = "Walks, hangouts & evening trips",
                ratingLabel = "Avoid",
                bestWindow = "Wait for rain to clear",
                reason = "High storm/rain risk ($outingRainRisk%) in the coming hours.",
                level = AdvisoryLevel.CAUTION
            )
            outingBest != null -> ActivitySuggestion(
                id = "outing",
                title = "Ghurte Jawa (Outing)",
                subtitle = "Walks, hangouts & evening trips",
                ratingLabel = "Good",
                bestWindow = "Best around ${outingBest.hourLabel}",
                reason = "Comfortable ${outingBest.temperatureCelsius}°C with low rain chance (${outingBest.precipitationProbabilityPercent}%).",
                level = AdvisoryLevel.GOOD
            )
            else -> ActivitySuggestion(
                id = "outing",
                title = "Ghurte Jawa (Outing)",
                subtitle = "Walks, hangouts & evening trips",
                ratingLabel = "Moderate",
                bestWindow = "Short trips with umbrella",
                reason = "Passing clouds or warm humidity (${current.humidityPercent}%).",
                level = AdvisoryLevel.MODERATE
            )
        }

        // 2. Kapor Dhowa (Laundry)
        val laundryWindow = next12.firstOrNull { it.precipitationProbabilityPercent < 20 && it.humidityPercent <= 75 }
        val laundryAdvice = when {
            laundryWindow != null && current.precipitationMm < 0.2 -> ActivitySuggestion(
                id = "laundry",
                title = "Kapor Dhowa (Laundry)",
                subtitle = "Sun drying & outdoor clothesline",
                ratingLabel = "Good",
                bestWindow = "Dry from ${laundryWindow.hourLabel}",
                reason = "Low rain risk (${laundryWindow.precipitationProbabilityPercent}%) helps clothes dry fast.",
                level = AdvisoryLevel.GOOD
            )
            outingRainRisk >= 60 -> ActivitySuggestion(
                id = "laundry",
                title = "Kapor Dhowa (Laundry)",
                subtitle = "Sun drying & outdoor clothesline",
                ratingLabel = "Avoid",
                bestWindow = "Dry indoors under fan",
                reason = "Rain probability peaks at $outingRainRisk% with high moisture.",
                level = AdvisoryLevel.CAUTION
            )
            else -> ActivitySuggestion(
                id = "laundry",
                title = "Kapor Dhowa (Laundry)",
                subtitle = "Sun drying & outdoor clothesline",
                ratingLabel = "Moderate",
                bestWindow = "Keep under shaded roof",
                reason = "Higher humidity (${current.humidityPercent}%) slows sun drying.",
                level = AdvisoryLevel.MODERATE
            )
        }

        // 3. Chad-e Jawa (Rooftop)
        val roofHour = next12.firstOrNull { it.hourOfDay in 16..21 && it.precipitationProbabilityPercent < 25 && it.windSpeedKmh < 30 }
        val roofAdvice = when {
            current.conditionType == WeatherConditionType.THUNDERSTORM -> ActivitySuggestion(
                id = "rooftop",
                title = "Chad-e Jawa (Rooftop)",
                subtitle = "Breeze, tea & sunset chill",
                ratingLabel = "Avoid",
                bestWindow = "Stay off open roof",
                reason = "Lightning/gusty winds detected. Safety first.",
                level = AdvisoryLevel.DANGER
            )
            roofHour != null -> ActivitySuggestion(
                id = "rooftop",
                title = "Chad-e Jawa (Rooftop)",
                subtitle = "Breeze, tea & sunset chill",
                ratingLabel = "Good",
                bestWindow = "Ideal at ${roofHour.hourLabel}",
                reason = "Gentle ${roofHour.windSpeedKmh} km/h breeze at ${roofHour.temperatureCelsius}°C.",
                level = AdvisoryLevel.GOOD
            )
            else -> ActivitySuggestion(
                id = "rooftop",
                title = "Chad-e Jawa (Rooftop)",
                subtitle = "Breeze, tea & sunset chill",
                ratingLabel = "Moderate",
                bestWindow = "Late evening",
                reason = "Warm afternoon UV (${current.uvIndex.roundToInt()}) or occasional drizzle risk.",
                level = AdvisoryLevel.MODERATE
            )
        }

        // 4. Khela (Outdoor Play)
        val playHour = next12.firstOrNull { it.hourOfDay in 15..18 && it.precipitationProbabilityPercent < 30 && it.temperatureCelsius <= 35 }
            ?: dryClearHours.firstOrNull()
        val playAdvice = when {
            current.conditionType == WeatherConditionType.RAIN || current.conditionType == WeatherConditionType.THUNDERSTORM -> ActivitySuggestion(
                id = "play",
                title = "Khela (Outdoor Play)",
                subtitle = "Field sports, jogging & cycling",
                ratingLabel = "Avoid",
                bestWindow = "Switch to indoor workout",
                reason = "${current.conditionLabel} makes fields slippery.",
                level = AdvisoryLevel.CAUTION
            )
            playHour != null -> ActivitySuggestion(
                id = "play",
                title = "Khela (Outdoor Play)",
                subtitle = "Field sports, jogging & cycling",
                ratingLabel = "Good",
                bestWindow = "Kickoff near ${playHour.hourLabel}",
                reason = "${playHour.temperatureCelsius}°C with ${playHour.precipitationProbabilityPercent}% rain risk.",
                level = AdvisoryLevel.GOOD
            )
            else -> ActivitySuggestion(
                id = "play",
                title = "Khela (Outdoor Play)",
                subtitle = "Field sports, jogging & cycling",
                ratingLabel = "Moderate",
                bestWindow = "Short hydration breaks",
                reason = "Warm feels-like ${current.feelsLikeCelsius}°C — carry plenty of water.",
                level = AdvisoryLevel.MODERATE
            )
        }

        // 5. Photo Tola (Lighting & Golden Hour)
        val photoHour = next12.firstOrNull { it.hourOfDay in listOf(6, 7, 16, 17) && it.weatherCode <= 3 }
        val photoAdvice = when {
            photoHour != null -> ActivitySuggestion(
                id = "photo",
                title = "Photo Tola (Golden Hour)",
                subtitle = "Soft sunlight & crisp outdoor portraits",
                ratingLabel = "Good",
                bestWindow = "Golden window ${photoHour.hourLabel}",
                reason = "Balanced soft light (${photoHour.conditionLabel}) without harsh noon glare.",
                level = AdvisoryLevel.GOOD
            )
            current.conditionType == WeatherConditionType.CLOUDY || current.conditionType == WeatherConditionType.PARTLY_CLOUDY -> ActivitySuggestion(
                id = "photo",
                title = "Photo Tola (Diffused Light)",
                subtitle = "Soft sunlight & crisp outdoor portraits",
                ratingLabel = "Good",
                bestWindow = "Now (soft natural diffuser)",
                reason = "Cloud cover removes harsh shadows for even skin tones.",
                level = AdvisoryLevel.GOOD
            )
            else -> ActivitySuggestion(
                id = "photo",
                title = "Photo Tola (Lighting)",
                subtitle = "Soft sunlight & crisp outdoor portraits",
                ratingLabel = "Moderate",
                bestWindow = "4:30 PM - 5:45 PM",
                reason = "Use shaded angles or weatherproof lens protection.",
                level = AdvisoryLevel.MODERATE
            )
        }

        return listOf(outingAdvice, laundryAdvice, roofAdvice, playAdvice, photoAdvice)
    }

    fun computeOutfitAdvice(current: WeatherSnapshot, next12hMaxRainProb: Int): OutfitAdvisory {
        val temp = current.feelsLikeCelsius
        val isRainy = next12hMaxRainProb >= 50 || current.conditionType == WeatherConditionType.RAIN ||
            current.conditionType == WeatherConditionType.THUNDERSTORM

        val items = mutableListOf<String>()
        val headline: String
        when {
            temp >= 32 -> {
                headline = "Breezy Cotton & Sun Protection"
                items.add("Light breathable cotton t-shirt or panjabi/kurti")
                items.add("UV sunglasses & cap if stepping out midday")
            }
            temp in 23..31 -> {
                headline = "Light Everyday Wear"
                items.add("Comfortable short-sleeve shirt or casual tee")
                items.add("Breathable trousers or jeans")
            }
            temp in 15..22 -> {
                headline = "Light Layer / Windbreaker"
                items.add("Full-sleeve shirt or light hoodie")
                items.add("Extra light cardigan for evening breeze")
            }
            else -> {
                headline = "Warm Winter Layering"
                items.add("Jacket/sweater over thermal base")
                items.add("Closed shoes & scarf if windy")
            }
        }

        val carryNote = if (isRainy) {
            items.add("Waterproof sandals or quick-dry footwear")
            "Carry an umbrella or compact raincoat (rain chance $next12hMaxRainProb%)."
        } else if (current.uvIndex >= 6.0) {
            "Apply SPF 30+ sunscreen before noon errands."
        } else {
            "No rain gear needed today — light carry only."
        }

        return OutfitAdvisory(headline = headline, clothingItems = items, carryNote = carryNote)
    }

    fun computeFarmingAdvice(
        current: WeatherSnapshot,
        hourly: List<HourlyWeatherPoint>,
        daily: List<DailyWeatherPoint>
    ): FarmingAdvisory {
        val next48hDaily = daily.take(2)
        val rainNext48Mm = next48hDaily.sumOf { it.precipitationSumMm }
        val maxRainProb48 = maxOf(
            next48hDaily.maxOfOrNull { it.precipitationProbabilityMax } ?: 0,
            hourly.take(24).maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
        )

        return when {
            rainNext48Mm >= 12.0 || maxRainProb48 >= 70 -> FarmingAdvisory(
                irrigationAction = "Pause Pani Dewa (Irrigation)",
                fertilizerAction = "Delay Sar Dewa (Fertilizer)",
                rainNext48hMm = rainNext48Mm,
                maxRainProbNext48h = maxRainProb48,
                summary = "Significant rainfall (~${String.format("%.1f", rainNext48Mm)} mm, $maxRainProb48% chance) ahead. Rainwater covers moisture; avoid applying fertilizer so nutrients don't wash out.",
                level = AdvisoryLevel.CAUTION
            )
            rainNext48Mm >= 3.0 || maxRainProb48 >= 40 -> FarmingAdvisory(
                irrigationAction = "Light Pani Dewa Only If Dry",
                fertilizerAction = "Sar Dewa With Caution",
                rainNext48hMm = rainNext48Mm,
                maxRainProbNext48h = maxRainProb48,
                summary = "Light showers possible ($maxRainProb48%). Inspect topsoil moisture before irrigating; split fertilizer dosage.",
                level = AdvisoryLevel.MODERATE
            )
            else -> FarmingAdvisory(
                irrigationAction = "Good Time for Pani Dewa",
                fertilizerAction = "Safe Window for Sar Dewa",
                rainNext48hMm = rainNext48Mm,
                maxRainProbNext48h = maxRainProb48,
                summary = "Dry 48h window (${maxRainProb48}% rain risk, soil moisture ${(current.soilMoisture * 100).roundToInt()}%). Ideal for early-morning irrigation and scheduled fertilizing.",
                level = AdvisoryLevel.GOOD
            )
        }
    }

    fun computeSportsAdvice(current: WeatherSnapshot, hourly: List<HourlyWeatherPoint>): SportsPlayAdvisory {
        val maxRainNext6h = hourly.take(6).maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
        val rainMmNext6h = hourly.take(6).sumOf { it.precipitationMm }

        val dryness = when {
            current.precipitationMm > 0.5 || rainMmNext6h >= 4.0 -> "Wet / Slippery Outfield"
            current.humidityPercent >= 82 || rainMmNext6h >= 0.8 -> "Slightly Damp Turf"
            else -> "Dry & Fast Pitch"
        }

        val risk = when {
            maxRainNext6h >= 65 || current.conditionType == WeatherConditionType.THUNDERSTORM -> "High ($maxRainNext6h%)"
            maxRainNext6h >= 35 -> "Moderate ($maxRainNext6h%)"
            else -> "Low ($maxRainNext6h%)"
        }

        val level = when {
            maxRainNext6h >= 65 || current.conditionType == WeatherConditionType.THUNDERSTORM -> AdvisoryLevel.CAUTION
            maxRainNext6h >= 35 || current.humidityPercent >= 85 -> AdvisoryLevel.MODERATE
            else -> AdvisoryLevel.GOOD
        }

        val lighting = when {
            !current.isDay -> "Floodlights Needed"
            current.conditionType == WeatherConditionType.CLOUDY -> "Overcast Swing Conditions"
            else -> "Clear Visibility (${String.format("%.0f", current.visibilityKm)} km)"
        }

        val verdict = when (level) {
            AdvisoryLevel.GOOD -> "Great conditions for full overs cricket or 90-min football!"
            AdvisoryLevel.MODERATE -> "Playable match window; keep covers/towels handy for passing clouds."
            else -> "High chance of rain delay or slippery ground — reschedule or play indoor."
        }

        return SportsPlayAdvisory(
            pitchDryness = dryness,
            rainInterruptionRisk = risk,
            lightingCondition = lighting,
            verdict = verdict,
            level = level
        )
    }

    fun evaluateSmartAlerts(
        bundle: ProWeatherBundle,
        settings: SmartWeatherAlertSettings,
        nowHourOfDay: Int
    ): List<TriggeredWeatherAlert> {
        val alerts = mutableListOf<TriggeredWeatherAlert>()
        val current = bundle.current
        val next2h = bundle.hourly24.take(2)
        val next3h = bundle.hourly24.take(3)

        // 1. Extreme Weather Warning
        if (settings.extremeWarningEnabled) {
            when {
                current.weatherCode >= 95 -> alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.EXTREME,
                        title = "⚠️ Extreme Storm Warning",
                        message = "Thunderstorm active around ${current.localityName}. Seek shelter indoors.",
                        isHighPriority = true
                    )
                )
                current.temperatureCelsius >= 38 -> alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.EXTREME,
                        title = "⚠️ Heatwave Alert",
                        message = "${current.temperatureCelsius}°C in ${current.localityName} — avoid midday exposure.",
                        isHighPriority = true
                    )
                )
                current.temperatureCelsius <= 8 -> alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.EXTREME,
                        title = "⚠️ Severe Cold Drop",
                        message = "Temperature down to ${current.temperatureCelsius}°C in ${current.localityName}.",
                        isHighPriority = true
                    )
                )
            }
        }

        // 2. Brishti Alert (next 30-60 mins / 1st-2nd hour)
        if (settings.brishtiAlertEnabled) {
            val imminent = next2h.firstOrNull {
                it.precipitationProbabilityPercent >= 55 || it.precipitationMm >= 0.5 ||
                    it.conditionType == WeatherConditionType.RAIN || it.conditionType == WeatherConditionType.THUNDERSTORM
            }
            if (imminent != null) {
                alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.BRISHTI,
                        title = "🌧️ Brishti Alert (${current.localityName})",
                        message = "${imminent.conditionLabel} likely around ${imminent.hourLabel} (${imminent.precipitationProbabilityPercent}% chance). Bring laundry in & take an umbrella.",
                        isHighPriority = false
                    )
                )
            }
        }

        // 3. Kapor Shukano Alert
        if (settings.kaporShukanoAlertEnabled) {
            val next3MaxRain = next3h.maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
            if (next3MaxRain < 20 && current.humidityPercent <= 74 && current.isDay) {
                alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.KAPOR_SHUKANO,
                        title = "👕 Kapor Shukano Window",
                        message = "Great drying weather in ${current.localityName} (${current.temperatureCelsius}°C, ${current.humidityPercent}% humidity) for the next 3 hours.",
                        isHighPriority = false
                    )
                )
            } else if (next3MaxRain >= 60) {
                alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.KAPOR_SHUKANO,
                        title = "👕 Bring Clothes Inside",
                        message = "Rain probability rising to $next3MaxRain% soon in ${current.localityName}.",
                        isHighPriority = false
                    )
                )
            }
        }

        // 4. Bazar Alert (7 AM - 10 AM window)
        if (settings.bazarAlertEnabled && nowHourOfDay in 7..10) {
            val morningMaxRain = next3h.maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
            alerts.add(
                TriggeredWeatherAlert(
                    kind = SmartAlertKind.BAZAR,
                    title = "🛒 Morning Bazar Weather",
                    message = if (morningMaxRain < 35) {
                        "Clear morning (${current.temperatureCelsius}°C) in ${current.localityName} — great time for bazar errands."
                    } else {
                        "Morning rain chance $morningMaxRain% in ${current.localityName} — carry an umbrella for bazar."
                    },
                    isHighPriority = false
                )
            )
        }

        // 5. Bike Ride Alert
        if (settings.bikeRideAlertEnabled) {
            val rideRisk = next2h.firstOrNull { it.precipitationProbabilityPercent >= 45 || it.windSpeedKmh >= 28 }
            if (rideRisk != null) {
                alerts.add(
                    TriggeredWeatherAlert(
                        kind = SmartAlertKind.BIKE_RIDE,
                        title = "🏍️ Bike Ride Advisory",
                        message = "Heads up before riding: ${rideRisk.precipitationProbabilityPercent}% rain & ${rideRisk.windSpeedKmh} km/h wind near ${rideRisk.hourLabel}. Pack rain gear!",
                        isHighPriority = false
                    )
                )
            }
        }

        return alerts
    }

    fun buildBanglaVoiceSummary(snapshot: WeatherSnapshot, outfit: OutfitAdvisory): String {
        val banglaCondition = when (snapshot.conditionType) {
            WeatherConditionType.CLEAR -> "আকাশ পরিষ্কার থাকবে"
            WeatherConditionType.PARTLY_CLOUDY -> "আকাশ আংশিক মেঘলা থাকবে"
            WeatherConditionType.CLOUDY -> "আকাশ মেঘলা থাকবে"
            WeatherConditionType.FOG -> "হালকা কুয়াশা থাকবে"
            WeatherConditionType.DRIZZLE -> "গুঁড়ি গুঁড়ি বৃষ্টি হতে পারে"
            WeatherConditionType.RAIN -> "বৃষ্টি হওয়ার সম্ভাবনা রয়েছে"
            WeatherConditionType.THUNDERSTORM -> "বজ্রসহ ঝড়-বৃষ্টি হতে পারে"
            WeatherConditionType.SNOW -> "তুষারপাত হতে পারে"
        }
        return "আজ ${snapshot.localityName}-এ $banglaCondition, তাপমাত্রা ${snapshot.temperatureCelsius} ডিগ্রি সেলসিয়াস এবং আর্দ্রতা ${snapshot.humidityPercent} শতাংশ। ${outfit.headline} পরা ভালো হবে।"
    }

    fun buildBanglishVoiceSummary(snapshot: WeatherSnapshot): String {
        return "Today in ${snapshot.localityName}, expect ${snapshot.conditionLabel}, temperature ${snapshot.temperatureCelsius} degrees Celsius, humidity ${snapshot.humidityPercent} percent, wind ${snapshot.windSpeedKmh} kilometers per hour."
    }
}
