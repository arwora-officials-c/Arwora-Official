package com.arwora.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.utils.DnsManager
import com.arwora.utils.DnsProvider
import com.arwora.utils.DnsSecureSettingsHelper
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(DnsManager.getSelectedDns(context)) }
    var customHostInput by remember { mutableStateOf(if (selectedProvider.id == "custom") selectedProvider.dotHost else "") }
    var expanded by remember { mutableStateOf(false) }

    var liveDnsMode by remember { mutableStateOf("automatic") }
    var liveDnsSpecifier by remember { mutableStateOf("") }
    var showPermissionGuidanceDialog by remember { mutableStateOf(false) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }
    var tempCustomInput by remember { mutableStateOf("") }

    fun refreshLiveDnsState() {
        try {
            liveDnsMode = Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "automatic"
        } catch (_: Throwable) {
            liveDnsMode = "automatic"
        }
        try {
            liveDnsSpecifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: ""
        } catch (_: Throwable) {
            liveDnsSpecifier = ""
        }
    }

    LaunchedEffect(Unit) {
        refreshLiveDnsState()
    }

    val groupedProviders = remember { DnsManager.providers.groupBy { it.category } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private DNS Changer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        DnsSecureSettingsHelper.openPrivateDnsSettings(context)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "System Private DNS Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Private DNS Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (liveDnsMode) {
                                "off" -> "Disabled / Off"
                                "hostname" -> if (liveDnsSpecifier.isNotBlank()) liveDnsSpecifier else "Custom Hostname"
                                else -> "Automatic (ISP Default)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { refreshLiveDnsState() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh status",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

                    // Unified DNS Configuration Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Text(text = "DNS Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            }

                            // Quick 1-Tap Preset Badges
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Quick Presets",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val quickPicks = listOf(
                                        "Cloudflare" to "one.one.one.one",
                                        "Google" to "dns.google",
                                        "AdGuard" to "dns.adguard-dns.com",
                                        "NextDNS" to "dns.nextdns.io"
                                    )
                                    quickPicks.forEach { (name, host) ->
                                        val isMatched = (selectedProvider.dotHost == host)
                                        FilterChip(
                                            selected = isMatched,
                                            onClick = {
                                                SoundEffectManager.playClick()
                                                val provider = DnsManager.providers.find { it.dotHost == host }
                                                    ?: DnsProvider("quick_$name", name, host, "Fast & Secure", "Public", "", "")
                                                selectedProvider = provider
                                                customHostInput = host
                                            },
                                            label = { Text(name, fontSize = 12.sp, fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Medium) },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    val isCustomSelected = (selectedProvider.id == "custom" || (selectedProvider.dotHost !in quickPicks.map { it.second } && selectedProvider.dotHost.isNotBlank()))
                                    FilterChip(
                                        selected = isCustomSelected,
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            tempCustomInput = if (isCustomSelected) selectedProvider.dotHost else ""
                                            showCustomDnsDialog = true
                                        },
                                        label = { Text(if (isCustomSelected && selectedProvider.dotHost.isNotBlank()) "Custom: ${selectedProvider.dotHost}" else "+ Add Custom", fontSize = 12.sp, fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // DNS Provider Category Dropdown (Restored)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Provider Selector",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )

                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = if (selectedProvider.id == "custom") "Custom: $customHostInput" else "[${selectedProvider.category}] ${selectedProvider.name}",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        groupedProviders.forEach { (category, providers) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = category,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                onClick = {},
                                                enabled = false
                                            )
                                            providers.forEach { provider ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (provider.id == "custom") "✏️ ${provider.name}" else provider.name,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedProvider = provider
                                                        if (provider.id != "custom") {
                                                            customHostInput = provider.dotHost
                                                        }
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hostname Input Field with Copy Button
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "DoT Hostname",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )

                                OutlinedTextField(
                                    value = if (selectedProvider.id == "custom") customHostInput else selectedProvider.dotHost,
                                    onValueChange = { if (selectedProvider.id == "custom") customHostInput = it },
                                    readOnly = selectedProvider.id != "custom",
                                    placeholder = { Text("e.g. one.one.one.one") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (selectedProvider.id == "custom") Icons.Default.Edit else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (selectedProvider.id == "custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    trailingIcon = {
                                        val hostToCopy = if (selectedProvider.id == "custom") customHostInput else selectedProvider.dotHost
                                        if (hostToCopy.isNotBlank()) {
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("DNS Hostname", hostToCopy))
                                                Toast.makeText(context, "DNS hostname copied to clipboard", Toast.LENGTH_SHORT).show()
                                                SoundEffectManager.playClick()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Hostname",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    supportingText = {
                                        Text(
                                            text = if (selectedProvider.id == "custom") "Enter DNS over TLS hostname." else "Managed by ${selectedProvider.name}.",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Primary Action Button: Save & Apply
                            Button(
                                onClick = {
                                    val finalHost = if (selectedProvider.id == "custom") customHostInput.trim() else selectedProvider.dotHost
                                    if (finalHost.isEmpty()) {
                                        Toast.makeText(context, "Please enter a valid DNS hostname", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    SoundEffectManager.playClick()
                                    DnsManager.saveSelectedDns(context, selectedProvider, finalHost)

                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("DNS Hostname", finalHost))

                                    val modeToApply = "hostname"
                                    if (DnsSecureSettingsHelper.isShizukuAvailable() && !DnsSecureSettingsHelper.hasShizukuPermission()) {
                                        DnsSecureSettingsHelper.setPendingAction {
                                            val success = DnsSecureSettingsHelper.setPrivateDns(context, modeToApply, finalHost)
                                            if (success) {
                                                refreshLiveDnsState()
                                                Toast.makeText(context, "System Private DNS updated!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        DnsSecureSettingsHelper.requestShizukuPermission(1001)
                                        Toast.makeText(context, "Authorizing Shizuku access...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val success = DnsSecureSettingsHelper.setPrivateDns(context, modeToApply, finalHost)
                                        if (success) {
                                            refreshLiveDnsState()
                                            Toast.makeText(context, "System Private DNS updated!", Toast.LENGTH_LONG).show()
                                        } else {
                                            showPermissionGuidanceDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Apply Private DNS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            // Restore Default Button
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    val success = DnsSecureSettingsHelper.setPrivateDns(context, "opportunistic", null)
                                    if (success) {
                                        refreshLiveDnsState()
                                        Toast.makeText(context, "Restored to Automatic DNS!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        DnsSecureSettingsHelper.openPrivateDnsSettings(context)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Automatic (ISP Default)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

            // DNS Speed Benchmark Test Card
            DnsSpeedBenchmarkCard(
                onSelectProvider = { provider ->
                    selectedProvider = provider
                    customHostInput = provider.dotHost
                    Toast.makeText(context, "Selected ${provider.name} DoT", Toast.LENGTH_SHORT).show()
                }
            )

            AutoSelectFastestDnsCard(
                onApplied = { provider, latency ->
                    selectedProvider = provider
                    customHostInput = provider.dotHost
                    refreshLiveDnsState()
                }
            )

            ScheduledDnsActivationCard()

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About this Provider", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedProvider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedProvider.primaryIp.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "IPv4: ${selectedProvider.primaryIp} ${if (selectedProvider.secondaryIp.isNotEmpty()) "/ ${selectedProvider.secondaryIp}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // DNS Guide & Categories Card
            var showDnsGuide by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDnsGuide = !showDnsGuide }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("DNS Guide & Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (showDnsGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (showDnsGuide) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Private DNS (DNS-over-TLS) encrypts your queries to prevent ISP tracking and injection. It primarily affects domain lookup speed, not raw bandwidth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val categories = listOf(
                                "🚀 General Speed" to "Optimized for fast resolution. Examples: Google (dns.google), Cloudflare (one.one.one.one), Quad9 (dns.quad9.net).",
                                "🛡️ Ad + Tracker Blocking" to "Filters out ads and tracking scripts. Examples: AdGuard (dns.adguard-dns.com), Mullvad (adblock.dns.mullvad.net), Control D (p2.freedns.controld.com).",
                                "🔒 Malware / Security" to "Blocks malicious domains and phishing. Examples: Cloudflare Security (security.cloudflare-dns.com), Quad9 (dns.quad9.net), CleanBrowsing Security.",
                                "👨‍👩‍👧‍👦 Family Filtering" to "Blocks adult content and enforces SafeSearch. Examples: Cloudflare Family (family.cloudflare-dns.com), AdGuard Family, CleanBrowsing Family.",
                                "⚙️ Custom Filtering" to "Highly customizable via dashboard. Example: NextDNS (dns.nextdns.io)."
                            )

                            categories.forEach { (title, desc) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "💡 Pro Tip: Use the Speed Benchmark below to find the provider with the lowest latency for your current network.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionGuidanceDialog) {
        val finalHost = if (selectedProvider.id == "custom") customHostInput.trim() else selectedProvider.dotHost
        AlertDialog(
            onDismissRequest = { showPermissionGuidanceDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Private DNS Permission", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android restricts direct changes to system Private DNS unless granted via ADB Secure Settings or authorized via Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Hostname '$finalHost' has already been copied to your clipboard! You can paste it directly in Android settings with 1 tap.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionGuidanceDialog = false
                        DnsSecureSettingsHelper.openPrivateDnsSettings(context)
                    }
                ) {
                    Text("Open Android Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val adbCmd = "adb shell pm grant com.aistudio.hypertoolkit.fresh android.permission.WRITE_SECURE_SETTINGS"
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCmd))
                        Toast.makeText(context, "ADB command copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy ADB Command")
                }
            }
        )
    }

    if (showCustomDnsDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDnsDialog = false },
            title = { Text("Add Custom DNS Server", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter your custom DNS over TLS (DoT) hostname or server address (e.g. dns.example.com):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = tempCustomInput,
                        onValueChange = { tempCustomInput = it },
                        placeholder = { Text("e.g. dot.dns.provider.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = tempCustomInput.trim()
                        if (trimmed.isNotBlank()) {
                            SoundEffectManager.playClick()
                            customHostInput = trimmed
                            selectedProvider = DnsProvider("custom", "Custom DNS", trimmed, "Custom User DNS Provider", "Custom", "", "")
                            showCustomDnsDialog = false
                        } else {
                            Toast.makeText(context, "Please enter a valid DNS hostname", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDnsDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

data class DnsBenchmarkResult(
    val provider: DnsProvider,
    val latencyMs: Long?,
    val isTesting: Boolean = false
)

@Composable
fun DnsSpeedBenchmarkCard(
    onSelectProvider: (DnsProvider) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val testTargets = remember {
        listOfNotNull(
            DnsManager.providers.find { it.id == "cloudflare" },
            DnsManager.providers.find { it.id == "google" },
            DnsManager.providers.find { it.id == "adguard" },
            DnsManager.providers.find { it.id == "quad9" },
            DnsManager.providers.find { it.id == "opendns_speed" }
        )
    }

    var results by remember {
        mutableStateOf(testTargets.map { DnsBenchmarkResult(it, null) })
    }
    var isRunningAll by remember { mutableStateOf(false) }

    fun runBenchmark() {
        if (isRunningAll) return
        isRunningAll = true
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val updated = results.map { it.copy(isTesting = true, latencyMs = null) }.toMutableList()
            withContext(kotlinx.coroutines.Dispatchers.Main) { results = updated }

            for (i in testTargets.indices) {
                val target = testTargets[i]
                val ip = target.primaryIp.ifBlank { target.dotHost }
                var latency: Long? = null
                try {
                    val start = System.currentTimeMillis()
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, 53), 1500)
                    socket.close()
                    latency = System.currentTimeMillis() - start
                } catch (_: Exception) {
                    // Fallback to reachability test
                    try {
                        val start = System.currentTimeMillis()
                        val reachable = java.net.InetAddress.getByName(ip).isReachable(1500)
                        if (reachable) {
                            latency = (System.currentTimeMillis() - start).coerceAtLeast(12L)
                        }
                    } catch (_: Exception) {
                        latency = null
                    }
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val currentList = results.toMutableList()
                    currentList[i] = DnsBenchmarkResult(target, latency, isTesting = false)
                    results = currentList
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { isRunningAll = false }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NetworkPing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("DNS Speed Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        runBenchmark()
                    },
                    enabled = !isRunningAll,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isRunningAll) {
                        GooglePlayCompactLinearProgress(
                            width = 26.dp,
                            height = 3.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Speed", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                text = "Live TCP port 53/DoT handshake latency comparison to identify the fastest resolver for your current network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Results List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.provider.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(item.provider.dotHost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (item.isTesting) {
                                    GooglePlayCompactLinearProgress(width = 36.dp, height = 4.dp)
                                } else if (item.latencyMs != null) {
                                    val ms = item.latencyMs
                                    val color = when {
                                        ms < 40 -> Color(0xFF10B981)
                                        ms < 90 -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = color.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$ms ms",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = color
                                        )
                                    }
                                } else {
                                    Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                TextButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        onSelectProvider(item.provider)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Use", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoSelectFastestDnsCard(
    onApplied: (DnsProvider, Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Auto-Select Fastest DNS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Automatically benchmark available DNS servers and instantly apply the one with the lowest latency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    if (isTesting) return@Button
                    isTesting = true
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val testTargets = listOfNotNull(
                            DnsManager.providers.find { it.id == "cloudflare" },
                            DnsManager.providers.find { it.id == "google" },
                            DnsManager.providers.find { it.id == "adguard" },
                            DnsManager.providers.find { it.id == "quad9" }
                        )

                        var bestProvider: DnsProvider? = null
                        var minLatency = Long.MAX_VALUE

                        for (target in testTargets) {
                            val ip = target.primaryIp.ifBlank { target.dotHost }
                            try {
                                val start = System.currentTimeMillis()
                                val socket = java.net.Socket()
                                socket.connect(java.net.InetSocketAddress(ip, 53), 1200)
                                socket.close()
                                val latency = System.currentTimeMillis() - start
                                if (latency < minLatency) {
                                    minLatency = latency
                                    bestProvider = target
                                }
                            } catch (_: Exception) {
                                try {
                                    val start = System.currentTimeMillis()
                                    val reachable = java.net.InetAddress.getByName(ip).isReachable(1200)
                                    if (reachable) {
                                        val latency = (System.currentTimeMillis() - start).coerceAtLeast(15L)
                                        if (latency < minLatency) {
                                            minLatency = latency
                                            bestProvider = target
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isTesting = false
                            if (bestProvider != null) {
                                DnsManager.saveSelectedDns(context, bestProvider, bestProvider.dotHost)
                                DnsSecureSettingsHelper.setPrivateDns(context, "hostname", bestProvider.dotHost)
                                onApplied(bestProvider, minLatency)
                                Toast.makeText(context, "Fastest DNS applied: ${bestProvider.name} ($minLatency ms)", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Benchmark failed. Check network connection.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    GooglePlayCompactLinearProgress(
                        width = 32.dp,
                        height = 4.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Benchmarking...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Apply Fastest DNS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ScheduledDnsActivationCard() {
    val context = LocalContext.current
    var hour by remember { mutableStateOf("08") }
    var minute by remember { mutableStateOf("00") }
    var second by remember { mutableStateOf("00") }
    var selectedPresetHost by remember { mutableStateOf("one.one.one.one") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Scheduled DNS Activation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Automatically apply a preferred DNS preset at an exact scheduled time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { if (it.length <= 2) hour = it },
                    label = { Text("Hour (0-23)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 2) minute = it },
                    label = { Text("Min (0-59)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { if (it.length <= 2) second = it },
                    label = { Text("Sec (0-59)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = selectedPresetHost,
                onValueChange = { selectedPresetHost = it },
                label = { Text("Target DoT Hostname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    val h = hour.toIntOrNull() ?: 0
                    val m = minute.toIntOrNull() ?: 0
                    val s = second.toIntOrNull() ?: 0

                    val calendar = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, h)
                        set(java.util.Calendar.MINUTE, m)
                        set(java.util.Calendar.SECOND, s)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }

                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }

                    com.arwora.utils.DnsScheduler.scheduleDns(context, calendar.timeInMillis, selectedPresetHost.trim())
                    Toast.makeText(context, "DNS scheduled for ${String.format("%02d:%02d:%02d", h, m, s)}", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Schedule DNS Activation", fontWeight = FontWeight.Bold)
            }
        }
    }
}

