package com.arwora.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.AutoVideoScrollCard
import com.arwora.ui.components.ExpressiveCompactLoadingIndicator
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.SoundEffectManager
import com.arwora.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // OCR state
    var isOcrExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var extractedText by rememberSaveable { mutableStateOf("") }
    var isOcrProcessing by remember { mutableStateOf(false) }
    var ocrErrorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri.toString()
            extractedText = ""
            ocrErrorMessage = null
            isOcrProcessing = true
            isOcrExpanded = true

            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        isOcrProcessing = false
                        if (visionText.text.isNotBlank()) {
                            extractedText = visionText.text
                            viewModel.showFloatingToast("Text extracted successfully!")
                        } else {
                            ocrErrorMessage = "No text detected in this image. Try another photo with clear text."
                            viewModel.showFloatingToast("No text detected in image.")
                        }
                    }
                    .addOnFailureListener { e ->
                        isOcrProcessing = false
                        ocrErrorMessage = "Could not recognize text: ${e.localizedMessage ?: "Unknown error"}"
                        viewModel.showFloatingToast("Text extraction failed.")
                    }
            } catch (e: Exception) {
                isOcrProcessing = false
                ocrErrorMessage = "Could not process image: ${e.localizedMessage}"
                viewModel.showFloatingToast("Failed to load image.")
            }
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Social Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.DownloadHistory.route)
                    }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Download History",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val cardBgColor = ArworaDesignTokens.cardContainerColor()
        val cardShape = ArworaDesignTokens.CardShape
        val cardBorder = ArworaDesignTokens.cardBorder()
        val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== HEADER ====================
            item {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Social & Media Privacy",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Inspect or clean photo and video EXIF metadata before sharing, and copy text from images.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        // ==================== AUTO VIDEO SCROLL CARD ====================
        item {
            AutoVideoScrollCard(
                onToastMessage = { msg -> viewModel.showFloatingToast(msg) }
            )
        }

        // ==================== MEDIA METADATA (PHOTO & VIDEO) CARD ====================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = {
                    SoundEffectManager.playClick()
                    navController.navigate(Screen.ImageMetadataEditor.route)
                },
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = cardElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title and Icon Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArworaTonalIconBadge(
                            icon = Icons.Default.Tune,
                            size = 46.dp,
                            iconSize = 23.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Media Metadata & Privacy",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Inspect, edit, or wipe EXIF & video tags, GPS",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ArworaPillBadge(text = "PHOTO & VIDEO")
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )

                    // EXIF & Video Capability Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Privacy & Media Tools",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val mediaFeatures = listOf("GPS Location", "Camera Specs", "Video Codecs", "Wipe Privacy")
                            mediaFeatures.forEach { name ->
                                Surface(
                                    shape = ArworaDesignTokens.PillShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = ArworaDesignTokens.innerTileBorder()
                                ) {
                                    Text(
                                        text = name,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        softWrap = false,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Protect your privacy on social media by viewing hidden EXIF & media metadata or stripping sensitive GPS coordinates and device identifiers from photos and videos before sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Action Buttons: Open Metadata Viewer & Strip EXIF / Inspect & Edit
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                navController.navigate(Screen.MetadataViewer.route)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = ArworaDesignTokens.PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Metadata Viewer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    navController.navigate(Screen.ImageMetadataEditor.route)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = ArworaDesignTokens.PillShape
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Strip All EXIF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    navController.navigate(Screen.ImageMetadataEditor.route)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = ArworaDesignTokens.PillShape,
                                border = ArworaDesignTokens.cardBorder()
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Inspect & Edit",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== IMAGE TO COPY IMAGE TEXT (OCR) CARD ====================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = cardElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isOcrExpanded) 16.dp else 0.dp)
                ) {
                    // Title and Icon Header (Clickable row to expand/collapse)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                SoundEffectManager.playClick()
                                isOcrExpanded = !isOcrExpanded
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArworaTonalIconBadge(
                            icon = Icons.Default.DocumentScanner,
                            size = 46.dp,
                            iconSize = 23.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Image to Text OCR",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isOcrExpanded) "Pick image & extract text with one tap" else "Extract and copy text from any image or photo",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ArworaPillBadge(text = "OCR")
                    }

                    // Collapsible Content
                    AnimatedVisibility(
                        visible = isOcrExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )

                            // Capability Chips
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Smart Optical Recognition",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val ocrFeatures = listOf("Instant OCR", "On-Device", "Multi-Line", "Fast Copy")
                                    ocrFeatures.forEach { name ->
                                        Surface(
                                            shape = ArworaDesignTokens.PillShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = ArworaDesignTokens.innerTileBorder()
                                        ) {
                                            Text(
                                                text = name,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                softWrap = false,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Selected Image Preview (if any)
                            if (selectedImageUri != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = "Selected image preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            // Processing state (M3 Expressive Loading + Wavy Progress)
                            if (isOcrProcessing) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExpressiveCompactLoadingIndicator(size = 18.dp)
                                        Text(
                                            text = "Scanning and recognizing text in image...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    ExpressiveWavyLinearProgressIndicator(
                                        progress = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        height = 10.dp
                                    )
                                }
                            }

                            // Extracted text display
                            if (extractedText.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Extracted Text (${extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size} words)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        TextButton(
                                            onClick = {
                                                SoundEffectManager.playClick()
                                                extractedText = ""
                                                selectedImageUri = null
                                                ocrErrorMessage = null
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Clear",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = extractedText,
                                        onValueChange = { extractedText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp, max = 220.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                    )
                                }

                                // Quick Copy to Clipboard Button
                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = ClipData.newPlainText("Extracted Text", extractedText)
                                            clipboard?.setPrimaryClip(clip)
                                            viewModel.showFloatingToast("Text copied to clipboard!")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not copy text", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = ArworaDesignTokens.PillShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = "Copy to Clipboard",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Error or Empty message
                            if (ocrErrorMessage != null && extractedText.isBlank() && !isOcrProcessing) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ocrErrorMessage ?: "No text detected in this image.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            // Image Picker Action Button
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = ArworaDesignTokens.PillShape,
                                colors = if (extractedText.isNotBlank()) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            ) {
                                Text(
                                    text = if (selectedImageUri != null) "Pick Another Image" else "Pick Image from Gallery",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
}
