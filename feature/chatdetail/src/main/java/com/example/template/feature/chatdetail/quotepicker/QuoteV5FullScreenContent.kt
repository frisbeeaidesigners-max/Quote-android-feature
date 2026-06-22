package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
        val senderName = remember(senderPersona) {
            buildString {
                append(senderPersona?.firstName.orEmpty())
                senderPersona?.lastName?.takeIf { it.isNotEmpty() }?.let { append(' '); append(it) }
            }.trim()
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
            .background(appBasic(isDark, 0.08f))
            .padding(vertical = 2.dp),
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
            label = "V5PopoverFsm",
        ) { current ->
            val items = itemsForState(current, callbacks)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    FullScreenMenuRow(
                        item = item,
                        paddingStart = 16.dp,
                        paddingEnd = 16.dp,
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
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .appClickable(onClick = onIconClick),
            contentAlignment = Alignment.Center,
        ) {
            if (popoverOpen) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appBasic(isDark, 0.08f)),
                )
            }
            DsIconImage(name = "reply-setting-n", tint = appBasic(isDark, 0.55f), sizeDp = 24)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
        ) {
            Text(
                text = senderName,
                style = DSTypography.subhead4M.toComposeTextStyle(),
                color = Color(brand.accentColor(isDark)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText,
                style = DSTypography.subhead2R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
