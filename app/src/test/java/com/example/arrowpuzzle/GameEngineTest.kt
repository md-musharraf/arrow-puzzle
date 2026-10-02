package com.example.arrowpuzzle

import com.example.arrowpuzzle.data.model.ArrowColorType
import com.example.arrowpuzzle.data.model.BoardShape
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.Direction
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.data.model.Point
import com.example.arrowpuzzle.domain.GameEngine
import com.example.arrowpuzzle.domain.PathTapResult
import com.example.arrowpuzzle.domain.ProceduralLevelGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    @Test
    fun testUnblockedWindingPathEscapes() {
        val path1 = PathArrow(
            id = 1,
            waypoints = listOf(Point(2, 1), Point(0, 1)),
            exitDirection = Direction.UP,
            colorType = ArrowColorType.CYAN
        )
        val path2 = PathArrow(
            id = 2,
            waypoints = listOf(Point(3, 1), Point(3, 3), Point(1, 3)),
            exitDirection = Direction.UP,
            colorType = ArrowColorType.PURPLE
        )

        val result1 = GameEngine.evaluateTap(path1, listOf(path1, path2), rows = 4, cols = 4)
        assertTrue(result1 is PathTapResult.Success)

        val result2 = GameEngine.evaluateTap(path2, listOf(path1, path2), rows = 4, cols = 4)
        assertTrue(result2 is PathTapResult.Success)
    }

    @Test
    fun testBlockedWindingPathCollides() {
        // path1 points UP exiting at (1,1)->(0,1), but path2 occupies (0,1)
        val path1 = PathArrow(
            id = 1,
            waypoints = listOf(Point(2, 1), Point(1, 1)),
            exitDirection = Direction.UP,
            colorType = ArrowColorType.CYAN
        )
        val path2 = PathArrow(
            id = 2,
            waypoints = listOf(Point(0, 0), Point(0, 3)),
            exitDirection = Direction.RIGHT,
            colorType = ArrowColorType.PURPLE
        )

        val result = GameEngine.evaluateTap(path1, listOf(path1, path2), rows = 4, cols = 4)
        assertTrue(result is PathTapResult.Blocked)
        val blocked = result as PathTapResult.Blocked
        assertEquals(path1.id, blocked.tappedPath.id)
        assertEquals(path2.id, blocked.blockerPath.id)
        assertEquals(1, blocked.hitDistance)
    }

    @Test
    fun testFindUnblockedPaths() {
        val path1 = PathArrow(
            id = 1,
            waypoints = listOf(Point(2, 1), Point(1, 1)),
            exitDirection = Direction.UP,
            colorType = ArrowColorType.CYAN
        ) // blocked by path2
        val path2 = PathArrow(
            id = 2,
            waypoints = listOf(Point(0, 0), Point(0, 3)),
            exitDirection = Direction.RIGHT,
            colorType = ArrowColorType.PURPLE
        ) // free

        val active = listOf(path1, path2)
        val unblocked = GameEngine.findUnblockedPaths(active, rows = 4, cols = 4)

        assertEquals(1, unblocked.size)
        assertTrue(unblocked.any { it.id == 2 })
        assertFalse(unblocked.any { it.id == 1 })
    }

    @Test
    fun testStarCalculation() {
        assertEquals(3, GameEngine.calculateStars(0, 5))
        assertEquals(2, GameEngine.calculateStars(1, 5))
        assertEquals(2, GameEngine.calculateStars(2, 5))
        assertEquals(1, GameEngine.calculateStars(3, 5))
        assertEquals(1, GameEngine.calculateStars(4, 5))
        assertEquals(0, GameEngine.calculateStars(5, 5))
    }

    @Test
    fun testProceduralLevelGeneratorProducesSolvableLevels() {
        for (diff in Difficulty.entries) {
            for (shape in BoardShape.entries) {
                val level = ProceduralLevelGenerator.generateSolvableLevel(
                    levelId = 100,
                    levelNumber = 1,
                    difficulty = diff,
                    size = 12,
                    shape = shape,
                    random = Random(12345)
                )
                assertTrue(
                    "$shape / $diff must produce arrows",
                    level.paths.isNotEmpty()
                )
                assertTrue(
                    "$shape / $diff must be solvable",
                    GameEngine.isLevelSolvable(level)
                )
            }
        }
    }

    @Test
    fun testGeneratorPacksBoardsDensely() {
        // A packed 16x16 square should carry far more arrows than the old border-seeded
        // generator managed; this guards the density the puzzles depend on.
        val level = ProceduralLevelGenerator.generateSolvableLevel(
            levelId = 1,
            levelNumber = 1,
            difficulty = Difficulty.HARD,
            size = 16,
            shape = BoardShape.SQUARE,
            random = Random(7)
        )
        assertTrue("Expected a dense board, got ${level.paths.size} arrows", level.paths.size >= 30)
    }

    @Test
    fun testShapesConfineArrowsToTheSilhouette() {
        val size = 14
        val level = ProceduralLevelGenerator.generateSolvableLevel(
            levelId = 1,
            levelNumber = 1,
            difficulty = Difficulty.MEDIUM,
            size = size,
            shape = BoardShape.ORB,
            random = Random(3)
        )
        for (path in level.paths) {
            for (cell in path.occupiedCells) {
                assertTrue(
                    "Cell $cell escaped the ORB silhouette",
                    BoardShape.ORB.contains(cell.r, cell.c, size, size)
                )
            }
        }
    }
}
