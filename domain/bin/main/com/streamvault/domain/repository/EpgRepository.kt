package com.streamvault.domain.repository

import com.streamvault.domain.model.Program
import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    fun getProgramsForChannel(channelId: String, startTime: Long, endTime: Long): Flow<List<Program>>
    fun getNowPlaying(channelId: String): Flow<Program?>
    fun getNowPlayingForChannels(channelIds: List<String>): Flow<List<Program>>
    fun getNowAndNext(channelId: String): Flow<Pair<Program?, Program?>>
    suspend fun refreshEpg(providerId: Long, epgUrl: String): Result<Unit>
    suspend fun clearOldPrograms(beforeTime: Long)
}
