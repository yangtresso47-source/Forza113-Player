package com.streamvault.data.local.dao

import androidx.room.*
import com.streamvault.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY created_at DESC")
    fun getAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE is_active = 1 LIMIT 1")
    fun getActive(): Flow<ProviderEntity?>

    @Query("SELECT * FROM providers WHERE server_url = :serverUrl AND username = :username")
    suspend fun getByUrlAndUser(serverUrl: String, username: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE providers SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE providers SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("UPDATE providers SET last_synced_at = :timestamp WHERE id = :id")
    suspend fun updateSyncTime(id: Long, timestamp: Long)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE provider_id = :providerId ORDER BY number ASC")
    fun getByProvider(providerId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE provider_id = :providerId AND category_id = :categoryId ORDER BY number ASC")
    fun getByCategory(providerId: Long, categoryId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE provider_id = :providerId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(providerId: Long, query: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE provider_id = :providerId")
    suspend fun deleteByProvider(providerId: Long)

    @Transaction
    suspend fun replaceAll(providerId: Long, channels: List<ChannelEntity>) {
        deleteByProvider(providerId)
        insertAll(channels)
    }

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): Flow<List<ChannelEntity>>

    @Query("SELECT category_id, COUNT(*) as item_count FROM channels WHERE provider_id = :providerId AND category_id IS NOT NULL GROUP BY category_id")
    fun getCategoryCounts(providerId: Long): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM channels WHERE provider_id = :providerId")
    fun getCount(providerId: Long): Flow<Int>
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE provider_id = :providerId ORDER BY name ASC")
    fun getByProvider(providerId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE provider_id = :providerId AND category_id = :categoryId ORDER BY name ASC")
    fun getByCategory(providerId: Long, categoryId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE provider_id = :providerId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(providerId: Long, query: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE provider_id = :providerId AND stream_id = :streamId")
    suspend fun getByStreamId(providerId: Long, streamId: Long): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("UPDATE movies SET watch_progress = :progress, last_watched_at = :timestamp WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, progress: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM movies WHERE provider_id = :providerId")
    suspend fun deleteByProvider(providerId: Long)

    @Transaction
    suspend fun replaceAll(providerId: Long, movies: List<MovieEntity>) {
        deleteByProvider(providerId)
        insertAll(movies)
    }

    @Query("SELECT category_id, COUNT(*) as item_count FROM movies WHERE provider_id = :providerId AND category_id IS NOT NULL GROUP BY category_id")
    fun getCategoryCounts(providerId: Long): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM movies WHERE provider_id = :providerId")
    fun getCount(providerId: Long): Flow<Int>
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE provider_id = :providerId ORDER BY name ASC")
    fun getByProvider(providerId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE provider_id = :providerId AND category_id = :categoryId ORDER BY name ASC")
    fun getByCategory(providerId: Long, categoryId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE provider_id = :providerId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(providerId: Long, query: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: Long): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE provider_id = :providerId")
    suspend fun deleteByProvider(providerId: Long)

    @Transaction
    suspend fun replaceAll(providerId: Long, series: List<SeriesEntity>) {
        deleteByProvider(providerId)
        insertAll(series)
    }

    @Query("SELECT category_id, COUNT(*) as item_count FROM series WHERE provider_id = :providerId AND category_id IS NOT NULL GROUP BY category_id")
    fun getCategoryCounts(providerId: Long): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM series WHERE provider_id = :providerId")
    fun getCount(providerId: Long): Flow<Int>
}


@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE series_id = :seriesId ORDER BY season_number ASC, episode_number ASC")
    fun getBySeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET watch_progress = :progress, last_watched_at = :timestamp WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, progress: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM episodes WHERE series_id = :seriesId")
    suspend fun deleteBySeries(seriesId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE provider_id = :providerId AND type = :type ORDER BY name ASC")
    fun getByProviderAndType(providerId: Long, type: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE provider_id = :providerId AND type = :type")
    suspend fun deleteByProviderAndType(providerId: Long, type: String)

    @Transaction
    suspend fun replaceAll(providerId: Long, type: String, categories: List<CategoryEntity>) {
        deleteByProviderAndType(providerId, type)
        insertAll(categories)
    }
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs WHERE channel_id = :channelId AND start_time >= :startTime AND end_time <= :endTime ORDER BY start_time ASC")
    fun getForChannel(channelId: String, startTime: Long, endTime: Long): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE channel_id = :channelId AND start_time <= :now AND end_time > :now LIMIT 1")
    fun getNowPlaying(channelId: String, now: Long = System.currentTimeMillis()): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE channel_id IN (:channelIds) AND start_time <= :now AND end_time > :now")
    fun getNowPlayingForChannels(channelIds: List<String>, now: Long = System.currentTimeMillis()): Flow<List<ProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE end_time < :beforeTime")
    suspend fun deleteOld(beforeTime: Long)

    @Query("DELETE FROM programs WHERE channel_id = :channelId")
    suspend fun deleteForChannel(channelId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE group_id IS NULL ORDER BY position ASC")
    fun getAllGlobal(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE content_type = :contentType AND group_id IS NULL ORDER BY position ASC")
    fun getGlobalByType(contentType: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE group_id = :groupId ORDER BY position ASC")
    fun getByGroup(groupId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE content_id = :contentId AND content_type = :contentType AND (:groupId IS NULL AND group_id IS NULL OR group_id = :groupId) LIMIT 1")
    suspend fun get(contentId: Long, contentType: String, groupId: Long?): FavoriteEntity?

    @Query("SELECT COUNT(*) FROM favorites WHERE group_id IS NULL AND content_type = 'LIVE'")
    fun getGlobalLiveCount(): Flow<Int>

    @Query("SELECT group_id as category_id, COUNT(*) as item_count FROM favorites WHERE group_id IS NOT NULL AND content_type = 'LIVE' GROUP BY group_id")
    fun getGroupLiveCounts(): Flow<List<CategoryCount>>

    @Query("SELECT group_id FROM favorites WHERE content_id = :contentId AND content_type = :contentType AND group_id IS NOT NULL")
    suspend fun getGroupMemberships(contentId: Long, contentType: String): List<Long>

    @Query("SELECT MAX(position) FROM favorites WHERE (:groupId IS NULL AND group_id IS NULL OR group_id = :groupId)")
    suspend fun getMaxPosition(groupId: Long?): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Update
    suspend fun updateAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE content_id = :contentId AND content_type = :contentType AND (:groupId IS NULL AND group_id IS NULL OR group_id = :groupId)")
    suspend fun delete(contentId: Long, contentType: String, groupId: Long?)

    @Query("UPDATE favorites SET group_id = :groupId WHERE id = :favoriteId")
    suspend fun updateGroup(favoriteId: Long, groupId: Long?)
}

@Dao
interface VirtualGroupDao {
    @Query("SELECT * FROM virtual_groups ORDER BY position ASC")
    fun getAll(): Flow<List<VirtualGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: VirtualGroupEntity): Long

    @Query("UPDATE virtual_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM virtual_groups WHERE id = :id")
    suspend fun delete(id: Long)
}
