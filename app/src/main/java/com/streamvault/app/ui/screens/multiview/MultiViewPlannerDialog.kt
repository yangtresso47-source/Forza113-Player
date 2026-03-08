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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamvault.domain.model.Channel

/**
 * The Split Screen Manager dialog.
 *
 * Two modes:
 *  1. [pendingChannel] != null  → slot-picker: pick a slot to place the channel
 *  2. [pendingChannel] == null  → manager: view/clear existing slots, then launch
 */
@Composable
fun MultiViewPlannerDialog(
    pendingChannel: Channel? = null,   // channel being added; null = just managing
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,              // navigate to MultiViewScreen
    viewModel: MultiViewViewModel = hiltViewModel()
) {
    val slots by viewModel.slotsFlow.collectAsState()
    val isPickerMode = pendingChannel != null
    val hasAny = slots.any { it != null }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12121F))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // ── Header ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPickerMode) "🔳  Choose a slot" else "🔳  Split Screen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isPickerMode) {
                        TextButton(onClick = {
                            viewModel.clearAll()
                            onDismiss()
                        }) {
                            Text("Clear All", color = Color(0xFFFF5252), fontSize = 13.sp)
                        }
                    }
                }

                if (isPickerMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adding: ${pendingChannel!!.name}",
                        color = Color(0xFF90CAF9),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── 2×2 Slot Grid ─────────────────────────────────────
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
                                    isPickerMode = isPickerMode,
                                    modifier = Modifier.weight(1f),
                                    onSlotClick = {
                                        if (isPickerMode && pendingChannel != null) {
                                            viewModel.assignChannelToSlot(slotIndex, pendingChannel)
                                            onDismiss()
                                        }
                                    },
                                    onClearSlot = { viewModel.clearSlot(slotIndex) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Bottom actions ─────────────────────────────────────
                if (!isPickerMode) {
                    Button(
                        onClick = onLaunch,
                        enabled = hasAny,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF2A3A2A)
                        )
                    ) {
                        Text(
                            text = if (hasAny) "▶  Start Split Screen" else "Add channels to slots first",
                            color = Color.White
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF888888))
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
    val borderColor = when {
        isPickerMode && !isEmpty -> Color(0xFFFF9800) // orange = "Replace"
        isPickerMode && isEmpty  -> Color(0xFF4CAF50) // green  = "Add here"
        else -> Color(0xFF333355)
    }
    val bgColor = if (isEmpty) Color(0xFF1A1A30) else Color(0xFF1E2A3A)

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = if (isPickerMode) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = isPickerMode) { onSlotClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isEmpty) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", color = Color(0xFF555577), fontSize = 22.sp)
                Text(
                    text = if (isPickerMode) "Add here" else "Empty",
                    color = Color(0xFF555577),
                    fontSize = 11.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = occupant!!.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
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
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                    if (isPickerMode) {
                        Text(
                            text = "Replace",
                            color = Color(0xFFFF9800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        IconButton(
                            onClick = onClearSlot,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Remove",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
