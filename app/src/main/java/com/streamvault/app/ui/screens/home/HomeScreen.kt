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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import com.streamvault.app.ui.components.CategoryRow
import com.streamvault.app.ui.components.ChannelCard
import com.streamvault.app.ui.components.dialogs.CategoryOptionsDialog
import com.streamvault.app.ui.components.dialogs.PinDialog
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.components.SkeletonCard
import com.streamvault.app.ui.components.shimmerEffect
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Provider
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.streamvault.app.R


// ── Screen ─────────────────────────────────────────────────────────




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
    
    // Parental Control State
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingUnlockCategory by remember { mutableStateOf<Category?>(null) }
    var pendingUnlockChannel by remember { mutableStateOf<Channel?>(null) }
    var pendingLockToggleCategory by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }
    
    if (showPinDialog) {
        PinDialog(
            onDismissRequest = { 
                showPinDialog = false
                pinError = null
                pendingUnlockCategory = null
                pendingUnlockChannel = null
            },
            onPinEntered = { pin ->
                scope.launch {
                    if (viewModel.verifyPin(pin)) {
                        showPinDialog = false
                        pinError = null
                        
                        pendingUnlockCategory?.let { category ->
                            // Use the sequence: Select (clears old unlocks) -> Unlock (adds this one)
                            viewModel.selectCategory(category)
                            viewModel.unlockCategory(category)
                            pendingUnlockCategory = null
                        }
                        
                        pendingUnlockChannel?.let { channel ->
                             onChannelClick(channel, uiState.selectedCategory, uiState.provider)
                             pendingUnlockChannel = null
                        }

                        pendingLockToggleCategory?.let { category ->
                            viewModel.toggleCategoryLock(category)
                            pendingLockToggleCategory = null
                        }
                    } else {
                        pinError = context.getString(R.string.home_incorrect_pin)
                    }
                }
            },
            error = pinError
        )
    }

    if (uiState.selectedCategoryForOptions != null) {
        val category = uiState.selectedCategoryForOptions!!
        CategoryOptionsDialog(
            category = category,
            onDismissRequest = { viewModel.dismissCategoryOptions() },
            onSetAsDefault = {
                viewModel.setDefaultCategory(category)
                viewModel.dismissCategoryOptions()
            },
            onToggleLock = {
                viewModel.dismissCategoryOptions()
                pendingLockToggleCategory = category
                showPinDialog = true
            },
            onDelete = if (category.isVirtual && category.id != -999L) {
                {
                    viewModel.dismissCategoryOptions()
                    viewModel.requestDeleteGroup(category)
                }
            } else null
        )
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
                
                // Playlist Switcher - REMOVED (Moved to Settings)
            }

            if (uiState.isLoading && uiState.categories.isEmpty()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar skeleton
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .background(SurfaceElevated)
                            .padding(vertical = 16.dp, horizontal = 16.dp)
                    ) {
                        repeat(10) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 4.dp)
                                    .background(Color.DarkGray, RoundedCornerShape(8.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                    // Content skeleton
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(20) {
                                SkeletonCard(
                                    modifier = Modifier.aspectRatio(16f/9f)
                                )
                            }
                        }
                    }
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
                                    text = stringResource(R.string.home_categories_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                                )
                                SearchInput(
                                    value = uiState.categorySearchQuery,
                                    onValueChange = { viewModel.updateCategorySearchQuery(it) },
                                    placeholder = stringResource(R.string.home_search_categories),
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
                            val isLocked = (category.isAdult || category.isUserProtected) && uiState.parentalControlLevel == 1
                            
                            CategoryItem(
                                category = category,
                                isSelected = category.id == uiState.selectedCategory?.id,
                                isLocked = isLocked,
                                onClick = { 
                                    if (isLocked) {
                                        pendingUnlockCategory = category
                                        showPinDialog = true
                                    } else {
                                        viewModel.selectCategory(category) 
                                    }
                                },
                                onLongClick = { viewModel.showCategoryOptions(category) }
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
                                text = uiState.selectedCategory?.name ?: stringResource(R.string.home_all_channels),
                                style = MaterialTheme.typography.headlineSmall,
                                color = OnBackground
                            )
                            
                            SearchInput(
                                value = uiState.channelSearchQuery,
                                onValueChange = { viewModel.updateChannelSearchQuery(it) },
                                placeholder = stringResource(R.string.home_search_channels),
                                modifier = Modifier.width(300.dp)
                            )
                        }
                        
                        if (uiState.isLoading) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(20) {
                                    SkeletonCard(
                                        modifier = Modifier.aspectRatio(16f/9f)
                                    )
                                }
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
                                        text = stringResource(R.string.home_no_channels_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = OnSurface
                                    )
                                    val selectedCategory = uiState.selectedCategory
                                    if (selectedCategory?.isVirtual == true && selectedCategory.id == -999L) {
                                        Text(
                                            text = stringResource(R.string.home_add_favorites_hint),
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
                                    val isLocked = (channel.isAdult || channel.isUserProtected || (uiState.selectedCategory?.isUserProtected ?: false)) && uiState.parentalControlLevel == 1
                                    
                                    ChannelCard(
                                        channel = channel,
                                        isLocked = isLocked,
                                        onClick = { 
                                            if (ignoreNextClick) {
                                                ignoreNextClick = false
                                            } else if (!uiState.showDialog) {
                                                if (isLocked) {
                                                    pendingUnlockChannel = channel
                                                    showPinDialog = true
                                                } else {
                                                    onChannelClick(channel, uiState.selectedCategory, uiState.provider)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            // Allow long click even if locked? Maybe restricts context menu?
                                            // For now, allow it (e.g. to delete from favorites/groups)
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
            title = { Text(stringResource(R.string.home_delete_group_title)) },
            text = { Text(stringResource(R.string.home_delete_group_body, group.name)) },
            confirmButton = {
                TextButton(onClick = { if (canInteract) viewModel.confirmDeleteGroup() }) {
                    Text(stringResource(R.string.home_delete_group_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = safeDismiss) {
                    Text(stringResource(R.string.home_delete_group_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    isSelected: Boolean,
    isLocked: Boolean = false,
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
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${category.name} (${category.count})",
                style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = if (isFocused) OnBackground else if (isSelected) Primary else OnSurface,
                modifier = Modifier.weight(1f)
            )
            
            if (isLocked) {
                Text(
                    text = "🔒",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
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
            .onFocusChanged { isFocused = it.hasFocus } // Track focus (self or child)
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
                        .focusRequester(focusRequester),
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
