package com.episodicreader.domain.lookup

import com.episodicreader.data.db.entity.EntityFactEntity
import com.episodicreader.data.db.entity.EntitySourceKind
import com.episodicreader.data.db.entity.NameAliasEntity
import com.episodicreader.data.db.entity.NameEntity
import com.episodicreader.data.db.model.MentionExcerptRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CuratedGlossaryServiceTest {

    private class FakeCuratedSource : CuratedGlossaryDataSource {
        val entities = mapOf(
            "nastenka" to NameEntity("nastenka", "wn", "Nastenka", EntitySourceKind.CURATED, "Young woman by the canal", 8),
        )
        val aliases = listOf(
            NameAliasEntity("a1", "wn", "nastenka", "Nastenka", "nastenka", "Affectionate diminutive."),
            NameAliasEntity("a2", "wn", "nastenka", "Anastasia", "anastasia", "Formal full name used by Grandmother."),
        )
        val facts = listOf(
            EntityFactEntity("f1", "wn", "nastenka", 8, "Lives with her Grandmother.", 1),
            EntityFactEntity("f2", "wn", "nastenka", 70, "Spoiler: reunites with the Lodger on Night 4.", 2),
        )
        val firstRow = MentionExcerptRow(
            "m1", "nastenka", "Nastenka", 8, "ep1", 1, "First Night",
            "She told me that her name was Nastenka.", 30, 38, "Nastenka",
        )

        override suspend fun getEntityById(bookId: String, entityId: String): NameEntity? = entities[entityId]
        override suspend fun getAllAliasesForBook(bookId: String): List<NameAliasEntity> = aliases
        override suspend fun getAliasesForEntity(entityId: String): List<NameAliasEntity> = aliases.filter { it.entityId == entityId }
        override suspend fun getUnlockedFacts(bookId: String, entityId: String, maxReadSeq: Int): List<EntityFactEntity> =
            facts.filter { it.entityId == entityId && it.unlockedAtSeq <= maxReadSeq }
        override suspend fun getFirstMentionUpTo(bookId: String, entityId: String, maxReadSeq: Int): MentionExcerptRow? =
            firstRow.takeIf { it.paragraphSeq <= maxReadSeq }
    }

    @Test
    fun resolvesAliasToneNoteAndStrictlyBlocksFutureSpoilerFacts() = runTest {
        val service = CuratedGlossaryService(FakeCuratedSource())

        val atEp1 = service.lookupByQuery("wn", "Anastasia", maxReadSeq = 12)
        assertNotNull(atEp1)
        assertEquals("Nastenka", atEp1!!.canonicalName)
        assertEquals("Formal full name used by Grandmother.", atEp1.aliasToneNote)
        assertEquals(1, atEp1.unlockedFacts.size)
        assertEquals("f1", atEp1.unlockedFacts.first().factId)

        val atEp5 = service.lookupByEntityId("wn", "nastenka", "Nastenka", maxReadSeq = 75)
        assertEquals(2, atEp5!!.unlockedFacts.size)

        // Unknown name returns null so caller falls back to FR-009 PriorMentionService
        assertNull(service.lookupByQuery("wn", "UnknownStranger", maxReadSeq = 75))
    }
}
