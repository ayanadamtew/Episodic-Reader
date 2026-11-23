package com.episodicreader.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.episodicreader.data.db.entity.ImportStatus
import com.episodicreader.data.db.entity.SourceType
import com.episodicreader.data.db.model.LibraryBookRow
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * SCR-01: Calm episodic Library screen.
 * Strictly obeys BR-001: NEVER displays total book page count, total episode count,
 * or overall book completion percentage.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onOpenBook: (String) -> Unit,
    onImportBookClick: () -> Unit,
    onRetrySeed: () -> Unit,
    onRestoreStarterClassic: () -> Unit,
    onRequestDeleteBook: (LibraryBookRow) -> Unit,
    onRequestResetStarter: (LibraryBookRow) -> Unit,
    onRequestHideStarter: (LibraryBookRow) -> Unit,
    habitBarContent: @Composable () -> Unit = {},
) {
    val colors = LocalReaderColors.current

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding),
        ) {
            LibraryTopHeader(
                onImportBookClick = onImportBookClick,
                hasHiddenStarter = uiState.hasHiddenStarterClassic && uiState.books.isNotEmpty(),
                onRestoreStarterClassic = onRestoreStarterClassic,
            )

            habitBarContent()

            if (uiState.seedErrorMessage != null) {
                SeedErrorBanner(
                    message = uiState.seedErrorMessage,
                    onRetry = onRetrySeed,
                )
            }

            if (uiState.isSeeding && uiState.books.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Preparing your starter classic...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk,
                        )
                    }
                }
            } else if (uiState.books.isEmpty()) {
                LibraryEmptyState(
                    onRestoreStarterClassic = onRestoreStarterClassic,
                    onImportBookClick = onImportBookClick,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.books, key = { it.bookId }) { book ->
                        LibraryBookCard(
                            book = book,
                            onClick = {
                                if (book.importStatus == ImportStatus.READY) {
                                    onOpenBook(book.bookId)
                                }
                            },
                            onDeleteBook = { onRequestDeleteBook(book) },
                            onResetStarter = { onRequestResetStarter(book) },
                            onHideStarter = { onRequestHideStarter(book) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LibraryTopHeader(
    onImportBookClick: () -> Unit,
    hasHiddenStarter: Boolean,
    onRestoreStarterClassic: () -> Unit,
) {
    val colors = LocalReaderColors.current
    var headerMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.primaryInk,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onImportBookClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Import EPUB or PDF book" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import Book",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            if (hasHiddenStarter) {
                Box {
                    IconButton(
                        onClick = { headerMenuExpanded = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Library options",
                            tint = colors.secondaryInk,
                        )
                    }
                    DropdownMenu(
                        expanded = headerMenuExpanded,
                        onDismissRequest = { headerMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Restore Starter Classic") },
                            onClick = {
                                headerMenuExpanded = false
                                onRestoreStarterClassic()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBookCard(
    book: LibraryBookRow,
    onClick: () -> Unit,
    onDeleteBook: () -> Unit,
    onResetStarter: () -> Unit,
    onHideStarter: () -> Unit,
) {
    val colors = LocalReaderColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isProcessing = book.importStatus == ImportStatus.PROCESSING
    val isStarter = book.sourceType == SourceType.STARTER_CLASSIC

    val tagText = when (book.sourceType) {
        SourceType.STARTER_CLASSIC -> "STARTER CLASSIC"
        SourceType.PDF -> "PDF"
        else -> "EPUB"
    }

    val episodeLine = when {
        isProcessing -> "Preparing episodes..."
        book.completedAtUtc != null -> "Finished · Revisit anytime"
        book.currentEpisodeIndex != null -> {
            val section = book.currentSectionTitle?.takeIf { it.isNotBlank() }
            val mins = book.currentEstReadMinutes ?: 5
            if (section != null) {
                "$section · Episode ${book.currentEpisodeIndex} · ~$mins min"
            } else {
                "Episode ${book.currentEpisodeIndex} · ~$mins min"
            }
        }
        else -> "Episode 1 · ~5 min"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isProcessing, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tagText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primaryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = episodeLine,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isProcessing) colors.secondaryInk else colors.primaryInk,
                    )
                }
            }

            if (!isProcessing) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Book options for ${book.title}",
                            tint = colors.secondaryInk,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (isStarter) {
                            DropdownMenuItem(
                                text = { Text("Reset Progress") },
                                onClick = {
                                    menuExpanded = false
                                    onResetStarter()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Hide Starter Classic") },
                                onClick = {
                                    menuExpanded = false
                                    onHideStarter()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Delete Book") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteBook()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.error),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    onRestoreStarterClassic: () -> Unit,
    onImportBookClick: () -> Unit,
) {
    val colors = LocalReaderColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your shelf is quiet",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryInk,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Import a DRM-free EPUB or text-based PDF, or restore the curated starter classic.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRestoreStarterClassic,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Starter Classic")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onImportBookClick,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Import EPUB or PDF")
            }
        }
    }
}
