# Reply to message — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать в android-template ответ на сообщение через пункт «Ответить» контекстного меню: при тапе в `MessagePanelView` появляется блок Reply (автор + превью оригинала); отправка с активным Reply пишет snapshot в новое сообщение; в баббле новое сообщение рендерится с включённым reply-блоком.

**Architecture:** Snapshot-подход — `Message.replyTo: ReplyPreview?(originalId, authorName, text)` хранится в самом сообщении. Snapshot формируется в `ChatDetailViewModel` один раз в момент тапа «Ответить», пробрасывается в репозиторий при send, кладётся в созданное сообщение. `MessageList` пробрасывает `replyTo.authorName/text` в `bubble.configure(replySender, replyText, …)`; если оригинал отсутствует в `messages` — `replyText` подменяется на «удалил(а) сообщение».

**Tech Stack:** Kotlin 1.9.22 · Compose · AndroidView-обёртки над `:components` (`MessagePanelView`, `BubblesView`, `MediaBubbleView`, `VoiceBubbleView`, `LinkBubbleView`) · JUnit · kotlinx-serialization

---

## Spec и контекст

- Spec: `docs/superpowers/specs/2026-05-19-reply-to-message-design.md` — читать первым.
- Проектные правила: `CLAUDE.md` в корне репо. Особое внимание:
  - после правок Kotlin/Compose → **`./gradlew :app:installDebug`** (не `assembleDebug`).
  - **Не дублируем компоненты** — Reply-API в `:components` уже готов.
  - Имена ассетов канонические, бренд-цвета только через `DSBrand.<x>ColorScheme(isDark)`.
- Готовое в `:components` (не трогаем):
  - `MessagePanelView.ContextBlock.Reply(author, preview, showThumbnail)` (`components/src/main/java/com/example/components/messagepanel/MessagePanelView.kt:97-103`).
  - `MessagePanelView.onContextClose: (() -> Unit)?` (`...:220`) — публичное поле, ставится снаружи.
  - `BubblesView.configure(... replySender, replyText, ...)` (`bubbles/BubblesView.kt:292-293`).
  - `MediaBubbleView.configure(... replySender, replyText, ...)` (`bubbles/MediaBubbleView.kt:376-377`).
  - `VoiceBubbleView.configure(... replySender, replyText, ...)` (`bubbles/VoiceBubbleView.kt:468-469`).
  - `LinkBubbleView.configure(... replySender, replyText, ...)` (`bubbles/LinkBubbleView.kt:443-444`).
  - `CallMeetView.configure(...)` (`callmeet/CallMeetView.kt:120-134`) — `replySender`/`replyText` **нет**; для CallMeet reply в баббле не рендерим.

## Карта изменяемых файлов

- `core/model/src/main/java/com/example/template/core/model/Message.kt` — добавить `ReplyPreview`; в 5 вариантах `Message` заменить `replyToId: String?` на `replyTo: ReplyPreview?`.
- `app/src/main/assets/mock/messages/chat-05.json` — `"replyToId": null` → `"replyTo": null` (2 места).
- `core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt` — то же самое в JSON-литералах (2 места).
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` — добавить `replyTo: ReplyPreview? = null` в `sendTextMessage` и `sendMediaMessage`.
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` — добавить pure-fun билдеры `buildSentTextMessage` / `buildSentMediaMessage` в companion; `sendTextMessage`/`sendMediaMessage` вызывают билдеры с `replyTo`.
- `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt` — добавить тесты, что билдеры пробрасывают `replyTo` в созданное сообщение.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` — `ReplyContext`, state, `dismissReplyContext`, ветка REPLY в `onMenuItem`, snapshot в send-методах, резолверы.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt` — `ReplyDisplay`, новые параметры `replyContext`/`onReplyClose`, маппинг в `ContextBlock.Reply`, подписка `panel.onContextClose`.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — `collectAsState(replyContext)` + проброс в `MessagePanelHost`.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — `messagesById` lookup, проброс `replySender`/`replyText` в configure для Bubbles/Media/Voice/Link.

