package com.episodicreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.episodicreader.data.db.entity.LineHeightMode
import com.episodicreader.data.db.entity.UserPreferencesEntity
import com.episodicreader.data.db.model.ParagraphMentionSpan
import com.episodicreader.ui.theme.LocalReaderColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * SCR-02: Distraction-free episodic ReaderScreen.
 * Obeys BR-001 (intra-episode progress hairline only, zero total book length indicators)
 * and BR-014 (clean serif reflowable typography, zero Bionic bold-prefix styling).
 */
@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    preferences: UserPreferencesEntity,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenManualRecap: () -> Unit,
    onOpenManualLookup: () -> Unit,
    onDismissPdfNotice: () -> Unit,
    onMentionTap: (ParagraphMentionSpan) -> Unit,
    onVisibleParagraphChanged: (firstSeq: Int, lastSeq: Int) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    completionCardContent: @Composable () -> Unit = {},
) {
    val colors = LocalReaderColors.current
    val listState = rememberLazyListState()
    var overflowExpanded by remember { mutableStateOf(false) }

    val episodeId = uiState.episode?.episodeId
    LaunchedEffect(episodeId, uiState.initialScrollParagraphIndex) {
        if (episodeId != null && uiState.paragraphs.isNotEmpty()) {
            val target = uiState.initialScrollParagraphIndex.coerceIn(0, uiState.paragraphs.lastIndex)
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState, uiState.paragraphs) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val paragraphCount = uiState.paragraphs.size
            if (visible.isEmpty() || paragraphCount == 0) {
                null
            } else {
                val firstIdx = visible.first().index.coerceIn(0, paragraphCount - 1)
                val lastIdx = visible.last().index.coerceIn(0, paragraphCount - 1)
                uiState.paragraphs[firstIdx].seqIndex to uiState.paragraphs[lastIdx].seqIndex
            }
        }
            .distinctUntilChanged()
            .collect { range ->
                if (range != null) {
                    onVisibleParagraphChanged(range.first, range.second)
                }
            }
    }

    val intraEpisodeProgress by remember(uiState.paragraphs) {
        derivedStateOf {
            val total = uiState.paragraphs.size
            if (total <= 1) {
                1f
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((lastVisible + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val lineHeightMultiplier = LineHeightMode.multiplierFor(preferences.lineHeightMode)

    Scaffold(
        containerColor = colors.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (uiState.episode == null || uiState.book == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Unable to load episode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryInk,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = uiState.chromeVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back to Library",
                                        tint = colors.primaryInk,
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Episode ${uiState.episode.episodeIndex} · ~${uiState.episode.estReadMinutes} min",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.primaryInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (uiState.episode.sectionTitle.isNotBlank()) {
                                        Text(
                                            text = uiState.episode.sectionTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.secondaryInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onOpenDisplaySettings,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.TextFields,
                                        contentDescription = "Display settings",
                                        tint = colors.primaryInk,
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { overflowExpanded = true },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = "Reader options",
                                            tint = colors.primaryInk,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = overflowExpanded,
                                        onDismissRequest = { overflowExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Recap Last Episode") },
                                            onClick = {
                                                overflowExpanded = false
                                                onOpenManualRecap()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Prior Mentions Lookup") },
                                            onClick = {
                                                overflowExpanded = false
                                                onOpenManualLookup()
                                            },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.border, thickness = 1.dp)
                        }
                    }

                    // 2dp intra-episode progress hairline ONLY (BR-001)
                    LinearProgressIndicator(
                        progress = { intraEpisodeProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .semantics {
                                contentDescription = "Current episode reading progress ${(intraEpisodeProgress * 100).toInt()} percent"
                            },
                        color = colors.accent,
                        trackColor = colors.border,
                    )

                    if (uiState.showPdfNoticeBanner) {
                        PdfFormattingHintBanner(onDismiss = onDismissPdfNotice)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 640.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                        ) {
                            items(
                                items = uiState.paragraphs,
                                key = { it.seqIndex },
                            ) { paragraph ->
                                ParagraphBlock(
                                    paragraph = paragraph,
                                    mentions = uiState.mentionsBySeq[paragraph.seqIndex].orEmpty(),
                                    fontSizeSp = preferences.fontSizeSp,
                                    lineHeightMultiplier = lineHeightMultiplier,
                                    onMentionTap = onMentionTap,
                                    onBackgroundTap = onToggleChrome,
                                )
                            }

                            item(key = "episode_completion_slot") {
                                Spacer(modifier = Modifier.height(16.dp))
                                completionCardContent()
                            }

                            item(key = "episode_nav_controls") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (uiState.hasPrevious) {
                                        OutlinedButton(
                                            onClick = onPreviousEpisode,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text("Previous Episode")
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                    if (uiState.hasNext) {
                                        OutlinedButton(
                                            onClick = onNextEpisode,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text("Skip to Next")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfFormattingHintBanner(onDismiss: () -> Unit) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reflowed from PDF text. Occasional line-break quirks may appear.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss PDF formatting note",
                    tint = colors.secondaryInk,
                )
            }
        }
    }
}
