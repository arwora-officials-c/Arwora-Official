package com.arwora.feature.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class CompressorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `all eight specialized compressor types expose expected titles and formats`() {
        val titles = CompressorType.entries.map { it.title }
        assertEquals(
            listOf(
                "Video Compressor",
                "Audio Compressor",
                "Image Compressor",
                "PDF Compressor",
                "Archive Compressor",
                "File Compressor",
                "GIF Compressor",
                "Batch Compressor"
            ),
            titles
        )
        assertEquals(CompressorType.VIDEO, detectCompressorTypeForFile("clip.mp4", "video/mp4"))
        assertEquals(CompressorType.AUDIO, detectCompressorTypeForFile("song.flac", "audio/flac"))
        assertEquals(CompressorType.IMAGE, detectCompressorTypeForFile("photo.png", "image/png"))
        assertEquals(CompressorType.PDF, detectCompressorTypeForFile("doc.pdf", "application/pdf"))
        assertEquals(CompressorType.FILE, detectCompressorTypeForFile("config.json", "application/json"))
        assertEquals(CompressorType.GIF, detectCompressorTypeForFile("anim.gif", "image/gif"))
        assertEquals(CompressorType.ARCHIVE, detectCompressorTypeForFile("backup.tar", "application/x-tar"))
    }

    @Test
    fun `image compressor scales and re-encodes bitmap cleanly on Dispatchers IO`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val srcFile = File(context.cacheDir, "test_large_photo.png")
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(42, 120, 210))
        FileOutputStream(srcFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bmp.recycle()

        val item = CompressorFileItem(
            uri = Uri.fromFile(srcFile),
            fileName = "test_large_photo.png",
            mimeType = "image/png",
            originalSizeBytes = srcFile.length()
        )
        val result = CompressorEngines.compressImage(
            context = context,
            item = item,
            options = ImageCompressionOptions(
                format = ImageOutputFormat.JPEG,
                quality = 70,
                maxDimensionPx = 400
            ),
            onProgress = {}
        )
        assertTrue(result.outputFile.exists())
        assertTrue(result.compressedBytes > 0L)
        assertTrue(result.outputName.endsWith(".jpg"))
        srcFile.delete()
        result.outputFile.delete()
    }

    @Test
    fun `archive compressor creates valid deflated zip with deduplicated names`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val f1 = File(context.cacheDir, "sample_a.txt").apply {
            writeText("Arwora Hyper Toolkit Compression Engine ".repeat(80))
        }
        val f2 = File(context.cacheDir, "sample_b.txt").apply {
            writeText("Repetitive test payload for deflate verification ".repeat(60))
        }

        val items = listOf(
            CompressorFileItem(Uri.fromFile(f1), "report.txt", "text/plain", f1.length()),
            CompressorFileItem(Uri.fromFile(f2), "report.txt", "text/plain", f2.length())
        )
        val result = CompressorEngines.compressArchive(
            context = context,
            items = items,
            options = ArchiveCompressionOptions(level = 9, useGzipForSingleFile = false),
            onProgress = {}
        )
        assertTrue(result.outputFile.exists())
        assertTrue(result.compressedBytes < result.originalBytes)

        val entries = mutableListOf<String>()
        ZipInputStream(result.outputFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries.add(entry.name)
                zis.closeEntry()
            }
        }
        assertEquals(listOf("report.txt", "report (1).txt"), entries)
        f1.delete()
        f2.delete()
        result.outputFile.delete()
    }

    @Test
    fun `file compressor minifies json preserving string literals and compresses html`() {
        val rawJson = """
            {
              "name": "Arwora   Hyper   Toolkit",
              "engine": "All-in-One Compressor",
              "items": [ 1, 2, 3 ]
            }
        """.trimIndent()
        val minJson = CompressorEngines.minifyContentByExtension(rawJson, "json")
        assertEquals(
            """{"name":"Arwora   Hyper   Toolkit","engine":"All-in-One Compressor","items":[1,2,3]}""",
            minJson
        )

        val rawHtml = """
            <html>
              <!-- strip me -->
              <body>   <p>Hello</p>   </body>
            </html>
        """.trimIndent()
        val minHtml = CompressorEngines.minifyContentByExtension(rawHtml, "html")
        assertEquals("<html><body><p>Hello</p></body></html>", minHtml)
    }

    @Test
    fun `gif codec engine scales frames skips cleanly and writes valid GIF89a header and trailer`(): Unit = runBlocking {
        val w = 24
        val h = 24
        val f1 = GifFrameData(IntArray(w * h) { Color.RED }, w, h, 80)
        val f2 = GifFrameData(IntArray(w * h) { Color.GREEN }, w, h, 80)
        val f3 = GifFrameData(IntArray(w * h) { Color.BLUE }, w, h, 80)

        val out = ByteArrayOutputStream()
        GifCodecEngine.compressFramesToGif(
            frames = listOf(f1, f2, f3),
            options = GifCompressionOptions(scalePercent = 50, frameSkip = 2, maxColors = 64),
            outputStream = out
        )
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 32)
        assertArrayEquals("GIF89a".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 6))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
    }

    @Test
    fun `media transcode helpers align dimensions to 16 and resample pcm safely`() {
        val (w, h) = MediaTranscodeEngine.computeScaledEvenDimensions(1920, 1080, 720)
        assertEquals(0, w % 16)
        assertEquals(0, h % 16)
        assertEquals(1280, w)
        assertEquals(720, h)

        val stereoPcm = ByteArray(16) { 10 }
        val monoPcm = MediaTranscodeEngine.resamplePcm16(
            input = stereoPcm,
            srcSampleRate = 44100,
            srcChannels = 2,
            dstSampleRate = 44100,
            dstChannels = 1
        )
        assertEquals(8, monoPcm.size)
    }

    @Test
    fun `compressor screen displays mode switcher and specialized controls cleanly`() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                CompressorScreen(
                    initialTypeId = "image",
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Image Compressor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compressor_mode_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compressor_pick_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Compress Now").assertIsDisplayed()

        // Switch to PDF mode via chip
        composeTestRule.onNodeWithText("PDF").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PDF Compressor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Balanced").assertIsDisplayed()
    }

    @Test
    fun `global compression settings persist updates and sync into CompressorViewModel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        CompressionDefaultsStore.resetToDefaults(app)

        CompressionDefaultsStore.update(app) {
            it.copy(
                imageFormat = ImageOutputFormat.WEBP_LOSSY,
                imageQuality = 64,
                imageMaxDimensionPx = 1080,
                videoBitrateKbps = 2500,
                videoTargetHeight = 1080,
                audioBitrateKbps = 128,
                pdfPreset = CompressorQualityPreset.EXTREME,
                batchBundleAsZip = false
            )
        }

        // Reload cleanly from SharedPreferences and verify VM picks up persisted defaults
        val reloaded = CompressionDefaultsStore.reload(app)
        assertEquals(ImageOutputFormat.WEBP_LOSSY, reloaded.imageFormat)
        assertEquals(64, reloaded.imageQuality)
        assertEquals(1080, reloaded.imageMaxDimensionPx)
        assertEquals(2500, reloaded.videoBitrateKbps)
        assertEquals(1080, reloaded.videoTargetHeight)
        assertEquals(128, reloaded.audioBitrateKbps)
        assertEquals(CompressorQualityPreset.EXTREME, reloaded.pdfPreset)
        assertEquals(false, reloaded.batchBundleAsZip)

        val vm = CompressorViewModel(app)
        assertEquals(ImageOutputFormat.WEBP_LOSSY, vm.uiState.value.imageOptions.format)
        assertEquals(64, vm.uiState.value.imageOptions.quality)
        assertEquals(2500, vm.uiState.value.videoOptions.bitrateKbps)
        assertEquals(128, vm.uiState.value.audioOptions.bitrateKbps)
        assertEquals(CompressorQualityPreset.EXTREME, vm.uiState.value.pdfOptions.preset)
        assertEquals(false, vm.uiState.value.batchBundleAsZip)

        CompressionDefaultsStore.resetToDefaults(app)
    }

    @Test
    fun `compression settings screen updates image format and video bitrate defaults cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompressionDefaultsStore.resetToDefaults(context)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                CompressionSettingsScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Compression Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cs_image_format_jpeg").performClick()
        composeTestRule.onNodeWithTag("cs_video_bitrate_2500").performClick()
        composeTestRule.waitForIdle()

        val updated = CompressionDefaultsStore.settings.value
        assertEquals(ImageOutputFormat.JPEG, updated.imageFormat)
        assertEquals(2500, updated.videoBitrateKbps)
        composeTestRule.onNodeWithTag("compression_settings_summary").assertIsDisplayed()

        // Reset via TopAppBar button restores default values
        composeTestRule.onNodeWithTag("compression_settings_reset_top").performClick()
        composeTestRule.waitForIdle()
        val reset = CompressionDefaultsStore.settings.value
        assertEquals(ImageOutputFormat.AUTO, reset.imageFormat)
        assertEquals(1400, reset.videoBitrateKbps)
    }
}
