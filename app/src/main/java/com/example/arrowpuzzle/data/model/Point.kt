package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Point(val r: Int, val c: Int) {
    operator fun plus(dir: Direction): Point = Point(r + dir.dr, c + dir.dc)
}
