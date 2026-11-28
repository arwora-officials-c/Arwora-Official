package com.arwora

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.connect.data.ArworaConnectChatManager
import com.arwora.feature.system.connect.data.ArworaConnectEngine
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ArworaVoiceRecorder
import com.arwora.feature.system.connect.data.ConnectSecurityStore
import com.arwora.feature.system.connect.model.ChatMessageType
import com.arwora.feature.system.connect.model.ConnectChatMessage
import com.arwora.feature.system.connect.model.DiscoveredNearbyPeer
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.service.NearbyDiscoveryService
import com.arwora.feature.system.connect.ui.ArworaConnectScreen
import com.arwora.feature.system.connect.ui.ArworaConnectViewModel
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.delay
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
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class ArworaConnectChatAndDiscoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Application
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA
        )
        ArworaConnectManager.resetForTests(context)
        tempRoot = File(context.cacheDir, "arwora_chat_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        ArworaConnectManager.stopHubService(context)
        ArworaConnectManager.resetForTests(context)
        tempRoot.deleteRecursively()
    }

    private fun allocateFreePort(): Int = ServerSocket(0).use { it.localPort }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(40L)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun nearbyDiscoveryHelperParsesAttributesAndFiltersSelf() {
        assertTrue(NearbyDiscoveryService.matchesArworaServiceType("_arworaconnect._tcp."))
        assertTrue(NearbyDiscoveryService.matchesArworaServiceType("_arworaconnect._tcp.local."))
        assertFalse(NearbyDiscoveryService.matchesArworaServiceType("_http._tcp."))

        val selfId = "local-node-1"
        assertNull(
            NearbyDiscoveryService.parseResolvedService(
                serviceName = "ArworaConnect-local",
                hostIp = "192.168.1.10",
                port = 48291,
                attributes = mapOf("id" to selfId, "name" to "Self"),
                localDeviceId = selfId
            )
        )

        val peer = NearbyDiscoveryService.parseResolvedService(
            serviceName = "ArworaConnect-peer2",
            hostIp = "/192.168.1.45",
            port = 48291,
            attributes = mapOf("id" to "peer-node-2", "name" to "Galaxy S24", "fp" to "abc123"),
            localDeviceId = selfId
        )
        assertNotNull(peer)
        assertEquals("peer-node-2", peer!!.deviceId)
        assertEquals("Galaxy S24", peer.deviceName)
        assertEquals("192.168.1.45", peer.hostIp)
        assertEquals(48291, peer.port)
    }

    @Test
    fun zeroConfigNearbyConnectLiveChatImageAndReactionsSyncOverSocket() {
        runBlocking {
            val dirA = File(tempRoot, "nodeA").apply { mkdirs() }
            val dirB = File(tempRoot, "nodeB").apply { mkdirs() }
            val storeA = ConnectSecurityStore(context, "chat_a").apply { clearAllForTests(); setDeviceName("Alpha Desk") }
            val storeB = ConnectSecurityStore(context, "chat_b").apply { clearAllForTests(); setDeviceName("Beta Phone") }

            val engineA = ArworaConnectEngine(context, storeA, preferredPort = allocateFreePort(), overrideReceiveDir = dirA)
            val engineB = ArworaConnectEngine(context, storeB, preferredPort = allocateFreePort(), overrideReceiveDir = dirB)

            try {
                val portA = engineA.startServer()
                engineB.startServer()
                val idA = storeA.getOrCreateDeviceId()
                val idB = storeB.getOrCreateDeviceId()

                // Zero-Config Nearby connect (no 6-digit code or QR)
                val discoveredA = DiscoveredNearbyPeer(
                    serviceName = NearbyDiscoveryService.buildServiceInstanceName(idA),
                    deviceId = idA,
                    deviceName = "Alpha Desk",
                    hostIp = "127.0.0.1",
                    port = portA
                )
                val pairResult = engineB.pairWithNearbyPeer(discoveredA)
                assertTrue("Zero-config nearby connect failed: ${pairResult.exceptionOrNull()}", pairResult.isSuccess)

                waitUntil {
                    engineA.peers.value.firstOrNull { it.peer.deviceId == idB }?.connectionState == PeerConnectionState.CONNECTED &&
                        engineB.peers.value.firstOrNull { it.peer.deviceId == idA }?.connectionState == PeerConnectionState.CONNECTED
                }

                // 1. Live Text Chat B -> A and A -> B
                val sendTextRes = engineB.sendChatText(idA, "Hello over local Wi-Fi!")
                assertTrue(sendTextRes.isSuccess)
                val sentMsgId = sendTextRes.getOrThrow().id

                waitUntil {
                    engineA.chatMessagesByPeer.value[idB]?.any {
                        it.id == sentMsgId && it.text == "Hello over local Wi-Fi!" && !it.isOutgoing
                    } == true
                }
                // Unread counter increments on A since no active chat room is open
                assertEquals(1, engineA.unreadChatCounts.value[idB])

                // 2. Compressed Photo Sharing A -> B
                val sampleBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.CYAN)
                }
                val compressedBase64 = ArworaConnectChatManager.compressBitmapToBase64(sampleBitmap)
                sampleBitmap.recycle()

                val imgRes = engineA.sendChatImageBase64(idB, compressedBase64, caption = "Snapshot")
                assertTrue(imgRes.isSuccess)
                val imgMsgId = imgRes.getOrThrow().id

                waitUntil {
                    engineB.chatMessagesByPeer.value[idA]?.any {
                        it.id == imgMsgId &&
                            it.type == ChatMessageType.IMAGE &&
                            it.text == "Snapshot" &&
                            !it.imageBase64.isNullOrBlank()
                    } == true
                }
                val receivedImg = engineB.chatMessagesByPeer.value[idA]!!.first { it.id == imgMsgId }
                val decoded = ArworaConnectChatManager.decodeBase64ToBitmap(receivedImg.imageBase64)
                assertNotNull(decoded)
                decoded?.recycle()

                // 3. Emoji Reaction A reacts ❤️ on B's text message, synced back to B
                assertTrue(engineA.toggleChatReaction(idB, sentMsgId, "❤️").isSuccess)
                waitUntil {
                    val msgOnB = engineB.chatMessagesByPeer.value[idA]?.firstOrNull { it.id == sentMsgId }
                    msgOnB?.reactions?.get("❤️")?.contains(idA) == true
                }

                // Toggle reaction off syncs removal cleanly
                assertTrue(engineA.toggleChatReaction(idB, sentMsgId, "❤️").isSuccess)
                waitUntil {
                    val msgOnB = engineB.chatMessagesByPeer.value[idA]?.firstOrNull { it.id == sentMsgId }
                    msgOnB?.reactions?.get("❤️").isNullOrEmpty()
                }

                // 4. Voice Note Snippet with Waveform B -> A over TCP socket
                val fakeAudioBase64 = android.util.Base64.encodeToString(
                    ByteArray(1024) { idx -> (idx % 127).toByte() },
                    android.util.Base64.NO_WRAP
                )
                val rawWave = listOf(0.15f, 0.45f, 0.85f, 0.62f, 0.95f, 0.30f, 0.70f, 0.40f)
                val voiceRes = engineB.sendChatVoice(
                    peerDeviceId = idA,
                    audioBase64 = fakeAudioBase64,
                    durationMs = 4_200L,
                    waveform = rawWave
                )
                assertTrue("Voice note send failed: ${voiceRes.exceptionOrNull()}", voiceRes.isSuccess)
                val voiceMsgId = voiceRes.getOrThrow().id

                waitUntil {
                    engineA.chatMessagesByPeer.value[idB]?.any {
                        it.id == voiceMsgId &&
                            it.type == ChatMessageType.VOICE &&
                            it.durationMs == 4_200L &&
                            it.waveform.size == ArworaVoiceRecorder.WAVEFORM_BAR_COUNT &&
                            it.audioBase64 == fakeAudioBase64
                    } == true
                }
            } finally {
                engineA.shutdown()
                engineB.shutdown()
            }
        }
    }

    @Test
    fun voiceWaveformHelpersNormalizeResampleAndFormatDuration() {
        assertEquals("0:00", ArworaVoiceRecorder.formatDurationMs(0L))
        assertEquals("0:05", ArworaVoiceRecorder.formatDurationMs(4_800L))
        assertEquals("1:00", ArworaVoiceRecorder.formatDurationMs(60_000L))

        val quiet = ArworaVoiceRecorder.normalizeAmplitude(0)
        val loud = ArworaVoiceRecorder.normalizeAmplitude(22_000)
        assertTrue(quiet in 0.10f..0.25f)
        assertTrue(loud >= 0.95f)

        val bars = ArworaVoiceRecorder.resampleWaveform(listOf(0.2f, 0.8f, 0.4f, 1.0f), targetBars = 16)
        assertEquals(16, bars.size)
        assertTrue(bars.all { it in 0.12f..1.0f })
    }

    @Test
    fun connectScreenDisplaysNearbyRadarOneTapConnectAndChatRoomWithReactionsAndImages() {
        val remoteDir = File(tempRoot, "remote_ui").apply { mkdirs() }
        val remoteStore = ConnectSecurityStore(context, "remote_ui_peer").apply {
            clearAllForTests()
            setDeviceName("Studio Mac")
        }
        val remoteEngine = ArworaConnectEngine(
            context,
            remoteStore,
            preferredPort = allocateFreePort(),
            overrideReceiveDir = remoteDir
        )
        val localEngine = ArworaConnectManager.getEngine(context)

        try {
            val remotePort = runBlocking {
                val p = remoteEngine.startServer()
                localEngine.startServer()
                p
            }
            val remoteId = remoteStore.getOrCreateDeviceId()

            // Inject discovered peer as returned from NSD resolver
            localEngine.nearbyDiscovery.upsertDiscoveredPeer(
                DiscoveredNearbyPeer(
                    serviceName = NearbyDiscoveryService.buildServiceInstanceName(remoteId),
                    deviceId = remoteId,
                    deviceName = "Studio Mac",
                    hostIp = "127.0.0.1",
                    port = remotePort
                )
            )

            val vm = ArworaConnectViewModel(context)
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    ArworaConnectScreen(
                        onBack = {},
                        vm = vm
                    )
                }
            }

            // 1. Top "Nearby Devices Found" radar banner & one-tap Connect button render
            composeTestRule.onNodeWithTag("connect_nearby_radar_card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Nearby Devices Found").assertIsDisplayed()
            composeTestRule.onNodeWithTag("connect_nearby_btn_$remoteId").assertIsDisplayed()

            // 2. Tap one-tap Connect (no 6-digit code or QR needed)
            composeTestRule.onNodeWithTag("connect_nearby_btn_$remoteId").performClick()

            composeTestRule.waitUntil(5_000L) {
                localEngine.peers.value.firstOrNull { it.peer.deviceId == remoteId }?.connectionState ==
                    PeerConnectionState.CONNECTED
            }

            // 3. Peer card displays Chat Room button -> Open Chat Room
            composeTestRule.onNodeWithTag("connect_hub_list")
                .performScrollToNode(hasTestTag("connect_peer_card_$remoteId"))
            composeTestRule.onNodeWithTag("connect_chat_peer_$remoteId").assertIsDisplayed().performClick()

            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_chat_dialog").fetchSemanticsNodes().isNotEmpty()
            }

            // 4. Send a live chat message
            composeTestRule.onNodeWithTag("connect_chat_input").performTextInput("Quick P2P ping")
            composeTestRule.onNodeWithTag("connect_chat_send_btn").performClick()

            composeTestRule.waitUntil(5_000L) {
                localEngine.chatMessagesByPeer.value[remoteId]?.any { it.text == "Quick P2P ping" } == true
            }
            val msgId = localEngine.chatMessagesByPeer.value[remoteId]!!.first { it.text == "Quick P2P ping" }.id
            composeTestRule.onNodeWithText("Quick P2P ping").assertIsDisplayed()

            // 5. Open reaction picker and react with ❤️
            composeTestRule.onNodeWithTag("connect_react_trigger_$msgId").performClick()
            composeTestRule.onNodeWithTag("connect_reaction_option_${msgId}_❤️").assertIsDisplayed().performClick()

            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_reaction_badge_${msgId}_❤️").fetchSemanticsNodes().isNotEmpty()
            }

            // 6. Receive an image message from remote peer and confirm image bubble renders
            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.MAGENTA)
            }
            val b64 = ArworaConnectChatManager.compressBitmapToBase64(bmp)
            bmp.recycle()
            val localId = localEngine.localIdentity.value.deviceId
            val sentImg = runBlocking { remoteEngine.sendChatImageBase64(localId, b64, "Photo drop") }
            assertTrue(sentImg.isSuccess)
            val imgId = sentImg.getOrThrow().id

            composeTestRule.waitUntil(4_000L) {
                composeTestRule.onAllNodesWithTag("connect_chat_image_$imgId").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Photo drop").assertIsDisplayed()

            // 7. Receive a voice note from remote peer and confirm mic button + waveform bubble render
            composeTestRule.onNodeWithTag("connect_chat_mic_btn").assertIsDisplayed()
            val voiceBytes = android.util.Base64.encodeToString(ByteArray(512) { 1 }, android.util.Base64.NO_WRAP)
            val sentVoice = runBlocking {
                remoteEngine.sendChatVoice(
                    peerDeviceId = localId,
                    audioBase64 = voiceBytes,
                    durationMs = 5_000L,
                    waveform = listOf(0.25f, 0.65f, 0.9f, 0.5f, 0.8f, 0.35f)
                )
            }
            assertTrue("Voice note send failed: ${sentVoice.exceptionOrNull()}", sentVoice.isSuccess)
            val voiceId = sentVoice.getOrThrow().id

            composeTestRule.waitUntil(5_000L) {
                localEngine.chatMessagesByPeer.value[remoteId]?.any {
                    it.id == voiceId && it.type == ChatMessageType.VOICE
                } == true
            }
            composeTestRule.onNodeWithTag("connect_chat_message_list")
                .performScrollToNode(hasTestTag("connect_chat_bubble_$voiceId"))
            composeTestRule.onNodeWithTag("connect_voice_waveform_$voiceId", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("connect_voice_play_btn_$voiceId").assertIsDisplayed()
            composeTestRule.onNodeWithTag("connect_voice_duration_$voiceId", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("0:05", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            remoteEngine.shutdown()
        }
    }

    @Test
    fun chatManagerAutoDeletesExpiredMessagesFor24HoursAnd7DaysPolicies() {
        var fakeNow = 1_800_000_000_000L
        val hourMs = 60L * 60L * 1000L
        val dayMs = 24L * hourMs
        val historyFile = File(tempRoot, "retention_history.json")
        val chatManager = ArworaConnectChatManager(
            context = context,
            initialRetentionPolicy = P2pMessageRetentionPolicy.OFF,
            nowProvider = { fakeNow },
            historyFile = historyFile
        )

        try {
            val peerId = "peer_retention_1"
            val old8d = ConnectChatMessage(
                id = "msg_8d",
                peerDeviceId = peerId,
                senderDeviceId = peerId,
                senderName = "Remote",
                type = ChatMessageType.TEXT,
                text = "Eight days old",
                timestampMillis = fakeNow - (8L * dayMs),
                isOutgoing = false
            )
            val old25h = ConnectChatMessage(
                id = "msg_25h",
                peerDeviceId = peerId,
                senderDeviceId = peerId,
                senderName = "Remote",
                type = ChatMessageType.TEXT,
                text = "Twenty five hours old",
                timestampMillis = fakeNow - (25L * hourMs),
                isOutgoing = false
            )
            val recent2h = ConnectChatMessage(
                id = "msg_2h",
                peerDeviceId = peerId,
                senderDeviceId = "local",
                senderName = "Me",
                type = ChatMessageType.TEXT,
                text = "Two hours old",
                timestampMillis = fakeNow - (2L * hourMs),
                isOutgoing = true
            )

            chatManager.appendLocalMessage(old8d)
            chatManager.appendLocalMessage(old25h)
            chatManager.appendLocalMessage(recent2h)
            assertEquals(3, chatManager.messagesByPeer.value[peerId]?.size)

            // OFF keeps everything
            assertEquals(0, chatManager.pruneExpiredMessages())
            assertEquals(3, chatManager.messagesByPeer.value[peerId]?.size)

            // 7 Days removes only the 8-day-old message
            assertEquals(1, chatManager.setRetentionPolicy(P2pMessageRetentionPolicy.DAYS_7))
            assertEquals(listOf("msg_25h", "msg_2h"), chatManager.messagesByPeer.value[peerId]?.map { it.id })

            // 24 Hours removes the 25-hour-old message too, leaving the 2-hour-old one
            assertEquals(1, chatManager.setRetentionPolicy(P2pMessageRetentionPolicy.HOURS_24))
            assertEquals(listOf("msg_2h"), chatManager.messagesByPeer.value[peerId]?.map { it.id })

            // Advancing clock past 24h prunes the remaining message
            fakeNow += 23L * hourMs
            assertEquals(1, chatManager.pruneExpiredMessages())
            assertTrue(chatManager.messagesByPeer.value[peerId].isNullOrEmpty())

            // Manager SharedPreferences persistence round-trip
            ArworaConnectManager.setMessageRetentionPolicy(context, P2pMessageRetentionPolicy.DAYS_7)
            assertEquals(P2pMessageRetentionPolicy.DAYS_7, ArworaConnectManager.messageRetentionPolicy.value)
            ArworaConnectManager.syncPreferences(context)
            assertEquals(P2pMessageRetentionPolicy.DAYS_7, ArworaConnectManager.messageRetentionPolicy.value)
            ArworaConnectManager.setMessageRetentionPolicy(context, P2pMessageRetentionPolicy.OFF)
            assertEquals(P2pMessageRetentionPolicy.OFF, ArworaConnectManager.messageRetentionPolicy.value)
            assertEquals(P2pMessageRetentionPolicy.DAYS_7, ArworaConnectManager.lastNonOffRetentionPolicy.value)
        } finally {
            chatManager.clearAll()
        }
    }

    @Test
    fun connectSettingsPanelAndDialogConfigureAutoDeleteAfter24HoursOr7Days() {
        val remoteDir = File(tempRoot, "remote_retention_ui").apply { mkdirs() }
        val remoteStore = ConnectSecurityStore(context, "remote_retention_peer").apply {
            clearAllForTests()
            setDeviceName("Pixel Fold")
        }
        val remoteEngine = ArworaConnectEngine(
            context,
            remoteStore,
            preferredPort = allocateFreePort(),
            overrideReceiveDir = remoteDir
        )
        val localEngine = ArworaConnectManager.getEngine(context)

        try {
            val remotePort = runBlocking {
                val p = remoteEngine.startServer()
                localEngine.startServer()
                p
            }
            val remoteId = remoteStore.getOrCreateDeviceId()
            localEngine.nearbyDiscovery.upsertDiscoveredPeer(
                DiscoveredNearbyPeer(
                    serviceName = NearbyDiscoveryService.buildServiceInstanceName(remoteId),
                    deviceId = remoteId,
                    deviceName = "Pixel Fold",
                    hostIp = "127.0.0.1",
                    port = remotePort
                )
            )

            val vm = ArworaConnectViewModel(context)
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    ArworaConnectScreen(
                        onBack = {},
                        vm = vm
                    )
                }
            }

            // Connect peer so we can later verify the chat header retention badge
            composeTestRule.onNodeWithTag("connect_nearby_btn_$remoteId").performClick()
            composeTestRule.waitUntil(5_000L) {
                localEngine.peers.value.firstOrNull { it.peer.deviceId == remoteId }?.connectionState ==
                    PeerConnectionState.CONNECTED
            }

            // Scroll to inline Arwora Connect Settings panel
            composeTestRule.onNodeWithTag("connect_hub_list")
                .performScrollToNode(hasTestTag("connect_settings_panel_card"))
            composeTestRule.onNodeWithTag("connect_settings_panel_card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Arwora Connect Settings").assertIsDisplayed()
            composeTestRule.onNodeWithText("24 Hours").assertIsDisplayed()
            composeTestRule.onNodeWithText("7 Days").assertIsDisplayed()

            // Toggle Auto-Delete ON -> defaults to 24 Hours
            composeTestRule.onNodeWithTag("connect_auto_delete_switch").performClick()
            composeTestRule.waitUntil(3_000L) {
                ArworaConnectManager.messageRetentionPolicy.value == P2pMessageRetentionPolicy.HOURS_24
            }

            // Switch duration to 7 Days
            composeTestRule.onNodeWithTag("connect_retention_7d_btn").performClick()
            composeTestRule.waitUntil(3_000L) {
                ArworaConnectManager.messageRetentionPolicy.value == P2pMessageRetentionPolicy.DAYS_7
            }

            // Open Settings Modal from TopAppBar & switch back to 24 Hours
            composeTestRule.onNodeWithTag("connect_hub_settings_button").performClick()
            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_settings_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("connect_settings_dialog").assertIsDisplayed()
            composeTestRule.onNode(
                hasTestTag("connect_retention_24h_btn") and hasAnyAncestor(hasTestTag("connect_settings_dialog"))
            ).performClick()
            composeTestRule.waitUntil(3_000L) {
                ArworaConnectManager.messageRetentionPolicy.value == P2pMessageRetentionPolicy.HOURS_24
            }
            composeTestRule.onNodeWithTag("connect_settings_done_btn").performClick()
            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_settings_dialog").fetchSemanticsNodes().isEmpty()
            }

            // Open Chat Room and verify active Auto-delete 24h badge renders in header
            composeTestRule.onNodeWithTag("connect_hub_list")
                .performScrollToNode(hasTestTag("connect_peer_card_$remoteId"))
            composeTestRule.onNodeWithTag("connect_chat_peer_$remoteId").performClick()
            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_chat_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("connect_chat_retention_badge").assertIsDisplayed()
            composeTestRule.onNodeWithText("• Auto-delete 24h").assertIsDisplayed()
            composeTestRule.onNodeWithTag("connect_chat_close_btn").performClick()

            // Toggle Auto-Delete OFF from inline panel
            composeTestRule.onNodeWithTag("connect_hub_list")
                .performScrollToNode(hasTestTag("connect_settings_panel_card"))
            composeTestRule.onNodeWithTag("connect_auto_delete_switch").performClick()
            composeTestRule.waitUntil(3_000L) {
                ArworaConnectManager.messageRetentionPolicy.value == P2pMessageRetentionPolicy.OFF
            }
            assertEquals(P2pMessageRetentionPolicy.HOURS_24, ArworaConnectManager.lastNonOffRetentionPolicy.value)
        } finally {
            remoteEngine.shutdown()
        }
    }
}
