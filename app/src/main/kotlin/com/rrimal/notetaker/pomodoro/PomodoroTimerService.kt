package com.rrimal.notetaker.pomodoro

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.rrimal.notetaker.data.local.PomodoroHistoryDao
import com.rrimal.notetaker.data.local.PomodoroHistoryEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Foreground service that manages Pomodoro timer countdown.
 * 
 * Features:
 * - 1-second tick countdown
 * - Pause/resume/stop controls
 * - Persistent notification with actions
 * - Broadcasts events to ViewModels
 * - WakeLock to prevent device sleep
 * - History tracking (saves completed/cancelled sessions)
 */
@AndroidEntryPoint
class PomodoroTimerService : Service() {

    @Inject
    lateinit var pomodoroHistoryDao: PomodoroHistoryDao

    companion object {
        private const val TAG = "PomodoroTimerService"
        
        // Intent extras
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_TASK_TAGS = "task_tags"
        const val EXTRA_TASK_PRIORITY = "task_priority"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_IS_BREAK = "is_break"
        
        // Broadcast actions
        const val BROADCAST_TIMER_TICK = "com.rrimal.notetaker.POMODORO_TICK"
        const val BROADCAST_TIMER_COMPLETED = "com.rrimal.notetaker.POMODORO_COMPLETED"
        const val BROADCAST_TIMER_PAUSED = "com.rrimal.notetaker.POMODORO_PAUSED"
        const val BROADCAST_TIMER_RESUMED = "com.rrimal.notetaker.POMODORO_RESUMED"
        const val BROADCAST_TIMER_STOPPED = "com.rrimal.notetaker.POMODORO_STOPPED"
        
        const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private lateinit var notificationHelper: PomodoroNotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Timer state
    private var timerJob: Job? = null
    private val _state = MutableStateFlow(PomodoroTimerState())
    val state = _state.asStateFlow()
    
    // History tracking
    private var sessionStartTime: Long = 0L
    
    inner class LocalBinder : Binder() {
        fun getService(): PomodoroTimerService = this@PomodoroTimerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationHelper = PomodoroNotificationHelper(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            PomodoroNotificationHelper.ACTION_PAUSE -> pauseTimer()
            PomodoroNotificationHelper.ACTION_RESUME -> resumeTimer()
            PomodoroNotificationHelper.ACTION_STOP -> stopTimer()
            else -> {
                // Start new timer
                val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L)
                val taskTitle = intent?.getStringExtra(EXTRA_TASK_TITLE)
                val taskTags = intent?.getStringArrayListExtra(EXTRA_TASK_TAGS) ?: emptyList()
                val taskPriority = intent?.getStringExtra(EXTRA_TASK_PRIORITY)
                val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 1500) ?: 1500
                val isBreak = intent?.getBooleanExtra(EXTRA_IS_BREAK, false) ?: false
                
                startTimer(taskId, taskTitle, taskTags, taskPriority, durationSeconds, isBreak)
            }
        }
        
