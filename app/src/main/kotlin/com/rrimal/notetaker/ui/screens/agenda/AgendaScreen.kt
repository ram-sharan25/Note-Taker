package com.rrimal.notetaker.ui.screens.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import androidx.hilt.navigation.compose.hiltViewModel
import com.rrimal.notetaker.ui.viewmodels.AgendaViewModel
import android.util.Log
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit = {},
    viewModel: AgendaViewModel = hiltViewModel()
) {
    Log.d("AgendaScreen", "=== AgendaScreen COMPOSING ===")
    
    val agendaItems by viewModel.agendaItems.collectAsState(initial = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val showStateDialog by viewModel.showStateDialog.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val togglProjects by viewModel.togglProjects.collectAsState()
    val isTogglEnabled by viewModel.isTogglEnabled.collectAsState()
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val isTimerMinimized by viewModel.isTimerMinimized.collectAsState()

    Log.d("AgendaScreen", "State collected: agendaItems.size=${agendaItems.size}, isRefreshing=$isRefreshing, statusFilter=$statusFilter")
    
    var showFilterDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentNoteNeedsProject by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Show snackbar when update result changes
    LaunchedEffect(updateResult) {
        updateResult?.let { result ->
            result.onSuccess { newState ->
                snackbarHostState.showSnackbar("Updated to $newState")
            }.onFailure { error ->
                snackbarHostState.showSnackbar("Failed to update: ${error.message}")
            }
            viewModel.clearUpdateResult()
        }
    }

    var showQuickTaskDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Task",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Agenda") },
                actions = {
                    // Pomodoro timer chip — shown when timer is active but minimized
                    val activePomodoro = pomodoroState
                    if (activePomodoro != null && isTimerMinimized) {
                        val timerEmoji = if (activePomodoro.isBreak) "☕" else "🍅"
                        Surface(
                            onClick = { viewModel.maximizeTimer() },
                            shape = RoundedCornerShape(16.dp),
                            color = if (activePomodoro.isBreak) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = timerEmoji, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = activePomodoro.formattedTime,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activePomodoro.isBreak) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }
                    }
                    BadgedBox(
                        badge = {
                            if (statusFilter.isNotEmpty()) {
                                Badge { Text(statusFilter.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    }
                    // Show pending syncs indicator (v0.9.0 JSON Sync)
                    if (pendingSyncCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(pendingSyncCount.toString())
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Pending syncs",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    // Refresh button - clears database and forces full re-sync from org files
                    // Useful after editing agenda.org externally (Emacs, Syncthing, etc.)
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onBrowseClick) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Browse")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Agenda items list
            if (agendaItems.isEmpty() && !isRefreshing) {
                Log.d("AgendaScreen", "Showing empty state (no items)")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No agenda items found.\nCheck your agenda files in Settings.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Log.d("AgendaScreen", "Rendering LazyColumn with ${agendaItems.size} items")
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = agendaItems,
                        key = { it.id },
                        contentType = { it::class }
                    ) { item ->
                        when (item) {
                            is AgendaItem.Day -> {
                                Log.d("AgendaScreen", "Rendering Day: ${item.formattedDate}")
                                DayHeader(item)
                            }
                            is AgendaItem.Note -> {
                                Log.d("AgendaScreen", "Rendering Note: ${item.todoState} ${item.title}")
                                AgendaNoteItem(
                                    item = item,
                                    onTodoStateClick = { noteId ->
                                        viewModel.showStateDialog(noteId, item.todoState)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Task dialog/modal (launches from FAB)
    if (showQuickTaskDialog) {
        QuickTaskDialog(
            togglProjects = togglProjects.map { it.name },
            onClose = { showQuickTaskDialog = false },
            onSubmit = { title, description, selectedProject, pomodoroEnabled ->
                // Launch quick task save in viewModelScope
                coroutineScope.launch {
                    showQuickTaskDialog = false
                    val result = viewModel.saveQuickTask(
                        title = title,
                        description = description,
                        projectName = selectedProject,
                        pomodoroEnabled = pomodoroEnabled
                    )
                    result.onSuccess {
                        snackbarHostState.showSnackbar("Quick task created: $title")
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar("Failed to create task: ${error.message}")
                    }
                }
            }
        )
    }

    // Status filter dialog
    if (showFilterDialog) {
        StatusFilterDialog(
            selectedStatuses = statusFilter,
            onStatusToggle = { viewModel.toggleStatusFilter(it) },
            onClearAll = { viewModel.clearStatusFilter() },
            onDismiss = { showFilterDialog = false }
        )
    }

    // State selection dialog
    showStateDialog?.let { (noteId, currentState) ->
        // Check if note needs project selection when dialog opens
        LaunchedEffect(noteId) {
            currentNoteNeedsProject = isTogglEnabled && viewModel.needsProjectSelection(noteId)
        }
        
        StateSelectionDialog(
            noteId = noteId,
            currentState = currentState,
            togglProjects = togglProjects,
            needsProjectSelection = currentNoteNeedsProject,
            isPomodoroActive = pomodoroState != null,
            onStateSelected = { newState, projectId ->
                // If there's a running Pomodoro and we're leaving IN-PROGRESS, stop it
                if (pomodoroState != null && currentState == "IN-PROGRESS") {
                    viewModel.stopPomodoro()
                }
                viewModel.updateTodoState(noteId, newState, projectId)
            },
            onStateChangedToInProgress = { projectId ->
                // Apply IN-PROGRESS state change WITHOUT closing the dialog
                // updateTodoState normally calls hideStateDialog — we bypass that by calling
                // the repository directly via a dedicated ViewModel function
                viewModel.updateTodoStateWithoutClosingDialog(noteId, "IN-PROGRESS", projectId)
            },
            onStartPomodoro = { taskId ->
                coroutineScope.launch {
                    viewModel.startPomodoro(taskId, isBreak = false)
                }
            },
            onDismiss = { viewModel.hideStateDialog() }
        )
    }
    
    // NOTE: PomodoroTimerScreen is rendered in MainScreen (above the HorizontalPager)
    // so it can be a true fullscreen overlay that isn't clipped by the pager.
}

@Composable
fun DayHeader(item: AgendaItem.Day) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = item.formattedDate,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AgendaNoteItem(
    item: AgendaItem.Note,
    onTodoStateClick: (Long) -> Unit
) {
    Surface(
        onClick = { onTodoStateClick(item.noteId) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            // Priority badge [#A], [#B], [#C]
            if (item.priority != null) {
                Text(
                    text = "[#${item.priority}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when (item.priority) {
                        "A" -> MaterialTheme.colorScheme.error
                        "B" -> MaterialTheme.colorScheme.primary
                        "C" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // TODO state badge
            if (item.todoState != null) {
                val color = when (item.todoState) {
                    "TODO" -> MaterialTheme.colorScheme.primary
                    "IN-PROGRESS" -> MaterialTheme.colorScheme.tertiary
                    "WAITING" -> MaterialTheme.colorScheme.secondary
                    "HOLD" -> MaterialTheme.colorScheme.tertiaryContainer
                    "DONE" -> MaterialTheme.colorScheme.secondaryContainer
                    "CANCELLED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = color,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.todoState,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.todoState) {
                            "TODO" -> MaterialTheme.colorScheme.onPrimary
                            "IN-PROGRESS" -> MaterialTheme.colorScheme.onTertiary
                            "WAITING" -> MaterialTheme.colorScheme.onSecondary
                            "HOLD" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "DONE" -> MaterialTheme.colorScheme.onSecondaryContainer
                            "CANCELLED" -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (item.todoState == "DONE") FontWeight.Normal else FontWeight.Medium
                )
            }

            // Show active session duration for IN-PROGRESS tasks
            if (item.todoState in setOf("IN-PROGRESS", "DOING", "STARTED")) {
                item.properties["PHONE_STARTED"]?.let { startedStr ->
                    val duration = calculateDuration(startedStr)
                    if (duration != null) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Active time",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Active: $duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            if (!item.formattedTime.isNullOrBlank()) {
                val chipBackground = when (item.timeType) {
                    TimeType.SCHEDULED -> Color(0xFF2E7D32)
                    TimeType.DEADLINE -> Color(0xFFC62828)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = chipBackground,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.formattedTime!!,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Calculate duration from start timestamp to now
 * Returns formatted string like "1h 15m" or "45m"
 */
private fun calculateDuration(startTimestamp: String): String? {
    return try {
        // Parse: [2026-03-02 Sun 14:00]
        val formatter = DateTimeFormatter.ofPattern("[yyyy-MM-dd EEE HH:mm]", Locale.ENGLISH)
        val startTime = LocalDateTime.parse(startTimestamp, formatter)
        val now = LocalDateTime.now()
        val duration = Duration.between(startTime, now)
        
        formatDuration(duration)
    } catch (e: Exception) {
        Log.e("AgendaScreen", "Failed to parse timestamp: $startTimestamp", e)
        null
    }
}

/**
 * Format duration as "Xh Ym" or "Ym"
 */
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterDialog(
    selectedStatuses: Set<String>,
    onStatusToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val allStatuses = listOf("TODO", "IN-PROGRESS", "WAITING", "HOLD", "DONE", "CANCELLED")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Status") },
        text = {
            Column {
                Text(
                    text = "Select statuses to show:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                allStatuses.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusToggle(status) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = status in selectedStatuses,
                            onCheckedChange = { onStatusToggle(status) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = status)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearAll()
                onDismiss()
            }) {
                Text("Clear All")
            }
        }
    )
}

// StateSelectionDialog moved to StateSelectionDialog.kt
