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

    /** Flow of the current 4 slot channels from the manager */
    val slotsFlow = multiViewManager.slots

    private val playerEngines = mutableListOf<PlayerEngine>()

    /** Called when MultiViewScreen is opened — spins up player engines for occupied slots */
    fun initSlots() {
        val channels = multiViewManager.slots.value
        val slots = channels.mapIndexed { index, channel ->
            if (channel != null) {
                val engine = playerEngineProvider.get()
                playerEngines.add(engine)
                MultiViewSlot(
                    index = index,
                    channel = channel,
                    streamUrl = channel.streamUrl,
                    title = channel.name,
                    playerEngine = engine,
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
                    try {
                        slot.playerEngine?.prepare(
                            com.streamvault.domain.model.StreamInfo(url = slot.streamUrl)
                        )
                        slot.playerEngine?.play()
                        updateSlot(index) { it.copy(isLoading = false) }
                    } catch (e: Exception) {
                        updateSlot(index) { it.copy(isLoading = false, hasError = true) }
                    }
                }
            }
        }

        applyFocusAudio(0)
    }

    fun setFocus(slotIndex: Int) {
        _uiState.value = _uiState.value.copy(focusedSlotIndex = slotIndex)
        applyFocusAudio(slotIndex)
    }

    private fun applyFocusAudio(focusedIndex: Int) {
        playerEngines.forEachIndexed { index, engine ->
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
        playerEngines.forEach { it.release() }
        playerEngines.clear()
    }
}
