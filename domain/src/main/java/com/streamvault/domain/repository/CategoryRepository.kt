package com.kuqforza.domain.repository

import com.kuqforza.domain.model.Category
import com.kuqforza.domain.model.ContentType
import com.kuqforza.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategories(providerId: Long): Flow<List<Category>>
    suspend fun setCategoryProtection(
        providerId: Long,
        categoryId: Long,
        type: ContentType,
        isProtected: Boolean
    ): Result<Unit>
}
