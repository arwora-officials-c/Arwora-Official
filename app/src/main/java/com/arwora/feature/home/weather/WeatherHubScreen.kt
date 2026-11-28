package com.arwora.feature.home.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager
import kotlin.math.roundToInt

private val WeatherModuleTabs = listOf(
    "Live & Radar",
    "Alerts",
    "Planner & Farm",
    "Health",
    "Utilities"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherHubScreen(
    onBack: () -> Unit,
    onOpenWeatherCamera: () -> Unit,
    onOpenAlertSettings: () -> Unit = {},
    proViewModel: WeatherProViewModel = viewModel()
) {
    val uiState by proViewModel.uiState.collectAsStateWithLifecycle()
    val alertSettings by proViewModel.alertSettings.collectAsStateWithLifecycle()
    val liveAlerts by proViewModel.liveAlertsPreview.collectAsStateWithLifecycle()
    val searchResults by proViewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by proViewModel.isSearchingCity.collectAsStateWithLifecycle()
    val isSpeaking by proViewModel.isSpeaking.collectAsStateWithLifecycle()

    var selectedModuleIndex by remember { mutableIntStateOf(0) }
    var showCitySearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(proViewModel) {
        proViewModel.ensureInitialized()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pro Weather Hub",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        val subtitle = (uiState as? ProWeatherUiState.Success)?.statusBanner
                            ?: "Real-time hyperlocal intelligence"
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("weather_hub_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onOpenAlertSettings()
                        },
                        modifier = Modifier.testTag("weather_hub_alerts_settings_btn")
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Weather Alerts Settings")
                    }
                    IconButton(
                        onClick = { showCitySearchDialog = true },
                        modifier = Modifier.testTag("weather_hub_add_city_btn")
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = "Manage Cities")
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            proViewModel.refresh(force = true)
                        },
                        modifier = Modifier.testTag("weather_hub_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Forecast")
                    }
                }
            )
        },
        modifier = Modifier.testTag("weather_hub_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ProWeatherUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading Pro Weather Hub...")
                    }
                }

                is ProWeatherUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Weather Hub Offline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { proViewModel.refresh(force = true) },
                            shape = ArworaDesignTokens.PillShape,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Retry Sync", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is ProWeatherUiState.Success -> {
                    val bundle = state.bundle
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .align(Alignment.TopCenter)
                            .testTag("weather_hub_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Multi-City Quick Switcher Bar
                        item {
                            CitySwitcherStrip(
                                savedCities = bundle.savedCities,
                                activeCityId = bundle.activeCityId,
                                onSelectCity = { proViewModel.selectCity(it) },
                                onRemoveCity = { proViewModel.removeFavoriteCity(it) },
                                onOpenAddDialog = { showCitySearchDialog = true }
                            )
                        }

                        if (state.isRefreshing) {
                            item {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        // Hero Summary Card
                        item {
                            WeatherHubHeroCard(bundle = bundle)
                        }

                        // 5-Module Navigation Tabs
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WeatherModuleTabs.forEachIndexed { idx, label ->
                                    FilterChip(
                                        selected = selectedModuleIndex == idx,
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            selectedModuleIndex = idx
                                        },
                                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.testTag("weather_hub_tab_$idx")
                                    )
                                }
                            }
                        }

                        when (selectedModuleIndex) {
                            0 -> {
                                item { ProCoreMetricsGrid(current = bundle.current) }
                                if (bundle.hourly24.isNotEmpty()) {
                                    item { HourlyForecastCurveCard(hourly = bundle.hourly24) }
                                }
                                if (bundle.daily14.isNotEmpty()) {
                                    item { ExtendedForecastCard(daily14 = bundle.daily14) }
                                }
                                item {
                                    WeatherRadarMapCard(
                                        latitude = bundle.current.latitude,
                                        longitude = bundle.current.longitude,
                                        localityName = bundle.current.localityName,
                                        radarPath = bundle.rainViewerRadarPath
                                    )
                                }
                            }
                            1 -> {
                                item {
                                    WeatherSmartAlertsSection(
                                        settings = alertSettings,
                                        liveAlerts = liveAlerts,
                                        onUpdateSettings = proViewModel::updateAlertSettings,
                                        onRunCheckNow = proViewModel::runManualAlertCheckAndNotify,
                                        onOpenAlertSettings = onOpenAlertSettings
                                    )
                                }
                            }
                            2 -> {
                                item { WeatherPlannerFarmSection(bundle = bundle) }
                            }
                            3 -> {
                                item { WeatherHealthComfortSection(bundle = bundle) }
                            }
                            4 -> {
                                item {
                                    WeatherProUtilitiesSection(
                                        bundle = bundle,
                                        isSpeaking = isSpeaking,
                                        onToggleBanglaVoice = proViewModel::toggleBanglaVoiceForecast,
                                        onOpenWeatherCamera = onOpenWeatherCamera
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showCitySearchDialog) {
        CitySearchDialog(
            searchResults = searchResults,
            isSearching = isSearching,
            savedCount = (uiState as? ProWeatherUiState.Success)?.bundle?.savedCities?.size ?: 0,
            onQueryChanged = { proViewModel.searchCities(it) },
            onSelectCity = {
                proViewModel.addAndSelectCity(it)
                showCitySearchDialog = false
            },
            onDismiss = { showCitySearchDialog = false }
        )
    }
}

@Composable
private fun CitySwitcherStrip(
    savedCities: List<SavedWeatherCity>,
    activeCityId: String,
    onSelectCity: (String) -> Unit,
    onRemoveCity: (String) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = activeCityId == ProWeatherBundle.CURRENT_LOCATION_CITY_ID,
            onClick = { onSelectCity(ProWeatherBundle.CURRENT_LOCATION_CITY_ID) },
            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("My Location", fontWeight = FontWeight.SemiBold) },
            modifier = Modifier.testTag("weather_city_chip_gps")
        )
        savedCities.forEach { city ->
            FilterChip(
                selected = activeCityId == city.id,
                onClick = { onSelectCity(city.id) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove ${city.name}",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemoveCity(city.id) }
                    )
                },
                label = { Text(city.name, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("weather_city_chip_${city.id}")
            )
        }
        if (savedCities.size < WeatherRepository.MAX_SAVED_CITIES) {
            FilterChip(
                selected = false,
                onClick = onOpenAddDialog,
                leadingIcon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Add City (${savedCities.size}/10)") },
                modifier = Modifier.testTag("weather_city_chip_add")
            )
        }
    }
}

