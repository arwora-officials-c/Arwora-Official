package com.arwora

import android.Manifest
import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.connect.data.ArworaConnectEngine
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ConnectSecurityStore
import com.arwora.feature.system.connect.data.QrMatrixEncoder
import com.arwora.feature.system.connect.model.PairingQrPayload
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.TransferDirection
import com.arwora.feature.system.connect.model.TransferStatus
import com.arwora.feature.system.connect.model.TrustedPeerRecord
import com.arwora.feature.system.connect.service.ArworaConnectService
import com.arwora.feature.system.connect.ui.ArworaConnectCard
import com.arwora.feature.system.connect.ui.ArworaConnectScreen
import com.arwora.feature.system.connect.ui.ArworaConnectViewModel
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class ArworaConnectTest {

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
        tempRoot = File(context.cacheDir, "arwora_connect_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        ArworaConnectManager.stopHubService(context)
        ArworaConnectManager.resetForTests(context)
        tempRoot.deleteRecursively()
    }

    private fun allocateFreePort(): Int = ServerSocket(0).use { it.localPort }

    private suspend fun waitUntilCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(40L)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private fun assertValidQrFinderPatterns(matrix: QrMatrixEncoder.QrMatrix) {
        assertEquals(matrix.size, matrix.modules.size)
        val finderOrigins = listOf(
            0 to 0,
            matrix.size - 7 to 0,
            0 to matrix.size - 7
        )
        for ((ox, oy) in finderOrigins) {
            for (dy in 0..6) {
                for (dx in 0..6) {
                    val onBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                    val inCore = dx in 2..4 && dy in 2..4
                    assertEquals(
                        "Finder mismatch at ($ox+$dx, $oy+$dy)",
                        onBorder || inCore,
                        matrix.get(ox + dx, oy + dy)
                    )
                }
            }
        }
    }

    @Test
    fun qrEncoderAndPayloadRoundtripSupportSmallAndFullEcCertPayloads() {
        // 1. Small payload (< 70 bytes -> Version <= 4)
        val shortMatrix = QrMatrixEncoder.encode("arwora://connect?id=abc123&ip=192.168.1.5")
        assertTrue(shortMatrix.size >= 21)
        assertValidQrFinderPatterns(shortMatrix)

        // 2. Real EC P-256 identity payload with 36-char UTF-8 device name (Versions 10..14)
        val store = ConnectSecurityStore(context, "qr_spec")
        store.clearAllForTests()
        store.setDeviceName("Arwora Pixel Pro Station Device Name")
        val payload = PairingQrPayload(
            deviceId = store.getOrCreateDeviceId(),
            deviceName = store.getDeviceName(),
            hostIp = "192.168.43.105",
            port = 48291,
            pairingCode = store.getOrGeneratePairingCode(),
            publicKeyBase64 = store.getPublicKeyBase64(),
            certFingerprint = store.getCertFingerprint()
        )
        val jsonUri = payload.toUriString()
        val parsedJson = PairingQrPayload.parse(jsonUri)
        assertNotNull(parsedJson)
        assertEquals(payload, parsedJson)

        val fullMatrix = QrMatrixEncoder.encode(jsonUri)
        assertTrue("Expected QR version >= 9 matrix for full EC payload", fullMatrix.size >= 53)
        assertValidQrFinderPatterns(fullMatrix)

        // 3. arwora://connect URI scheme fallback parser
        val deepUri = "arwora://connect?id=peer99&name=Desk%20Node&ip=10.0.0.8&port=48291&code=123456&pk=abc&fp=def"
        val parsedUri = PairingQrPayload.parse(deepUri)
        assertNotNull(parsedUri)
        assertEquals("peer99", parsedUri!!.deviceId)
        assertEquals("Desk Node", parsedUri.deviceName)
        assertEquals("10.0.0.8", parsedUri.hostIp)
        assertEquals(48291, parsedUri.port)
        assertEquals("123456", parsedUri.pairingCode)
    }

    @Test
    fun multiPeerPairingRenameBroadcastAutoAcceptUntrustedRejectDisconnectAndAutoReconnect() = runBlocking {
        val dirA = File(tempRoot, "recvA").apply { mkdirs() }
        val dirB = File(tempRoot, "recvB").apply { mkdirs() }
        val dirC = File(tempRoot, "recvC").apply { mkdirs() }
        val dirRogue = File(tempRoot, "recvRogue").apply { mkdirs() }

        val storeA = ConnectSecurityStore(context, "peer_a").apply { clearAllForTests(); setDeviceName("Alpha Node") }
        val storeB = ConnectSecurityStore(context, "peer_b").apply { clearAllForTests(); setDeviceName("Beta Phone") }
        val storeC = ConnectSecurityStore(context, "peer_c").apply { clearAllForTests(); setDeviceName("Gamma Tablet") }
        val storeRogue = ConnectSecurityStore(context, "peer_rogue").apply { clearAllForTests(); setDeviceName("Rogue Device") }

        val portA = allocateFreePort()
        val portB = allocateFreePort()
        val portC = allocateFreePort()
        val portRogue = allocateFreePort()

        val engineA = ArworaConnectEngine(context, storeA, preferredPort = portA, overrideReceiveDir = dirA)
        val engineB = ArworaConnectEngine(context, storeB, preferredPort = portB, overrideReceiveDir = dirB)
        val engineC = ArworaConnectEngine(context, storeC, preferredPort = portC, overrideReceiveDir = dirC)
        val rogueEngine = ArworaConnectEngine(context, storeRogue, preferredPort = portRogue, overrideReceiveDir = dirRogue)

        try {
            val actualPortA = engineA.startServer()
            val actualPortB = engineB.startServer()
            val actualPortC = engineC.startServer()
            rogueEngine.startServer()

            // 1. Pair B -> A via QR Payload
            val qrPayloadA = engineA.buildQrPayload().copy(hostIp = "127.0.0.1", port = actualPortA)
            val pairBResult = engineB.pairWithQrPayload(qrPayloadA)
            assertTrue("QR pair failed: ${pairBResult.exceptionOrNull()}", pairBResult.isSuccess)

            // 2. Pair C -> A via 6-Digit Pairing Code
            val codeA = engineA.localIdentity.value.pairingCode
            val pairCResult = engineC.pairWithCode("127.0.0.1", actualPortA, codeA)
            assertTrue("Code pair failed: ${pairCResult.exceptionOrNull()}", pairCResult.isSuccess)

            val idA = storeA.getOrCreateDeviceId()
            val idB = storeB.getOrCreateDeviceId()
            val idC = storeC.getOrCreateDeviceId()

            waitUntilCondition {
                engineA.peers.value.count { it.connectionState == PeerConnectionState.CONNECTED } == 2
            }
            assertEquals(actualPortB, engineA.peers.value.first { it.peer.deviceId == idB }.peer.port)
            assertEquals(actualPortC, engineA.peers.value.first { it.peer.deviceId == idC }.peer.port)

            // 3. Rename Alpha Node -> live RENAME_NOTIFY sync to both Beta Phone & Gamma Tablet
            engineA.updateDeviceName("Studio Pixel")
            waitUntilCondition {
                engineB.peers.value.firstOrNull { it.peer.deviceId == idA }?.peer?.deviceName == "Studio Pixel" &&
                    engineC.peers.value.firstOrNull { it.peer.deviceId == idA }?.peer?.deviceName == "Studio Pixel"
            }

            // 4. Simultaneous Multi-Device File Broadcast with Silent Trusted Auto-Accept
            val sampleBytes = ByteArray(48 * 1024) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
            val sourceFile = File(tempRoot, "arwora_sync_bundle.bin").apply { writeBytes(sampleBytes) }
            val sentCount = engineA.broadcastFilesToSelectedPeers(listOf(Uri.fromFile(sourceFile)))
            assertEquals(2, sentCount)

            waitUntilCondition {
                val txB = engineB.transfers.value.firstOrNull()
                val txC = engineC.transfers.value.firstOrNull()
                txB?.status == TransferStatus.COMPLETED && txC?.status == TransferStatus.COMPLETED
            }

            val rxB = engineB.transfers.value.first()
            val rxC = engineC.transfers.value.first()
            assertEquals(TransferDirection.INCOMING, rxB.direction)
            assertTrue(rxB.autoAccepted)
            assertTrue(rxC.autoAccepted)
            assertArrayEquals(sampleBytes, File(rxB.savedFilePath!!).readBytes())
            assertArrayEquals(sampleBytes, File(rxC.savedFilePath!!).readBytes())

            // 5. Untrusted / Rogue peer spoofing Alpha's deviceId is rejected (cannot auto-send files without trusted cert)
            storeRogue.upsertTrustedPeer(
                TrustedPeerRecord(
                    deviceId = idA,
                    deviceName = "Alpha Spoof",
                    lastKnownIp = "127.0.0.1",
                    port = actualPortA,
                    publicKeyBase64 = storeA.getPublicKeyBase64(),
                    certFingerprint = storeA.getCertFingerprint(),
                    pairedAtMillis = System.currentTimeMillis(),
                    lastSeenMillis = System.currentTimeMillis(),
                    trustedAutoAccept = true
                )
            )
            // Force rogue session list & broadcast using forged identity against A -> A rejects certificate/signature mismatch
            val rogueProbe = rogueEngine.probeAndAutoReconnectDisconnectedPeers()
            assertEquals(0, rogueProbe)
            val rogueSent = rogueEngine.broadcastFilesToSelectedPeers(listOf(Uri.fromFile(sourceFile)))
            assertEquals(0, rogueSent)
            assertEquals(0, engineA.transfers.value.count { it.direction == TransferDirection.INCOMING })

            // 6. Individual Disconnect: Disconnect B while C remains CONNECTED
            engineA.disconnectPeer(idB, userInitiated = true)
            waitUntilCondition {
                val peerB = engineA.peers.value.first { it.peer.deviceId == idB }
                val peerC = engineA.peers.value.first { it.peer.deviceId == idC }
                peerB.connectionState == PeerConnectionState.OFFLINE &&
                    peerC.connectionState == PeerConnectionState.CONNECTED
            }

            // 7. Auto-Reconnect Engine: Simulate Gamma dropping out of range, returning, and auto-reconnecting
            engineC.stopServerImmediate()
            engineA.disconnectPeer(idC, userInitiated = false)
            waitUntilCondition {
                engineA.peers.value.first { it.peer.deviceId == idC }.connectionState == PeerConnectionState.OFFLINE
            }

            // Gamma comes back online
            engineC.startServer()
            val reconnected = engineA.probeAndAutoReconnectDisconnectedPeers()
            assertEquals(1, reconnected)
            waitUntilCondition {
                val peerB = engineA.peers.value.first { it.peer.deviceId == idB }
                val peerC = engineA.peers.value.first { it.peer.deviceId == idC }
                // Manually disconnected Peer B stays offline; Peer C is silently restored!
                peerB.connectionState == PeerConnectionState.OFFLINE &&
                    peerC.connectionState == PeerConnectionState.CONNECTED
            }

            // Manual reconnect restores Peer B
            assertTrue(engineA.reconnectPeer(idB))
            waitUntilCondition {
                engineA.peers.value.first { it.peer.deviceId == idB }.connectionState == PeerConnectionState.CONNECTED
            }
        } finally {
            engineA.shutdown()
            engineB.shutdown()
            engineC.shutdown()
            rogueEngine.shutdown()
        }
    }

    @Test
    fun serviceLifecycleAndConnectCardAndHubUiWorkEndToEnd() {
        // 1. Foreground service lifecycle sync
        val controller = Robolectric.buildService(ArworaConnectService::class.java).create().startCommand(0, 0)
        assertTrue(ArworaConnectManager.isServiceRunning.value)
        controller.destroy()
        assertFalse(ArworaConnectManager.isServiceRunning.value)

        // 2. Seed a trusted peer in store so card and hub display connected/trusted peer UI
        val engine = ArworaConnectManager.getEngine(context)
        engine.securityStore.upsertTrustedPeer(
            TrustedPeerRecord(
                deviceId = "peer_ui_1",
                deviceName = "Pixel Studio",
                lastKnownIp = "192.168.1.44",
                port = 48291,
                publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
                certFingerprint = "abcdef1234567890",
                pairedAtMillis = System.currentTimeMillis(),
                lastSeenMillis = System.currentTimeMillis(),
                trustedAutoAccept = true
            )
        )
        engine.refreshLocalNetworkInfo()

        // 3. Test ArworaConnectCard inside System Tab container
        var hubClicks = 0
        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArworaConnectCard(onOpenConnectHub = { hubClicks++ })
                }
            }
        }

        composeTestRule.onNodeWithText("Arwora Connect").assertIsDisplayed()
        composeTestRule.onNodeWithTag("arwora_connect_status_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pixel Studio").performScrollTo().assertIsDisplayed()

        // Rename local device via Card dialog
        composeTestRule.onNodeWithTag("connect_card_rename_button").performScrollTo()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("connect_card_rename_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("connect_rename_device_input").performTextReplacement("My Workstation")
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("connect_rename_confirm_button").performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithText("My Workstation").fetchSemanticsNodes().isNotEmpty()
        }

        // Show QR dialog from Card
        composeTestRule.onNodeWithTag("connect_card_qr_button").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("connect_qr_matrix_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Done").performClick()

        // Open Connect Hub CTA
        composeTestRule.onNodeWithTag("connect_card_open_hub_button").performScrollTo().performClick()
        assertEquals(1, hubClicks)
    }

    @Test
    fun connectHubScreenDisplaysQrModalPairCodeDialogAndPeerControls() {
        val engine = ArworaConnectManager.getEngine(context)
        engine.securityStore.upsertTrustedPeer(
            TrustedPeerRecord(
                deviceId = "peer_hub_1",
                deviceName = "Tablet Node",
                lastKnownIp = "192.168.1.99",
                port = 48291,
                publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
                certFingerprint = "0123456789abcdef",
                pairedAtMillis = System.currentTimeMillis(),
                lastSeenMillis = System.currentTimeMillis(),
                trustedAutoAccept = true
            )
        )
        val vm = ArworaConnectViewModel(context)

        composeTestRule.setContent {
            NameGenProTheme {
                ArworaConnectScreen(
                    onBack = {},
                    vm = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Arwora Connect").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_hub_pairing_code").assertIsDisplayed()

        // Toggle Auto-Reconnect switch
        composeTestRule.onNodeWithTag("connect_hub_auto_reconnect_switch").performClick()
        composeTestRule.waitUntil(3_000L) { !ArworaConnectManager.autoReconnectEnabled.value }
        composeTestRule.onNodeWithTag("connect_hub_auto_reconnect_switch").performClick()
        composeTestRule.waitUntil(3_000L) { ArworaConnectManager.autoReconnectEnabled.value }

        // Open QR modal
        composeTestRule.onNodeWithTag("connect_hub_show_qr_button").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("connect_qr_matrix_canvas").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Done").performClick()

        // Open Pair by 6-digit code dialog
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("connect_hub_enter_code_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("connect_pair_ip_input").performTextReplacement("192.168.1.50")
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("connect_pair_code_input").performTextInput("654321")
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("connect_pair_submit_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.mainClock.autoAdvance = true

        // Verify peer card renders & Forget removes it cleanly
        composeTestRule.onNodeWithTag("connect_hub_list")
            .performScrollToNode(hasTestTag("connect_peer_card_peer_hub_1"))
        composeTestRule.onNodeWithText("Tablet Node").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_forget_peer_peer_hub_1").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithText("No Trusted Peers Paired").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun connectQrScannerDialogConfiguresCompatibleFillCenterPreviewAndFixedRoundedFrame() {
        val preview = com.arwora.feature.system.connect.ui.createConnectScannerPreviewView(context)
        assertEquals(
            androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE,
            preview.implementationMode
        )
        assertEquals(
            androidx.camera.view.PreviewView.ScaleType.FILL_CENTER,
            preview.scaleType
        )
        assertEquals(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            preview.layoutParams.width
        )
        assertEquals(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            preview.layoutParams.height
        )

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                com.arwora.feature.system.connect.ui.ConnectQrScannerDialog(
                    onQrDetected = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("connect_qr_scanner_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Arwora Connect QR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_scanner_camera_frame").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_scanner_preview_view").assertIsDisplayed()

        val frameHeightDp = with(composeTestRule.density) {
            composeTestRule.onNodeWithTag("connect_scanner_camera_frame")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
                .toDp()
                .value
        }
        assertTrue(
            "Scanner camera frame height should be cleanly constrained between 280dp and 320dp (was ${frameHeightDp}dp)",
            frameHeightDp in 280f..320f
        )
    }

    @Test
    fun connectQrScannerDialogShowsPermissionPromptWhenCameraDenied() {
        shadowOf(context).denyPermissions(Manifest.permission.CAMERA)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                com.arwora.feature.system.connect.ui.ConnectQrScannerDialog(
                    onQrDetected = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("connect_qr_scanner_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_scanner_camera_frame").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_scanner_grant_camera_btn").assertIsDisplayed()
    }

    private fun assertSingleLineNoEllipsis(tag: String) {
        val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult]
        assertTrue("Expected GetTextLayoutResult on $tag", action.action?.invoke(layouts) == true)
        assertTrue("Expected non-empty TextLayoutResult for $tag", layouts.isNotEmpty())
        val layout = layouts.first()
        assertEquals("Expected single line for $tag (got ${layout.lineCount})", 1, layout.lineCount)
        assertFalse("Expected no ellipsis for $tag", layout.isLineEllipsized(0))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h780dp")
    fun connectHubDisconnectButtonStaysSingleLineAndRequiresConfirmation() = runBlocking {
        val remoteDir = File(tempRoot, "remote_hub").apply { mkdirs() }
        val remoteStore = ConnectSecurityStore(context, "remote_hub_peer").apply {
            clearAllForTests()
            setDeviceName("Remote Studio Node")
        }
        val remotePort = allocateFreePort()
        val remoteEngine = ArworaConnectEngine(
            context,
            remoteStore,
            preferredPort = remotePort,
            overrideReceiveDir = remoteDir
        )
        val localEngine = ArworaConnectManager.getEngine(context)

        try {
            val boundRemotePort = remoteEngine.startServer()
            localEngine.startServer()
            val payload = remoteEngine.buildQrPayload().copy(hostIp = "127.0.0.1", port = boundRemotePort)
            val pairResult = localEngine.pairWithQrPayload(payload)
            assertTrue("Pairing failed: ${pairResult.exceptionOrNull()}", pairResult.isSuccess)
            val remoteId = remoteStore.getOrCreateDeviceId()

            waitUntilCondition {
                localEngine.peers.value.firstOrNull { it.peer.deviceId == remoteId }?.connectionState ==
                    PeerConnectionState.CONNECTED
            }

            val vm = ArworaConnectViewModel(context)
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    ArworaConnectScreen(
                        onBack = {},
                        vm = vm
                    )
                }
            }

            composeTestRule.onNodeWithTag("connect_hub_list")
                .performScrollToNode(hasTestTag("connect_peer_card_$remoteId"))
            composeTestRule.onNodeWithTag("connect_disconnect_peer_$remoteId").assertIsDisplayed()

            // Verify Disconnect stays strictly on 1 line even on compact 360dp width
            assertSingleLineNoEllipsis("connect_disconnect_label_$remoteId")

            // Tapping Disconnect opens confirmation dialog without disconnecting immediately
            composeTestRule.onNodeWithTag("connect_disconnect_peer_$remoteId").performClick()
            composeTestRule.onNodeWithTag("connect_disconnect_confirm_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("Disconnect Device").assertIsDisplayed()
            composeTestRule.onNodeWithText("Are you sure you want to disconnect from this peer?").assertIsDisplayed()
            assertEquals(
                PeerConnectionState.CONNECTED,
                localEngine.peers.value.first { it.peer.deviceId == remoteId }.connectionState
            )

            // Cancel preserves active connection
            composeTestRule.onNodeWithTag("connect_disconnect_cancel_btn").performClick()
            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("connect_disconnect_confirm_dialog").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(
                PeerConnectionState.CONNECTED,
                localEngine.peers.value.first { it.peer.deviceId == remoteId }.connectionState
            )

            // Confirming Disconnect terminates session
            composeTestRule.onNodeWithTag("connect_disconnect_peer_$remoteId").performClick()
            composeTestRule.onNodeWithTag("connect_disconnect_confirm_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("connect_disconnect_confirm_btn").performClick()

            waitUntilCondition {
                localEngine.peers.value.first { it.peer.deviceId == remoteId }.connectionState ==
                    PeerConnectionState.OFFLINE
            }
        } finally {
            remoteEngine.shutdown()
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h780dp")
    fun connectCardDisconnectButtonStaysSingleLineAndRequiresConfirmation() = runBlocking {
        val remoteDir = File(tempRoot, "remote_card").apply { mkdirs() }
        val remoteStore = ConnectSecurityStore(context, "remote_card_peer").apply {
            clearAllForTests()
            setDeviceName("Dashboard Peer")
        }
        val remotePort = allocateFreePort()
        val remoteEngine = ArworaConnectEngine(
            context,
            remoteStore,
            preferredPort = remotePort,
            overrideReceiveDir = remoteDir
        )
        val localEngine = ArworaConnectManager.getEngine(context)

        try {
            val boundRemotePort = remoteEngine.startServer()
            localEngine.startServer()
            val payload = remoteEngine.buildQrPayload().copy(hostIp = "127.0.0.1", port = boundRemotePort)
            assertTrue(localEngine.pairWithQrPayload(payload).isSuccess)
            val remoteId = remoteStore.getOrCreateDeviceId()

            waitUntilCondition {
                localEngine.peers.value.firstOrNull { it.peer.deviceId == remoteId }?.connectionState ==
                    PeerConnectionState.CONNECTED
            }

            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ArworaConnectCard(onOpenConnectHub = {})
                    }
                }
            }

            composeTestRule.onNodeWithTag("connect_card_disconnect_$remoteId").performScrollTo().assertIsDisplayed()
            assertSingleLineNoEllipsis("connect_card_disconnect_label_$remoteId")

            composeTestRule.onNodeWithTag("connect_card_disconnect_$remoteId").performClick()
            composeTestRule.onNodeWithTag("connect_disconnect_confirm_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("Are you sure you want to disconnect from this peer?").assertIsDisplayed()
            assertEquals(
                PeerConnectionState.CONNECTED,
                localEngine.peers.value.first { it.peer.deviceId == remoteId }.connectionState
            )

            composeTestRule.onNodeWithTag("connect_disconnect_confirm_btn").performClick()
            waitUntilCondition {
                localEngine.peers.value.first { it.peer.deviceId == remoteId }.connectionState ==
                    PeerConnectionState.OFFLINE
            }
        } finally {
            remoteEngine.shutdown()
        }
    }
}
