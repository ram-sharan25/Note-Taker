package com.rrimal.notetaker.ui.orgview.utils

import androidx.compose.ui.graphics.Color

object OrgTheme {
    /**
     * Level-based colors for headlines (inspired by Orgro/Emacs)
     */
    fun levelColor(level: Int): Color {
        return when (level % 8) {
            1 -> Color(0xFF4285F4) // Blue
            2 -> Color(0xFF34A853) // Green
            3 -> Color(0xFFFBBC04) // Yellow
            4 -> Color(0xFFEA4335) // Red
            5 -> Color(0xFF9C27B0) // Purple
            6 -> Color(0xFF00BCD4) // Cyan
            7 -> Color(0xFFE91E63) // Pink
            0 -> Color(0xFFFF9800) // Orange
            else -> Color.Gray
        }
    }

    /**
     * Colors for TODO states
     */
    fun todoColors(state: String): Pair<Color, Color> {
        return when (state.uppercase()) {
            "TODO" -> Color(0xFFEA4335) to Color.White          // Red
            "DONE" -> Color(0xFF34A853) to Color.White          // Green
            "IN-PROGRESS", "STARTED" -> Color(0xFFFBBC04) to Color.Black // Yellow
            "WAITING" -> Color(0xFFFF6D00) to Color.White      // Orange
            "CANCELLED", "CANCELED" -> Color.Gray to Color.White
            else -> Color.LightGray to Color.Black
        }
    }

    /**
     * Colors for planning lines
     */
    object PlanningColors {
        val closed = Color.Gray
        val scheduled = Color(0xFF34A853) // Green
        val deadline = Color(0xFFEA4335)  // Red
    }

    /**
     * Colors for syntax highlighting
     */
    object SyntaxColors {
        val bold = Color(0xFFE91E63)      // Pinkish
        val italic = Color(0xFF9C27B0)    // Purple
        val code = Color(0xFF34A853)      // Green
        val link = Color(0xFF4285F4)      // Blue
        val verbatim = Color(0xFF795548)  // Brown
        val comment = Color(0xFF757575)   // Gray
    }
}
