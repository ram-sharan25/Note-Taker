package com.rrimal.notetaker.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
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
     * Handles both document IDs (from browsing) and file paths (direct access)
     */
    suspend fun readFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Determine if this is a document ID or file path
            val fileUri = if (filename.contains(':')) {
                // It's a document ID (from browse - e.g., "primary:Brain/readings.org")
                DocumentsContract.buildDocumentUriUsingTree(folderUri, filename)
            } else if (filename.contains('/')) {
                // It's a path with directory (e.g., "Brain/inbox.org")
                findFileByPath(folderUri, filename)
                    ?: return@withContext Result.failure(Exception("File not found: $filename"))
            } else {
                // It's a simple filename - search for it in root folder
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
     * Create a new folder in the specified path
     * @param folderName Name of the folder to create
     * @param parentPath Folder path like "phone_inbox" or document ID (empty for root)
     */
    suspend fun createFolder(folderName: String, parentPath: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Determine parent document ID
            val parentDocId = if (parentPath.contains(':')) {
                // It's already a document ID
                parentPath
            } else {
                // It's a folder path - navigate to it
                navigateToFolder(parentPath)
                    ?: return@withContext Result.failure(Exception("Folder not found: $parentPath"))
            }

            val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)

            val newFolderUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName
            ) ?: return@withContext Result.failure(Exception("Failed to create folder"))

            // Extract the document ID from the created folder URI
            val newDocumentId = DocumentsContract.getDocumentId(newFolderUri)
            Result.success(newDocumentId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new file in the specified path
     * @param fileName Name of the file to create
     * @param parentPath Folder path like "phone_inbox/dictations" or document ID (empty for root)
     * @param content Initial content (empty by default)
     */
    suspend fun createFile(fileName: String, parentPath: String = "", content: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("LocalFileManager", "createFile: fileName=$fileName, parentPath=$parentPath")

            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)
            Log.d("LocalFileManager", "createFile: folderUri=$folderUri")

            // Determine parent document ID
            val parentDocId = if (parentPath.contains(':')) {
                // It's already a document ID
                Log.d("LocalFileManager", "createFile: using document ID directly: $parentPath")
                parentPath
            } else {
                // It's a folder path - navigate to it
                Log.d("LocalFileManager", "createFile: navigating to folder path: $parentPath")
                val docId = navigateToFolder(parentPath)
                Log.d("LocalFileManager", "createFile: navigated to docId: $docId")
                docId ?: return@withContext Result.failure(Exception("Folder not found: $parentPath"))
            }

            val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)

            // Determine MIME type
            val mimeType = when {
                fileName.endsWith(".org") -> "text/org"
                fileName.endsWith(".md") -> "text/markdown"
                fileName.endsWith(".txt") -> "text/plain"
                else -> "text/plain"
            }

            val newFileUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mimeType,
                fileName
            ) ?: return@withContext Result.failure(Exception("Failed to create file"))

            // Write initial content if provided
            if (content.isNotEmpty()) {
                context.contentResolver.openOutputStream(newFileUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(content)
                    }
                }
            }

            // Extract the document ID from the created file URI
            val newDocumentId = DocumentsContract.getDocumentId(newFileUri)
            Result.success(newDocumentId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update file content using document ID or file path
     * @param documentId The document ID, filename, or file path
     * @param content New content to write
     */
    suspend fun updateFile(documentId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Build the file URI
            val fileUri = if (documentId.contains(':')) {
                // It's a document ID
                DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
            } else if (documentId.contains('/')) {
                // It's a path with directory (e.g., "Brain/inbox.org")
                findFileByPath(folderUri, documentId)
                    ?: return@withContext Result.failure(Exception("File not found: $documentId"))
            } else {
                // It's a simple filename - find it in root
                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                findFileByName(folderUri, rootDocId, documentId)
                    ?: return@withContext Result.failure(Exception("File not found: $documentId"))
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

    /**
     * Helper: Find a file by path (e.g., "Brain/inbox.org")
     * Navigates through directories to find the file
     */
    private suspend fun findFileByPath(folderUri: Uri, filePath: String): Uri? = withContext(Dispatchers.IO) {
        try {
            // Parse path into directory and filename
            val lastSlash = filePath.lastIndexOf('/')
            if (lastSlash == -1) {
                // No directory, just filename - search in root
                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                return@withContext findFileByName(folderUri, rootDocId, filePath)
            }

            // Has directory
            val dirPath = filePath.substring(0, lastSlash)
            val filename = filePath.substring(lastSlash + 1)

            // Navigate to the directory
            val dirDocId = navigateToFolder(dirPath)
                ?: return@withContext null

            // Find file in that directory
            return@withContext findFileByName(folderUri, dirDocId, filename)
        } catch (e: Exception) {
            return@withContext null
        }
    }

    /**
     * Helper: Navigate to a folder path and return its document ID
     * @param folderPath Path like "phone_inbox/dictations" (relative to root)
     * @return Document ID of the target folder, or null if not found
     */
    private suspend fun navigateToFolder(folderPath: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("LocalFileManager", "navigateToFolder: folderPath=$folderPath")

            if (folderPath.isEmpty()) {
                // Root folder
                val folderUriStr = storageConfigManager.localFolderUri.first() ?: return@withContext null
                val folderUri = Uri.parse(folderUriStr)
                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                Log.d("LocalFileManager", "navigateToFolder: returning root docId=$rootDocId")
                return@withContext rootDocId
            }

            val folderUriStr = storageConfigManager.localFolderUri.first() ?: return@withContext null
            val folderUri = Uri.parse(folderUriStr)
            var currentDocId = DocumentsContract.getTreeDocumentId(folderUri)
            Log.d("LocalFileManager", "navigateToFolder: starting from root docId=$currentDocId")

            // Navigate through each path segment
            val segments = folderPath.split("/").filter { it.isNotBlank() }
            Log.d("LocalFileManager", "navigateToFolder: segments=$segments")

            for (segment in segments) {
                Log.d("LocalFileManager", "navigateToFolder: looking for segment='$segment' in docId=$currentDocId")
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, currentDocId)

                var foundDocId: String? = null
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    Log.d("LocalFileManager", "navigateToFolder: query returned ${cursor.count} children")
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0)
                        val name = cursor.getString(1)
                        val mimeType = cursor.getString(2)
                        Log.d("LocalFileManager", "navigateToFolder: child name='$name', mimeType=$mimeType")

                        if (name == segment && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            foundDocId = docId
                            Log.d("LocalFileManager", "navigateToFolder: FOUND segment='$segment' with docId=$docId")
                            break
                        }
                    }
                }

                if (foundDocId == null) {
                    // Folder not found in path
                    Log.d("LocalFileManager", "navigateToFolder: segment='$segment' NOT FOUND")
                    return@withContext null
                }
                currentDocId = foundDocId!!
            }

            Log.d("LocalFileManager", "navigateToFolder: final docId=$currentDocId")
            return@withContext currentDocId
        } catch (e: Exception) {
            Log.e("LocalFileManager", "navigateToFolder: exception", e)
            return@withContext null
        }
    }
}
