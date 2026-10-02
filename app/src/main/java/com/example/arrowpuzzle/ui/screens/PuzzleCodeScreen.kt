package com.example.arrowpuzzle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowpuzzle.data.model.PuzzleSeed
import com.example.arrowpuzzle.theme.Accent
import com.example.arrowpuzzle.theme.Chip
import com.example.arrowpuzzle.theme.Danger
import com.example.arrowpuzzle.theme.Ink
import com.example.arrowpuzzle.theme.InkFaint
import com.example.arrowpuzzle.theme.InkMuted
import com.example.arrowpuzzle.theme.Screen
import com.example.arrowpuzzle.ui.components.bounceClick

@Composable
fun PuzzleCodeScreen(
    onPlayPuzzle: (PuzzleSeed) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var typed by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val decoded = remember(typed) { PuzzleSeed.decode(typed) }

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
                text = "Puzzle Code",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Every board is just a short code. Type one in to play the exact same puzzle " +
                "a friend played — no download, no level pack.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = {
                typed = it.uppercase()
                showError = false
            },
            label = { Text("Enter code") },
            placeholder = { Text("XXXX-XXXX") },
            singleLine = true,
            isError = showError,
            textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = InkFaint,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (showError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "That code isn't valid — check for a typo.",
                style = MaterialTheme.typography.bodySmall,
                color = Danger
            )
        } else if (decoded != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${decoded.shape.title} board, ${decoded.size}x${decoded.size}, " +
                    "${decoded.difficulty.title}",
                style = MaterialTheme.typography.bodySmall,
                color = decoded.difficulty.badgeColor
            )
        }

        Spacer(Modifier.height(20.dp))
        CodeButton(
            label = "Play this puzzle",
            icon = Icons.Default.PlayArrow,
            background = if (decoded != null) Accent else Chip,
            content = if (decoded != null) Color.White else InkFaint
        ) {
            if (decoded != null) onPlayPuzzle(decoded) else showError = true
        }

        Spacer(Modifier.height(28.dp))
        Text("Or", style = MaterialTheme.typography.labelLarge, color = InkFaint)
        Spacer(Modifier.height(12.dp))
        CodeButton(
            label = "Surprise me",
            icon = Icons.Default.Casino,
            background = Chip,
            content = Ink
        ) {
            onPlayPuzzle(PuzzleSeed.random())
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Clear any puzzle and you can share its code from the results screen.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint
        )
    }
}

@Composable
private fun CodeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    content: Color,
    onClick: () -> Unit
) {
    // The play button lights up the moment a typed code decodes.
    val fill by animateColorAsState(background, label = "codeFill")
    val ink by animateColorAsState(content, label = "codeInk")
    Row(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = ink, fontWeight = FontWeight.Bold)
    }
}
