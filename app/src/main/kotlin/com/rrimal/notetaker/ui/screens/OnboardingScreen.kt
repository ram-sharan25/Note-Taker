package com.rrimal.notetaker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rrimal.notetaker.ui.viewmodels.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate to MainRoute when onboarding completes
    LaunchedEffect(uiState.isCompleting) {
        if (uiState.isCompleting) {
            // Small delay for smooth transition
            kotlinx.coroutines.delay(300)
            onComplete()
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Folder pickers
    val rootFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onRootFolderSelected(uri)
        } else {
            viewModel.onFolderPickerCancelled()
        }
    }

    val phoneInboxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhoneInboxFolderSelected(uri)
        } else {
            viewModel.onFolderPickerCancelled()
        }
    }

    // Handle back button
    BackHandler(enabled = uiState.step != OnboardingViewModel.OnboardingStep.MODE_SELECTION) {
        viewModel.onBackPressed()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.step != OnboardingViewModel.OnboardingStep.MODE_SELECTION) {
                Column {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.background)
                    )
                    TopAppBar(
                        title = { Text("Setup") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.onBackPressed() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        windowInsets = WindowInsets(0)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState.step) {
                OnboardingViewModel.OnboardingStep.MODE_SELECTION -> {
                    ModeSelectionStep(
                        onGitHubTapped = { viewModel.onGitHubCardTapped() },
                        onLocalTapped = { viewModel.selectLocalMode() }
                    )
                }
                OnboardingViewModel.OnboardingStep.SELECT_ROOT_FOLDER -> {
                    FolderSelectionStep(
                        stepNumber = 1,
                        title = "Select Root Folder",
                        description = "Choose the main folder where all your org files are stored. You'll be able to browse all notes in this folder.",
                        onSelectFolder = { rootFolderPickerLauncher.launch(null) }
                    )
                }
                OnboardingViewModel.OnboardingStep.SELECT_INBOX_FOLDER -> {
                    FolderSelectionStep(
                        stepNumber = 2,
                        title = "Select Phone Inbox Folder",
                        description = "Choose the phone_inbox folder for dictations, TODOs, and agenda files.",
                        showDirectoryStructure = true,
                        onSelectFolder = { phoneInboxPickerLauncher.launch(null) }
                    )
                }
            }

            if (uiState.isCompleting) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Text(
                    "Setting up...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModeSelectionStep(
    onGitHubTapped: () -> Unit,
    onLocalTapped: () -> Unit
) {
    Text(
        "Welcome to Note Taker",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Choose how to store your notes",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(48.dp))

    // GitHub Card (disabled)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onGitHubTapped),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "GitHub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Cloud sync & backup",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "Available in future update",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Local Files Card (enabled)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLocalTapped),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Local Files Only",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Store notes on device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLocalTapped,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Set Up Folders")
            }
        }
    }
}

@Composable
private fun FolderSelectionStep(
    stepNumber: Int,
    title: String,
    description: String,
    showDirectoryStructure: Boolean = false,
    onSelectFolder: () -> Unit
) {
    Text(
        "Step $stepNumber of 2",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    if (showDirectoryStructure) {
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Directory structure:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    """
                    phone_inbox/
                    ├── dictations/     (voice notes)
                    ├── inbox/
                    │   └── inbox.org   (TODO entries)
                    ├── sync/           (state changes)
                    └── agenda.org      (agenda view)
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onSelectFolder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Folder, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Select Folder")
    }
}
