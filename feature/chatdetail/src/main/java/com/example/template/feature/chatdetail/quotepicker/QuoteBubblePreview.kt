package com.example.template.feature.chatdetail.quotepicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarColorScheme
import com.example.components.bubbles.BubblesView
import com.example.components.bubbles.MediaBubbleView
import com.example.components.bubbles.SelectionMenuItem
import com.example.components.bubbles.VoiceBubbleView
import com.example.components.designsystem.DSBrand
import com.example.template.core.model.AttachmentType
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface02
import com.example.template.core.ui.format.TimeFormatter
import com.example.template.core.ui.theme.avatarColorSchemeForGradient
import com.example.template.core.ui.utils.findMessageTextView

/**
 * Минимальная Compose-обёртка над `BubblesView`/`MediaBubbleView` для preview в QuotePickerModal.
 *
 * Selection через `setMessageTextSelectable(selectable=true, menuItems=...)`. CustomSelectionController
 * внутри `:components` рисует highlight + handles + FloatingMenu со «Скопировать»/«Цитировать».
 *
 * - [tvRef] — ссылка на inner TextView (после configure). Полезно для размеров; selection ЧИТАЕМ через
 *   `view.messageTextSelection`, не через `tv.selectionStart/End`.
 * - [onQuoteFromActionMode] — вызывается из FloatingMenu «Цитировать».
 * - [selectAllRef] — лямбда, которую outer FSM вызывает на «Выбрать фрагмент» (`view.selectAllMessageText()`).
 */
