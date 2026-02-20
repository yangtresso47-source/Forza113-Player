package com.streamvault.data.sync

import android.util.Log
import com.streamvault.data.local.dao.CategoryDao
import com.streamvault.data.local.dao.ChannelDao
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.data.local.dao.SeriesDao
import com.streamvault.data.local.entity.CategoryEntity
import com.streamvault.data.mapper.*
import com.streamvault.data.parser.M3uParser
import com.streamvault.data.remote.xtream.XtreamApiService
import com.streamvault.data.remote.xtream.XtreamProvider
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Provider
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.SyncState
import com.streamvault.domain.repository.EpgRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"

/**
 * Centralised sync engine that owns the state machine and all data refresh logic.
 *
 * Formerly this logic was scattered inside `ProviderRepositoryImpl`. Moving it here
 * gives us:
 *  - A single source of truth for `SyncState`
 *  - A cancellable coroutine scope independent of ViewModel lifetimes
 *  - Stable, hash-based M3U entry IDs (no more index-based fragility)
 *  - Testable sync logic (can be unit-tested with fakes for all DAOs)
 */
@Singleton
class SyncManager @Inject constructor(
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val xtreamApiService: XtreamApiService,
    private val m3uParser: M3uParser,
    private val epgRepository: EpgRepository,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Perform a full data refresh for [providerId].
     * [onProgress] is an optional UI callback for fine-grained status strings.
     */
    suspend fun sync(
        providerId: Long,
        onProgress: ((String) -> Unit)? = null
    ): com.streamvault.domain.model.Result<Unit> {
        val providerEntity = providerDao.getById(providerId)
            ?: return com.streamvault.domain.model.Result.error("Provider $providerId not found")

        val provider = providerEntity.toDomain()
        _syncState.value = SyncState.Syncing("Starting…")

        return try {
            when (provider.type) {
                ProviderType.XTREAM_CODES -> syncXtream(provider, onProgress)
                ProviderType.M3U -> syncM3u(provider, onProgress)
            }
            providerDao.updateSyncTime(providerId, System.currentTimeMillis())
            _syncState.value = SyncState.Success()
            com.streamvault.domain.model.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for provider $providerId: ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Unknown error", e)
            com.streamvault.domain.model.Result.error(e.message ?: "Sync failed", e)
        }
    }

    /** Fire-and-forget variant — launches on the internal scope so callers don't need to await. */
    fun syncAsync(providerId: Long, onProgress: ((String) -> Unit)? = null) {
        scope.launch { sync(providerId, onProgress) }
    }

    fun resetState() {
        _syncState.value = SyncState.Idle
    }

    // ── Xtream sync ─────────────────────────────────────────────────

    private suspend fun syncXtream(provider: Provider, onProgress: ((String) -> Unit)?) {
        progress(onProgress, "Connecting to server…")
        val api = XtreamProvider(
            providerId = provider.id,
            api = xtreamApiService,
            serverUrl = provider.serverUrl,
            username = provider.username,
            password = provider.password
        )

        // Live
        progress(onProgress, "Downloading Live TV…")
        api.getLiveCategories().getOrThrow("Live categories").let { cats ->
            Log.d(TAG, "Saving ${cats.size} live categories")
            categoryDao.replaceAll(provider.id, "LIVE", cats.map { it.toEntity(provider.id) })
        }
        api.getLiveStreams().getOrThrow("Live streams").let { channels ->
            Log.d(TAG, "Saving ${channels.size} channels")
            channelDao.replaceAll(provider.id, channels.map { it.toEntity() })
        }

        // VOD
        progress(onProgress, "Downloading Movies…")
        api.getVodCategories().getOrNull()?.let { cats ->
            Log.d(TAG, "Saving ${cats.size} VOD categories")
            categoryDao.replaceAll(provider.id, "MOVIE", cats.map { it.toEntity(provider.id) })
        }
        api.getVodStreams().getOrNull()?.let { movies ->
            Log.d(TAG, "Saving ${movies.size} movies")
            movieDao.replaceAll(provider.id, movies.map { it.toEntity() })
        }

        // Series
        progress(onProgress, "Downloading Series…")
        api.getSeriesCategories().getOrNull()?.let { cats ->
            Log.d(TAG, "Saving ${cats.size} series categories")
            categoryDao.replaceAll(provider.id, "SERIES", cats.map { it.toEntity(provider.id) })
        }
        api.getSeriesList().getOrNull()?.let { seriesList ->
            Log.d(TAG, "Saving ${seriesList.size} series")
            seriesDao.replaceAll(provider.id, seriesList.map { it.toEntity() })
        }

        // EPG
        try {
            progress(onProgress, "Downloading EPG…")
            val base = provider.serverUrl.trimEnd('/')
            val xmltvUrl = "$base/xmltv.php?username=${provider.username}&password=${provider.password}"
            epgRepository.refreshEpg(provider.id, xmltvUrl)
        } catch (e: Exception) {
            Log.e(TAG, "EPG sync failed (non-fatal): ${e.message}")
        }
    }

    // ── M3U sync ────────────────────────────────────────────────────

    private suspend fun syncM3u(provider: Provider, onProgress: ((String) -> Unit)?) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting M3U refresh for ${provider.name}")
            progress(onProgress, "Downloading Playlist…")
            val url = provider.m3uUrl.ifBlank { provider.serverUrl }
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()

            if (!response.isSuccessful) {
                throw Exception("Failed to download M3U: HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Empty M3U response")

            progress(onProgress, "Parsing Playlist…")
            val entries = body.byteStream().use { m3uParser.parse(it) }
            Log.d(TAG, "Parsed ${entries.size} M3U entries")

            val liveEntries = entries.filter { !isVodEntry(it) }
            val vodEntries = entries.filter { isVodEntry(it) }

            // ── Categories ──────────────────────────────────────
            val liveGroups = liveEntries.map { it.groupTitle }.distinct()
            val vodGroups = vodEntries.map { it.groupTitle }.distinct()

            val liveCategories = liveGroups.mapIndexed { i, name ->
                CategoryEntity(categoryId = (i + 1).toLong(), name = name,
                    parentId = 0, type = "LIVE", providerId = provider.id)
            }
            val vodCategories = vodGroups.mapIndexed { i, name ->
                CategoryEntity(categoryId = (i + 10_000).toLong(), name = name,
                    parentId = 0, type = "MOVIE", providerId = provider.id)
            }

            progress(onProgress, "Saving Channels…")
            categoryDao.replaceAll(provider.id, "LIVE", liveCategories)
            categoryDao.replaceAll(provider.id, "MOVIE", vodCategories)

            val liveCategoryMap = liveGroups.withIndex().associate { (i, n) -> n to (i + 1).toLong() }
            val vodCategoryMap  = vodGroups.withIndex().associate { (i, n) -> n to (i + 10_000).toLong() }

            // ── Channels with stable hash IDs ───────────────────
            val channels = liveEntries.map { entry ->
                Channel(
                    id = stableId(provider.id, entry.tvgId, entry.url),
                    name = entry.name,
                    logoUrl = entry.tvgLogo,
                    groupTitle = entry.groupTitle,
                    categoryId = liveCategoryMap[entry.groupTitle],
                    categoryName = entry.groupTitle,
                    epgChannelId = entry.tvgId ?: entry.tvgName,
                    number = entry.tvgChno ?: 0,
                    streamUrl = entry.url,
                    catchUpSupported = entry.catchUp != null,
                    providerId = provider.id
                ).toEntity()
            }
            Log.d(TAG, "Saving ${channels.size} channels")
            channelDao.replaceAll(provider.id, channels)

            // ── Movies with stable hash IDs ─────────────────────
            progress(onProgress, "Saving Movies…")
            val movies = vodEntries.map { entry ->
                Movie(
                    id = stableId(provider.id, entry.tvgId, entry.url),
                    name = entry.name,
                    posterUrl = entry.tvgLogo,
                    categoryId = vodCategoryMap[entry.groupTitle],
                    categoryName = entry.groupTitle,
                    streamUrl = entry.url,
                    providerId = provider.id
                ).toEntity()
            }
            Log.d(TAG, "Saving ${movies.size} movies")
            movieDao.replaceAll(provider.id, movies)
            Log.d(TAG, "M3U refresh complete")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Generate a stable, collision-resistant ID for an M3U entry.
     *
     * Priority:
     *  1. tvg-id  (explicit EPG key — most stable)
     *  2. SHA-256(providerId + streamUrl)  (URL changes invalidate the record — acceptable)
     *
     * This replaces the previous `index.toLong() + offset` scheme which caused
     * data corruption whenever the playlist order changed.
     */
    private fun stableId(providerId: Long, tvgId: String?, url: String): Long {
        if (!tvgId.isNullOrBlank()) {
            // Hash the tvgId to fit in a Long
            return hashToLong("$providerId:tvg:$tvgId")
        }
        return hashToLong("$providerId:url:$url")
    }

    private fun hashToLong(input: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        // Take first 8 bytes and fold into a Long (masks sign bit to keep positive)
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (digest[i].toLong() and 0xFF)
        }
        return result and Long.MAX_VALUE // ensure positive
    }

    /**
     * Heuristic to distinguish VOD (movie) entries from live streams in M3U playlists.
     * Consolidated here as the single source of truth (previously duplicated).
     */
    internal fun isVodEntry(entry: M3uParser.M3uEntry): Boolean {
        val url = entry.url.lowercase()
        val group = entry.groupTitle.lowercase()
        return url.endsWith(".mp4") ||
                url.endsWith(".mkv") ||
                url.endsWith(".avi") ||
                url.contains("/movie/") ||
                group.contains("movie") ||
                group.contains("vod") ||
                group.contains("film")
    }

    private fun progress(callback: ((String) -> Unit)?, message: String) {
        _syncState.value = SyncState.Syncing(message)
        callback?.invoke(message)
    }

    // Extension to convert Result<T> to T-or-throw for mandatory resources
    private fun <T> com.streamvault.domain.model.Result<T>.getOrThrow(resourceName: String): T {
        return when (this) {
            is com.streamvault.domain.model.Result.Success -> data
            is com.streamvault.domain.model.Result.Error ->
                throw Exception("Failed to fetch $resourceName: $message")
            is com.streamvault.domain.model.Result.Loading ->
                throw Exception("Unexpected loading state for $resourceName")
        }
    }
}
