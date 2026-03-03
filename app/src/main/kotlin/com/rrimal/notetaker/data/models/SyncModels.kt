package com.rrimal.notetaker.data.models

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * JSON format for state change sync
 * Written by mobile app, read by Emacs
 * 
 * Example filename: 56C3785A-5616-424F-8A3E-99F3C2474332_DONE_1709467200000.json
 * 
 * JSON content:
 * {
 *   "id": "56C3785A-5616-424F-8A3E-99F3C2474332",
 *   "status": "DONE",
 *   "changed_at": "2026-03-02T12:35:00-05:00"
 * }
 */
data class StateChangeSyncMessage(
    val originId: String,      // ORIGIN_ID from org headline
    val newState: String,      // TODO, DONE, IN-PROGRESS, etc.
    val oldState: String?,     // Previous TODO state (null if unknown)
    val timestamp: Long        // Epoch milliseconds
)

/**
 * Build JSON string for state change sync
 * Uses manual JSON construction to avoid kotlinx.serialization dependency
 *
 * Example output:
 * {
 *   "id": "56C3785A-...",
 *   "status": "IN-PROGRESS",
 *   "old_status": "TODO",
 *   "changed_at": "2026-03-02T12:35:00-05:00"
 * }
 *
 * @param originId ORIGIN_ID property from org headline
 * @param newState New TODO state (TODO, DONE, etc.)
 * @param oldState Previous TODO state (null if unknown)
 * @param timestamp Epoch milliseconds
 * @return JSON string
 */
fun buildStateChangeJson(originId: String, newState: String, timestamp: Long, oldState: String? = null): String {
    val iso8601 = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    val oldStatusLine = if (oldState != null) "\n    \"old_status\": \"$oldState\"," else ""

    return """
        {
            "id": "$originId",
            "status": "$newState",$oldStatusLine
            "changed_at": "$iso8601"
        }
    """.trimIndent()
}

/**
 * Generate sync filename from state change
 * Format: <origin-id>_<new-state>_<timestamp>.json
 * 
 * Example: 56C3785A-5616-424F-8A3E-99F3C2474332_DONE_1709467200000.json
 */
fun generateSyncFilename(originId: String, newState: String, timestamp: Long): String {
    return "${originId}_${newState}_$timestamp.json"
}

/**
 * Validate sync filename format
 * Returns true if filename matches expected pattern
 */
fun isValidSyncFilename(filename: String): Boolean {
    // Pattern: <UUID>_<STATE>_<TIMESTAMP>.json
    // UUID: 8-4-4-4-12 hex digits with hyphens (or without)
    // STATE: All caps with optional hyphens
    // TIMESTAMP: Digits only
    val pattern = Regex("^[A-Fa-f0-9-]+_[A-Z-]+_\\d+\\.json$")
    return filename.matches(pattern)
}
