package com.rrimal.notetaker.pomodoro

/**
 * Represents the state of the Pomodoro timer.
 */
data class PomodoroTimerState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val taskTags: List<String> = emptyList(),
    val taskPriority: String? = null,
    val durationSeconds: Int = 1500, // 25 minutes default
    val remainingSeconds: Int = 1500,
    val isBreak: Boolean = false,
    val isComplete: Boolean = false
) {
    /**
     * Progress from 0.0 to 1.0
     */
    val progress: Float
        get() = if (durationSeconds > 0) {
            1f - (remainingSeconds.toFloat() / durationSeconds.toFloat())
        } else {
            0f
        }

    /**
     * Formatted time remaining as "MM:SS"
     */
    val formattedTime: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Actions user can take when Pomodoro timer completes.
 */
enum class PomodoroCompletionAction {
    /**
     * Start a break timer (short or long break based on settings).
     */
    START_BREAK,

    /**
     * Start another Pomodoro session for the same task.
     */
    ANOTHER_POMODORO,

    /**
     * Mark the task as DONE and exit Pomodoro mode.
     */
    MARK_DONE,

    /**
     * Cancel and return to agenda without state change.
     */
    CANCEL
}

/**
 * Type of break timer.
 */
enum class BreakType {
    SHORT,
    LONG
}

/**
 * Events broadcast from PomodoroTimerService to ViewModels.
 */
sealed class PomodoroServiceEvent {
    data class Tick(val remainingSeconds: Int) : PomodoroServiceEvent()
    data object Completed : PomodoroServiceEvent()
    data object Paused : PomodoroServiceEvent()
    data object Resumed : PomodoroServiceEvent()
    data object Stopped : PomodoroServiceEvent()
    data class Error(val message: String) : PomodoroServiceEvent()
}
