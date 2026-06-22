# Редактирование своих сообщений

**Дата:** 2026-05-20
**Статус:** design

## Цель

Дать пользователю возможность отредактировать свои отправленные сообщения (Text или Media с/без caption) через пункт «Редактировать» контекстного меню. После сохранения сообщение помечается флагом `isEdited`, и `:components` рисует соответствующий индикатор в статусе баббла.

## Scope

- Только **MY**-сообщения (свои). Чужие не редактируем.
- Только типы **Text** и **Media**. Voice/Link/CallMeet контекстное меню вообще не открывают.
- Edit и Reply — **взаимоисключающие** режимы (один `MessagePanelView.ContextBlock` за раз). Активация одного автоматически сбрасывает другой.
- На Media-сообщении edit-mode даёт пользователю изменять и caption, и сам набор вложений (через стандартную attachments-секцию `MessagePanelView`).
- Если в edit-mode пользователь удалил весь текст и все вложения — `MessagePanel` остаётся в дефолтном состоянии (с context-block'ом Edit). Send-нажатие в таком состоянии — no-op (edit не сохраняется, но и оригинал не удаляется).
- Timestamp оригинала после edit **не обновляется** — единственный визуальный сигнал «отредактировано» это флаг `isEdited` через индикатор от `:components`.
- Reply-snapshots на отредактированных оригиналах **не обновляются** — `ReplyPreview` замораживается на момент отправки ответа (стандартное Telegram-поведение, согласовано с уже существующим reply-флоу).

## UX

1. Пользователь long-press'ом на своём Text/Media-баббле открывает контекстное меню.
2. Выбирает пункт **«Редактировать»** (`ContextMenuView.Item.EDIT`). Контекстное меню закрывается.
3. Над `MessagePanel` появляется context-block:
   - title (статический в `:components`): «Редактировать сообщение»
   - subtitle (preview):
     - текст сообщения (для Text — `body`; для Media с caption — `caption`),
     - либо литерал «Изображение», если у Media сообщения caption пуст.
4. Input `MessagePanel` пред-заполнен:
   - текстом оригинала (`body` для Text, `caption.orEmpty()` для Media);
   - курсор — в конце текста;
   - IME поднимается автоматически.
5. Для Media: attachments-секция **автоматически раскрыта** (через `panel.expandAttachments()`) и содержит вложения оригинала. Тап на «скрепку» в edit-mode НЕ подсовывает 5 demo-файлов (default-fill отключён через guard'ы в `onAttachClick`). Удаление одного attachment'а удаляет именно его, не каскадно (id синтезируется с индексом).
6. Кнопка отправки (та же иконка `send`) сохраняет правку:
   - Text: `repository.editTextMessage(chatId, originalId, trimmed)`.
   - Media: `repository.editMediaMessage(chatId, originalId, attachments, caption.takeIf { it.isNotBlank() })`.
7. После сохранения: edit-mode сбрасывается, context-block исчезает, `panel.clear()`, сообщение в ленте обновляется (новый `body`/`caption`/`attachments` + `isEdited = true`), и `:components` рисует indicator в статусе.
8. Чтобы выйти из edit-mode без сохранения — пользователь нажимает «крестик» на context-block'е (`onContextClose` callback).

## Архитектура

### Слой 1. Model (`:core:model`)

В `Message.Text` и `Message.Media` добавляется:

```kotlin
val isEdited: Boolean = false,
```

Сериализация через kotlinx-serialization — поле опциональное (default `false`), обратная совместимость с уже распарсенным JSON-mock'ом сохраняется.

### Слой 2. Repository (`:core:data`)

В интерфейс `MessengerRepository` добавляются два метода:

```kotlin
suspend fun editTextMessage(chatId: String, messageId: String, newBody: String)

suspend fun editMediaMessage(
    chatId: String,
    messageId: String,
    newAttachments: List<MediaAttachment>,
    newCaption: String?,
)
```

`MockRepositoryImpl`:
- Находит сообщение по id в `_messages[chatId]`.
- Если тип не совпадает (например, `editTextMessage` на Media или наоборот) — no-op + лог (программная ошибка, не сценарий пользователя).
- Если `!message.isMine` — no-op (не должно произойти, но guard на VM-уровне).
- Заменяет message новой копией с обновлёнными полями + `isEdited = true`. `timestamp`, `senderId`, `status`, `reactions`, `replyTo` сохраняются.

### Слой 3. ViewModel (`:feature:chatdetail`)

```kotlin
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
```

Методы:

```kotlin
fun startEdit(messageId: String) {
    val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
    if (!msg.isMine) return
    val (kind, body, attachments) = when (msg) {
        is Message.Text -> Triple(EditContext.Kind.TEXT, msg.body, emptyList<MediaAttachment>())
        is Message.Media -> Triple(EditContext.Kind.MEDIA, msg.caption.orEmpty(), msg.attachments)
        else -> return
    }
    _replyContext.value = null                        // взаимоисключение reply ↔ edit
    _editContext.value = EditContext(msg.id, kind, body, attachments)
}

fun dismissEdit() {
    _editContext.value = null
}

fun saveEdit(text: String, attachments: List<MediaAttachment>) {
    val ctx = _editContext.value ?: return
    val trimmedText = text.trim()
    if (trimmedText.isEmpty() && attachments.isEmpty()) return  // empty — оставляем panel в edit-mode
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

В `onMenuItem(...)` обработка `Item.EDIT`:

```kotlin
ContextMenuView.Item.EDIT -> startEdit(st.messageId)
```

`startReply(messageId)` симметрично сбрасывает `_editContext.value = null` перед установкой `_replyContext`.

### Слой 4. MessagePanelHost (`:core:ui`)

Новые параметры:

```kotlin
data class EditDisplay(
    val originalId: String,
    val previewText: String,                       // готовый subtitle для ContextBlock.Edit
    val initialBody: String,                       // для prefill EditText
    val initialAttachments: List<AttachmentItem>,  // для prefill attachments state
)

@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (List<MediaAttachment>, String?) -> Unit,
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    editContext: EditDisplay? = null,
    onEditClose: () -> Unit = {},
    onSaveEdit: (text: String, attachments: List<MediaAttachment>) -> Unit = { _, _ -> },
    ...
)
```

Внутри:
- `config.contextBlock`: при `editContext != null` → `ContextBlock.Edit(preview = editContext.previewText)`; иначе при `replyContext != null` → `ContextBlock.Reply(...)`; иначе `None`.
- `attachments` Compose-state переинициализируется через `LaunchedEffect(editContext?.originalId)` из `editContext.initialAttachments`.
- В `panel.onSendText` lambda — ветка: если `editContext != null` → `onSaveEdit(text, currentAttachments)`, иначе существующая логика (`onSendText` / `onSendMedia`).
- В `panel.onContextClose` lambda — ветка: если `editContext != null` → `onEditClose()`, иначе → `onReplyClose()`.

### Слой 5. ChatDetailScreen

- Передаёт в `MessagePanelHost` параметры из VM: `editContext = editCtx?.let { /* мап на EditDisplay */ }`, `onEditClose = viewModel::dismissEdit`, `onSaveEdit = viewModel::saveEdit`.
- Маппинг `EditContext` → `EditDisplay`: previewText вычисляется как `originalBody.takeIf { it.isNotBlank() } ?: "Изображение"`; `initialAttachments` маппится из `List<MediaAttachment>` в `List<AttachmentItem>` (обратное преобразование `AttachmentItem.toDomain()` — нужна inverse-маппинг функция в `MessagePanelHost`).
- `LaunchedEffect(panel, editCtx?.originalId)` (новый, параллельно с reply-LaunchedEffect): когда новый edit активируется — находит EditText через `findFirstEditText()`, вызывает `setText(editCtx.initialBody)` + `setSelection(initialBody.length)` + `imm.showSoftInput(...)`. Когда `editCtx == null` (выход из edit-mode) — `panel.clear()`.

### Слой 6. MessageList (`:core:ui`)

В уже существующих вызовах `BubblesView.configure(...)` и `MediaBubbleView.configure(...)` добавляется параметр:

```kotlin
isEdited = msg.isEdited,
```

`:components` берут оттуда — нам ничего рисовать самим не нужно.

### Слой 7. Контекстное меню

Дополнительных правок не требует. `ContextMenuView` уже включает `Item.EDIT` для MY-баблов во всех `Mode`. Для Voice/Link/CallMeet меню в принципе не открывается (`ChatDetailViewModel.openContextMenu` делает early-return на этих типах).

## Конфликты и их разрешение

- **Reply ↔ Edit взаимоисключение.** `startEdit` обнуляет `_replyContext`; `startReply` обнуляет `_editContext`. ContextBlock рендерится по приоритету: edit > reply > none.
- **Заглушка empty input при save.** `saveEdit` возвращается с no-op если текст и attachments пусты. `MessagePanel` остаётся в edit-mode с пустым input и context-block'ом Edit — пользователь либо что-то впишет/прикрепит, либо нажмёт «крестик» на context-block'е.
- **Pre-fill EditText при отсутствии публичного API.** `MessagePanelView` не экспонирует input EditText. Используем тот же приём, что для IME-focus при reply: walk down view-tree через `findFirstEditText()`, set/select.
- **Удаление вложений в edit-mode.** `MessagePanelView.onAttachmentRemove` уже работает в host'е (убирает из state). Если в edit-mode удалены ВСЕ вложения, но caption остался непустым — `editMediaMessage` в repo конвертирует Media-сообщение в `Message.Text` (через pure builder `buildMediaToTextConversion`, сохраняя id/chatId/senderId/timestamp/status/reactions/replyTo). Это нужно, потому что `MediaBubbleView` с пустыми items рисует «растянутый» баббл — визуально некрасиво для message без media. Если оба пусты — `saveEdit` в VM делает no-op (edit-mode остаётся активным).
- **Edit не меняет тип сообщения.** На Text-сообщении в edit-mode пользователь может теоретически прикрепить вложения через «скрепку» — но `saveEdit` для `Kind.TEXT` маршрутизирует в `editTextMessage` и attachments игнорирует. Текстовое сообщение остаётся текстовым. Аналог в Telegram/WhatsApp.
- **Reset attachments state после exit edit-mode.** В `MessagePanelHost` существующий Compose-state `attachments` переинициализируется на entry в edit-mode из `initialAttachments`. При выходе из edit-mode (save или dismiss через крестик) state сбрасывается в `emptyList()` — иначе следующий reply / новая отправка унаследует чужой набор вложений.

## Тестирование

Unit-тесты на `MockRepositoryImpl.editTextMessage` / `editMediaMessage`:
- Поле `isEdited` устанавливается в true.
- `body`/`caption`/`attachments` заменяются.
- `timestamp`, `senderId`, `status`, `reactions`, `replyTo` сохраняются.
- No-op на mismatched type / on non-MY message.
- Расположение тестов: `core/data/src/test/.../EditMessageTest.kt` (по аналогии с `SendMessageTest.kt`).

Manual smoke на устройстве (Task 6 будущего плана):
- Edit Text-сообщения: ввод/изменение/save → новый body виден, edited-индикатор появляется.
- Edit Media: добавить/удалить вложения, изменить caption, save → отрисовка обновляется.
- Edit Media с непустым caption: subtitle context-block'а = caption.
- Edit Media с пустым caption: subtitle = «Изображение».
- Reply на сообщение, потом перевод на Edit другого сообщения через меню — reply context-block сбрасывается, открывается edit.
- Edit чужого сообщения через меню (если вдруг попадёт): `startEdit` ничего не делает.
- Удалить весь контент в edit-mode и нажать send: ничего не происходит, edit-mode сохраняется.
- Закрыть edit-mode крестиком: `panel.clear()`, input пустой.

## Что НЕ делаем (out of scope)

- Редактирование Voice/Link/CallMeet.
- Edit-history (показ предыдущих версий).
- Откатить edit / Undo после save.
- Lock-edit-window (15-минутное окно как в Telegram — не нужно для visual prototype).
- Перенос отредактированного сообщения в конец ленты (изменение timestamp).
- Обновление reply-snapshots в зависимых сообщениях.