---

### Task 1: Data model — `ReplyPreview` + замена `replyToId` на `replyTo`

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Message.kt`
- Modify: `app/src/main/assets/mock/messages/chat-05.json`
- Modify: `core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt`

- [ ] **Step 1: Добавить `ReplyPreview` и заменить `replyToId` на `replyTo` во всех вариантах Message.**

Открыть `core/model/src/main/java/com/example/template/core/model/Message.kt`. После строки с `data class TextSpan(...)` (около строки 37) добавить:

```kotlin
@Serializable
data class ReplyPreview(
    val originalId: String,
    val authorName: String,
    val text: String,
)
```

В sealed-классе `Message` (строка 40+) заменить:
```kotlin
abstract val replyToId: String?
```
на:
```kotlin
abstract val replyTo: ReplyPreview?
```

В каждом из 5 вариантов (`Text`, `Media`, `Voice`, `Link`, `CallMeet`) заменить:
```kotlin
override val replyToId: String? = null,
```
на:
```kotlin
override val replyTo: ReplyPreview? = null,
```

- [ ] **Step 2: Обновить мок-данные.**

Открыть `app/src/main/assets/mock/messages/chat-05.json`. Заменить **обе** строки:
- `"replyToId": null,` → `"replyTo": null,`

Открыть `core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt`. Заменить **обе** строки:
- `"replyToId":null,` → `"replyTo":null,`

- [ ] **Step 3: Запустить тесты, убедиться что компилируется.**

Run:
```
./gradlew :core:model:compileDebugKotlin :core:data:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, все существующие тесты в `:core:data` зелёные (`JsonParsingTest`, `ReactionsTest`, `SendMessageTest`, `DeleteMessageTest`, `RepositoryFilterTest`).

- [ ] **Step 4: Commit.**

```bash
git add core/model/src/main/java/com/example/template/core/model/Message.kt \
  app/src/main/assets/mock/messages/chat-05.json \
  core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt
git commit -m "feat(model): add ReplyPreview snapshot, replace replyToId with replyTo on Message"
```

---

### Task 2: Repository — `replyTo` параметр + pure-fun билдеры (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt`

- [ ] **Step 1: Написать падающий тест на билдер `buildSentTextMessage`.**

Открыть `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt`. Перед закрывающей `}` класса добавить:

```kotlin
    @Test
    fun `buildSentTextMessage carries replyTo snapshot`() {
        val reply = com.example.template.core.model.ReplyPreview(
            originalId = "m-source",
            authorName = "Алиса",
            text = "оригинал",
        )
        val msg = MockRepositoryImpl.buildSentTextMessage(
            chatId = "c-1",
            senderId = "u-me",
            body = "ответ",
            replyTo = reply,
            timestamp = 1L,
        )
        assertEquals(reply, msg.replyTo)
        assertEquals("ответ", msg.body)
        assertEquals(true, msg.isMine)
    }

    @Test
    fun `buildSentTextMessage without replyTo is null`() {
        val msg = MockRepositoryImpl.buildSentTextMessage(
            chatId = "c-1",
            senderId = "u-me",
            body = "ответ",
            replyTo = null,
            timestamp = 1L,
        )
        assertEquals(null, msg.replyTo)
    }

    @Test
    fun `buildSentMediaMessage carries replyTo snapshot`() {
        val reply = com.example.template.core.model.ReplyPreview(
            originalId = "m-source",
            authorName = "Боб",
            text = "видео",
        )
        val att = com.example.template.core.model.MediaAttachment(
            placeholderColor = "#000",
            type = com.example.template.core.model.AttachmentType.Photo,
        )
        val msg = MockRepositoryImpl.buildSentMediaMessage(
            chatId = "c-1",
            senderId = "u-me",
            attachments = listOf(att),
            caption = "пдпсь",
            replyTo = reply,
            timestamp = 2L,
        )
        assertEquals(reply, msg.replyTo)
        assertEquals("пдпсь", msg.caption)
        assertEquals(1, msg.attachments.size)
    }
```

