package com.arwora.feature.system.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.screens.SystemCard
import com.arwora.utils.SoundEffectManager

@Composable
fun ShellCommandCard(
    viewModel: ShellCommandViewModel,
    modifier: Modifier = Modifier
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    val badgeColor = when (status) {
        ShellStatus.READY -> Color(0xFF10B981)
        ShellStatus.ADB_REQUIRED -> MaterialTheme.colorScheme.error
        ShellStatus.WIFI_REQUIRED -> MaterialTheme.colorScheme.error
        ShellStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }

    SystemCard(
        modifier = modifier,
        onClick = {
            SoundEffectManager.playClick()
            showSheet = true
        }
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Shell Command",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = "Shell Command",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Run advanced shell commands using Wireless ADB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }

    if (showSheet) {
        ShellCommandSheet(
            viewModel = viewModel,
            onDismissRequest = { showSheet = false }
        )
    }
}
