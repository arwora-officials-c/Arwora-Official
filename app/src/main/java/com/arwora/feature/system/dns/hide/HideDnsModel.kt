package com.arwora.feature.system.dns.hide

import android.graphics.drawable.Drawable

enum class AppCategory {
    USER,
    SYSTEM
}

data class HideDnsAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isHidden: Boolean = false,
    val icon: Drawable? = null,
    val versionName: String = "",
    val category: AppCategory = if (isSystemApp) AppCategory.SYSTEM else AppCategory.USER
)

enum class HideDnsTab {
    VISIBLE,
    HIDDEN
}

sealed interface HideDnsUiState {
    object Loading : HideDnsUiState
    data class Success(
        val isMasterEnabled: Boolean,
        val selectedTab: HideDnsTab,
        val searchQuery: String,
        val visibleApps: List<HideDnsAppItem>,
        val hiddenApps: List<HideDnsAppItem>,
        val totalAppsCount: Int,
        val hiddenCount: Int,
        val isWifiConnected: Boolean,
        val isAdbConnected: Boolean
    ) : HideDnsUiState
    data class Error(val message: String) : HideDnsUiState
}
