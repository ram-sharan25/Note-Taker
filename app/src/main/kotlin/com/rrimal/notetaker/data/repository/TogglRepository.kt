package com.rrimal.notetaker.data.repository

import android.util.Base64
import android.util.Log
import com.rrimal.notetaker.data.api.StartTimeEntryRequest
import com.rrimal.notetaker.data.api.TimeEntryResponse
import com.rrimal.notetaker.data.api.TogglApi
import com.rrimal.notetaker.data.api.TogglProject
import com.rrimal.notetaker.data.api.TogglUserResponse
import com.rrimal.notetaker.data.preferences.TogglPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Toggl Track integration.
 * 
 * Handles:
 * - Time entry start/stop
 * - Project syncing
 * - Authentication
 * 
 * Architecture:
 * - Similar to Emacs toggl.el implementation
 * - Automatic time tracking on TODO state changes
 * - Uses Basic Auth (API token + "api_token" string)
 */
@Singleton
class TogglRepository @Inject constructor(
    private val togglApi: TogglApi,
    private val prefsManager: TogglPreferencesManager
) {
    companion object {
        private const val TAG = "TogglRepository"
    }
    
    // ============================================================================
    // Authentication & User Info
    // ============================================================================
    
    /**
     * Generate Basic Auth header from API token.
     * Format: "Basic base64(token:api_token)"
     */
    private fun getAuthHeader(apiToken: String): String {
        val credentials = "$apiToken:api_token"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }
    
    /**
     * Fetch user info and projects from Toggl API.
     * Equivalent to: GET /api/v9/me?with_related_data=true
     */
    suspend fun fetchUserAndProjects(): Result<TogglUserResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val apiToken = prefsManager.apiToken.first() 
                ?: throw IllegalStateException("No Toggl API token configured")
            
            val response = togglApi.getCurrentUser(
                auth = getAuthHeader(apiToken),
                withRelatedData = true
            )
            
            if (response.isSuccessful && response.body() != null) {
                val userData = response.body()!!
                
                // Save workspace ID if not set
                if (prefsManager.workspaceId.first() == null) {
                    prefsManager.saveWorkspace(userData.defaultWorkspaceId)
                }
                
                prefsManager.updateLastSync()
                Log.d(TAG, "Fetched ${userData.projects?.size ?: 0} projects")
                
                userData
            } else {
                throw Exception("Failed to fetch user data: ${response.code()} ${response.message()}")
            }
        }
    }
    
    /**
     * Get active projects (filters out archived projects).
     * Equivalent to Emacs: rsr/update-toggl-projects
     */
    suspend fun getActiveProjects(): Result<List<TogglProject>> = withContext(Dispatchers.IO) {
        runCatching {
            val userData = fetchUserAndProjects().getOrThrow()
            userData.projects?.filter { it.active } ?: emptyList()
        }
    }
    
    // ============================================================================
    // Time Entry Management
    // ============================================================================
    
    /**
     * Start a new time entry.
     * Equivalent to Emacs: toggl-start-time-entry
     * 
     * @param description Task description (e.g., headline title)
     * @param projectId Optional project ID (uses default if null)
     * @param tags Optional tags array
     * @return Time entry response with ID
     */
    suspend fun startTimeEntry(
        description: String,
        projectId: Long? = null,
        tags: List<String> = emptyList()
    ): Result<TimeEntryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val apiToken = prefsManager.apiToken.first() 
                ?: throw IllegalStateException("No Toggl API token configured")
            
            val workspaceId = prefsManager.workspaceId.first() 
                ?: throw IllegalStateException("No workspace configured")
            
            val effectiveProjectId = projectId ?: prefsManager.defaultProjectId.first()
            
            val now = Instant.now()
            val startTime = DateTimeFormatter.ISO_INSTANT.format(now)
            
            val request = StartTimeEntryRequest(
                description = description,
                projectId = effectiveProjectId,
                tags = tags,
                createdWith = "Note Taker Android App",
                start = startTime,
                workspaceId = workspaceId,
                duration = -1 // -1 indicates running timer
            )
            
            val response = togglApi.startTimeEntry(
                auth = getAuthHeader(apiToken),
                workspaceId = workspaceId,
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val entry = response.body()!!
                Log.d(TAG, "Started time entry: ${entry.id} - $description")
                entry
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e(TAG, "Failed to start time entry: ${response.code()} - $errorBody")
                throw Exception("Failed to start time entry: ${response.code()} ${response.message()} - $errorBody")
            }
        }
    }
    
    /**
     * Stop the currently running time entry.
     * Equivalent to Emacs: toggl-stop-time-entry (via org-toggl-clock-out)
     * 
     * @param timeEntryId ID of the time entry to stop
     * @return Stopped time entry response
     */
    suspend fun stopTimeEntry(timeEntryId: Long): Result<TimeEntryResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val apiToken = prefsManager.apiToken.first() 
                ?: throw IllegalStateException("No Toggl API token configured")
            
            val workspaceId = prefsManager.workspaceId.first() 
                ?: throw IllegalStateException("No workspace configured")
            
            val response = togglApi.stopTimeEntry(
                auth = getAuthHeader(apiToken),
                workspaceId = workspaceId,
                timeEntryId = timeEntryId
            )
            
            if (response.isSuccessful && response.body() != null) {
                val entry = response.body()!!
                Log.d(TAG, "Stopped time entry: ${entry.id}")
                entry
            } else {
                throw Exception("Failed to stop time entry: ${response.code()} ${response.message()}")
            }
        }
    }
    
    /**
     * Get the currently running time entry (if any).
     * 
     * @return Running time entry, or null if no timer active
     */
    suspend fun getCurrentTimeEntry(): Result<TimeEntryResponse?> = withContext(Dispatchers.IO) {
        runCatching {
            val apiToken = prefsManager.apiToken.first() 
                ?: throw IllegalStateException("No Toggl API token configured")
            
            val response = togglApi.getCurrentTimeEntry(
                auth = getAuthHeader(apiToken)
            )
            
            if (response.isSuccessful) {
                response.body()
            } else {
                throw Exception("Failed to get current time entry: ${response.code()} ${response.message()}")
            }
        }
    }
    
    // ============================================================================
    // Helper Methods
    // ============================================================================
    
    /**
     * Check if time tracking is enabled and configured.
     */
    suspend fun isEnabled(): Boolean {
        return prefsManager.isEnabled.first() && prefsManager.isConfigured.first()
    }
    
    /**
     * Validate API token by fetching user info.
     * 
     * @return true if token is valid, false otherwise
     */
    suspend fun validateToken(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            fetchUserAndProjects().isSuccess
        }
    }
}
