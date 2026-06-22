package com.example.template.core.ui.hosts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSColors
import com.example.components.designsystem.dsIconPainter
import com.example.template.core.ui.LocalIsDark
import kotlin.math.abs
import kotlin.math.tanh
import kotlinx.coroutines.launch

/**
 * Сильный haptic-tick. Pattern (нарастающе-затухающая амплитуда, всего ~34мс) — из
 * AudioPanelView в :components.
 *
 * **Usage = ALARM** — семантический компромисс. MIUI/Xiaomi 24117RN76O фильтрует вибрации
 * по usage'у:
 * - USAGE_TOUCH/HARDWARE_FEEDBACK — управляется системной опцией "вибрация при касании";
 *   если выключена в настройках устройства, не вибрирует совсем.
 * - USAGE_NOTIFICATION — управляется ringer mode (silent → vibrates, normal → ring instead).
 * - USAGE_ALARM — alarms ВСЕГДА вибрируют независимо от ringer/touch-настроек. Это и
 *   используем для надёжности, хотя семантически мы не alarm. Длительность 34мс делает
 *   эффект коротким щелчком, а не alarm-rattle'ом.
 *
 * Требует `android.permission.VIBRATE` в манифесте.
 */
private fun performStrongHaptic(context: Context) {
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: run {
        android.util.Log.w("SwipeToReply", "Vibrator service unavailable")
        return
    }
    val effect = VibrationEffect.createWaveform(
        longArrayOf(5L, 7L, 10L, 7L, 5L),
        intArrayOf(35, 170, 255, 170, 35),
        -1,
    )
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
            android.util.Log.d("SwipeToReply", "vibrate (VibrationAttributes.USAGE_ALARM)")
        } else {
            val audioAttrs = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttrs)
            android.util.Log.d("SwipeToReply", "vibrate (AudioAttributes.USAGE_ALARM, fallback)")
        }
    } catch (t: Throwable) {
        android.util.Log.w("SwipeToReply", "Vibrate failed", t)
    }
}

private val THRESHOLD_DP = 60.dp
private const val ICON_NAME = "replay"
private val ICON_SIZE = 24.dp
private val ICON_CONTAINER_SIZE = 36.dp
private const val MAX_DRAG_FRACTION = 0.25f // 25% ширины экрана — асимптота, бабл к ней приближается

/**
 * Scope, передаваемый в content-лямбду [SwipeToReplyItem]. Через него caller получает:
 *   - [dragOffsetPx] — текущее eased-смещение баббла по X (отрицательное при свайпе влево);
 *     применяется к graphicsLayer на translatable-частях row item'а (баббл, реакции).
 *   - [iconContainerSize] — размер круглого контейнера индикатора, нужен caller'у для
 *     ручного позиционирования индикатора (например, при вычислении offset'а от правого края
 *     бабла к правому краю экрана).
 *   - composable [ReplyIndicator] — круглую иконку ответа с привязанным state'ом (alpha,
 *     scale-pulse на threshold). Caller размещает её через переданный modifier — внутри
 *     того контейнера, по высоте которого индикатор должен центрироваться.
 */
interface SwipeToReplyScope {
    val dragOffsetPx: Float
    val iconContainerSize: Dp

    @Composable
    fun ReplyIndicator(modifier: Modifier)
}