- [ ] **Step 2: Запустить тесты — должны упасть на ссылке на `buildSentTextMessage` / `buildSentMediaMessage`.**

Run:
```
./gradlew :core:data:testDebugUnitTest
```
Expected: FAIL, unresolved reference на `buildSentTextMessage`.

- [ ] **Step 3: Добавить `replyTo` параметр в интерфейс репозитория.**

Открыть `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`. В импорты добавить:
```kotlin
import com.example.template.core.model.ReplyPreview
```

Сигнатуры `sendTextMessage` и `sendMediaMessage` заменить:

```kotlin
    suspend fun sendTextMessage(chatId: String, body: String, replyTo: ReplyPreview? = null)
    suspend fun sendMediaMessage(
        chatId: String,
        attachments: List<MediaAttachment>,
        caption: String?,
        replyTo: ReplyPreview? = null,
    )
```

- [ ] **Step 4: Добавить pure-fun билдеры в `MockRepositoryImpl.companion` + обновить send-методы.**

Открыть `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`. В импорты добавить:
```kotlin
import com.example.template.core.model.ReplyPreview
```

В блок `companion object` (около строки 274) **до** функции `previewFromLast` вставить два билдера:

```kotlin
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
```

Заменить тело `override suspend fun sendTextMessage` (около строки 124) на:

```kotlin
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
```

Заменить тело `override suspend fun sendMediaMessage` на:

```kotlin
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
```

- [ ] **Step 5: Запустить тесты — теперь должны пройти.**

Run:
```
./gradlew :core:data:testDebugUnitTest
```
Expected: PASS, все 3 новых теста + старые зелёные.

- [ ] **Step 6: Commit.**

```bash
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt \
  core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt \
  core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt
git commit -m "feat(data): replyTo param in sendText/sendMedia + pure-fun message builders"
```

---

### Task 3: ViewModel — `ReplyContext` state, REPLY handler, snapshot в send

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Добавить импорты и `ReplyContext`.**

Открыть `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`. В импорты добавить:
```kotlin
import com.example.template.core.model.AttachmentType
import com.example.template.core.model.ReplyPreview
```

В конец файла **внутри** класса `ChatDetailViewModel`, перед закрывающей `}`, добавить:

```kotlin
    data class ReplyContext(val originalId: String, val authorName: String, val previewText: String) {
        fun toSnapshot() = ReplyPreview(originalId, authorName, previewText)
    }

    private val _replyContext = MutableStateFlow<ReplyContext?>(null)
    val replyContext: StateFlow<ReplyContext?> = _replyContext.asStateFlow()

    fun dismissReplyContext() {
        _replyContext.value = null
    }

    private fun resolveAuthorName(msg: Message): String =
        if (msg.isMine) "Вы"
        else repository.personaForUser(msg.senderId)?.firstName ?: "Собеседник"

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
    }
```

- [ ] **Step 2: Добавить ветку REPLY в `onMenuItem`.**

В `onMenuItem` (около строки 82), перед `else -> Unit` добавить:

```kotlin
            ContextMenuView.Item.REPLY -> {
                val original = _messages.value.firstOrNull { it.id == st.messageId }
                if (original != null) {
                    _replyContext.value = ReplyContext(
                        originalId = original.id,
                        authorName = resolveAuthorName(original),
                        previewText = resolveMessagePreview(original),
                    )
                }
            }
```

- [ ] **Step 3: Send-методы — snapshot + reset.**

Заменить тело `sendText` (около строки 103) на:

```kotlin
    fun sendText(body: String) {
        if (body.isBlank()) return
        val reply = _replyContext.value?.toSnapshot()
        viewModelScope.launch { repository.sendTextMessage(chatId, body, replyTo = reply) }
        _replyContext.value = null
    }
```

