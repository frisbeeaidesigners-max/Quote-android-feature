package com.example.template.feature.calls

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.model.AvatarSpec
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark

/**
 * UI исходящего P2P-звонка. Рендерится внутри [CallFadeOverlay] — fade-in/out.
 *
 * 4 комбинации камер (self × counterpart):
 * 1. **OFF × OFF** — тёмный фон + центральная аватарка counterpart-а с именем и статусом. Long-tap
 *    по аватарке имитирует «counterpart включает камеру» ([onToggleSomeoneCamera]).
 * 2. **ON × OFF** — fullscreen self-video (имитация фронтальной камеры).
 * 3. **OFF × ON** — fullscreen аватарка counterpart-а (Image ContentScale.Crop). Long-tap по ней
 *    «выключает» обратно. В хедере появляется имя + таймер.
 * 4. **ON × ON** — fullscreen аватарка counterpart-а + PiP self-video 100×150dp в правом нижнем
 *    углу (rounded 12dp, 20dp выше кнопок, 16dp от правого края). В хедере имя + таймер.
 *
 * Header и bottom row — общий chrome ([CallHeader] / [CallBottomBar]). Особенности P2P:
 * - `showFlip = false` (нет flip-кнопки в P2P).
 * - `iconTint` = white50 в state 1, white иначе (низкоконтрастный аватарочный фон).
 * - title/timer в header — только когда `someoneCameraOn`.
 */
@OptIn(UnstableApi::class)
@Composable
fun P2PInCallContent(
    counterpartName: String,
    counterpartAvatar: AvatarSpec,
    callConnected: Boolean,
    elapsedMs: Long,
    toggles: CallToggles,
    someoneCameraOn: Boolean,
    selfPlayer: ExoPlayer,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleSomeoneCamera: () -> Unit,
    onCollapse: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val accent = Color(brand.accentColor(isDark))
    val danger = Color(brand.dangerDefault())
    val bitmapCache = LocalBitmapCache.current
    val cacheVersion by bitmapCache.version

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
    ) {
        // Layer 1: Background — зависит от комбинации камер.
        when {
            someoneCameraOn -> {
                // «Видео» counterpart-а — на самом деле его аватарка в full-screen с center-crop.
                // Long-tap → toggle counterpart camera off.
                val counterpartBitmap = counterpartAvatar.imageAsset?.let {
                    @Suppress("UNUSED_EXPRESSION") cacheVersion
                    bitmapCache.get(it)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onToggleSomeoneCamera() })
                        },
                ) {
                    if (counterpartBitmap != null) {
                        Image(
                            bitmap = counterpartBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CallAvatarOverlay(spec = counterpartAvatar)
                        }
                    }
                }
            }
            toggles.camera -> {
                CallVideoPlayer(
                    player = selfPlayer,
                    cameraOn = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                // State 1 — avatar + name + status. Long-tap по аватарке → counterpart camera on.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 48.dp, bottom = 84.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.offset(y = (-24).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Анимация говорения стартует только когда звонок реально пошёл:
                        // callConnected=true + 2 секунды отсчёта (т.е. на 0:02 таймера).
                        P2PSpeakingIndicator(
                            active = callConnected && elapsedMs >= 2000L,
                            avatarSizeDp = 120.dp,
                            accentColor = accent,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onToggleSomeoneCamera() })
                            },
                        ) {
                            CallAvatarOverlay(spec = counterpartAvatar)
                        }
                        // Indicator кладёт 24dp вокруг через padding — визуально 24dp gap уже есть.
                        Text(
                            text = counterpartName,
                            color = Color.White,
                            style = DSTypography.title7R.toComposeTextStyle().copy(
                                fontSize = 22.sp,
                                lineHeight = 31.sp,
                            ),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (callConnected) formatElapsed(elapsedMs) else "Вызов...",
                            color = Color.White.copy(alpha = 0.5f),
                            style = DSTypography.body3M.toComposeTextStyle(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Layer 2: PiP self-video tile — когда обе камеры on.
        if (someoneCameraOn && toggles.camera) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp, end = 16.dp)
                    .size(width = 100.dp, height = 150.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                CallVideoPlayer(
                    player = selfPlayer,
                    cameraOn = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Layer 3: Header. title/timer показываем только когда counterpart camera ON.
        // Цвет иконок: white при активном видео, white50 в state 1 (низкоконтрастный фон с аватаркой).
        val headerIconTint = if (toggles.camera || someoneCameraOn) {
            Color.White
        } else {
            Color.White.copy(alpha = 0.5f)
        }
        CallHeader(
            onCollapse = onCollapse,
            onShare = { /* no-op V1 */ },
            title = if (someoneCameraOn) counterpartName else null,
            timer = if (someoneCameraOn) {
                if (callConnected) formatElapsed(elapsedMs) else "Вызов..."
            } else null,
            showFlip = false,
            iconTint = headerIconTint,
        )

        // Layer 4: Bottom buttons row.
        CallBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            toggles = toggles,
            onToggleCamera = onToggleCamera,
            onToggleSpeaker = onToggleSpeaker,
            onToggleMic = onToggleMic,
            onEndCall = onEndCall,
            danger = danger,
        )
    }
}
