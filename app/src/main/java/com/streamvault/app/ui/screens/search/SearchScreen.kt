package com.streamvault.app.ui.screens.search

import androidx.annotation.StringRes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.*
import com.streamvault.app.R
import com.streamvault.app.ui.components.SearchInput
import com.streamvault.app.ui.components.ChannelCard
import com.streamvault.app.ui.components.MovieCard
import com.streamvault.app.ui.components.SeriesCard
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.MovieRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val preferencesRepository: com.streamvault.data.preferences.PreferencesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _parentalControlLevel = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            preferencesRepository.parentalControlLevel.collect { level ->
                _parentalControlLevel.value = level
            }
        }
    }

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SearchUiState> = combine(
        providerRepository.getActiveProvider(),
        _query.debounce(300),
        _selectedTab,
        _parentalControlLevel
    ) { provider, query, tab, level ->
        SearchFilterParams(provider, query, tab, level)
    }.flatMapLatest { params ->
        val provider = params.provider
        val query = params.query
        val tab = params.tab
        val level = params.level

        if (provider == null || query.length < 2) {
            flowOf(SearchUiState(parentalControlLevel = level))
        } else {
            val providerId = provider.id
            combine(
                if (tab == SearchTab.ALL || tab == SearchTab.LIVE) 
                    channelRepository.searchChannels(providerId, query) else flowOf(emptyList()),
                if (tab == SearchTab.ALL || tab == SearchTab.MOVIES) 
                    movieRepository.searchMovies(providerId, query) else flowOf(emptyList()),
                if (tab == SearchTab.ALL || tab == SearchTab.SERIES) 
                    seriesRepository.searchSeries(providerId, query) else flowOf(emptyList())
            ) { channels, movies, series ->
                SearchUiState(
                    channels = channels,
                    movies = movies,
                    series = series,
                    isLoading = false,
                    hasSearched = true,
                    parentalControlLevel = level
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onTabSelected(tab: SearchTab) {
        _selectedTab.value = tab
    }

    suspend fun verifyPin(pin: String): Boolean {
        return preferencesRepository.parentalPin.first() == pin
    }
}

private data class SearchFilterParams(
    val provider: com.streamvault.domain.model.Provider?,
    val query: String,
    val tab: SearchTab,
    val level: Int
)

enum class SearchTab(@StringRes val titleRes: Int) {
    ALL(R.string.search_all),
    LIVE(R.string.search_live_tv),
    MOVIES(R.string.search_movies),
    SERIES(R.string.search_series)
}

data class SearchUiState(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val parentalControlLevel: Int = 0
) {
    val isEmpty: Boolean get() = hasSearched && channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingChannel by remember { mutableStateOf<Channel?>(null) }
    var pendingMovie by remember { mutableStateOf<Movie?>(null) }
    var pendingSeries by remember { mutableStateOf<Series?>(null) }
    val scope = rememberCoroutineScope()

    if (showPinDialog) {
        com.streamvault.app.ui.components.dialogs.PinDialog(
            onDismissRequest = {
                showPinDialog = false
                pinError = null
                pendingChannel = null
                pendingMovie = null
                pendingSeries = null
            },
            onPinEntered = { pin ->
                scope.launch {
                    if (viewModel.verifyPin(pin)) {
                        showPinDialog = false
                        pinError = null
                        pendingChannel?.let { onChannelClick(it) }
                        pendingMovie?.let { onMovieClick(it) }
                        pendingSeries?.let { onSeriesClick(it) }
                        pendingChannel = null
                        pendingMovie = null
                        pendingSeries = null
                    } else {
                        pinError = context.getString(R.string.search_incorrect_pin)
                    }
                }
            },
            error = pinError
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left Side: Keyboard & Filters
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Real Search Input triggering system keyboard
            SearchInput(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = stringResource(R.string.search_hint),
                focusRequester = searchFocusRequester,
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.padding(bottom = 0.dp)
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchTab.values().toList()) { tab ->
                    FilterChip(
                        selected = tab == selectedTab,
                        onClick = { viewModel.onTabSelected(tab) },
                        colors = FilterChipDefaults.colors(
                            selectedContainerColor = Primary,
                            selectedContentColor = Color.White
                        )
                    ) {
                        Text(stringResource(tab.titleRes))
                    }
                }
            }
        }

        // Right Side: Results
        Box(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
        if (uiState.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.length < 2) stringResource(R.string.search_type_to_search) else stringResource(R.string.search_no_results, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceDim
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Channels
                if (uiState.channels.isNotEmpty()) {
                    if (selectedTab == SearchTab.ALL) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            SectionHeader(stringResource(R.string.search_live_tv))
                        }
                    }
                    items(uiState.channels) { channel ->
                        val isLocked = (channel.isAdult || channel.isUserProtected) && uiState.parentalControlLevel == 1
                        ChannelCard(
                            channel = channel,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    pendingChannel = channel
                                    showPinDialog = true
                                } else {
                                    onChannelClick(channel)
                                }
                            },
                            modifier = Modifier.aspectRatio(16f/9f)
                        )
                    }
                }

                // Movies
                if (uiState.movies.isNotEmpty()) {
                    if (selectedTab == SearchTab.ALL) {
                         item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            SectionHeader(stringResource(R.string.search_movies))
                        }
                    }
                    items(uiState.movies) { movie ->
                        val isLocked = (movie.isAdult || movie.isUserProtected) && uiState.parentalControlLevel == 1
                        MovieCard(
                            movie = movie,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    pendingMovie = movie
                                    showPinDialog = true
                                } else {
                                    onMovieClick(movie)
                                }
                            },
                            modifier = Modifier.aspectRatio(2f/3f)
                        )
                    }
                }

                // Series
                if (uiState.series.isNotEmpty()) {
                    if (selectedTab == SearchTab.ALL) {
                         item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            SectionHeader(stringResource(R.string.search_series))
                        }
                    }
                    items(uiState.series) { series ->
                        val isLocked = (series.isAdult || series.isUserProtected) && uiState.parentalControlLevel == 1
                        SeriesCard(
                            series = series,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    pendingSeries = series
                                    showPinDialog = true
                                } else {
                                    onSeriesClick(series)
                                }
                            },
                            modifier = Modifier.aspectRatio(2f/3f)
                        )
                    }
                }
            }
        }
    }
}
}


@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
