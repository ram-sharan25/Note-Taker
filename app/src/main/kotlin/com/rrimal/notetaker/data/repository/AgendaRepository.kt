package com.rrimal.notetaker.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.rrimal.notetaker.data.local.*
import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.data.orgmode.OrgParser
import com.rrimal.notetaker.data.orgmode.OrgTimestampParser
import com.rrimal.notetaker.data.orgmode.OrgWriter
import com.rrimal.notetaker.data.preferences.AgendaConfigManager
import com.rrimal.notetaker.data.storage.LocalFileManager
import com.rrimal.notetaker.data.storage.PhoneInboxStructure
import com.rrimal.notetaker.ui.screens.agenda.AgendaItem
import com.rrimal.notetaker.ui.screens.agenda.TimeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaRepository @Inject constructor(
    private val database: AppDatabase,
    private val localFileManager: LocalFileManager,
    private val agendaConfigManager: AgendaConfigManager,
    private val orgParser: OrgParser,
    private val timestampParser: OrgTimestampParser,
    private val orgWriter: OrgWriter,
    private val togglRepository: TogglRepository
) {
    private val noteDao = database.noteDao()
    private val timestampDao = database.orgTimestampDao()
    private val planningDao = database.notePlanningDao()
    private val fileMetadataDao = database.fileMetadataDao()
    private val todoKeywordsDao = database.todoKeywordsDao()
    private val propertiesConverter = PropertiesConverter()
    
    // Toggl time entry tracking (maps headlineId to Toggl time entry ID)
    private val activeTogglEntries = mutableMapOf<String, Long>()

    companion object {
        private const val TAG = "AgendaRepository"
        
        /**
         * Format timestamp to time string (HH:mm) if time component exists.
         * Returns null if timestamp is 0 or only has date component.
         */
        fun formatTime(timestamp: Long): String? {
            if (timestamp == 0L) return null
            
            val dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
            )
            
            // Only show time if hours/minutes are specified (not just date)
            return if (dateTime.hour != 0 || dateTime.minute != 0) {
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            } else {
                null
            }
        }
        
        /**
         * Extract plain ID from org-mode ID property value.
         * Handles both [[id:UUID]] and plain UUID formats.
         */
        private fun extractPlainId(idValue: String): String {
            return when {
                idValue.startsWith("[[id:") && idValue.endsWith("]]") -> {
                    // Extract UUID from [[id:UUID]] format
                    idValue.substring(5, idValue.length - 2)
                }
                else -> idValue // Already plain format
            }
        }
    }

    suspend fun getAgendaItems(days: Int): Flow<List<AgendaItem>> {
        Log.d(TAG, "=== getAgendaItems START ===")
        Log.d(TAG, "Requested days: $days")

        // Sync TODO keywords config first
        syncTodoKeywordsConfig()

        // Check both agenda.org and quick.org for external changes (Syncthing, Emacs, etc.)
        val filesToShow = listOf(PhoneInboxStructure.AGENDA_FILENAME, PhoneInboxStructure.QUICK_FILE_PATH)
        for (filename in filesToShow) {
            Log.d(TAG, "Checking file: $filename")
            try {
                val needsSync = checkIfFileNeedsSync(filename)
                Log.d(TAG, "File $filename needs sync: $needsSync")
                if (needsSync) {
                    Log.d(TAG, "File $filename changed externally, syncing before query")
                    syncFileToDatabase(filename)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file $filename for changes", e)
                // Continue even if one file fails — show what we have
            }
        }

        val today = LocalDate.now()
        Log.d(TAG, "=== Query Setup ===")
        Log.d(TAG, "Querying notes from files: $filesToShow")
        Log.d(TAG, "Today's date: $today")

        return noteDao.getAgendaItems(filesToShow).map { results ->
            Log.d(TAG, "=== Query Results ===")
            Log.d(TAG, "Query returned ${results.size} results from database")
            results.forEachIndexed { index, result ->
                Log.d(TAG, "  [$index] ${result.note.todoState ?: "(no state)"} ${result.note.title} - ts=${result.timestamp} (${java.time.Instant.ofEpochMilli(result.timestamp)}) type=${result.timeType}")
            }
            val agendaItems = buildAgendaList(results, today, days)
            Log.d(TAG, "=== Built Agenda Items ===")
            Log.d(TAG, "Built ${agendaItems.size} agenda items after processing")
            agendaItems.forEachIndexed { index, item ->
                when (item) {
                    is AgendaItem.Day -> Log.d(TAG, "  [$index] Day: ${item.formattedDate}")
                    is AgendaItem.Note -> Log.d(TAG, "  [$index] Note: ${item.todoState ?: "(no state)"} ${item.title} - ts=${item.timestamp} timeType=${item.timeType}")
                }
            }
            Log.d(TAG, "=== getAgendaItems END ===")
            agendaItems
        }
    }

    suspend fun getAgendaItemsFiltered(days: Int, statusFilter: Set<String>): Flow<List<AgendaItem>> {
        // Sync TODO keywords config first
        syncTodoKeywordsConfig()

        // Use the same file list as getAgendaItems so filter searches the same data set
        val files = listOf(PhoneInboxStructure.AGENDA_FILENAME, PhoneInboxStructure.QUICK_FILE_PATH)

        for (filename in files) {
            try {
                val needsSync = checkIfFileNeedsSync(filename)
                if (needsSync) {
                    Log.d(TAG, "File $filename changed externally, syncing before query")
                    syncFileToDatabase(filename)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file $filename for changes", e)
            }
        }

        val today = LocalDate.now()

        val sourceFlow = if (statusFilter.isEmpty()) {
            noteDao.getAgendaItems(files)
        } else {
            noteDao.getAgendaItemsFiltered(files, statusFilter.toList())
        }

        return sourceFlow.map { results ->
            buildAgendaList(results, today, days)
        }
    }

    private fun buildAgendaList(
        results: List<AgendaNoteQueryResult>,
        today: LocalDate,
        days: Int
    ): List<AgendaItem> {
        val agendaItems = mutableListOf<AgendaItem.Note>()
        var idCounter = 1L

        // Track seen noteIds to prevent duplicates (in case same note has both SCHEDULED and DEADLINE)
        val seenNoteIds = mutableSetOf<Long>()

        results.forEach { result ->
            // Skip duplicates
            if (result.note.id in seenNoteIds) {
                return@forEach
            }
            seenNoteIds.add(result.note.id)
            
            // Determine the display timestamp and time type
            val displayTimestamp = if (result.timestamp > 0) result.timestamp else 0L
            val displayTimeType = when (result.timeType) {
                "SCHEDULED" -> TimeType.SCHEDULED
                "DEADLINE" -> TimeType.DEADLINE
                else -> TimeType.SCHEDULED // Default for items without timestamps
            }
            
            // Extract formatted time if timestamp exists
            val formattedTime = if (result.timestamp > 0) {
                val dateTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(result.timestamp),
                    java.time.ZoneId.systemDefault()
                )
                // Only show time if hours/minutes are specified (not just date)
                if (dateTime.hour != 0 || dateTime.minute != 0) {
                    dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    null
                }
            } else {
                null
            }
            
            // Determine if item is overdue (only for items with timestamps)
            val isOverdue = if (result.timestamp > 0) {
                val itemDate = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(result.timestamp),
                    java.time.ZoneId.systemDefault()
                ).toLocalDate()
                itemDate.isBefore(today)
            } else {
                false
            }
            
            agendaItems.add(AgendaItem.Note(
                id = idCounter++,
                noteId = result.note.id,
                title = result.note.title,
                todoState = result.note.todoState,
                priority = result.note.priority,
                timeType = displayTimeType,
                timestamp = displayTimestamp,
                formattedTime = formattedTime,
                isOverdue = isOverdue,
                tags = result.note.tags.split(":").filter { it.isNotBlank() },
                filename = result.note.filename,
                properties = result.note.properties
            ))
        }

        // Group by sections and add day headers
        return buildList {
            val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
            
            // Stateful items (IN-PROGRESS/HOLD/WAITING) first
            val statefulItems = agendaItems.filter { 
                it.todoState in listOf("IN-PROGRESS", "HOLD", "WAITING") 
            }
            
            // Items with timestamps (sorted by timestamp)
            val timedItems = agendaItems.filter { 
                it.timestamp > 0 && it.todoState !in listOf("IN-PROGRESS", "HOLD", "WAITING")
            }.sortedBy { it.timestamp }
            
            // Items without timestamps and not stateful
            val untimedItems = agendaItems.filter {
                it.timestamp == 0L && it.todoState !in listOf("IN-PROGRESS", "HOLD", "WAITING")
            }
            
            // Add "Today" header
            add(AgendaItem.Day(idCounter++, today, today.format(dateFormatter)))
            
            // Add all items: IN-PROGRESS first, then HOLD/WAITING, then timed, then untimed
            addAll(statefulItems.sortedWith(compareBy(
                { if (it.todoState == "IN-PROGRESS") 0 else 1 },
                { it.title }
            )))
            addAll(timedItems)
            addAll(untimedItems.sortedBy { it.title })
        }
    }

    private fun expandRecurringTimestamp(
        timestamp: OrgTimestampEntity,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDateTime> {
        val baseDate = LocalDate.of(timestamp.year, timestamp.month, timestamp.day)
        val baseTime = LocalTime.of(timestamp.hour ?: 0, timestamp.minute ?: 0)

        if (timestamp.repeaterType == null) {
            return if (!baseDate.isAfter(endDate)) {
                listOf(baseDate.atTime(baseTime))
            } else {
                emptyList()
            }
        }

        val result = mutableListOf<LocalDateTime>()
        var current = baseDate
        val value = (timestamp.repeaterValue ?: 1).toLong()

        if (current.isBefore(startDate)) {
            val unit = when (timestamp.repeaterUnit) {
                "d" -> ChronoUnit.DAYS
                "w" -> ChronoUnit.WEEKS
                "m" -> ChronoUnit.MONTHS
                "y" -> ChronoUnit.YEARS
                else -> null
            }
            if (unit != null) {
                val diff = unit.between(current, startDate)
                if (diff > 0) {
                    current = current.plus((diff / value) * value, unit)
                    if (current.isBefore(startDate)) {
                        current = current.plus(value, unit)
                    }
                }
            }
        }

        while (!current.isAfter(endDate)) {
            if (!current.isBefore(baseDate)) { 
                result.add(current.atTime(baseTime))
            }

            current = when (timestamp.repeaterUnit) {
                "h" -> {
                    val currentDateTime = current.atTime(baseTime)
                    val nextDateTime = currentDateTime.plusHours(value)
                    nextDateTime.toLocalDate()
                }
                "d" -> current.plusDays(value)
                "w" -> current.plusWeeks(value)
                "m" -> current.plusMonths(value)
                "y" -> current.plusYears(value)
                else -> break
            }
            if (value <= 0) break
        }

        return result
    }

    /**
     * Check if file needs syncing by comparing current hash with stored hash
     * Returns true if file changed externally, false if unchanged or on error
     */
    private suspend fun checkIfFileNeedsSync(filename: String): Boolean {
        return try {
            // Read from phone_inbox folder for all known phone inbox files
            val contentResult = if (filename == PhoneInboxStructure.AGENDA_FILENAME ||
                                    filename == PhoneInboxStructure.QUICK_FILE_PATH) {
                localFileManager.readFileFromPhoneInbox(filename)
            } else {
                // Fallback for other files (if any)
                localFileManager.readFile(filename)
            }
            
            if (contentResult.isFailure) {
                Log.w(TAG, "Could not read file $filename for hash check", contentResult.exceptionOrNull())
                return false // File doesn't exist or can't be read, don't sync
            }

            val content = contentResult.getOrThrow()
            val currentHash = calculateHash(content)
            val metadata = fileMetadataDao.getByFilename(filename)

            // If no metadata, file needs initial sync
            if (metadata == null) {
                Log.d(TAG, "No metadata for $filename, needs sync")
                return true
            }

            // If hash differs, file changed externally
            val needsSync = metadata.hash != currentHash
            if (needsSync) {
                Log.d(TAG, "File $filename hash mismatch (stored: ${metadata.hash.take(8)}..., current: ${currentHash.take(8)}...)")
            }
            needsSync
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file needs sync: $filename", e)
            false // On error, don't sync to avoid disrupting UI
        }
    }

    /**
     * Clear all database data and force full re-sync from files
     * This is called when user explicitly clicks refresh button
     */
    suspend fun clearAndResyncAll() {
        Log.d(TAG, "=== clearAndResyncAll START ===")
        
        // Sync TODO keywords config to database
        syncTodoKeywordsConfig()
        
        val files = agendaConfigManager.agendaFiles.first()
        Log.d(TAG, "Clearing all database data for ${files.size} agenda files")

        // Clear ALL agenda-related data from database
        database.withTransaction {
            val allMetadata = fileMetadataDao.getAll()
            
            // Delete all notes, timestamps, and metadata for all files
            for (metadata in allMetadata) {
                Log.d(TAG, "Clearing data for file: ${metadata.filename}")
                timestampDao.deleteByFilename(metadata.filename)
                noteDao.deleteByFilename(metadata.filename)
                fileMetadataDao.deleteByFilename(metadata.filename)
            }
        }

        Log.d(TAG, "Database cleared, now re-syncing all files from storage")
        
        // Force full re-sync of all configured files
        for (filename in files) {
            try {
                syncFileToDatabase(filename, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync $filename", e)
            }
        }
        
        Log.d(TAG, "=== clearAndResyncAll END ===")
    }

    suspend fun syncAllFiles(force: Boolean = false) {
        // Sync TODO keywords config to database
        syncTodoKeywordsConfig()
        
        val files = agendaConfigManager.agendaFiles.first()

        // Clean up database entries for files no longer in the agenda list
        database.withTransaction {
            val allMetadata = fileMetadataDao.getAll()
            val filesToRemove = allMetadata.filter { it.filename !in files }

            for (metadata in filesToRemove) {
                Log.d(TAG, "Removing stale file from database: ${metadata.filename}")
                timestampDao.deleteByFilename(metadata.filename)
                noteDao.deleteByFilename(metadata.filename)
                fileMetadataDao.deleteByFilename(metadata.filename)
            }
        }

        // Sync all configured files
        for (filename in files) {
            try {
                syncFileToDatabase(filename, force)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync $filename", e)
            }
        }
    }
    
    /**
     * Sync TODO keywords configuration from AgendaConfigManager to database
     */
    private suspend fun syncTodoKeywordsConfig() {
        val keywords = agendaConfigManager.todoKeywords.first()
        Log.d(TAG, "Syncing TODO keywords config: $keywords")
        todoKeywordsDao.insertConfig(
            TodoKeywordsConfigEntity(
                id = 0,
                sequence = keywords
            )
        )
    }

    /**
     * Sync only quick.org into the database.
     * Called after writing a new instant task so it's immediately queryable.
     */
    suspend fun syncQuickFile() {
        try {
            syncFileToDatabase(PhoneInboxStructure.QUICK_FILE_PATH, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync quick.org", e)
        }
    }

    suspend fun syncFileToDatabase(filename: String, force: Boolean = false) {
        Log.d(TAG, "syncFileToDatabase: filename=$filename, force=$force")

        // Read from phone_inbox for all known phone inbox files
        val contentResult = if (filename == PhoneInboxStructure.AGENDA_FILENAME ||
                                filename == PhoneInboxStructure.QUICK_FILE_PATH) {
            localFileManager.readFileFromPhoneInbox(filename)
        } else {
            // Fallback for old behavior (if called with different filename)
            localFileManager.readFile(filename)
        }
        
        if (contentResult.isFailure) {
            val error = contentResult.exceptionOrNull()
            Log.e(TAG, "Could not read file $filename: ${error?.message}", error)
            return
        }

        val content = contentResult.getOrThrow()
        val hash = calculateHash(content)

        val metadata = fileMetadataDao.getByFilename(filename)

        // Skip if not forced and hash matches (file hasn't changed)
        if (!force && metadata != null && metadata.hash == hash) {
            Log.d(TAG, "File $filename already synced and hash matches (skipping)")
            return
        }

        Log.d(TAG, "Syncing file $filename (${content.length} bytes)")
        Log.d(TAG, "File content preview (first 500 chars):\n${content.take(500)}")
        val orgFile = orgParser.parse(content)
        // Use orgFile.headlines (top-level only), NOT getAllHeadlines() which includes nested
        // insertNoteRecursively already handles children, so using getAllHeadlines causes duplicates
        val headlines = orgFile.headlines
        Log.d(TAG, "Parsed ${headlines.size} top-level headlines from $filename")
        headlines.forEachIndexed { index, headline ->
            Log.d(TAG, "  [$index] Level ${headline.level}: ${headline.todoState ?: "(no state)"} ${headline.title}")
            Log.d(TAG, "       SCHEDULED: ${headline.scheduled}")
            Log.d(TAG, "       DEADLINE: ${headline.deadline}")
            Log.d(TAG, "       Children: ${headline.children.size}")
        }

        database.withTransaction {
            // Clear existing data for this file
            // IMPORTANT: Delete notes FIRST (cascades to note_planning), THEN timestamps
            // Deleting timestamps first would violate foreign key constraints in note_planning
            noteDao.deleteByFilename(filename)
            timestampDao.deleteByFilename(filename)

            // Insert all headlines
            headlines.forEachIndexed { index, headline ->
                insertNoteRecursively(headline, filename, index, null)
            }

            // Update metadata
            fileMetadataDao.insert(
                FileMetadataEntity(
                    filename = filename,
                    lastSynced = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis(),
                    hash = hash
                )
            )
        }

        Log.d(TAG, "Successfully synced $filename")
    }

    private suspend fun insertNoteRecursively(
        headline: OrgNode.Headline,
        filename: String,
        position: Int,
        parentId: Long?
    ): Long {
        // Use ORIGIN_ID if available, fallback to SOURCE_ID, ID, or generate new UUID
        // Extract plain UUID from org-mode link format [[id:UUID]] if present
        val rawId = headline.properties["ORIGIN_ID"] 
            ?: headline.properties["SOURCE_ID"] 
            ?: headline.properties["ID"] 
            ?: UUID.randomUUID().toString()
        val headlineId = extractPlainId(rawId)
        
        Log.d(TAG, "insertNoteRecursively: level=${headline.level}, title=${headline.title}, todoState=${headline.todoState}, scheduled=${headline.scheduled}, deadline=${headline.deadline}, headlineId=$headlineId")
        Log.d(TAG, "  Properties from headline: ${headline.properties}")
        
        val noteEntity = NoteEntity(
            filename = filename,
            headlineId = headlineId,
            level = headline.level,
            title = headline.title,
            todoState = headline.todoState,
            priority = headline.priority,
            tags = headline.tags.joinToString(":"),
            body = headline.body,
            parentId = parentId,
            position = position,
            lastModified = System.currentTimeMillis(),
            properties = headline.properties
        )
        
        val noteId = noteDao.insert(noteEntity)
        Log.d(TAG, "  Inserted note with id=$noteId")
        
        val scheduledTs = timestampParser.parse(headline.scheduled)
        val deadlineTs = timestampParser.parse(headline.deadline)
        val closedTs = timestampParser.parse(headline.closed)
        
        Log.d(TAG, "  Parsed timestamps: scheduled=$scheduledTs, deadline=$deadlineTs, closed=$closedTs")
        
        val scheduledId = scheduledTs?.let { timestampDao.insert(it) }
        val deadlineId = deadlineTs?.let { timestampDao.insert(it) }
        val closedId = closedTs?.let { timestampDao.insert(it) }
        
        if (scheduledId != null || deadlineId != null || closedId != null) {
            Log.d(TAG, "  Creating planning entry: scheduledId=$scheduledId, deadlineId=$deadlineId, closedId=$closedId")
            planningDao.insert(
                NotePlanningEntity(
                    noteId = noteId,
                    scheduledTimestampId = scheduledId,
                    deadlineTimestampId = deadlineId,
                    closedTimestampId = closedId
                )
            )
        } else {
            Log.d(TAG, "  No planning entry needed (no timestamps)")
        }
        
        headline.children.forEachIndexed { childIndex, childHeadline ->
            insertNoteRecursively(childHeadline, filename, childIndex, noteId)
        }
        
        return noteId
    }

    /**
     * Directly update the TODO state of a quick task in quick.org.
     * Matches the headline by its CREATED property (stable identifier for quick tasks).
     * Also sets/clears ENDED and CLOSED when transitioning to/from DONE.
     */
    private suspend fun updateQuickTaskStateInFile(note: NoteEntity, newState: String) {
        val createdIso = note.properties["CREATED"] ?: run {
            Log.w(TAG, "Quick task '${note.title}' has no CREATED property — cannot update file")
            return
        }
        val readResult = localFileManager.readFileFromPhoneInbox(PhoneInboxStructure.QUICK_FILE_PATH)
        if (readResult.isFailure) {
            Log.e(TAG, "Failed to read quick.org for state update", readResult.exceptionOrNull())
            return
        }
        val orgFile = orgParser.parse(readResult.getOrThrow())
        val now = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val updatedHeadlines = orgFile.headlines.map { headline ->
            if (headline.properties["CREATED"] == createdIso) {
                val updatedProps = headline.properties.toMutableMap().apply {
                    if (newState == "DONE") put("ENDED", now) else remove("ENDED")
                }
                headline.copy(
                    todoState = newState,
                    closed = if (newState == "DONE") now else null,
                    properties = updatedProps
                )
            } else headline
        }
        val writeResult = localFileManager.writeFileToPhoneInbox(
            PhoneInboxStructure.QUICK_FILE_PATH,
            orgWriter.writeFile(orgFile.copy(headlines = updatedHeadlines))
        )
        if (writeResult.isFailure) {
            Log.e(TAG, "Failed to write quick.org after state update", writeResult.exceptionOrNull())
        } else {
            Log.d(TAG, "  Updated quick.org: '${note.title}' -> $newState")
        }
    }

    private fun calculateHash(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Update TODO state of a note (v0.9.0 JSON Sync version).
     * 
     * Flow:
     * 1. Mobile writes JSON to sync/ directory
     * 2. Syncthing syncs to desktop
     * 3. Emacs detects new file (file-notify-add-watch)
     * 4. Emacs exports agenda.org (M-m s e)
     * 5. Syncthing syncs agenda.org back to mobile
     * 6. Mobile detects change and re-reads (automatic hash check)
     * 
     * @param noteId Database ID of the note
     * @param newState New TODO state (TODO, IN-PROGRESS, DONE, etc.)
     * @param projectId Optional Toggl project ID (for session-only override)
     * @return Result with Unit on success, error message on failure
     */
    suspend fun updateTodoState(noteId: Long, newState: String, projectId: Long? = null): Result<Unit> = runCatching {
        Log.d(TAG, "=== updateTodoState START (JSON SYNC) ===")
        Log.d(TAG, "  noteId: $noteId, newState: $newState")
        
        // 1. Get note from database
        val note = noteDao.getById(noteId) 
            ?: throw IllegalArgumentException("Note not found: $noteId")
        
        Log.d(TAG, "  Note found:")
        Log.d(TAG, "    filename: ${note.filename}")
        Log.d(TAG, "    headlineId: ${note.headlineId}")
        Log.d(TAG, "    title: ${note.title}")
        Log.d(TAG, "    currentState: ${note.todoState}")
        
        val oldState = note.todoState

        // 2. Optimistically update local database
        //    This provides immediate UI feedback without waiting for Emacs
        database.withTransaction {
            noteDao.updateState(noteId, newState)
        }
        Log.d(TAG, "  Database updated optimistically")

        // 3. Quick tasks (quick.org) are app-only — Emacs doesn't manage them.
        //    Update the file directly instead of writing a JSON sync message.
        if (note.filename == PhoneInboxStructure.QUICK_FILE_PATH) {
            Log.d(TAG, "  Quick task — updating quick.org directly (no JSON sync)")
            updateQuickTaskStateInFile(note, newState)
            handleTogglStateChange(note, oldState, newState, projectId)
            Log.d(TAG, "=== updateTodoState END (quick task, file updated) ===")
            return@runCatching
        }

        // 4. Build JSON sync message (include oldState for Emacs logbook)
        val timestamp = System.currentTimeMillis()
        val json = com.rrimal.notetaker.data.models.buildStateChangeJson(
            originId = note.headlineId,
            newState = newState,
            timestamp = timestamp,
            oldState = oldState
        )

        // 5. Generate filename: <origin-id>_<state>_<timestamp>.json
        val filename = com.rrimal.notetaker.data.models.generateSyncFilename(note.headlineId, newState, timestamp)

        Log.d(TAG, "  Writing JSON sync file: sync/$filename")
        Log.d(TAG, "  JSON content: $json")

        // 6. Write JSON to sync/ directory
        val writeResult = localFileManager.writeSyncJson(filename, json)
        if (writeResult.isFailure) {
            // Rollback optimistic update
            Log.e(TAG, "  Failed to write sync file, rolling back", writeResult.exceptionOrNull())
            database.withTransaction {
                noteDao.updateState(noteId, oldState ?: "TODO")
            }
            throw writeResult.exceptionOrNull()
                ?: java.io.IOException("Failed to write sync file")
        }

        Log.d(TAG, "  Successfully wrote sync file")

        // 7. Handle Toggl integration (if enabled)
        //    This happens immediately on mobile, doesn't wait for Emacs
        handleTogglStateChange(note, oldState, newState, projectId)
        
        Log.d(TAG, "  State change complete, waiting for Emacs to process")
        Log.d(TAG, "=== updateTodoState END ===")
    }
    
    /**
     * Handle Toggl time tracking on TODO state changes.
     * Similar to Emacs org-clock-in/org-clock-out hook.
     * 
     * Behavior:
     * - Transition TO IN-PROGRESS (or DOING/STARTED) → Start Toggl timer
     * - Transition FROM IN-PROGRESS (or DOING/STARTED) → Stop Toggl timer
     * 
     * Uses headline properties for configuration:
     * - TOGGL_PROJECT_ID: Custom project ID for this task
     * - TAGS: Automatically sent as Toggl tags
     * 
     * @param overrideProjectId Optional project ID for session-only override (doesn't persist)
     */
    private suspend fun handleTogglStateChange(
        note: NoteEntity,
        oldState: String?,
        newState: String,
        overrideProjectId: Long? = null
    ) {
        Log.d(TAG, "handleTogglStateChange called: ${note.title}, $oldState → $newState")
        try {
            // Check if Toggl is enabled and configured
            val isEnabled = togglRepository.isEnabled()
            Log.d(TAG, "Toggl isEnabled: $isEnabled")
            
            if (!isEnabled) {
                Log.d(TAG, "Toggl not enabled, skipping time tracking")
                return
            }
            
            when {
                // Starting work: Changed TO IN-PROGRESS
                newState == "IN-PROGRESS" && oldState != "IN-PROGRESS" -> {
                    Log.d(TAG, "Starting Toggl timer for: ${note.title}")
                    Log.d(TAG, "  Note properties: ${note.properties}")
                    Log.d(TAG, "  Note tags: ${note.tags}")
                    
                    // Extract project ID from override OR properties (override takes precedence)
                    val projectId = overrideProjectId 
                        ?: note.properties["TOGGL_PROJECT_ID"]?.toLongOrNull()
                    Log.d(TAG, "  Project ID: $projectId (override: $overrideProjectId, property: ${note.properties["TOGGL_PROJECT_ID"]})")
                    
                    // Extract tags
                    val tags = note.tags.split(":").filter { it.isNotBlank() }
                    Log.d(TAG, "  Extracted tags: $tags")
                    
                    val result = togglRepository.startTimeEntry(
                        description = note.title,
                        projectId = projectId,
                        tags = tags
                    )
                    
                    result.onSuccess { timeEntry ->
                        // Store time entry ID for later stopping
                        activeTogglEntries[note.headlineId] = timeEntry.id
                        Log.d(TAG, "Toggl timer started: ${timeEntry.id}")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to start Toggl timer: ${error.message}", error)
                        // Don't throw - continue with TODO state change even if Toggl fails
                    }
                }
                
                // Stopping work: Changed FROM IN-PROGRESS to anything else
                oldState == "IN-PROGRESS" && newState != "IN-PROGRESS" -> {
                    Log.d(TAG, "Stopping Toggl timer for: ${note.title}")
                    
                    // Get stored time entry ID
                    val timeEntryId = activeTogglEntries[note.headlineId]
                    
                    if (timeEntryId != null) {
                        val result = togglRepository.stopTimeEntry(timeEntryId)
                        
                        result.onSuccess { timeEntry ->
                            activeTogglEntries.remove(note.headlineId)
                            Log.d(TAG, "Toggl timer stopped: ${timeEntry.id}")
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to stop Toggl timer: ${error.message}", error)
                            // Clean up entry anyway
                            activeTogglEntries.remove(note.headlineId)
                        }
                    } else {
                        // No stored entry - try to find and stop current running entry
                        Log.d(TAG, "No stored Toggl entry ID, checking for running timer")
                        val currentResult = togglRepository.getCurrentTimeEntry()
                        
                        currentResult.onSuccess { currentEntry ->
                            if (currentEntry != null) {
                                Log.d(TAG, "Found running timer: ${currentEntry.id}, stopping")
                                togglRepository.stopTimeEntry(currentEntry.id)
                            }
                        }
                    }
                }
                
                // No state change related to IN-PROGRESS
                else -> {
                    Log.d(TAG, "No Toggl action needed for state change: $oldState → $newState")
                }
            }
        } catch (e: Exception) {
            // Catch all exceptions to prevent Toggl failures from breaking TODO state updates
            Log.e(TAG, "Toggl integration error (non-fatal): ${e.message}", e)
        }
    }

    /**
     * Cycle TODO state for a note (e.g., TODO → IN-PROGRESS → DONE → TODO)
     * Returns Result with new state on success, error message on failure
     */
    suspend fun cycleTodoState(noteId: Long): Result<String> = runCatching {
        val note = noteDao.getById(noteId) ?: throw IllegalArgumentException("Note not found")
        val config = todoKeywordsDao.getConfig()
            ?: TodoKeywordsConfigEntity(sequence = "TODO | DONE")

        val newState = config.cycleState(note.todoState)
        updateTodoState(noteId, newState).getOrThrow()

        newState
    }


    /**
     * Count pending sync files in sync/ directory
     * Used for UI indicators showing unprocessed state changes
     */
    suspend fun countPendingSyncs(): Result<Int> {
        return localFileManager.countPendingSyncs()
    }

    /**
     * Get a note by its database ID.
     * Used for checking if project selection is needed.
     */
    suspend fun getNoteById(noteId: Long): NoteEntity? {
        return noteDao.getById(noteId)
    }

    /**
     * Get a single task by noteId (for Pomodoro timer).
     * Returns AgendaItem.Note with all relevant details.
     */
    suspend fun getTask(noteId: Long): AgendaItem.Note? {
        return try {
            val noteEntity = noteDao.getNoteById(noteId) ?: return null
            
            // Return basic task info (timestamp not critical for Pomodoro UI)
            AgendaItem.Note(
                id = noteEntity.id,
                noteId = noteEntity.id,
                title = noteEntity.title,
                todoState = noteEntity.todoState,
                priority = noteEntity.priority,
                timeType = TimeType.SCHEDULED,
                timestamp = System.currentTimeMillis(),
                formattedTime = null,
                isOverdue = false,
                tags = noteEntity.tags.split(":").filter { it.isNotEmpty() },
                filename = noteEntity.filename,
                headlineId = noteEntity.headlineId,
                properties = noteEntity.properties
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting task by noteId=$noteId", e)
            null
        }
    }

    /**
     * Get all tasks currently in IN-PROGRESS state for Pomodoro task picker.
     * Returns list of AgendaItem.Note with IN-PROGRESS state.
     */
    suspend fun getInProgressTasks(): List<AgendaItem.Note> {
        return try {
            // Get all IN-PROGRESS notes from database
            val inProgressNotes = noteDao.getNotesByState("IN-PROGRESS")
            
            // Convert to AgendaItem.Note
            inProgressNotes.map { note ->
                AgendaItem.Note(
                    id = note.id,
                    noteId = note.id,
                    title = note.title,
                    todoState = note.todoState,
                    priority = note.priority,
                    timeType = TimeType.SCHEDULED,
                    timestamp = System.currentTimeMillis(),
                    formattedTime = null,
                    isOverdue = false,
                    tags = note.tags.split(":").filter { it.isNotEmpty() },
                    filename = note.filename,
                    headlineId = note.headlineId,
                    properties = note.properties
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IN-PROGRESS tasks", e)
            emptyList()
        }
    }
}
