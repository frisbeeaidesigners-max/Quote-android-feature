package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.template.core.ui.appClickable
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
) {
    val isDark = LocalIsDark.current

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

    BackHandler {
        clearSelectionRef.value?.invoke()
        onDismiss()
    }

    var popoverOpen by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(menuState) {
        if (menuState == QuoteMenuState.SELECTING) popoverOpen = true
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
            val range = selectionRef.value?.invoke()
            clearSelectionRef.value?.invoke()
            onConfirm(range?.first ?: 0, range?.last ?: 0)
        })
        val headerConfig = remember(menuState) {
            val title = when (menuState) {
                QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
                QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
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
        HeaderHost(config = headerConfig)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appSurface01(isDark))
                .padding(horizontal = 16.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Вы можете процитировать фрагмент сообщения",
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
            val callbacks = MenuCallbacks(
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
            androidx.compose.animation.AnimatedVisibility(
                visible = popoverOpen,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp),
                enter = fadeIn(tween(150)) +
                    slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 4 },
                exit = fadeOut(tween(120)),
            ) {
                PopoverCard(state = menuState, callbacks = callbacks)
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
        BottomStrip(
            senderName = senderName,
            previewText = previewText,
            popoverOpen = popoverOpen,
            onIconClick = { popoverOpen = !popoverOpen },
        )
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
            .appClickable(onClick = onIconClick)
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
