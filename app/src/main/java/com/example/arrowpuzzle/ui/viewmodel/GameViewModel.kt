package com.example.arrowpuzzle.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.arrowpuzzle.audio.SoundManager
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.levels.LevelsRepository
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.Level
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.data.model.PuzzleSeed
import com.example.arrowpuzzle.domain.GameEngine
import com.example.arrowpuzzle.domain.PathTapResult
import com.example.arrowpuzzle.domain.ProceduralLevelGenerator
import com.example.arrowpuzzle.haptics.HapticsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Where the current board came from, which decides what winning it records. */
enum class GameMode { CAMPAIGN, ENDLESS, DAILY, SHARED }

enum class GamePlayStatus {
    PLAYING,
    LEVEL_WON,
    LEVEL_FAILED
}

data class GameUiState(
    val currentLevel: Level,
    val activePaths: List<PathArrow> = emptyList(),
    val mistakesMade: Int = 0,
    val maxMistakes: Int = 5,
    val movesMade: Int = 0,
    val comboStreak: Int = 0,
    val elapsedSeconds: Int = 0,
    val status: GamePlayStatus = GamePlayStatus.PLAYING,
    val starsEarned: Int = 0,
    val hintPathId: Int? = null,
    val availableHints: Int = 5,
    /** True when the board just won paid out a hint — shown on the result sheet. */
    val hintEarned: Boolean = false,
    val blockedPath: PathArrow? = null,
    val blockerPath: PathArrow? = null,
    val mode: GameMode = GameMode.CAMPAIGN,
    /** Shareable code for boards that have one — the daily and shared puzzles. */
    val puzzleCode: String? = null,
    val dailyStreak: Int = 0,
    val endlessStreak: Int = 0,
    val endlessHighScore: Int = 0,
    val canUndo: Boolean = false,
    /** True while a board is being built off the main thread. */
    val isLoading: Boolean = false,
    /** The tier an endless run started at; its boards harden from here as the streak grows. */
    val endlessDifficulty: Difficulty = Difficulty.MEDIUM,
    /** Bumped every time a board is (re)started, so the UI can replay its entry animation. */
    val runId: Int = 0
) {
    val isEndlessMode: Boolean get() = mode == GameMode.ENDLESS

    val remainingLives: Int
        get() = (maxMistakes - mistakesMade).coerceAtLeast(0)

    val progressPercent: Float
        get() {
            val total = currentLevel.totalPaths
            if (total == 0) return 1f
            val cleared = total - activePaths.size
            return (cleared.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val soundManager = SoundManager(application)
    private val hapticsManager = HapticsManager(application)

    private val undoStack = mutableListOf<PathArrow>()
    private var timerJob: Job? = null
    private var collisionClearJob: Job? = null

    /** Cancels an in-flight build when the player moves on before it finishes. */
    private var loadJob: Job? = null

    /**
     * The next endless board, built while the current one is being played.
     *
     * Endless boards cannot be cached by number the way campaign levels are — every run rolls its
     * own — so the step ahead is held here instead, keyed by the streak it belongs to. Without it
     * every board cleared in a run is followed by a wait on the spinner.
     */
    private var endlessAhead: Pair<Int, Level>? = null
    private var endlessAheadJob: Job? = null

    /**
     * Starts on a placeholder rather than a real board: building one is tens of milliseconds of
     * CPU work and a ViewModel is constructed on the main thread during the first frame. The
     * first real board arrives from [loadCampaignLevel] a moment later.
     */
    private val _uiState = MutableStateFlow(
        GameUiState(
            currentLevel = EMPTY_LEVEL,
            availableHints = prefs.availableHints,
            endlessHighScore = prefs.endlessHighScore,
            isLoading = true
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        loadCampaignLevel(1)
    }

    fun loadCampaignLevel(levelNumber: Int) {
        val number = levelNumber.coerceAtLeast(1)
        load(GameMode.CAMPAIGN) { LevelsRepository.getLevel(number) }
        // Build the level after this one while the player is busy with this one, so that tapping
        // "Next Level" lands on a board already in the cache.
        prefetch(number + 1)
    }

    /**
     * Starts a fresh endless run at [difficulty], from streak zero.
     *
     * Resetting the streak here is the point: it used to carry over from the previous run, so the
     * result sheet reported a streak the player had not earned and the recorded high score climbed
     * on a run that had only just begun.
     */
    fun startEndlessRun(difficulty: Difficulty = Difficulty.MEDIUM) {
        endlessAheadJob?.cancel()
        endlessAhead = null
        _uiState.update { it.copy(endlessStreak = 0, endlessDifficulty = difficulty) }
        loadEndlessLevel(difficulty, streak = 0)
    }

    private fun loadEndlessLevel(difficulty: Difficulty, streak: Int) {
        val ready = endlessAhead?.takeIf { it.first == streak }?.second
        endlessAhead = null
        if (ready != null) {
            loadJob?.cancel()
            startLevel(ready, GameMode.ENDLESS)
        } else {
            load(GameMode.ENDLESS) { buildEndless(difficulty, streak) }
        }
        prefetchEndless(difficulty, streak + 1)
    }

    private fun buildEndless(difficulty: Difficulty, streak: Int): Level =
        ProceduralLevelGenerator.generate(
            levelId = ENDLESS_LEVEL_ID,
            levelNumber = streak + 1,
            recipe = LevelsRepository.endlessRecipe(difficulty, streak),
            random = Random.Default
        )

    /** Builds the board one step further up the ramp while this one is being played. */
    private fun prefetchEndless(difficulty: Difficulty, streak: Int) {
        endlessAheadJob?.cancel()
        endlessAheadJob = viewModelScope.launch(Dispatchers.Default) {
            val level = buildEndless(difficulty, streak)
            endlessAhead = streak to level
        }
    }

    fun restartCurrentLevel() {
        val state = _uiState.value
        if (state.currentLevel.paths.isEmpty()) return
        startLevel(state.currentLevel, state.mode, state.puzzleCode)
    }

    /** Today's worldwide board. Everyone gets the same one, and it rolls over at UTC midnight. */
    fun loadDailyPuzzle() {
        val puzzle = PuzzleSeed.forDay(PuzzleSeed.today())
        load(GameMode.DAILY, puzzle.encode()) {
            LevelsRepository.buildPuzzle(puzzle, "Daily Puzzle")
        }
    }

    /** A board someone shared as a code. */
    fun loadSharedPuzzle(puzzle: PuzzleSeed) {
        load(GameMode.SHARED, puzzle.encode()) {
            LevelsRepository.buildPuzzle(puzzle, "Shared Puzzle")
        }
    }

    /**
     * Builds a board off the main thread and installs it.
     *
     * Generation searches several candidate boards and discards the ones that miss the recipe, so
     * it is CPU-bound work that grows with difficulty — far too much to run inside a frame. Doing
     * it on [Dispatchers.Default] is what keeps a level transition from dropping frames on a
     * low-end phone.
     */
    private fun load(mode: GameMode, code: String? = null, build: () -> Level) {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val level = withContext(Dispatchers.Default) { build() }
            startLevel(level, mode, code)
        }
    }

    /** Warms the cache for a level the player is likely to reach next. Never blocks them. */
    private fun prefetch(levelNumber: Int) {
        if (LevelsRepository.isCached(levelNumber)) return
        viewModelScope.launch(Dispatchers.Default) { LevelsRepository.getLevel(levelNumber) }
    }

    private fun startLevel(level: Level, mode: GameMode, code: String? = null) {
        undoStack.clear()
        _uiState.update { current ->
            current.copy(
                currentLevel = level,
                activePaths = level.paths,
                mistakesMade = 0,
                maxMistakes = level.maxMistakes,
                movesMade = 0,
                comboStreak = 0,
                elapsedSeconds = 0,
                status = GamePlayStatus.PLAYING,
                starsEarned = 0,
                hintEarned = false,
                hintPathId = null,
                blockedPath = null,
                blockerPath = null,
                mode = mode,
                puzzleCode = code,
                dailyStreak = prefs.dailyStreak,
                canUndo = false,
                isLoading = false,
                runId = current.runId + 1
            )
        }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_uiState.value.status == GamePlayStatus.PLAYING) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    fun onPathTap(path: PathArrow) {
        val state = _uiState.value
        if (state.status != GamePlayStatus.PLAYING) return
        if (state.isLoading) return
        if (!state.activePaths.contains(path)) return

        val result = GameEngine.evaluateTap(
            tappedPath = path,
            activePaths = state.activePaths,
            rows = state.currentLevel.rows,
            cols = state.currentLevel.cols
        )

        when (result) {
            is PathTapResult.Success -> {
                // Success: Path untangles and escapes
                undoStack.add(path)
                val newActive = state.activePaths.filter { it.id != path.id }
                val newMoves = state.movesMade + 1
                val newCombo = state.comboStreak + 1

                soundManager.playArrowLaunch(newCombo)
                hapticsManager.vibrateSuccess()

                if (newActive.isEmpty()) {
                    // Level Won!
                    timerJob?.cancel()
                    val stars = GameEngine.calculateStars(state.mistakesMade, state.maxMistakes)
                    when (state.mode) {
                        GameMode.CAMPAIGN -> {
                            prefs.setLevelStars(state.currentLevel.levelNumber, stars)
                            prefs.highestUnlockedLevel = state.currentLevel.levelNumber + 1
                        }
                        GameMode.ENDLESS -> prefs.endlessHighScore = state.endlessStreak + 1
                        GameMode.DAILY -> prefs.recordDailySolved(PuzzleSeed.today())
                        GameMode.SHARED -> Unit
                    }
                    soundManager.playVictory()
                    hapticsManager.vibrateVictory()

                    // The campaign never ends, so hints have to come back or a player who spends
                    // the starting five is without them forever. A flawless clear earns one.
                    val hintEarned = stars == 3 && state.availableHints < MAX_HINTS
                    val hints = state.availableHints + if (hintEarned) 1 else 0
                    if (hintEarned) prefs.availableHints = hints

                    _uiState.update {
                        it.copy(
                            activePaths = newActive,
                            movesMade = newMoves,
                            comboStreak = newCombo,
                            status = GamePlayStatus.LEVEL_WON,
                            starsEarned = stars,
                            availableHints = hints,
                            hintEarned = hintEarned,
                            hintPathId = null,
                            blockedPath = null,
                            blockerPath = null,
                            canUndo = false,
                            endlessStreak = if (it.isEndlessMode) it.endlessStreak + 1 else 0,
                            endlessHighScore = prefs.endlessHighScore,
                            dailyStreak = prefs.dailyStreak
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            activePaths = newActive,
                            movesMade = newMoves,
                            comboStreak = newCombo,
                            hintPathId = null,
                            blockedPath = null,
                            blockerPath = null,
                            canUndo = true
                        )
                    }
                }
            }

            is PathTapResult.Blocked -> {
                // Blocked: Bump collision and strike cost (5-strike retry rule)
                val newMistakes = state.mistakesMade + 1
                soundManager.playBlockedCollision()
                hapticsManager.vibrateCollision()

                val isFailed = newMistakes >= state.maxMistakes

                if (isFailed) {
                    timerJob?.cancel()
                    soundManager.playDefeat()
                    hapticsManager.vibrateDefeat()
                }

                _uiState.update {
                    it.copy(
                        mistakesMade = newMistakes,
                        comboStreak = 0,
                        blockedPath = result.tappedPath,
                        blockerPath = result.blockerPath,
                        status = if (isFailed) GamePlayStatus.LEVEL_FAILED else GamePlayStatus.PLAYING
                    )
                }

                // Clear bump visuals after short delay
                collisionClearJob?.cancel()
                collisionClearJob = viewModelScope.launch {
                    delay(500)
                    _uiState.update { it.copy(blockedPath = null, blockerPath = null) }
                }
            }
        }
    }

    fun useHint() {
        val state = _uiState.value
        if (state.status != GamePlayStatus.PLAYING) return
        if (state.availableHints <= 0) return

        val unblocked = GameEngine.findUnblockedPaths(
            state.activePaths,
            state.currentLevel.rows,
            state.currentLevel.cols
        )

        if (unblocked.isNotEmpty()) {
            val hintPath = unblocked.random()
            val newHints = state.availableHints - 1
            prefs.availableHints = newHints
            soundManager.playButtonClick()
            _uiState.update {
                it.copy(
                    hintPathId = hintPath.id,
                    availableHints = newHints
                )
            }
        }
    }

    fun undo() {
        val state = _uiState.value
        if (state.status != GamePlayStatus.PLAYING || undoStack.isEmpty()) return

        val lastPath = undoStack.removeAt(undoStack.size - 1)
        val restoredPaths = state.activePaths + lastPath
        soundManager.playButtonClick()

        _uiState.update {
            it.copy(
                activePaths = restoredPaths,
                comboStreak = 0,
                hintPathId = null,
                canUndo = undoStack.isNotEmpty()
            )
        }
    }

    fun nextLevel() {
        val state = _uiState.value
        when (state.mode) {
            // The streak was banked by the win, so the next board is built one step further up
            // the ramp rather than repeating the difficulty just cleared.
            GameMode.ENDLESS -> loadEndlessLevel(state.endlessDifficulty, state.endlessStreak)
            GameMode.CAMPAIGN -> loadCampaignLevel(state.currentLevel.levelNumber + 1)
            // A daily or shared board is one specific puzzle; there is no "next" to go to.
            GameMode.DAILY, GameMode.SHARED -> restartCurrentLevel()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        collisionClearJob?.cancel()
        loadJob?.cancel()
        endlessAheadJob?.cancel()
    }

    private companion object {
        const val ENDLESS_LEVEL_ID = 9999
        const val MAX_HINTS = 9

        /** Stands in until the first real board is built; never rendered as a playable board. */
        val EMPTY_LEVEL = Level(
            id = 0,
            levelNumber = 1,
            title = "",
            difficulty = Difficulty.EASY,
            rows = 1,
            cols = 1,
            paths = emptyList()
        )
    }
}
