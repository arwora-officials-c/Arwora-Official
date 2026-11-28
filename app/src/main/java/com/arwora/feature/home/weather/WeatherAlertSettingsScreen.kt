package com.arwora.feature.home.weather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherAlertSettingsScreen(
    onBack: () -> Unit,
    proViewModel: WeatherProViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by proViewModel.alertSettings.collectAsStateWithLifecycle()
    val liveAlerts by proViewModel.liveAlertsPreview.collectAsStateWithLifecycle()

    var hasPostNotificationsPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotificationsPermission = granted
        val msg = if (granted) {
            "Notification permission granted for Weather Alerts"
        } else {
            "Notification permission denied. Preferences are still saved."
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(proViewModel) {
        proViewModel.ensureInitialized()
    }

    val activeCount = listOf(
        settings.brishtiAlertEnabled,
        settings.kaporShukanoAlertEnabled,
        settings.bazarAlertEnabled,
        settings.bikeRideAlertEnabled,
        settings.extremeWarningEnabled
    ).count { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Weather Alerts",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$activeCount of 5 notifications enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onBack()
                        },
                        modifier = Modifier.testTag("weather_alert_settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("weather_alert_settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
                .testTag("weather_alert_settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Card
            item {
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ArworaTonalIconBadge(
                            icon = Icons.Default.NotificationsActive,
                            tint = Color(0xFFF59E0B),
                            size = 44.dp,
                            iconSize = 22.dp,
                            contentDescription = "Weather Alerts Summary"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Hyperlocal Smart Notifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Background checks run every 30 mins and persist locally via Room.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ArworaPillBadge(
                                text = "Room Database • $activeCount Active",
                                tint = Color(0xFF10B981),
                                showDot = true,
                                modifier = Modifier.testTag("weather_alerts_room_badge")
                            )
                        }
                    }
                }
            }

            if (!hasPostNotificationsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(
                        shape = ArworaDesignTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                        border = ArworaDesignTokens.cardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weather_alerts_permission_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Enable System Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant notification access so Rain, Laundry, Market, and Commuting alerts appear in your status bar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                shape = ArworaDesignTokens.PillShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("weather_alerts_allow_notif_btn")
                            ) {
                                Text("Allow Notifications", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Notification Preferences Card
            item {
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_alerts_settings_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Individual Alert Channels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        WeatherAlertToggleTile(
                            icon = Icons.Default.WaterDrop,
                            tint = Color(0xFF38BDF8),
                            title = "Rain (Brishti)",
                            subtitle = "Notify 30–60 mins before rain reaches your area",
                            checked = settings.brishtiAlertEnabled,
                            switchTag = "weather_alert_setting_brishti",
                            onCheckedChange = { enabled ->
                                proViewModel.updateAlertSettings { it.copy(brishtiAlertEnabled = enabled) }
                            }
                        )

                        WeatherAlertToggleTile(
                            icon = Icons.Default.DryCleaning,
                            tint = Color(0xFFF59E0B),
                            title = "Laundry (Kapor Shukano)",
                            subtitle = "Alert for sunny drying windows and sudden rain risk",
                            checked = settings.kaporShukanoAlertEnabled,
                            switchTag = "weather_alert_setting_kapor",
                            onCheckedChange = { enabled ->
                                proViewModel.updateAlertSettings { it.copy(kaporShukanoAlertEnabled = enabled) }
                            }
                        )

                        WeatherAlertToggleTile(
                            icon = Icons.Default.ShoppingBag,
                            tint = Color(0xFF10B981),
                            title = "Market (Bazar)",
                            subtitle = "Morning 7:00 AM – 10:00 AM clear window alert for errands",
                            checked = settings.bazarAlertEnabled,
                            switchTag = "weather_alert_setting_bazar",
                            onCheckedChange = { enabled ->
                                proViewModel.updateAlertSettings { it.copy(bazarAlertEnabled = enabled) }
                            }
                        )

                        WeatherAlertToggleTile(
                            icon = Icons.Default.TwoWheeler,
                            tint = Color(0xFFA855F7),
                            title = "Commuting (Bike Ride)",
                            subtitle = "Next 2-hour rain & wind speed warning before your ride",
                            checked = settings.bikeRideAlertEnabled,
                            switchTag = "weather_alert_setting_bike",
                            onCheckedChange = { enabled ->
                                proViewModel.updateAlertSettings { it.copy(bikeRideAlertEnabled = enabled) }
                            }
                        )

                        WeatherAlertToggleTile(
                            icon = Icons.Default.Warning,
                            tint = Color(0xFFEF4444),
                            title = "Extreme Weather Warning",
                            subtitle = "High-priority storm, heatwave & severe cold alerts",
                            checked = settings.extremeWarningEnabled,
                            switchTag = "weather_alert_setting_extreme",
                            onCheckedChange = { enabled ->
                                proViewModel.updateAlertSettings { it.copy(extremeWarningEnabled = enabled) }
                            }
                        )
                    }
                }
            }

            // Action Buttons
            item {
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                val sent = proViewModel.runManualAlertCheckAndNotify()
                                val msg = if (sent > 0) {
                                    "Dispatched $sent local weather alert(s)"
                                } else {
                                    "Checked forecast (${liveAlerts.size} active advisories)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            shape = ArworaDesignTokens.PillShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("weather_alerts_settings_check_btn")
                        ) {
                            Text("Run Alert Check Now", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                proViewModel.resetAlertSettingsToDefaults()
                                Toast.makeText(context, "Alert preferences reset to defaults", Toast.LENGTH_SHORT).show()
                            },
                            shape = ArworaDesignTokens.PillShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("weather_alerts_settings_reset_btn")
                        ) {
                            Text("Reset Defaults", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WeatherAlertToggleTile(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = ArworaDesignTokens.InnerTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = ArworaDesignTokens.innerTileBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                SoundEffectManager.playClick()
                onCheckedChange(!checked)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = icon,
                tint = tint,
                size = 38.dp,
                iconSize = 20.dp,
                contentDescription = title
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = {
                    SoundEffectManager.playClick()
                    onCheckedChange(it)
                },
                modifier = Modifier.testTag(switchTag)
            )
        }
    }
}
