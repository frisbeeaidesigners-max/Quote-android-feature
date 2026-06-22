# Message Bubble Context Menu — Design Spec

**Дата:** 2026-05-18
**Статус:** approved (готово к плану)
**Источник макетов:** Figma `VVccyf630TP7LcIFOKeVYk` (`node-id=5862-61393` для чужих сообщений, `node-id=5862-61446` для своих). Файл за авторизацией — состав меню и стиль уточнены вербально.

## Цель

Добавить контекстное меню по короткому тапу на любой message-bubble в `ChatDetailScreen`. Меню — фикс снизу экрана, поверх dim 50%; сверху отдельный блок с быстрыми реакциями (5 эмодзи + кнопка «+»), снизу через 8dp — карточка с пунктами действий.

Дополнительно: тап по эмодзи в верхнем ряду добавляет/убирает ReactionsView-стэк под бабблом. Пункты «Копировать» и «Удалить» работают; остальные пункты — закрытие меню без побочных действий (поведение фич — вне scope этого прототипа).

Меню и быстрые реакции сейчас отсутствуют в `:components` (android-components). Пока реализуем локально в `android-template` как **временное решение**, пометив TODO; после стабилизации мигрируют в `:components`.

## Не входит в scope

- Полный emoji picker по «+» (заглушка).
- Reply / Forward / Edit / Pin / AddTag / Save / Select — заглушки (только закрытие меню).
- Long-press, multi-selection, hero-анимация бабла, selection mode.
- a11y / TalkBack-полировка (минимум: стандартный clickable).
- Реакции от других пользователей в моке (`count>1`).
- Тап-перехват внутри media play-button — он consume'тся самой кнопкой; это OK для прототипа.

## UX

**Жест:** короткий tap по баблу.

**Раскладка overlay:**

```
+----------------------+
|  dim 50% black       |
|                      |
|  [бабл в чате видим  |
|   сквозь dim, на     |
|   своём месте]       |
|                      |
|        ...           |
|                      |
| ┌─ReactionsPicker──┐ |  ← Card, padding 16dp горизонт.
| │ 👌 👍 🎉 🥳 ❤️ ＋ │ |
| └──────────────────┘ |
|         8dp          |
| ┌─MenuItemsCard────┐ |
| │ ⤺  Ответить       │ |
| │ ✎  Редактировать  │ |  (только свои)
| │ ➤  Переслать      │ |
| │ ...               │ |
| │ 🗑  Удалить (red) │ |  (только свои)
| └──────────────────┘ |
|         24dp         |
+----------------------+
```

- Меню всегда у нижнего края экрана. Бабл вверху виден сквозь dim, но не подсвечивается дополнительно и не двигается.
- Анимация показа: `fadeIn(220ms) + slideInVertically(initialOffsetY = it/4)`. Закрытие — `fadeOut(160ms) + slideOutVertically(targetOffsetY = it/4)`.
- Системный back закрывает overlay (не чат).
- Тап вне карточек (на dim) закрывает overlay.

**Состав ReactionsPicker:**
- 5 эмодзи: `👌 👍 🎉 🥳 ❤️` (из `ReactionsViewPreviewScreen` gallery — это и есть «5 реакций с компоненты»).
- 6-й элемент — `ButtonView`, `Size.S`, `filled=true`, `colorScheme = brand.secondaryButtonColorScheme(isDark)`. Цвет иконки переопределяется через `ButtonColorScheme.iconContentColor = ContextCompat.getColor(ctx, R.color.ds_basic_55)`. Иконка — `plus` (имя сверять с `app/src/main/assets/icons/`).
- Тап по эмодзи → `onReactionTap(emoji)` → меню закрывается + reaction toggle на сообщении.
- Тап по «+» → `onAddReactionTap()` → меню закрывается (no-op в этой версии).

**Состав MenuItemsCard:**

Каждый пункт — Row `[icon 24dp][8dp gap][label]` высота ~48dp, без разделителей, фон карточки — `DSColors.backgroundSheet(ctx)`, скругление ~16dp.

Чужое сообщение (7 пунктов сверху вниз):

| # | Label | Icon | Действие |
|---|---|---|---|
| 1 | Ответить | reply | dismiss |
| 2 | Переслать | forward | dismiss |
| 3 | Закрепить | pin | dismiss |
| 4 | Копировать | copy | ClipboardManager.copyText(...) (для Text/Media с текстом); иначе dismiss |
| 5 | Добавить метку | tag | dismiss |
| 6 | В сохранённое | bookmark | dismiss |
| 7 | Выбрать | select | dismiss |

