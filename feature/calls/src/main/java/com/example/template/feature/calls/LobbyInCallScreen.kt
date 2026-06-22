package com.example.template.feature.calls

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.CallContext

private const val ASSET_SELF_VIDEO_URI = "asset:///videos/My_9_16.mp4"

/**
 * Outgoing call для **Group/BrandMeet** — Lobby (sheet) → InCall (slide-in справа). Между фазами
 * параллельные анимации: sheet exit'ит вниз, InCall заезжает справа. Player общий через [selfPlayer].
 *
 * mount-during-exit: оба overlay'я удерживаются в композиции до завершения своих exit-анимаций.
 * [onClose] вызывается ровно один раз, когда `isClosing=true && !sheetMounted && !incallMounted`
 * (guard через `closedReported`).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LobbyInCallScreen(
    context: CallContext,
    selfAvatar: AvatarSpec,
    repository: MessengerRepository,
    onClose: () -> Unit,
) {
    require(context !is CallContext.P2PCall) { "LobbyInCallScreen не для P2P — используй P2POutgoingScreen" }

    val viewModel = remember(context, repository) {
        OutgoingCallViewModel(context = context, repository = repository)
    }
    val phase by viewModel.phase.collectAsState()
    val toggles by viewModel.toggles.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()

    val androidCtx = LocalContext.current
    val selfPlayer = remember(androidCtx) {
        ExoPlayer.Builder(androidCtx).build().apply {
            setMediaItem(MediaItem.fromUri(ASSET_SELF_VIDEO_URI))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(selfPlayer) {
        onDispose { selfPlayer.release() }
    }

    var isClosing by remember { mutableStateOf(false) }
    var closedReported by remember { mutableStateOf(false) }
    val isLobby = phase is CallPhase.Lobby && !isClosing
    val isInCall = phase is CallPhase.InCall && !isClosing

    var sheetMounted by remember { mutableStateOf(phase is CallPhase.Lobby) }
    var incallMounted by remember { mutableStateOf(phase is CallPhase.InCall) }
    LaunchedEffect(isLobby) { if (isLobby) sheetMounted = true }
    LaunchedEffect(isInCall) { if (isInCall) incallMounted = true }

    LaunchedEffect(isClosing, sheetMounted, incallMounted) {
        if (isClosing && !sheetMounted && !incallMounted && !closedReported) {
            closedReported = true
            onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (sheetMounted) {
            LobbyBottomSheet(
                visible = isLobby,
                onDismiss = {
                    viewModel.onDismissWithoutRecording()
                    isClosing = true
                },
                onExitComplete = { sheetMounted = false },
            ) {
                LobbyContent(
                    title = context.title,
                    toggles = toggles,
                    selfAvatar = selfAvatar,
                    player = selfPlayer,
                    videoActive = isLobby,
                    onToggleCamera = viewModel::onToggleCamera,
                    onToggleMic = viewModel::onToggleMic,
                    onToggleSpeaker = viewModel::onToggleSpeaker,
                    onJoin = viewModel::onJoin,
                    onClose = {
                        viewModel.onDismissWithoutRecording()
                        isClosing = true
                    },
                )
            }
        }

        if (incallMounted) {
            InCallSlideOverlay(
                visible = isInCall,
                onExitComplete = { incallMounted = false },
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
                    CallVideoPlayer(
                        player = selfPlayer,
                        cameraOn = toggles.camera,
                        modifier = Modifier.fillMaxSize(),
                    )
                    InCallContent(
                        title = context.title,
                        elapsedMs = elapsedMs,
                        toggles = toggles,
                        selfAvatar = selfAvatar,
                        onToggleCamera = viewModel::onToggleCamera,
                        onToggleMic = viewModel::onToggleMic,
                        onToggleSpeaker = viewModel::onToggleSpeaker,
                        onCollapse = {
                            viewModel.onDismissWithoutRecording()
                            isClosing = true
                        },
                        onEndCall = {
                            viewModel.onEndCall()
                            isClosing = true
                        },
                    )
                }
            }
        }
    }
}
