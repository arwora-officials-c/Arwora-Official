package com.arwora.feature.compressor

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class GlobalCompressionSettings(
    val imageFormat: ImageOutputFormat = ImageOutputFormat.AUTO,
    val imageQuality: Int = 72,
    val imageMaxDimensionPx: Int = 1600,
    val videoTargetHeight: Int = 720,
    val videoBitrateKbps: Int = 1400,
    val videoKeepAudio: Boolean = true,
    val audioBitrateKbps: Int = 96,
    val audioSampleRateHz: Int = 44100,
    val audioMono: Boolean = false,
    val pdfPreset: CompressorQualityPreset = CompressorQualityPreset.BALANCED,
    val archiveLevel: Int = 9,
    val archiveGzipSingleFile: Boolean = false,
    val fileMinifyCode: Boolean = true,
    val fileWrapWithGzip: Boolean = false,
    val gifScalePercent: Int = 70,
    val gifFrameSkip: Int = 2,
    val gifMaxColors: Int = 128,
    val batchBundleAsZip: Boolean = true
) {
    fun toVideoOptions(): VideoCompressionOptions = VideoCompressionOptions(
        targetHeight = videoTargetHeight,
        bitrateKbps = videoBitrateKbps,
        keepAudio = videoKeepAudio
    )

    fun toAudioOptions(): AudioCompressionOptions = AudioCompressionOptions(
        bitrateKbps = audioBitrateKbps,
        sampleRateHz = audioSampleRateHz,
        mono = audioMono
    )

    fun toImageOptions(): ImageCompressionOptions = ImageCompressionOptions(
        format = imageFormat,
        quality = imageQuality,
        maxDimensionPx = imageMaxDimensionPx
    )

    fun toPdfOptions(): PdfCompressionOptions = PdfCompressionOptions(
        preset = pdfPreset
    )

    fun toArchiveOptions(): ArchiveCompressionOptions = ArchiveCompressionOptions(
        level = archiveLevel,
        useGzipForSingleFile = archiveGzipSingleFile
    )

    fun toFileOptions(): FileCompressionOptions = FileCompressionOptions(
        minifyCode = fileMinifyCode,
        wrapWithGzip = fileWrapWithGzip
    )

    fun toGifOptions(): GifCompressionOptions = GifCompressionOptions(
        scalePercent = gifScalePercent,
        frameSkip = gifFrameSkip,
        maxColors = gifMaxColors
    )

    fun summaryLabel(): String =
        "${imageFormat.label} $imageQuality% • Video ${videoBitrateKbps} kbps • Audio ${audioBitrateKbps} kbps"
}

/**
 * Single source of truth for global Compressor Hub default formats and quality targets.
 */
object CompressionDefaultsStore {
    private const val PREFS_NAME = "arwora_compression_defaults"

    private const val KEY_IMAGE_FORMAT = "image_format"
    private const val KEY_IMAGE_QUALITY = "image_quality"
    private const val KEY_IMAGE_MAX_DIM = "image_max_dim"
    private const val KEY_VIDEO_HEIGHT = "video_height"
    private const val KEY_VIDEO_BITRATE = "video_bitrate"
    private const val KEY_VIDEO_KEEP_AUDIO = "video_keep_audio"
    private const val KEY_AUDIO_BITRATE = "audio_bitrate"
    private const val KEY_AUDIO_SAMPLE_RATE = "audio_sample_rate"
    private const val KEY_AUDIO_MONO = "audio_mono"
    private const val KEY_PDF_PRESET = "pdf_preset"
    private const val KEY_ARCHIVE_LEVEL = "archive_level"
    private const val KEY_ARCHIVE_GZIP = "archive_gzip_single"
    private const val KEY_FILE_MINIFY = "file_minify"
    private const val KEY_FILE_GZIP = "file_gzip"
    private const val KEY_GIF_SCALE = "gif_scale"
    private const val KEY_GIF_SKIP = "gif_frame_skip"
    private const val KEY_GIF_COLORS = "gif_max_colors"
    private const val KEY_BATCH_ZIP = "batch_bundle_zip"

    private val _settings = MutableStateFlow(GlobalCompressionSettings())
    val settings: StateFlow<GlobalCompressionSettings> = _settings.asStateFlow()

