# Architecture Overview

## System Architecture

The Note Taker app follows a **Layered Architecture** with clear separation of concerns, utilizing modern Android development best practices.

### Core Layers
1. **UI Layer (Jetpack Compose):** Screens and reusable components observe state from ViewModels.
2. **Presentation Layer (ViewModels):** Manages UI state and orchestrates business logic by calling Repositories.
3. **Domain/Repository Layer:** Provides a clean API for the UI layer to interact with data. Handles the complexity of multiple data sources (Room, API, SAF).
4. **Data Layer:**
   - **Local Storage:** Room Database for caching agenda and queuing notes.
   - **File Storage:** Storage Access Framework (SAF) for direct interaction with `.org` files.
   - **Remote API:** GitHub API for note submission and repository browsing.
   - **External APIs:** Toggl Track for time tracking.

---

## Technical Stack

- **Language:** Kotlin 2.2.10
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt (Dependency Injection)
- **Database:** Room (SQLite)
- **Network:** Retrofit + OkHttp
- **Serialization:** Kotlinx Serialization
- **Background Tasks:** WorkManager
- **Security:** EncryptedSharedPreferences (Android Keystore)
- **Speech:** Android SpeechRecognizer API
- **Navigation:** Type-safe Compose Navigation

---

## Architectural Principles

### 1. Database-Centric Agenda (Orgzly-Inspired)
To ensure high performance, we don't parse large `.org` files on every screen load. Instead:
- We sync file contents into a normalized Room database.
- We use SHA-256 hashes to detect changes and skip unnecessary parsing.
- Recurring tasks are expanded in-memory from the database records for the visible date range.
- *See [ADR 003](003-agenda-view-with-orgzly-architecture.md) and [ADR 005](005-agenda-architecture-alternatives.md) for details.*

### 2. Offline-First Submission
Note capture is designed for reliability in poor network conditions:
- Notes are always saved to a `pending_notes` table in Room first.
- `WorkManager` handles the background upload to GitHub with exponential backoff.
- The UI provides immediate "Sent!" or "Queued" feedback.

### 3. AST-Based Org Parsing
Our `OrgParser` uses a recursive descent approach to build an Abstract Syntax Tree (AST) of the org file. This allows for:
- High-fidelity rendering in the `OrgFileViewer`.
- Structured updates via `OrgWriter`.
- *See [ADR 004](004-high-fidelity-org-viewer.md) for details.*

### 4. Hybrid Sync (JSON + Direct)
- **JSON Sync:** For most agenda files, the phone writes "Change Requests" (JSON) that Emacs processes. This prevents file corruption and race conditions.
- **Direct Edit:** For `quick.org`, the phone edits the file directly, as it is the primary owner of that file until refiling.
- *See [JSON Sync Summary](../JSON_SYNC_SUMMARY.md) for details.*

---

## <a name="adrs"></a>Architecture Decision Records (ADRs)

We document major technical decisions using ADRs to preserve context for future development:

| ID | Title | Status |
|----|-------|--------|
| [001](001-pat-over-oauth.md) | Authentication Strategy | Accepted (OAuth Primary) |
| [002](002-nepali-language-support.md) | Nepali Language Support | Phased Implementation |
| [003](003-agenda-view-with-orgzly-architecture.md) | Agenda View Architecture | Accepted (DB-Centric) |
| [004](004-high-fidelity-org-viewer.md) | High-Fidelity Org Viewer | Accepted (AST-Driven) |
| [005](005-agenda-architecture-alternatives.md) | Agenda Architecture Alternatives | Accepted (Single-File) |

---
*Last Updated: 2026-03-04*
