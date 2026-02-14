package com.streamvault.data.repository

import com.streamvault.data.local.dao.*
import com.streamvault.data.local.entity.CategoryEntity
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.data.mapper.*
import com.streamvault.data.parser.M3uParser
import com.streamvault.data.remote.xtream.XtreamApiService
import com.streamvault.data.remote.xtream.XtreamProvider
import com.streamvault.domain.model.*
import com.streamvault.domain.provider.IptvProvider
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val xtreamApiService: XtreamApiService,
    private val m3uParser: M3uParser,
    private val epgRepository: EpgRepository,
    private val okHttpClient: OkHttpClient
) : ProviderRepository {

    override fun getProviders(): Flow<List<Provider>> =
        providerDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveProvider(): Flow<Provider?> =
        providerDao.getActive().map { it?.toDomain() }

    override suspend fun getProvider(id: Long): Provider? =
        providerDao.getById(id)?.toDomain()

    override suspend fun addProvider(provider: Provider): Result<Long> = try {
        val id = providerDao.insert(provider.toEntity())
        Result.success(id)
    } catch (e: Exception) {
        Result.error("Failed to add provider: ${e.message}", e)
    }

    override suspend fun updateProvider(provider: Provider): Result<Unit> = try {
        providerDao.update(provider.toEntity())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to update provider: ${e.message}", e)
    }

    override suspend fun deleteProvider(id: Long): Result<Unit> = try {
        channelDao.deleteByProvider(id)
        movieDao.deleteByProvider(id)
        seriesDao.deleteByProvider(id)
        categoryDao.deleteByProviderAndType(id, "LIVE")
        categoryDao.deleteByProviderAndType(id, "MOVIE")
        categoryDao.deleteByProviderAndType(id, "SERIES")
        providerDao.delete(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to delete provider: ${e.message}", e)
    }

    override suspend fun setActiveProvider(id: Long): Result<Unit> = try {
        providerDao.deactivateAll()
        providerDao.activate(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to set active provider: ${e.message}", e)
    }

    override suspend fun loginXtream(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        onProgress: ((String) -> Unit)?
    ): Result<Provider> {
        onProgress?.invoke("Authenticating...")
        val provider = createXtreamProvider(0, serverUrl, username, password)
        return when (val authResult = provider.authenticate()) {
            is Result.Success -> {
                // Check if provider already exists
                val existingProvider = providerDao.getByUrlAndUser(serverUrl, username)
                
                val providerData = if (existingProvider != null) {
                    onProgress?.invoke("Updating existing provider...")
                    // Update existing provider details
                    val updated = existingProvider.copy(
                        name = name.ifBlank { existingProvider.name },
                        password = password, // Update password if changed
                        isActive = true,
                        lastSyncedAt = 0 // Reset sync time to force update? Or keep?
                    )
                    providerDao.update(updated)
                    updated.toDomain()
                } else {
                    // Use the provided name, or fallback to server's name if empty
                    val newData = authResult.data.copy(
                        name = name.ifBlank { authResult.data.name }
                    )
                    val id = providerDao.insert(newData.toEntity())
                    newData.copy(id = id)
                }
                
                val id = providerData.id
                

                try {
                    providerDao.deactivateAll()
                    providerDao.activate(id)
                    
                    val refreshResult = refreshProviderData(id, onProgress)
                    
                    if (refreshResult is Result.Success) {
                        Result.success(providerData)
                    } else if (refreshResult is Result.Error) {
                        // Allow saving even if sync fails
                        android.util.Log.e("ProviderRepo", "Initial Xtream sync failed: ${refreshResult.message}")
                        Result.success(providerData)
                    } else {
                        Result.success(providerData)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProviderRepo", "Xtream Sync crashed: ${e.message}")
                    Result.success(providerData)
                }
            }
            is Result.Error -> Result.error(authResult.message, authResult.exception)
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    override suspend fun validateM3u(url: String, name: String, onProgress: ((String) -> Unit)?): Result<Provider> = try {
        onProgress?.invoke("Validating playlist URL...")
        val providerName = name.ifBlank { 
            url.substringAfterLast("/").substringBefore("?").ifBlank { "M3U Playlist" } 
        }
        
        // Check for existing
        val existingProvider = providerDao.getByUrlAndUser(url, "")
        
        val providerData = if (existingProvider != null) {
            val updated = existingProvider.copy(
                name = if (name.isNotBlank()) name else existingProvider.name,
                isActive = true
            )
            providerDao.update(updated)
            updated.toDomain()
        } else {
            val provider = Provider(
                name = providerName,
                type = ProviderType.M3U,
                serverUrl = url,
                m3uUrl = url,
                status = ProviderStatus.ACTIVE
            )
            val id = providerDao.insert(provider.toEntity())
            provider.copy(id = id)
        }
        
        val id = providerData.id

        try {
            providerDao.deactivateAll()
            providerDao.activate(id)
            
            // Use the public refresh method to ensure consistency with manual refresh
            val refreshResult = refreshProviderData(id, onProgress)
            
            if (refreshResult is Result.Success) {
                Result.success(providerData)
            } else if (refreshResult is Result.Error) {
                // Return success but with the provider data, so the UI considers it "added"
                // The sync error will be logged and the user might see empty content, but they can edit it later.
                // We could potentially wrap this in a custom result or just rely on the fact that 
                // providerData is returned. 
                // Ideally we'd return Result.Success(providerData) but maybe trigger a toast.
                // For now, let's treat it as success so navigation proceeds, but log the error.
                android.util.Log.e("ProviderRepo", "Initial M3U sync failed: ${refreshResult.message}")
                Result.success(providerData)
            } else {
                 Result.success(providerData)
            }
        } catch (e: Exception) {
            // Even if exception occurs (unlikely due to refreshProviderData having its own try/catch),
            // we keep the provider so user can edit it.
            android.util.Log.e("ProviderRepo", "M3U Validation/Sync crashed: ${e.message}")
            Result.success(providerData) 
        }
    } catch (e: Exception) {
        Result.error("Failed to add M3U provider: ${e.message}", e)
    }

    override suspend fun refreshProviderData(providerId: Long, onProgress: ((String) -> Unit)?): Result<Unit> = try {
        val providerEntity = providerDao.getById(providerId)
            ?: return Result.error("Provider not found")
        val provider = providerEntity.toDomain()

        when (provider.type) {
            ProviderType.XTREAM_CODES -> refreshXtreamData(provider, onProgress)
            ProviderType.M3U -> refreshM3uData(provider, onProgress)
        }

        providerDao.updateSyncTime(providerId, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to refresh provider data: ${e.message}", e)
    }

    private suspend fun refreshXtreamData(provider: Provider, onProgress: ((String) -> Unit)? = null) {
        onProgress?.invoke("Connecting to server...")
        val xtreamProvider = createXtreamProvider(
            provider.id, provider.serverUrl, provider.username, provider.password
        )

        // Refresh live categories & channels
        onProgress?.invoke("Downloading Live TV...")
        val liveCatsResult = xtreamProvider.getLiveCategories()
        if (liveCatsResult is Result.Error) {
             android.util.Log.e("ProviderRepo", "Failed to fetch Live Categories: ${liveCatsResult.message}")
             throw Exception("Failed to fetch Live Categories: ${liveCatsResult.message}")
        }
        
        liveCatsResult.getOrNull()?.let { categories ->
            android.util.Log.d("ProviderRepo", "Saving ${categories.size} live categories")
            categoryDao.replaceAll(
                provider.id, "LIVE",
                categories.map { it.toEntity(provider.id) }
            )
        }
        
        val liveStreamsResult = xtreamProvider.getLiveStreams()
        if (liveStreamsResult is Result.Error) {
            android.util.Log.e("ProviderRepo", "Failed to fetch Live Streams: ${liveStreamsResult.message}")
            throw Exception("Failed to fetch Live Streams: ${liveStreamsResult.message}")
        }
        
        liveStreamsResult.getOrNull()?.let { channels ->
            android.util.Log.d("ProviderRepo", "Saving ${channels.size} live channels")
            channelDao.replaceAll(provider.id, channels.map { it.toEntity() })
        }

        // Refresh VOD categories & movies
        onProgress?.invoke("Downloading Movies...")
        val vodCatsResult = xtreamProvider.getVodCategories()
        
        vodCatsResult.getOrNull()?.let { categories ->
             android.util.Log.d("ProviderRepo", "Saving ${categories.size} VOD categories")
            categoryDao.replaceAll(
                provider.id, "MOVIE",
                categories.map { it.toEntity(provider.id) }
            )
        }
        
        xtreamProvider.getVodStreams().getOrNull()?.let { movies ->
             android.util.Log.d("ProviderRepo", "Saving ${movies.size} movies")
            movieDao.replaceAll(provider.id, movies.map { it.toEntity() })
        }

        // Refresh series categories & series
        onProgress?.invoke("Downloading Series...")
        xtreamProvider.getSeriesCategories().getOrNull()?.let { categories ->
            android.util.Log.d("ProviderRepo", "Saving ${categories.size} series categories")
            categoryDao.replaceAll(
                provider.id, "SERIES",
                categories.map { it.toEntity(provider.id) }
            )
        }
        xtreamProvider.getSeriesList().getOrNull()?.let { seriesList ->
             android.util.Log.d("ProviderRepo", "Saving ${seriesList.size} series")
            seriesDao.replaceAll(provider.id, seriesList.map { it.toEntity() })
        }

        // Refresh EPG (XMLTV)
        try {
            onProgress?.invoke("Downloading EPG...")
            val serverUrl = provider.serverUrl.trimEnd('/')
            val xmltvUrl = "$serverUrl/xmltv.php?username=${provider.username}&password=${provider.password}"
            epgRepository.refreshEpg(provider.id, xmltvUrl)
        } catch (e: Exception) {
            e.printStackTrace()
             android.util.Log.e("ProviderRepo", "EPG Sync failed: ${e.message}")
        }
    }

    private suspend fun refreshM3uData(provider: Provider, onProgress: ((String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        android.util.Log.d("ProviderRepo", "Starting M3U refresh for ${provider.name}")
        onProgress?.invoke("Downloading Playlist...")
        val m3uUrl = provider.m3uUrl.ifBlank { provider.serverUrl }
        val request = Request.Builder().url(m3uUrl).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            android.util.Log.e("ProviderRepo", "Failed to download M3U: ${response.code}")
            throw Exception("Failed to download M3U: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty M3U response")

        onProgress?.invoke("Parsing Playlist...")
        val entries = body.byteStream().use { inputStream ->
            m3uParser.parse(inputStream)
        }
        android.util.Log.d("ProviderRepo", "Parsed ${entries.size} entries")

        // Separate live channels vs VOD (movies)
        val liveEntries = entries.filter { !isVodEntry(it) }
        val vodEntries = entries.filter { isVodEntry(it) }

        // Build categories from group titles
        val liveGroups = liveEntries.map { it.groupTitle }.distinct()
        val vodGroups = vodEntries.map { it.groupTitle }.distinct()

        // Insert live categories
        onProgress?.invoke("Saving Channels...")
        val liveCategories = liveGroups.mapIndexed { index, name ->
            CategoryEntity(
                categoryId = (index + 1).toLong(),
                name = name,
                parentId = 0,
                type = "LIVE",
                providerId = provider.id
            )
        }
        android.util.Log.d("ProviderRepo", "Saving ${liveCategories.size} live categories")
        categoryDao.replaceAll(provider.id, "LIVE", liveCategories)

        // Insert VOD categories
        onProgress?.invoke("Saving Movies...")
        val vodCategories = vodGroups.mapIndexed { index, name ->
            CategoryEntity(
                categoryId = (index + 10000).toLong(),
                name = name,
                parentId = 0,
                type = "MOVIE",
                providerId = provider.id
            )
        }
        android.util.Log.d("ProviderRepo", "Saving ${vodCategories.size} VOD categories")
        categoryDao.replaceAll(provider.id, "MOVIE", vodCategories)

        // Build category lookup maps
        val liveCategoryMap = liveGroups.withIndex().associate { (i, name) -> name to (i + 1).toLong() }
        val vodCategoryMap = vodGroups.withIndex().associate { (i, name) -> name to (i + 10000).toLong() }

        // Insert live channels
        val channels = liveEntries.mapIndexed { index, entry ->
            Channel(
                id = index.toLong() + 1,
                name = entry.name,
                logoUrl = entry.tvgLogo,
                groupTitle = entry.groupTitle,
                categoryId = liveCategoryMap[entry.groupTitle],
                categoryName = entry.groupTitle,
                epgChannelId = entry.tvgId ?: entry.tvgName,
                number = entry.tvgChno ?: (index + 1),
                streamUrl = entry.url,
                catchUpSupported = entry.catchUp != null,
                providerId = provider.id
            ).toEntity()
        }
        android.util.Log.d("ProviderRepo", "Saving ${channels.size} channels")
        channelDao.replaceAll(provider.id, channels)

        // Insert movies
        val movies = vodEntries.mapIndexed { index, entry ->
            Movie(
                id = index.toLong() + 100000,
                name = entry.name,
                posterUrl = entry.tvgLogo,
                categoryId = vodCategoryMap[entry.groupTitle],
                categoryName = entry.groupTitle,
                streamUrl = entry.url,
                providerId = provider.id
            ).toEntity()
        }
        android.util.Log.d("ProviderRepo", "Saving ${movies.size} movies")
        movieDao.replaceAll(provider.id, movies)
        
        android.util.Log.d("ProviderRepo", "M3U refresh complete")
    }

    private fun isVodEntry(entry: M3uParser.M3uEntry): Boolean {
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

    fun createXtreamProvider(
        providerId: Long,
        serverUrl: String,
        username: String,
        password: String
    ): IptvProvider = XtreamProvider(
        providerId = providerId,
        api = xtreamApiService,
        serverUrl = serverUrl,
        username = username,
        password = password
    )
}
