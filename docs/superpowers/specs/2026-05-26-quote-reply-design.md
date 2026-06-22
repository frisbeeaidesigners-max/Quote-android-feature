# Quote reply (партикулярное цитирование) — design

## Цель

Расширить существующий full-reply возможностью ответить на **выделенный фрагмент** сообщения (как в Telegram). Реализовать **4 UX-варианта** входа в режим выбора фрагмента, переключаемых runtime'ом в debug-сборке, чтобы выбрать финальный и смержить в `main`:

- **V1 (in-place).** Long-press на бабле → multi-select (как сейчас) + в одиночно-выбранном бабле текст становится выделяемым нативными handles. Floating ActionMode даёт «Цитировать» / «Copy».
- **V2 (swipe + tap-block).** Swipe-влево → full-reply в `MessagePanel` (как сейчас) → тап по reply-block в панели → открывается picker.
- **V3 (ctx-menu + tap-block).** Long-press через ContextMenu → «Ответить» → full-reply в панели → тап по reply-block → picker.

V2 и V3 различаются только entry-point'ом; picker — общий, с **3 визуальными стилями**: модалка / bottom-sheet / fullscreen.

Итого 4 picker-реализации (`INPLACE`, `MODAL`, `SHEET`, `FULLSCREEN`), все живут в одной debug-сборке за runtime-переключателем.

## Scope

**In:**
- Расширение `ReplyPreview` опциональными `quoteStart/quoteEnd: Int?`.
- Quote доступен для типов сообщений с текстовым блоком: **Text** и **Media** (caption). Архитектура расширяема под Voice (транскрипция) и Link (preview-текст) в будущем — но эти типы вне этой реализации.
- 4 picker-варианта + runtime-переключатель в Calls-табе.
- Tap-on-reply-block внутри бабла → scroll к оригиналу + bubble-pulse highlight.
- Edge cases (удалённый оригинал, изменённый текст) → toast.
- Юнит-тесты на сериализацию и matching-логику.

**Out:**
- Fragment-highlight (подсветка конкретного диапазона символов внутри TextView через Spannable). Offset'ы хранятся, добавление — отдельной задачей.
- Quote от Voice/Link/CallMeet (текстовых блоков в этих бабблах сейчас нет).
- Persistence dev-toggle (пересоздание процесса → default `INPLACE`).
- Анимации перехода между состояниями picker'а (modal/sheet просто появляются по дефолту фреймворка).
- UI-тесты (проект-прототип, ручной QA через dev-toggle).
- Tracking edit'ов оригинала для синхронизации offset'ов.

## Архитектура

**Подход 2: общее ядро + 4 шкуры.** Общая инфраструктура (модель, VM-state, кликабельность reply-block, маршрутизация) реализуется один раз и **остаётся в коде после выбора финального варианта**. Отличаются только 4 picker-имплементации, после выбора 3 из них удаляются вместе с enum.

```
┌─────────────────────────────────────────────────────────────┐
│                    Общее ядро (остаётся)                     │
│  • ReplyPreview.quoteStart/End                               │
│  • ChatDetailViewModel: setQuote/clearQuote/openQuotePicker  │
│  • MessagePanelHost: clickable reply-block                   │
│  • MainActivity: QuotePickerOverlay + jump-to-original       │
│  • bubble-pulse highlight                                    │
│  • icon кавычек в reply-block при quote                      │
└─────────────────────────────────────────────────────────────┘
                          │
       ┌──────────────────┼──────────────────┬─────────────┐
       ▼                  ▼                  ▼             ▼
  ┌─────────┐       ┌──────────┐       ┌──────────┐  ┌───────────┐
  │ INPLACE │       │  MODAL   │       │  SHEET   │  │ FULLSCREEN│
  │ (V1)    │       │ (V2/V3)  │       │ (V2/V3)  │  │ (V2/V3)   │
  └─────────┘       └──────────┘       └──────────┘  └───────────┘
   (в самом         (Dialog с         (ModalBottom    (Dialog
   бабле через      Card 90% ширины)  Sheet)          fillMaxSize)
   ActionMode)
```

## Data model

`:core:model/Message.kt`:

