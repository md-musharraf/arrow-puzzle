package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Level(
    val id: Int,
    val levelNumber: Int,
    val title: String,
    val difficulty: Difficulty,
    val rows: Int,
    val cols: Int,
    val maxMistakes: Int = 5,
    val paths: List<PathArrow>,
    val targetTimeSeconds: Int = 60
) {
    val totalPaths: Int
        get() = paths.count { !it.isObstacle }
}
