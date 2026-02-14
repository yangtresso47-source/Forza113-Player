package com.streamvault.app.ui.screens.home

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import com.streamvault.app.ui.components.CategoryRow
import com.streamvault.app.ui.components.ChannelCard
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Provider
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.ProviderRepository
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.streamvault.domain.usecase.GetCustomCategories

// ── ViewModel ──────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val favoriteRepository: com.streamvault.domain.repository.FavoriteRepository,
    private val preferencesRepository: com.streamvault.data.preferences.PreferencesRepository,
    private val epgRepository: EpgRepository,
    private val getCustomCategories: com.streamvault.domain.usecase.GetCustomCategories
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cache channels locally for filtering
    // Reactive state for channels and favorites
    private val _localChannels = MutableStateFlow<List<Channel>>(emptyList())
    private var epgJob: Job? = null

    
    private var loadChannelsJob: Job? = null

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
                favoriteRepository.getFavorites(com.streamvault.domain.model.ContentType.LIVE)
            ) { channels, query, favorites ->
                Triple(channels, query, favorites)
            }.collectLatest { (channels, query, favorites) ->
                val favoriteIds = favorites.map { it.contentId }.toSet()
                
                // 1. Filter
                val filtered = if (query.isBlank()) {
                    channels
                } else {
                    channels.filter { it.name.contains(query, ignoreCase = true) }
                }

                // 2. Mark Favorites
                val markedChannels = filtered.map { channel ->
                    if (favoriteIds.contains(channel.id)) channel.copy(isFavorite = true) 
                    else channel.copy(isFavorite = false)
                }
                
                _uiState.update { it.copy(filteredChannels = markedChannels, isLoading = false) }

                // 3. Fetch EPG for visible channels
                fetchEpgForChannels(markedChannels)
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

    private var categoriesJob: Job? = null

    private fun loadCategoriesAndChannels(providerId: Long) {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            // Combine provider categories with custom categories
            combine(
                channelRepository.getCategories(providerId),
                getCustomCategories()
            ) { providerCats, customCats ->
                customCats + providerCats
            }.collect { categories ->
                _uiState.update { it.copy(categories = categories) }
                
                val currentSelected = _uiState.value.selectedCategory
                
                if (currentSelected == null && categories.isNotEmpty()) {
                    // Initial selection
                    val favoritesCat = categories.find { it.id == -999L }
                    if (favoritesCat != null) {
                        selectCategory(favoritesCat)
                    } else {
                        selectCategory(categories.first())
                    }
                } else if (currentSelected != null) {
                    // Try to find the currently selected category in the new list to preserve selection
                    val reselectedCat = categories.find { it.id == currentSelected.id }
                    
                    if (reselectedCat != null) {
                        // Category still exists, update state with new object just in case (name change etc)
                        if (reselectedCat != currentSelected) {
                            _uiState.update { it.copy(selectedCategory = reselectedCat) }
                        }
                        // Refresh content. Do NOT set isLoading=true here to avoid flickering.
                        loadChannelsForCategory(reselectedCat)
                    } else {
                         // Category disappeared (deleted group?), fallback to default
                        val favoritesCat = categories.find { it.id == -999L }
                        if (favoritesCat != null) {
                            selectCategory(favoritesCat)
                        } else if (categories.isNotEmpty()) {
                            selectCategory(categories.first())
                        }
                    }
                }
            }
        }
    }

    fun selectCategory(category: Category) {
        // Fix for Bug 1: Prevent double-click from reloading/clearing if already selected
        if (_uiState.value.selectedCategory?.id == category.id) return

        _uiState.update { it.copy(selectedCategory = category, isLoading = true) }
        loadChannelsForCategory(category)
    }

    private fun loadChannelsForCategory(category: Category) {
        // Fix for Bug 1: Cancel previous job to prevent race conditions
        loadChannelsJob?.cancel()
        
        loadChannelsJob = viewModelScope.launch {
            val providerId = _uiState.value.provider?.id ?: return@launch
            
            val channelsFlow = if (category.isVirtual) {
                if (category.id == -999L) {
                    // Global Favorites
                    favoriteRepository.getFavorites(com.streamvault.domain.model.ContentType.LIVE)
                        .map { favorites -> favorites.sortedBy { it.position }.map { it.contentId } }
                        .flatMapLatest { ids -> 
                            if (ids.isEmpty()) flowOf(emptyList()) 
                            else channelRepository.getChannelsByIds(ids).map { unsorted ->
                                val map = unsorted.associateBy { it.id }
                                ids.mapNotNull { map[it] }
                            }
                        }
                } else {
                    // Custom Group
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
                // Regular Category
                 channelRepository.getChannelsByCategory(providerId, category.id)
            }

            channelsFlow.collect { channels ->
                _uiState.update { it.copy(hasChannels = channels.isNotEmpty()) }
                _localChannels.value = channels
            }
        }
    }
    
    fun updateCategorySearchQuery(query: String) {
        _uiState.update { it.copy(categorySearchQuery = query) }
        // We calculate filtered categories in UI or here? 
        // Better to expose filtered list or filter in UI. 
        // For performance, let's filter in UI for now or add a derived state.
    }

    fun updateChannelSearchQuery(query: String) {
        _uiState.update { it.copy(channelSearchQuery = query) }
        // updateFilteredChannels handled by flow
    }

    private fun fetchEpgForChannels(channels: List<Channel>) {
        epgJob?.cancel()
        val epgIds = channels.mapNotNull { it.epgChannelId }.distinct()
        
        epgJob = viewModelScope.launch {
            // Get Favorites to mark channels (No need here, already marked)
            
            // Fetch EPG
            val programs = if (epgIds.isNotEmpty()) {
                epgRepository.getNowPlayingForChannels(epgIds).firstOrNull() ?: emptyList()
            } else {
                emptyList()
            }
            val programMap = programs.associateBy { it.channelId }
            
            val enrichedChannels = channels.map { channel ->
                val program = channel.epgChannelId?.let { programMap[it] }
                if (program != null) channel.copy(currentProgram = program) else channel
            }
            
            _uiState.update { it.copy(filteredChannels = enrichedChannels) }
        }
    }

    fun addFavorite(channel: Channel) {
        viewModelScope.launch {
            favoriteRepository.addFavorite(
                contentId = channel.id,
                contentType = com.streamvault.domain.model.ContentType.LIVE,
                groupId = null // Global Favorites
            )
            onDismissDialog()
            _uiState.update { it.copy(userMessage = "Added ${channel.name} to Favorites") }
        }
    }
    
    fun removeFavorite(channel: Channel) {
        viewModelScope.launch {
             favoriteRepository.removeFavorite(
                contentId = channel.id,
                contentType = com.streamvault.domain.model.ContentType.LIVE,
                groupId = null // Global Favorites
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
            favoriteRepository.createGroup(trimmed)
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
            _uiState.update { it.copy(
                showDeleteGroupDialog = false, 
                groupToDelete = null,
                userMessage = "Group '${category.name}' deleted"
            ) }
        }
    }

    fun addToGroup(channel: Channel, category: Category) {
        if (!category.isVirtual || category.id == -999L) return
        viewModelScope.launch {
            val groupId = -category.id
            favoriteRepository.addFavorite(
                contentId = channel.id,
                contentType = com.streamvault.domain.model.ContentType.LIVE,
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
                contentType = com.streamvault.domain.model.ContentType.LIVE,
                groupId = groupId
             )
             onDismissDialog()
             _uiState.update { it.copy(userMessage = "Removed ${channel.name} from ${category.name}") }
        }
    }

    fun moveChannel(channel: Channel, direction: Int) {
        // direction: -1 for UP/Left, 1 for DOWN/Right
        val currentCategory = _uiState.value.selectedCategory ?: return
        if (!currentCategory.isVirtual) return // Only for Favorites/Custom Groups

        val currentList = _localChannels.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == channel.id }
        if (index == -1) return
        
        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= currentList.size) return
        
        // Optimistic UI update
        java.util.Collections.swap(currentList, index, newIndex)
        _localChannels.value = currentList
        
        // Persist change
        viewModelScope.launch {
            val groupId = if (currentCategory.id == -999L) null else -currentCategory.id
            
            // We need to fetch current favorites to get their IDs and other metadata
            val favoritesCallback = if (groupId == null) {
                favoriteRepository.getFavorites(com.streamvault.domain.model.ContentType.LIVE)
            } else {
                favoriteRepository.getFavoritesByGroup(groupId)
            }
            
            val favorites = favoritesCallback.first()
            val favoriteMap = favorites.associateBy { it.contentId }
            
            // Reconstruct the ordered list of favorites based on the NEW channel order
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
            val memberships = favoriteRepository.getGroupMemberships(channel.id, com.streamvault.domain.model.ContentType.LIVE)
            _uiState.update { it.copy(dialogGroupMemberships = memberships) }
        }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(showDialog = false, selectedChannelForDialog = null) }
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
    val groupToDelete: Category? = null
)

