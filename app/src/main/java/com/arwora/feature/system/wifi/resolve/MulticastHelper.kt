package com.arwora.feature.system.wifi.resolve

import android.content.Context
import android.net.wifi.WifiManager

object MulticastHelper {
    private var multicastLock: WifiManager.MulticastLock? = null

    fun acquireMdnsLock(context: Context) {
        if (multicastLock?.isHeld == true) return
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("MdnsResolverLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    fun releaseMdnsLock() {
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
        multicastLock = null
    }
}
