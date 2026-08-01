package com.builtdifferent.audio8d.data.repository

import com.builtdifferent.audio8d.data.db.ConversionDao
import com.builtdifferent.audio8d.data.db.ConversionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for conversion records. The "already converted?" check
 * lives here so both the picker flow and the worker agree on the same rule:
 * a source is considered already-converted only if a COMPLETED row exists for
 * that exact originalUri AND the converted file still exists on disk.
 */
@Singleton
class ConversionRepository @Inject constructor(
    private val dao: ConversionDao
) {
    fun observeAll(): Flow<List<ConversionEntity>> = dao.observeAll()
    fun observeCompleted(): Flow<List<ConversionEntity>> = dao.observeCompleted()
    fun observeFavorites(): Flow<List<ConversionEntity>> = dao.observeFavorites()
    fun search(query: String): Flow<List<ConversionEntity>> = dao.search(query)
    fun observeTotalStorageBytes(): Flow<Long?> = dao.observeTotalStorageBytes()

    suspend fun findExistingConversion(originalUri: String): ConversionEntity? {
        val existing = dao.findByOriginalUri(originalUri) ?: return null
        if (existing.status != "COMPLETED") return null
        val path = existing.convertedFilePath ?: return null
        return if (java.io.File(path).exists()) existing else null
    }

    suspend fun upsert(entity: ConversionEntity): Long = dao.upsert(entity)
    suspend fun update(entity: ConversionEntity) = dao.update(entity)
    suspend fun delete(entity: ConversionEntity) = dao.delete(entity)
    suspend fun findById(id: Long): ConversionEntity? = dao.findById(id)
}
