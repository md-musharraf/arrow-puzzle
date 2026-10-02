package com.example.arrowpuzzle

import com.example.arrowpuzzle.data.levels.LevelsRepository
import com.example.arrowpuzzle.data.model.BoardShape
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.PuzzleSeed
import com.example.arrowpuzzle.domain.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleSeedTest {

    @Test
    fun codesRoundTrip() {
        val random = Random(11)
        repeat(3_000) {
            val puzzle = PuzzleSeed.random(random)
            assertEquals(puzzle, PuzzleSeed.decode(puzzle.encode()))
        }
    }

    @Test
    fun codesAreEightCharactersPlusSeparator() {
        val code = PuzzleSeed.random(Random(3)).encode()
        assertEquals(9, code.length)
        assertEquals('-', code[4])
    }

    @Test
    fun lookAlikeCharactersAndFormattingAreForgiven() {
        val puzzle = PuzzleSeed(seed = 12345, size = 16, shape = BoardShape.ORB, difficulty = Difficulty.HARD, turns = 6)
        val code = puzzle.encode()
        assertEquals(puzzle, PuzzleSeed.decode(code.lowercase()))
        assertEquals(puzzle, PuzzleSeed.decode(code.replace("-", "")))
        assertEquals(puzzle, PuzzleSeed.decode("  $code  "))
        // O/I/L are not in the alphabet, so they fold back to 0/1 rather than failing.
        assertEquals(
            PuzzleSeed.decode(code),
            PuzzleSeed.decode(code.replace('0', 'O').replace('1', 'I'))
        )
    }

    @Test
    fun typosAreRejectedRatherThanSilentlyLoadingAnotherBoard() {
        val code = PuzzleSeed(seed = 999, size = 14, shape = BoardShape.HEX, difficulty = Difficulty.MEDIUM, turns = 4).encode()
        var rejected = 0
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        for (position in code.indices) {
            if (code[position] == '-') continue
            for (ch in alphabet) {
                if (ch == code[position]) continue
                val typo = code.toCharArray().also { it[position] = ch }.concatToString()
                if (PuzzleSeed.decode(typo) == null) rejected++
            }
        }
        // A 5-bit checksum should catch the large majority of single-character slips.
        assertTrue("Only $rejected typos rejected", rejected > 220)
        assertNull(PuzzleSeed.decode("nonsense"))
        assertNull(PuzzleSeed.decode(""))
    }

    @Test
    fun dailyPuzzleIsStablePerDayAndChangesDaily() {
        val day = PuzzleSeed.today()
        assertEquals(PuzzleSeed.forDay(day), PuzzleSeed.forDay(day))
        assertTrue(PuzzleSeed.forDay(day) != PuzzleSeed.forDay(day + 1))
    }

    @Test
    fun everySharedOrDailyBoardIsSolvable() {
        val random = Random(7)
        repeat(120) {
            val puzzle = PuzzleSeed.random(random)
            val level = LevelsRepository.buildPuzzle(puzzle, "test")
            assertTrue("${puzzle.encode()} must have arrows", level.paths.isNotEmpty())
            assertTrue("${puzzle.encode()} must be solvable", GameEngine.isLevelSolvable(level))
        }
        for (offset in 0..400) {
            val level = LevelsRepository.buildPuzzle(PuzzleSeed.forDay(PuzzleSeed.today() + offset), "daily")
            assertNotNull(level)
            assertTrue("Daily +$offset must be solvable", GameEngine.isLevelSolvable(level))
        }
    }

    @Test
    fun sameCodeRebuildsTheSameBoard() {
        val code = PuzzleSeed.random(Random(42)).encode()
        val first = LevelsRepository.buildPuzzle(PuzzleSeed.decode(code)!!, "x")
        val second = LevelsRepository.buildPuzzle(PuzzleSeed.decode(code)!!, "x")
        assertEquals(first.paths, second.paths)
    }
}
