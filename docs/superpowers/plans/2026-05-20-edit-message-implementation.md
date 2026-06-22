# Edit Message Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать редактирование своих Text/Media-сообщений через пункт «Редактировать» контекстного меню, с автоматической пометкой `isEdited` и индикатором в баббле от `:components`.

**Architecture:** Модель получает `isEdited: Boolean` флаг (Text + Media). Репозиторий получает `editTextMessage` и `editMediaMessage` методы с pure-builders для TDD. ViewModel хранит `EditContext` (по аналогии с `ReplyContext`), routing'ит `Item.EDIT` из контекстного меню. `MessagePanelHost` получает `editContext`/`onEditClose`/`onSaveEdit` параметры и переключает `ContextBlock`, prefill attachments и routing send'а. `ChatDetailScreen` маппит VM-state и через `findFirstEditText` pre-fill'ит EditText + поднимает IME. `MessageList` плюмит `isEdited` в configure'ы баблов — компоненты сами рисуют edited-индикатор.

**Tech Stack:** Kotlin + Compose. Существующие модули: `:core:model`, `:core:data`, `:core:ui`, `:feature:chatdetail`. Component dependencies: `:components/messagepanel/ContextBlock.Edit` (готов), `BubblesView.isEdited`/`MediaBubbleView.isEdited` (готовы), `ContextMenuView.Item.EDIT` (готов).

---

## File Structure

| Файл | Действие | Ответственность |
|---|---|---|
| `core/model/src/main/java/com/example/template/core/model/Message.kt` | MODIFY | `Text.isEdited`, `Media.isEdited` поля (default false). |
| `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` | MODIFY | Передача `isEdited = msg.isEdited` в `BubblesView.configure` и `MediaBubbleView.configure`. |
| `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` | MODIFY | Сигнатуры `editTextMessage`, `editMediaMessage`. |
| `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` | MODIFY | Pure-builders `buildEditedTextMessage` / `buildEditedMediaMessage` + suspend-обёртки. |
| `core/data/src/test/java/com/example/template/core/data/EditMessageTest.kt` | CREATE | Unit-тесты на pure-builders. |
| `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` | MODIFY | `EditContext` data class + `_editContext` flow + `startEdit/dismissEdit/saveEdit` + dispatch `Item.EDIT` + mutual exclusion с reply. |
| `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt` | MODIFY | `EditDisplay` data class, новые параметры `editContext/onEditClose/onSaveEdit`, routing `ContextBlock`, prefill `attachments`, ветка send и contextClose в edit-mode. |
| `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` | MODIFY | Подписка на `viewModel.editContext`, маппинг в `EditDisplay`, LaunchedEffect для prefill EditText + IME. |

## Testing approach

Pure-builders в `MockRepositoryImpl.companion` тестируются юнит-тестами (precedent: `SendMessageTest.kt`). VM и UI — manual smoke в Task 8 (тестовой инфры для них в codebase нет).

---

