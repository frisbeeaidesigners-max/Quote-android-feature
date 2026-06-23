package com.example.template.feature.chatdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.components.contextmenu.ContextMenuView
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AttachmentType
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Persona
import com.example.template.core.model.ReplyPreview
import com.example.template.core.model.User
import com.example.template.core.ui.hosts.VoicePlayback
import com.example.template.core.ui.hosts.VoicePlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatDetailViewModel(
    private val chatId: String,
    private val repository: MessengerRepository,
    private val voiceController: VoicePlaybackController,
) : ViewModel() {
    // Прокидываем в ChatDetailScreen для AudioPanelHost — он живёт прямо под хедером и
    // нуждается в repository (для резолва sender/group названия) и controller (для callback'ов).
    val audioPanelRepository: MessengerRepository get() = repository
    val audioPanelController: VoicePlaybackController get() = voiceController
    val chat: Chat? = repository.getChat(chatId)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // _selection объявлено ЗДЕСЬ, выше init, потому что StateFlow.collect внутри init
    // эмитит первое значение синхронно — раньше, чем property-инициализаторы ниже init
    // успевают выполниться. Раньше это давало NPE на _selection.value в prune-логике.
    // Сам `data class SelectionContext` и публичный API (startSelection и т.д.) остаются
    // ниже — там нужны только типы, а они резолвятся независимо от порядка объявления.
    private val _selection = MutableStateFlow<SelectionContext?>(null)
    val selection: StateFlow<SelectionContext?> = _selection.asStateFlow()

    // Active voice-плеер живёт в AppContainer (см. VoicePlaybackController) — переживает
    // закрытие чата и переключение между чат-листом / пространствами. VM лишь проксирует
    // публичный flow и делегирует actions.
    val voicePlayback: StateFlow<VoicePlayback?> = voiceController.voicePlayback

    init {
        viewModelScope.launch {
            repository.loadMessages(chatId).collect { list ->
                _messages.value = list
                // Если в selection остались id, которых уже нет в messages (например, после
                // delete извне), выкидываем их. Пустое множество = выход из режима выбора.
                _selection.value?.let { sel ->
                    val aliveIds = list.mapTo(HashSet(list.size)) { it.id }
                    val pruned = sel.selectedIds.intersect(aliveIds)
                    _selection.value = if (pruned.isEmpty()) null
                                       else if (pruned.size == sel.selectedIds.size) sel
                                       else sel.copy(selectedIds = pruned)
                }
                // Если играем баббл, который больше не Voice (удалён → System placeholder),
                // глушим. Контроллер app-scope, но мы знаем активный список этого чата.
                val activeVoiceIds = list.asSequence()
                    .filterIsInstance<Message.Voice>()
                    .mapTo(HashSet()) { it.id }
                val curPlayback = voiceController.voicePlayback.value
                if (curPlayback != null && curPlayback.chatId == chatId) {
                    voiceController.pruneIfMessageGone(activeVoiceIds)
                }
            }
        }
    }

    fun toggleVoicePlayback(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } as? Message.Voice ?: return
        voiceController.toggle(
            messageId = msg.id,
            chatId = chatId,
            senderId = msg.senderId,
            durationMs = msg.durationMs,
        )
    }

    /**
     * Пользователь сдвинул playhead на waveform'е. Компонент стреляет на ACTION_UP с позицией
     * 0..1, сам позицию не двигает (`WaveformView.onSeekListener` зовётся изнутри
     * `VoiceBubbleView`). Делегируем в контроллер — он сохранит play/pause-state и
     * рестартит тикер с новой позиции.
     */
    fun seekVoicePlayback(messageId: String, position: Float) {
        voiceController.seek(messageId, position)
    }

    private val _contextMenu = MutableStateFlow<ContextMenuState?>(null)
    val contextMenu: StateFlow<ContextMenuState?> = _contextMenu.asStateFlow()

    fun openContextMenu(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        // ContextMenuView знает только TEXT/POLL/MEDIA — Voice мапим на TEXT (мин. набор
        // пунктов). EDIT и COPY для Voice графически появятся, но при тапе no-op'нут:
        // `startEdit` гейтит по типу (else → return), `copyTextFor(Voice) = ""` → COPY
        // guard в onMenuItem не пускает пустой текст в clipboard. Link/CallMeet/System
        // оставлены без меню — рабочих фич нет.
        val kind = when (msg) {
            is Message.Text -> ContextMenuView.MessageKind.TEXT
            is Message.Media -> ContextMenuView.MessageKind.MEDIA
            is Message.Voice -> ContextMenuView.MessageKind.TEXT
            is Message.Link, is Message.CallMeet, is Message.System -> return
        }
        val c = chat ?: return
        val mode = when (c.type) {
            ChatType.P2P -> ContextMenuView.Mode.P2P
            ChatType.Group -> ContextMenuView.Mode.GROUP
            ChatType.Channel -> ContextMenuView.Mode.CHANNEL
        }
        _contextMenu.value = ContextMenuState(
            messageId = msg.id,
            type = if (msg.isMine) ContextMenuView.Type.MY else ContextMenuView.Type.SOMEONE,
            mode = mode,
            messageKind = kind,
            isPinned = false,
            isSending = msg.status == MessageStatus.SENDING,
            canReport = false,
            copyText = copyTextFor(msg),
        )
    }

    fun dismissContextMenu() {
        _contextMenu.value = null
    }

    fun toggleReaction(emoji: String) {
        val st = _contextMenu.value ?: return
        viewModelScope.launch { repository.toggleReaction(chatId, st.messageId, emoji) }
        dismissContextMenu()
    }

    /** Прямой toggle минуя overlay — для тапа по уже выбранной реакции под бабблом. */
    fun toggleReactionDirect(messageId: String, emoji: String) {
        viewModelScope.launch { repository.toggleReaction(chatId, messageId, emoji) }
    }

    fun onMenuItem(item: ContextMenuView.Item, context: Context) {
        val st = _contextMenu.value ?: return
        when (item) {
            ContextMenuView.Item.COPY -> if (st.copyText.isNotEmpty()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("message", st.copyText))
            }
            ContextMenuView.Item.DELETE -> if (st.type == ContextMenuView.Type.MY) {
                viewModelScope.launch { repository.deleteMessage(chatId, st.messageId) }
            }
            ContextMenuView.Item.REPLY -> startReply(st.messageId)
            ContextMenuView.Item.EDIT -> startEdit(st.messageId)
            ContextMenuView.Item.SELECT -> startSelection(st.messageId)
            else -> Unit
        }
        dismissContextMenu()
    }

    fun startReply(messageId: String) {
        val original = _messages.value.firstOrNull { it.id == messageId } ?: return
        val fullText = resolveMessagePreview(original)
        _editContext.value = null      // взаимоисключение reply ↔ edit
        _selection.value = null        // взаимоисключение reply ↔ selection
        _replyContext.value = ReplyContext(
            originalId = original.id,
            authorName = resolveAuthorName(original),
            originalFullText = fullText,
            previewText = fullText,
            quoteStart = null,
            quoteEnd = null,
            canQuote = when (original) {
                is Message.Text -> true
                is Message.Media -> !original.caption.isNullOrBlank()
                is Message.Voice, is Message.Link, is Message.CallMeet, is Message.System -> false
            },
        )
    }

    private fun copyTextFor(msg: Message): String = when (msg) {
        is Message.Text -> msg.body
        is Message.Media -> msg.caption.orEmpty()
        is Message.Voice, is Message.Link, is Message.CallMeet, is Message.System -> ""
    }

    fun sendText(body: String) {
        if (body.isBlank()) return
        val reply = _replyContext.value?.toSnapshot()
        viewModelScope.launch { repository.sendTextMessage(chatId, body, replyTo = reply) }
        _replyContext.value = null
        _quotePickerVisible.value = false
    }

    fun sendMedia(attachments: List<MediaAttachment>, caption: String?) {
        if (attachments.isEmpty()) return
        val reply = _replyContext.value?.toSnapshot()
        viewModelScope.launch { repository.sendMediaMessage(chatId, attachments, caption, replyTo = reply) }
        _replyContext.value = null
        _quotePickerVisible.value = false
    }

    fun sendVoice(durationMs: Long) {
        if (durationMs <= 0L) return
        val reply = _replyContext.value?.toSnapshot()
        viewModelScope.launch { repository.sendVoiceMessage(chatId, durationMs, replyTo = reply) }
        _replyContext.value = null
        _quotePickerVisible.value = false
    }

    fun userById(id: String): User? = repository.getUser(id)
    fun personaForUser(userId: String): Persona? = repository.personaForUser(userId)

    data class ReplyContext(
        val originalId: String,
        val authorName: String,
        val originalFullText: String,
        val previewText: String,
        val quoteStart: Int? = null,
        val quoteEnd: Int? = null,
        val canQuote: Boolean = false,
    ) {
        fun toSnapshot() = ReplyPreview(
            originalId = originalId,
            authorName = authorName,
            text = previewText,
            quoteStart = quoteStart,
            quoteEnd = quoteEnd,
        )
    }

    private val _replyContext = MutableStateFlow<ReplyContext?>(null)
    val replyContext: StateFlow<ReplyContext?> = _replyContext.asStateFlow()

    private val _quotePickerVisible = MutableStateFlow(false)
    val quotePickerVisible: StateFlow<Boolean> = _quotePickerVisible.asStateFlow()

    // Зеркало текста в EditText MessagePanel'а. Используется V5 quote-picker'ом для
    // показа draft'а как `message`-поля в mock-LinkBubble на вкладке «Ссылка».
    // ChatDetailScreen вешает TextWatcher на findFirstEditText() и пушит сюда; пока
    // picker не открыт, эта подписка просто работает в холостую.
    private val _panelDraftText = MutableStateFlow("")
    val panelDraftText: StateFlow<String> = _panelDraftText.asStateFlow()

    fun setPanelDraftText(text: String) {
        _panelDraftText.value = text
    }

    data class EditContext(
        val originalId: String,
        val kind: Kind,
        val originalBody: String,
        val originalAttachments: List<MediaAttachment>,
    ) {
        enum class Kind { TEXT, MEDIA }
    }

    private val _editContext = MutableStateFlow<EditContext?>(null)
    val editContext: StateFlow<EditContext?> = _editContext.asStateFlow()

    fun dismissReplyContext() {
        _replyContext.value = null
        _quotePickerVisible.value = false
    }

    fun openQuotePicker() {
        if (_replyContext.value == null) return
        _quotePickerVisible.value = true
    }

    fun dismissQuotePicker() {
        _quotePickerVisible.value = false
    }

    fun setQuote(start: Int, end: Int) {
        val curr = _replyContext.value ?: return
        if (start < 0 || end > curr.originalFullText.length || start >= end) return
        val fragment = curr.originalFullText.substring(start, end)
        _replyContext.value = curr.copy(
            previewText = fragment,
            quoteStart = start,
            quoteEnd = end,
        )
    }

    fun clearQuote() {
        val curr = _replyContext.value ?: return
        _replyContext.value = curr.copy(
            previewText = curr.originalFullText,
            quoteStart = null,
            quoteEnd = null,
        )
    }

    fun startQuoteInPlace(messageId: String, start: Int, end: Int) {
        startReply(messageId)
        setQuote(start, end)
    }

    fun startEdit(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (!msg.isMine) return
        val (kind, body, attachments) = when (msg) {
            is Message.Text -> Triple(EditContext.Kind.TEXT, msg.body, emptyList<MediaAttachment>())
            is Message.Media -> Triple(EditContext.Kind.MEDIA, msg.caption.orEmpty(), msg.attachments)
            else -> return
        }
        _replyContext.value = null   // взаимоисключение reply ↔ edit
        _selection.value = null      // взаимоисключение selection ↔ edit
        _editContext.value = EditContext(msg.id, kind, body, attachments)
    }

    fun dismissEdit() {
        _editContext.value = null
    }

    fun saveEdit(text: String, attachments: List<MediaAttachment>) {
        val ctx = _editContext.value ?: return
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() && attachments.isEmpty()) return
        viewModelScope.launch {
            when (ctx.kind) {
                EditContext.Kind.TEXT -> repository.editTextMessage(chatId, ctx.originalId, trimmedText)
                EditContext.Kind.MEDIA -> repository.editMediaMessage(
                    chatId,
                    ctx.originalId,
                    attachments,
                    trimmedText.takeIf { it.isNotEmpty() },
                )
            }
            _editContext.value = null
        }
    }

    /**
     * Mutual-exclusive с reply/edit третий контекст. `null` = режим выбора выключен.
     * `selectedIds` всегда непустое: `toggleSelection` auto-exit'ит в `null` когда множество
     * опустевает после снятия чекбокса. Начальный selection всегда содержит инициирующее
     * сообщение из меню (см. `startSelection`).
     */
    data class SelectionContext(val selectedIds: Set<String>)

    // _selection / selection объявлены выше init по причине, описанной у тех declaration'ов.

    fun startSelection(initialMessageId: String) {
        _replyContext.value = null
        _editContext.value = null
        _selection.value = SelectionContext(setOf(initialMessageId))
    }

    fun toggleSelection(messageId: String) {
        val curr = _selection.value ?: return
        val newSet =
            if (messageId in curr.selectedIds) curr.selectedIds - messageId
            else curr.selectedIds + messageId
        _selection.value = if (newSet.isEmpty()) null else curr.copy(selectedIds = newSet)
    }

    fun dismissSelection() {
        _selection.value = null
    }

    fun deleteSelected() {
        val ids = _selection.value?.selectedIds ?: return
        val msgs = _messages.value.filter { it.id in ids }
        if (msgs.any { !it.isMine }) return  // safety: UI also disables button
        viewModelScope.launch {
            ids.forEach { repository.deleteMessage(chatId, it) }
            _selection.value = null
        }
    }

    fun saveSelected() {
        val ids = _selection.value?.selectedIds ?: return
        val msgs = _messages.value
            .filter { it.id in ids }
            .sortedBy { it.timestamp }
        viewModelScope.launch {
            repository.forwardMessagesToSaved(msgs)
            _selection.value = null
        }
    }

    fun forwardSelectedTbd(showToast: (String) -> Unit) {
        showToast("Переслать — TBD")
        _selection.value = null
    }

    fun pinSelectedTbd(showToast: (String) -> Unit) {
        showToast("Закрепить — TBD")
        _selection.value = null
    }

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    private val _highlightedQuoteRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val highlightedQuoteRange: StateFlow<Pair<Int, Int>?> = _highlightedQuoteRange.asStateFlow()

    /**
     * Подсветка оригинала по тапу на reply-block. Двухстадийная с перекрытием:
     *  - 0..1000ms: bubble pulse (basic10, см. MessageList).
     *  - 400..3400ms: подсветка quote-фрагмента (если quoteStart/End переданы) —
     *    scale 2.0→1.0 + alpha 0→1→0 с corner-radius 2dp, +2dp expansion по краям.
     * VM держит state в течение всей анимации (~3400ms), MessageList оркестрирует фазы.
     * Caller (ChatDetailScreen) дожидается окончания scroll до бабла перед вызовом.
     */
    fun requestHighlight(messageId: String, quoteStart: Int? = null, quoteEnd: Int? = null) {
        _highlightedMessageId.value = messageId
        _highlightedQuoteRange.value = if (quoteStart != null && quoteEnd != null) quoteStart to quoteEnd else null
        viewModelScope.launch {
            // 400 (frag delay) + 600 (in) + 1800 (hold) + 600 (out) = 3400ms + 100 buffer.
            kotlinx.coroutines.delay(3500L)
            if (_highlightedMessageId.value == messageId) {
                _highlightedMessageId.value = null
                _highlightedQuoteRange.value = null
            }
        }
    }

    private fun resolveAuthorName(msg: Message): String =
        if (msg.isMine) "Вы"
        else personaForUser(msg.senderId)?.fullName ?: "Собеседник"

    private fun resolveMessagePreview(msg: Message): String = when (msg) {
        is Message.Text -> msg.body
        is Message.Media -> msg.caption ?: when (msg.attachments.firstOrNull()?.type) {
            AttachmentType.Photo -> "Фото"
            AttachmentType.Video -> "Видео"
            AttachmentType.File -> "Файл"
            null -> "Медиа"
        }
        is Message.Voice -> "Голосовое сообщение"
        is Message.Link -> msg.title.ifBlank { msg.url }
        is Message.CallMeet -> "Звонок"
        is Message.System -> ""
    }
}
