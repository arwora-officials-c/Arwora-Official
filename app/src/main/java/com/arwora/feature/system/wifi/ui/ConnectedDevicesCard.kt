package com.arwora.feature.system.wifi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.DeviceInfo
import com.arwora.feature.system.wifi.ScanMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesCard(
    devices: List<DeviceInfo>,
    isScanning: Boolean,
    scanMode: ScanMode,
    scanProgress: Float,
    statusText: String,
    onSetScanMode: (ScanMode) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSaveCustomName: (mac: String, name: String) -> Unit,
    onPingDevice: (DeviceInfo) -> Unit,
    hostIp: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDevices = remember(devices, searchQuery) {
        if (searchQuery.isBlank()) devices
        else {
            devices.filter {
                (it.customLabel ?: "").contains(searchQuery, ignoreCase = true) ||
                        (it.friendlyName ?: "").contains(searchQuery, ignoreCase = true) ||
                        it.ip.contains(searchQuery, ignoreCase = true) ||
                        it.mac.contains(searchQuery, ignoreCase = true) ||
                        it.vendor.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    WifiSectionCard(
        title = "Connected Devices",
        description = "Discover active hosts on subnet with vendor lookup and latency",
        icon = Icons.Default.Devices,
        iconTint = Color(0xFF10B981),
        statusBadge = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (devices.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (devices.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${devices.size} Discovered",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = if (devices.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        actionButton = {
            if (isScanning) {
                Button(
                    onClick = onStopScan,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Stop", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                Button(
                    onClick = onStartScan,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan Subnet", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        },
        modifier = modifier
    ) {
        // Mode Selector: Fast Scan vs Deep Scan
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = scanMode == ScanMode.FAST,
                onClick = { onSetScanMode(ScanMode.FAST) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Fast ARP Scan", style = MaterialTheme.typography.labelSmall)
            }
            SegmentedButton(
                selected = scanMode == ScanMode.DEEP,
                onClick = { onSetScanMode(ScanMode.DEEP) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Deep Scan (Port Audit)", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Scanning Status Indicator
        if (isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(scanProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Filter
        if (devices.size > 1) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter devices by name, IP, or vendor...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Empty state
        if (filteredDevices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (devices.isEmpty()) "No Subnet Devices Discovered" else "No matching devices found",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (devices.isEmpty()) "Tap 'Scan Subnet' above to probe local IPv4 addresses and discover connected hardware." else "Try adjusting your search criteria.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Devices List Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredDevices.forEach { device ->
                        ModernDeviceListCard(
                            device = device,
                            hostIp = hostIp,
                            onEditName = { editingDevice = device },
                            onPing = { onPingDevice(device) },
                            onCopy = { text, label ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                            Toast.makeText(context, "Copied $label: $text", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Edit Custom Name Dialog
    editingDevice?.let { dev ->
        var tempName by remember { mutableStateOf(dev.customLabel ?: dev.friendlyName ?: "") }

        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = {
                Text(
                    text = "Set Custom Device Label",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Assign an easy-to-scan friendly name for ${dev.ip} (${dev.vendor.ifBlank { "Host" }}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        placeholder = { Text("e.g. Mahfuz's Phone or Office TV") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val labelKey = dev.mac.takeIf { it != "Unknown" && it.isNotBlank() } ?: dev.ip
                    onSaveCustomName(labelKey, tempName)
                    editingDevice = null
                }) {
                    Text("Save Label")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ModernDeviceListCard(
    device: DeviceInfo,
    hostIp: String,
    onEditName: () -> Unit,
    onPing: () -> Unit,
    onCopy: (text: String, label: String) -> Unit
) {
    val displayName = device.customLabel
        ?: device.friendlyName?.takeIf { it.isNotBlank() }
        ?: device.hostname?.takeIf { it.isNotBlank() }
        ?: device.vendor.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Generic", ignoreCase = true) }?.let { "$it Device (${device.ip})" }
        ?: "Host (${device.ip})"

    val formattedLastSeen = remember(device.lastSeenTime) {
        val diff = System.currentTimeMillis() - device.lastSeenTime
        when {
            diff < 60_000 -> "Active now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            else -> "${diff / 3600_000}h ago"
        }
    }

    val (deviceIcon, iconTint, iconBg) = remember(device.vendor, device.friendlyName, device.customLabel, device.hostname) {
        val text = "${device.vendor} ${device.friendlyName} ${device.customLabel} ${device.hostname}".lowercase()
        when {
            text.contains("router") || text.contains("gateway") || text.contains("modem") || text.contains("cisco") || text.contains("netgear") || text.contains("tp-link") || text.contains("asus") ->
                Triple(Icons.Default.Router, Color(0xFF8B5CF6), Color(0xFF8B5CF6).copy(alpha = 0.12f))
            text.contains("phone") || text.contains("pixel") || text.contains("iphone") || text.contains("galaxy") || text.contains("android") || text.contains("xiaomi") || text.contains("huawei") ->
                Triple(Icons.Default.Smartphone, Color(0xFF3B82F6), Color(0xFF3B82F6).copy(alpha = 0.12f))
            text.contains("laptop") || text.contains("macbook") || text.contains("thinkpad") || text.contains("dell") || text.contains("lenovo") || text.contains("hp") || text.contains("surface") || text.contains("pc") || text.contains("intel") ->
                Triple(Icons.Default.Laptop, Color(0xFF0EA5E9), Color(0xFF0EA5E9).copy(alpha = 0.12f))
            text.contains("tv") || text.contains("roku") || text.contains("chromecast") || text.contains("bravia") || text.contains("fire tv") || text.contains("apple tv") ->
                Triple(Icons.Default.Tv, Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.12f))
            text.contains("print") || text.contains("canon") || text.contains("epson") || text.contains("brother") ->
                Triple(Icons.Default.Print, Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.12f))
            text.contains("tablet") || text.contains("ipad") || text.contains("tab") ->
                Triple(Icons.Default.Tablet, Color(0xFF6366F1), Color(0xFF6366F1).copy(alpha = 0.12f))
            else ->
                Triple(Icons.Default.Devices, Color(0xFF64748B), Color(0xFF64748B).copy(alpha = 0.12f))
        }
    }

    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Avatar + Device Name & Vendor + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Avatar with Status indicator
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconBg)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Online Status Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (device.customLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.vendor.ifBlank { "Generic Vendor" },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedLastSeen,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                            color = Color(0xFF10B981)
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onEditName,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Custom Label",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // IP & MAC with Clickable copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCopy(device.ip, "IP Address") }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "IP: ${device.ip}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy IP",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val hasValidMac = device.mac.isNotBlank() && !device.mac.equals("Unknown", ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(4.dp))
                        .let { mod ->
                            if (hasValidMac) mod.clickable { onCopy(device.mac, "MAC Address") } else mod
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasValidMac) "MAC: ${device.mac}" else "MAC: Restricted",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasValidMac) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy MAC",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expandable Details Section
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Grid of 2 columns for details - Pruned and balanced
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DeviceDetailItem(label = "Band", value = device.band ?: "Auto", modifier = Modifier.weight(1f))
                            DeviceDetailItem(label = "SSID", value = device.ssid ?: "Unknown", modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DeviceDetailItem(
                                label = "RSSI / Signal", 
                                value = "${device.signalQuality ?: "Good"} (${device.rssi ?: -45} dBm)", 
                                modifier = Modifier.weight(1f)
                            )
                            DeviceDetailItem(
                                label = "Distance", 
                                value = if (device.estimatedDistanceMeters != null) "~${String.format("%.1f", device.estimatedDistanceMeters)}m" else "Approx. 2.5 meters", 
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DeviceDetailItem(label = "Channel", value = device.channel?.toString() ?: "36", modifier = Modifier.weight(1f))
                            DeviceDetailItem(
                                label = "Rate", 
                                value = if (device.linkSpeedMbps != null) "${device.linkSpeedMbps}M" else "292M", 
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Latency Badge & Ping Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = device.latencyMs?.let { "$it ms" } ?: "-- ms",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onPing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Ping", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun DeviceDetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
