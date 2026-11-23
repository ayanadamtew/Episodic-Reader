package com.episodicreader.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.ui.theme.LocalReaderColors

/**
 * DLG-01a: Confirmation dialog for permanently deleting an imported book.
 */
@Composable
fun DeleteBookDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val colors = LocalReaderColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.primaryInk,
        textContentColor = colors.secondaryInk,
        title = { Text("Delete \"$bookTitle\"?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "This removes the imported book copy and reading position from this device. Your overall reading streak and episode totals will be kept.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.secondaryInk)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text("Delete Book", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * DLG-01b: Confirmation dialog for resetting the Starter Classic back to Episode 1.
 */
@Composable
fun ResetStarterClassicDialog(
    bookTitle: String,
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    val colors = LocalReaderColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.primaryInk,
        textContentColor = colors.secondaryInk,
        title = { Text("Reset \"$bookTitle\"?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "This resets your position to Episode 1 and re-locks all character glossary facts so you can reread spoiler-free.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.secondaryInk)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmReset) {
                Text("Reset Progress", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * DLG-01c: Duplicate book import resolution dialog (FR-001 / BR-009).
 */
@Composable
fun DuplicateImportDialog(
    existingTitle: String,
    onDismiss: () -> Unit,
    onOpenExisting: () -> Unit,
    onImportSeparateCopy: () -> Unit,
) {
    val colors = LocalReaderColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.primaryInk,
        textContentColor = colors.secondaryInk,
        title = { Text("Already in Your Library", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "\"$existingTitle\" matches a book you already imported. Would you like to open your existing copy or import a separate copy?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            TextButton(onClick = onImportSeparateCopy) {
                Text("Import Separate Copy", color = colors.secondaryInk)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenExisting) {
                Text("Open Existing Copy", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * DLG-01d: Actionable local import error dialog (ERR-001 .. ERR-008).
 */
@Composable
fun ImportErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalReaderColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.primaryInk,
        textContentColor = colors.secondaryInk,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * Manual name lookup dialog for querying un-underlined names strictly within read history (FR-009).
 */
@Composable
fun ManualNameLookupDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val colors = LocalReaderColors.current
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.primaryInk,
        textContentColor = colors.secondaryInk,
        title = { Text("Prior Mentions Lookup", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a character or place name to search strictly within paragraphs you have already read.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("e.g., Nastenka") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.secondaryInk)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = query.trim()
                    if (trimmed.isNotEmpty()) {
                        onSearch(trimmed)
                    }
                },
                enabled = query.trim().isNotEmpty(),
            ) {
                Text("Search", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
