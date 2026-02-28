package com.rrimal.notetaker.data.storage

/**
 * Storage backend interface for different note storage strategies.
 * Implementations include GitHub API storage and local file storage.
 */
interface StorageBackend {
    /**
     * Submit a note with optional metadata
     */
    suspend fun submitNote(text: String, metadata: NoteMetadata? = null): Result<SubmitResult>

    /**
     * Fetch directory contents at the given path
     */
    suspend fun fetchDirectoryContents(path: String): Result<List<FileEntry>>

    /**
     * Fetch content of a file at the given path
     */
    suspend fun fetchFileContent(path: String): Result<String>

    /**
     * Fetch the current topic
     */
    suspend fun fetchCurrentTopic(): String?

    /**
     * The storage mode this backend represents
     */
    val storageMode: StorageMode
}

/**
 * Metadata for org-mode notes
 */
data class NoteMetadata(
    val tags: List<String> = emptyList(),
    val todoState: String? = null,  // TODO, DONE, WAITING, CANCELLED, etc.
    val priority: String? = null,    // A, B, C
    val scheduled: String? = null,
    val deadline: String? = null,
    val properties: Map<String, String> = emptyMap()
)

/**
 * File entry for directory listings
 */
data class FileEntry(
    val name: String,
    val path: String,
    val type: FileType,
    val size: Long = 0,
    val lastModified: Long? = null
)

enum class FileType {
    FILE,
    DIRECTORY
}

enum class SubmitResult {
    SENT,
    QUEUED,
    AUTH_FAILED
}

enum class StorageMode {
    GITHUB_MARKDOWN,
    LOCAL_ORG_FILES
}
