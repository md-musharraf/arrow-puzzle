package com.example.arrowpuzzle.domain

import com.example.arrowpuzzle.data.model.ArrowColorType
import com.example.arrowpuzzle.data.model.BoardShape
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.data.model.Direction
import com.example.arrowpuzzle.data.model.Level
import com.example.arrowpuzzle.data.model.PathArrow
import com.example.arrowpuzzle.data.model.Point
import kotlin.random.Random

/**
 * Builds packed, guaranteed-solvable boards by laying arrows down in *reverse* solve order.
 *
 * The first arrow placed is the one the player will clear last, and each new arrow is only
 * accepted if its exit lane is clear of everything placed before it. Read forwards that means
 * the last arrow placed is the first that can leave, so peeling the board in reverse placement
 * order always works — solvability is a property of the construction, not something we search for.
 *
 * On top of that the generator *steers*. Every placed arrow remembers the lane it will fly down,
 * so when a new arrow's body lands in an older arrow's lane we know the new one now blocks the
 * old one, and we can keep a running dependency depth for the whole board. Candidate placements
 * are scored against that depth, which is what lets a level ask for a board needing fifteen waves
 * to peel rather than hoping randomness supplies one.
 */
object ProceduralLevelGenerator {

    /**
     * Passes over the shuffled cell list. Early passes lay down the long winding arrows that make
     * a board interesting; later ones taper to short stubs filling whatever gaps are left, so the
     * result is both tangled and densely packed rather than one at the cost of the other.
     *
     * Six, because the clear-lane rule saturates the board around the fifth: measuring showed
     * passes beyond this place no arrow at all on any board size, while still paying to shuffle
     * every cell and grow four candidate tails for each one.
     */
    private const val PACKING_PASSES = 6

    /**
     * Whole-board rebuilds allowed while chasing a recipe. Steering gets most boards there on the
     * first try; the retries cover seeds where the silhouette leaves too little room to tangle.
     */
    private const val MAX_ATTEMPTS = 10

    /** Cells a single run may cover before the arrow has to turn. */
    private const val MAX_RUN = 4

    /**
     * Run length for the arrows that make up the dependency chain. Short runs keep a chain link
     * compact, so it still winds through its full corner budget while leaving room on the board
     * for the next link — long sweeping links run the board out of space after about ten of them.
     */
    private const val CHAIN_RUN = 1

    /** Corners a chain link winds through. Kept low so links stay compact. */
    private const val CHAIN_TURNS = 3

    /** Arrows spent closing off opening moves once the board is packed. */
    private const val MAX_CLOSING_ARROWS = 24

    /** Busiest lane crossings to try per closing arrow. */
    private const val CLOSING_CANDIDATES = 12

    /** Chain tips to try before accepting that the chain cannot grow any further. */
    private const val TIP_CANDIDATES = 8

    /** One in this many corners reverses the spin, breaking up a too-regular spiral. */
    private const val TURN_FLIP_ODDS = 3

    /**
     * Legacy entry point. Callers that know only a difficulty get a recipe derived from it.
     */
    fun generateSolvableLevel(
        levelId: Int,
        levelNumber: Int,
        difficulty: Difficulty,
        size: Int,
        shape: BoardShape,
        maxTurns: Int = maxSegmentsFor(difficulty),
        random: Random
    ): Level = generate(
        levelId = levelId,
        levelNumber = levelNumber,
        recipe = BoardRecipe(
            size = size,
            shape = shape,
            difficulty = difficulty,
            maxTurns = maxTurns,
            minTurns = (maxTurns / 2).coerceAtLeast(1),
            targetDepth = defaultDepthFor(difficulty),
            maxOpeningMoves = defaultOpeningMovesFor(difficulty)
        ),
        random = random
    )

    /**
     * Builds the best board this seed can produce for [recipe].
     *
     * Attempts are scored against the recipe rather than accepted blindly, so a silhouette that
     * simply cannot hold a deep tangle — a thin Cross on a small board, say — still yields the
     * most tangled board it can instead of failing.
     */
    fun generate(
        levelId: Int,
        levelNumber: Int,
        recipe: BoardRecipe,
        random: Random
    ): Level {
        var best: Board? = null
        var bestScore = Int.MIN_VALUE

        for (attempt in 0 until MAX_ATTEMPTS) {
            val board = build(recipe, Random(random.nextLong()))
            val score = board.matchScore(recipe)
            if (score > bestScore) {
                bestScore = score
                best = board
            }
            // Good enough is good enough — stop burning time once the recipe is met.
            if (board.meets(recipe)) break
        }

        val board = best ?: build(recipe, random)
        val arrows = board.arrows

        return Level(
            id = levelId,
            levelNumber = levelNumber,
            title = "${recipe.shape.title} ${recipe.difficulty.title}",
            difficulty = recipe.difficulty,
            rows = recipe.size,
            cols = recipe.size,
            maxMistakes = recipe.difficulty.lives,
            // Shuffled only for draw order; solvability does not depend on list order.
            paths = arrows.shuffled(random),
            // A deeper board needs more reading per move, so allow time per wave too.
            targetTimeSeconds = arrows.size * 3 + board.depth * 5
        )
    }