Своё сообщение (9 пунктов): добавляются **Редактировать** (позиция 2, icon `edit`, действие — dismiss) и **Удалить** (последний, icon `trash`, `destructive=true` → цвет `DSColors.danger(ctx)`, действие — `repository.deleteMessage(chatId, messageId)`).

Имена иконок — рабочие гипотезы. Перед имплементацией сверить с реально доступными в `app/src/main/assets/icons/` (синканое из `icons-library`). Если имя не совпадает — подобрать ближайшее и оставить inline `// TODO icon name — sync with components`.

## Архитектура

### Размещение файлов

```
:core:model
  ReactionStack.kt         [new]  — data class (emoji, count, isMine)
  Message.kt               [mod]  — abstract val reactions, проброс в каждый подтип

:core:data
  MessengerRepository.kt   [mod]  — toggleReaction, deleteMessage
  MockRepositoryImpl.kt    [mod]  — impl

:core:ui/contextmenu/      [new tmp dir, c README/комментарием "переедет в :components"]
  MessageContextMenu.kt    [new]  — overlay-Composable, принимает state + lambdas
  ReactionsPicker.kt       [new]  — row эмодзи + ButtonView «+»
  MenuItemsCard.kt         [new]  — column пунктов с иконкой+label
  MessageMenuModel.kt      [new]  — enum MenuItem + forMessage(isMine), data class ContextMenuState

:core:ui/hosts/MessageList.kt    [mod]
  — параметры onBubbleTap, onReactionTap
  — Box(clickable) обёртка вокруг каждого item'а
  — ReactionsView под бабблом, если msg.reactions.isNotEmpty()

:feature:chatdetail
  ChatDetailViewModel.kt   [mod]  — StateFlow<ContextMenuState?> + openContextMenu / dismissContextMenu /
                                     toggleReaction / copyMessage / deleteMessage
  ChatDetailScreen.kt      [mod]  — Box overlay с AnimatedVisibility + MessageContextMenu
```

`:components` (android-components) **не трогаем**, по правилу из CLAUDE.md.

### Модель

```kotlin
// :core:model/ReactionStack.kt
@Serializable
data class ReactionStack(
    val emoji: String,
    val count: Int = 1,
    val isMine: Boolean = false,
)

// :core:model/Message.kt
@Serializable
sealed class Message {
    abstract val id: String
    abstract val chatId: String
    abstract val senderId: String
    abstract val timestamp: Long
    abstract val isMine: Boolean
    abstract val status: MessageStatus
    abstract val reactions: List<ReactionStack>   // default emptyList в каждом подтипе

    // Все подтипы (Text/Media/Voice/Link/CallMeet) получают override val reactions: List<ReactionStack> = emptyList()
}
```

`@Transient` для `reactions` НЕ ставим — это часть состояния сообщения и должна сериализоваться (на случай, если в моке мы захотим засеять реакции). Default `emptyList` означает, что существующие JSON-файлы без поля `reactions` продолжают парситься.

### Repository

```kotlin
// :core:data/MessengerRepository.kt
suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
suspend fun deleteMessage(chatId: String, messageId: String)
```

`MockRepositoryImpl.toggleReaction`:

1. Найти сообщение в `messageCache[chatId]`.
2. Найти `ReactionStack` с этим emoji в `msg.reactions`:
   - **Нет** → добавить `ReactionStack(emoji, count = 1, isMine = true)`.
   - **Есть, `isMine = true`** → `count -= 1`. Если `count == 0` — убрать стэк, иначе `isMine = false`.
   - **Есть, `isMine = false`** → `count += 1`, `isMine = true`.
3. Обновить сообщение в кэше через `.copy(reactions = updated)`.

`MockRepositoryImpl.deleteMessage`:

1. Удалить сообщение из `messageCache[chatId]`.
2. Пересчитать `Chat.lastMessage` по новому `messages.lastOrNull()`. Если список пуст — оставить «пустое» превью (`text = ""`, `timestamp = 0`).
3. Сортировка чат-листа подхватит изменение автоматически (через существующий `sortChats` в репозитории).

### State в VM

