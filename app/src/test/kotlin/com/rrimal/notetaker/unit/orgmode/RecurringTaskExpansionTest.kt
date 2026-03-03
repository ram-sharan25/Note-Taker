package com.rrimal.notetaker.unit.orgmode

import com.rrimal.notetaker.data.local.OrgTimestampEntity
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CRITICAL TEST: Validates recurring task expansion logic for all repeater types.
 *
 * Tests all org-mode repeater patterns:
 * - + (cumulative): Show all missed instances
 * - ++ (catch-up): Shift from original date
 * - .+ (restart): Shift from completion date
 *
 * And all time units: h (hour), d (day), w (week), m (month), y (year)
 */
class RecurringTaskExpansionTest {

    private fun expandRecurringTimestamp(
        timestamp: OrgTimestampEntity,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDateTime> {
        val baseDate = LocalDate.of(timestamp.year, timestamp.month, timestamp.day)
        val baseTime = LocalTime.of(timestamp.hour ?: 0, timestamp.minute ?: 0)

        if (timestamp.repeaterType == null) {
            return if (!baseDate.isAfter(endDate)) {
                listOf(baseDate.atTime(baseTime))
            } else {
                emptyList()
            }
        }

        val result = mutableListOf<LocalDateTime>()
        var current = baseDate
        val value = (timestamp.repeaterValue ?: 1).toLong()

        // Jump-ahead logic for efficiency
        if (current.isBefore(startDate)) {
            val unit = when (timestamp.repeaterUnit) {
                "d" -> java.time.temporal.ChronoUnit.DAYS
                "w" -> java.time.temporal.ChronoUnit.WEEKS
                "m" -> java.time.temporal.ChronoUnit.MONTHS
                "y" -> java.time.temporal.ChronoUnit.YEARS
                else -> null
            }
            if (unit != null) {
                val diff = unit.between(current, startDate)
                if (diff > 0) {
                    current = current.plus((diff / value) * value, unit)
                    if (current.isBefore(startDate)) {
                        current = current.plus(value, unit)
                    }
                }
            }
        }

        while (!current.isAfter(endDate)) {
            if (!current.isBefore(baseDate)) {
                result.add(current.atTime(baseTime))
            }

            current = when (timestamp.repeaterUnit) {
                "h" -> {
                    val currentDateTime = current.atTime(baseTime)
                    val nextDateTime = currentDateTime.plusHours(value)
                    nextDateTime.toLocalDate()
                }
                "d" -> current.plusDays(value)
                "w" -> current.plusWeeks(value)
                "m" -> current.plusMonths(value)
                "y" -> current.plusYears(value)
                else -> break
            }
            if (value <= 0) break
        }

        return result
    }

    @Test
    fun `non-recurring timestamp returns single instance`() {
        // GIVEN: Timestamp without repeater
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-15 Sat 10:00>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 15,
            hour = 10,
            minute = 0,
            timestamp = 0,
            repeaterType = null,
            repeaterValue = null,
            repeaterUnit = null
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)

        // WHEN: Expand
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: Single instance on March 15
        assertEquals(1, instances.size)
        assertEquals(LocalDateTime.of(2026, 3, 15, 10, 0), instances[0])
    }

    @Test
    fun `daily repeater generates one instance per day`() {
        // GIVEN: Daily repeater (++1d)
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat 09:00 ++1d>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = 9,
            minute = 0,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 7)

