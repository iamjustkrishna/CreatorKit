package space.iamjustkrishna.creatorkit.ui.editor

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import space.iamjustkrishna.creatorkit.data.AudioExtractor
import space.iamjustkrishna.creatorkit.network.GroqApiClient
import space.iamjustkrishna.creatorkit.util.DynamicSubtitleOverlay
import java.io.File
import space.iamjustkrishna.creatorkit.BuildConfig

// ══════════════════════════════════════════════════════════════════════════════
// DATA MODELS
// ══════════════════════════════════════════════════════════════════════════════

data class CaptionSegment(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    var text: String
)

data class ClipSelection(val startMs: Long, val endMs: Long)

/**
 * Visual style for caption rendering.
 *
 * [scale]      — pinch-to-zoom scale factor (0.5–3.0)
 * [offsetX/Y]  — pixel offset from the natural anchor in the video frame
 * [fontFamily] — Compose FontFamily for the overlay text
 * [textColor]  — ARGB int (use Color(...).toArgb())
 * [bgAlpha]    — 0f = transparent background, 1f = fully opaque
 * [alignment]  — "left" | "center" | "right"
 */
data class GlobalCaptionStyle(
    val scale: Float           = 1f,
    val offsetX: Float         = 0f,
    val offsetY: Float         = 150f,      // near bottom by default
    val fontFamily: FontFamily = FontFamily.Default,
    val textColor: Int         = Color.White.toArgb(),
    val bgAlpha: Float         = 0.6f,
    val alignment: String      = "center",
    val fontSizeSp: Float      = 18f
)

// ══════════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════════

sealed class CaptionUiState {
    object Idle    : CaptionUiState()
    object Loading : CaptionUiState()
    data class Success(val segments: List<CaptionSegment>, val srtFile: File) : CaptionUiState()
    data class Error(val message: String) : CaptionUiState()
}

// ══════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ══════════════════════════════════════════════════════════════════════════════

