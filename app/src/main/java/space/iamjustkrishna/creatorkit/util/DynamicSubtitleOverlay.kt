package space.iamjustkrishna.creatorkit.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.text.font.FontFamily
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.BitmapOverlay
import space.iamjustkrishna.creatorkit.ui.editor.CaptionSegment
import space.iamjustkrishna.creatorkit.ui.editor.GlobalCaptionStyle

/**
 * DynamicSubtitleOverlay
 *
 * A Media3 TextureOverlay that draws caption text onto every video frame
 * during Transformer export. Respects per-caption style overrides so each
 * segment can have a different font, colour, position, scale, etc.
 *
 * How positioning works:
 *   Media3's StaticOverlaySettings uses a normalised coordinate system
 *   where (0, 0) = centre of the frame, (-1, -1) = bottom-left,
 *   (1, 1) = top-right. We convert the pixel offsetX/Y from the UI into
 *   this space using the reference dimensions VIDEO_REF_W × VIDEO_REF_H.
 *
 *   The UI places the natural anchor at bottom-centre (offsetY = 150f by
 *   default), which maps to approximately (0, -0.8) in normalised space.
 */
@UnstableApi
class DynamicSubtitleOverlay(
    private val captions: List<CaptionSegment>,

    /** Global fallback style — used when no per-caption override exists */
    private val globalStyle: GlobalCaptionStyle,

    /**
     * Per-caption overrides keyed by [CaptionSegment.index].
     * Empty by default → all captions use [globalStyle].
     */
    private val perCaptionStyles: Map<Int, GlobalCaptionStyle> = emptyMap()

) : BitmapOverlay() {

    // ── Reference frame size for normalised coordinate conversion ────────────
    // These don't have to match the actual video resolution — they define
    // the coordinate space that the UI's offsetX/Y values live in.
    private val VIDEO_REF_W = 1080f
    private val VIDEO_REF_H = 1920f  // portrait reference; landscape will still look good

    // ── Bitmap cache ──────────────────────────────────────────────────────────
    // We cache the last rendered bitmap. The cache key is the caption index
    // combined with a hash of the style so any style change busts the cache.
    private data class CacheKey(val captionIndex: Int, val styleHash: Int)
    private var cacheKey: CacheKey? = null
    private var cachedBitmap: Bitmap? = null

    // ── Paint objects (reused across frames, reconfigured per caption) ────────
    private val textPaint = TextPaint().apply {
        isAntiAlias    = true
        isFakeBoldText = true
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getBitmap — called every frame by Transformer
    // ─────────────────────────────────────────────────────────────────────────

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val frameMs       = presentationTimeUs / 1000L
        val activeCaption = captions.find { frameMs in it.startTimeMs..it.endTimeMs }

        // No active caption → return a 1×1 transparent bitmap (zero cost)
        if (activeCaption == null) {
            return transparentBitmap()
        }

        val style    = styleFor(activeCaption.index)
        val newKey   = CacheKey(
            captionIndex = activeCaption.index,
            styleHash    = styleHash(style, activeCaption.text)
        )

        // Return cached bitmap if nothing changed
        if (newKey == cacheKey && cachedBitmap != null) {
            return cachedBitmap!!
        }

        // Render fresh bitmap
        cachedBitmap = renderCaption(activeCaption.text, style)
        cacheKey     = newKey
        return cachedBitmap!!
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOverlaySettings — called every frame to position the overlay
    // ─────────────────────────────────────────────────────────────────────────

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val frameMs       = presentationTimeUs / 1000L
        val activeCaption = captions.find { frameMs in it.startTimeMs..it.endTimeMs }
        val style         = activeCaption?.let { styleFor(it.index) } ?: globalStyle

        // Convert pixel offsets (UI space) → normalised Media3 space [-1, 1]
        // offsetX: positive = right,  negative = left
        // offsetY: positive = down in UI → negative Y in Media3 (bottom of frame)
        //
        // The UI default is offsetY = 150f which should sit near the bottom.
        // We invert Y because Media3 Y axis points up.
        val normX = (style.offsetX / (VIDEO_REF_W / 2f)).coerceIn(-1f, 1f)
        val normY = (-(style.offsetY / (VIDEO_REF_H / 2f))).coerceIn(-1f, 1f)

        return StaticOverlaySettings.Builder()
            // anchor = where on the FRAME the overlay centre is placed
            .setBackgroundFrameAnchor(normX, normY)
            .setScale(style.scale, style.scale)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // renderCaption — builds the bitmap for a single caption + style
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderCaption(text: String, style: GlobalCaptionStyle): Bitmap {

        // ── Configure text paint from style ───────────────────────────────────
        //
        // Convert fontSizeSp → bitmap pixels.
        //
        // The preview uses Compose sp units at the screen's density.
        // The export bitmap is rendered at VIDEO_REF_W × VIDEO_REF_H pixels.
        // We scale fontSizeSp relative to the reference frame height so
        // "18sp looks the same in the video as it did in the preview".
        //
        // Formula:  textPx = fontSizeSp * (VIDEO_REF_H / PREVIEW_VIEWPORT_HEIGHT_SP)
        //   Where PREVIEW_VIEWPORT_HEIGHT_SP ≈ 800sp (typical phone visible area).
        //   Simplification: 1sp at 1x density = 1px.  At VIDEO_REF_H=1920 and
        //   preview viewport ≈ 800dp, the scale factor is 1920 / 800 = 2.4.
        //
        // Pinch gesture now writes directly into fontSizeSp (8–64sp range),
        // so style.scale is always 1f and is intentionally ignored here.
        val fontSizePx = style.fontSizeSp * (VIDEO_REF_H / PREVIEW_VIEWPORT_HEIGHT_SP)

        textPaint.apply {
            textSize    = fontSizePx
            color       = style.textColor
            typeface    = fontFamilyToTypeface(style.fontFamily)
            setShadowLayer(fontSizePx * 0.12f, 0f, fontSizePx * 0.06f, Color.BLACK)
        }

        val textAlignment = when (style.alignment) {
            "left"  -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else    -> Layout.Alignment.ALIGN_CENTER
        }

        // ── Build StaticLayout for text wrapping ──────────────────────────────
        // Max line width is 85% of the reference frame width
        val maxTextWidth = (VIDEO_REF_W * 0.85f).toInt()

        val staticLayout = buildStaticLayout(text, textPaint, maxTextWidth, textAlignment)

        // ── Calculate bitmap dimensions ───────────────────────────────────────
        val hPad        = (fontSizePx * 0.5f).toInt()
        val vPad        = (fontSizePx * 0.3f).toInt()
        val bitmapW     = maxTextWidth + hPad * 2
        val bitmapH     = staticLayout.height + vPad * 2

        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── Background pill ───────────────────────────────────────────────────
        if (style.bgAlpha > 0f) {
            bgPaint.color = Color.argb(
                (style.bgAlpha * 255f).toInt().coerceIn(0, 255),
                0, 0, 0
            )
            canvas.drawRoundRect(
                RectF(0f, 0f, bitmapW.toFloat(), bitmapH.toFloat()),
                CORNER_RADIUS, CORNER_RADIUS,
                bgPaint
            )
        }

        // ── Draw text ─────────────────────────────────────────────────────────
        canvas.save()
        canvas.translate(hPad.toFloat(), vPad.toFloat())
        staticLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the effective style for a caption, falling back to [globalStyle]. */
    private fun styleFor(captionIndex: Int): GlobalCaptionStyle =
        perCaptionStyles[captionIndex] ?: globalStyle

    /**
     * Stable hash of the style fields that affect rendering + the text itself.
     * Used to decide whether the cached bitmap is still valid.
     */
    private fun styleHash(style: GlobalCaptionStyle, text: String): Int =
        arrayOf(
            text,
            style.fontSizeSp,       // was style.scale — now fontSizeSp drives size
            style.textColor,
            style.bgAlpha,
            style.alignment,
            style.fontFamily.toString()
        ).contentHashCode()

    /** Maps a Compose [FontFamily] to an Android [Typeface]. */
    private fun fontFamilyToTypeface(fontFamily: FontFamily): Typeface = when (fontFamily) {
        FontFamily.Serif     -> Typeface.SERIF
        FontFamily.Monospace -> Typeface.MONOSPACE
        FontFamily.Cursive   -> Typeface.create("cursive", Typeface.NORMAL)
        FontFamily.SansSerif -> Typeface.SANS_SERIF
        else                 -> Typeface.DEFAULT_BOLD  // Default / SansSerif
    }

    /** API-safe StaticLayout builder. */
    private fun buildStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, alignment, 1.15f, 0f, false)
        }

    /** Cheap 1×1 transparent bitmap for frames with no active caption. */
    private fun transparentBitmap(): Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            .also { it.eraseColor(Color.TRANSPARENT) }

    companion object {
        // Reference video frame size (portrait 1080p).
        // The overlay bitmap is rendered at this resolution regardless of actual video size —
        // Media3 Transformer scales it to fit the real output.
        private const val VIDEO_REF_W = 1080f
        private const val VIDEO_REF_H = 1920f

        // Approximate visible viewport height in a typical phone Compose layout (dp ≈ sp at 1x).
        // Used to convert fontSizeSp → video pixels so text appears the same size as the preview.
        private const val PREVIEW_VIEWPORT_HEIGHT_SP = 800f

        private const val CORNER_RADIUS = 20f
    }
}










