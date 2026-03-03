package com.rrimal.notetaker.data.orgmode

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for OrgParser
 * Tests parsing of org-mode headlines, properties, tags, planning lines, and nested structures
 */
@DisplayName("OrgParser")
class OrgParserTest {

    private val parser = OrgParser()

    @Nested
    @DisplayName("Empty and Blank Content")
    inner class EmptyContent {

        @Test
        @DisplayName("should return empty OrgFile for blank content")
        fun testBlankContent() {
            val result = parser.parse("")
            assertEquals("", result.preamble)
            assertTrue(result.headlines.isEmpty())
        }

        @Test
        @DisplayName("should return empty OrgFile for whitespace-only content")
        fun testWhitespaceOnly() {
            val result = parser.parse("   \n\n  \t  ")
            assertEquals("", result.preamble)
            assertTrue(result.headlines.isEmpty())
        }
    }

    @Nested
    @DisplayName("Simple Headlines")
    inner class SimpleHeadlines {

        @Test
        @DisplayName("should parse single level-1 headline without TODO state")
        fun testSimpleLevel1Headline() {
            val content = "* Meeting Notes"
            val result = parser.parse(content)

            assertEquals(1, result.headlines.size)
            val headline = result.headlines[0]
            assertEquals(1, headline.level)
            assertEquals("Meeting Notes", headline.title)
            assertNull(headline.todoState)
            assertNull(headline.priority)
            assertTrue(headline.tags.isEmpty())
        }

        @Test
        @DisplayName("should parse multiple level-1 headlines")
        fun testMultipleLevel1Headlines() {
            val content = """
                * First Headline
                * Second Headline
                * Third Headline
            """.trimIndent()
            val result = parser.parse(content)

            assertEquals(3, result.headlines.size)
            assertEquals("First Headline", result.headlines[0].title)
            assertEquals("Second Headline", result.headlines[1].title)
            assertEquals("Third Headline", result.headlines[2].title)
        }

        @Test
        @DisplayName("should parse different headline levels")
        fun testDifferentLevels() {
            val content = """
                * Level 1
                ** Level 2
                *** Level 3
                **** Level 4
            """.trimIndent()
            val result = parser.parse(content)

            // Level 2, 3, 4 should be nested under Level 1
            assertEquals(1, result.headlines.size)
            val level1 = result.headlines[0]
            assertEquals(1, level1.level)
            assertEquals("Level 1", level1.title)

            assertEquals(1, level1.children.size)
            val level2 = level1.children[0]
            assertEquals(2, level2.level)
            assertEquals("Level 2", level2.title)

            assertEquals(1, level2.children.size)
            val level3 = level2.children[0]
            assertEquals(3, level3.level)
            assertEquals("Level 3", level3.title)

            assertEquals(1, level3.children.size)
            val level4 = level3.children[0]
            assertEquals(4, level4.level)
            assertEquals("Level 4", level4.title)
        }
    }

    @Nested
    @DisplayName("TODO States")
    inner class TodoStates {

        @Test
        @DisplayName("should parse TODO state")
        fun testTodoState() {
            val content = "* TODO Buy groceries"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("TODO", headline.todoState)
            assertEquals("Buy groceries", headline.title)
        }

        @Test
        @DisplayName("should parse DONE state")
        fun testDoneState() {
            val content = "* DONE Complete project report"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("DONE", headline.todoState)
            assertEquals("Complete project report", headline.title)
        }

        @Test
        @DisplayName("should parse IN-PROGRESS state")
        fun testInProgressState() {
            val content = "* IN-PROGRESS Review pull request"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("IN-PROGRESS", headline.todoState)
            assertEquals("Review pull request", headline.title)
        }

        @Test
        @DisplayName("should parse WAITING state")
        fun testWaitingState() {
            val content = "* WAITING Approval from manager"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("WAITING", headline.todoState)
            assertEquals("Approval from manager", headline.title)
        }

        @Test
        @DisplayName("should parse CANCELLED state")
        fun testCancelledState() {
            val content = "* CANCELLED Old task no longer needed"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("CANCELLED", headline.todoState)
            assertEquals("Old task no longer needed", headline.title)
        }
    }

