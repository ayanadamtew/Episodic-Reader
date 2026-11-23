package com.episodicreader.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.episodicreader.data.db.model.EpisodeKeyNameRow
import com.episodicreader.domain.recap.RecapPayload
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * OV-01: Calm "Previously On..." Break Re-Entry Overlay (FR-011 / BR-005).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviouslyOnOverlay(
    payload: RecapPayload,
    onDismiss: () -> Unit,
    onKeyNameTap: (EpisodeKeyNameRow) -> Unit,
) {
    val colors = LocalReaderColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "PREVIOUSLY ON...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close recap",
                            tint = colors.secondaryInk,
                        )
                    }
                }

                when (payload) {
                    is RecapPayload.Curated -> {
                        Text(
                            text = "Episode ${payload.episodeIndex} · ${payload.sectionTitle}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.primaryInk,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            payload.bullets.forEach { bullet ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "•  ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.accent,
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.primaryInk,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    is RecapPayload.Uploaded -> {
                        Text(
                            text = "Episode ${payload.episodeIndex} · ${payload.sectionTitle}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.primaryInk,
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        payload.openingSentence?.let { opening ->
                            Text(
                                text = "WHERE IT BEGAN",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.secondaryInk,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = opening,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = colors.primaryInk,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        if (payload.closingSentences.isNotEmpty()) {
                            Text(
                                text = "WHERE YOU LEFT OFF",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.secondaryInk,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = payload.closingSentences.joinToString(" "),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = colors.primaryInk,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (payload.keyNames.isNotEmpty()) {
                            Text(
                                text = "KEY NAMES IN THAT EPISODE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.secondaryInk,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                payload.keyNames.forEach { nameRow ->
                                    OutlinedButton(
                                        onClick = { onKeyNameTap(nameRow) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            text = nameRow.canonicalName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryInk,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is RecapPayload.EpisodeOneInProgress -> {
                        Text(
                            text = "Episode 1 in Progress",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.primaryInk,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = payload.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
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
                        text = "Continue Reading",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