@Composable
fun QuoteBubblePreview(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    tvRef: MutableState<TextView?>,
    onQuoteFromActionMode: (Int, Int) -> Unit,
    /** «Скопировать» в FloatingMenu: текст уже в clipboard; хост получает сигнал
     *  закрыть picker БЕЗ изменения текущего reply/quote-контекста. */
    onCopyFromActionMode: () -> Unit = {},
    selectAllRef: MutableState<(() -> Unit)?>,
    selectionRef: MutableState<(() -> IntRange?)?>,
    clearSelectionRef: MutableState<(() -> Unit)?>,
    onSelectionStart: () -> Unit = {},
    /** Fires when selection becomes INACTIVE — e.g., after Copy tap clears the selection
     *  via the controller. Host uses this to roll the FSM back from SELECTING to INITIAL
     *  so the bottom menu rows don't stay stuck on «Назад» / «Цитировать фрагмент». */
    onSelectionEnd: () -> Unit = {},
    initialSelectionStart: Int = 0,
    initialSelectionEnd: Int = 0,
    /** ScrollState of the Compose-scroll container around the preview. Когда user
     *  оттаскивает handle к краю preview, [com.example.components.bubbles.CustomSelectionController]
     *  репортит scroll-delta — мы пробрасываем его через `dispatchRawDelta` чтобы
     *  Compose-скролл двинулся. Без этого native fallback (View.scrollBy) не находит
     *  скролл-контейнер (Compose) и auto-scroll не работает. */
    previewScrollState: ScrollState? = null,
    /** Window-space clip rect для **handles** — обычно границы preview-области с
     *  расширением вниз на handle-overflow (~24dp), чтобы маркер не отрезался когда
     *  выделена нижняя строка. Без него `SelectionClipRect.resolve` fallback'ится на
     *  размер экрана (Compose-окно не имеет native scroll-container'а). */
    previewClipRect: Rect? = null,
    /** Window-space clip rect для **floating menu** — обычно строго границы preview
     *  без расширения. При `null` используется [previewClipRect]. */
    previewMenuClipRect: Rect? = previewClipRect,
    /** true когда Compose-скролл preview-контейнера сейчас активен. На время скролла
     *  floating menu паркуется off-screen — без этого она дрожит / визуально мешает. */
    previewScrollInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val senderName = senderPersona?.let { "${it.firstName} ${it.lastName}" } ?: if (isMine) "Вы" else "Собеседник"
    val senderInitials = senderPersona?.initials ?: "?"
    val gradientIndex = senderPersona?.gradientIndex ?: 0

    val avatarScheme = remember(brand, isDark, gradientIndex) {
        resolveAvatarScheme(brand, isDark, gradientIndex)
    }

    val accentInt = brand.accentColor(isDark)
    val highlightColor = (accentInt and 0x00FFFFFF) or 0x66000000
    val menuBg = appSurface02(isDark).toArgb()
    val menuText = appBasic(isDark, 0.9f).toArgb()
    val menuRipple = appBasic(isDark, 0.08f).toArgb()

    // Suppresses onSelectionStart callback во время programmatic вызовов
    // (selectAllMessageText / selectMessageTextRange) — нам нужен callback только
    // на real long-press, чтобы переключить меню picker'а на SELECTING. Programmatic
    // вызовы инициируются caller'ом, который сам управляет menuState.
    val suppressOnStart = remember { booleanArrayOf(false) }

    // Bridge auto-scroll requests из CustomSelectionController в Compose-скролл preview'а.
    // dispatchRawDelta — non-suspending, мы под main-looper'ом (controller's scroll-handler
    // — Handler(Looper.getMainLooper())). Если previewScrollState не передан, callback null
    // → controller fallback'ит на View.scrollBy native контейнера, который в Compose-окнах
    // не находится. Без bridge'а auto-scroll в V2/V3 picker'е не работает.
    val autoScrollCallback: ((Int) -> Unit)? = remember(previewScrollState) {
        previewScrollState?.let { ss -> { dy: Int -> ss.dispatchRawDelta(dy.toFloat()) } }
    }

    when (message) {
        is Message.Text -> {
            val time = remember(message.timestamp) { TimeFormatter.formatBubbleTime(message.timestamp) }
            val bubblesScheme = remember(brand, isDark) { brand.bubblesColorScheme(isDark) }
            val sendingState = remember(message.status) { message.status.toBubblesState() }
            AndroidView(
                modifier = modifier.fillMaxWidth(),
                factory = { ctx ->
                    BubblesView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view ->
                    view.configure(
                        type = if (isMine) BubblesView.BubbleType.MY else BubblesView.BubbleType.SOMEONE,
                        message = message.body,
                        sender = senderName,
                        time = time,
                        avatar = senderAvatar,
                        colorScheme = bubblesScheme,
                        sendingState = sendingState,
                        isFirstInGroup = true,
                        isLastInGroup = true,
                        avatarColorScheme = avatarScheme,
                        senderInitials = senderInitials,
                        replySender = message.replyTo?.authorName,
                        replyText = message.replyTo?.text,
                        isEdited = message.isEdited,
                        onReplyBlockClick = null,
                        showQuoteIcon = message.replyTo?.quoteStart != null,
                        quoteIconTint = accentInt,
                    )
                    val menuItems = listOf(
                        SelectionMenuItem("Скопировать") {
                            val range = view.messageTextSelection ?: return@SelectionMenuItem
                            // range.last из controller'а — exclusive end (convention SelectionState.end),
                            // подтверждено BubblesViewPreviewScreen: substring(first, last) без +1.
                            val text = message.body.substring(range.first, range.last)
                            val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("message", text))
                            // Закрываем picker БЕЗ изменения reply/quote — если был выбранный
                            // фрагмент, он остаётся; если был простой reply, тоже.
                            view.clearMessageTextSelection()
                            onCopyFromActionMode()
                        },
                        SelectionMenuItem("Цитировать") {
                            val range = view.messageTextSelection ?: return@SelectionMenuItem
                            // Dismiss handles + floating menu ДО dismiss'а Dialog'а — иначе
                            // dangling PopupWindow'ы дают NPE в dispatchDetachedFromWindow при
                            // dispose Dialog'а (особенно в V4 FullScreen где Dialog покрывает экран).
                            view.clearMessageTextSelection()
                            onQuoteFromActionMode(range.first, range.last)
                        },
                    )
                    // Mirror BubblesViewPreviewScreen: установить selectable один раз (через
                    // tag-guard), чтобы controller не сбрасывался на каждой рекомпозиции.
                    if (view.tag !== TAG_SELECTABLE_APPLIED) {
                        view.tag = TAG_SELECTABLE_APPLIED
                        view.setMessageTextSelectable(
                            selectable = true,
                            menuItems = menuItems,
                            highlightColor = highlightColor,
                            handleTint = accentInt,
                            menuBackgroundColor = menuBg,
                            menuTextColor = menuText,
                            menuRippleColor = menuRipple,
                        )
                        view.onMessageTextSelectionStart = {
                            if (!suppressOnStart[0]) onSelectionStart()
                        }
                        view.onMessageTextSelectionEnd = { onSelectionEnd() }
                        // INITIAL_WITH_QUOTE restore: подсвечиваем ранее выбранный фрагмент.
                        if (initialSelectionStart < initialSelectionEnd) {
                            suppressOnStart[0] = true
                            view.post {
                                view.selectMessageTextRange(initialSelectionStart, initialSelectionEnd)
                                suppressOnStart[0] = false
                            }
                        }
                    }
                    tvRef.value = view.findMessageTextView(message.body)
                    selectionRef.value = { view.messageTextSelection }
                    clearSelectionRef.value = { view.clearMessageTextSelection() }
                    // Mirror preview-screen flow: re-apply selectable + selectAll парой,
                    // и через view.post — чтобы layout cycle гарантированно завершился.
                    selectAllRef.value = {
                        view.setMessageTextSelectable(
                            selectable = true,
                            menuItems = menuItems,
                            highlightColor = highlightColor,
                            handleTint = accentInt,
                            menuBackgroundColor = menuBg,
                            menuTextColor = menuText,
                            menuRippleColor = menuRipple,
                        )
                        suppressOnStart[0] = true
                        view.post {
                            view.selectAllMessageText()
                            suppressOnStart[0] = false
                        }
                    }
                    view.setSelectionAutoScrollCallback(autoScrollCallback)
                    view.setSelectionClipRect(previewClipRect, previewMenuClipRect)
                    view.setSelectionMenuTemporarilyHidden(previewScrollInProgress)
                },
            )
        }
        is Message.Media -> {
            val time = remember(message.timestamp) { TimeFormatter.formatBubbleTime(message.timestamp) }
            val mediaScheme = remember(brand, isDark) { brand.mediaColorScheme(isDark) }
            val sendingState = remember(message.status) { message.status.toMediaState() }
            val items = remember(message.attachments) {
                message.attachments.map { att ->
                    val isVideo = att.type == AttachmentType.Video
                    MediaBubbleView.MediaItem(
                        bitmap = att.thumbnail as? Bitmap,
                        isVideo = isVideo,
                        duration = att.durationLabel,
                        buttonState = if (isVideo) MediaBubbleView.ButtonState.PLAY else MediaBubbleView.ButtonState.NONE,
                    )
                }
            }
            AndroidView(
                modifier = modifier.fillMaxWidth(),
                factory = { ctx ->
                    MediaBubbleView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view ->
                    view.configure(
                        type = if (isMine) MediaBubbleView.BubbleType.MY else MediaBubbleView.BubbleType.SOMEONE,
                        items = items,
                        message = message.caption.orEmpty(),
                        sender = senderName,
                        avatarBitmap = senderAvatar,
                        time = time,
                        sendingState = sendingState,
                        colorScheme = mediaScheme,
                        isFirstInGroup = true,
                        isLastInGroup = true,
                        avatarColorScheme = avatarScheme,
                        senderInitials = senderInitials,
                        replySender = message.replyTo?.authorName,
                        replyText = message.replyTo?.text,
                        isEdited = message.isEdited,
                        onReplyBlockClick = null,
                        showQuoteIcon = message.replyTo?.quoteStart != null,
                        quoteIconTint = accentInt,
                    )
                    val caption = message.caption.orEmpty()
                    if (caption.isNotEmpty()) {
                        val menuItems = listOf(
                            SelectionMenuItem("Скопировать") {
                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                val text = caption.substring(range.first, range.last)
                                val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", text))
                                // Закрываем picker БЕЗ изменения reply/quote-контекста.
                                view.clearMessageTextSelection()
                                onCopyFromActionMode()
                            },
                            SelectionMenuItem("Цитировать") {
                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                view.clearMessageTextSelection()
                                onQuoteFromActionMode(range.first, range.last)
                            },
                        )
                        if (view.tag !== TAG_SELECTABLE_APPLIED) {
                            view.tag = TAG_SELECTABLE_APPLIED
                            view.setMessageTextSelectable(
                                selectable = true,
                                menuItems = menuItems,
                                highlightColor = highlightColor,
                                handleTint = accentInt,
                                menuBackgroundColor = menuBg,
                                menuTextColor = menuText,
                                menuRippleColor = menuRipple,
                            )
                            view.onMessageTextSelectionStart = {
                                if (!suppressOnStart[0]) onSelectionStart()
                            }
                            if (initialSelectionStart < initialSelectionEnd) {
                                suppressOnStart[0] = true
                                view.post {
                                    view.selectMessageTextRange(initialSelectionStart, initialSelectionEnd)
                                    suppressOnStart[0] = false
                                }
                            }
                        }
                        tvRef.value = view.findMessageTextView(caption)
                        selectionRef.value = { view.messageTextSelection }
                        clearSelectionRef.value = { view.clearMessageTextSelection() }
                        selectAllRef.value = {
                            view.setMessageTextSelectable(
                                selectable = true,
                                menuItems = menuItems,
                                highlightColor = highlightColor,
                                handleTint = accentInt,
                                menuBackgroundColor = menuBg,
                                menuTextColor = menuText,
                                menuRippleColor = menuRipple,
                            )
                            suppressOnStart[0] = true
                            view.post {
                                view.selectAllMessageText()
                                suppressOnStart[0] = false
                            }
                        }
                        view.setSelectionAutoScrollCallback(autoScrollCallback)
                    view.setSelectionClipRect(previewClipRect, previewMenuClipRect)
                    view.setSelectionMenuTemporarilyHidden(previewScrollInProgress)
                    } else {
                        view.setMessageTextSelectable(selectable = false)
                        view.setSelectionAutoScrollCallback(null)
                        view.setSelectionClipRect(null, null)
                        view.tag = null
                        tvRef.value = null
                        selectionRef.value = null
                        clearSelectionRef.value = null
                        selectAllRef.value = null
                    }
                },
            )
        }
        is Message.Voice -> {
            // Голосовое — не quote-target (нет текста для выделения), но как reply-target
            // его всё равно нужно показывать в preview-области picker'а. FSM в
            // QuoteFullScreenContent ловит этот случай через `hasSelectableText=false →
            // INITIAL_MINIMAL` (заголовок «Ответ на сообщение», без кнопки «Цитировать
            // фрагмент»). Все selection-refs обнуляем — picker не будет звать selectAll/
            // selection-read для voice'а.
            tvRef.value = null
            selectAllRef.value = null
            selectionRef.value = null
            clearSelectionRef.value = null
            val time = remember(message.timestamp) { TimeFormatter.formatBubbleTime(message.timestamp) }
            val totalDuration = remember(message.durationMs) {
                TimeFormatter.formatVoiceDuration(message.durationMs)
            }
            val voiceScheme = remember(brand, isDark) { brand.voiceBubbleColorScheme(isDark) }
            val sendingState = remember(message.status) { message.status.toVoiceState() }
            AndroidView(
                modifier = modifier.fillMaxWidth(),
                factory = { ctx ->
                    VoiceBubbleView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view ->
                    // Static-preview — реальный playback нам тут не нужен: остаёмся в
                    // resting DOWNLOADED state с нулевой позицией (remainingTime = full).
                    view.configure(
                        type = if (isMine) VoiceBubbleView.BubbleType.MY else VoiceBubbleView.BubbleType.SOMEONE,
                        waveformBars = message.waveform,
                        totalDuration = totalDuration,
                        playbackPosition = 0f,
                        remainingTime = totalDuration,
                        playbackState = VoiceBubbleView.PlaybackState.DOWNLOADED,
                        downloadProgress = 0f,
                        sender = senderName,
                        avatarBitmap = senderAvatar,
                        time = time,
                        sendingState = sendingState,
                        colorScheme = voiceScheme,
                        isFirstInGroup = true,
                        isLastInGroup = true,
                        avatarColorScheme = avatarScheme,
                        senderInitials = senderInitials,
                        replySender = message.replyTo?.authorName,
                        replyText = message.replyTo?.text,
                    )
                    view.onPlayPauseClick = {}
                    view.onSeek = {}
                },
            )
        }
        else -> Unit  // Link/CallMeet/System не доступны как quote-targets
    }
}

