package com.streamvault.app.ui.screens.movies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.streamvault.app.ui.components.CategoryRow
import com.streamvault.app.ui.components.ContinueWatchingRow
import com.streamvault.app.ui.components.MovieCard
import com.streamvault.app.ui.components.SkeletonRow
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Movie
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.streamvault.app.R

@Composable
fun MoviesScreen(
    onMovieClick: (Movie) -> Unit,
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingMovie by remember { mutableStateOf<Movie?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showPinDialog) {
        com.streamvault.app.ui.components.dialogs.PinDialog(
            onDismissRequest = {
                showPinDialog = false
                pinError = null
                pendingMovie = null
            },
            onPinEntered = { pin ->
                scope.launch {
                    if (viewModel.verifyPin(pin)) {
                        showPinDialog = false
                        pinError = null
                        pendingMovie?.let { onMovieClick(it) }
                        pendingMovie = null
                    } else {
                        pinError = context.getString(R.string.movies_incorrect_pin)
                    }
                }
            },
            error = pinError
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNavBar(currentRoute = currentRoute, onNavigate = onNavigate)

        if (uiState.isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(3) {
                    SkeletonRow(
                        modifier = Modifier.fillMaxWidth(),
                        cardWidth = 140,
                        cardHeight = 210,
                        itemsCount = 10
                    )
                }
            }
        } else if (uiState.moviesByCategory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎬", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.movies_no_found), style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Continue Watching row (shown first, only if non-empty)
                item(key = "continue_watching") {
                    ContinueWatchingRow(
                        items = uiState.continueWatching,
                        onItemClick = { history -> onMovieClick(
                            com.streamvault.domain.model.Movie(
                                id = history.contentId,
                                name = history.title,
                                posterUrl = history.posterUrl,
                                streamUrl = history.streamUrl,
                                providerId = history.providerId
                            )
                        )}
                    )
                }
                items(
                    items = uiState.moviesByCategory.entries.toList(),
                    key = { it.key }
                ) { (categoryName, movies) ->
                    CategoryRow(title = categoryName, items = movies) { movie ->
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
                            }
                        )
                    }
                }
            }
        }
    }
}
