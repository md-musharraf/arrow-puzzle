package com.example.arrowpuzzle

import androidx.navigation3.runtime.NavKey
import com.example.arrowpuzzle.data.model.Difficulty
import kotlinx.serialization.Serializable

@Serializable
data object MainMenuKey : NavKey

@Serializable
data object LevelSelectKey : NavKey

@Serializable
data object EndlessSetupKey : NavKey

@Serializable
data object PuzzleCodeKey : NavKey

@Serializable
data class GamePlayKey(
    val levelNumber: Int = 1,
    val isEndless: Boolean = false,
    val difficultyName: String = Difficulty.EASY.name,
    val gridSize: Int = 4
) : NavKey