```kotlin
data class ContextMenuState(
    val messageId: String,
    val isMine: Boolean,
    val canCopy: Boolean,   // true для Text с непустым body или Media с непустым caption
    val copyText: String,   // body / caption / ""
)

class ChatDetailViewModel(...) : ViewModel() {
    private val _contextMenu = MutableStateFlow<ContextMenuState?>(null)
    val contextMenu: StateFlow<ContextMenuState?> = _contextMenu.asStateFlow()

    fun openContextMenu(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        _contextMenu.value = ContextMenuState(
            messageId = msg.id,
            isMine = msg.isMine,
            canCopy = canCopy(msg),
            copyText = copyTextFor(msg),
        )
    }
    fun dismissContextMenu() { _contextMenu.value = null }

    fun toggleReaction(emoji: String) {
        val st = _contextMenu.value ?: return
        viewModelScope.launch { repository.toggleReaction(chatId, st.messageId, emoji) }
        dismissContextMenu()
    }

    /** Прямой toggle минуя overlay — для тапов по уже выбранной реакции под бабблом. */
    fun toggleReactionDirect(messageId: String, emoji: String) {
        viewModelScope.launch { repository.toggleReaction(chatId, messageId, emoji) }
    }

    fun onMenuItem(item: MenuItem, context: Context) {
        val st = _contextMenu.value ?: return
        when (item) {
            MenuItem.Copy -> if (st.canCopy) copyToClipboard(context, st.copyText)
            MenuItem.Delete -> viewModelScope.launch { repository.deleteMessage(chatId, st.messageId) }
            else -> Unit
        }
        dismissContextMenu()
    }
}
```

Тап по эмодзи в нижнем рендере `ReactionsView` под бабблом (НЕ из меню, а с уже-выбранной реакции) — toggling реализуем через тот же `toggleReaction(emoji)`, который проходит **прямо из MessageList** (см. ниже сигнатуру `onReactionTap` в MessageList).

### Component split

```kotlin
// :core:ui/contextmenu/MessageMenuModel.kt
enum class MenuItem(val label: String, val iconName: String, val destructive: Boolean = false) {
    Reply("Ответить", "reply"),
    Edit("Редактировать", "edit"),
    Forward("Переслать", "forward"),
    Pin("Закрепить", "pin"),
    Copy("Копировать", "copy"),
    AddTag("Добавить метку", "tag"),
    Save("В сохранённое", "bookmark"),
    Select("Выбрать", "select"),
    Delete("Удалить", "trash", destructive = true);

    companion object {
        fun forMessage(isMine: Boolean): List<MenuItem> =
            if (isMine) listOf(Reply, Edit, Forward, Pin, Copy, AddTag, Save, Select, Delete)
            else        listOf(Reply, Forward, Pin, Copy, AddTag, Save, Select)
    }
}
```

