package com.rrimal.notetaker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.rrimal.notetaker.data.auth.AuthManager
import com.rrimal.notetaker.data.storage.LocalFileManager
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.ui.navigation.AppNavGraph
import com.rrimal.notetaker.ui.theme.NoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import com.rrimal.notetaker.data.worker.OrgFileSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var localFileManager: LocalFileManager
    @Inject lateinit var storageConfigManager: StorageConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize phone inbox directory structure for JSON sync workflow (v0.9.0)
        lifecycleScope.launch {
            try {
                localFileManager.ensurePhoneInboxStructure().getOrThrow()
                Log.d(TAG, "Phone inbox directory structure initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize phone inbox structure", e)
                // Non-fatal: structure will be created on-demand when needed
            }
        }

        // Migration for existing users: auto-complete onboarding if already configured
        lifecycleScope.launch {
            try {
                val onboardingComplete = storageConfigManager.onboardingComplete.first()
                
                if (!onboardingComplete) {
                    // Check if user already has configuration (upgrading from v0.8.0)
                    val hasGitHubAuth = authManager.isAuthenticated.first()
                    val hasLocalFolders = storageConfigManager.localFolderUri.first() != null
                    
                    if (hasGitHubAuth || hasLocalFolders) {
                        // User is upgrading - already configured via old Settings flow
                        storageConfigManager.markOnboardingComplete()
                        Log.d(TAG, "Migrated existing user: marked onboarding complete")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check onboarding migration", e)
            }
        }

        // Schedule background sync for agenda
        scheduleAgendaSync()

        val initialRoute = when {
            intent.getBooleanExtra("open_settings", false) -> "settings"
            intent.getBooleanExtra("open_browse", false) -> "browse"
            else -> null
        }

        setContent {
            NoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(
                        authManager = authManager,
                        storageConfigManager = storageConfigManager,
                        initialRoute = initialRoute
                    )
                }
            }
        }
    }

    private fun scheduleAgendaSync() {
        val syncRequest = PeriodicWorkRequestBuilder<OrgFileSyncWorker>(
            15, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "agenda_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    /**
     * Enter fullscreen mode (hide system bars) for Pomodoro timer.
     */
    fun enterFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    /**
     * Exit fullscreen mode (show system bars).
     */
    fun exitFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(
            WindowInsetsCompat.Type.systemBars()
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
