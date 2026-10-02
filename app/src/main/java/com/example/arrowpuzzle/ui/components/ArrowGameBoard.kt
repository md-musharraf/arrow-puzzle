package com.example.arrowpuzzle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.example.arrowpuzzle.data.model.Direction
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.Danger
import com.example.arrowpuzzle.theme.Ink
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Lets end-to-end tests find the board and tap cells on it. */
const val BOARD_TAG = "board"

private const val TUBE = 0.15f          // line width, as a fraction of the smaller cell side
private const val HEAD = 0.27f          // arrowhead half-width, also a fraction of that cell side
private const val ESCAPE_MS = 480
private const val ENTRY_MS = 560
private const val UNDO_MS = 380
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

/** Gentle wind-up into a steady glide, so the slither reads as deliberate rather than flung. */
private val SLITHER = CubicBezierEasing(0.32f, 0f, 0.66f, 0.42f)

/**
 * Pan and zoom for the board, hoisted so the screen can offer a "fit" control and reset it
 * between levels. Scale is about the board centre; [offset] lives in the board's parent space.
 */
@Stable
class BoardZoomState {
    var scale by mutableFloatStateOf(MIN_ZOOM)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > MIN_ZOOM + 0.02f

    fun reset() {
        scale = MIN_ZOOM
        offset = Offset.Zero
    }

    /** Clamps so the board can never be dragged off screen; at 1x there is nowhere to go. */
    internal fun apply(newScale: Float, newOffset: Offset, width: Float, height: Float) {
        // A non-finite value here would reach graphicsLayer and stop the board drawing
        // entirely, and it would stick until the next level. Refuse it at the door.
        if (!newScale.isFinite() || !newOffset.x.isFinite() || !newOffset.y.isFinite()) return
        scale = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val limitX = width * (scale - 1f) / 2f
        val limitY = height * (scale - 1f) / 2f
        offset = Offset(
            newOffset.x.coerceIn(-limitX, limitX),
            newOffset.y.coerceIn(-limitY, limitY)
        )
    }
}

@Composable
fun rememberBoardZoomState(): BoardZoomState = remember { BoardZoomState() }

/** A path with its pixel geometry — body and head — precomputed, so drawing never rebuilds it. */
private class Rendered(val arrow: PathArrow, val points: List<Offset>, val outline: Path, val head: Path)

/**
 * An arrow slithering off the board after a successful tap.
 *
 * Its own outline is extended by a straight run in the exit direction, and the body is drawn as
 * a fixed-length window sliding along that combined track. The head leads, every following part
 * passes through exactly where the head has been, and the tail is last to leave — so the arrow
 * unwinds through its own corners like a snake going down a hole instead of sliding as one rigid
 * shape. [window] is reused each frame to keep the animation allocation-free.
 */
private class Escape(
    val rendered: Rendered,
    val progress: Animatable<Float, *>,
    val track: PathMeasure,
    val bodyLength: Float,
    val trackLength: Float,
    /** An undo plays the slither backwards, so the arrow crawls back into its own cells. */
    val returning: Boolean = false,
    val window: Path = Path(),
    val head: Path = Path()
)

/** Builds the extended track an escaping arrow travels along. */
private fun escapeOf(
    r: Rendered,
    boardW: Float,
    boardH: Float,
    cell: Float,
    returning: Boolean = false
): Escape {
    val points = r.points
    var body = 0f
    for (i in 0 until points.size - 1) body += (points[i + 1] - points[i]).getDistance()

    // Long enough that the tail clears the board before the animation ends.
    val runOut = max(boardW, boardH) + body + cell * 2f
    val d = r.arrow.exitDirection
    val head = points.last()

    val track = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        lineTo(head.x + d.dc * runOut, head.y + d.dr * runOut)
    }
    val measure = PathMeasure().apply { setPath(track, false) }
    return Escape(r, Animatable(if (returning) 1f else 0f), measure, body, measure.length, returning)
}

