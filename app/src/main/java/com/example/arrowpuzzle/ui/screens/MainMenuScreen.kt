package com.example.arrowpuzzle.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.arrowpuzzle.data.PreferencesManager
import com.example.arrowpuzzle.data.model.PuzzleSeed
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.Chip
import com.example.arrowpuzzle.theme.Gold
import com.example.arrowpuzzle.theme.Ink
import com.example.arrowpuzzle.theme.InkMuted
import com.example.arrowpuzzle.theme.Screen
import com.example.arrowpuzzle.theme.InkFaint
import com.example.arrowpuzzle.theme.Success
import com.example.arrowpuzzle.ui.components.appear
import com.example.arrowpuzzle.ui.components.bounceClick

@Composable
fun MainMenuScreen(
    onPlayCampaign: () -> Unit,
    onLevelSelect: () -> Unit,
    onEndlessMode: () -> Unit,
    onDailyPuzzle: () -> Unit,
    onPuzzleCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val nextLevel = remember { prefs.highestUnlockedLevel }
    val stars = remember { prefs.totalStars }
    val today = remember { PuzzleSeed.today() }
    val dailyDone = remember { prefs.isDailySolved(today) }
    val streak = remember { prefs.dailyStreak }
    var sound by remember { mutableStateOf(prefs.isSoundEnabled) }
    var haptics by remember { mutableStateOf(prefs.isHapticsEnabled) }

    Box(modifier = modifier.fillMaxSize().background(Screen).systemBarsPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoMark(modifier = Modifier.size(120.dp))

        Spacer(Modifier.height(14.dp))
        Text(
            "Arrow Escape",
            style = MaterialTheme.typography.displayLarge,
            color = Ink,
            modifier = Modifier.appear(1)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap an arrow to send it off the board.\nIt only leaves if its lane is clear.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2)
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.appear(3)) {
            StatChip("$stars stars", Gold)
            if (streak > 0) {
                StatChip("$streak day streak", Accent, Icons.Default.LocalFireDepartment)
            }
        }

        Spacer(Modifier.height(28.dp))
        MenuButton(
            icon = Icons.Default.PlayArrow,
            label = if (nextLevel > 1) "Continue · Level $nextLevel" else "Play",
            background = Accent,
            content = Color.White,
            modifier = Modifier.appear(4),
            onClick = onPlayCampaign
        )
        Spacer(Modifier.height(10.dp))
        MenuButton(
            icon = Icons.Default.CalendarMonth,
            label = if (dailyDone) "Daily Puzzle · solved" else "Daily Puzzle",
            background = if (dailyDone) Chip else Success,
            content = if (dailyDone) InkMuted else Color.White,
            modifier = Modifier.appear(5),
            onClick = onDailyPuzzle
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().appear(6),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallButton(Icons.Default.Apps, "Levels", Modifier.weight(1f), onLevelSelect)
            SmallButton(Icons.Default.AllInclusive, "Endless", Modifier.weight(1f), onEndlessMode)
            SmallButton(Icons.Default.Tag, "Code", Modifier.weight(1f), onPuzzleCode)
        }
    }

    Row(
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).appear(7),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingToggle(
            on = sound,
            icon = if (sound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            description = if (sound) "Sound on" else "Sound off"
        ) {
            sound = !sound
            prefs.isSoundEnabled = sound
        }
        SettingToggle(
            on = haptics,
            icon = Icons.Default.Vibration,
            description = if (haptics) "Vibration on" else "Vibration off"
        ) {
            haptics = !haptics
            prefs.isHapticsEnabled = haptics
        }
    }
    }
}

@Composable
private fun SettingToggle(on: Boolean, icon: ImageVector, description: String, onToggle: () -> Unit) {
    val tint by animateColorAsState(if (on) Ink else InkFaint, label = "toggleTint")
    Box(
        modifier = Modifier
            .bounceClick(onClick = onToggle)
            .size(42.dp)
            .clip(CircleShape)
            .background(Chip),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatChip(text: String, tint: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Chip)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = InkMuted)
    }
}

@Composable
private fun RowScope.SmallButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Chip),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun MenuButton(
    icon: ImageVector,
    label: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, fontWeight = FontWeight.Bold)
    }
}

/**
 * Three interlocking arrows drawn in the same ink language as the board. On first show each one
 * draws itself in tail-first, a beat after the last, and its head snaps on as the line arrives.
 */
@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(modifier = modifier) {
        val u = size.minDimension / 10f
        val stroke = Stroke(width = u * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun arrow(index: Int, points: List<Offset>, color: Color) {
            val p = ((draw.value - index * 0.18f) / 0.64f).coerceIn(0f, 1f)
            if (p <= 0f) return
            measure.setPath(
                Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                },
                false
            )
            segment.reset()
            measure.getSegment(0f, measure.length * p, segment, true)
            drawPath(path = segment, color = color, style = stroke)

            val grow = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
            if (grow <= 0f) return
            val tip = points.last()
            val previous = points[points.size - 2]
            val dx = if (tip.x == previous.x) 0f else if (tip.x > previous.x) 1f else -1f
            val dy = if (tip.y == previous.y) 0f else if (tip.y > previous.y) 1f else -1f
            val h = u * 1.2f * grow
            drawPath(
                path = Path().apply {
                    moveTo(tip.x - dy * h, tip.y + dx * h)
                    lineTo(tip.x + dx * h * 1.2f, tip.y + dy * h * 1.2f)
                    lineTo(tip.x + dy * h, tip.y - dx * h)
                    close()
                },
                color = color
            )
        }

        arrow(0, listOf(Offset(u * 1.5f, u * 7.5f), Offset(u * 1.5f, u * 2.5f), Offset(u * 6.5f, u * 2.5f)), Ink)
        arrow(1, listOf(Offset(u * 8.5f, u * 1.5f), Offset(u * 8.5f, u * 5f), Offset(u * 4f, u * 5f)), Accent)
        arrow(2, listOf(Offset(u * 2.5f, u * 9f), Offset(u * 7f, u * 9f), Offset(u * 7f, u * 6.8f)), Ink)
    }
}
