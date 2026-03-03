package com.rrimal.notetaker.ui.screens.pomodoro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Settings card for Pomodoro timer durations.
 * Allows user to customize focus duration, short break, and long break durations.
 */
@Composable
fun PomodoroSettingsCard(
    pomodoroDuration: Int,
    shortBreakDuration: Int,
    longBreakDuration: Int,
    onPomodoroDurationChange: (Int) -> Unit,
    onShortBreakDurationChange: (Int) -> Unit,
    onLongBreakDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Pomodoro Timer Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Focus duration slider
            DurationSlider(
                label = "Focus Duration",
                value = pomodoroDuration,
                onValueChange = onPomodoroDurationChange,
                icon = "🍅"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Short break slider
            DurationSlider(
                label = "Short Break",
                value = shortBreakDuration,
                onValueChange = onShortBreakDurationChange,
                icon = "☕"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Long break slider (future use)
            DurationSlider(
                label = "Long Break",
                value = longBreakDuration,
                onValueChange = onLongBreakDurationChange,
                icon = "🌴"
            )
        }
    }
}

/**
 * Slider for selecting duration in minutes (1-60 range).
 */
@Composable
private fun DurationSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    icon: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = icon,
                    fontSize = 20.sp
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "$value min",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..60f,
            steps = 58, // 60 values - 2 endpoints = 58 steps
            modifier = Modifier.fillMaxWidth()
        )
    }
}