```kotlin
@Serializable
data class ReplyPreview(
    val originalId: String,
    val authorName: String,
    val text: String,              // full-reply: весь текст; quote: substring
    val quoteStart: Int? = null,   // offsets в исходном тексте оригинала
    val quoteEnd: Int? = null,
)
```

Семантика: `quoteStart != null` ⇔ это quote. Offset'ы индексируют тот же текстовый блок, который был источником:
- `Message.Text` → `text`
- `Message.Media` → `caption`

JSON: kotlinx-serialization с дефолтами `null`. Старые сообщения в `mock/messages/*.json` остаются full-reply без миграции.

**Инвариант:** если `quoteStart != null`, то `0 ≤ quoteStart < quoteEnd ≤ originalText.length` и `text == originalText.substring(quoteStart, quoteEnd)` **на момент отправки**. После этого момента оригинал может измениться — снапшот в `ReplyPreview.text` остаётся актуальным для отображения, offset'ы могут «съехать» (см. Edge cases).

## VM state

`feature:chatdetail/ChatDetailViewModel.kt`:

```kotlin
data class ReplyContext(
    val originalId: String,
    val authorName: String,
    val originalFullText: String,  // нужен для QuotePicker — selectable preview
    val previewText: String,       // что показывать в reply-block: full или substring
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
) {
    fun toSnapshot(): ReplyPreview = ReplyPreview(
        originalId = originalId,
        authorName = authorName,
        text = previewText,
        quoteStart = quoteStart,
        quoteEnd = quoteEnd,
    )
}

private val _quotePickerVisible = MutableStateFlow(false)
val quotePickerVisible: StateFlow<Boolean> = _quotePickerVisible

fun setQuote(start: Int, end: Int) { /* update reply-state */ }
fun clearQuote() { /* откатить к full-reply, previewText = originalFullText */ }

fun openQuotePicker() {
    // variant читается через AppContainer.quoteVariant: StateFlow<QuoteVariant>,
    // прокинутый в VM через конструктор (CompositionLocal в VM недоступен).
    if (quoteVariant.value == QuoteVariant.INPLACE) return
    if (_replyContext.value == null) return
    _quotePickerVisible.value = true
}
fun dismissQuotePicker() { _quotePickerVisible.value = false }

fun startQuoteInPlace(messageId: String, start: Int, end: Int) {
    startReply(messageId)         // существующий метод
    setQuote(start, end)
}
```

Mutual-exclusive guard не меняется: `startReply` обнуляет edit и selection как и раньше. Quote — внутри reply-state, отдельного взаимоисключения не требуется.

При отправке: `sendText`/`sendMedia` вызывает `_replyContext.value?.toSnapshot()` — offset'ы попадают в `ReplyPreview`.

## UI-роутинг

```kotlin
// :core:model/QuoteVariant.kt
enum class QuoteVariant { INPLACE, MODAL, SHEET, FULLSCREEN }

// :core:ui — CompositionLocal
val LocalQuoteVariant = compositionLocalOf<QuoteVariant> { QuoteVariant.INPLACE }
```

**Хранение state:** `MutableStateFlow<QuoteVariant>` в `AppContainer`, без persistence. Прокидывается в `LocalQuoteVariant` на корне `AppTheme`.

**Dev-toggle UI:** в `feature:calls` добавляется секция «Quote variant» с 4 RadioButton'ами над списком звонков. Минимально оформлено, с пометкой «dev».

## V1 — INPLACE (long-press → multi-select + текст-селекшен)

### Поток

1. Long-press на бабле → `startSelection(id)` (как сейчас). Multi-select bar появляется, бабл получает чекбокс.
2. Если `LocalQuoteVariant == INPLACE` И в selection ровно 1 элемент:
   - View-walk внутри `BubblesView`/`MediaBubbleView` находит TextView с текстом сообщения.
   - `tv.setTextIsSelectable(true)`.
   - По координатам long-press'а (передаются через `Offset` из Compose pointerInput) определяется character-offset через `tv.getOffsetForPosition(x, y)`.
   - `BreakIterator.getWordInstance()` определяет границы слова под пальцем.
   - `tv.setSelection(wordStart, wordEnd)` → handles появляются на выбранном слове.
   - Если `getOffsetForPosition` возвращает -1 (палец вне текста, типично для Media с большой картинкой и узким caption) — fallback на `selectAll()`.