private val TAG_SELECTABLE_APPLIED = Any()

private fun resolveAvatarScheme(brand: DSBrand, isDark: Boolean, gradientIndex: Int): AvatarColorScheme {
    val pairs = brand.avatarGradientPairs(isDark)
    if (pairs.isEmpty()) return brand.avatarColorScheme(isDark)
    return avatarColorSchemeForGradient(brand, isDark, gradientIndex)
}

private fun MessageStatus.toBubblesState(): BubblesView.SendingState = when (this) {
    MessageStatus.NONE -> BubblesView.SendingState.NONE
    MessageStatus.SENDING -> BubblesView.SendingState.SENDING
    MessageStatus.DELIVERED -> BubblesView.SendingState.DELIVERED
    MessageStatus.READ -> BubblesView.SendingState.READ
    MessageStatus.ERROR -> BubblesView.SendingState.ERROR
}

private fun MessageStatus.toMediaState(): MediaBubbleView.SendingState = when (this) {
    MessageStatus.NONE -> MediaBubbleView.SendingState.NONE
    MessageStatus.SENDING -> MediaBubbleView.SendingState.SENDING
    MessageStatus.DELIVERED -> MediaBubbleView.SendingState.DELIVERED
    MessageStatus.READ -> MediaBubbleView.SendingState.READ
    MessageStatus.ERROR -> MediaBubbleView.SendingState.ERROR
}

private fun MessageStatus.toVoiceState(): VoiceBubbleView.SendingState = when (this) {
    MessageStatus.NONE -> VoiceBubbleView.SendingState.NONE
    MessageStatus.SENDING -> VoiceBubbleView.SendingState.SENDING
    MessageStatus.DELIVERED -> VoiceBubbleView.SendingState.DELIVERED
    MessageStatus.READ -> VoiceBubbleView.SendingState.READ
    MessageStatus.ERROR -> VoiceBubbleView.SendingState.ERROR
}
