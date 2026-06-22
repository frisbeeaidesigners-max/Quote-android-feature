package com.example.template.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color

/**
 * Дефолтный clickable для статичных Compose-элементов (своих, не AndroidView-обёрток над
 * библиотечными `View`-компонентами). Тонированный Material ripple того же цвета, что у
 * [com.example.components.button.ButtonView] SECONDARY — `basicColor55` на 10% alpha.
 * Material3-серый-по-умолчанию на светлой теме смотрится мутно и не совпадает с фидбэком
 * кнопок из библиотеки.
 *
 * Это «по умолчанию» для строк/плиток/кастомных тапабельных элементов, которых нет в `:components`
 * (`CallRow`, `CallActionTile`, и т. п.). Для библиотечных компонентов ripple идёт изнутри — этот
 * хелпер там не нужен.
 *
 * `rememberRipple(color=...)` — Material1 API: только он принимает явный Color, Foundation и
 * Material3 такого override'а не дают.
 */
fun Modifier.appClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier = composed {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val rippleColor = remember(brand, isDark) { Color(brand.basicColor55(isDark)) }
    val interaction = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interaction,
        indication = rememberRipple(color = rippleColor),
        enabled = enabled,
        onClick = onClick,
    )
}