        return START_STICKY // Restart service if killed by system
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        timerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        notificationHelper.cancelAllNotifications()
        super.onDestroy()
    }

    /**
     * Start Pomodoro timer.
     */
    private fun startTimer(
        taskId: Long?,
        taskTitle: String?,
        taskTags: List<String>,
        taskPriority: String?,
        durationSeconds: Int,
        isBreak: Boolean
    ) {
        Log.d(TAG, "Starting timer: duration=$durationSeconds, isBreak=$isBreak, task=$taskTitle")
        
        // Record start time for history
        sessionStartTime = System.currentTimeMillis()
        
        // Update state
        _state.value = PomodoroTimerState(
            isActive = true,
            isPaused = false,
            taskId = taskId,
            taskTitle = taskTitle,
            taskTags = taskTags,
            taskPriority = taskPriority,
            durationSeconds = durationSeconds,
            remainingSeconds = durationSeconds,
            isBreak = isBreak,
            isComplete = false
        )
        
        // Start foreground service
        val notification = notificationHelper.buildTimerNotification(
            remainingSeconds = durationSeconds,
            isPaused = false,
            isBreak = isBreak,
            taskTitle = taskTitle
        )
        startForeground(PomodoroNotificationHelper.NOTIFICATION_ID, notification)
        
        // Start countdown
        startCountdown()
    }

    /**
     * Pause timer.
     */
    private fun pauseTimer() {
        Log.d(TAG, "Pausing timer")
        timerJob?.cancel()
        
        _state.value = _state.value.copy(isPaused = true)
        
        // Update notification
        updateNotification()
        
        // Broadcast pause event
        sendBroadcast(Intent(BROADCAST_TIMER_PAUSED).apply {
            setPackage(packageName)
        })
    }

    /**
     * Resume timer.
     */
    private fun resumeTimer() {
        Log.d(TAG, "Resuming timer")
        _state.value = _state.value.copy(isPaused = false)
        
        // Update notification
        updateNotification()
        
        // Restart countdown
        startCountdown()
        
        // Broadcast resume event
        sendBroadcast(Intent(BROADCAST_TIMER_RESUMED).apply {
            setPackage(packageName)
        })
    }

    /**
     * Stop timer and cleanup.
     */
    private fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        timerJob?.cancel()
        
        // Save history if timer was running (cancelled session)
        if (_state.value.isActive && sessionStartTime > 0) {
            saveHistory(completedSuccessfully = false)
        }
        
        _state.value = PomodoroTimerState() // Reset to default
        sessionStartTime = 0L
        
        // Broadcast stop event
        sendBroadcast(Intent(BROADCAST_TIMER_STOPPED).apply {
            setPackage(packageName)
        })
        
        // Stop foreground and remove notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Start countdown coroutine (1-second tick).
     */
    private fun startCountdown() {
        timerJob?.cancel() // Cancel existing job
        
        timerJob = serviceScope.launch {
            while (_state.value.remainingSeconds > 0 && isActive) {
                delay(1000) // 1 second
                
                val newRemaining = _state.value.remainingSeconds - 1
                _state.value = _state.value.copy(remainingSeconds = newRemaining)
                
                // Update notification every second
                updateNotification()
                
                // Broadcast tick event
                sendBroadcast(Intent(BROADCAST_TIMER_TICK).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_REMAINING_SECONDS, newRemaining)
                })
                
                // Check for completion
                if (newRemaining <= 0) {
                    onTimerComplete()
                }
            }
        }
    }

    /**
     * Handle timer completion.
     */
    private fun onTimerComplete() {
        Log.d(TAG, "Timer completed")
        
        _state.value = _state.value.copy(
            isComplete = true,
            remainingSeconds = 0
        )
        
        // Save history (completed successfully)
        if (sessionStartTime > 0) {
            saveHistory(completedSuccessfully = true)
        }
        
        // Show completion notification with sound
        notificationHelper.showCompletionNotification(
            isBreak = _state.value.isBreak,
            taskTitle = _state.value.taskTitle
        )
        
        // Broadcast completion event
        sendBroadcast(Intent(BROADCAST_TIMER_COMPLETED).apply {
            setPackage(packageName)
        })
        
        // Stop foreground (but keep service alive for completion dialog)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    /**
     * Update foreground notification.
     */
    private fun updateNotification() {
        val notification = notificationHelper.buildTimerNotification(
            remainingSeconds = _state.value.remainingSeconds,
            isPaused = _state.value.isPaused,
            isBreak = _state.value.isBreak,
            taskTitle = _state.value.taskTitle
        )
        
        // Update existing notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(PomodoroNotificationHelper.NOTIFICATION_ID, notification)
    }

    /**
     * Acquire partial wake lock to prevent CPU sleep during timer.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NoteTaker::PomodoroTimerWakeLock"
            ).apply {
                acquire(60 * 60 * 1000L) // 1 hour max (safety timeout)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    /**
     * Save Pomodoro session to history database.
     */
    private fun saveHistory(completedSuccessfully: Boolean) {
        val state = _state.value
        val taskId = state.taskId ?: return // Skip if no task ID
        val taskTitle = state.taskTitle ?: "Untitled"
        
        val endTime = System.currentTimeMillis()
        val actualDurationSeconds = ((endTime - sessionStartTime) / 1000).toInt()
        
        val historyEntity = PomodoroHistoryEntity(
            taskId = taskId,
            taskTitle = taskTitle,
            startTime = sessionStartTime,
            endTime = endTime,
            durationSeconds = actualDurationSeconds,
            isBreak = state.isBreak,
            completedSuccessfully = completedSuccessfully
        )
        
        serviceScope.launch {
            try {
                pomodoroHistoryDao.insert(historyEntity)
                Log.d(TAG, "Saved history: task=$taskTitle, completed=$completedSuccessfully, duration=${actualDurationSeconds}s")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save history", e)
            }
        }
    }
}
