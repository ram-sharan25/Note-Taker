package com.rrimal.notetaker.unit.repository

import com.rrimal.notetaker.data.local.AppDatabase
import com.rrimal.notetaker.data.local.FileMetadataDao
import com.rrimal.notetaker.data.local.FileMetadataEntity
import com.rrimal.notetaker.data.local.NoteDao
import com.rrimal.notetaker.data.local.NotePlanningDao
import com.rrimal.notetaker.data.local.OrgTimestampDao
import com.rrimal.notetaker.data.orgmode.OrgParser
import com.rrimal.notetaker.data.orgmode.OrgTimestampParser
import com.rrimal.notetaker.data.orgmode.OrgWriter
import com.rrimal.notetaker.data.preferences.AgendaConfigManager
import com.rrimal.notetaker.data.repository.AgendaRepository
import com.rrimal.notetaker.data.repository.TogglRepository
import com.rrimal.notetaker.data.storage.LocalFileManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CRITICAL TEST: Validates that agenda respects user configuration
 * and does NOT use hardcoded "phone_inbox/agenda.org" path.
 *
 * This test ensures Settings UI actually affects agenda behavior.
 */
class AgendaConfigurationTest {

    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var timestampDao: OrgTimestampDao
    private lateinit var planningDao: NotePlanningDao
    private lateinit var fileMetadataDao: FileMetadataDao
    private lateinit var todoKeywordsDao: com.rrimal.notetaker.data.local.TodoKeywordsDao
    private lateinit var localFileManager: LocalFileManager
    private lateinit var agendaConfigManager: AgendaConfigManager
    private lateinit var orgParser: OrgParser
    private lateinit var timestampParser: OrgTimestampParser
    private lateinit var orgWriter: OrgWriter
    private lateinit var togglRepository: TogglRepository
    private lateinit var repository: AgendaRepository

