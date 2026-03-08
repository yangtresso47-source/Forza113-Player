package com.streamvault.app.ui.screens.multiview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Channel
import com.streamvault.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class MultiViewViewModel @Inject constructor(
    val multiViewManager: MultiViewManager,
    private val playerEngineProvider: Provider<PlayerEngine>
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiViewUiState())
    val uiState: StateFlow<MultiViewUiState> = _uiState.asStateFlow()
    
    private var borderHideJob: kotlinx.coroutines.Job? = null

    /** Flow of the current 4 slot channels from the manager */
    val slotsFlow = multiViewManager.slots

    private val playerEngines = mutableMapOf<Int, PlayerEngine>()

    /** Called when MultiViewScreen is opened — spins up player engines for occupied slots */
    fun initSlots() {
        val channels = multiViewManager.slots.value
        val slots = channels.mapIndexed { index, channel ->
            if (channel != null) {
                MultiViewSlot(
                    index = index,
                    channel = channel,
                    streamUrl = channel.streamUrl,
                    title = channel.name,
                    isLoading = true
                )
            } else {
                MultiViewSlot(index = index)
            }
        }
        _uiState.value = MultiViewUiState(slots = slots)

        // Start all occupied engines
        slots.forEachIndexed { index, slot ->
            if (!slot.isEmpty) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(index * 250L) // Stagger decoders to prevent Peak usage crash
                    try {
                        val engine = playerEngineProvider.get()
                        playerEngines[index] = engine
                        
                        engine.prepare(
                            com.streamvault.domain.model.StreamInfo(url = slot.streamUrl)
                        )
                        engine.play()
                        
                        // Copy the engine into the slot UI state as well
                        updateSlot(index) { it.copy(isLoading = false, playerEngine = engine) }
                        
                        // Re-apply audio to make sure the focused one is audible
                        applyFocusAudio(_uiState.value.focusedSlotIndex)
                    } catch (e: Exception) {
                        updateSlot(index) { it.copy(isLoading = false, hasError = true) }
                    }
                }
            }
        }

        applyFocusAudio(0)
        showSelectionBorderTemporarily()
    }

    fun setFocus(slotIndex: Int) {
        if (_uiState.value.focusedSlotIndex != slotIndex) {
            _uiState.value = _uiState.value.copy(focusedSlotIndex = slotIndex)
            applyFocusAudio(slotIndex)
            showSelectionBorderTemporarily()
        }
    }

    private fun showSelectionBorderTemporarily() {
        borderHideJob?.cancel()
        _uiState.value = _uiState.value.copy(showSelectionBorder = true)
        borderHideJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(showSelectionBorder = false)
        }
    }

    private fun applyFocusAudio(focusedIndex: Int) {
        playerEngines.forEach { (index, engine) ->
            if (index == focusedIndex) engine.setVolume(1f) else engine.setVolume(0f)
        }
    }

    /** Assign a channel to a specific slot (0–3). Called from dialog or AddToGroupDialog. */
    fun assignChannelToSlot(slotIndex: Int, channel: Channel) {
        multiViewManager.setChannel(slotIndex, channel)
    }

    /** Clear a specific slot */
    fun clearSlot(slotIndex: Int) {
        multiViewManager.clearSlot(slotIndex)
    }

    /** Clear all slots */
    fun clearAll() {
        multiViewManager.clearAll()
    }

    fun isQueued(channelId: Long): Boolean = multiViewManager.isQueued(channelId)

    private fun updateSlot(index: Int, transform: (MultiViewSlot) -> MultiViewSlot) {
        val updated = _uiState.value.slots.toMutableList()
        if (index < updated.size) {
            updated[index] = transform(updated[index])
            _uiState.value = _uiState.value.copy(slots = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerEngines.values.forEach { it.release() }
        playerEngines.clear()
    }
}
