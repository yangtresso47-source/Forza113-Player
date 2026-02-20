package com.streamvault.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.data.preferences.PreferencesRepository
import com.streamvault.domain.model.Provider
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                providerRepository.getProviders(),
                preferencesRepository.lastActiveProviderId,
                preferencesRepository.parentalControlLevel
            ) { providers, activeId, level ->
                Triple(providers, activeId, level)
            }.collect { (providers, activeId, level) ->
                _uiState.update {
                    it.copy(
                        providers = providers,
                        activeProviderId = activeId,
                        parentalControlLevel = level
                    )
                }
            }
        }
    }

    fun setActiveProvider(providerId: Long) {
        viewModelScope.launch {
            preferencesRepository.setLastActiveProviderId(providerId)
            providerRepository.setActiveProvider(providerId)
            // Force sync on connect
            refreshProvider(providerId)
        }
    }

    fun setParentalControlLevel(level: Int) {
        viewModelScope.launch {
            preferencesRepository.setParentalControlLevel(level)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val currentPin = preferencesRepository.parentalPin.firstOrNull() ?: "0000"
        return pin == currentPin
    }

    fun changePin(newPin: String) {
        viewModelScope.launch {
            preferencesRepository.setParentalPin(newPin)
            _uiState.update { it.copy(userMessage = "PIN changed successfully") }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun refreshProvider(providerId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = providerRepository.refreshProviderData(providerId)
            _uiState.update { state ->
                state.copy(
                    isSyncing = false,
                    userMessage = if (result is Result.Error)
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
    val activeProviderId: Long? = null,
    val isSyncing: Boolean = false,
    val userMessage: String? = null,
    val parentalControlLevel: Int = 0
)
