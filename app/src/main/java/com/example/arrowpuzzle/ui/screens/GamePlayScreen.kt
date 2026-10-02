package com.example.arrowpuzzle.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.AccentSoft
import com.example.arrowpuzzle.theme.Chip
import com.example.arrowpuzzle.theme.Danger
import com.example.arrowpuzzle.theme.DiffMaster
import com.example.arrowpuzzle.theme.Gold
import com.example.arrowpuzzle.theme.GoldEmpty
import com.example.arrowpuzzle.theme.Heart
import com.example.arrowpuzzle.theme.HeartEmpty
import com.example.arrowpuzzle.theme.Ink
import com.example.arrowpuzzle.theme.InkFaint
import com.example.arrowpuzzle.theme.InkMuted
import com.example.arrowpuzzle.theme.Screen
import com.example.arrowpuzzle.theme.Success
import com.example.arrowpuzzle.ui.components.ArrowGameBoard
import com.example.arrowpuzzle.ui.components.BoardZoomState
import com.example.arrowpuzzle.ui.components.bounceClick
import com.example.arrowpuzzle.ui.components.rememberBoardZoomState
import com.example.arrowpuzzle.ui.viewmodel.GameMode
import com.example.arrowpuzzle.ui.viewmodel.GamePlayStatus
import com.example.arrowpuzzle.ui.viewmodel.GameUiState
import com.example.arrowpuzzle.ui.viewmodel.GameViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GamePlayScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val level = state.currentLevel
    val zoom = rememberBoardZoomState()

    // A new board always starts fitted, never inheriting the last level's pan.
    LaunchedEffect(state.runId) { zoom.reset() }

    // A blocked tap shakes the whole board, so the cost of the mistake is felt, not just counted.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.mistakesMade) {
        if (state.mistakesMade == 0) return@LaunchedEffect
        for (x in floatArrayOf(14f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, tween(38))
    }

    // The result sheet waits for the last arrow to finish slithering off (or the fatal bump to
    // land) instead of slamming down over it. It keeps the state it opened with, so its exit
    // fade does not flash the next board's numbers.
    var sheet by remember { mutableStateOf<GameUiState?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.status, state.runId) {
        if (state.status == GamePlayStatus.PLAYING) {
            sheetVisible = false
        } else {
            delay(if (state.status == GamePlayStatus.LEVEL_WON) 620 else 420)
            sheet = state
            sheetVisible = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Screen)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)
        ) {
            val title = titleFor(state)
            val board: @Composable (Modifier) -> Unit = { boardModifier ->
                Box(
                    modifier = boardModifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    } else {
                        ArrowGameBoard(
                            rows = level.rows,
                            cols = level.cols,
                            activePaths = state.activePaths,
                            blockedPath = state.blockedPath,
                            blockerPath = state.blockerPath,
                            hintPathId = state.hintPathId,
                            runId = state.runId,
                            zoom = zoom,
                            onPathClick = viewModel::onPathTap
                        )
                    }
                    ComboBurst(state.comboStreak)
                }
            }

            // A square board stacked under a header and above a control row only works while the
            // screen is taller than it is wide. In landscape that chrome eats most of the height,
            // and the board — which can only be as large as the height allows — shrinks to a
            // fraction of the screen with dead space either side. Putting the chrome beside the
            // board instead gives the board the full height to fill.
            if (maxWidth > maxHeight) {
                Row(modifier = Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.size(12.dp))
                    Column(
                        modifier = Modifier.width(LANDSCAPE_SIDEBAR).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIcon(Icons.Default.ChevronLeft, "Back", onClick = onBack)
                            Title(title, Modifier.weight(1f), MaterialTheme.typography.titleMedium)
                            RoundIcon(Icons.Default.Refresh, "Restart", onClick = viewModel::restartCurrentLevel)
                        }
                        Spacer(Modifier.height(14.dp))
                        ProgressBar(state.progressPercent)
                        Spacer(Modifier.height(14.dp))
                        ArrowsLeftPill(state.activePaths.size)
                        Spacer(Modifier.height(10.dp))
                        Lives(state.maxMistakes, state.remainingLives)
                        Spacer(Modifier.height(10.dp))
                        DifficultyPill(state)
                        Spacer(Modifier.height(20.dp))
                        Controls(state, zoom, viewModel, horizontal = false)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundIcon(Icons.Default.ChevronLeft, "Back", onClick = onBack)
                        Title(title, Modifier.weight(1f), MaterialTheme.typography.titleLarge)
                        RoundIcon(Icons.Default.Refresh, "Restart", onClick = viewModel::restartCurrentLevel)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArrowsLeftPill(state.activePaths.size)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Lives(state.maxMistakes, state.remainingLives)
                        }
                        DifficultyPill(state)
                    }
                    ProgressBar(state.progressPercent)
                    Spacer(Modifier.height(8.dp))

                    board(Modifier.weight(1f).fillMaxWidth())

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Controls(state, zoom, viewModel, horizontal = true)
                    }
                }
            }
        }

        val shown = sheet
        if (shown != null) {
            // The sheet stays on screen while it fades out; a second tap in that window must not
            // act again, or a double-tap on "Next Level" would skip a level.
            fun once(action: () -> Unit): () -> Unit = {
                if (sheetVisible) {
                    sheetVisible = false
                    action()
                }
            }
            ResultSheet(
                visible = sheetVisible,
                state = shown,
                onNext = once(viewModel::nextLevel),
                onReplay = once(viewModel::restartCurrentLevel),
                onNewRun = once { viewModel.startEndlessRun(shown.endlessDifficulty) },
                onMenu = once(onBack)
            )
        }
    }
}

