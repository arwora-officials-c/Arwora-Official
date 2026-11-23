package com.arwora

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowEngine
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager
import com.arwora.feature.system.downloadworkflow.model.DownloadFileCategory
import com.arwora.feature.system.downloadworkflow.model.DownloadWorkflowConfig
import com.arwora.feature.system.downloadworkflow.service.DownloadWorkflowService
import com.arwora.feature.system.downloadworkflow.ui.SmartDownloadWorkflowCard
import com.arwora.feature.system.downloadworkflow.ui.SmartDownloadWorkflowScreen
import com.arwora.feature.system.downloadworkflow.ui.SmartDownloadWorkflowViewModel
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class SmartDownloadWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Application
    private lateinit var tempDownloadDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        DownloadWorkflowManager.clearForTests(context)
        tempDownloadDir = File(context.cacheDir, "test_downloads_${System.nanoTime()}").apply {
            mkdirs()
        }
        DownloadWorkflowService.customWatchDirForTests = tempDownloadDir
    }

    @After
    fun tearDown() {
        DownloadWorkflowService.customWatchDirForTests = null
        DownloadWorkflowManager.clearForTests(context)
        tempDownloadDir.deleteRecursively()
    }

    @Test
    fun partialAndTempFilesAreIgnoredAndStabilityCheckRejectsEmptyFiles() = runBlocking {
        assertTrue(DownloadWorkflowEngine.isPartialOrTemporaryFile("sample.crdownload"))
        assertTrue(DownloadWorkflowEngine.isPartialOrTemporaryFile("movie.part"))
        assertTrue(DownloadWorkflowEngine.isPartialOrTemporaryFile("installer.apk.tmp"))
        assertTrue(DownloadWorkflowEngine.isPartialOrTemporaryFile("archive.aria2"))
        assertTrue(DownloadWorkflowEngine.isPartialOrTemporaryFile(".hidden_temp"))
        assertFalse(DownloadWorkflowEngine.isPartialOrTemporaryFile("release_build.apk"))

        val emptyFile = File(tempDownloadDir, "empty.pdf").apply { createNewFile() }
        assertFalse(
            DownloadWorkflowEngine.waitForStableFileSize(
                file = emptyFile,
                stabilityDurationMs = 120L,
                pollIntervalMs = 40L
            )
        )

        val validFile = File(tempDownloadDir, "manual.pdf").apply {
            writeBytes("PDF_TEST_CONTENT".toByteArray())
        }
        assertTrue(
            DownloadWorkflowEngine.waitForStableFileSize(
                file = validFile,
                stabilityDurationMs = 120L,
                pollIntervalMs = 40L
            )
        )
    }

    @Test
    fun categorizationAndFilenameCleaningNormalizeClutteredDownloads() {
        assertEquals(DownloadFileCategory.APKS, DownloadWorkflowEngine.categorizeFileName("app.apk"))
        assertEquals(DownloadFileCategory.ARCHIVES, DownloadWorkflowEngine.categorizeFileName("bundle.zip"))
        assertEquals(DownloadFileCategory.MEDIA, DownloadWorkflowEngine.categorizeFileName("clip.mp4"))
        assertEquals(DownloadFileCategory.DOCUMENTS, DownloadWorkflowEngine.categorizeFileName("report.pdf"))
        assertEquals(DownloadFileCategory.OTHER, DownloadWorkflowEngine.categorizeFileName("unknown.bin"))

        assertEquals(
            "Tutorial Video.mp4",
            DownloadWorkflowEngine.cleanFileName("y2mate.com - Tutorial_Video (1080p).mp4")
        )
        assertEquals(
            "Design Spec.pdf",
            DownloadWorkflowEngine.cleanFileName("[1080p] Copy of Design__Spec (copy).pdf")
        )
        assertEquals(
            "clip.mp4",
            DownloadWorkflowEngine.cleanFileName("ssstik.io_clip [HD].MP4")
        )
    }

    @Test
    fun masterAndGranularTogglesControlCategorizationRenameDuplicatesAndNotifications() = runBlocking {
        // 1. Master disabled blocks background monitoring trigger
        DownloadWorkflowManager.setMasterEnabled(context, false, startServiceIfEnabled = false)
        val blockedFile = File(tempDownloadDir, "y2mate.com - blocked_track.mp3").apply {
            writeBytes("AUDIO_BYTES".toByteArray())
        }
        val skipped = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = blockedFile,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = true
        )
        assertNull(skipped)
        assertTrue(blockedFile.exists())

        // 2. Enable Master + all granular toggles -> sort into Media/, clean name, notify
        DownloadWorkflowManager.setMasterEnabled(context, true, startServiceIfEnabled = false)
        DownloadWorkflowManager.setCategorizationEnabled(context, true)
        DownloadWorkflowManager.setAutoRenameEnabled(context, true)
        DownloadWorkflowManager.setDuplicateDetectionEnabled(context, true)
        DownloadWorkflowManager.setInstantNotificationEnabled(context, true)

        val firstEvent = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = blockedFile,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = true
        )
        assertNotNull(firstEvent)
        assertEquals("blocked track.mp3", firstEvent!!.finalName)
        assertEquals(DownloadFileCategory.MEDIA, firstEvent.category)
        assertTrue(firstEvent.wasRenamed)
        assertTrue(firstEvent.wasMoved)
        assertFalse(firstEvent.isDuplicate)
        assertTrue(File(tempDownloadDir, "Media/blocked track.mp3").exists())

        // Verify notification actions ("Open Folder", "View Details")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(nm)
        val notifications = shadowNm.allNotifications
        assertTrue(notifications.isNotEmpty())
        val latestActions = notifications.last().actions?.map { it.title.toString() }.orEmpty()
        assertTrue(latestActions.contains("Open Folder"))
        assertTrue(latestActions.contains("View Details"))

        // 3. Duplicate Detection: download identical bytes under a completely different name
        val duplicateFile = File(tempDownloadDir, "another_copy_song.mp3").apply {
            writeBytes("AUDIO_BYTES".toByteArray())
        }
        val dupEvent = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = duplicateFile,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = true
        )
        assertNotNull(dupEvent)
        assertTrue(dupEvent!!.isDuplicate)
        assertEquals("blocked track.mp3", dupEvent.duplicateOfName)

        // 4. Disable categorization, autoRename, duplicateDetection & instantNotification individually
        nm.cancelAll()
        DownloadWorkflowManager.setCategorizationEnabled(context, false)
        DownloadWorkflowManager.setAutoRenameEnabled(context, false)
        DownloadWorkflowManager.setDuplicateDetectionEnabled(context, false)
        DownloadWorkflowManager.setInstantNotificationEnabled(context, false)

        val rawApk = File(tempDownloadDir, "[1080p] raw_installer.apk").apply {
            writeBytes("APK_PAYLOAD_123".toByteArray())
        }
        val rawEvent = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = rawApk,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = true
        )
        assertNotNull(rawEvent)
        assertEquals("[1080p] raw_installer.apk", rawEvent!!.finalName)
        assertFalse(rawEvent.wasRenamed)
        assertFalse(rawEvent.wasMoved)
        assertFalse(rawEvent.isDuplicate)
        assertTrue(rawApk.exists())
        assertTrue(shadowNm.allNotifications.isEmpty())
    }

    @Test
    fun serviceLifecycleUpdatesRunningStateCleanly() {
        val controller = Robolectric.buildService(DownloadWorkflowService::class.java).create().startCommand(0, 0)
        assertTrue(DownloadWorkflowManager.isServiceRunning.value)
        controller.destroy()
        assertFalse(DownloadWorkflowManager.isServiceRunning.value)
    }

    @Test
    fun cardRendersMasterAndAllFourGranularSwitchesAndUpdatesConfig() = runBlocking {
        DownloadWorkflowManager.initIfNeeded(context)
        var hubOpenedCount = 0

        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SmartDownloadWorkflowCard(
                        onOpenWorkflowHub = { hubOpenedCount++ }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Smart Download Workflow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_workflow_status_badge").assertIsDisplayed()

        // Toggle Master Switch ON
        composeTestRule.onNodeWithTag("download_workflow_master_switch").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) { DownloadWorkflowManager.config.value.masterEnabled }

        // Toggle each of the 4 granular switches OFF and verify persistence state
        composeTestRule.onNodeWithTag("toggle_download_categorization").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) { !DownloadWorkflowManager.config.value.categorizationEnabled }

        composeTestRule.onNodeWithTag("toggle_download_auto_rename").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) { !DownloadWorkflowManager.config.value.autoRenameEnabled }

        composeTestRule.onNodeWithTag("toggle_download_duplicate_detection").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) { !DownloadWorkflowManager.config.value.duplicateDetectionEnabled }

        composeTestRule.onNodeWithTag("toggle_download_instant_notification").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) { !DownloadWorkflowManager.config.value.instantNotificationEnabled }

        // Open Hub button works
        composeTestRule.onNodeWithTag("download_workflow_open_hub_button").performScrollTo().performClick()
        assertEquals(1, hubOpenedCount)
    }

    @Test
    fun workflowHubDisplaysProcessedEventsFiltersAndDetailSheet() = runBlocking {
        DownloadWorkflowManager.initIfNeeded(context)
        DownloadWorkflowManager.setMasterEnabled(context, true, startServiceIfEnabled = false)

        val apkFile = File(tempDownloadDir, "Copy of toolkit_v2.apk").apply {
            writeBytes("APK_CONTENT_V2".toByteArray())
        }
        val first = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = apkFile,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = false
        )!!

        val dupApk = File(tempDownloadDir, "toolkit_v2_mirror.apk").apply {
            writeBytes("APK_CONTENT_V2".toByteArray())
        }
        val second = DownloadWorkflowManager.handleCandidateFile(
            context = context,
            file = dupApk,
            downloadRoot = tempDownloadDir,
            stabilityDurationMs = 0L,
            requireMasterEnabled = false
        )!!

        val vm = SmartDownloadWorkflowViewModel(context)

        composeTestRule.setContent {
            NameGenProTheme {
                SmartDownloadWorkflowScreen(
                    initialEventId = null,
                    onBack = {},
                    vm = vm
                )
            }
        }

        composeTestRule.onNodeWithTag("download_workflow_hub_list")
            .performScrollToNode(hasTestTag("download_event_card_${first.id}"))
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithText("toolkit v2.apk").fetchSemanticsNodes().isNotEmpty()
        }

        // Filter to Duplicates only and open second event detail bottom sheet
        composeTestRule.onNodeWithTag("download_workflow_hub_list")
            .performScrollToNode(hasTestTag("workflow_filter_row"))
        composeTestRule.onNodeWithTag("workflow_filter_row")
            .performScrollToNode(hasTestTag("workflow_filter_duplicates"))
        composeTestRule.onNodeWithTag("workflow_filter_duplicates").performClick()
        composeTestRule.onNodeWithTag("download_workflow_hub_list")
            .performScrollToNode(hasTestTag("download_event_card_${second.id}"))
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithText("toolkit v2 mirror.apk").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("download_event_card_${second.id}").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithText("MD5 Checksum").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("detail_delete_download_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_open_folder_button").assertIsDisplayed()
        assertTrue(first.md5Hash.isNotBlank())
    }
}
