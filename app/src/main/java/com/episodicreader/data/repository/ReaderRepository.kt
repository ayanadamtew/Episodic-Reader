package com.episodicreader.data.repository

import com.episodicreader.data.db.dao.EpisodeDao
import com.episodicreader.data.db.dao.ParagraphDao
import com.episodicreader.data.db.entity.EpisodeEntity
import com.episodicreader.data.db.entity.ParagraphEntity

class ReaderRepository(
    private val episodeDao: EpisodeDao,
    private val paragraphDao: ParagraphDao,
) {
    suspend fun getEpisode(episodeId: String): EpisodeEntity? = episodeDao.getById(episodeId)

    suspend fun getEpisodeParagraphs(episodeId: String): List<ParagraphEntity> =
        paragraphDao.getForEpisode(episodeId)
}

