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
        val early = meanWaves(3)
        val mid = meanWaves(80)
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
        assertTrue(meanCorners(200) > meanCorners(2) + 1.0)
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