@Composable
fun ArrowGameBoard(
    rows: Int,
    cols: Int,
    activePaths: List<PathArrow>,
    blockedPath: PathArrow?,
    blockerPath: PathArrow?,
    hintPathId: Int?,
    runId: Int,
    zoom: BoardZoomState,
    onPathClick: (PathArrow) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(cols.toFloat() / rows.toFloat())
            .clipToBounds()
            .testTag(BOARD_TAG)
    ) {
        val density = LocalDensity.current
        val boardW = with(density) { maxWidth.toPx() }
        val boardH = with(density) { maxHeight.toPx() }
        val cellW = boardW / cols
        val cellH = boardH / rows
        val cell = min(cellW, cellH)
        val stroke = cell * TUBE
        val headHalf = cell * HEAD
        // One Stroke per width for the life of the board, not one per arrow per frame.
        val bodyStroke = remember(stroke) { Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        val haloStroke = remember(stroke) { Stroke(width = stroke * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round) }

        // Geometry is keyed by arrow id and reused across taps. Clearing one arrow used to
        // rebuild every remaining arrow's Path, which on a packed board is dozens of throwaway
        // allocations on the frame the player is already animating an escape on.
        // Keyed by runId as well as cell size: arrow ids restart at 1 on every board, so a new
        // level of the same size would otherwise be drawn with the previous level's geometry.
        val geometry = remember(cellW, cellH, runId) { HashMap<Int, Rendered>() }
        val rendered = remember(activePaths, cellW, cellH, runId) {
            activePaths.map { arrow ->
                geometry.getOrPut(arrow.id) { render(arrow, cellW, cellH) }
            }
        }

        // Entry: arrows drift in from just off-board and fade up, lightly staggered so a
        // packed level assembles itself instead of appearing all at once.
        val entry = remember { Animatable(1f) }
        LaunchedEffect(runId) {
            entry.snapTo(0f)
            entry.animateTo(1f, tween(ENTRY_MS, easing = FastOutSlowInEasing))
        }

        // Fly-out: detect the single arrow that just cleared and launch it off the board.
        val escaping = remember { mutableStateListOf<Escape>() }
        // Escapes run on their own scope, not the effect's: the effect restarts on every tap, and
        // a quick second tap used to cancel the first slither mid-flight and leave it frozen.
        val escapeScope = rememberCoroutineScope()
        var previous by remember { mutableStateOf(activePaths) }
        var previousRun by remember { mutableStateOf(runId) }
        // An undone arrow is back in the list a frame before the effect below starts its
        // slither, and would flash in place for that frame. Hide it until the slither takes over.
        val arriving = remember(activePaths, previous, runId) {
            if (previousRun != runId) emptySet()
            else activePaths.mapTo(HashSet()) { it.id }.apply { previous.forEach { remove(it.id) } }
        }
        LaunchedEffect(activePaths, runId) {
            // A restart also changes the list by one arrow after a single clear; it is not an
            // undo, and the board's own entry animation already covers it.
            val sameBoard = previousRun == runId
            val gone = previous.filter { old -> activePaths.none { it.id == old.id } }
            val back = activePaths.filter { new -> previous.none { it.id == new.id } }
            val isSingleClear = gone.size == 1 && activePaths.size == previous.size - 1
            val isSingleUndo = back.size == 1 && activePaths.size == previous.size + 1
            previous = activePaths
            previousRun = runId
            if (!sameBoard) {
                escaping.clear()
                return@LaunchedEffect
            }
            if ((isSingleClear || isSingleUndo) && cellW > 0f) {
                val arrow = if (isSingleClear) gone.first() else back.first()
                val escape = escapeOf(render(arrow, cellW, cellH), boardW, boardH, cell, isSingleUndo)
                escaping += escape
                escapeScope.launch {
                    try {
                        if (isSingleUndo) {
                            escape.progress.animateTo(0f, tween(UNDO_MS, easing = FastOutSlowInEasing))
                        } else {
                            escape.progress.animateTo(1f, tween(ESCAPE_MS, easing = SLITHER))
                        }
                    } finally {
                        escaping -= escape
                    }
                }
            }
        }

        // Blocked bump: a short shove into the blocker, then a spring back.
        val bump = remember(blockedPath) { Animatable(0f) }
        LaunchedEffect(blockedPath) {
            if (blockedPath != null) {
                bump.animateTo(1f, tween(70, easing = FastOutSlowInEasing))
                bump.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }

        // Only pulse while a hint is actually on screen. An infinite transition never stops on
        // its own, so leaving it running kept the board requesting a frame callback for the whole
        // life of the app — every one of them redrawing a board that had not changed.
        val pulse = if (hintPathId == null) 0f else {
            val transition = rememberInfiniteTransition("hint")
            val animated by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    tween(760, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "hintPulse"
            )
            animated
        }

        // Two layers: the resting board, and the arrows in flight. Escapes animate every frame for
        // half a second per tap; keeping them on their own canvas means only the one or two moving
        // arrows are redrawn, not every arrow on a packed board.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                }
        ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val entryValue = entry.value
            val settled = entryValue >= 1f
            val count = rendered.size.coerceAtLeast(1)

            rendered.forEachIndexed { index, r ->
                val id = r.arrow.id
                val d = r.arrow.exitDirection
                // Mid-undo the slither below stands in for the arrow; drawing both doubles it.
                if (id in arriving || escaping.any { it.returning && it.rendered.arrow.id == id }) {
                    return@forEachIndexed
                }

                // Staggered per-arrow entry progress, eased with a smoothstep.
                val appear = if (settled) 1f else {
                    val begin = index.toFloat() / count * 0.55f
                    val raw = ((entryValue - begin) / 0.45f).coerceIn(0f, 1f)
                    raw * raw * (3f - 2f * raw)
                }
                if (appear <= 0f) return@forEachIndexed

                var shift = if (settled) 0f else (1f - appear) * cell * 1.6f
                if (blockedPath?.id == id) shift += bump.value * stroke * 0.7f

                val color = when {
                    blockedPath?.id == id -> Danger
                    blockerPath?.id == id -> Danger.copy(alpha = 0.5f)
                    hintPathId == id -> Accent
                    else -> Ink
                }.let { if (settled) it else it.copy(alpha = it.alpha * appear) }

                val displacement = Offset(d.dc * shift, d.dr * shift)
                if (hintPathId == id) {
                    drawArrow(r, haloStroke, false, Accent.copy(alpha = pulse), displacement)
                }
                drawArrow(r, bodyStroke, true, color, displacement)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (e in escaping) {
                // The head runs from the arrow's own tip out to the end of the track; the tail
                // trails exactly one body-length behind it, following the same corners.
                val headAt = e.bodyLength + e.progress.value * (e.trackLength - e.bodyLength)
                val tailAt = (headAt - e.bodyLength).coerceAtLeast(0f)

                e.window.reset()
                if (e.track.getSegment(tailAt, headAt, e.window, true)) {
                    drawPath(path = e.window, color = Ink, style = bodyStroke)
                }
                // Past its own tip the head is always travelling straight out, so the tip keeps
                // the arrow's exit direction for the whole slither.
                e.head.setHead(e.track.getPosition(headAt), e.rendered.arrow.exitDirection, headHalf)
                drawPath(path = e.head, color = Ink)
            }
        }
        }

        // Gestures sit on an untransformed overlay so pan and zoom share one coordinate space;
        // only the tap point has to be mapped back through the transform into board space.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(rendered, boardW, boardH) {
                    val centre = Offset(boardW / 2f, boardH / 2f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var travelled = 0f
                        var transforming = false
                        while (true) {
                            val event = awaitPointerEvent()
                            // The release event carries no pressed pointers, and asking it for a
                            // centroid yields Offset.Unspecified. Leave before that can happen.
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break

                            val pan = event.calculatePan()
                            travelled += pan.getDistance()
                            if (pressed == 1 && travelled <= viewConfiguration.touchSlop) continue

                            transforming = true
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid.isSpecified) {
                                val focus = centroid - centre
                                val next = (zoom.scale * event.calculateZoom())
                                    .coerceIn(MIN_ZOOM, MAX_ZOOM)
                                val growth = next / zoom.scale
                                // Hold whatever sits under the pinch centroid still while scaling.
                                zoom.apply(
                                    newScale = next,
                                    newOffset = focus - (focus - zoom.offset) * growth + pan,
                                    width = boardW,
                                    height = boardH
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }

                        if (!transforming) {
                            val local = centre + (down.position - centre - zoom.offset) / zoom.scale
                            nearestPath(local, rendered, cellW, cellH)?.let(onPathClick)
                        }
                    }
                }
        )
    }
}

