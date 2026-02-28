package com.rrimal.notetaker.data.storage

import android.util.Base64
import com.rrimal.notetaker.data.api.CreateFileRequest
import com.rrimal.notetaker.data.api.GitHubApi
import com.rrimal.notetaker.data.auth.AuthManager
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub storage backend implementation using GitHub REST API
 */
@Singleton
class GitHubStorageBackend @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager
) : StorageBackend {

    override val storageMode = StorageMode.GITHUB_MARKDOWN

    override suspend fun submitNote(text: String, metadata: NoteMetadata?): Result<SubmitResult> {
        return try {
            val token = authManager.accessToken.first()
                ?: throw Exception("Not authenticated")
            val owner = authManager.repoOwner.first()
                ?: throw Exception("No repo configured")
            val repo = authManager.repoName.first()
                ?: throw Exception("No repo configured")

            val now = ZonedDateTime.now()
            val filename = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"))
            val path = "inbox/$filename.md"
            val content = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)

            api.createFile(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = path,
                request = CreateFileRequest(
                    message = "Add note $filename",
                    content = content
                )
            )

            Result.success(SubmitResult.SENT)
        } catch (e: Exception) {
            if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                Result.success(SubmitResult.AUTH_FAILED)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun fetchDirectoryContents(path: String): Result<List<FileEntry>> {
        return try {
            val token = authManager.accessToken.first()
                ?: return Result.failure(Exception("Not authenticated"))
            val owner = authManager.repoOwner.first()
                ?: return Result.failure(Exception("No repo configured"))
            val repo = authManager.repoName.first()
                ?: return Result.failure(Exception("No repo configured"))

            val entries = if (path.isEmpty()) {
                api.getRootContents(auth = "Bearer $token", owner = owner, repo = repo)
            } else {
                api.getDirectoryContents(auth = "Bearer $token", owner = owner, repo = repo, path = path)
            }

            // Convert GitHub entries to FileEntry
            val fileEntries = entries.map { entry ->
                FileEntry(
                    name = entry.name,
                    path = entry.path,
                    type = if (entry.type == "dir") FileType.DIRECTORY else FileType.FILE,
                    size = entry.size ?: 0,
                    lastModified = null  // GitHub API doesn't provide this
                )
            }

            // Sort: dirs first, then alphabetical
            val sorted = fileEntries.sortedWith(
                compareBy<FileEntry> { it.type != FileType.DIRECTORY }.thenBy { it.name }
            )

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchFileContent(path: String): Result<String> {
        return try {
            val token = authManager.accessToken.first()
                ?: return Result.failure(Exception("Not authenticated"))
            val owner = authManager.repoOwner.first()
                ?: return Result.failure(Exception("No repo configured"))
            val repo = authManager.repoName.first()
                ?: return Result.failure(Exception("No repo configured"))

            val response = api.getFileContent(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = path
            )

            val decoded = response.content?.let { encoded ->
                String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT))
            } ?: ""

            Result.success(decoded)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchCurrentTopic(): String? {
        return try {
            val token = authManager.accessToken.first() ?: return null
            val owner = authManager.repoOwner.first() ?: return null
            val repo = authManager.repoName.first() ?: return null

            val response = api.getFileContent(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = ".current_topic"
            )

            response.content?.let { encoded ->
                String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT)).trim()
            }
        } catch (_: Exception) {
            null
        }
    }
}
