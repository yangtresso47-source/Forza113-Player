package com.streamvault.app.ui.screens.multiview

import com.streamvault.domain.model.Channel
import com.streamvault.player.PlayerEngine

/**
 * Represents a single player slot in the 2x2 Multi-View grid.
 */
data class MultiViewSlot(
    val index: Int,
    val channel: Channel? = null,
    val streamUrl: String = "",
    val title: String = "",
    val playerEngine: PlayerEngine? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
) {
    val isEmpty: Boolean get() = channel == null
}

data class MultiViewUiState(
    val slots: List<MultiViewSlot> = List(4) { MultiViewSlot(index = it) },
    val focusedSlotIndex: Int = 0,
    val isLaunching: Boolean = false
)
