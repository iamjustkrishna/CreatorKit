@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package space.iamjustkrishna.creatorkit.ui.editor

/**
 * VideoEditorScreen.kt — CreatorKit NLE
 *
 * Wired 1-to-1 with the real EditorViewModel:
 *   - captionStyle  / updateCaptionStyle / updateCaptionFont
 *   - applyStyle    / styleFor           / perCaptionStyles
 *   - processVideoForCaptions(videoUri)
 *   - exportVideoWithCaptions(videoUri)
 *   - CaptionUiState.Idle / Loading / Success / Error
 */

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import space.iamjustkrishna.creatorkit.R
import kotlin.math.abs
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// DESIGN TOKENS
// ══════════════════════════════════════════════════════════════════════════════

private val BgDeep         = Color(0xFF080808)
private val BgPanel        = Color(0xFF141414)
private val BgTrack        = Color(0xFF1C1C1C)
private val BgSheet        = Color(0xFF181818)
private val Amber          = Color(0xFFFFB300)
private val AmberDim       = Color(0x33FFB300)
private val Red            = Color(0xFFFF4444)
private val Green          = Color(0xFF00E676)
private val TextPrimary    = Color(0xFFEEEEEE)
private val TextSecondary  = Color(0xFF777777)
private val TrackVideo     = Color(0xFF3D2B8F)
private val TrackCaption   = Color(0xFF00695C)

// Font options shown in the style panel
val FONT_OPTIONS = listOf(
    FontFamily.Default   to "Default",
    FontFamily.Serif     to "Serif",
    FontFamily.Monospace to "Mono",
    FontFamily.Cursive   to "Cursive",
    FontFamily.SansSerif to "Sans"
)

// Colour swatches for caption text
val COLOR_OPTIONS = listOf(
    Color.White          to "White",
    Color(0xFFFFB300)    to "Amber",
    Color(0xFF00E676)    to "Green",
    Color(0xFF40C4FF)    to "Blue",
    Color(0xFFFF4444)    to "Red",
    Color(0xFFFF80AB)    to "Pink"
)

// ══════════════════════════════════════════════════════════════════════════════
// STABLE HOLDER — flicker fix
// ══════════════════════════════════════════════════════════════════════════════

@Stable
class StableHolder<T>(val value: T)

