# Reply to message — design

## Цель

Реализовать в android-template ответ на сообщение через пункт «Ответить» в контекстном меню баббла:

1. Тап «Ответить» → в `MessagePanelView` появляется context-блок Reply (author + preview-текст оригинала).
2. Кнопка «×» внутри блока, отправка, либо выход из чата — сбрасывают reply-context.
3. Отправка с активным reply-context публикует сообщение со **snapshot'ом** (author + текст) оригинала; полученный баббл рендерится с включённым reply-блоком.
4. Ответ работает для всех типов сообщений (Text/Media/Voice/Link/CallMeet) на стороне источника; payload ответа — любой (text, media+caption, media).

Все визуальные элементы (reply-context в панели, reply-блок в бабблах) — уже готовы в `:components` (`MessagePanelView.ContextBlock.Reply`, `BubblesView/MediaBubbleView/VoiceBubbleView/LinkBubbleView.configure(replySender, replyText, ...)`). Из template ничего не рисуем — только wiring.

## Scope

In:
- Замена `Message.replyToId: String?` → `Message.replyTo: ReplyPreview?` (snapshot из id + author + text).
- Обработчик `ContextMenuView.Item.REPLY` в `ChatDetailViewModel`.
- Reply-context state в VM + API `MessagePanelHost`.
- Передача snapshot в `repository.sendTextMessage/sendMediaMessage`.
- Render reply-блока в бабблах через существующий API `configure(replySender, replyText, ...)`.
- Фолбэк «{Автор} удалил(а) сообщение» в баббле, если оригинал отсутствует в `messages.value`.

Out:
- Скролл к оригиналу по тапу на reply-блок (no-op).
- Миниатюры/иконки в reply-context (showThumbnail = false; ждём дореализации компонента под Figma-макет).
- Авто-открытие IME при появлении reply-context.
- Пометка «edited» / редактирование сообщений.
- i18n.

## Data model

`:core:model/Message.kt`:

```kotlin
@Serializable
data class ReplyPreview(
    val originalId: String,
    val authorName: String,
    val text: String,
)

sealed class Message {
    // …
    abstract val replyTo: ReplyPreview?
    // 5 variants: replyToId: String? = null  →  replyTo: ReplyPreview? = null
}
```

Поле `replyToId` сейчас не используется (grep по моделям, репозиторию, UI и JSON-мокам). Замена безопасна, без миграции моков и тестов JSON-парсинга.

## Repository

`:core:data/MessengerRepository.kt`:

```kotlin
suspend fun sendTextMessage(chatId: String, body: String, replyTo: ReplyPreview? = null)
suspend fun sendMediaMessage(
    chatId: String,
    attachments: List<MediaAttachment>,
    caption: String?,
    replyTo: ReplyPreview? = null,
)
```

`MockRepositoryImpl` пробрасывает `replyTo` в `Message.Text` / `Message.Media`. Статусные переходы (DELIVERED → READ) не меняются.

Тесты:
- `core:data` — новые/расширенные тесты, что переданный `replyTo` оказывается в результирующем `Message.replyTo`.
- Существующий `SendMessageTest` (status transitions) не трогаем.

## Preview-резолверы (в `ChatDetailViewModel`)

```kotlin
private fun resolveAuthorName(msg: Message): String =
    if (msg.isMine) "Вы"
    else personaForUser(msg.senderId)?.firstName ?: "Собеседник"

private fun resolveMessagePreview(msg: Message): String = when (msg) {
    is Message.Text     -> msg.body
    is Message.Media    -> msg.caption ?: when (msg.attachments.firstOrNull()?.type) {
        AttachmentType.Photo -> "Фото"
        AttachmentType.Video -> "Видео"
        AttachmentType.File  -> "Файл"
        null                 -> "Медиа"
    }
    is Message.Voice    -> "Голосовое сообщение"
    is Message.Link     -> msg.title.ifBlank { msg.url }
    is Message.CallMeet -> "Звонок"
}
```

Snapshot формируется один раз — в момент тапа «Ответить». Дальнейшие правки/удаления оригинала на snapshot не влияют.

Truncation текста не делаем — `MessagePanelView` и баббл-вью сами обрезают по ширине.

## ViewModel state и flow

`ChatDetailViewModel`:

```kotlin
data class ReplyContext(val originalId: String, val authorName: String, val previewText: String) {
    fun toSnapshot() = ReplyPreview(originalId, authorName, previewText)
}

private val _replyContext = MutableStateFlow<ReplyContext?>(null)
val replyContext: StateFlow<ReplyContext?> = _replyContext.asStateFlow()

fun dismissReplyContext() { _replyContext.value = null }
```

`onMenuItem` — новая ветка REPLY перед `else -> Unit`:

```kotlin
ContextMenuView.Item.REPLY -> {
    val original = messages.value.firstOrNull { it.id == st.messageId }
    if (original != null) {
        _replyContext.value = ReplyContext(
            originalId = original.id,
            authorName = resolveAuthorName(original),
            previewText = resolveMessagePreview(original),
        )
    }
}
```
(После ветки выполняется существующий `dismissContextMenu()`.)