3. Установлен `customSelectionActionModeCallback` с custom item «Цитировать» + дефолтные Copy/Share.
4. Юзер тянет handles → меняет selection.
5. Тап «Цитировать» → callback в MessageList → `VM.startQuoteInPlace(id, start, end)`. Multi-select сбрасывается через mutual-exclusive guard в `startReply`. MessagePanel показывает reply-block с фрагментом.
6. Тап «Copy» → стандартное копирование, ActionMode исчезает, multi-select остаётся.
7. Тап другого бабла во время text-selection → `setTextIsSelectable(false)` на старом бабле (handles исчезают), multi-select расширяется как обычно.
8. Выход из multi-select (cancel / dismiss / startReply) → `setTextIsSelectable(false)` на всех бабблах.

### View-walk: поиск TextView внутри BubblesView

```kotlin
// :core:ui/utils/BubbleTextProbe.kt
fun View.findMessageTextView(messageText: String): TextView? {
    if (this is TextView && text?.toString() == messageText) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findMessageTextView(messageText)?.let { return it }
        }
    }
    return null
}
```

**Fragility-нота:** эвристика по совпадению текста — если `:components` поменяет внутреннюю структуру (например, разобьёт текст на несколько TextView), сломается. В этом случае согласуем с pользователем правку в `:components`: добавление публичного API `BubblesView.selectableTextView(): TextView` и аналог в `MediaBubbleView`.

### Координаты long-press → character offset

В Compose long-press передаёт `Offset(x, y)` в локальных координатах pointerInput-области. Конвертация в координаты TextView:

```kotlin
val bubbleLoc = IntArray(2); bubbleView.getLocationOnScreen(bubbleLoc)
val tvLoc = IntArray(2); tv.getLocationOnScreen(tvLoc)
val tvX = (bubbleLoc[0] + offset.x) - tvLoc[0]
val tvY = (bubbleLoc[1] + offset.y) - tvLoc[1]
val charOffset = tv.getOffsetForPosition(tvX, tvY)
```

`charOffset == -1` → fallback на `selectAll()`.

### Word boundary через BreakIterator

```kotlin
fun wordBoundary(text: String, offset: Int): IntRange {
    val iter = BreakIterator.getWordInstance()
    iter.setText(text)
    val start = iter.preceding(offset + 1).let { if (it == BreakIterator.DONE) 0 else it }
    val end = iter.following(offset).let { if (it == BreakIterator.DONE) text.length else it }
    return start..end
}
```

### Custom ActionMode

```kotlin
class QuoteActionModeCallback(
    private val tv: TextView,
    private val onQuote: (start: Int, end: Int) -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_QUOTE_ID, 0, "Цитировать")
        return true
    }
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == MENU_QUOTE_ID) {
            onQuote(tv.selectionStart, tv.selectionEnd)
            mode.finish()
            return true
        }
        return false  // default Copy/Share обрабатываются системой
    }
    override fun onDestroyActionMode(mode: ActionMode) = Unit
    companion object { const val MENU_QUOTE_ID = 1 }
}
```

## V2 / V3 — Picker (MODAL / SHEET / FULLSCREEN)

### Entry-points

Оба уже работают, изменений нет:
- V2: `SwipeToReplyItem` → `VM.startReply()`
- V3: ContextMenu «Ответить» → `VM.startReply()`

Оба ставят `replyContext` с `previewText = originalFullText`, `quoteStart/End = null` (т.е. full-reply).

### Reply-block становится кликабельным

`MessagePanelHost.kt`: в конфиг `ContextBlock.Reply` добавляется `onClick: () -> Unit`. Из ChatDetailScreen передаётся:
```kotlin
ReplyDisplay(it.authorName, it.previewText, onClick = { viewModel.openQuotePicker() })
```

API `MessagePanelView.ContextBlock.Reply` уже принимает `onClick` (либо просто маршрутизуется через root-click на блок) — детали wiring'а решаются на этапе плана.

### QuotePickerOverlay (в MainActivity)

State-driven overlay рядом с ChatDetail-overlay:

