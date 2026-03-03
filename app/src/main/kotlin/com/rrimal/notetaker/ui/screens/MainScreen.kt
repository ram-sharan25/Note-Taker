package com.rrimal.notetaker.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rrimal.notetaker.ui.screens.agenda.StateSelectionDialog
import com.rrimal.notetaker.ui.screens.pomodoro.PomodoroTimerScreen
import com.rrimal.notetaker.ui.screens.pomodoro.PomodoroCompletionDialog
import com.rrimal.notetaker.ui.viewmodels.AgendaViewModel
import com.rrimal.notetaker.ui.viewmodels.NoteViewModel
import kotlinx.coroutines.launch

/**
 * Main screen with horizontal pager navigation
 *
 * Pages:
 * - 0: Dictation (swipe left from agenda)
 * - 1: Agenda (default/home screen)
 * - 2: Inbox Capture (swipe right from agenda)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = 1, // Start at Agenda (middle)
        pageCount = { 3 }
    )

    // Get the NoteViewModel to control voice recording
    val noteViewModel: NoteViewModel = hiltViewModel()

    // Get the AgendaViewModel to observe Pomodoro state
    val agendaViewModel: AgendaViewModel = hiltViewModel()
    val pomodoroState by agendaViewModel.pomodoroState.collectAsState()
    val isTimerMinimized by agendaViewModel.isTimerMinimized.collectAsState()
    val togglProjects by agendaViewModel.togglProjects.collectAsState()
    val isPomodoroActive = pomodoroState != null

    // Whether to show the state-selection dialog triggered from the task card
    var showPomodoroTaskDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Track current page to stop/start voice recording
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    // Stop voice recording when leaving Dictation page (page 0)
    // Start voice recording when entering Dictation page (page 0)
    LaunchedEffect(currentPage) {
        if (currentPage == 0) {
            // Entered Dictation page - auto-start voice (sets mode to VOICE and starts)
            noteViewModel.startVoiceInput()
        } else {
            // Left Dictation page - stop voice recording
            noteViewModel.stopVoiceInput()
        }
    }

    // Stop voice recording when MainScreen is disposed
    DisposableEffect(Unit) {
        onDispose {
            noteViewModel.stopVoiceInput()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isPomodoroActive, // Disable swipe when timer active
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> NoteInputScreen(
                    onSettingsClick = onSettingsClick,
                    onBrowseClick = onBrowseClick,
                    onInboxCaptureClick = {}, // Not needed in pager
                    onAgendaClick = {}, // Not needed in pager
                    viewModel = noteViewModel, // Pass the same ViewModel instance
                    inPager = true // Voice control handled by MainScreen
                )
                1 -> com.rrimal.notetaker.ui.screens.agenda.AgendaScreen(
                    onBack = {}, // Not needed in pager - no back navigation
                    onSettingsClick = onSettingsClick,
                    onBrowseClick = onBrowseClick,
                    viewModel = agendaViewModel // Share ViewModel so Pomodoro state is on same instance
                )
                2 -> InboxCaptureScreen(
                    onBack = {}, // Not needed in pager
                    onBrowseClick = onBrowseClick,
                    onSettingsClick = onSettingsClick
                )
            }
        }

        // Page indicators at bottom with labels (hidden when Pomodoro active)
        if (!isPomodoroActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pages = listOf("Dictation", "Agenda", "Quick Task")
                    pages.forEachIndexed { index, label ->
                        val isSelected = pagerState.currentPage == index
                        val indicatorSize by animateDpAsState(
                            targetValue = if (isSelected) 8.dp else 6.dp,
                            label = "indicator_$index"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(indicatorSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        }
                                    )
                            )
                            if (isSelected) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pomodoro timer overlay - rendered ABOVE the pager for true fullscreen
        pomodoroState?.let { state ->
            // Show fullscreen overlay only when not minimized
            if (!isTimerMinimized) {
                PomodoroTimerScreen(
                    state = state,
                    onPause = { agendaViewModel.pausePomodoro() },
                    onResume = { agendaViewModel.resumePomodoro() },
                    onStop = { agendaViewModel.stopPomodoroEarly() },
                    onMinimize = { agendaViewModel.minimizeTimer() },
                    onTaskClick = { showPomodoroTaskDialog = true }
                )
            }

            // Show completion dialog regardless of minimized state
            if (state.isComplete) {
                PomodoroCompletionDialog(
                    isBreak = state.isBreak,
                    taskTitle = state.taskTitle,
                    onAction = { action ->
                        agendaViewModel.handlePomodoroCompletionAction(action)
                    }
                )
            }

            // State-selection dialog opened by tapping the task card in the timer
            val taskId = state.taskId
            if (showPomodoroTaskDialog && taskId != null) {
                StateSelectionDialog(
                    noteId = taskId,
                    currentState = "IN-PROGRESS", // Timer is always running for an IN-PROGRESS task
                    togglProjects = togglProjects,
                    needsProjectSelection = false, // Project already chosen when timer started
                    isPomodoroActive = true,        // Hide "Start Pomodoro" button — it's already running
                    onStateSelected = { newState, _ ->
                        // DONE/CANCELLED → stop timer completely
                        // WAITING/HOLD/TODO → pause timer only
                        when (newState) {
                            "DONE", "CANCELLED" -> agendaViewModel.stopPomodoro()
                            else -> agendaViewModel.pausePomodoro()
                        }
                        agendaViewModel.updateTodoState(taskId, newState, null)
                        showPomodoroTaskDialog = false
                    },
                    onStateChangedToInProgress = { _ ->
                        // Already IN-PROGRESS with a running timer — just close the dialog
                        showPomodoroTaskDialog = false
                    },
                    onStartPomodoro = { _ ->
                        // Should not be reachable (isPomodoroActive = true hides the button)
                        showPomodoroTaskDialog = false
                    },
                    onDismiss = { showPomodoroTaskDialog = false }
                )
            }
        }
    }
}
