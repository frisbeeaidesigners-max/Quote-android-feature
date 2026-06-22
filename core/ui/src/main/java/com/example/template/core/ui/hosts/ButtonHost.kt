package com.example.template.core.ui.hosts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.button.ButtonColorScheme
import com.example.components.button.ButtonView
import com.example.components.designsystem.DSTextStyle

/**
 * Compose-обёртка над библиотечным [ButtonView]. Используем её вместо самописных Box/Row +
 * .clickable {} — все размеры (L/M/S/XS), padding'и, corner-radius (1000dp pill), ripple
 * (content-color × 10% alpha), tint иконки и цвет лейбла приходят из ButtonView.configure().
 *
 * Параметры зеркалят ButtonView.configure. onClick перевешивается на каждом update —
 * рекомпозиция со свежей лямбдой работает корректно.
 *
 * Размеры (по ButtonView): L=56dp, M=48dp, S=40dp, XS=36dp.
 * Default textStyle по размеру: L → subtitle1M (18sp), M/S → body3M (16sp), XS → body4M (14sp).
 * `textStyle` override используем там, где Figma требует другой DSTextStyle (например body1R 16sp/20).
 */
@Composable
fun ButtonHost(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "",
    iconName: String? = null,
    size: ButtonView.Size = ButtonView.Size.L,
    filled: Boolean = true,
    enabled: Boolean = true,
    colorScheme: ButtonColorScheme = ButtonColorScheme.DEFAULT,
    textStyle: DSTextStyle? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { ButtonView(it) },
        update = { view ->
            view.configure(
                text = text,
                iconName = iconName,
                size = size,
                filled = filled,
                enabled = enabled,
                colorScheme = colorScheme,
                textStyle = textStyle,
            )
            view.onClick = onClick
        },
    )
}
