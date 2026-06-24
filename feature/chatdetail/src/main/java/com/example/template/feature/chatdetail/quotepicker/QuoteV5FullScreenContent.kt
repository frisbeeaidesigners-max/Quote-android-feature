package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.bubbles.LinkBubbleView
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.first
import com.example.components.segmentedcontrol.SegmentedControlView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.components.headers.HeadersView
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import com.example.template.core.ui.appSurface02
import com.example.template.core.ui.hosts.HeaderHost

@Composable
fun QuoteV5FullScreenContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
    draftText: String = "",
) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current

    // FSM init — то же правило, что у V4 (QuoteFullScreenContent.kt:87-100).
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

    // Selection refs — те же, что у V4 (QuoteFullScreenContent.kt:103-106).
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
    val clearSelectionRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    // V5-only: на возврат с вкладки «Ссылка» восстанавливаем подсветку фрагмента
    // (range берётся из snapshotRange). См. LaunchedEffect(selectedTab) ниже.
    val restoreSelectionRef = remember { mutableStateOf<((Int, Int) -> Unit)?>(null) }

    BackHandler {
        clearSelectionRef.value?.invoke()
        onDismiss()
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Ответ, 1 = Ссылка
    var popoverOpen by rememberSaveable { mutableStateOf(true) }
    // Mock selection for Ссылка-tab popover (Общая информация / Главное сообщение).
    // Содержимое reply-секции LinkBubble НЕ зависит от этого выбора (per spec — popover
    // декоративный, реальной коммутации нет; единственное состояние, которое управляет
    // reply-блоком, — snapshotRange с вкладки «Ответ»).
    var linkPopoverSelection by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(menuState) {
        if (menuState == QuoteMenuState.SELECTING) popoverOpen = true
    }

    var snapshotRange by rememberSaveable {
        mutableStateOf(
            if (initialStart < initialEnd) initialStart to initialEnd else null
        )
    }

    LaunchedEffect(menuState) {
        when (menuState) {
            QuoteMenuState.INITIAL_WITH_QUOTE -> {
                val r = selectionRef.value?.invoke()
                if (r != null && r.first < r.last) snapshotRange = r.first to r.last
            }
            QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> {
                snapshotRange = null
            }
            QuoteMenuState.SELECTING -> Unit
        }
    }

    // Трекаем переход «1 → 0» для restore selection. На initial composition с
    // selectedTab=0 эффект тоже фирится, но previousTab совпадает → restore не вызываем
    // (factory PreviewArea сам инициирует подсветку через initialStart/initialEnd).
    val previousTab = remember { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        val transitioning = previousTab.value != selectedTab
        previousTab.value = selectedTab
        if (selectedTab == 1) {
            // Capture live selection range BEFORE clearing — when the user opens the
            // picker with no pre-existing quote and picks a fragment, the FSM never
            // reaches INITIAL_WITH_QUOTE (onSelectionEnd uses initialStart/initialEnd),
            // so snapshotRange would remain null and the LinkBubble reply block would
            // show the full message instead of the fragment.
            val live = selectionRef.value?.invoke()
            if (live != null && live.first < live.last) {
                snapshotRange = live.first to live.last
            }
            // Если на момент перехода на «Ссылка» цитата выбрана (либо live, либо был
            // снимок), считаем выбор зафиксированным и переводим FSM в INITIAL_WITH_QUOTE.
            // Это нужно, чтобы на возврат пользователя на «Ответ» бабл и popover показывали
            // состояние «цитата уже выбрана» (а не сброшенное INITIAL), как если бы юзер
            // подтвердил выбор фрагмента — он же по факту его подтвердил, перейдя дальше.
            if (snapshotRange != null && menuState != QuoteMenuState.INITIAL_MINIMAL) {
                menuState = QuoteMenuState.INITIAL_WITH_QUOTE
            }
            clearSelectionRef.value?.invoke()
            tvRef.value?.clearFocus()
        } else if (transitioning) {
            // На возврат с вкладки «Ссылка» восстанавливаем подсветку фрагмента из снапшота.
            // Только на реальном переходе, чтобы не дёргать restore на initial composition'е
            // (там factory PreviewArea сам обрабатывает initialStart/initialEnd).
            val range = snapshotRange
            if (range != null) {
                restoreSelectionRef.value?.invoke(range.first, range.second)
            }
        }
        // На любом переключении вкладок popover открыт (правило юзера) — и при входе
        // на «Ссылка», и при возврате на «Ответ».
        popoverOpen = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // HeadersView LEFT alignment не рендерит description → отдельная Row под HeaderHost.
        val onLeftClickLatest = rememberUpdatedState(newValue = {
            clearSelectionRef.value?.invoke()
            onDismiss()
        })
        val onRightClickLatest = rememberUpdatedState(newValue = {
            val live = selectionRef.value?.invoke()
            val (s, e) = if (live != null && live.first < live.last) {
                live.first to live.last
            } else {
                snapshotRange ?: (0 to 0)
            }
            clearSelectionRef.value?.invoke()
            onConfirm(s, e)
        })
        val headerConfig = remember(menuState, selectedTab) {
            val title = if (selectedTab == 1) {
                "Оформление ссылки"
            } else {
                when (menuState) {
                    QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
                    QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
                }
            }
            HeadersView.HeaderConfig.Custom(
                title = title,
                description = null,
                titleAlignment = HeadersView.HeaderConfig.Custom.TitleAlignment.LEFT,
                leftButton = HeadersView.HeaderConfig.Custom.LeftButton.BACK,
                rightButton = HeadersView.HeaderConfig.Custom.RightButton.TEXT,
                rightButtonType = HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
                rightButtonLabel = "Сохранить",
                onLeftClick = { onLeftClickLatest.value() },
                onRightClick = { onRightClickLatest.value() },
            )
        }
        HeaderHost(config = headerConfig, backgroundColorOverride = appSurface01(isDark))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appSurface01(isDark))
                .padding(horizontal = 16.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedTab == 1) "Так будет выглядеть ваша ссылка после отправки"
                       else "Вы можете процитировать фрагмент сообщения",
                style = DSTypography.body1R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
            )
        }

        // Бабл едет вверх синхронно с открытием popover'а. Closed → 16dp (V4-дефолт).
        // Open → menu_height + 20dp (96dp у 2-строчного state'а, 48dp у MINIMAL).
        val targetBubbleBottomDp = when {
            !popoverOpen -> 16.dp
            menuState == QuoteMenuState.INITIAL_MINIMAL -> 68.dp
            else -> 116.dp
        }
        // В SELECTING — snap вместо tween'а. Bubble shift каждый кадр через Compose-layout
        // не успевает протолкнуть recalc через Editor.SelectionController'у — handles
        // (PopupWindow'ы) отстают. Один snap → один layout-pass → handles re-position
        // в тот же кадр, синхронно. В остальных state'ах selection нет, плавный tween OK.
        val animatedBubbleBottomDp by animateDpAsState(
            targetValue = targetBubbleBottomDp,
            animationSpec = if (menuState == QuoteMenuState.SELECTING) snap()
                else tween(220, easing = FastOutSlowInEasing),
            label = "v5BubbleBottomInset",
        )

        // Inset для clip-rect'ов action-bar'а / handles. Popover занимает 8dp (bottom anchor)
        // + popoverHeight + 8dp (gap до клипа). Native Editor пристёгивает floating-bar
        // и handles выше bottomMenu — не залезают в popover-зону при scroll'е длинного бабла.
        val menuClipBottomInsetDp = when {
            !popoverOpen -> 0.dp
            menuState == QuoteMenuState.INITIAL_MINIMAL -> 64.dp   // 8 + 48 + 8
            else -> 112.dp                                          // 8 + 96 + 8
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(popoverOpen) {
                    if (popoverOpen) {
                        detectTapGestures(onTap = { popoverOpen = false })
                    }
                },
        ) {
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
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
                bottomSpacerDp = animatedBubbleBottomDp,
                menuClipBottomInsetDp = menuClipBottomInsetDp,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize(),
            ) {
                LinkBubbleOverlay(
                    message = message,
                    senderPersona = senderPersona,
                    isMine = isMine,
                    snapshotRange = snapshotRange,
                    bottomSpacerDp = animatedBubbleBottomDp,
                    draftText = draftText,
                )
            }
            val callbacks = MenuCallbacks(
                onSelectFragment = {
                    selectAllRef.value?.invoke()
                    menuState = QuoteMenuState.SELECTING
                },
                onApply = {
                    val live = selectionRef.value?.invoke()
                    val (s, e) = if (live != null && live.first < live.last) {
                        live.first to live.last
                    } else {
                        snapshotRange ?: (0 to 0)
                    }
                    clearSelectionRef.value?.invoke()
                    onConfirm(s, e)
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
                    val live = selectionRef.value?.invoke()
                    val (s, e) = if (live != null && live.first < live.last) {
                        live.first to live.last
                    } else {
                        snapshotRange ?: (0 to 0)
                    }
                    clearSelectionRef.value?.invoke()
                    onConfirm(s, e)
                },
                onClearQuote = {
                    clearSelectionRef.value?.invoke()
                    menuState = QuoteMenuState.INITIAL
                },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = popoverOpen,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp),
                enter = fadeIn(tween(150)) +
                    slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 4 },
                exit = fadeOut(tween(120)),
            ) {
                if (selectedTab == 1) {
                    LinkPopoverCard(
                        selectedIndex = linkPopoverSelection,
                        onSelect = { i ->
                            // Popover остаётся открытым после выбора (правило юзера) —
                            // только обновляем selection state.
                            linkPopoverSelection = i
                        },
                    )
                } else {
                    PopoverCard(state = menuState, callbacks = callbacks)
                }
            }
        }
        // Зеркалим ChatDetailViewModel.resolveAuthorName: "Вы" для своих сообщений (у меня
        // самого нет записи в personaForUser, поэтому без явной ветки senderPersona = null →
        // sender пропадал из reply-секции), иначе persona.fullName или "Собеседник".
        val senderName = remember(isMine, senderPersona) {
            if (isMine) "Вы"
            else senderPersona?.fullName?.takeIf { it.isNotEmpty() } ?: "Собеседник"
        }
        val previewText = remember(message) { replyPreviewText(message) }
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            },
            label = "v5BottomStrip",
        ) { tab ->
            if (tab == 1) {
                BottomStripLink(
                    popoverOpen = popoverOpen,
                    onIconClick = { popoverOpen = !popoverOpen },
                )
            } else {
                BottomStrip(
                    senderName = senderName,
                    previewText = previewText,
                    popoverOpen = popoverOpen,
                    onIconClick = { popoverOpen = !popoverOpen },
                )
            }
        }
        // SegmentedControl-секция ПОД reply-строкой. Полная ширина на appSurface01
        // (как сама reply-строка), внутри — surface02 wrap с центрированным контролом.
        // Padding'и: 16dp по бокам, 8dp сверху/снизу.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(appSurface01(isDark))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(appSurface02(isDark)),
            ) {
                AndroidView(
                    factory = { ctx -> SegmentedControlView(ctx) },
                    update = { view ->
                        view.configure(
                            labels = listOf("Ответ", "Ссылка"),
                            selectedIndex = selectedTab,
                            onSelect = { selectedTab = it },
                            colorScheme = brand.segmentedControlColorScheme(isDark),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PopoverCard(
    state: QuoteMenuState,
    callbacks: MenuCallbacks,
) {
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(appSurface02(isDark)),
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
            label = "V5PopoverFsm",
        ) { current ->
            val items = itemsForState(current, callbacks)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    PopoverMenuItem(item)
                    if (i < items.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = appBasic(isDark, 0.08f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopoverMenuItem(item: FullScreenMenuItemSpec) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)
    val iconColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.55f)
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
        DsIconImage(name = item.iconName, tint = iconColor, sizeDp = 24)
    }
}

@Composable
private fun BottomStrip(
    senderName: String,
    previewText: String,
    popoverOpen: Boolean,
    onIconClick: () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val borderColor = appBasic(isDark, 0.08f)
    val stripInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appSurface01(isDark))
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            // Тап по всей секции toggling popover, но БЕЗ ripple-эффекта —
            // indication = null. interactionSource нужен в сигнатуре clickable, но мы
            // его никому не показываем.
            .clickable(
                interactionSource = stripInteractionSource,
                indication = null,
                onClick = onIconClick,
            )
            // end=8dp канон MessagePanel'а (там это компенсация под close-кнопку). У нас
            // close-кнопки нет, но строгое соответствие важнее небольшой визуальной асимметрии.
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon container строго 24dp (как в MessagePanel), 36dp active-circle вешается
        // overflow'ом через requiredSize — не влияет на layout, иначе text уезжал на +12dp.
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (popoverOpen) {
                Box(
                    modifier = Modifier
                        .requiredSize(36.dp)
                        .clip(CircleShape)
                        .background(appBasic(isDark, 0.08f)),
                )
            }
            DsIconImage(name = "reply-setting", tint = appBasic(isDark, 0.55f), sizeDp = 24)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
        ) {
            Text(
                text = senderName,
                style = DSTypography.body4M.toComposeTextStyle(),
                color = Color(brand.accentColor(isDark)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText,
                style = DSTypography.body5R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BottomStripLink(
    popoverOpen: Boolean,
    onIconClick: () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val borderColor = appBasic(isDark, 0.08f)
    val stripInteractionSource = remember { MutableInteractionSource() }
    // Геометрия и поведение строго зеркалит BottomStrip (вкладка «Ответ»), чтобы
    // обе секции имели одинаковую высоту и одинаковый tap-feedback вокруг иконки.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appSurface01(isDark))
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .clickable(
                interactionSource = stripInteractionSource,
                indication = null,
                onClick = onIconClick,
            )
            // end=8dp — компенсация под close-кнопку (как в BottomStrip / MessagePanel canon);
            // bottom=4dp такой же.
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (popoverOpen) {
                Box(
                    modifier = Modifier
                        .requiredSize(36.dp)
                        .clip(CircleShape)
                        .background(appBasic(isDark, 0.08f)),
                )
            }
            DsIconImage(name = "link-chain", tint = appBasic(isDark, 0.55f), sizeDp = 24)
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Прикрепленная ссылка",
                style = DSTypography.body4M.toComposeTextStyle(),
                color = Color(brand.accentColor(isDark)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "https://meet.google.com/dvz-prtb-xyk",
                style = DSTypography.body5R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun extractQuote(message: Message, start: Int, end: Int): String {
    val body = (message as? Message.Text)?.body ?: return replyPreviewText(message)
    if (body.isEmpty()) return replyPreviewText(message)
    val s = start.coerceIn(0, body.length)
    val e = end.coerceIn(s, body.length)
    return if (s < e) body.substring(s, e) else replyPreviewText(message)
}

@Composable
private fun LinkBubbleOverlay(
    message: Message,
    senderPersona: Persona?,
    isMine: Boolean,
    snapshotRange: Pair<Int, Int>?,
    bottomSpacerDp: Dp,
    draftText: String,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val replySender = remember(isMine, senderPersona) {
        if (isMine) "Вы"
        else senderPersona?.fullName?.takeIf { it.isNotEmpty() } ?: "Собеседник"
    }
    val replyText = remember(snapshotRange, message) {
        snapshotRange?.let { (s, e) -> extractQuote(message, s, e) }
            ?: replyPreviewText(message)
    }
    val scheme = remember(brand, isDark) { brand.linkBubbleColorScheme(isDark) }
    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark)),
    ) {
        // Pattern-фон — тот же, что в PreviewArea на вкладке «Ответ», чтобы визуальная
        // континуация между вкладками сохранялась.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BackgroundPatternView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    configure(patternAsset, patternColorScheme)
                }
            },
            update = { v -> v.configure(patternAsset, patternColorScheme) },
        )
        // Бабл прижат к низу — то же расположение, что у QuoteBubblePreview в PreviewArea.
        // bottomSpacerDp получаем из родителя — тот же animated value, что у вкладки «Ответ»,
        // так что бабл едет вверх синхронно с popover'ом.
        //
        // verticalScroll + LaunchedEffect(snapshotFlow{maxValue}.first{it>0}) — зеркалит
        // PreviewArea на «Ответ». Когда LinkBubble + длинный draft занимают почти всю
        // высоту, рост bottomSpacerDp при открытии popover'а раздувает контент Column'а
        // выше viewport'а; без скролла бабл просто выпихивался бы за верх и визуально
        // оставался на месте — popover ложился бы поверх. С автоскроллом на bottom при
        // первом overflow'е бабл реально сдвигается вверх, освобождая место под popover.
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
            // 8dp top spacer — то же, что у PreviewArea на «Ответ» (QuotePickerShared.kt:276).
            // Виден, только если контент короче viewport'а (при Arrangement.Bottom прижат к
            // верху bubble'а, отбивает от описания над преview-боксом).
            Spacer(Modifier.height(8.dp))
            AndroidView(
                // fillMaxWidth без горизонтального padding'а — каноническая обёртка для
                // компонентов-бабблов (см. MessageList.kt:1438 для LinkBubbleView в реальном
                // чате и QuoteBubblePreview для Text/Media/Voice). Внутренние margin'ы бабл
                // считает сам — внешний padding ломает выравнивание MY-края к правому борту.
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    LinkBubbleView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view ->
                    view.configure(
                        type = LinkBubbleView.BubbleType.MY,
                        message = draftText,
                        title = "Google Meet",
                        description = "Видеовстреча для обсуждения задач и обмена ключевыми обновлениями по текущему проекту",
                        url = "https://meet.google.com/dvz-prtb-xyk",
                        domain = "meet.google.com",
                        labels = null,
                        time = "10:15",
                        sendingState = LinkBubbleView.SendingState.READ,
                        replySender = replySender,
                        replyText = replyText,
                        colorScheme = scheme,
                        // Если есть выбранный фрагмент — reply оформляется как цитата
                        // (маленький quote-индикатор в правом верхнем углу reply-секции).
                        showQuoteIcon = snapshotRange != null,
                        quoteIconTint = scheme.replyNameColor,
                    )
                },
            )
            Spacer(Modifier.height(bottomSpacerDp))
        }
    }
}

@Composable
private fun LinkPopoverCard(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val isDark = LocalIsDark.current
    val items = listOf("Общая информация", "Главное сообщение")
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(appSurface02(isDark)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, label ->
                LinkPopoverMenuItem(
                    label = label,
                    isSelected = i == selectedIndex,
                    onClick = { onSelect(i) },
                )
                if (i < items.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = appBasic(isDark, 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkPopoverMenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val labelColor = appBasic(isDark, 0.9f)
    val successColor = remember(context) { Color(com.example.components.designsystem.DSColors.success(context)) }
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
            if (isSelected) {
                DsIconImage(name = "done", tint = successColor, sizeDp = 24)
            }
        }
    }
}
