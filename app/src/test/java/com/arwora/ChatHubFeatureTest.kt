package com.arwora

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.feature.chat.data.ChatHubRepository
import com.arwora.feature.chat.data.ChatMessageEntity
import com.arwora.feature.chat.data.ScheduledMessageWorker
import com.arwora.feature.chat.model.ChatHubTab
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatStickerCatalog
import com.arwora.feature.chat.model.MessageDeliveryStatus
import com.arwora.feature.chat.ui.ChatHubScreen
import com.arwora.feature.chat.ui.ChatHubViewModel
import com.arwora.feature.chat.ui.ChatThreadScreen
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.ui.MainViewModel
import com.arwora.ui.screens.SettingsScreen
import com.arwora.ui.screens.allGlobalTools
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AppLockerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class ChatHubFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Application
    private var appDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        runCatching {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(context, Configuration.Builder().build())
            }
        }
        ChatHubRepository.resetForTests(context)
        ArworaConnectManager.resetForTests(context)
        AppLockerManager.init(context)
    }

    @After
    fun tearDown() {
        ChatHubRepository.resetForTests(context)
        ArworaConnectManager.resetForTests(context)
        appDb?.close()
        appDb = null
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(40L)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun settingsScreenExcludesChatLockHideChatAndArworaConnectSettings() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDb = database
        val viewModel = MainViewModel(
            historyRepository = HistoryRepository(database.historyDao()),
            favoriteRepository = FavoriteRepository(database.favoriteDao()),
            savedTextRepository = SavedTextRepository(database.savedTextDao()),
            settingsRepository = SettingsRepository(context.dataStore)
        )

        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                SettingsScreen(navController = navController, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.onAllNodesWithTag("settings_arwora_connect_row").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Chat Lock").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Hide Chat").fetchSemanticsNodes().isEmpty())
        assertTrue(allGlobalTools.any { it.title == "Chat Hub" })
    }

    @Test
    fun repositorySupportsMessagingActionsViewOnceSearchScheduleVaultAndRetention() {
        runBlocking {
            val repo = ChatHubRepository.getInstance(context)

            // 1. Direct & Group threads
            val alice = repo.createDirectThread("Alice")
            val team = repo.createGroupThread("Field Ops", listOf("Alice", "Bob"))
            assertFalse(alice.isGroup)
            assertTrue(team.isGroup)
            assertEquals(listOf("Alice", "Bob"), team.participantNames)

            // 2. Text, Sticker, Location & Voice messages
            val msg1 = repo.sendTextMessage(alice.id, "Check https://arwora.dev for updates").getOrThrow()
            val sticker = repo.sendStickerMessage(alice.id, ChatStickerCatalog.stickers.first().id, replyTo = msg1).getOrThrow()
            assertEquals(msg1.id, sticker.replyToMessageId)
            val loc = repo.sendLocationCoordinates(alice.id, 25.07, 91.39, "Sunamganj Hub").getOrThrow()
            assertEquals(ChatMessageType.LOCATION, loc.type)
            val voice = repo.sendVoiceMessage(alice.id, "AAAA", 3500L, listOf(0.2f, 0.8f, 0.4f)).getOrThrow()
            assertEquals(ChatMessageType.VOICE, voice.type)

            // 3. Reactions, Edit, Forward & Delete for Everyone
            repo.toggleReaction(alice.id, msg1.id, "🚀")
            val afterReact = repo.dao.getMessageById(msg1.id)!!.toDomain()
            assertEquals(1, afterReact.reactions["🚀"]?.size)

            assertTrue(repo.editMessage(alice.id, msg1.id, "Updated link https://arwora.dev/chat"))
            val edited = repo.dao.getMessageById(msg1.id)!!.toDomain()
            assertTrue(edited.isEdited)
            assertTrue(edited.text.contains("Updated link"))

            val forwarded = repo.forwardMessage(edited, team.id).getOrThrow()
            assertEquals(team.id, forwarded.threadId)
            assertNotNull(forwarded.forwardedFrom)

            repo.deleteForEveryone(team.id, forwarded.id)
            val deleted = repo.dao.getMessageById(forwarded.id)!!.toDomain()
            assertTrue(deleted.isDeletedForEveryone)

            // 4. Data Saver compression & View-Once self-destruction
            repo.preferencesStore.update { it.copy(dataSaverMode = true) }
            val tempImg = File(context.cacheDir, "view_once_sample.png")
            val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            tempImg.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val viewOnceMsg = repo.sendImageMessage(
                threadId = alice.id,
                uri = Uri.fromFile(tempImg),
                isViewOnce = true
            ).getOrThrow()
            assertTrue(viewOnceMsg.isViewOnce)
            assertFalse(viewOnceMsg.viewOnceConsumed)
            assertNotNull(viewOnceMsg.mediaBase64)

            repo.consumeViewOnceMedia(viewOnceMsg.id)
            val wiped = repo.dao.getMessageById(viewOnceMsg.id)!!.toDomain()
            assertTrue(wiped.viewOnceConsumed)
            assertNull(wiped.mediaBase64)
            assertNull(wiped.mediaPathOrUri)

            // 5. Screenshot alert
            repo.recordScreenshotDetected(alice.id, "Alice")
            val aliceMsgs = repo.observeMessagesForThread(alice.id).first()
            assertTrue(aliceMsgs.any { it.type == ChatMessageType.SYSTEM_ALERT && it.text.contains("screenshot") })

            // 6. Calendar Day + Keyword in-thread search
            val todayCal = Calendar.getInstance()
            val dayHits = repo.searchInThread(alice.id, "Updated link", todayCal.timeInMillis)
            assertEquals(1, dayHits.size)
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
            val yesterdayHits = repo.searchInThread(alice.id, "Updated link", yesterdayCal.timeInMillis)
            assertTrue(yesterdayHits.isEmpty())

            // 7. Scheduled Message persistence + manual immediate dispatch
            val futureTime = System.currentTimeMillis() + 60_000L
            val scheduled = repo.sendTextMessage(alice.id, "Future ping", scheduledForMillis = futureTime).getOrThrow()
            assertEquals(MessageDeliveryStatus.SCHEDULED, scheduled.deliveryStatus)
            assertTrue(repo.dispatchScheduledMessageNow(scheduled.id))
            val dispatched = repo.dao.getMessageById(scheduled.id)!!.toDomain()
            assertEquals(MessageDeliveryStatus.SENT, dispatched.deliveryStatus)
            ScheduledMessageWorker.cancel(context, scheduled.id)

            // 8. Hidden Vault search isolation
            repo.updateThread(alice.id) { it.copy(isHidden = true, isLocked = true) }
            val hiddenExcluded = repo.searchGlobalMessages("Sunamganj", includeHidden = false)
            assertTrue(hiddenExcluded.isEmpty())
            val hiddenIncluded = repo.searchGlobalMessages("Sunamganj", includeHidden = true)
            assertEquals(1, hiddenIncluded.size)

            // 9. Ghost mode & DND settings
            repo.preferencesStore.update {
                it.copy(
                    ghostModeEnabled = true,
                    dndEnabled = true,
                    dndStartHour = 0,
                    dndStartMinute = 0,
                    dndEndHour = 23,
                    dndEndMinute = 59
                )
            }
            assertTrue(repo.settings.value.ghostModeEnabled)
            assertTrue(repo.preferencesStore.isWithinDndSchedule())

            // 10. Auto-delete retention pruning
            val staleId = "stale-msg-1"
            repo.dao.upsertMessage(
                ChatMessageEntity.fromDomain(
                    ChatMessage(
                        id = staleId,
                        threadId = team.id,
                        senderId = "me",
                        senderName = "Me",
                        isOutgoing = true,
                        text = "Old secret",
                        timestampMillis = System.currentTimeMillis() - (48L * 60L * 60L * 1000L)
                    )
                )
            )
            val removed = repo.applyRetentionPolicy(P2pMessageRetentionPolicy.HOURS_24)
            assertTrue(removed >= 1)
            assertNull(repo.dao.getMessageById(staleId))
        }
    }

    @Test
    fun chatHubAndThreadUiSupportCreationTabsSettingsAndLongPressActions() {
        val vm = ChatHubViewModel(context)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                ChatHubScreen(
                    onBack = {},
                    onOpenThread = { vm.openThread(it) },
                    onOpenAppLocker = {},
                    vm = vm
                )
            }
        }
        composeTestRule.waitForIdle()

        // Create a 1-on-1 chat via FAB & dialog
        composeTestRule.onNodeWithTag("chat_hub_new_fab").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("new_chat_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("new_chat_title_input").performTextInput("Jordan")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_chat_confirm_btn").assertIsEnabled().performClick()

        composeTestRule.waitUntil(5_000L) {
            vm.allThreads.value.any { it.title == "Jordan" }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Jordan").assertIsDisplayed()

        // Switch tabs including Near Chat and Vault
        composeTestRule.onNodeWithTag("chat_hub_tab_near_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("near_chat_scan_btn").assertIsDisplayed()

        // Open Chat Hub Settings and toggle Ghost Mode
        composeTestRule.onNodeWithTag("chat_hub_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_hub_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_setting_sqlcipher_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_setting_ghost_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(vm.settings.value.ghostModeEnabled)
        composeTestRule.onNodeWithTag("chat_hub_settings_done_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_hub_ghost_badge").assertIsDisplayed()

        // Switch to Chats tab and verify thread card
        vm.selectTab(ChatHubTab.CHATS)
        composeTestRule.waitForIdle()
        val threadId = vm.allThreads.value.first { it.title == "Jordan" }.id

        // Now test ChatThreadScreen sending + long press message actions
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                ChatThreadScreen(
                    threadId = threadId,
                    onBack = {},
                    vm = vm
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_composer_input").performTextInput("Hello encrypted world")
        composeTestRule.onNodeWithTag("chat_composer_send_btn").performClick()

        composeTestRule.waitUntil(5_000L) {
            vm.activeMessages.value.any { it.text == "Hello encrypted world" }
        }
        composeTestRule.waitForIdle()
        val msgId = vm.activeMessages.value.first { it.text == "Hello encrypted world" }.id
        composeTestRule.onNodeWithText("Hello encrypted world", useUnmergedTree = true).assertIsDisplayed()

        // Long press bubble to trigger Messenger-style actions & add custom reaction
        composeTestRule.onNodeWithTag("chat_msg_bubble_$msgId").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_react_🔥").performClick()

        composeTestRule.waitUntil(5_000L) {
            vm.activeMessages.value.firstOrNull { it.id == msgId }?.reactions?.get("🔥")?.isNotEmpty() == true
        }
    }
}
