package com.brianrobinson.fonds.ai

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NanoState { CHECKING, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/**
 * Gemini Nano on the Pixel's own chip, through ML Kit's GenAI Prompt API.
 * Nothing leaves the phone. Android only allows this while the app is on screen,
 * so the pipeline pauses Nano work whenever Fonds is in the background.
 */
class NanoEngine {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    private val _state = MutableStateFlow(NanoState.CHECKING)
    val state: StateFlow<NanoState> = _state.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    var lastError: String = ""
        private set

    suspend fun refresh(): NanoState {
        val s = try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> NanoState.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> NanoState.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> NanoState.DOWNLOADING
                else -> NanoState.UNAVAILABLE
            }
        } catch (t: Throwable) {
            lastError = t.message.orEmpty()
            NanoState.UNAVAILABLE
        }
        _state.value = s
        return s
    }

    val isAvailable: Boolean get() = _state.value == NanoState.AVAILABLE

    suspend fun download() {
        _state.value = NanoState.DOWNLOADING
        try {
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadProgress -> _downloadedBytes.value = status.totalBytesDownloaded
                    is DownloadStatus.DownloadFailed -> {
                        lastError = status.e.message.orEmpty()
                        _state.value = NanoState.DOWNLOADABLE
                    }
                    DownloadStatus.DownloadCompleted -> _state.value = NanoState.AVAILABLE
                    else -> Unit
                }
            }
        } catch (t: Throwable) {
            lastError = t.message.orEmpty()
        }
        refresh()
    }

    suspend fun generate(prompt: String, image: Bitmap? = null): String {
        val request = if (image != null) {
            generateContentRequest(ImagePart(image), TextPart(prompt)) {
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 600
            }
        } else {
            generateContentRequest(TextPart(prompt)) {
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 600
            }
        }
        val response = model.generateContent(request)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    companion object {
        /** Nano accepts under ~4,000 tokens; keep the document text well inside that. */
        const val MAX_TEXT_CHARS = 8_000
    }
}
