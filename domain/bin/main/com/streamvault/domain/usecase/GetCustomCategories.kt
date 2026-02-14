package com.streamvault.domain.usecase

import com.streamvault.domain.model.Category
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCustomCategories @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) {
    operator fun invoke(): Flow<List<Category>> {
        return kotlinx.coroutines.flow.combine(
            favoriteRepository.getGroups(),
            favoriteRepository.getGlobalLiveFavoriteCount(),
            favoriteRepository.getGroupLiveFavoriteCounts()
        ) { groups, globalCount, groupCounts ->
            try {
                println("GetCustomCategories: Processing ${groups.size} groups. Global: $globalCount, Counts: ${groupCounts.size}")
                
                val categories = groups.map { group ->
                    Category(
                        id = -group.id, // Use negative ID for virtual groups
                        name = group.name,
                        type = ContentType.LIVE,
                        isVirtual = true,
                        count = groupCounts.getOrDefault(group.id, 0)
                    )
                }.toMutableList()

                // prepend "Favorites" as a special virtual category
                categories.add(0, Category(
                    id = -999L, // Special ID for "All Favorites"
                    name = "★ Favorites",
                    type = ContentType.LIVE,
                    isVirtual = true,
                    count = globalCount
                ))

                categories
            } catch (e: Exception) {
                println("GetCustomCategories: Error processing categories: ${e.message}")
                e.printStackTrace()
                emptyList<Category>()
            }
        }
    }
}
