package com.streamvault.domain.repository

import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Favorite
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.VirtualGroup
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavorites(contentType: ContentType? = null): Flow<List<Favorite>>
    fun getFavoritesByGroup(groupId: Long): Flow<List<Favorite>>
    fun getGroups(): Flow<List<VirtualGroup>>

    fun getGlobalLiveFavoriteCount(): Flow<Int>
    fun getGroupLiveFavoriteCounts(): Flow<Map<Long, Int>>

    suspend fun addFavorite(contentId: Long, contentType: ContentType, groupId: Long? = null): Result<Unit>
    suspend fun removeFavorite(contentId: Long, contentType: ContentType, groupId: Long? = null): Result<Unit>
    
    suspend fun reorderFavorites(favorites: List<Favorite>): Result<Unit>
    suspend fun isFavorite(contentId: Long, contentType: ContentType): Boolean
    
    suspend fun getGroupMemberships(contentId: Long, contentType: ContentType): List<Long>

    suspend fun createGroup(name: String, iconEmoji: String? = null): Result<VirtualGroup>
    suspend fun deleteGroup(groupId: Long): Result<Unit>
    suspend fun renameGroup(groupId: Long, newName: String): Result<Unit>
}
