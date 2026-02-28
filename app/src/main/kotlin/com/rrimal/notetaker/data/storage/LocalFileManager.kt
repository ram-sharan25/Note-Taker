package com.rrimal.notetaker.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages local file operations using Android Storage Access Framework (SAF)
 */
@Singleton
class LocalFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageConfigManager: StorageConfigManager
) {
    /**
     * List files in the selected folder or a relative path within it
     * @param relativePath Either empty (root), or a document ID (e.g., "primary:Brain")
     */
    suspend fun listFiles(relativePath: String = ""): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Determine which document ID to use
            val documentId = if (relativePath.isEmpty()) {
                // Root folder
                DocumentsContract.getTreeDocumentId(folderUri)
            } else if (relativePath.contains(':')) {
                // It's a document ID (from clicking a folder in browse)
                relativePath
            } else {
                // Fallback: treat as relative path from root (not commonly used)
                DocumentsContract.getTreeDocumentId(folderUri)
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)

            val files = mutableListOf<FileEntry>()
            val resolver = context.contentResolver

            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mimeType = cursor.getString(2)
                    val size = cursor.getLong(3)
                    val lastModified = cursor.getLong(4)

                    val type = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        FileType.DIRECTORY
                    } else {
                        FileType.FILE
                    }

                    files.add(
                        FileEntry(
                            name = name,
                            path = docId,
                            type = type,
                            size = size,
                            lastModified = lastModified
                        )
                    )
                }
            }

            Result.success(files.sortedBy { it.name })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read file content from the selected folder
     * Handles both document IDs (from browsing) and filenames (direct access)
     */
    suspend fun readFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Determine if this is a document ID or filename
            val fileUri = if (filename.contains(':')) {
                // It's a document ID (from browse - e.g., "primary:Brain/readings.org")
                DocumentsContract.buildDocumentUriUsingTree(folderUri, filename)
            } else {
                // It's a filename - search for it in root folder
                val documentId = DocumentsContract.getTreeDocumentId(folderUri)
                findFileByName(folderUri, documentId, filename)
                    ?: return@withContext Result.failure(Exception("File not found: $filename"))
            }

            // Read file content
            val content = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: ""

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Write content to a file (creates new file or overwrites existing)
     */
    suspend fun writeFile(
        filename: String,
        content: String,
        relativePath: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)
            val documentId = DocumentsContract.getTreeDocumentId(folderUri)

            // Check if file already exists
            val existingFileUri = findFileByName(folderUri, documentId, filename)

            val fileUri = if (existingFileUri != null) {
                // Overwrite existing file
                existingFileUri
            } else {
                // Create new file
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                val mimeType = when {
                    filename.endsWith(".org") -> "text/org"
                    filename.endsWith(".md") -> "text/markdown"
                    else -> "text/plain"
                }

                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    mimeType,
                    filename
                ) ?: return@withContext Result.failure(Exception("Failed to create file: $filename"))
            }

            // Write content
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Append content to an existing file
     */
    suspend fun appendToFile(filename: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Read existing content
            val existingContent = readFile(filename).getOrElse { "" }

            // Write combined content
            val newContent = if (existingContent.isBlank()) {
                content
            } else {
                "$existingContent\n$content"
            }

            writeFile(filename, newContent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Request persistent permission for a folder URI
     */
    fun requestPersistentPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    /**
     * Check if persistent permission exists for the selected folder
     */
    suspend fun hasValidPermission(): Boolean {
        val folderUriStr = storageConfigManager.localFolderUri.first()
            ?: return false

        val folderUri = Uri.parse(folderUriStr)
        val persistedUris = context.contentResolver.persistedUriPermissions

        return persistedUris.any { it.uri == folderUri && it.isReadPermission && it.isWritePermission }
    }

    /**
     * Helper: Find a file by name in the given folder
     */
    private fun findFileByName(folderUri: Uri, documentId: String, filename: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)

                if (name == filename) {
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }

        return null
    }
}
