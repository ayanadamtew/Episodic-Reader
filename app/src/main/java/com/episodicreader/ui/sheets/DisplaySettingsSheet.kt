package com.episodicreader.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.episodicreader.data.db.entity.LineHeightMode
import com.episodicreader.data.db.entity.ThemeMode
import com.episodicreader.data.db.entity.TypographyBounds
import com.episodicreader.data.db.entity.UserPreferencesEntity
import com.episodicreader.ui.theme.LocalReaderColors
import com.episodicreader.ui.theme.ReaderPalettes

/**
 * SH-01: Live-preview Display & Typography Settings sheet.
 * Supports Light / Sepia / High-Contrast Dark, 12–28sp (2sp steps), and Compact/Standard/Relaxed spacing.
 * Strictly excludes Bionic/bold-prefix typography per BR-014.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsSheet(
    preferences: UserPreferencesEntity,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onChangeFontSize: (Int) -> Unit,
    onSelectLineHeight: (String) -> Unit,
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
        ) {
            Text(
                text = "Display Settings",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryInk,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "THEME",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeSwatchOption(
                    label = "Light",
                    swatchBg = ReaderPalettes.Light.background,
                    swatchInk = ReaderPalettes.Light.primaryInk,
                    selected = preferences.themeMode == ThemeMode.LIGHT,
                    onClick = { onSelectTheme(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemeSwatchOption(
                    label = "Sepia",
                    swatchBg = ReaderPalettes.Sepia.background,
                    swatchInk = ReaderPalettes.Sepia.primaryInk,
                    selected = preferences.themeMode == ThemeMode.SEPIA,
                    onClick = { onSelectTheme(ThemeMode.SEPIA) },
                    modifier = Modifier.weight(1f),
                )
                ThemeSwatchOption(
                    label = "Dark",
                    swatchBg = ReaderPalettes.DarkHighContrast.background,
                    swatchInk = ReaderPalettes.DarkHighContrast.primaryInk,
                    selected = preferences.themeMode == ThemeMode.DARK_HIGH_CONTRAST,
                    onClick = { onSelectTheme(ThemeMode.DARK_HIGH_CONTRAST) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TEXT SIZE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val canDecrease = preferences.fontSizeSp > TypographyBounds.MIN_FONT_SIZE_SP
                val canIncrease = preferences.fontSizeSp < TypographyBounds.MAX_FONT_SIZE_SP

                OutlinedIconButton(
                    onClick = {
                        onChangeFontSize(preferences.fontSizeSp - TypographyBounds.FONT_SIZE_STEP_SP)
                    },
                    enabled = canDecrease,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Decrease text size" },
                ) {
                    Icon(imageVector = Icons.Outlined.Remove, contentDescription = null)
                }

                Text(
                    text = "${preferences.fontSizeSp} sp",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primaryInk,
                )

                OutlinedIconButton(
                    onClick = {
                        onChangeFontSize(preferences.fontSizeSp + TypographyBounds.FONT_SIZE_STEP_SP)
                    },
                    enabled = canIncrease,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Increase text size" },
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "LINE SPACING",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LineSpacingOption(
                    label = "Compact",
                    selected = preferences.lineHeightMode == LineHeightMode.COMPACT,
                    onClick = { onSelectLineHeight(LineHeightMode.COMPACT) },
                    modifier = Modifier.weight(1f),
                )
                LineSpacingOption(
                    label = "Standard",
                    selected = preferences.lineHeightMode == LineHeightMode.STANDARD,
                    onClick = { onSelectLineHeight(LineHeightMode.STANDARD) },
                    modifier = Modifier.weight(1f),
                )
                LineSpacingOption(
                    label = "Relaxed",
                    selected = preferences.lineHeightMode == LineHeightMode.RELAXED,
                    onClick = { onSelectLineHeight(LineHeightMode.RELAXED) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatchOption(
    label: String,
    swatchBg: Color,
    swatchInk: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label reading theme" },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.highlightTint else colors.background,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.accent else colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(swatchBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(swatchInk),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = colors.primaryInk,
            )
        }
    }
}

@Composable
private fun LineSpacingOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label line spacing" },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.highlightTint else colors.background,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.accent else colors.border,
        ),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = colors.primaryInk,
            )
        }
    }
}
