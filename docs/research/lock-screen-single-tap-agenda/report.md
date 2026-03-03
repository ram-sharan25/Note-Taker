# Lock Screen Single Tap Agenda Launch - Final Report

**Date:** 2026-03-02  
**Research Status:** Complete

---

## Executive Summary

**What you requested:**
1. Single tap power button to launch Agenda on lock screen
2. Lock screen widget to launch Agenda on lock screen
3. Keep existing long-press for Dictation

**What's technically possible:**
1. ❌ **Power button single tap interception** — Not possible due to Android security restrictions
2. ❌ **Lock screen widgets** — Not available in Android 16 (removed after Android 4.4)
3. ✅ **Alternative solutions exist** that provide quick lock screen access

---

## The Bad News

### Power Button Single Tap: Not Possible

**Why:**
- `KEYCODE_POWER` is a system-reserved hardware key for security reasons
- Android does not allow any app (including with AccessibilityService) to intercept power button events
- This is intentional: power button must always function as a physical kill switch for device locking and emergency access

**Samsung Exception:**
- Samsung devices allow **user-configured** side button remapping in Settings
- User can manually set double-press or long-press to launch your app
- **However:** This is NOT programmatically accessible to your app — the user must do it themselves

### Lock Screen Widgets: Not Available

**Why:**
- Android removed lock screen widget support in Android 5.0 (2014)
- Android 16 does NOT have lock screen widgets
- Jetpack Glance library is for home screen widgets and Wear OS only, NOT lock screen
- Security and design philosophy prevent third-party lock screen customization

---

## The Good News: Practical Alternatives

You have **three excellent options** that provide quick Agenda access from the lock screen:

### ✅ Option 1: Persistent Notification (RECOMMENDED)

**What it is:**
A permanent notification in your notification shade with an "Open Agenda" action button.

**User experience:**
1. From lock screen: Swipe down notification shade
2. Tap "Open Agenda" button in notification
3. Agenda screen opens instantly (no unlock required)

**Benefits:**
- Single tap after one swipe down
- Works on all Android versions and manufacturers
- Can have multiple actions (Agenda, Dictation, Inbox)
- User can dismiss notification if they don't want it
- Requires `showWhenLocked=true` flag on target activity (already implemented)

**Drawbacks:**
- Takes up permanent notification space
- Requires one swipe before tap (not pure "single tap")
- Some users may find persistent notifications annoying (make it optional)

**Implementation effort:** Low (1-2 hours)

---

### ✅ Option 2: Quick Settings Tile

**What it is:**
A custom tile in the Quick Settings panel (swipe down from top).

**User experience:**
1. From lock screen: Swipe down once (shows notifications)
2. Swipe down again (shows Quick Settings)
3. Tap "Agenda" tile
4. Agenda screen opens

**Benefits:**
- Native Android feature, excellent UX
- Works great when device is already unlocked (just one swipe down)
- Can create multiple tiles (Agenda, Dictation, Inbox)
- Users can rearrange or hide tiles as they prefer

