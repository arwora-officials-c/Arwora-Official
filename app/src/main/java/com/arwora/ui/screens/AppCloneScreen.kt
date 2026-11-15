package com.arwora.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import com.arwora.feature.clone.ui.CheckStatus
import com.arwora.feature.clone.ui.CloneCheck
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Policy
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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.navigation.NavController
import com.arwora.feature.clone.ui.CreateCloneTab
import com.arwora.feature.clone.ui.MyClonesTab
import com.arwora.feature.clone.ui.CloneSettingsTab
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.clone.ui.CloneViewModel
import com.arwora.ui.components.GoogleStyleLoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCloneScreen(navController: NavController, viewModel: CloneViewModel = viewModel()) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Create", "My clones", "Settings")
    val myClones by viewModel.myClones.collectAsState(initial = emptyList())
    
    var showChecksSheet by remember { mutableStateOf(false) }
    val checkResults by viewModel.checkResults.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val scope = rememberCoroutineScope()
    val checksSheetState = rememberModalBottomSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App clone") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.runChecks()
                            showChecksSheet = true
                        }
                    ) {
                        Text("Run checks")
                    }
                    
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Help") },
                            onClick = { expanded = false },
                            leadingIcon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Policy") },
                            onClick = { expanded = false },
                            leadingIcon = { Icon(Icons.Outlined.Policy, contentDescription = null) }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sticky summary header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = "Clones",
                    value = "${myClones.size}",
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Storage",
                    value = "0 B / 10 GB",
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Engine",
                    value = "Virtual",
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTabIndex = 2 }
                )
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                divider = { HorizontalDivider() }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> CreateCloneTab(viewModel)
                    1 -> MyClonesTab(viewModel, onCreateNew = { selectedTabIndex = 0 })
                    2 -> CloneSettingsTab(viewModel)
                }
            }
        }
    }

    if (showChecksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChecksSheet = false },
            sheetState = checksSheetState
        ) {
            CloneChecksContent(
                results = checkResults,
                isChecking = isChecking,
                onAction = { intentAction ->
                    if (intentAction != null) {
                        try {
                            val intent = Intent(intentAction)
                            if (intentAction == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) {
                                intent.data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback if specific package URI fails
                            val intent = Intent(intentAction)
                            context.startActivity(intent)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CloneChecksContent(
    results: List<CloneCheck>,
    isChecking: Boolean,
    onAction: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "System Compatibility Checks",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = isChecking && results.isEmpty(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "app_clone_compat_loading"
        ) { loading ->
            if (loading) {
                GoogleStyleLoadingView(
                    title = "Checking system compatibility",
                    subtitle = "Inspecting Android runtime and profile capabilities...",
                    icon = Icons.Default.AssignmentTurnedIn,
                    showSkeletonRows = false,
                    scrollable = false,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    results.forEach { check ->
                        CheckItem(check = check, onAction = onAction)
                    }
                }
            }
        }
    }
}

@Composable
fun CheckItem(check: CloneCheck, onAction: (String?) -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val cardBgColor = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (check.status) {
                    CheckStatus.PASS -> Icons.Default.CheckCircle
                    CheckStatus.FAIL -> Icons.Default.Error
                    CheckStatus.WARN -> Icons.Default.Warning
                    CheckStatus.PENDING -> Icons.Default.AssignmentTurnedIn
                },
                contentDescription = null,
                tint = when (check.status) {
                    CheckStatus.PASS -> Color(0xFF4CAF50)
                    CheckStatus.FAIL -> MaterialTheme.colorScheme.error
                    CheckStatus.WARN -> Color(0xFFFFA000)
                    CheckStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = check.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (check.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = check.errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (check.status == CheckStatus.FAIL) MaterialTheme.colorScheme.error else Color(0xFFFFA000)
                    )
                }

                if (check.actionLabel != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onAction(check.actionIntent) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = check.actionLabel!!, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    }

    var baseModifier = modifier
        .clip(RoundedCornerShape(16.dp))
        
    if (onClick != null) {
        baseModifier = baseModifier.clickable { onClick() }
    }

    Card(
        modifier = baseModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