private fun titleFor(state: GameUiState) = when (state.mode) {
    GameMode.ENDLESS -> "Endless #${state.endlessStreak + 1}"
    GameMode.DAILY -> "Daily Puzzle"
    GameMode.SHARED -> "Shared Puzzle"
    GameMode.CAMPAIGN -> "Level ${state.currentLevel.levelNumber}"
}

/** Width of the landscape sidebar: room for the title row and a 60dp action button. */
private val LANDSCAPE_SIDEBAR = 190.dp

// -- Building blocks ------------------------------------------------------

/** The title rolls up to the next level rather than snapping, so progress is felt. */
@Composable
private fun Title(text: String, modifier: Modifier, style: TextStyle) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        contentAlignment = Alignment.Center,
        label = "title"
    ) { value ->
        Text(
            text = value,
            style = style,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    val color by animateColorAsState(if (fraction >= 1f) Success else Accent, label = "progressColor")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Chip)) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ArrowsLeftPill(count: Int) {
    Pill(Modifier.clearAndSetSemantics { contentDescription = "$count arrows left" }) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "Arrows left",
            tint = Ink,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.size(6.dp))
        // The count rolls: down when an arrow escapes, back up on undo.
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                if (targetState < initialState) {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                } else {
                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                }
            },
            label = "arrowsLeft"
        ) { value ->
            Text(text = "$value", style = MaterialTheme.typography.labelLarge, color = Ink)
        }
    }
}

@Composable
private fun DifficultyPill(state: GameUiState) {
    Pill {
        Text(
            text = state.currentLevel.difficulty.title,
            style = MaterialTheme.typography.labelLarge,
            color = state.currentLevel.difficulty.badgeColor
        )
    }
}

/** A lost heart pops, then drains to grey. A restored one (new board) simply refills. */
@Composable
private fun Lives(maxMistakes: Int, remaining: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$remaining of $maxMistakes lives" }
    ) {
        repeat(maxMistakes) { i ->
            val alive = i < remaining
            val pop = remember { Animatable(1f) }
            var wasAlive by remember { mutableStateOf(alive) }
            LaunchedEffect(alive) {
                if (wasAlive && !alive) {
                    pop.snapTo(1.55f)
                    pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
                }
                wasAlive = alive
            }
            val tint by animateColorAsState(
                targetValue = if (alive) Heart else HeartEmpty,
                animationSpec = tween(420),
                label = "heart"
            )
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(horizontal = 3.dp).size(22.dp).scale(pop.value)
            )
        }
    }
}

