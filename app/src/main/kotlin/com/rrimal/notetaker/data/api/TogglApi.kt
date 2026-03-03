package com.rrimal.notetaker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * Toggl Track API v9 interface
 * API Documentation: https://developers.track.toggl.com/docs/
 */
interface TogglApi {
    
    /**
     * Get current user info with related data (projects, workspaces)
     * GET /api/v9/me?with_related_data=true
     */
    @GET("me")
    suspend fun getCurrentUser(
        @Header("Authorization") auth: String,
        @Query("with_related_data") withRelatedData: Boolean = true
    ): Response<TogglUserResponse>
    
    /**
     * Start a new time entry
     * POST /api/v9/workspaces/{workspace_id}/time_entries
     */
    @POST("workspaces/{workspace_id}/time_entries")
    suspend fun startTimeEntry(
        @Header("Authorization") auth: String,
        @Path("workspace_id") workspaceId: Long,
        @Body request: StartTimeEntryRequest
    ): Response<TimeEntryResponse>
    
    /**
     * Stop a running time entry
     * PATCH /api/v9/workspaces/{workspace_id}/time_entries/{time_entry_id}/stop
     */
    @PATCH("workspaces/{workspace_id}/time_entries/{time_entry_id}/stop")
    suspend fun stopTimeEntry(
        @Header("Authorization") auth: String,
        @Path("workspace_id") workspaceId: Long,
        @Path("time_entry_id") timeEntryId: Long
    ): Response<TimeEntryResponse>
    
    /**
     * Get current running time entry
     * GET /api/v9/me/time_entries/current
     */
    @GET("me/time_entries/current")
    suspend fun getCurrentTimeEntry(
        @Header("Authorization") auth: String
    ): Response<TimeEntryResponse?>
}

// ============================================================================
// Request/Response Data Models
// ============================================================================

@Serializable
data class TogglUserResponse(
    val id: Long,
    val email: String,
    @SerialName("fullname") val fullName: String? = null,
    @SerialName("default_workspace_id") val defaultWorkspaceId: Long,
    val projects: List<TogglProject>? = null
)

@Serializable
data class TogglProject(
    val id: Long,
    val name: String,
    @SerialName("workspace_id") val workspaceId: Long,
    val active: Boolean = true,
    val color: String? = null
)

@Serializable
data class StartTimeEntryRequest(
    val description: String,
    @SerialName("project_id") val projectId: Long? = null,
    val tags: List<String> = emptyList(),
    @SerialName("created_with") val createdWith: String = "Note Taker Android App",
    val start: String, // ISO 8601 format: "2026-03-02T14:30:00Z"
    val duration: Long = -1, // -1 means running (started but not stopped)
    @SerialName("workspace_id") val workspaceId: Long // Required in request body per API v9 docs
)

@Serializable
data class TimeEntryResponse(
    val id: Long,
    val description: String? = null,
    @SerialName("project_id") val projectId: Long? = null,
    val tags: List<String>? = null,  // Nullable - Toggl API may return null instead of empty array
    val start: String,
    val stop: String? = null,
    val duration: Long, // In seconds, negative if running
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("at") val updatedAt: String // ISO 8601 timestamp
)
