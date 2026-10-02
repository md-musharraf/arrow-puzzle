package com.example.arrowpuzzle

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.domain.GameEngine
import com.example.arrowpuzzle.ui.components.BOARD_TAG
import com.example.arrowpuzzle.ui.viewmodel.GamePlayStatus
import com.example.arrowpuzzle.ui.viewmodel.GameUiState
import com.example.arrowpuzzle.ui.viewmodel.GameViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plays the real app end to end: real screens, real navigation, real taps on the board canvas.
 *
 * The board is a canvas, so there are no arrow nodes to click. Each test reads the live board out
 * of the [GameViewModel] the screen is bound to, picks an arrow the engine says can leave, and taps
 * the centre of its head cell — exactly where a player's finger would go.
 *
 * WARNING: every test wipes saved progress first. Run it on an emulator, not a phone someone plays
 * on: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameEndToEndTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun freshInstall() {
        PreferencesManager(ApplicationProvider.getApplicationContext<Context>()).resetAllProgress()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() = scenario.close()

    // ── Campaign ─────────────────────────────────────────────────────────

    @Test
    fun campaignLevelsClearAndUnlockTheNext() {
        click("Play")
        awaitBoard()
        awaitText("Level 1")
        solveBoard()

        awaitText("Level Cleared")
        awaitText("+1 hint for a perfect clear")
        assertEquals(6, state().availableHints)

        click("Next Level")
        awaitBoard()
        assertEquals(2, state().currentLevel.levelNumber)
        awaitText("Level 2")
        solveBoard()
        awaitText("Level Cleared")

        // Back on the menu, Continue must point at the level just unlocked, not a stale one.
        click("Menu")
        awaitText("Continue · Level 3")
    }

    @Test
    fun levelSelectOpensTheChosenLevel() {
        click("Levels")
        awaitText("Select Level")
        click("1")
        awaitBoard()
        awaitText("Level 1")
    }

    // ── Mistakes, failure, retry ─────────────────────────────────────────

    @Test
    fun blockedTapsCostLivesAndRunningOutOffersARetry() {
        click("Play")
        awaitBoard()
        val lives = state().maxMistakes
        awaitDescription("$lives of $lives lives")

        tap(blockedArrow())
        compose.waitUntil(TIMEOUT) { state().mistakesMade == 1 }
        awaitDescription("${lives - 1} of $lives lives")

        repeat(lives - 1) {
            val before = state().mistakesMade
            tap(blockedArrow())
            compose.waitUntil(TIMEOUT) { state().mistakesMade == before + 1 }
        }
        assertEquals(GamePlayStatus.LEVEL_FAILED, state().status)
        awaitText("Out of Lives")

        click("Try Again")
        awaitBoard()
        awaitDescription("$lives of $lives lives")
        assertEquals(state().currentLevel.paths.size, state().activePaths.size)
    }

    // ── Undo and hint ────────────────────────────────────────────────────

    @Test
    fun undoPutsTheLastArrowBack() {
        click("Play")
        awaitBoard()
        val total = state().activePaths.size

        tap(freeArrow())
        compose.waitUntil(TIMEOUT) { state().activePaths.size == total - 1 }
        awaitDescription("${total - 1} arrows left")

        compose.onNodeWithContentDescription("Undo").performClick()
        compose.waitUntil(TIMEOUT) { state().activePaths.size == total }
        awaitDescription("$total arrows left")
        assertEquals(0, state().mistakesMade)
    }

    @Test
    fun hintPointsAtAnArrowThatCanLeave() {
        click("Play")
        awaitBoard()
        val hintsBefore = state().availableHints

        compose.onNodeWithContentDescription("Hint").performClick()
        compose.waitUntil(TIMEOUT) { state().hintPathId != null }

        val s = state()
        val free = GameEngine.findUnblockedPaths(s.activePaths, s.currentLevel.rows, s.currentLevel.cols)
        assertTrue("hint must be a free arrow", free.any { it.id == s.hintPathId })
        assertEquals(hintsBefore - 1, s.availableHints)

        // Following the hint clears that arrow and the highlight goes with it.
        tap(s.activePaths.first { it.id == s.hintPathId })
        compose.waitUntil(TIMEOUT) { state().hintPathId == null }
        assertEquals(0, state().mistakesMade)
    }

    // ── Other modes ──────────────────────────────────────────────────────

    @Test
    fun endlessRunsClimbBoardByBoard() {
        click("Endless")
        awaitText("Start Run")
        click("Start Run")
        awaitBoard()
        awaitText("Endless #1")
        solveBoard()
        awaitText("Level Cleared")

        click("Next Level")
        awaitBoard()
        awaitText("Endless #2")
        assertEquals(1, state().endlessStreak)
        solveBoard()
        awaitText("Level Cleared")
        assertEquals(2, state().endlessStreak)
    }

    @Test
    fun dailyPuzzleIsMarkedSolvedOnTheMenu() {
        click("Daily Puzzle")
        awaitBoard()
        awaitText("Daily Puzzle")
        solveBoard()
        awaitText("Daily Solved")
        awaitText("Puzzle code")

        click("Menu")
        awaitText("Daily Puzzle · solved")
    }

    @Test
    fun randomPuzzleFromTheCodeScreenIsPlayable() {
        click("Code")
        awaitText("Surprise me")
        click("Surprise me")
        awaitBoard()
        awaitText("Shared Puzzle")
        solveBoard()
        awaitText("Level Cleared")
        awaitText("Puzzle code")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun viewModel(): GameViewModel {
        lateinit var vm: GameViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        return vm
    }

    private fun state(): GameUiState = viewModel().uiState.value

    private fun awaitBoard() {
        compose.waitUntil(BUILD_TIMEOUT) {
            val s = state()
            !s.isLoading && s.status == GamePlayStatus.PLAYING && s.activePaths.isNotEmpty()
        }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag(BOARD_TAG), TIMEOUT)
        compose.waitForIdle()
    }

    private fun awaitText(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text), TIMEOUT)

    private fun awaitDescription(text: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(text), TIMEOUT)

    private fun click(text: String) {
        awaitText(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun freeArrow(): PathArrow {
        val s = state()
        return GameEngine.findUnblockedPaths(s.activePaths, s.currentLevel.rows, s.currentLevel.cols)
            .first()
    }

    private fun blockedArrow(): PathArrow {
        val s = state()
        val free = GameEngine.findUnblockedPaths(s.activePaths, s.currentLevel.rows, s.currentLevel.cols)
            .map { it.id }.toSet()
        return s.activePaths.first { it.id !in free }
    }

    /** Taps the centre of [arrow]'s head cell, which no other arrow can occupy. */
    private fun tap(arrow: PathArrow) {
        val level = state().currentLevel
        val board = compose.onNodeWithTag(BOARD_TAG)
        val size = board.fetchSemanticsNode().size
        val cellW = size.width.toFloat() / level.cols
        val cellH = size.height.toFloat() / level.rows
        val head = arrow.headPoint
        board.performTouchInput { click(Offset((head.c + 0.5f) * cellW, (head.r + 0.5f) * cellH)) }
    }

    /** Clears the whole board one free arrow at a time, the way a careful player would. */
    private fun solveBoard() {
        while (true) {
            val s = state()
            if (s.activePaths.isEmpty()) break
            val free = GameEngine.findUnblockedPaths(s.activePaths, s.currentLevel.rows, s.currentLevel.cols)
            assertTrue("board deadlocked with ${s.activePaths.size} arrows left", free.isNotEmpty())
            val before = s.activePaths.size
            tap(free.first())
            compose.waitUntil(TIMEOUT) { state().activePaths.size == before - 1 }
        }
        assertEquals("a careful solve must not cost a life", 0, state().mistakesMade)
        compose.waitUntil(TIMEOUT) { state().status == GamePlayStatus.LEVEL_WON }
    }

    private companion object {
        const val TIMEOUT = 5_000L
        /** Board generation runs on a worker thread and is slowest on an emulator. */
        const val BUILD_TIMEOUT = 20_000L
    }
}
