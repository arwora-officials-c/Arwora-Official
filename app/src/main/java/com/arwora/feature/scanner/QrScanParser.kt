package com.arwora.feature.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import com.arwora.utils.SoundEffectManager
import com.google.mlkit.vision.barcode.common.Barcode

enum class QrScanCategory(val badgeLabel: String) {
    WIFI("Wi-Fi Network"),
    URL("Web Link"),
    CONTACT("Contact Card"),
    PRODUCT("Product Barcode"),
    EMAIL("Email"),
    PHONE("Phone Number"),
    SMS("SMS Message"),
    GEO("Location"),
    TEXT("Plain Text")
}

data class QrWifiDetails(
    val ssid: String,
    val securityType: String,
    val password: String,
    val isHidden: Boolean
)

data class QrContactDetails(
    val name: String,
    val phone: String,
    val email: String,
    val organization: String
)

data class QrScanResult(
    val rawValue: String,
    val displayValue: String,
    val formatLabel: String,
    val category: QrScanCategory,
    val wifiDetails: QrWifiDetails? = null,
    val contactDetails: QrContactDetails? = null,
    val normalizedUrl: String? = null
)

object QrScanParser {

    fun formatLabelForCode(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "Barcode"
    }

    fun isProductBarcodeFormat(format: Int): Boolean = when (format) {
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_CODE_93,
        Barcode.FORMAT_CODABAR,
        Barcode.FORMAT_ITF -> true
        else -> false
    }

    fun fromMlKitBarcode(barcode: Barcode): QrScanResult? {
        val raw = barcode.rawValue?.takeIf { it.isNotBlank() }
            ?: barcode.displayValue?.takeIf { it.isNotBlank() }
            ?: return null
        val display = barcode.displayValue?.takeIf { it.isNotBlank() } ?: raw
        val formatLabel = formatLabelForCode(barcode.format)

        if (barcode.valueType == Barcode.TYPE_WIFI || raw.startsWith("WIFI:", ignoreCase = true)) {
            val wifi = barcode.wifi?.let { w ->
                QrWifiDetails(
                    ssid = w.ssid.orEmpty(),
                    securityType = when (w.encryptionType) {
                        Barcode.WiFi.TYPE_WEP -> "WEP"
                        Barcode.WiFi.TYPE_WPA -> "WPA/WPA2"
                        Barcode.WiFi.TYPE_OPEN -> "Open"
                        else -> "WPA/WPA2"
                    },
                    password = w.password.orEmpty(),
                    isHidden = false
                )
            } ?: parseWifiPayload(raw)
            if (wifi != null && wifi.ssid.isNotBlank()) {
                return QrScanResult(
                    rawValue = raw,
                    displayValue = wifi.ssid,
                    formatLabel = formatLabel,
                    category = QrScanCategory.WIFI,
                    wifiDetails = wifi
                )
            }
        }

        if (barcode.valueType == Barcode.TYPE_URL || looksLikeUrl(raw)) {
            val url = normalizeUrl(barcode.url?.url ?: raw)
            return QrScanResult(
                rawValue = raw,
                displayValue = url,
                formatLabel = formatLabel,
                category = QrScanCategory.URL,
                normalizedUrl = url
            )
        }

        if (barcode.valueType == Barcode.TYPE_CONTACT_INFO ||
            raw.startsWith("BEGIN:VCARD", ignoreCase = true) ||
            raw.startsWith("MECARD:", ignoreCase = true)
        ) {
            val c = barcode.contactInfo
            val fallback = parseContactPayload(raw)
            val contact = QrContactDetails(
                name = c?.name?.formattedName?.takeIf { it.isNotBlank() } ?: fallback.name,
                phone = c?.phones?.firstOrNull()?.number.orEmpty().ifBlank { fallback.phone },
                email = c?.emails?.firstOrNull()?.address.orEmpty().ifBlank { fallback.email },
                organization = c?.organization.orEmpty().ifBlank { fallback.organization }
            )
            return QrScanResult(
                rawValue = raw,
                displayValue = contact.name.ifBlank { display },
                formatLabel = formatLabel,
                category = QrScanCategory.CONTACT,
                contactDetails = contact
            )
        }

        if (barcode.valueType == Barcode.TYPE_PRODUCT || isProductBarcodeFormat(barcode.format)) {
            return QrScanResult(
                rawValue = raw,
                displayValue = display,
                formatLabel = formatLabel,
                category = QrScanCategory.PRODUCT
            )
        }

        return parseRawPayload(raw, formatLabel)
    }

