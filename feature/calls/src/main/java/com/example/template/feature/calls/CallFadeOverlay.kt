package com.example.template.feature.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
 * Fade-in/fade-out wrapper для overlay'а звонка. Используется для P2P InCall — плавное появление
 * на mount, плавное исчезновение на Collapse / End-call. По завершении exit-анимации зовётся
 * [onExitComplete] (parent снимает с композиции).
 *
 * Альтернатива [InCallSlideOverlay] (slide-in справа), использующемся для Group/BrandMeet InCall —
 * для P2P slide неестественный, потому что нет предыдущего экрана Lobby, с которым он бы стыковался.
 */
@Composable
fun CallFadeOverlay(
    visible: Boolean,
    onExitComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
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
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress },
    ) {
        content()
    }
}
