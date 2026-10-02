package com.example.arrowpuzzle

import com.example.arrowpuzzle.data.levels.LevelsRepository
import com.example.arrowpuzzle.domain.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun everyEarlyCampaignLevelIsSolvable() {
        for (number in 1..250) {
            val level = LevelsRepository.getLevel(number)
            assertTrue("Level $number must have arrows", level.paths.isNotEmpty())
            assertTrue(
                "Level $number (${level.title}, ${level.difficulty}) must be solvable",
                GameEngine.isLevelSolvable(level)
            )
        }
    }

    @Test
    fun deepLevelsStaySolvable() {
        // The campaign has no end, so spot-check far beyond anything a fixed roster would cover.
        for (number in listOf(500, 1_000, 5_000, 50_000, 1_000_000)) {
            val level = LevelsRepository.getLevel(number)
            assertTrue("Level $number must have arrows", level.paths.isNotEmpty())
            assertTrue("Level $number must be solvable", GameEngine.isLevelSolvable(level))
        }
    }

    @Test
    fun levelsAreDeterministic() {
        // Same number must rebuild the same board, even after the cache has evicted it.
        val first = LevelsRepository.getLevel(777)
        repeat(40) { LevelsRepository.getLevel(it + 1) } // overflow the LRU
        val second = LevelsRepository.getLevel(777)
        assertEquals(first.paths, second.paths)
        assertEquals(first.rows, second.rows)
    }

    @Test
    fun difficultyKeepsClimbingWithLevelNumber() {
        // Boards must never shrink as the player advances.
        var previous = 0
        for (number in 1..2_000) {
            val size = LevelsRepository.boardSizeFor(number)
            assertTrue("Board shrank at level $number", size >= previous)
            previous = size
        }
        // Board size deliberately stops climbing at a readable ceiling — see
        // BoardRecipe.MAX_READABLE_SIZE. Difficulty past that point comes from the axes below,
        // which is what DifficultyRampTest measures on the boards themselves.
        assertTrue(
            "Boards should have grown over the early campaign",
            LevelsRepository.boardSizeFor(150) > LevelsRepository.boardSizeFor(1)
        )
        assertTrue(
            "Arrows should wind through more corners deeper in",
            LevelsRepository.maxTurnsFor(400) > LevelsRepository.maxTurnsFor(1)
        )
        assertTrue(
            "The tangle should deepen deeper in",
            LevelsRepository.targetDepthFor(400) > LevelsRepository.targetDepthFor(1)
        )
        assertTrue(
            "Opening moves should dry up deeper in",
            LevelsRepository.maxOpeningMovesFor(400) < LevelsRepository.maxOpeningMovesFor(1)
        )
    }
}
