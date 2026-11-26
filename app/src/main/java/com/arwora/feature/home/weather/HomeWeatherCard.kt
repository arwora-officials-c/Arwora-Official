package com.arwora.feature.home.weather

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager

/**
 * Material 3 Expressive Dark live weather widget card placed at the top of Home Dashboard.
 * Matches ArworaDesignTokens (24.dp corner radius, 1.dp uniform outline, tonal inner strip).
 */
@Composable
fun HomeWeatherCard(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_weather_card"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = cardElevation
    ) {
        when (uiState) {
            is WeatherUiState.Loading -> {
                if (uiState.cached != null) {
                    WeatherContentBody(
                        data = uiState.cached,
                        isRefreshing = true,
                        needsLocationPermission = false,
                        staleErrorMessage = null,
                        onRefresh = onRefresh,
                        onRequestLocationPermission = onRequestLocationPermission
                    )
                } else {
                    WeatherLoadingPlaceholder()
                }
            }

            is WeatherUiState.Success -> {
                WeatherContentBody(
                    data = uiState.data,
                    isRefreshing = uiState.isRefreshing,
                    needsLocationPermission = uiState.needsLocationPermission,
                    staleErrorMessage = uiState.staleErrorMessage,
                    onRefresh = onRefresh,
                    onRequestLocationPermission = onRequestLocationPermission
                )
            }

            is WeatherUiState.Error -> {
                WeatherErrorPlaceholder(
                    message = uiState.message,
                    needsLocationPermission = uiState.needsLocationPermission,
                    onRetry = onRefresh,
                    onRequestLocationPermission = onRequestLocationPermission
                )
            }
        }
    }
}

@Composable
private fun WeatherLoadingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .testTag("home_weather_loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = Icons.Default.WbSunny,
                tint = Color(0xFFF59E0B),
                size = 48.dp,
                iconSize = 24.dp,
                contentDescription = "Loading Weather"
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Weather",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Fetching local forecast...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun WeatherErrorPlaceholder(
    message: String,
    needsLocationPermission: Boolean,
    onRetry: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .testTag("home_weather_error"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = Icons.Default.Cloud,
                tint = MaterialTheme.colorScheme.error,
                size = 46.dp,
                iconSize = 22.dp,
                contentDescription = "Weather Unavailable"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Weather Offline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (needsLocationPermission) {
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onRequestLocationPermission()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("home_weather_enable_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enable Location",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    SoundEffectManager.playClick()
                    onRetry()
                },
                shape = ArworaDesignTokens.PillShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("home_weather_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Retry",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun WeatherContentBody(
    data: WeatherSnapshot,
    isRefreshing: Boolean,
    needsLocationPermission: Boolean,
    staleErrorMessage: String?,
    onRefresh: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val visual = resolveWeatherVisual(data.conditionType, data.isDay)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Row: Temperature, Condition & Locality on Left/Center; Weather Icon & Refresh on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = visual.tint,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = data.localityName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("home_weather_locality")
                    )
                    ArworaPillBadge(
                        text = data.locationSource.label,
                        tint = if (data.locationSource == WeatherLocationSource.GPS) {
                            Color(0xFF10B981)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        showDot = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${data.temperatureCelsius}°C",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.8).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.testTag("home_weather_temperature")
                    )
                    Text(
                        text = data.conditionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .testTag("home_weather_condition")
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ArworaTonalIconBadge(
                    icon = visual.icon,
                    tint = visual.tint,
                    containerColor = visual.tint.copy(alpha = 0.14f),
                    borderColor = visual.tint.copy(alpha = 0.32f),
                    size = 52.dp,
                    iconSize = 28.dp,
                    contentDescription = data.conditionLabel
                )

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("home_weather_refresh_spinner"),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                onRefresh()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_weather_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Weather",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = visual.tint,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        }

        // Bottom Strip: Compact secondary metrics (Humidity, Wind Speed, High/Low)
        Surface(
            shape = ArworaDesignTokens.InnerTileShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            border = ArworaDesignTokens.innerTileBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_weather_details_strip")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherMetricItem(
                    icon = Icons.Default.WaterDrop,
                    tint = Color(0xFF38BDF8),
                    label = "Humidity",
                    value = "${data.humidityPercent}%",
                    modifier = Modifier.weight(1f)
                )
                WeatherMetricItem(
                    icon = Icons.Default.Air,
                    tint = Color(0xFF10B981),
                    label = "Wind",
                    value = "${data.windSpeedKmh} km/h",
                    modifier = Modifier.weight(1f)
                )
                WeatherMetricItem(
                    icon = Icons.Default.Thermostat,
                    tint = Color(0xFFF59E0B),
                    label = "High / Low",
                    value = "${data.highTempCelsius}° / ${data.lowTempCelsius}°",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Optional compact footer prompt when location permission is denied or network is offline
        if (needsLocationPermission || !staleErrorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = staleErrorMessage
                        ?: "Approximate location • Enable GPS for exact local weather",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                if (needsLocationPermission) {
                    TextButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onRequestLocationPermission()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_weather_enable_location_button")
                    ) {
                        Text(
                            text = "Enable GPS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricItem(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class WeatherVisual(
    val icon: ImageVector,
    val tint: Color
)

private fun resolveWeatherVisual(type: WeatherConditionType, isDay: Boolean): WeatherVisual =
    when (type) {
        WeatherConditionType.CLEAR -> if (isDay) {
            WeatherVisual(Icons.Default.WbSunny, Color(0xFFF59E0B))
        } else {
            WeatherVisual(Icons.Default.NightsStay, Color(0xFF8B5CF6))
        }
        WeatherConditionType.PARTLY_CLOUDY -> WeatherVisual(Icons.Default.FilterDrama, Color(0xFF38BDF8))
        WeatherConditionType.CLOUDY -> WeatherVisual(Icons.Default.Cloud, Color(0xFF94A3B8))
        WeatherConditionType.FOG -> WeatherVisual(Icons.Default.Grain, Color(0xFF94A3B8))
        WeatherConditionType.DRIZZLE -> WeatherVisual(Icons.Default.WaterDrop, Color(0xFF38BDF8))
        WeatherConditionType.RAIN -> WeatherVisual(Icons.Default.WaterDrop, Color(0xFF0EA5E9))
        WeatherConditionType.THUNDERSTORM -> WeatherVisual(Icons.Default.Thunderstorm, Color(0xFF8B5CF6))
        WeatherConditionType.SNOW -> WeatherVisual(Icons.Default.AcUnit, Color(0xFFE2E8F0))
    }
