package com.episodicreader.domain.habit

import com.episodicreader.data.db.model.DailyCompletionCountRow
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalHabitCalculatorTest {

    @Test
    fun calculatesContiguousStreakEndingTodayAnd7DayActiveCount() {
        val rows = listOf(
            DailyCompletionCountRow("2026-11-20", 2),
            DailyCompletionCountRow("2026-11-19", 1),
            DailyCompletionCountRow("2026-11-18", 1),
            DailyCompletionCountRow("2026-11-16", 3), // gap on Nov 17
            DailyCompletionCountRow("2026-11-10", 4), // older than 7 days
        )

        val summary = LocalHabitCalculator.calculate(rows, booksFinished = 1, todayIso = "2026-11-20")
        assertEquals(2, summary.todayEpisodes)
        assertEquals(3, summary.currentStreakDays)
        assertEquals(4, summary.activeDaysLast7)
        assertEquals(11, summary.allTimeEpisodes)
        assertEquals(1, summary.booksFinished)
    }

    @Test
    fun keepsStreakAliveWhenReadYesterdayAndSilentlyResetsToZeroAfterMissedDay() {
        val yesterdayOnly = listOf(
            DailyCompletionCountRow("2026-11-19", 1),
            DailyCompletionCountRow("2026-11-18", 2),
        )
        val alive = LocalHabitCalculator.calculate(yesterdayOnly, booksFinished = 0, todayIso = "2026-11-20")
        assertEquals(0, alive.todayEpisodes)
        assertEquals(2, alive.currentStreakDays)

        val twoDaysAgo = listOf(
            DailyCompletionCountRow("2026-11-18", 2),
            DailyCompletionCountRow("2026-11-17", 1),
        )
        val reset = LocalHabitCalculator.calculate(twoDaysAgo, booksFinished = 0, todayIso = "2026-11-20")
        assertEquals(0, reset.currentStreakDays)
        assertEquals(3, reset.allTimeEpisodes)
    }
}
