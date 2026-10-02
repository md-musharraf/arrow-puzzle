package com.example.arrowpuzzle.data.model

import kotlin.random.Random

/**
 * Everything needed to rebuild one exact board.
 *
 * A puzzle here is a pure function of these five fields, so a board can travel as a nine
 * character code instead of as level data — which is what makes shared puzzles and a worldwide
 * daily possible without a server.
 *
 * The code packs 35 bits of payload plus a 5 bit checksum into 8 Crockford base-32 characters.
 * Crockford's alphabet drops I, L, O and U, and decoding folds the look-alikes back, so a code
 * read off a screen and typed by hand still resolves.
 */
data class PuzzleSeed(
    val seed: Int,
    val size: Int,
    val shape: BoardShape,
    val difficulty: Difficulty,
    val turns: Int
) {
    fun encode(): String {
        val payload = (seed.toLong() and SEED_MASK) or
            ((size - MIN_SIZE).toLong() shl 20) or
            (shape.ordinal.toLong() shl 25) or
            (difficulty.ordinal.toLong() shl 29) or
            ((turns - MIN_TURNS).toLong() shl 31)
        val value = payload or (checksum(payload).toLong() shl 35)

        return buildString {
            for (i in 7 downTo 0) {
                append(ALPHABET[((value shr (i * 5)) and 0x1FL).toInt()])
                if (i == 4) append('-')
            }
        }
    }

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val SEED_MASK = 0xFFFFFL
        private const val PAYLOAD_MASK = 0x7FFFFFFFFL
        private const val MIN_SIZE = 8
        private const val MIN_TURNS = 2
        private const val SEED_RANGE = 1 shl 20

        /** Returns null for anything that is not a well-formed, checksum-clean code. */
        fun decode(text: String): PuzzleSeed? {
            val digits = text.uppercase()
                .filter { !it.isWhitespace() && it != '-' }
                .map { if (it == 'O') '0' else if (it == 'I' || it == 'L') '1' else it }
            if (digits.size != 8) return null

            var value = 0L
            for (ch in digits) {
                val index = ALPHABET.indexOf(ch)
                if (index < 0) return null
                value = (value shl 5) or index.toLong()
            }

            val payload = value and PAYLOAD_MASK
            if (((value shr 35) and 0x1FL).toInt() != checksum(payload)) return null

            val shapeOrdinal = ((payload shr 25) and 0xFL).toInt()
            if (shapeOrdinal >= BoardShape.entries.size) return null

            return PuzzleSeed(
                seed = (payload and SEED_MASK).toInt(),
                size = MIN_SIZE + ((payload shr 20) and 0x1FL).toInt(),
                shape = BoardShape.entries[shapeOrdinal],
                difficulty = Difficulty.entries[((payload shr 29) and 0x3L).toInt()],
                turns = MIN_TURNS + ((payload shr 31) and 0xFL).toInt()
            )
        }

        /**
         * Days since the epoch, in UTC. The daily board turns over at the same instant
         * everywhere, so two people comparing times are always on the same puzzle.
         */
        fun today(): Long = System.currentTimeMillis() / 86_400_000L

        /** The one board everybody in the world gets on [day]. */
        fun forDay(day: Long): PuzzleSeed {
            val random = Random(day * 6364136223846793005L + 1442695040888963407L)
            return PuzzleSeed(
                seed = random.nextInt(SEED_RANGE),
                size = 14 + day.mod(6),
                shape = BoardShape.entries[day.mod(BoardShape.entries.size)],
                difficulty = Difficulty.HARD,
                turns = 5 + day.mod(3)
            )
        }

        fun random(random: Random = Random.Default) = PuzzleSeed(
            seed = random.nextInt(SEED_RANGE),
            size = random.nextInt(12, 22),
            shape = BoardShape.entries.random(random),
            difficulty = Difficulty.entries.random(random),
            turns = random.nextInt(3, 9)
        )

        private fun checksum(payload: Long): Int {
            var x = payload
            x = x xor (x shr 17)
            x = x xor (x shr 29)
            x = x xor (x shr 7)
            return (x and 0x1FL).toInt()
        }
    }
}
