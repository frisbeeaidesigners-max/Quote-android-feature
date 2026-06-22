package com.example.template.feature.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet для Lobby outgoing-call. Sheet занимает [sheetHeightFraction] (по умолчанию
 * 1.0 — full screen). Rounded top 16dp. Без scrim'а — во время entry/exit под sheet'ом виден
 * предыдущий экран без затемнения.
 *
 * `statusBarsPadding()` применяется к sheet Box'у — drag-handle и content сидят под статус-баром,
 * не перекрываются им.
 *
 * Управление видимостью — снаружи через [visible]:
 * - visible=true (mount) → sheet въезжает снизу за 280ms.
 * - visible=false → sheet уезжает вниз за 240ms; по завершении вызывается [onExitComplete] (parent
 *   снимает sheet с композиции).
 *
 * Закрытие пользователем:
 * - vertical swipe down: drag меняет progress в реальном времени; на release если progress < 0.5
 *   ИЛИ velocity > порога — вызывается [onDismiss] (parent должен поставить visible=false).
 * - X-кнопка (handled снаружи в LobbyContent).
 *
 * Геометрия: progress=1 → sheet at rest, progress=0 → off-screen down.
 *
 * Drag-handle 36×4dp сверху, alpha 0.3 (brand-агностичный).
 */
@Composable
fun LobbyBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onExitComplete: () -> Unit = {},
    sheetHeightFraction: Float = 1.0f,
    backgroundColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()
        val sheetHeightPx = screenHeightPx * sheetHeightFraction
        val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

        // progress: 1 = sheet at rest, 0 = off-screen down. Стартуем с 0, entry-анимация
        // отыграет в LaunchedEffect(visible) на mount.
        var progress by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(visible) {
            if (dragging) return@LaunchedEffect
            val target = if (visible) 1f else 0f
            val durMs = if (visible) 280 else 240
            animate(
                initialValue = progress,
                targetValue = target,
                animationSpec = tween(durMs, easing = FastOutSlowInEasing),
            ) { v, _ -> progress = v }
            if (!visible) onExitComplete()
        }

        // Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .graphicsLayer { translationY = sheetHeightPx * (1f - progress) }
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(backgroundColor)
                .statusBarsPadding()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        // delta>0 — drag вниз → sheet уезжает → progress уменьшается.
                        progress = (progress - delta / sheetHeightPx).coerceIn(0f, 1f)
                    },
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity ->
                        dragging = false
                        val shouldDismiss = velocity > FLING_DISMISS_THRESHOLD || progress < 0.5f
                        if (shouldDismiss) {
                            // parent поставит visible=false → LaunchedEffect(visible) допишет
                            // exit-анимацию из текущей позиции до 0 и позовёт onExitComplete.
                            onDismiss()
                        } else {
                            scope.launch {
                                animate(
                                    initialValue = progress,
                                    targetValue = 1f,
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                ) { v, _ -> progress = v }
                            }
                        }
                    },
                ),
        ) {
            // Content заполняет всю площадь sheet'а — drag-handle рисуется ПОВЕРХ него
            // (declared позже → выше z-order'ом). Это даёт video full-bleed, controls overlay'ятся.
            Box(modifier = Modifier.fillMaxSize(), content = content)

            // Drag handle поверх content'а.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            )
        }
    }
}

private const val FLING_DISMISS_THRESHOLD = 1500f
