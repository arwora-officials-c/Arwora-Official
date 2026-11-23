package com.arwora.feature.system.wifi.resolve

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.jmdns.JmDNS

object MdnsResolver {
    private val serviceTypes = listOf(
        "_workstation._tcp.local.",
        "_device-info._tcp.local.",
        "_companion-link._tcp.local.",
        "_smb._tcp.local.",
        "_googlecast._tcp.local.",
        "_ssh._tcp.local.",
        "_http._tcp.local.",
        "_airplay._tcp.local.",
        "_raop._tcp.local.",
        "_printer._tcp.local.",
        "_ipp._tcp.local.",
        "_hap._tcp.local.",
        "_adb-tls-connect._tcp.local."
    )

    suspend fun resolveAll(context: Context, bindAddress: InetAddress): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        var jmdns: JmDNS? = null
        try {
            MulticastHelper.acquireMdnsLock(context)
            jmdns = JmDNS.create(bindAddress)
            
            for (type in serviceTypes) {
                currentCoroutineContext().ensureActive()
                val infos = jmdns.list(type, 600)
                for (info in infos) {
                    val cleanName = info.name?.trim()?.takeIf { it.isNotBlank() } ?: continue
                    val addresses = info.inetAddresses
                    for (addr in addresses) {
                        val hostAddr = addr.hostAddress
                        if (!hostAddr.isNullOrBlank() && !results.containsKey(hostAddr)) {
                            results[hostAddr] = cleanName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                jmdns?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            MulticastHelper.releaseMdnsLock()
        }
        results
    }
}
