package com.example.arrowpuzzle.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(
    val title: String,
    val subtitle: String,
    val badgeColorHex: Long,
    val minGridSize: Int,
    val maxGridSize: Int,
    /** Mistakes allowed. Boards cannot get much denser, so the margin for error tightens instead. */
    val lives: Int
) {
    EASY("Easy", "Short arrows, room to breathe", 0xFF16A34A, 9, 10, 5),
    MEDIUM("Medium", "Packed boards, longer tails", 0xFFF59E0B, 12, 13, 5),
    HARD("Hard", "Deep tangles, few free lanes", 0xFFEF4444, 15, 16, 4),
    MASTER("Master", "Big boards, one slip is costly", 0xFF8B5CF6, 18, 20, 3);

    val badgeColor: Color
        get() = Color(badgeColorHex)
}
