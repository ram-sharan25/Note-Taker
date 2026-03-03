package com.rrimal.notetaker.ui.screens.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.data.api.TogglProject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StateSelectionDialog(
    noteId: Long,
    currentState: String,
    togglProjects: List<TogglProject>,
    needsProjectSelection: Boolean,
    isPomodoroActive: Boolean,
    onStateSelected: (state: String, projectId: Long?) -> Unit,       // Changes state + closes dialog
    onStateChangedToInProgress: (projectId: Long?) -> Unit,           // Changes state to IN-PROGRESS, keeps dialog open
    onStartPomodoro: (noteId: Long) -> Unit,                          // Starts timer + closes dialog
    onDismiss: () -> Unit
) {
    val allStates = listOf("TODO", "IN-PROGRESS", "WAITING", "HOLD", "DONE", "CANCELLED")

    // Whether IN-PROGRESS was just selected (show Pomodoro prompt)
    var showPomodoroPrompt by remember { mutableStateOf(currentState == "IN-PROGRESS") }

    // Local state for project picker (Toggl)
    var showProjectPicker by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<TogglProject?>(null) }

    // Handle chip tap
    fun handleStateClick(state: String) {
        if (state == "IN-PROGRESS") {
            if (needsProjectSelection && togglProjects.isNotEmpty()) {
                showProjectPicker = true
            } else {
                // Apply IN-PROGRESS change, stay open, show Pomodoro prompt
                onStateChangedToInProgress(null)
                showPomodoroPrompt = true
            }
        } else {
            // Any other state: apply immediately and close dialog
            onStateSelected(state, null)
        }
    }

    // Effective current state for chip highlighting
    val displayState = if (showPomodoroPrompt) "IN-PROGRESS" else currentState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    showProjectPicker -> "Select Project"
                    showPomodoroPrompt -> "IN-PROGRESS"
                    else -> "Change Status"
                }
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .heightIn(min = 200.dp, max = 600.dp)
            ) {
                Column {
                    if (showProjectPicker) {
                        // ── PROJECT PICKER VIEW (Toggl) ──────────────────────
                        Text(
                            text = "Select a project for this timer:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            togglProjects.forEach { project ->
                                Surface(
                                    modifier = Modifier.padding(0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (project == selectedProject)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (project == selectedProject) 4.dp else 0.dp,
                                    onClick = { selectedProject = project }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        project.color?.let { colorHex ->
                                            val parsedColor = remember(colorHex) {
                                                try { Color(android.graphics.Color.parseColor(colorHex)) }
                                                catch (e: Exception) { null }
                                            }
                                            parsedColor?.let { color ->
                                                Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                                            }
                                        }
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (project == selectedProject) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (project == selectedProject) {
                                            Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ── STATUS SELECTION VIEW ────────────────────────────
                        Text(
                            text = "Current: $displayState",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allStates.forEach { state ->
                                val isSelected = state == displayState
                                Surface(
                                    modifier = Modifier.padding(0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (state) {
                                        "TODO"        -> MaterialTheme.colorScheme.primary
                                        "IN-PROGRESS" -> MaterialTheme.colorScheme.tertiary
                                        "WAITING"     -> MaterialTheme.colorScheme.secondary
                                        "HOLD"        -> MaterialTheme.colorScheme.tertiaryContainer
                                        "DONE"        -> MaterialTheme.colorScheme.secondaryContainer
                                        "CANCELLED"   -> MaterialTheme.colorScheme.error
                                        else          -> MaterialTheme.colorScheme.primary
                                    },
                                    tonalElevation = if (isSelected) 8.dp else 2.dp,
                                    shadowElevation = if (isSelected) 4.dp else 0.dp,
                                    onClick = { handleStateClick(state) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = state,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = when (state) {
                                                "TODO"        -> MaterialTheme.colorScheme.onPrimary
                                                "IN-PROGRESS" -> MaterialTheme.colorScheme.onTertiary
                                                "WAITING"     -> MaterialTheme.colorScheme.onSecondary
                                                "HOLD"        -> MaterialTheme.colorScheme.onTertiaryContainer
                                                "DONE"        -> MaterialTheme.colorScheme.onSecondaryContainer
                                                "CANCELLED"   -> MaterialTheme.colorScheme.onError
                                                else          -> MaterialTheme.colorScheme.onPrimary
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "✓",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = when (state) {
                                                    "TODO"        -> MaterialTheme.colorScheme.onPrimary
                                                    "IN-PROGRESS" -> MaterialTheme.colorScheme.onTertiary
                                                    "WAITING"     -> MaterialTheme.colorScheme.onSecondary
                                                    "HOLD"        -> MaterialTheme.colorScheme.onTertiaryContainer
                                                    "DONE"        -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    "CANCELLED"   -> MaterialTheme.colorScheme.onError
                                                    else          -> MaterialTheme.colorScheme.onPrimary
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── POMODORO PROMPT (shown after IN-PROGRESS selected) ──
                        if (showPomodoroPrompt && !isPomodoroActive) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    onStartPomodoro(noteId)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Pomodoro")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                showProjectPicker -> {
                    TextButton(
                        onClick = {
                            selectedProject?.let { project ->
                                // Apply IN-PROGRESS with project, keep dialog open for Pomodoro prompt
                                onStateChangedToInProgress(project.id)
                                showProjectPicker = false
                                showPomodoroPrompt = true
                            }
                        },
                        enabled = selectedProject != null
                    ) {
                        Text("Confirm")
                    }
                }
                showPomodoroPrompt -> {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        },
        dismissButton = if (showProjectPicker) {
            {
                TextButton(onClick = {
                    showProjectPicker = false
                    selectedProject = null
                }) {
                    Text("Back")
                }
            }
        } else null
    )
}