### Task 1: Add `isEdited` field в model

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Message.kt`

- [ ] **Step 1:** Открыть `Message.kt`, найти `data class Text` (≈ строки 57-69) и `data class Media` (≈ строки 71-83).

- [ ] **Step 2:** Добавить поле `isEdited: Boolean = false` в `Text`. Класс должен выглядеть так:

```kotlin
@Serializable @SerialName("text")
data class Text(
    override val id: String,
    override val chatId: String,
    override val senderId: String,
    override val timestamp: Long,
    override val isMine: Boolean,
    override val status: MessageStatus = MessageStatus.NONE,
    override val reactions: List<Reaction> = emptyList(),
    override val replyTo: ReplyPreview? = null,
    val body: String,
    val formatting: List<TextSpan> = emptyList(),
    val isEdited: Boolean = false,
) : Message()
```

- [ ] **Step 3:** Добавить такое же поле `isEdited: Boolean = false` в `Media`. Класс должен выглядеть так:

```kotlin
@Serializable @SerialName("media")
data class Media(
    override val id: String,
    override val chatId: String,
    override val senderId: String,
    override val timestamp: Long,
    override val isMine: Boolean,
    override val status: MessageStatus = MessageStatus.NONE,
    override val reactions: List<Reaction> = emptyList(),
    override val replyTo: ReplyPreview? = null,
    val attachments: List<MediaAttachment>,
    val caption: String? = null,
    val isEdited: Boolean = false,
) : Message()
```

- [ ] **Step 4:** Smoke compile:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
& .\gradlew.bat :core:model:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5:** Commit:

```bash
git add core/model/src/main/java/com/example/template/core/model/Message.kt
git commit -m "feat(model): add isEdited flag to Message.Text and Message.Media"
```

---

### Task 2: Plumb `isEdited` через MessageList в `:components` bubbles

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1:** Открыть `MessageList.kt`, найти вызов `view.configure(...)` внутри Text-кейса `when (msg)` (≈ строка 275). Добавить параметр `isEdited = msg.isEdited` в конце списка параметров:

```kotlin
view.configure(
    type = if (msg.isMine) BubblesView.BubbleType.MY else BubblesView.BubbleType.SOMEONE,
    message = msg.body,
    sender = senderName,
    time = time,
    avatar = senderAvatar,
    colorScheme = bubblesScheme,
    sendingState = msg.status.toBubblesState(),
    isFirstInGroup = isFirstInGroup,
    isLastInGroup = isLastInGroup,
    avatarColorScheme = avatarScheme,
    senderInitials = senderInitials,
    replySender = replySender,
    replyText = replyText,
    isEdited = msg.isEdited,
)
```

- [ ] **Step 2:** Найти вызов `view.configure(...)` внутри Media-кейса `when (msg)`. Добавить `isEdited = msg.isEdited` в конце:

```kotlin
view.configure(
    type = if (msg.isMine) MediaBubbleView.BubbleType.MY else MediaBubbleView.BubbleType.SOMEONE,
    items = mediaItems,
    message = msg.caption.orEmpty(),
    sender = senderName,
    // ... остальные параметры ...
    isEdited = msg.isEdited,
)
```

(Сохрани все существующие параметры — добавляем только `isEdited` в конец.)

- [ ] **Step 3:** Smoke compile:

```powershell
& .\gradlew.bat :core:ui:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL. (Может быть compilation warning что `:components` `configure` принимает `isEdited` с default value — это OK, поле существует.)

- [ ] **Step 4:** Commit:

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(messagelist): pass isEdited to BubblesView and MediaBubbleView configure"
```

---

### Task 3: Repository interface — add edit signatures

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`

- [ ] **Step 1:** Открыть `MessengerRepository.kt`, найти метод `deleteMessage` (≈ строка 39). Сразу после него добавить два новых метода:

```kotlin
suspend fun editTextMessage(chatId: String, messageId: String, newBody: String)

suspend fun editMediaMessage(
    chatId: String,
    messageId: String,
    newAttachments: List<MediaAttachment>,
    newCaption: String?,
)
```

- [ ] **Step 2:** Smoke compile (build пока упадёт — `MockRepositoryImpl` ещё не имплементирует новые методы):

```powershell
& .\gradlew.bat :core:data:compileReleaseKotlin
```

Expected: FAIL — error `'MockRepositoryImpl' is not abstract and does not implement abstract member`. Это ожидаемо, чиним в Task 4.

- [ ] **Step 3:** Не коммитим в этой задаче — interface без impl ломает build. Коммит будет в Task 4 вместе с реализацией.

---

### Task 4: Repository impl + tests (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/EditMessageTest.kt`

