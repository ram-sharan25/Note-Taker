package com.rrimal.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.storage.FileEntry
import com.rrimal.notetaker.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewingFile: String? = null,
    val fileContent: String? = null,
    val isFileLoading: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val showCreateFileDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        loadDirectory("")
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    viewingFile = null,
                    fileContent = null
                )
            }

            val result = repository.fetchDirectoryContents(path)
            result.onSuccess { entries ->
                _uiState.update {
                    it.copy(
                        currentPath = path,
                        pathSegments = if (path.isEmpty()) emptyList() else path.split("/"),
                        entries = entries,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                val message = if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                    "Session expired. Please disconnect and sign back in from Settings."
                } else {
                    e.message ?: "Failed to load directory"
                }
                _uiState.update {
                    it.copy(isLoading = false, error = message)
                }
            }
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(viewingFile = path, isFileLoading = true, fileContent = null)
            }

            val result = repository.fetchFileContent(path)
            result.onSuccess { content ->
                _uiState.update {
                    it.copy(fileContent = content, isFileLoading = false)
                }
            }.onFailure { e ->
                val message = if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                    "Session expired. Please disconnect and sign back in from Settings."
                } else {
                    e.message ?: "Failed to load file"
                }
                _uiState.update {
                    it.copy(isFileLoading = false, error = message)
                }
            }
        }
    }

    /** Returns true if navigation was handled (went up one level), false if already at root with no file open */
    fun navigateUp(): Boolean {
        val state = _uiState.value

        // If editing, exit edit mode
        if (state.isEditing) {
            _uiState.update { it.copy(isEditing = false) }
            return true
        }

        // If viewing a file, go back to directory
        if (state.viewingFile != null) {
            _uiState.update { it.copy(viewingFile = null, fileContent = null) }
            return true
        }

        // If in a subdirectory, go up one level
        if (state.currentPath.isNotEmpty()) {
            val parent = state.currentPath.substringBeforeLast("/", "")
            loadDirectory(parent)
            return true
        }

        return false
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun updateFileContent(newContent: String) {
        _uiState.update { it.copy(fileContent = newContent) }
    }

    fun saveFile() {
        val state = _uiState.value
        val filePath = state.viewingFile ?: return
        val content = state.fileContent ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = repository.updateFile(filePath, content)
            result.onSuccess {
                _uiState.update { it.copy(isEditing = false, isSaving = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save file"
                    )
                }
            }
        }
    }

    fun showCreateFileDialog() {
        _uiState.update { it.copy(showCreateFileDialog = true) }
    }

    fun hideCreateFileDialog() {
        _uiState.update { it.copy(showCreateFileDialog = false) }
    }

    fun showCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = true) }
    }

    fun hideCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = false) }
    }

    fun createFile(fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateFileDialog = false) }

            val result = repository.createFile(fileName, _uiState.value.currentPath)
            result.onSuccess {
                // Reload directory to show new file
                loadDirectory(_uiState.value.currentPath)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create file"
                    )
                }
            }
        }
    }

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateFolderDialog = false) }

            val result = repository.createFolder(folderName, _uiState.value.currentPath)
            result.onSuccess {
                // Reload directory to show new folder
                loadDirectory(_uiState.value.currentPath)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create folder"
                    )
                }
            }
        }
    }
}
