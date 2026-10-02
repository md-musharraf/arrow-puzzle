package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Direction(val dr: Int, val dc: Int, val angleDegrees: Float) {
    UP(-1, 0, 270f),
    RIGHT(0, 1, 0f),
    DOWN(1, 0, 90f),
    LEFT(0, -1, 180f);

    val isHorizontal: Boolean
        get() = this == LEFT || this == RIGHT

    val isVertical: Boolean
        get() = this == UP || this == DOWN

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}
