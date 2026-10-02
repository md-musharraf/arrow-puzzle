package com.example.arrowpuzzle.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Ink,
    onSecondary = Color.White,
    background = Screen,
    onBackground = Ink,
    surface = Screen,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = InkMuted,
    outline = Divider,
    error = Danger,
    onError = Color.White
)

@Composable
fun ArrowPuzzleTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(colorScheme = LightScheme, typography = Typography, content = content)
}
