# Множественный выбор сообщений

**Дата:** 2026-05-25
**Статус:** design

## Цель

Добавить режим множественного выбора сообщений в чате. Запускается через пункт «Выбрать» (`ContextMenuView.Item.SELECT`) в контекстном меню сообщения. В режиме:
- слева от каждого баббла появляется чекбокс,
- header заменяется на `HeaderConfig.Custom` со счётчиком и кнопкой «Отмена»,
- `MessagePanel` снизу заменяется на ActionBar с действиями над выбранными сообщениями.

## Scope

- Режим выбора **взаимоисключающий** с reply и edit (третий mutual-exclusive контекст рядом с уже существующими `ReplyContext` / `EditContext`).
- Выбираются все типы баббл-сообщений: `Text`, `Media`, `Voice`, `Link`, `CallMeet`.
- Системные сообщения (`SystemRow` — date-разделители, «Joined ...») **не выбираются**, чекбокс рядом с ними не рисуется.
- ActionBar содержит четыре действия: **Удалить · Переслать · Сохранить · Закрепить**.
  - **Удалить** работает; кнопка `disabled`, если в выборе есть хотя бы одно SOMEONE-сообщение (удаление чужих не поддерживаем).
  - **Сохранить** работает: пересылает выбранные Text/Media в чат «Сохранённые» (Self-чат).
  - **Переслать** и **Закрепить** — стаб: показывают Toast «TBD» и выходят из режима выбора.
