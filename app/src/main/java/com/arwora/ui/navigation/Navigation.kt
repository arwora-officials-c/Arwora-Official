package com.arwora.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.arwora.ui.screens.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
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
fun MainAppScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val darkModeState by viewModel.darkMode.collectAsStateWithLifecycle()
    val whiteThemeState by viewModel.whiteTheme.collectAsStateWithLifecycle()
    val isDarkTheme = (darkModeState ?: isSystemInDarkTheme()) && !whiteThemeState

    Box(modifier = Modifier.fillMaxSize()) {
        NameGenNavHost(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
            ) {
                CustomBottomNavigationBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    isDarkTheme = isDarkTheme
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

@Composable
fun NameGenNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
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

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
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
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.GenerateDashboard.route) {
            GenerateDashboardScreen(navController = navController, viewModel = viewModel)
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
        composable(Screen.SystemDashboard.route) {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.arwora.NameGenApplication
            SystemDashboardScreen(navController = navController, appManagerViewModel = app.appManagerViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
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
        composable(Screen.Social.route) {
            SocialScreen(navController = navController, viewModel = viewModel)
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
