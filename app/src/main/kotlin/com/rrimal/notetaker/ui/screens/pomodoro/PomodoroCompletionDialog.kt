package com.rrimal.notetaker.ui.screens.pomodoro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrimal.notetaker.pomodoro.PomodoroCompletionAction

/**
 * Dialog shown when Pomodoro timer completes.
 * Offers 4 actions: Start Break, Another Pomodoro, Mark DONE, or Cancel.
 */
@Composable
fun PomodoroCompletionDialog(
    isBreak: Boolean,
    taskTitle: String?,
    onAction: (PomodoroCompletionAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onAction(PomodoroCompletionAction.CANCEL) },
        title = {
            Text(
                text = if (isBreak) "☕ Break Complete!" else "🍅 Pomodoro Complete!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (taskTitle != null) {
                    Text(
                        text = "Task: $taskTitle",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text(
                    text = if (isBreak) {
                        "Time to focus! What would you like to do next?"
                    } else {
                        "Great work! What would you like to do next?"
                    },
                    fontSize = 16.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary action: Start Break / Another Pomodoro
                Button(
                    onClick = {
                        if (isBreak) {
                            onAction(PomodoroCompletionAction.ANOTHER_POMODORO)
                        } else {
                            onAction(PomodoroCompletionAction.START_BREAK)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBreak) "▶ Start Another Pomodoro" else "☕ Start Break",
                        fontSize = 16.sp
                    )
                }

                // Secondary action: Another Pomodoro / Mark DONE
                FilledTonalButton(
                    onClick = {
                        if (isBreak) {
                            onAction(PomodoroCompletionAction.MARK_DONE)
                        } else {
                            onAction(PomodoroCompletionAction.ANOTHER_POMODORO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBreak) "✓ Mark Task DONE" else "🍅 Another Pomodoro",
                        fontSize = 16.sp
                    )
                }

                // Tertiary action: Mark DONE (if focus) / Skip (if break)
                if (!isBreak) {
                    OutlinedButton(
                        onClick = { onAction(PomodoroCompletionAction.MARK_DONE) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Mark Task DONE",
                            fontSize = 16.sp
                        )
                    }
                }

                // Cancel action
                TextButton(
                    onClick = { onAction(PomodoroCompletionAction.CANCEL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    )
}
