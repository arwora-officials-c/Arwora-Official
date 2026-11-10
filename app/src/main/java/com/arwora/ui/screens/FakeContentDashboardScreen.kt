package com.arwora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.arwora.utils.SoundEffectManager

data class FakeContentItem(val name: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeContentDashboardScreen(navController: NavController) {
    val items = listOf(
        FakeContentItem("WhatsApp", Icons.Default.Chat),
        FakeContentItem("Imo", Icons.Default.Call),
        FakeContentItem("Messenger", Icons.Default.Message),
        FakeContentItem("Instagram", Icons.Default.CameraAlt),
        FakeContentItem("TikTok", Icons.Default.PlayArrow),
        FakeContentItem("Twitter/X", Icons.Default.Share),
        FakeContentItem("Pinterest", Icons.Default.Image),
        FakeContentItem("Snapchat", Icons.Default.Camera),
        FakeContentItem("bKash Bill", Icons.Default.AccountBalanceWallet),
        FakeContentItem("Nagad Bill", Icons.Default.AccountBalanceWallet)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fake Chat & Bill", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable {
                            SoundEffectManager.playClick()
                            if (item.name.contains("Bill")) {
                                navController.navigate("fake_bill_gen/${item.name}")
                            } else {
                                navController.navigate("fake_chat_gen/${item.name}")
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = item.icon, contentDescription = item.name, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