@UnstableApi
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val context        = application.applicationContext
    private val audioExtractor = AudioExtractor(application)
    private val groqClient     = GroqApiClient(apiKey = BuildConfig.GROQ_API_KEY)

    // ── Caption generation state ──────────────────────────────────────────────
    private val _captionState = MutableStateFlow<CaptionUiState>(CaptionUiState.Idle)
    val captionState: StateFlow<CaptionUiState> = _captionState.asStateFlow()

    // ── Export state ──────────────────────────────────────────────────────────
    private val _isExporting    = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    // ── Caption styles ────────────────────────────────────────────────────────

    /**
     * Global default style — applied to every caption unless a per-caption
     * override exists in [perCaptionStyles].
     */
    var captionStyle by mutableStateOf(GlobalCaptionStyle())
        private set

    /**
     * Per-caption overrides keyed by [CaptionSegment.index].
     * If absent → falls back to [captionStyle].
     */
    private var perCaptionStyles by mutableStateOf<Map<Int, GlobalCaptionStyle>>(emptyMap())

    // ── Style accessors ───────────────────────────────────────────────────────

    /** Returns the effective style for a given caption index. */
    fun styleFor(captionIndex: Int): GlobalCaptionStyle =
        perCaptionStyles[captionIndex] ?: captionStyle

    /**
     * Apply a style change.
     *
     * [applyToAll]   = true  → updates [captionStyle] and clears all per-caption overrides
     * [applyToAll]   = false → writes only [captionIndex]'s override (other captions unaffected)
     */
    fun applyStyle(
        newStyle: GlobalCaptionStyle,
        applyToAll: Boolean,
        captionIndex: Int = -1
    ) {
        if (applyToAll) {
            captionStyle     = newStyle
            perCaptionStyles = emptyMap()
        } else if (captionIndex >= 0) {
            perCaptionStyles = perCaptionStyles + (captionIndex to newStyle)
        }
    }

    // ── Legacy single-field helpers (kept for backward-compat) ───────────────

    fun updateCaptionStyle(scale: Float, offsetX: Float, offsetY: Float) {
        captionStyle = captionStyle.copy(scale = scale, offsetX = offsetX, offsetY = offsetY)
    }

    fun updateCaptionFont(fontFamily: FontFamily) {
        captionStyle = captionStyle.copy(fontFamily = fontFamily)
    }

    fun updateFontSize(fontSizeSp: Float) {
        captionStyle = captionStyle.copy(fontSizeSp = fontSizeSp)
    }

    fun updateFontSizeByPinch(captionIndex: Int, pinchScale: Float, applyToAll: Boolean) {
        val base        = styleFor(captionIndex)
        val newFontSize = (base.fontSizeSp * pinchScale).coerceIn(8f, 64f)
        val updated     = base.copy(fontSizeSp = newFontSize, scale = 1f)
        applyStyle(updated, applyToAll, captionIndex)
    }
    /** Convenience: update position/scale via gesture, respecting per-caption vs global. */
    fun updatePositionAndScale(
        captionIndex: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        applyToAll: Boolean
    ) {
        val base    = styleFor(captionIndex)
        val updated = base.copy(scale = scale, offsetX = offsetX, offsetY = offsetY)
        applyStyle(updated, applyToAll, captionIndex)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CAPTION GENERATION
    // ══════════════════════════════════════════════════════════════════════════

    fun processVideoForCaptions(videoUri: Uri) {
        _captionState.value = CaptionUiState.Loading

        val tempFile = File(context.cacheDir, "temp_caption_audio.m4a")
        if (tempFile.exists()) tempFile.delete()

        audioExtractor.extract(
            inputUri       = videoUri,
            outputFilePath = tempFile.absolutePath,
            mimeType       = "audio/mp4a-latm",
            listener       = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    sendToGroqAndParse(tempFile)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    _captionState.value = CaptionUiState.Error(
                        "Failed to extract audio: ${exportException.message}"
                    )
                }
            }
        )
    }

    fun resetCaptionState() {
        _captionState.value = CaptionUiState.Idle
    }

    private fun sendToGroqAndParse(audioFile: File) {
        viewModelScope.launch {
            try {
                val jsonResponse = groqClient.generateCaptions(audioFile)
                if (jsonResponse != null) {
                    val segments = parseGroqJson(jsonResponse)
                    val srtText  = generateSrtString(segments)
                    val srtFile  = saveSrtToFile(srtText)
                    _captionState.value = CaptionUiState.Success(segments, srtFile)
                } else {
                    _captionState.value = CaptionUiState.Error("Caption generation returned empty.")
                }
            } catch (e: Exception) {
                _captionState.value = CaptionUiState.Error(
                    "Failed to generate captions: ${e.localizedMessage}"
                )
            } finally {
                if (audioFile.exists()) audioFile.delete()
            }
        }
    }

    // ── Groq JSON → CaptionSegment ────────────────────────────────────────────

    private fun parseGroqJson(jsonString: String): List<CaptionSegment> {
        val segments = mutableListOf<CaptionSegment>()
        try {
            val root  = JSONObject(jsonString)
            val array = root.getJSONArray("segments")
            for (i in 0 until array.length()) {
                val obj     = array.getJSONObject(i)
                val startMs = (obj.getDouble("start") * 1000).toLong()
                val endMs   = (obj.getDouble("end")   * 1000).toLong()
                val text    = obj.getString("text").trim()
                segments.add(CaptionSegment(i + 1, startMs, endMs, text))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return segments
    }

    private fun generateSrtString(segments: List<CaptionSegment>): String =
        buildString {
            for (seg in segments) {
                append("${seg.index}\n")
                append("${formatMsToSrtTime(seg.startTimeMs)} --> ${formatMsToSrtTime(seg.endTimeMs)}\n")
                append("${seg.text}\n\n")
            }
        }

    private fun formatMsToSrtTime(ms: Long): String {
        val h   = ms / 3_600_000
        val m   = (ms % 3_600_000) / 60_000
        val s   = (ms % 60_000) / 1_000
        val mil = ms % 1_000
        return "%02d:%02d:%02d,%03d".format(h, m, s, mil)
    }

    private fun saveSrtToFile(srtText: String): File =
        File(context.filesDir, "captions_${System.currentTimeMillis()}.srt")
            .also { it.writeText(srtText, Charsets.UTF_8) }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT — burn captions via Media3 Transformer + DynamicSubtitleOverlay
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the video with captions burned in using the current [captionStyle]
     * (and any per-caption overrides). Progress is polled every 200 ms.
     * On completion the file is saved to the device gallery (Movies/CreatorKit).
     *
     * [captionIndex] is passed to [DynamicSubtitleOverlay] so it can call
     * [styleFor] for each segment during rendering — each caption gets its
     * own style if an override exists.
     */
    fun exportVideoWithCaptions(videoUri: Uri) {
        _isExporting.value    = true
        _exportProgress.value = 0

        val outputFileName = "CreatorKit_${System.currentTimeMillis()}.mp4"
        val tempOutputPath = File(context.cacheDir, outputFileName).absolutePath

        val currentCaptions = (captionState.value as? CaptionUiState.Success)?.segments
            ?: emptyList()

        // DynamicSubtitleOverlay receives the global style AND the per-caption map
        // so it can resolve the correct style for every frame it renders.
        val subtitleOverlay = DynamicSubtitleOverlay(
            captions         = currentCaptions,
            globalStyle      = captionStyle,
            perCaptionStyles = perCaptionStyles       // <-- pass overrides
        )

        val overlayEffect = OverlayEffect(
            ImmutableList.of<TextureOverlay>(subtitleOverlay)
        )
        val effects = Effects(
            emptyList(),
            listOf(overlayEffect)
        )

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    saveVideoToGallery(File(tempOutputPath))
                    _isExporting.value    = false
                    _exportProgress.value = 100
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    android.util.Log.e("Export", "Transformer failed", exportException)
                    _captionState.value = CaptionUiState.Error(
                        "Export failed: ${exportException.message}"
                    )
                    _isExporting.value = false
                }
            })
            .build()

        transformer.start(editedMediaItem, tempOutputPath)

        // Poll Transformer progress on the main thread (as required by Media3)
        viewModelScope.launch {
            while (_isExporting.value) {
                val holder        = androidx.media3.transformer.ProgressHolder()
                val progressState = transformer.getProgress(holder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportProgress.value = holder.progress
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    private fun saveVideoToGallery(videoFile: File) {
        val resolver      = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/CreatorKit")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                videoFile.inputStream().copyTo(out)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }

        if (videoFile.exists()) videoFile.delete()
    }
}