package com.example.template.feature.chatdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.dsIconPainter
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

/**
 * Action bar в режиме множественного выбора: 4 ячейки (88dp каждая, центрированы группой).
 * Удалить (danger-цвет) дизейблится при наличии SOMEONE в выборе; остальные — accent-цвет бренда.
 *
 * Bottom-padding 24dp согласован с MessagePanel — высоту бара не трогаем (см. memory selection-mode).
 */
@Composable
fun SelectionActionBar(
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onSave: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val barBackground = remember(brand, isDark) { Color(brand.backgroundBase(isDark)) }
    val borderColor = remember(brand, isDark) { Color(brand.basicColor08(isDark)) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .drawBehind {
                val sw = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = sw,
                )
            }
            // Высота 88.5dp == MessagePanel (замер 249px @ density 2.8125). Cell content = ~75.3dp,
            // top 2 + bottom 11 = 13dp → итог 88.5dp. Bottom > top чтобы кнопки прижаты к верху.
            .padding(top = 2.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionBarCell(
            iconName = "delete",
            label = "Удалить",
            isDanger = true,
            enabled = deleteEnabled,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
        ActionBarCell(
            iconName = "forward-stroke",
            label = "Переслать",
            isDanger = false,
            enabled = true,
            onClick = onForward,
            modifier = Modifier.weight(1f),
        )
        ActionBarCell(
            iconName = "saved",
            label = "В сохраненное",
            isDanger = false,
            enabled = true,
            onClick = onSave,
            modifier = Modifier.weight(1f),
        )
        ActionBarCell(
            iconName = "pin-stroke",
            label = "Закрепить",
            isDanger = false,
            enabled = true,
            onClick = onPin,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Одна ячейка: круглая 48dp tinted-подложка с 24dp иконкой + лейбл 13sp/16sp снизу.
 * Цветовая логика: enabled+danger → red, enabled+!danger → brand accent, !enabled → grey (basic 6%/25%).
 * Tint иконки через ColorFilter.tint, потому что DSIcon.named рендерит drawable чёрным.
 */
@Composable
private fun RowScope.ActionBarCell(
    iconName: String,
    label: String,
    isDanger: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val tintColor = remember(brand, isDark, isDanger) {
        Color(if (isDanger) brand.dangerDefault() else brand.accentColor(isDark))
    }
    val iconBg = remember(tintColor, enabled, isDark) {
        if (enabled) tintColor.copy(alpha = 0.1f)
        else (if (isDark) Color.White else Color.Black).copy(alpha = 0.06f)
    }
    val contentColor = remember(tintColor, enabled, brand, isDark) {
        if (enabled) tintColor else Color(brand.basicColor25(isDark))
    }

    Column(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            val painter = dsIconPainter(iconName, sizeDp = 24f)
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(contentColor),
                )
            }
        }
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}