Заменить тело `sendMedia` (около строки 108) на:

```kotlin
    fun sendMedia(attachments: List<MediaAttachment>, caption: String?) {
        if (attachments.isEmpty()) return
        val reply = _replyContext.value?.toSnapshot()
        viewModelScope.launch { repository.sendMediaMessage(chatId, attachments, caption, replyTo = reply) }
        _replyContext.value = null
    }
```

- [ ] **Step 4: Скомпилировать модуль.**

Run:
```
./gradlew :feature:chatdetail:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(chatdetail): ReplyContext state + REPLY handler + snapshot in send"
```

---

### Task 4: `MessagePanelHost` — Reply API

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt`

- [ ] **Step 1: Добавить `ReplyDisplay`, новые параметры, маппинг.**

Открыть `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt`. В импорты добавить:
```kotlin
import com.example.components.messagepanel.MessagePanelView.ContextBlock
```

После `import com.example.template.core.model.AttachmentType as DomainAttachmentType` (около строки 30), перед `@Composable` добавить:

```kotlin
data class ReplyDisplay(val authorName: String, val previewText: String)
```

В сигнатуре `MessagePanelHost` (строка 33) добавить два параметра между `onSendMedia` и `modifier`:

```kotlin
@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (attachments: List<MediaAttachment>, caption: String?) -> Unit = { _, _ -> },
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    labels: List<String> = DEFAULT_LABELS,
    onPanelReady: ((MessagePanelView) -> Unit)? = null,
) {
```

В блок `remember(brand, isDark, attachments, labels, stickerImages, foxtrotManStickers, gifDrawables)` (около строки 73) добавить `replyContext` в ключи и `contextBlock` в конструктор `MessagePanelConfig`:

```kotlin
    val config = remember(brand, isDark, attachments, labels, stickerImages, foxtrotManStickers, gifDrawables, replyContext) {
        MessagePanelConfig(
            attachments = attachments,
            labels = labels,
            attachedMediaColorScheme = brand.attachedMediaColorScheme(isDark),
            segmentedControlColorScheme = brand.segmentedControlColorScheme(isDark),
            brandAccentColor = brand.accentColor(isDark),
            stickerImages = stickerImages,
            additionalStickerPacks = listOf("FoxtrotMan" to foxtrotManStickers),
            gifDrawables = gifDrawables,
            stickerAddButtonColorScheme = brand.buttonColorScheme(ButtonType.SECONDARY, isDark),
            contextBlock = when (replyContext) {
                null -> ContextBlock.None
                else -> ContextBlock.Reply(
                    author = replyContext.authorName,
                    preview = replyContext.previewText,
                    showThumbnail = false,
                )
            },
        )
    }
```

В `update` lambda (строка 99) **в самом начале**, до `panel.configure(...)`, добавить подписку на крестик:

```kotlin
        update = { panel ->
            panel.onContextClose = { onReplyClose() }
            panel.configure(config, colorScheme)
            // …остальное без изменений
```

(`panel.onContextClose` — публичное `var`-поле в `MessagePanelView:220`, поэтому ставить можно прямо в `update`; компонент сам не сбрасывает его между configure'ами.)

- [ ] **Step 2: Скомпилировать модуль.**

Run:
```
./gradlew :core:ui:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt
git commit -m "feat(messagepanelhost): expose replyContext + onReplyClose"
```

---

### Task 5: `ChatDetailScreen` — связать VM ↔ Host

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Подписаться на `viewModel.replyContext` и пробросить.**

Открыть `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`. В импорты добавить:
```kotlin
import com.example.template.core.ui.hosts.ReplyDisplay
```

После строки `val messages by viewModel.messages.collectAsState()` (около строки 44) добавить:

```kotlin
    val replyCtx by viewModel.replyContext.collectAsState()
```

Заменить вызов `MessagePanelHost` (около строки 175) на:

```kotlin
        MessagePanelHost(
            onSendText = viewModel::sendText,
            onSendMedia = viewModel::sendMedia,
            replyContext = replyCtx?.let { ReplyDisplay(it.authorName, it.previewText) },
            onReplyClose = viewModel::dismissReplyContext,
            modifier = Modifier.imePadding(),
            onPanelReady = { panelRef.value = it },
        )
```

- [ ] **Step 2: Скомпилировать модуль.**

Run:
```
./gradlew :feature:chatdetail:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(chatdetail): wire replyContext through MessagePanelHost"
```

---

### Task 6: `MessageList` — reply-блок в бабблах + фолбэк удалённого

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: Добавить `messagesById` lookup и helper.**

Открыть `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`. После строки `val reversed = remember(messages) { messages.asReversed() }` (около строки 242) добавить:

```kotlin
    // ID-индекс для проверки «оригинал ещё жив?» в reply-фолбэке. Пересчитывается
    // когда меняется messages — это дешёвая операция (associateBy один проход).
    val messagesById = remember(messages) { messages.associateBy { it.id } }
```

Перед `items(reversed, key = { it.id }) { msg ->` (около строки 274) ничего добавлять не нужно — `messagesById` уже в scope.

- [ ] **Step 2: Вынести helper для текста реплая.**

В тот же файл, **перед** `@Composable fun MessageList(...)` (около строки 202), добавить:

```kotlin
/**
 * Текст для replyText-поля баббла:
 *   • если есть snapshot и оригинал ещё в чате → snapshot.text;
 *   • если snapshot есть, но оригинал удалён → «удалил(а) сообщение» (имя автора
 *     уйдёт в replySender, грамматика читается: «{Имя}\nудалил(а) сообщение»);
 *   • если snapshot'а нет → null (компонент скрывает reply-блок).
 */
private fun resolveReplyText(msg: Message, byId: Map<String, Message>): String? {
    val rt = msg.replyTo ?: return null
    return if (byId.containsKey(rt.originalId)) rt.text else "удалил(а) сообщение"
}
```

- [ ] **Step 3: Пробросить `replySender` / `replyText` в configure четырёх баблов.**

В блоке `when (msg) { ... }` внутри `items(...)`:

**3a. `is Message.Text` (около строки 311), ветка `update = { view -> view.configure(...) }`:** перед закрывающей `)` `configure` добавить два параметра:
```kotlin
                                replySender = msg.replyTo?.authorName,
                                replyText = resolveReplyText(msg, messagesById),
```

**3b. `is Message.Media` (около строки 337) — то же самое в её `configure`:**
```kotlin
                                replySender = msg.replyTo?.authorName,
                                replyText = resolveReplyText(msg, messagesById),
```

**3c. `is Message.Voice` (около строки 382) — то же:**
```kotlin
                                replySender = msg.replyTo?.authorName,
                                replyText = resolveReplyText(msg, messagesById),
```

**3d. `is Message.Link` (около строки 410) — то же:**
```kotlin
                                replySender = msg.replyTo?.authorName,
                                replyText = resolveReplyText(msg, messagesById),
```

**`is Message.CallMeet` — НЕ ТРОГАЕМ:** `CallMeetView.configure` не имеет `replySender`/`replyText` параметров.

- [ ] **Step 4: Скомпилировать модуль.**

Run:
```
./gradlew :core:ui:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(messagelist): render reply block in bubbles + deleted-original fallback"
```

---

### Task 7: Финальная сборка, установка на устройство, smoke-проверка

**Files:** —

- [ ] **Step 1: Полный билд проекта (без install).**

Run:
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. Если упало — починить compile errors **до** install.

- [ ] **Step 2: Установка на подключённое устройство.**

Per [[feedback_install_after_changes]] (CLAUDE.md): после правок Kotlin/Compose делаем `:app:installDebug`.

Run:
```
./gradlew :app:installDebug
```
Expected: `Installed on 1 device`. Если упало на `mergeDexRelease` с locked `classes.dex` — `rm -rf app/build/intermediates/dex/release` и повторить (см. CLAUDE.md «Windows gotcha»).

- [ ] **Step 3: Smoke-проверка вручную.**

Открыть приложение, зайти в любой чат с сообщениями (например chat-05 — там готовая история).

Чек-лист:
1. Long-press / тап по входящему текстовому сообщению (SOMEONE) → меню → «Ответить» → ожидается: панель показывает блок с автором (имя из persona) + первой строкой текста оригинала.
2. Тап «×» в блоке Reply → блок исчезает.
3. Снова «Ответить» → ввести текст → отправить → ожидается: новый MY-баббл, **сверху него** маленький reply-блок с именем + текстом оригинала. Reply-context в панели сбросился.
4. «Ответить» на медиа без подписи → preview в панели = «Изображение»/«Видео»/«Фото».
5. «Ответить» на голосовое → preview = «Голосовое сообщение». (Voice-сообщения в chat-05 есть; если нет — нагляднее проверить в другом чате или временно подменить тип в моке.)
6. Отправить ответ с **media+caption**: открыть скрепку, прикрепить демо-вложения, текст-caption, send → Media-баббл с reply-блоком сверху.
7. **Удаление оригинала:** отправить MY-text → ответить на него (своё сообщение) → удалить оригинал через «Удалить» в context-меню → ответ должен остаться в чате, в его reply-блоке текст превратится в «удалил(а) сообщение», имя «Вы» сохранится.
8. Выйти из ChatDetail (back) с активным reply-context → зайти обратно → reply-context сброшен (VM пересоздалась).

Если хотя бы один пункт не работает — НЕ закрывать таск, починить и записать в коммит-чейн.

- [ ] **Step 4 (если всё ок):** ничего не коммитить — это manual gate, артефактов нет.

---

## Self-review

**1. Spec coverage:**
- Data model (`ReplyPreview` + `replyTo` на Message) → Task 1.
- Repository signatures + builder + tests → Task 2.
- VM state + REPLY handler + snapshot в send + резолверы → Task 3.
- `MessagePanelHost` API (replyContext + onReplyClose + ContextBlock.Reply + onContextClose hook) → Task 4.
- `ChatDetailScreen` wiring → Task 5.
- `MessageList` reply в бабблах + фолбэк удалённого → Task 6.
- Smoke-чек (все 8 пунктов покрывают: тап-меню-ответить, ×, send text/media, voice/media превью, ответ на удалённое, выход-вход) → Task 7.

**2. Placeholders:** нет «TBD»/«implement later»/«similar to» — все код-блоки конкретные.

**3. Type consistency:**
- `ReplyPreview(originalId, authorName, text)` — везде те же три поля.
- `ReplyDisplay(authorName, previewText)` (UI-layer) vs `ReplyContext(originalId, authorName, previewText)` (VM-layer) vs `ReplyPreview(originalId, authorName, text)` (модель). Имя поля `previewText` (UI/VM) и `text` (модель) — разные намеренно: в модели это «то что показать», в VM/UI — «превью оригинала, который мы ответом». Snapshot-converter `toSnapshot()` маппит `previewText → text`. Все mappings явные, ни одной ссылки на несуществующий идентификатор.
- `MessagePanelView.ContextBlock.Reply(author, preview, showThumbnail)` (`:components`) — мапинг сделан в Task 4: `author = replyContext.authorName`, `preview = replyContext.previewText`.
- bubble configure: `replySender` (String?) и `replyText` (String?) — те же имена, что в `BubblesView`/`MediaBubbleView`/`VoiceBubbleView`/`LinkBubbleView`.

**4. Spec gaps:** ни одного. Все требования из `2026-05-19-reply-to-message-design.md` имеют конкретный таск.
