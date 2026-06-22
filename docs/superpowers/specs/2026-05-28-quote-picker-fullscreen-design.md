# V4 FullScreen Quote Picker — Design

**Дата:** 2026-05-28
**Ветка:** `feature/quote-reply`
**Variant:** `QuoteVariant.FULLSCREEN`

## Цель

Стилизовать V4 (FullScreen) вариант quote-picker'а под Figma-макеты ([INITIAL](https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=161-300302), [SELECTING](https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=161-300321)). Логика, FSM и controller-integration переиспользуются из V2 Modal — меняется только раскладка и dialog properties.

## Что есть сейчас

`QuotePickerFullScreen.kt` — placeholder: `Dialog(usePlatformDefaultWidth=false)` → `Surface` → `Column { TopAppBar { Close + Title } + QuotePickerContent { BasicTextField(readOnly) } }`. Без брендирования, без preview бабла, без FSM меню. Signature принимает только `(fullText, initialStart, initialEnd, onConfirm, onDismiss)` — отсутствуют `message`, `senderPersona`, `senderAvatar`, `isMine`, `onCancelReply`.

## Layout

Структура одинакова для всех состояний FSM. Меняются: title (динамичный), top «Применить» (visible/hidden), список menu items.

```
┌────────────────────────────────────────┐
│ Status bar (system, не оверлэйим)      │
├────────────────────────────────────────┤
│ Header (44dp)                          │
│ ⟨  Ответ на сообщение         Применить│
├────────────────────────────────────────┤
│ Subtitle (44dp)                        │
│ Вы можете процитировать фрагмент…      │
├────────────────────────────────────────┤
│ Preview area (weight 1f)               │
│ ┌──────── pattern background ────────┐ │
│ │                                    │ │
│ │                                    │ │
│ │              ┌─ bubble ─┐          │ │
│ │              │  …       │          │ │
│ │              └──────────┘          │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ ⩘  Выбрать фрагмент                    │
│ ✓  Применить изменения                 │
│ 🗑  Отменить ответ           (danger)  │
│ navigationBars padding                 │
└────────────────────────────────────────┘
```

### 1. Header (44dp)

- **Left** ⟨ back-icon (`back.svg`), 36×36 `IconButton`, padding-start 8dp.
  - `onClick = onDismiss` **во всех состояниях** (FSM-back реализован «Назад» в bottom-меню в SELECTING).
- **Center-left**: title text. Roboto Medium 17/24, `appBasic(0.9)`.
  - Динамика по `menuState`:
    - `INITIAL` / `INITIAL_MINIMAL` → «Ответ на сообщение».
    - `INITIAL_WITH_QUOTE` / `SELECTING` → «Ответ на цитату».
- **Right**: текстовая кнопка «Применить» (accent), 118×40, Roboto Medium 17/24, `brand.accentColor(isDark)`.
  - **Visible** в `INITIAL` / `INITIAL_WITH_QUOTE` / `INITIAL_MINIMAL`.
  - **Hidden** в `SELECTING`.
  - `onClick = onConfirm(currentSelectionRange)` — **тот же handler**, что у bottom «Применить изменения».
  - Дублирование намеренное: top-Apply нужен для one-handed reachability.

### 2. Subtitle (44dp)

Статичный текст «Вы можете процитировать фрагмент сообщения». Roboto Regular 14/16, `appBasic(0.5)`, padding-horizontal 16dp, vertical-center.

### 3. Preview area (`Modifier.weight(1f)`, fill remaining height)

