package com.arwora.feature.system.htmltoapk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Android AAPT2-compliant Binary XML (AXML) encoder.
 * Produces valid AndroidManifest.xml with:
 * - Proper package name (null namespace)
 * - Standard SDK configuration (minSdkVersion 21, targetSdkVersion 34)
 * - Internet permission
 * - Application and Main/Launcher Activity declarations
 * - 1:1 mapped ResMap chunk for accurate attribute identification by Android's PackageParser
 */
object AxmlEncoder {

    data class Attr(
        val nsIdx: Int,
        val nameIdx: Int,
        val rawIdx: Int,
        val type: Byte,
        val data: Int
    )

    fun encode(
        packageName: String,
        appName: String,
        activityClass: String = "com.arwora.htmlapp.HtmlAppActivity",
        minSdk: Int = 21,
        targetSdk: Int = 34
    ): ByteArray {
        val resAttrs = listOf(
            "theme" to 0x01010000,
            "label" to 0x01010001,
            "icon" to 0x01010002,
            "name" to 0x01010003,
            "hasCode" to 0x0101000c,
            "exported" to 0x01010010,
            "windowSoftInputMode" to 0x0101022b,
            "hardwareAccelerated" to 0x010102d3,
            "minSdkVersion" to 0x0101020c,
            "versionCode" to 0x0101021b,
            "versionName" to 0x0101021c,
            "targetSdkVersion" to 0x01010270,
            "allowBackup" to 0x01010280,
            "supportsRtl" to 0x010103af,
            "roundIcon" to 0x0101052c
        )

        val resNames = resAttrs.map { it.first }
        val resIds = resAttrs.map { it.second }

        val otherStrings = listOf(
            "1.0",
            appName,
            "action",
            "activity",
            "android",
            "android.intent.action.MAIN",
            "android.intent.category.LAUNCHER",
            "android.permission.INTERNET",
            "application",
            "category",
            activityClass,
            packageName,
            "http://schemas.android.com/apk/res/android",
            "intent-filter",
            "manifest",
            "package",
            "uses-permission",
            "uses-sdk"
        )

        val allStrings = resNames + otherStrings
        val strMap = allStrings.mapIndexed { index, s -> s to index }.toMap()

        val nsUriIdx = strMap["http://schemas.android.com/apk/res/android"]!!
        val nsAndroidIdx = strMap["android"]!!

        val buffer = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)

        fun putShortLE(v: Int) = buffer.putShort(v.toShort())
        fun putIntLE(v: Int) = buffer.putInt(v)
        fun putIntLEAt(pos: Int, v: Int) {
            val cur = buffer.position()
            buffer.position(pos)
            buffer.putInt(v)
            buffer.position(cur)
        }

        // 1. AXML File Header (0x0003)
        putShortLE(0x0003) // RES_XML_TYPE
        putShortLE(0x0008) // headerSize
        val fileSizePos = buffer.position()
        putIntLE(0) // file size placeholder

        // 2. String Pool Chunk (0x0001) - UTF-16LE
        val stringPoolStart = buffer.position()
        putShortLE(0x0001) // RES_STRING_POOL_TYPE
        putShortLE(0x001C) // headerSize = 28
        val stringPoolSizePos = buffer.position()
        putIntLE(0) // pool size placeholder
        putIntLE(allStrings.size) // stringCount
        putIntLE(0) // styleCount
        putIntLE(0) // flags: UTF-16LE
        val stringsStartOffsetPos = buffer.position()
        putIntLE(0) // stringsStart placeholder
        putIntLE(0) // stylesStart

        val offsetsPos = buffer.position()
        for (i in allStrings.indices) {
            putIntLE(0) // placeholder for offsets
        }

        val stringDataStart = buffer.position()
        val stringsRelativeStart = stringDataStart - stringPoolStart
        putIntLEAt(stringsStartOffsetPos, stringsRelativeStart)

