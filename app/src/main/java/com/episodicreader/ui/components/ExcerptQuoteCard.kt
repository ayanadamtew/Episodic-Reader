package com.episodicreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.domain.lookup.MentionExcerpt
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * Reusable quote card displaying a spoiler-safe prior excerpt with the matched name highlighted.
 */
@Composable
fun ExcerptQuoteCard(
    excerpt: MentionExcerpt,
    badgeLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColors.current

    val locationLabel = buildString {
        if (!badgeLabel.isNullOrBlank()) {
            append(badgeLabel)
            append(" · ")
        }
        append("Episode ${excerpt.episodeIndex}")
        if (excerpt.sectionTitle.isNotBlank()) {
            append(" · ${excerpt.sectionTitle}")
        }
    }

    val annotatedSnippet = buildAnnotatedString {
        append(excerpt.snippetText)
        val s = excerpt.highlightStart.coerceIn(0, excerpt.snippetText.length)
        val e = excerpt.highlightEnd.coerceIn(s, excerpt.snippetText.length)
        if (s < e) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    background = colors.highlightTint,
                    color = colors.primaryInk,
                ),
                start = s,
                end = e,
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = annotatedSnippet,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                color = colors.primaryInk,
            )
        }
    }
}
