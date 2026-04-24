package com.kuqforza.data.repository

import com.kuqforza.domain.model.ExternalRatings
import com.kuqforza.domain.model.ExternalRatingsLookup
import com.kuqforza.domain.model.Result
import com.kuqforza.domain.repository.ExternalRatingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRatingsRepositoryImpl @Inject constructor() : ExternalRatingsRepository {

    override suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings> {
        return Result.success(ExternalRatings.unavailable())
    }
}