/**
 * Draws one arrow: a thin rounded line plus a solid head. The head is sized from the cell
 * rather than the line, so the body can stay hairline-thin without the tip disappearing.
 * Pass [headHalf] as 0 to draw the body alone, which is how the hint halo and motion trail work.
 */
private fun DrawScope.drawArrow(
    r: Rendered,
    stroke: Stroke,
    withHead: Boolean,
    color: Color,
    shift: Offset
) {
    translate(shift.x, shift.y) {
        drawPath(path = r.outline, color = color, style = stroke)
        if (withHead) drawPath(path = r.head, color = color)
    }
}

/**
 * Rewrites this path as a solid tip pointing along [direction]. Sized from the cell so a thin
 * body keeps a bold head. Reuses the path, so a moving head costs no allocation per frame.
 */
private fun Path.setHead(tip: Offset, direction: Direction, headHalf: Float) {
    val ax = direction.dc.toFloat()
    val ay = direction.dr.toFloat()
    val baseX = tip.x - ax * headHalf * 0.35f
    val baseY = tip.y - ay * headHalf * 0.35f
    reset()
    moveTo(baseX - ay * headHalf, baseY + ax * headHalf)
    lineTo(tip.x + ax * headHalf * 1.3f, tip.y + ay * headHalf * 1.3f)
    lineTo(baseX + ay * headHalf, baseY - ax * headHalf)
    close()
}

private fun render(arrow: PathArrow, cellW: Float, cellH: Float): Rendered {
    val points = arrow.waypoints.map { Offset(it.c * cellW + cellW / 2f, it.r * cellH + cellH / 2f) }
    val outline = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    val head = Path().apply { setHead(points.last(), arrow.exitDirection, min(cellW, cellH) * HEAD) }
    return Rendered(arrow, points, outline, head)
}

/** Picks the arrow whose body is closest to the tap, so overlapping paths resolve predictably. */
private fun nearestPath(
    tap: Offset,
    rendered: List<Rendered>,
    cellW: Float,
    cellH: Float
): PathArrow? {
    val threshold = max(cellW, cellH) * 0.5f
    var best: PathArrow? = null
    var bestDistance = Float.MAX_VALUE
    for (r in rendered) {
        for (i in 0 until r.points.size - 1) {
            val distance = distanceToSegment(tap, r.points[i], r.points[i + 1])
            if (distance < bestDistance) {
                bestDistance = distance
                best = r.arrow
            }
        }
    }
    return best.takeIf { bestDistance <= threshold }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val lengthSquared = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (lengthSquared == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / lengthSquared).coerceIn(0f, 1f)
    return (p - Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))).getDistance()
}