        val stringOffsets = IntArray(allStrings.size)
        for (i in allStrings.indices) {
            val str = allStrings[i]
            val strOffset = buffer.position() - stringDataStart
            stringOffsets[i] = strOffset
            putShortLE(str.length)
            for (ch in str) {
                buffer.putChar(ch)
            }
            putShortLE(0) // UTF-16 null terminator
        }

        // Align string pool to 4-byte boundary
        while ((buffer.position() - stringPoolStart) % 4 != 0) {
            buffer.put(0.toByte())
        }

        val stringPoolEnd = buffer.position()
        putIntLEAt(stringPoolSizePos, stringPoolEnd - stringPoolStart)

        // Write string offsets
        for (i in allStrings.indices) {
            putIntLEAt(offsetsPos + i * 4, stringOffsets[i])
        }

        // 3. Resource Map Chunk (0x0180)
        val resMapStart = buffer.position()
        putShortLE(0x0180) // RES_XML_RESOURCE_MAP_TYPE
        putShortLE(0x0008) // headerSize = 8
        putIntLE(8 + resIds.size * 4)
        for (id in resIds) {
            putIntLE(id)
        }

        // 4. Start Namespace Chunk (0x0100)
        putShortLE(0x0100) // RES_XML_START_NAMESPACE_TYPE
        putShortLE(0x0010) // headerSize = 16
        putIntLE(24) // size
        putIntLE(1) // lineNumber
        putIntLE(-1) // comment
        putIntLE(nsAndroidIdx) // prefix "android"
        putIntLE(nsUriIdx) // uri

        fun writeStartElement(nameIdx: Int, attrs: List<Attr>) {
            putShortLE(0x0102) // RES_XML_START_ELEMENT_TYPE
            putShortLE(0x0010) // headerSize = 16
            val elemSize = 36 + attrs.size * 20
            putIntLE(elemSize)
            putIntLE(1) // lineNumber
            putIntLE(-1) // comment
            putIntLE(-1) // ns = null
            putIntLE(nameIdx)
            putShortLE(36) // attributeStart
            putShortLE(20) // attributeSize
            putShortLE(attrs.size) // attributeCount
            putShortLE(0) // idIndex
            putShortLE(0) // classIndex
            putShortLE(0) // styleIndex

            for (a in attrs) {
                putIntLE(a.nsIdx)
                putIntLE(a.nameIdx)
                putIntLE(a.rawIdx)
                putShortLE(8) // typed value size
                buffer.put(0.toByte()) // res0
                buffer.put(a.type) // dataType
                putIntLE(a.data) // data
            }
        }

        fun writeEndElement(nameIdx: Int) {
            putShortLE(0x0103) // RES_XML_END_ELEMENT_TYPE
            putShortLE(0x0010) // headerSize = 16
            putIntLE(24) // size
            putIntLE(1) // lineNumber
            putIntLE(-1) // comment
            putIntLE(-1) // ns = null
            putIntLE(nameIdx)
        }

        // <manifest package="..." versionCode="1" versionName="1.0">
        writeStartElement(
            strMap["manifest"]!!,
            listOf(
                Attr(nsUriIdx, strMap["versionCode"]!!, -1, 0x10.toByte(), 1),
                Attr(nsUriIdx, strMap["versionName"]!!, strMap["1.0"]!!, 0x03.toByte(), strMap["1.0"]!!),
                Attr(-1, strMap["package"]!!, strMap[packageName]!!, 0x03.toByte(), strMap[packageName]!!)
            )
        )

        // <uses-sdk minSdkVersion="21" targetSdkVersion="34"/>
        writeStartElement(
            strMap["uses-sdk"]!!,
            listOf(
                Attr(nsUriIdx, strMap["minSdkVersion"]!!, -1, 0x10.toByte(), minSdk),
                Attr(nsUriIdx, strMap["targetSdkVersion"]!!, -1, 0x10.toByte(), targetSdk)
            )
        )
        writeEndElement(strMap["uses-sdk"]!!)

