package com.example.template.feature.calls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.dsIconPainter
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appClickable

/**
 * Плитка-«action» из верхнего ряда вкладки «Звонки» («Новая встреча» / «Создать ссылку»).
 *
 * Не использует [com.example.components.button.ButtonView] — у того layout «иконка слева,
 * текст справа», а в Figma иконка над лейблом. Тонированный basic55-ripple — через
 * [appClickable], общий хелпер для статичных не-`:components` элементов.
 *
 * Используется ровно для двух плиток в [CallsScreen]; на других экранах смысла переиспользовать
 * нет, поэтому файл живёт здесь, а не в `core/ui/components/`.
 */
@Composable
fun CallActionTile(
    iconName: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current

    val backgroundColor = appBasic(isDark, 0.06f)
    val iconColor = appBasic(isDark, 0.55f)
    val labelColor = appBasic(isDark, 0.9f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .appClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 24f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(iconColor),
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = label,
            color = labelColor,
            style = DSTypography.body3M.toComposeTextStyle(),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
