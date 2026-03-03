package com.rrimal.notetaker.data.storage

/**
 * Constants defining the standardized phone inbox directory structure.
 * 
 * Structure:
 * ```
 * phone_inbox/                  # User-selected root (via SAF)
 * ├── dictations/               # Voice/quick notes (*.org files)
 * ├── inbox/                    # TODO inbox directory
 * │   └── inbox.org            # Inbox TODO entries
 * ├── sync/                     # JSON state changes for Emacs
 * └── agenda.org                # Emacs-generated agenda view
 * ```
 */
object PhoneInboxStructure {
    // Subdirectory names (hardcoded, not user-configurable)
    const val DICTATIONS_DIR = "dictations"
    const val INBOX_DIR = "inbox"
    const val SYNC_DIR = "sync"
    
     // Filenames (hardcoded, not user-configurable)
    const val INBOX_FILENAME = "inbox.org"
    const val QUICK_FILENAME = "quick.org"
    const val AGENDA_FILENAME = "agenda.org"
    
    // Relative paths (for convenience)
    const val INBOX_FILE_PATH = "$INBOX_DIR/$INBOX_FILENAME"  // "inbox/inbox.org"
    const val QUICK_FILE_PATH = "$INBOX_DIR/$QUICK_FILENAME"  // "inbox/quick.org"
    
    // List of required subdirectories to create
    val REQUIRED_SUBDIRECTORIES = listOf(
        DICTATIONS_DIR,
        INBOX_DIR,
        SYNC_DIR
    )
}
