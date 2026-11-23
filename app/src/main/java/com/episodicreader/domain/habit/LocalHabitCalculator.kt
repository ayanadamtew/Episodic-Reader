package com.episodicreader.domain.habit

import com.episodicreader.data.db.model.DailyCompletionCountRow
import java.time.LocalDate

data class DayActivityMarker(
    val localDateIso: String,
    val dayShortLabel: String,
    val isActive: Boolean,
)

data class HabitSummary(
    val todayEpisodes: Int,
    val currentStreakDays: Int,
    val activeDaysLast7: Int,
    val last7Days: List<DayActivityMarker>,
    val allTimeEpisodes: Int,
    val booksFinished: Int,
)

/**
 * Pure local reading habit calculator (T-19 / FR-012 / BR-007 / BR-013).
 * Computes daily count, contiguous local-date streak ending today or yesterday (silent reset to 0),
 * 7-day active days count, and all-time totals strictly from local ReadingSessionLogs.
 */
object LocalHabitCalculator {

    fun calculate(
        dailyCounts: List<DailyCompletionCountRow>,
        booksFinished: Int,
        todayIso: String,
    ): HabitSummary {
        val today = LocalDate.parse(todayIso)
        val countByDate = mutableMapOf<LocalDate, Int>()
        var allTime = 0

        for (row in dailyCounts) {
            val parsed = runCatching { LocalDate.parse(row.localDateIso) }.getOrNull() ?: continue
            if (row.episodesCompleted > 0) {
                countByDate[parsed] = (countByDate[parsed] ?: 0) + row.episodesCompleted
                allTime += row.episodesCompleted
            }
        }

        val todayEpisodes = countByDate[today] ?: 0

        // Current streak: contiguous days ending today, or ending yesterday if today has 0 so far
        val streakAnchor = when {
            (countByDate[today] ?: 0) > 0 -> today
            (countByDate[today.minusDays(1)] ?: 0) > 0 -> today.minusDays(1)
            else -> null
        }

        var streak = 0
        if (streakAnchor != null) {
            var cursor: LocalDate = streakAnchor
            while ((countByDate[cursor] ?: 0) > 0) {
                streak++
                cursor = cursor.minusDays(1)
            }
        }

        val last7 = (6L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            DayActivityMarker(
                localDateIso = date.toString(),
                dayShortLabel = date.dayOfWeek.name.take(1),
                isActive = (countByDate[date] ?: 0) > 0,
            )
        }

        return HabitSummary(
            todayEpisodes = todayEpisodes,
            currentStreakDays = streak,
            activeDaysLast7 = last7.count { it.isActive },
            last7Days = last7,
            allTimeEpisodes = allTime,
            booksFinished = booksFinished.coerceAtLeast(0),
        )
    }
}