/**
 * Praise for a run of clean escapes. Floats up from the board and fades; any blocked tap resets
 * the combo to zero, so it only ever celebrates a genuine streak.
 */
@Composable
private fun ComboBurst(combo: Int) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 3) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(950, easing = LinearOutSlowInEasing))
        }
    }
    if (combo < 3 || progress.value >= 1f) return
    val (word, color) = when {
        combo >= 10 -> "Unstoppable!" to DiffMaster
        combo >= 7 -> "Amazing!" to Danger
        combo >= 5 -> "Great!" to Gold
        else -> "Nice!" to Accent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            val pop = (p * 5f).coerceAtMost(1f)
            alpha = 1f - p * p
            scaleX = 0.7f + 0.3f * pop
            scaleY = scaleX
            translationY = -p * 64.dp.toPx()
        }
    ) {
        Text(
            word,
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            fontWeight = FontWeight.Black
        )
        Text(
            "combo ×$combo",
            style = MaterialTheme.typography.titleSmall,
            color = InkMuted
        )
    }
}

/** Undo, hint and the fit-board control, laid out along whichever axis has the room. */
@Composable
private fun Controls(
    state: GameUiState,
    zoom: BoardZoomState,
    viewModel: GameViewModel,
    horizontal: Boolean
) {
    val buttons: List<@Composable () -> Unit> = buildList {
        add {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                enabled = state.canUndo,
                onClick = viewModel::undo
            )
        }
        add {
            ActionButton(
                icon = Icons.Default.Lightbulb,
                label = "Hint",
                enabled = state.availableHints > 0,
                badge = state.availableHints,
                onClick = viewModel::useHint
            )
        }
        if (zoom.isZoomed) {
            add {
                ActionButton(
                    icon = Icons.Default.ZoomOutMap,
                    label = "Fit board",
                    enabled = true,
                    onClick = zoom::reset
                )
            }
        }
    }

    if (horizontal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) Spacer(Modifier.size(28.dp))
                button()
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            buttons.forEachIndexed { index, button ->
                if (index > 0) Spacer(Modifier.size(16.dp))
                button()
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(Chip),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Chip)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    badge: Int? = null,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(if (enabled) Accent else InkFaint, label = "actionTint")
    val fill by animateColorAsState(if (enabled) AccentSoft else Chip, label = "actionFill")
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .bounceClick(enabled = enabled, onClick = onClick)
                .size(60.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        if (badge != null && badge > 0) {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(targetState = badge, label = "badge") { value ->
                    Text("$value", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

// ── Result sheet ─────────────────────────────────────────────────────────

@Composable
private fun ResultSheet(
    visible: Boolean,
    state: GameUiState,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onNewRun: () -> Unit,
    onMenu: () -> Unit
) {
    val won = state.status == GamePlayStatus.LEVEL_WON
    val endless = state.mode == GameMode.ENDLESS
    val accent = if (won) Accent else Danger

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = 0.38f))
                // Swallow taps so nothing reaches the board or controls behind the sheet.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            if (won) Confetti()

            Column(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = 420f), initialScale = 0.8f) +
                            slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 5 },
                        exit = scaleOut(tween(180), targetScale = 0.92f)
                    )
                    .padding(24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Screen)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        won && state.mode == GameMode.DAILY -> "Daily Solved"
                        won -> "Level Cleared"
                        endless -> "Run Over"
                        else -> "Out of Lives"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        endless -> "Streak ${state.endlessStreak} · best ${state.endlessHighScore}"
                        !won -> "Clear the arrows that have a free lane first"
                        state.mode == GameMode.DAILY -> "${state.dailyStreak} day streak"
                        state.mode == GameMode.SHARED -> "Shared puzzle"
                        else -> "Level ${state.currentLevel.levelNumber}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )

                if (won) {
                    Spacer(Modifier.height(20.dp))
                    Row {
                        repeat(3) { i ->
                            val scale = remember { Animatable(0f) }
                            val spin = remember { Animatable(-90f) }
                            LaunchedEffect(Unit) {
                                delay(180L + i * 160L)
                                coroutineScope {
                                    launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, 400f)) }
                                    launch { spin.animateTo(0f, spring(0.55f, 300f)) }
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (i < state.starsEarned) Gold else GoldEmpty,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(if (i == 1) 56.dp else 46.dp)
                                    .graphicsLayer {
                                        scaleX = scale.value
                                        scaleY = scale.value
                                        rotationZ = spin.value
                                        // The centre star sits a little higher, like a podium.
                                        translationY = if (i == 1) -6.dp.toPx() else 0f
                                    }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Stat("Time", formatTime(state.elapsedSeconds))
                    Stat("Moves", "${state.movesMade}")
                }

                if (won && state.hintEarned) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentSoft)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lightbulb, null, tint = Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "+1 hint for a perfect clear",
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent
                        )
                    }
                }

                val code = state.puzzleCode
                if (code != null) {
                    Spacer(Modifier.height(18.dp))
                    ShareCodeRow(code = code, seconds = state.elapsedSeconds)
                }

                Spacer(Modifier.height(24.dp))
                when {
                    won -> SheetButton(
                        if (state.mode == GameMode.CAMPAIGN || endless) "Next Level" else "Play Again",
                        accent, Color.White, onNext
                    )
                    // An endless run is over once the lives are gone, so the way on is a new run.
                    // Replaying the board that ended it would resume a streak already banked.
                    endless -> SheetButton("New Run", accent, Color.White, onNewRun)
                    else -> SheetButton("Try Again", accent, Color.White, onReplay)
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (won) {
                        Box(Modifier.weight(1f)) { SheetButton("Replay", Chip, Ink, onReplay) }
                        Spacer(Modifier.size(10.dp))
                    }
                    Box(Modifier.weight(1f)) { SheetButton("Menu", Chip, Ink, onMenu) }
                }
            }
        }
    }
}

