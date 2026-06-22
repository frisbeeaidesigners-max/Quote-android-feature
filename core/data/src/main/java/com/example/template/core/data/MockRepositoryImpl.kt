package com.example.template.core.data

import android.content.Context
import com.example.template.core.model.Call
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.AttachmentType
import com.example.template.core.model.CurrentUser
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.MessagePreview
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Persona
import com.example.template.core.model.PreviewKind
import com.example.template.core.model.Reaction
import com.example.template.core.model.ReplyPreview
import com.example.template.core.model.Space
import com.example.template.core.model.SystemKind
import com.example.template.core.model.User
import com.example.template.core.model.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MockRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MessengerRepository {

    data class HistoryTargetMock(
        val id: String,
        val name: String,
        val avatar: com.example.template.core.model.AvatarSpec,
    )

    private val _currentUser = MutableStateFlow(loadCurrentUser())
    override val currentUser: StateFlow<CurrentUser> = _currentUser.asStateFlow()

    private val _users = MutableStateFlow(loadList<User>("mock/users.json"))
    override val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _personas = MutableStateFlow(loadList<Persona>("mock/personas.json"))
    override val personas: StateFlow<List<Persona>> = _personas.asStateFlow()

    private val _spaces = MutableStateFlow(loadList<Space>("mock/spaces.json"))
    override val spaces: StateFlow<List<Space>> = _spaces.asStateFlow()

    private val _currentSpaceId = MutableStateFlow(_spaces.value.firstOrNull()?.id.orEmpty())
    override val currentSpaceId: StateFlow<String> = _currentSpaceId.asStateFlow()

    private val _chats = MutableStateFlow(loadList<Chat>("mock/chats.json"))

    override val p2pChats: StateFlow<List<Chat>> =
        _chats.map { sortChats(filterP2P(it)) }
            .stateIn(scope, SharingStarted.Eagerly, sortChats(filterP2P(_chats.value)))

    override val allSpaceChats: StateFlow<List<Chat>> =
        _chats.map { list -> sortChats(list.filter { it.type != ChatType.P2P }) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                sortChats(_chats.value.filter { it.type != ChatType.P2P }),
            )

    override fun spaceChats(spaceId: String): StateFlow<List<Chat>> =
        _chats.map { sortChats(filterSpace(it, spaceId)) }
            .stateIn(scope, SharingStarted.Eagerly, sortChats(filterSpace(_chats.value, spaceId)))

    private val _calls = MutableStateFlow(loadList<Call>("mock/calls.json"))
    override val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val messageCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    override fun setCurrentSpace(id: String) {
        _currentSpaceId.value = id
    }

    override fun getUser(id: String): User? = _users.value.firstOrNull { it.id == id }
    override fun getPersona(id: String): Persona? = _personas.value.firstOrNull { it.id == id }
    override fun personaForUser(userId: String): Persona? {
        val user = getUser(userId) ?: return null
        val pid = user.personaId ?: return null
        return getPersona(pid)
    }
    override fun getChat(id: String): Chat? = _chats.value.firstOrNull { it.id == id }

    override suspend fun loadMessages(chatId: String): StateFlow<List<Message>> {
        messageCache[chatId]?.let { return it }
        // Чтение assets + Json.decode — на Dispatchers.IO, чтобы не блокировать UI-тред
        // (viewModelScope по умолчанию = Main.immediate). Гонка двух параллельных
        // загрузок для одного chatId возможна, но getOrPut гарантирует один итоговый
        // StateFlow — лишняя работа дешевле блокировки UI на парсинг.
        val initial = withContext(Dispatchers.IO) {
            try {
                loadList<Message>("mock/messages/$chatId.json")
            } catch (_: java.io.FileNotFoundException) {
                emptyList()
            }
        }
        return messageCache.getOrPut(chatId) {
            MutableStateFlow(appendIncomingPreviewIfNeeded(chatId, initial))
        }
    }

    /**
     * Если chat-list preview сейчас показывает входящее текстовое сообщение
     * (`kind=Text`, `ownStatus=NONE` — т.е. от собеседника, не от нас), а в истории чата
     * этого сообщения ещё нет (по timestamp), синтезируем для него Message.Text-бабл
     * от первого участника. Это даёт пустым чатам хотя бы одну реплику, согласованную
     * с тем, что пользователь видит в чат-листе.
     */
    private fun appendIncomingPreviewIfNeeded(chatId: String, messages: List<Message>): List<Message> {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return messages
        val preview = chat.lastMessage
        if (preview.kind != PreviewKind.Text) return messages
        if (preview.ownStatus != MessageStatus.NONE) return messages
        if (messages.any { it.timestamp == preview.timestamp }) return messages
        val senderId = chat.participantIds.firstOrNull() ?: return messages
        val synth = Message.Text(
            id = "preview-${chat.id}",
            chatId = chat.id,
            senderId = senderId,
            timestamp = preview.timestamp,
            isMine = false,
            status = MessageStatus.NONE,
            body = preview.text,
        )
        return messages + synth
    }

    override suspend fun sendTextMessage(chatId: String, body: String, replyTo: ReplyPreview?) {
        if (body.isBlank()) return
        val cache = messageCache[chatId] ?: return
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val mine = _currentUser.value
        val newMsg = buildSentTextMessage(
            chatId = chatId,
            senderId = mine.id,
            body = body,
            replyTo = replyTo,
            timestamp = System.currentTimeMillis(),
        )
        cache.value = cache.value + newMsg
        updateChatLastMessage(chatId, newMsg)
        // READ имитируем только если есть «адресат online»:
        //   • P2P  → собеседник в чате должен быть UserStatus.Online;
        //   • Group/Channel → всегда (для имитации множества читающих).
        // Иначе сообщение остаётся в DELIVERED как в реальном клиенте, пока кто-то
        // не прочитает.
        val isP2P = chat.type == ChatType.P2P
        val recipientOnline = if (isP2P) {
            val recipientId = chat.participantIds.firstOrNull()
            recipientId != null &&
                _users.value.firstOrNull { it.id == recipientId }?.status is UserStatus.Online
        } else {
            true
        }
        if (!recipientOnline) return
        scope.launch {
            for (i in 0 until StatusTransitions.intervalsMs.size) {
                delay(StatusTransitions.intervalsMs[i])
                val nextStatus = StatusTransitions.sequence[i + 1]
                cache.updateText(newMsg.id) { it.copy(status = nextStatus) }
                val updated = cache.value.firstOrNull { it.id == newMsg.id } as? Message.Text
                if (updated != null) updateChatLastMessage(chatId, updated)
            }
        }
    }

    override suspend fun sendVoiceMessage(
        chatId: String,
        durationMs: Long,
        replyTo: ReplyPreview?,
    ) {
        if (durationMs <= 0L) return
        val cache = messageCache[chatId] ?: return
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val mine = _currentUser.value
        val newMsg = buildSentVoiceMessage(
            chatId = chatId,
            senderId = mine.id,
            durationMs = durationMs,
            replyTo = replyTo,
            timestamp = System.currentTimeMillis(),
        )
        cache.value = cache.value + newMsg
        updateChatLastMessage(chatId, newMsg)
        // READ-симуляция как у Text/Media: только если адресат «онлайн».
        val isP2P = chat.type == ChatType.P2P
        val recipientOnline = if (isP2P) {
            val recipientId = chat.participantIds.firstOrNull()
            recipientId != null &&
                _users.value.firstOrNull { it.id == recipientId }?.status is UserStatus.Online
        } else {
            true
        }
        if (!recipientOnline) return
        scope.launch {
            for (i in 0 until StatusTransitions.intervalsMs.size) {
                delay(StatusTransitions.intervalsMs[i])
                val nextStatus = StatusTransitions.sequence[i + 1]
                cache.updateVoice(newMsg.id) { it.copy(status = nextStatus) }
                val updated = cache.value.firstOrNull { it.id == newMsg.id } as? Message.Voice
                if (updated != null) updateChatLastMessage(chatId, updated)
            }
        }
    }

    override suspend fun recordOutgoingCall(
        counterpartId: String,
        counterpartName: String,
        counterpartAvatar: com.example.template.core.model.AvatarSpec,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val call = buildOutgoingCallRecord(
            target = HistoryTargetMock(counterpartId, counterpartName, counterpartAvatar),
            durationMs = durationMs,
            isVideo = isVideo,
            isGroupCall = isGroupCall,
            timestamp = now,
            id = "call-$now",
        )
        _calls.value = listOf(call) + _calls.value
    }

    override suspend fun appendCallMeetMessage(
        chatId: String,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
    ) {
        val cache = messageCache[chatId] ?: return
        if (_chats.value.none { it.id == chatId }) return
        val now = System.currentTimeMillis()
        val msg = buildCallMeetMessage(
            chatId = chatId,
            senderId = _currentUser.value.id,
            durationMs = durationMs,
            isVideo = isVideo,
            isGroupCall = isGroupCall,
            timestamp = now,
            id = "m-callmeet-$now",
        ) as Message.CallMeet
        cache.value = cache.value + msg
        updateChatLastMessage(chatId, msg)
    }

    override suspend fun sendMediaMessage(
        chatId: String,
        attachments: List<MediaAttachment>,
        caption: String?,
        replyTo: ReplyPreview?,
    ) {
        if (attachments.isEmpty()) return
        val cache = messageCache[chatId] ?: return
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val mine = _currentUser.value
        val newMsg = buildSentMediaMessage(
            chatId = chatId,
            senderId = mine.id,
            attachments = attachments,
            caption = caption,
            replyTo = replyTo,
            timestamp = System.currentTimeMillis(),
        )
        cache.value = cache.value + newMsg
        updateChatLastMessage(chatId, newMsg)
        // READ имитируем только если есть «адресат online»:
        //   • P2P  → собеседник в чате должен быть UserStatus.Online;
        //   • Group/Channel → всегда (для имитации множества читающих).
        // Иначе сообщение остаётся в DELIVERED как в реальном клиенте, пока кто-то
        // не прочитает.
        val isP2P = chat.type == ChatType.P2P
        val recipientOnline = if (isP2P) {
            val recipientId = chat.participantIds.firstOrNull()
            recipientId != null &&
                _users.value.firstOrNull { it.id == recipientId }?.status is UserStatus.Online
        } else {
            true
        }
        if (!recipientOnline) return
        scope.launch {
            for (i in 0 until StatusTransitions.intervalsMs.size) {
                delay(StatusTransitions.intervalsMs[i])
                val nextStatus = StatusTransitions.sequence[i + 1]
                cache.updateMedia(newMsg.id) { it.copy(status = nextStatus) }
                val updated = cache.value.firstOrNull { it.id == newMsg.id } as? Message.Media
                if (updated != null) updateChatLastMessage(chatId, updated)
            }
        }
    }

    override suspend fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val cache = messageCache[chatId] ?: return
        val current = cache.value.firstOrNull { it.id == messageId } ?: return
        val updated = updateReactions(current, toggleReactionList(current.reactions, emoji))
        cache.value = cache.value.map { if (it.id == messageId) updated else it }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String) {
        val cache = messageCache[chatId] ?: return
        val current = cache.value
        val original = current.firstOrNull { it.id == messageId } ?: return
        // Заменяем сообщение на System-placeholder, не удаляем из списка — так все
        // reply-блоки в других баблах продолжают находить originalId, тап по reply
        // скроллит на сам placeholder, а reply-блоки помечаются «Удалил(а) сообщение».
        val placeholder = Message.System(
            id = original.id,
            chatId = original.chatId,
            senderId = original.senderId,
            timestamp = original.timestamp,
            isMine = original.isMine,
            kind = SystemKind.MessageDeleted,
        )
        val newList = current.map { if (it.id == messageId) placeholder else it }
        cache.value = newList
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(lastMessage = previewFromLast(newList)) else chat
        }
    }

    override suspend fun editTextMessage(
        chatId: String,
        messageId: String,
        newBody: String,
    ) {
        val cache = messageCache[chatId] ?: return
        val current = cache.value
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val original = current[idx] as? Message.Text ?: return
        if (!original.isMine) return
        val edited = buildEditedTextMessage(original, newBody)
        val updated = current.toMutableList().apply {
            this[idx] = edited
        }
        cache.value = updated
        // Если редактировали последнее сообщение чата — обновляем preview в chat list.
        // Иначе превью указывает на другое (более позднее) сообщение, его не трогаем.
        if (idx == updated.lastIndex) updateChatLastMessage(chatId, edited)
    }

    override suspend fun editMediaMessage(
        chatId: String,
        messageId: String,
        newAttachments: List<MediaAttachment>,
        newCaption: String?,
    ) {
        val cache = messageCache[chatId] ?: return
        val current = cache.value
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val original = current[idx] as? Message.Media ?: return
        if (!original.isMine) return
        // Media без attachments но с непустым caption — превращается в Text (визуально
        // MediaBubbleView с пустым items даёт растянутый-на-ширину баббл, что некрасиво).
        val replacement: Message = if (newAttachments.isEmpty() && !newCaption.isNullOrBlank()) {
            buildMediaToTextConversion(original, newCaption)
        } else {
            buildEditedMediaMessage(original, newAttachments, newCaption)
        }
        val updated = current.toMutableList().apply {
            this[idx] = replacement
        }
        cache.value = updated
        // Если редактировали последнее сообщение чата — обновляем preview в chat list.
        // Replacement может быть Text (при Media→Text конверсии) либо Media — диспатчим по типу.
        if (idx == updated.lastIndex) {
            when (replacement) {
                is Message.Text -> updateChatLastMessage(chatId, replacement)
                is Message.Media -> updateChatLastMessage(chatId, replacement)
                else -> Unit
            }
        }
    }

    override suspend fun forwardMessagesToSaved(messages: List<Message>) {
        val saved = _chats.value.firstOrNull {
            it.avatar.type == com.example.template.core.model.AvatarType.Self
        } ?: return
        // Прогреваем cache Self-чата так же, как loadMessages (чтобы append дальше попал
        // в существующий StateFlow, и его подписчики получили апдейт).
        val cache = messageCache.getOrPut(saved.id) {
            val initial = try {
                loadList<Message>("mock/messages/${saved.id}.json")
            } catch (_: java.io.FileNotFoundException) {
                emptyList()
            }
            MutableStateFlow(appendIncomingPreviewIfNeeded(saved.id, initial))
        }
        val mine = _currentUser.value
        val newMessages = buildForwardedBatch(
            messages = messages.sortedBy { it.timestamp },
            targetChatId = saved.id,
            senderId = mine.id,
            baseTimestamp = System.currentTimeMillis(),
        )
        if (newMessages.isEmpty()) return
        cache.value = cache.value + newMessages
        // Обновляем preview Self-чата по последнему добавленному сообщению.
        when (val last = newMessages.last()) {
            is Message.Text -> updateChatLastMessage(saved.id, last)
            is Message.Media -> updateChatLastMessage(saved.id, last)
            else -> Unit  // batch собран только из Text/Media по построению.
        }
    }

    private fun updateReactions(msg: Message, reactions: List<Reaction>): Message = when (msg) {
        is Message.Text -> msg.copy(reactions = reactions)
        is Message.Media -> msg.copy(reactions = reactions)
        is Message.Voice -> msg.copy(reactions = reactions)
        is Message.Link -> msg.copy(reactions = reactions)
        is Message.CallMeet -> msg.copy(reactions = reactions)
        is Message.System -> msg.copy(reactions = reactions)
    }

    private fun MutableStateFlow<List<Message>>.updateText(id: String, block: (Message.Text) -> Message.Text) {
        value = value.map { if (it.id == id && it is Message.Text) block(it) else it }
    }

    private fun MutableStateFlow<List<Message>>.updateMedia(id: String, block: (Message.Media) -> Message.Media) {
        value = value.map { if (it.id == id && it is Message.Media) block(it) else it }
    }

    private fun MutableStateFlow<List<Message>>.updateVoice(id: String, block: (Message.Voice) -> Message.Voice) {
        value = value.map { if (it.id == id && it is Message.Voice) block(it) else it }
    }

    private fun updateChatLastMessage(chatId: String, msg: Message.Text) {
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(
                lastMessage = MessagePreview(
                    text = msg.body,
                    kind = PreviewKind.Text,
                    timestamp = msg.timestamp,
                    ownStatus = msg.status,
                )
            ) else chat
        }
    }

    private fun updateChatLastMessage(chatId: String, msg: Message.Media) {
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(
                lastMessage = MessagePreview(
                    text = mediaPreviewText(msg),
                    kind = PreviewKind.Text,
                    timestamp = msg.timestamp,
                    ownStatus = msg.status,
                )
            ) else chat
        }
    }

    // ChatList превью для Voice — «Голосовое сообщение» (совпадает с previewFromLast и
    // ChatDetailViewModel.resolveMessagePreview для reply-author). Прокидываем timestamp+status
    // чтобы ChatList сортировался по свежести и показывал own-status галочки.
    private fun updateChatLastMessage(chatId: String, msg: Message.Voice) {
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(
                lastMessage = MessagePreview(
                    text = "Голосовое сообщение",
                    kind = PreviewKind.Text,
                    timestamp = msg.timestamp,
                    ownStatus = msg.status,
                )
            ) else chat
        }
    }

    private fun updateChatLastMessage(chatId: String, msg: Message.CallMeet) {
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(
                lastMessage = MessagePreview(
                    text = callMeetPreviewText(msg),
                    kind = PreviewKind.Text,
                    timestamp = msg.timestamp,
                    ownStatus = MessageStatus.NONE,
                )
            ) else chat
        }
    }

    private fun loadCurrentUser(): CurrentUser =
        AppJson.decodeFromString(readAsset("mock/current-user.json"))

    private inline fun <reified T> loadList(asset: String): List<T> =
        AppJson.decodeFromString(readAsset(asset))

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        // В companion — чтобы unit-тесты могли проверять toggle-семантику без поднятия Mock-репо.
        fun toggleReactionList(reactions: List<Reaction>, emoji: String): List<Reaction> {
            val existing = reactions.firstOrNull { it.emoji == emoji }
            return when {
                existing == null -> reactions + Reaction(emoji = emoji, count = 1, isMine = true)
                existing.isMine && existing.count <= 1 -> reactions.filterNot { it.emoji == emoji }
                existing.isMine -> reactions.map {
                    if (it.emoji == emoji) it.copy(count = it.count - 1, isMine = false) else it
                }
                else -> reactions.map {
                    if (it.emoji == emoji) it.copy(count = it.count + 1, isMine = true) else it
                }
            }
        }

        fun filterP2P(chats: List<Chat>): List<Chat> =
            chats.filter { it.type == ChatType.P2P }

        fun filterSpace(chats: List<Chat>, spaceId: String): List<Chat> =
            chats.filter { it.spaceId == spaceId && it.type != ChatType.P2P }

        // Pinned всегда сверху; в каждой группе — по свежести lastMessage.
        // Применяется ко всем чат-лентам репозитория, чтобы после отправки сообщения
        // чат поднимался вверх (внутри своей группы pinned/non-pinned).
        fun sortChats(chats: List<Chat>): List<Chat> =
            chats.sortedWith(
                compareByDescending<Chat> { it.pinned }
                    .thenByDescending { it.lastMessage.timestamp },
            )

        fun buildSentTextMessage(
            chatId: String,
            senderId: String,
            body: String,
            replyTo: ReplyPreview?,
            timestamp: Long,
        ): Message.Text = Message.Text(
            id = "m-$timestamp",
            chatId = chatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.DELIVERED,
            replyTo = replyTo,
            body = body,
        )

        fun buildSentMediaMessage(
            chatId: String,
            senderId: String,
            attachments: List<MediaAttachment>,
            caption: String?,
            replyTo: ReplyPreview?,
            timestamp: Long,
        ): Message.Media = Message.Media(
            id = "m-$timestamp",
            chatId = chatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.DELIVERED,
            replyTo = replyTo,
            attachments = attachments,
            caption = caption?.takeIf { it.isNotBlank() },
        )

        fun buildSentVoiceMessage(
            chatId: String,
            senderId: String,
            durationMs: Long,
            replyTo: ReplyPreview?,
            timestamp: Long,
        ): Message.Voice = Message.Voice(
            id = "m-$timestamp",
            chatId = chatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.DELIVERED,
            replyTo = replyTo,
            durationMs = durationMs,
            waveform = synthesizeWaveform(durationMs, seed = timestamp),
        )

        /**
         * Синтетический waveform для голосового — реального аудио мы не записываем.
         * ~20 баров/сек (1 бар на ≈50мс), clamp [60, 200]. Нижняя граница 60 — чтобы
         * на коротких записях (<3с) waveform всё равно заполнял типичную ширину
         * waveform-area: WaveformView расставляет бары с шагом 4dp (2dp bar + 2dp gap),
         * 60 баров ≈ 240dp, что покрывает waveform-area даже на широких бабблах.
         * Верхняя 200 — чтобы не раздуть JSON при «забыл выключить». Значения
         * 10..90 — относительные, WaveformView сам нормализует к max.
         * Seed по timestamp → один и тот же id всегда даёт ту же форму (детерминизм
         * для рекомпозиций и потенциального snapshot-теста).
         */
        fun synthesizeWaveform(durationMs: Long, seed: Long): List<Int> {
            val barCount = (durationMs / 50L).toInt().coerceIn(60, 200)
            val rnd = kotlin.random.Random(seed)
            return List(barCount) { 10 + rnd.nextInt(81) }
        }

        fun buildEditedTextMessage(
            original: Message.Text,
            newBody: String,
        ): Message.Text = original.copy(
            body = newBody,
            isEdited = true,
        )

        fun buildEditedMediaMessage(
            original: Message.Media,
            newAttachments: List<MediaAttachment>,
            newCaption: String?,
        ): Message.Media = original.copy(
            attachments = newAttachments,
            caption = newCaption,
            isEdited = true,
        )

        fun buildMediaToTextConversion(
            original: Message.Media,
            newBody: String,
        ): Message.Text = Message.Text(
            id = original.id,
            chatId = original.chatId,
            senderId = original.senderId,
            timestamp = original.timestamp,
            isMine = original.isMine,
            status = original.status,
            reactions = original.reactions,
            replyTo = original.replyTo,
            body = newBody,
            formatting = emptyList(),
            isEdited = true,
        )

        fun previewFromLast(messages: List<Message>): MessagePreview {
            val last = messages.lastOrNull()
                ?: return MessagePreview(
                    text = "",
                    kind = PreviewKind.Text,
                    timestamp = 0L,
                    ownStatus = MessageStatus.NONE,
                )
            return when (last) {
                is Message.Text -> MessagePreview(
                    text = last.body,
                    kind = PreviewKind.Text,
                    timestamp = last.timestamp,
                    ownStatus = last.status,
                )
                is Message.Media -> MessagePreview(
                    text = mediaPreviewText(last),
                    kind = PreviewKind.Text,
                    timestamp = last.timestamp,
                    ownStatus = last.status,
                )
                is Message.Voice -> MessagePreview(
                    text = "Голосовое сообщение",
                    kind = PreviewKind.Text,
                    timestamp = last.timestamp,
                    ownStatus = last.status,
                )
                is Message.CallMeet -> MessagePreview(
                    text = callMeetPreviewText(last),
                    kind = PreviewKind.Text,
                    timestamp = last.timestamp,
                    ownStatus = MessageStatus.NONE,
                )
                // Link/System: ChatList пока без preview таких сообщений (прототип).
                // Перечислены явно (не `else ->`), чтобы новый Message-подтип вынужденно
                // требовал решения, что показывать в превью, а не падал в эту ветку.
                is Message.Link, is Message.System -> MessagePreview(
                    text = "",
                    kind = PreviewKind.Text,
                    timestamp = last.timestamp,
                    ownStatus = MessageStatus.NONE,
                )
            }
        }

        // PreviewKind.Text — других вариантов модель не имеет; для медиа без подписи
        // показываем «Изображение»/«Видео» (chat-14 «Сохранённые» использует ту же конвенцию).
        fun mediaPreviewText(msg: Message.Media): String =
            msg.caption?.takeIf { it.isNotBlank() } ?: when {
                msg.attachments.any { it.type == AttachmentType.Video } -> "Видео"
                else -> "Изображение"
            }

        /** Превью CallMeet'а в чат-листе. Аналогично Calls-вкладке: направление по `isMine`,
         *  Missed/NoAnswer (= трубку никто не поднял до конца) идёт особым лейблом. */
        fun callMeetPreviewText(msg: Message.CallMeet): String = when {
            msg.callStatus == com.example.template.core.model.CallStatus.Missed ||
                msg.callStatus == com.example.template.core.model.CallStatus.NoAnswer -> "Пропущенный звонок"
            msg.isMine -> "Исходящий звонок"
            else -> "Входящий звонок"
        }

        fun buildForwardedTextMessage(
            original: Message.Text,
            targetChatId: String,
            senderId: String,
            timestamp: Long,
        ): Message.Text = Message.Text(
            id = "fwd-$timestamp",
            chatId = targetChatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.READ,
            body = original.body,
        )

        fun buildForwardedMediaMessage(
            original: Message.Media,
            targetChatId: String,
            senderId: String,
            timestamp: Long,
        ): Message.Media = Message.Media(
            id = "fwd-$timestamp",
            chatId = targetChatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.READ,
            attachments = original.attachments,
            caption = original.caption,
        )

        /**
         * Pure builder: переслать список сообщений в targetChatId. Voice / Link / CallMeet /
         * System пропускаются — для прототипа в Saved-чат улетают только Text/Media. id строятся
         * монотонно от `baseTimestamp` (+i по индексу принимаемого batch'а) — защита от коллизий
         * при быстром loop'е, когда System.currentTimeMillis() даёт одинаковое значение.
         */
        fun buildForwardedBatch(
            messages: List<Message>,
            targetChatId: String,
            senderId: String,
            baseTimestamp: Long,
        ): List<Message> {
            val out = mutableListOf<Message>()
            var nextTs = baseTimestamp
            for (m in messages) {
                when (m) {
                    is Message.Text -> {
                        out += buildForwardedTextMessage(m, targetChatId, senderId, nextTs)
                        nextTs++
                    }
                    is Message.Media -> {
                        out += buildForwardedMediaMessage(m, targetChatId, senderId, nextTs)
                        nextTs++
                    }
                    is Message.Voice, is Message.Link, is Message.CallMeet, is Message.System -> Unit
                }
            }
            return out
        }

        fun buildOutgoingCallRecord(
            target: HistoryTargetMock,
            durationMs: Long,
            isVideo: Boolean,
            isGroupCall: Boolean,
            timestamp: Long,
            id: String,
        ): com.example.template.core.model.Call = com.example.template.core.model.Call(
            id = id,
            // Положили трубку до connect'а (durationMs == 0) → Calls-вкладка показывает
            // как Missed, иначе обычный Outgoing.
            type = if (durationMs > 0L) com.example.template.core.model.CallType.Outgoing
                   else com.example.template.core.model.CallType.Missed,
            counterpartId = target.id,
            counterpartName = target.name,
            counterpartAvatar = target.avatar,
            isVideo = isVideo,
            isGroupCall = isGroupCall,
            timestamp = timestamp,
            durationMs = durationMs,
        )

        fun buildCallMeetMessage(
            chatId: String,
            senderId: String,
            durationMs: Long,
            isVideo: Boolean,
            isGroupCall: Boolean,
            timestamp: Long,
            id: String,
        ): com.example.template.core.model.Message = com.example.template.core.model.Message.CallMeet(
            id = id,
            chatId = chatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = com.example.template.core.model.MessageStatus.DELIVERED,
            // Положили трубку до connect'а → Missed-баббл (бабл-subtitle через
            // TimeFormatter.formatCallDuration отрабатывает пустую строку при 0).
            callStatus = if (durationMs > 0L) com.example.template.core.model.CallStatus.Answered
                         else com.example.template.core.model.CallStatus.Missed,
            isVideo = isVideo,
            isGroupCall = isGroupCall,
            durationMs = durationMs,
        )
    }
}

internal object StatusTransitions {
    // SENDING-промежуток отключён: новое сообщение сразу публикуется как DELIVERED,
    // потом одной задержкой переходит в READ. Если когда-нибудь снова потребуется
    // «часики» — вернуть SENDING в начало sequence и добавить ещё один интервал.
    val sequence = listOf(MessageStatus.DELIVERED, MessageStatus.READ)
    val intervalsMs = listOf(800L)
}
