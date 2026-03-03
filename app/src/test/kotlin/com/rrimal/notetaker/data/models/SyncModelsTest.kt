package com.rrimal.notetaker.data.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

/**
 * Unit tests for JSON sync models
 * Tests JSON generation, filename generation, and validation
 */
@DisplayName("SyncModels")
class SyncModelsTest {

    @Nested
    @DisplayName("buildStateChangeJson()")
    inner class BuildStateChangeJson {

        @Test
        @DisplayName("should generate valid JSON with all fields")
        fun testValidJson() {
            val originId = "56C3785A-5616-424F-8A3E-99F3C2474332"
            val newState = "DONE"
            val timestamp = 1709467200000L // 2024-03-03T12:00:00Z
            
            val json = buildStateChangeJson(originId, newState, timestamp)
            
            // Verify JSON contains expected fields
            assertTrue(json.contains("\"id\": \"$originId\""))
            assertTrue(json.contains("\"status\": \"$newState\""))
            assertTrue(json.contains("\"changed_at\":"))
            
            // Verify JSON is valid (no trailing commas, proper braces)
            assertTrue(json.trim().startsWith("{"))
            assertTrue(json.trim().endsWith("}"))
        }

        @Test
        @DisplayName("should handle different TODO states")
        fun testDifferentStates() {
            val originId = "TEST-ID"
            val timestamp = System.currentTimeMillis()
            
            val states = listOf("TODO", "DONE", "IN-PROGRESS", "WAITING", "CANCELLED")
            
            for (state in states) {
                val json = buildStateChangeJson(originId, state, timestamp)
                assertTrue(json.contains("\"status\": \"$state\""))
            }
        }

        @Test
        @DisplayName("should generate ISO 8601 timestamp with timezone")
        fun testTimestampFormat() {
            val originId = "TEST-ID"
            val newState = "DONE"
            val timestamp = 1709467200000L
            
            val json = buildStateChangeJson(originId, newState, timestamp)
            
            // Verify ISO 8601 format with timezone (e.g., "2024-03-03T12:00:00-05:00")
            // Pattern: YYYY-MM-DDTHH:MM:SS±HH:MM
            val iso8601Pattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}".toRegex()
            assertTrue(iso8601Pattern.containsMatchIn(json), "JSON should contain ISO 8601 timestamp")
        }