// ══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    videoUri: Uri,
    viewModel: EditorViewModel,
    onClipDeleted: (startMs: Long, endMs: Long) -> Unit = { _, _ -> }
) {
    LaunchedEffect(videoUri) { Log.i("VideoEditor", videoUri.toString()) }

    val context       = LocalContext.current
    val density       = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val halfScreen    = screenWidthDp / 2

    val pxPerSecond = with(density) { 100.dp.toPx() }
    val pxPerMs     = pxPerSecond / 1000f

    val scrollState = rememberScrollState()

    // ── Player ────────────────────────────────────────────────────────────────
    val playerHolder = remember(videoUri) {
        StableHolder(ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        })
    }
    DisposableEffect(playerHolder) { onDispose { playerHolder.value.release() } }

    // ── Playback state (polled every 30 ms) ───────────────────────────────────
    var currentMs  by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var isPlaying  by remember { mutableStateOf(false) }

    LaunchedEffect(playerHolder) {
        while (true) {
            currentMs  = playerHolder.value.currentPosition
            val dur    = playerHolder.value.duration
            durationMs = if (dur > 0) dur else 1L
            isPlaying  = playerHolder.value.isPlaying
            delay(30)
        }
    }

    // ── Scrubbing (pause-on-drag, resume after fling settles) ─────────────────
    val isDragging by scrollState.interactionSource.collectIsDraggedAsState()
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        if (isDragging) { isScrubbing = true; playerHolder.value.pause() }
    }
    LaunchedEffect(isScrubbing) {
        if (isScrubbing && !isDragging) {
            snapshotFlow { scrollState.isScrollInProgress }
                .filter { !it }.collect {
//                    isScrubbing = false;
                                    playerHolder.value.play() }
        }
    }
    LaunchedEffect(isDragging) {
        if (isDragging) {
            snapshotFlow { scrollState.value }.distinctUntilChanged().collect { px ->
                playerHolder.value.seekTo((px / pxPerMs).toLong().coerceIn(0L, durationMs))
            }
        }
    }

    // ── Auto-scroll during playback ───────────────────────────────────────────
    var lastScrollPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentMs) {
        if (!isScrubbing && isPlaying) {
            val target = (currentMs * pxPerMs).toInt()
            if (abs(target - lastScrollPx) >= 1) {
                scrollState.scrollTo(target); lastScrollPx = target
            }
        }
    }

    // ── ViewModel state ───────────────────────────────────────────────────────
    val captionState   by viewModel.captionState.collectAsState()
    val isExporting    by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    val captions = remember(captionState) {
        (captionState as? CaptionUiState.Success)?.segments ?: emptyList()
    }
    val activeCaption by remember(captions) {
        derivedStateOf { captions.find { currentMs in it.startTimeMs..it.endTimeMs } }
    }

    // ── UI state ──────────────────────────────────────────────────────────────
    var clipSelection  by remember { mutableStateOf<ClipSelection?>(null) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var applyToAll     by remember { mutableStateOf(true) }

    val trackWidthDp = remember(durationMs, density) {
        if (durationMs > 1000L) with(density) { (durationMs * pxPerMs).toDp() } else 200.dp
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROOT LAYOUT
    // ══════════════════════════════════════════════════════════════════════════
    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top app bar with Export button ────────────────────────────────
            EditorAppBar(
                isExporting    = isExporting,
                exportProgress = exportProgress,
                canExport      = captionState is CaptionUiState.Success && !isExporting,
                onExportClick  = {
                    playerHolder.value.pause()
                    viewModel.exportVideoWithCaptions(videoUri)
                }
            )

            // ── Video preview ─────────────────────────────────────────────────
            VideoPreviewPane(
                modifier      = Modifier.fillMaxWidth().weight(1f),
                playerHolder  = playerHolder,
                activeCaption = activeCaption,
                isScrubbing   = isScrubbing,
                viewModel     = viewModel,
                applyToAll    = applyToAll,
                onCaptionDoubleTap = { showStyleSheet = true }
            )

            // ── Editor panel ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(BgPanel)
            ) {
                PlaybackBar(
                    currentMs   = currentMs,
                    durationMs  = durationMs,
                    isPlaying   = isPlaying,
                    isScrubbing = isScrubbing,
                    onPlayPause = {
                        if (isPlaying) playerHolder.value.pause() else playerHolder.value.play()
                    }
                )

                // Timeline
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Row(
                        modifier          = Modifier.fillMaxSize().horizontalScroll(scrollState),
                        verticalAlignment = Alignment.Top
                    ) {
                        Spacer(Modifier.width(halfScreen))
                        Column(
                            modifier            = Modifier.width(trackWidthDp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TimeRuler(durationMs, trackWidthDp, pxPerMs)
                            VideoClipTrack(
                                durationMs       = durationMs,
                                clipSelection    = clipSelection,
                                pxPerMs          = pxPerMs,
                                density          = density,
                                onTapPosition    = { tapMs ->
                                    clipSelection = ClipSelection(
                                        (tapMs - 1000L).coerceAtLeast(0L),
                                        (tapMs + 1000L).coerceAtMost(durationMs)
                                    )
                                },
                                onClearSelection = { clipSelection = null }
                            )
                            if (captions.isNotEmpty()) {
                                CaptionTrack(captions, pxPerMs, currentMs, density)
                            }
                        }
                        Spacer(Modifier.width(halfScreen))
                    }

                    // Playhead
                    Box(Modifier.width(2.dp).fillMaxHeight()
                        .background(Color.White).align(Alignment.Center))
                    Box(Modifier.size(8.dp).align(Alignment.TopCenter)
                        .background(Color.White, CircleShape))
                }

                ClipActionBar(
                    clipSelection = clipSelection,
                    onDelete      = {
                        clipSelection?.let { sel ->
                            onClipDeleted(sel.startMs, sel.endMs)
                            clipSelection = null
                        }
                    },
                    onDeselect    = { clipSelection = null }
                )

                EditorToolbar(
                    captionState       = captionState,
                    onAiCaptionClicked = { viewModel.processVideoForCaptions(videoUri) },
                    onRetry            = { viewModel.resetCaptionState() },
                    onStyleClicked     = { showStyleSheet = true }
                )
            }
        }

        // Full-screen overlays
        CaptionGenerationOverlay(
            state          = captionState,
            onRetry        = { viewModel.resetCaptionState() },
            onDismissError = { viewModel.resetCaptionState() }
        )
        ExportOverlay(isExporting = isExporting, progress = exportProgress)
    }

    // ── Caption style bottom sheet ────────────────────────────────────────────
    if (showStyleSheet) {
        val currentStyle = activeCaption?.let { viewModel.styleFor(it.index) }
            ?: viewModel.captionStyle

        CaptionStyleSheet(
            activeCaption      = activeCaption,
            currentStyle       = currentStyle,
            applyToAll         = applyToAll,
            onApplyToAllChange = { applyToAll = it },
            onStyleChange      = { newStyle ->
                viewModel.applyStyle(
                    newStyle     = newStyle,
                    applyToAll   = applyToAll,
                    captionIndex = activeCaption?.index ?: -1
                )
            },
            onDismiss          = { showStyleSheet = false }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TOP APP BAR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun EditorAppBar(
    isExporting: Boolean,
    exportProgress: Int,
    canExport: Boolean,
    onExportClick: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().height(52.dp)
            .background(BgPanel).padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "CreatorKit",
            color      = TextPrimary,
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )

        when {
            isExporting -> Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    progress    = { exportProgress / 100f },
                    modifier    = Modifier.size(18.dp),
                    color       = Green,
                    strokeWidth = 2.dp,
                    trackColor  = BgTrack
                )
                Text("$exportProgress%", color = Green, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace)
            }
            else -> Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canExport) Amber else BgTrack)
                    .clickable(enabled = canExport) { onExportClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Export",
                    color      = if (canExport) Color.Black else TextSecondary,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// VIDEO PREVIEW PANE
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPane(
    modifier: Modifier = Modifier,
    playerHolder: StableHolder<ExoPlayer>,
    activeCaption: CaptionSegment?,
    isScrubbing: Boolean,
    viewModel: EditorViewModel,
    applyToAll: Boolean,
    onCaptionDoubleTap: () -> Unit
) {
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }

    DisposableEffect(playerHolder.value) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(vs: VideoSize) {
                if (vs.width > 0 && vs.height > 0)
                    videoAspectRatio = (vs.width * vs.pixelWidthHeightRatio) / vs.height
            }
        }
        playerHolder.value.addListener(listener)
        onDispose { playerHolder.value.removeListener(listener) }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false)
                .clipToBounds()
        ) {
            IsolatedVideoPlayer(playerHolder)

            // Scrubbing overlay
            AnimatedVisibility(isScrubbing, enter = fadeIn(tween(60)), exit = fadeOut(tween(180))) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◀▶  SCRUBBING", color = Color.White.copy(0.8f), fontSize = 11.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 3.sp)
                }
            }

            // Caption interaction layer
            CaptionInteractionLayer(
                modifier           = Modifier.fillMaxSize(),
                activeCaption      = activeCaption,
                viewModel          = viewModel,
                applyToAll         = applyToAll,
                onCaptionDoubleTap = onCaptionDoubleTap
            )
        }
    }
}