    @Nested
    @DisplayName("Priorities")
    inner class Priorities {

        @Test
        @DisplayName("should parse priority [#A]")
        fun testPriorityA() {
            val content = "* TODO [#A] Critical bug fix"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("TODO", headline.todoState)
            assertEquals("A", headline.priority)
            assertEquals("Critical bug fix", headline.title)
        }

        @Test
        @DisplayName("should parse priority [#B]")
        fun testPriorityB() {
            val content = "* TODO [#B] Important feature"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("B", headline.priority)
            assertEquals("Important feature", headline.title)
        }

        @Test
        @DisplayName("should parse priority [#C]")
        fun testPriorityC() {
            val content = "* TODO [#C] Low priority task"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("C", headline.priority)
            assertEquals("Low priority task", headline.title)
        }

        @Test
        @DisplayName("should parse priority without TODO state")
        fun testPriorityWithoutTodo() {
            val content = "* [#A] Important meeting"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertNull(headline.todoState)
            assertEquals("A", headline.priority)
            assertEquals("Important meeting", headline.title)
        }
    }

    @Nested
    @DisplayName("Tags")
    inner class Tags {

        @Test
        @DisplayName("should parse single tag")
        fun testSingleTag() {
            val content = "* Meeting Notes :work:"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals(listOf("work"), headline.tags)
        }

        @Test
        @DisplayName("should parse multiple tags")
        fun testMultipleTags() {
            val content = "* Project Update :work:urgent:meeting:"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals(listOf("work", "urgent", "meeting"), headline.tags)
        }

        @Test
        @DisplayName("should parse tags with TODO state and priority")
        fun testTagsWithTodoAndPriority() {
            val content = "* TODO [#A] Fix critical bug :dev:urgent:"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("TODO", headline.todoState)
            assertEquals("A", headline.priority)
            assertEquals("Fix critical bug", headline.title)
            assertEquals(listOf("dev", "urgent"), headline.tags)
        }
    }