```kotlin
// :core:ui/contextmenu/MessageContextMenu.kt
@Composable
fun MessageContextMenu(
    state: ContextMenuState,
    emojis: List<String> = listOf("👌", "👍", "🎉", "🥳", "❤️"),
    onReactionTap: (emoji: String) -> Unit,
    onAddReactionTap: () -> Unit,
    onMenuItemTap: (MenuItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Тело:

```kotlin
Box(modifier = modifier.fillMaxSize()) {
    // dim
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        ReactionsPicker(emojis, onReactionTap, onAddReactionTap)
        Spacer(Modifier.height(8.dp))
        MenuItemsCard(items = MenuItem.forMessage(state.isMine), onItemTap = onMenuItemTap)
    }
}
```

`MessageContextMenu` — **stateless**, принимает только state + lambdas. Готов к выезду в `:components` как pure-UI.

```kotlin
@Composable fun ReactionsPicker(
    emojis: List<String>,
    onReactionTap: (String) -> Unit,
    onAddReactionTap: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable fun MenuItemsCard(
    items: List<MenuItem>,
    onItemTap: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
)
```

### Wiring в ChatDetailScreen

```kotlin
val contextMenuState by viewModel.contextMenu.collectAsState()
val ctx = LocalContext.current

Box(Modifier.fillMaxSize()) {
    Column(...) {
        HeaderHost(...)
        Box { BackgroundPatternView; MessageList(
            ...,
            onBubbleTap = viewModel::openContextMenu,
            onReactionTap = { _, emoji -> viewModel.toggleReactionDirect(emoji) },
        ) }
        MessagePanelHost(...)
    }
    AnimatedVisibility(
        visible = contextMenuState != null,
        enter = fadeIn(220ms) + slideInVertically(220ms, initialOffsetY = { it / 4 }),
        exit = fadeOut(160ms) + slideOutVertically(160ms, targetOffsetY = { it / 4 }),
    ) {
        contextMenuState?.let { state ->
            MessageContextMenu(
                state = state,
                onReactionTap = viewModel::toggleReaction,
                onAddReactionTap = viewModel::dismissContextMenu,
                onMenuItemTap = { item -> viewModel.onMenuItem(item, ctx) },
                onDismiss = viewModel::dismissContextMenu,
            )
        }
    }
}
BackHandler(enabled = contextMenuState != null) { viewModel.dismissContextMenu() }
```

«Тап по уже выбранной реакции под бабблом» (`onReactionTap` в MessageList) идёт мимо overlay — это прямой toggle через `viewModel.toggleReactionDirect(messageId, emoji)`. У этого метода нет state — он принимает messageId явно.

### MessageList wiring

```kotlin
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    personaByUserId: (String) -> Persona? = { null },
    onBubbleTap: (messageId: String) -> Unit = {},
    onReactionTap: (messageId: String, emoji: String) -> Unit = { _, _ -> },
)
```

В item:

```kotlin
items(reversed, key = { it.id }) { msg ->
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onBubbleTap(msg.id) },
        )
        .padding(top = topPadding),
    ) {
        when (msg) { ... AndroidView(...) ... }
        if (msg.reactions.isNotEmpty()) {
            AndroidView(
                factory = { ReactionsView(it) },
                update = { it.configure(
                    reactions = msg.reactions.map { r -> ReactionsView.Stack(r.emoji, r.count, r.isMine) },
                    showAddButton = false,
                    colorScheme = brand.reactionsColorScheme(isDark),
                    onReactionClick = { idx -> onReactionTap(msg.id, msg.reactions[idx].emoji) },
                ) },
            )
        }
    }
}
```

Существующий `padding(top = topPadding)` переезжает с AndroidView на внешний Column-обёртку, чтобы тап-зона тоже включала верхний gap (необязательно — но по UX логичнее).

## Acceptance criteria

1. Тап по любому баблу (свой/чужой, Text/Media/Voice/Link/CallMeet) → появляется overlay с reactions + menu.
2. Чужое — 7 пунктов: Ответить, Переслать, Закрепить, Копировать, Добавить метку, В сохранённое, Выбрать. Своё — 9: добавляются Редактировать (2-й) и Удалить (последний, красным).
3. Тап по эмодзи в верхнем ряду → меню закрывается, под бабблом появляется `ReactionsView.Stack(emoji, 1, isMine=true)`. Повторный тап на ту же реакцию из меню — `count → 0`, стэк исчезает.
4. «+» в верхнем ряду — no-op + меню закрывается.
5. Копировать (для Text с непустым body или Media с непустым caption) — текст в `ClipboardManager`. Для Voice/Link/CallMeet — пункт отображается, но action — no-op.
6. Удалить — сообщение исчезает из ленты; `Chat.lastMessage` recompute'ится; чат-лист пересортируется через существующий `sortChats`.
7. Остальные пункты — закрывают меню без побочных действий.
8. Dim 50%, gap 8dp между блоком реакций и меню, оба прижаты к низу + 16dp горизонтального padding и 24dp снизу.
9. Системный back закрывает меню (не чат). Тап вне карточек на dim — тоже.
10. Анимация — fade+slide-up (~220ms) при показе, чуть быстрее (~160ms) на закрытии.
11. Существующий авто-скролл к новому сообщению и сортировка чат-листа после `sendText`/`sendMedia` продолжают работать после удаления.

## Тесты

- `core/data/src/test/.../ReactionsTest.kt` — unit-тесты `MockRepositoryImpl.toggleReaction` на трёх сценариях (нет / mine / not-mine). Опционально — тест на `deleteMessage` и recompute `lastMessage`.
- UI без Compose-test rig не покрываем — проверка глазами на устройстве по acceptance criteria.

## Риски

- **Имена иконок** в `icons-library` могут отличаться от `reply/edit/forward/...` — нужна сверка перед имплементацией. Mitigation: проверить `app/src/main/assets/icons/` после prebuild-sync и подобрать ближайшее имя с inline TODO.
- **Тап на play-кнопке MediaBubble** consume'тся и меню не открывается. Acceptable для прототипа; зафиксировано в «out of scope».
- **DSColors API не выводит `ds_basic_55`** — обращаемся через `ContextCompat.getColor(ctx, R.color.ds_basic_55)` (XML-ресурс существует). Это допустимо: ресурс — часть DS API, просто Kotlin-аксессор отсутствует.
- **JSON-совместимость**: добавление `abstract val reactions` в `Message` с default `emptyList` в каждом подтипе — обратно совместимо с существующими `messages/<chatId>.json` (поле опционально). Проверить: парсинг старых файлов в `:core:data` unit-тестах остаётся зелёным.
