package com.rrimal.notetaker.data.orgmode

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for OrgWriter
 * Tests writing org-mode headlines, files, and various operations (append, prepend)
 */
@DisplayName("OrgWriter")
class OrgWriterTest {

    private val writer = OrgWriter()
    private val parser = OrgParser()

    @Nested
    @DisplayName("Headline Writing")
    inner class HeadlineWriting {

        @Test
        @DisplayName("should write simple headline")
        fun testSimpleHeadline() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Meeting Notes"
            )

            val result = writer.writeHeadline(headline)
            assertEquals("* Meeting Notes\n", result)
        }

        @Test
        @DisplayName("should write headline with TODO state")
        fun testHeadlineWithTodo() {
            val headline = OrgNode.Headline(
                level = 1,
                todoState = "TODO",
                title = "Buy groceries"
            )

            val result = writer.writeHeadline(headline)
            assertEquals("* TODO Buy groceries\n", result)
        }

        @Test
        @DisplayName("should write headline with priority")
        fun testHeadlineWithPriority() {
            val headline = OrgNode.Headline(
                level = 1,
                todoState = "TODO",
                priority = "A",
                title = "Critical bug fix"
            )

            val result = writer.writeHeadline(headline)
            assertEquals("* TODO [#A] Critical bug fix\n", result)
        }

        @Test
        @DisplayName("should write headline with tags")
        fun testHeadlineWithTags() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Project Update",
                tags = listOf("work", "urgent")
            )

            val result = writer.writeHeadline(headline)
            assertEquals("* Project Update :work:urgent:\n", result)
        }

        @Test
        @DisplayName("should write headline with all features")
        fun testFullyFeaturedHeadline() {
            val headline = OrgNode.Headline(
                level = 1,
                todoState = "TODO",
                priority = "A",
                title = "Complete task",
                tags = listOf("work", "meeting")
            )

            val result = writer.writeHeadline(headline)
            assertEquals("* TODO [#A] Complete task :work:meeting:\n", result)
        }

        @Test
        @DisplayName("should write headline with different levels")
        fun testDifferentLevels() {
            val level1 = OrgNode.Headline(level = 1, title = "Level 1")
            val level2 = OrgNode.Headline(level = 2, title = "Level 2")
            val level3 = OrgNode.Headline(level = 3, title = "Level 3")

            assertEquals("* Level 1\n", writer.writeHeadline(level1))
            assertEquals("** Level 2\n", writer.writeHeadline(level2))
            assertEquals("*** Level 3\n", writer.writeHeadline(level3))
        }
    }

    @Nested
    @DisplayName("Planning Lines and Properties")
    inner class PlanningAndProperties {

        @Test
        @DisplayName("should write SCHEDULED line")
        fun testScheduledLine() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Task",
                scheduled = "<2026-03-01 Sat>"
            )

            val result = writer.writeHeadline(headline)
            assertTrue(result.contains("SCHEDULED: <2026-03-01 Sat>\n"))
        }

        @Test
        @DisplayName("should write DEADLINE line")
        fun testDeadlineLine() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Task",
                deadline = "<2026-03-05 Wed>"
            )

            val result = writer.writeHeadline(headline)
            assertTrue(result.contains("DEADLINE: <2026-03-05 Wed>\n"))
        }

        @Test
        @DisplayName("should write CLOSED line")
        fun testClosedLine() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Task",
                closed = "[2026-02-28 Fri 14:30]"
            )

            val result = writer.writeHeadline(headline)
            assertTrue(result.contains("CLOSED: [2026-02-28 Fri 14:30]\n"))
        }

        @Test
        @DisplayName("should write all planning lines in correct order")
        fun testAllPlanningLines() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Task",
                closed = "[2026-03-01 Sat 10:00]",
                scheduled = "<2026-02-25 Tue>",
                deadline = "<2026-03-01 Sat>"
            )

            val result = writer.writeHeadline(headline)
            val lines = result.lines()
            
            // CLOSED should come before SCHEDULED and DEADLINE
            assertTrue(lines[1].startsWith("CLOSED:"))
            assertTrue(lines[2].startsWith("SCHEDULED:"))
            assertTrue(lines[3].startsWith("DEADLINE:"))
        }

        @Test
        @DisplayName("should write properties drawer")
        fun testPropertiesDrawer() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Task",
                properties = mapOf(
                    "CREATED" to "[2026-03-01 Sat 09:00]",
                    "ID" to "task-123"
                )
            )

            val result = writer.writeHeadline(headline)
            assertTrue(result.contains(":PROPERTIES:\n"))
            assertTrue(result.contains(":CREATED: [2026-03-01 Sat 09:00]\n"))
            assertTrue(result.contains(":ID: task-123\n"))
            assertTrue(result.contains(":END:\n"))
        }
    }

    @Nested
    @DisplayName("Body Content and Children")
    inner class BodyAndChildren {

        @Test
        @DisplayName("should write headline with body")
        fun testHeadlineWithBody() {
            val headline = OrgNode.Headline(
                level = 1,
                title = "Meeting Notes",
                body = "Discussed project timeline.\nAction items assigned."
            )

            val result = writer.writeHeadline(headline)
            assertTrue(result.contains("Discussed project timeline.\nAction items assigned."))
        }

        @Test
        @DisplayName("should write headline with children")
        fun testHeadlineWithChildren() {
            val child1 = OrgNode.Headline(level = 2, title = "Child 1")
            val child2 = OrgNode.Headline(level = 2, title = "Child 2")
            val parent = OrgNode.Headline(
                level = 1,
                title = "Parent",
                children = listOf(child1, child2)
            )

            val result = writer.writeHeadline(parent)
            assertTrue(result.contains("* Parent\n"))
            assertTrue(result.contains("** Child 1\n"))
            assertTrue(result.contains("** Child 2\n"))
        }

        @Test
        @DisplayName("should write nested children correctly")
        fun testNestedChildren() {
            val grandchild = OrgNode.Headline(level = 3, title = "Grandchild")
            val child = OrgNode.Headline(level = 2, title = "Child", children = listOf(grandchild))
            val parent = OrgNode.Headline(level = 1, title = "Parent", children = listOf(child))

            val result = writer.writeHeadline(parent)
            assertTrue(result.contains("* Parent\n"))
            assertTrue(result.contains("** Child\n"))
            assertTrue(result.contains("*** Grandchild\n"))
        }
    }

    @Nested
    @DisplayName("File Writing")
    inner class FileWriting {

        @Test
        @DisplayName("should write file with single headline")
        fun testFileSingleHeadline() {
            val headline = OrgNode.Headline(level = 1, title = "First Headline")
            val orgFile = OrgFile(headlines = listOf(headline))

            val result = writer.writeFile(orgFile)
            assertEquals("* First Headline\n", result)
        }

        @Test
        @DisplayName("should write file with multiple headlines")
        fun testFileMultipleHeadlines() {
            val headline1 = OrgNode.Headline(level = 1, title = "First")
            val headline2 = OrgNode.Headline(level = 1, title = "Second")
            val orgFile = OrgFile(headlines = listOf(headline1, headline2))

            val result = writer.writeFile(orgFile)
            assertTrue(result.contains("* First\n"))
            assertTrue(result.contains("* Second\n"))
        }

        @Test
        @DisplayName("should write file with preamble")
        fun testFileWithPreamble() {
            val preamble = "This is preamble content.\nBefore any headlines."
            val headline = OrgNode.Headline(level = 1, title = "First Headline")
            val orgFile = OrgFile(preamble = preamble, headlines = listOf(headline))

            val result = writer.writeFile(orgFile)
            assertTrue(result.startsWith("This is preamble content.\nBefore any headlines.\n\n"))
            assertTrue(result.contains("* First Headline\n"))
        }

        @Test
        @DisplayName("should write empty file for empty OrgFile")
        fun testEmptyFile() {
            val orgFile = OrgFile()
            val result = writer.writeFile(orgFile)
            assertEquals("", result)
        }
    }

    @Nested
    @DisplayName("Append Operations")
    inner class AppendOperations {

        @Test
        @DisplayName("should append to blank content")
        fun testAppendToBlank() {
            val newHeadline = OrgNode.Headline(level = 1, title = "New Entry")
            val result = writer.appendEntry("", newHeadline)

            assertEquals("* New Entry\n", result)
        }

        @Test
        @DisplayName("should append at end when no target section")
        fun testAppendAtEnd() {
            val existingContent = "* Existing Headline\n"
            val newHeadline = OrgNode.Headline(level = 1, title = "New Entry")
            val result = writer.appendEntry(existingContent, newHeadline)

            assertTrue(result.startsWith("* Existing Headline\n"))
            assertTrue(result.endsWith("* New Entry\n"))
        }

        @Test
        @DisplayName("should append under target section")
        fun testAppendUnderTargetSection() {
            val existingContent = "* Inbox\n* Projects\n"
            val newHeadline = OrgNode.Headline(level = 1, title = "New Task")
            val result = writer.appendEntry(existingContent, newHeadline, targetSection = "Inbox")

            // Parse result to verify structure
            val parsed = parser.parse(result)
            val inboxSection = parsed.findHeadline("Inbox")
            assertNotNull(inboxSection)
            assertEquals(1, inboxSection?.children?.size)
            assertEquals("New Task", inboxSection?.children?.get(0)?.title)
            assertEquals(2, inboxSection?.children?.get(0)?.level) // Should be level 2 (child of level 1)
        }

        @Test
        @DisplayName("should append at end when target section not found")
        fun testAppendWhenTargetNotFound() {
            val existingContent = "* Existing\n"
            val newHeadline = OrgNode.Headline(level = 1, title = "New")
            val result = writer.appendEntry(existingContent, newHeadline, targetSection = "NonExistent")

            assertTrue(result.contains("* Existing\n"))
            assertTrue(result.endsWith("* New\n"))
        }
    }

    @Nested
    @DisplayName("Prepend Operations")
    inner class PrependOperations {

        @Test
        @DisplayName("should prepend to blank content")
        fun testPrependToBlank() {
            val newHeadline = OrgNode.Headline(level = 1, title = "New Entry")
            val result = writer.prependEntry("", newHeadline)

            assertEquals("* New Entry\n", result)
        }

        @Test
        @DisplayName("should prepend at beginning")
        fun testPrependAtBeginning() {
            val existingContent = "* Existing Headline\n"
            val newHeadline = OrgNode.Headline(level = 1, title = "New Entry")
            val result = writer.prependEntry(existingContent, newHeadline)

            assertTrue(result.startsWith("* New Entry\n"))
            assertTrue(result.contains("* Existing Headline\n"))
        }
    }

    @Nested
    @DisplayName("Create Headline Helper")
    inner class CreateHeadline {

        @Test
        @DisplayName("should create headline from single line text")
        fun testSingleLineText() {
            val headline = writer.createHeadline("Meeting with team")

            assertEquals("Meeting with team", headline.title)
            assertEquals("", headline.body)
        }

        @Test
        @DisplayName("should create headline from multi-line text")
        fun testMultiLineText() {
            val text = "Meeting Notes\nDiscussed project timeline.\nAction items assigned."
            val headline = writer.createHeadline(text)

            assertEquals("Meeting Notes", headline.title)
            assertEquals("Discussed project timeline.\nAction items assigned.", headline.body)
        }

        @Test
        @DisplayName("should respect custom level")
        fun testCustomLevel() {
            val headline = writer.createHeadline("Task", level = 3)
            assertEquals(3, headline.level)
        }

        @Test
        @DisplayName("should include TODO state")
        fun testWithTodoState() {
            val headline = writer.createHeadline("Buy milk", todoState = "TODO")
            assertEquals("TODO", headline.todoState)
        }

        @Test
        @DisplayName("should include tags")
        fun testWithTags() {
            val headline = writer.createHeadline("Project meeting", tags = listOf("work", "urgent"))
            assertEquals(listOf("work", "urgent"), headline.tags)
        }

        @Test
        @DisplayName("should include priority")
        fun testWithPriority() {
            val headline = writer.createHeadline("Critical fix", priority = "A")
            assertEquals("A", headline.priority)
        }

        @Test
        @DisplayName("should include properties")
        fun testWithProperties() {
            val props = mapOf("ID" to "task-123")
            val headline = writer.createHeadline("Task", properties = props)
            assertEquals("task-123", headline.properties["ID"])
        }

        @Test
        @DisplayName("should limit title length to 100 characters")
        fun testTitleLengthLimit() {
            val longText = "a".repeat(150)
            val headline = writer.createHeadline(longText)
            assertEquals(100, headline.title.length)
        }

        @Test
        @DisplayName("should handle blank text gracefully")
        fun testBlankText() {
            val headline = writer.createHeadline("")
            assertEquals("", headline.title)  // Empty string returns empty title, not "Untitled"
            assertEquals("", headline.body)
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    inner class RoundTripTests {

        @Test
        @DisplayName("should preserve simple headline through parse-write cycle")
        fun testSimpleRoundTrip() {
            val original = "* TODO Task :work:\n"
            val parsed = parser.parse(original)
            val written = writer.writeFile(parsed)

            assertEquals(original, written)
        }

        @Test
        @DisplayName("should preserve complex headline through parse-write cycle")
        fun testComplexRoundTrip() {
            val original = """
                * TODO [#A] Complex task :work:urgent:
                SCHEDULED: <2026-03-01 Sat>
                :PROPERTIES:
                :CREATED: [2026-02-28 Fri]
                :END:
                Body content here.
            """.trimIndent() + "\n"

            val parsed = parser.parse(original)
            val written = writer.writeFile(parsed)

            // Parse both to compare structure (string comparison may differ in whitespace)
            val parsedOriginal = parser.parse(original)
            val parsedWritten = parser.parse(written)

            assertEquals(parsedOriginal.headlines.size, parsedWritten.headlines.size)
            val h1 = parsedOriginal.headlines[0]
            val h2 = parsedWritten.headlines[0]
            assertEquals(h1.title, h2.title)
            assertEquals(h1.todoState, h2.todoState)
            assertEquals(h1.priority, h2.priority)
            assertEquals(h1.tags, h2.tags)
            assertEquals(h1.scheduled, h2.scheduled)
            assertEquals(h1.properties, h2.properties)
            assertEquals(h1.body, h2.body)
        }
    }
}
