# Lock Screen Single Tap Agenda Launch - Research Notes

**Date:** 2026-03-02

## Sources

1. Android Developer Documentation - KeyEvent API
2. Android Developer Documentation - Jetpack Glance
3. Android Developer Documentation - VoiceInteractionService
4. Android Developer Documentation - Notifications
5. Wikipedia - Android version history (Android 16)
6. Stack Overflow - Power button interception discussions
7. XDA Developers - Custom ROM kernel restrictions
8. Samsung Developer - OneUI customization documentation

## Key Findings

**TL;DR:**
- ❌ **Power button single tap interception is NOT possible** for security reasons
- ❌ **Lock screen widgets are NOT available** in Android 16 (removed after Android 4.4)
- ✅ **Persistent notification** with lock screen action is the best alternative
- ✅ **Current long-press approach** (VoiceInteractionService) is optimal for power button
- ✅ **Quick Settings Tile** can provide single-tap access when device is unlocked

---

## Power Button Single Tap Intercept

### Search Results: "Android intercept power button single press"

**Finding:** Power button events **cannot be intercepted by third-party apps** at all.

**Technical Details:**
- `KEYCODE_POWER` is a **system-reserved hardware key**
- Even `onKeyDown()`, `KeyEvent` listeners, and `dispatchKeyEvent()` do not receive power button events
- AccessibilityService **does not receive power button key events**
- This is by design for security and emergency access

**Why This Restriction Exists:**
1. **Security**: Power button must function as physical kill switch
2. **Emergency access**: Must work to lock device instantly
3. **Hardware control**: Needs to work even when OS is frozen
4. **Boot/recovery**: Used in hardware combinations for system recovery mode

[Source: Android KeyEvent API Reference, Stack Overflow consensus][1]

### Samsung-Specific Capabilities

**Finding:** Samsung OneUI provides **user-configurable** side button remapping, but **not programmatically accessible**.

**Samsung Side Key Settings:**
- Path: `Settings > Advanced Features > Side Button`
- User can manually set single press, double press, or long press to launch custom apps
- Options include: Power off menu, Bixby, Camera, or custom app Intent
- **Critical limitation**: User must configure manually; apps cannot set this programmatically

**Bixby Routines:**
- User can create automation: "When power button pressed → launch app"
- May work for double-press or gesture patterns
- Requires manual setup by user in Bixby Routines app
- Not accessible via public APIs

[Source: Samsung Developer OneUI documentation][2]

### Workarounds Explored

**Screen On/Off Broadcast Receivers:**
```kotlin
// Can detect screen wake, but NOT power button specifically
registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
```

**Limitations:**
- Cannot distinguish between power button vs. fingerprint vs. lift-to-wake vs. notification tap
- High battery drain if monitoring constantly
- Does not provide "single tap" semantic behavior
- User may wake device many times without wanting app launch

**Verdict:** Not suitable for "single tap power button to launch agenda" requirement.

[Source: Android BroadcastReceiver documentation][3]

---

## Lock Screen Widgets (Android 16)

### Search Results: "Android 16 lock screen widgets"

**Finding:** Android 16 **does NOT support third-party lock screen widgets**.

**Historical Context:**
- Android 4.2-4.4 (KitKat) had lock screen widget support
- **Removed in Android 5.0 (Lollipop)** for security and complexity reasons
- Never re-introduced in subsequent versions through Android 16

**Android 16 Release Info:**
- Released June 2025 (unusually early schedule)
- API Level 36, codename "Baklava"
- No lock screen widget APIs announced in feature list
- Focus on privacy, performance, and AI integration

[Source: Wikipedia Android version history, Android 16 release notes][4]

### Jetpack Glance Library

**Finding:** Glance is for **home screen widgets and Wear OS**, NOT lock screen.

**What Glance Provides:**
- Declarative framework using Compose-like syntax
- Targets: App Widgets (home screen), Wear OS tiles/complications
- **Does NOT support lock screen placement** on phones/tablets

**API Support:**
```kotlin
// Glance is for AppWidgetProvider, not lock screen
@Composable
fun MyAppWidget() {
    GlanceAppWidget(...)
}
```