- Voice / Link / CallMeet при «Сохранить» пропускаются (см. Edge cases).
- Тап по любой части строки в режиме выбора — `toggle`. Long-press, контекстное меню и swipe-to-reply отключены.
- Системный back и кнопка «Отмена» (правая кнопка header'а) — выходят из режима выбора (не из чата).
- Снятие последнего чекбокса — автоматический выход из режима (`selection.selectedIds.isEmpty() → null`).

## UX

1. Пользователь открывает контекстное меню на сообщении и выбирает пункт **«Выбрать»** (`Item.SELECT`).
2. Контекстное меню закрывается. Включается режим выбора:
   - сообщение, на котором меню вызывалось, **уже отмечено** (в `selectedIds` сразу лежит его id);
   - слева от каждого баббла появляется круглый чекбокс (`CheckboxView`, `Shape.CIRCLE`, `showText = false`). Появление анимировано — ширина checkbox-cell `0dp → 48dp` через `animateDpAsState(tween(200))`. Бабблы (включая MY, прижатые к концу) визуально сдвигаются вправо;
   - header перерисовывается из `HeaderConfig.Chat` в `HeaderConfig.Custom` со счётчиком `«Выбрано N»` слева и кнопкой `«Отмена»` справа (Primary text-кнопка);
   - `MessagePanel` снизу заменяется на ActionBar (4 кнопки в Row).
3. Тап по любой части строки сообщения — toggle (добавление в выбор / удаление из выбора). Чекбокс реагирует визуально через rerun `configure(isChecked = ...)`.
4. Tap на «Отмена» в header'е, системный back, или снятие последнего чекбокса — выход из режима. UI откатывается к обычному состоянию.
5. Действия ActionBar (см. ниже) — после успешного выполнения выход из режима автоматический.

## Архитектура

### Слой 1. ViewModel (`feature:chatdetail`)

В `ChatDetailViewModel` добавляется новый mutual-exclusive контекст:

```kotlin
data class SelectionContext(val selectedIds: Set<String>)

private val _selection = MutableStateFlow<SelectionContext?>(null)
val selection: StateFlow<SelectionContext?> = _selection.asStateFlow()
```

`null` — режим выключен. Не-`null` — включён; `selectedIds` всегда непустое (защита через auto-exit в `toggleSelection`).

API:

```kotlin
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

fun exitSelection() { _selection.value = null }

fun deleteSelected() {
    val ids = _selection.value?.selectedIds ?: return
    val msgs = _messages.value.filter { it.id in ids }
    if (msgs.any { !it.isMine }) return  // safety; UI disables button
    viewModelScope.launch {
        ids.forEach { repository.deleteMessage(chatId, it) }
        _selection.value = null
    }
}

fun saveSelected() {
    val ids = _selection.value?.selectedIds ?: return
    val msgs = _messages.value.filter { it.id in ids }.sortedBy { it.timestamp }
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
```

Mutual exclusion с reply/edit:
- `startSelection` чистит `_replyContext` и `_editContext`.
- Симметрично — `startReply` и `startEdit` чистят `_selection`.

`init`-блок дополняется prune-логикой: при обновлении `_messages` (например, после удаления извне) — выкидываем из `selectedIds` исчезнувшие id, и если набор опустел — выходим в `null`.

Контекстное меню → старт: в `onMenuItem` добавляется ветка
```kotlin
ContextMenuView.Item.SELECT -> startSelection(st.messageId)
```

### Слой 2. Repository (`:core:data`)

В `MessengerRepository`:

```kotlin
suspend fun forwardMessagesToSaved(messages: List<Message>)
```

Реализация в `MockRepositoryImpl`:
1. Находим Self-чат: `_chats.value.firstOrNull { it.avatar.type == AvatarType.Self }`. Если нет — `return` (тихий no-op).
2. Прогреваем `messageCache` для Self-чата по той же логике, что `loadMessages` (загрузка JSON / пустой список / `appendIncomingPreviewIfNeeded`).
3. Для каждого исходного сообщения через builder-функции в `companion object` строим новое сообщение в Self-чате:
   - `id = "fwd-$ts"`, где `ts = now + i` (монотонный сдвиг по индексу — защита от коллизии).
   - `senderId = currentUser.id`, `isMine = true`, `status = MessageStatus.READ` (Self — «прочитано» сразу).
   - `Text → Text(body)`, `Media → Media(attachments, caption)`.
   - `Voice / Link / CallMeet / System` — **пропускаются** (см. Edge cases).
4. Аппендим все новые сообщения в `cache.value` одной операцией.
5. Обновляем `chat.lastMessage` Self-чата по последнему добавленному сообщению через существующий `updateChatLastMessage(...)`. `sortChats` поднимет Self-чат вверх по `lastMessage.timestamp` автоматически.

Без `forwardedFrom` метадаты — прототип. Без status-transition корутин (пишем сразу `READ`, не идём через `sendTextMessage` чтобы не плодить N параллельных «DELIVERED → READ через 800мс»).

### Слой 3. UI (`feature:chatdetail`, `core:ui`)

**`ChatDetailScreen`** наблюдает `viewModel.selection.collectAsState()`. Флаг `selectionActive = selection != null` управляет переключением:

- **Header**: `HeaderConfig.Chat(...)` ↔ `HeaderConfig.Custom(...)`:

  ```kotlin
  HeadersView.HeaderConfig.Custom(
      title = "Выбрано ${sel.selectedIds.size}",
      titleAlignment = TitleAlignment.LEFT,
      leftButton = LeftButton.HIDE,
      rightButton = RightButton.TEXT,
      rightButtonType = ButtonType.PRIMARY,
      rightButtonLabel = "Отмена",
      onRightClick = viewModel::exitSelection,
  )
  ```

  Счётчик — единым форматом `«Выбрано N»` без склонения по числу.

- **Bottom slot**: `MessagePanelHost(...)` ↔ `SelectionActionBar(...)`.

- **BackHandler**: `enabled = selectionActive`, `onBack = viewModel::exitSelection`. При выключенном — back уходит наверх (выход из чата).

- **IME**: `LaunchedEffect(selectionActive)` — при `true` зовём `panelRef.value?.dismissKeyboard()`. Иначе клавиатура осталась бы поверх ActionBar.

- **Колбэки в `MessageList`** переключаются:

  ```kotlin
  onBubbleTap = if (selectionActive) viewModel::toggleSelection else viewModel::openContextMenu,
  onReactionTap = if (selectionActive) { id, _ -> viewModel.toggleSelection(id) } else viewModel::toggleReactionDirect,
  onAddReactionTap = if (selectionActive) viewModel::toggleSelection else viewModel::openContextMenu,
  ```

  Это обеспечивает поведение «тап по любой части строки = toggle» даже когда тач консьюмится внутри `ReactionsView` (AndroidView с Material3-ripple).

**`SelectionActionBar`** — новый Composable в `feature:chatdetail` (file `SelectionActionBar.kt`):

| # | Иконка           | Лейбл       | Disabled когда                    | Action                                    |
|---|------------------|-------------|------------------------------------|-------------------------------------------|
| 1 | `delete`         | Удалить     | в selection есть SOMEONE          | `deleteSelected()` → выход                |
| 2 | `forward-stroke` | Переслать   | —                                  | Toast «TBD», выход                        |
| 3 | `saved`          | Сохранить   | —                                  | `saveSelected()` (forward → Self) → выход |
| 4 | `pin-stroke`     | Закрепить   | —                                  | Toast «TBD», выход                        |

Имена иконок повторяют те, что использует `ContextMenuView.Item` в `:components` (`delete`, `forward-stroke`, `saved`, `pin-stroke`). Все четыре уже синканы из `../icons-library/icons/` через `:app:syncIconsFromLibrary`.

Раскладка: `Row { fillMaxWidth, weight(1f) каждой кнопке }`. Каждая ячейка — `ButtonView` из `:components` (icon-only, `Size.S`/`XS`) + лейбл под ним через Text. Фон совпадает с MessagePanel (sheet-color из brand). Высота `~64dp`, `imePadding()` оставляем для симметрии.

Состояние `deleteEnabled` вычисляется из `selection.selectedIds` и `messages` каждой рекомпозицией.

**`MessageList` (`core:ui/hosts/MessageList.kt`)** — новые параметры:

```kotlin
selectionActive: Boolean = false,
selectedIds: Set<String> = emptySet(),
onToggleSelection: (messageId: String) -> Unit = {},
```

Каждый `RowItem.Bubble` оборачивается в `Row` с двумя слотами:

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    val checkboxCellWidth by animateDpAsState(
        targetValue = if (selectionActive) 48.dp else 0.dp,
        animationSpec = tween(200),
        label = "checkboxCell",
    )
    Box(
        modifier = Modifier.width(checkboxCellWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (checkboxCellWidth > 0.dp) {
            AndroidView(
                factory = { ctx -> CheckboxView(ctx).apply { /* layoutParams WRAP */ } },
                update = { it.configure(
                    shape = CheckboxView.Shape.CIRCLE,
                    isChecked = msg.id in selectedIds,
                    showText = false,
                    colorScheme = checkboxScheme,
                ) },
            )
        }
    }
    // Существующая Column-обёртка (SwipeToReplyItem + bubble row + reactions)
    Column(modifier = Modifier.weight(1f)) { /* ... */ }
}
```

`SwipeToReplyItem(enabled = !selectionActive)` — свайп выключен в режиме выбора.

Системные row'ы (`RowItem.SystemRow`) рендерятся как раньше (без Row-обёртки и checkbox-cell). По ним нет clickable — клик впустую.

`checkboxScheme = brand.checkboxColorScheme(isDark)` — готовая brand-aware схема из `DSBrand`, реализована во всех 5 бренд-кодах.

## Edge cases

- **Voice / Link / CallMeet при «Сохранить»**: тихо пропускаются. В Self-чат улетают только Text/Media из выбора. Без Toast.
- **Self-чат отсутствует** (если кто-то поправит `chats.json`): `forwardMessagesToSaved` — `?: return`, тихий no-op. Прототип.
- **Удаление выбранного сообщения извне** (через какой-либо параллельный путь): prune-логика в `init`-блоке VM выкидывает исчезнувшие id из `selectedIds`; пустой набор → выход.
- **Configuration change**: `_selection` живёт в VM, переживает rotation. `process death` — состояние теряется (соответствует `_replyContext` / `_editContext`).
- **Counter «Выбрано 0»** не отображается никогда: `toggleSelection` при пустом наборе auto-exit'ит в `null`. Начальный набор всегда содержит инициирующее сообщение.
- **IME при входе в selection**: явно тушим через `panelRef.value?.dismissKeyboard()`.
- **ReactionsView в режиме selection**: внутренние тап-консьюмеры обходятся через подмену VM-колбэков (`onReactionTap`/`onAddReactionTap` → `toggleSelection`). `Modifier.clickable` на внешнем Column не сработал бы.

## Тесты

Pure-builder-тесты в `core/data/src/test/` (по образцу существующего `SendMessageTest.kt`):

- `buildForwardedTextMessage` сохраняет `body`, выдаёт правильный `id`, `chatId`, `senderId`, `isMine = true`, `status = READ`, `replyTo = null`.
- `buildForwardedMediaMessage` сохраняет `attachments` и `caption`.
- `buildForwardedBatch` сохраняет порядок и выдаёт монотонные `id` (`fwd-100`, `fwd-101`, ...).
- `buildForwardedBatch` пропускает Voice / Link / CallMeet / System.

VM-тесты на toggle / mutual exclusion — out of scope (нет существующей VM-test-инфраструктуры; будет покрыто ручным QA).

UI-тесты — out of scope.

## Out of scope

- Forward (отдельный экран выбора чата) — стаб «TBD».
- Pin (закрепление сообщения) — стаб «TBD».
- ForwardedFrom-метадата в пересланном сообщении.
- Сохранение selection при `process death`.
- Compose UI-тесты.
- Множественное выделение через drag (long-press + drag поверх соседних) — только tap-toggle.

## Связанные файлы

- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/SelectionActionBar.kt` (новый)
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`
- `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt` (или новый `ForwardMessageTest.kt`)
