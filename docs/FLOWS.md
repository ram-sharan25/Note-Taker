# Data & System Flows

This document visualizes and describes the primary data flows within the Note Taker application.

## 1. Note Capture & GitHub Upload Flow

This flow describes how a note moves from the user's voice/text input to your GitHub repository.

```mermaid
graph TD
    A[User Input: Voice/Text] --> B[ViewModel]
    B --> C[NoteRepository]
    C --> D[Insert into Room: pending_notes]
    D --> E[Initial Push Attempt]
    E -- Success --> F[Delete from pending_notes]
    F --> G[Add to submissions history]
    E -- Failure --> H[Mark as failed]
    H --> I[WorkManager scheduled retry]
    I --> J[Exponential Backoff Upload]
    J -- Success --> F
```

---

## 2. Agenda Synchronization Flow (Desktop to Mobile)

This flow describes how the app refreshes the agenda view based on changes from your desktop.

```mermaid
graph TD
    A[Desktop: Emacs exports agenda.org] --> B[Syncthing Syncs File]
    B --> C[Mobile App: File Observer detects change]
    C --> D[Calculate SHA-256 Hash]
    D -- Hash Match --> E[Done: Skip Sync]
    D -- Hash Mismatch --> F[Parse agenda.org via OrgParser]
    F --> G[Sync into Room Database: notes, timestamps, planning]
    G --> H[Apply Pending Sync Layering]
    H --> I[UI: Display updated state]
```

---

## 3. TODO State Update Flow (JSON Sync)

This flow describes how changing a task's status on your phone updates your desktop source files.

```mermaid
graph TD
    A[User Taps TODO in Agenda] --> B[AgendaViewModel]
    B --> C[Update Room DB: Optimistic UI update]
    C --> D[Write JSON Change Request to sync/ folder]
    D --> E[Syncthing Syncs sync/ to Desktop]
    E --> F[Desktop: Emacs runs M-m s s]
    F --> G[Update source .org files]
    G --> H[Delete processed JSONs]
    H --> I[Emacs runs M-m s e: Export fresh agenda.org]
    I --> J[Syncthing Syncs agenda.org to Mobile]
    J --> K[Mobile Re-Sync Flow]
```

---

## 4. Voice Interaction Flow (Lock Screen)

This flow describes how the app handles a voice capture when the phone is locked.

```mermaid
graph TD
    A[Long-press Power/Side Button] --> B[VoiceInteractionService triggers]
    B --> C[NoteCaptureActivity launches over lock screen]
    C --> D[Auto-start Speech Recognition]
    D --> E[Note Submission Flow]
    E --> F[Activity Finishes]
```

---
*Last Updated: 2026-03-04*
