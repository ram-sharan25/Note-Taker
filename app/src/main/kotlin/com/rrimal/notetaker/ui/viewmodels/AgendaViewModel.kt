package com.rrimal.notetaker.ui.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.api.TogglProject
import com.rrimal.notetaker.data.preferences.AgendaConfigManager
import com.rrimal.notetaker.data.preferences.PomodoroPreferencesManager
import com.rrimal.notetaker.data.repository.AgendaRepository
import com.rrimal.notetaker.data.repository.TogglRepository
import com.rrimal.notetaker.pomodoro.*
import com.rrimal.notetaker.ui.screens.agenda.AgendaItem
import com.rrimal.notetaker.data.storage.LocalOrgStorageBackend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgendaViewModel @Inject constructor(
    application: Application,
    private val agendaRepository: AgendaRepository,
    private val agendaConfigManager: AgendaConfigManager,
    private val togglRepository: TogglRepository,
    private val pomodoroPreferencesManager: PomodoroPreferencesManager,
    private val localOrgStorageBackend: LocalOrgStorageBackend
) : AndroidViewModel(application) {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _updateResult = MutableStateFlow<Result<String>?>(null)
    val updateResult = _updateResult.asStateFlow()

    private val _showStateDialog = MutableStateFlow<Pair<Long, String>?>(null) // noteId (database ID), currentState
    val showStateDialog = _showStateDialog.asStateFlow()

    // Pending sync count (v0.9.0 JSON Sync)
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount = _pendingSyncCount.asStateFlow()

    // Toggl integration - project caching
    private val _togglProjects = MutableStateFlow<List<TogglProject>>(emptyList())
    val togglProjects = _togglProjects.asStateFlow()

    private val _isTogglEnabled = MutableStateFlow(false)
    val isTogglEnabled = _isTogglEnabled.asStateFlow()

    // Pomodoro integration
    private val _pomodoroState = MutableStateFlow<PomodoroTimerState?>(null)
    val pomodoroState = _pomodoroState.asStateFlow()

    // Whether the timer overlay is minimized (hidden behind agenda, tappable chip shown)
    private val _isTimerMinimized = MutableStateFlow(false)
    val isTimerMinimized = _isTimerMinimized.asStateFlow()

    fun minimizeTimer() { _isTimerMinimized.value = true }
    fun maximizeTimer() { _isTimerMinimized.value = false }

    /**
     * Save a new Quick Task: Org headline, Toggl timer, (optional Pomodoro)
     */
    suspend fun saveQuickTask(
        title: String,
        description: String,
        projectName: String,
        pomodoroEnabled: Boolean
    ): Result<Unit> {
        return try {
            val startedIso = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

            // Find Toggl project (basic match)
            val togglProject = togglProjects.value.find { it.name == projectName }
            val togglProjectId = togglProject?.id
            val togglId = try {
                if (togglProjectId != null) {
                    togglRepository.startTimeEntry(
                        description = title,
                        projectId = togglProjectId,
                        tags = emptyList()
                    ).getOrNull()?.id?.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Toggl timer", e)
                null
            }

            // Compose headline
            val headline = localOrgStorageBackend.createQuickTaskHeadline(
                title = title,
                description = description,
                startedIso = startedIso,
                togglId = togglId,
                togglProject = projectName,
                pomodoro = pomodoroEnabled
            )

            // Store to quick.org
            localOrgStorageBackend.appendToQuickFile(headline).getOrThrow()
            Log.d(TAG, "✓ Quick task written to quick.org: $title")

            // Sync only quick.org into the database (targeted, no full resync needed)
            agendaRepository.syncQuickFile()
            _refreshTrigger.value += 1
            Log.d(TAG, "✓ Agenda refresh triggered")

            // Verify the task appears in the database
            val allTasks = agendaRepository.getInProgressTasks()
            Log.d(TAG, "Found ${allTasks.size} IN-PROGRESS tasks after refresh")

            // Start Pomodoro if enabled - find the newly created task by title
            if (pomodoroEnabled) {
                val newTask = allTasks.find { it.title == title }
                if (newTask != null) {
                    Log.d(TAG, "✓ Found task in database: ${newTask.id} - $title")
                    Log.d(TAG, "✓ Starting Pomodoro for task...")
                    val started = startPomodoro(newTask.id, isBreak = false)
                    if (started) {
                        Log.d(TAG, "✓ Pomodoro started successfully!")
                    } else {
                        Log.e(TAG, "✗ Failed to start Pomodoro")
                    }
                } else {
                    Log.e(TAG, "✗ Task not found in database after refresh. Available tasks:")
                    allTasks.forEach { task ->
                        Log.e(TAG, "  - ${task.id}: ${task.title}")
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save quick task", e)
            Result.failure(e)
        }
    }


    private val pomodoroReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "Pomodoro broadcast received: $action")
            
            when (action) {
                PomodoroTimerService.BROADCAST_TIMER_TICK -> {
                    val remainingSeconds = intent.getIntExtra(PomodoroTimerService.EXTRA_REMAINING_SECONDS, 0)
                    _pomodoroState.value = _pomodoroState.value?.copy(
                        remainingSeconds = remainingSeconds
                    )
                }
                PomodoroTimerService.BROADCAST_TIMER_PAUSED -> {
                    _pomodoroState.value = _pomodoroState.value?.copy(isPaused = true)
                }
                PomodoroTimerService.BROADCAST_TIMER_RESUMED -> {
                    _pomodoroState.value = _pomodoroState.value?.copy(isPaused = false)
                }
                PomodoroTimerService.BROADCAST_TIMER_COMPLETED -> {
                    handlePomodoroCompletion()
                }
                PomodoroTimerService.BROADCAST_TIMER_STOPPED -> {
                    _pomodoroState.value = null
                    _isTimerMinimized.value = false
                }
                else -> {
                    Log.w(TAG, "Unknown broadcast action: $action")
                }
            }
        }
    }

    // Get agenda items from repository (database-centric with file freshness checks)
    private val _agendaDays = MutableStateFlow(7) // Default to 7 days
    private val _refreshTrigger = MutableStateFlow(0) // Increment to trigger refresh
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val agendaItems = combine(
        _agendaDays,
        agendaConfigManager.statusFilter,
        _refreshTrigger
    ) { days, filter, trigger ->
        Log.d(TAG, ">>> Flow combine triggered: days=$days, filter=$filter, trigger=$trigger")
        days to filter
    }.flatMapLatest { (days, filter) ->
        Log.d(TAG, ">>> flatMapLatest executing: days=$days, filter=$filter")
        if (filter.isEmpty()) {
            Log.d(TAG, ">>> Calling getAgendaItems(days=$days)")
            agendaRepository.getAgendaItems(days)
        } else {
            Log.d(TAG, ">>> Calling getAgendaItemsFiltered(days=$days, filter=$filter)")
            agendaRepository.getAgendaItemsFiltered(days, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusFilter = agendaConfigManager.statusFilter.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    init {
        Log.d(TAG, "=== AgendaViewModel INIT START ===")
        
        // Register BroadcastReceiver for Pomodoro events - listen to all timer broadcasts
        val filter = IntentFilter().apply {
            addAction(PomodoroTimerService.BROADCAST_TIMER_TICK)
            addAction(PomodoroTimerService.BROADCAST_TIMER_COMPLETED)
            addAction(PomodoroTimerService.BROADCAST_TIMER_PAUSED)
            addAction(PomodoroTimerService.BROADCAST_TIMER_RESUMED)
            addAction(PomodoroTimerService.BROADCAST_TIMER_STOPPED)
        }
        getApplication<Application>().registerReceiver(pomodoroReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Pomodoro receiver registered")
        
        // Initialize agenda flow observer for debugging
        viewModelScope.launch {
            agendaItems.collect { items ->
                Log.d(TAG, ">>> AgendaItems Flow EMITTED: ${items.size} items")
                items.take(5).forEachIndexed { index, item ->
                    when (item) {
                        is AgendaItem.Day -> Log.d(TAG, "    [$index] Day: ${item.formattedDate}")
                        is AgendaItem.Note -> Log.d(TAG, "    [$index] Note: ${item.todoState} ${item.title}")
                    }
                }
                if (items.size > 5) Log.d(TAG, "    ... (${items.size - 5} more)")
            }
        }
        
        refresh()
        
        // Fetch Toggl projects if enabled
        viewModelScope.launch {
            try {
                val enabled = togglRepository.isEnabled()
                _isTogglEnabled.value = enabled
                
                if (enabled) {
                    Log.d(TAG, "Toggl enabled, fetching projects for cache")
                    val result = togglRepository.getActiveProjects()
                    result.onSuccess { projects ->
                        _togglProjects.value = projects
                        Log.d(TAG, "Cached ${projects.size} Toggl projects")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to fetch Toggl projects", error)
                    }
                } else {
                    Log.d(TAG, "Toggl not enabled, skipping project fetch")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Toggl status", e)
            }
        }
        
        // Periodically check for pending sync files
        viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                try {
                    val count = agendaRepository.countPendingSyncs().getOrElse { 0 }
                    _pendingSyncCount.value = count
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check pending syncs", e)
                }
            }
        }
        
        Log.d(TAG, "=== AgendaViewModel INIT END ===")
    }

    fun toggleStatusFilter(status: String) {
        viewModelScope.launch {
            val current = statusFilter.value.toMutableSet()
            if (status in current) {
                current.remove(status)
            } else {
                current.add(status)
            }
            agendaConfigManager.setStatusFilter(current)
        }
    }

    fun clearStatusFilter() {
        viewModelScope.launch {
            agendaConfigManager.setStatusFilter(emptySet())
        }
    }

    fun refresh() {
        Log.d(TAG, "=== refresh() called ===")
        viewModelScope.launch {
            _isRefreshing.value = true
            Log.d(TAG, "Starting refresh, clearing and resyncing all files")
            try {
                // Clear all database and force full re-sync from files
                // This ensures agenda.org changes are reflected even if hash matching fails
                agendaRepository.clearAndResyncAll()
                
                // Increment refresh trigger to cause Flow to re-query
                val newTrigger = _refreshTrigger.value + 1
                Log.d(TAG, "Incrementing refresh trigger: ${_refreshTrigger.value} -> $newTrigger")
                _refreshTrigger.value = newTrigger
                
                Log.d(TAG, "Refresh completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed with exception", e)
                // Handle error - repository already logs errors
            } finally {
                _isRefreshing.value = false
                Log.d(TAG, "=== refresh() END ===")
            }
        }
    }

    // STATE DIALOG MANAGEMENT
    
    fun showStateDialog(noteId: Long, currentState: String?) {
        _showStateDialog.value = noteId to (currentState ?: "TODO")
    }

    fun hideStateDialog() {
        _showStateDialog.value = null
    }

    // TODO STATE UPDATE
    
    fun updateTodoState(noteId: Long, newState: String, projectId: Long? = null) {
        viewModelScope.launch {
            val result = agendaRepository.updateTodoState(noteId, newState, projectId)
            _updateResult.value = result.map { newState }
            _refreshTrigger.value += 1
            hideStateDialog() // Close the dialog after update
        }
    }

    /**
     * Apply a state change without closing the dialog.
     * Used when switching to IN-PROGRESS so the Pomodoro prompt can be shown.
     */
    fun updateTodoStateWithoutClosingDialog(noteId: Long, newState: String, projectId: Long? = null) {
        viewModelScope.launch {
            val result = agendaRepository.updateTodoState(noteId, newState, projectId)
            _updateResult.value = result.map { newState }
            _refreshTrigger.value += 1
            // Deliberately NOT calling hideStateDialog() — dialog stays open
        }
    }
    
    fun cycleTodoState(noteId: Long) {
        viewModelScope.launch {
            _updateResult.value = agendaRepository.cycleTodoState(noteId)
            _refreshTrigger.value += 1 // Trigger agenda refresh
        }
    }

    fun clearUpdateResult() {
        _updateResult.value = null
    }

    /**
     * Check if a note needs project selection for Toggl timer.
     * Returns true if:
     * - Toggl is enabled
     * - Note doesn't have TOGGL_PROJECT_ID property
     * - Projects are available
     */
    suspend fun needsProjectSelection(noteId: Long): Boolean {
        return try {
            if (!_isTogglEnabled.value || _togglProjects.value.isEmpty()) {
                return false
            }
            val note = agendaRepository.getNoteById(noteId)
            note?.properties?.get("TOGGL_PROJECT_ID") == null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking project selection requirement", e)
            false
        }
    }

    // POMODORO METHODS

    /**
     * Start a Pomodoro timer for a specific task.
     * If taskId is null, picks the first IN-PROGRESS task automatically.
     */
    suspend fun startPomodoro(taskId: Long? = null, isBreak: Boolean = false): Boolean {
        try {
            // Determine which task to use
            val targetTaskId = if (taskId != null) {
                taskId
            } else {
                // Auto-select first IN-PROGRESS task
                val inProgressTasks = agendaRepository.getInProgressTasks()
                if (inProgressTasks.isEmpty()) {
                    Log.w(TAG, "No IN-PROGRESS tasks available for Pomodoro")
                    return false
                }
                inProgressTasks.first().id
            }

            // Get task details
            val task = agendaRepository.getTask(targetTaskId) ?: run {
                Log.e(TAG, "Task not found: $targetTaskId")
                return false
            }

            // Get duration from preferences (in minutes, convert to seconds)
            val durationSeconds = if (isBreak) {
                pomodoroPreferencesManager.shortBreakDuration.first() * 60
            } else {
                pomodoroPreferencesManager.pomodoroDuration.first() * 60
            }

            // Update state
            _pomodoroState.value = PomodoroTimerState(
                isActive = true,
                isPaused = false,
                taskId = targetTaskId,
                taskTitle = task.title,
                taskTags = task.tags,
                taskPriority = task.priority,
                durationSeconds = durationSeconds,
                remainingSeconds = durationSeconds,
                isBreak = isBreak,
                isComplete = false
            )

            // Show timer fullscreen (not minimized)
            _isTimerMinimized.value = false

            // Start service
            val intent = Intent(getApplication(), PomodoroTimerService::class.java).apply {
                putExtra(PomodoroTimerService.EXTRA_TASK_ID, targetTaskId)
                putExtra(PomodoroTimerService.EXTRA_TASK_TITLE, task.title)
                putStringArrayListExtra(PomodoroTimerService.EXTRA_TASK_TAGS, ArrayList(task.tags))
                putExtra(PomodoroTimerService.EXTRA_TASK_PRIORITY, task.priority)
                putExtra(PomodoroTimerService.EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(PomodoroTimerService.EXTRA_IS_BREAK, isBreak)
            }
            getApplication<Application>().startForegroundService(intent)

            Log.d(TAG, "Started Pomodoro for task: ${task.title}, isBreak: $isBreak, duration: ${durationSeconds}s")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Pomodoro", e)
            return false
        }
    }

    fun pausePomodoro() {
        val intent = Intent(getApplication(), PomodoroTimerService::class.java).apply {
            action = PomodoroNotificationHelper.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
    }

    fun resumePomodoro() {
        val intent = Intent(getApplication(), PomodoroTimerService::class.java).apply {
            action = PomodoroNotificationHelper.ACTION_RESUME
        }
        getApplication<Application>().startService(intent)
    }

    fun stopPomodoro() {
        val intent = Intent(getApplication(), PomodoroTimerService::class.java).apply {
            action = PomodoroNotificationHelper.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _pomodoroState.value = null
        _isTimerMinimized.value = false
    }

    /**
     * Stop the timer early (user tapped ⏹ Stop before the timer finished).
     * Automatically sets the task state to HOLD so it surfaces as "paused work"
     * in the agenda. Only applies to focus sessions — stopping a break does nothing.
     */
    fun stopPomodoroEarly() {
        val current = _pomodoroState.value ?: return
        val taskId = current.taskId
        stopPomodoro()
        if (!current.isBreak && taskId != null) {
            viewModelScope.launch {
                agendaRepository.updateTodoState(taskId, "HOLD")
            }
        }
    }

    /**
     * Handle Pomodoro completion and show dialog.
     * Dialog actions are handled by handlePomodoroCompletionAction.
     */
    private fun handlePomodoroCompletion() {
        val currentState = _pomodoroState.value ?: return
        
        // Update state to show completion (dialog is shown by UI observing this state)
        _pomodoroState.value = currentState.copy(
            remainingSeconds = 0,
            isComplete = true
        )
    }

    /**
     * Handle user action from completion dialog.
     */
    fun handlePomodoroCompletionAction(action: PomodoroCompletionAction) {
        val currentState = _pomodoroState.value ?: return
        
        viewModelScope.launch {
            when (action) {
                PomodoroCompletionAction.START_BREAK -> {
                    // Stop current timer
                    stopPomodoro()
                    
                    // Auto-start break (user confirmed, no additional confirmation needed)
                    startPomodoro(currentState.taskId, isBreak = true)
                }
                PomodoroCompletionAction.ANOTHER_POMODORO -> {
                    // Stop current timer
                    stopPomodoro()
                    
                    // Start new focus session for same task
                    startPomodoro(currentState.taskId, isBreak = false)
                }
                PomodoroCompletionAction.MARK_DONE -> {
                    // Stop timer
                    stopPomodoro()
                    
                    // Mark task as DONE
                    currentState.taskId?.let { taskId ->
                        updateTodoState(taskId, "DONE")
                    }
                }
                PomodoroCompletionAction.CANCEL -> {
                    // Just stop timer and clear state
                    stopPomodoro()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(pomodoroReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Pomodoro receiver", e)
        }
    }

    companion object {
        private const val TAG = "AgendaViewModel"
    }
}
