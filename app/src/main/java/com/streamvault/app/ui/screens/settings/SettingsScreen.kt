package com.streamvault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.Provider
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            providerRepository.getProviders().collect { providers ->
                _uiState.update { it.copy(providers = providers) }
            }
        }
    }

    fun refreshProvider(providerId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = providerRepository.refreshProviderData(providerId)
            _uiState.update { state -> 
                state.copy(
                    isSyncing = false,
                    userMessage = if (result is com.streamvault.domain.model.Result.Error) 
                        "Refresh failed: ${result.message}" 
                    else "Provider refreshed successfully"
                )
            }
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
        }
    }
    
    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}

data class SettingsUiState(
    val providers: List<Provider> = emptyList(),
    val isSyncing: Boolean = false,
    val userMessage: String? = null
)

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onAddProvider: () -> Unit = {},
    onEditProvider: (Provider) -> Unit = {},
    currentRoute: String,
    viewModel: SettingsViewModel = hiltViewModel()
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
                        isSyncing = uiState.isSyncing,
                        onRefresh = { viewModel.refreshProvider(provider.id) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onEdit = { onEditProvider(provider) }
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
    }
}

@Composable
private fun ProviderSettingsCard(
    provider: Provider,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit) {
    // Use Column layout - provider info + buttons below as separate focusable items
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceElevated,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Provider info
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

        // Action buttons - each independently focusable for d-pad
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                onClick = onRefresh,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.2f),
                    focusedContainerColor = Primary.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = if (isSyncing) "⟳ Syncing..." else "⟳ Refresh",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
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
