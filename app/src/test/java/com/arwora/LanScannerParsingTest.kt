package com.arwora

import com.arwora.feature.system.wifi.LanScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanScannerParsingTest {

    @Test
    fun parsePingOutputMs_parsesDecimalAndSubMillisecondRtt() {
        val standardPing = """
            PING 192.168.1.1 (192.168.1.1) 56(84) bytes of data.
            64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=12.4 ms

            --- 192.168.1.1 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
            rtt min/avg/max/mdev = 12.400/12.400/12.400/0.000 ms
        """.trimIndent()
        assertEquals(12L, LanScanner.parsePingOutputMs(standardPing))

        val subMillisecondPing = "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=0.42 ms"
        assertEquals(1L, LanScanner.parsePingOutputMs(subMillisecondPing))

        val summaryOnly = "rtt min/avg/max/mdev = 4.100/7.800/11.200/2.000 ms"
        assertEquals(8L, LanScanner.parsePingOutputMs(summaryOnly))

        val unreachable = """
            PING 192.168.1.99 (192.168.1.99) 56(84) bytes of data.

            --- 192.168.1.99 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 0ms
        """.trimIndent()
        assertNull(LanScanner.parsePingOutputMs(unreachable))
        assertNull(LanScanner.parsePingOutputMs(""))
    }

    @Test
    fun parseArpAndNeighOutput_filtersInvalidEntriesAndNormalizesMacs() {
        val arpText = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         a4:83:e7:11:22:33     *        wlan0
            192.168.1.50     0x1         0x0         00:00:00:00:00:00     *        wlan0
            192.168.1.255    0x1         0x2         ff:ff:ff:ff:ff:ff     *        wlan0
        """.trimIndent()

        val neighText = """
            192.168.1.1 dev wlan0 lladdr a4-83-e7-11-22-33 REACHABLE
            192.168.1.15 dev wlan0 lladdr d8:a0:1d:44:55:66 STALE
            192.168.1.20 dev wlan0  FAILED
            192.168.1.21 dev wlan0 lladdr 14:cf:92:aa:bb:cc INCOMPLETE
        """.trimIndent()

        val result = LanScanner.parseArpAndNeighOutput(arpText, neighText)

        assertEquals(2, result.size)
        assertEquals("A4:83:E7:11:22:33", result["192.168.1.1"])
        assertEquals("D8:A0:1D:44:55:66", result["192.168.1.15"])
        assertNull(result["192.168.1.50"])
        assertNull(result["192.168.1.255"])
        assertNull(result["192.168.1.20"])
        assertNull(result["192.168.1.21"])
    }

    @Test
    fun getVendor_identifiesKnownOuiPrefixes() {
        assertEquals("Apple", LanScanner.getVendor("a4:83:e7:11:22:33"))
        assertEquals("Google", LanScanner.getVendor("D8-A0-1D-44-55-66"))
        assertEquals("Generic", LanScanner.getVendor("02:11:22:33:44:55"))
        assertEquals("Unknown", LanScanner.getVendor("Unknown"))
        assertEquals("Unknown", LanScanner.getVendor(""))
    }
}
