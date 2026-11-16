package com.arwora.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.launch

@Composable
fun WirelessAdbGuidanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isConnected = NativeAdbManager.isAdbReady()
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Wireless ADB Privileges",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "Wireless ADB Connected & Ready" else "Wireless ADB Disconnected",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "Built-in Wireless ADB allows Arwora Hyper Toolkit to execute elevated system commands directly over local Wi-Fi without needing external helper apps or root access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "How to enable:\n1. Connect to Wi-Fi\n2. Enable Wireless Debugging in Developer Options\n3. Tap Connect Now to bind the local ADB daemon",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        NativeAdbManager.connectAutomatically(context)
                    }
                    onDismiss()
                }
            ) {
                Text("Connect Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AccessibilityGuidanceDialog(
    title: String = "Accessibility Service Required",
    description: String = "Auto Clicker requires Android Accessibility Service permission to perform automatic screen tap gestures and swipe macros on your behalf.",
    serviceName: String = "Auto Clicker Service",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Accessibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Quick Setup Steps:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Tap 'Open Accessibility' below\n2. Find '$serviceName' (or 'Arwora Hyper Toolkit')\n3. Switch the service toggle to ON",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Open Accessibility")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UsageStatsGuidanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.QueryStats,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Usage Access Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To monitor locked apps in real-time and provide app screen time analytics, Android requires 'Usage Access' permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Please locate 'Arwora Hyper Toolkit' in the list and enable 'Permit usage access'.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    try {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open Usage Access settings", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Grant Usage Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BatteryOptimizationGuidanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Disable Battery Optimization",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Many device manufacturers (Xiaomi, Samsung, Oppo, Vivo, OnePlus) aggressively kill background services like App Locker and Battery Alarms to save power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Exempting Arwora Hyper Toolkit ensures your app locker stays active without being unexpectedly killed by the OS.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    com.arwora.utils.AppLockerManager.requestIgnoreBatteryOptimization(context)
                }
            ) {
                Text("Whitelist App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
