package com.streamvault.app.ui.screens.multiview

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.streamvault.app.R
import com.streamvault.app.ui.theme.*
import androidx.tv.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource

@Composable
fun MultiViewScreen(
    onBack: () -> Unit,
    viewModel: MultiViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstSlotFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.initSlots()
        try {
            // Force focus on first slot after a tiny delay for inflation
            kotlinx.coroutines.delay(100)
            firstSlotFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Row 1
            Row(modifier = Modifier.weight(1f)) {
                PlayerCell(
                    slot = uiState.slots.getOrNull(0),
                    isFocused = uiState.focusedSlotIndex == 0,
                    showSelectionBorder = uiState.showSelectionBorder,
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(firstSlotFocusRequester),
                    onFocused = { viewModel.setFocus(0) }
                )
                PlayerCell(
                    slot = uiState.slots.getOrNull(1),
                    isFocused = uiState.focusedSlotIndex == 1,
                    showSelectionBorder = uiState.showSelectionBorder,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocused = { viewModel.setFocus(1) }
                )
            }
            // Row 2
            Row(modifier = Modifier.weight(1f)) {
                PlayerCell(
                    slot = uiState.slots.getOrNull(2),
                    isFocused = uiState.focusedSlotIndex == 2,
                    showSelectionBorder = uiState.showSelectionBorder,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocused = { viewModel.setFocus(2) }
                )
                PlayerCell(
                    slot = uiState.slots.getOrNull(3),
                    isFocused = uiState.focusedSlotIndex == 3,
                    showSelectionBorder = uiState.showSelectionBorder,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocused = { viewModel.setFocus(3) }
                )
            }
        }

        // Focused slot label at bottom center
        val focused = uiState.slots.getOrNull(uiState.focusedSlotIndex)
        if (focused != null && focused.title.isNotBlank()) {
            Text(
                text = "▶ ${focused.title}",
                color = Color(0xFF4CAF50),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerCell(
    slot: MultiViewSlot?,
    isFocused: Boolean,
    showSelectionBorder: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit
) {
    val showBorder = isFocused && showSelectionBorder
    
        Surface(
            onClick = { /* Could open full screen? */ },
            modifier = modifier
                .padding(2.dp)
                .onFocusChanged { if (it.isFocused) onFocused() },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (showBorder) 4.dp else 0.dp,
                        color = if (showBorder) Color.White else Color.Transparent
                    )
                ),
                focusedBorder = Border.None,
                pressedBorder = Border.None
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF111111),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF111111)
            ),
            shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
        when {
            slot == null || slot.isEmpty -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", color = Color(0xFF555555), fontSize = 32.sp)
                    Text(
                        text = stringResource(R.string.multiview_empty_slot),
                        color = Color(0xFF555555),
                        fontSize = 12.sp
                    )
                }
            }
            slot.isLoading -> {
                CircularProgressIndicator(
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
            }
            slot.hasError -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", color = Color(0xFFFF5252), fontSize = 24.sp)
                    Text(
                        text = stringResource(R.string.multiview_stream_error),
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                // ✅ Attach ExoPlayer to a Media3 PlayerView — this is what actually renders video
                val engine = slot.playerEngine
                if (engine != null) {
                    val exoPlayer = engine.getPlayerView()
                    if (exoPlayer is androidx.media3.common.Player) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    // CRITICAL: Ensure view doesn't steal focus
                                    isFocusable = false
                                    isFocusableInTouchMode = false
                                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                }
                            },
                            update = { view ->
                                if (view.player != exoPlayer) {
                                    view.player = exoPlayer
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }



                // Speaker icon overlay (only when focused and has channel)
                if (isFocused && !slot.isEmpty) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔊",
                                color = Primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Title overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Text(
                        text = slot.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
}
