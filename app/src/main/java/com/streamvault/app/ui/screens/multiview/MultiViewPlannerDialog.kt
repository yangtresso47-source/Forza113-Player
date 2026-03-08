package com.streamvault.app.ui.screens.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.streamvault.domain.model.Channel

/**
 * Split Screen Manager dialog — two modes:
 *
 *  • Picker mode  [pendingChannel] != null — user chose "Add to Split Screen" from long-press.
 *    Shows the 2×2 grid; tapping a slot places the channel and STAYS OPEN so they can
 *    hit "Start Split Screen" right away.
 *
 *  • Manager mode [pendingChannel] == null — opened from the sidebar button or player overlay.
 *    Shows all 4 slots with individual remove buttons + Launch button.
 */
@Composable
fun MultiViewPlannerDialog(
    pendingChannel: Channel? = null,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    viewModel: MultiViewViewModel = hiltViewModel()
) {
    val slots by viewModel.slotsFlow.collectAsState()
    val isPickerMode = pendingChannel != null
    val hasAny = slots.any { it != null }

    // Track whether channel was just placed (picker mode only)
    var channelPlaced by remember { mutableStateOf(false) }
    val firstSlotFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        // Ensure focus starts on a slot, not a button below
        try {
            kotlinx.coroutines.delay(150)
            firstSlotFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12121F))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // ── Header ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔳  Split Screen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isPickerMode || channelPlaced) {
                        TextButton(onClick = {
                            viewModel.clearAll()
                            onDismiss()
                        }) {
                            Text("Clear All", color = Color(0xFFFF5252), fontSize = 13.sp)
                        }
                    }
                }

                // Subtitle
                if (isPickerMode && !channelPlaced) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick a slot for: ${pendingChannel!!.name}",
                        color = Color(0xFF90CAF9),
                        fontSize = 13.sp
                    )
                } else if (channelPlaced) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✓ ${pendingChannel?.name} added",
                        color = Color(0xFF81C784),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── 2×2 Slot Grid ──────────────────────────────────
                val stillPicking = isPickerMode && !channelPlaced
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in 0 until 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0 until 2) {
                                val slotIndex = row * 2 + col
                                val occupant = slots.getOrNull(slotIndex)
                                SlotCell(
                                    slotIndex = slotIndex,
                                    occupant = occupant,
                                    isPickerMode = stillPicking,
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (slotIndex == 0) Modifier.focusRequester(firstSlotFocusRequester) else Modifier),
                                    onSlotClick = {
                                        if (stillPicking && pendingChannel != null) {
                                            viewModel.assignChannelToSlot(slotIndex, pendingChannel)
                                            channelPlaced = true   // stay open
                                        }
                                    },
                                    onClearSlot = { viewModel.clearSlot(slotIndex) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Bottom actions ──────────────────────────────────
                val showLaunch = !isPickerMode || channelPlaced
                if (showLaunch) {
                    androidx.tv.material3.Button(
                        onClick = onLaunch,
                        enabled = hasAny,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color(0xFF1B5E20),
                            focusedContainerColor = Color(0xFF2E7D32),
                            disabledContainerColor = Color(0xFF1A2E1A)
                        ),
                        border = androidx.tv.material3.ButtonDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White)
                            )
                        ),
                        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = if (hasAny) "▶  Start Split Screen" else "Add channels to slots first",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPickerMode || channelPlaced) {
                            androidx.tv.material3.Button(
                                onClick = {
                                    viewModel.clearAll()
                                    onDismiss()
                                },
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color(0xFFB71C1C)
                                ),
                                border = androidx.tv.material3.ButtonDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                    )
                                )
                            ) {
                                Text("Clear All", color = Color.White, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        androidx.tv.material3.Button(
                            onClick = onDismiss,
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color(0xFF455A64)
                            ),
                            border = androidx.tv.material3.ButtonDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                                )
                            )
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                } else {
                    // Still picking — just a cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.tv.material3.Button(
                            onClick = onDismiss,
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color(0x22FFFFFF)
                            )
                        ) {
                            Text("Cancel", color = Color(0xFFCCCCCC))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    slotIndex: Int,
    occupant: Channel?,
    isPickerMode: Boolean,
    modifier: Modifier = Modifier,
    onSlotClick: () -> Unit,
    onClearSlot: () -> Unit
) {
    val isEmpty = occupant == null
    
    val baseBorderColor = when {
        isPickerMode && !isEmpty -> Color(0xFFFF9800)
        isPickerMode && isEmpty  -> Color(0xFF4CAF50)
        !isEmpty                 -> Color(0xFF2D4A6E)
        else                     -> Color(0xFF333355)
    }

    androidx.tv.material3.Surface(
        onClick = { if (isPickerMode) onSlotClick() else onClearSlot() },
        modifier = modifier
            .aspectRatio(16f / 9f),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (isEmpty) Color(0xFF1A1A30) else Color(0xFF1E2A3A),
            focusedContainerColor = if (isEmpty) Color(0xFF3B3B60) else Color(0xFF3E5A7A),
            pressedContainerColor = Color(0xFF4CAF50)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, baseBorderColor.copy(alpha = 0.5f))
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White)
            )
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isEmpty) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "＋",
                        color = if (isPickerMode) Color(0xFF4CAF50) else Color(0xFF555577),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPickerMode) "Add here" else "Empty",
                        color = if (isPickerMode) Color(0xFF81C784) else Color(0xFF555577),
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = occupant!!.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slot ${slotIndex + 1}",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                        if (isPickerMode) {
                            Text(
                                text = "Replace",
                                color = Color(0xFFFF9800),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        } else {
                            Text(
                                text = "✕ Clear",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
