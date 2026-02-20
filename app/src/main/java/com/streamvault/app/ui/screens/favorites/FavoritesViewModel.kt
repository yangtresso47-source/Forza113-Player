package com.streamvault.app.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Favorite
import com.streamvault.domain.model.VirtualGroup
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.FavoriteRepository
import com.streamvault.domain.repository.MovieRepository
import com.streamvault.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoriteUiModel(
    val favorite: Favorite,
    val title: String,
    val subtitle: String? = null,
    val streamUrl: String = ""
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val channelRepository: ChannelRepository,
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.getFavorites(null).collect { favorites ->
                val uiModels = favorites.mapNotNull { fav ->
                    try {
                        when (fav.contentType) {
                            ContentType.LIVE -> {
                                val channel = channelRepository.getChannel(fav.contentId)
                                if (channel != null) {
                                    FavoriteUiModel(
                                        favorite = fav,
                                        title = channel.name,
                                        subtitle = "Channel ${channel.number}",
                                        streamUrl = channel.streamUrl
                                    )
                                } else null
                            }
                            ContentType.MOVIE -> {
                                val movie = movieRepository.getMovie(fav.contentId)
                                if (movie != null) {
                                    FavoriteUiModel(
                                        favorite = fav,
                                        title = movie.name,
                                        subtitle = "Movie",
                                        streamUrl = movie.streamUrl
                                    )
                                } else null
                            }
                            ContentType.SERIES -> {
                                val series = seriesRepository.getSeriesById(fav.contentId)
                                if (series != null) {
                                    FavoriteUiModel(
                                        favorite = fav,
                                        title = series.name,
                                        subtitle = "Series",
                                        streamUrl = "" // Series doesn't have a single stream URL
                                    )
                                } else null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                _uiState.update { it.copy(favorites = uiModels, isLoading = false) }
            }
        }
        viewModelScope.launch {
            favoriteRepository.getGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun enterReorderMode(item: FavoriteUiModel) {
        _uiState.update { it.copy(isReorderMode = true, reorderItem = item.favorite) }
    }

    fun exitReorderMode() {
        _uiState.update { it.copy(isReorderMode = false, reorderItem = null) }
    }

    fun moveItem(direction: Int) {
        val currentList = _uiState.value.favorites.toMutableList()
        val reorderItem = _uiState.value.reorderItem ?: return
        val index = currentList.indexOfFirst { it.favorite.id == reorderItem.id }
        if (index == -1) return

        val newIndex = index + direction
        if (newIndex in 0 until currentList.size) {
            java.util.Collections.swap(currentList, index, newIndex)
            _uiState.update { it.copy(favorites = currentList) }
        }
    }

    fun saveReorder() {
        val currentModels = _uiState.value.favorites
        val updatedFavorites = currentModels.map { it.favorite }
        viewModelScope.launch {
            favoriteRepository.reorderFavorites(updatedFavorites)
            exitReorderMode()
        }
    }
}

data class FavoritesUiState(
    val favorites: List<FavoriteUiModel> = emptyList(),
    val groups: List<VirtualGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isReorderMode: Boolean = false,
    val reorderItem: Favorite? = null
)