private class ActiveScope(
    private val dragOffsetState: () -> Float,
    private val iconScaleState: () -> Float,
    private val didCrossThresholdState: () -> Boolean,
    private val thresholdPx: Float,
) : SwipeToReplyScope {
    override val dragOffsetPx: Float
        get() = dragOffsetState()
    override val iconContainerSize: Dp = ICON_CONTAINER_SIZE

    @Composable
    override fun ReplyIndicator(modifier: Modifier) {
        val context = LocalContext.current
        // Тема — app-level (LocalIsDark), а не system night-mode. Поэтому DSColors через
        // ContextCompat не подходит для «basic_NN» — они читают system Configuration.
        // basic-серия в нашем DS = чёрный (light) / белый (dark) с alpha; синтезируем.
        // Базовый фон — basic_10 (alpha 0.1); как только threshold пересечён (reply станет
        // доступным на release) — анимируем до basic_20 (alpha 0.2) за 150мс. Это
        // подчёркивает скачок индикатора (scale-pulse) единой "фокус" пульсацией.
        val isDark = LocalIsDark.current
        val baseColor = if (isDark) Color.White else Color.Black
        val bgAlpha by animateFloatAsState(
            targetValue = if (didCrossThresholdState()) 0.2f else 0.1f,
            animationSpec = tween(durationMillis = 150),
            label = "swipeToReplyIndicatorBgAlpha",
        )
        val bgColor = baseColor.copy(alpha = bgAlpha)
        // Если dsIconPainter вернул null (нет SVG в синканных assets), внутри контейнера
        // ничего не рисуется — gesture всё равно работает.
        val iconPainter = dsIconPainter(name = ICON_NAME, sizeDp = ICON_SIZE.value)
        Box(
            modifier = modifier
                .graphicsLayer {
                    val offset = dragOffsetState()
                    val absOffset = abs(offset)
                    alpha = (absOffset / thresholdPx).coerceIn(0f, 1f)
                    scaleX = iconScaleState()
                    scaleY = iconScaleState()
                }
                .size(ICON_CONTAINER_SIZE)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            iconPainter?.let { painter ->
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = Color(DSColors.white100(context)).copy(alpha = 0.8f),
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
    }
}

// Disabled-scope: для Voice/Link/CallMeet типов. dragOffsetPx постоянно 0, ReplyIndicator no-op.
private object DisabledScope : SwipeToReplyScope {
    override val dragOffsetPx: Float = 0f
    override val iconContainerSize: Dp = ICON_CONTAINER_SIZE

    @Composable
    override fun ReplyIndicator(modifier: Modifier) {
        // no-op — индикатор не нужен на типах, где reply не доступен.
    }
}

/**
 * Compose-обёртка с жестом "свайп влево → ответить". Передаёт caller'у [SwipeToReplyScope] — он
 * сам ставит translatable-секции (баббл, реакции) и индикатор.
 *
 * Easing: пользовательский палец двигает `rawDrag` свободно, а видимое смещение баббла
 * вычисляется как `-maxDragPx * tanh(|rawDrag| / maxDragPx)` — асимптотический подход к
 * `-maxDragPx`. По мере приближения к пределу бабл двигается всё медленнее за тем же
 * движением пальца, до асимптотически нулевого изменения. На release rawDrag сбрасывается
 * (вместе с `Animatable.animateTo(0f)`) — следующий drag стартует с нуля.
 *
 * На пересечении threshold (60dp от старта) даёт лёгкий haptic и scale-pulse иконки.
 * На release: если `|easedOffset| >= threshold` — вызывает [onTriggerReply], иначе просто
 * spring back к 0.
 *
 * Если [enabled] = false (Voice/Link/CallMeet, либо selection-mode), gesture detector не
 * регистрируется (pointerInput возвращает рано) и content получает [DisabledScope] — bubble
 * не двигается, индикатор no-op. **Структура composable'а одинакова для enabled=true/false**
 * (всегда `Box(modifier.pointerInput) { content() }`) — это критично, иначе Compose dispose'ит
 * композицию вокруг AndroidView внутри content при toggle enabled → BubblesView re-inflate
 * → subpixel-rounding в internal LinearLayout даёт ±3px разницу в height.
 */
@Composable
fun SwipeToReplyItem(
    messageId: String,
    enabled: Boolean,
    onTriggerReply: (messageId: String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable SwipeToReplyScope.() -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val thresholdPx = with(density) { THRESHOLD_DP.toPx() }
    // maxDragPx — асимптота, к которой стремится easing (а не жёсткий потолок).
    val maxDragPx = with(density) { (configuration.screenWidthDp.dp * MAX_DRAG_FRACTION).toPx() }
    val dragOffset = remember(messageId) { Animatable(0f) }
    val iconScale = remember(messageId) { Animatable(1f) }
    var didCrossThreshold by remember(messageId) { mutableStateOf(false) }
    // rawDrag — сырое (не eased) смещение пальца, аккумулируется от старта жеста.
    // Eased offset вычисляется из него как -maxDragPx * tanh(|rawDrag|/maxDragPx).
    var rawDrag by remember(messageId) { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeScope = remember(thresholdPx) {
        ActiveScope(
            dragOffsetState = { dragOffset.value },
            iconScaleState = { iconScale.value },
            didCrossThresholdState = { didCrossThreshold },
            thresholdPx = thresholdPx,
        )
    }
    val swipeScope: SwipeToReplyScope = if (enabled) activeScope else DisabledScope

    // При переходе enabled true→false: сброс leftover-state (если бабл был во время drag'а).
    // Использует LaunchedEffect чтобы выполниться в side-effect фазе (а не во время composition).
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) {
            rawDrag = 0f
            didCrossThreshold = false
            dragOffset.snapTo(0f)
        }
    }

    Box(
        // pointerInput keyed by (messageId, enabled) — при toggle enabled пересоздаётся
        // только gesture-detector (без re-mount Box и его content/AndroidView).
        modifier = modifier.pointerInput(messageId, enabled) {
            if (!enabled) return@pointerInput  // в selection-mode и для не-text типов: без жеста
            detectHorizontalDragGestures(
                onDragStart = {
                    didCrossThreshold = false
                    rawDrag = 0f
                },
                onDragEnd = {
                    val crossed = abs(dragOffset.value) >= thresholdPx
                    scope.launch { dragOffset.animateTo(0f, spring()) }
                    if (crossed) onTriggerReply(messageId)
                },
                onDragCancel = {
                    scope.launch { dragOffset.animateTo(0f, spring()) }
                },
            ) { change, dragAmount ->
                change.consume()
                rawDrag = (rawDrag + dragAmount).coerceAtMost(0f)
                val easedOffset = -maxDragPx * tanh(abs(rawDrag) / maxDragPx)
                scope.launch { dragOffset.snapTo(easedOffset) }
                if (!didCrossThreshold && abs(easedOffset) >= thresholdPx) {
                    didCrossThreshold = true
                    performStrongHaptic(context)
                    scope.launch {
                        iconScale.animateTo(1.15f, tween(75))
                        iconScale.animateTo(1.0f, tween(75))
                    }
                }
            }
        },
    ) {
        swipeScope.content()
    }
}
