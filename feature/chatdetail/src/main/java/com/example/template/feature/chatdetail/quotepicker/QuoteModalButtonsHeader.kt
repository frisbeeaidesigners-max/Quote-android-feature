package com.example.template.feature.chatdetail.quotepicker

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RobotoFamilyV2 = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

/**
 * V2 (MODAL_BUTTONS) — нижний Header модалки с круглыми кнопками-стрелками по бокам.
 * Свайп между вкладками отключён в QuoteModalContent (см. branch на variant); переключение
 * только тапом по [onPrev]/[onNext]. Для двух вкладок оба колбэка фактически toggle'ят tab.
 *
 * Геометрия (Figma node 8857:940577): pill-формы, padding 8dp внутри, gap 8dp между детьми.
 * Левый/правый круглые батоны 36dp, центральная колонка занимает оставшееся пространство.
 */
@Composable
internal fun QuoteModalButtonsHeader(
    selectedTab: Int,
    menuState: QuoteMenuState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleArrowButton(iconName = "arrow-left-m", onClick = onPrev)
        Box(modifier = Modifier.weight(1f)) {
            ButtonsHeaderText(tab = selectedTab, menuState = menuState)
        }
        CircleArrowButton(iconName = "arrow-right-m", onClick = onNext)
    }
}

@Composable
private fun ButtonsHeaderText(tab: Int, menuState: QuoteMenuState) {
    val isDark = LocalIsDark.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = titleForV2(tab, menuState),
            color = appBasic(isDark, 0.9f),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontFamily = RobotoFamilyV2,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = descriptionForV2(tab),
            color = appBasic(isDark, 0.5f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = RobotoFamilyV2,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CircleArrowButton(iconName: String, onClick: () -> Unit) {
    val isDark = LocalIsDark.current
    val ctx = LocalContext.current
    val bg = appBasic(isDark, 0.08f)
    val tint = appBasic(isDark, 0.9f)
    var bitmap by remember(iconName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(iconName) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, iconName, 24f) as? BitmapDrawable)?.bitmap
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
    }
}

private fun titleForV2(tab: Int, menuState: QuoteMenuState): String = when (tab) {
    1 -> "Оформление ссылки"
    else -> when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
    }
}

private fun descriptionForV2(tab: Int): String = when (tab) {
    1 -> "Так будет выглядеть ваша ссылка"
    else -> "Выберите фрагмент для цитирования"
}
