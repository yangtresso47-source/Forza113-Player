package com.streamvault.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalControlManager @Inject constructor() {
    private val _unlockedCategoriesByProvider = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val unlockedCategoriesByProvider: StateFlow<Map<Long, Set<Long>>> =
        _unlockedCategoriesByProvider.asStateFlow()

    fun unlockedCategoriesForProvider(providerId: Long) =
        unlockedCategoriesByProvider.map { it[providerId] ?: emptySet() }

    fun unlockCategory(providerId: Long, categoryId: Long) {
        _unlockedCategoriesByProvider.update { current ->
            val providerSet = (current[providerId] ?: emptySet()) + categoryId
            current + (providerId to providerSet)
        }
    }

    fun isCategoryUnlocked(providerId: Long, categoryId: Long): Boolean {
        return _unlockedCategoriesByProvider.value[providerId]?.contains(categoryId) == true
    }

    fun clearUnlockedCategories(providerId: Long? = null) {
        if (providerId == null) {
            _unlockedCategoriesByProvider.value = emptyMap()
            return
        }

        _unlockedCategoriesByProvider.update { current ->
            current - providerId
        }
    }
}
