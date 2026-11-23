package com.episodicreader.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.domain.lookup.PriorMentionsResult
import com.episodicreader.ui.components.ExcerptQuoteCard
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * SH-03: Spoiler-free Prior Mentions sheet for uploaded books and manual lookup (FR-009 / ERR-012).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorMentionsSheet(
    result: PriorMentionsResult,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = result.queryTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primaryInk,
            )
            Text(
                text = "Spoiler-free mentions strictly from what you have read so far",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (result.isEmpty) {
                Text(
                    text = PriorMentionsResult.EMPTY_MESSAGE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                result.firstSeen?.let { first ->
                    Text(
                        text = "FIRST SEEN",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.secondaryInk,
                    )
                    ExcerptQuoteCard(excerpt = first, badgeLabel = "First Seen")
                }

                if (result.recentMentions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RECENT MENTIONS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.secondaryInk,
                    )
                    result.recentMentions.forEach { mention ->
                        ExcerptQuoteCard(excerpt = mention)
                    }
                }
            }
        }
    }
}