    @BeforeEach
    fun setup() {
        database = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        timestampDao = mockk(relaxed = true)
        planningDao = mockk(relaxed = true)
        fileMetadataDao = mockk(relaxed = true)
        todoKeywordsDao = mockk(relaxed = true)
        localFileManager = mockk(relaxed = true)
        agendaConfigManager = mockk(relaxed = true)
        orgParser = mockk(relaxed = true)
        timestampParser = OrgTimestampParser()
        orgWriter = mockk(relaxed = true)
        togglRepository = mockk(relaxed = true)
        
        // Mock Toggl as disabled by default (won't interfere with tests)
        coEvery { togglRepository.isEnabled() } returns false

        every { database.noteDao() } returns noteDao
        every { database.orgTimestampDao() } returns timestampDao
        every { database.notePlanningDao() } returns planningDao
        every { database.fileMetadataDao() } returns fileMetadataDao
        every { database.todoKeywordsDao() } returns todoKeywordsDao

        repository = AgendaRepository(
            database,
            localFileManager,
            agendaConfigManager,
            orgParser,
            timestampParser,
            orgWriter,
            togglRepository
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `sync uses configured agenda files not hardcoded path`() = runTest {
        // GIVEN: User configures multiple agenda files
        val configuredFiles = listOf(
            "inbox.org",
            "Brain/tasks.org",
            "Work/projects.org"
        )

        every { agendaConfigManager.agendaFiles } returns flowOf(configuredFiles)
        coEvery { fileMetadataDao.getByFilename(any()) } returns null
        coEvery { localFileManager.readFile(any()) } returns Result.success("* TODO Test")
        every { orgParser.parse(any()) } returns mockk(relaxed = true)
        coEvery { noteDao.deleteByFilename(any()) } just Runs
        coEvery { timestampDao.deleteByFilename(any()) } just Runs
        coEvery { fileMetadataDao.insert(any()) } just Runs

        // WHEN: Sync all files
        repository.syncAllFiles()

        // THEN: All configured files are read
        coVerify(exactly = 1) { localFileManager.readFile("inbox.org") }
        coVerify(exactly = 1) { localFileManager.readFile("Brain/tasks.org") }
        coVerify(exactly = 1) { localFileManager.readFile("Work/projects.org") }

        // AND: Hardcoded path is NOT read
        coVerify(exactly = 0) { localFileManager.readFile("phone_inbox/agenda.org") }
    }

    @Test
    fun `sync skips files with matching hash`() = runTest {
        // GIVEN: File exists with hash that matches current content
        val filename = "inbox.org"
        val content = "* TODO Test task"
        val hash = "matching-hash"

        every { agendaConfigManager.agendaFiles } returns flowOf(listOf(filename))
        coEvery { fileMetadataDao.getByFilename(filename) } returns FileMetadataEntity(
            filename = filename,
            lastSynced = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            hash = hash
        )
        coEvery { localFileManager.readFile(filename) } returns Result.success(content)

        // Mock calculateHash to return same hash
        // (In real implementation, it would calculate SHA-256)

        // WHEN: Sync with force=false
        repository.syncFileToDatabase(filename, force = false)

        // THEN: File is read for hash check
        coVerify(exactly = 1) { localFileManager.readFile(filename) }

        // AND: orgParser is NOT called (because hash matches)
        verify(exactly = 0) { orgParser.parse(any()) }
    }

    @Test
    fun `sync removes database entries for unconfigured files`() = runTest {
        // GIVEN: Database has entries for files no longer in config
        val currentFiles = listOf("inbox.org")
        val staleFiles = listOf(
            FileMetadataEntity("old-file1.org", 0, 0, "hash1"),
            FileMetadataEntity("old-file2.org", 0, 0, "hash2"),
            FileMetadataEntity("inbox.org", 0, 0, "hash3")
        )

        every { agendaConfigManager.agendaFiles } returns flowOf(currentFiles)
        coEvery { fileMetadataDao.getAll() } returns staleFiles
        coEvery { fileMetadataDao.getByFilename("inbox.org") } returns null
        coEvery { localFileManager.readFile("inbox.org") } returns Result.success("* TODO Test")
        every { orgParser.parse(any()) } returns mockk(relaxed = true)

        coEvery { timestampDao.deleteByFilename(any()) } just Runs
        coEvery { noteDao.deleteByFilename(any()) } just Runs
        coEvery { fileMetadataDao.deleteByFilename(any()) } just Runs
        coEvery { fileMetadataDao.insert(any()) } just Runs

        // WHEN: Sync all files
        repository.syncAllFiles()

        // THEN: Stale entries are removed
        coVerify(exactly = 1) { timestampDao.deleteByFilename("old-file1.org") }
        coVerify(exactly = 1) { noteDao.deleteByFilename("old-file1.org") }
        coVerify(exactly = 1) { fileMetadataDao.deleteByFilename("old-file1.org") }

        coVerify(exactly = 1) { timestampDao.deleteByFilename("old-file2.org") }
        coVerify(exactly = 1) { noteDao.deleteByFilename("old-file2.org") }
        coVerify(exactly = 1) { fileMetadataDao.deleteByFilename("old-file2.org") }

        // AND: Current file is NOT deleted
        coVerify(exactly = 0) { fileMetadataDao.deleteByFilename("inbox.org") }
    }

    @Test
    fun `empty configuration does not crash sync`() = runTest {
        // GIVEN: User has not configured any agenda files (empty list)
        every { agendaConfigManager.agendaFiles } returns flowOf(emptyList())
        coEvery { fileMetadataDao.getAll() } returns emptyList()

        // WHEN: Sync all files
        repository.syncAllFiles()

        // THEN: No files are read
        coVerify(exactly = 0) { localFileManager.readFile(any()) }

        // AND: No crash occurs (test passes)
    }

    @Test
    fun `subdirectory paths are handled correctly`() = runTest {
        // GIVEN: User configures file with subdirectory
        val filename = "Brain/personal/habits.org"
        every { agendaConfigManager.agendaFiles } returns flowOf(listOf(filename))
        coEvery { fileMetadataDao.getByFilename(filename) } returns null
        coEvery { localFileManager.readFile(filename) } returns Result.success("* TODO Test")
        every { orgParser.parse(any()) } returns mockk(relaxed = true)

        coEvery { noteDao.deleteByFilename(any()) } just Runs
        coEvery { timestampDao.deleteByFilename(any()) } just Runs
        coEvery { fileMetadataDao.insert(any()) } just Runs

        // WHEN: Sync file
        repository.syncFileToDatabase(filename)

        // THEN: File is read with full path including subdirectories
        coVerify(exactly = 1) { localFileManager.readFile("Brain/personal/habits.org") }
    }

    @Test
    fun `file sync handles read errors gracefully`() = runTest {
        // GIVEN: File read fails (file doesn't exist or permission denied)
        val filename = "missing.org"
        every { agendaConfigManager.agendaFiles } returns flowOf(listOf(filename))
        coEvery { localFileManager.readFile(filename) } returns Result.failure(Exception("File not found"))

        // WHEN: Sync file
        repository.syncFileToDatabase(filename)

        // THEN: orgParser is NOT called
        verify(exactly = 0) { orgParser.parse(any()) }

        // AND: No database changes are made
        coVerify(exactly = 0) { noteDao.insert(any()) }

        // AND: No crash occurs (error is logged and handled)
    }
}