**Drawbacks:**
- Requires two swipes from lock screen
- User must manually add tile to Quick Settings during setup
- API Level 24+ (Android 7.0+, you're targeting 29+ so this is fine)

**Implementation effort:** Low (1-2 hours per tile)

---

### ✅ Option 3: Samsung Side Key Setup Guide (Documentation Only)

**What it is:**
Guide Samsung users to manually configure double-tap power button.

**User experience:**
1. User follows in-app guide to configure Samsung settings
2. Go to `Settings > Advanced Features > Side Button`
3. Set "Double press" to "Open app" → Select "Note Taker"
4. Now double-tap power button launches your app directly to Agenda

**Benefits:**
- True hardware button access (double-tap power)
- Very fast from lock screen
- Samsung devices have ~20% global market share
- No ongoing notification or UI clutter

**Drawbacks:**
- Only works on Samsung devices
- Requires manual user setup (not automatic)
- Double-tap, not single-tap
- Will launch app, but you need to detect and route to Agenda vs Dictation

**Implementation effort:** Very low (just documentation + detection)

---

## Recommended Solution: Combination Approach

Implement **all three options** and let users choose:

1. **Persistent Notification** (default for all users):
   - Add Settings toggle: "Show quick access notification"
   - When enabled, start foreground service with notification
   - Notification has two actions: "Agenda" and "Dictate"
   - Works everywhere, all the time

2. **Quick Settings Tile** (power users):
   - Implement `AgendaTile` service
   - Show one-time tutorial after first app launch
   - Great for users who want cleaner notification shade

3. **Samsung Side Key Guide** (Samsung users):
   - Auto-detect Samsung devices (`Build.MANUFACTURER`)
   - Show special tutorial card in Settings screen
   - Deep link to Samsung settings if possible

---

## Technical Implementation Plan

### Phase 1: Create Unified Lock Screen Activity

**New file:** `AgendaLockScreenActivity.kt`

```kotlin
@AndroidEntryPoint
class AgendaLockScreenActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Route based on intent extra
        val targetScreen = intent.getStringExtra("target_screen") ?: "agenda"
        
        setContent {
            NoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (targetScreen) {
                        "agenda" -> AgendaScreen(...)
                        "dictation" -> NoteInputScreen(...)
                        "inbox" -> InboxCaptureScreen(...)
                    }
                }
            }
        }
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

### Phase 2: Persistent Notification Service

**New file:** `QuickAccessNotificationService.kt`

```kotlin
class QuickAccessNotificationService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        val agendaIntent = PendingIntent.getActivity(
            this, REQUEST_AGENDA,
            Intent(this, AgendaLockScreenActivity::class.java).apply {
                putExtra("target_screen", "agenda")
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val dictationIntent = PendingIntent.getActivity(
            this, REQUEST_DICTATION,
            Intent(this, NoteCaptureActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notes)
            .setContentTitle("Quick Capture")
            .setContentText("Fast access to your notes")
            .addAction(R.drawable.ic_agenda, "Agenda", agendaIntent)
            .addAction(R.drawable.ic_mic, "Dictate", dictationIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

**Settings toggle:**
- Add DataStore preference: `quick_access_notification_enabled`
- Add switch in SettingsScreen
- Start/stop service based on preference

### Phase 3: Quick Settings Tile

**New file:** `tiles/AgendaTile.kt`

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
    
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Agenda"
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
    android:label="Agenda"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

### Phase 4: Samsung Side Key Guide

**In SettingsScreen:**
```kotlin
// Detect Samsung device
if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
    SamsungSideKeyGuideCard(
        onOpenSettings = {
            // Try to open Samsung side key settings
            try {
                startActivity(Intent("com.samsung.settings.ACTION_SIDE_KEY"))
            } catch (e: ActivityNotFoundException) {
                // Fallback to general settings
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    )
}
```

---

## Comparison Table

| Feature | Power Button | Notification | Quick Settings | Samsung Guide |
|---------|--------------|--------------|----------------|---------------|
| **Single tap?** | ❌ Not possible | ✅ After 1 swipe | ❌ After 2 swipes | ✅ Double-tap |
| **Lock screen?** | N/A | ✅ Yes | ✅ Yes | ✅ Yes |
| **All devices?** | N/A | ✅ Yes | ✅ Yes | ❌ Samsung only |
| **Automatic?** | N/A | ✅ Yes | ⚠️ User adds tile | ❌ Manual setup |
| **Effort** | N/A | Low (2 hours) | Low (2 hours) | Very low (docs) |

---

## Recommendation

**Implement Persistent Notification + Quick Settings Tile + Samsung Guide:**

1. **Default behavior:** Enable persistent notification on first launch
   - Provides immediate value to all users
   - Most direct lock screen access available
   - Make it easy to disable in Settings

2. **Show Quick Settings tutorial** after setup
   - Guide users to add tile to Quick Settings
   - Better long-term UX for power users
   - Cleaner than persistent notification

3. **Samsung users get bonus guide**
   - Auto-detect Samsung devices
   - Show special setup card in Settings
   - Hardware button access is best UX for Samsung users

**Total implementation time:** ~4-6 hours for all three approaches

---

## Questions for You

Before I implement, please confirm:

1. **Persistent notification**:
   - Enable by default, or make it opt-in?
   - Should it have both Agenda and Dictation actions, or just Agenda?

2. **Quick Settings**:
   - One tile (just Agenda), or three tiles (Agenda, Dictation, Inbox)?

3. **Existing long-press**:
   - Keep it pointing to Dictation (current behavior)?
   - Or also route to Agenda based on some logic?

4. **Priority**:
   - Which approach do you want first? (I recommend starting with persistent notification)

Let me know your preferences and I'll start implementing!
