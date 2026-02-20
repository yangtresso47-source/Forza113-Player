package com.streamvault.data.repository

import com.streamvault.data.local.dao.*
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.data.mapper.*
import com.streamvault.data.remote.xtream.XtreamApiService
import com.streamvault.data.remote.xtream.XtreamProvider
import com.streamvault.data.sync.SyncManager
import com.streamvault.domain.model.*
import com.streamvault.domain.provider.IptvProvider
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val syncManager: SyncManager
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
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> {
        onProgress?.invoke("Authenticating...")
        val provider = createXtreamProvider(0, serverUrl, username, password)
        return when (val authResult = provider.authenticate()) {
            is Result.Success -> {
                val existingProvider = if (id != null) {
                    providerDao.getById(id)
                } else {
                    providerDao.getByUrlAndUser(serverUrl, username)
                }

                val providerData = if (existingProvider != null) {
                    onProgress?.invoke("Updating existing provider...")
                    val updated = existingProvider.copy(
                        name = name.ifBlank { existingProvider.name },
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        isActive = true,
                        lastSyncedAt = 0
                    )
                    providerDao.update(updated)
                    updated.toDomain()
                } else {
                    val newData = authResult.data.copy(name = name.ifBlank { authResult.data.name })
                    val newId = providerDao.insert(newData.toEntity())
                    newData.copy(id = newId)
                }

                try {
                    providerDao.deactivateAll()
                    providerDao.activate(providerData.id)
                    // Delegate sync to SyncManager
                    syncManager.sync(providerData.id, onProgress)
                } catch (e: Exception) {
                    android.util.Log.e("ProviderRepo", "Initial Xtream sync failed: ${e.message}")
                }
                Result.success(providerData)
            }
            is Result.Error -> Result.error(authResult.message, authResult.exception)
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    override suspend fun validateM3u(
        url: String,
        name: String,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> = try {
        onProgress?.invoke("Validating playlist URL...")
        val providerName = name.ifBlank {
            url.substringAfterLast("/").substringBefore("?").ifBlank { "M3U Playlist" }
        }

        val existingProvider = if (id != null) {
            providerDao.getById(id)
        } else {
            providerDao.getByUrlAndUser(url, "")
        }

        val providerData = if (existingProvider != null) {
            val updated = existingProvider.copy(
                name = if (name.isNotBlank()) name else existingProvider.name,
                serverUrl = url,
                m3uUrl = url,
                isActive = true,
                lastSyncedAt = 0
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
            val newId = providerDao.insert(provider.toEntity())
            provider.copy(id = newId)
        }

        try {
            providerDao.deactivateAll()
            providerDao.activate(providerData.id)
            // Delegate sync to SyncManager
            syncManager.sync(providerData.id, onProgress)
        } catch (e: Exception) {
            android.util.Log.e("ProviderRepo", "Initial M3U sync failed: ${e.message}")
        }
        Result.success(providerData)
    } catch (e: Exception) {
        Result.error("Failed to add M3U provider: ${e.message}", e)
    }

    /**
     * Delegates to [SyncManager] — the single source of truth for the full sync pipeline.
     */
    override suspend fun refreshProviderData(
        providerId: Long,
        onProgress: ((String) -> Unit)?
    ): Result<Unit> = syncManager.sync(providerId, onProgress)

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
