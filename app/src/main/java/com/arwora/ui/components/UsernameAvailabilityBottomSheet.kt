package com.arwora.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.MainViewModel
import com.arwora.utils.AvailabilityState
import com.arwora.utils.PlatformAvailabilityResult
import com.arwora.utils.SocialPlatformSpec
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.UsernameAvailabilityChecker

private fun platformVectorIcon(platformId: String): ImageVector = when (platformId) {
    "instagram" -> Icons.Default.PhotoCamera
    "tiktok" -> Icons.Default.MusicNote
    "github" -> Icons.Default.Code
    "telegram" -> Icons.AutoMirrored.Filled.Send
    "youtube" -> Icons.Default.PlayArrow
    "twitter_x" -> Icons.Default.Tag
    "twitch" -> Icons.Default.Videocam
    "reddit" -> Icons.Default.Forum
    else -> Icons.Default.Public
}

private fun openPlatformUrl(
    context: Context,
    url: String,
    viewModel: MainViewModel?
): Boolean {
    SoundEffectManager.playClick(context)
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        if (viewModel != null) {
            viewModel.showFloatingToast("Cannot open browser")
        } else {
            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
        }
        false
    }
}

private fun copyPlatformLink(
    context: Context,
    url: String,
    viewModel: MainViewModel?
) {
    SoundEffectManager.vibrate(context, 35L)
    SoundEffectManager.playClick(context)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Profile Link", url))
    if (viewModel != null) {
        viewModel.showFloatingToast("Link copied!")
    } else {
        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Reusable inline expandable availability panel shown underneath the generated username hero card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineUsernameAvailabilityCard(
    visible: Boolean,
    username: String,
    onCollapse: () -> Unit,
    viewModel: MainViewModel? = null,
    compactMode: Boolean = false,
    onExternalLinkOpened: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && username.isNotBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val cleanHandle = UsernameAvailabilityChecker.sanitizeHandle(username)
        val resultsMap = remember(cleanHandle) { mutableStateMapOf<String, PlatformAvailabilityResult>() }
        var isChecking by remember(cleanHandle) { mutableStateOf(false) }
        var checkTrigger by remember(cleanHandle) { mutableIntStateOf(0) }

        LaunchedEffect(visible, cleanHandle, checkTrigger) {
            if (!visible || cleanHandle.isEmpty()) return@LaunchedEffect
            isChecking = true
            UsernameAvailabilityChecker.supportedPlatforms.forEach { spec ->
                resultsMap[spec.id] = PlatformAvailabilityResult(
                    platform = spec,
                    state = AvailabilityState.CHECKING,
                    statusNote = "Checking @$cleanHandle..."
                )
            }
            try {
                UsernameAvailabilityChecker.checkAllPlatforms(context, cleanHandle) { result ->
                    resultsMap[result.platform.id] = result
                }
            } finally {
                isChecking = false
            }
        }

        val results = UsernameAvailabilityChecker.supportedPlatforms.map { spec ->
            resultsMap[spec.id] ?: PlatformAvailabilityResult(spec, AvailabilityState.IDLE)
        }
        val availableCount = results.count { it.state == AvailabilityState.AVAILABLE }
        val takenCount = results.count { it.state == AvailabilityState.TAKEN }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(
                    if (compactMode) "floating_inline_username_availability_card"
                    else "inline_username_availability_card"
                ),
            shape = RoundedCornerShape(if (compactMode) 14.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compactMode) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE AVAILABILITY",
                            style = if (compactMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "@$cleanHandle",
                            style = if (compactMode) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(if (compactMode) 18.dp else 20.dp)
                                    .testTag(
                                        if (compactMode) "floating_availability_loading"
                                        else "inline_availability_loading"
                                    ),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick(context)
                                    checkTrigger++
                                },
                                modifier = Modifier
                                    .size(if (compactMode) 40.dp else 48.dp)
                                    .testTag("inline_availability_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-check availability",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(if (compactMode) 18.dp else 22.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                onCollapse()
                            },
                            modifier = Modifier
                                .size(if (compactMode) 40.dp else 48.dp)
                                .testTag("inline_availability_collapse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close availability results",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (compactMode) 18.dp else 22.dp)
                            )
                        }
                    }
                }

                // Status Summary Row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    AvailabilitySummaryChip(
                        label = "$availableCount Available",
                        dotColor = Color(0xFF16A34A)
                    )
                    AvailabilitySummaryChip(
                        label = "$takenCount Taken",
                        dotColor = Color(0xFFDC2626)
                    )
                    if (isChecking) {
                        Text(
                            text = "Querying...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))

                Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp)) {
                    results.forEach { item ->
                        PlatformAvailabilityRow(
                            result = item,
                            handle = cleanHandle,
                            compactMode = compactMode,
                            onRowClick = {
                                val target = item.platform.targetActionUrl(item.state, cleanHandle)
                                val opened = openPlatformUrl(context, target, viewModel)
                                if (opened) {
                                    onExternalLinkOpened?.invoke()
                                }
                            },
                            onCopyLink = {
                                copyPlatformLink(
                                    context,
                                    item.platform.profileUrl(cleanHandle),
                                    viewModel
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameAvailabilityBottomSheet(
    visible: Boolean,
    username: String,
    onDismissRequest: () -> Unit,
    viewModel: MainViewModel? = null
) {
    if (!visible) return

    val context = LocalContext.current
    val cleanHandle = UsernameAvailabilityChecker.sanitizeHandle(username)
    val resultsMap = remember(cleanHandle) { mutableStateMapOf<String, PlatformAvailabilityResult>() }
    var isChecking by remember(cleanHandle) { mutableStateOf(false) }
    var checkTrigger by remember(cleanHandle) { mutableIntStateOf(0) }

    LaunchedEffect(visible, cleanHandle, checkTrigger) {
        if (!visible || cleanHandle.isEmpty()) return@LaunchedEffect
        isChecking = true
        UsernameAvailabilityChecker.supportedPlatforms.forEach { spec ->
            resultsMap[spec.id] = PlatformAvailabilityResult(
                platform = spec,
                state = AvailabilityState.CHECKING,
                statusNote = "Checking @$cleanHandle..."
            )
        }
        try {
            UsernameAvailabilityChecker.checkAllPlatforms(context, cleanHandle) { result ->
                resultsMap[result.platform.id] = result
            }
        } finally {
            isChecking = false
        }
    }

    val results = UsernameAvailabilityChecker.supportedPlatforms.map { spec ->
        resultsMap[spec.id] ?: PlatformAvailabilityResult(spec, AvailabilityState.IDLE)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username Availability Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$cleanHandle",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { checkTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-check")
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val availableCount = results.count { it.state == AvailabilityState.AVAILABLE }
            val takenCount = results.count { it.state == AvailabilityState.TAKEN }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvailabilitySummaryChip(
                    label = "$availableCount Available",
                    dotColor = Color(0xFF16A34A)
                )
                AvailabilitySummaryChip(
                    label = "$takenCount Taken",
                    dotColor = Color(0xFFDC2626)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(results, key = { it.platform.id }) { item ->
                    PlatformAvailabilityRow(
                        result = item,
                        handle = cleanHandle,
                        onRowClick = {
                            val target = item.platform.targetActionUrl(item.state, cleanHandle)
                            openPlatformUrl(context, target, viewModel)
                        },
                        onCopyLink = {
                            copyPlatformLink(
                                context,
                                item.platform.profileUrl(cleanHandle),
                                viewModel
                            )
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AvailabilitySummaryChip(
    label: String,
    dotColor: Color
) {
    Surface(
        shape = CircleShape,
        color = dotColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        }
    }
}

@Composable
private fun PlatformAvailabilityRow(
    result: PlatformAvailabilityResult,
    handle: String,
    compactMode: Boolean = false,
    onRowClick: () -> Unit,
    onCopyLink: () -> Unit
) {
    val platform = result.platform
    val accentColor = Color(platform.colorHex)
    val stateColor = when (result.state) {
        AvailabilityState.AVAILABLE -> Color(0xFF16A34A)
        AvailabilityState.TAKEN -> Color(0xFFDC2626)
        AvailabilityState.CHECKING -> MaterialTheme.colorScheme.primary
        AvailabilityState.UNVERIFIED -> Color(0xFFD97706)
        AvailabilityState.OFFLINE -> Color(0xFF64748B)
        AvailabilityState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onRowClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (compactMode) 48.dp else 60.dp)
            .testTag("platform_availability_row_${platform.id}"),
        shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compactMode) 8.dp else 12.dp,
                    vertical = if (compactMode) 8.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactMode) 28.dp else 40.dp)
                    .clip(RoundedCornerShape(if (compactMode) 8.dp else 10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = platformVectorIcon(platform.id),
                    contentDescription = platform.name,
                    tint = accentColor,
                    modifier = Modifier.size(if (compactMode) 16.dp else 20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.name,
                    style = if (compactMode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compactMode) {
                    Text(
                        text = result.statusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status Badge
            Surface(
                shape = CircleShape,
                color = stateColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, stateColor.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compactMode) 7.dp else 8.dp,
                        vertical = if (compactMode) 3.dp else 4.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (result.state == AvailabilityState.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (compactMode) 10.dp else 12.dp),
                            strokeWidth = 2.dp,
                            color = stateColor
                        )
                    } else {
                        Icon(
                            imageVector = when (result.state) {
                                AvailabilityState.AVAILABLE -> Icons.Default.CheckCircle
                                AvailabilityState.TAKEN -> Icons.Default.Cancel
                                AvailabilityState.OFFLINE -> Icons.Default.WifiOff
                                else -> Icons.Default.HelpOutline
                            },
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(if (compactMode) 11.dp else 13.dp)
                        )
                    }
                    Text(
                        text = result.state.badgeLabel,
                        fontSize = if (compactMode) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (!compactMode) {
                IconButton(
                    onClick = onCopyLink,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy ${platform.name} link for @$handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open ${platform.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (compactMode) 15.dp else 18.dp)
            )
        }
    }
}
