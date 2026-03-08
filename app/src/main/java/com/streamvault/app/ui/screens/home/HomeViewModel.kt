package com.streamvault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.data.preferences.PreferencesRepository
import com.streamvault.domain.manager.ParentalControlManager
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Provider
import com.streamvault.domain.repository.CategoryRepository
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.FavoriteRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.usecase.GetCustomCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val preferencesRepository: PreferencesRepository,
    private val epgRepository: EpgRepository,
    private val getCustomCategories: GetCustomCategories,
    private val parentalControlManager: ParentalControlManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _localChannels = MutableStateFlow<List<Channel>>(emptyList())
    private var epgJob: Job? = null
    private var loadChannelsJob: Job? = null
    private var categoriesJob: Job? = null

    init {
        loadAllProviders()
        viewModelScope.launch {
            providerRepository.getActiveProvider()
                .filterNotNull()
                .collectLatest { provider ->
                    _uiState.update { it.copy(provider = provider) }
                    loadCategoriesAndChannels(provider.id)
                    preferencesRepository.setLastActiveProviderId(provider.id)
                }
        }

        // Observe channels, search query, and favorites to update UI
        viewModelScope.launch {
            combine(
                _localChannels,
                _uiState.map { it.channelSearchQuery }.distinctUntilChanged(),
                favoriteRepository.getFavorites(ContentType.LIVE)
            ) { channels, query, favorites ->
                Triple(channels, query, favorites)
            }.collectLatest { (channels, query, favorites) ->
                val favoriteIds = favorites.map { it.contentId }.toSet()

                val filtered = if (query.isBlank()) channels
                else channels.filter { it.name.contains(query, ignoreCase = true) }

                val markedChannels = filtered.map { channel ->
                    if (favoriteIds.contains(channel.id)) channel.copy(isFavorite = true)
                    else channel.copy(isFavorite = false)
                }

                _uiState.update { it.copy(filteredChannels = markedChannels, isLoading = false) }
                fetchEpgForChannels(markedChannels)
            }
        }

        viewModelScope.launch {
            preferencesRepository.parentalControlLevel.collectLatest { level ->
                _uiState.update { it.copy(parentalControlLevel = level) }
            }
        }
    }

    private fun loadAllProviders() {
        viewModelScope.launch {
            providerRepository.getProviders().collect { providers ->
                _uiState.update { it.copy(allProviders = providers) }
            }
        }
    }

    fun switchProvider(providerId: Long) {
        viewModelScope.launch {
            providerRepository.setActiveProvider(providerId)
        }
    }

    private fun loadCategoriesAndChannels(providerId: Long) {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            combine(
                channelRepository.getCategories(providerId),
                getCustomCategories(),
                preferencesRepository.defaultCategoryId
            ) { providerCats, customCats, defaultId ->
                Triple(customCats + providerCats, defaultId, Unit)
            }.collect { (categories, defaultId, _) ->
                _uiState.update { it.copy(categories = categories) }

                val currentSelected = _uiState.value.selectedCategory

                if (currentSelected == null && categories.isNotEmpty()) {
                    val defaultCat = defaultId?.let { id -> categories.find { it.id == id } }
                    val favoritesCat = categories.find { it.id == -999L }

                    if (defaultCat != null) selectCategory(defaultCat)
                    else if (favoritesCat != null) selectCategory(favoritesCat)
                    else selectCategory(categories.first())
                } else if (currentSelected != null) {
                    val reselectedCat = categories.find { it.id == currentSelected.id }

                    if (reselectedCat != null) {
                        if (reselectedCat != currentSelected) {
                            _uiState.update { it.copy(selectedCategory = reselectedCat) }
                        }
                        loadChannelsForCategory(reselectedCat)
                    } else {
                        val defaultCat = defaultId?.let { id -> categories.find { it.id == id } }
                        val favoritesCat = categories.find { it.id == -999L }

                        if (defaultCat != null) selectCategory(defaultCat)
                        else if (favoritesCat != null) selectCategory(favoritesCat)
                        else if (categories.isNotEmpty()) selectCategory(categories.first())
                    }
                }
            }
        }
    }

    fun selectCategory(category: Category) {
        if (_uiState.value.selectedCategory?.id == category.id) return
        parentalControlManager.clearUnlockedCategories()
        _uiState.update { it.copy(selectedCategory = category, isLoading = true) }
        loadChannelsForCategory(category)
    }

    private fun loadChannelsForCategory(category: Category) {
        val providerId = _uiState.value.provider?.id ?: return
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch {
            val channelsFlow = if (category.isVirtual) {
                if (category.id == -999L) {
                    favoriteRepository.getFavorites(ContentType.LIVE)
                        .map { favorites -> favorites.sortedBy { it.position }.map { it.contentId } }
                        .flatMapLatest { ids ->
                            if (ids.isEmpty()) flowOf(emptyList())
                            else channelRepository.getChannelsByIds(ids).map { unsorted ->
                                val map = unsorted.associateBy { it.id }
                                ids.mapNotNull { map[it] }
                            }
                        }
                } else {
                    val groupId = -category.id
                    favoriteRepository.getFavoritesByGroup(groupId)
                        .map { favorites -> favorites.sortedBy { it.position }.map { it.contentId } }
                        .flatMapLatest { ids ->
                            if (ids.isEmpty()) flowOf(emptyList())
                            else channelRepository.getChannelsByIds(ids).map { unsorted ->
                                val map = unsorted.associateBy { it.id }
                                ids.mapNotNull { map[it] }
                            }
                        }
                }
            } else {
                channelRepository.getChannelsByCategory(providerId, category.id)
            }

            channelsFlow.collect { channels ->
                _uiState.update { it.copy(hasChannels = channels.isNotEmpty(), isLoading = false) } // Force isLoading = false here
                _localChannels.value = channels
            }
        }
    }

    fun updateCategorySearchQuery(query: String) {
        _uiState.update { it.copy(categorySearchQuery = query) }
    }

    fun updateChannelSearchQuery(query: String) {
        _uiState.update { it.copy(channelSearchQuery = query) }
    }

    private fun fetchEpgForChannels(channels: List<Channel>) {
        epgJob?.cancel()
        val epgIds = channels.mapNotNull { it.epgChannelId }.distinct()

        epgJob = viewModelScope.launch {
            val programMap = if (epgIds.isNotEmpty()) {
                epgRepository.getNowPlayingForChannels(epgIds).firstOrNull() ?: emptyMap()
            } else {
                emptyMap()
            }

            val enrichedChannels = channels.map { channel ->
                val programList = channel.epgChannelId?.let { programMap[it] }
                val program = programList?.firstOrNull()
                if (program != null) channel.copy(currentProgram = program) else channel
            }

            _uiState.update { it.copy(filteredChannels = enrichedChannels) }
        }
    }

    fun addFavorite(channel: Channel) {
        viewModelScope.launch {
            favoriteRepository.addFavorite(
                contentId = channel.id,
                contentType = ContentType.LIVE,
                groupId = null
            )
            onDismissDialog()
            _uiState.update { it.copy(userMessage = "Added ${channel.name} to Favorites") }
        }
    }

    fun removeFavorite(channel: Channel) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(
                contentId = channel.id,
                contentType = ContentType.LIVE,
                groupId = null
            )
            onDismissDialog()
            _uiState.update { it.copy(userMessage = "Removed ${channel.name} from Favorites") }
        }
    }

    fun createCustomGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.equals("Favorites", ignoreCase = true)) {
            _uiState.update { it.copy(userMessage = "Cannot create group named 'Favorites'") }
            return
        }
        if (_uiState.value.categories.any { it.name.equals(trimmed, ignoreCase = true) }) {
            _uiState.update { it.copy(userMessage = "Group '$trimmed' already exists") }
            return
        }

        viewModelScope.launch {
            favoriteRepository.createGroup(trimmed, contentType = ContentType.LIVE)
            _uiState.update { it.copy(userMessage = "Group '$trimmed' created") }
        }
    }

    fun requestDeleteGroup(category: Category) {
        if (!category.isVirtual || category.id == -999L) return
        _uiState.update { it.copy(showDeleteGroupDialog = true, groupToDelete = category) }
    }

    fun cancelDeleteGroup() {
        _uiState.update { it.copy(showDeleteGroupDialog = false, groupToDelete = null) }
    }

    fun confirmDeleteGroup() {
        val category = _uiState.value.groupToDelete ?: return
        viewModelScope.launch {
            favoriteRepository.deleteGroup(-category.id)
            _uiState.update {
                it.copy(
                    showDeleteGroupDialog = false,
                    groupToDelete = null,
                    userMessage = "Group '${category.name}' deleted"
                )
            }
        }
    }

    fun addToGroup(channel: Channel, category: Category) {
        if (!category.isVirtual || category.id == -999L) return
        viewModelScope.launch {
            val groupId = -category.id
            favoriteRepository.addFavorite(
                contentId = channel.id,
                contentType = ContentType.LIVE,
                groupId = groupId
            )
            onDismissDialog()
            _uiState.update { it.copy(userMessage = "Added ${channel.name} to ${category.name}") }
        }
    }

    fun removeFromGroup(channel: Channel, category: Category) {
        if (!category.isVirtual || category.id == -999L) return
        viewModelScope.launch {
            val groupId = -category.id
            favoriteRepository.removeFavorite(
                contentId = channel.id,
                contentType = ContentType.LIVE,
                groupId = groupId
            )
            onDismissDialog()
            _uiState.update { it.copy(userMessage = "Removed ${channel.name} from ${category.name}") }
        }
    }

    fun moveChannel(channel: Channel, direction: Int) {
        val currentCategory = _uiState.value.selectedCategory ?: return
        if (!currentCategory.isVirtual) return

        val currentList = _localChannels.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == channel.id }
        if (index == -1) return

        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= currentList.size) return

        java.util.Collections.swap(currentList, index, newIndex)
        _localChannels.value = currentList

        viewModelScope.launch {
            val groupId = if (currentCategory.id == -999L) null else -currentCategory.id

            val favoritesFlow = if (groupId == null) {
                favoriteRepository.getFavorites(ContentType.LIVE)
            } else {
                favoriteRepository.getFavoritesByGroup(groupId)
            }

            val favorites = favoritesFlow.first()
            val favoriteMap = favorites.associateBy { it.contentId }

            val reorderedFavorites = currentList.mapNotNull { ch ->
                favoriteMap[ch.id]
            }.mapIndexed { i, fav ->
                fav.copy(position = i)
            }

            favoriteRepository.reorderFavorites(reorderedFavorites)
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun refreshData() {
        val provider = _uiState.value.provider ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            providerRepository.refreshProviderData(provider.id)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onShowDialog(channel: Channel) {
        _uiState.update { it.copy(showDialog = true, selectedChannelForDialog = channel) }
        viewModelScope.launch {
            val memberships = favoriteRepository.getGroupMemberships(channel.id, ContentType.LIVE)
            _uiState.update { it.copy(dialogGroupMemberships = memberships) }
        }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(showDialog = false, selectedChannelForDialog = null) }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val storedPin = preferencesRepository.parentalPin.firstOrNull() ?: "0000"
        return pin == storedPin
    }

    fun unlockCategory(category: Category) {
        parentalControlManager.unlockCategory(category.id)
    }

    fun setDefaultCategory(category: Category) {
        viewModelScope.launch {
            preferencesRepository.setDefaultCategory(category.id)
            _uiState.update { it.copy(userMessage = "Set '${category.name}' as default") }
        }
    }

    fun toggleCategoryLock(category: Category) {
        viewModelScope.launch {
            val newStatus = !category.isUserProtected
            categoryRepository.setCategoryProtection(category.id, newStatus)
            val msg = if (newStatus) "Locked '${category.name}'" else "Unlocked '${category.name}'"
            _uiState.update { it.copy(userMessage = msg) }
        }
    }

    fun showCategoryOptions(category: Category) {
        _uiState.update { it.copy(selectedCategoryForOptions = category) }
    }

    fun dismissCategoryOptions() {
        _uiState.update { it.copy(selectedCategoryForOptions = null) }
    }

    fun enterChannelReorderMode(category: Category) {
        dismissCategoryOptions()
        _uiState.update { it.copy(isChannelReorderMode = true, reorderCategory = category) }
    }

    fun exitChannelReorderMode() {
        // Discard any unsaved sorting by restoring from the original local snapshot
        _uiState.update { it.copy(
            isChannelReorderMode = false, 
            reorderCategory = null,
            filteredChannels = _localChannels.value
        ) }
    }

    fun moveChannelUp(channel: Channel) {
        val state = _uiState.value
        val list = state.filteredChannels.toMutableList()
        val idx = list.indexOf(channel)
        if (idx > 0) {
            list.removeAt(idx)
            list.add(idx - 1, channel)
            _uiState.update { it.copy(filteredChannels = list) }
        }
    }

    fun moveChannelDown(channel: Channel) {
        val state = _uiState.value
        val list = state.filteredChannels.toMutableList()
        val idx = list.indexOf(channel)
        if (idx >= 0 && idx < list.size - 1) {
            list.removeAt(idx)
            list.add(idx + 1, channel)
            _uiState.update { it.copy(filteredChannels = list) }
        }
    }

    fun saveChannelReorder() {
        val state = _uiState.value
        val category = state.reorderCategory ?: return
        val currentList = state.filteredChannels

        // Exit reorder mode immediately for responsive UI
        _uiState.update { it.copy(isChannelReorderMode = false, reorderCategory = null) }
        
        // Optimistically update local channels to match the new order before DB flow catches up
        _localChannels.value = currentList

        viewModelScope.launch {
            try {
                // Map the virtual category ID back to the Favorite Group ID
                val groupId = if (category.id == -999L) null else -category.id

                val favoritesFlow = if (groupId == null) {
                    favoriteRepository.getFavorites(ContentType.LIVE)
                } else {
                    favoriteRepository.getFavoritesByGroup(groupId)
                }

                // Get current favorites
                val favorites = favoritesFlow.first()
                val favoriteMap = favorites.associateBy { it.contentId }

                // Map the sorted Channel list back to Favorite entities with new positions
                val reorderedFavorites = currentList.mapNotNull { ch ->
                    favoriteMap[ch.id]
                }.mapIndexed { i, fav ->
                    fav.copy(position = i)
                }

                // Persist the new order in DB
                favoriteRepository.reorderFavorites(reorderedFavorites)

                _uiState.update { it.copy(userMessage = "Channel order saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Failed to save channel order") }
            }
        }
    }
}

data class HomeUiState(
    val provider: Provider? = null,
    val allProviders: List<Provider> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredChannels: List<Channel> = emptyList(),
    val hasChannels: Boolean = false,
    val isLoading: Boolean = true,
    val categorySearchQuery: String = "",
    val channelSearchQuery: String = "",
    val showDialog: Boolean = false,
    val selectedChannelForDialog: Channel? = null,
    val dialogGroupMemberships: List<Long> = emptyList(),
    val userMessage: String? = null,
    val showDeleteGroupDialog: Boolean = false,
    val groupToDelete: Category? = null,
    val parentalControlLevel: Int = 0,
    val selectedCategoryForOptions: Category? = null,
    val isChannelReorderMode: Boolean = false,
    val reorderCategory: Category? = null
)
