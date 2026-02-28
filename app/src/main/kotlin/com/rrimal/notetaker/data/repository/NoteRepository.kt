package com.rrimal.notetaker.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rrimal.notetaker.data.local.PendingNoteDao
import com.rrimal.notetaker.data.local.PendingNoteEntity
import com.rrimal.notetaker.data.local.SubmissionDao
import com.rrimal.notetaker.data.local.SubmissionEntity
import com.rrimal.notetaker.data.storage.FileEntry
import com.rrimal.notetaker.data.storage.GitHubStorageBackend
import com.rrimal.notetaker.data.storage.LocalOrgStorageBackend
import com.rrimal.notetaker.data.storage.StorageBackend
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.StorageMode
import com.rrimal.notetaker.data.storage.SubmitResult
import com.rrimal.notetaker.data.worker.NoteUploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val githubBackend: GitHubStorageBackend,
    private val localOrgBackend: LocalOrgStorageBackend,
    private val storageConfigManager: StorageConfigManager,
    private val submissionDao: SubmissionDao,
    private val pendingNoteDao: PendingNoteDao,
    private val workManager: WorkManager
) {
    val recentSubmissions: Flow<List<SubmissionEntity>> = submissionDao.getRecent()
    val pendingCount: Flow<Int> = pendingNoteDao.getPendingCount()

    /**
     * Get the current storage backend based on configuration
     */
    private suspend fun getCurrentBackend(): StorageBackend {
        return when (storageConfigManager.storageMode.first()) {
            StorageMode.GITHUB_MARKDOWN -> githubBackend
            StorageMode.LOCAL_ORG_FILES -> localOrgBackend
        }
    }

    suspend fun submitNote(text: String): Result<SubmitResult> {
        val backend = getCurrentBackend()

        // GitHub backend: queue-first approach with WorkManager retry
        if (backend.storageMode == StorageMode.GITHUB_MARKDOWN) {
            return submitNoteGitHub(text)
        }

        // Local backend: direct submission (handles retry internally if needed)
        return submitNoteLocal(text)
    }

    /**
     * Submit note using GitHub backend with queue-first approach
     */
    private suspend fun submitNoteGitHub(text: String): Result<SubmitResult> {
        val now = ZonedDateTime.now()
        val filename = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"))

        // Always save to local queue first
        val noteId = pendingNoteDao.insert(
            PendingNoteEntity(
                text = text,
                filename = filename,
                createdAt = System.currentTimeMillis()
            )
        )

        return try {
            val result = githubBackend.submitNote(text)

            if (result.isSuccess) {
                when (result.getOrNull()) {
                    SubmitResult.SENT -> {
                        // Success — record in submissions and remove from queue
                        submissionDao.insert(
                            SubmissionEntity(
                                timestamp = System.currentTimeMillis(),
                                preview = text.take(50),
                                success = true
                            )
                        )
                        pendingNoteDao.delete(noteId)
                    }
                    SubmitResult.AUTH_FAILED -> {
                        // Auth failure — delete the pending note
                        pendingNoteDao.delete(noteId)
                    }
                    else -> {}
                }
            } else {
                // Other failure — schedule WorkManager retry
                scheduleRetry()
                return Result.success(SubmitResult.QUEUED)
            }

            result
        } catch (e: Exception) {
            // Failure — schedule WorkManager retry
            scheduleRetry()
            Result.success(SubmitResult.QUEUED)
        }
    }

    /**
     * Submit note using local org backend
     */
    private suspend fun submitNoteLocal(text: String): Result<SubmitResult> {
        return try {
            val result = localOrgBackend.submitNote(text)

            if (result.isSuccess && result.getOrNull() == SubmitResult.SENT) {
                // Record in submissions
                submissionDao.insert(
                    SubmissionEntity(
                        timestamp = System.currentTimeMillis(),
                        preview = text.take(50),
                        success = true
                    )
                )
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<NoteUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            "note_upload",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun fetchDirectoryContents(path: String): Result<List<FileEntry>> {
        val backend = getCurrentBackend()
        return backend.fetchDirectoryContents(path)
    }

    suspend fun fetchFileContent(path: String): Result<String> {
        val backend = getCurrentBackend()
        return backend.fetchFileContent(path)
    }

    suspend fun fetchCurrentTopic(): String? {
        val backend = getCurrentBackend()
        return backend.fetchCurrentTopic()
    }

}