- [ ] **Step 1: Create failing test file.** Создать `EditMessageTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.AttachmentType
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Reaction
import com.example.template.core.model.ReplyPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditMessageTest {
    private val originalText = Message.Text(
        id = "m-1",
        chatId = "c-1",
        senderId = "u-me",
        timestamp = 1000L,
        isMine = true,
        status = MessageStatus.READ,
        reactions = listOf(Reaction(emoji = "👍", count = 1, isMine = true)),
        replyTo = ReplyPreview(originalId = "m-0", authorName = "Алиса", text = "оригинал"),
        body = "original body",
    )

    private val originalMedia = Message.Media(
        id = "m-2",
        chatId = "c-1",
        senderId = "u-me",
        timestamp = 2000L,
        isMine = true,
        status = MessageStatus.READ,
        reactions = emptyList(),
        replyTo = null,
        attachments = listOf(
            MediaAttachment(placeholderColor = "#fff", type = AttachmentType.Photo),
        ),
        caption = "original caption",
    )

    @Test
    fun `buildEditedTextMessage replaces body and sets isEdited`() {
        val edited = MockRepositoryImpl.buildEditedTextMessage(originalText, newBody = "new body")
        assertEquals("new body", edited.body)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedTextMessage preserves id timestamp status reactions and replyTo`() {
        val edited = MockRepositoryImpl.buildEditedTextMessage(originalText, newBody = "x")
        assertEquals(originalText.id, edited.id)
        assertEquals(originalText.chatId, edited.chatId)
        assertEquals(originalText.senderId, edited.senderId)
        assertEquals(originalText.timestamp, edited.timestamp)
        assertEquals(originalText.status, edited.status)
        assertEquals(originalText.reactions, edited.reactions)
        assertEquals(originalText.replyTo, edited.replyTo)
        assertEquals(originalText.isMine, edited.isMine)
    }

    @Test
    fun `buildEditedMediaMessage replaces attachments and caption and sets isEdited`() {
        val newAttachments = listOf(
            MediaAttachment(placeholderColor = "#000", type = AttachmentType.Video),
            MediaAttachment(placeholderColor = "#111", type = AttachmentType.Photo),
        )
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = newAttachments,
            newCaption = "updated",
        )
        assertEquals(newAttachments, edited.attachments)
        assertEquals("updated", edited.caption)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedMediaMessage allows clearing caption to null`() {
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = originalMedia.attachments,
            newCaption = null,
        )
        assertEquals(null, edited.caption)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedMediaMessage preserves id timestamp status reactions and replyTo`() {
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = emptyList(),
            newCaption = null,
        )
        assertEquals(originalMedia.id, edited.id)
        assertEquals(originalMedia.chatId, edited.chatId)
        assertEquals(originalMedia.senderId, edited.senderId)
        assertEquals(originalMedia.timestamp, edited.timestamp)
        assertEquals(originalMedia.status, edited.status)
        assertEquals(originalMedia.reactions, edited.reactions)
        assertEquals(originalMedia.replyTo, edited.replyTo)
        assertEquals(originalMedia.isMine, edited.isMine)
    }
}
```

- [ ] **Step 2: Verify tests fail (unresolved references).**

```powershell
& .\gradlew.bat :core:data:testDebugUnitTest
```

Expected: FAIL — `unresolved reference: buildEditedTextMessage` (functions don't exist yet).

- [ ] **Step 3: Add pure builders в `MockRepositoryImpl.companion`.** Открыть `MockRepositoryImpl.kt`, найти companion-object с `buildSentTextMessage` / `buildSentMediaMessage` (≈ строка 308). Сразу после `buildSentMediaMessage` добавить:

```kotlin
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
```

- [ ] **Step 4: Verify tests pass:**

```powershell
& .\gradlew.bat :core:data:testDebugUnitTest
```

Expected: PASS — все 5 тестов EditMessageTest зелёные.

- [ ] **Step 5: Реализовать suspend-обёртки в `MockRepositoryImpl`.** Найти существующие `deleteMessage` или `sendTextMessage` методы класса MockRepositoryImpl (≈ строка 125 для sendTextMessage). Добавить рядом два новых метода:

```kotlin
override suspend fun editTextMessage(
    chatId: String,
    messageId: String,
    newBody: String,
) {
    val current = _messagesByChat[chatId]?.value ?: return
    val idx = current.indexOfFirst { it.id == messageId }
    if (idx < 0) return
    val original = current[idx] as? Message.Text ?: return
    if (!original.isMine) return
    val updated = current.toMutableList().apply {
        this[idx] = buildEditedTextMessage(original, newBody)
    }
    _messagesByChat[chatId]?.value = updated
}

