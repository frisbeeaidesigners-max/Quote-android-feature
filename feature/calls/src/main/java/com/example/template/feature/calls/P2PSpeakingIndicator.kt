package com.example.template.feature.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Анимация «собеседник говорит» вокруг аватарки в P2P-звонке (state 1: обе камеры выкл).
 * Три кольца, имитирующие голосовую амплитуду + master envelope с фазой silence:
 *
 * - Кольцо 1 (2dp accent): alpha pulses 0→0.7 по аудио-envelope (sin(angle1)·0.6+sin(angle2)·0.4).
 * - Кольцо 2 (40% accent): толщина 0→20dp по той же envelope (синфазно кольцу 1).
 * - Кольцо 3 (40% accent, 1dp): offset 2→24dp от аватарки по независимой envelope (angle3+angle4).
 *   Своя база (2.2s vs 3.0s), амплитуды не совпадают с кольцами 1/2.
 *
 * Master envelope (мультипликатор амплитуды и alpha) — циклит «говорит → молчит»:
 * - 0..500ms: ramp-up 0→1 (ease-in-out), плавное появление колец.
 * - 500..3000ms: sustain 1.0, нормальная «речь».
 * - 3000..3700ms: decay 1→0 (ease-in-out), плавное затухание.
 * - 3700..4700ms: silence 0, ничего не рисуется.
 * - Цикл бесшовный: рестарт = 0→0, не jumpа.
 *
 * Параметр [active] — gate: пока false, animation state НЕ создаётся (`if (active)` отрезает
 * весь stack rememberInfiniteTransition+animateFloat). Когда становится true, state создаётся
 * с initialValue=0 → первый цикл стартует с чистого silence + ramp-up. Это даёт «свежий»
 * старт анимации без подхвата в середине цикла.
 */
@Composable
fun P2PSpeakingIndicator(
    active: Boolean,
    avatarSizeDp: Dp,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val totalSize = avatarSizeDp + INDICATOR_PADDING_DP * 2

    Box(
        modifier = modifier.size(totalSize),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            val infiniteTransition = rememberInfiniteTransition(label = "p2p-speaking")
            val angle1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle1",
            )
            val angle2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1300, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle2",
            )
            val angle3 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle3",
            )
            val angle4 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 950, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle4",
            )
            val envelope by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 4700
                        0f at 0
                        1f at 500 using FastOutSlowInEasing       // ramp-up 0→1
                        1f at 3000                                // sustain end
                        0f at 3700 using FastOutSlowInEasing      // decay 1→0
                        // implicit 0f at 4700 (silence до конца цикла)
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "envelope",
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val avatarRadius = (avatarSizeDp / 2).toPx()
                drawRings(center, avatarRadius, accentColor, angle1, angle2, angle3, angle4, envelope)
            }
        }
        content()
    }
}

/** Зазор вокруг аватарки в индикаторе — покрывает максимальное расширение колец (ring 3 ≈ 24.5dp). */
private val INDICATOR_PADDING_DP = 28.dp

private fun DrawScope.drawRings(
    center: Offset,
    avatarRadius: Float,
    accent: Color,
    angle1: Float,
    angle2: Float,
    angle3: Float,
    angle4: Float,
    envelope: Float,
) {
    if (envelope <= 0.001f) return
    val gap = 2.dp.toPx()
    // «Аудио-envelope» для кольца 1 и 2 — сумма двух sin'ов от разных независимых углов.
    val pulse12Raw = sin(angle1.toDouble()) * 0.6 + sin(angle2.toDouble()) * 0.4
    val pulse12 = ((pulse12Raw + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) * envelope
    // Кольцо 3 — отдельный «голос», независимый ритм.
    val pulse3Raw = sin(angle3.toDouble()) * 0.6 + sin(angle4.toDouble()) * 0.4
    val pulse3 = ((pulse3Raw + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) * envelope

    // Кольцо 1 — solid 2dp accent, alpha pulse.
    val ring1Stroke = 2.dp.toPx()
    drawCircle(
        color = accent.copy(alpha = pulse12 * 0.7f),
        radius = avatarRadius + gap + ring1Stroke / 2f,
        center = center,
        style = Stroke(width = ring1Stroke),
    )

    // Кольцо 2 — толщина 0..20dp, accent 40% × envelope. Max диаметр ≈ 164dp.
    val ring2Thickness = 20.dp.toPx() * pulse12
    if (ring2Thickness > 0.5f) {
        drawCircle(
            color = accent.copy(alpha = 0.4f * envelope),
            radius = avatarRadius + gap + ring2Thickness / 2f,
            center = center,
            style = Stroke(width = ring2Thickness),
        )
    }

    // Кольцо 3 — 1dp, offset 2..24dp от аватарки (max диаметр ≈ 170dp при avatar=120dp).
    val ring3Stroke = 1.dp.toPx()
    val ring3Offset = gap + 22.dp.toPx() * pulse3
    drawCircle(
        color = accent.copy(alpha = 0.4f * envelope),
        radius = avatarRadius + ring3Offset + ring3Stroke / 2f,
        center = center,
        style = Stroke(width = ring3Stroke),
    )
}
