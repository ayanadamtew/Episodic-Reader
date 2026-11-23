package com.episodicreader.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.domain.habit.HabitSummary
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * SH-04: "My Reading Progress" local habit sheet (FR-012 / BR-007 / BR-013).
 * Displays Today, Current Streak, Past 7 Days ("X of 7 days active"), and All-Time episodes & finished books.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingProgressSheet(
    summary: HabitSummary,
    onDismiss: () -> Unit,
) {
    val colors = LocalReaderColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.primaryInk,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "My Reading Progress",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primaryInk,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HabitMetricCard(
                    label = "TODAY",
                    value = "${summary.todayEpisodes}",
                    unit = if (summary.todayEpisodes == 1) "episode" else "episodes",
                    modifier = Modifier.weight(1f),
                )
                HabitMetricCard(
                    label = "CURRENT STREAK",
                    value = "${summary.currentStreakDays}",
                    unit = if (summary.currentStreakDays == 1) "day" else "days",
                    modifier = Modifier.weight(1f),
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = colors.background,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PAST 7 DAYS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.secondaryInk,
                        )
                        Text(
                            text = "${summary.activeDaysLast7} of 7 days active",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        summary.last7Days.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (day.isActive) colors.accent else colors.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.dayShortLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (day.isActive) colors.onAccent else colors.secondaryInk,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HabitMetricCard(
                    label = "ALL-TIME EPISODES",
                    value = "${summary.allTimeEpisodes}",
                    unit = "completed",
                    modifier = Modifier.weight(1f),
                )
                HabitMetricCard(
                    label = "BOOKS FINISHED",
                    value = "${summary.booksFinished}",
                    unit = if (summary.booksFinished == 1) "book" else "books",
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Stored privately on this device. No cloud sync or tracking.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk,
            )
        }
    }
}

@Composable
private fun HabitMetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primaryInk,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk,
            )
        }
    }
}
