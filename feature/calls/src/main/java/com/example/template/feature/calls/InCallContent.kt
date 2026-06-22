package com.example.template.feature.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.template.core.model.AvatarSpec
import com.example.template.core.ui.LocalAppBrand

/**
 * UI активного звонка (фаза InCall) для **Group/BrandMeet** — поверх [CallVideoPlayer]
 * (фон живёт уровнем выше). Chrome (header + bottom row) — общий с другими call-экранами,
 * см. [CallHeader] / [CallBottomBar].
 *
 * Off-state аватарка центрируется в зоне между header'ом (52dp+statusBars) и нижним рядом
 * (112dp+navigationBars); виден только когда [CallToggles.camera] = false (своё видео off).
 */
@Composable
fun InCallContent(
    title: String,
    elapsedMs: Long,
    toggles: CallToggles,
    selfAvatar: AvatarSpec,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onCollapse: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val danger = Color(brand.dangerDefault())

    Box(modifier = modifier.fillMaxSize()) {
        // Off-state avatar — между header (top 12+48+8=68dp+statusBars) и
        // нижним рядом (28+56+28=112dp+navigationBars).
        if (!toggles.camera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 68.dp, bottom = 112.dp),
                contentAlignment = Alignment.Center,
            ) {
                CallAvatarOverlay(spec = selfAvatar)
            }
        }

        CallHeader(
            onCollapse = onCollapse,
            onShare = { /* no-op V1 */ },
            title = title,
            timer = formatElapsed(elapsedMs),
            showFlip = true,
            onFlip = { /* no-op V1 */ },
        )

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
