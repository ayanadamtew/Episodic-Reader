package com.episodicreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.episodicreader.data.db.entity.InlineMarkType
import com.episodicreader.data.db.entity.ParagraphEntity
import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.model.ParagraphMentionSpan
import com.episodicreader.ui.theme.LocalReaderColors
import com.episodicreader.ui.theme.ReaderColorTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class InlineMark(
    val start: Int,
    val end: Int,
    val style: String,
)

object InlineMarksParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String?): List<InlineMark> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            json.parseToJsonElement(rawJson).jsonArray.mapNotNull { el ->
                val obj = el.jsonObject
                val start = obj["start"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val end = obj["end"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val style = obj["style"]?.jsonPrimitive?.content ?: return@mapNotNull null
                InlineMark(start, end, style)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

fun buildParagraphAnnotatedString(
    paragraph: ParagraphEntity,
    mentions: List<ParagraphMentionSpan>,
    colors: ReaderColorTokens,
    onMentionTap: (ParagraphMentionSpan) -> Unit,
): AnnotatedString {
    val text = paragraph.textContent
    val marks = InlineMarksParser.parse(paragraph.inlineMarksJson)

    return buildAnnotatedString {
        append(text)

        marks.forEach { mark ->
            val safeStart = mark.start.coerceIn(0, text.length)
            val safeEnd = mark.end.coerceIn(safeStart, text.length)
            if (safeStart < safeEnd) {
                val style = when (mark.style) {
                    InlineMarkType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    InlineMarkType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> null
                }
                if (style != null) {
                    addStyle(style, safeStart, safeEnd)
                }
            }
        }

        mentions.forEach { span ->
            val safeStart = span.charStart.coerceIn(0, text.length)
            val safeEnd = span.charEnd.coerceIn(safeStart, text.length)
            if (safeStart < safeEnd) {
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "mention_${span.mentionId}",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = colors.primaryInk,
                                background = colors.highlightTint,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { onMentionTap(span) },
                    ),
                    start = safeStart,
                    end = safeEnd,
                )
            }
        }
    }
}

@Composable
fun ParagraphBlock(
    paragraph: ParagraphEntity,
    mentions: List<ParagraphMentionSpan>,
    fontSizeSp: Int,
    lineHeightMultiplier: Float,
    onMentionTap: (ParagraphMentionSpan) -> Unit,
    onBackgroundTap: () -> Unit,
) {
    val colors = LocalReaderColors.current
    val interactionSource = remember { MutableInteractionSource() }

    val baseLineHeight = (fontSizeSp * lineHeightMultiplier).sp

    when (paragraph.paragraphType) {
        ParagraphType.HEADING -> {
            Text(
                text = paragraph.textContent,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (fontSizeSp + 6).sp,
                    lineHeight = ((fontSizeSp + 6) * 1.3f).sp,
                    color = colors.primaryInk,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackgroundTap,
                    )
                    .padding(top = 12.dp, bottom = 16.dp),
            )
        }

        ParagraphType.SCENE_BREAK -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackgroundTap,
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = paragraph.textContent.ifBlank { "* * *" },
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = fontSizeSp.sp,
                        letterSpacing = 6.sp,
                        color = colors.secondaryInk,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }

        ParagraphType.BLOCKQUOTE -> {
            val annotated = remember(paragraph, mentions, colors) {
                buildParagraphAnnotatedString(paragraph, mentions, colors, onMentionTap)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackgroundTap,
                    )
                    .padding(vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.accent),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = fontSizeSp.sp,
                        lineHeight = baseLineHeight,
                        color = colors.secondaryInk,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        else -> {
            val annotated = remember(paragraph, mentions, colors) {
                buildParagraphAnnotatedString(paragraph, mentions, colors, onMentionTap)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackgroundTap,
                    )
                    .padding(bottom = 14.dp),
            ) {
                Text(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = fontSizeSp.sp,
                        lineHeight = baseLineHeight,
                        color = colors.primaryInk,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
