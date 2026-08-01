package com.builtdifferent.audio8d.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {

    @Query("SELECT * FROM conversions ORDER BY dateConvertedEpochMs DESC")
    fun observeAll(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE status = 'COMPLETED' ORDER BY dateConvertedEpochMs DESC")
    fun observeCompleted(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE isFavorite = 1 ORDER BY dateConvertedEpochMs DESC")
    fun observeFavorites(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversions WHERE originalUri = :originalUri LIMIT 1")
    suspend fun findByOriginalUri(originalUri: String): ConversionEntity?

    @Query("SELECT * FROM conversions WHERE id = :id")
    suspend fun findById(id: Long): ConversionEntity?

    @Query("SELECT * FROM conversions WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<ConversionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversionEntity): Long

    @Update
    suspend fun update(entity: ConversionEntity)

    @Delete
    suspend fun delete(entity: ConversionEntity)

    @Query("SELECT SUM(fileSizeBytes) FROM conversions WHERE status = 'COMPLETED'")
    fun observeTotalStorageBytes(): Flow<Long?>
}
