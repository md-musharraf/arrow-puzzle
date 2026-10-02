package com.example.arrowpuzzle.data.model

import kotlinx.serialization.Serializable

/**
 * Retained as level metadata. The board renders every arrow in a single ink colour
 * (see [com.example.arrowpuzzle.theme.Ink]); colour is reserved for state feedback
 * — hinted, blocked, blocking — so the maze stays readable when paths overlap.
 */
@Serializable
enum class ArrowColorType {
    CYAN, PURPLE, AMBER, EMERALD, CORAL, ROSE, BLUE, LIME;

    companion object {
        fun fromIndex(index: Int): ArrowColorType =
            entries[((index % entries.size) + entries.size) % entries.size]
    }
}
