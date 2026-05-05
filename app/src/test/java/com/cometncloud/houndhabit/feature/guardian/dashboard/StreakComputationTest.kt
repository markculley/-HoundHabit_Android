package com.cometncloud.houndhabit.feature.guardian.dashboard

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Streak rules being verified:
 *  - Empty input → 0
 *  - Today only → 1
 *  - Today + yesterday + ... → length of unbroken back-chain from today
 *  - No today, but yesterday → starts the chain at yesterday (so user has all of today to log)
 *  - No today and no yesterday → 0 (streak is broken)
 *  - Duplicate sessions on the same day count as one
 *  - A gap (missed day) ends the streak before the gap
 */
class StreakComputationTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 5)

    /** Build an Instant representing the given LocalDate at noon in [tz]. */
    private fun day(date: LocalDate) =
        LocalDateTime(date, LocalTime(12, 0)).toInstant(tz)

    private fun day(year: Int, month: Int, dayOfMonth: Int) =
        day(LocalDate(year, month, dayOfMonth))

    @Test
    fun emptyInputReturnsZero() {
        assertEquals(0, computeStreak(emptyList(), tz = tz, today = today))
    }

    @Test
    fun todayOnlyReturnsOne() {
        assertEquals(1, computeStreak(listOf(day(today)), tz = tz, today = today))
    }

    @Test
    fun threeConsecutiveDaysEndingTodayReturnsThree() {
        val sessions = listOf(
            day(2026, 5, 3),
            day(2026, 5, 4),
            day(2026, 5, 5),
        )
        assertEquals(3, computeStreak(sessions, tz = tz, today = today))
    }

    @Test
    fun yesterdayButNotTodayReturnsOne() {
        // Streak is allowed to "linger" on yesterday until end of day today.
        val sessions = listOf(day(2026, 5, 4))
        assertEquals(1, computeStreak(sessions, tz = tz, today = today))
    }

    @Test
    fun noTodayAndNoYesterdayReturnsZero() {
        // Streak is broken once there's a gap of 2+ days.
        val sessions = listOf(day(2026, 5, 1), day(2026, 5, 2))
        assertEquals(0, computeStreak(sessions, tz = tz, today = today))
    }

    @Test
    fun gapEndsTheStreakAtTheGap() {
        // Logged: May 1, 3, 4, 5 (May 2 missing). Streak from today should be 3.
        val sessions = listOf(
            day(2026, 5, 1),
            day(2026, 5, 3),
            day(2026, 5, 4),
            day(2026, 5, 5),
        )
        assertEquals(3, computeStreak(sessions, tz = tz, today = today))
    }

    @Test
    fun multipleSessionsSameDayCountAsOne() {
        val morning = LocalDateTime(today, LocalTime(8, 0)).toInstant(tz)
        val evening = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        assertEquals(1, computeStreak(listOf(morning, evening), tz = tz, today = today))
    }

    @Test
    fun futureSessionsAreIgnored() {
        // A session dated tomorrow should not extend the streak from today's perspective.
        // (The streak walks back from today, so future days simply don't intersect the chain.)
        val sessions = listOf(day(2026, 5, 5), day(2026, 5, 7))
        assertEquals(1, computeStreak(sessions, tz = tz, today = today))
    }
}
