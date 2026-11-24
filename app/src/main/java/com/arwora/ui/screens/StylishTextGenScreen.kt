package com.arwora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.SymbolControlPanel
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.StylishTextEngine
import com.arwora.utils.StylishTextVariant
import com.arwora.utils.SymbolCategory
import com.arwora.utils.SymbolPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylishTextGenScreen(navController: NavController, viewModel: MainViewModel) {
    var inputText by rememberSaveable { mutableStateOf("") }
    var selectedSymbol by rememberSaveable { mutableStateOf("") }
    var selectedPositionName by rememberSaveable { mutableStateOf(SymbolPosition.FIRST.name) }
    var selectedCategoryName by rememberSaveable { mutableStateOf(SymbolCategory.ALL.name) }

    val selectedPosition = remember(selectedPositionName) {
        runCatching { SymbolPosition.valueOf(selectedPositionName) }.getOrDefault(SymbolPosition.FIRST)
    }
    val selectedCategory = remember(selectedCategoryName) {
        runCatching { SymbolCategory.valueOf(selectedCategoryName) }.getOrDefault(SymbolCategory.ALL)
    }

    val context = LocalContext.current
    val fontStyle by viewModel.fontStyle.collectAsStateWithLifecycle()
    var showFontSelector by remember { mutableStateOf(false) }

    val variants by produceState<List<StylishTextVariant>>(
        initialValue = emptyList(),
        inputText,
        selectedSymbol,
        selectedPosition,
        fontStyle
    ) {
        value = if (inputText.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                StylishTextEngine.generateAllVariants(
                    inputText = inputText,
                    selectedSymbol = selectedSymbol,
                    position = selectedPosition,
                    prioritizedStyle = fontStyle
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Stylish Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFontSelector = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("font_style_selector_button")
                    ) {
                        Icon(Icons.Default.FontDownload, contentDescription = "Font Style")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Enter your text") },
                    placeholder = { Text("Type something...") },
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_text_input_button")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Text")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stylish_text_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = ArworaDesignTokens.cardBorderColor()
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("stylish_variants_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item(key = "symbol_control_panel") {
                        SymbolControlPanel(
                            selectedPosition = selectedPosition,
                            onPositionSelected = { selectedPositionName = it.name },
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategoryName = it.name },
                            selectedSymbol = selectedSymbol,
                            onSymbolSelected = { selectedSymbol = it },
                            onClearSymbol = { selectedSymbol = "" }
                        )
                    }

                    if (variants.isEmpty()) {
                        item(key = "empty_state") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stylish_empty_state"),
                                shape = ArworaDesignTokens.CardShape,
                                colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                                border = ArworaDesignTokens.cardBorder(),
                                elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Type some text above and tap any symbol to preview all ${com.arwora.utils.FontStyles.styles.size} stylish fonts live.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = variants,
                            key = { it.styleName }
                        ) { variant ->
                            StylishTextCard(
                                styleName = variant.styleName,
                                styledText = variant.decoratedText,
                                isPreferredFont = variant.styleName == fontStyle,
                                viewModel = viewModel,
                                context = context,
                                onCopy = {
                                    viewModel.addHistory("STYLISH_TEXT", variant.decoratedText)
                                },
                                onShare = {
                                    shareStyledText(context, variant.decoratedText)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    FontStyleBottomSheet(
        visible = showFontSelector,
        onDismissRequest = { showFontSelector = false },
        currentStyle = fontStyle,
        onStyleSelected = { viewModel.setFontStyle(it) }
    )
}

internal fun shareStyledText(context: Context, text: String) {
    try {
        SoundEffectManager.playClick(context)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Stylish Text").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "No compatible sharing app found", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun StylishTextCard(
    styleName: String,
    styledText: String,
    viewModel: MainViewModel,
    context: Context,
    onCopy: () -> Unit,
    isPreferredFont: Boolean = false,
    onShare: () -> Unit = { shareStyledText(context, styledText) }
) {
    val isFav by viewModel.isFavorite("STYLISH_TEXT", styledText).collectAsStateWithLifecycle(initialValue = false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stylish_card_$styleName"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = styleName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isPreferredFont) {
                    ArworaPillBadge(text = "Preferred")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = styledText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("styled_text_$styleName")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        copyToClipboard(context, styledText, "Stylish Text", viewModel)
                        onCopy()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("copy_styled_$styleName")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy $styleName",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_styled_$styleName")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share $styleName",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        SoundEffectManager.playClick(context)
                        if (isFav) viewModel.removeFavorite("STYLISH_TEXT", styledText)
                        else viewModel.addFavorite("STYLISH_TEXT", styledText)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("fav_styled_$styleName")
                ) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite $styleName",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
