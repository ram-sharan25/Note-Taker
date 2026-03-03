package com.rrimal.notetaker.ui.screens.toggl

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rrimal.notetaker.ui.viewmodels.TogglSettingsViewModel
import kotlinx.coroutines.launch

/**
 * Toggl Track Settings UI Component
 * 
 * Allows users to:
 * - Configure Toggl API token
 * - Enable/disable automatic time tracking
 * - Select default project
 * - Test connection
 */
@Composable
fun TogglSettingsCard(
    viewModel: TogglSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var showApiTokenDialog by remember { mutableStateOf(false) }
    var apiTokenInput by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Toggl Track Integration",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Automatically start/stop Toggl timers when TODO states change to/from IN-PROGRESS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (uiState.isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (uiState.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isConfigured) "Connected" else "Not configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            
            if (uiState.isConfigured && uiState.workspaceId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Workspace ID: ${uiState.workspaceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (uiState.defaultProjectName != null) {
                Text(
                    text = "Default Project: ${uiState.defaultProjectName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Enable/Disable Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Time Tracking",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Auto-start timer on IN-PROGRESS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setEnabled(enabled)
                        }
                    },
                    enabled = uiState.isConfigured
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Configure API Token Button
            OutlinedButton(
                onClick = { showApiTokenDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isConfigured) "Change API Token" else "Configure API Token")
            }
            
            // Sync Projects Button
            if (uiState.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.syncProjects()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSyncing
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Sync Projects")
                }
            }
            
            // Disconnect Button
            if (uiState.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.disconnect()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
            
            // Error Message
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Success Message
            if (uiState.successMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = uiState.successMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
    
    // API Token Dialog
    if (showApiTokenDialog) {
        AlertDialog(
            onDismissRequest = { showApiTokenDialog = false },
            title = { Text("Configure Toggl API Token") },
            text = {
                Column {
                    Text(
                        text = "Get your API token from Toggl Track:\n1. Go to track.toggl.com/profile\n2. Scroll to \"API Token\"\n3. Copy and paste below",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = apiTokenInput,
                        onValueChange = { apiTokenInput = it },
                        label = { Text("API Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.saveApiToken(apiTokenInput)
                            if (success) {
                                showApiTokenDialog = false
                                apiTokenInput = ""
                            }
                        }
                    },
                    enabled = apiTokenInput.isNotBlank() && !uiState.isSyncing
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save & Test")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApiTokenDialog = false
                    apiTokenInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
