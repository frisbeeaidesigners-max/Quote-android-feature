package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.designsystem.DSIcon
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun QuoteFullScreenContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val isDark = LocalIsDark.current

    // FSM state — initial по тому же правилу что в QuoteModalContent.
    val hasSelectableText = when (message) {
        is Message.Text -> message.body.isNotEmpty()
        is Message.Media -> !message.caption.isNullOrEmpty()
        else -> false
    }
    var menuState by rememberSaveable {
        mutableStateOf(
            when {
                !hasSelectableText -> QuoteMenuState.INITIAL_MINIMAL
                initialStart < initialEnd -> QuoteMenuState.INITIAL_WITH_QUOTE
                else -> QuoteMenuState.INITIAL
            }
        )
    }

    // Refs для QuoteBubblePreview (как в QuoteModalContent).
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
    val clearSelectionRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Системная back-кнопка дисмиссит picker. В SELECTING-режиме на превью активны
    // selection-handle'ы (popup-окна custom-controller'а) — если зайти на onDismiss
    // напрямую, View успевает уйти из иерархии, а handle-окна остаются «подвешенными».
    // Чистим selection синхронно до dismiss, пока View ещё жива.
    BackHandler {
        clearSelectionRef.value?.invoke()
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header — HeaderConfig.Custom из :components.
        // Stable lambdas через rememberUpdatedState: HeaderHost.configure() пересоздаёт
        // кнопки на каждый non-equal config, lambda equality = reference equality, поэтому
        // создаём lambda один раз и держим actual callback'и в state-ref'ах.
        val onLeftClickLatest = androidx.compose.runtime.rememberUpdatedState(newValue = {
            clearSelectionRef.value?.invoke()
            onDismiss()
        })
        val onRightClickLatest = androidx.compose.runtime.rememberUpdatedState(newValue = {
            val range = selectionRef.value?.invoke()
            clearSelectionRef.value?.invoke()
            onConfirm(range?.first ?: 0, range?.last ?: 0)
        })
        val headerConfig = remember(menuState) {
            val title = when (menuState) {
                QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
                QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
            }
            // «Применить» всегда видна. С selection → quote, без selection → ответ без quote
            // (onConfirm(0,0) → ViewModel.clearQuote → reply остаётся без цитаты).
            com.example.components.headers.HeadersView.HeaderConfig.Custom(
                title = title,
                description = "Вы можете процитировать фрагмент сообщения",
                titleAlignment = com.example.components.headers.HeadersView.HeaderConfig.Custom.TitleAlignment.BOTTOM,
                leftButton = com.example.components.headers.HeadersView.HeaderConfig.Custom.LeftButton.CLOSE,
                rightButton = com.example.components.headers.HeadersView.HeaderConfig.Custom.RightButton.TEXT,
                rightButtonType = com.example.components.headers.HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
                rightButtonLabel = "Применить",
                onLeftClick = { onLeftClickLatest.value() },
                onRightClick = { onRightClickLatest.value() },
            )
        }
        com.example.template.core.ui.hosts.HeaderHost(config = headerConfig)

        // Preview area — Task 5
        PreviewArea(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            tvRef = tvRef,
            selectAllRef = selectAllRef,
            selectionRef = selectionRef,
            clearSelectionRef = clearSelectionRef,
            onSelectionStart = {
                if (menuState != QuoteMenuState.SELECTING) {
                    menuState = QuoteMenuState.SELECTING
                }
            },
            onSelectionEnd = {
                // После Copy / тапа вне выделения / любого clearSelection из controller'а
                // FSM возвращается в INITIAL (или INITIAL_WITH_QUOTE если был сохранён quote),
                // чтобы нижние menu rows не висели на «Назад» / «Цитировать фрагмент».
                if (menuState == QuoteMenuState.SELECTING) {
                    menuState = if (initialStart < initialEnd) QuoteMenuState.INITIAL_WITH_QUOTE
                    else QuoteMenuState.INITIAL
                }
            },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Menu rows — Task 6
        FullScreenMenuRows(
            state = menuState,
            onSelectFragment = {
                selectAllRef.value?.invoke()
                menuState = QuoteMenuState.SELECTING
            },
            onApply = {
                val range = selectionRef.value?.invoke()
                clearSelectionRef.value?.invoke()
                onConfirm(range?.first ?: 0, range?.last ?: 0)
            },
            onCancelReply = {
                clearSelectionRef.value?.invoke()
                onCancelReply()
            },
            onBack = {
                clearSelectionRef.value?.invoke()
                menuState = QuoteMenuState.INITIAL
            },
            onConfirmQuote = {
                val range = selectionRef.value?.invoke()
                clearSelectionRef.value?.invoke()
                onConfirm(range?.first ?: 0, range?.last ?: 0)
            },
            onClearQuote = {
                clearSelectionRef.value?.invoke()
                menuState = QuoteMenuState.INITIAL
            },
        )
    }
}

