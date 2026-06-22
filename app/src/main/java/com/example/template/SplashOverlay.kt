package com.example.template

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import kotlinx.coroutines.delay

/**
 * Splash-оверлей с тёмным фоном (`#1A1A1A`, как у `@color/splash_background` в theme'е) и
 * центрированным капитализированным codename'ом бренда из [BuildConfig.BRAND_CODENAME].
 *
 * Текст: 48sp, `FontWeight.Black` (900), letterSpacing -1sp (на основе метрик `title1B`).
 * Цвет `#FFFFFF`.
 *
 * Видимость: оверлей скрывается ТОЛЬКО когда выполнено **И** `mainUiReady`, **И** прошло
 * [SPLASH_MIN_DURATION_MS]. Появление мгновенное (первый кадр Compose). Уход — каскадом:
 * сначала затухает текст ([TEXT_FADE_MS]), затем фон ([BG_FADE_MS]).
 *
 * Alpha обоих слоёв читается **в draw scope через `graphicsLayer { alpha = ... }`** — state read
 * там не триггерит рекомпозицию, только инвалидирует draw layer. Без этого `Color.copy(alpha=...)`
 * в composition scope рекомпозировал Box на каждый кадр анимации, и пока сверху ещё прогревался
 * MainScaffold (тяжёлые AndroidView), фейд подёргивался.
 *
 * `hidden` гейт флипается после завершения ОБЕИХ анимаций — оверлей перестаёт рендериться и
 * больше не съедает тапы по UI под ним. Чек один раз при флипе, не каждый кадр.
 */
private const val SPLASH_MIN_DURATION_MS = 700L
private const val TEXT_FADE_MS = 200
private const val BG_FADE_MS = 300

@Composable
fun SplashOverlay(
    mainUiReady: Boolean,
    /** Вызывается один раз, когда обе fadeOut-анимации (text → bg) завершились. */
    onGone: () -> Unit = {},
) {
    var minDurationElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_DURATION_MS)
        minDurationElapsed = true
    }

    val visible = !(mainUiReady && minDurationElapsed)

    val textAlpha = remember { Animatable(1f) }
    val bgAlpha = remember { Animatable(1f) }
    // rememberSaveable: на rotation splash не показывается заново. Animatable'ы остаются
    // remember (reset на rotation), но `if (hidden) return` ниже отрезает рендер до них.
    var hidden by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            textAlpha.animateTo(0f, animationSpec = tween(TEXT_FADE_MS))
            bgAlpha.animateTo(0f, animationSpec = tween(BG_FADE_MS))
            hidden = true
            onGone()
        }
    }

    if (hidden) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Фон — отдельный слой со своей graphicsLayer.alpha. State read внутри лямбды
        // graphicsLayer происходит на draw-фазе, не на composition.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha.value }
                .background(Color(0xFF1A1A1A)),
        )
        // Текст — поверх, со своей независимой alpha.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = textAlpha.value },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = BuildConfig.BRAND_CODENAME
                    .replaceFirstChar { it.uppercase() },
                color = Color.White,
                style = DSTypography.title1B.toComposeTextStyle().copy(
                    // FontFamily.Default = системный Roboto, у которого есть актуальный Roboto-Black
                    // (900) глиф. RobotoFamily в :components задаёт только до 700 (Bold), поэтому
                    // FontWeight.Black там фолбэчился на faux-bold синтез Bold'а — визуально это
                    // не отличалось от 700.
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Black,
                    fontSize = 48.sp,
                    letterSpacing = (-1).sp,
                    // Shadow с тем же белым и blur=0 = ещё один проход текста со сдвигом 1px —
                    // визуально утолщает контур, без необходимости рендерить два Text'а руками.
                    shadow = Shadow(
                        color = Color.White,
                        offset = Offset(1f, 0f),
                        blurRadius = 0f,
                    ),
                ),
            )
        }
    }
}
