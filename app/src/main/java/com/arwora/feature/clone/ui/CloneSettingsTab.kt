package com.arwora.feature.clone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneSettingsTab(viewModel: CloneViewModel) {
    val isAdvancedMode by viewModel.isAdvancedModeEnabled.collectAsState()
    var engineExpanded by remember { mutableStateOf(false) }
    var selectedEngine by remember { mutableStateOf("Virtual Container") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("General", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        
        ExposedDropdownMenuBox(
            expanded = engineExpanded,
            onExpandedChange = { engineExpanded = !engineExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedEngine,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Engine") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = engineExpanded,
                onDismissRequest = { engineExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Virtual Container") },
                    onClick = {
                        selectedEngine = "Virtual Container"
                        engineExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Work Profile") },
                    onClick = {
                        selectedEngine = "Work Profile"
                        engineExpanded = false
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Storage location", style = MaterialTheme.typography.bodyLarge)
                Text("app-private only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        Text("Data & Privacy", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

        Button(onClick = { /* Stub */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear all clone caches")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { /* Stub */ }, modifier = Modifier.weight(1f)) {
                Text("Export Metadata")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { /* Stub */ }, modifier = Modifier.weight(1f)) {
                Text("Import Metadata")
            }
        }

        HorizontalDivider()
        Text("Advanced", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Advanced (Repackage) mode", style = MaterialTheme.typography.titleMedium)
                Text("Requires REQUEST_INSTALL_PACKAGES", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Switch(
                checked = isAdvancedMode,
                onCheckedChange = { viewModel.setAdvancedMode(it) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cloning may trigger Play Protect prompts. Ensure you comply with app Terms of Service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
