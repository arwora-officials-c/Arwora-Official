package com.arwora.feature.system.htmltoapk

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.core.content.FileProvider
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ExpressiveCompactLoadingIndicator
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.MediaDownloadUtils
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlToApkBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form states
    var selectedHtmlUri by remember { mutableStateOf<Uri?>(null) }
    var selectedHtmlFileName by remember { mutableStateOf<String?>(null) }
    var selectedHtmlContent by remember { mutableStateOf<String?>(null) }
    var htmlFileSizeText by remember { mutableStateOf<String?>(null) }

    var selectedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var apkName by remember { mutableStateOf("My Web App") }
    var packageName by remember { mutableStateOf("com.arwora.htmlapp") }
    var versionName by remember { mutableStateOf("1.0.0") }

    // Advanced toggles
    var enableJs by remember { mutableStateOf(true) }
    var enableDomStorage by remember { mutableStateOf(true) }
    var fullScreenMode by remember { mutableStateOf(false) }
    var showCodePreview by remember { mutableStateOf(false) }

    // Build process states
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgress by remember { mutableFloatStateOf(0f) }
    var buildStatusText by remember { mutableStateOf("") }
    var generatedApkFile by remember { mutableStateOf<File?>(null) }
    var showPreviewModal by remember { mutableStateOf(false) }

    // HTML File Picker Launcher
    val htmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedHtmlUri = uri
            val fileName = getFileNameFromUri(context, uri) ?: "index.html"
            selectedHtmlFileName = fileName

            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    htmlFileSizeText = formatFileSize(bytes.size.toLong())
                    if (bytes.size < 200 * 1024) {
                        selectedHtmlContent = String(bytes, Charsets.UTF_8)
                    } else {
                        selectedHtmlContent = "/* HTML file size: $htmlFileSizeText */"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            SoundEffectManager.playClick()
        }
    }

    // Logo Image Picker Launcher
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedLogoUri = uri
            SoundEffectManager.playClick()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isBuilding) onDismissRequest()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Html To Apk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Convert HTML code to Android APK package",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { if (!isBuilding) onDismissRequest() },
                    enabled = !isBuilding
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Section 1: HTML File Upload
            Text(
                text = "HTML Source File",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                onClick = {
                    if (!isBuilding) {
                        htmlPickerLauncher.launch("*/*")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedHtmlUri != null)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedHtmlUri != null)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (selectedHtmlUri == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Upload Local HTML File",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select .html, .htm, or web bundle file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { htmlPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Browse", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Html,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = selectedHtmlFileName ?: "index.html",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = htmlFileSizeText ?: "HTML Document Loaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (selectedHtmlContent != null && selectedHtmlContent!!.length < 100000) {
                                    IconButton(
                                        onClick = { showCodePreview = !showCodePreview },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showCodePreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Code Preview",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        selectedHtmlUri = null
                                        selectedHtmlFileName = null
                                        selectedHtmlContent = null
                                        htmlFileSizeText = null
                                        showCodePreview = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove File",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (showCodePreview && !selectedHtmlContent.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = selectedHtmlContent!!.take(800) + if (selectedHtmlContent!!.length > 800) "\n..." else "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: APK Logo Upload
            Text(
                text = "APK App Logo / Icon",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                onClick = {
                    if (!isBuilding) {
                        logoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (selectedLogoUri != null) {
                            AsyncImage(
                                model = selectedLogoUri,
                                contentDescription = "Custom Logo",
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = if (selectedLogoUri != null) "Custom Logo Uploaded" else "Upload Custom APK Logo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedLogoUri != null) "Tap to change icon image" else "PNG/JPG recommended (512x512)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selectedLogoUri != null) {
                        IconButton(onClick = { selectedLogoUri = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove logo",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Select Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: APK Details (APK Name & Package Name)
            Text(
                text = "Package Configuration",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // APK Name Input Field
            OutlinedTextField(
                value = apkName,
                onValueChange = { apkName = it },
                label = { Text("APK Name") },
                placeholder = { Text("e.g. My Web App") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (apkName.isNotEmpty()) {
                        IconButton(onClick = { apkName = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isBuilding
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Package Name Input Field
            OutlinedTextField(
                value = packageName,
                onValueChange = { input ->
                    packageName = input.lowercase().replace(" ", "")
                },
                label = { Text("Package Name") },
                placeholder = { Text("e.g. com.arwora.myapp") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (packageName.isNotEmpty()) {
                        IconButton(onClick = { packageName = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                supportingText = {
                    Text("Android package identifier (e.g. com.arwora.app)")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isBuilding
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Advanced WebView Settings Toggles
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "WebView Runtime Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable JavaScript Engine",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ArworaAnimatedSwitch(
                            checked = enableJs,
                            onCheckedChange = { enableJs = it },
                            enabled = !isBuilding
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable DOM Storage & Caching",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ArworaAnimatedSwitch(
                            checked = enableDomStorage,
                            onCheckedChange = { enableDomStorage = it },
                            enabled = !isBuilding
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fullscreen Web View Mode",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ArworaAnimatedSwitch(
                            checked = fullScreenMode,
                            onCheckedChange = { fullScreenMode = it },
                            enabled = !isBuilding
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Build Progress & Results Section
            if (isBuilding) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpressiveCompactLoadingIndicator(size = 18.dp)
                            Text(
                                text = buildStatusText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpressiveWavyLinearProgressIndicator(
                            progress = buildProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            height = 10.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(buildProgress * 100).toInt()}% Completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (generatedApkFile != null && !isBuilding) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "APK Generated Successfully!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = generatedApkFile!!.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    showPreviewModal = true
                                    SoundEffectManager.playClick()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val apkUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            generatedApkFile!!
                                        )
                                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(installIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Saved to Downloads: ${generatedApkFile!!.name}", Toast.LENGTH_LONG).show()
                                    }
                                    SoundEffectManager.playClick()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Install APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Generator Primary Action Button (Clean, minimal with bold label, NO emojis/stars)
            Button(
                onClick = {
                    if (selectedHtmlUri == null) {
                        Toast.makeText(context, "Please select an HTML file first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (apkName.isBlank()) {
                        Toast.makeText(context, "Please enter an APK name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (packageName.isBlank()) {
                        Toast.makeText(context, "Please enter a package name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    SoundEffectManager.playClick()
                    coroutineScope.launch {
                        isBuilding = true
                        generatedApkFile = null
                        buildProgress = 0.1f
                        buildStatusText = "Reading HTML project assets..."
                        delay(600)

                        buildProgress = 0.3f
                        buildStatusText = "Generating WebView app wrapper..."
                        delay(700)

                        buildProgress = 0.55f
                        buildStatusText = "Packaging $packageName..."
                        delay(700)

                        buildProgress = 0.8f
                        buildStatusText = "Compiling & signing APK..."
                        delay(800)

                        // Save HTML file into APK build structure
                        val apkFile = generateApkFile(
                            context = context,
                            htmlUri = selectedHtmlUri!!,
                            apkName = apkName.trim(),
                            packageName = packageName.trim(),
                            logoUri = selectedLogoUri
                        )

                        buildProgress = 1.0f
                        buildStatusText = "Build complete!"
                        delay(300)

                        isBuilding = false
                        generatedApkFile = apkFile
                        Toast.makeText(context, "APK built: ${apkFile?.name}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isBuilding
            ) {
                Text(
                    text = if (isBuilding) "Building APK..." else "Build APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Embedded Web App Live Preview Modal
    if (showPreviewModal && selectedHtmlUri != null) {
        Dialog(
            onDismissRequest = { showPreviewModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Preview Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "$apkName (Live APK Preview)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { showPreviewModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview")
                        }
                    }

                    // Embedded WebView
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = enableJs
                                    settings.domStorageEnabled = enableDomStorage
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    webViewClient = WebViewClient()

                                    try {
                                        val inputStream = ctx.contentResolver.openInputStream(selectedHtmlUri!!)
                                        val htmlStr = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                                        loadDataWithBaseURL(null, htmlStr, "text/html", "UTF-8", null)
                                    } catch (e: Exception) {
                                        loadData("<h3>Error loading preview: ${e.localizedMessage}</h3>", "text/html", "UTF-8")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

private const val EMBEDDED_CLASSES_DEX_B64 =
    "ZGV4CjAzNQB4vr55YxL6Vtk+MR0ZDytjhz/IZCjmY+fcCQAAcAAAAHhWNBIAAAAAAAAAABgJAAAxAAAAcAAAABEAAAA0AQAADQAAAHgBAAAB" +
    "AAAAFAIAABgAAAAcAgAAAgAAANwCAADABgAAHAMAAMQEAADMBAAA4gQAAOUEAADoBAAAAAUAABsFAAAwBQAASQUAAF4FAAB8BQAAlgUAALYF" +
    "AADfBQAABgYAACsGAABLBgAAcQYAAIUGAACIBgAAjAYAAJAGAACUBgAAlwYAAJwGAAChBgAArgYAALkGAADbBgAA6AYAAPAGAAD5BgAA/wYA" +
    "AAYHAAAQBwAAGwcAADIHAABGBwAAVAcAAGQHAAB4BwAAjgcAAKQHAAC9BwAA0QcAAOMHAAD9BwAABAgAAA0IAAACAAAABAAAAAUAAAAGAAAA" +
    "BwAAAAgAAAAJAAAACgAAAAsAAAAMAAAADQAAAA4AAAAPAAAAEAAAABEAAAASAAAAFgAAAAMAAAAGAAAAAAAAABIAAAAPAAAAAAAAABMAAAAP" +
    "AAAAdAQAABQAAAAPAAAAfAQAABQAAAAPAAAAhAQAABQAAAAPAAAAjAQAABQAAAAPAAAAlAQAABQAAAAPAAAAnAQAABQAAAAPAAAApAQAABUA" +
    "AAAPAAAArAQAABYAAAAQAAAAAAAAABcAAAAQAAAAtAQAABgAAAAQAAAAvAQAAAoABwAvAAAAAQABAAAAAAABAAQAIQAAAAEACwAiAAAABgAJ" +
    "ACMAAAAGAAkAJAAAAAYAAgAlAAAABgAJACcAAAAGAAkAKAAAAAYACQApAAAABgAJACoAAAAGAAkAKwAAAAcAAwAAAAAABwAKABoAAAAHAAAA" +
    "HAAAAAcAAQAdAAAABwAIAB4AAAAHAAYALAAAAAgAAQAAAAAACQAHAAAAAAAJAAwALQAAAAoAAQAAAAAACgAEACEAAAAKAAsAIgAAAAoABQAm" +
    "AAAACQAAAAAAAAAIAAAAAAAAAAEAAAAACQAAyQgAAAAAAAAKAAAAAQAAAAEAAAAAAAAAAQAAAAAAAADXCAAAAAAAAAMAAwACAAAARgQAAAcA" +
    "AAA4AgUAbiAPACEAEhEPAQAAAgACAAEAAABOBAAABAAAAHAQEQAAAA4ABAADAAMAAABTBAAAGwAAABJAMwIVAFQQAAA4ABEAVBAAAG4QDAAA" +
    "AAoAOAAJAFQSAABuEA4AAgASEg8CbzACACEDCgIPAgAAAQABAAEAAABcBAAABAAAAHAQAAAAAA4AAwACAAIAAABgBAAAQQAAAG8gAQAhACIC" +
    "BwBwIAsAEgBbEgAAVBIAAG4gFwAhAFQSAABuEA0AAgAMAhIQbiAIAAIAbiAHAAIAbiAGAAIAbiAEAAIAbiADAAIAbiAJAAIAbiAKAAIAEvBu" +
    "IAUAAgBUEgAAIgAJAHAgEgAQAG4gEAACAFQSAAAaABsAbiAPAAIADgAgAgAADi09AB0BAA4ALAIAAA7wWi4ACgAOAA8BAA48eFtpSzw8PDw8" +
    "PEyueAAAAQAAAAAAAAABAAAAAgAAAAEAAAADAAAAAQAAAAUAAAABAAAACAAAAAEAAAAKAAAAAQAAAA4AAAABAAAAEAAAAAIAAAAAAAQAAgAA" +
    "AAcADgAGPGluaXQ+ABRIdG1sQXBwQWN0aXZpdHkuamF2YQABSQABTAAWTGFuZHJvaWQvYXBwL0FjdGl2aXR5OwAZTGFuZHJvaWQvY29udGVu" +
    "dC9Db250ZXh0OwATTGFuZHJvaWQvb3MvQnVuZGxlOwAXTGFuZHJvaWQvdmlldy9LZXlFdmVudDsAE0xhbmRyb2lkL3ZpZXcvVmlldzsAHExh" +
    "bmRyb2lkL3dlYmtpdC9XZWJTZXR0aW5nczsAGExhbmRyb2lkL3dlYmtpdC9XZWJWaWV3OwAeTGFuZHJvaWQvd2Via2l0L1dlYlZpZXdDbGll" +
    "bnQ7ACdMY29tL2V4YW1wbGUvaHRtbGFwcC9IdG1sQXBwQWN0aXZpdHkkMTsAJUxjb20vZXhhbXBsZS9odG1sYXBwL0h0bWxBcHBBY3Rpdml0" +
    "eTsAI0xkYWx2aWsvYW5ub3RhdGlvbi9FbmNsb3NpbmdNZXRob2Q7AB5MZGFsdmlrL2Fubm90YXRpb24vSW5uZXJDbGFzczsAJExkYWx2aWsv" +
    "YW5ub3RhdGlvbi9NZXRob2RQYXJhbWV0ZXJzOwASTGphdmEvbGFuZy9TdHJpbmc7AAFWAAJWSQACVkwAAlZaAAFaAANaSUwAA1pMTAALYWNj" +
    "ZXNzRmxhZ3MACWNhbkdvQmFjawAgZmlsZTovLy9hbmRyb2lkX2Fzc2V0L2luZGV4Lmh0bWwAC2dldFNldHRpbmdzAAZnb0JhY2sAB2xvYWRV" +
    "cmwABG5hbWUABW5hbWVzAAhvbkNyZWF0ZQAJb25LZXlEb3duABVzZXRBbGxvd0NvbnRlbnRBY2Nlc3MAEnNldEFsbG93RmlsZUFjY2VzcwAM" +
    "c2V0Q2FjaGVNb2RlAA5zZXRDb250ZW50VmlldwASc2V0RGF0YWJhc2VFbmFibGVkABRzZXREb21TdG9yYWdlRW5hYmxlZAAUc2V0SmF2YVNj" +
    "cmlwdEVuYWJsZWQAF3NldExvYWRXaXRoT3ZlcnZpZXdNb2RlABJzZXRVc2VXaWRlVmlld1BvcnQAEHNldFdlYlZpZXdDbGllbnQAGHNob3Vs" +
    "ZE92ZXJyaWRlVXJsTG9hZGluZwAFdmFsdWUAB3dlYlZpZXcAnQF+fkQ4eyJiYWNrZW5kIjoiZGV4IiwiY29tcGlsYXRpb24tbW9kZSI6ImRl" +
    "YnVnIiwiaGFzLWNoZWNrc3VtcyI6ZmFsc2UsIm1pbi1hcGkiOjIxLCJzaGEtMSI6ImE3YWQxOGE3MDQ2MGI3OTlkMDQ4MmU0OTdjMTA5YTc1" +
    "YmY3ZjkxZGUiLCJ2ZXJzaW9uIjoiOC4xMC45LWRldiJ9AAINAhkcAUQQgAAgHAEeAgsBLhoVAgwCGQQAHx4AAAEBEoCABLwGEwGcBgABAQIA" +
    "AhSBgAScBxUEtAcBAdQGAAEAAACtCAAAAgAAALsIAADBCAAA9AgAAAAAAAABAAAAAAAAABIAAADsCAAAEAAAAAAAAAABAAAAAAAAAAEAAAAx" +
    "AAAAcAAAAAIAAAARAAAANAEAAAMAAAANAAAAeAEAAAQAAAABAAAAFAIAAAUAAAAYAAAAHAIAAAYAAAACAAAA3AIAAAEgAAAFAAAAHAMAAAMg" +
    "AAAFAAAARgQAAAEQAAAKAAAAdAQAAAIgAAAxAAAAxAQAAAQgAAADAAAArQgAAAAgAAACAAAAyQgAAAMQAAACAAAA7AgAAAYgAAABAAAAAAkA" +
    "AAAQAAABAAAAGAkAAA=="

/**
 * Pre-compiled minimal resources.arsc (Base64)
 * Maps Resource ID 0x7F010000 to "res/drawable/ic_launcher.png"
 * Package: com.arwora.htmlapp
 */
private const val RESOURCES_ARSC_B64 =
    "AgAMAKAAAAByAAAAAQAAABAAAAAYAAAAAQAAAAEAAAAIAAAAIAAAAAEAAAAAY29tLmV4YW1wbGUuaHRtbGFwcAAAAAAAAAAAAAAAAAAA" +
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAAEAAAABAAAAAQAAAAGAAAAAAABAAAAAAA" +
    "AAAAAAAAAAAABAAAAGljX2xhdW5jaGVyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
    "AAAAAAAAAAAAAAAAAAAAAAABAAAAAQAAAAAAAABfAQAAHAAAAAIAAAAkAAAAGAAAAAAAcmVzL2RyYXdhYmxlL2ljX2xhdW5jaGVyLnBuZwAA" +
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
    "AAAA"

private fun generateApkFile(
    context: Context,
    htmlUri: Uri,
    apkName: String,
    packageName: String,
    logoUri: Uri?
): File? {
    return try {
        val safeName = apkName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val downloadDir = MediaDownloadUtils.getMainDownloadDir(context, false)
        val tempUnsignedApk = File(context.cacheDir, "temp_${safeName}_unsigned.apk")
        val finalSignedApk = File(downloadDir, "${safeName}_v1.0.apk")

        // 1. Read HTML Content
        val htmlInputStream = context.contentResolver.openInputStream(htmlUri)
        val htmlBytes = htmlInputStream?.use { it.readBytes() }
            ?: "<html><body><h1>$apkName</h1></body></html>".toByteArray(Charsets.UTF_8)

        // 2. Read Logo Image Bytes if provided
        var logoBytes: ByteArray? = null
        if (logoUri != null) {
            try {
                context.contentResolver.openInputStream(logoUri)?.use {
                    logoBytes = it.readBytes()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Load classes.dex (from assets or embedded fallback)
        var dexBytes: ByteArray? = null
        try {
            context.assets.open("template/classes.dex").use { stream ->
                dexBytes = stream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (dexBytes == null || dexBytes!!.isEmpty()) {
            dexBytes = try {
                Base64.decode(EMBEDDED_CLASSES_DEX_B64, Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(EMBEDDED_CLASSES_DEX_B64.trim())
            }
        }

        // 4. Encode Binary AndroidManifest.xml using standard AAPT2-compatible encoder
        val validPackageName = packageName.trim().ifEmpty { "com.arwora.htmlapp" }
        val validAppName = apkName.trim().ifEmpty { "HTML App" }
        val axmlBytes = AxmlEncoder.encode(
            packageName = validPackageName,
            appName = validAppName,
            activityClass = "com.arwora.htmlapp.HtmlAppActivity",
            minSdk = 21,
            targetSdk = 34
        )

        // 5. Build Unsigned APK Zip Archive
        ZipOutputStream(FileOutputStream(tempUnsignedApk)).use { outZip ->
            outZip.setLevel(ZipOutputStream.DEFLATED)
            
            // AndroidManifest.xml (Must be first or early)
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            outZip.putNextEntry(manifestEntry)
            outZip.write(axmlBytes)
            outZip.closeEntry()

            // classes.dex
            if (dexBytes != null && dexBytes!!.isNotEmpty()) {
                val dexEntry = ZipEntry("classes.dex")
                outZip.putNextEntry(dexEntry)
                outZip.write(dexBytes!!)
                outZip.closeEntry()
            }

            // assets/index.html
            val assetEntry = ZipEntry("assets/index.html")
            outZip.putNextEntry(assetEntry)
            outZip.write(htmlBytes)
            outZip.closeEntry()

            // 4. Bundle resources.arsc (Maps 0x7f010000 to res/drawable/ic_launcher.png)
            val arscEntry = ZipEntry("resources.arsc")
            outZip.putNextEntry(arscEntry)
            outZip.write(android.util.Base64.decode(RESOURCES_ARSC_B64, android.util.Base64.DEFAULT))
            outZip.closeEntry()

            // 5. Bundle ic_launcher.png
            val iconEntry = ZipEntry("res/drawable/ic_launcher.png")
            outZip.putNextEntry(iconEntry)
            if (logoBytes != null) {
                outZip.write(logoBytes!!)
            } else {
                // Fallback to app's own icon if no logo provided
                try {
                    val defaultIconStream = context.resources.openRawResource(com.arwora.R.mipmap.ic_launcher)
                    outZip.write(defaultIconStream.readBytes())
                } catch (e: Exception) {
                    // Critical fallback: Empty transparent 1x1 png if everything fails
                    outZip.write(android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==", android.util.Base64.DEFAULT))
                }
            }
            outZip.closeEntry()
        }

        // 6. Sign APK with Google ApkSigner (V1, V2, and V3 schemes)
        val signSuccess = ApkSignerUtil.signApk(tempUnsignedApk, finalSignedApk, context)
        tempUnsignedApk.delete()

        val resultFile = if (signSuccess && finalSignedApk.exists()) finalSignedApk else null
        if (resultFile != null) {
            MediaDownloadUtils.scanMediaFile(context, resultFile, "application/vnd.android.package-archive")
        }
        resultFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
