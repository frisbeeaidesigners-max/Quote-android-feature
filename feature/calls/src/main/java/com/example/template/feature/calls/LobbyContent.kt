package com.example.template.feature.calls

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.model.AvatarSpec
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appClickable

/**
 * UI лобби — контент ВНУТРИ [LobbyBottomSheet]. Sheet занимает весь экран; video (имитация
 * self-камеры) — fullscreen фон ВНУТРИ sheet'а, controls (header + Join + toggles) overlay'ятся
 * поверх.
 *
 * Layout (Box-positioned):
 * - Video fullscreen background (через [CallVideoPlayer]).
 * - Off-state аватарка [CallAvatarOverlay] — по центру **видимой зоны между header'ом и нижним
 *   рядом кнопок**. Padding'и: top 60dp под header, bottom 160dp + navigationBarsPadding под
 *   toggles+Join.
 * - Header row (X | Title | Flip) — top, padding top 24dp учитывает drag-handle, рисуемый ПОВЕРХ
 *   content'а в [LobbyBottomSheet].
 * - Join button + toggles row — bottom, navigationBarsPadding.
 *
 * `videoActive=false` (на переходе Lobby → InCall) → PlayerView не монтируется, sheet exit'ит со
 * статичным фоном.
 *
 * Кнопки [CircleIconButton] и [ToggleButton] — общие из [CallChrome] (одинаковые стили во всех
 * call-экранах).
 */
@OptIn(UnstableApi::class)
@Composable
fun LobbyContent(
    title: String,
    toggles: CallToggles,
    selfAvatar: AvatarSpec,
    player: ExoPlayer,
    videoActive: Boolean,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onJoin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val accent = Color(brand.accentColor(isDark))

    Box(modifier = modifier.fillMaxSize()) {
        // Video fullscreen — фон. cameraOn управляет play/pause и backdrop'ом внутри.
        // Rounded top 16dp матчит скругление sheet'а.
        if (videoActive) {
            CallVideoPlayer(
                player = player,
                cameraOn = toggles.camera,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )
        }
        // Off-state аватарка — центрируется между header'ом и нижним рядом.
        if (!toggles.camera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(top = 60.dp, bottom = 160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CallAvatarOverlay(spec = selfAvatar)
            }
        }

        // Header: X | Title | Flip. Padding top 24dp под drag-handle поверх content'а.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 8.dp, end = 8.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(iconName = "close", onClick = onClose)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    style = DSTypography.body3M.toComposeTextStyle(),
                    maxLines = 1,
                )
            }
            CircleIconButton(iconName = "flip", onClick = { /* no-op V1 */ })
        }

        // Toggles row (bottom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            ToggleButton(
                iconName = if (toggles.camera) "video-on-call-filled" else "video-off-call-filled",
                isActive = toggles.camera,
                onClick = onToggleCamera,
            )
            Spacer(Modifier.width(64.dp))
            ToggleButton(
                iconName = "volume",
                isActive = toggles.speaker,
                onClick = onToggleSpeaker,
            )
            Spacer(Modifier.width(64.dp))
            ToggleButton(
                iconName = if (toggles.mic) "microphone-on" else "microphone-off",
                isActive = toggles.mic,
                onClick = onToggleMic,
            )
        }

        // Join — расположена над toggle-рядом
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp + 56.dp + 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                // Ширина = ширине toggles-ряда ниже: 3 кнопки по 56dp + 2 spacer'а по 64dp = 296dp.
                modifier = Modifier
                    .width(296.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(1000.dp))
                    .background(accent)
                    .appClickable(onClick = onJoin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Присоединиться",
                    color = Color.White,
                    style = DSTypography.subtitle1M.toComposeTextStyle().copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                    ),
                )
            }
        }
    }
}