    fun parseRawPayload(rawInput: String, formatLabel: String = "QR Code"): QrScanResult {
        val raw = rawInput.trim()
        parseWifiPayload(raw)?.let { wifi ->
            return QrScanResult(
                rawValue = raw,
                displayValue = wifi.ssid,
                formatLabel = formatLabel,
                category = QrScanCategory.WIFI,
                wifiDetails = wifi
            )
        }
        if (looksLikeUrl(raw)) {
            val url = normalizeUrl(raw)
            return QrScanResult(
                rawValue = raw,
                displayValue = url,
                formatLabel = formatLabel,
                category = QrScanCategory.URL,
                normalizedUrl = url
            )
        }
        if (raw.startsWith("BEGIN:VCARD", ignoreCase = true) || raw.startsWith("MECARD:", ignoreCase = true)) {
            val contact = parseContactPayload(raw)
            return QrScanResult(
                rawValue = raw,
                displayValue = contact.name.ifBlank { raw },
                formatLabel = formatLabel,
                category = QrScanCategory.CONTACT,
                contactDetails = contact
            )
        }
        if (raw.startsWith("mailto:", ignoreCase = true)) {
            return QrScanResult(
                rawValue = raw,
                displayValue = raw.removePrefix("mailto:").substringBefore("?"),
                formatLabel = formatLabel,
                category = QrScanCategory.EMAIL
            )
        }
        if (raw.startsWith("tel:", ignoreCase = true)) {
            return QrScanResult(
                rawValue = raw,
                displayValue = raw.removePrefix("tel:"),
                formatLabel = formatLabel,
                category = QrScanCategory.PHONE
            )
        }
        if (raw.startsWith("smsto:", ignoreCase = true) || raw.startsWith("sms:", ignoreCase = true)) {
            return QrScanResult(
                rawValue = raw,
                displayValue = raw.substringAfter(":"),
                formatLabel = formatLabel,
                category = QrScanCategory.SMS
            )
        }
        if (raw.startsWith("geo:", ignoreCase = true)) {
            return QrScanResult(
                rawValue = raw,
                displayValue = raw.removePrefix("geo:"),
                formatLabel = formatLabel,
                category = QrScanCategory.GEO
            )
        }
        if (formatLabel in setOf("EAN-13", "EAN-8", "UPC-A", "UPC-E", "Code 128", "Code 39", "Code 93", "Codabar", "ITF")) {
            return QrScanResult(
                rawValue = raw,
                displayValue = raw,
                formatLabel = formatLabel,
                category = QrScanCategory.PRODUCT
            )
        }
        return QrScanResult(
            rawValue = raw,
            displayValue = raw,
            formatLabel = formatLabel,
            category = QrScanCategory.TEXT
        )
    }

    fun parseWifiPayload(raw: String): QrWifiDetails? {
        if (!raw.startsWith("WIFI:", ignoreCase = true)) return null
        val body = raw.substring(5)
        val fields = mutableMapOf<String, String>()
        val token = StringBuilder()
        var escaped = false

        fun flushToken() {
            val entry = token.toString()
            token.setLength(0)
            val colonIdx = entry.indexOf(':')
            if (colonIdx > 0) {
                val k = entry.substring(0, colonIdx).uppercase()
                val v = entry.substring(colonIdx + 1)
                fields[k] = v
            }
        }

        for (ch in body) {
            if (escaped) {
                token.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == ';') {
                flushToken()
            } else {
                token.append(ch)
            }
        }
        if (token.isNotEmpty()) flushToken()

        val ssid = fields["S"]?.trim().orEmpty()
        if (ssid.isEmpty()) return null
        val rawType = fields["T"]?.uppercase().orEmpty()
        val security = when {
            rawType.contains("WPA3") || rawType.contains("SAE") -> "WPA3"
            rawType.contains("WPA") -> "WPA/WPA2"
            rawType.contains("WEP") -> "WEP"
            rawType == "NOPASS" || rawType.isEmpty() -> "Open"
            else -> rawType
        }
        val password = fields["P"].orEmpty()
        val hidden = fields["H"]?.equals("true", ignoreCase = true) == true
        return QrWifiDetails(
            ssid = ssid,
            securityType = security,
            password = password,
            isHidden = hidden
        )
    }

