package com.kuqforza.data.remote.xtream

import com.kuqforza.data.remote.dto.XtreamAuthResponse
import com.kuqforza.data.remote.dto.XtreamCategory
import com.kuqforza.data.remote.dto.XtreamEpgResponse
import com.kuqforza.data.remote.dto.XtreamSeriesInfoResponse
import com.kuqforza.data.remote.dto.XtreamSeriesItem
import com.kuqforza.data.remote.dto.XtreamStream
import com.kuqforza.data.remote.dto.XtreamVodInfoResponse

/**
 * Xtream Codes player API abstraction.
 */
interface XtreamApiService {
    suspend fun authenticate(endpoint: String): XtreamAuthResponse

    suspend fun getLiveCategories(endpoint: String): List<XtreamCategory>

    suspend fun getLiveStreams(endpoint: String): List<XtreamStream>

    suspend fun getVodCategories(endpoint: String): List<XtreamCategory>

    suspend fun getVodStreams(endpoint: String): List<XtreamStream>

    suspend fun getVodInfo(endpoint: String): XtreamVodInfoResponse

    suspend fun getSeriesCategories(endpoint: String): List<XtreamCategory>

    suspend fun getSeriesList(endpoint: String): List<XtreamSeriesItem>

    suspend fun getSeriesInfo(endpoint: String): XtreamSeriesInfoResponse

    suspend fun getShortEpg(endpoint: String): XtreamEpgResponse

    suspend fun getFullEpg(endpoint: String): XtreamEpgResponse
}