// ── Isolated video player ─────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun IsolatedVideoPlayer(playerHolder: StableHolder<ExoPlayer>) {
    AndroidView(
        factory  = { ctx ->
            PlayerView(ctx).apply {
                player        = playerHolder.value
                useController = false
                resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams  = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update   = { },   // intentionally empty — never re-layout the Surface
        modifier = Modifier.fillMaxSize()
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// CAPTION INTERACTION LAYER
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CaptionInteractionLayer(
    modifier: Modifier,
    activeCaption: CaptionSegment?,
    viewModel: EditorViewModel,
    applyToAll: Boolean,
    onCaptionDoubleTap: () -> Unit
) {
    // Stable refs so gesture lambdas always read latest values
    // without restarting the pointerInput coroutine mid-gesture
    val styleRef         = remember { mutableStateOf(viewModel.captionStyle) }
    val captionRef       = remember { mutableStateOf(activeCaption) }
    val applyToAllRef    = remember { mutableStateOf(applyToAll) }

    captionRef.value    = activeCaption
    applyToAllRef.value = applyToAll
    val currentStyle    = activeCaption?.let { viewModel.styleFor(it.index) } ?: viewModel.captionStyle
    styleRef.value      = currentStyle

    Box(
        modifier = modifier
            // Pinch → adjust fontSizeSp (what you see = what exports)
            // Pan   → reposition caption
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val s   = styleRef.value
                    val cap = captionRef.value
                    val idx = cap?.index ?: -1
                    val all = applyToAllRef.value

                    // Update font size via pinch (zoom != 1 means pinch happened)
                    if (zoom != 1f) {
                        viewModel.updateFontSizeByPinch(
                            captionIndex = idx,
                            pinchScale   = zoom,
                            applyToAll   = all
                        )
                    }
                    // Update position via pan
                    if (pan.x != 0f || pan.y != 0f) {
                        viewModel.updatePositionAndScale(
                            captionIndex = idx,
                            scale        = 1f,          // scale is now baked into fontSizeSp
                            offsetX      = s.offsetX + pan.x,
                            offsetY      = s.offsetY + pan.y,
                            applyToAll   = all
                        )
                    }
                }
            }
            // Double-tap = open style sheet
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onCaptionDoubleTap() })
            }
    ) {
        val style = currentStyle

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                // Position is layout-space so hit-testing follows it
                .offset { IntOffset(style.offsetX.roundToInt(), style.offsetY.roundToInt()) }
            // NOTE: no graphicsLayer scale here — size is controlled by fontSizeSp
        ) {
            AnimatedContent(
                targetState    = activeCaption,
                transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                label          = "caption"
            ) { caption ->
                if (caption != null) {
                    val s = viewModel.styleFor(caption.index)
                    Text(
                        text       = caption.text,
                        color      = Color(s.textColor),
                        // fontSizeSp drives both preview AND export — single source of truth
                        fontSize   = s.fontSizeSp.sp,
                        fontFamily = s.fontFamily,
                        fontWeight = FontWeight.Bold,
                        textAlign  = when (s.alignment) {
                            "left"  -> TextAlign.Start
                            "right" -> TextAlign.End
                            else    -> TextAlign.Center
                        },
                        modifier   = Modifier
                            .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.88f)
                            .background(Color.Black.copy(alpha = s.bgAlpha), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// CAPTION STYLE BOTTOM SHEET
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CaptionStyleSheet(
    activeCaption: CaptionSegment?,
    currentStyle: GlobalCaptionStyle,
    applyToAll: Boolean,
    onApplyToAllChange: (Boolean) -> Unit,
    onStyleChange: (GlobalCaptionStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = BgSheet,
        dragHandle       = {
            Box(
                Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp)
                    .background(Color(0xFF444444), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header + scope toggle ─────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Caption Style", color = TextPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScopeChip("This", !applyToAll, enabled = activeCaption != null) {
                        onApplyToAllChange(false)
                    }
                    ScopeChip("All", applyToAll) { onApplyToAllChange(true) }
                }
            }

            // ── Font picker ───────────────────────────────────────────────────
            SheetLabel("Font")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FONT_OPTIONS.size) { idx ->
                    val (family, name) = FONT_OPTIONS[idx]
                    val selected       = currentStyle.fontFamily == family
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Amber else BgTrack)
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = Color(0xFF2E2E2E),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onStyleChange(currentStyle.copy(fontFamily = family)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            color      = if (selected) Color.Black else TextPrimary,
                            fontFamily = family,
                            fontSize   = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // ── Text colour ───────────────────────────────────────────────────
            SheetLabel("Colour")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.horizontalScroll(rememberScrollState())
            ) {
                COLOR_OPTIONS.forEach { (color, _) ->
                    val selected = currentStyle.textColor == color.toArgb()
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Color.White else Color(0xFF333333),
                                shape = CircleShape
                            )
                            .clickable { onStyleChange(currentStyle.copy(textColor = color.toArgb())) }
                    )
                }
            }

            // ── Alignment ─────────────────────────────────────────────────────
            SheetLabel("Alignment")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("left" to "Left", "center" to "Centre", "right" to "Right").forEach { (key, label) ->
                    val selected = currentStyle.alignment == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AmberDim else BgTrack)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) Amber else Color(0xFF2E2E2E),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onStyleChange(currentStyle.copy(alignment = key)) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = if (selected) Amber else TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            // ── Background opacity ────────────────────────────────────────────
            SheetLabel("Background  ${(currentStyle.bgAlpha * 100).toInt()}%")
            Slider(
                value         = currentStyle.bgAlpha,
                onValueChange = { onStyleChange(currentStyle.copy(bgAlpha = it)) },
                valueRange    = 0f..1f,
                colors        = SliderDefaults.colors(
                    thumbColor         = Amber,
                    activeTrackColor   = Amber,
                    inactiveTrackColor = BgTrack
                )
            )

            // ── Reset ─────────────────────────────────────────────────────────
            TextButton(
                onClick  = { onStyleChange(currentStyle.copy(scale = 1f, offsetX = 0f, offsetY = 150f)) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Reset Position & Scale", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled  -> BgTrack.copy(alpha = 0.4f)
                    selected  -> Amber
                    else      -> BgTrack
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color      = when {
                !enabled -> TextSecondary.copy(alpha = 0.4f)
                selected -> Color.Black
                else     -> TextSecondary
            },
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 11.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
}

// ══════════════════════════════════════════════════════════════════════════════
// PLAYBACK BAR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PlaybackBar(
    currentMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    onPlayPause: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatTime(currentMs), color = TextPrimary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)

        IconButton(onClick = onPlayPause, enabled = !isScrubbing, modifier = Modifier.size(40.dp)) {
            Box(
                Modifier.size(40.dp)
                    .background(if (isScrubbing) BgTrack else Color(0xFF252525), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isPlaying || isScrubbing)
                        ImageVector.vectorResource(R.drawable.pause)
                    else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint               = if (isScrubbing) TextSecondary else TextPrimary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        Text(formatTime(durationMs), color = TextSecondary, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TIME RULER
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun TimeRuler(durationMs: Long, trackWidthDp: Dp, pxPerMs: Float) {
    val totalSeconds = (durationMs / 1000L).toInt().coerceAtLeast(1)
    Canvas(modifier = Modifier.width(trackWidthDp).height(22.dp)) {
        val tickColor  = Color(0xFF383838)
        val labelPaint = android.graphics.Paint().apply {
            color       = Color(0xFF666666).toArgb()
            textSize    = 22f; isAntiAlias = true
            typeface    = android.graphics.Typeface.MONOSPACE
        }
        for (sec in 0..totalSeconds) {
            val x       = sec * pxPerMs * 1000f
            val isMajor = sec % 5 == 0
            drawLine(tickColor, Offset(x, size.height - if (isMajor) 12f else 6f),
                Offset(x, size.height), strokeWidth = if (isMajor) 1.5f else 1f)
            if (isMajor) drawContext.canvas.nativeCanvas.drawText(
                formatTime(sec * 1000L), x + 3f, size.height - 14f, labelPaint)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// VIDEO CLIP TRACK
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VideoClipTrack(
    durationMs: Long,
    clipSelection: ClipSelection?,
    pxPerMs: Float,
    density: Density,
    onTapPosition: (Long) -> Unit,
    onClearSelection: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackVideo)
            .pointerInput(pxPerMs, durationMs) {
                detectTapGestures(
                    onTap       = { offset ->
                        onTapPosition((offset.x / pxPerMs).toLong().coerceIn(0L, durationMs))
                    },
                    onLongPress = { onClearSelection() }
                )
            }
    ) {
        Text(
            text     = if (durationMs > 1000L) "Video" else "Loading…",
            color    = Color.White.copy(0.3f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
        )

        if (clipSelection != null) {
            val s = with(density) { (clipSelection.startMs * pxPerMs).toDp() }
            val w = with(density) { ((clipSelection.endMs - clipSelection.startMs) * pxPerMs).toDp() }
            Box(Modifier.absoluteOffset(0.dp).width(s).fillMaxHeight().background(Color.Black.copy(0.5f)))
            Box(Modifier.absoluteOffset(x = s + w).fillMaxSize().background(Color.Black.copy(0.5f)))
            Box(Modifier.absoluteOffset(x = s).width(w).fillMaxHeight()
                .border(1.5.dp, Amber, RoundedCornerShape(4.dp)))
            Box(Modifier.absoluteOffset(x = s - 3.dp).width(6.dp).fillMaxHeight()
                .background(Amber, RoundedCornerShape(3.dp)))
            Box(Modifier.absoluteOffset(x = s + w - 3.dp).width(6.dp).fillMaxHeight()
                .background(Amber, RoundedCornerShape(3.dp)))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CAPTION TRACK
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CaptionTrack(
    captions: List<CaptionSegment>,
    pxPerMs: Float,
    currentMs: Long,
    density: Density
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .clip(RoundedCornerShape(6.dp)).background(BgTrack)
    ) {
        captions.forEach { caption ->
            val isActive = currentMs in caption.startTimeMs..caption.endTimeMs
            val startDp  = with(density) { (caption.startTimeMs * pxPerMs).toDp() }
            val widthDp  = with(density) {
                ((caption.endTimeMs - caption.startTimeMs) * pxPerMs).toDp().coerceAtLeast(5.dp)
            }
            Box(
                modifier = Modifier
                    .absoluteOffset(x = startDp).width(widthDp).fillMaxHeight()
                    .padding(vertical = 3.dp, horizontal = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) Amber else TrackCaption)
                    .then(if (isActive) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier),
                contentAlignment = Alignment.CenterStart
            ) {
                if (widthDp > 30.dp) {
                    Text(
                        text     = caption.text,
                        color    = if (isActive) Color.Black else Color.White.copy(0.8f),
                        fontSize = 8.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CLIP ACTION BAR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ClipActionBar(clipSelection: ClipSelection?, onDelete: () -> Unit, onDeselect: () -> Unit) {
    AnimatedVisibility(
        visible = clipSelection != null,
        enter   = fadeIn(tween(120)) + expandVertically(),
        exit    = fadeOut(tween(120)) + shrinkVertically()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF1A0C00))
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            clipSelection?.let { sel ->
                Text(
                    "${formatTime(sel.startMs)}  →  ${formatTime(sel.endMs)}",
                    color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Red)) {
                Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", fontSize = 12.sp)
            }
            TextButton(onClick = onDeselect,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EDITOR TOOLBAR
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun EditorToolbar(
    captionState: CaptionUiState,
    onAiCaptionClicked: () -> Unit,
    onRetry: () -> Unit,
    onStyleClicked: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).background(BgDeep),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // AI Caption button — state-aware
        when (captionState) {
            is CaptionUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(horizontal = 8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Amber,
                        strokeWidth = 2.dp, trackColor = BgTrack)
                    Spacer(Modifier.height(3.dp))
                    Text("Generating…", color = Amber, fontSize = 9.sp)
                }
            }
            is CaptionUiState.Success ->
                ToolItem(icon = "✅", label = "Captions ✓", onClick = {})
            is CaptionUiState.Error ->
                ToolItem(icon = "⚠️", label = "Retry", tintColor = Red, onClick = onRetry)
            else ->
                ToolItem(icon = "✨", label = "AI Caption", onClick = onAiCaptionClicked)
        }

        ToolItem(
            icon    = "✏️",
            label   = "Style",
            enabled = captionState is CaptionUiState.Success,
            onClick = onStyleClicked
        )
        ToolItem(icon = "🎵", label = "Audio",   onClick = { })
        ToolItem(icon = "🎨", label = "Filters", onClick = { })
    }
}

@Composable
fun ToolItem(
    icon: String,
    label: String,
    enabled: Boolean = true,
    tintColor: Color = TextSecondary,
    onClick: () -> Unit
) {
    TextButton(
        onClick        = onClick,
        enabled        = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 19.sp, modifier = Modifier.alpha(if (enabled) 1f else 0.35f))
            Text(
                label,
                color      = if (enabled) tintColor else Color(0xFF3A3A3A),
                fontSize   = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// OVERLAYS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CaptionGenerationOverlay(
    state: CaptionUiState,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    AnimatedVisibility(
        visible = state is CaptionUiState.Loading || state is CaptionUiState.Error,
        enter   = fadeIn(tween(200)),
        exit    = fadeOut(tween(200))
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.78f)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is CaptionUiState.Loading -> StatusCard(
                    "Generating AI Captions…",
                    "This may take a moment"
                )
                is CaptionUiState.Error   -> ErrorCard(state.message, onRetry, onDismissError)
                else                      -> {}
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier            = Modifier
            .clip(RoundedCornerShape(16.dp)).background(BgPanel).padding(32.dp)
    ) {
        CircularProgressIndicator(Modifier.size(44.dp), color = Amber,
            strokeWidth = 3.dp, trackColor = BgTrack)
        Text(title,    color = TextPrimary,   fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier
            .clip(RoundedCornerShape(16.dp)).background(BgPanel)
            .padding(28.dp).widthIn(max = 300.dp)
    ) {
        Text("⚠️", fontSize = 36.sp)
        Text("Something went wrong", color = TextPrimary, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold)
        Text(message, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Amber)) {
                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ExportOverlay(isExporting: Boolean, progress: Int) {
    AnimatedVisibility(
        visible = isExporting,
        enter   = fadeIn(tween(200)),
        exit    = fadeOut(tween(200))
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier            = Modifier
                    .clip(RoundedCornerShape(16.dp)).background(BgPanel).padding(36.dp)
            ) {
                CircularProgressIndicator(
                    progress    = { progress / 100f },
                    modifier    = Modifier.size(64.dp),
                    color       = Green,
                    strokeWidth = 5.dp,
                    trackColor  = BgTrack
                )
                Text("Burning captions…", color = TextPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("$progress%", color = Green, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
                Text("Keep the app open", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// UTILITY
// ══════════════════════════════════════════════════════════════════════════════

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}