override suspend fun editMediaMessage(
    chatId: String,
    messageId: String,
    newAttachments: List<MediaAttachment>,
    newCaption: String?,
) {
    val current = _messagesByChat[chatId]?.value ?: return
    val idx = current.indexOfFirst { it.id == messageId }
    if (idx < 0) return
    val original = current[idx] as? Message.Media ?: return
    if (!original.isMine) return
    val updated = current.toMutableList().apply {
        this[idx] = buildEditedMediaMessage(original, newAttachments, newCaption)
    }
    _messagesByChat[chatId]?.value = updated
}
```

**Важно:** имя приватного поля для хранения per-chat messages в `MockRepositoryImpl` может отличаться от `_messagesByChat` — посмотри как сейчас реализованы `sendTextMessage` / `deleteMessage`, и используй то же имя. Принцип тот же — найти в стейт-мэпе по chatId, заменить элемент по индексу, записать обратно.

- [ ] **Step 6: Smoke compile + run tests one more time:**

```powershell
& .\gradlew.bat :core:data:compileReleaseKotlin :core:data:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, тесты зелёные.

- [ ] **Step 7: Commit:**

```bash
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt core/data/src/test/java/com/example/template/core/data/EditMessageTest.kt
git commit -m "feat(data): editTextMessage and editMediaMessage with pure builders and tests"
```

---

### Task 5: ChatDetailViewModel — EditContext + handlers

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Добавить EditContext data class.** В классе `ChatDetailViewModel`, рядом с существующим `data class ReplyContext` (≈ строка 132):

```kotlin
data class EditContext(
    val originalId: String,
    val kind: Kind,
    val originalBody: String,
    val originalAttachments: List<MediaAttachment>,
) {
    enum class Kind { TEXT, MEDIA }
}
```

- [ ] **Step 2: Добавить state-flow.** Рядом с существующим `_replyContext`:

```kotlin
private val _editContext = MutableStateFlow<EditContext?>(null)
val editContext: StateFlow<EditContext?> = _editContext.asStateFlow()
```

- [ ] **Step 3: Добавить методы `startEdit` / `dismissEdit` / `saveEdit`.** Рядом с `startReply` / `dismissReplyContext`:

```kotlin
fun startEdit(messageId: String) {
    val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
    if (!msg.isMine) return
    val (kind, body, attachments) = when (msg) {
        is Message.Text -> Triple(EditContext.Kind.TEXT, msg.body, emptyList<MediaAttachment>())
        is Message.Media -> Triple(EditContext.Kind.MEDIA, msg.caption.orEmpty(), msg.attachments)
        else -> return
    }
    _replyContext.value = null   // взаимоисключение reply ↔ edit
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
```

- [ ] **Step 4: Mutual exclusion в `startReply`.** В существующем `startReply` (≈ строка 99) добавить сброс edit в начало:

было:
```kotlin
fun startReply(messageId: String) {
    val original = _messages.value.firstOrNull { it.id == messageId } ?: return
    _replyContext.value = ReplyContext(...)
}
```

стало:
```kotlin
fun startReply(messageId: String) {
    val original = _messages.value.firstOrNull { it.id == messageId } ?: return
    _editContext.value = null   // взаимоисключение reply ↔ edit
    _replyContext.value = ReplyContext(
        originalId = original.id,
        authorName = resolveAuthorName(original),
        previewText = resolveMessagePreview(original),
    )
}
```