```kotlin
val replyContext by viewModel.replyContext.collectAsState()
val pickerVisible by viewModel.quotePickerVisible.collectAsState()

if (replyContext != null && pickerVisible) {
    QuotePickerOverlay(
        replyContext = replyContext!!,
        onConfirm = { start, end ->
            viewModel.setQuote(start, end)
            viewModel.dismissQuotePicker()
        },
        onDismiss = { viewModel.dismissQuotePicker() },
    )
}
```

Внутри overlay диспатч по варианту:

```kotlin
when (LocalQuoteVariant.current) {
    INPLACE -> Unit  // V1 — селекшен в самом бабле
    MODAL -> QuotePickerModal(replyContext, onConfirm, onDismiss)
    SHEET -> QuotePickerSheet(replyContext, onConfirm, onDismiss)
    FULLSCREEN -> QuotePickerFullScreen(replyContext, onConfirm, onDismiss)
}
```

### Общий контент picker'а

```kotlin
@Composable
fun QuotePickerContent(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tfv by remember {
        mutableStateOf(TextFieldValue(fullText, TextRange(initialStart, initialEnd)))
    }
    Column(modifier) {
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it },   // юзер тянет handles → selection меняется
            readOnly = true,
            textStyle = DSTypography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        Row(horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Отмена") }
            Button(
                onClick = { onConfirm(tfv.selection.start, tfv.selection.end) },
                enabled = !tfv.selection.collapsed,
            ) { Text("Цитировать") }
        }
    }
}
```

`BasicTextField(readOnly=true)` сохраняет нативные selection-handles и отдаёт `TextRange` через `TextFieldValue`. Чистый Compose, без AndroidView.

При первом открытии picker'а initial selection = весь текст (если quote ещё не был выбран) либо текущий quote-диапазон (если повторное открытие).

### Три шкуры

- **`QuotePickerModal`** — `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` + `Card(Modifier.fillMaxWidth(0.9f).wrapContentHeight())`. Центрирован, dim-фон. Кнопки в нижнем правом углу карточки.
- **`QuotePickerSheet`** — Material3 `ModalBottomSheet` с `sheetState`. Контент тянется снизу. Кнопки в bottom-rоw bottom-sheet'а.
- **`QuotePickerFullScreen`** — `Dialog(usePlatformDefaultWidth = false)` + `Surface(Modifier.fillMaxSize())`. Кнопка «Отмена» в TopAppBar (slot navigationIcon как `IconButton(Close)`), кнопка «Цитировать» внизу.

## Reply-block внешний вид при quote

В `MessagePanelHost.ReplyDisplay` добавляется иконка кавычек (`DSIcon.named(context, "quote_left", ...)` или подобная) слева от текста, видимая когда `quoteStart != null`. Аналогично в reply-block внутри бабла (`replyText` параметр в `BubblesView.configure`) — но это требует либо правки `:components` (вешать иконку перед текстом), либо локального префикса: `"“${quote}”"` (символы « » или " ") в `replyText`. Договариваемся на префикс с двумя кавычками: `"«${quote}»"` — минимальное вмешательство, без правок `:components`.

## Tap-on-reply-block в бабле → jump

### Поиск контейнера reply-block

Аналогично view-walk-у для TextView: ищем TextView с текстом `replySender`, поднимаемся к родителю, вешаем `OnClickListener`:

```kotlin
fun BubblesView.findReplyContainer(replySender: String): View? {
    val senderTv = findMessageTextView(replySender) ?: return null
    return senderTv.parent as? View
}
```

**Fragility-нота:** аналогична TextView-finder'у; если `:components` сменит структуру — согласуем правку `BubblesView`.

### Действие

Клик → callback в ChatDetailScreen → две операции:

1. `lazyListState.animateScrollToItem(...)` — индекс ищется в `messages.value` по `originalId`. Если не найден — toast «Сообщение удалено», скролла не происходит.
2. Если найден, проверяем match: `original.text.substring(quoteStart, quoteEnd) == replyTo.text`. Не совпадает — toast «Цитируемый фрагмент не найден», скролл всё равно выполнен. Совпадает — `VM.requestHighlight(originalId)`.

### Bubble-pulse highlight

`VM.highlightedMessageId: StateFlow<String?>`. Set на jump, clear через `delay(1400)`. В MessageList — `AnimatedVisibility` overlay поверх бабла с `Modifier.background(brand.accent.copy(alpha = 0.2f))`, alpha анимируется к 0 за 1400ms easeOut.