    fun parseContactPayload(raw: String): QrContactDetails {
        if (raw.startsWith("MECARD:", ignoreCase = true)) {
            val body = raw.substring(7)
            val fields = mutableMapOf<String, String>()
            body.split(";").forEach { part ->
                val idx = part.indexOf(':')
                if (idx > 0) {
                    val key = part.substring(0, idx).uppercase()
                    val value = part.substring(idx + 1).trim()
                    if (value.isNotEmpty()) {
                        fields[key] = value
                    }
                }
            }
            return QrContactDetails(
                name = fields["N"].orEmpty().replace(",", " ").trim(),
                phone = fields["TEL"].orEmpty(),
                email = fields["EMAIL"].orEmpty(),
                organization = fields["ORG"].orEmpty()
            )
        }
        return QrContactDetails(
            name = extractVCardField(raw, "FN").ifBlank { extractVCardField(raw, "N").replace(";", " ").trim() },
            phone = extractVCardField(raw, "TEL"),
            email = extractVCardField(raw, "EMAIL"),
            organization = extractVCardField(raw, "ORG")
        )
    }

    private fun looksLikeUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("www.", ignoreCase = true)
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("www.", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun extractVCardField(raw: String, key: String): String {
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val upper = trimmed.uppercase()
            if (upper.startsWith("$key:") || upper.startsWith("$key;")) {
                return trimmed.substringAfter(":").trim()
            }
        }
        return ""
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Scanned Code") {
        runCatching {
            SoundEffectManager.vibrate(context, 30L)
            SoundEffectManager.playClick(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Unable to copy to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String) {
        runCatching {
            SoundEffectManager.playClick(context)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Scanned Result").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrlLocally(context: Context, rawUrl: String) {
        val url = normalizeUrl(rawUrl)
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, "Unsupported link scheme", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            SoundEffectManager.playClick(context)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No local browser available to open link", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveContactLocally(context: Context, contact: QrContactDetails) {
        runCatching {
            SoundEffectManager.playClick(context)
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                if (contact.name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
                if (contact.phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
                if (contact.email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
                if (contact.organization.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, contact.organization)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No Contacts app available", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDialerLocally(context: Context, phoneRaw: String) {
        val number = phoneRaw.removePrefix("tel:").trim()
        runCatching {
            SoundEffectManager.playClick(context)
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Unable to open phone dialer", Toast.LENGTH_SHORT).show()
        }
    }

    fun composeEmailLocally(context: Context, emailRaw: String) {
        val clean = if (emailRaw.startsWith("mailto:", ignoreCase = true)) emailRaw else "mailto:$emailRaw"
        runCatching {
            SoundEffectManager.playClick(context)
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No email app installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun connectWifi(context: Context, wifi: QrWifiDetails) {
        SoundEffectManager.playClick(context)
        if (wifi.password.isNotEmpty()) {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", wifi.password))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val suggestionBuilder = WifiNetworkSuggestion.Builder()
                    .setSsid(wifi.ssid)
                    .setIsHiddenSsid(wifi.isHidden)
                if (wifi.securityType.contains("WPA3")) {
                    suggestionBuilder.setWpa3Passphrase(wifi.password)
                } else if (wifi.securityType.contains("WPA") && wifi.password.isNotEmpty()) {
                    suggestionBuilder.setWpa2Passphrase(wifi.password)
                }
                wifiManager?.addNetworkSuggestions(listOf(suggestionBuilder.build()))
            }
            runCatching {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                Toast.makeText(
                    context,
                    if (wifi.password.isNotEmpty()) "Wi-Fi suggested & password copied for ${wifi.ssid}" else "Select ${wifi.ssid} in Wi-Fi panel",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        runCatching {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                if (wifi.password.isNotEmpty()) "Password copied! Connect to ${wifi.ssid}" else "Open Wi-Fi to connect to ${wifi.ssid}",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(context, "Unable to open Wi-Fi settings", Toast.LENGTH_SHORT).show()
        }
    }
}
