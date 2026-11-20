package com.arwora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.arwora.ui.navigation.Screen

import androidx.compose.material3.ripple
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

fun NavController.navigateSingleTopWithRestore(route: String) {
    this.navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(this@navigateSingleTopWithRestore.graph.findStartDestination().id) {
            saveState = true
        }
    }
}

@Immutable
data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Generate", Screen.GenerateDashboard.route, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    BottomNavItem("Social", Screen.Social.route, Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed),
    BottomNavItem("System", Screen.SystemDashboard.route, Icons.Filled.Dns, Icons.Outlined.Dns),
    BottomNavItem("Settings", Screen.Settings.route, Icons.Filled.Settings, Icons.Outlined.Settings)
)

internal fun indexOfMainTabRoute(route: String?): Int {
    if (route == null) return -1
    return bottomNavItems.indexOfFirst { it.route == route }
}

internal fun computeHopPageBeforeAnimate(currentPage: Int, targetPage: Int): Int? {
    val distance = kotlin.math.abs(targetPage - currentPage)
    return if (distance > 1) {
        if (targetPage > currentPage) targetPage - 1 else targetPage + 1
    } else {
        null
    }
}

@Composable
fun CustomBottomNavigationBar(
    navController: NavController,
    currentDestination: NavDestination?,
    isDarkTheme: Boolean,
    selectedTabIndex: Int? = null,
    onTabSelected: ((Int, BottomNavItem) -> Unit)? = null
) {
    val pillShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkTheme) Color(0xE615151C) else Color(0xEEFFFFFF),
            border = BorderStroke(1.dp, if (isDarkTheme) Color(0x2EFFFFFF) else Color(0x50E2E8F0)),
            shadowElevation = if (isDarkTheme) 2.dp else 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    val isSelected = if (selectedTabIndex != null) {
                        selectedTabIndex == index
                    } else {
                        currentDestination?.hierarchy?.any { it.route == item.route } == true
                    }
                    val selectedPillColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    }
                    val selectedTintColor = MaterialTheme.colorScheme.primary
                    val unselectedTintColor = MaterialTheme.colorScheme.onSurfaceVariant

                    val pillBgColor by animateColorAsState(
                        targetValue = if (isSelected) selectedPillColor else Color.Transparent,
                        animationSpec = tween(
                            durationMillis = ArworaMotionTokens.LIST_ENTRY_DURATION_MS,
                            easing = ArworaMotionTokens.StandardEasing
                        ),
                        label = "nav_pill_bg"
                    )
                    val pillBorderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                        animationSpec = tween(
                            durationMillis = ArworaMotionTokens.LIST_ENTRY_DURATION_MS,
                            easing = ArworaMotionTokens.StandardEasing
                        ),
                        label = "nav_pill_border"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) selectedTintColor else unselectedTintColor,
                        animationSpec = tween(
                            durationMillis = ArworaMotionTokens.LIST_ENTRY_DURATION_MS,
                            easing = ArworaMotionTokens.StandardEasing
                        ),
                        label = "nav_content_tint"
                    )
                    val pillScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.94f,
                        animationSpec = tween(
                            durationMillis = ArworaMotionTokens.LIST_ENTRY_DURATION_MS,
                            easing = ArworaMotionTokens.StandardEasing
                        ),
                        label = "nav_pill_scale"
                    )
                    val itemInteractionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("nav_item_${item.title.lowercase()}")
                            .clickable(
                                interactionSource = itemInteractionSource,
                                indication = null
                            ) {
                                com.arwora.utils.SoundEffectManager.playClick()
                                if (onTabSelected != null) {
                                    onTabSelected(index, item)
                                } else {
                                    navController.navigateSingleTopWithRestore(item.route)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 30.dp)
                                .graphicsLayer {
                                    scaleX = pillScale
                                    scaleY = pillScale
                                }
                                .clip(pillShape)
                                .background(pillBgColor, pillShape)
                                .border(
                                    width = 1.dp,
                                    color = pillBorderColor,
                                    shape = pillShape
                                )
                                .indication(
                                    interactionSource = itemInteractionSource,
                                    indication = ripple(bounded = true, color = selectedTintColor)
                                )
                                .testTag("nav_pill_${item.title.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title,
                                modifier = Modifier.size(20.dp),
                                tint = contentTint
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            color = contentTint,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