        // <uses-permission name="android.permission.INTERNET"/>
        writeStartElement(
            strMap["uses-permission"]!!,
            listOf(
                Attr(
                    nsUriIdx,
                    strMap["name"]!!,
                    strMap["android.permission.INTERNET"]!!,
                    0x03.toByte(),
                    strMap["android.permission.INTERNET"]!!
                )
            )
        )
        writeEndElement(strMap["uses-permission"]!!)

        // <application label="..." icon="@0x7f010000" roundIcon="@0x7f010000" hasCode="true" allowBackup="true" supportsRtl="true" hardwareAccelerated="true">
        writeStartElement(
            strMap["application"]!!,
            listOf(
                Attr(nsUriIdx, strMap["label"]!!, strMap[appName]!!, 0x03.toByte(), strMap[appName]!!),
                Attr(nsUriIdx, strMap["icon"]!!, -1, 0x01.toByte(), 0x7F010000),
                Attr(nsUriIdx, strMap["roundIcon"]!!, -1, 0x01.toByte(), 0x7F010000),
                Attr(nsUriIdx, strMap["hasCode"]!!, -1, 0x12.toByte(), -1),
                Attr(nsUriIdx, strMap["allowBackup"]!!, -1, 0x12.toByte(), -1),
                Attr(nsUriIdx, strMap["supportsRtl"]!!, -1, 0x12.toByte(), -1),
                Attr(nsUriIdx, strMap["hardwareAccelerated"]!!, -1, 0x12.toByte(), -1)
            )
        )

        // <activity name="..." label="..." exported="true" windowSoftInputMode="adjustResize">
        writeStartElement(
            strMap["activity"]!!,
            listOf(
                Attr(nsUriIdx, strMap["label"]!!, strMap[appName]!!, 0x03.toByte(), strMap[appName]!!),
                Attr(nsUriIdx, strMap["name"]!!, strMap[activityClass]!!, 0x03.toByte(), strMap[activityClass]!!),
                Attr(nsUriIdx, strMap["exported"]!!, -1, 0x12.toByte(), -1),
                Attr(nsUriIdx, strMap["windowSoftInputMode"]!!, -1, 0x11.toByte(), 0x10) // adjustResize
            )
        )

        // <intent-filter>
        writeStartElement(strMap["intent-filter"]!!, emptyList())

        // <action name="android.intent.action.MAIN"/>
        writeStartElement(
            strMap["action"]!!,
            listOf(
                Attr(
                    nsUriIdx,
                    strMap["name"]!!,
                    strMap["android.intent.action.MAIN"]!!,
                    0x03.toByte(),
                    strMap["android.intent.action.MAIN"]!!
                )
            )
        )
        writeEndElement(strMap["action"]!!)

        // <category name="android.intent.category.LAUNCHER"/>
        writeStartElement(
            strMap["category"]!!,
            listOf(
                Attr(
                    nsUriIdx,
                    strMap["name"]!!,
                    strMap["android.intent.category.LAUNCHER"]!!,
                    0x03.toByte(),
                    strMap["android.intent.category.LAUNCHER"]!!
                )
            )
        )
        writeEndElement(strMap["category"]!!)

        // Close tags
        writeEndElement(strMap["intent-filter"]!!)
        writeEndElement(strMap["activity"]!!)
        writeEndElement(strMap["application"]!!)
        writeEndElement(strMap["manifest"]!!)

        // 5. End Namespace Chunk (0x0101)
        putShortLE(0x0101) // RES_XML_END_NAMESPACE_TYPE
        putShortLE(0x0010) // headerSize = 16
        putIntLE(24) // size
        putIntLE(1) // lineNumber
        putIntLE(-1) // comment
        putIntLE(nsAndroidIdx) // prefix "android"
        putIntLE(nsUriIdx) // uri

        val totalSize = buffer.position()
        putIntLEAt(fileSizePos, totalSize)

        val result = ByteArray(totalSize)
        buffer.position(0)
        buffer.get(result)
        return result
    }
}
