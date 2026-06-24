package com.example.template.feature.chatdetail

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.template.core.data.MatchResult
import com.example.template.core.data.matchesQuote
import com.example.template.core.model.Message
import kotlinx.coroutines.launch
import com.example.components.avatar.AvatarView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.headers.HeadersView
import com.example.components.messagepanel.MessagePanelView
import com.example.template.core.model.AvatarType
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.PreviewKind
import com.example.template.core.model.UserStatus
import com.example.template.core.ui.BitmapCache
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.format.TimeFormatter
import com.example.template.core.ui.hosts.AudioPanelHost
import com.example.template.core.ui.hosts.HeaderHost
import com.example.template.core.ui.hosts.MessageList
import com.example.template.core.ui.hosts.EditDisplay
import com.example.template.core.ui.hosts.MessagePanelHost
import com.example.template.core.ui.hosts.ReplyDisplay

@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    onBack: () -> Unit,
    onVoiceModeChange: (Boolean) -> Unit = {},
    onStartCall: (com.example.template.core.model.CallContext) -> Unit = {},
) {
    val chat = viewModel.chat ?: return
    val messages by viewModel.messages.collectAsState()
    val replyCtx by viewModel.replyContext.collectAsState()
    val editCtx by viewModel.editContext.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val selectionActive = selection != null
    val highlighted by viewModel.highlightedMessageId.collectAsState()
    val highlightedQuoteRange by viewModel.highlightedQuoteRange.collectAsState()
    val quotePickerVisible by viewModel.quotePickerVisible.collectAsState()
    // КЛЮЧЕВО: voicePlayback здесь НЕ читаем (нет collectAsState на уровне ChatDetailScreen).
    // Подписка изолирована в `ChatAudioPanelSlot` ниже и внутри `MessageList`'s voice-item scope.
    // Иначе body ChatDetailScreen пересобирался бы 30 раз/сек при тике плейбэка, вместе с
    // MessageList (List<Message> нестабилен в Compose-классификации → нельзя smart-skip'нуть).
    // Voice-режим локально, чтобы IME-эффекты (reply/edit ниже) могли его учесть. Перехват
    // callback'а MessagePanelHost — наружу (MainActivity) тоже пересылаем, не теряя поток.
    var isVoiceMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Ссылка на MessagePanelView для двух сценариев:
    //   • outer pointerInput.dismissKeyboard() — тап вне панели снимает фокус.
    //   • LaunchedEffect ниже — при «Ответить» поднимаем IME и фокусим input.
    // factory MessagePanelHost дёргается один раз — ref стабилен на жизнь экрана.
    val panelRef = remember { mutableStateOf<MessagePanelView?>(null) }
    val editDisplay = editCtx?.let { ctx ->
        EditDisplay(
            originalId = ctx.originalId,
            previewText = ctx.originalBody.takeIf { it.isNotBlank() } ?: "Изображение",
            initialBody = ctx.originalBody,
            initialAttachments = ctx.originalAttachments,
        )
    }
    // На каждое новое «Ответить» (другой originalId) — фокусим EditText панели и
    // поднимаем IME. Ключ — originalId, чтобы повторное «Ответить» на то же сообщение
    // после закрытия reply-блока тоже триггерило показ клавиатуры (originalId → null → originalId).
    // Гейт isVoiceMode: если активна voice-панель (например, юзер свайпнул reply ВО ВРЕМЯ записи),
    // input скрыт — клавиатуру не показываем, иначе она вылезет поверх voice-панели.
    val panel = panelRef.value
    LaunchedEffect(panel, replyCtx?.originalId) {
        if (panel != null && replyCtx != null && !isVoiceMode) {
            val edit = panel.findFirstEditText() ?: return@LaunchedEffect
            edit.requestFocus()
            val imm = panel.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    // При входе в edit-mode pre-fill input текстом оригинала, ставим cursor в конец, поднимаем IME.
    // Ключ — originalId, чтобы каждое новое edit'ование триггерило заново даже если editCtx-non-null
    // уже был. Тот же гейт isVoiceMode — edit во время voice-режима не должен показывать клаву
    // (хотя edit обычно начинается из контекстного меню, которое сначала закрывает voice — пока
    // защита от любых будущих сценариев).
    LaunchedEffect(panel, editCtx?.originalId) {
        val ctx = editCtx ?: return@LaunchedEffect
        if (panel == null || isVoiceMode) return@LaunchedEffect
        val edit = panel.findFirstEditText() ?: return@LaunchedEffect
        edit.setText(ctx.originalBody)
        edit.setSelection(ctx.originalBody.length)
        edit.requestFocus()
        val imm = panel.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }
    LaunchedEffect(selectionActive) {
        if (selectionActive) {
            panelRef.value?.dismissKeyboard()
        }
    }
    // При открытии fullscreen picker'а IME прячется через WindowInsetsController в MainActivity,
    // но Editor'у EditText'а MessagePanel'а это безразлично: фокус и selection range остаются —
    // и его selection-handle'ы (PopupWindow'ы) продолжают висеть поверх picker'а. Коллапсим
    // selection до точки: маркеры пропадают (показываются только при start != end), а фокус
    // остаётся — чтобы controller.show(ime()) в onDispose picker'а смог вернуть клавиатуру.
    LaunchedEffect(quotePickerVisible) {
        if (quotePickerVisible) {
            panelRef.value?.findFirstEditText()?.let { edit ->
                edit.setSelection(edit.selectionEnd)
            }
        }
    }
    // Зеркалим текст EditText'а MessagePanel'а в VM, чтобы V5 quote-picker мог использовать
    // его как `message`-поле в mock-LinkBubble на вкладке «Ссылка». TextWatcher
    // переустанавливается при смене panel-инстанса; на dispose чистим listener.
    DisposableEffect(panel) {
        val edit = panel?.findFirstEditText()
        val watcher = edit?.let { e ->
            val w = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    viewModel.setPanelDraftText(s?.toString().orEmpty())
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            e.addTextChangedListener(w)
            // Инициализируем VM текущим значением — на случай рестарта Compose'а без
            // перенаведения watcher'а до первого keystroke.
            viewModel.setPanelDraftText(e.text?.toString().orEmpty())
            w
        }
        onDispose {
            if (edit != null && watcher != null) edit.removeTextChangedListener(watcher)
        }
    }
    BackHandler(enabled = selectionActive) {
        viewModel.dismissSelection()
    }
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    val cacheVersion by bitmapCache.version

    val baseAvatarScheme = remember(brand, isDark) { brand.avatarColorScheme(isDark) }
    val gradientPairs = remember(brand, isDark) { brand.avatarGradientPairs(isDark) }

    val isTyping = chat.lastMessage.kind == PreviewKind.Typing
    val participantStatus = chat.participantIds.firstOrNull()?.let(viewModel::userById)?.status

    val (subtitleText, subtitleStyle) = when {
        // HeadersView.buildTypingSubtitle рисует точки + текстовый лейбл рядом, а внешний guard
        // `subtitle.isNullOrEmpty()` пропускает весь subtitle-блок, если строка пустая. Поэтому
        // передаём осмысленный лейбл — иначе ни точек, ни статуса в хедере не будет.
        isTyping ->
            "печатает" to HeadersView.HeaderConfig.Chat.SubtitleStyle.TYPING
        // «Сохранённые» — это P2P по типу (участник = текущий пользователь), но семантически
        // отдельный чат с самим собой. Никакого статуса присутствия не должно быть. Пустая строка
        // в subtitle отлавливается guard'ом HeadersView и subtitle-блок не отрисовывается.
        chat.avatar.type == AvatarType.Self ->
            "" to HeadersView.HeaderConfig.Chat.SubtitleStyle.DEFAULT
        chat.type == ChatType.P2P -> {
            val text = when (participantStatus) {
                is UserStatus.Online -> "Онлайн"
                is UserStatus.LastSeen -> "Был(а) в сети ${TimeFormatter.formatLastSeen(participantStatus.timestamp)}"
                else -> "Был(а) в сети недавно"
            }
            text to HeadersView.HeaderConfig.Chat.SubtitleStyle.DEFAULT
        }
        chat.type == ChatType.Channel ->
            "канал" to HeadersView.HeaderConfig.Chat.SubtitleStyle.DEFAULT
        else ->
            "${chat.participantIds.size} участников" to HeadersView.HeaderConfig.Chat.SubtitleStyle.DEFAULT
    }

    val headerAvatar = remember(chat.id, bitmapCache, cacheVersion) {
        resolveHeaderAvatar(chat, viewModel, bitmapCache)
    }
    val avatarScheme = remember(baseAvatarScheme, gradientPairs, headerAvatar.gradientIndex) {
        if (gradientPairs.isEmpty()) baseAvatarScheme
        else {
            val idx = headerAvatar.gradientIndex.coerceAtLeast(0) % gradientPairs.size
            val pair = gradientPairs[idx]
            baseAvatarScheme.copy(
                initialsGradientTop = pair.top,
                initialsGradientBottom = pair.bottom,
            )
        }
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Дебаунс toast'а «Цитата не найдена»: на множественные тапы по reply-блоку (когда
    // оригинал удалён / отредактирован) показываем только первый, пока он не закроется
    // (Toast.LENGTH_SHORT ≈ 2с). Без дебаунса Android очередит N toast'ов один за другим.
    val lastQuoteNotFoundToastAt = remember { mutableStateOf(0L) }
    val showQuoteNotFoundToast: () -> Unit = remember(context) {
        {
            val now = System.currentTimeMillis()
            if (now - lastQuoteNotFoundToastAt.value > 2000L) {
                lastQuoteNotFoundToastAt.value = now
                Toast.makeText(context, "Цитата не найдена", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Блокировка повторных тапов по reply-блоку: пока scroll + highlight (~2100мс)
    // не отработали, последующие тапы игнорируем. Без этого параллельные
    // animateScrollToItem и requestHighlight интерферят, давая визуальные «зависания».
    val replyTapInProgress = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Плотный brand-фон под всем ChatDetail. Без него во время IME-анимации
            // (или любых других resize'ов внутри) случались кадры, где `AndroidView`
            // BackgroundPatternView не успевал перерисоваться под новые границы Box'а
            // и снизу проглядывал MainScaffold (список чатов из overlay'я под нами).
            .background(Color(brand.messageScreenBackground(isDark)))
            // Тап вне MessagePanel и кнопок Header'а скрывает клавиатуру + снимает focus
            // с EditText'а. detectTapGestures внутри awaitFirstDown(requireUnconsumed=true) —
            // если дочерний AndroidView (кнопка / EditText) забрал ACTION_DOWN, наш onTap
            // не сработает. Поэтому тапы по самой панели и её контролам IME не глушат.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { panelRef.value?.dismissKeyboard() })
            },
        // imePadding на Column'е НЕ ставим: он бы сокращал Column на высоту клавиатуры
        // и в моменты, когда Compose-фрейм уже сжался, а системный IME ещё не доехал
        // до своей позиции, снизу проглядывал бы MainScaffold (список чатов под overlay'ем).
        // Вместо этого imePadding применяется ниже точечно к MessagePanelHost — Column
        // остаётся на весь экран, экран MainScaffold нигде не светится.
    ) {
        // КРИТИЧНО: HeaderConfig обёрнут в remember(...) со стабильными keys на изменяемые
        // поля. Без remember каждая рекомпозиция ChatDetailScreen (а их много — bitmapCache
        // version бампается из фонового прогрева, status'ы транзишены и т.п.) создавала бы
        // новый HeaderConfig instance с новой onRightClick-лямбдой. data class.equals() для
        // lambda-полей сравнивает по identity → каждый раз "config changed" → DisposableEffect
        // в HeaderHost пересоздавал кнопки между ACTION_DOWN и ACTION_UP → tap на «Отмена»
        // терялся. Сейчас лямбды захватывают stable viewModel reference и переживают рекомпозиции.
        // Та же гарантия для onBack — он передан сверху и стабилен через каждый кадр.
        val selectedCount = selection?.selectedIds?.size ?: 0
        val customHeaderConfig = remember(selectedCount, viewModel) {
            HeadersView.HeaderConfig.Custom(
                title = "Выбрано $selectedCount",
                titleAlignment = HeadersView.HeaderConfig.Custom.TitleAlignment.LEFT,
                leftButton = HeadersView.HeaderConfig.Custom.LeftButton.HIDE,
                rightButton = HeadersView.HeaderConfig.Custom.RightButton.TEXT,
                rightButtonType = HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
                rightButtonLabel = "Отмена",
                onRightClick = { viewModel.dismissSelection() },
            )
        }
        // Built once per chat — passed to HeaderConfig.Chat.onCallClick. Decides P2PCall vs GroupCall.
        val callContextFromChat: com.example.template.core.model.CallContext? = remember(chat) {
            when (chat.type) {
                ChatType.P2P -> {
                    val participantId = chat.participantIds.firstOrNull() ?: return@remember null
                    val persona = viewModel.personaForUser(participantId) ?: return@remember null
                    val target = com.example.template.core.model.HistoryTarget(
                        id = participantId,
                        name = persona.fullName,
                        avatar = com.example.template.core.model.AvatarSpec(
                            type = com.example.template.core.model.AvatarType.Person,
                            initials = persona.initials,
                            gradientIndex = persona.gradientIndex,
                            imageAsset = persona.avatarAsset,
                        ),
                    )
                    com.example.template.core.model.CallContext.P2PCall(
                        chatId = chat.id,
                        title = persona.fullName,
                        historyTarget = target,
                    )
                }
                ChatType.Group, ChatType.Channel -> {
                    val target = com.example.template.core.model.HistoryTarget(
                        id = chat.id,
                        name = chat.title,
                        avatar = chat.avatar,
                    )
                    com.example.template.core.model.CallContext.GroupCall(
                        chatId = chat.id,
                        title = chat.title,
                        historyTarget = target,
                    )
                }
            }
        }
        val chatHeaderConfig = remember(
            chat.title,
            subtitleText,
            subtitleStyle,
            headerAvatar,
            avatarScheme,
            chat.hasPinnedMessages,
            chat.type,
            onBack,
        ) {
            HeadersView.HeaderConfig.Chat(
                name = chat.title,
                subtitle = subtitleText,
                subtitleStyle = subtitleStyle,
                image = headerAvatar.image,
                avatarType = headerAvatar.type,
                avatarText = headerAvatar.text,
                avatarColorScheme = avatarScheme,
                showPinButton = chat.hasPinnedMessages,
                showBoardButton = true,
                showSearchButton = true,
                showCallButton = chat.type != ChatType.Channel,
                callButtonIconName = if (chat.type == ChatType.Group) "video-call" else "call",
                onBack = onBack,
                onPinClick = { },
                onBoardClick = { },
                onSearchClick = { },
                onCallClick = { callContextFromChat?.let(onStartCall) },
            )
        }
        HeaderHost(config = if (selectionActive) customHeaderConfig else chatHeaderConfig)
        // AudioPanel прямо под хедером — подписка на app-scope `VoicePlaybackController` вынесена
        // в отдельный composable `ChatAudioPanelSlot` ниже. Так body ChatDetailScreen не
        // recompose'ится на тиках playback'а (см. большой комментарий выше про nestable List).
        // tap по play/X в нём бьёт ту же state-машину, что и play-кнопка в баббле.
        // Если playback из ДРУГОГО чата (юзер свернул сюда из иного места и пауза висит) —
        // панель всё равно показываем, потому что requirement: видна везде пока playback != null.
        ChatAudioPanelSlot(viewModel)
        val patternColorScheme = remember(brand, isDark) {
            brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
        }
        // Pattern индексы 1-based (1..patternCount). 0 не существует.
        val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    BackgroundPatternView(ctx).apply { configure(patternAsset, patternColorScheme) }
                },
                update = { v -> v.configure(patternAsset, patternColorScheme) },
            )
            MessageList(
                messages = messages,
                modifier = Modifier.fillMaxSize(),
                personaByUserId = viewModel::personaForUser,
                isP2P = chat.type == ChatType.P2P,
                onBubbleTap = { id ->
                    if (selectionActive) {
                        viewModel.toggleSelection(id)
                    } else {
                        panelRef.value?.dismissKeyboard()
                        viewModel.openContextMenu(id)
                    }
                },
                onBubbleLongPress = { id ->
                    // Long-press на UNSELECTED бабле: входим в selection (или расширяем).
                    // Long-press на ALREADY SELECTED бабле: no-op (пользователь удерживает
                    // внутри баббла чтобы нативно выбрать текст через handles).
                    val curr = selection?.selectedIds.orEmpty()
                    if (id !in curr) {
                        panelRef.value?.dismissKeyboard()
                        if (curr.isEmpty()) {
                            viewModel.startSelection(id)    // вход в multi-select с этим баблом
                        } else {
                            viewModel.toggleSelection(id)   // добавить в существующий multi-select
                        }
                    }
                },
                onQuoteSelected = { id, start, end -> viewModel.startQuoteInPlace(id, start, end) },
                onReactionTap = { id, emoji ->
                    if (selectionActive) viewModel.toggleSelection(id)
                    else viewModel.toggleReactionDirect(id, emoji)
                },
                onAddReactionTap = { id ->
                    if (selectionActive) {
                        viewModel.toggleSelection(id)
                    } else {
                        panelRef.value?.dismissKeyboard()
                        viewModel.openContextMenu(id)
                    }
                },
                onSwipeReply = viewModel::startReply,
                onReplyBlockTap = { replyTo ->
                    // Гейт от повторных тапов пока scroll + highlight не отработали.
                    if (replyTapInProgress.value) return@MessageList
                    val msgs = viewModel.messages.value
                    val originalIdx = msgs.indexOfFirst { it.id == replyTo.originalId }
                    if (originalIdx < 0) {
                        // Edge case: id ушёл совсем (миграция/чистка кэша). На обычном flow
                        // удалённое сообщение остаётся в списке как Message.System(MessageDeleted).
                        showQuoteNotFoundToast()
                        return@MessageList
                    }
                    val original = msgs[originalIdx]
                    val originalText = when (original) {
                        is Message.Text -> original.body
                        is Message.Media -> original.caption.orEmpty()
                        else -> ""
                    }
                    val isDeleted = original is Message.System &&
                        original.kind == com.example.template.core.model.SystemKind.MessageDeleted
                    replyTapInProgress.value = true
                    coroutineScope.launch {
                        try {
                            // reverseLayout=true: индекс 0 — самое новое сообщение (нижнее).
                            // msgs упорядочен oldest→latest, поэтому oldest = msgs.size-1 в reversed.
                            val reversedIdx = msgs.size - 1 - originalIdx
                            // Telegram-like single-motion scroll-to-fragment.
                            //
                            // ЗАЧЕМ запускать requestHighlight ДО animateScrollToItem:
                            // когда баббл цели всплывёт в viewport (mid-flight первой анимации),
                            // его per-bubble LaunchedEffect (MessageList.kt) видит готовый
                            // highlightedQuoteRange и сразу инициирует animateScrollBy к фрагменту.
                            // ScrollableState принимает одну анимацию за раз → второй scroll
                            // прерывает первый и продолжает к точной цели. Пользователь видит
                            // одно непрерывное движение к фрагменту, без видимой паузы между
                            // фазами.
                            //
                            // 3 ветки highlight:
                            //  • Match — fragment overlay + bubble pulse, без toast'а.
                            //  • QuoteMismatch (edit «съел» фрагмент) — pulse, toast «Цитата не найдена».
                            //  • Deleted (оригинал заменён на System placeholder) — pulse системного
                            //    row'а, без toast'а (System-сообщение само объясняет, что произошло).
                            when {
                                isDeleted ->
                                    viewModel.requestHighlight(messageId = replyTo.originalId)
                                matchesQuote(originalText, replyTo) == MatchResult.Match ->
                                    viewModel.requestHighlight(
                                        messageId = replyTo.originalId,
                                        quoteStart = replyTo.quoteStart,
                                        quoteEnd = replyTo.quoteEnd,
                                    )
                                else -> {
                                    viewModel.requestHighlight(messageId = replyTo.originalId)
                                    showQuoteNotFoundToast()
                                }
                            }
                            try {
                                lazyListState.animateScrollToItem(reversedIdx)
                            } catch (_: kotlinx.coroutines.CancellationException) {
                                // Перехвачено вторым скроллом из MessageList — это by design.
                            }
                            // 2100мс — полная длительность highlight-анимации (см.
                            // ChatDetailViewModel.requestHighlight). До её завершения
                            // гейт не отпускаем.
                            kotlinx.coroutines.delay(3500L)
                        } finally {
                            replyTapInProgress.value = false
                        }
                    }
                },
                listState = lazyListState,
                selectionActive = selectionActive,
                selectedIds = selection?.selectedIds ?: emptySet(),
                onToggleSelection = viewModel::toggleSelection,
                highlightedMessageId = highlighted,
                highlightedQuoteRange = highlightedQuoteRange,
                voicePlaybackFlow = viewModel.voicePlayback,
                onVoicePlayPauseClick = viewModel::toggleVoicePlayback,
                onVoiceSeek = viewModel::seekVoicePlayback,
            )
        }
        if (selectionActive) {
            val sel = selection!!
            val anySomeone = remember(sel.selectedIds, messages) {
                val byId = messages.associateBy { it.id }
                sel.selectedIds.any { id -> byId[id]?.isMine == false }
            }
            SelectionActionBar(
                deleteEnabled = !anySomeone,
                onDelete = { viewModel.deleteSelected() },
                onForward = {
                    viewModel.forwardSelectedTbd { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = { viewModel.saveSelected() },
                onPin = {
                    viewModel.pinSelectedTbd { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.imePadding(),
            )
        } else {
            MessagePanelHost(
                onSendText = viewModel::sendText,
                onSendMedia = viewModel::sendMedia,
                onSendVoice = viewModel::sendVoice,
                replyContext = replyCtx?.let { ctx ->
                    ReplyDisplay(
                        authorName = ctx.authorName,
                        previewText = ctx.previewText,
                        isQuote = ctx.quoteStart != null,
                        canQuote = ctx.canQuote,
                    )
                },
                onReplyClose = viewModel::dismissReplyContext,
                onReplyClick = viewModel::openQuotePicker,
                editContext = editDisplay,
                onEditClose = viewModel::dismissEdit,
                onSaveEdit = viewModel::saveEdit,
                modifier = Modifier.imePadding(),
                onPanelReady = { panelRef.value = it },
                onVoiceModeChange = { mode ->
                    isVoiceMode = mode
                    onVoiceModeChange(mode)
                },
            )
        }
    }
    // QuotePicker (Fullscreen) и contextMenu рендерятся отдельно в MainActivity
    // (вне AppScaffold) — overlay'и через ContextMenuOverlay/QuotePickerFullScreen
    // тянут фон до системных баров (status + nav). См. MainActivity.kt и ContextMenuOverlay.kt.
}

/**
 * Изолированная подписка на voice playback: composable scope узкий, его recomposition
 * 30 раз/сек НЕ касается body ChatDetailScreen, MessageList и всех остальных дочерних
 * AndroidView'ов. ChatDetailScreen.composable body выполняется один раз (на стабильных
 * входах), плейбэк-тики живут отдельно. См. большой комментарий в ChatDetailScreen body.
 */
@Composable
private fun ChatAudioPanelSlot(viewModel: ChatDetailViewModel) {
    val playback by viewModel.voicePlayback.collectAsState()
    val current = playback
    // Удерживаем последнее non-null значение, чтобы во время exit-анимации
    // AnimatedVisibility'у было что рендерить (после null StateFlow перестаёт
    // поставлять данные, без удержания content исчезнет до окончания shrink+fade).
    val lastPlayback = remember { mutableStateOf<com.example.template.core.ui.hosts.VoicePlayback?>(null) }
    if (current != null) lastPlayback.value = current
    val displayPlayback = lastPlayback.value
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) +
            fadeIn(tween(150)),
        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) +
            fadeOut(tween(150)),
    ) {
        if (displayPlayback != null) {
            AudioPanelHost(
                playback = displayPlayback,
                repository = viewModel.audioPanelRepository,
                controller = viewModel.audioPanelController,
            )
        }
    }
}

private data class HeaderAvatar(
    val type: AvatarView.AvatarViewType,
    val image: Bitmap?,
    val text: String,
    val gradientIndex: Int,
)

private fun resolveHeaderAvatar(
    chat: Chat,
    viewModel: ChatDetailViewModel,
    bitmapCache: BitmapCache,
): HeaderAvatar {
    if (chat.avatar.type == AvatarType.Self) {
        return HeaderAvatar(AvatarView.AvatarViewType.SAVED, null, "", chat.avatar.gradientIndex)
    }
    if (chat.type == ChatType.P2P) {
        val persona = chat.participantIds.firstOrNull()?.let(viewModel::personaForUser)
        if (persona != null) {
            val image = bitmapCache.get(persona.avatarAsset)
            return if (image != null) {
                HeaderAvatar(AvatarView.AvatarViewType.IMAGE, image, persona.initials, persona.gradientIndex)
            } else {
                HeaderAvatar(AvatarView.AvatarViewType.INITIALS, null, persona.initials, persona.gradientIndex)
            }
        }
    }
    return HeaderAvatar(
        AvatarView.AvatarViewType.INITIALS,
        null,
        chat.avatar.initials.orEmpty(),
        chat.avatar.gradientIndex,
    )
}

// MessagePanelView держит editText приватным, парного showKeyboard() у него нет
// (только dismissKeyboard). Чтобы при «Ответить» поднять IME, ходим по дереву и
// находим первый EditText внутри панели — это и есть основной input (labels-search
// скрыт по умолчанию).
private fun View.findFirstEditText(): EditText? {
    if (this is EditText) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val edit = getChildAt(i).findFirstEditText()
            if (edit != null) return edit
        }
    }
    return null
}
