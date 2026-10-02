package com.example.arrowpuzzle.data.levels

import com.example.arrowpuzzle.data.model.BoardShape
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.Level
import com.example.arrowpuzzle.data.model.PuzzleSeed
import com.example.arrowpuzzle.domain.BoardRecipe
import com.example.arrowpuzzle.domain.ProceduralLevelGenerator
import kotlin.random.Random

/**
 * An endless campaign. Level numbers are unbounded — every level is generated on demand from its
 * own seed, so level 7 and level 7000 both build the same board on every device without a single
 * byte of level data being shipped.
 *
 * Difficulty climbs on four axes, and deliberately *not* mainly on board size. A bigger board
 * looks harder but mostly just shrinks the cells: past [BoardRecipe.MAX_READABLE_SIZE] a side the
 * cells are smaller than a fingertip on a phone, so growth stops there and the real work is done
 * by the other three — how tangled the dependency chain is, how few openings a board offers, and
 * how many corners each arrow winds through.
 */
object LevelsRepository {

    private const val BASE_BOARD = 8
    private const val LEVELS_PER_EXTRA_CELL = 8

    private const val BASE_TURNS = 2
    private const val MAX_TURNS = 12
    private const val LEVELS_PER_EXTRA_TURN = 10

    private const val BASE_MIN_TURNS = 1
    private const val MAX_MIN_TURNS = 6
    private const val LEVELS_PER_EXTRA_MIN_TURN = 30

    private const val BASE_DEPTH = 3
    /**
     * Measurement puts the deepest chain a readable board can hold at roughly a third of its
     * arrows, so a ceiling much above this is one the generator can never satisfy — and an
     * unreachable target costs every one of [ProceduralLevelGenerator]'s retries on every build.
     */
    private const val MAX_DEPTH = 12
    private const val LEVELS_PER_EXTRA_DEPTH = 6

    private const val BASE_OPENINGS = 7
    private const val MIN_OPENINGS = 1
    private const val LEVELS_PER_FEWER_OPENING = 10

    /**
     * How many levels the picker offers beyond the furthest one unlocked. The campaign has no end,
     * so the list is a moving window rather than a fixed roster.
     */
    const val LEVELS_AHEAD = 24

    private const val CACHE_LIMIT = 32

