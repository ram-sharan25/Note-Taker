package com.rrimal.notetaker.data.sync

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rrimal.notetaker.data.api.CreateFileRequest
import com.rrimal.notetaker.data.api.GitHubApi
import com.rrimal.notetaker.data.auth.AuthManager
import com.rrimal.notetaker.data.local.SyncQueueDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Worker for syncing local org files to GitHub as backup
 */
@HiltWorker
class GitHubSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: GitHubApi,
    private val authManager: AuthManager,
    private val syncQueueDao: SyncQueueDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingItems = syncQueueDao.getAllPending()

        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        var allSucceeded = true

        for (item in pendingItems) {
            try {
                // Update status to syncing
                syncQueueDao.updateStatus(item.id, "syncing")

                // Get auth info
                val token = authManager.accessToken.first()
                val owner = authManager.repoOwner.first()
                val repo = authManager.repoName.first()

                if (token == null || owner == null || repo == null) {
                    // Not authenticated - mark as auth_failed and skip
                    syncQueueDao.updateStatus(item.id, "auth_failed")
                    continue
                }

                // Upload to GitHub under org/ directory
                val path = "org/${item.filename}"
                val content = Base64.encodeToString(item.content.toByteArray(), Base64.NO_WRAP)

                api.createFile(
                    auth = "Bearer $token",
                    owner = owner,
                    repo = repo,
                    path = path,
                    request = CreateFileRequest(
                        message = "Sync ${item.filename} from local",
                        content = content
                    )
                )

                // Success - mark as synced
                syncQueueDao.updateStatus(item.id, "synced")

            } catch (e: HttpException) {
                when (e.code()) {
                    401, 403 -> {
                        // Auth failure - stop retrying
                        syncQueueDao.updateStatus(item.id, "auth_failed")
                    }
                    422 -> {
                        // File already exists (conflict) - consider it synced
                        syncQueueDao.updateStatus(item.id, "synced")
                    }
                    else -> {
                        // Network or other error - mark as failed, will retry
                        syncQueueDao.updateStatus(item.id, "failed")
                        allSucceeded = false
                    }
                }
            } catch (e: Exception) {
                // Other failure - mark as failed, will retry
                syncQueueDao.updateStatus(item.id, "failed")
                allSucceeded = false
            }
        }

        // Clean up synced items
        syncQueueDao.deleteAllSynced()

        return if (allSucceeded) {
            Result.success()
        } else {
            // Some failed - retry later
            Result.retry()
        }
    }
}
