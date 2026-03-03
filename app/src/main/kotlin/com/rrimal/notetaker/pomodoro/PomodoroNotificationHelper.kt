package com.rrimal.notetaker.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rrimal.notetaker.MainActivity
import com.rrimal.notetaker.R

/**
 * Helper class for creating Pomodoro timer notifications.
 */
class PomodoroNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "pomodoro_timer_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_PAUSE = "com.rrimal.notetaker.POMODORO_PAUSE"
        const val ACTION_RESUME = "com.rrimal.notetaker.POMODORO_RESUME"
        const val ACTION_STOP = "com.rrimal.notetaker.POMODORO_STOP"
    }

    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Create notification channel for Pomodoro timer.
     * Uses high importance to enable sound and vibration.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_HIGH // Enable sound + vibration
            ).apply {
                description = "Shows countdown and completion notifications for Pomodoro timer"
                enableVibration(true)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Build foreground service notification with countdown.
     */
    fun buildTimerNotification(
        remainingSeconds: Int,
        isPaused: Boolean,
        isBreak: Boolean,
        taskTitle: String?
    ): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeString = "%02d:%02d".format(minutes, seconds)

        val title = when {
            isPaused -> "⏸ Pomodoro Paused"
            isBreak -> "☕ Break Time"
            else -> "🍅 Pomodoro Focus"
        }

        val contentText = if (taskTitle != null) {
            "$timeString • $taskTitle"
        } else {
            timeString
        }

        // Intent to open app on notification tap
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history) // Clock/timer icon
            .setOnlyAlertOnce(true) // Don't alert on every update
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For pre-O devices
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen

        // Add pause/resume button
        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                createServicePendingIntent(ACTION_RESUME)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                createServicePendingIntent(ACTION_PAUSE)
            )
        }

        // Add stop button
        builder.addAction(
            android.R.drawable.ic_delete,
            "Stop",
            createServicePendingIntent(ACTION_STOP)
        )

        return builder.build()
    }

    /**
     * Build completion notification with sound.
     */
    fun buildCompletionNotification(isBreak: Boolean, taskTitle: String?): Notification {
        val title = if (isBreak) {
            "☕ Break Complete!"
        } else {
            "🍅 Pomodoro Complete!"
        }

        val contentText = if (taskTitle != null) {
            "Task: $taskTitle"
        } else {
            if (isBreak) "Time to focus!" else "Time for a break!"
        }

        // Intent to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true) // Dismiss on tap
            .setContentIntent(openAppPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE) // Sound + vibration
            .build()
    }

    /**
     * Create PendingIntent for service action.
     */
    private fun createServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, PomodoroTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Show completion notification.
     */
    fun showCompletionNotification(isBreak: Boolean, taskTitle: String?) {
        notificationManager.notify(
            NOTIFICATION_ID + 1, // Different ID from foreground notification
            buildCompletionNotification(isBreak, taskTitle)
        )
    }

    /**
     * Cancel all notifications.
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
