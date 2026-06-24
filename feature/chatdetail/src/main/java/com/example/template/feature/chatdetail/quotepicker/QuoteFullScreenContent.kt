package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appSurface01

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
    val callbacks = MenuCallbacks(
        onSelectFragment = onSelectFragment,
        onApply = onApply,
        onCancelReply = onCancelReply,
        onBack = onBack,
        onConfirmQuote = onConfirmQuote,
        onClearQuote = onClearQuote,
    )
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
            val items = itemsForState(current, callbacks)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item -> FullScreenMenuRow(item) }
            }
        }
    }
}
