package com.episodicreader.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.domain.lookup.CuratedCharacterProfile
import com.episodicreader.ui.components.ExcerptQuoteCard
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * SH-02: Spoiler-free Curated Character Glossary sheet for the starter classic (FR-010 / BR-004).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratedCharacterSheet(
    profile: CuratedCharacterProfile,
    onDismiss: () -> Unit,
) {
    val colors = LocalReaderColors.current
    var firstSeenExpanded by remember { mutableStateOf(false) }

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
                text = "CHARACTER GUIDE · SPOILER-FREE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )

            Text(
                text = profile.canonicalName,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primaryInk,
            )

            if (profile.shortRole.isNotBlank()) {
                Text(
                    text = profile.shortRole,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk,
                )
            }

            if (!profile.aliasToneNote.isNullOrBlank() && !profile.matchedAlias.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.highlightTint,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Address note: \"${profile.matchedAlias}\"",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.aliasToneNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primaryInk,
                        )
                    }
                }
            }

            Text(
                text = "WHAT YOU KNOW SO FAR",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
            )

            if (profile.unlockedFacts.isEmpty()) {
                Text(
                    text = "Keep reading to learn more about ${profile.canonicalName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.unlockedFacts.forEach { fact ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•  ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.accent,
                            )
                            Text(
                                text = fact.factText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryInk,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (profile.firstSeen != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { firstSeenExpanded = !firstSeenExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.background,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "First Seen in the Story",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryInk,
                        )
                        Icon(
                            imageVector = if (firstSeenExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (firstSeenExpanded) "Collapse First Seen" else "Expand First Seen",
                            tint = colors.secondaryInk,
                        )
                    }
                }

                AnimatedVisibility(visible = firstSeenExpanded) {
                    ExcerptQuoteCard(excerpt = profile.firstSeen, badgeLabel = "First Seen")
                }
            }
        }
    }
}
