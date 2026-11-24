package com.arwora

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.installassistant.data.AppCategoryClassifier
import com.arwora.feature.system.installassistant.data.OnlineFetchResponse
import com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager
import com.arwora.feature.system.installassistant.model.AppInstallCategory
import com.arwora.feature.system.installassistant.model.OnlineSyncStatus
import com.arwora.feature.system.installassistant.model.SetupChecklistStep
import com.arwora.feature.system.installassistant.ui.SmartInstallAssistantCard
import com.arwora.feature.system.installassistant.ui.SmartInstallAssistantScreen
import com.arwora.feature.system.installassistant.ui.SmartInstallAssistantViewModel
import com.arwora.receiver.AppInstallReceiver
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class SmartInstallAssistantTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SmartInstallAssistantManager.clearForTests(context)
    }

    @After
    fun tearDown() {
        SmartInstallAssistantManager.clearForTests(context)
    }

    private fun installFakePackage(
        packageName: String,
        label: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
        requestedPermissions: Array<String> = emptyArray(),
        grantedFlags: IntArray = IntArray(0)
    ) {
        val shadowPm = shadowOf(context.packageManager)
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.name = label
            this.nonLocalizedLabel = label
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.category = category
            }
        }
        val pkgInfo = PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = appInfo
            this.versionName = "2.4.1"
            this.longVersionCode = 241L
            this.firstInstallTime = System.currentTimeMillis() - 60_000L
            this.lastUpdateTime = System.currentTimeMillis()
            this.requestedPermissions = requestedPermissions
            this.requestedPermissionsFlags = grantedFlags
        }
        shadowPm.installPackage(pkgInfo)
    }

    @Test
    fun classifierMapsNativeAndKeywordCategoriesAccurately() {
        val gameInfo = ApplicationInfo().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                category = ApplicationInfo.CATEGORY_GAME
            }
        }
        assertEquals(
            AppInstallCategory.GAMES,
            AppCategoryClassifier.classify(gameInfo, "com.example.myapp", "My App")
        )
        assertEquals(
            AppInstallCategory.SOCIAL,
            AppCategoryClassifier.classifyByKeywords("com.whatsapp", "WhatsApp")
        )
        assertEquals(
            AppInstallCategory.PRODUCTIVITY,
            AppCategoryClassifier.classifyByKeywords("com.notion.id", "Notion Notes")
        )
        assertEquals(
            AppInstallCategory.UTILITIES,
            AppCategoryClassifier.classifyByKeywords("com.example.dnsscanner", "DNS Utility")
        )
        assertEquals(
            AppInstallCategory.FINANCE,
            AppCategoryClassifier.classifyByKeywords("com.bKash.customerapp", "bKash Wallet")
        )
    }

    @Test
    fun receiverDetectsPackageAddedAuditsDangerousPermissionsAndPostsNotification() = runBlocking {
        val targetPkg = "com.example.cameranotes"
        installFakePackage(
            packageName = targetPkg,
            label = "Camera Notes",
            category = ApplicationInfo.CATEGORY_PRODUCTIVITY,
            requestedPermissions = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET
            ),
            grantedFlags = intArrayOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                0,
                PackageInfo.REQUESTED_PERMISSION_GRANTED
            )
        )

        // Grant POST_NOTIFICATIONS for Robolectric process
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val receiver = AppInstallReceiver()
        receiver.handlePackageEventSuspend(
            context = context,
            packageName = targetPkg,
            isUpdate = false
        )

        val records = SmartInstallAssistantManager.records.value
        val record = records.firstOrNull { it.packageName == targetPkg }
        assertNotNull("Expected installed package record to be saved", record)
        assertEquals("Camera Notes", record!!.appName)
        assertEquals(AppInstallCategory.PRODUCTIVITY, record.category)
        assertFalse(record.isUpdate)

        // Verify dangerous permissions audit captured CAMERA (granted) & ACCESS_FINE_LOCATION (denied), skipping normal INTERNET
        assertEquals(2, record.dangerousPermissions.size)
        assertEquals(1, record.grantedDangerousCount)
        assertTrue(record.dangerousPermissions.any { it.groupLabel == "Camera" && it.isGranted })
        assertTrue(record.dangerousPermissions.any { it.groupLabel == "Location" && !it.isGranted })

        // Verify notification posted
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).allNotifications
        assertTrue("Expected install assistant notification", posted.isNotEmpty())
    }

    @Test
    fun broadcastFilterSkipsDuplicatePackageAddedWhenReplacing() {
        val pkg = "com.example.updatedapp"
        installFakePackage(pkg, "Updated App", category = ApplicationInfo.CATEGORY_SOCIAL)
        val receiver = AppInstallReceiver()

        // Should ignore intermediate PACKAGE_ADDED with EXTRA_REPLACING = true without recording
        val duplicateAddedIntent = Intent(Intent.ACTION_PACKAGE_ADDED).apply {
            data = Uri.parse("package:$pkg")
            putExtra(Intent.EXTRA_REPLACING, true)
        }
        receiver.onReceive(context, duplicateAddedIntent)
        assertTrue(SmartInstallAssistantManager.records.value.none { it.packageName == pkg })
    }

    @Test
    fun checklistStepsAndOnlineSyncHandleSuccessAndOfflineGracefully() = runBlocking {
        val targetPkg = "com.example.syncapp"
        installFakePackage(targetPkg, "Sync Demo", category = ApplicationInfo.CATEGORY_MAPS)

        SmartInstallAssistantManager.recordPackageEvent(context, targetPkg, isUpdate = false)
        SmartInstallAssistantManager.markStepReviewed(context, targetPkg, SetupChecklistStep.BATTERY_OPTIMIZATION)
        SmartInstallAssistantManager.markStepReviewed(context, targetPkg, SetupChecklistStep.NOTIFICATIONS)

        val updated = SmartInstallAssistantManager.records.value.first { it.packageName == targetPkg }
        assertEquals(2, updated.completedCount)
        assertEquals("2/4 Done", updated.statusBadgeText)

        // Online sync success path
        val playHtml = """
            <html>
              <head>
                <meta property="og:title" content="Sync Demo Pro - Apps on Google Play" />
                <title>Sync Demo Pro - Apps on Google Play</title>
              </head>
              <body>{"applicationCategory":"TOOLS","author":{"@type":"Person","name":"Arwora Labs"}}</body>
            </html>
        """.trimIndent()
        val okResult = SmartInstallAssistantManager.syncOnlineAppInfo(context, targetPkg) { url ->
            if (url.contains("play.google.com")) OnlineFetchResponse(200, playHtml) else null
        }
        assertEquals(OnlineSyncStatus.SUCCESS, okResult.status)
        assertEquals("Sync Demo Pro", okResult.storeTitle)
        assertEquals("TOOLS", okResult.publicCategory)

        // Online sync offline/error path
        val offlineResult = SmartInstallAssistantManager.syncOnlineAppInfo(context, targetPkg) {
            throw UnknownHostException("No route to host")
        }
        assertEquals(OnlineSyncStatus.OFFLINE, offlineResult.status)
        assertNotNull(offlineResult.errorMessage)
    }

    @Test
    fun assistantCardAndDashboardFilterChecklistAndOpenAppOps() {
        val gamePkg = "com.example.pixelracer"
        val prodPkg = "com.example.focusnotes"
        installFakePackage(gamePkg, "Pixel Racer", category = ApplicationInfo.CATEGORY_GAME)
        installFakePackage(
            prodPkg,
            "Focus Notes",
            category = ApplicationInfo.CATEGORY_PRODUCTIVITY,
            requestedPermissions = arrayOf(Manifest.permission.CAMERA),
            grantedFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        )

        runBlocking {
            SmartInstallAssistantManager.recordPackageEvent(context, gamePkg, isUpdate = false)
            SmartInstallAssistantManager.recordPackageEvent(context, prodPkg, isUpdate = false)
        }

        var openedPkgFromCard: String? = "unset"
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                SmartInstallAssistantCard(
                    onOpenAssistant = { pkg -> openedPkgFromCard = pkg }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smart App Installation Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Assistant Hub").performClick()
        assertEquals(null, openedPkgFromCard)
    }

    @Test
    fun assistantDashboardCategoryFilteringAndChecklistAppOpsHandoff() {
        val gamePkg = "com.example.pixelracer2"
        val prodPkg = "com.example.focusnotes2"
        installFakePackage(gamePkg, "Pixel Racer 2", category = ApplicationInfo.CATEGORY_GAME)
        installFakePackage(
            prodPkg,
            "Focus Notes 2",
            category = ApplicationInfo.CATEGORY_PRODUCTIVITY,
            requestedPermissions = arrayOf(Manifest.permission.CAMERA),
            grantedFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        )
        runBlocking {
            SmartInstallAssistantManager.recordPackageEvent(context, gamePkg, isUpdate = false)
            SmartInstallAssistantManager.recordPackageEvent(context, prodPkg, isUpdate = false)
        }

        val vm = SmartInstallAssistantViewModel(context)
        var launchedAppOpsPkg: String? = null

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                SmartInstallAssistantScreen(
                    initialPackageName = null,
                    onBack = {},
                    onOpenAppOpsForPackage = { pkg -> launchedAppOpsPkg = pkg },
                    vm = vm
                )
            }
        }
        composeTestRule.waitForIdle()

        // Filter to Games only
        composeTestRule.onNodeWithTag("category_chip_games").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("install_assistant_card_$gamePkg").assertIsDisplayed()

        // Switch back to All and open Focus Notes 2 detail checklist sheet
        composeTestRule.onNodeWithTag("category_chip_all").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("install_assistant_card_$prodPkg").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithText("App Setup Checklist (0/4)").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("App Setup Checklist (0/4)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_step_battery_checkbox").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule.onAllNodesWithText("App Setup Checklist (1/4)").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("App Setup Checklist (1/4)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("checklist_step_app_ops_action").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(prodPkg, launchedAppOpsPkg)
    }
}