// ── Screen ─────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onChannelClick: (Channel, Category?, Provider?) -> Unit,
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopNavBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    modifier = Modifier.weight(1f)
                )
                
                // Playlist Switcher
                if (uiState.allProviders.size > 1) {
                    com.streamvault.app.ui.components.PlaylistSwitcher(
                        currentProvider = uiState.provider,
                        allProviders = uiState.allProviders,
                        onProviderSelected = { provider ->
                            viewModel.switchProvider(provider.id)
                        },
                        modifier = Modifier.padding(end = 32.dp)
                    )
                }
            }

            if (uiState.isLoading && uiState.categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading channels...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                }
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar - Categories
                    LazyColumn(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .background(SurfaceElevated)
                            .padding(vertical = 16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item {
                            Column {
                                Text(
                                    text = "Categories",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                )
                                SearchInput(
                                    value = uiState.categorySearchQuery,
                                    onValueChange = { viewModel.updateCategorySearchQuery(it) },
                                    placeholder = "Search categories...",
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
                        
                        items(
                            items = uiState.categories.filter { 
                                uiState.categorySearchQuery.isEmpty() || 
                                it.name.contains(uiState.categorySearchQuery, ignoreCase = true) 
                            },
                            key = { it.id }
                        ) { category ->
                            CategoryItem(
                                category = category,
                                isSelected = category.id == uiState.selectedCategory?.id,
                                onClick = { viewModel.selectCategory(category) },
                                onLongClick = { viewModel.requestDeleteGroup(category) }
                            )
                        }
                    }
                    
                    // Content - Channel Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Category Title Header and Search
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, top = 24.dp, bottom = 16.dp, end = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.selectedCategory?.name ?: "All Channels",
                                style = MaterialTheme.typography.headlineSmall,
                                color = OnBackground
                            )
                            
                            SearchInput(
                                value = uiState.channelSearchQuery,
                                onValueChange = { viewModel.updateChannelSearchQuery(it) },
                                placeholder = "Search channels...",
                                modifier = Modifier.width(300.dp)
                            )
                        }
                        
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurface
                                )
                            }
                        } else if (!uiState.hasChannels) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "📺",
                                        style = MaterialTheme.typography.displayLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No channels found in this category",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = OnSurface
                                    )
                                    val selectedCategory = uiState.selectedCategory
                                    if (selectedCategory?.isVirtual == true && selectedCategory.id == -999L) {
                                        Text(
                                            text = "Add channels to favorites to see them here",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceDim,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Shared lock state for all items
                            var ignoreNextClick by remember { mutableStateOf(false) }

                            // Auto-reset lock if click never comes (e.g. user drags away)
                            LaunchedEffect(ignoreNextClick) {
                                if (ignoreNextClick) {
                                    kotlinx.coroutines.delay(1000)
                                    ignoreNextClick = false
                                }
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = uiState.filteredChannels,
                                    key = { it.id }
                                ) { channel ->
                                    ChannelCard(
                                        channel = channel,
                                        onClick = { 
                                            if (ignoreNextClick) {
                                                ignoreNextClick = false
                                            } else if (!uiState.showDialog) {
                                                onChannelClick(channel, uiState.selectedCategory, uiState.provider) 
                                            }
                                        },
                                        onLongClick = {
                                            ignoreNextClick = true
                                            viewModel.onShowDialog(channel)
                                        },
                                        modifier = Modifier.aspectRatio(16f/9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (uiState.showDialog && uiState.selectedChannelForDialog != null) {
        val channel = uiState.selectedChannelForDialog!!
        val context = androidx.compose.ui.platform.LocalContext.current
        com.streamvault.app.ui.components.dialogs.AddToGroupDialog(
            channel = channel,
            groups = uiState.categories.filter { it.isVirtual && it.id != -999L },
            isFavorite = channel.isFavorite, // This is now Global Favorite status
            memberOfGroups = uiState.dialogGroupMemberships,
            onDismiss = { viewModel.onDismissDialog() },
            onToggleFavorite = { 
                if (channel.isFavorite) viewModel.removeFavorite(channel) else viewModel.addFavorite(channel)
                // Dialog dismissed by ViewModel via side-effect or manually here?
                // VM dismisses it now.
            },
            onAddToGroup = { group ->
                viewModel.addToGroup(channel, group)
            },
            onRemoveFromGroup = { group ->
                 viewModel.removeFromGroup(channel, group)
            },
            onCreateGroup = { name -> viewModel.createCustomGroup(name) },
            onMoveUp = if (uiState.selectedCategory?.isVirtual == true) { { viewModel.moveChannel(channel, -1) } } else null,
            onMoveDown = if (uiState.selectedCategory?.isVirtual == true) { { viewModel.moveChannel(channel, 1) } } else null
        )
    }

    if (uiState.showDeleteGroupDialog && uiState.groupToDelete != null) {
        val group = uiState.groupToDelete!!
        
        // Fix for ghost clicks: Debounce interaction for 500ms to ignore long-press release
        var canInteract by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(500)
            canInteract = true
        }

        val safeDismiss = {
            if (canInteract) viewModel.cancelDeleteGroup()
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = safeDismiss,
            title = { Text("Delete Group?") },
            text = { Text("Are you sure you want to delete '${group.name}'? This will remove all channels from this group.") },
            confirmButton = {
                TextButton(onClick = { if (canInteract) viewModel.confirmDeleteGroup() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = safeDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = SurfaceHighlight,
            contentColor = if (isSelected) Primary else OnSurface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusBorder),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Text(
            text = "${category.name} (${category.count})",
            style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            maxLines = 1,
            color = if (isFocused) OnBackground else if (isSelected) Primary else OnSurface
        )
    }
}

@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val borderColor = if (isFocused) FocusBorder else OnSurfaceDim.copy(alpha = 0.5f)
    val bgColor = if (isFocused) SurfaceHighlight else SurfaceElevated
    val borderWidth = if (isFocused) 2.dp else 1.dp

    // Pattern copied from ProviderSetupScreen's TvTextField
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable { 
                focusRequester.requestFocus() 
                keyboardController?.show()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔍", modifier = Modifier.padding(end = 8.dp))
            
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && !isFocused) {
                    Text(
                        text = placeholder, 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = OnSurfaceDim
                    )
                }

                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = OnSurface
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary)
                )
            }
        }
    }
}
