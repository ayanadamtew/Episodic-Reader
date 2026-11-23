package com.episodicreader.data.seed

import com.episodicreader.data.db.entity.ParagraphType
import com.episodicreader.data.db.entity.StarterClassicIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StarterPackIntegrityTest {

    private fun loadStarterPackJson(): String {
        val candidates = listOf(
            File("src/main/assets/starter_classic/white_nights_pack.json"),
            File("app/src/main/assets/starter_classic/white_nights_pack.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate white_nights_pack.json in ${candidates.map { it.absolutePath }}")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun starterPackHasContiguousSeqIndexesAndTargetEpisodeWordCounts() {
        val pack = StarterPackParser.parse(loadStarterPackJson())
        assertEquals(StarterClassicIds.WHITE_NIGHTS_BOOK_ID, pack.bookId)
        assertEquals("White Nights", pack.title)
        assertEquals("Fyodor Dostoevsky", pack.author)
        assertTrue(pack.attributionNote.contains("Public domain", ignoreCase = true))
        assertTrue("Expected at least 6 curated episodes", pack.episodes.size >= 6)

        var expectedSeq = 0
        val allSeqs = mutableSetOf<Int>()

        pack.episodes.forEachIndexed { idx, ep ->
            assertEquals(idx + 1, ep.episodeIndex)
            assertEquals("Each episode must have 3 curated recap bullets", 3, ep.recapBullets.size)
            assertTrue(
                "Episode ${ep.episodeIndex} word count ${ep.wordCount} outside [900, 1200]",
                ep.wordCount in 900..1200,
            )
            assertTrue(ep.estReadMinutes in 4..7)

            var actualWords = 0
            ep.paragraphs.forEach { p ->
                assertEquals("seq_index must be strictly contiguous", expectedSeq, p.seqIndex)
                allSeqs += p.seqIndex
                expectedSeq++
                assertTrue(
                    p.paragraphType in setOf(
                        ParagraphType.BODY,
                        ParagraphType.HEADING,
                        ParagraphType.BLOCKQUOTE,
                        ParagraphType.SCENE_BREAK,
                    ),
                )
                p.inlineMarks.forEach { mark ->
                    assertTrue(mark.start >= 0 && mark.end <= p.textContent.length && mark.start < mark.end)
                }
                if (p.paragraphType != ParagraphType.SCENE_BREAK) {
                    actualWords += p.textContent.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                }
            }
            assertEquals("Stored word_count must match actual paragraph word count", actualWords, ep.wordCount)
        }

        val maxSeq = expectedSeq - 1
        assertTrue(pack.entities.size >= 5)
        pack.entities.forEach { entity ->
            assertTrue(entity.aliases.isNotEmpty())
            assertTrue(entity.facts.isNotEmpty())
            assertTrue(entity.firstMentionSeq in 0..maxSeq)
            entity.facts.forEach { fact ->
                assertTrue(
                    "Fact ${fact.factId} unlocked_at_seq=${fact.unlockedAtSeq} out of bounds 0..$maxSeq",
                    fact.unlockedAtSeq in 0..maxSeq,
                )
            }
        }

        val bundle = StarterPackParser.toIngestionBundle(pack, importedAtUtc = 1000L)
        assertTrue("Expected curated mention spans to be detected", bundle.mentions.size >= 30)
        bundle.mentions.forEach { m ->
            val p = bundle.paragraphs.first { it.seqIndex == m.paragraphSeq }
            assertEquals(m.surfaceText, p.textContent.substring(m.charStart, m.charEnd))
        }
    }
}