        @Test
        @DisplayName("should handle UUIDs with and without hyphens")
        fun testUuidFormats() {
            val uuidWithHyphens = "56C3785A-5616-424F-8A3E-99F3C2474332"
            val uuidWithoutHyphens = "56C3785A56164424F8A3E99F3C2474332"
            val timestamp = System.currentTimeMillis()
            
            val json1 = buildStateChangeJson(uuidWithHyphens, "DONE", timestamp)
            val json2 = buildStateChangeJson(uuidWithoutHyphens, "DONE", timestamp)
            
            assertTrue(json1.contains("\"id\": \"$uuidWithHyphens\""))
            assertTrue(json2.contains("\"id\": \"$uuidWithoutHyphens\""))
        }
    }

    @Nested
    @DisplayName("generateSyncFilename()")
    inner class GenerateSyncFilename {

        @Test
        @DisplayName("should generate filename in correct format")
        fun testFilenameFormat() {
            val originId = "56C3785A-5616-424F-8A3E-99F3C2474332"
            val newState = "DONE"
            val timestamp = 1709467200000L
            
            val filename = generateSyncFilename(originId, newState, timestamp)
            
            assertEquals("56C3785A-5616-424F-8A3E-99F3C2474332_DONE_1709467200000.json", filename)
        }

        @Test
        @DisplayName("should handle different states")
        fun testDifferentStates() {
            val originId = "TEST-ID"
            val timestamp = 1709467200000L
            
            val states = listOf("TODO", "DONE", "IN-PROGRESS", "WAITING")
            
            for (state in states) {
                val filename = generateSyncFilename(originId, state, timestamp)
                assertTrue(filename.startsWith("TEST-ID_"))
                assertTrue(filename.contains("_${state}_"))
                assertTrue(filename.endsWith(".json"))
            }
        }

        @Test
        @DisplayName("should handle hyphenated states")
        fun testHyphenatedStates() {
            val originId = "TEST-ID"
            val timestamp = 1709467200000L
            
            val filename = generateSyncFilename(originId, "IN-PROGRESS", timestamp)
            
            assertEquals("TEST-ID_IN-PROGRESS_1709467200000.json", filename)
        }

        @Test
        @DisplayName("should use exact timestamp")
        fun testTimestampPreservation() {
            val originId = "TEST-ID"
            val timestamp = 1234567890123L
            
            val filename = generateSyncFilename(originId, "DONE", timestamp)
            
            assertTrue(filename.contains("_1234567890123.json"))
        }

        @Test
        @DisplayName("should generate unique filenames for same ID with different timestamps")
        fun testUniqueFilenames() {
            val originId = "TEST-ID"
            val newState = "DONE"
            
            val filename1 = generateSyncFilename(originId, newState, 1000L)
            val filename2 = generateSyncFilename(originId, newState, 2000L)
            
            assertNotEquals(filename1, filename2)
            assertEquals("TEST-ID_DONE_1000.json", filename1)
            assertEquals("TEST-ID_DONE_2000.json", filename2)
        }
    }

    @Nested
    @DisplayName("isValidSyncFilename()")
    inner class IsValidSyncFilename {

        @Test
        @DisplayName("should accept valid UUID-based filename")
        fun testValidUuidFilename() {
            val filename = "56C3785A-5616-424F-8A3E-99F3C2474332_DONE_1709467200000.json"
            assertTrue(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should accept UUID without hyphens")
        fun testUuidWithoutHyphens() {
            val filename = "56C3785A56164424F8A3E99F3C2474332_DONE_1709467200000.json"
            assertTrue(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should accept hyphenated states")
        fun testHyphenatedStates() {
            val validFilenames = listOf(
                "TEST-ID_IN-PROGRESS_1709467200000.json",
                "TEST-ID_NOT-STARTED_1709467200000.json",
                "ANOTHER-UUID_TODO_1234567890123.json"
            )
            
            validFilenames.forEach { filename ->
                assertTrue(isValidSyncFilename(filename), "Should accept: $filename")
            }
        }

        @Test
        @DisplayName("should reject filename without .json extension")
        fun testMissingExtension() {
            val filename = "56C3785A-5616-424F-8A3E-99F3C2474332_DONE_1709467200000"
            assertFalse(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should reject filename with lowercase characters")
        fun testLowercaseRejected() {
            val filename = "56c3785a-5616-424f-8a3e-99f3c2474332_done_1709467200000.json"
            assertFalse(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should reject filename without underscores")
        fun testMissingUnderscores() {
            val invalidFilenames = listOf(
                "56C3785A-5616-424F-8A3E-99F3C2474332DONE1709467200000.json",
                "56C3785A-5616-424F-8A3E-99F3C2474332_DONE1709467200000.json",
                "56C3785A-5616-424F-8A3E-99F3C2474332DONE_1709467200000.json"
            )
            
            invalidFilenames.forEach { filename ->
                assertFalse(isValidSyncFilename(filename), "Should reject: $filename")
            }
        }

        @Test
        @DisplayName("should reject filename with non-numeric timestamp")
        fun testNonNumericTimestamp() {
            val filename = "TEST-ID_DONE_abc123.json"
            assertFalse(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should reject filename with special characters in state")
        fun testSpecialCharactersInState() {
            val filename = "TEST-ID_DONE!_1709467200000.json"
            assertFalse(isValidSyncFilename(filename))
        }

        @Test
        @DisplayName("should reject empty filename")
        fun testEmptyFilename() {
            assertFalse(isValidSyncFilename(""))
        }

        @Test
        @DisplayName("should reject filename with path separators")
        fun testPathSeparators() {
            val invalidFilenames = listOf(
                "sync/TEST-ID_DONE_1709467200000.json",
                "../TEST-ID_DONE_1709467200000.json",
                "TEST-ID_DONE_1709467200000.json/"
            )
            
            invalidFilenames.forEach { filename ->
                assertFalse(isValidSyncFilename(filename), "Should reject: $filename")
            }
        }
    }

    @Nested
    @DisplayName("StateChangeSyncMessage")
    inner class StateChangeSyncMessageTest {

        @Test
        @DisplayName("should create data class instance")
        fun testDataClassCreation() {
            val message = StateChangeSyncMessage(
                originId = "TEST-ID",
                newState = "DONE",
                timestamp = 1709467200000L
            )
            
            assertEquals("TEST-ID", message.originId)
            assertEquals("DONE", message.newState)
            assertEquals(1709467200000L, message.timestamp)
        }

        @Test
        @DisplayName("should support data class copy")
        fun testDataClassCopy() {
            val original = StateChangeSyncMessage(
                originId = "TEST-ID",
                newState = "TODO",
                timestamp = 1000L
            )
            
            val copied = original.copy(newState = "DONE", timestamp = 2000L)
            
            assertEquals("TEST-ID", copied.originId) // Unchanged
            assertEquals("DONE", copied.newState)    // Changed
            assertEquals(2000L, copied.timestamp)     // Changed
        }

        @Test
        @DisplayName("should support data class equality")
        fun testDataClassEquality() {
            val message1 = StateChangeSyncMessage("ID1", "DONE", 1000L)
            val message2 = StateChangeSyncMessage("ID1", "DONE", 1000L)
            val message3 = StateChangeSyncMessage("ID1", "TODO", 1000L)
            
            assertEquals(message1, message2)
            assertNotEquals(message1, message3)
        }
    }

    @Nested
    @DisplayName("Integration: Full workflow")
    inner class IntegrationTests {

        @Test
        @DisplayName("should generate valid filename and JSON together")
        fun testFullWorkflow() {
            val originId = "56C3785A-5616-424F-8A3E-99F3C2474332"
            val newState = "DONE"
            val timestamp = System.currentTimeMillis()
            
            // Generate filename
            val filename = generateSyncFilename(originId, newState, timestamp)
            
            // Validate filename
            assertTrue(isValidSyncFilename(filename))
            
            // Generate JSON
            val json = buildStateChangeJson(originId, newState, timestamp)
            
            // Verify JSON contains correct data
            assertTrue(json.contains("\"id\": \"$originId\""))
            assertTrue(json.contains("\"status\": \"$newState\""))
        }

        @Test
        @DisplayName("should handle multiple state changes for same ID")
        fun testMultipleStateChanges() {
            val originId = "TEST-ID"
            val states = listOf("TODO", "IN-PROGRESS", "DONE")
            val baseTimestamp = 1709467200000L
            
            val files = states.mapIndexed { index, state ->
                val timestamp = baseTimestamp + (index * 1000L)
                generateSyncFilename(originId, state, timestamp)
            }
            
            // All filenames should be unique
            assertEquals(3, files.toSet().size)
            
            // All filenames should be valid
            files.forEach { filename ->
                assertTrue(isValidSyncFilename(filename))
            }
        }
    }
}
