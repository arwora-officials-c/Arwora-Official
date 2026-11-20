package com.arwora.feature.compressor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CompressorUiState(
    val activeType: CompressorType = CompressorType.IMAGE,
    val selectedFiles: List<CompressorFileItem> = emptyList(),
    val isResolvingFiles: Boolean = false,
    val isCompressing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val errorMessage: String? = null,
    val results: List<CompressedOutputResult> = emptyList(),
    val batchZipResult: CompressedOutputResult? = null,
    val batchEntries: List<BatchCompressionEntry> = emptyList(),
    val savedSuccessNote: String? = null,

    // Per-engine specialized options
    val videoOptions: VideoCompressionOptions = VideoCompressionOptions(),
    val audioOptions: AudioCompressionOptions = AudioCompressionOptions(),
    val imageOptions: ImageCompressionOptions = ImageCompressionOptions(),
    val pdfOptions: PdfCompressionOptions = PdfCompressionOptions(),
    val archiveOptions: ArchiveCompressionOptions = ArchiveCompressionOptions(),
    val fileOptions: FileCompressionOptions = FileCompressionOptions(),
    val gifOptions: GifCompressionOptions = GifCompressionOptions(),
    val batchBundleAsZip: Boolean = true
)

class CompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompressorUiState())
    val uiState: StateFlow<CompressorUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        val defaults = CompressionDefaultsStore.ensureLoaded(application)
        _uiState.update { it.withGlobalDefaults(defaults) }

        viewModelScope.launch {
            CompressionDefaultsStore.settings.drop(1).collect { updatedDefaults ->
                if (activeJob?.isActive != true) {
                    _uiState.update { it.withGlobalDefaults(updatedDefaults) }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            CompressorEngines.clearStaleOutputs(getApplication())
        }
    }

    private fun CompressorUiState.withGlobalDefaults(defaults: GlobalCompressionSettings): CompressorUiState = copy(
        videoOptions = defaults.toVideoOptions(),
        audioOptions = defaults.toAudioOptions(),
        imageOptions = defaults.toImageOptions(),
        pdfOptions = defaults.toPdfOptions(),
        archiveOptions = defaults.toArchiveOptions(),
        fileOptions = defaults.toFileOptions(),
        gifOptions = defaults.toGifOptions(),
        batchBundleAsZip = defaults.batchBundleAsZip
    )

    fun selectCompressorType(type: CompressorType) {
        if (activeJob?.isActive == true) return
        _uiState.update { state ->
            if (state.activeType == type) state
            else state.copy(
                activeType = type,
                selectedFiles = emptyList(),
                results = emptyList(),
                batchZipResult = null,
                batchEntries = emptyList(),
                errorMessage = null,
                savedSuccessNote = null,
                progress = 0f,
                statusText = ""
            )
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingFiles = true,
                    errorMessage = null,
                    savedSuccessNote = null,
                    results = emptyList(),
                    batchZipResult = null,
                    batchEntries = emptyList()
                )
            }
            try {
                val resolved = uris.distinct().map { uri ->
                    CompressorEngines.resolveFileMetadata(app, uri)
                }
                val allowMulti = _uiState.value.activeType == CompressorType.ARCHIVE ||
                    _uiState.value.activeType == CompressorType.BATCH
                val finalList = if (allowMulti) resolved else resolved.take(1)
                _uiState.update {
                    it.copy(
                        selectedFiles = finalList,
                        isResolvingFiles = false,
                        batchEntries = finalList.map { f ->
                            BatchCompressionEntry(
                                fileName = f.fileName,
                                detectedType = f.detectedType,
                                originalBytes = f.originalSizeBytes
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingFiles = false,
                        errorMessage = e.message ?: "Failed to inspect selected file."
                    )
                }
            }
        }
    }

    fun removeSelectedFile(uri: Uri) {
        if (_uiState.value.isCompressing) return
        _uiState.update { state ->
            val updated = state.selectedFiles.filterNot { it.uri == uri }
            state.copy(
                selectedFiles = updated,
                batchEntries = updated.map {
                    BatchCompressionEntry(
                        fileName = it.fileName,
                        detectedType = it.detectedType,
                        originalBytes = it.originalSizeBytes
                    )
                },
                results = emptyList(),
                batchZipResult = null
            )
        }
    }

    fun updateVideoOptions(transform: (VideoCompressionOptions) -> VideoCompressionOptions) {
        _uiState.update { it.copy(videoOptions = transform(it.videoOptions)) }
    }

    fun updateAudioOptions(transform: (AudioCompressionOptions) -> AudioCompressionOptions) {
        _uiState.update { it.copy(audioOptions = transform(it.audioOptions)) }
    }

    fun updateImageOptions(transform: (ImageCompressionOptions) -> ImageCompressionOptions) {
        _uiState.update { it.copy(imageOptions = transform(it.imageOptions)) }
    }

    fun updatePdfOptions(transform: (PdfCompressionOptions) -> PdfCompressionOptions) {
        _uiState.update { it.copy(pdfOptions = transform(it.pdfOptions)) }
    }

    fun updateArchiveOptions(transform: (ArchiveCompressionOptions) -> ArchiveCompressionOptions) {
        _uiState.update { it.copy(archiveOptions = transform(it.archiveOptions)) }
    }

    fun updateFileOptions(transform: (FileCompressionOptions) -> FileCompressionOptions) {
        _uiState.update { it.copy(fileOptions = transform(it.fileOptions)) }
    }

    fun updateGifOptions(transform: (GifCompressionOptions) -> GifCompressionOptions) {
        _uiState.update { it.copy(gifOptions = transform(it.gifOptions)) }
    }

    fun setBatchBundleAsZip(bundle: Boolean) {
        _uiState.update { it.copy(batchBundleAsZip = bundle) }
    }

    fun startCompression() {
        val snapshot = _uiState.value
        if (snapshot.isCompressing || snapshot.selectedFiles.isEmpty()) return

        val app = getApplication<Application>()
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCompressing = true,
                    progress = 0.04f,
                    statusText = "Preparing ${snapshot.activeType.title} engine...",
                    errorMessage = null,
                    savedSuccessNote = null,
                    results = emptyList(),
                    batchZipResult = null
                )
            }

            try {
                when (snapshot.activeType) {
                    CompressorType.VIDEO -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Transcoding video with MediaCodec...") }
                        val res = CompressorEngines.compressVideo(app, file, snapshot.videoOptions) { pct ->
                            _uiState.update {
                                it.copy(
                                    progress = pct.coerceIn(0.05f, 1f),
                                    statusText = "Encoding H.264 stream (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "Video compression complete") }
                    }

                    CompressorType.AUDIO -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Transcoding audio to AAC-LC...") }
                        val res = CompressorEngines.compressAudio(app, file, snapshot.audioOptions) { pct ->
                            _uiState.update {
                                it.copy(
                                    progress = pct.coerceIn(0.05f, 1f),
                                    statusText = "Encoding AAC frames (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "Audio compression complete") }
                    }

                    CompressorType.IMAGE -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Optimizing image bitmap...") }
                        val res = CompressorEngines.compressImage(app, file, snapshot.imageOptions) { pct ->
                            _uiState.update { it.copy(progress = pct.coerceIn(0.05f, 1f)) }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "Image compression complete") }
                    }

                    CompressorType.PDF -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Optimizing PDF pages & streams...") }
                        val res = CompressorEngines.compressPdf(app, file, snapshot.pdfOptions) { pct ->
                            _uiState.update {
                                it.copy(
                                    progress = pct.coerceIn(0.05f, 1f),
                                    statusText = "Compressing PDF document (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "PDF optimization complete") }
                    }

                    CompressorType.ARCHIVE -> {
                        _uiState.update { it.copy(statusText = "Deflating archive entries...") }
                        val res = CompressorEngines.compressArchive(app, snapshot.selectedFiles, snapshot.archiveOptions) { pct ->
                            _uiState.update {
                                it.copy(
                                    progress = pct.coerceIn(0.05f, 1f),
                                    statusText = "Compressing archive (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "Archive created") }
                    }

                    CompressorType.FILE -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Minifying & compacting file...") }
                        val res = CompressorEngines.compressTextOrCodeFile(app, file, snapshot.fileOptions) { pct ->
                            _uiState.update { it.copy(progress = pct.coerceIn(0.05f, 1f)) }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "File optimization complete") }
                    }

                    CompressorType.GIF -> {
                        val file = snapshot.selectedFiles.first()
                        _uiState.update { it.copy(statusText = "Quantizing GIF frames & LZW encoding...") }
                        val res = CompressorEngines.compressGif(app, file, snapshot.gifOptions) { pct ->
                            _uiState.update {
                                it.copy(
                                    progress = pct.coerceIn(0.05f, 1f),
                                    statusText = "Encoding GIF89a (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                        _uiState.update { it.copy(results = listOf(res), isCompressing = false, progress = 1f, statusText = "GIF optimization complete") }
                    }

                    CompressorType.BATCH -> {
                        _uiState.update { it.copy(statusText = "Running parallel batch engines...") }
                        val (itemsOut, zipOut) = CompressorEngines.compressBatch(
                            context = app,
                            items = snapshot.selectedFiles,
                            videoOptions = snapshot.videoOptions,
                            audioOptions = snapshot.audioOptions,
                            imageOptions = snapshot.imageOptions,
                            pdfOptions = snapshot.pdfOptions,
                            archiveOptions = snapshot.archiveOptions,
                            fileOptions = snapshot.fileOptions,
                            gifOptions = snapshot.gifOptions,
                            bundleAsZip = snapshot.batchBundleAsZip,
                            onEntryUpdated = { idx, updatedEntry ->
                                _uiState.update { st ->
                                    val mutable = st.batchEntries.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable[idx] = updatedEntry
                                    }
                                    st.copy(batchEntries = mutable)
                                }
                            },
                            onOverallProgress = { pct ->
                                _uiState.update {
                                    it.copy(
                                        progress = pct.coerceIn(0.05f, 1f),
                                        statusText = "Processing batch queue (${(pct * 100).toInt()}%)..."
                                    )
                                }
                            }
                        )
                        _uiState.update {
                            it.copy(
                                results = itemsOut,
                                batchZipResult = zipOut,
                                isCompressing = false,
                                progress = 1f,
                                statusText = "Batch compression finished (${itemsOut.size} file(s))"
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isCompressing = false,
                        progress = 0f,
                        statusText = "Compression cancelled"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCompressing = false,
                        progress = 0f,
                        errorMessage = e.message ?: "Compression failed. Please verify the input file."
                    )
                }
            }
        }
    }

    fun cancelCompression() {
        activeJob?.cancel()
        activeJob = null
    }

    fun saveResultToDownloads(result: CompressedOutputResult) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val savedPath = CompressorEngines.saveToDownloads(app, result)
                _uiState.update { it.copy(savedSuccessNote = "Saved to $savedPath") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun clearSavedNote() {
        _uiState.update { it.copy(savedSuccessNote = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
    }
}
