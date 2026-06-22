package com.example.template.feature.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Slide-in-from-right wrapper для InCall fullscreen. Содержимое заезжает справа налево за 280ms,
 * exit (visible=false) — обратный slide вправо за 240ms; по завершении [onExitComplete] (parent
 * снимает с композиции).
 *
 * Используется при переходе Lobby → InCall параллельно с exit-анимацией LobbyBottomSheet.
 */
@Composable
fun InCallSlideOverlay(
    visible: Boolean,
    onExitComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        var progress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(visible) {
            val target = if (visible) 1f else 0f
            val durMs = if (visible) 280 else 240
            animate(
                initialValue = progress,
                targetValue = target,
                animationSpec = tween(durMs, easing = FastOutSlowInEasing),
            ) { v, _ -> progress = v }
            if (!visible) onExitComplete()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = widthPx * (1f - progress) },
        ) {
            content()
        }
    }
}
