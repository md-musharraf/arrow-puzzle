package com.example.arrowpuzzle

import com.example.arrowpuzzle.data.levels.LevelsRepository
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.Level
import com.example.arrowpuzzle.domain.BoardRecipe
import com.example.arrowpuzzle.domain.GameEngine
import com.example.arrowpuzzle.domain.ProceduralLevelGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Guards the property the campaign is built on: later levels are actually harder.
 *
 * The measure is the greedy peel depth — clear every arrow with a free lane, then everything that
 * frees, and so on. A board solvable in two waves is easy however many arrows it holds, so depth
 * is what these tests assert on rather than board size or arrow count.
 */
class DifficultyRampTest {

    /** Waves needed to peel [level], or -1 if it deadlocks. */
    private fun waves(level: Level): Int {
        val remaining = level.paths.toMutableList()
        var count = 0
        while (remaining.any { !it.isObstacle }) {
            val free = GameEngine.findUnblockedPaths(remaining, level.rows, level.cols)
            if (free.isEmpty()) return -1
            remaining.removeAll(free.toSet())
            count++
        }
        return count
    }

    private fun meanWaves(levelNumber: Int, samples: Int = 5): Double =
        (0 until samples).map { seed ->
            waves(
                ProceduralLevelGenerator.generate(
                    levelId = levelNumber,
                    levelNumber = levelNumber,
                    recipe = LevelsRepository.recipeFor(levelNumber),
                    random = Random(levelNumber * 7919L + seed)
                )
            ).toDouble()
        }.average()

    @Test
    fun lateLevelsAreSubstantiallyDeeperThanEarlyOnes() {
        // Mid sits before the board-size cap; by ~level 80 the ramp is close to its ceiling.
        val early = meanWaves(3)
        val mid = meanWaves(40)
        val late = meanWaves(300)

        assertTrue("mid ($mid) must out-tangle early ($early)", mid > early + 1)
        assertTrue("late ($late) must out-tangle mid ($mid)", late >= mid)
        assertTrue("late ($late) must be at least twice early ($early)", late >= early * 2)
    }

    @Test
    fun arrowsWindThroughMoreCornersAsLevelsClimb() {
        fun meanCorners(levelNumber: Int): Double {
            val level = ProceduralLevelGenerator.generate(
                levelId = levelNumber,
                levelNumber = levelNumber,
                recipe = LevelsRepository.recipeFor(levelNumber),
                random = Random(levelNumber * 104729L + 17L)
            )
            return level.paths.map { it.waypoints.size - 1 }.average()
        }
        // Averaged over a full cycle of silhouettes: one thin shape (a Cross) winds less than a
        // square at any level, so a single sample says more about its shape than its level.
        val early = (1..9).map(::meanCorners).average()
        val late = (200..208).map(::meanCorners).average()
        assertTrue("late ($late) must wind more than early ($early)", late > early + 1.0)
    }

    /**
     * Boards must stay tappable. Past this many cells a side the cells are smaller than a
     * fingertip on a small phone, which is fiddly rather than difficult.
     */
    @Test
    fun boardsNeverGrowBeyondAReadableSize() {
        for (levelNumber in listOf(1, 50, 200, 1_000, 100_000)) {
            assertTrue(
                "level $levelNumber board is ${LevelsRepository.boardSizeFor(levelNumber)}",
                LevelsRepository.boardSizeFor(levelNumber) <= BoardRecipe.MAX_READABLE_SIZE
            )
        }
    }

    @Test
    fun everyCampaignLevelAcrossTheRampIsSolvable() {
        for (levelNumber in listOf(1, 2, 5, 17, 31, 64, 99, 140, 220, 500, 1_500, 9_999)) {
            val level = LevelsRepository.getLevel(levelNumber)
            assertTrue("level $levelNumber has no arrows", level.paths.isNotEmpty())
            assertTrue("level $levelNumber deadlocks", GameEngine.isLevelSolvable(level))
        }
    }

