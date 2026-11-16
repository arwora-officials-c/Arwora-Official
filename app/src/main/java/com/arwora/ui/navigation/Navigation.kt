package com.arwora.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.CustomBottomNavigationBar
import com.arwora.ui.components.bottomNavItems
import com.arwora.ui.components.computeHopPageBeforeAnimate
import com.arwora.ui.components.indexOfMainTabRoute
import com.arwora.ui.components.rememberBottomBarScrollController
import com.arwora.ui.screens.*
import kotlinx.coroutines.launch

/**
 * Allows nested screens (e.g., Home quick actions) to switch directly
 * to any main HorizontalPager tab by route, returning true if handled by the pager.
 */
val LocalMainTabNavigator = staticCompositionLocalOf<((String) -> Boolean)?> { null }

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object MainTabs : Screen("main_tabs")
    object Home : Screen("home")
    object GenerateDashboard : Screen("generate_dashboard")
    
    // Tools
    object NameGen : Screen("name_gen")
    object MiddleNameGen : Screen("middle_name_gen")
    object FirstMiddleGen : Screen("first_middle_gen")
    object UsernameGen : Screen("username_gen")
    object PasswordGen : Screen("password_gen")
    object BioGen : Screen("bio_gen")
    object NicknameGen : Screen("nickname_gen")
    object StylishTextGen : Screen("stylish_text_gen")
    object RandomProfile : Screen("random_profile")
    object NameMixer : Screen("name_mixer")
    object UsernameBuilder : Screen("username_builder")
    object TextSaver : Screen("text_saver")
    object CoinToss : Screen("coin_toss")
    object DnsChanger : Screen("dns_changer")
    object AnimationSettings : Screen("animation_settings")
    object AppList : Screen("app_list")
    object FakeGps : Screen("fake_gps")
    object Social : Screen("social")
    object SecurityScan : Screen("security_scan")
    object DeepScanner : Screen("deep_scanner")
    object ImageMetadataEditor : Screen("image_metadata_editor")
    object MetadataViewer : Screen("metadata_viewer")
    object AutoClicker : Screen("auto_clicker")
    
    object SystemDashboard : Screen("system_dashboard")
    object History : Screen("history")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object About : Screen("about")
    object PrivacyPolicy : Screen("privacy_policy")
    object DownloadHistory : Screen("download_history")
    object NotificationHistory : Screen("notification_history")
    object BatteryAlarm : Screen("battery_alarm")
    object StorageAnalyzer : Screen("storage_analyzer")
    object SoundDirection : Screen("sound_direction")
    object PermissionInspector : Screen("permission_inspector")
    object LocationHistory : Screen("location_history")
    object LocationMap : Screen("location_map")


    object AgeCalculator : Screen("age_calculator")
    object FakeContentDashboard : Screen("fake_content_dashboard")
    object FakeChatGenerator : Screen("fake_chat_gen/{templateName}") {
        fun createRoute(templateName: String) = "fake_chat_gen/$templateName"
    }
    object FakeBillGenerator : Screen("fake_bill_gen/{templateName}") {
        fun createRoute(templateName: String) = "fake_bill_gen/$templateName"
    }
    object FakeDataGenerator : Screen("fake_data_gen")
    object Calculator : Screen("calculator")
    object WifiController : Screen("wifi_controller")
    object WifiPassword : Screen("wifi_password")
    object HidePrivateDns : Screen("hide_private_dns")
    object AppClone : Screen("app_clone")
    object TextRepeat : Screen("text_repeat")
    object AppUsageStats : Screen("app_usage_stats")
    object AppLocker : Screen("app_locker")
    object AppBooster : Screen("app_booster")
    object ScreenOffOptimizer : Screen("screen_off_optimizer")
    object BatteryMonitor : Screen("battery_monitor")
    object WakelockDetector : Screen("wakelock_detector")
    object AppOps : Screen("app_ops")
}