- [ ] **Step 5: Dispatch `Item.EDIT` в `onMenuItem`.** Найти `when (item)` в `onMenuItem` (≈ строка 86). Заменить ветку `else -> Unit` (или добавить новую если EDIT уже там обрабатывается через else):

было:
```kotlin
when (item) {
    ContextMenuView.Item.COPY -> { ... }
    ContextMenuView.Item.DELETE -> { ... }
    ContextMenuView.Item.REPLY -> startReply(st.messageId)
    else -> Unit
}
```

стало:
```kotlin
when (item) {
    ContextMenuView.Item.COPY -> { ... }
    ContextMenuView.Item.DELETE -> { ... }
    ContextMenuView.Item.REPLY -> startReply(st.messageId)
    ContextMenuView.Item.EDIT -> startEdit(st.messageId)
    else -> Unit
}
```

- [ ] **Step 6: Smoke compile:**

```powershell
& .\gradlew.bat :feature:chatdetail:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit:**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(chatdetail): EditContext state + startEdit/dismissEdit/saveEdit + Item.EDIT dispatch"
```

---

### Task 6: MessagePanelHost — editContext параметры и routing

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt`

- [ ] **Step 1: Добавить data class `EditDisplay`.** Рядом с `data class ReplyDisplay` (≈ строка 33):

```kotlin
data class EditDisplay(
    val originalId: String,
    val previewText: String,
    val initialBody: String,
    val initialAttachments: List<MediaAttachment>,
)
```

- [ ] **Step 2: Добавить параметры в `MessagePanelHost` сигнатуру.** Найти @Composable fun MessagePanelHost (≈ строка 35) и добавить три новых параметра после `onReplyClose`:

было:
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

стало:
```kotlin
@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (attachments: List<MediaAttachment>, caption: String?) -> Unit = { _, _ -> },
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    editContext: EditDisplay? = null,
    onEditClose: () -> Unit = {},
    onSaveEdit: (text: String, attachments: List<MediaAttachment>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    labels: List<String> = DEFAULT_LABELS,
    onPanelReady: ((MessagePanelView) -> Unit)? = null,
) {
```

- [ ] **Step 3: Добавить inverse-mapping `MediaAttachment.toAttachmentItem()`.** В конце файла (рядом с существующим `AttachmentItem.toDomain()`) добавить:

```kotlin
// Inverse of AttachmentItem.toDomain — для prefill panel'а attachments из edited Media-сообщения.
// Domain не хранит Uri (она нужна только для рендера компоненте, mock без реальных Uri), используем
// Uri.EMPTY. Domain AttachmentType.File мапим в IMAGE (визуально показываем как файл-thumbnail).
private fun MediaAttachment.toAttachmentItem(): AttachmentItem = AttachmentItem(
    id = id,
    uri = android.net.Uri.EMPTY,
    type = when (type) {
        DomainAttachmentType.Photo -> AttachmentType.IMAGE
        DomainAttachmentType.Video -> AttachmentType.VIDEO
        DomainAttachmentType.File -> AttachmentType.IMAGE
    },
    thumbnail = thumbnail as? android.graphics.Bitmap,
    durationLabel = durationLabel,
)
```

(Имя `AttachmentType` в каждой стороне может конфликтовать — посмотри текущие импорты файла; `:components` `AttachmentType` обычно импортирована как `AttachmentType`, а `:core:model` — как `DomainAttachmentType` (alias). Если alias другой — используй точку: `com.example.template.core.model.AttachmentType.Photo`.)

- [ ] **Step 4: LaunchedEffect для prefill / reset attachments.** Внутри `MessagePanelHost`, после `var voiceMode by remember { mutableStateOf(false) }` (≈ строка 76):

```kotlin
// Prefill / reset attachments state при entry/exit edit-mode. На entry — берём
// initialAttachments из edit-контекста; на exit (editContext == null) — сбрасываем в emptyList,
// иначе следующий reply / новая отправка унаследовал бы чужой набор.
LaunchedEffect(editContext?.originalId) {
    attachments = editContext?.initialAttachments?.map { it.toAttachmentItem() } ?: emptyList()
}
```

(Импортируй `androidx.compose.runtime.LaunchedEffect` если ещё не импортирован.)

- [ ] **Step 5: Обновить `config.contextBlock` в `remember(...)` блоке.** Найти `val config = remember(brand, isDark, attachments, labels, ...)` (≈ строка 78). Добавить `editContext` в `remember`-keys, и поменять `contextBlock`-выражение:

было:
```kotlin
val config = remember(brand, isDark, attachments, labels, stickerImages, foxtrotManStickers, gifDrawables, replyContext) {
    MessagePanelConfig(
        ...
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

стало:
```kotlin
val config = remember(brand, isDark, attachments, labels, stickerImages, foxtrotManStickers, gifDrawables, replyContext, editContext) {
    MessagePanelConfig(
        ...
        // Edit имеет приоритет над reply (mutual exclusion через VM, но guard здесь для надёжности).
        contextBlock = when {
            editContext != null -> ContextBlock.Edit(preview = editContext.previewText)
            replyContext != null -> ContextBlock.Reply(
                author = replyContext.authorName,
                preview = replyContext.previewText,
                showThumbnail = false,
            )
            else -> ContextBlock.None
        },
    )
}
```

- [ ] **Step 6: Routing send и contextClose внутри `update` lambda AndroidView.** Найти существующий блок, где определяются `panel.onSendText` и `panel.onContextClose` (≈ строка 113-131). Заменить:

было:
```kotlin
update = { panel ->
    panel.onContextClose = { onReplyClose() }
    panel.configure(config, colorScheme)
    panel.onSendText = { text ->
        val pending = attachments
        if (pending.isEmpty()) {
            onSendText(text)
        } else {
            val media = pending.map { it.toDomain() }
            val caption = text.takeIf { it.isNotBlank() }
            onSendMedia(media, caption)
            attachments = emptyList()
        }
        panel.clear()
    }
    // ... остальные panel.onXxx ...
},
```

стало:
```kotlin
update = { panel ->
    panel.onContextClose = {
        if (editContext != null) {
            panel.clear()
            onEditClose()
        } else {
            onReplyClose()
        }
    }
    panel.configure(config, colorScheme)
    panel.onSendText = { text ->
        if (editContext != null) {
            // В edit-mode VM сама проверит empty + no-op'нет.
            onSaveEdit(text, attachments.map { it.toDomain() })
            panel.clear()
        } else {
            val pending = attachments
            if (pending.isEmpty()) {
                onSendText(text)
            } else {
                val media = pending.map { it.toDomain() }
                val caption = text.takeIf { it.isNotBlank() }
                onSendMedia(media, caption)
                attachments = emptyList()
            }
            panel.clear()
        }
    }
    // ... остальные panel.onXxx (onMicClick, onDeleteVoice, etc.) — НЕ ТРОГАТЬ ...
},
```

(Все остальные обработчики `panel.onXxx` (voice, attachment remove, attach click и т.д.) — оставить как есть. Меняем только onContextClose и onSendText.)

- [ ] **Step 7: Smoke compile:**

```powershell
& .\gradlew.bat :core:ui:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit:**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt
git commit -m "feat(messagepanelhost): edit-mode (editContext, onEditClose, onSaveEdit)"
```

---

### Task 7: ChatDetailScreen — wiring + prefill

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Подписка на editContext.** В начале `@Composable fun ChatDetailScreen` (рядом с `val replyCtx by viewModel.replyContext.collectAsState()`) добавить:

```kotlin
val editCtx by viewModel.editContext.collectAsState()
```

- [ ] **Step 2: Маппинг VM.EditContext → host.EditDisplay.** Под подпиской добавить:

```kotlin
val editDisplay = editCtx?.let { ctx ->
    EditDisplay(
        originalId = ctx.originalId,
        previewText = ctx.originalBody.takeIf { it.isNotBlank() } ?: "Изображение",
        initialBody = ctx.originalBody,
        initialAttachments = ctx.originalAttachments,
    )
}
```

Добавь импорт `import com.example.template.core.ui.hosts.EditDisplay` (рядом с уже существующим импортом `ReplyDisplay` из того же пакета).

- [ ] **Step 3: LaunchedEffect для prefill EditText + IME.** Рядом с существующим `LaunchedEffect(panel, replyCtx?.originalId)` (≈ строка 62-69) добавить второй LaunchedEffect:

```kotlin
// При входе в edit-mode pre-fill input текстом оригинала, ставим cursor в конец, поднимаем IME.
// Ключ — originalId, чтобы каждое новое edit'ование триггерило заново даже если editCtx-non-null
// уже был.
LaunchedEffect(panel, editCtx?.originalId) {
    if (panel != null && editCtx != null) {
        val edit = panel.findFirstEditText() ?: return@LaunchedEffect
        edit.setText(editCtx!!.initialBody)
        edit.setSelection(editCtx!!.initialBody.length)
        edit.requestFocus()
        val imm = panel.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }
}
```

(Внутри LaunchedEffect `editCtx` — это snapshot через `by collectAsState`, проверка `!= null` отдельно от использования. Используй `editCtx!!.initialBody` ИЛИ скопируй в локальную val: `val ctx = editCtx ?: return@LaunchedEffect`.)

Лучший вариант — local val:

```kotlin
LaunchedEffect(panel, editCtx?.originalId) {
    val ctx = editCtx ?: return@LaunchedEffect
    if (panel == null) return@LaunchedEffect
    val edit = panel.findFirstEditText() ?: return@LaunchedEffect
    edit.setText(ctx.initialBody)
    edit.setSelection(ctx.initialBody.length)
    edit.requestFocus()
    val imm = panel.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
}
```

- [ ] **Step 4: Передать новые параметры в `MessagePanelHost(...)`.** Найти существующий вызов (≈ строка 196). Добавить новые параметры после `onReplyClose = viewModel::dismissReplyContext`:

было:
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

стало:
```kotlin
MessagePanelHost(
    onSendText = viewModel::sendText,
    onSendMedia = viewModel::sendMedia,
    replyContext = replyCtx?.let { ReplyDisplay(it.authorName, it.previewText) },
    onReplyClose = viewModel::dismissReplyContext,
    editContext = editDisplay,
    onEditClose = viewModel::dismissEdit,
    onSaveEdit = viewModel::saveEdit,
    modifier = Modifier.imePadding(),
    onPanelReady = { panelRef.value = it },
)
```

- [ ] **Step 5: Smoke compile:**

```powershell
& .\gradlew.bat :feature:chatdetail:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit:**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(chatdetail): wire editContext + prefill EditText on enter edit-mode"
```

---

### Task 8: Сборка, установка, manual smoke

**Files:**
- None (только smoke test)

- [ ] **Step 1: Full release build:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
& .\gradlew.bat :app:assembleRelease -Pbrand=foxtrot
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install:**

```powershell
& "C:\Users\Grond\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Claude\test_project\android-template\app\build\outputs\apk\release\app-release.apk"
```

Expected: `Success`.

- [ ] **Step 3: Acceptance-list** (ручная проверка на устройстве `qgjrbqlrrkswk755`):

   1. Открыть P2P-чат. Long-press по СВОЕМУ Text-сообщению → в контекстном меню есть «Редактировать» (Item.EDIT). Тап на него.
   2. Контекстное меню закрывается; над MessagePanel появляется context-block с заголовком «Редактировать сообщение» и subtitle = текст исходного сообщения. Input уже содержит тот же текст, курсор в конце. Клавиатура открыта.
   3. Изменяю текст, нажимаю кнопку отправки (та же иконка). Сообщение обновляется в ленте — новый текст, плюс **появляется индикатор "edited"** в статусе баббла (компонент рисует сам, точное место смотреть на устройстве).
   4. **MY Media-сообщение с caption**: тот же сценарий. Subtitle context-block'а = caption.
   5. **MY Media-сообщение без caption**: subtitle = "Изображение" (литерал).
   6. **Media edit с изменением attachments**: можно удалять через «крестик» на thumbnail; после save сообщение обновляется. Если все attachments удалены и текст пустой — кнопка send ничего не делает (no-op).
   7. **Long-press по ЧУЖОМУ сообщению** → пункт «Редактировать» либо отсутствует (ContextMenuView сам не показывает EDIT для SOMEONE), либо нажатие на него ничего не делает (VM ранний return на `!isMine`). Конкретное поведение зависит от `ContextMenuView`, оба варианта приемлемы.
   8. **Long-press по Voice/Link/CallMeet** → контекстное меню не открывается (`openContextMenu` early-return). Edit недоступен.
   9. **Mutual exclusion с reply**: long-press → Reply → начинается reply-режим (видим reply context-block). Потом long-press по своему сообщению → Edit → reply-режим должен мгновенно смениться на edit-режим (один context-block, prefill input текстом MY-сообщения).
   10. **Dismiss edit без save**: при активном edit нажать крестик на context-block'е. Edit-режим уходит, input очищается, context-block убирается. Оригинал в ленте без изменений.
   11. **`isEdited` сохраняется через save**: после edit'ования сообщения проверить в ленте — индикатор «edited» виден. Reactions/replies на это сообщение не сломались.
   12. **Tap по баббле** после edit (открытие контекстного меню) — всё ещё работает.

- [ ] **Step 4:** Если acceptance прошёл — финальный коммит не нужен (все коммиты по ходу). Если что-то не работает — wip-коммит с пометкой issue и обсудить.

---

## Спасательные шаги при типовых проблемах

- **EditText не pre-fill'ится.** Проверь `findFirstEditText()` — он должен находить тот же EditText, что и для IME-focus при reply (тот же приём уже работает в Task 7 / `LaunchedEffect(panel, replyCtx?.originalId)`). Если null — посмотри логически, что `panelRef` уже получен через `onPanelReady` к моменту срабатывания LaunchedEffect (он срабатывает при изменении ключа, в т.ч. при первой композиции).

- **Context-block остаётся на месте при reply→edit переходе.** Проверь, что `_replyContext.value = null` стоит до `_editContext.value = ...` в `startEdit`. И что `config.contextBlock`-when имеет порядок: edit→reply→none, не наоборот.

- **`isEdited` не отображается.** Проверь, что `BubblesView.configure(...)` и `MediaBubbleView.configure(...)` действительно получают `isEdited = msg.isEdited`. В `:components` поле имеет default false, так что отсутствие параметра — silent no-op.

- **Attachments старого сообщения остаются после save.** LaunchedEffect с key=`editContext?.originalId` должен сбросить state в `emptyList()` когда editContext становится null. Если зависает — проверь, что VM действительно очищает `_editContext.value = null` после repository call.

- **Edit пустого Media → save ничего не делает, но edit-mode не выходит.** Это by design. Пользователь либо что-то прикрепит/впишет и сохранит, либо нажмёт крестик.

- **Compose-warning "MessagePanelConfig.contextBlock changed, MessagePanelConfig is not stable".** Это nit — `remember` всё равно перевычислит config при смене editContext (он в keys). Можно проигнорировать.