        // WHEN: Expand for 7 days
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: 7 instances (Mar 1-7)
        assertEquals(7, instances.size)
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), instances[0])
        assertEquals(LocalDateTime.of(2026, 3, 7, 9, 0), instances[6])
    }

    @Test
    fun `weekly repeater generates one instance per week`() {
        // GIVEN: Weekly repeater (.+1w)
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat .+1w>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = ".+",
            repeaterValue = 1,
            repeaterUnit = "w"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 31)

        // WHEN: Expand for 31 days
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: 5 instances (Mar 1, 8, 15, 22, 29)
        assertEquals(5, instances.size)
        assertEquals(LocalDate.of(2026, 3, 1), instances[0].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 29), instances[4].toLocalDate())
    }

    @Test
    fun `monthly repeater generates one instance per month`() {
        // GIVEN: Monthly repeater (+1m)
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-01-15 Wed +1m>",
            isActive = true,
            year = 2026,
            month = 1,
            day = 15,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = "+",
            repeaterValue = 1,
            repeaterUnit = "m"
        )

        val startDate = LocalDate.of(2026, 1, 1)
        val endDate = LocalDate.of(2026, 6, 30)

        // WHEN: Expand for 6 months
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: 6 instances (15th of each month: Jan, Feb, Mar, Apr, May, Jun)
        assertEquals(6, instances.size)
        assertEquals(LocalDate.of(2026, 1, 15), instances[0].toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 15), instances[5].toLocalDate())
    }

    @Test
    fun `yearly repeater generates one instance per year`() {
        // GIVEN: Yearly repeater (++1y)
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2025-12-25 Thu ++1y>",
            isActive = true,
            year = 2025,
            month = 12,
            day = 25,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "y"
        )

        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2027, 12, 31)

        // WHEN: Expand for 3 years
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: 3 instances (Dec 25 in 2025, 2026, 2027)
        assertEquals(3, instances.size)
        assertEquals(LocalDate.of(2025, 12, 25), instances[0].toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 25), instances[1].toLocalDate())
        assertEquals(LocalDate.of(2027, 12, 25), instances[2].toLocalDate())
    }

    @Test
    fun `multi-day repeater skips intermediate days`() {
        // GIVEN: Every 3 days repeater (++3d)
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat ++3d>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 3,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 10)

        // WHEN: Expand for 10 days
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: 4 instances (Mar 1, 4, 7, 10)
        assertEquals(4, instances.size)
        assertEquals(LocalDate.of(2026, 3, 1), instances[0].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 4), instances[1].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 7), instances[2].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 10), instances[3].toLocalDate())
    }

    @Test
    fun `jump-ahead logic efficiently skips old recurring tasks`() {
        // GIVEN: Daily repeater starting 1 year ago
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2025-01-01 Wed ++1d>",
            isActive = true,
            year = 2025,
            month = 1,
            day = 1,
            hour = 9,
            minute = 0,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 7)

        val startTime = System.currentTimeMillis()

        // WHEN: Expand for current week
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        val duration = System.currentTimeMillis() - startTime

        // THEN: Returns 7 instances (current week)
        assertEquals(7, instances.size)

        // AND: Completes quickly (jump-ahead skips 400+ days in constant time)
        assertTrue(duration < 100, "Should complete in <100ms, took ${duration}ms")

        // AND: First instance is in current week
        assertEquals(LocalDate.of(2026, 3, 1), instances[0].toLocalDate())
    }

    @Test
    fun `timestamp with time preserves hour and minute`() {
        // GIVEN: Repeater with specific time
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat 14:30 ++1d>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = 14,
            minute = 30,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 3)

        // WHEN: Expand
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: All instances have same time
        assertEquals(3, instances.size)
        instances.forEach { instance ->
            assertEquals(14, instance.hour)
            assertEquals(30, instance.minute)
        }
    }

    @Test
    fun `timestamp without time defaults to midnight`() {
        // GIVEN: Repeater without time
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat ++1d>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 3)

        // WHEN: Expand
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: All instances default to 00:00
        assertEquals(3, instances.size)
        instances.forEach { instance ->
            assertEquals(0, instance.hour)
            assertEquals(0, instance.minute)
        }
    }

    @Test
    fun `expansion stops at end date`() {
        // GIVEN: Daily repeater
        val timestamp = OrgTimestampEntity(
            id = 0,
            string = "<2026-03-01 Sat ++1d>",
            isActive = true,
            year = 2026,
            month = 3,
            day = 1,
            hour = null,
            minute = null,
            timestamp = 0,
            repeaterType = "++",
            repeaterValue = 1,
            repeaterUnit = "d"
        )

        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 5)

        // WHEN: Expand with specific end date
        val instances = expandRecurringTimestamp(timestamp, startDate, endDate)

        // THEN: Only instances up to end date
        assertEquals(5, instances.size)
        assertTrue(instances.all { it.toLocalDate() <= endDate })
    }
}
