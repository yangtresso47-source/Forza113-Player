package com.streamvault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider

import com.streamvault.app.ui.components.dialogs.PinDialog
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Provider
import kotlinx.coroutines.launch


@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onAddProvider: () -> Unit = {},
    onEditProvider: (Provider) -> Unit = {},
    onNavigateToParentalControl: (Long) -> Unit = {},
    currentRoute: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Parental Control State
    var showPinDialog by remember { mutableStateOf(false) }
    var showLevelDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<ParentalAction?>(null) }



    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopNavBar(
                currentRoute = currentRoute,
                onNavigate = { if (!uiState.isSyncing) onNavigate(it) } // Block nav
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = !uiState.isSyncing // Block scroll
            ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Providers section
            item {
                Text(
                    text = "Providers",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (uiState.providers.isEmpty()) {
                item {
                    Text(
                        text = "No providers configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface
                    )
                }
            } else {
                items(uiState.providers.size) { index ->
                    val provider = uiState.providers[index]
                    ProviderSettingsCard(
                        provider = provider,
                        isActive = provider.id == uiState.activeProviderId,
                        isSyncing = uiState.isSyncing,
                        onConnect = { viewModel.setActiveProvider(provider.id) },
                        onRefresh = { viewModel.refreshProvider(provider.id) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onEdit = { onEditProvider(provider) },
                        onParentalControl = { onNavigateToParentalControl(provider.id) }
                    )
                }




            }

            // Add Provider button
            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onAddProvider,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.15f),
                        focusedContainerColor = Primary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "➕  Add Provider",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Primary
                        )
                    }
                }
            }

            // Parental Control
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Parental Control",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ParentalControlCard(
                    level = uiState.parentalControlLevel,
                    onChangeLevel = { 
                        pendingAction = ParentalAction.ChangeLevel
                        showPinDialog = true
                    },
                    onChangePin = {
                        pendingAction = ParentalAction.ChangePin
                        showPinDialog = true
                    }
                )
            }

            // Decoder settings
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Playback",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsRow(label = "Decoder Mode", value = "Auto (HW preferred)")
                SettingsRow(label = "Buffer Duration", value = "5 seconds")
            }

            // About section
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsRow(label = "App Version", value = "1.0.0")
                SettingsRow(label = "Build", value = "StreamVault for Android TV")
            }
            }
        }


    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )

        // Blocking Loading Overlay
        if (uiState.isSyncing) {
            // Block Back Press
            androidx.activity.compose.BackHandler(enabled = true) {
                // Do nothing, effectively blocking back
            }
            
            // Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = true, onClick = {}) // Consume clicks
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Primary)
                    Text(
                        text = "Syncing data, please wait...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                }
            }
        }


        if (showPinDialog) {
            PinDialog(
                onDismissRequest = { 
                    showPinDialog = false
                    pinError = null 
                },
                onPinEntered = { pin ->
                    scope.launch {
                        if (pendingAction == ParentalAction.SetNewPin) {
                            viewModel.changePin(pin)
                            showPinDialog = false
                            pendingAction = null
                        } else {
                            if (viewModel.verifyPin(pin)) {
                                showPinDialog = false
                                pinError = null
                                when (pendingAction) {
                                    ParentalAction.ChangeLevel -> showLevelDialog = true
                                    ParentalAction.ChangePin -> {
                                        pendingAction = ParentalAction.SetNewPin
                                        showPinDialog = true 
                                    }
                                    else -> pendingAction = null
                                }
                            } else {
                                pinError = "Incorrect PIN"
                            }
                        }
                    }
                },
                title = if (pendingAction == ParentalAction.SetNewPin) "Enter New PIN" else "Enter PIN",
                error = pinError
            )
        }

        if (showLevelDialog) {
            AlertDialog(
                onDismissRequest = { showLevelDialog = false },
                title = { Text("Select Protection Level") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LevelOption(0, "OFF - No restrictions", uiState.parentalControlLevel) {
                            viewModel.setParentalControlLevel(0)
                            showLevelDialog = false
                        }
                        LevelOption(1, "LOCKED - Content visible, PIN required", uiState.parentalControlLevel) {
                            viewModel.setParentalControlLevel(1)
                            showLevelDialog = false
                        }
                        LevelOption(2, "HIDDEN - Content hidden from all lists", uiState.parentalControlLevel) {
                            viewModel.setParentalControlLevel(2)
                            showLevelDialog = false
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLevelDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private enum class ParentalAction {
    ChangeLevel, ChangePin, SetNewPin
}

@Composable
private fun LevelOption(level: Int, text: String, currentLevel: Int, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = level == currentLevel,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Explicitly use Black for dialog text as it likely has a light background
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
    }
}

@Composable
private fun ParentalControlCard(
    level: Int,
    onChangeLevel: () -> Unit,
    onChangePin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Protection Level", style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                Text(
                    text = when(level) {
                        0 -> "OFF"
                        1 -> "LOCKED"
                        2 -> "HIDDEN"
                        else -> "UNKNOWN"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (level == 0) ErrorColor else Primary
                )
            }
            
            // Custom Focusable Button for "Change"
            Surface(
                onClick = onChangeLevel,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Secondary.copy(alpha = 0.2f),
                    focusedContainerColor = Secondary.copy(alpha = 0.5f),
                    contentColor = Secondary,
                    focusedContentColor = Secondary
                )
            ) {
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Parental PIN", style = MaterialTheme.typography.bodyLarge, color = OnBackground)
            
            // Custom Focusable Button for "Change PIN"
            Surface(
                onClick = onChangePin,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.2f),
                    focusedContainerColor = Primary.copy(alpha = 0.5f),
                    contentColor = Primary,
                    focusedContentColor = Primary
                )
            ) {
                Text(
                    text = "Change PIN",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(
    provider: Provider,
    isActive: Boolean,
    isSyncing: Boolean,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onParentalControl: () -> Unit
) {
    // Use Column layout - provider info + buttons below as separate focusable items
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isActive) SurfaceHighlight else SurfaceElevated,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = if (isActive) Primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Provider info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground
                )
                Text(
                    text = "${provider.type.name} • ${provider.status.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }
            if (isActive) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Expiration Date
        val expDate = provider.expirationDate
        val expirationText = remember(expDate) {
            when (expDate) {
                null -> "Expiration: Unknown"
                Long.MAX_VALUE -> "Expiration: Never"
                else -> "Expires: " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(expDate))
            }
        }
        Text(
            text = expirationText,
            style = MaterialTheme.typography.bodySmall,
            color = if (expDate != null && expDate < System.currentTimeMillis() && expDate != Long.MAX_VALUE) ErrorColor else OnSurfaceDim
        )

        // Action buttons - each independently focusable for d-pad
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isActive) {
                Surface(
                    onClick = onConnect,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary,
                        focusedContainerColor = Primary.copy(alpha = 0.8f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "⚡ Connect",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                Surface(
                    onClick = onRefresh,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.2f),
                        focusedContainerColor = Primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (isSyncing) "⟳ Syncing..." else "⟳ Sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            Surface(
                onClick = onEdit,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Secondary.copy(alpha = 0.2f),
                    focusedContainerColor = Secondary.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "✎ Edit",
                    style = MaterialTheme.typography.labelMedium,
                    color = Secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Surface(
                onClick = onDelete,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = ErrorColor.copy(alpha = 0.2f),
                    focusedContainerColor = ErrorColor.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "🗑 Delete",
                    style = MaterialTheme.typography.labelMedium,
                    color = ErrorColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (isActive) {
                Surface(
                    onClick = onParentalControl,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.15f),
                        focusedContainerColor = Primary.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "🛡️ Parental Control",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
    }
}
