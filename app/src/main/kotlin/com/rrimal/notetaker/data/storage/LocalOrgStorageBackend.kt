package com.rrimal.notetaker.data.storage

import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.data.orgmode.OrgParser
import com.rrimal.notetaker.data.orgmode.OrgWriter
import com.rrimal.notetaker.data.sync.GitHubSyncManager
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
    private val storageConfigManager: StorageConfigManager,
    private val syncManager: GitHubSyncManager
) : StorageBackend {

    override val storageMode = StorageMode.LOCAL_ORG_FILES

    private val inboxFilename = "inbox.org"

    /**
     * Submit a note by appending it to inbox.org as an org headline
     */
    override suspend fun submitNote(text: String, metadata: NoteMetadata?): Result<SubmitResult> {
        return try {
            // Check if folder is selected
            if (!fileManager.hasValidPermission()) {
                return Result.failure(Exception("No folder selected or permission revoked"))
            }

            val timestamp = ZonedDateTime.now()

            // Create org headline from note text
            val headline = createHeadlineFromNote(text, metadata, timestamp)

            // Read existing inbox.org content (or empty if doesn't exist)
            val existingContent = fileManager.readFile(inboxFilename).getOrElse { "" }

            // Append new headline
            val updatedContent = if (existingContent.isBlank()) {
                orgWriter.writeFile(
                    com.rrimal.notetaker.data.orgmode.OrgFile(headlines = listOf(headline))
                )
            } else {
                orgWriter.appendEntry(existingContent, headline)
            }

            // Write back to file
            fileManager.writeFile(inboxFilename, updatedContent).getOrThrow()

            // Queue GitHub sync if enabled
            if (storageConfigManager.syncToGitHubEnabled.first()) {
                syncManager.queueFileSync(inboxFilename, updatedContent)
            }

            Result.success(SubmitResult.SENT)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
     * Create an org headline from note text and metadata
     */
    private fun createHeadlineFromNote(
        text: String,
        metadata: NoteMetadata?,
        timestamp: ZonedDateTime
    ): OrgNode.Headline {
        val lines = text.lines()
        val title = lines.firstOrNull()?.take(100) ?: "Untitled"
        val body = if (lines.size > 1) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            ""
        }

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
}
