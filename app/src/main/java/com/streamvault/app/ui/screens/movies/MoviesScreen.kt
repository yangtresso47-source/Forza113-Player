package com.streamvault.app.ui.screens.movies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.streamvault.app.ui.components.SearchInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.streamvault.app.navigation.Routes
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.border
import com.streamvault.app.ui.components.ReorderTopBar
import com.streamvault.app.ui.components.dialogs.DeleteGroupDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Border

@OptIn(ExperimentalFoundationApi::class)
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
        if (uiState.isReorderMode && uiState.reorderCategory != null) {
            ReorderTopBar(
                categoryName = uiState.reorderCategory!!.name,
                onSave = { viewModel.saveReorder() },
                onCancel = { viewModel.exitCategoryReorderMode() }
            )
        } else {
            TopNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
        }

        if (uiState.isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(3) {
                    SkeletonRow(
                        modifier = Modifier.fillMaxWidth(),
                        cardWidth = 240,
                        cardHeight = 135,
                        itemsCount = 4
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
            Row(modifier = Modifier.fillMaxSize()) {
                val focusManager = LocalFocusManager.current
                val categorySearchFocusRequester = remember { FocusRequester() }

                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(SurfaceElevated.copy(alpha = 0.5f))
                        .padding(top = 8.dp)
                ) {
                    // Sticky Header
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = Primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        SearchInput(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = "Search movies...",
                            focusRequester = categorySearchFocusRequester,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                    item {
                        val isAllSelected = uiState.selectedCategory == null
                        Surface(
                            onClick = { viewModel.selectCategory(null) },
                            shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isAllSelected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                                focusedContainerColor = Primary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                        if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                            categorySearchFocusRequester.requestFocus()
                                            true
                                        } else false
                                    } else false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Categories",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAllSelected) Primary else OnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${uiState.moviesByCategory.values.sumOf { it.size }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceDim
                                )
                            }
                        }
                    }
                    items(uiState.categoryNames.size) { index ->
                        val categoryName = uiState.categoryNames[index]
                        val isSelected = uiState.selectedCategory == categoryName
                        val count = uiState.moviesByCategory[categoryName]?.size ?: 0
                        Surface(
                            onClick = { viewModel.selectCategory(categoryName) },
                            onLongClick = { viewModel.showCategoryOptions(categoryName) },
                            shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                                focusedContainerColor = Primary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                        if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                            categorySearchFocusRequester.requestFocus()
                                            true
                                        } else false
                                    } else false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) Primary else OnSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceDim
                                )
                            }
                        }
                    }
                }
                }

                // Main content
                if (uiState.selectedCategory == null) {
                    // Netflix-style rows (All categories view)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(
                            start = LocalSpacing.current.safeHoriz, 
                            end = LocalSpacing.current.safeHoriz, 
                            bottom = LocalSpacing.current.safeBottom
                        )
                    ) {
                        item {
                            Surface(
                                onClick = { onNavigate(Routes.SEARCH) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalSpacing.current.md),
                                shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = SurfaceElevated,
                                    focusedContainerColor = Primary.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔍", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.width(16.dp))
                                    Text(stringResource(R.string.search_hint), color = OnSurfaceDim, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        val heroMovie = uiState.moviesByCategory.values.flatten().firstOrNull()
                        if (heroMovie != null) {
                            item {
                                HeroBanner(
                                    movie = heroMovie,
                                    onClick = {
                                        val isLocked = (heroMovie.isAdult || heroMovie.isUserProtected) && uiState.parentalControlLevel == 1
                                        if (isLocked) {
                                            pendingMovie = heroMovie
                                            showPinDialog = true
                                        } else {
                                            onMovieClick(heroMovie)
                                        }
                                    }
                                )
                            }
                        }

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
                                    },
                                    onLongClick = {
                                        viewModel.onShowDialog(movie)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Filtered grid for selected category
                    val filteredMovies = uiState.moviesByCategory[uiState.selectedCategory] ?: emptyList()
                    val activeMovies = if (uiState.isReorderMode) uiState.filteredMovies else filteredMovies
                    
                    var draggingMovie by remember { mutableStateOf<Movie?>(null) }
                    
                    LaunchedEffect(uiState.isReorderMode) {
                        if (!uiState.isReorderMode) {
                            draggingMovie = null
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 240.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (uiState.isReorderMode && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                    if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                        if (draggingMovie != null) {
                                            draggingMovie = null
                                            true
                                        } else {
                                            viewModel.exitCategoryReorderMode()
                                            true
                                        }
                                    } else false
                                } else false
                            },
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = uiState.selectedCategory ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        
                        gridItems(
                            items = activeMovies,
                            key = { it.id }
                        ) { movie ->
                            val isLocked = (movie.isAdult || movie.isUserProtected) && uiState.parentalControlLevel == 1
                            val isDraggingThis = draggingMovie == movie

                            MovieCard(
                                movie = movie,
                                isLocked = isLocked,
                                isReorderMode = uiState.isReorderMode,
                                isDragging = isDraggingThis,
                                onClick = {
                                    if (uiState.isReorderMode) {
                                        draggingMovie = if (isDraggingThis) null else movie
                                    } else if (isLocked) {
                                        pendingMovie = movie
                                        showPinDialog = true
                                    } else {
                                        onMovieClick(movie)
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.isReorderMode) {
                                        viewModel.onShowDialog(movie)
                                    }
                                },
                                modifier = Modifier.onPreviewKeyEvent { event ->
                                    if (uiState.isReorderMode && isDraggingThis && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                        when (event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                viewModel.moveItemUp(movie)
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                viewModel.moveItemDown(movie)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showDialog && uiState.selectedMovieForDialog != null) {
        val movie = uiState.selectedMovieForDialog!!
        com.streamvault.app.ui.components.dialogs.AddToGroupDialog(
            contentTitle = movie.name,
            groups = uiState.categories.filter { it.isVirtual && it.id != -999L },
            isFavorite = movie.isFavorite,
            memberOfGroups = uiState.dialogGroupMemberships,
            onDismiss = { viewModel.onDismissDialog() },
            onToggleFavorite = {
                if (movie.isFavorite) viewModel.removeFavorite(movie) else viewModel.addFavorite(movie)
            },
            onAddToGroup = { group -> viewModel.addToGroup(movie, group) },
            onRemoveFromGroup = { group -> viewModel.removeFromGroup(movie, group) },
            onCreateGroup = { name -> viewModel.createCustomGroup(name) }
        )
    }

    if (uiState.selectedCategoryForOptions != null) {
        val category = uiState.selectedCategoryForOptions!!
        com.streamvault.app.ui.components.dialogs.CategoryOptionsDialog(
            category = category,
            onDismissRequest = { viewModel.dismissCategoryOptions() },
            onDelete = if (category.isVirtual && category.id != -999L) {
                {
                    viewModel.dismissCategoryOptions()
                    viewModel.requestDeleteGroup(category)
                }
            } else null,
            onReorderChannels = if (category.isVirtual) {
                { viewModel.enterCategoryReorderMode(category) }
            } else null
        )
    }

    if (uiState.showDeleteGroupDialog && uiState.groupToDelete != null) {
        DeleteGroupDialog(
            groupName = uiState.groupToDelete!!.name,
            onDismissRequest = { viewModel.cancelDeleteGroup() },
            onConfirmDelete = { viewModel.confirmDeleteGroup() }
        )
    }
}

@Composable
fun HeroBanner(
    movie: Movie,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.posterUrl ?: movie.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 200f
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(32.dp)
            ) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                if (!movie.plot.isNullOrEmpty()) {
                    Text(
                        text = movie.plot!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                    Spacer(Modifier.height(16.dp))
                }
                
                // Play Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(if (isFocused) Primary else Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text("▶", color = if (isFocused) Color.White else Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_resume).substringBefore(" "), color = if (isFocused) Color.White else Color.Black, style = MaterialTheme.typography.titleMedium) // "Play" fallback
                }
            }
        }
    }
}