    /**
     * Levels are rebuildable from their seed, so the cache only has to cover recent play. An
     * unbounded map would otherwise keep growing for as long as someone keeps playing.
     */
    private val cache = object : LinkedHashMap<Int, Level>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Level>) = size > CACHE_LIMIT
    }

    /**
     * Builds — or returns the cached copy of — one campaign level.
     *
     * Generation is CPU work measured in tens of milliseconds on a slow phone, so callers must
     * keep this off the main thread. [com.example.arrowpuzzle.ui.viewmodel.GameViewModel] does.
     */
    @Synchronized
    fun getLevel(levelNumber: Int): Level {
        val number = levelNumber.coerceAtLeast(1)
        return cache.getOrPut(number) {
            ProceduralLevelGenerator.generate(
                levelId = number,
                levelNumber = number,
                recipe = recipeFor(number),
                random = Random(number * 104729L + 17L)
            )
        }
    }

    /** True when [levelNumber] is already built, so a caller can skip a needless dispatch. */
    @Synchronized
    fun isCached(levelNumber: Int): Boolean = cache.containsKey(levelNumber.coerceAtLeast(1))

    /** Rebuilds the exact board a [PuzzleSeed] describes — the daily board and shared codes. */
    fun buildPuzzle(puzzle: PuzzleSeed, title: String): Level =
        ProceduralLevelGenerator.generateSolvableLevel(
            levelId = puzzle.seed,
            levelNumber = 1,
            difficulty = puzzle.difficulty,
            size = puzzle.size,
            shape = puzzle.shape,
            maxTurns = puzzle.turns,
            random = Random(puzzle.seed.toLong() * 2654435761L + 11L)
        ).copy(title = title)

    /** The full difficulty specification for a campaign level. */
    fun recipeFor(levelNumber: Int): BoardRecipe {
        val n = levelNumber.coerceAtLeast(1)
        return BoardRecipe(
            size = boardSizeFor(n),
            shape = BoardShape.forLevel(n),
            difficulty = getDifficultyForLevel(n),
            maxTurns = maxTurnsFor(n),
            minTurns = minTurnsFor(n),
            targetDepth = targetDepthFor(n),
            maxOpeningMoves = maxOpeningMovesFor(n)
        )
    }

    fun getDifficultyForLevel(levelNumber: Int): Difficulty = when {
        levelNumber <= 20 -> Difficulty.EASY
        levelNumber <= 50 -> Difficulty.MEDIUM
        levelNumber <= 100 -> Difficulty.HARD
        else -> Difficulty.MASTER
    }

    /**
     * Boards grow a cell at a time and then stop for good at [BoardRecipe.MAX_READABLE_SIZE].
     * Growing past that would shrink cells below a comfortable tap target on a small phone, and
     * an arrow the player cannot reliably hit is frustration rather than difficulty.
     */
    fun boardSizeFor(levelNumber: Int): Int {
        val n = levelNumber.coerceAtLeast(1)
        return (BASE_BOARD + (n - 1) / LEVELS_PER_EXTRA_CELL).coerceAtMost(BoardRecipe.MAX_READABLE_SIZE)
    }

    /** Arrows wind through more corners the deeper you go, until they cap out. */
    fun maxTurnsFor(levelNumber: Int): Int =
        (BASE_TURNS + levelNumber / LEVELS_PER_EXTRA_TURN).coerceAtMost(MAX_TURNS)

    /** The floor rises too, so late boards stop containing simple L-shaped arrows at all. */
    fun minTurnsFor(levelNumber: Int): Int =
        (BASE_MIN_TURNS + levelNumber / LEVELS_PER_EXTRA_MIN_TURN).coerceAtMost(MAX_MIN_TURNS)

    /**
     * How many peel waves the board should need. The main difficulty axis.
     *
     * Also capped by the board itself: a chain cannot be longer than the arrows available to make
     * it, and measurement puts the ceiling at roughly a third of the arrows a board of a given
     * size holds. Asking a small board for a depth it cannot reach only burns generation attempts
     * on boards that will never satisfy the recipe.
     */
    fun targetDepthFor(levelNumber: Int): Int =
        (BASE_DEPTH + levelNumber / LEVELS_PER_EXTRA_DEPTH)
            .coerceAtMost(MAX_DEPTH)
            .coerceAtMost(boardSizeFor(levelNumber) - 4)
            .coerceAtLeast(2)

    /** Openings dry up as levels climb, so later boards have to be read rather than poked at. */
    fun maxOpeningMovesFor(levelNumber: Int): Int =
        (BASE_OPENINGS - levelNumber / LEVELS_PER_FEWER_OPENING).coerceAtLeast(MIN_OPENINGS)

    // ── Endless mode ─────────────────────────────────────────────────────

    /**
     * The board for one step of an endless run.
     *
     * The player picks the tier the run starts at, and every board cleared after that tightens the
     * recipe: the board grows towards the tier's ceiling, arrows wind through more corners, the
     * dependency chain deepens and the openings dry up. A run therefore ends when the player stops
     * being able to keep up, which is the whole point of an endless mode — the previous behaviour
     * regenerated the same difficulty forever, so a run could only ever end by accident.
     */
    fun endlessRecipe(difficulty: Difficulty, streak: Int): BoardRecipe {
        val step = streak.coerceAtLeast(0)
        val size = (difficulty.minGridSize + step / 4)
            .coerceAtMost(difficulty.maxGridSize)
            .coerceAtMost(BoardRecipe.MAX_READABLE_SIZE)
        val maxTurns = (ProceduralLevelGenerator.maxSegmentsFor(difficulty) + step / 3)
            .coerceAtMost(MAX_TURNS)
        return BoardRecipe(
            size = size,
            // A fresh silhouette each step, so a long run never looks repetitive.
            shape = BoardShape.entries[(step * 4).mod(BoardShape.entries.size)],
            difficulty = difficulty,
            maxTurns = maxTurns,
            minTurns = (1 + step / 6).coerceAtMost(maxTurns).coerceAtMost(MAX_MIN_TURNS),
            targetDepth = (endlessBaseDepth(difficulty) + step / 2)
                .coerceAtMost(MAX_DEPTH)
                .coerceAtMost(size - 4)
                .coerceAtLeast(2),
            maxOpeningMoves = (endlessBaseOpenings(difficulty) - step / 3).coerceAtLeast(MIN_OPENINGS)
        )
    }

    private fun endlessBaseDepth(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 2
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 6
        Difficulty.MASTER -> 8
    }

    private fun endlessBaseOpenings(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 10
        Difficulty.MEDIUM -> 8
        Difficulty.HARD -> 6
        Difficulty.MASTER -> 4
    }
}
