package com.example.arrowpuzzle.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.model.Difficulty
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.Chip
import com.example.arrowpuzzle.theme.Ink
import com.example.arrowpuzzle.theme.InkMuted
import com.example.arrowpuzzle.theme.Screen
import com.example.arrowpuzzle.ui.components.appear
import com.example.arrowpuzzle.ui.components.bounceClick

@Composable
fun EndlessModeScreen(
    onStartEndless: (Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Screen)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.bounceClick(onClick = onBack).size(44.dp).clip(CircleShape).background(Chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronLeft, "Back", tint = Ink, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "Endless Mode",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .appear(0)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Chip)
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Best streak", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Text("${prefs.endlessHighScore}", style = MaterialTheme.typography.displayLarge, color = Ink)
            }
            Text(
                text = "Every board you clear makes\nthe next one harder.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Difficulty", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(12.dp))

        Difficulty.entries.forEachIndexed { index, option ->
            DifficultyRow(
                option = option,
                selected = option == difficulty,
                modifier = Modifier.appear(index + 1),
                onClick = { difficulty = option }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .appear(Difficulty.entries.size + 1)
                .bounceClick { onStartEndless(difficulty) }
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Accent),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Start Run",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DifficultyRow(
    option: Difficulty,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fill by animateColorAsState(
        if (selected) option.badgeColor.copy(alpha = 0.10f) else Chip,
        label = "rowFill"
    )
    val edge by animateColorAsState(
        if (selected) option.badgeColor else Color.Transparent,
        label = "rowEdge"
    )
    Row(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(width = 2.dp, color = edge, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(option.badgeColor))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Text(
            // The starting board. It grows from here as the run goes on.
            text = "from ${option.minGridSize}x${option.minGridSize}",
            style = MaterialTheme.typography.labelLarge,
            color = InkMuted
        )
    }
}
