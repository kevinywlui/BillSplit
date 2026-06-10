package com.kevinywlui.billsplit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinywlui.billsplit.model.Person

val avatarColors = listOf(
    Color(0xFF64748B), // Slate
    Color(0xFF6366F1), // Indigo
    Color(0xFF3B82F6), // Blue
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Emerald
    Color(0xFF84CC16), // Lime
    Color(0xFFF59E0B), // Amber
    Color(0xFFF97316), // Orange
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Violet
)

@Composable
fun PersonAvatar(person: Person, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = avatarColors[person.avatarColorIndex % avatarColors.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = person.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}
