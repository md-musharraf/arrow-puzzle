package com.example.arrowpuzzle.domain

import com.example.arrowpuzzle.data.model.BoardShape
import com.example.arrowpuzzle.data.model.Difficulty

/**
 * What a finished board should feel like, rather than how big it should be.
 *
 * Board size is a poor difficulty knob: past about 18 cells a side the cells are smaller than a
 * fingertip on a phone, so a bigger board stops being harder and just becomes fiddly. These are
 * the knobs that actually change how hard a board is to think about.
 */
data class BoardRecipe(
    val size: Int,
    val shape: BoardShape,
    val difficulty: Difficulty,
    /** Corners the longest arrows wind through. */
    val maxTurns: Int,
    /** Corners an arrow must reach to be accepted while the complexity passes run. */
    val minTurns: Int,
    /**
     * Greedy peel waves the finished board should need — clear everything that has a free lane,
     * then everything freed by that, and so on. This is the depth of the dependency chain and the
     * closest thing to a true difficulty measure: a board solvable in two waves is trivial no
     * matter how many arrows it holds.
     */
    val targetDepth: Int,
    /** Arrows tappable on move one. Fewer means less to try at random and more to read. */
    val maxOpeningMoves: Int
) {
    companion object {
        /** Cells a side past which a phone screen cannot render a tappable board. */
        const val MAX_READABLE_SIZE = 18
    }
}
