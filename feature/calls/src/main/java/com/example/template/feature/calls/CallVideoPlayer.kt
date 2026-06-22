package com.example.template.feature.calls

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.template.feature.calls.R

/**
 * Видео-плеер для outgoing-call экрана. Циклит mock-видео из app-assets, mute'd. Когда
 * `cameraOn=false` — pause + тёмный backdrop поверх (#1A1A1A).
 *
 * Аватарка off-state рендерится НЕ здесь, а в [LobbyContent]/[InCallContent] поверх плеера —
 * там она знает свою chrome'у (header сверху, кнопки снизу) и центрируется между ними,
 * а не по середине fillMaxSize. См. [CallAvatarOverlay].
 *
 * Плеер передаётся СНАРУЖИ (создаётся в OutgoingCallScreen и переиспользуется между
 * Lobby/InCall) — здесь только биндим к PlayerView и управляем playWhenReady.
 *
 * **PlayerView инфлейтится из XML** (`R.layout.call_player_view`) с `surface_type=texture_view`.
 * Это критично для rounded-corner PiP (state 4 в P2P): Compose `Modifier.clip` обрезает
 * только `TextureView`, а дефолтная `SurfaceView` рисуется hardware overlay'ем и игнорит клип.
 */
@OptIn(UnstableApi::class)
@Composable
fun CallVideoPlayer(
    player: ExoPlayer,
    cameraOn: Boolean,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(player, cameraOn) {
        player.playWhenReady = cameraOn
        onDispose { /* плеер не отпускаем здесь — owner OutgoingCallScreen */ }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.call_player_view, null) as PlayerView
                // Дублируем resizeMode программно — на ряде версий Media3 XML-атрибут
                // resize_mode применяется не всегда, и без этой страховки видео получало
                // FIT и чёрные полосы по краям контейнера, когда aspect-ratio не совпадает.
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                view.setPlayer(player)
                view
            },
            update = { v -> v.player = player },
        )
        if (!cameraOn) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        }
    }
}
