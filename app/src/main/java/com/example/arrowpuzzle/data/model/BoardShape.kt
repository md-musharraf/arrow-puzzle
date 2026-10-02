package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The silhouette a puzzle is cut from. Arrows are only ever placed on cells inside the
 * shape, so every level reads as a distinct object rather than another square of lines.
 */
@Serializable
enum class BoardShape(val title: String) {
    SQUARE("Grid"),
    SLAB("Slab"),
    ORB("Orb"),
    KITE("Kite"),
    CROSS("Cross"),
    RING("Ring"),
    COLUMNS("Columns"),
    DECKS("Decks"),
    HEX("Hex");

    /**
     * Cell centres are normalised to [-1, 1] on both axes, so a shape looks the same
     * whether it is cut from a 7x7 board or a 20x20 one.
     */
    fun contains(row: Int, col: Int, rows: Int, cols: Int): Boolean {
        val x = (col + 0.5f) / cols * 2f - 1f
        val y = (row + 0.5f) / rows * 2f - 1f
        return when (this) {
            SQUARE -> true
            SLAB -> quartic(x) + quartic(y) <= 1f
            ORB -> x * x + y * y <= 1f
            KITE -> abs(x) + abs(y) <= 1.05f
            CROSS -> abs(x) <= 0.36f || abs(y) <= 0.36f
            RING -> hypot(x, y) >= 0.46f
            COLUMNS -> strips((col + 0.5f) / cols)
            DECKS -> strips((row + 0.5f) / rows)
            HEX -> abs(x) <= 1f - 0.5f * abs(y)
        }
    }

    /** A superellipse: square-ish with softly rounded corners. */
    private fun quartic(v: Float) = (v * v) * (v * v)

    /** Three parallel strips separated by empty gutters. */
    private fun strips(t: Float) = t <= 0.28f || (t >= 0.37f && t <= 0.63f) || t >= 0.72f

    companion object {
        /**
         * Walks the shapes with a stride coprime to the list size, so consecutive levels
         * never repeat a silhouette but every shape still comes round evenly.
         */
        fun forLevel(levelNumber: Int): BoardShape =
            entries[((levelNumber - 1) * 4).mod(entries.size)]
    }
}
