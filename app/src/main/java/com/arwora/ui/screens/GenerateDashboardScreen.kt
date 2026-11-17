package com.arwora.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import android.os.SystemClock
import android.util.Log
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.navigation.Screen

@Immutable
data class ToolItem(val name: String, val icon: ImageVector, val route: String, val color: androidx.compose.ui.graphics.Color? = null)

@Composable
private fun getGeneratorTileIconColor(isWhiteTheme: Boolean): androidx.compose.ui.graphics.Color {
    return if (isWhiteTheme) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateDashboardScreen(navController: NavController, viewModel: MainViewModel) {
    val t0 = remember { SystemClock.uptimeMillis() }
    SideEffect {
        Log.d("Tabs", "GenerateDashboardScreen switched in ${SystemClock.uptimeMillis() - t0}ms")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onGenerateVisible()
        }
    }

    val isWhiteTheme by viewModel.whiteTheme.collectAsStateWithLifecycle()
    val tileIconColor = getGeneratorTileIconColor(isWhiteTheme)

    val tools = listOf(
        ToolItem("Name Generator", Icons.Filled.People, Screen.FirstMiddleGen.route),
        ToolItem("Username Generator", Icons.Filled.AlternateEmail, Screen.UsernameGen.route),
        ToolItem("Nickname Generator", Icons.Filled.Face, Screen.NicknameGen.route),
        ToolItem("Stylish Text Generator", Icons.Filled.FontDownload, Screen.StylishTextGen.route),
        
        // Other tools
        ToolItem("Passwords", Icons.Filled.Lock, Screen.PasswordGen.route),
        ToolItem("Bio Maker", Icons.Filled.TextSnippet, Screen.BioGen.route),
        ToolItem("Random Profile", Icons.Filled.AssignmentInd, Screen.RandomProfile.route),
        ToolItem("Name Mixer", Icons.Filled.Shuffle, Screen.NameMixer.route),
        ToolItem("Username Builder", Icons.Filled.Build, Screen.UsernameBuilder.route),
        ToolItem("Text Saver", Icons.Filled.Save, Screen.TextSaver.route),
        ToolItem("Text Repeat", Icons.Filled.Repeat, Screen.TextRepeat.route),
        ToolItem("Age Calculator", Icons.Filled.Cake, Screen.AgeCalculator.route),
        ToolItem("Fake Chat & Bill", Icons.Filled.ChatBubble, Screen.FakeContentDashboard.route),
        ToolItem("Fake Data Generator", Icons.Filled.DataArray, Screen.FakeDataGenerator.route),
        ToolItem("Coin Toss", Icons.Filled.Casino, Screen.CoinToss.route)
    )

    val context = LocalContext.current
    val history by viewModel.allHistory.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
        val cardBgColor = ArworaDesignTokens.cardContainerColor()
        val cardShape = ArworaDesignTokens.CardShape
        val cardBorder = ArworaDesignTokens.cardBorder()
        val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        navController.navigate(Screen.History.route)
                    },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = cardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ArworaTonalIconBadge(
                                    icon = Icons.Filled.History,
                                    size = 38.dp,
                                    iconSize = 20.dp,
                                    cornerRadius = 12.dp
                                )
                                Text(
                                    text = "Generation History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            ArworaPillBadge(text = "View All")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (history.isEmpty()) {
                            Text(
                                text = "Your generation history is empty. Try creating some names or passwords below!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                history.take(3).forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(ArworaDesignTokens.InnerTileShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                            .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                                            .clickable {
                                                copyToClipboard(context, item.content, item.type, viewModel)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                 text = item.content,
                                                 style = MaterialTheme.typography.bodyLarge,
                                                 fontWeight = FontWeight.SemiBold,
                                                 color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                 text = item.type,
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                 fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(tools) { tool ->
                val resolvedTint = tool.color ?: tileIconColor
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp),
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        navController.navigate(tool.route)
                    },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = cardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        ArworaTonalIconBadge(
                            icon = tool.icon,
                            contentDescription = tool.name,
                            tint = resolvedTint,
                            size = ArworaDesignTokens.IconBadgeSize,
                            iconSize = 22.dp
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}