    /**
     * An arrow whose body lies in its own exit lane points at its own tail and, on escape, would
     * slide its head straight through itself. The engine ignores self-blocking, so only a test
     * catches it.
     */
    @Test
    fun noArrowPointsIntoItsOwnBody() {
        val boards = listOf(1, 7, 40, 99, 150, 300, 777).map { LevelsRepository.getLevel(it) } +
            Difficulty.entries.map { d ->
                ProceduralLevelGenerator.generate(9999, 1, LevelsRepository.endlessRecipe(d, 25), Random(d.ordinal + 5L))
            }
        for (level in boards) {
            for (arrow in level.paths) {
                val head = arrow.headPoint
                val d = arrow.exitDirection
                val selfHit = arrow.occupiedCells.any { cell ->
                    val dr = cell.r - head.r
                    val dc = cell.c - head.c
                    if (d.dr != 0) dc == 0 && dr * d.dr > 0 else dr == 0 && dc * d.dc > 0
                }
                assertTrue("level ${level.levelNumber} arrow ${arrow.id} points into itself", !selfHit)
            }
        }
    }

    /**
     * Packing leaves many arrows tappable on move one; filler trimming brings boards back to their
     * opening budget. It can only drop arrows that block nothing, so a free arrow holding up
     * another stays — measured, that leaves a few levels up to three over. Without trimming most
     * early levels ran double their budget.
     */
    @Test
    fun earlyBoardsMostlyKeepToTheirOpeningBudget() {
        var within = 0
        for (levelNumber in 1..30) {
            val level = LevelsRepository.getLevel(levelNumber)
            val open = GameEngine.findUnblockedPaths(level.paths, level.rows, level.cols).size
            val budget = LevelsRepository.maxOpeningMovesFor(levelNumber)
            if (open <= budget) within++
            assertTrue("level $levelNumber opens with $open moves, budget $budget", open <= budget + 3)
        }
        assertTrue("only $within of 30 levels kept to their budget", within >= 22)
    }

    /** A level number must build the same board every time, on every device. */
    @Test
    fun levelsAreDeterministic() {
        val first = LevelsRepository.getLevel(77)
        val rebuilt = ProceduralLevelGenerator.generate(
            levelId = 77,
            levelNumber = 77,
            recipe = LevelsRepository.recipeFor(77),
            random = Random(77 * 104729L + 17L)
        )
        assertEquals(first.paths.size, rebuilt.paths.size)
        assertEquals(first.paths.map { it.waypoints }, rebuilt.paths.map { it.waypoints })
    }

    // ── Endless mode ─────────────────────────────────────────────────────

    @Test
    fun endlessRunsHardenAsTheStreakGrows() {
        val start = LevelsRepository.endlessRecipe(Difficulty.MEDIUM, streak = 0)
        val deep = LevelsRepository.endlessRecipe(Difficulty.MEDIUM, streak = 30)

        assertTrue("board should grow", deep.size > start.size)
        assertTrue("arrows should wind more", deep.maxTurns > start.maxTurns)
        assertTrue("tangle should deepen", deep.targetDepth > start.targetDepth)
        assertTrue("openings should dry up", deep.maxOpeningMoves < start.maxOpeningMoves)
    }

    @Test
    fun endlessBoardsStayWithinTheirTierAndStaySolvable() {
        for (difficulty in Difficulty.entries) {
            for (streak in listOf(0, 5, 20, 60, 200)) {
                val recipe = LevelsRepository.endlessRecipe(difficulty, streak)
                assertTrue(
                    "$difficulty streak $streak board ${recipe.size} exceeds its tier",
                    recipe.size <= difficulty.maxGridSize &&
                        recipe.size <= BoardRecipe.MAX_READABLE_SIZE
                )
                assertTrue(recipe.maxOpeningMoves >= 1)
                assertTrue(recipe.minTurns <= recipe.maxTurns)

                val level = ProceduralLevelGenerator.generate(
                    levelId = 9999,
                    levelNumber = streak + 1,
                    recipe = recipe,
                    random = Random(streak * 31L + difficulty.ordinal)
                )
                assertTrue(
                    "$difficulty streak $streak deadlocks",
                    GameEngine.isLevelSolvable(level)
                )
            }
        }
    }
}
