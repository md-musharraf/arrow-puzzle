package com.example.arrowpuzzle.domain

import com.example.arrowpuzzle.data.model.Level
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.data.model.Point

sealed class PathTapResult {
    data class Success(val path: PathArrow) : PathTapResult()
    data class Blocked(
        val tappedPath: PathArrow,
        val blockerPath: PathArrow,
        val hitDistance: Int
    ) : PathTapResult()
}

object GameEngine {

    /**
     * Evaluates what happens when the player taps a winding arrow path.
     * Checks if the exit trajectory from its arrowhead out to the board boundary is free of other paths.
     */
    fun evaluateTap(
        tappedPath: PathArrow,
        activePaths: List<PathArrow>,
        rows: Int,
        cols: Int
    ): PathTapResult {
        // Map of all grid coordinates occupied by OTHER active paths
        val otherPaths = activePaths.filter { it.id != tappedPath.id }
        val cellOccupancy = HashMap<Point, PathArrow>()
        for (p in otherPaths) {
            for (cell in p.occupiedCells) {
                cellOccupancy[cell] = p
            }
        }

        val head = tappedPath.headPoint
        val dir = tappedPath.exitDirection

        var curR = head.r + dir.dr
        var curC = head.c + dir.dc
        var distance = 1

        while (curR in 0 until rows && curC in 0 until cols) {
            val checkPt = Point(curR, curC)
            val blocker = cellOccupancy[checkPt]
            if (blocker != null) {
                return PathTapResult.Blocked(
                    tappedPath = tappedPath,
                    blockerPath = blocker,
                    hitDistance = distance
                )
            }
            curR += dir.dr
            curC += dir.dc
            distance++
        }

        return PathTapResult.Success(tappedPath)
    }

    /**
     * Finds all winding arrow paths that currently have an unobstructed exit route.
     */
    fun findUnblockedPaths(
        activePaths: List<PathArrow>,
        rows: Int,
        cols: Int
    ): List<PathArrow> {
        // Build the occupancy map once for the whole sweep rather than once per candidate.
        // evaluateTap rebuilds it every call, which turns a solvability check into O(n^3);
        // sharing it here is what keeps large boards and the hint button cheap.
        val owner = HashMap<Point, Int>(activePaths.sumOf { it.occupiedCells.size } * 2)
        for (path in activePaths) {
            for (cell in path.occupiedCells) owner[cell] = path.id
        }

        val unblocked = mutableListOf<PathArrow>()
        for (path in activePaths) {
            if (path.isObstacle) continue
            if (laneIsClear(path, owner, rows, cols)) unblocked.add(path)
        }
        return unblocked
    }

    /** Walks a path's exit lane to the board edge. Its own cells never block it. */
    private fun laneIsClear(
        path: PathArrow,
        owner: Map<Point, Int>,
        rows: Int,
        cols: Int
    ): Boolean {
        val dir = path.exitDirection
        var r = path.headPoint.r + dir.dr
        var c = path.headPoint.c + dir.dc
        while (r in 0 until rows && c in 0 until cols) {
            val blocker = owner[Point(r, c)]
            if (blocker != null && blocker != path.id) return false
            r += dir.dr
            c += dir.dc
        }
        return true
    }

    /**
     * Checks if a level can be 100% completed by systematically picking unblocked paths.
     */
    fun isLevelSolvable(level: Level): Boolean {
        val remaining = level.paths.toMutableList()

        while (remaining.any { !it.isObstacle }) {
            val free = findUnblockedPaths(remaining, level.rows, level.cols)
            if (free.isEmpty()) return false // Deadlock!
            // Everything free right now stays free as the others leave — clearing an arrow
            // only ever empties cells — so the whole batch can go in one round.
            remaining.removeAll(free.toSet())
        }
        return true
    }

    /**
     * Calculates star rating (1 to 3 stars) based on mistakes made.
     * 0 mistakes -> 3 stars
     * 1-2 mistakes -> 2 stars
     * 3-4 mistakes -> 1 star
     */
    fun calculateStars(mistakesMade: Int, maxMistakes: Int): Int {
        return when {
            mistakesMade == 0 -> 3
            mistakesMade <= 2 -> 2
            mistakesMade < maxMistakes -> 1
            else -> 0
        }
    }
}