private class ConfettiPiece(random: Random) {
    val x = random.nextFloat()
    val delay = random.nextFloat() * 0.25f
    val speed = 0.7f + random.nextFloat() * 0.6f
    val sway = 8f + random.nextFloat() * 22f
    val freq = 6f + random.nextFloat() * 8f
    val spin = (random.nextFloat() - 0.5f) * 1440f
    val w = 6f + random.nextFloat() * 6f
    val h = 10f + random.nextFloat() * 8f
    val color = CONFETTI[random.nextInt(CONFETTI.size)]
}

private val CONFETTI = listOf(Accent, Gold, Success, Heart, DiffMaster)

/** One shower of paper that falls past the sheet once, then stops drawing entirely. */
@Composable
private fun Confetti() {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(2400, easing = LinearEasing)) }
    val pieces = remember { Random(System.nanoTime()).let { r -> List(80) { ConfettiPiece(r) } } }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        if (t >= 1f) return@Canvas
        val d = density
        for (c in pieces) {
            val p = ((t - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val y = -40f * d + p * c.speed * size.height * 1.15f
            val x = c.x * size.width + sin(p * c.freq) * c.sway * d
            val fade = if (p > 0.8f) (1f - p) / 0.2f else 1f
            rotate(c.spin * p, pivot = Offset(x, y)) {
                drawRect(
                    color = c.color.copy(alpha = fade),
                    topLeft = Offset(x - c.w * d / 2f, y - c.h * d / 2f),
                    size = Size(c.w * d, c.h * d)
                )
            }
        }
    }
}

private fun formatTime(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * The board's code plus a share action. Tapping opens the system share sheet — the user picks
 * the app and sends it themselves; nothing leaves the device on its own.
 */
@Composable
private fun ShareCodeRow(code: String, seconds: Int) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .bounceClick {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "I cleared Arrow Escape puzzle $code in ${formatTime(seconds)}. Can you beat it?"
                    )
                }
                context.startActivity(Intent.createChooser(share, "Share puzzle"))
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Chip)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Puzzle code", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Text(
                text = code,
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                color = Ink
            )
        }
        Icon(Icons.Default.Share, contentDescription = "Share", tint = Accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun SheetButton(label: String, background: Color, content: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, fontWeight = FontWeight.Bold)
    }
}