@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    skipSplash: Boolean = false
) {
    val navController = rememberNavController()
    val darkModeState by viewModel.darkMode.collectAsStateWithLifecycle()
    val whiteThemeState by viewModel.whiteTheme.collectAsStateWithLifecycle()
    val isDarkTheme = (darkModeState ?: isSystemInDarkTheme()) && !whiteThemeState

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { bottomNavItems.size })
    val coroutineScope = rememberCoroutineScope()
    val bottomBarScrollController = rememberBottomBarScrollController()

    // Always reveal the bottom navigation bar automatically when switching tabs via swipe gestures
    LaunchedEffect(pagerState.currentPage, pagerState.targetPage, pagerState.isScrollInProgress) {
        bottomBarScrollController.reveal(animate = true)
    }

    fun selectMainTab(tabIndex: Int, animate: Boolean = true) {
        val clamped = tabIndex.coerceIn(0, bottomNavItems.lastIndex)
        bottomBarScrollController.reveal(animate = animate)
        if (navController.currentDestination?.route != Screen.MainTabs.route) {
            navController.popBackStack(Screen.MainTabs.route, inclusive = false)
        }
        coroutineScope.launch {
            if (!animate) {
                pagerState.scrollToPage(clamped)
            } else {
                val hopPage = computeHopPageBeforeAnimate(pagerState.currentPage, clamped)
                if (hopPage != null) {
                    pagerState.scrollToPage(hopPage)
                }
                pagerState.animateScrollToPage(
                    page = clamped,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    val navigateToRouteOrTab: (String) -> Boolean = remember(navController, pagerState) {
        { route ->
            val tabIdx = indexOfMainTabRoute(route)
            if (tabIdx >= 0) {
                selectMainTab(tabIdx, animate = true)
                true
            } else {
                false
            }
        }
    }

    CompositionLocalProvider(LocalMainTabNavigator provides navigateToRouteOrTab) {
        Box(modifier = Modifier.fillMaxSize()) {
            NameGenNavHost(
                navController = navController,
                viewModel = viewModel,
                pagerState = pagerState,
                skipSplash = skipSplash,
                onSelectMainTab = { index, animate -> selectMainTab(index, animate) },
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(bottomBarScrollController.nestedScrollConnection)
            )

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route
            val isOnMainTabs = currentRoute == Screen.MainTabs.route
            val showBottomBar = isOnMainTabs || bottomNavItems.any { it.route == currentRoute }

            LaunchedEffect(showBottomBar) {
                if (showBottomBar) {
                    bottomBarScrollController.reveal(animate = true)
                }
            }

            if (showBottomBar) {
                val activeTabIndex = if (isOnMainTabs) {
                    pagerState.targetPage
                } else {
                    indexOfMainTabRoute(currentRoute).takeIf { it >= 0 }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f)
                        .onSizeChanged { size ->
                            bottomBarScrollController.updateMaxOffsetPx(size.height.toFloat())
                        }
                        .graphicsLayer {
                            translationY = bottomBarScrollController.offsetPx
                        }
                        .testTag("bottom_navigation_container")
                ) {
                    CustomBottomNavigationBar(
                        navController = navController,
                        currentDestination = currentDestination,
                        isDarkTheme = isDarkTheme,
                        selectedTabIndex = activeTabIndex,
                        onTabSelected = { index, _ ->
                            selectMainTab(index, animate = true)
                        }
                    )
                }
            }

        // Animated custom floating toast notification
        val floatingToastMessage by viewModel.floatingToastMessage.collectAsStateWithLifecycle()

        val showDownloaderBottomSheet by viewModel.showDownloaderBottomSheet.collectAsStateWithLifecycle()
        if (showDownloaderBottomSheet) {
            com.arwora.ui.components.ShareDownloaderBottomSheet(viewModel = viewModel)
        }

        AnimatedVisibility(
            visible = floatingToastMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
                .padding(horizontal = 20.dp)
                .zIndex(100f)
        ) {
            floatingToastMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E2E) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.widthIn(max = 380.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alert",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.showFloatingToast("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun NameGenNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    pagerState: PagerState,
    skipSplash: Boolean = false,
    onSelectMainTab: (index: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val springSpec = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
    val fadeSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    val pagerScope = rememberCoroutineScope()

    // Prevent horizontal child scrollables (LazyRow / horizontalScroll cards) from leaking
    // edge overscroll/flings into HorizontalPager and triggering unintended tab switches.
    val antiConflictPagerNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset.Zero

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = Velocity.Zero
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (skipSplash) Screen.MainTabs.route else Screen.Splash.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = springSpec) +
                    fadeIn(animationSpec = fadeSpringSpec)
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = springSpec) +
                    fadeOut(animationSpec = fadeSpringSpec)
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = springSpec) +
                    fadeIn(animationSpec = fadeSpringSpec)
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = springSpec) +
                    fadeOut(animationSpec = fadeSpringSpec)
        }
    ) {
        composable(
            route = Screen.Splash.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 250)) }
        ) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.MainTabs.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.MainTabs.route,
            enterTransition = {
                if (initialState.destination.route == Screen.Splash.route) {
                    fadeIn(animationSpec = tween(durationMillis = 260))
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = springSpec) +
                        fadeOut(animationSpec = fadeSpringSpec)
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = springSpec) +
                        fadeIn(animationSpec = fadeSpringSpec)
            }
        ) {
            BackHandler(enabled = pagerState.currentPage != 0) {
                pagerScope.launch {
                    val hopPage = computeHopPageBeforeAnimate(pagerState.currentPage, 0)
                    if (hopPage != null) {
                        pagerState.scrollToPage(hopPage)
                    }
                    pagerState.animateScrollToPage(
                        page = 0,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { bottomNavItems[it].route },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapPositionalThreshold = 0.35f,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                pageNestedScrollConnection = antiConflictPagerNestedScroll,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("main_tab_pager")
            ) { page ->
                when (page) {
                    0 -> HomeScreen(navController = navController, viewModel = viewModel)
                    1 -> GenerateDashboardScreen(navController = navController, viewModel = viewModel)
                    2 -> SocialScreen(navController = navController, viewModel = viewModel)
                    3 -> {
                        val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.arwora.NameGenApplication
                        SystemDashboardScreen(navController = navController, appManagerViewModel = app.appManagerViewModel)
                    }
                    4 -> SettingsScreen(navController = navController, viewModel = viewModel)
                }
            }
        }

        // Seamless compatibility routes for any direct navController.navigate(Screen.<Tab>.route)
        bottomNavItems.forEachIndexed { tabIndex, tabItem ->
            composable(
                route = tabItem.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                LaunchedEffect(Unit) {
                    onSelectMainTab(tabIndex, true)
                }
            }
        }
        composable(Screen.NameGen.route) {
            NameGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.MiddleNameGen.route) {
            MiddleNameGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.FirstMiddleGen.route) {
            FirstMiddleGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.UsernameGen.route) {
            UsernameGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.PasswordGen.route) {
            PasswordGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.BioGen.route) {
            BioGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.NicknameGen.route) {
            NicknameGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.StylishTextGen.route) {
            StylishTextGenScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.RandomProfile.route) {
            RandomProfileScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.NameMixer.route) {
            NameMixerScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.UsernameBuilder.route) {
            UsernameBuilderScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.TextSaver.route) {
            TextSaverScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.CoinToss.route) {
            CoinTossScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }
        composable(Screen.Calculator.route) {
            CalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.DnsChanger.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                try {
                    val intent = android.content.Intent(context, com.arwora.DnsChangerActivity::class.java)
                    // If context is not an activity, we need NEW_TASK. 
                    // LocalContext.current is usually an Activity context, but safe to add if needed.
                    if (context !is android.app.Activity) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    navController.popBackStack()
                } catch (e: Exception) {
                    android.util.Log.e("Navigation", "Failed to launch DnsChangerActivity", e)
                    android.widget.Toast.makeText(context, "Error: Could not launch DNS Selector", android.widget.Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }
        }
        composable(Screen.AnimationSettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                try {
                    val intent = android.content.Intent(context, com.arwora.AnimationSettingsActivity::class.java)
                    if (context !is android.app.Activity) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    navController.popBackStack()
                } catch (e: Exception) {
                    android.util.Log.e("Navigation", "Failed to launch AnimationSettingsActivity", e)
                    android.widget.Toast.makeText(context, "Error: Could not launch Animation Settings", android.widget.Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }
        }
        composable(Screen.AppList.route) {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.arwora.NameGenApplication
            AppListScreen(
                viewModel = app.appManagerViewModel,
                onBack = { navController.popBackStack() },
                onOpenPermissionInspector = { navController.navigate(Screen.PermissionInspector.route) }
            )
        }
        composable(Screen.FakeGps.route) {
            FakeGpsMapScreen(navController = navController)
        }
        composable(Screen.DeepScanner.route) {
            com.arwora.ui.screens.DeepScannerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SecurityScan.route) {
            com.arwora.ui.screens.SecurityScanScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.ImageMetadataEditor.route) {
            ImageMetadataEditorScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.MetadataViewer.route) {
            MetadataViewerScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.AutoClicker.route) {
            AutoClickerScreen(navController = navController)
        }
        composable(Screen.WifiController.route) {
            com.arwora.feature.system.wifi.ui.WifiControllerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.WifiPassword.route) {
            com.arwora.feature.system.wifi.password.WifiPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HidePrivateDns.route) {
            com.arwora.feature.system.dns.hide.HidePrivateDnsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AppClone.route) {
            AppCloneScreen(navController = navController)
        }
        composable(Screen.TextRepeat.route) {
            TextRepeatScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.AppUsageStats.route) {
            ScreenActivityScreen(navController = navController, onBack = { navController.popBackStack() })
        }
        composable(Screen.DownloadHistory.route) {
            DownloadHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.NotificationHistory.route) {
            NotificationHistoryScreen(navController = navController)
        }
        composable(Screen.BatteryAlarm.route) {
            BatteryAlarmScreen(navController = navController)
        }
        composable(Screen.StorageAnalyzer.route) {
            StorageAnalyzerScreen(navController = navController)
        }
        composable(Screen.SoundDirection.route) {
            SoundDirectionScreen(navController = navController)
        }
        composable(Screen.PermissionInspector.route) {
            PermissionInspectorScreen(navController = navController)
        }
        composable(Screen.LocationHistory.route) {
            LocationHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenMap = { navController.navigate(Screen.LocationMap.route) }
            )
        }
        composable(Screen.LocationMap.route) {
            LocationMapScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AgeCalculator.route) {
            AgeCalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.FakeContentDashboard.route) {
            FakeContentDashboardScreen(navController = navController)
        }
        composable(
            route = "fake_chat_gen/{templateName}",
            arguments = listOf(navArgument("templateName") { type = NavType.StringType })
        ) { backStackEntry ->
            val templateName = backStackEntry.arguments?.getString("templateName") ?: "WhatsApp"
            FakeChatGeneratorScreen(navController = navController, initialPlatform = templateName)
        }
        composable(
            route = "fake_bill_gen/{templateName}",
            arguments = listOf(navArgument("templateName") { type = NavType.StringType })
        ) { backStackEntry ->
            val templateName = backStackEntry.arguments?.getString("templateName") ?: "bKash Bill"
            FakeBillGeneratorScreen(navController = navController, initialPlatform = templateName)
        }
        composable(Screen.FakeDataGenerator.route) {
            FakeDataGeneratorScreen(navController = navController)
        }
        composable(Screen.AppLocker.route) {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.arwora.NameGenApplication
            AppLockerScreen(navController = navController, appManagerViewModel = app.appManagerViewModel)
        }
        composable(Screen.AppBooster.route) {
            com.arwora.feature.system.booster.ui.AppBoosterScreen(navController = navController)
        }
        composable(Screen.ScreenOffOptimizer.route) {
            com.arwora.feature.system.optimizer.ui.ScreenOffOptimizerScreen(navController = navController)
        }
        composable(Screen.BatteryMonitor.route) {
            com.arwora.feature.system.battery.ui.BatteryMonitorScreen(navController = navController)
        }
        composable(Screen.WakelockDetector.route) {
            com.arwora.feature.system.process.WakelockDetectorScreen(navController = navController)
        }
        composable(Screen.AppOps.route) {
            com.arwora.feature.system.appops.ui.AppOpsScreen(onBack = { navController.popBackStack() })
        }
    }
}
