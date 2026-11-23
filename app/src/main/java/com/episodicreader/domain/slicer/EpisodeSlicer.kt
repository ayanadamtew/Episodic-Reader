package com.episodicreader.domain.slicer

import com.episodicreader.data.db.entity.ParagraphType

data class RawInlineMark(
    val start: Int,
    val end: Int,
    val style: String,
)

data class RawParagraph(
    val paragraphType: String,
    val textContent: String,
    val sectionTitle: String? = null,
    val inlineMarks: List<RawInlineMark> = emptyList(),
)

data class SequencedParagraph(
    val seqIndex: Int,
    val paragraphType: String,
    val textContent: String,
    val inlineMarks: List<RawInlineMark>,
)

data class SlicedEpisode(
    val episodeIndex: Int,
    val sectionTitle: String,
    val wordCount: Int,
    val estReadMinutes: Int,
    val startParagraphSeq: Int,
    val endParagraphSeq: Int,
    val paragraphs: List<SequencedParagraph>,
)

/**
 * Deterministic episodic slicer implementing BR-002 and ERR-011:
 *  - Target 900–1,200 words per episode.
 *  - Never splits a single paragraph across episodes.
 *  - Snaps breaks within ±150 words to HEADING or SCENE_BREAK boundaries.
 *  - Merges micro-chapters (< 400 words) with the next section when combined <= 1,200 words.
 *  - Keeps oversized single paragraphs (> 1,200 words) intact as standalone episodes.
 *  - Short books (< 900 words, ERR-011) become a single valid Episode 1.
 */
class EpisodeSlicer(
    private val minWords: Int = 900,
    private val targetWords: Int = 1000,
    private val maxWords: Int = 1200,
    private val snapWindowWords: Int = 150,
    private val microChapterThresholdWords: Int = 400,
) {
    fun slice(rawParagraphs: List<RawParagraph>, fallbackTitle: String = "Section"): List<SlicedEpisode> {
        val cleaned = rawParagraphs.filter {
            it.paragraphType == ParagraphType.SCENE_BREAK || it.textContent.isNotBlank()
        }
        if (cleaned.isEmpty()) return emptyList()

        val sequenced = cleaned.mapIndexed { idx, raw ->
            val words = if (raw.paragraphType == ParagraphType.SCENE_BREAK) {
                0
            } else {
                WordCounter.countWords(raw.textContent)
            }
            IndexedRawParagraph(
                seqIndex = idx,
                raw = raw,
                wordCount = words,
            )
        }

        val episodes = mutableListOf<List<IndexedRawParagraph>>()
        val sectionTitles = mutableListOf<String>()

        var current = mutableListOf<IndexedRawParagraph>()
        var currentWords = 0
        var activeSectionTitle = fallbackTitle
        var episodeSectionTitle = fallbackTitle

        fun flushEpisode() {
            if (current.isEmpty()) return
            episodes += current.toList()
            sectionTitles += episodeSectionTitle
            current = mutableListOf()
            currentWords = 0
            episodeSectionTitle = activeSectionTitle
        }

        var i = 0
        while (i < sequenced.size) {
            val item = sequenced[i]

            if (item.raw.paragraphType == ParagraphType.HEADING && item.raw.textContent.isNotBlank()) {
                activeSectionTitle = item.raw.textContent.trim()
            } else if (!item.raw.sectionTitle.isNullOrBlank()) {
                activeSectionTitle = item.raw.sectionTitle.trim()
            }

            if (current.isEmpty()) {
                episodeSectionTitle = activeSectionTitle
            }

            // Natural boundary snap: HEADING starts a new episode if we are within [minWords - snapWindow, maxWords + snapWindow]
            if (item.raw.paragraphType == ParagraphType.HEADING &&
                current.isNotEmpty() &&
                currentWords >= (minWords - snapWindowWords)
            ) {
                flushEpisode()
                episodeSectionTitle = activeSectionTitle
                current += item
                currentWords += item.wordCount
                i++
                continue
            }

            // Oversized single paragraph (> maxWords): keep strictly atomic
            if (item.wordCount > maxWords) {
                if (current.isNotEmpty()) {
                    flushEpisode()
                }
                episodeSectionTitle = activeSectionTitle
                current += item
                currentWords = item.wordCount
                flushEpisode()
                i++
                continue
            }

            // Would adding this paragraph exceed maxWords?
            if (current.isNotEmpty() && currentWords + item.wordCount > maxWords) {
                flushEpisode()
                episodeSectionTitle = activeSectionTitle
                current += item
                currentWords += item.wordCount
                i++
                continue
            }

            current += item
            currentWords += item.wordCount

            // If we hit a SCENE_BREAK inside the snap window (>= minWords - snapWindow), flush cleanly right after it
            if (item.raw.paragraphType == ParagraphType.SCENE_BREAK &&
                currentWords >= (minWords - snapWindowWords)
            ) {
                flushEpisode()
                i++
                continue
            }

            // Inside [minWords, maxWords], flush at targetWords or right before a HEADING / overflow
            val next = sequenced.getOrNull(i + 1)
            if (currentWords >= minWords && next != null) {
                if (next.raw.paragraphType == ParagraphType.HEADING ||
                    currentWords >= targetWords ||
                    currentWords + next.wordCount > maxWords
                ) {
                    flushEpisode()
                }
            }

            i++
        }

        flushEpisode()

        // Micro-chapter merge pass: merge trailing or intermediate tiny episodes (< 400w) when combined <= maxWords
        val mergedEpisodes = mutableListOf<Pair<String, List<IndexedRawParagraph>>>()
        for (idx in episodes.indices) {
            val epTitle = sectionTitles[idx]
            val epParas = episodes[idx]
            val epWords = epParas.sumOf { it.wordCount }

            if (mergedEpisodes.isNotEmpty()) {
                val (prevTitle, prevParas) = mergedEpisodes.last()
                val prevWords = prevParas.sumOf { it.wordCount }
                if ((prevWords < microChapterThresholdWords || epWords < microChapterThresholdWords) &&
                    prevWords + epWords <= maxWords
                ) {
                    mergedEpisodes[mergedEpisodes.lastIndex] = prevTitle to (prevParas + epParas)
                    continue
                }
            }
            mergedEpisodes += epTitle to epParas
        }

        return mergedEpisodes.mapIndexed { idx, (title, paras) ->
            val words = paras.sumOf { it.wordCount }
            SlicedEpisode(
                episodeIndex = idx + 1,
                sectionTitle = title.ifBlank { fallbackTitle },
                wordCount = words,
                estReadMinutes = WordCounter.estimateMinutes(words),
                startParagraphSeq = paras.first().seqIndex,
                endParagraphSeq = paras.last().seqIndex,
                paragraphs = paras.map {
                    SequencedParagraph(
                        seqIndex = it.seqIndex,
                        paragraphType = it.raw.paragraphType,
                        textContent = it.raw.textContent,
                        inlineMarks = it.raw.inlineMarks,
                    )
                },
            )
        }
    }

    private data class IndexedRawParagraph(
        val seqIndex: Int,
        val raw: RawParagraph,
        val wordCount: Int,
    )
}