@Composable
private fun WeatherHubHeroCard(bundle: ProWeatherBundle) {
    val current = bundle.current
    val visual = resolveWeatherVisual(current.conditionType, current.isDay)

    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = visual.tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = current.localityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (bundle.isFromOfflineCache) {
                        ArworaPillBadge(text = "OFFLINE CACHE", tint = Color(0xFFF59E0B))
                    }
                }
                Text(
                    text = "${current.temperatureCelsius}°C",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${current.conditionLabel} • Feels like ${current.feelsLikeCelsius}°C • H:${current.highTempCelsius}° L:${current.lowTempCelsius}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ArworaTonalIconBadge(
                icon = visual.icon,
                tint = visual.tint,
                containerColor = visual.tint.copy(alpha = 0.14f),
                borderColor = visual.tint.copy(alpha = 0.35f),
                size = 64.dp,
                iconSize = 34.dp,
                cornerRadius = 18.dp,
                contentDescription = current.conditionLabel
            )
        }
    }
}

@Composable
private fun ProCoreMetricsGrid(current: WeatherSnapshot) {
    val metrics = listOf(
        Triple("Feels Like", "${current.feelsLikeCelsius}°C", Icons.Default.Thermostat),
        Triple("Humidity", "${current.humidityPercent}%", Icons.Default.WaterDrop),
        Triple("Wind", "${current.windSpeedKmh} km/h ${WeatherAdvisoryEngine.windDirectionCompass(current.windDirectionDeg)}", Icons.Default.Air),
        Triple("UV Index", "${current.uvIndex.roundToInt()}", Icons.Default.WbSunny),
        Triple("Pressure", "${current.pressureHpa} hPa", Icons.Default.Compress),
        Triple("Dew Point", "${current.dewPointCelsius}°C", Icons.Default.WaterDrop),
        Triple("Visibility", "${String.format("%.1f", current.visibilityKm)} km", Icons.Default.Visibility),
        Triple("Rain Now", "${String.format("%.1f", current.precipitationMm)} mm", Icons.Default.WaterDrop)
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { (label, value, icon) ->
                    CoreMetricCard(
                        label = label,
                        value = value,
                        icon = icon,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArworaTonalIconBadge(
                icon = icon,
                size = 36.dp,
                iconSize = 18.dp,
                contentDescription = label
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HourlyForecastCurveCard(hourly: List<HourlyWeatherPoint>) {
    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "24-Hour Temperature & Rain Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Smooth temperature trend curve on Canvas
            val curveColor = MaterialTheme.colorScheme.primary
            val minTemp = (hourly.minOfOrNull { it.temperatureCelsius } ?: 15).toFloat()
            val maxTemp = (hourly.maxOfOrNull { it.temperatureCelsius } ?: 35).toFloat()
            val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp)
            ) {
                if (hourly.size < 2) return@Canvas
                val path = Path()
                val stepX = size.width / (hourly.size - 1).toFloat()
                hourly.forEachIndexed { i, point ->
                    val x = i * stepX
                    val norm = (point.temperatureCelsius - minTemp) / tempRange
                    val y = size.height - 10f - (norm * (size.height - 20f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = curveColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                hourly.forEachIndexed { i, point ->
                    if (i % 3 == 0) {
                        val x = i * stepX
                        val norm = (point.temperatureCelsius - minTemp) / tempRange
                        val y = size.height - 10f - (norm * (size.height - 20f))
                        drawCircle(color = curveColor, radius = 6f, center = Offset(x, y))
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(hourly) { h ->
                    val visual = resolveWeatherVisual(h.conditionType, h.isDay)
                    Surface(
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        border = ArworaDesignTokens.innerTileBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = h.hourLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = h.conditionLabel,
                                tint = visual.tint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${h.temperatureCelsius}°",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${h.precipitationProbabilityPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtendedForecastCard(daily14: List<DailyWeatherPoint>) {
    var rangeTabIndex by remember { mutableIntStateOf(0) }
    val shownDays = if (rangeTabIndex == 0) daily14.take(7) else daily14.take(14)

    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Extended Daily Forecast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TabRow(selectedTabIndex = rangeTabIndex) {
                Tab(
                    selected = rangeTabIndex == 0,
                    onClick = { rangeTabIndex = 0 },
                    text = { Text("7-Day", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("weather_forecast_tab_7d")
                )
                Tab(
                    selected = rangeTabIndex == 1,
                    onClick = { rangeTabIndex = 1 },
                    text = { Text("14-Day", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("weather_forecast_tab_14d")
                )
            }
            shownDays.forEach { day ->
                val visual = resolveWeatherVisual(day.conditionType, true)
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = ArworaDesignTokens.innerTileBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day.dayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.2f)
                        )
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = day.conditionLabel,
                            tint = visual.tint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${day.precipitationProbabilityMax}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = "${day.highTempCelsius}° / ${day.lowTempCelsius}°",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CitySearchDialog(
    searchResults: List<SavedWeatherCity>,
    isSearching: Boolean,
    savedCount: Int,
    onQueryChanged: (String) -> Unit,
    onSelectCity: (SavedWeatherCity) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Favorite City ($savedCount/10)",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChanged(it)
                    },
                    placeholder = { Text("Search Dhaka, Sylhet, London...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_city_search_input")
                )
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                searchResults.take(5).forEach { city ->
                    Surface(
                        onClick = { onSelectCity(city) },
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = ArworaDesignTokens.innerTileBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(city.name, fontWeight = FontWeight.Bold)
                            if (city.subtitle.isNotBlank()) {
                                Text(
                                    text = city.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
