package com.streamvault.app.ui.screens.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.model.DecoderMode
import com.streamvault.domain.model.StreamInfo
import com.streamvault.domain.model.VideoFormat
import com.streamvault.domain.model.Program
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.player.PlaybackState
import com.streamvault.player.PlayerEngine
import com.streamvault.player.PlayerError
import com.streamvault.player.PlayerTrack
import com.streamvault.player.TrackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.streamvault.app.ui.components.dialogs.ProgramHistoryDialog
import androidx.compose.ui.res.stringResource
import com.streamvault.app.R
import com.streamvault.app.ui.screens.multiview.MultiViewViewModel
import com.streamvault.app.ui.screens.multiview.MultiViewPlannerDialog
import com.streamvault.app.navigation.Routes



@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    epgChannelId: String? = null,
    internalChannelId: Long = -1L,
    categoryId: Long? = null,
    providerId: Long? = null,
    isVirtual: Boolean = false,
    contentType: String = "LIVE",
    onBack: () -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    multiViewViewModel: MultiViewViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playerEngine.playbackState.collectAsState()
    val isPlaying by viewModel.playerEngine.isPlaying.collectAsState()
    val showControls by viewModel.showControls.collectAsState()
    val videoFormat by viewModel.videoFormat.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val currentProgram by viewModel.currentProgram.collectAsState()
    val nextProgram by viewModel.nextProgram.collectAsState()
    val programHistory by viewModel.programHistory.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val showZapOverlay by viewModel.showZapOverlay.collectAsState()
    val resumePrompt by viewModel.resumePrompt.collectAsState()
    
    val showChannelListOverlay by viewModel.showChannelListOverlay.collectAsState()
    val showEpgOverlay by viewModel.showEpgOverlay.collectAsState()
    val currentChannelList by viewModel.currentChannelList.collectAsState()
    val displayChannelNumber by viewModel.displayChannelNumber.collectAsState()
    val upcomingPrograms by viewModel.upcomingPrograms.collectAsState()
    val showChannelInfoOverlay by viewModel.showChannelInfoOverlay.collectAsState()
    
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsState()
    val availableSubtitleTracks by viewModel.availableSubtitleTracks.collectAsState()
    val availableVideoQualities by viewModel.availableVideoQualities.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val showDiagnostics by viewModel.showDiagnostics.collectAsState()
    val playerStats by viewModel.playerStats.collectAsState()
    val currentPosition by viewModel.playerEngine.currentPosition.collectAsState()
    val duration by viewModel.playerEngine.duration.collectAsState()

    var showTrackSelection by remember { mutableStateOf<TrackType?>(null) }
    var showProgramHistory by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val channelInfoFocusRequester = remember { FocusRequester() } // NEW
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Consolidated focus management for all overlays
    val anyOverlayVisible = showChannelListOverlay || showEpgOverlay || showChannelInfoOverlay || showTrackSelection != null || showProgramHistory || showSplitDialog || showZapOverlay || showDiagnostics
    
    LaunchedEffect(anyOverlayVisible) {
        if (anyOverlayVisible) {
            // Give overlays a moment to animate in before requesting focus
            delay(150)
            try {
                when {
                    showChannelListOverlay -> channelListFocusRequester.requestFocus()
                    showChannelInfoOverlay -> channelInfoFocusRequester.requestFocus()
                    // EPG and Dialogs usually handle their own initial focus or use their own re-composition logic
                }
            } catch (_: Exception) {}
        } else {
            // Restore focus to main player when all overlays are gone
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    // Show resolution overlay temporarily when it changes
    var showResolution by remember { mutableStateOf(false) }
    
    LaunchedEffect(videoFormat) {
        if (!videoFormat.isEmpty) {
            showResolution = true
            delay(3000)
            showResolution = false
        }
    }

    if (showProgramHistory) {
        ProgramHistoryDialog(
            programs = programHistory,
            onDismiss = { showProgramHistory = false },
            onProgramSelect = { program ->
                viewModel.playCatchUp(program)
                showProgramHistory = false
            }
        )
    }

    // Split Screen Manager dialog
    if (showSplitDialog && currentChannel != null) {
        MultiViewPlannerDialog(
            pendingChannel = currentChannel,
            onDismiss = { showSplitDialog = false },
            onLaunch = {
                showSplitDialog = false
                onNavigate?.invoke(Routes.MULTI_VIEW)
            },
            viewModel = multiViewViewModel
        )
    }

    LaunchedEffect(streamUrl, epgChannelId) {
        viewModel.prepare(streamUrl, epgChannelId, internalChannelId, categoryId ?: -1, providerId ?: -1, isVirtual, contentType)
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(100)
            try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
            viewModel.hideControlsAfterDelay()
        } else {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusProperties {
                // Only allow focus on the main background when no overlays are active
                canFocus = !anyOverlayVisible
            }
            .focusable()
            .onKeyEvent { event ->
                // Only handle KeyDown to avoid double actions
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (contentType == "LIVE") {
                                if (showChannelInfoOverlay) viewModel.closeChannelInfoOverlay()
                                else viewModel.openChannelInfoOverlay()
                            } else {
                                viewModel.toggleControls()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (contentType == "LIVE" && !showChannelListOverlay && !showEpgOverlay && !showChannelInfoOverlay) {
                                if (isRtl) viewModel.openEpgOverlay() else viewModel.openChannelListOverlay()
                                true
                            } else if (!showChannelListOverlay && !showEpgOverlay && !showChannelInfoOverlay) {
                                if (isRtl) viewModel.seekForward() else viewModel.seekBackward()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (contentType == "LIVE" && !showChannelListOverlay && !showEpgOverlay && !showChannelInfoOverlay) {
                                if (isRtl) viewModel.openChannelListOverlay() else viewModel.openEpgOverlay()
                                true
                            } else if (!showChannelListOverlay && !showEpgOverlay && !showChannelInfoOverlay) {
                                if (isRtl) viewModel.seekBackward() else viewModel.seekForward()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showChannelListOverlay && !showEpgOverlay) {
                                viewModel.playNext()
                                true
                            } else {
                                // Let the overlay LazyColumn handle this event
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showChannelListOverlay && !showEpgOverlay) {
                                viewModel.playPrevious()
                                true
                            } else {
                                // Let the overlay LazyColumn handle this event
                                false
                            }
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (showChannelInfoOverlay) {
                                viewModel.closeChannelInfoOverlay()
                                true
                            } else if (showChannelListOverlay || showEpgOverlay) {
                                viewModel.closeOverlays()
                                true
                            } else if (showTrackSelection != null) {
                                showTrackSelection = null
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (isPlaying) viewModel.pause() else viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                             viewModel.playNext()
                             true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                             viewModel.playPrevious()
                             true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_0 -> {
                             viewModel.zapToLastChannel()
                             true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // ExoPlayer Video Surface
        val player = viewModel.playerEngine.getPlayerView()
        if (player is androidx.media3.common.Player) {
            AndroidView<androidx.media3.ui.PlayerView>(
                factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = when (aspectRatio) {
                        AspectRatio.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatio.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatio.ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffering indicator
        if (playbackState == PlaybackState.BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.player_buffering),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }

        // Error overlay
        if (playbackState == PlaybackState.ERROR) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ " + stringResource(R.string.player_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = ErrorColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show specific error message based on error type
                    val errorMessage = when (playerError) {
                        is PlayerError.NetworkError -> 
                            stringResource(R.string.player_error_network)
                        is PlayerError.SourceError -> 
                            stringResource(R.string.player_error_source)
                        is PlayerError.DecoderError -> 
                            stringResource(R.string.player_error_decoder)
                        is PlayerError.UnknownError -> 
                            playerError?.message ?: stringResource(R.string.player_error_unknown)
                        null -> stringResource(R.string.player_error_unknown)
                    }
                    
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Retry button
                    Surface(
                        onClick = { viewModel.retryStream(streamUrl, epgChannelId) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Primary,
                            focusedContainerColor = PrimaryVariant
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.player_retry),
                            color = OnBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.player_error_back),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
            }
        }

        // Cinematic Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Gradient & Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (contentType != "LIVE") {
                                Text(
                                    text = if (contentType == "MOVIE") stringResource(R.string.player_type_movie) else stringResource(R.string.player_type_series),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // System Clock
                            val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
                            LaunchedEffect(Unit) {
                                while(true) {
                                    delay(10000)
                                    currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                }
                            }
                            Text(
                                text = currentTime.value,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(end = 24.dp)
                            )

                            // Exit Button
                            Surface(
                                onClick = onBack,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Primary.copy(alpha = 0.9f)
                                )
                            ) {
                                Text(
                                    text = "✕ " + stringResource(R.string.player_close),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Gradient & Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp, vertical = 32.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (contentType == "LIVE" && currentProgram != null) {
                            // Live TV Program Info
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentProgram?.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    currentChannel?.let {
                                        Text(
                                            text = "$displayChannelNumber. ${it.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                // Next Program Preview Removed (Moved to EPG Overlay)
                                
                                // Track selection buttons shifted here for Live
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (currentChannel?.catchUpSupported == true) {
                                        QuickSettingsButton("🔄 " + stringResource(R.string.player_restart)) { viewModel.restartCurrentProgram() }
                                        QuickSettingsButton("📼 " + stringResource(R.string.player_archive)) { showProgramHistory = true }
                                    }
                                    QuickSettingsButton("📺 ${aspectRatio.modeName}") { viewModel.toggleAspectRatio() }
                                    if (availableSubtitleTracks.isNotEmpty()) {
                                        QuickSettingsButton("💬 " + stringResource(R.string.player_subs)) { showTrackSelection = TrackType.TEXT }
                                    }
                                    if (availableAudioTracks.size > 1) {
                                        QuickSettingsButton("🔊 " + stringResource(R.string.player_audio)) { showTrackSelection = TrackType.AUDIO }
                                    }
                                    if (availableVideoQualities.size > 1) {
                                        QuickSettingsButton("⚙ HD") { showTrackSelection = TrackType.VIDEO }
                                    }
                                    // Split Screen button — available in LIVE mode
                                    QuickSettingsButton("🔳 " + stringResource(R.string.multiview_nav)) { showSplitDialog = true }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Progress bar for Live TV
                            val now = System.currentTimeMillis()
                            val start = currentProgram?.startTime ?: 0
                            val end = currentProgram?.endTime ?: 0
                            if (start > 0 && end > 0) {
                                val progress = (now - start).toFloat() / (end - start)
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Primary,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(end)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else if (contentType != "LIVE") {
                            // VOD Seek Bar
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatDuration(currentPosition),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                                Slider(
                                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                    onValueChange = { /* Handled via DPAD usually */ },
                                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Content info for VOD
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                // Track selection for VOD
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QuickSettingsButton("📺 ${aspectRatio.modeName}") { viewModel.toggleAspectRatio() }
                                    if (availableSubtitleTracks.isNotEmpty()) {
                                        QuickSettingsButton("💬 " + stringResource(R.string.player_subs)) { showTrackSelection = TrackType.TEXT }
                                    }
                                    if (availableAudioTracks.size > 1) {
                                        QuickSettingsButton("🔊 " + stringResource(R.string.player_audio)) { showTrackSelection = TrackType.AUDIO }
                                    }
                                    if (availableVideoQualities.size > 1) {
                                        // VODs might have multiple qualities in rare cases if the provider provides them as separate stream IDs and we grouped them, though our grouping targets Live channels mostly.
                                        QuickSettingsButton("⚙ HD") { showTrackSelection = TrackType.VIDEO }
                                    }
                                }
                            }
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (contentType != "LIVE") {
                        TransportButton("⏪") { viewModel.seekBackward() }
                    }
                    
                    if (contentType != "LIVE") {
                        Surface(
                            onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Primary.copy(alpha = 0.8f),
                                focusedContainerColor = Primary
                            ),
                            modifier = Modifier
                                .size(80.dp)
                                .focusRequester(playButtonFocusRequester)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = if (isPlaying) "⏸" else "▶",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (contentType != "LIVE") {
                        TransportButton("⏩") { viewModel.seekForward() }
                    }
                }
            }
        }
        
        // Cinematic Zap Overlay
        AnimatedVisibility(
            visible = showZapOverlay && !showControls && currentChannel != null,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
                    .widthIn(min = 300.dp, max = 450.dp)
            ) {
                 Column {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         Text(
                            text = displayChannelNumber.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = currentChannel?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            if (currentProgram != null) {
                                Text(
                                    text = currentProgram?.title ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                     }
                 }
            }
         }
         
         // Aspect Ratio confirmation Toast
         var showAspectRatioToast by remember { mutableStateOf(false) }
         LaunchedEffect(aspectRatio) {
             showAspectRatioToast = true
             delay(2000)
             showAspectRatioToast = false
         }
         
         AnimatedVisibility(
             visible = showAspectRatioToast && !showControls,
             enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
             exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
             modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
         ) {
             Box(
                 modifier = Modifier
                     .background(Primary.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                     .padding(horizontal = 24.dp, vertical = 12.dp)
             ) {
                 Text(
                     text = "Aspect Ratio: ${aspectRatio.modeName}",
                     style = MaterialTheme.typography.titleMedium,
                     color = Color.White,
                     fontWeight = FontWeight.Bold
                 )
             }
         }
         
         // EPG Overlay removed (Unified into side overlay and bottom info overlay)

        // Resolution Overlay (Top Right)
        if (showResolution && !showControls && !videoFormat.isEmpty) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = videoFormat.resolutionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
        
        // Resume Prompt Dialog
        if (resumePrompt.show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.player_resume_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.player_resume_desc, resumePrompt.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            onClick = { viewModel.dismissResumePrompt(resume = false) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = SurfaceVariant,
                                focusedContainerColor = SurfaceHighlight
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.player_resume_start_over),
                                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Color.White
                            )
                        }
                        
                        Surface(
                            onClick = { viewModel.dismissResumePrompt(resume = true) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Primary,
                                focusedContainerColor = PrimaryVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.player_resume),
                                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // Track Selection Dialog
        if (showTrackSelection != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(onClick = { showTrackSelection = null }),
                contentAlignment = Alignment.Center
            ) {
                val tracks = when (showTrackSelection) {
                    TrackType.AUDIO -> availableAudioTracks
                    TrackType.VIDEO -> availableVideoQualities
                    else -> availableSubtitleTracks
                }
                
                val titleRes = when (showTrackSelection) {
                    TrackType.AUDIO -> R.string.player_track_audio
                    TrackType.VIDEO -> R.string.player_video_quality
                    else -> R.string.player_track_subs
                }
                
                Column(
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 400.dp)
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (showTrackSelection == TrackType.VIDEO) "Video Quality" else stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showTrackSelection == TrackType.TEXT) {
                            item {
                                TrackItem(
                                    name = stringResource(R.string.player_track_off),
                                    isSelected = tracks.none { it.isSelected },
                                    onClick = {
                                        viewModel.selectSubtitleTrack(null)
                                        showTrackSelection = null
                                    }
                                )
                            }
                        }
                        
                        items(tracks.size) { index ->
                            val track = tracks[index]
                            TrackItem(
                                name = track.name,
                                isSelected = track.isSelected,
                                onClick = {
                                    when (showTrackSelection) {
                                        TrackType.AUDIO -> viewModel.selectAudioTrack(track.id)
                                        TrackType.VIDEO -> viewModel.selectVideoQuality(track.id)
                                        else -> viewModel.selectSubtitleTrack(track.id)
                                    }
                                    showTrackSelection = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Overlays ---
        if (showDiagnostics) {
            DiagnosticsOverlay(
                stats = playerStats,
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
            )
        }

        AnimatedVisibility(
            visible = showChannelListOverlay,
            enter = slideInHorizontally(initialOffsetX = { if (isRtl) it else -it }),
            exit = slideOutHorizontally(targetOffsetX = { if (isRtl) it else -it }),
            modifier = Modifier
                .align(if (isRtl) Alignment.TopEnd else Alignment.TopStart)
                .fillMaxHeight()
                .width(350.dp)
                .focusGroup()
        ) {
            ChannelListOverlay(
                channels = currentChannelList,
                currentChannelId = internalChannelId,
                overlayFocusRequester = channelListFocusRequester,
                onSelectChannel = { channelId -> viewModel.zapToChannel(channelId) },
                onDismiss = { viewModel.closeOverlays() }
            )
        }

        AnimatedVisibility(
            visible = showEpgOverlay,
            enter = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }),
            exit = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }),
            modifier = Modifier
                .align(if (isRtl) Alignment.TopStart else Alignment.TopEnd)
                .fillMaxHeight()
                .width(400.dp)
                .focusGroup()
        ) {
            EpgOverlay(
                currentChannel = currentChannel,
                displayChannelNumber = displayChannelNumber,
                currentProgram = currentProgram,
                nextProgram = nextProgram,
                upcomingPrograms = upcomingPrograms,
                onDismiss = { viewModel.closeOverlays() },
                onPlayCatchUp = { program -> 
                    viewModel.playCatchUp(program)
                    viewModel.closeOverlays()
                }
            )
        }

        AnimatedVisibility(
            visible = showChannelInfoOverlay,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .focusGroup()
        ) {
            ChannelInfoOverlay(
                currentChannel = currentChannel,
                displayChannelNumber = displayChannelNumber,
                currentProgram = currentProgram,
                nextProgram = nextProgram,
                focusRequester = channelInfoFocusRequester,
                onDismiss = { viewModel.closeChannelInfoOverlay() },
                onOpenFullEpg = {
                    viewModel.closeChannelInfoOverlay()
                    viewModel.openEpgOverlay()
                },
                onOpenFullControls = {
                    viewModel.closeChannelInfoOverlay()
                    viewModel.toggleControls()
                },
                onRestartProgram = { viewModel.restartCurrentProgram() },
                onToggleAspectRatio = { viewModel.toggleAspectRatio() },
                onToggleDiagnostics = { viewModel.toggleDiagnostics() },
                onTogglePlayPause = { if (isPlaying) viewModel.pause() else viewModel.play() },
                isPlaying = isPlaying,
                currentAspectRatio = aspectRatio.modeName,
                isDiagnosticsEnabled = showDiagnostics,
                onOpenSplitScreen = { showSplitDialog = true }
            )
        }
    }
}

@Composable
fun ChannelInfoOverlay(
    currentChannel: com.streamvault.domain.model.Channel?,
    displayChannelNumber: Int,
    currentProgram: Program?,
    nextProgram: Program?,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onOpenFullEpg: () -> Unit,
    onOpenFullControls: () -> Unit,
    onRestartProgram: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onTogglePlayPause: () -> Unit,
    isPlaying: Boolean,
    currentAspectRatio: String,
    isDiagnosticsEnabled: Boolean,
    onOpenSplitScreen: () -> Unit = {}
) {
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .background(
                Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel name & number
            if (currentChannel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$displayChannelNumber",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = currentChannel.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (currentChannel.catchUpSupported) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .background(Primary.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("⏪ Catch-Up", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            // Current program info
            if (currentProgram != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentProgram.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${timeFormat.format(java.util.Date(currentProgram.startTime))} – ${timeFormat.format(java.util.Date(currentProgram.endTime))}  ·  ${currentProgram.durationMinutes} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Progress bar
                    val now = System.currentTimeMillis()
                    val start = currentProgram.startTime
                    val end = currentProgram.endTime
                    if (start in 1..<end) {
                        val progress = (now - start).toFloat() / (end - start)
                        val remainingMin = ((end - now) / 60000).toInt().coerceAtLeast(0)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Primary,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "$remainingMin min remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                }
            }

            // Next program & Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    if (nextProgram != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Next:  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary
                            )
                            Text(
                                text = nextProgram.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = timeFormat.format(java.util.Date(nextProgram.startTime)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Quick action buttons aligned to the right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionButton(
                        icon = if (isPlaying) "⏸" else "▶",
                        label = if (isPlaying) "Pause" else "Play",
                        onClick = onTogglePlayPause,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Primary.copy(alpha = 0.25f),
                            focusedContainerColor = Primary,
                            pressedContainerColor = Primary.copy(alpha = 0.8f)
                        )
                    )
                    if (currentChannel?.catchUpSupported == true && currentProgram?.hasArchive == true) {
                        QuickActionButton(
                            icon = "⏮",
                            label = "Restart",
                            onClick = {
                                onRestartProgram()
                                onDismiss()
                            }
                        )
                    }
                    QuickActionButton(
                        icon = "📺",
                        label = currentAspectRatio,
                        onClick = onToggleAspectRatio,
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                    QuickActionButton(
                        icon = "📋",
                        label = "Full EPG",
                        onClick = onOpenFullEpg
                    )
                    QuickActionButton(
                        icon = "🔳",
                        label = "Split Screen",
                        onClick = {
                            onDismiss()
                            onOpenSplitScreen()
                        }
                    )
                    QuickActionButton(
                        icon = if (isDiagnosticsEnabled) "🛠️" else "📊",
                        label = "Stats",
                        onClick = onToggleDiagnostics
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    colors: androidx.tv.material3.ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.1f),
        focusedContainerColor = Primary.copy(alpha = 0.85f)
    ),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = colors,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "iconToggle"
            ) { targetIcon ->
                Text(targetIcon, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrackItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = SurfaceHighlight
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) Primary else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text("✓", color = Primary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ChannelListOverlay(
    channels: List<com.streamvault.domain.model.Channel>,
    currentChannelId: Long,
    overlayFocusRequester: FocusRequester = remember { FocusRequester() },
    onSelectChannel: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = remember(channels, currentChannelId) {
        channels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
    }
    // Scroll to current item when channels load
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            listState.scrollToItem(currentIndex)
        }
    }

    BackHandler { onDismiss() }

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f)),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Channels (${channels.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
            )
        }
        items(channels.size) { index ->
            val channel = channels[index]
            val isSelected = channel.id == currentChannelId
            var isFocused by remember { mutableStateOf(false) }

            Surface(
                onClick = {
                    onSelectChannel(channel.id)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(
                        if (isSelected) Modifier.focusRequester(overlayFocusRequester)
                        else Modifier
                    ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Primary
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSelected) {
                        Text("▶", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("  ", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (channel.catchUpSupported) {
                        Text("📼", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


@Composable
fun EpgOverlay(
    currentChannel: com.streamvault.domain.model.Channel?,
    displayChannelNumber: Int,
    currentProgram: Program?,
    nextProgram: Program?,
    upcomingPrograms: List<Program>,
    onDismiss: () -> Unit,
    onPlayCatchUp: (Program) -> Unit
) {
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Channel info
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📺  ",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Advanced EPG",
                        style = MaterialTheme.typography.titleLarge,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (currentChannel != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$displayChannelNumber. ${currentChannel.name}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentChannel.catchUpSupported) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "⏪ Catch-Up available (${currentChannel.catchUpDays} days)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Now Playing section
            item {
                androidx.compose.material3.HorizontalDivider(color = SurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (currentProgram != null) {
                    Text(
                        currentProgram.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${timeFormat.format(java.util.Date(currentProgram.startTime))} - ${timeFormat.format(java.util.Date(currentProgram.endTime))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${currentProgram.durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                        if (currentProgram.lang.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentProgram.lang.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Progress bar
                    Spacer(Modifier.height(8.dp))
                    val now = System.currentTimeMillis()
                    val start = currentProgram.startTime
                    val end = currentProgram.endTime
                    if (start in 1..<end) {
                        val progress = (now - start).toFloat() / (end - start)
                        val remainingMin = ((end - now) / 60000).toInt().coerceAtLeast(0)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Primary,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$remainingMin min remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                    // Description
                    if (!currentProgram.description.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            currentProgram.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text("No EPG Information", color = OnSurfaceDim)
                }
            }

            // Upcoming schedule
            if (upcomingPrograms.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider(color = SurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "UPCOMING SCHEDULE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Skip the current program if it appears in upcoming list
                val filteredUpcoming = upcomingPrograms.filter { it.id != currentProgram?.id && it.id != nextProgram?.id }
                val displayPrograms = if (nextProgram != null) listOf(nextProgram) + filteredUpcoming else filteredUpcoming
                items(displayPrograms.size) { index ->
                    val program = displayPrograms[index]
                    val isNext = index == 0 && nextProgram != null
                    
                    Surface(
                        onClick = {
                            if (program.hasArchive || currentChannel?.catchUpSupported == true) {
                                onPlayCatchUp(program)
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isNext) Primary.copy(alpha = 0.08f) else Color.Transparent,
                            focusedContainerColor = Primary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                        if (isNext) {
                            Text(
                                text = "UP NEXT",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            program.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isNext) Color.White else Color.White.copy(alpha = 0.8f),
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row {
                            Text(
                                text = "${timeFormat.format(java.util.Date(program.startTime))} - ${timeFormat.format(java.util.Date(program.endTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${program.durationMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                            if (program.hasArchive) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "📼",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
    }
}
}
}

@Composable
private fun QuickSettingsButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Primary.copy(alpha = 0.9f)
        )
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TransportButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White.copy(alpha = 0.3f)
        ),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(ms))
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, seconds)
    }
}

@Composable
fun DiagnosticsOverlay(
    stats: com.streamvault.player.PlayerStats,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Player Diagnostics", color = Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Resolution: ${stats.width}x${stats.height}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("Video Codec: ${stats.videoCodec}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("Audio Codec: ${stats.audioCodec}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("Video Bitrate: ${stats.videoBitrate / 1000} kbps", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("Dropped Frames: ${stats.droppedFrames}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}
