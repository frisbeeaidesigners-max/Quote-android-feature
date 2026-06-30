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
import androidx.compose.foundation.layout.padding
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
    linkRender: Boolean,    // ← НОВЫЙ параметр
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val swipeEnabled = linkRender && variant == QuoteVariant.MODAL_DOTS
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
    // linkRender OFF → tab=0 принудительно. Defensive guard (Profile взаимоисключителен
    // с открытым picker'ом, но если флаг flip'нется через программный путь — снепаем).
    LaunchedEffect(linkRender) {
        if (!linkRender) selectedTab = 0
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 44.dp, start = 12.dp, end = 12.dp)
            .then(swipeModifier),
        horizontalAlignment = Alignment.Start,
    ) {
        val handleOverflowPx = with(density) { 24.dp.roundToPx() }
        // Preview Box (Header c Title/Description/Dots — внутри, прижат к низу).
        // DOTS — 24dp (V1 sticky-header OFF + V2 dots ON), BUTTONS — 34dp (более выраженный
        // pill-стиль вокруг floating pill-хедера).
        val previewCornerRadius = if (variant == QuoteVariant.MODAL_DOTS) 24.dp else 34.dp
        // V1 sticky-header режим: DOTS + linkRender OFF → sticky header INSIDE preview Box,
        // без внешнего bottom footer'а. Зеркалит первую итерацию Modal'а из sibling
        // android-template-quote @ 4c4036f.
        val useV1StickyHeader = !linkRender && variant == QuoteVariant.MODAL_DOTS
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 351.dp)
                .height(564.dp)
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

            // V1: контент ограничен 74dp снизу — footer непрозрачный и под него ничего не лезет.
            // V2: контент занимает весь preview-Box, пилл рендерится z-overlay'ем поверх. Чтобы
            // бабл по умолчанию сидел 16dp выше пилла, ниже в content-Column'е добавим Spacer
            // высотой [bottomContentReserve] = 66 (пилл с inset) + 16 = 82dp.
            val contentBottomPad = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 74.dp else 0.dp
            val bottomContentReserve = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 16.dp else 82.dp
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

            // Footer chrome — SwipeFooter/StaticFooter (Task 5), ButtonsHeader (Task 6).
            if (linkRender) {
                when (variant) {
                    QuoteVariant.MODAL_DOTS -> QuoteModalSwipeFooter(
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
                    QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(
                        selectedTab = selectedTab,
                        menuState = menuState,
                        onPrev = { selectedTab = 1 - selectedTab },
                        onNext = { selectedTab = 1 - selectedTab },
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
            } else if (useV1StickyHeader) {
                // DOTS + OFF — V1 sticky header INSIDE preview Box (TopCenter), без bottom footer'а.
                QuoteModalStickyHeader(
                    menuState = menuState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else {
                // BUTTONS + OFF — bottom-aligned StaticFooter (visually идентично BUTTONS+ON
                // минус кнопки-стрелки).
                QuoteModalStaticFooter(
                    menuState = menuState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(74.dp)
                        .background(appSurface01(isDark))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Popover row — AnimatedContent (QuoteMenu ↔ LinkPopoverCard). По макету — центрировано.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
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
                    )
                } else {
                    QuoteModalLinkPopoverCard(
                        selectedIndex = linkPopoverSelection,
                        onSelect = { linkPopoverSelection = it },
                    )
                }
            }
        }

    }
}