@Composable
private fun PreviewArea(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    tvRef: androidx.compose.runtime.MutableState<TextView?>,
    selectAllRef: androidx.compose.runtime.MutableState<(() -> Unit)?>,
    selectionRef: androidx.compose.runtime.MutableState<(() -> IntRange?)?>,
    clearSelectionRef: androidx.compose.runtime.MutableState<(() -> Unit)?>,
    onSelectionStart: () -> Unit,
    onSelectionEnd: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = appSurface01(isDark)

    // Два clip rect'а (см. QuoteBubblePreview):
    //  • handle rect — границы preview + 24dp снизу на overflow маркера (anchor на
    //    baseline текста, body спускается вниз; без extra-bottom маркер пропадал раньше
    //    времени когда выделена нижняя строка).
    //  • menu rect — строго границы preview без расширения, чтобы floating menu
    //    при «selectionBelow» пинилась к реальному нижнему краю preview с 8dp safe-зоной.
    val previewHandleClipRect = remember { androidx.compose.runtime.mutableStateOf<android.graphics.Rect?>(null) }
    val previewMenuClipRect = remember { androidx.compose.runtime.mutableStateOf<android.graphics.Rect?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleOverflowPx = with(density) { 24.dp.roundToPx() }

    Box(
        modifier = modifier
            .background(previewBg)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                val x = pos.x.toInt()
                val y = pos.y.toInt()
                val menuR = android.graphics.Rect(x, y, x + size.width, y + size.height)
                val handleR = android.graphics.Rect(x, y, x + size.width, y + size.height + handleOverflowPx)
                if (previewMenuClipRect.value != menuR) previewMenuClipRect.value = menuR
                if (previewHandleClipRect.value != handleR) previewHandleClipRect.value = handleR
            },
    ) {
        // Pattern background — AndroidView, full-width full-height.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BackgroundPatternView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    configure(patternAsset, patternColorScheme)
                }
            },
            update = { v -> v.configure(patternAsset, patternColorScheme) },
        )

        // Bottom-aligned scrollable bubble.
        val scrollState = rememberScrollState()
        LaunchedEffect(message.id) {
            snapshotFlow { scrollState.maxValue }
                .first { it > 0 }
                .let { scrollState.scrollTo(it) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Vertical inset (top 8dp / bottom 16dp) реализован Spacer'ами ВНУТРИ
            // scrollable, а не padding'ом на Column — padding клип'ит content на свою
            // величину, и крупный бабл при скролле обрезался "под отступом". Spacer'ы
            // прокручиваются вместе с баблом и обрезаются настоящим edge'ом preview Box'а.
            Spacer(Modifier.height(8.dp))
            QuoteBubblePreview(
                message = message,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = isMine,
                tvRef = tvRef,
                onQuoteFromActionMode = onConfirm,
                selectAllRef = selectAllRef,
                selectionRef = selectionRef,
                clearSelectionRef = clearSelectionRef,
                onSelectionStart = onSelectionStart,
                onSelectionEnd = onSelectionEnd,
                onCopyFromActionMode = onDismiss,
                initialSelectionStart = initialStart,
                initialSelectionEnd = initialEnd,
                previewScrollState = scrollState,
                previewClipRect = previewHandleClipRect.value,
                previewMenuClipRect = previewMenuClipRect.value,
                previewScrollInProgress = scrollState.isScrollInProgress,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DsIconImage(name: String, tint: Color, sizeDp: Int = 24) {
    val ctx = LocalContext.current
    var bitmap by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(name) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, name, sizeDp.toFloat()) as? BitmapDrawable)?.bitmap
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Spacer(Modifier.size(sizeDp.dp))
    }
}

// Bundled Roboto TTF из :components — на MIUI FontWeight.Medium без явного font fallback'ится
// на Mi Sans Regular, и Medium-вес визуально не виден.
private val FullScreenRobotoFontFamily = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

private data class FullScreenMenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun FullScreenMenuRows(
    state: QuoteMenuState,
    onSelectFragment: () -> Unit,
    onApply: () -> Unit,
    onCancelReply: () -> Unit,
    onBack: () -> Unit,
    onConfirmQuote: () -> Unit,
    onClearQuote: () -> Unit,
) {
    val isDark = LocalIsDark.current
    // Фиксированная высота секции 136dp — визуально совпадает с MessagePanel
    // при открытом reply-context. Контент всех FSM-состояний (max 2 пункта =
    // 96dp + 16dp vertical padding = 112dp) укладывается с запасом; при переходах
    // между состояниями preview-area не дёргается.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .background(appSurface01(isDark))
            .padding(vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * dir } +
                    fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it * dir } +
                        fadeOut(tween(120)))
            },
            label = "FullScreenMenuFsm",
        ) { current ->
            val items: List<FullScreenMenuItemSpec> = when (current) {
                QuoteMenuState.INITIAL -> listOf(
                    FullScreenMenuItemSpec("Выбрать фрагмент", "quote-full", onClick = onSelectFragment),
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
                QuoteMenuState.INITIAL_WITH_QUOTE -> listOf(
                    FullScreenMenuItemSpec("Снять выделение", "cancel-quote", onClick = onClearQuote),
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
                QuoteMenuState.SELECTING -> listOf(
                    FullScreenMenuItemSpec("Назад", "back", onClick = onBack),
                    FullScreenMenuItemSpec("Цитировать фрагмент", "quote", onClick = onConfirmQuote),
                )
                QuoteMenuState.INITIAL_MINIMAL -> listOf(
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item -> FullScreenMenuRow(item) }
            }
        }
    }
}

@Composable
private fun FullScreenMenuRow(item: FullScreenMenuItemSpec) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)
    val iconColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.55f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            )
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DsIconImage(name = item.iconName, tint = iconColor, sizeDp = 24)
        Spacer(Modifier.width(24.dp))
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontFamily = FullScreenRobotoFontFamily,
            fontWeight = FontWeight.Normal,
        )
    }
}
