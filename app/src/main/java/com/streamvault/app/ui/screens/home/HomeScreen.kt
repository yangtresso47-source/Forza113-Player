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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.*
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
import com.streamvault.app.ui.screens.multiview.MultiViewViewModel
import com.streamvault.app.ui.screens.multiview.MultiViewPlannerDialog
import com.streamvault.app.navigation.Routes


// ── Screen ─────────────────────────────────────────────────────────




// ── Screen ─────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onChannelClick: (Channel, Category?, Provider?) -> Unit,
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: HomeViewModel = hiltViewModel(),
    multiViewViewModel: MultiViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Split screen state
    val splitSlots by multiViewViewModel.slotsFlow.collectAsState()
    val hasSplitChannels = splitSlots.any { it != null }
    var showSplitManagerDialog by remember { mutableStateOf(false) }
    
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
            } else null,
            onReorderChannels = if (category.isVirtual) {
                { viewModel.enterChannelReorderMode(category) }
            } else null
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isChannelReorderMode) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Reordering '${uiState.selectedCategory?.name ?: "Channels"}'",
                                style = MaterialTheme.typography.titleLarge,
                                color = Primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                androidx.tv.material3.Button(
                                    onClick = { viewModel.exitChannelReorderMode() },
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = OnSurface
                                    )
                                ) { Text("Cancel", modifier = Modifier.padding(horizontal = 8.dp)) }
                                
                                androidx.tv.material3.Button(
                                    onClick = { viewModel.saveChannelReorder() },
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = Primary,
                                        contentColor = Color.White
                                    )
                                ) { Text("Save Order", modifier = Modifier.padding(horizontal = 8.dp)) }
                            }
                        }
                    }
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
                    ) {
                        TopNavBar(
                            currentRoute = currentRoute,
                            onNavigate = onNavigate,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Split Screen button — visible only if at least 1 slot is filled
                                if (hasSplitChannels) {
                                    Surface(
                                        onClick = { showSplitManagerDialog = true },
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFF1B5E20),
                                            focusedContainerColor = Color(0xFF2E7D32)
                                        )
                                    ) {
                                        Text(
                                            text = "🔳  " + stringResource(R.string.multiview_nav) +
                                                " (${splitSlots.count { it != null }})",
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                SearchInput(
                                    value = uiState.channelSearchQuery,
                                    onValueChange = { viewModel.updateChannelSearchQuery(it) },
                                    placeholder = stringResource(R.string.home_search_channels),
                                    modifier = Modifier.width(300.dp)
                                )
                            }
                        }
                        
                        if (uiState.isLoading) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = LocalSpacing.current.safeHoriz, 
                                    end = LocalSpacing.current.safeHoriz, 
                                    bottom = LocalSpacing.current.safeBottom
                                ),
                                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
                                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
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

                            // Reorder Drag State
                            var draggingChannel by remember { mutableStateOf<Channel?>(null) }
                            
                            // Exit drag state if reorder mode is cancelled
                            LaunchedEffect(uiState.isChannelReorderMode) {
                                if (!uiState.isChannelReorderMode) {
                                    draggingChannel = null
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Handle BACK key to cancel reorder mode
                                    .onPreviewKeyEvent { event ->
                                        if (uiState.isChannelReorderMode && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                                if (draggingChannel != null) {
                                                    draggingChannel = null // Drop item
                                                    true
                                                } else {
                                                    viewModel.exitChannelReorderMode()
                                                    true
                                                }
                                            } else false
                                        } else false
                                    },
                                contentPadding = PaddingValues(
                                    start = LocalSpacing.current.safeHoriz, 
                                    end = LocalSpacing.current.safeHoriz, 
                                    bottom = LocalSpacing.current.safeBottom
                                ),
                                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
                                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                            ) {
                                items(
                                    items = uiState.filteredChannels,
                                    key = { it.id }
                                ) { channel ->
                                    val isLocked = (channel.isAdult || channel.isUserProtected || (uiState.selectedCategory?.isUserProtected ?: false)) && uiState.parentalControlLevel == 1
                                    val isDraggingThis = draggingChannel == channel
                                    
                                    ChannelCard(
                                        channel = channel,
                                        isLocked = isLocked,
                                        isReorderMode = uiState.isChannelReorderMode,
                                        isDragging = isDraggingThis,
                                        onClick = { 
                                            if (uiState.isChannelReorderMode) {
                                                // Toggle drag state
                                                draggingChannel = if (isDraggingThis) null else channel
                                            } else if (ignoreNextClick) {
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
                                            if (!uiState.isChannelReorderMode) {
                                                ignoreNextClick = true
                                                viewModel.onShowDialog(channel)
                                            }
                                        },
                                        modifier = Modifier
                                            .aspectRatio(16f/9f)
                                            .onPreviewKeyEvent { event ->
                                                if (uiState.isChannelReorderMode && isDraggingThis && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    // Consume D-pad to move item instead of changing focus
                                                    when (event.nativeKeyEvent.keyCode) {
                                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { viewModel.moveChannelUp(channel); true }
                                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> { viewModel.moveChannelUp(channel); true }
                                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.moveChannelDown(channel); true }
                                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { viewModel.moveChannelDown(channel); true }
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
        com.streamvault.app.ui.components.dialogs.AddToGroupDialog(
            contentTitle = channel.name,
            channel = channel,
            groups = uiState.categories.filter { it.isVirtual && it.id != -999L },
            isFavorite = channel.isFavorite,
            memberOfGroups = uiState.dialogGroupMemberships,
            onDismiss = { viewModel.onDismissDialog() },
            onToggleFavorite = {
                if (channel.isFavorite) viewModel.removeFavorite(channel) else viewModel.addFavorite(channel)
            },
            onAddToGroup = { group -> viewModel.addToGroup(channel, group) },
            onRemoveFromGroup = { group -> viewModel.removeFromGroup(channel, group) },
            onCreateGroup = { name -> viewModel.createCustomGroup(name) },
            onNavigateToSplitScreen = { onNavigate(Routes.MULTI_VIEW) }
        )
    }

    // Split Screen manager dialog (opened from the header button)
    if (showSplitManagerDialog) {
        MultiViewPlannerDialog(
            pendingChannel = null,
            onDismiss = { showSplitManagerDialog = false },
            onLaunch = {
                showSplitManagerDialog = false
                onNavigate(Routes.MULTI_VIEW)
            },
            viewModel = multiViewViewModel
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

@Composable
fun ReorderSidePanel(
    channels: List<Channel>,
    onMoveUp: (Channel) -> Unit,
    onMoveDown: (Channel) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var draggingChannel by remember { mutableStateOf<Channel?>(null) }
    
    // Focus requester to trap focus inside the panel
    val panelFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        panelFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(SurfaceElevated)
            .padding(16.dp)
            .focusRequester(panelFocusRequester)
            .focusGroup() // Traps D-pad focus in this container
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                "Reorder Channels", 
                style = MaterialTheme.typography.titleMedium, 
                color = Primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                androidx.tv.material3.Button(
                    onClick = onSave,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Save", maxLines = 1) }

                androidx.tv.material3.Button(
                    onClick = onCancel,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = OnSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel", maxLines = 1) }
            }
            
            // List
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels.size) { index ->
                    val channel = channels[index]
                    var isFocused by remember { mutableStateOf(false) }
                    val isDraggingThis = draggingChannel == channel
                    
                    Surface(
                        onClick = { 
                            draggingChannel = if (isDraggingThis) null else channel 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                    if (isDraggingThis) {
                                        when (event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                onMoveUp(channel)
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                onMoveDown(channel)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        // Trap focus left/right so we don't accidentally exit panel
                                        if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                                            true 
                                        } else {
                                            false
                                        }
                                    }
                                } else false
                            },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            focusedContainerColor = if (isDraggingThis) Primary else Primary.copy(alpha = 0.2f),
                            containerColor = if (isDraggingThis) Primary.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, FocusBorder),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDraggingThis) {
                                Text("↕", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                            }
                            Text(
                                "${index + 1}. ${channel.name}", 
                                modifier = Modifier.weight(1f), 
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDraggingThis) Color.White else if (isFocused) OnBackground else OnSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // Helper Text
            Text(
                if (draggingChannel != null) "UP/DOWN to move.\nOK to drop." else "OK to grab channel.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
