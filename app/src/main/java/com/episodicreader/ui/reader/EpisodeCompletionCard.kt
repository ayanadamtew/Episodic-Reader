package com.episodicreader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * Calm end-of-episode completion card (FR-007 / BR-006).
 * Completion is always explicit; scrolling alone never completes an episode.
 */
@Composable
fun EpisodeCompletionCard(
    episode: EpisodeEntity,
    isFinalEpisode: Boolean,
    hasNextEpisode: Boolean,
    onCompleteAndStop: () -> Unit,
    onCompleteAndContinue: () -> Unit,
) {
    val colors = LocalReaderColors.current
    val alreadyCompleted = episode.isCompleted == 1

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isFinalEpisode) {
                    "FINAL EPISODE REACHED"
                } else {
                    "END OF EPISODE ${episode.episodeIndex}"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    isFinalEpisode -> "A quiet finish to the journey."
                    alreadyCompleted -> "You have already completed this episode."
                    else -> "A natural place to pause."
                },
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryInk,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isFinalEpisode) {
                    "Mark this book finished to record it in your local reading progress."
                } else {
                    "Save your stopping point for today, or step calmly into the next episode."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onCompleteAndStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(
                    text = if (isFinalEpisode) "Finish Book" else "Complete Episode & Stop for Today",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            if (hasNextEpisode && !isFinalEpisode) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCompleteAndContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Complete & Continue",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.primaryInk,
                    )
                }
            }
        }
    }
}
