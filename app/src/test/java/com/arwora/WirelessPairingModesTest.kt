package com.arwora

import com.arwora.utils.PairingStatus
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessPairingModesTest {

    @Test
    fun allThreePairingModesExistWithDistinctMetadata() {
        val modes = WirelessPairingMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(WirelessPairingMode.NOTIFICATION_MANUAL))
        assertTrue(modes.contains(WirelessPairingMode.AUTOMATIC_WIZARD))
        assertTrue(modes.contains(WirelessPairingMode.AUTO_PAIR_ON_WIFI))
        assertEquals(3, modes.map { it.title }.distinct().size)
        assertEquals(3, modes.map { it.shortLabel }.distinct().size)
    }

    @Test
    fun manualPairingValidationAcceptsValidInputsAndRejectsInvalidOnes() {
        assertTrue(WirelessPairingManager.isValidManualPairingInput("127.0.0.1", 37491, "482910"))
        assertTrue(WirelessPairingManager.isValidManualPairingInput("192.168.1.50", 5555, "000123"))

        assertFalse(WirelessPairingManager.isValidManualPairingInput("", 37491, "482910"))
        assertFalse(WirelessPairingManager.isValidManualPairingInput("127.0.0.1", 80, "482910"))
        assertFalse(WirelessPairingManager.isValidManualPairingInput("127.0.0.1", 70000, "482910"))
        assertFalse(WirelessPairingManager.isValidManualPairingInput("127.0.0.1", 37491, "12345"))
        assertFalse(WirelessPairingManager.isValidManualPairingInput("127.0.0.1", 37491, "12a456"))
    }

    @Test
    fun wifiAutoPairDebounceRespectsToggleAndCooldownWindow() {
        assertFalse(
            WirelessPairingManager.shouldTriggerWifiAutoPair(
                enabled = false,
                nowMs = 20_000L,
                lastAttemptMs = 0L
            )
        )
        assertFalse(
            WirelessPairingManager.shouldTriggerWifiAutoPair(
                enabled = true,
                nowMs = 12_000L,
                lastAttemptMs = 10_000L,
                minIntervalMs = 5_000L
            )
        )
        assertTrue(
            WirelessPairingManager.shouldTriggerWifiAutoPair(
                enabled = true,
                nowMs = 15_000L,
                lastAttemptMs = 10_000L,
                minIntervalMs = 5_000L
            )
        )
    }

    @Test
    fun extractsSixDigitCodeAndPortFromWirelessDebuggingNotification() {
        val notif = "Wireless debugging pairing code: 649201 on port: 41835"
        assertEquals("649201", WirelessPairingManager.extractPairingCode(notif))
        assertEquals(41835, WirelessPairingManager.extractPairingPort(notif))
    }

    @Test
    fun parsesProcNetTcpListeningPortsAndEnforcesTenSecondTimeout() {
        val sampleProc = """
            sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
             0: 0100007F:15B3 00000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 12345
             1: 00000000:9E5D 00000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 12346
             2: 0100007F:01BB 0100007F:C350 01 00000000:00000000 00:00000000 00000000  2000        0 12347
        """.trimIndent()
        val ports = WirelessPairingManager.parseProcNetTcpListeningPorts(sampleProc, minPort = 1024)
        assertEquals(listOf(5555, 40541), ports)
        assertEquals(10_000L, WirelessPairingManager.AUTO_PAIR_DISCOVERY_TIMEOUT_MS)
        assertEquals("Scanning local ADB port...", PairingStatus.Listening().stageMessage)
        assertEquals("Establishing secure pairing...", PairingStatus.PairingInProgress(40541).stageMessage)
        assertEquals("Connected successfully!", PairingStatus.Success().message)
    }
}