    @Nested
    @DisplayName("Planning Lines")
    inner class PlanningLines {

        @Test
        @DisplayName("should parse SCHEDULED line")
        fun testScheduled() {
            val content = """
                * TODO Review code
                SCHEDULED: <2026-03-01 Sat>
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("<2026-03-01 Sat>", headline.scheduled)
        }

        @Test
        @DisplayName("should parse DEADLINE line")
        fun testDeadline() {
            val content = """
                * TODO Submit report
                DEADLINE: <2026-03-05 Wed>
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("<2026-03-05 Wed>", headline.deadline)
        }

        @Test
        @DisplayName("should parse CLOSED line")
        fun testClosed() {
            val content = """
                * DONE Completed task
                CLOSED: [2026-02-28 Fri 14:30]
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("[2026-02-28 Fri 14:30]", headline.closed)
        }

        @Test
        @DisplayName("should parse multiple planning lines together")
        fun testMultiplePlanningLines() {
            val content = """
                * TODO Important task
                SCHEDULED: <2026-03-01 Sat>
                DEADLINE: <2026-03-05 Wed>
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("<2026-03-01 Sat>", headline.scheduled)
            assertEquals("<2026-03-05 Wed>", headline.deadline)
        }

        @Test
        @DisplayName("should parse CLOSED with SCHEDULED and DEADLINE")
        fun testClosedWithScheduledAndDeadline() {
            val content = """
                * DONE Finished project
                CLOSED: [2026-03-01 Sat 10:00] SCHEDULED: <2026-02-25 Tue> DEADLINE: <2026-03-01 Sat>
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("[2026-03-01 Sat 10:00] SCHEDULED: <2026-02-25 Tue> DEADLINE: <2026-03-01 Sat>", headline.closed)
        }
    }

    @Nested
    @DisplayName("Properties Drawer")
    inner class PropertiesDrawer {

        @Test
        @DisplayName("should parse empty properties drawer")
        fun testEmptyPropertiesDrawer() {
            val content = """
                * Task
                :PROPERTIES:
                :END:
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertTrue(headline.properties.isEmpty())
        }

        @Test
        @DisplayName("should parse single property")
        fun testSingleProperty() {
            val content = """
                * Task
                :PROPERTIES:
                :CREATED: [2026-03-01 Sat 09:00]
                :END:
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("[2026-03-01 Sat 09:00]", headline.properties["CREATED"])
        }

        @Test
        @DisplayName("should parse multiple properties")
        fun testMultipleProperties() {
            val content = """
                * Project Task
                :PROPERTIES:
                :CREATED: [2026-03-01 Sat 09:00]
                :ID: task-123
                :CATEGORY: Work
                :EFFORT: 2h
                :END:
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals(4, headline.properties.size)
            assertEquals("[2026-03-01 Sat 09:00]", headline.properties["CREATED"])
            assertEquals("task-123", headline.properties["ID"])
            assertEquals("Work", headline.properties["CATEGORY"])
            assertEquals("2h", headline.properties["EFFORT"])
        }

        @Test
        @DisplayName("should parse properties with SCHEDULED line")
        fun testPropertiesWithScheduled() {
            val content = """
                * TODO Task with properties
                SCHEDULED: <2026-03-01 Sat>
                :PROPERTIES:
                :CREATED: [2026-02-28 Fri 14:00]
                :END:
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("<2026-03-01 Sat>", headline.scheduled)
            assertEquals("[2026-02-28 Fri 14:00]", headline.properties["CREATED"])
        }
    }

    @Nested
    @DisplayName("Body Content")
    inner class BodyContent {

        @Test
        @DisplayName("should parse headline with body text")
        fun testSimpleBody() {
            val content = """
                * Meeting Notes
                Discussed project timeline and deliverables.
                Action items assigned to team members.
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("Discussed project timeline and deliverables.\nAction items assigned to team members.", headline.body)
        }

        @Test
        @DisplayName("should parse body with blank lines")
        fun testBodyWithBlankLines() {
            val content = """
                * Notes
                First paragraph.
                
                Second paragraph after blank line.
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertTrue(headline.body.contains("First paragraph."))
            assertTrue(headline.body.contains("Second paragraph after blank line."))
        }

        @Test
        @DisplayName("should parse body after properties drawer")
        fun testBodyAfterProperties() {
            val content = """
                * Task
                :PROPERTIES:
                :CREATED: [2026-03-01 Sat]
                :END:
                This is the body content after properties.
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("This is the body content after properties.", headline.body)
        }
    }

    @Nested
    @DisplayName("Nested Headlines")
    inner class NestedHeadlines {

        @Test
        @DisplayName("should parse direct children (level 2 under level 1)")
        fun testDirectChildren() {
            val content = """
                * Parent
                ** Child 1
                ** Child 2
            """.trimIndent()
            val result = parser.parse(content)

            val parent = result.headlines[0]
            assertEquals(2, parent.children.size)
            assertEquals("Child 1", parent.children[0].title)
            assertEquals("Child 2", parent.children[1].title)
        }

        @Test
        @DisplayName("should parse grandchildren (level 3 under level 2)")
        fun testGrandchildren() {
            val content = """
                * Grandparent
                ** Parent
                *** Child 1
                *** Child 2
            """.trimIndent()
            val result = parser.parse(content)

            val grandparent = result.headlines[0]
            val parent = grandparent.children[0]
            assertEquals(2, parent.children.size)
            assertEquals("Child 1", parent.children[0].title)
            assertEquals("Child 2", parent.children[1].title)
        }

        @Test
        @DisplayName("should separate siblings at same level")
        fun testSiblings() {
            val content = """
                * First Parent
                ** Child of First
                * Second Parent
                ** Child of Second
            """.trimIndent()
            val result = parser.parse(content)

            assertEquals(2, result.headlines.size)
            assertEquals("First Parent", result.headlines[0].title)
            assertEquals(1, result.headlines[0].children.size)
            assertEquals("Child of First", result.headlines[0].children[0].title)

            assertEquals("Second Parent", result.headlines[1].title)
            assertEquals(1, result.headlines[1].children.size)
            assertEquals("Child of Second", result.headlines[1].children[0].title)
        }

        @Test
        @DisplayName("should handle complex nested structure")
        fun testComplexNesting() {
            val content = """
                * Level 1 A
                ** Level 2 A1
                *** Level 3 A1a
                *** Level 3 A1b
                ** Level 2 A2
                * Level 1 B
                ** Level 2 B1
            """.trimIndent()
            val result = parser.parse(content)

            assertEquals(2, result.headlines.size)
            
            val level1A = result.headlines[0]
            assertEquals("Level 1 A", level1A.title)
            assertEquals(2, level1A.children.size)
            
            val level2A1 = level1A.children[0]
            assertEquals("Level 2 A1", level2A1.title)
            assertEquals(2, level2A1.children.size)
            assertEquals("Level 3 A1a", level2A1.children[0].title)
            assertEquals("Level 3 A1b", level2A1.children[1].title)
            
            val level2A2 = level1A.children[1]
            assertEquals("Level 2 A2", level2A2.title)
            
            val level1B = result.headlines[1]
            assertEquals("Level 1 B", level1B.title)
            assertEquals(1, level1B.children.size)
            assertEquals("Level 2 B1", level1B.children[0].title)
        }
    }