    /** Fallback turn budget for callers that have no level number, such as a shared code. */
    fun maxSegmentsFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 8
        Difficulty.MASTER -> 11
    }

    private fun defaultDepthFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 9
        Difficulty.HARD -> 15
        Difficulty.MASTER -> 22
    }

    private fun defaultOpeningMovesFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 3
        Difficulty.MASTER -> 2
    }

    // ── One build attempt ────────────────────────────────────────────────

    private fun build(recipe: BoardRecipe, random: Random): Board {
        val size = recipe.size
        val board = Board(size, recipe.shape)

        val cells = board.insideCells().toMutableList()

        // Never ask for more corners than the board can physically hold.
        val longest = recipe.maxTurns.coerceIn(2, (size / 2).coerceAtLeast(2))
        val floor = recipe.minTurns.coerceIn(1, longest)

        // Build the dependency chain first, on an empty board where there is room to run it out.
        // Scattering arrows at random and hoping a deep tangle falls out does not work: a chain
        // only grows when a new arrow lands in the lane of the current tip, and a randomly chosen
        // cell almost never does. So each link is placed *into* that lane on purpose.
        // Chain links wind through a few corners in a small footprint. Spending the full turn
        // budget here would make each link sprawl and the chain would run out of board long
        // before it reached its target length; the packing passes below carry the long arrows.
        val linkTurns = longest.coerceAtMost(CHAIN_TURNS)
        var link = 0
        while (link < recipe.targetDepth && board.extendChain(linkTurns, recipe, random)) link++

        for (pass in 0 until PACKING_PASSES) {
            // Early passes build the winding arrows, later ones fill the gaps they leave.
            val turns = when {
                pass <= 1 -> longest
                pass <= 3 -> ((longest + 1) / 2).coerceAtLeast(2)
                pass <= 5 -> 2
                else -> 1
            }
            val required = if (pass <= 1) floor else 1

            cells.shuffle(random)
            for (head in cells) {
                board.tryPlace(head, turns, MAX_RUN, required, recipe, random)
            }
        }

        // Packing leaves the board wide open: an arrow laid down late has nothing in front of it,
        // so it is tappable on move one. Closing those needs an arrow that stands in front of
        // several at once — one that blocks only a single opening replaces it with itself and the
        // count never moves.
        var closing = 0
        while (board.openingMoves > recipe.maxOpeningMoves &&
            closing < MAX_CLOSING_ARROWS &&
            board.closeOpenings(recipe, random)
        ) closing++

        return board
    }

    // ── The board under construction ─────────────────────────────────────

    /**
     * Arrows placed so far, plus the bookkeeping that makes steering possible: which cells are
     * used, which arrows' lanes cross each cell, and how deep the dependency chain currently runs.
     */
    private class Board(val size: Int, shape: BoardShape) {

        val inside = Array(size) { r -> BooleanArray(size) { c -> shape.contains(r, c, size, size) } }

        private val taken = Array(size) { BooleanArray(size) }

        /** Arrows whose exit lane passes through each cell. A body landing here blocks them all. */
        private val laneOwners = Array(size) { Array(size) { mutableListOf<Int>() } }

        /** The cells each arrow will fly through on its way out — where to stand to block it. */
        private val lanes = mutableListOf<List<Point>>()

        val arrows = mutableListOf<PathArrow>()

        /**
         * The longest dependency chain that *starts* at each arrow: one plus the tallest of the
         * arrows it stands in front of.
         *
         * Height rather than wave number is what the scoring needs, and it is also far cheaper.
         * An arrow only ever blocks arrows placed before it, so the set it blocks is fixed the
         * moment it is committed and its height never changes afterwards — no propagation, and
         * the board's peel depth is just the tallest arrow on it.
         */
        private val height = mutableListOf<Int>()

        /** Arrows something stands in front of. The rest are the board's opening moves. */
        private val blocked = HashSet<Int>()

        /**
         * Every cell inside the silhouette.
         *
         * Deliberately not a `buildList`: inside that builder the implicit `MutableList` receiver
         * shadows this class's own `size`, so `0 until size` silently counts up to the length of
         * the list being built — zero — and the board comes back empty.
         */
        fun insideCells(): List<Point> {
            val cells = ArrayList<Point>(size * size)
            for (r in 0 until size) for (c in 0 until size) if (inside[r][c]) cells.add(Point(r, c))
            return cells
        }

        val depth: Int get() = height.maxOrNull() ?: 0

        val openingMoves: Int get() = arrows.indices.count { it !in blocked }

        fun meets(recipe: BoardRecipe) =
            depth >= recipe.targetDepth && openingMoves <= recipe.maxOpeningMoves

        /**
         * How well this attempt matches the recipe. Depth outweighs the opening-move count because
         * a shallow board is easy however few moves it offers, and arrow count breaks ties so a
         * tangled-but-sparse board never beats a tangled-and-full one.
         */
        fun matchScore(recipe: BoardRecipe): Int {
            val depthScore = depth.coerceAtMost(recipe.targetDepth) * 1000
            val openingPenalty = (openingMoves - recipe.maxOpeningMoves).coerceAtLeast(0) * 50
            return depthScore - openingPenalty + arrows.size
        }

        /**
         * Adds one link to the longest chain on the board, and reports whether it managed to.
         *
         * The tip is the tallest arrow — the one that has to leave last of everything below it.
         * Seating a new arrow with its head anywhere in the tip's lane puts that arrow's body in
         * the way, so the tip now has to wait for it too and the chain grows by exactly one. On an
         * empty board the very first call just seats a seed arrow to start from.
         */
        fun extendChain(turns: Int, recipe: BoardRecipe, random: Random): Boolean {
            if (arrows.isEmpty()) {
                for (head in insideCells().shuffled(random)) {
                    if (tryPlace(head, turns, CHAIN_RUN, 1, recipe, random)) return true
                }
                return false
            }

            // The tallest arrow is the chain tip, but its lane may be full. Falling back to the
            // next tallest keeps the chain growing where a single-tip search would give up with
            // most of the board still empty.
            val before = depth
            val tips = height.indices.sortedByDescending { height[it] }.take(TIP_CANDIDATES)
            for (tip in tips) {
                for (cell in lanes[tip].shuffled(random)) {
                    if (taken[cell.r][cell.c]) continue
                    if (tryPlace(cell, turns, CHAIN_RUN, 1, recipe, random) && depth > before) return true
                }
            }
            return false
        }

        /**
         * Seats one arrow where two or more open lanes cross, and reports whether it managed to.
         *
         * Standing in front of several openings at once is the only placement that reduces how
         * many moves the board offers on turn one, since the new arrow is itself open.
         */
        fun closeOpenings(recipe: BoardRecipe, random: Random): Boolean {
            val open = arrows.indices.filter { it !in blocked }
            if (open.size < 2) return false

            // Count how many open lanes cross each free cell, then try the busiest first.
            val crossings = HashMap<Point, Int>()
            for (j in open) {
                for (cell in lanes[j]) {
                    if (!taken[cell.r][cell.c]) crossings[cell] = (crossings[cell] ?: 0) + 1
                }
            }

            val before = openingMoves
            val busiest = crossings.entries
                .filter { it.value >= 2 }
                .sortedByDescending { it.value }
                .take(CLOSING_CANDIDATES)
            for ((cell, _) in busiest) {
                if (tryPlace(cell, CHAIN_TURNS, CHAIN_RUN, 1, recipe, random) &&
                    openingMoves < before
                ) return true
            }
            return false
        }

        /**
         * Tries to seat one arrow with its head on [head], choosing among the directions whose
         * lane is currently clear. Every option is grown into a real tail and scored, because the
         * body is what decides which existing arrows the new arrow ends up blocking.
         *
         * Reports whether an arrow was placed.
         */
        fun tryPlace(
            head: Point,
            turns: Int,
            maxRun: Int,
            requiredCorners: Int,
            recipe: BoardRecipe,
            random: Random
        ): Boolean {
            // An exit lane runs to the board edge whether or not the silhouette does, so cells
            // offered by the chain and closing passes — which walk lanes — can sit outside the
            // shape. The head is a body cell, so seating one there would push an arrow out of
            // the silhouette the level is cut from.
            if (!inside[head.r][head.c]) return false
            if (taken[head.r][head.c]) return false

            var bestPoints: List<Point>? = null
            var bestExit = Direction.UP
            var bestScore = Int.MIN_VALUE

            for (exit in Direction.entries) {
                if (!laneIsClear(head, exit)) continue
                val points = growTail(head, exit, turns, maxRun, random)
                if (points.size < 2) continue
                if (points.size - 1 < requiredCorners) continue

                val score = scoreOf(points, recipe)
                if (score > bestScore) {
                    bestScore = score
                    bestPoints = points
                    bestExit = exit
                }
            }

            commit(bestPoints ?: return false, bestExit)
            return true
        }

        /**
         * Prefers placements that lengthen the longest dependency chain until the recipe's depth
         * is met, then switches to shutting down opening moves.
         *
         * The chain only grows by landing in front of the *tallest* arrow on the board — that is
         * the tip of the longest chain, and standing in front of it adds one more link. Landing in
         * front of an arrow that is already blocked adds a second way to free it and changes
         * nothing about how deep the board is, which is why height and not the count of arrows
         * blocked drives the score.
         */
        private fun scoreOf(points: List<Point>, recipe: BoardRecipe): Int {
            val crossed = lanesCrossedBy(points)
            var tallest = 0
            var openingsClosed = 0
            for (j in crossed) {
                if (height[j] > tallest) tallest = height[j]
                if (j !in blocked) openingsClosed++
            }

            val corners = points.size - 1
            return if (depth < recipe.targetDepth) {
                tallest * 200 + crossed.size * 10 + corners
            } else {
                // Depth is there; now take away the easy openings and pack the board.
                openingsClosed * 200 + crossed.size * 10 + corners
            }
        }

        private fun commit(points: List<Point>, exit: Direction) {
            val index = arrows.size
            val crossed = lanesCrossedBy(points)

            arrows += PathArrow(
                id = index + 1,
                waypoints = points,
                exitDirection = exit,
                colorType = ArrowColorType.fromIndex(index + 1)
            )
            // One taller than the tallest arrow it now stands in front of.
            height += 1 + (crossed.maxOfOrNull { height[it] } ?: 0)
            blocked.addAll(crossed)

            for (cell in cellsOf(points)) taken[cell.r][cell.c] = true

            val head = points.last()
            val lane = mutableListOf<Point>()
            var r = head.r + exit.dr
            var c = head.c + exit.dc
            while (r in 0 until size && c in 0 until size) {
                laneOwners[r][c].add(index)
                lane += Point(r, c)
                r += exit.dr
                c += exit.dc
            }
            lanes += lane
        }

        /** Distinct arrows whose lane any of these body cells sits in. */
        private fun lanesCrossedBy(points: List<Point>): Set<Int> {
            val crossed = mutableSetOf<Int>()
            for (cell in cellsOf(points)) crossed.addAll(laneOwners[cell.r][cell.c])
            return crossed
        }

        /** True when nothing already placed stands between [head] and the edge along [dir]. */
        private fun laneIsClear(head: Point, dir: Direction): Boolean {
            var r = head.r + dir.dr
            var c = head.c + dir.dc
            while (r in 0 until size && c in 0 until size) {
                if (taken[r][c]) return false
                r += dir.dr
                c += dir.dc
            }
            return true
        }

        /**
         * Grows the body backwards from the head, turning perpendicular at each corner so the
         * arrow winds. Returns the waypoints tail-first, matching [PathArrow]'s ordering.
         *
         * Runs are drawn long enough for a corner to be worth having — a winding arrow made of
         * one-cell runs reads as a staircase rather than as a path with real turns.
         */
        private fun growTail(
            head: Point,
            exit: Direction,
            turns: Int,
            maxRun: Int,
            random: Random
        ): List<Point> {
            val body = hashSetOf(head)
            val corners = mutableListOf(head)
            var cursor = head
            var heading = exit.opposite
            // Holding one rotational sense for a while produces spirals and switchbacks instead
            // of the drunk walk an independent coin flip at every corner gives.
            var clockwise = random.nextBoolean()

            for (turn in 0 until turns) {
                val run = random.nextInt(1, maxRun + 1)
                var reached = cursor
                for (step in 0 until run) {
                    val next = reached + heading
                    if (next.r !in 0 until size || next.c !in 0 until size) break
                    if (!inside[next.r][next.c] || taken[next.r][next.c]) break
                    if (!body.add(next)) break
                    reached = next
                }
                if (reached != cursor) {
                    corners += reached
                    cursor = reached
                }
                // Turn even when the run was blocked — the other axis may still have room.
                heading = heading.turn(clockwise)
                if (random.nextInt(TURN_FLIP_ODDS) == 0) clockwise = !clockwise
            }

            return corners.asReversed().toList()
        }

        /** Every grid cell the segments between [points] pass through. */
        private fun cellsOf(points: List<Point>): List<Point> {
            val cells = mutableListOf<Point>()
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val dr = (b.r - a.r).coerceIn(-1, 1)
                val dc = (b.c - a.c).coerceIn(-1, 1)
                var r = a.r
                var c = a.c
                while (true) {
                    cells.add(Point(r, c))
                    if (r == b.r && c == b.c) break
                    r += dr
                    c += dc
                }
            }
            return cells
        }
    }

    private fun Direction.turn(clockwise: Boolean): Direction = when (this) {
        Direction.UP -> if (clockwise) Direction.RIGHT else Direction.LEFT
        Direction.RIGHT -> if (clockwise) Direction.DOWN else Direction.UP
        Direction.DOWN -> if (clockwise) Direction.LEFT else Direction.RIGHT
        Direction.LEFT -> if (clockwise) Direction.UP else Direction.DOWN
    }
}
