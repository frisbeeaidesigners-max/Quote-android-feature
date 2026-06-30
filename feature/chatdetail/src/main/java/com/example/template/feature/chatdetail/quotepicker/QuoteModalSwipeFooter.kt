package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import com.example.template.core.ui.appSurface02

private val RobotoFontFamilySwipe = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

/**
 * V1 sticky header — Title+Description блок поверх верха preview Box'а (как в первой
 * итерации Modal'а в android-template-quote @ 4c4036f). Используется в `MODAL_STICKY`
 * варианте — внешнего bottom footer'а нет, header лежит внутри preview-card'а на
 * appSurface01 фоне. BackgroundPatternView и QuoteBubblePreview под ним остаются
 * скроллящимися; 60dp top-spacer в content-Column'е резервирует место.
 *
 * При [selectedTab] = 1 (вкладка «Ссылка» в STICKY+linkRender ON) заголовок переключается
 * на «Оформление ссылки» — миррор SwipeFooter/ButtonsHeader титульной логики.
 */
@Composable
internal fun QuoteModalStickyHeader(
    selectedTab: Int,
    menuState: QuoteMenuState,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val title = if (selectedTab == 1) {
        "Оформление ссылки"
    } else when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        else -> "Ответ на сообщение"
    }
    val description = if (selectedTab == 1) {
        "Так будет выглядеть ваша ссылка"
    } else {
        "Вы можете процитировать фрагмент сообщения"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(appSurface01(isDark))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = appBasic(isDark, 0.9f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            color = appBasic(isDark, 0.5f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Двух-сегментный «Ответ / Ссылка» контрол для MODAL_STICKY + linkRender=ON. Рендерится
 * ниже QuoteMenu popover'а (центрирован), даёт переключение между tab=0 (QuoteBubblePreview)
 * и tab=1 (QuoteModalLinkPreview).
 *
 * Геометрия Figma node 8843:877921: 152×32dp фиксированный размер, контейнер padding 2dp,
 * 9dp container radius, 7dp segment radius, body5R текст. Активный сегмент использует
 * brand-tinted [com.example.components.designsystem.DSBrand.messageScreenBackground]
 * — тот же цвет, что и фон preview Box'а; контейнер на [appSurface02].
 */
@Composable
internal fun QuoteModalReplyLinkSegmented(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current
    val containerBg = appSurface02(isDark)
    val activeBg = androidx.compose.ui.graphics.Color(brand.messageScreenBackground(isDark))
    val activeText = appBasic(isDark, 0.9f)
    val inactiveText = appBasic(isDark, 0.5f)
    val labels = listOf("Ответ", "Ссылка")
    Row(
        modifier = modifier
            .size(width = 152.dp, height = 32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(containerBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(2.dp))
        labels.forEachIndexed { i, label ->
            val isActive = i == selectedTab
            val segmentInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .then(if (isActive) Modifier.background(activeBg) else Modifier)
                    .clickable(
                        interactionSource = segmentInteraction,
                        indication = null,
                        onClick = { if (i != selectedTab) onSelect(i) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isActive) activeText else inactiveText,
                    fontFamily = RobotoFontFamilySwipe,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(2.dp))
    }
}
