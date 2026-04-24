package com.kuqforza.domain.repository

import com.kuqforza.domain.model.ExternalRatings
import com.kuqforza.domain.model.ExternalRatingsLookup
import com.kuqforza.domain.model.Result

interface ExternalRatingsRepository {
    suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings>
}