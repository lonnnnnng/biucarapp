package com.lonnnnnng.biucar.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.lonnnnnng.biucar.data.model.Creator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "selected_creators")
data class SelectedCreatorEntity(
    @PrimaryKey val mid: Long,
    val name: String,
    val faceUrl: String,
    val position: Int,
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaId: String,
    val bvid: String,
    val cid: Long,
    val title: String,
    val pageTitle: String?,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val playedAtEpochMs: Long,
    val playCount: Int,
    val localFilePath: String?,
    val cacheState: String,
)

enum class AudioCacheState { NONE, CACHING, READY, FAILED }

@Dao
interface SelectedCreatorDao {
    @Query("SELECT * FROM selected_creators ORDER BY position")
    fun observeAll(): Flow<List<SelectedCreatorEntity>>

    @Query("DELETE FROM selected_creators")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SelectedCreatorEntity>)

    @Transaction
    suspend fun replaceAll(items: List<SelectedCreatorEntity>) {
        deleteAll()
        insertAll(items)
    }
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun find(mediaId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaybackHistoryEntity)

    @Query("UPDATE playback_history SET lastPositionMs = :positionMs, durationMs = :durationMs WHERE mediaId = :mediaId")
    suspend fun updateProgress(mediaId: String, positionMs: Long, durationMs: Long)

    @Query("UPDATE playback_history SET localFilePath = :path, cacheState = :state WHERE mediaId = :mediaId")
    suspend fun updateCache(mediaId: String, path: String?, state: String)

    @Query("UPDATE playback_history SET cacheState = :state WHERE mediaId = :mediaId")
    suspend fun updateCacheState(mediaId: String, state: String)

    @Query("SELECT * FROM playback_history WHERE cacheState = 'READY' AND localFilePath IS NOT NULL ORDER BY playedAtEpochMs ASC")
    suspend fun oldestReadyCaches(): List<PlaybackHistoryEntity>

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}

@Database(
    entities = [SelectedCreatorEntity::class, PlaybackHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CarDatabase : RoomDatabase() {
    abstract fun selectedCreatorDao(): SelectedCreatorDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}

class CreatorSelectionRepository(private val dao: SelectedCreatorDao) {
    val selected: Flow<List<Creator>> = dao.observeAll().map { items ->
        items.map { Creator(it.mid, it.name, it.faceUrl) }
    }

    suspend fun replaceAll(creators: List<Creator>) {
        dao.replaceAll(
            creators.distinctBy(Creator::mid).mapIndexed { index, creator ->
                SelectedCreatorEntity(creator.mid, creator.name, creator.faceUrl, index)
            },
        )
    }
}

class PlaybackHistoryRepository(
    private val dao: PlaybackHistoryDao,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    val recent: Flow<List<PlaybackHistoryEntity>> = dao.observeRecent()

    suspend fun recordStarted(
        mediaId: String,
        bvid: String,
        cid: Long,
        title: String,
        pageTitle: String?,
        artist: String,
        artworkUrl: String,
        streamUrl: String,
    ) {
        val existing = dao.find(mediaId)
        dao.upsert(
            PlaybackHistoryEntity(
                mediaId = mediaId,
                bvid = bvid,
                cid = cid,
                title = title,
                pageTitle = pageTitle,
                artist = artist,
                artworkUrl = artworkUrl,
                streamUrl = streamUrl,
                lastPositionMs = existing?.lastPositionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                playedAtEpochMs = nowEpochMs(),
                playCount = (existing?.playCount ?: 0) + 1,
                localFilePath = existing?.localFilePath,
                cacheState = existing?.cacheState ?: AudioCacheState.NONE.name,
            ),
        )
    }

    suspend fun find(mediaId: String): PlaybackHistoryEntity? = dao.find(mediaId)
    suspend fun updateProgress(mediaId: String, positionMs: Long, durationMs: Long) =
        dao.updateProgress(mediaId, positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L))

    suspend fun markCaching(mediaId: String) = dao.updateCacheState(mediaId, AudioCacheState.CACHING.name)
    suspend fun markReady(mediaId: String, path: String) = dao.updateCache(mediaId, path, AudioCacheState.READY.name)
    suspend fun markFailed(mediaId: String) = dao.updateCacheState(mediaId, AudioCacheState.FAILED.name)
    suspend fun clearCache(mediaId: String) = dao.updateCache(mediaId, null, AudioCacheState.NONE.name)
    suspend fun readyCaches(): List<PlaybackHistoryEntity> = dao.oldestReadyCaches()
    suspend fun clear() = dao.clear()
}
