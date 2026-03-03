package com.rrimal.notetaker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubmissionEntity::class,
        PendingNoteEntity::class,
        SyncQueueEntity::class,
        NoteEntity::class,
        OrgTimestampEntity::class,
        NotePlanningEntity::class,
        FileMetadataEntity::class,
        TodoKeywordsConfigEntity::class,
        PomodoroHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
@androidx.room.TypeConverters(PropertiesConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
    abstract fun pendingNoteDao(): PendingNoteDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun noteDao(): NoteDao
    abstract fun orgTimestampDao(): OrgTimestampDao
    abstract fun notePlanningDao(): NotePlanningDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun todoKeywordsDao(): TodoKeywordsDao
    abstract fun pomodoroHistoryDao(): PomodoroHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pending_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'pending'
                    )"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'pending'
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `headlineId` TEXT NOT NULL,
                        `level` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `todoState` TEXT,
                        `priority` TEXT,
                        `tags` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `parentId` INTEGER,
                        `position` INTEGER NOT NULL,
                        `lastModified` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `org_timestamps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `string` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `day` INTEGER NOT NULL,
                        `hour` INTEGER,
                        `minute` INTEGER,
                        `timestamp` INTEGER NOT NULL,
                        `repeaterType` TEXT,
                        `repeaterValue` INTEGER,
                        `repeaterUnit` TEXT
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `note_planning` (
                        `noteId` INTEGER PRIMARY KEY NOT NULL,
                        `scheduledTimestampId` INTEGER,
                        `deadlineTimestampId` INTEGER,
                        `closedTimestampId` INTEGER,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                        FOREIGN KEY(`scheduledTimestampId`) REFERENCES `org_timestamps`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL ,
                        FOREIGN KEY(`deadlineTimestampId`) REFERENCES `org_timestamps`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL ,
                        FOREIGN KEY(`closedTimestampId`) REFERENCES `org_timestamps`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL 
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `file_metadata` (
                        `filename` TEXT PRIMARY KEY NOT NULL,
                        `lastSynced` INTEGER NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `hash` TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `todo_keywords_config` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `sequence` TEXT NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add properties column to notes table
                db.execSQL("ALTER TABLE notes ADD COLUMN properties TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pomodoro_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `taskTitle` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `isBreak` INTEGER NOT NULL,
                        `completedSuccessfully` INTEGER NOT NULL
                    )"""
                )
            }
        }
    }
}
