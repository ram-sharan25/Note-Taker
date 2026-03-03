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
            Log.d("LocalFileManager", "writeFile: filename=$filename")
            
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: return@withContext Result.failure(Exception("No folder selected"))

            val folderUri = Uri.parse(folderUriStr)

            // Check if file already exists (handle paths with directories)
            val existingFileUri = if (filename.contains('/')) {
                // File is in a subdirectory, use findFileByPath
                Log.d("LocalFileManager", "writeFile: searching for file with path: $filename")
                findFileByPath(folderUri, filename)
            } else {
                // File is in root, use findFileByName
                Log.d("LocalFileManager", "writeFile: searching for file in root: $filename")
                val documentId = DocumentsContract.getTreeDocumentId(folderUri)
                findFileByName(folderUri, documentId, filename)
            }

            val fileUri = if (existingFileUri != null) {
                // Overwrite existing file
                Log.d("LocalFileManager", "writeFile: found existing file, overwriting")
                existingFileUri
            } else {
                // Create new file
                Log.d("LocalFileManager", "writeFile: file not found, creating new file")
                
                // Determine parent directory and filename
                val (parentDocId, actualFilename) = if (filename.contains('/')) {
                    // Extract directory path and filename
                    val lastSlash = filename.lastIndexOf('/')
                    val dirPath = filename.substring(0, lastSlash)
                    val fileOnly = filename.substring(lastSlash + 1)
                    
                    Log.d("LocalFileManager", "writeFile: dirPath=$dirPath, fileOnly=$fileOnly")
                    
                    // Navigate to parent directory
                    val parentId = navigateToFolder(dirPath)
                        ?: return@withContext Result.failure(Exception("Parent directory not found: $dirPath"))
                    
                    parentId to fileOnly
                } else {
                    // Root directory
                    val documentId = DocumentsContract.getTreeDocumentId(folderUri)
                    documentId to filename
                }
                
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, parentDocId)
                val mimeType = when {
                    actualFilename.endsWith(".org") -> "text/org"
                    actualFilename.endsWith(".md") -> "text/markdown"
                    actualFilename.endsWith(".json") -> "application/json"
                    else -> "text/plain"
                }

                Log.d("LocalFileManager", "writeFile: creating file '$actualFilename' in parent $parentDocId")
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    mimeType,
                    actualFilename
                ) ?: return@withContext Result.failure(Exception("Failed to create file: $filename"))
            }

            // Write content
            Log.d("LocalFileManager", "writeFile: writing content (${content.length} chars)")
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }

            Log.d("LocalFileManager", "writeFile: SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LocalFileManager", "writeFile: FAILED", e)
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
                fileName.endsWith(".json") -> "application/json"
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

    // ========== Phone Inbox Helper Methods ==========

    /**
     * Get the document ID for the phone inbox folder
     * @return Document ID or null if not configured
     */
    private suspend fun getPhoneInboxDocumentId(): String? {
        val phoneInboxUriStr = storageConfigManager.phoneInboxFolderUri.first()
            ?: return null
        val phoneInboxUri = Uri.parse(phoneInboxUriStr)
        return DocumentsContract.getTreeDocumentId(phoneInboxUri)
    }

    /**
     * Get the Uri for the phone inbox folder
     * @return Uri or null if not configured
     */
    private suspend fun getPhoneInboxUri(): Uri? {
        val phoneInboxUriStr = storageConfigManager.phoneInboxFolderUri.first()
            ?: return null
        return Uri.parse(phoneInboxUriStr)
    }

    /**
     * Navigate to a subdirectory within a parent URI
     * @param parentUri The parent folder URI
     * @param parentDocId The parent document ID
     * @param subdirectoryName Name of the subdirectory to find
     * @return Document ID of the subdirectory, or null if not found
     */
    private fun findChildDirectory(parentUri: Uri, parentDocId: String, subdirectoryName: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocId)
        
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
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                val mimeType = cursor.getString(2)
                
                if (name == subdirectoryName && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return docId
                }
            }
        }
        
        return null
    }

    /**
     * Navigate to a subdirectory within phone inbox, creating it if it doesn't exist
     * @param subdirectoryName Name of the subdirectory (e.g., "dictations", "inbox", "sync")
     * @return Document ID of the subdirectory
     * @throws IllegalStateException if phone inbox not configured
     * @throws IOException if directory cannot be created
     */
    private suspend fun navigateToPhoneInboxSubdirectory(subdirectoryName: String): String {
        val phoneInboxUri = getPhoneInboxUri() 
            ?: throw IllegalStateException("Phone inbox folder not configured")
        val phoneInboxDocId = getPhoneInboxDocumentId()
            ?: throw IllegalStateException("Phone inbox folder not configured")
        
        // Check if subdirectory exists
        val existingDocId = findChildDirectory(phoneInboxUri, phoneInboxDocId, subdirectoryName)
        if (existingDocId != null) {
            return existingDocId
        }
        
        // Create subdirectory
        val phoneInboxDocUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, phoneInboxDocId)
        val newDirUri = DocumentsContract.createDocument(
            context.contentResolver,
            phoneInboxDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            subdirectoryName
        ) ?: throw java.io.IOException("Failed to create subdirectory: $subdirectoryName")
        
        val newDocId = DocumentsContract.getDocumentId(newDirUri)
        Log.d(TAG, "Created phone inbox subdirectory: $subdirectoryName (docId: $newDocId)")
        return newDocId
    }

    // ========== Phone Inbox Public Methods ==========

    /**
     * Ensure phone inbox directory structure exists
     * Creates dictations/, inbox/, and sync/ subdirectories if they don't exist
     * Called on app startup
     * 
     * @return Result with Unit on success, error on failure
     */
    suspend fun ensurePhoneInboxStructure(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Check if phone inbox is configured
            val phoneInboxUri = getPhoneInboxUri()
            if (phoneInboxUri == null) {
                Log.d(TAG, "Phone inbox not configured, skipping structure creation")
                return@runCatching
            }
            
            val phoneInboxDocId = getPhoneInboxDocumentId()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            
            // Create all required subdirectories
            PhoneInboxStructure.REQUIRED_SUBDIRECTORIES.forEach { subdirName ->
                navigateToPhoneInboxSubdirectory(subdirName)
            }
            
            // Create placeholder agenda.org if it doesn't exist
            val agendaFilename = PhoneInboxStructure.AGENDA_FILENAME
            val existingAgendaDocId = findChildFile(phoneInboxUri, phoneInboxDocId, agendaFilename)
            
            if (existingAgendaDocId == null) {
                Log.d(TAG, "Creating placeholder $agendaFilename")
                
                // Create placeholder content
                val agendaPlaceholderContent = """
                    |#+TITLE: Agenda View
                    |#+FILETAGS: :agenda:
                    |
                    |* Welcome to Note Taker
                    |:PROPERTIES:
                    |:CREATED: [${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm"))}]
                    |:END:
                    |
                    |This is your agenda view. Tasks will appear here once you:
                    |1. Add tasks using the Inbox Capture screen (swipe right)
                    |2. Set up Emacs to sync org files (see documentation)
                    |
                    |For now, you can create quick notes and TODO entries!
                """.trimMargin()
                
                // Create the file at root of phone inbox
                val phoneInboxRootUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, phoneInboxDocId)
                val newFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    phoneInboxRootUri,
                    "text/org",
                    agendaFilename
                ) ?: throw java.io.IOException("Failed to create $agendaFilename")
                
                // Write placeholder content
                context.contentResolver.openOutputStream(newFileUri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(agendaPlaceholderContent)
                    }
                } ?: throw java.io.IOException("Failed to write to $agendaFilename")
                
                Log.d(TAG, "Created placeholder $agendaFilename")
            }
            
            // Create placeholder inbox/inbox.org if it doesn't exist
            val inboxSubdirDocId = findChildDirectory(phoneInboxUri, phoneInboxDocId, PhoneInboxStructure.INBOX_DIR)
            if (inboxSubdirDocId != null) {
                val existingInboxDocId = findChildFile(phoneInboxUri, inboxSubdirDocId, PhoneInboxStructure.INBOX_FILENAME)
                
                if (existingInboxDocId == null) {
                    Log.d(TAG, "Creating placeholder ${PhoneInboxStructure.INBOX_FILE_PATH}")
                    
                    val inboxPlaceholderContent = """
                        |#+TITLE: Inbox
                        |#+FILETAGS: :inbox:
                        |
                        |* Your tasks will appear here
                        |:PROPERTIES:
                        |:CREATED: [${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm"))}]
                        |:END:
                        |
                        |Add tasks using the Inbox Capture screen (swipe right from Agenda).
                    """.trimMargin()
                    
                    // Create the file in inbox/ subdirectory
                    val inboxSubdirUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, inboxSubdirDocId)
                    val newInboxFileUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        inboxSubdirUri,
                        "text/org",
                        PhoneInboxStructure.INBOX_FILENAME
                    ) ?: throw java.io.IOException("Failed to create ${PhoneInboxStructure.INBOX_FILENAME}")
                    
                    // Write placeholder content
                    context.contentResolver.openOutputStream(newInboxFileUri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(inboxPlaceholderContent)
                        }
                    } ?: throw java.io.IOException("Failed to write to ${PhoneInboxStructure.INBOX_FILENAME}")
                    
                    Log.d(TAG, "Created placeholder ${PhoneInboxStructure.INBOX_FILE_PATH}")
                }
            }
            
            Log.d(TAG, "Phone inbox structure verified/created")
        }
    }

    /**
     * Write a file to the phone inbox folder at a specific relative path
     * Creates parent subdirectories if they don't exist
     * 
     * @param relativePath Path relative to phone inbox (e.g., "dictations/note.org")
     * @param content File content
     * @return Result with Unit on success, error on failure
     */
    suspend fun writeFileToPhoneInbox(relativePath: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(relativePath.isNotBlank()) { "Relative path cannot be blank" }
            
            val phoneInboxUri = getPhoneInboxUri()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            
            // Parse the path to extract subdirectory and filename
            val parts = relativePath.split("/")
            require(parts.size >= 2) { "Path must include subdirectory and filename" }
            
            val subdirectory = parts[0]
            val filename = parts.drop(1).joinToString("/")
            
            // Navigate to subdirectory
            val subdirDocId = navigateToPhoneInboxSubdirectory(subdirectory)
            val subdirUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, subdirDocId)
            
            // Check if file exists
            val existingFileDocId = findChildFile(phoneInboxUri, subdirDocId, filename)
            val fileUri = if (existingFileDocId != null) {
                // File exists, open for writing (overwrite)
                DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, existingFileDocId)
            } else {
                // Create new file
                val mimeType = when {
                    filename.endsWith(".org") -> "text/org"
                    filename.endsWith(".json") -> "application/json"
                    else -> "text/plain"
                }
                
                DocumentsContract.createDocument(
                    context.contentResolver,
                    subdirUri,
                    mimeType,
                    filename
                ) ?: throw java.io.IOException("Failed to create file: $filename")
            }
            
            // Write content
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            } ?: throw java.io.IOException("Failed to open output stream for: $filename")
            
            Log.d(TAG, "Wrote file to phone inbox: $relativePath")
            Unit
        }
    }

    /**
     * Find a child file in a directory
     * @param parentUri Parent folder URI
     * @param parentDocId Parent document ID
     * @param filename Name of the file to find
     * @return Document ID of the file, or null if not found
     */
    private fun findChildFile(parentUri: Uri, parentDocId: String, filename: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentDocId)
        
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
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1)
                val mimeType = cursor.getString(2)
                
                if (name == filename && mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                    return docId
                }
            }
        }
        
        return null
    }

    /**
     * Read a file from the phone inbox folder at a specific relative path
     * 
     * @param relativePath Path relative to phone inbox (e.g., "agenda.org", "inbox/inbox.org")
     * @return Result with file content on success, error on failure
     */
    suspend fun readFileFromPhoneInbox(relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== readFileFromPhoneInbox START ===")
        Log.d(TAG, "  relativePath: $relativePath")
        
        runCatching {
            require(relativePath.isNotBlank()) { "Relative path cannot be blank" }
            
            val phoneInboxUri = getPhoneInboxUri()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            val phoneInboxDocId = getPhoneInboxDocumentId()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            
            Log.d(TAG, "  phoneInboxUri: $phoneInboxUri")
            Log.d(TAG, "  phoneInboxDocId: $phoneInboxDocId")
            
            // Parse the path
            val parts = relativePath.split("/")
            Log.d(TAG, "  path parts: $parts")
            
            // Navigate to file location
            val fileDocId = if (parts.size == 1) {
                // File is at root of phone inbox (e.g., "agenda.org")
                Log.d(TAG, "  Looking for file in root: ${parts[0]}")
                val docId = findChildFile(phoneInboxUri, phoneInboxDocId, parts[0])
                    ?: throw java.io.FileNotFoundException("File not found: $relativePath")
                Log.d(TAG, "  Found file docId: $docId")
                docId
            } else {
                // File is in a subdirectory (e.g., "inbox/inbox.org")
                val subdirectory = parts[0]
                val filename = parts.drop(1).joinToString("/")
                
                Log.d(TAG, "  Looking in subdirectory: $subdirectory, filename: $filename")
                val subdirDocId = findChildDirectory(phoneInboxUri, phoneInboxDocId, subdirectory)
                    ?: throw java.io.FileNotFoundException("Subdirectory not found: $subdirectory")
                Log.d(TAG, "  Found subdirectory docId: $subdirDocId")
                
                val docId = findChildFile(phoneInboxUri, subdirDocId, filename)
                    ?: throw java.io.FileNotFoundException("File not found: $relativePath")
                Log.d(TAG, "  Found file docId: $docId")
                docId
            }
            
            // Read file content
            Log.d(TAG, "  Reading file content...")
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, fileDocId)
            val content = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw java.io.IOException("Failed to read file: $relativePath")
            
            Log.d(TAG, "  Content length: ${content.length} bytes")
            Log.d(TAG, "  Content preview (first 300 chars):\n${content.take(300)}")
            Log.d(TAG, "=== readFileFromPhoneInbox END ===")
            
            content
        }.also { result ->
            if (result.isFailure) {
                Log.e(TAG, "readFileFromPhoneInbox FAILED", result.exceptionOrNull())
            }
        }
    }

    /**
     * Create a new file in a phone inbox subdirectory with the given content
     * Throws exception if file already exists
     * 
     * @param filename Name of the file to create
     * @param subdirectory Name of the subdirectory (e.g., "dictations", "inbox")
     * @param content File content
     * @return Result with Unit on success, error on failure
     */
    suspend fun createFileInPhoneInbox(
        filename: String,
        subdirectory: String,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(filename.isNotBlank()) { "Filename cannot be blank" }
            require(subdirectory.isNotBlank()) { "Subdirectory cannot be blank" }
            
            val phoneInboxUri = getPhoneInboxUri()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            
            // Navigate to subdirectory
            val subdirDocId = navigateToPhoneInboxSubdirectory(subdirectory)
            
            // Check if file already exists
            val existingFileDocId = findChildFile(phoneInboxUri, subdirDocId, filename)
            if (existingFileDocId != null) {
                throw java.io.IOException("File already exists: $filename")
            }
            
            // Create new file
            val subdirUri = DocumentsContract.buildDocumentUriUsingTree(phoneInboxUri, subdirDocId)
            val mimeType = when {
                filename.endsWith(".org") -> "text/org"
                filename.endsWith(".json") -> "application/json"
                else -> "text/plain"
            }
            
            val newFileUri = DocumentsContract.createDocument(
                context.contentResolver,
                subdirUri,
                mimeType,
                filename
            ) ?: throw java.io.IOException("Failed to create file: $filename")
            
            // Write content
            context.contentResolver.openOutputStream(newFileUri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            } ?: throw java.io.IOException("Failed to write to file: $filename")
            
            Log.d(TAG, "Created file in phone inbox: $subdirectory/$filename")
            Unit
        }
    }

    // ========== JSON Sync Methods (v0.9.0) ==========

    /**
     * Ensure sync/ directory exists at root of org files
     * Called on app startup and before first sync write
     * 
     * @return Result with Unit on success, error on failure
     * @deprecated Use ensurePhoneInboxStructure() instead (creates sync/ at phone_inbox/sync/)
     */
    @Deprecated("Use ensurePhoneInboxStructure() instead")
    suspend fun ensureSyncDirectoryExists(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val folderUriStr = storageConfigManager.localFolderUri.first()
                ?: throw IllegalStateException("No folder selected")

            val folderUri = Uri.parse(folderUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, rootDocId)

            // Check if sync/ already exists
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)
            var syncDirExists = false
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val mimeType = cursor.getString(1)
                    if (name == "sync" && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        syncDirExists = true
                        break
                    }
                }
            }

            if (syncDirExists) {
                Log.d(TAG, "Sync directory already exists")
                return@runCatching
            }

            // Create sync/ directory
            val newFolderUri = DocumentsContract.createDocument(
                context.contentResolver,
                rootUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                "sync"
            ) ?: throw java.io.IOException("Failed to create sync directory")

            Log.d(TAG, "Created sync directory: $newFolderUri")
        }
    }

    /**
     * Write JSON file to phone_inbox/sync/ directory
     * Overwrites if file already exists
     * 
     * @param filename Filename only (not full path), e.g., "ABC-123_DONE_1709467200000.json"
     * @param content JSON content as string
     * @return Result with Unit on success, error on failure
     */
    suspend fun writeSyncJson(filename: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Validate filename format
            require(com.rrimal.notetaker.data.models.isValidSyncFilename(filename)) {
                "Invalid sync filename format: $filename"
            }

            // Ensure phone inbox structure exists
            ensurePhoneInboxStructure().getOrThrow()

            // Write to phone_inbox/sync/<filename>
            val relativePath = "${PhoneInboxStructure.SYNC_DIR}/$filename"
            writeFileToPhoneInbox(relativePath, content).getOrThrow()

            Log.d(TAG, "Wrote sync file: $relativePath")
            Unit
        }
    }

    /**
     * List all JSON files in phone_inbox/sync/ directory
     * Used for debugging and showing "pending syncs" indicator
     * 
     * @return Result with list of filenames (no path prefix)
     */
    suspend fun listSyncFiles(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val phoneInboxUri = getPhoneInboxUri()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            val phoneInboxDocId = getPhoneInboxDocumentId()
                ?: throw IllegalStateException("Phone inbox folder not configured")
            
            // Navigate to sync/ subdirectory
            val syncDocId = findChildDirectory(phoneInboxUri, phoneInboxDocId, PhoneInboxStructure.SYNC_DIR)
                ?: return@runCatching emptyList<String>()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(phoneInboxUri, syncDocId)
            val files = mutableListOf<String>()

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val mimeType = cursor.getString(1)
                    
                    // Only include JSON files
                    if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR && name.endsWith(".json")) {
                        files.add(name)
                    }
                }
            }

            files.sorted()
        }
    }

    /**
     * Count pending sync files in sync/ directory
     * Lightweight version of listSyncFiles() for UI indicators
     */
    suspend fun countPendingSyncs(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            listSyncFiles().getOrElse { emptyList() }.size
        }
    }

    companion object {
        private const val TAG = "LocalFileManager"
    }
}
