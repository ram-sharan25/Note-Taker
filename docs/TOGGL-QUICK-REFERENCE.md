# Toggl Integration - Quick Reference Card

## User Setup (2 minutes)

1. Get API token: https://track.toggl.com/profile
2. Open Note Taker → Settings → Toggl Track
3. Tap "Configure API Token" → Paste token → Save
4. Enable time tracking toggle
5. Tap "Sync Projects" (optional)

✅ Done! Time tracking is now automatic.

---

## How It Works

| Action | Result |
|--------|--------|
| Any state → **IN-PROGRESS** | ▶️ Timer starts |
| **IN-PROGRESS** → Any other state | ⏹️ Timer stops |

**Active State**: Only `IN-PROGRESS` triggers Toggl operations

---

## Org-Mode Properties

### Set Project Per Task

```org
* IN-PROGRESS Build feature
:PROPERTIES:
:TOGGL_PROJECT_ID: 123456789
:END:
```

### Tags Auto-Sync

```org
* IN-PROGRESS Fix bug  :urgent:mobile:
```
→ Toggl tags: `["urgent", "mobile"]`

---

## Troubleshooting

### Timer Not Starting?

**Check**:
1. Settings → Toggl enabled? (toggle ON)
2. API token configured?
3. Task changed to `IN-PROGRESS`?

**Debug**:
```bash
adb logcat -d | grep "handleTogglStateChange"
```

### "Failed to start time entry: 400"

**Cause**: API configuration issue

**Fix**:
1. Check API token is valid (test on toggl.com)
2. Check logs: `adb logcat -d | grep "TogglRepository"`
3. Re-enter API token in Settings

### "No Toggl API token configured"

**Fix**: Settings → Configure API Token

---

## Debug Commands

```bash
# Clear logs
adb logcat -c

# Watch real-time
adb logcat | grep -i toggl

# Get errors
adb logcat -d | grep "Failed to" | tail -20

# Check enabled state
adb logcat -d | grep "Toggl isEnabled"
```

---

## API Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /me` | Fetch user & projects |
| `POST /workspaces/{id}/time_entries` | Start timer |
| `PATCH /workspaces/{id}/time_entries/{id}/stop` | Stop timer |
| `GET /me/time_entries/current` | Get running timer |

**Base URL**: `https://api.track.toggl.com/api/v9/`

---

## Security

- API token: `EncryptedSharedPreferences` (Android Keystore)
- No tokens logged (password-masked UI)
- HTTPS only (TLS 1.2+)

---

## Files Reference

| File | Purpose |
|------|---------|
| `TogglApi.kt` | API interface |
| `TogglRepository.kt` | Business logic |
| `TogglPreferencesManager.kt` | Configuration |
| `AgendaRepository.kt` | Integration hook |
| `TogglSettingsCard.kt` | Settings UI |

---

## Documentation

📖 **Complete Guide**: `docs/TOGGL-INTEGRATION.md`  
📝 **Implementation**: `docs/TOGGL-IMPLEMENTATION-SUMMARY.md`  
🌐 **API Docs**: https://developers.track.toggl.com/

---

*Quick Reference - v0.8.0*
