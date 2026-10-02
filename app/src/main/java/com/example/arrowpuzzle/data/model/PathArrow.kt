package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class PathArrow(
    val id: Int,
    val waypoints: List<Point>,
    val exitDirection: Direction,
    val colorType: ArrowColorType = ArrowColorType.CYAN,
    val isObstacle: Boolean = false
) {
    init {
        require(waypoints.size >= 2) { "PathArrow must have at least 2 waypoints" }
    }

    val headPoint: Point
        get() = waypoints.last()

    val tailPoint: Point
        get() = waypoints.first()

    /**
     * Set of all grid integer coordinates (cells) occupied by this winding path.
     */
    /**
     * Published rather than synchronized: a board is built on a worker thread and then read on
     * the main one, so the default lazy would take a monitor on every access for a value that is
     * pure and identical whoever computes it. Worst case here is two threads computing it once.
     */
    val occupiedCells: Set<Point> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val cells = mutableSetOf<Point>()
        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            val minR = min(p1.r, p2.r)
            val maxR = max(p1.r, p2.r)
            val minC = min(p1.c, p2.c)
            val maxC = max(p1.c, p2.c)

            for (r in minR..maxR) {
                for (c in minC..maxC) {
                    cells.add(Point(r, c))
                }
            }
        }
        cells
    }

    /**
     * Checks if this path contains a given grid cell.
     */
    fun containsCell(r: Int, c: Int): Boolean {
        return occupiedCells.contains(Point(r, c))
    }
}
