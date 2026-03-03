package com.rrimal.notetaker.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rrimal.notetaker.data.repository.AgendaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker for syncing configured agenda files from storage to local database.
 * This enables fast querying and recurring task expansion for the agenda view.
 */
@HiltWorker
class OrgFileSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val agendaRepository: AgendaRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("OrgFileSyncWorker", "Starting agenda sync")
            agendaRepository.syncAllFiles()
            Log.d("OrgFileSyncWorker", "Agenda sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("OrgFileSyncWorker", "Agenda sync failed", e)
            Result.retry()
        }
    }
}