    @Nested
    @DisplayName("Preamble Content")
    inner class PreambleContent {

        @Test
        @DisplayName("should parse preamble before first headline")
        fun testPreambleBeforeHeadline() {
            val content = """
                This is preamble content.
                It appears before any headlines.
                
                * First Headline
            """.trimIndent()
            val result = parser.parse(content)

            assertTrue(result.preamble.contains("This is preamble content."))
            assertTrue(result.preamble.contains("It appears before any headlines."))
            assertEquals(1, result.headlines.size)
        }

        @Test
        @DisplayName("should have empty preamble when content starts with headline")
        fun testNoPreamble() {
            val content = "* First Headline"
            val result = parser.parse(content)

            assertEquals("", result.preamble)
        }
    }

    @Nested
    @DisplayName("Edge Cases and Complex Scenarios")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle headline with all features combined")
        fun testFullyFeaturedHeadline() {
            val content = """
                * TODO [#A] Complex task with everything :work:urgent:
                SCHEDULED: <2026-03-01 Sat>
                DEADLINE: <2026-03-05 Wed>
                :PROPERTIES:
                :CREATED: [2026-02-28 Fri 10:00]
                :ID: task-456
                :END:
                This is the body content.
                ** Subtask 1
                *** Sub-subtask
            """.trimIndent()
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals(1, headline.level)
            assertEquals("TODO", headline.todoState)
            assertEquals("A", headline.priority)
            assertEquals("Complex task with everything", headline.title)
            assertEquals(listOf("work", "urgent"), headline.tags)
            assertEquals("<2026-03-01 Sat>", headline.scheduled)
            assertEquals("<2026-03-05 Wed>", headline.deadline)
            assertEquals("[2026-02-28 Fri 10:00]", headline.properties["CREATED"])
            assertEquals("task-456", headline.properties["ID"])
            assertEquals("This is the body content.", headline.body)
            assertEquals(1, headline.children.size)
            assertEquals("Subtask 1", headline.children[0].title)
            assertEquals(1, headline.children[0].children.size)
            assertEquals("Sub-subtask", headline.children[0].children[0].title)
        }

        @Test
        @DisplayName("should handle headlines with special characters in title")
        fun testSpecialCharactersInTitle() {
            val content = "* TODO Buy milk & eggs (2 dozen) [urgent!]"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertEquals("Buy milk & eggs (2 dozen) [urgent!]", headline.title)
        }

        @Test
        @DisplayName("should handle empty TODO state placeholder")
        fun testNoTodoStateWithPriority() {
            val content = "* [#B] Regular task with priority only"
            val result = parser.parse(content)

            val headline = result.headlines[0]
            assertNull(headline.todoState)
            assertEquals("B", headline.priority)
            assertEquals("Regular task with priority only", headline.title)
        }
    }
}