**Why Lock Screen Not Supported:**
- **Security concerns**: Widgets could expose sensitive data without authentication
- **Design philosophy**: Android moved to controlled "At-A-Glance" widget (Google-only)
- Third-party lock screen customization requires overlay permissions (poor UX)

[Source: Jetpack Glance documentation][5]

### What IS Available on Lock Screen

**Native Android Lock Screen Features:**
1. **Notification actions** - Interactive buttons on notifications
2. **Media controls** - Music/video playback controls
3. **At-A-Glance widget** - Pixel devices only, not extensible to third parties
4. **Camera/Assistant shortcuts** - System-defined, not customizable

**For Third-Party Apps:**
- Notifications with `PendingIntent` actions work on lock screen
- Can show full-screen intents (similar to alarm apps)
- Cannot add custom persistent UI elements like widgets

[Source: Android Notifications API documentation][6]

---

## Alternative Approaches

Given the constraints, here are viable alternatives for single-tap Agenda access:

### 1. Persistent Notification with Lock Screen Action ✅ (RECOMMENDED)

**How it works:**
```kotlin
val agendaIntent = PendingIntent.getActivity(
    context,
    REQUEST_CODE_AGENDA,
    Intent(context, AgendaLockScreenActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE
)

val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_agenda)
    .setContentTitle("Quick Agenda")
    .setContentText("Tap to view your tasks")
    .addAction(R.drawable.ic_open, "Open Agenda", agendaIntent)
    .setOngoing(true) // Persistent
    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
    .setPriority(NotificationCompat.PRIORITY_LOW) // Minimize distraction
    .build()
```

**Benefits:**
- ✅ Single tap from lock screen (no unlock required)
- ✅ Always visible in notification shade
- ✅ Works on all Android versions
- ✅ Can have multiple actions (Dictation, Agenda, Inbox)
- ✅ User can dismiss if they don't want it

**Tradeoffs:**
- Takes up notification space
- User must swipe down to access (not truly "single tap")
- Some users may find persistent notifications annoying

### 2. Quick Settings Tile ✅ (GOOD FOR UNLOCKED ACCESS)

**Implementation:**
```kotlin
class AgendaTile : TileService() {
    override fun onClick() {
        val intent = Intent(this, AgendaLockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
```

**Benefits:**
- ✅ Two swipes from lock screen (swipe down twice → tap tile)
- ✅ Works great when device is unlocked (one swipe down)
- ✅ Native Android feature, good UX
- ✅ Can add multiple tiles (Dictation, Agenda, Inbox)

**Tradeoffs:**
- Not truly "single tap" from lock screen
- Requires user to add tile to Quick Settings

### 3. Lock Screen Shortcut Double-Tap (Samsung Only) ⚠️

**User Documentation Approach:**
Guide Samsung users to configure manually:

**Setup Steps:**
1. Go to `Settings > Advanced Features > Side Button`
2. Set "Double press" to "Open app"
3. Select "Note Taker"
4. Now double-tap power button launches your app

**In-App Guidance:**
```kotlin
// Detect Samsung device
if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
    // Show tutorial card in Settings screen
    showSamsungSideKeySetupGuide()
}
```

**Benefits:**
- ✅ True hardware button access (double-tap)
- ✅ Very fast from lock screen
- ✅ Samsung devices have good market share

**Tradeoffs:**
- Only works on Samsung devices
- Requires manual user setup (not programmatic)
- Not "single tap" (double-tap)

### 4. Voice Activation (Existing Google Assistant Integration)

**How it works:**
User can say "Hey Google, open Note Taker" or create custom Google Assistant routine.

**Benefits:**
- ✅ Works from lock screen
- ✅ Truly hands-free
- ✅ No physical tap needed
- ✅ Already available via Android ecosystem

**Tradeoffs:**
- Requires speaking out loud (not discreet)
- Requires network connection for "Hey Google"
- May not work in noisy environments

---

## Implementation Architecture

### Current State

**Lock Screen Entry Points:**
```
Side button long-press → NoteAssistService.onLaunchVoiceAssistFromKeyguard()
                      → NoteCaptureActivity (shows Dictation screen)
```

**Normal App Launch:**
```
App icon tap → MainActivity → MainScreen (HorizontalPager, default page = Agenda)
```

### Proposed Architecture: Multiple Lock Screen Entry Points

**Goal:** Support both Dictation and Agenda launches from lock screen.

