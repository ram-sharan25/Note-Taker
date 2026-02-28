package com.rrimal.notetaker.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import com.rrimal.notetaker.data.storage.StorageMode
import com.rrimal.notetaker.ui.theme.Blue40
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.rrimal.notetaker.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.checkAssistantRole()
        }
    }

    Scaffold(
        topBar = {
            Column {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background)
                )
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Blue40
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Storage Mode section
            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let { viewModel.onFolderSelected(it) }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // GitHub Markdown option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.storageMode == StorageMode.GITHUB_MARKDOWN,
                            onClick = { viewModel.setStorageMode(StorageMode.GITHUB_MARKDOWN) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("GitHub Markdown", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Store notes as .md files in GitHub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Local Org Files option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.storageMode == StorageMode.LOCAL_ORG_FILES,
                            onClick = { viewModel.setStorageMode(StorageMode.LOCAL_ORG_FILES) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Local Org Files", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Store notes as .org files locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Local folder selection (visible when LOCAL_ORG_FILES mode)
                    if (uiState.storageMode == StorageMode.LOCAL_ORG_FILES) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.localFolderUri != null) {
                            Text(
                                "Folder: ${Uri.parse(uiState.localFolderUri).lastPathSegment ?: "Selected"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Change Folder")
                            }
                        } else {
                            Text(
                                "No folder selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Select Folder")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Inbox Configuration (visible when LOCAL_ORG_FILES mode)
            if (uiState.storageMode == StorageMode.LOCAL_ORG_FILES) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Inbox Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Configure where inbox TODO entries are saved when using the inbox capture feature (✓ icon)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        var inboxPath by remember { mutableStateOf(uiState.inboxFilePath) }

                        LaunchedEffect(uiState.inboxFilePath) {
                            inboxPath = uiState.inboxFilePath
                        }

                        Text(
                            "Inbox file path:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = inboxPath,
                            onValueChange = { inboxPath = it },
                            label = { Text("File path") },
                            placeholder = { Text("inbox.org") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Examples: inbox.org, Brain/inbox.org, Work/todos.org",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.setInboxFilePath(inboxPath.trim())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = inboxPath.trim().isNotEmpty()
                        ) {
                            Text("Save Inbox Path")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Device Connection section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Connection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.username.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connected as ${uiState.username}")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (uiState.repoFullName.isNotEmpty()) {
                                    append(uiState.repoFullName)
                                    append(" · ")
                                }
                                append(
                                    when (uiState.authType) {
                                        "oauth" -> "via GitHub"
                                        "pat" -> "via Personal Access Token"
                                        else -> ""
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This device is authenticated to push notes to your repo. Disconnecting removes credentials from this device only — you can reconnect with one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (uiState.authType == "oauth" || uiState.pendingCount > 0) {
                                    showSignOutDialog = true
                                } else {
                                    viewModel.signOut { onSignedOut() }
                                }
                            },
                            enabled = !uiState.isSigningOut,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (uiState.isSigningOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disconnecting...")
                            } else {
                                Text("Disconnect")
                            }
                        }
                    } else {
                        Text("Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Note Taker on GitHub section (OAuth only)
            if (uiState.authType == "oauth" && uiState.installationId.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Note Taker on GitHub",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.repoFullName.isNotEmpty()) {
                                "Note Taker has read & write access to file contents in ${uiState.repoFullName}. No access to issues, pull requests, or settings."
                            } else {
                                "Note Taker has read & write access to file contents. No access to issues, pull requests, or settings."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Change repo access, add repos, or uninstall Note Taker from your GitHub account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/installations/${uiState.installationId}"))
                                )
                            }
                        ) {
                            Text("Manage on GitHub")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Digital Assistant section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Digital Assistant",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Step 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Set as digital assistant",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isAssistantDefault) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set as default")
                        } else {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Not set as default")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 2
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "2",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Set side button to digital assistant",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Change your side button's long-press action from Bixby to Digital assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Assistant Settings")
                        }
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent().setComponent(
                                            ComponentName(
                                                "com.samsung.android.settings",
                                                "com.samsung.android.settings.SideKeySettings"
                                            )
                                        )
                                    )
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(
                                            Intent().setComponent(
                                                ComponentName(
                                                    "com.android.settings",
                                                    "com.samsung.android.settings.SideKeySettings"
                                                )
                                            )
                                        )
                                    } catch (_: Exception) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_SETTINGS)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Button Settings")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Delete All Data section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Delete All Data",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permanently delete all app data from this device, including your submission history, pending notes, and saved credentials. Your notes already pushed to GitHub are not affected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete All Data")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all data?") },
            text = {
                Text("This will permanently remove all local data from this device: submission history, pending notes, and your GitHub credentials. You will need to sign in again. Notes already on GitHub are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.clearAllData { onSignedOut() }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSigningOut) showSignOutDialog = false
            },
            title = { Text("Disconnect from GitHub?") },
            text = {
                Column {
                    if (uiState.pendingCount > 0) {
                        val noteWord = if (uiState.pendingCount == 1) "note" else "notes"
                        Text(
                            "You have ${uiState.pendingCount} unsent $noteWord that will not be uploaded until you sign back in.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text("This removes your GitHub credentials from this device.")
                    if (uiState.authType == "oauth") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Note Taker will remain installed on your GitHub account. To uninstall it, use \"Manage on GitHub\" in Settings.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.signOut {
                            showSignOutDialog = false
                            onSignedOut()
                        }
                    },
                    enabled = !uiState.isSigningOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (uiState.isSigningOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!uiState.isSigningOut) showSignOutDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
