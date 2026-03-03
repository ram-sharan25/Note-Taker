package com.rrimal.notetaker.data.orgmode

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import java.util.*

/**
 * Comprehensive unit tests for OrgTimestampParser
 * Tests parsing of org-mode timestamps with various formats, repeaters, and time components
 */
@DisplayName("OrgTimestampParser")
class OrgTimestampParserTest {

    private val parser = OrgTimestampParser()

    @Nested
    @DisplayName("Basic Timestamp Parsing")
    inner class BasicTimestamps {

        @Test
        @DisplayName("should parse active timestamp without time")
        fun testActiveTimestampDateOnly() {
            val result = parser.parse("<2026-03-01 Sat>")

            assertNotNull(result)
            assertEquals("<2026-03-01 Sat>", result?.string)
            assertTrue(result?.isActive == true)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
            assertNull(result?.hour)
            assertNull(result?.minute)
        }

        @Test
        @DisplayName("should parse inactive timestamp without time")
        fun testInactiveTimestampDateOnly() {
            val result = parser.parse("[2026-03-01 Sat]")

            assertNotNull(result)
            assertEquals("[2026-03-01 Sat]", result?.string)
            assertFalse(result?.isActive == true)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
            assertNull(result?.hour)
            assertNull(result?.minute)
        }

        @Test
        @DisplayName("should parse active timestamp with time")
        fun testActiveTimestampWithTime() {
            val result = parser.parse("<2026-03-01 Sat 09:30>")

            assertNotNull(result)
            assertEquals("<2026-03-01 Sat 09:30>", result?.string)
            assertTrue(result?.isActive == true)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
            assertEquals(9, result?.hour)
            assertEquals(30, result?.minute)
        }

        @Test
        @DisplayName("should parse inactive timestamp with time")
        fun testInactiveTimestampWithTime() {
            val result = parser.parse("[2026-02-28 Fri 14:00]")

            assertNotNull(result)
            assertEquals("[2026-02-28 Fri 14:00]", result?.string)
            assertFalse(result?.isActive == true)
            assertEquals(2026, result?.year)
            assertEquals(2, result?.month)
            assertEquals(28, result?.day)
            assertEquals(14, result?.hour)
            assertEquals(0, result?.minute)
        }

        @Test
        @DisplayName("should handle timestamp without day name")
        fun testTimestampWithoutDayName() {
            val result = parser.parse("<2026-03-01>")

            assertNotNull(result)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
        }

        @Test
        @DisplayName("should return null for null input")
        fun testNullInput() {
            val result = parser.parse(null)
            assertNull(result)
        }

        @Test
        @DisplayName("should return null for invalid timestamp format")
        fun testInvalidFormat() {
            val result = parser.parse("not a timestamp")
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Repeater Parsing")
    inner class Repeaters {

        @Test
        @DisplayName("should parse cumulative repeater +1d")
        fun testCumulativeRepeaterDaily() {
            val result = parser.parse("<2026-03-01 Sat +1d>")

            assertNotNull(result)
            assertEquals("+", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("d", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse catch-up repeater ++1d")
        fun testCatchUpRepeaterDaily() {
            val result = parser.parse("<2026-03-01 Sat ++1d>")

            assertNotNull(result)
            assertEquals("++", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("d", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse restart repeater .+1d")
        fun testRestartRepeaterDaily() {
            val result = parser.parse("<2026-03-01 Sat .+1d>")

            assertNotNull(result)
            assertEquals(".+", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("d", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse weekly repeater +1w")
        fun testWeeklyRepeater() {
            val result = parser.parse("<2026-03-01 Sat +1w>")

            assertNotNull(result)
            assertEquals("+", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("w", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse monthly repeater ++1m")
        fun testMonthlyRepeater() {
            val result = parser.parse("<2026-03-01 Sat ++1m>")

            assertNotNull(result)
            assertEquals("++", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("m", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse yearly repeater .+1y")
        fun testYearlyRepeater() {
            val result = parser.parse("<2026-03-01 Sat .+1y>")

            assertNotNull(result)
            assertEquals(".+", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("y", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse hourly repeater +2h")
        fun testHourlyRepeater() {
            val result = parser.parse("<2026-03-01 Sat 09:00 +2h>")

            assertNotNull(result)
            assertEquals("+", result?.repeaterType)
            assertEquals(2, result?.repeaterValue)
            assertEquals("h", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse multi-value repeater +7d")
        fun testMultiValueRepeater() {
            val result = parser.parse("<2026-03-01 Sat +7d>")

            assertNotNull(result)
            assertEquals("+", result?.repeaterType)
            assertEquals(7, result?.repeaterValue)
            assertEquals("d", result?.repeaterUnit)
        }

        @Test
        @DisplayName("should parse timestamp with time and repeater")
        fun testTimestampWithTimeAndRepeater() {
            val result = parser.parse("<2026-03-01 Sat 14:30 ++1d>")

            assertNotNull(result)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
            assertEquals(14, result?.hour)
            assertEquals(30, result?.minute)
            assertEquals("++", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("d", result?.repeaterUnit)
        }
    }

    @Nested
    @DisplayName("Timestamp Conversion")
    inner class TimestampConversion {

        @Test
        @DisplayName("should convert date to epoch milliseconds")
        fun testDateToEpoch() {
            val result = parser.parse("<2026-03-01 Sat>")

            assertNotNull(result)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = result!!.timestamp
            assertEquals(2026, calendar.get(Calendar.YEAR))
            assertEquals(2, calendar.get(Calendar.MONTH)) // Calendar.MONTH is 0-indexed
            assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, calendar.get(Calendar.MINUTE))
        }

        @Test
        @DisplayName("should convert date with time to epoch milliseconds")
        fun testDateTimeToEpoch() {
            val result = parser.parse("<2026-03-01 Sat 14:30>")

            assertNotNull(result)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = result!!.timestamp
            assertEquals(2026, calendar.get(Calendar.YEAR))
            assertEquals(2, calendar.get(Calendar.MONTH)) // Calendar.MONTH is 0-indexed
            assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
            assertEquals(14, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(30, calendar.get(Calendar.MINUTE))
            assertEquals(0, calendar.get(Calendar.SECOND))
            assertEquals(0, calendar.get(Calendar.MILLISECOND))
        }

        @Test
        @DisplayName("should handle midnight correctly")
        fun testMidnight() {
            val result = parser.parse("<2026-03-01 Sat 00:00>")

            assertNotNull(result)
            assertEquals(0, result?.hour)
            assertEquals(0, result?.minute)
            
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = result!!.timestamp
            assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, calendar.get(Calendar.MINUTE))
        }

        @Test
        @DisplayName("should handle end of day correctly")
        fun testEndOfDay() {
            val result = parser.parse("<2026-03-01 Sat 23:59>")

            assertNotNull(result)
            assertEquals(23, result?.hour)
            assertEquals(59, result?.minute)
            
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = result!!.timestamp
            assertEquals(23, calendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, calendar.get(Calendar.MINUTE))
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle December date")
        fun testDecemberDate() {
            val result = parser.parse("<2026-12-31 Thu>")

            assertNotNull(result)
            assertEquals(2026, result?.year)
            assertEquals(12, result?.month)
            assertEquals(31, result?.day)
        }

        @Test
        @DisplayName("should handle January date")
        fun testJanuaryDate() {
            val result = parser.parse("<2026-01-01 Thu>")

            assertNotNull(result)
            assertEquals(2026, result?.year)
            assertEquals(1, result?.month)
            assertEquals(1, result?.day)
        }

        @Test
        @DisplayName("should handle leap year date")
        fun testLeapYearDate() {
            val result = parser.parse("<2024-02-29 Thu>")

            assertNotNull(result)
            assertEquals(2024, result?.year)
            assertEquals(2, result?.month)
            assertEquals(29, result?.day)
        }

        @Test
        @DisplayName("should preserve original string exactly")
        fun testPreserveOriginalString() {
            val original = "<2026-03-01 Sat 14:30 ++1d>"
            val result = parser.parse(original)

            assertNotNull(result)
            assertEquals(original, result?.string)
        }

        @Test
        @DisplayName("should handle timestamp with warning period")
        fun testTimestampWithWarningPeriod() {
            // Warning period format: <2026-03-01 Sat +1w -2d>
            // Parser should still extract base timestamp and repeater
            val result = parser.parse("<2026-03-01 Sat +1w -2d>")

            assertNotNull(result)
            assertEquals(2026, result?.year)
            assertEquals(3, result?.month)
            assertEquals(1, result?.day)
            assertEquals("+", result?.repeaterType)
            assertEquals(1, result?.repeaterValue)
            assertEquals("w", result?.repeaterUnit)
        }
    }
}
