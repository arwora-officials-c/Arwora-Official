package com.arwora.feature.system.wifi.resolve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object ReverseDnsResolver {
    suspend fun resolve(ip: String, gatewayIp: String? = null): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(800) {
            try {
                val address = InetAddress.getByName(ip)
                val canonical = address.canonicalHostName?.takeIf {
                    it.isNotBlank() && it != ip && !it.equals("localhost", ignoreCase = true)
                }
                if (canonical != null) {
                    return@withTimeoutOrNull cleanHostname(canonical)
                }
                val hostName = address.hostName?.takeIf {
                    it.isNotBlank() && it != ip && !it.equals("localhost", ignoreCase = true)
                }
                if (hostName != null) {
                    return@withTimeoutOrNull cleanHostname(hostName)
                }
            } catch (_: Exception) {
            }

            // Direct UDP PTR query to local router DNS when Android Private DNS bypasses LAN names
            val gw = gatewayIp?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            if (gw != null) {
                queryRouterPtr(ip, gw)?.let { return@withTimeoutOrNull cleanHostname(it) }
            }
            null
        }
    }

    private fun cleanHostname(raw: String): String {
        return raw.trimEnd('.')
            .removeSuffix(".lan")
            .removeSuffix(".local")
            .removeSuffix(".home")
            .trim()
            .ifBlank { raw }
    }

    private fun queryRouterPtr(ip: String, gatewayIp: String): String? {
        val octets = ip.split(".")
        if (octets.size != 4) return null
        val ptrLabels = listOf(octets[3], octets[2], octets[1], octets[0], "in-addr", "arpa")
        var socket: DatagramSocket? = null
        return try {
            val queryBytes = buildPtrQueryPacket(ptrLabels)
            socket = DatagramSocket().apply { soTimeout = 400 }
            val gwAddr = InetAddress.getByName(gatewayIp)
            socket.send(DatagramPacket(queryBytes, queryBytes.size, gwAddr, 53))
            val respBuf = ByteArray(512)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)
            parsePtrResponse(respBuf, respPacket.length)
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun buildPtrQueryPacket(labels: List<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // Header: ID=0x4242, Flags=0x0100 (RD=1), QDCOUNT=1, AN/NS/AR=0
        out.write(byteArrayOf(0x42, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0) // root label
        // QTYPE = PTR (12), QCLASS = IN (1)
        out.write(byteArrayOf(0x00, 0x0C, 0x00, 0x01))
        return out.toByteArray()
    }

    private fun parsePtrResponse(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (anCount <= 0) return null

        var pos = 12
        // Skip question name
        pos = skipDnsName(data, pos, length) ?: return null
        pos += 4 // QTYPE + QCLASS
        if (pos >= length) return null

        // Parse first answer
        pos = skipDnsName(data, pos, length) ?: return null
        if (pos + 10 > length) return null
        val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        val rdLength = ((data[pos + 8].toInt() and 0xFF) shl 8) or (data[pos + 9].toInt() and 0xFF)
        pos += 10
        if (type != 12 || pos + rdLength > length) return null
        return readDnsName(data, pos, length)
    }

    private fun skipDnsName(data: ByteArray, start: Int, length: Int): Int? {
        var pos = start
        while (pos < length) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if ((len and 0xC0) == 0xC0) return pos + 2
            pos += 1 + len
        }
        return null
    }

    private fun readDnsName(data: ByteArray, start: Int, length: Int): String? {
        val parts = mutableListOf<String>()
        var pos = start
        var jumps = 0
        while (pos < length && jumps < 10) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= length) break
                pos = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                jumps++
                continue
            }
            if (pos + 1 + len > length) break
            parts.add(String(data, pos + 1, len, Charsets.UTF_8))
            pos += 1 + len
        }
        val name = parts.joinToString(".")
        return name.ifBlank { null }
    }
}