- **Background**: `BackgroundPatternView` AndroidView, full-width × full-height preview area. `brand.backgroundPatternName(1)` + `brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)` (как в V2).
- **Bubble**: переиспользуется existing `QuoteBubblePreview` — signature НЕ меняется, передаём message/persona/avatar/isMine/tvRef/onQuoteFromActionMode/selectAllRef/selectionRef/initialStart/initialEnd. Controller, restore initial range, FSM-callbacks работают без изменений.
- **Scroll**: внутренний `verticalScroll` (auto-scroll-to-bottom по `LaunchedEffect(message.id)` через `snapshotFlow { maxValue }.first { > 0 }` → `scrollTo` — pattern из V2).
- **Bubble padding**: horizontal 8dp, vertical inset 8dp (без spacer'а под sticky-header — header в V4 находится ВНЕ preview area).

### 4. Menu rows (bottom, `Modifier.navigationBarsPadding()`)

Каждый item — full-width row:
- Высота 48dp.
- Padding-start 24dp.
- Icon 24×24, через `DSIcon.named(context, iconName, ...)` (синканный SVG).
- Spacing icon ↔ text 24dp.
- Text 17/24 Roboto Regular.
- Color: `appBasic(0.9)` (regular), `#E06141` (danger).
- Ripple `appBasic(0.08)` на всю строку (`Modifier.clickable(indication = rememberRipple(...))`).

**FSM menu config:**

| State | Items |
|-------|-------|
| `INITIAL` (3) | `quote-full` / «Выбрать фрагмент» → enter SELECTING + `selectAllRef.invoke()` |
| | `check-outline` / «Применить изменения» → `onConfirm(currentSelection)` |
| | `delete` / «Отменить ответ» (danger) → `onCancelReply()` |
| `INITIAL_WITH_QUOTE` (3) | `cancel-quote` / «Снять выделение» → clear controller selection + state = INITIAL |
| | `check-outline` / «Применить изменения» → `onConfirm(currentSelection)` |
| | `delete` / «Отменить ответ» (danger) → `onCancelReply()` |
| `SELECTING` (2) | `back` / «Назад» → clear controller selection + state = INITIAL |
| | `quote` / «Цитировать фрагмент» → `onConfirm(currentSelection)` |
| `INITIAL_MINIMAL` (2) | `check-outline` / «Применить изменения» → `onConfirm(0, 0)` (no quote) |
| | `delete` / «Отменить ответ» (danger) → `onCancelReply()` |

**Анимация переключения state'а:** `AnimatedContent(menuState) + SizeTransform` (slide+fade), как в V2 menu. Не reuse `QuoteMenu.kt` — у V2 floating-box, у V4 full-width rows.

## Dialog properties

- `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))`.
- Background — `appSurface01(isDark)` (Surface на корневом Column).
- **Без blur** — full-screen перекрывает весь UI; dim не нужен.
- **IME**:
  - `FLAG_ALT_FOCUSABLE_IM` — Dialog получает focus, но IME остаётся прикреплён к окну под ним (MessagePanel EditText).
  - `SOFT_INPUT_STATE_UNCHANGED` — не закрываем/не открываем IME при mount/dismiss.
  - `SOFT_INPUT_ADJUST_RESIZE` — окно Dialog ресайзится под IME, menu rows поднимаются выше.

Application via `SideEffect { val window = (LocalView.current.parent as? DialogWindowProvider)?.window }` (паттерн из V2 `QuotePickerModal.kt`).

## Файлы

### `QuotePickerFullScreen.kt` — обновить signature + IME logic

```kotlin
@Composable
fun QuotePickerFullScreen(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
)
```

Body: `Dialog { SideEffect { IME flags } + QuoteFullScreenContent(...) }`.

### `QuoteFullScreenContent.kt` — NEW

`Column(fillMaxSize, background = appSurface01)`:
1. `FullScreenHeader(menuState, onDismiss, onApply)` — header 44dp.
2. `SubtitleRow()` — subtitle 44dp.
3. `Box(weight(1f)) { BackgroundPatternView + ScrollableBubbleColumn }`.
4. `FullScreenMenuRows(menuState, …callbacks…)` — menu animation block.

State (внутри content composable):
- `var menuState by rememberSaveable { mutableStateOf(initialState) }` — initial по существующей логике: `!hasSelectableText` → MINIMAL, `initialStart < initialEnd` → WITH_QUOTE, иначе INITIAL.
- `val tvRef = remember { mutableStateOf<TextView?>(null) }`.
- `val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }`.
- `val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }`.

### `QuotePickerOverlay.kt` — обновить FULLSCREEN ветку

Передать `message`, `senderPersona`, `senderAvatar`, `isMine`, `onCancelReply` в `QuotePickerFullScreen(...)`. Параметры уже есть в caller'е (тот же путь, что MODAL).

### `QuoteMenu.kt`, `QuoteModalContent.kt`, `QuoteBubblePreview.kt`, `QuoteMenuState` — не меняем

`QuoteBubblePreview` — переиспользуется как есть. `QuoteMenu` остаётся для V2 (floating-box). V4 имеет inline `FullScreenMenuRows` composable внутри `QuoteFullScreenContent.kt` (или отдельный файл — на усмотрение реализации).

## Что НЕ scope

- V3 (Sheet) — остаётся placeholder'ом до отдельной задачи.
- Шеринг menu logic между V2 и V4 (параметризация `QuoteMenu` стилем) — было рассмотрено и отвергнуто: layout слишком разный (floating-box 250×N vs full-width rows), параметризация раздула бы общий компонент.
- Изменения controller'а, FSM-логики, или ViewModel.

## Acceptance criteria

1. Открыть V4 (через dev-toggle в Calls): full-screen окно с header / subtitle / preview / 3 menu items («Выбрать фрагмент» / «Применить изменения» / «Отменить ответ»).
2. «Выбрать фрагмент» → selection (selectAll) с handles + floating Copy/Quote menu (custom SelectionController). Header справа hidden, bottom menu = «Назад» / «Цитировать фрагмент».
3. «Цитировать фрагмент» → `onConfirm(currentRange)` → закрытие picker'а, reply содержит выбранный фрагмент.
4. Re-open picker'а с existing quote → `INITIAL_WITH_QUOTE`: подсветка ранее выбранного фрагмента в баббле, bottom menu = «Снять выделение» / «Применить изменения» / «Отменить ответ».
5. «Применить» (header right) и «Применить изменения» (bottom) делают то же действие.
6. Back-arrow (header left) — закрывает picker из любого state'а (без сохранения quote, если не было apply).
7. IME state — preserved (был открыт → остаётся, был закрыт → остаётся).
8. Brand-цвета (foxtrot/tango/sierra/kilo/love) применяются ко всем элементам: pattern, bubble, header accent, danger text.

## Связано

- [V2 Modal spec](./2026-05-27-quote-picker-modal-styling-design.md) — reference styling.
- [Quote-reply design](./2026-05-26-quote-reply-design.md) — родительская задача.
- `QuoteModalContent.kt` — reference implementation для V2 (FSM, refs, scroll, IME).
