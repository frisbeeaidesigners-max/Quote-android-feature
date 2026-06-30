package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import kotlin.math.roundToInt

private val RobotoFontFamilySwipe = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

/**
 * Нижний header-блок Modal-picker'а. Title+description зависят от вкладки (selectedTab)
 * и от FSM menuState (на tab=0). Под текстом — индикатор из 2 точек.
 *
 * Gesture-state поднят на уровень QuoteModalContent (свайп ловится по всему модалу).
 * Сюда приходит [dragOffsetPx] — текущий горизонтальный сдвиг в пикселях; используется
 * только для визуального оффсета title'а, чтобы текст «следовал за пальцем».
 */
@Composable
internal fun QuoteModalSwipeFooter(
    selectedTab: Int,
    menuState: QuoteMenuState,
    dragOffsetPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * dir } +
                    fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it * dir } +
                        fadeOut(tween(120))) using
                    SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) }
            },
            label = "modalSwipeTitle",
        ) { tab ->
            val title = titleFor(tab, menuState)
            val description = descriptionFor(tab)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(dragOffsetPx().roundToInt(), 0) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = appBasic(isDark, 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontFamily = RobotoFontFamilySwipe,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = description,
                    color = appBasic(isDark, 0.5f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = RobotoFontFamilySwipe,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        DotsRow(activeIndex = selectedTab, count = 2)
    }
}

@Composable
private fun DotsRow(activeIndex: Int, count: Int) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current
    val activeColor = Color(brand.accentColor(isDark))
    val inactiveColor = appBasic(isDark, 0.2f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            if (i == activeIndex) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(activeColor),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(inactiveColor),
                )
            }
        }
    }
}

private fun titleFor(tab: Int, menuState: QuoteMenuState): String = when (tab) {
    1 -> "Оформление ссылки"
    else -> when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
    }
}

private fun descriptionFor(tab: Int): String = when (tab) {
    1 -> "Так будет выглядеть ваша ссылка после отправки"
    else -> "Вы можете процитировать фрагмент сообщения"
}

/**
 * Static footer для linkRender=OFF состояния Modal-picker'а. Только Title/Description
 * вертикально центрированы внутри 82dp reserve area, без dots/buttons/свайпа.
 *
 * Title зависит от menuState (миррор sibling SwipeFooter tab=0 логики):
 *  - INITIAL/INITIAL_MINIMAL → «Ответ на сообщение»
 *  - INITIAL_WITH_QUOTE/SELECTING → «Ответ на цитату»
 * Description константный: «Вы можете процитировать фрагмент сообщения».
 */
@Composable
internal fun QuoteModalStaticFooter(
    menuState: QuoteMenuState,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val title = when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        else -> "Ответ на сообщение"
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            text = "Вы можете процитировать фрагмент сообщения",
            color = appBasic(isDark, 0.5f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * V1 sticky header — Title+Description блок поверх верха preview Box'а (как в первой
 * итерации Modal'а в android-template-quote @ 4c4036f). Используется только в состоянии
 * `MODAL_DOTS + linkRender=OFF` — внешнего bottom footer'а нет, header лежит внутри
 * preview-card'а на appSurface01 фоне. BackgroundPatternView и QuoteBubblePreview под
 * ним остаются скроллящимися; 60dp top-spacer в content-Column'е резервирует место.
 */
@Composable
internal fun QuoteModalStickyHeader(
    menuState: QuoteMenuState,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val title = when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        else -> "Ответ на сообщение"
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
            text = "Вы можете процитировать фрагмент сообщения",
            color = appBasic(isDark, 0.5f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