Fragment-highlight (Spannable) — deferred TODO; offset'ы хранятся в модели и доступны для последующей реализации.

## Edge cases

| Сценарий | Поведение |
|----------|-----------|
| Оригинал удалён (нет в `messages.value`) | `Toast.makeText(..., "Сообщение удалено", SHORT)` |
| Оригинал найден, фрагмент не совпадает (edit) | scroll выполнен, `Toast(..., "Цитируемый фрагмент не найден", SHORT)`, без highlight |
| Оригинал найден, фрагмент совпадает | scroll + bubble-pulse |
| QuotePicker confirm с `selection.collapsed` | кнопка «Цитировать» disabled |
| MessagePanel в edit-mode → юзер таппает reply-block | невозможно: reply-block не показывается во время edit (edit и reply mutual-exclusive в существующем коде) |
| Variant=INPLACE, юзер таппает reply-block в панели | `openQuotePicker()` early-return при INPLACE — ничего не происходит |
| Long-press вне TextView (по картинке Media) | fallback на `selectAll()` caption'а |
| Выход из чата с активным reply (full или quote) | сбрасывается `replyContext`, picker dismiss — как сейчас |

## Files affected

| Слой | Файлы (новые / изменённые) |
|------|---------------------------|
| Model | `core/model/Message.kt` (+ `quoteStart/End` в ReplyPreview), `core/model/QuoteVariant.kt` (new) |
| VM | `feature/chatdetail/ChatDetailViewModel.kt` (+ ReplyContext.quote, setQuote, openQuotePicker, startQuoteInPlace, highlightedMessageId, requestHighlight) |
| AppContainer | `app/AppContainer.kt` (+ quoteVariant: MutableStateFlow), `app/MainActivity.kt` (+ LocalQuoteVariant provider, + QuotePickerOverlay, + highlight на MessageList) |
| Picker UI | `feature/chatdetail/quotepicker/QuotePickerOverlay.kt` (new), `QuotePickerContent.kt` (new), `QuotePickerModal.kt` (new), `QuotePickerSheet.kt` (new), `QuotePickerFullScreen.kt` (new) |
| V1 wiring | `core/ui/hosts/MessageList.kt` (+ word-detection, + view-walk, + ActionMode setup в update-блоке), `core/ui/utils/BubbleTextProbe.kt` (new), `core/ui/utils/QuoteActionModeCallback.kt` (new) |
| Panel | `core/ui/hosts/MessagePanelHost.kt` (+ onClick в ReplyDisplay), `feature/chatdetail/ChatDetailScreen.kt` (wiring) |
| Dev-toggle | `feature/calls/CallsScreen.kt` (+ dev-section с RadioButton'ами) |
| Tests | `core/data/src/test/.../QuoteSnapshotTest.kt` (new), `core/data/src/test/.../QuoteRangeMatchTest.kt` (new) |

## Testing

Юнит-тесты в `core/data/src/test/`:

- **`QuoteSnapshotTest`** — `ReplyContext.toSnapshot()` сериализует `quoteStart/End` корректно; full-reply (`quoteStart == null`) сериализует без offset-полей.
- **`QuoteRangeMatchTest`** — функция `matchesQuote(original: Message, snapshot: ReplyPreview)` возвращает true для точного совпадения, false для edit-кейса, false для full-reply (offsets отсутствуют → match не проверяется, считаем true).

UI/integration-тесты не добавляются — ручной QA через dev-toggle на устройстве.

## Open questions

Нет — все клиничные решения утверждены в брейнсторме:
- Модель: `quoteStart/quoteEnd: Int?` (плоские поля, не вложенный `Quote`).
- Persistence dev-toggle: нет.
- Расположение dev-toggle: Calls-таб.
- V1 long-press collision: вариант B (multi-select + handles в одиночно-выбранном бабле, drag → пока в multi-select, выход через Quote-action).
- По умолчанию выделять слово под пальцем (BreakIterator), fallback на selectAll.
- Selectable preview в picker'е: `BasicTextField(readOnly=true)`.
- Маркер quote в reply-block: иконка кавычек в панели, текстовые « » в бабле.
- Tap-to-jump: bubble-pulse сейчас, fragment-highlight (Spannable) — deferred TODO.
- Edge cases — toast'ы.
