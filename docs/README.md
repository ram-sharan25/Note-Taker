# Documentation Index

## Quick Navigation

### 📱 For Users
- [Requirements](REQUIREMENTS.md) - Complete feature specifications
- [Roadmap](ROADMAP.md) - Completed features and future plans
- [PAT Setup Guide](PAT-SETUP.md) - GitHub Personal Access Token setup
- [App Trigger Guide](APP-TRIGGER.md) - Lock screen launch setup
- [Toggl Integration](TOGGL-INTEGRATION.md) - Time tracking setup and usage

### 👨‍💻 For Developers
- [CLAUDE.md](../CLAUDE.md) - **Start here** - AI assistant context and project overview
- [Deployment Guide](DEPLOYMENT.md) - CI/CD pipeline and release process
- [Architecture Decision Records](adr/) - Key design decisions and rationale

### 🚀 Upcoming Features
- **[JSON Sync (v0.9.0)](JSON_SYNC_SUMMARY.md)** - Overview of upcoming agenda simplification
  - [Implementation Plan](JSON_SYNC_IMPLEMENTATION_PLAN.md) - Complete technical specification
  - [Quick Reference](JSON_SYNC_QUICK_REFERENCE.md) - Code snippets and cheat sheet
  - [Diagrams](JSON_SYNC_DIAGRAMS.md) - Visual architecture and workflows

## Documentation by Topic

### Core Features
| Feature | User Docs | Developer Docs |
|---------|-----------|----------------|
| Note Input & Voice Dictation | [FR1, FR9 in REQUIREMENTS.md](REQUIREMENTS.md#fr1-note-input) | `ui/screens/NoteInputScreen.kt` |
| GitHub Integration | [FR3, FR5 in REQUIREMENTS.md](REQUIREMENTS.md#fr3-push-to-github) | [ADR 001: PAT over OAuth](adr/001-pat-over-oauth.md) |
| Lock Screen Launch | [FR6 in REQUIREMENTS.md](REQUIREMENTS.md#fr6-lock-screen-launch), [APP-TRIGGER.md](APP-TRIGGER.md) | `assist/NoteAssistService.kt` |
| Offline Note Queue | [FR7 in REQUIREMENTS.md](REQUIREMENTS.md#fr7-offline-note-queuing) | `workers/NoteUploadWorker.kt` |
| Local Org Files | [FR10 in REQUIREMENTS.md](REQUIREMENTS.md#fr10-local-org-files-storage) | `storage/LocalFileManager.kt` |
| Inbox Capture | [FR11 in REQUIREMENTS.md](REQUIREMENTS.md#fr11-inbox-capture-org-mode-todo) | `ui/screens/InboxCaptureScreen.kt` |
| Agenda View | [FR13 in REQUIREMENTS.md](REQUIREMENTS.md#fr13-agenda-view), [JSON Sync](JSON_SYNC_SUMMARY.md) | [ADR 003: Agenda Architecture](adr/003-agenda-view-with-orgzly-architecture.md) |
| Org-Mode Viewer | [FR14 in REQUIREMENTS.md](REQUIREMENTS.md#fr14-high-fidelity-org-mode-viewer) | [ADR 004: Org Viewer](adr/004-high-fidelity-org-viewer.md) |
| Swipeable Navigation | [FR15 in REQUIREMENTS.md](REQUIREMENTS.md#fr15-swipeable-navigation-with-agenda-first-design) | `ui/screens/MainScreen.kt` |
| Toggl Time Tracking | [FR16 in REQUIREMENTS.md](REQUIREMENTS.md#fr16-toggl-track-time-tracking), [TOGGL-INTEGRATION.md](TOGGL-INTEGRATION.md) | `data/repository/TogglRepository.kt` |

### Architecture
| Topic | Document |
|-------|----------|
| Authentication (PAT vs OAuth) | [ADR 001](adr/001-pat-over-oauth.md) |
| Nepali Language Support | [ADR 002](adr/002-nepali-language-support.md) |
| Agenda Database Architecture | [ADR 003](adr/003-agenda-view-with-orgzly-architecture.md) |
| Org-Mode Viewer Design | [ADR 004](adr/004-high-fidelity-org-viewer.md) |
| Agenda Architecture Alternatives | [ADR 005](adr/005-agenda-architecture-alternatives.md) |

### Special Topics
| Topic | Document |
|-------|----------|
| Phone Time Tracking Behavior | [PHONE-TIME-TRACKING.md](PHONE-TIME-TRACKING.md) |
| Agenda Debugging | [AGENDA-DEBUGGING.md](AGENDA-DEBUGGING.md) |
| Agenda Refresh Feature | [features/AGENDA_REFRESH.md](features/AGENDA_REFRESH.md) |

## Play Store Assets
- Screenshots: `playstore/screenshots/`
- Store listing: `playstore/store-listing.md`
- Privacy policy: `playstore/privacy-policy.md`

## Version History

| Version | Status | Key Features |
|---------|--------|--------------|
| 0.1.0 | ✅ Released | Basic note input, GitHub push |
| 0.2.0 | ✅ Released | OAuth, Browse screen |
| 0.3.0 | ✅ Released | Local org files, Storage Access Framework |
| 0.4.0 | ✅ Released | Inbox capture, improved dictation |
| 0.5.0 | ✅ Released | Nepali language support |
| 0.6.0 | ✅ Released | High-fidelity org viewer |
| 0.7.0 | ✅ Released | Agenda view (database-centric) |
| 0.8.0 | ✅ Released | Swipeable navigation, Toggl integration |
| **0.9.0** | 📋 Planning | **JSON Sync** - Simplified agenda architecture |

## Understanding the JSON Sync Initiative (v0.9.0)

The next major release will significantly simplify the agenda system by removing direct file editing from the mobile app.

### What's Changing?
- Mobile writes **JSON files** instead of editing org files directly
- Emacs becomes the **single source of truth** for org file changes
- **~800 lines of code removed** from mobile app

### Why?
- Eliminates race conditions
- Safer (can't corrupt org files)
- Simpler codebase
- Clear audit trail

### Learn More
1. **Start here:** [JSON Sync Summary](JSON_SYNC_SUMMARY.md) - High-level overview
2. **For implementation:** [Implementation Plan](JSON_SYNC_IMPLEMENTATION_PLAN.md) - Complete technical spec
3. **Quick lookup:** [Quick Reference](JSON_SYNC_QUICK_REFERENCE.md) - Code snippets
4. **Visual guide:** [Diagrams](JSON_SYNC_DIAGRAMS.md) - Architecture flowcharts

## Contributing

Before implementing features:
1. Read [CLAUDE.md](../CLAUDE.md) for project context
2. Check [REQUIREMENTS.md](REQUIREMENTS.md) for feature specifications
3. Review relevant ADRs for design decisions
4. Follow established patterns (MVVM, Repository, Hilt)

## Git Workflow

See [DEPLOYMENT.md](DEPLOYMENT.md) for:
- Branch strategy (develop → staging → master)
- Versioning (versionCode, versionName)
- Release process
- CI/CD pipeline

## Need Help?

- **For users:** Check [REQUIREMENTS.md](REQUIREMENTS.md) for feature documentation
- **For developers:** Start with [CLAUDE.md](../CLAUDE.md) and relevant ADRs
- **For JSON Sync:** See [JSON Sync Summary](JSON_SYNC_SUMMARY.md)

---

*Last Updated: 2026-03-02*
*Current Version: 0.8.0*
*Next Version: 0.9.0 (JSON Sync - In Planning)*