**Option A: Single Activity with Intent Extras (RECOMMENDED)**

Create one lock screen activity that routes based on intent extras:

```kotlin
// New: AgendaLockScreenActivity.kt
@AndroidEntryPoint
class AgendaLockScreenActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val targetScreen = intent.getStringExtra("target_screen") ?: "agenda"
        
        setContent {
            NoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (targetScreen) {
                        "agenda" -> com.rrimal.notetaker.ui.screens.agenda.AgendaScreen(
                            onBack = { finish() },
                            onSettingsClick = { dismissAndNavigate("open_settings") },
                            onBrowseClick = { dismissAndNavigate("open_browse") }
                        )
                        "dictation" -> NoteInputScreen(
                            onSettingsClick = { dismissAndNavigate("open_settings") },
                            onBrowseClick = { dismissAndNavigate("open_browse") }
                        )
                        "inbox" -> InboxCaptureScreen(
                            onBack = { finish() },
                            onSettingsClick = { dismissAndNavigate("open_settings") },
                            onBrowseClick = { dismissAndNavigate("open_browse") }
                        )
                    }
                }
            }
        }
    }
    
    private fun dismissAndNavigate(extraKey: String) {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                startActivity(Intent(this@AgendaLockScreenActivity, MainActivity::class.java).apply {
                    putExtra(extraKey, true)
                })
                finish()
            }
        })
    }
}
```

**AndroidManifest.xml:**
```xml
<activity
    android:name=".AgendaLockScreenActivity"
    android:exported="false"
    android:showWhenLocked="true"
    android:turnScreenOn="true" />
```

**Option B: Separate Activities (More Explicit)**

Keep NoteCaptureActivity for Dictation, create new AgendaLockScreenActivity for Agenda.

**Pros:**
- Clear separation of concerns
- Easier to maintain different behaviors

**Cons:**
- More code duplication
- More manifest entries

**Recommendation:** Use Option A (single activity with routing) for simplicity.

### Integration with Notification

**Persistent Notification Service:**
```kotlin
class QuickAccessNotificationService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        val agendaIntent = PendingIntent.getActivity(
            this,
            REQUEST_AGENDA,
            Intent(this, AgendaLockScreenActivity::class.java).apply {
                putExtra("target_screen", "agenda")
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val dictationIntent = PendingIntent.getActivity(
            this,
            REQUEST_DICTATION,
            Intent(this, NoteCaptureActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notes)
            .setContentTitle("Quick Capture")
            .setContentText("Tap to open Note Taker")
            .addAction(R.drawable.ic_agenda, "Agenda", agendaIntent)
            .addAction(R.drawable.ic_mic, "Dictate", dictationIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

**User Preference:**
- Add toggle in Settings: "Show persistent notification"
- Start service when enabled, stop when disabled
- Store preference in DataStore

### Integration with Quick Settings Tile

**Tile Implementation:**
```kotlin
@RequiresApi(24)
class AgendaTile : TileService() {
    
    override fun onClick() {
        val intent = Intent(this, AgendaLockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("target_screen", "agenda")
        }
        startActivityAndCollapse(intent)
    }
    
    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }
    
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }
    
    private fun updateTileState() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Agenda"
            contentDescription = "Open agenda view"
            updateTile()
        }
    }
}
```

**AndroidManifest.xml:**
```xml
<service
    android:name=".tiles.AgendaTile"
    android:icon="@drawable/ic_agenda"
    android:label="@string/tile_agenda_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

### Summary of Implementation Plan

**Three Access Methods:**

1. **Long-press power button** (existing) → Dictation screen
2. **Persistent notification** (new) → Agenda or Dictation screen  
3. **Quick Settings tile** (new) → Agenda screen

**Codebase Changes:**
- Create `AgendaLockScreenActivity` (or rename/refactor existing `NoteCaptureActivity`)
- Create `QuickAccessNotificationService`
- Create `AgendaTile` (Quick Settings)
- Add Settings toggle for persistent notification
- Add user guidance for Samsung side key setup

**Follow-up Questions:**
1. Do you want the persistent notification by default, or opt-in?
2. Should notification have both Agenda and Dictation actions, or just Agenda?
3. Should we add Quick Settings tiles for all three screens (Dictation, Agenda, Inbox)?

