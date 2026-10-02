package com.example.arrowpuzzle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.levels.LevelsRepository
import com.example.arrowpuzzle.theme.Chip
import com.example.arrowpuzzle.theme.Gold
import com.example.arrowpuzzle.theme.GoldEmpty
import com.example.arrowpuzzle.theme.Ink
import com.example.arrowpuzzle.theme.InkFaint
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.Screen
import com.example.arrowpuzzle.ui.components.appear
import com.example.arrowpuzzle.ui.components.bounceClick

@Composable
fun LevelSelectScreen(
    onSelectLevel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val unlocked = remember { prefs.highestUnlockedLevel }
    // The campaign is endless, so offer a window that follows the player forward.
    val levels = remember { (1..(unlocked + LevelsRepository.LEVELS_AHEAD)).toList() }
    // Open on the level to play next, a couple of rows down, rather than at level 1 — on an
    // endless campaign the top of the list is somewhere the player left long ago.
    val firstShown = (unlocked - 9).coerceAtLeast(0)
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = firstShown)

    Column(
        modifier = modifier.fillMaxSize().background(Screen).systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.bounceClick(onClick = onBack).size(44.dp).clip(CircleShape).background(Chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronLeft, "Back", tint = Ink, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "Select Level",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(44.dp))
        }

        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(76.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(levels, key = { _, number -> number }) { index, number ->
                LevelTile(
                    number = number,
                    stars = prefs.getLevelStars(number),
                    difficultyColor = LevelsRepository.getDifficultyForLevel(number).badgeColor,
                    locked = number > unlocked,
                    current = number == unlocked,
                    // Tiles on the opening screen cascade in; ones scrolled to later just rise.
                    modifier = Modifier.appear((index - firstShown).coerceIn(0, 16)),
                    onClick = { onSelectLevel(number) }
                )
            }
        }
    }
}

@Composable
private fun LevelTile(
    number: Int,
    stars: Int,
    difficultyColor: Color,
    locked: Boolean,
    current: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ink = if (current) Color.White else Ink
    Column(
        modifier = modifier
            .bounceClick(enabled = !locked, onClick = onClick)
            .aspectRatio(0.88f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    current -> Accent
                    locked -> Chip.copy(alpha = 0.5f)
                    else -> Chip
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (locked) {
            Icon(Icons.Default.Lock, "Locked", tint = InkFaint, modifier = Modifier.size(22.dp))
        } else {
            Text("$number", style = MaterialTheme.typography.titleLarge, color = ink)
            Spacer(Modifier.height(4.dp))
            Row {
                repeat(3) { i ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = when {
                            i < stars -> Gold
                            current -> Color.White.copy(alpha = 0.35f)
                            else -> GoldEmpty
                        },
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.size(width = 18.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(if (current) Color.White else difficultyColor)
            )
        }
    }
}
