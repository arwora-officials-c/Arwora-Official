package com.arwora.utils

import android.content.Context
import android.content.SharedPreferences

data class DnsProvider(
    val id: String,
    val name: String,
    val category: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dotHost: String,
    val description: String
)

object DnsManager {
    private const val PREF_NAME = "dns_manager_prefs"
    private const val KEY_SELECTED_ID = "selected_dns_id"
    private const val KEY_SELECTED_NAME = "selected_dns_name"
    private const val KEY_SELECTED_HOST = "selected_dns_host"
    private const val KEY_SELECTED_PRIMARY = "selected_dns_primary"
    private const val KEY_SELECTED_SECONDARY = "selected_dns_secondary"

    val providers = listOf(
        // Speed & Performance
        DnsProvider("cloudflare", "Cloudflare", "Speed & Performance", "1.1.1.1", "1.0.0.1", "one.one.one.one", "Fastest general DNS with built-in privacy protection."),
        DnsProvider("google", "Google", "Speed & Performance", "8.8.8.8", "8.8.4.4", "dns.google", "Highly reliable, secure, and globally distributed DNS."),
        DnsProvider("alidns", "AliDNS", "Speed & Performance", "223.5.5.5", "223.6.6.6", "dns.alidns.com", "Alibaba's public DNS for speed and reliability."),
        DnsProvider("baidu", "Baidu", "Speed & Performance", "180.76.76.76", "", "dns.baidu.com", "Baidu's fast public DNS service."),
        DnsProvider("opendns_speed", "OpenDNS Home", "Speed & Performance", "208.67.222.222", "208.67.220.220", "dns.opendns.com", "Fast and secure DNS from Cisco."),
        DnsProvider("verisign", "Verisign", "Speed & Performance", "64.6.64.6", "64.6.65.6", "dns.verisign.com", "Public DNS focused on stability and security."),
        DnsProvider("level3", "Level3", "Speed & Performance", "4.2.2.1", "4.2.2.2", "dns.level3.com", "Enterprise-grade DNS infrastructure."),
        DnsProvider("tenta", "Tenta DNS", "Speed & Performance", "99.192.182.100", "", "dns.tenta.com", "Privacy-focused DNS built into Tenta Browser."),
        DnsProvider("dnspod", "DNSPod", "Speed & Performance", "119.29.29.29", "", "dot.pub", "Tencent's public DNS for optimal speed."),
        DnsProvider("dns4all", "DNS4All", "Speed & Performance", "156.154.70.1", "156.154.71.1", "dns.dns4all.eu", "European DNS focusing on speed."),

        // Ad & Tracker Blockers
        DnsProvider("adguard", "AdGuard", "Ad & Tracker Blockers", "94.140.14.14", "94.140.15.15", "dns.adguard-dns.com", "Blocks ads, trackers, and phishing domains dynamically."),
        DnsProvider("mullvad", "Mullvad", "Ad & Tracker Blockers", "194.242.2.2", "194.242.2.3", "adblock.dns.mullvad.net", "Privacy-focused tracker blocking from Mullvad VPN."),
        DnsProvider("controld", "Control D", "Ad & Tracker Blockers", "76.76.2.0", "76.76.10.0", "p2.freedns.controld.com", "Advanced filtering engine blocking ads and common trackers."),
        DnsProvider("alternate_dns", "Alternate DNS", "Ad & Tracker Blockers", "76.76.19.19", "76.223.122.150", "dns.alternate-dns.com", "Blocks ads and malicious websites globally."),
        DnsProvider("rethinkdns", "RethinkDNS", "Ad & Tracker Blockers", "", "", "max.rethinkdns.com", "Privacy-first DNS that blocks malware and ads."),
        DnsProvider("ahadns", "AhaDNS", "Ad & Tracker Blockers", "45.91.92.121", "", "dot.ahadns.com", "Privacy-focused DNS that blocks ads and trackers."),
        DnsProvider("decloudus", "DeCloudus", "Ad & Tracker Blockers", "", "", "dns.decloudus.com", "De-Google and block trackers with DeCloudus."),
        DnsProvider("libredns", "LibreDNS", "Ad & Tracker Blockers", "116.202.176.26", "", "dot.libredns.gr", "Community-run, private DNS based in Germany."),
        DnsProvider("blahdns_ads", "BlahDNS (Ads)", "Ad & Tracker Blockers", "", "", "dot-jp.blahdns.com", "Blocks ads and trackers with BlahDNS."),
        DnsProvider("puredns", "PureDNS", "Ad & Tracker Blockers", "", "", "puredns.org", "Uncensored DNS with ad-blocking features."),

        // Security & Malware Protection
        DnsProvider("quad9", "Quad9", "Security & Malware Protection", "9.9.9.9", "149.112.112.112", "dns.quad9.net", "Blocks known malicious domains and phishing sites in real-time."),
        DnsProvider("comodo", "Comodo", "Security & Malware Protection", "8.26.56.26", "8.20.247.20", "dns.comodo.com", "Focuses on threat protection and security."),
        DnsProvider("opendns_security", "OpenDNS Security", "Security & Malware Protection", "208.67.222.222", "208.67.220.220", "dns.opendns.com", "Enterprise-grade web protection and threat filtering."),
        DnsProvider("safesurfer", "SafeSurfer", "Security & Malware Protection", "104.197.28.121", "104.155.201.52", "dns.safesurfer.io", "Cyber-security focused protection."),
        DnsProvider("cloudflare_security", "Cloudflare Security", "Security & Malware Protection", "1.1.1.2", "1.0.0.2", "security.cloudflare-dns.com", "Cloudflare DNS that blocks malware."),
        DnsProvider("cleanbrowsing_security", "CleanBrowsing Security", "Security & Malware Protection", "185.228.168.9", "185.228.169.9", "security-filter-dns.cleanbrowsing.org", "Malware and phishing protection filter."),
        DnsProvider("yandex_safe", "Yandex Safe", "Security & Malware Protection", "77.88.8.88", "77.88.8.2", "safe.dns.yandex.ru", "Protects against malicious sites and bots."),
        DnsProvider("norton", "Norton ConnectSafe", "Security & Malware Protection", "199.85.126.10", "199.85.127.10", "dns.norton.com", "Blocks malicious websites and phishing."),
        DnsProvider("dns4eu", "DNS4EU Security", "Security & Malware Protection", "9.9.9.11", "", "dns4eu.eu", "Official EU public DNS project for cybersecurity."),
        DnsProvider("freenom", "Freenom World", "Security & Malware Protection", "80.80.80.80", "80.80.81.81", "dns.freenom.com", "Anonymous and secure DNS from Freenom."),

        // Family Safety & SafeSearch
        DnsProvider("adguard_family", "AdGuard Family", "Family Safety & SafeSearch", "94.140.14.15", "94.140.15.16", "family.adguard-dns.com", "Blocks adult content and enforces safe search."),
        DnsProvider("cleanbrowsing_family", "CleanBrowsing Family", "Family Safety & SafeSearch", "185.228.168.168", "185.228.169.168", "family-filter-dns.cleanbrowsing.org", "Blocks proxy networks, adult sites, and malicious domains."),
        DnsProvider("opendns_family", "OpenDNS FamilyShield", "Family Safety & SafeSearch", "208.67.222.123", "208.67.220.123", "familyshield.opendns.com", "Blocks adult content out of the box."),
        DnsProvider("cloudflare_family", "Cloudflare Family", "Family Safety & SafeSearch", "1.1.1.3", "1.0.0.3", "family.cloudflare-dns.com", "Blocks malware and adult content."),
        DnsProvider("yandex_family", "Yandex Family", "Family Safety & SafeSearch", "77.88.8.7", "77.88.8.3", "family.dns.yandex.ru", "Blocks adult content and dangerous sites."),
        DnsProvider("safe_dns_family", "SafeDNS Family", "Family Safety & SafeSearch", "195.46.39.39", "195.46.39.40", "dns.safedns.com", "Comprehensive web filtering for families."),
        DnsProvider("neustar_family", "Neustar Family", "Family Safety & SafeSearch", "156.154.70.3", "156.154.71.3", "family.neustar.biz", "Blocks adult content and offensive material."),
        DnsProvider("tenta_family", "Tenta Family", "Family Safety & SafeSearch", "", "", "family.tenta.com", "Safe surfing with Tenta's family filter."),
        DnsProvider("controld_family", "Control D Family", "Family Safety & SafeSearch", "76.76.2.3", "", "family.freedns.controld.com", "Control D filter for family safety."),
        DnsProvider("dns_watch_family", "DNS.WATCH Family", "Family Safety & SafeSearch", "", "", "family.dns.watch", "Uncensored DNS with optional family filtering."),

        // Privacy & Uncensored
        DnsProvider("blahdns_jp", "BlahDNS Japan", "Privacy & Uncensored", "", "", "dot-jp.blahdns.com", "Small hobby uncensored DNS (Japan)."),
        DnsProvider("blahdns_de", "BlahDNS Germany", "Privacy & Uncensored", "", "", "dot-de.blahdns.com", "Small hobby uncensored DNS (Germany)."),
        DnsProvider("dns_watch", "DNS.WATCH", "Privacy & Uncensored", "84.200.69.80", "84.200.70.40", "resolver2.dns.watch", "Fast and uncensored internet without logging."),
        DnsProvider("njalla", "Njal.la", "Privacy & Uncensored", "95.215.19.53", "81.95.15.59", "dns.njal.la", "Privacy-first DNS provider."),
        DnsProvider("cira", "CIRA Canadian Shield", "Privacy & Uncensored", "149.112.121.20", "149.112.122.20", "shield.cira.ca", "Private DNS operated by CIRA in Canada."),
        DnsProvider("uncensored_dns", "UncensoredDNS", "Privacy & Uncensored", "91.239.100.100", "89.233.43.71", "unicast.censurfridns.dk", "DNS service with no logging and no censorship."),
        DnsProvider("digitale_gesellschaft", "Digitale Gesellschaft", "Privacy & Uncensored", "5.1.66.255", "185.150.99.255", "dns3.digitalegesellschaft.ch", "Swiss-based privacy-focused DNS."),
        DnsProvider("switch_dns", "SWITCH DNS", "Privacy & Uncensored", "130.59.31.248", "130.59.31.251", "dns.switch.ch", "Swiss academic and research DNS."),
        DnsProvider("foundation_dns", "Foundation DNS", "Privacy & Uncensored", "", "", "dns.foundation", "Decentralized and private DNS foundation."),
        DnsProvider("opennic", "OpenNIC DNS", "Privacy & Uncensored", "185.121.177.177", "169.239.202.202", "dot.opennic.org", "Democratic and privacy-focused DNS network."),

        // Custom Hostname
        DnsProvider("custom", "Custom Hostname", "Custom Hostname", "", "", "", "Manually enter a custom DoT/DoH hostname.")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSelectedDns(context: Context, provider: DnsProvider, customHost: String = "") {
        getPrefs(context).edit().apply {
            putString(KEY_SELECTED_ID, provider.id)
            putString(KEY_SELECTED_NAME, provider.name)
            putString(KEY_SELECTED_HOST, if (provider.id == "custom") customHost else provider.dotHost)
            putString(KEY_SELECTED_PRIMARY, provider.primaryIp)
            putString(KEY_SELECTED_SECONDARY, provider.secondaryIp)
            apply()
        }
    }

    fun getSelectedDns(context: Context): DnsProvider {
        val prefs = getPrefs(context)
        val id = prefs.getString(KEY_SELECTED_ID, "cloudflare") ?: "cloudflare"
        val customHost = prefs.getString(KEY_SELECTED_HOST, "") ?: ""
        val provider = providers.find { it.id == id } ?: providers[0]
        if (provider.id == "custom") {
            return provider.copy(dotHost = customHost)
        }
        return provider
    }
}
