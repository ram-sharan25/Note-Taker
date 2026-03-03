package com.rrimal.notetaker.ui.screens.pomodoro

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrimal.notetaker.pomodoro.PomodoroTimerState
import com.rrimal.notetaker.ui.theme.*

/**
 * Fullscreen Pomodoro timer screen with:
 * - Green theme for focus timer
 * - Blue theme for break timer
 * - Overlay with task info
 * - Large countdown display
 * - Circular progress indicator
 * - Pause/Resume/Stop controls
 * - Screen always-on (KEEP_SCREEN_ON flag)
 */
@Composable
fun PomodoroTimerScreen(
    state: PomodoroTimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMinimize: () -> Unit = {},
    onTaskClick: () -> Unit = {}
) {
    // Keep screen on while timer active
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Color theme based on timer type
    val backgroundColor = if (state.isBreak) {
        Blue40.copy(alpha = 0.15f) // Blue tint for break
    } else {
        Green40.copy(alpha = 0.15f) // Green tint for focus
    }

    val accentColor = if (state.isBreak) {
        Blue80 // Blue for break
    } else {
        Green80 // Green for focus
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkPurple10) // Base dark background
    ) {
        // Colored overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        )

        // Centered timer display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular progress indicator
            CircularProgressIndicator(
                progress = state.progress,
                color = accentColor,
                modifier = Modifier.size(280.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Countdown text
            Text(
                text = state.formattedTime,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer label
            Text(
                text = when {
                    state.isPaused -> "⏸ Paused"
                    state.isBreak -> "☕ Break Time"
                    else -> "🍅 Focus Time"
                },
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Task info overlay at top
        if (state.taskTitle != null) {
            TaskInfoOverlay(
                title = state.taskTitle,
                tags = state.taskTags,
                priority = state.taskPriority,
                onClick = onTaskClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Minimize button — top-right corner
        IconButton(
            onClick = onMinimize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize timer",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }

        // Controls at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause/Resume button
            FilledTonalButton(
                onClick = if (state.isPaused) onResume else onPause,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkPurple25
                )
            ) {
                Text(
                    text = if (state.isPaused) "▶ Resume" else "⏸ Pause",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stop button
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "⏹ Stop",
                    fontSize = 18.sp
                )
            }
        }

        // Completion dialog removed - now handled in AgendaScreen
    }
}

/**
 * Circular progress indicator with custom drawing.
 */
@Composable
private fun CircularProgressIndicator(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progress"
    )

    Canvas(modifier = modifier) {
        val size = size.minDimension
        val strokeWidth = 12.dp.toPx()

        // Background circle (track)
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(
                x = (this.size.width - size) / 2f,
                y = (this.size.height - size) / 2f
            ),
            size = Size(size, size),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = Offset(
                x = (this.size.width - size) / 2f,
                y = (this.size.height - size) / 2f
            ),
            size = Size(size, size),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
