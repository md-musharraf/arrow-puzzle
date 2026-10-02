package com.example.arrowpuzzle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.ui.screens.EndlessModeScreen
import com.example.arrowpuzzle.ui.screens.GamePlayScreen
import com.example.arrowpuzzle.ui.screens.LevelSelectScreen
import com.example.arrowpuzzle.ui.screens.MainMenuScreen
import com.example.arrowpuzzle.ui.screens.PuzzleCodeScreen
import com.example.arrowpuzzle.ui.viewmodel.GameViewModel

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(MainMenuKey)
    val gameViewModel: GameViewModel = viewModel()
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Forward pushes the new screen in from the right while the old one recedes; back
        // reverses it, so the direction of travel always matches the direction of the gesture.
        transitionSpec = {
            (slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                fadeIn(tween(NAV_MS))) togetherWith
                (scaleOut(tween(NAV_MS), targetScale = 0.94f) + fadeOut(tween(NAV_MS / 2)))
        },
        popTransitionSpec = {
            (scaleIn(tween(NAV_MS), initialScale = 0.94f) + fadeIn(tween(NAV_MS))) togetherWith
                (slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 3 } +
                    fadeOut(tween(NAV_MS / 2)))
        },
        predictivePopTransitionSpec = {
            (scaleIn(tween(NAV_MS), initialScale = 0.94f) + fadeIn(tween(NAV_MS))) togetherWith
                (slideOutHorizontally(tween(NAV_MS)) { it / 3 } + fadeOut(tween(NAV_MS / 2)))
        },
        entryProvider = entryProvider {
            entry<MainMenuKey> {
                MainMenuScreen(
                    onPlayCampaign = {
                        val targetLevel = prefs.highestUnlockedLevel
                        gameViewModel.loadCampaignLevel(targetLevel)
                        backStack.add(GamePlayKey(levelNumber = targetLevel, isEndless = false))
                    },
                    onLevelSelect = {
                        backStack.add(LevelSelectKey)
                    },
                    onEndlessMode = {
                        backStack.add(EndlessSetupKey)
                    },
                    onDailyPuzzle = {
                        gameViewModel.loadDailyPuzzle()
                        backStack.add(GamePlayKey())
                    },
                    onPuzzleCode = {
                        backStack.add(PuzzleCodeKey)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<LevelSelectKey> {
                LevelSelectScreen(
                    onSelectLevel = { levelNum ->
                        gameViewModel.loadCampaignLevel(levelNum)
                        backStack.add(GamePlayKey(levelNumber = levelNum, isEndless = false))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<EndlessSetupKey> {
                EndlessModeScreen(
                    onStartEndless = { diff ->
                        gameViewModel.startEndlessRun(diff)
                        backStack.add(
                            GamePlayKey(
                                levelNumber = 1,
                                isEndless = true,
                                difficultyName = diff.name,
                                gridSize = diff.minGridSize
                            )
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<PuzzleCodeKey> {
                PuzzleCodeScreen(
                    onPlayPuzzle = { puzzle ->
                        gameViewModel.loadSharedPuzzle(puzzle)
                        backStack.add(GamePlayKey())
                    },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<GamePlayKey> {
                GamePlayScreen(
                    viewModel = gameViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

private const val NAV_MS = 340
