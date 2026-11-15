package com.arwora.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.arwora.BuildConfig
import com.arwora.utils.AppConstants

data class PrivacyPolicySection(
    val id: String,
    val title: String,
    val summary: String,
    val bullets: List<String>
)

object PrivacyPolicyContent {
    const val EFFECTIVE_DATE = "November 2026"

    val sections: List<PrivacyPolicySection> = listOf(
        PrivacyPolicySection(
            id = "offline_commitment",
            title = "1. Offline-First & Zero Telemetry",
            summary = "Arwora Hyper Toolkit is engineered as an offline-first system diagnostics and creative utility suite.",
            bullets = listOf(
                "No cloud analytics, crash telemetry, ad SDKs, or behavioral tracking are included.",
                "No personal profile, contacts, or generated records are uploaded to external servers.",
                "All utility generators, password/name builders, and hardware diagnostics execute locally on your device."
            )
        ),
        PrivacyPolicySection(
            id = "local_storage",
            title = "2. Local Storage & Data Retention",
            summary = "Preferences and history are stored strictly in private device storage under your full control.",
            bullets = listOf(
                "Theme colors, favorites, saved text snippets, and generator history remain locally in app DataStore and Room storage.",
                "Exported backups and extracted APKs are written only when you explicitly initiate an export.",
                "You can permanently erase cached history or favorites at any time from Settings -> Data & Storage."
            )
        ),
        PrivacyPolicySection(
            id = "permission_transparency",
            title = "3. Permission Transparency",
            summary = "Optional elevated and hardware permissions are only used for the specific tools you open:",
            bullets = listOf(
                "Shizuku & Wireless ADB: Used strictly on-device for user-initiated App Ops inspection, animation scaling, and Private DNS toggling.",
                "Storage & Media / Photo Picker: Used locally for Storage Analyzer, APK extraction, HTML-to-APK packaging, metadata inspection/editing, and file exports.",
                "Microphone (RECORD_AUDIO): Used in real time by the Sound Direction analyzer; audio is processed locally on-device and never transmitted.",
                "Notification Access: Used solely to populate your private local Notification History Log inside the app.",
                "Location & Usage Stats: Used locally for GPS diagnostics, user-initiated Fake GPS testing, Wi-Fi info, and app usage breakdown."
            )
        ),
        PrivacyPolicySection(
            id = "network_usage",
            title = "4. User-Initiated Network Tools",
            summary = "Network requests occur only when you deliberately trigger an online utility.",
            bullets = listOf(
                "Features such as DNS lookup, Social media URL downloading, or opening external links only contact the destination requested by you.",
                "No background syncing or hidden telemetry occurs when offline."
            )
        ),
        PrivacyPolicySection(
            id = "user_control",
            title = "5. Your Control & Policy Updates",
            summary = "You remain in complete control of permissions and locally stored data.",
            bullets = listOf(
                "Any granted permission can be revoked immediately in Android System Settings.",
                "Uninstalling Arwora Hyper Toolkit removes all private app-sandboxed databases and preferences.",
                "Policy updates are packaged transparently with application version releases."
            )
        )
    )
}

private fun iconForPrivacySection(sectionId: String): ImageVector = when (sectionId) {
    "offline_commitment" -> Icons.Default.Shield
    "local_storage" -> Icons.Default.Storage
    "permission_transparency" -> Icons.Default.Security
    "network_usage" -> Icons.Default.Public
    "user_control" -> Icons.Default.VerifiedUser
    else -> Icons.Default.Lock
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    }
    val cardElevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))
    val cardShape = RoundedCornerShape(22.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("privacy_policy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .testTag("privacy_policy_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "privacy_header") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = cardBorder,
                        elevation = cardElevation
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppConstants.APP_NAME,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Version ${BuildConfig.VERSION_NAME} • Updated ${PrivacyPolicyContent.EFFECTIVE_DATE}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                            ) {
                                Text(
                                    text = "100% Offline & Local-First Privacy Commitment",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Text(
                                text = "This Privacy Policy explains how Arwora Hyper Toolkit handles local data, device storage, and optional system permissions on your Android device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(
                    items = PrivacyPolicyContent.sections,
                    key = { it.id }
                ) { section ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_section_${section.id}"),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = cardBorder,
                        elevation = cardElevation
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconForPrivacySection(section.id),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = section.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                section.bullets.forEach { bullet ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = bullet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "privacy_footer_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
