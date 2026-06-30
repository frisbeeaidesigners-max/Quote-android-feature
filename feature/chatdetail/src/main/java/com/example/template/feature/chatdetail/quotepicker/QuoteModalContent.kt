package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appSurface01
import kotlinx.coroutines.flow.first

@Composable
fun QuoteModalContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    @Suppress("UNUSED_PARAMETER") fullText: String,
    initialStart: Int,
    initialEnd: Int,
    draftText: String,
    variant: QuoteVariant,
    linkRender: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val swipeEnabled = variant == QuoteVariant.MODAL_SWIPE
    // BUTTONS пилл нет связи с linkRender'ом по геометрии, но кнопки-стрелки — только при ON.
    val buttonsShowArrows = variant == QuoteVariant.MODAL_BUTTONS && linkRender
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current

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
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // link-tab достижим: SWIPE (всегда), BUTTONS+ON (стрелки), STICKY+ON (segmented control
    // ниже меню). Если flip variant/linkRender уберёт доступ — paranoid reset на tab=0.
    LaunchedEffect(variant, linkRender) {
        val linkTabReachable = variant == QuoteVariant.MODAL_SWIPE ||
            ((variant == QuoteVariant.MODAL_BUTTONS || variant == QuoteVariant.MODAL_STICKY) && linkRender)
        if (!linkTabReachable) selectedTab = 0
    }
    var snapshotRange by rememberSaveable {
        mutableStateOf(
            if (initialStart < initialEnd) initialStart to initialEnd else null
        )
    }
    var linkPopoverSelection by rememberSaveable { mutableStateOf(0) }

    val tvRef = remember { mutableStateOf<android.widget.TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
    val clearSelectionRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val restoreSelectionRef = remember { mutableStateOf<((Int, Int) -> Unit)?>(null) }

    // Tracking real transitions (selectedTab change) — initial composition не считается.
    val previousTab = remember { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        val transitioning = previousTab.value != selectedTab
        previousTab.value = selectedTab
        if (selectedTab == 1) {
            // Capture live selection до clear.
            val live = selectionRef.value?.invoke()
            if (live != null && live.first < live.last) {
                snapshotRange = live.first to live.last
            }
            if (snapshotRange != null && menuState != QuoteMenuState.INITIAL_MINIMAL) {
                menuState = QuoteMenuState.INITIAL_WITH_QUOTE
            }
            clearSelectionRef.value?.invoke()
            tvRef.value?.clearFocus()
        } else if (transitioning) {
            val range = snapshotRange
            if (range != null) {
                restoreSelectionRef.value?.invoke(range.first, range.second)
            }
        }
    }

    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = Color(brand.messageScreenBackground(isDark))

    val previewHandleClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val previewMenuClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Drag-state поднят сюда из SwipeFooter, чтобы свайп ловился по всему модалу,
    // а не только по footer'у. SwipeFooter получает dragOffset через () → Float
    // и сдвигает title визуально.
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    var modalWidthPx by remember { mutableStateOf(0) }
    val velocityThresholdPxPerS = with(density) { 600.dp.toPx() }
    val commitDistanceRatio = 0.20f
    val currentTab by rememberUpdatedState(selectedTab)

    val draggableState = rememberDraggableState { delta ->
        coroutineScope.launch {
            val raw = dragOffset.value + delta
            val clamped = if (currentTab == 0) raw.coerceAtMost(0f) else raw.coerceAtLeast(0f)
            dragOffset.snapTo(clamped)
        }
    }

    val swipeModifier = if (swipeEnabled) {
        Modifier
            .onSizeChanged { modalWidthPx = it.width }
            .draggable(
                orientation = Orientation.Horizontal,
                state = draggableState,
                onDragStopped = { velocity ->
                    val w = modalWidthPx
                    val current = dragOffset.value
                    val distanceCommit = w > 0 && current.absoluteValue > w * commitDistanceRatio
                    val sameDirection = (velocity > 0f && current > 0f) || (velocity < 0f && current < 0f)
                    val velocityCommit = sameDirection &&
                        velocity.absoluteValue > velocityThresholdPxPerS &&
                        current.absoluteValue > 0f
                    if (distanceCommit || velocityCommit) {
                        // selectedTab меняется ДО анимации — AnimatedContent (title) запустит
                        // свой slide-out из текущей позиции; параллельно dragOffset плавно идёт
                        // к 0, чтобы движение продолжалось без «прыжка» от drag-точки.
                        selectedTab = 1 - currentTab
                        dragOffset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                    } else {
                        dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                },
            )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(swipeModifier),
    ) {
    // STICKY-вариант 3 использует системные insets и flexible preview Box (weight(1f)),
    // чтобы preview поднимался под status bar, а menu+segmented прижимались к nav bar.
    // SWIPE/BUTTONS сохраняют исходный визуал (44dp top padding, фикс 564dp preview).
    val isSticky = variant == QuoteVariant.MODAL_STICKY
    val columnInsetMod = if (isSticky)
        Modifier.statusBarsPadding().navigationBarsPadding().padding(bottom = 16.dp)
    else
        Modifier.padding(top = 44.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(columnInsetMod)
            .padding(start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        val handleOverflowPx = with(density) { 24.dp.roundToPx() }
        // V1 sticky-header режим — variant == MODAL_STICKY: sticky header INSIDE preview Box,
        // без внешнего bottom footer'а. Зеркалит первую итерацию Modal'а из sibling
        // android-template-quote @ 4c4036f.
        val useV1StickyHeader = isSticky
        // Preview Box corner radius: BUTTONS → 34dp (выраженный pill вокруг floating pill-
        // хедера), SWIPE и STICKY → 24dp.
        val previewCornerRadius = if (variant == QuoteVariant.MODAL_BUTTONS) 34.dp else 24.dp
        // Preview Box размер: STICKY — weight(1f) чтобы поднять под status bar, остальные —
        // фиксированные 564dp.
        Box(
            (if (isSticky) Modifier.weight(1f) else Modifier.height(564.dp))
                .fillMaxWidth()
                .widthIn(max = 351.dp)
                .clip(RoundedCornerShape(previewCornerRadius))
                .background(previewBg)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    val size = coords.size
                    val left = pos.x.toInt()
                    val top = pos.y.toInt()
                    val right = pos.x.toInt() + size.width
                    val bottom = pos.y.toInt() + size.height
                    val menuR = android.graphics.Rect(left, top, right, bottom)
                    val handleR = android.graphics.Rect(left, top, right, bottom + handleOverflowPx)
                    if (previewMenuClipRect.value != menuR) previewMenuClipRect.value = menuR
                    if (previewHandleClipRect.value != handleR) previewHandleClipRect.value = handleR
                }
                .pointerInput(Unit) { detectTapGestures { /* consume — тапы внутри preview не должны закрывать модал */ } },
        ) {
            // Pattern background — общий для обеих вкладок (LinkPreview сам рисует свой,
            // но снаружи preview Box'а у нас единая раскладка). Здесь pattern остаётся на
            // tab=0; на tab=1 поверх Pattern'а ляжет LinkPreview со своим фоном appSurface01.
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

            // SWIPE: контент ограничен 74dp снизу — footer непрозрачный, под него ничего не
            // лезет; bottomContentReserve=16dp.
            // BUTTONS: floating pill 58dp + 8dp inset = 66dp занимает overlay'ем; контент не
            // ограничен padding'ом (pill ляжет ПОВЕРХ), но bottomContentReserve=82dp чтобы
            // бабл по умолчанию был на 16dp выше pill'а.
            // STICKY: footer'а нет (sticky header overlay сверху); bottomContentReserve=16dp.
            val contentBottomPad = if (variant == QuoteVariant.MODAL_SWIPE) 74.dp else 0.dp
            val bottomContentReserve = if (variant == QuoteVariant.MODAL_BUTTONS) 82.dp else 16.dp
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(220, easing = FastOutSlowInEasing)) using
                        SizeTransform(clip = false)
                },
                label = "modalPreviewSwap",
                modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPad),
            ) { tab ->
                if (tab == 0) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(message.id) {
                        snapshotFlow { scrollState.maxValue }
                            .first { it > 0 }
                            .let { scrollState.scrollTo(it) }
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // 60dp в V1-режиме резервирует место под sticky header'ом сверху;
                        // в остальных случаях — стандартный 16dp top-gap.
                        Spacer(Modifier.height(if (useV1StickyHeader) 60.dp else 16.dp))
                        QuoteBubblePreview(
                            message = message,
                            senderPersona = senderPersona,
                            senderAvatar = senderAvatar,
                            isMine = isMine,
                            tvRef = tvRef,
                            onQuoteFromActionMode = onConfirm,
                            onCopyFromActionMode = onDismiss,
                            selectAllRef = selectAllRef,
                            selectionRef = selectionRef,
                            clearSelectionRef = clearSelectionRef,
                            restoreSelectionRef = restoreSelectionRef,
                            onSelectionStart = {
                                if (menuState != QuoteMenuState.SELECTING) {
                                    menuState = QuoteMenuState.SELECTING
                                }
                            },
                            onSelectionEnd = {
                                if (menuState == QuoteMenuState.SELECTING) {
                                    menuState = if (initialStart < initialEnd) QuoteMenuState.INITIAL_WITH_QUOTE
                                    else QuoteMenuState.INITIAL
                                }
                            },
                            initialSelectionStart = initialStart,
                            initialSelectionEnd = initialEnd,
                            previewScrollState = scrollState,
                            previewClipRect = previewHandleClipRect.value,
                            previewMenuClipRect = previewMenuClipRect.value,
                            previewScrollInProgress = scrollState.isScrollInProgress,
                        )
                        // Bottom-Spacer внутри scroll-content (а не padding(bottom) на Column'е).
                        // При overflow это даёт «хвост», прокручиваемый вместе с баблом. Для V2
                        // высота включает резерв под пилл-overlay (66dp) + 16dp gap.
                        Spacer(Modifier.height(bottomContentReserve))
                    }
                } else {
                    QuoteModalLinkPreview(
                        message = message,
                        senderPersona = senderPersona,
                        isMine = isMine,
                        snapshotRange = snapshotRange,
                        draftText = draftText,
                        bottomContentReserve = bottomContentReserve,
                        onBack = if (variant == QuoteVariant.MODAL_BUTTONS) {
                            { selectedTab = 0 }
                        } else null,
                    )
                }
            }

            // Footer chrome — variant-driven. SWIPE и STICKY: linkRender placeholder
            // (одинаковый вид ON/OFF — реализуем позже). BUTTONS: linkRender гейтит стрелки.
            when (variant) {
                QuoteVariant.MODAL_SWIPE -> QuoteModalSwipeFooter(
                    selectedTab = selectedTab,
                    menuState = menuState,
                    dragOffsetPx = { dragOffset.value },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(74.dp)
                        .background(appSurface01(isDark))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                QuoteVariant.MODAL_STICKY -> QuoteModalStickyHeader(
                    selectedTab = selectedTab,
                    menuState = menuState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(
                    selectedTab = selectedTab,
                    menuState = menuState,
                    onPrev = { selectedTab = 1 - selectedTab },
                    onNext = { selectedTab = 1 - selectedTab },
                    showButtons = buttonsShowArrows,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(50))
                        .background(appSurface01(isDark))
                        .padding(8.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Popover row — AnimatedContent (QuoteMenu ↔ LinkPopoverCard). По макету — центрировано.
        // STICKY: фиксируем высоту слота — preview получает детерминированную высоту независимо
        // от текущего menuState. С splitApply=true (для STICKY) max-высота меню:
        //   2-item main (2*48 + 0.5 = 96.5dp) + 4dp gap + apply-card (48dp) = 148.5dp
        // → используем 150dp с round-up. Меню само выравнивается по верху слота.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .then(if (isSticky) Modifier.height(150.dp) else Modifier)
                .pointerInput(Unit) { detectTapGestures { /* consume */ } }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(220, easing = FastOutSlowInEasing)) using
                        SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) }
                },
                label = "modalPopoverSwap",
            ) { tab ->
                if (tab == 0) {
                    QuoteMenu(
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
                        splitApply = isSticky,
                    )
                } else {
                    QuoteModalLinkPopoverCard(
                        selectedIndex = linkPopoverSelection,
                        onSelect = { linkPopoverSelection = it },
                    )
                }
            }
        }

        // STICKY + linkRender=ON: segmented control «Ответ / Ссылка» — последний child
        // Column'а (Column'у nav-bar inset уже применён выше). 16dp gap от menu сверху,
        // segmented сидит на нижнем краю safe area. preview Box(weight=1f) растягивается
        // вверх, толкая menu и segmented к низу.
        if (variant == QuoteVariant.MODAL_STICKY && linkRender) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(Unit) { detectTapGestures { /* consume */ } },
            ) {
                QuoteModalReplyLinkSegmented(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        }
    }
    }
}
