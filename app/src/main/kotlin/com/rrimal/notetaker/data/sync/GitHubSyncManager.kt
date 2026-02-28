package com.rrimal.notetaker.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rrimal.notetaker.data.local.SyncQueueDao
import com.rrimal.notetaker.data.local.SyncQueueEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages syncing local org files to GitHub as backup
 */
@Singleton
class GitHubSyncManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val workManager: WorkManager
) {
    /**
     * Queue a file for GitHub sync
     */
    suspend fun queueFileSync(filename: String, content: String) {
        // Save to sync queue
        syncQueueDao.insert(
            SyncQueueEntity(
                filename = filename,
                content = content,
                createdAt = System.currentTimeMillis(),
                status = "pending"
            )
        )

        // Schedule WorkManager sync job
        scheduleSync()
    }

    /**
     * Schedule a sync job with network connectivity constraint
     */
    private fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<GitHubSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            "github_sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
