package com.arwora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.utils.SoundEffectManager
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeDataGeneratorScreen(navController: NavController) {
    val clipboardManager = LocalClipboardManager.current
    var generatedData by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf("Select a category") }

    val categories = listOf(
        "NID Number" to { generateNid() },
        "Credit Card" to { generateCreditCard() },
        "Person Profile" to { "John Doe\nAge: 30\nGender: Male" },
        "Address" to { "123 Main St, Springfield, IL 62704" },
        "Phone Number" to { "+1 (555) 010-${Random.nextInt(1000, 9999)}" },
        "Email" to { "tester${Random.nextInt(100, 999)}@example.com" }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fake Data Generator", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Data Display Area
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                shape = ArworaDesignTokens.CardShape,
                colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                border = ArworaDesignTokens.cardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = currentTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = generatedData.ifBlank { "Generated data will appear here" }, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (generatedData.isNotBlank()) {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        clipboardManager.setText(AnnotatedString(generatedData))
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
                }
            }

            // Categories
            Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            categories.forEach { (name, generator) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        SoundEffectManager.playClick()
                        currentTitle = name
                        generatedData = generator()
                    },
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun generateNid(): String = Random.nextLong(1000000000L, 9999999999L).toString()
private fun generateCreditCard(): String = "4" + Random.nextLong(100000000000000L, 999999999999999L).toString()