    @Volatile
    private var loadedFromDisk = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context): GlobalCompressionSettings {
        if (!loadedFromDisk) {
            synchronized(this) {
                if (!loadedFromDisk) {
                    _settings.value = readFromPrefs(prefs(context))
                    loadedFromDisk = true
                }
            }
        }
        return _settings.value
    }

    fun reload(context: Context): GlobalCompressionSettings {
        val loaded = readFromPrefs(prefs(context))
        _settings.value = loaded
        loadedFromDisk = true
        return loaded
    }

    fun update(
        context: Context,
        transform: (GlobalCompressionSettings) -> GlobalCompressionSettings
    ): GlobalCompressionSettings {
        val current = ensureLoaded(context)
        val updated = sanitize(transform(current))
        _settings.value = updated
        writeToPrefs(prefs(context), updated)
        return updated
    }

    fun resetToDefaults(context: Context): GlobalCompressionSettings {
        val defaults = GlobalCompressionSettings()
        _settings.value = defaults
        writeToPrefs(prefs(context), defaults)
        loadedFromDisk = true
        return defaults
    }

    private fun sanitize(s: GlobalCompressionSettings): GlobalCompressionSettings = s.copy(
        imageQuality = s.imageQuality.coerceIn(25, 95),
        imageMaxDimensionPx = if (s.imageMaxDimensionPx in listOf(0, 1080, 1600, 2048)) s.imageMaxDimensionPx else 1600,
        videoTargetHeight = if (s.videoTargetHeight in listOf(480, 720, 1080)) s.videoTargetHeight else 720,
        videoBitrateKbps = s.videoBitrateKbps.coerceIn(500, 5000),
        audioBitrateKbps = if (s.audioBitrateKbps in listOf(64, 96, 128, 192)) s.audioBitrateKbps else 96,
        audioSampleRateHz = if (s.audioSampleRateHz in listOf(22050, 44100, 48000)) s.audioSampleRateHz else 44100,
        archiveLevel = s.archiveLevel.coerceIn(1, 9),
        gifScalePercent = if (s.gifScalePercent in listOf(50, 70, 100)) s.gifScalePercent else 70,
        gifFrameSkip = if (s.gifFrameSkip in listOf(1, 2, 3)) s.gifFrameSkip else 2,
        gifMaxColors = if (s.gifMaxColors in listOf(64, 128, 256)) s.gifMaxColors else 128
    )

    private fun readFromPrefs(p: SharedPreferences): GlobalCompressionSettings {
        val imgFormatName = p.getString(KEY_IMAGE_FORMAT, ImageOutputFormat.AUTO.name)
        val imgFormat = ImageOutputFormat.entries.firstOrNull { it.name == imgFormatName }
            ?: ImageOutputFormat.AUTO

        val pdfPresetName = p.getString(KEY_PDF_PRESET, CompressorQualityPreset.BALANCED.name)
        val pdfPreset = CompressorQualityPreset.entries.firstOrNull { it.name == pdfPresetName }
            ?: CompressorQualityPreset.BALANCED

        return sanitize(
            GlobalCompressionSettings(
                imageFormat = imgFormat,
                imageQuality = p.getInt(KEY_IMAGE_QUALITY, 72),
                imageMaxDimensionPx = p.getInt(KEY_IMAGE_MAX_DIM, 1600),
                videoTargetHeight = p.getInt(KEY_VIDEO_HEIGHT, 720),
                videoBitrateKbps = p.getInt(KEY_VIDEO_BITRATE, 1400),
                videoKeepAudio = p.getBoolean(KEY_VIDEO_KEEP_AUDIO, true),
                audioBitrateKbps = p.getInt(KEY_AUDIO_BITRATE, 96),
                audioSampleRateHz = p.getInt(KEY_AUDIO_SAMPLE_RATE, 44100),
                audioMono = p.getBoolean(KEY_AUDIO_MONO, false),
                pdfPreset = pdfPreset,
                archiveLevel = p.getInt(KEY_ARCHIVE_LEVEL, 9),
                archiveGzipSingleFile = p.getBoolean(KEY_ARCHIVE_GZIP, false),
                fileMinifyCode = p.getBoolean(KEY_FILE_MINIFY, true),
                fileWrapWithGzip = p.getBoolean(KEY_FILE_GZIP, false),
                gifScalePercent = p.getInt(KEY_GIF_SCALE, 70),
                gifFrameSkip = p.getInt(KEY_GIF_SKIP, 2),
                gifMaxColors = p.getInt(KEY_GIF_COLORS, 128),
                batchBundleAsZip = p.getBoolean(KEY_BATCH_ZIP, true)
            )
        )
    }

    private fun writeToPrefs(p: SharedPreferences, s: GlobalCompressionSettings) {
        p.edit()
            .putString(KEY_IMAGE_FORMAT, s.imageFormat.name)
            .putInt(KEY_IMAGE_QUALITY, s.imageQuality)
            .putInt(KEY_IMAGE_MAX_DIM, s.imageMaxDimensionPx)
            .putInt(KEY_VIDEO_HEIGHT, s.videoTargetHeight)
            .putInt(KEY_VIDEO_BITRATE, s.videoBitrateKbps)
            .putBoolean(KEY_VIDEO_KEEP_AUDIO, s.videoKeepAudio)
            .putInt(KEY_AUDIO_BITRATE, s.audioBitrateKbps)
            .putInt(KEY_AUDIO_SAMPLE_RATE, s.audioSampleRateHz)
            .putBoolean(KEY_AUDIO_MONO, s.audioMono)
            .putString(KEY_PDF_PRESET, s.pdfPreset.name)
            .putInt(KEY_ARCHIVE_LEVEL, s.archiveLevel)
            .putBoolean(KEY_ARCHIVE_GZIP, s.archiveGzipSingleFile)
            .putBoolean(KEY_FILE_MINIFY, s.fileMinifyCode)
            .putBoolean(KEY_FILE_GZIP, s.fileWrapWithGzip)
            .putInt(KEY_GIF_SCALE, s.gifScalePercent)
            .putInt(KEY_GIF_SKIP, s.gifFrameSkip)
            .putInt(KEY_GIF_COLORS, s.gifMaxColors)
            .putBoolean(KEY_BATCH_ZIP, s.batchBundleAsZip)
            .commit()
    }
}
