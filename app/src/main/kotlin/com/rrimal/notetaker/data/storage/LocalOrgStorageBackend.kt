package com.rrimal.notetaker.data.storage

import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.data.orgmode.OrgParser
import com.rrimal.notetaker.data.orgmode.OrgWriter
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local org file storage backend implementation
 */
@Singleton
class LocalOrgStorageBackend @Inject constructor(
    private val fileManager: LocalFileManager,
    private val orgParser: OrgParser,
    private val orgWriter: OrgWriter,
    private val storageConfigManager: StorageConfigManager
) : StorageBackend {

    override val storageMode = StorageMode.LOCAL_ORG_FILES

    /**
     * Submit a note by creating a new org file or appending to inbox based on metadata
     * If metadata with title is provided, append to inbox file
     * Otherwise, create a new file in capture folder
     */
    override suspend fun submitNote(text: String, metadata: NoteMetadata?): Result<SubmitResult> {
        return try {
            // Check if folder is selected
            if (!fileManager.hasValidPermission()) {
                return Result.failure(Exception("No folder selected or permission revoked"))
            }

            // Determine behavior based on metadata
            if (metadata?.title != null) {
                // Has title metadata → inbox capture mode
                appendToInboxFile(text, metadata)
            } else {
                // No title metadata → quick note mode
                submitAsNewFile(text, metadata)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit note by creating a new file
     */
    private suspend fun submitAsNewFile(text: String, metadata: NoteMetadata?): Result<SubmitResult> {
        // Get the configured capture folder
        val captureFolder = storageConfigManager.captureFolder.first()

        val timestamp = ZonedDateTime.now()

        // Generate filename using timestamp (same format as GitHub backend)
        val filename = "${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"))}.org"

        // Create org headline from note text
        val headline = createHeadlineFromNote(text, metadata, timestamp)

        // Create org file content with single headline
        val content = orgWriter.writeFile(
            com.rrimal.notetaker.data.orgmode.OrgFile(headlines = listOf(headline))
        )

        // Create new file in the capture folder
        fileManager.createFile(filename, captureFolder, content).getOrThrow()

        return Result.success(SubmitResult.SENT)
    }

    /**
     * Submit note by appending to inbox file
     */
    private suspend fun appendToInboxFile(text: String, metadata: NoteMetadata?): Result<SubmitResult> {
        val inboxFilePath = storageConfigManager.inboxFilePath.first()
        val timestamp = ZonedDateTime.now()

        // Create org headline for inbox entry
        val headline = createInboxHeadline(text, metadata, timestamp)

        // Parse inbox file path to separate directory and filename
        val (parentPath, filename) = parseFilePath(inboxFilePath)

        // Try to read existing inbox file
        val readResult = fileManager.readFile(inboxFilePath)

        if (readResult.isSuccess) {
            // File exists - update it with appended content
            val existingContent = readResult.getOrThrow()

            val updatedContent = if (existingContent.isBlank()) {
                // File exists but is empty - add preamble + entry
                createInboxFileWithPreamble(headline)
            } else {
                // File has content - append new entry
                orgWriter.appendEntry(existingContent, headline)
            }

            fileManager.updateFile(inboxFilePath, updatedContent).getOrThrow()
        } else {
            // File doesn't exist - create it with preamble + entry
            val content = createInboxFileWithPreamble(headline)
            fileManager.createFile(filename, parentPath, content).getOrThrow()
        }

        return Result.success(SubmitResult.SENT)
    }

    /**
     * Parse file path into parent directory and filename
     * Examples:
     * - "inbox.org" -> ("", "inbox.org")
     * - "Brain/inbox.org" -> ("Brain", "inbox.org")
     * - "Work/Projects/todos.org" -> ("Work/Projects", "todos.org")
     */
    private fun parseFilePath(path: String): Pair<String, String> {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash == -1) {
            // No directory, just filename
            "" to path
        } else {
            // Has directory
            path.substring(0, lastSlash) to path.substring(lastSlash + 1)
        }
    }

    /**
     * Create inbox file content with standard preamble
     */
    private fun createInboxFileWithPreamble(headline: OrgNode.Headline): String {
        val preamble = """#+STARTUP: content showstars indent
#+FILETAGS: inbox
#+PROPERTY: Effort_ALL 0 0:05 0:10 0:15 0:30 0:45 1:00 2:00 4:00

"""
        return preamble + orgWriter.writeHeadline(headline)
    }

    /**
     * Fetch directory contents (list org files)
     */
    override suspend fun fetchDirectoryContents(path: String): Result<List<FileEntry>> {
        return fileManager.listFiles(path)
    }

    /**
     * Fetch file content
     */
    override suspend fun fetchFileContent(path: String): Result<String> {
        // The path here is actually the filename for local storage
        return fileManager.readFile(path)
    }

    /**
     * Fetch current topic (not implemented for local storage yet)
     */
    override suspend fun fetchCurrentTopic(): String? {
        // Could read from a special .current_topic.org file or property
        // For now, return null (no topic support in local mode)
        return null
    }

    /**
     * Create a new file
     */
    override suspend fun createFile(fileName: String, parentPath: String, content: String): Result<String> {
        return fileManager.createFile(fileName, parentPath, content)
    }

    /**
     * Create a new folder
     */
    override suspend fun createFolder(folderName: String, parentPath: String): Result<String> {
        return fileManager.createFolder(folderName, parentPath)
    }

    /**
     * Update file content
     */
    override suspend fun updateFile(documentId: String, content: String): Result<Unit> {
        return fileManager.updateFile(documentId, content)
    }

    /**
     * Create an org headline from note text and metadata
     * Extracts first sentence as title, rest as body
     */
    private fun createHeadlineFromNote(
        text: String,
        metadata: NoteMetadata?,
        timestamp: ZonedDateTime
    ): OrgNode.Headline {
        // Extract first sentence as title, rest as body
        val (title, body) = extractTitleAndBody(text)

        // Build properties
        val properties = mutableMapOf<String, String>()
        properties["CREATED"] = timestamp.format(DateTimeFormatter.ISO_INSTANT)

        // Add metadata properties if provided
        metadata?.properties?.forEach { (key, value) ->
            properties[key] = value
        }

        return OrgNode.Headline(
            level = 1,
            todoState = metadata?.todoState,
            priority = metadata?.priority,
            title = title,
            tags = metadata?.tags ?: emptyList(),
            scheduled = metadata?.scheduled,
            deadline = metadata?.deadline,
            properties = properties,
            body = body
        )
    }

    /**
     * Extract title and body from text
     * - First sentence becomes the title
     * - Rest becomes the body
     */
    private fun extractTitleAndBody(text: String): Pair<String, String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return "Untitled" to ""
        }

        // Find first sentence-ending punctuation (., ?, !)
        var sentenceEnd = -1
        for (i in trimmed.indices) {
            val char = trimmed[i]
            if (char == '.' || char == '?' || char == '!') {
                // Check if it's followed by space or end of string (to avoid false positives like "3.14")
                if (i == trimmed.length - 1 || trimmed[i + 1].isWhitespace()) {
                    sentenceEnd = i
                    break
                }
            }
        }

        return if (sentenceEnd != -1 && sentenceEnd < trimmed.length - 1) {
            // Found sentence ending - split there
            val title = trimmed.substring(0, sentenceEnd + 1).trim().take(200)
            val body = trimmed.substring(sentenceEnd + 1).trim()
            title to body
        } else {
            // No sentence ending found
            val lines = trimmed.lines()
            if (lines.size > 1) {
                // Multiple lines - use first line as title
                val title = lines[0].trim().take(200)
                val body = lines.drop(1).joinToString("\n").trim()
                title to body
            } else if (trimmed.length > 80) {
                // Single long line - take first 80 chars as title
                val title = trimmed.take(77) + "..."
                val body = trimmed
                title to body
            } else {
                // Short single line - use as title with no body
                trimmed to ""
            }
        }
    }

    /**
     * Create an inbox headline with Emacs-style formatting
     * Date format: [YYYY-MM-DD DDD HH:mm]
     */
    private fun createInboxHeadline(
        text: String,
        metadata: NoteMetadata?,
        timestamp: ZonedDateTime
    ): OrgNode.Headline {
        // Use title and description from metadata if provided, otherwise parse from text
        val title = metadata?.title ?: text.lines().firstOrNull()?.take(100) ?: "Untitled"
        val description = metadata?.description ?: run {
            val lines = text.lines()
            if (lines.size > 1) {
                lines.drop(1).joinToString("\n").trim()
            } else {
                ""
            }
        }

        // Format body as bullet points if description has multiple lines
        val body = if (description.isNotBlank()) {
            description.lines()
                .filter { it.isNotBlank() }
                .joinToString("\n") { line ->
                    if (line.trimStart().startsWith("-")) line else "- $line"
                }
        } else {
            ""
        }

        // Build properties with Emacs-style date format
        val properties = mutableMapOf<String, String>()
        // Format: [2026-01-24 Sat 11:40]
        val createdDate = timestamp.format(DateTimeFormatter.ofPattern("[yyyy-MM-dd EEE HH:mm]"))
        properties["CREATED"] = createdDate

        // Add metadata properties if provided
        metadata?.properties?.forEach { (key, value) ->
            properties[key] = value
        }

        return OrgNode.Headline(
            level = 1,
            todoState = metadata?.todoState ?: "TODO",  // Default to TODO for inbox
            priority = metadata?.priority,
            title = title,
            tags = metadata?.tags ?: emptyList(),
            scheduled = metadata?.scheduled,
            deadline = metadata?.deadline,
            properties = properties,
            body = body
        )
    }
}
