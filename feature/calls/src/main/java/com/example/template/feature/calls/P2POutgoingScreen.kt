package com.example.template.feature.calls

import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.CallContext
import com.example.template.core.ui.utils.performStrongHaptic

private const val ASSET_SELF_VIDEO_URI = "asset:///videos/My_9_16.mp4"

/**
 * P2P outgoing call screen — fullscreen fade-in (без Lobby). Создаёт VM + selfPlayer,
 * mount-during-exit для CallFadeOverlay'я, координирует isClosing → onClose.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun P2POutgoingScreen(
    context: CallContext.P2PCall,
    selfAvatar: AvatarSpec,
    repository: MessengerRepository,
    onClose: () -> Unit,
) {
    val viewModel = remember(context, repository) {
        OutgoingCallViewModel(context = context, repository = repository)
    }
    val toggles by viewModel.toggles.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val callConnected by viewModel.callConnected.collectAsState()
    val someoneCameraOn by viewModel.someoneCameraOn.collectAsState()

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

    // Вибрация в момент "поднятия трубки" — старт таймера. На false-старте
    // (mount при callConnected=false) ничего не делаем, реагируем только на
    // false→true переход после ringingJob (RINGING_DELAY_MS) в VM.
    LaunchedEffect(callConnected) {
        if (callConnected) performStrongHaptic(androidCtx)
    }

    var isClosing by remember { mutableStateOf(false) }
    var closedReported by remember { mutableStateOf(false) }
    var incallMounted by remember { mutableStateOf(true) }

    LaunchedEffect(isClosing, incallMounted) {
        if (isClosing && !incallMounted && !closedReported) {
            closedReported = true
            onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (incallMounted) {
            CallFadeOverlay(
                visible = !isClosing,
                onExitComplete = { incallMounted = false },
            ) {
                P2PInCallContent(
                    counterpartName = context.historyTarget.name,
                    counterpartAvatar = context.historyTarget.avatar,
                    callConnected = callConnected,
                    elapsedMs = elapsedMs,
                    toggles = toggles,
                    someoneCameraOn = someoneCameraOn,
                    selfPlayer = selfPlayer,
                    onToggleCamera = viewModel::onToggleCamera,
                    onToggleMic = viewModel::onToggleMic,
                    onToggleSpeaker = viewModel::onToggleSpeaker,
                    onToggleSomeoneCamera = viewModel::onToggleSomeoneCamera,
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