`sendText` / `sendMedia` — собрать snapshot, отправить, обнулить:

```kotlin
fun sendText(text: String) {
    val reply = _replyContext.value?.toSnapshot()
    viewModelScope.launch { repository.sendTextMessage(chatId, text, replyTo = reply) }
    _replyContext.value = null
}

fun sendMedia(attachments: List<MediaAttachment>, caption: String?) {
    val reply = _replyContext.value?.toSnapshot()
    viewModelScope.launch { repository.sendMediaMessage(chatId, attachments, caption, replyTo = reply) }
    _replyContext.value = null
}
```

**Сброс при выходе из чата** — автоматически: `ChatDetailViewModel` создаётся через `remember(openChatId)` на root-уровне `MainActivity`; закрытие ChatDetail обнуляет `openChatId` → VM выкидывается вместе с `_replyContext`.

**Корнер** — оригинал пропал между показом меню и тапом REPLY: `firstOrNull` → null → ветка no-op → меню закрывается обычным `dismissContextMenu()`.

## UI wiring

### MessagePanelHost (`:core:ui`)

Новые параметры:

```kotlin
data class ReplyDisplay(val authorName: String, val previewText: String)

fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (attachments: List<MediaAttachment>, caption: String?) -> Unit = { _, _ -> },
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    labels: List<String> = DEFAULT_LABELS,
    onPanelReady: ((MessagePanelView) -> Unit)? = null,
)
```

В `remember(...)` для `MessagePanelConfig` добавить `replyContext` в ключ; внутри:

```kotlin
contextBlock = when (replyContext) {
    null -> MessagePanelView.ContextBlock.None
    else -> MessagePanelView.ContextBlock.Reply(
        author = replyContext.authorName,
        preview = replyContext.previewText,
        showThumbnail = false,
    )
}
```

В `update` lambda — подписка на крестик:

```kotlin
panel.onContextClose = { onReplyClose() }
```

### ChatDetailScreen (`:feature:chatdetail`)

```kotlin
val replyCtx by viewModel.replyContext.collectAsState()
MessagePanelHost(
    onSendText  = viewModel::sendText,
    onSendMedia = viewModel::sendMedia,
    replyContext = replyCtx?.let { ReplyDisplay(it.authorName, it.previewText) },
    onReplyClose = viewModel::dismissReplyContext,
    // …existing params
)
```

### MessageList (`:core:ui`)

Над LazyColumn'ом:

```kotlin
val messagesById = remember(messages) { messages.associateBy { it.id } }
```

В configure каждого бабла (Bubbles / Media / Voice / Link — у всех одна сигнатура `replySender: String? / replyText: String?`):

```kotlin
val rt = msg.replyTo
val replySender = rt?.authorName
val replyText = when {
    rt == null -> null
    messagesById.containsKey(rt.originalId) -> rt.text
    else -> "удалил(а) сообщение"
}
bubbleView.configure(
    /* … existing args … */
    replySender = replySender,
    replyText = replyText,
)
```

`CallMeetView` reply-параметров не имеет — для CallMeet-ответа в баббле reply-блок не показываем (на этапе implementation сверить сигнатуру; если параметры всё же есть — подключить).

Тап по reply-блоку в баббле — компонент сам не кликабельный, ничего не подключаем (no-op).

## Файлы под изменение

- `core/model/src/main/java/com/example/template/core/model/Message.kt` — `ReplyPreview` + замена `replyToId` на `replyTo` в 5 вариантах.
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` — `replyTo` параметр в обоих send-методах.
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` — проброс `replyTo` в построенный Message.
- `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt` или новый файл — тесты проброса snapshot'а.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` — `ReplyContext` state, `dismissReplyContext`, REPLY-кейс в `onMenuItem`, snapshot в `sendText`/`sendMedia`, резолверы.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt` — `ReplyDisplay` + параметры `replyContext`/`onReplyClose`, маппинг в `ContextBlock.Reply`, `panel.onContextClose`.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — `messagesById` lookup, проброс `replySender`/`replyText` в 4 bubble-вида.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — `collectAsState` + параметры в MessagePanelHost.

## Не входит в эту фичу

- Тап-скролл к оригиналу.
- Подсветка/выделение оригинала после скролла.
- Миниатюра-thumbnail в reply-context панели.
- Иконки и rich-preview по типу сообщения (📷 / 🎤 / 🔗) — ждём дореализации компонента.
- Pre-show IME при появлении reply-context.

## Известные гочи / связь с уже существующим

- ContextMenuView пункт `REPLY("Ответить", ...)` уже определён в `:components`, обработчик в `ChatDetailViewModel.onMenuItem` сейчас попадает в `else -> Unit` — после правок появится отдельная ветка.
- `Message.replyToId` присутствует в модели с момента её создания (включён в дизайн ещё на 2026-05-13), но фактически нигде не используется. Замена на `replyTo` — единственное место в коде, где это поле «оживает».
- Reactions wiring (`ReactionsView` под бабблом) на reply никак не завязан; параллельные изменения по reactions [[project-context-menu-integration]] не пересекаются.
