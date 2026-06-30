package com.example.template.feature.chatdetail.quotepicker

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSColors
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface02

enum class QuoteMenuState { INITIAL, INITIAL_WITH_QUOTE, SELECTING, INITIAL_MINIMAL }

private data class MenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val showIcon: Boolean = true,
    val onClick: () -> Unit,
)

private val APPLY_LABEL = "Применить изменения"
private val APPLY_ICON = "check-outline"

@Composable
fun QuoteMenu(
    state: QuoteMenuState,
    modifier: Modifier = Modifier,
    onSelectFragment: () -> Unit,
    onApply: () -> Unit,
    onCancelReply: () -> Unit,
    onBack: () -> Unit,
    onConfirmQuote: () -> Unit,
    onClearQuote: () -> Unit,
    splitApply: Boolean = false,
) {
    val isDark = LocalIsDark.current
    val containerBg = appSurface02(isDark)

    Column(modifier = modifier) {
        // Основная карта меню — состояния FSM. При splitApply=true пункт «Применить изменения»
        // отфильтровывается и выносится в отдельную карту ниже.
        Box(
            modifier = Modifier
                .width(250.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerBg),
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * dir } +
                        fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it * dir } +
                        fadeOut(tween(120))) using
                    SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) }
                },
                label = "QuoteMenuFsm",
            ) { current ->
                val allItems: List<MenuItemSpec> = when (current) {
                    QuoteMenuState.INITIAL -> listOf(
                        MenuItemSpec("Выбрать фрагмент", "text-select", onClick = onSelectFragment),
                        MenuItemSpec(APPLY_LABEL, APPLY_ICON, onClick = onApply),
                        MenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                    )
                    QuoteMenuState.INITIAL_WITH_QUOTE -> listOf(
                        MenuItemSpec("Снять выделение", "quote-clear", onClick = onClearQuote),
                        MenuItemSpec(APPLY_LABEL, APPLY_ICON, onClick = onApply),
                        MenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                    )
                    QuoteMenuState.SELECTING -> listOf(
                        MenuItemSpec("Назад", "back", onClick = onBack),
                        MenuItemSpec("Цитировать фрагмент", "quote-create", onClick = onConfirmQuote),
                    )
                    QuoteMenuState.INITIAL_MINIMAL -> listOf(
                        MenuItemSpec(APPLY_LABEL, APPLY_ICON, onClick = onApply),
                        MenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                    )
                }
                val items = if (splitApply) allItems.filterNot { it.label == APPLY_LABEL } else allItems
                Column {
                    items.forEachIndexed { idx, item ->
                        QuoteMenuItem(item)
                        if (idx < items.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(appBasic(isDark, 0.08f))
                            )
                        }
                    }
                }
            }
        }
        // splitApply=true: «Применить изменения» — отдельная карта 4dp ниже основной (без
        // иконки). Показывается только когда в текущем FSM-состоянии есть apply (SELECTING
        // его не имеет).
        val stateHasApply = state == QuoteMenuState.INITIAL ||
            state == QuoteMenuState.INITIAL_WITH_QUOTE ||
            state == QuoteMenuState.INITIAL_MINIMAL
        if (splitApply && stateHasApply) {
            Spacer(Modifier.height(4.dp))
            QuoteMenuApplyChangesCard(onClick = onApply)
        }
    }
}

@Composable
private fun QuoteMenuItem(item: MenuItemSpec) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)
    val iconColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.55f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Default — transparent (виден surface02 контейнера). Press — overlay Basic 08.
    val itemBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(180.dp),
        )
        if (item.showIcon) {
            QuoteMenuIcon(item.iconName, iconColor)
        }
    }
}

/**
 * Apply-changes card — отдельная 250×48dp карта с пунктом «Применить изменения» БЕЗ
 * иконки. Используется в variant 3 (MODAL_STICKY) split-apply layout: одна и та же
 * карта рендерится под основным меню на tab=0 (Ответ) и под LinkPopoverCard на tab=1
 * (Ссылка).
 */
@Composable
internal fun QuoteMenuApplyChangesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val containerBg = appSurface02(isDark)
    Box(
        modifier = modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg),
    ) {
        QuoteMenuItem(MenuItemSpec(APPLY_LABEL, APPLY_ICON, showIcon = false, onClick = onClick))
    }
}

@Composable
private fun QuoteMenuIcon(iconName: String, tint: Color) {
    val ctx = LocalContext.current
    // DSIcon.named returns Drawable? (always BitmapDrawable from the implementation).
    // Signature: fun named(context, name, sizeDp: Float = 24f): Drawable?
    var bitmap by remember(iconName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(iconName) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, iconName, 24f) as? BitmapDrawable)?.bitmap
        }
    }
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

/**
 * Variant 2 (MODAL_STICKY_2) main menu — двухкнопочный choice «Ответ на сообщение /
 * Цитата на сообщение» с checkmark-индикатором на активном пункте. Заменяет FSM-меню
 * со слайд-анимациями. Тапы перенаправляются в onSelect, который дёргает FSM наружу
 * (clearSelection / selectAll).
 *
 * Спека Figma 8954:1123959.
 */
@Composable
internal fun QuoteMenuChoice(
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val containerBg = appSurface02(isDark)
    val checkTint = remember(context) { Color(DSColors.success(context)) }
    val items = listOf("Ответ на сообщение", "Цитата на сообщение")
    Box(
        modifier = modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, label ->
                QuoteMenuChoiceItem(
                    label = label,
                    showCheck = i == activeIndex,
                    checkTint = checkTint,
                    onClick = { onSelect(i) },
                )
                if (i < items.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(appBasic(isDark, 0.08f))
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteMenuChoiceItem(
    label: String,
    showCheck: Boolean,
    checkTint: Color,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val labelColor = appBasic(isDark, 0.9f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showCheck) QuoteMenuIcon("done", checkTint)
        }
    }
}

/**
 * «Применить» card — отдельная карта 250×48dp для variant 2 (MODAL_STICKY_2) под обеими
 * вкладками (Ответ / Ссылка). Текст центрирован, цвет = brand accent (themefirst/accent/
 * text-or-icon). Без иконки.
 *
 * Спека Figma 8954:1123960.
 */
@Composable
internal fun QuoteMenuApply(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current
    val containerBg = appSurface02(isDark)
    val accentText = Color(brand.accentColor(isDark))
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (isPressed) appBasic(isDark, 0.08f) else containerBg
    Box(
        modifier = modifier
            .width(250.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Применить",
            color = accentText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
        )
    }
}
