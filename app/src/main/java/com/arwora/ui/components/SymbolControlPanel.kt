package com.arwora.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.StylishTextEngine
import com.arwora.utils.SymbolCategory
import com.arwora.utils.SymbolItem
import com.arwora.utils.SymbolPosition

@Composable
fun SymbolControlPanel(
    selectedPosition: SymbolPosition,
    onPositionSelected: (SymbolPosition) -> Unit,
    selectedCategory: SymbolCategory,
    onCategorySelected: (SymbolCategory) -> Unit,
    selectedSymbol: String,
    onSymbolSelected: (String) -> Unit,
    onClearSymbol: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleSymbols = remember(selectedCategory) {
        StylishTextEngine.symbolsFor(selectedCategory)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("symbol_control_panel"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.AutoFixHigh,
                        size = 38.dp,
                        iconSize = 20.dp,
                        cornerRadius = 12.dp
                    )
                    Column {
                        Text(
                            text = "Symbol Decorator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${visibleSymbols.size} Unicode symbols • ${selectedPosition.label} placement",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selectedSymbol.isNotEmpty()) {
                        ArworaPillBadge(
                            text = selectedSymbol,
                            modifier = Modifier.testTag("selected_symbol_badge")
                        )
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onClearSymbol()
                        },
                        enabled = selectedSymbol.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_symbol_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Symbol",
                            tint = if (selectedSymbol.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }

            // Equal-width 3-button Position Segmented Group: First | Middle | Last
            SymbolPositionSegmentedRow(
                selectedPosition = selectedPosition,
                onPositionSelected = onPositionSelected
            )

            // Horizontal Scrollable Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("symbol_category_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(
                    items = SymbolCategory.entries,
                    key = { it.name }
                ) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            SoundEffectManager.playClick()
                            onCategorySelected(category)
                        },
                        label = {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        shape = ArworaDesignTokens.PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = ArworaDesignTokens.cardBorderColor(),
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("symbol_category_${category.name.lowercase()}")
                    )
                }
            }

            // Two-row horizontal virtualized grid for fast 60/120 FPS browsing across 150+ symbols
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .testTag("symbol_grid"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(
                    items = visibleSymbols,
                    key = { it.symbol }
                ) { item ->
                    SymbolGridTile(
                        item = item,
                        isSelected = item.symbol == selectedSymbol,
                        onClick = {
                            SoundEffectManager.playClick()
                            if (item.symbol == selectedSymbol) {
                                onClearSymbol()
                            } else {
                                onSymbolSelected(item.symbol)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SymbolPositionSegmentedRow(
    selectedPosition: SymbolPosition,
    onPositionSelected: (SymbolPosition) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(ArworaDesignTokens.PillShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.PillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SymbolPosition.entries.forEach { position ->
            val isSelected = position == selectedPosition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(ArworaDesignTokens.PillShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            SoundEffectManager.playClick()
                            onPositionSelected(position)
                        }
                    )
                    .padding(vertical = 10.dp)
                    .testTag("symbol_position_${position.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SymbolGridTile(
    item: SymbolItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    val border = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        ArworaDesignTokens.innerTileBorder()
    }

    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "${item.label} (${item.symbol})" }
            .testTag("symbol_tile_${item.symbol}"),
        onClick = onClick,
        shape = ArworaDesignTokens.InnerTileShape,
        color = containerColor,
        border = border
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.symbol,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
