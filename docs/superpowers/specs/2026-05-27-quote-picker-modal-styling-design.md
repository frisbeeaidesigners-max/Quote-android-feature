# Quote Picker — V2 Modal styling — design

## Цель

Стилизовать V2 (Modal) вариант quote-picker'а под Figma-макеты:
- https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=150-303106 (State INITIAL, 3 пункта)
- https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=150-292855 (State SELECTING, 2 пункта)

Сейчас V2 Modal — базовый Material3 `Dialog + Card` с `BasicTextField`. Заменяем на полноценный modal:
- Full-screen Dialog с hardware-blur фона
- Preview-блок с pattern-фоном и точным рендером цитируемого бабла
- Кастомное «context menu» (250×{84|124}, iOS-like Quick Actions) с FSM из двух состояний и slide-анимацией

V3 (Sheet) и V4 (FullScreen) **в этой итерации не трогаем** — `QuotePickerContent.kt` остаётся для них.

## Scope

**In:**
- Полная переписка `QuotePickerModal.kt` под Figma 1:1.
- FSM меню: INITIAL (3 пункта) ↔ SELECTING (2 пункта) с slide-анимацией; для empty-text баблов — INITIAL-MINIMAL (2 пункта, SELECTING недостижим).
- Compose mini-bubble в preview: Text и Media (с caption и без). Selection через `BasicTextField(readOnly=true)` с программным `TextRange`-управлением.
- Hardware blur фона (API 31+) + dim-fallback (API 26-30).
- Поддержка MY-сообщений (бабл справа, menu прижат к правому краю preview).
- Расширение колбэков picker'а: `onCancelReply` (новый) — отдельно от `onDismiss`.
- VM-метод `cancelReply()` — сбрасывает `_replyTo` + закрывает picker.

**Out (отдельные итерации):**
- V3 Sheet и V4 FullScreen стилизация.
- Quote от Voice/Link/CallMeet (контекст-меню для них Reply пока не открывает — см. `ChatDetailViewModel.openContextMenu`). Архитектурно empty-text path готов под них.
- Анимация открытия/закрытия самого Modal'а (полагаемся на default Dialog).
- Тесты на UI Modal'а (проект-прототип, ручной QA).

## FSM меню

```
            modal open (quotableText.isNotEmpty)
                       ↓
            ┌─────────────────────────┐
            │   INITIAL (3 items)     │ selection = empty
            │ · Выбрать фрагмент   ───┼──┐
            │ · Применить изменения   │  │ slide right
            │ · Отменить ответ        │  │
            └─────────────────────────┘  │
                       ↑                 ↓
                       │     ┌───────────────────────┐
                       │     │ SELECTING (2 items)   │ selection = (0..len)
            slide left │     │ · Назад               │ handles enabled
                       │     │ · Цитировать фрагмент │
                       └─────│                       │
                             └───────────────────────┘

            modal open (quotableText.isEmpty)
                       ↓
            ┌──────────────────────────┐
            │ INITIAL-MINIMAL (2 items)│ SELECTING недостижим
            │ · Применить изменения    │
            │ · Отменить ответ         │
            └──────────────────────────┘
```

**Переходы и эффекты:**

| State | Action | New state | Effect |
|---|---|---|---|
| INITIAL | «Выбрать фрагмент» | SELECTING | `selection = TextRange(0, len)`; `focusRequester.requestFocus()` |
| INITIAL | «Применить изменения» | (close) | `onConfirm(0, 0)` — empty range → simple reply без quote |
| INITIAL | «Отменить ответ» | (close) | `onCancelReply()` — VM clears `_replyTo` + `_quotePickerVisible = false` |
| INITIAL | system back / tap-outside | (close) | `onDismiss()` — reply context unchanged |
| SELECTING | «Назад» | INITIAL | `selection = TextRange.Zero`; defocus |
| SELECTING | «Цитировать фрагмент» | (close) | `onConfirm(sel.start, sel.end)` — quote applied |
| SELECTING | system back / tap-outside | INITIAL | same as «Назад» (clear selection, no close) |
| INITIAL-MINIMAL | «Применить изменения» | (close) | `onConfirm(0, 0)` — simple reply |
| INITIAL-MINIMAL | «Отменить ответ» | (close) | `onCancelReply()` |
| INITIAL-MINIMAL | system back / tap-outside | (close) | `onDismiss()` |

Классификация: `quotableText = (message as? Text)?.body ?: (message as? Media)?.caption ?: ""`. Empty ⇒ INITIAL-MINIMAL.

## Архитектура

### Файлы

```
feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/
├── QuotePickerOverlay.kt       — расширяется сигнатура (onCancelReply, message-payload)
├── QuotePickerModal.kt          — переписывается полностью (~300 строк)
├── QuotePickerContent.kt        — БЕЗ ИЗМЕНЕНИЙ (используется V3/V4)
├── QuotePickerSheet.kt          — без изменений
└── QuotePickerFullScreen.kt     — без изменений

feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/
└── ChatDetailViewModel.kt       — добавить cancelReply(), pickerMessage state
```

### Сигнатура picker'а

```kotlin
@Composable
fun QuotePickerOverlay(
    message: Message,                       // NEW: для рендера preview-бабла
    senderPersona: Persona?,                // NEW: name + gradient + avatar
    senderAvatar: Bitmap?,                  // NEW: from BitmapCache
    isMine: Boolean,                        // NEW: MY vs SOMEONE раскладка
    initialStart: Int,                      // как сейчас
    initialEnd: Int,                        // как сейчас
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,              // NEW
)
```

V3/V4 не используют новые параметры — для них передаём, но игнорируем в их Composable'ах. Если нужно — позже сделаем `QuotePickerContent` совместимым с message-payload.

`fullText` извлекается внутри: `message.body | message.caption | ""`.

### Композиция Modal

```
QuotePickerModal
└── Dialog(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)
    ├── SideEffect: window.blurBehindRadius + FLAG_BLUR_BEHIND (API 31+)
    │                window.setDimAmount(0.2f / 0.4f fallback)
    └── QuoteModalContent (Column, fillMaxSize, padding top=44dp)
        ├── QuotePreview (Box, w=351dp h=520dp rounded=24 align=if(isMine) End else Start)
        │   ├── PatternBackground (Image, fillMaxSize, clipped by rounded-24)
        │   ├── BubbleScrollContainer (verticalScroll, scrolled to maxValue)
        │   │   └── QuoteBubblePreview (isMine, message, persona, avatar, tfvState)
        │   └── QuotePreviewHeader (align=TopCenter, white bg, title + subtitle)
        ├── Spacer(8dp)
        └── QuoteMenu (w=250dp align=if(isMine) End else CenterHorizontally)
            └── AnimatedContent(menuState, slide-horizontal + SizeTransform)
                ├── INITIAL items
                ├── SELECTING items
                └── INITIAL-MINIMAL items
```

## UI спецификация

### Frame и safe area

- Modal — full-screen Dialog. Контент рисуется внутри Compose-`Column` с `Modifier.fillMaxSize().padding(top = 44.dp)` (top inset, эквивалент Figma y=44 в 812-фрейме).
- Горизонтальный inset preview = 12dp от краёв экрана; max-width preview = 351dp (если экран шире — preview центрируется, ширина = 351 фиксированно).

### Preview-блок (351×520, rounded 24)

```
┌─[rounded 24]──────────────────────────────────┐
│ ╔═══════════════════════════════════════════╗ │ ← absolute top-0
│ ║          Ответ на цитату                  ║ │   bg = surface01
│ ║   Вы можете процитировать фрагмент …      ║ │   py=12 px=16
│ ╚═══════════════════════════════════════════╝ │   centered text
│ [pattern bg, fills preview, clipped]          │
│                                               │
│                                               │
│  Verticalscroll(init=maxValue, align bottom)  │
│                                               │
│  ┌──┐ ┌──────────────────────────────────┐    │ ← SOMEONE
│  │AV│ │ Алексей Гурин                    │    │   pl=40 pr=32 gap=8
│  │  │ │ Проверьте ещё НДС в последнем…   │    │   bubble max-w=287
│  └──┘ │                              09:21│    │   timestamp absolute b-4 r-4
│       └──────────────────────────────────┘    │
└───────────────────────────────────────────────┘
                            ↑ внутреннее padding 8dp по всем сторонам
```

**Header:**
- Position: `Modifier.align(TopCenter)`, full-width, **поверх** scroll-контейнера (z-order: scroll → header).
- Background: `appSurface01(isDark)`.
- Padding: vertical 12dp, horizontal 16dp.
- Title: «Ответ на цитату» — Roboto Medium 16/22, color `appBasic(isDark, 0.9)`, text-align center.
- Subtitle: «Вы можете процитировать фрагмент сообщения» — Roboto Regular 14/20, color `appBasic(isDark, 0.5)`, text-align center.

**Pattern background:**
- Реюзим существующий механизм `BackgroundPatternView` (используется в чате).
- Brand-aware: тот же паттерн, что в текущем чате. Передаём через AndroidView wrapper или Compose-equivalent.
- Поверх pattern — `Color(0x66000000)` overlay? **Нет**, в Figma бэкграунд = только `background/message-screen` + pattern image, без доп. tint.

**Scroll-контейнер:**
- `Modifier.verticalScroll(state)`.
- `LaunchedEffect(message.id) { state.scrollTo(state.maxValue) }` — initial = bottom.
- Padding снизу = 8dp (preview's internal padding); сверху = высота header (рассчитать через `onSizeChanged` или фиксированная 60dp = 12+22+20+12).

### QuoteBubblePreview

**Общие правила:**
- Avatar и sender name показываются ВСЕГДА (даже если в исходном чате бабл сгруппирован). Per твоего уточнения.
- Per-corner радиусы: rounded 14 со всех углов (single bubble, no grouping).
- MY/SOMEONE отличия:
  - SOMEONE: bubble слева, avatar (32×32) при нём, bg = `brand.someoneMessageBubble(isDark)`, sender name color = persona-gradient accent.
  - MY: bubble справа, без avatar (как в чате), bg = `brand.myMessageBubble(isDark)` (если такой helper есть в DSBrand, иначе brand accent), sender name НЕ показываем (это твоё сообщение).

**Text-бабл (body NotEmpty):**
```
Row (pl=40 pr=32 gap=8 items.End) for SOMEONE
Row (pl=32 pr=40 gap=8 items.End reverse) for MY
├── (SOMEONE only) Avatar Box(32×32 circle)
└── Bubble Box(maxW=287 rounded=14 bg=someoneOrMyColor overflow.clip)
    └── Column
        ├── (SOMEONE only) Header (pt=8 px=12): sender name, Roboto Medium 16/22, color=senderAccent
        ├── Content (pt=8 pb=8 px=12, maxW=287):
        │   └── BasicTextField(value=tfv, readOnly=true, textStyle=15/20 Regular basic-90)
        └── Status (Box align=BottomEnd, padding(4,4)):
            └── pill: min-h=24 rounded=50 px=8 py=4
                └── Text: timestamp HH:mm, Roboto Regular 12/14 color=basic-50
```

**Media-бабл:**
```
Bubble Box(maxW=287 rounded=14 bg overflow.clip)
└── Column
    ├── MediaGrid (Compose Image / grid-of-Images): aspect-ratio = от attachments
    │       — если 1 image: aspect ratio оригинала
    │       — если 2-4 images: 2×2 grid (как в MediaBubbleView)
    │       — берём thumbnails из MediaAttachment.thumbnail (cast Any? → Bitmap)
    ├── (caption NotEmpty only) Header + Content + Status — как в Text-бабле
    └── (caption Empty) Status overlay поверх media — Roboto 12/14 basic-50 + white shadow для читаемости (или просто без него)
```

**Caption Empty case:**
- MediaGrid рендерится без текстового блока.
- В preview-FSM устанавливается INITIAL-MINIMAL (2 items).
- BasicTextField не создаётся → нет селекции.

### Quote menu (250×{84|124}, rounded 14)

```
Container Box(w=250dp rounded=14 bg=appSurface02(isDark) overflow.clip)
└── Compose backdrop-blur (Modifier.blur(27.dp)) — wait: blur у contents, не background
    └── Column(items.Start)
        ├── Item 1: Row(px=16 py=8, fillMaxWidth, items.Center, justify=spaceBetween)
        │       ├── Text(label, Roboto 15/20, color=labelColor, maxLines=1, w=180)
        │       └── Icon(24×24, tint=labelColor)
        ├── Divider 0.5dp color=appBasic(isDark, 0.08)
        ├── Item 2 ...
        ├── (Divider)
        └── Item N
```

**Notes:**
- `Modifier.blur(27.dp)` применяется к **фону** (берёт пиксели снизу). Для этого нужно либо нативный backdrop-blur Box (`Modifier.hazeChild(...)` если есть библиотека) либо просто **не применять** blur — у нас уже есть hardware blur всего фона на уровне Window. Без библиотек делаем без backdrop-blur **на самом меню** — фон под меню всё равно blured через FLAG_BLUR_BEHIND.
- Если в финальном QA визуально не хватит — добавим `Modifier.background(appSurface02(isDark).copy(alpha = 0.85f))` для эффекта прозрачности.

**Item label colors:**
- Normal: `appBasic(isDark, 0.9)` (basic-90)
- Danger («Отменить ответ»): `Color(0xFFE06141)` (hardcode из Figma `system/danger-default`)

**Icon tint:** = labelColor.

**Click-area:** весь Row кликабельный (`Modifier.clickable {...}`), ripple — стандартный Material indication с цветом labelColor × 0.2 alpha.

### MY-vs-SOMEONE menu alignment

- SOMEONE: menu `Modifier.align(Alignment.CenterHorizontally)` (slight asymmetry из Figma x=60 на 375 — ≈ центр).
- MY: menu `Modifier.align(Alignment.End).padding(end = previewRightInset)` так, чтобы `menu.rightEdge == preview.rightEdge - 8dp` (preview's internal padding) — т.е. menu align с правым краем bubble.

### Анимация slide между состояниями menu

```kotlin
AnimatedContent(
    targetState = menuState,
    transitionSpec = {
        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
        (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * direction } +
            fadeIn(tween(120))) togetherWith
        (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it * direction } +
            fadeOut(tween(120)))
    } using SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) },
) { state -> /* items для state */ }
```

INITIAL→SELECTING: enter from right (positive direction). SELECTING→INITIAL: enter from left. INITIAL-MINIMAL — отдельный enum-вариант, не переходит куда-либо.

### Blur фона

```kotlin
val view = LocalView.current
val density = LocalDensity.current
SideEffect {
    val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
    if (Build.VERSION.SDK_INT >= 31) {
        window.attributes = window.attributes.apply {
            blurBehindRadius = with(density) { 30.dp.roundToPx() }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.setDimAmount(0.2f)
    } else {
        window.setDimAmount(0.4f)
    }
}
```

На устройствах, где `WindowManager.isCrossWindowBlurEnabled()` = false — `FLAG_BLUR_BEHIND` игнорируется, остаётся dim. Это приемлемая деградация.

## DS-token mapping

| Figma token | Project resolution |
|---|---|
| `background/message-screen` (#0F0F10) | `brand.messageScreenBackground(isDark)` |
| `background/01-base` (header bg) | `appSurface01(isDark)` — новый helper: light=`Color.White`, dark=`Color(0xFF1E1E1E)` *(значение dark — подтвердить визуально по существующим dark-экранам app)* |
| `background/02-second` (menu bg) | `appSurface02(isDark)` — новый helper: light=`Color(0xFFF5F5F5)`, dark=`Color(0xFF2A2A2A)` *(точно подтвердить)* |
| `background/someone-message` | `brand.someoneMessageBubble(isDark)` (существующий) |
| `background/my-message` (для MY bubble) | `brand.myMessageBubble(isDark)` если есть, иначе использовать существующий чат-render helper |
| `basic-colors/90%` | `appBasic(isDark, 0.9)` = `(if isDark Color.White else Color.Black).copy(alpha=0.9f)` |
| `basic-colors/50%` | `appBasic(isDark, 0.5)` |
| `basic-colors/8%` (divider) | `appBasic(isDark, 0.08)` |
| `system/danger-default` (#E06141) | `Color(0xFFE06141)` hardcode (см. memory: icons-library tokens не синкаются, делаем локально) |
| `avatarka/N` (sender name color, MY/SOMEONE accent) | `brand.senderNameColor(persona.gradientIndex, isDark)` или эквивалент через `AvatarColorScheme` (существующий) |

**Helper'ы для добавления (если их нет):**

```kotlin
// В core/ui/AppBrand.kt или новый core/ui/AppColors.kt:
fun appBasic(isDark: Boolean, alpha: Float): Color =
    (if (isDark) Color.White else Color.Black).copy(alpha = alpha)

fun appSurface01(isDark: Boolean): Color =
    if (isDark) Color(0xFF1E1E1E) else Color.White

fun appSurface02(isDark: Boolean): Color =
    if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
```

Точные dark-значения подтверждаем визуально на этапе implementation. Если в проекте уже есть аналогичные хелперы — используем их.

## Icons

Из icons-library (`app/src/main/assets/icons/`):

| State / item | Figma icon | Lookup name | Need verify |
|---|---|---|---|
| INITIAL · «Выбрать фрагмент» | `ic-quote-full` | `quote-full` (или `quote`, если в lib один вариант) | Да |
| INITIAL · «Применить изменения» | `ic-check-outline` | `check-outline` (или `check`) | Да |
| INITIAL · «Отменить ответ» | `ic-delete` | `delete` (или `trash`) | Да |
| SELECTING · «Назад» | `ic-back` | `back` (или `arrow-left`) | Да |
| SELECTING · «Цитировать фрагмент» | `ic-quote` | `quote-s` (уже добавлен для reply-block) или отдельный `quote` | Да |

На этапе implementation — `Glob` по `app/src/main/assets/icons/*.svg`, выбрать ближайший match. Если иконки нет — запросим у пользователя добавить в icons-library (с согласия).

Загрузка: `DSIcon.named(context, "<name>", tint = labelColor)`.

## Интеграционные изменения

### `ChatDetailViewModel`

```kotlin
// Существующее:
private val _quotePickerVisible = MutableStateFlow(false)
private val _replyTo = MutableStateFlow<Message?>(null)

// NEW:
private val _quotePickerMessage = MutableStateFlow<Message?>(null)
val quotePickerMessage: StateFlow<Message?> = _quotePickerMessage.asStateFlow()

fun openQuotePicker(message: Message) {
    _quotePickerMessage.value = message
    _quotePickerVisible.value = true
}

fun cancelReply() {
    _replyTo.value = null
    _quotePickerVisible.value = false
    _quotePickerMessage.value = null
}

// applyQuote(start, end) — существующее. На этапе плана проверить:
// если current behaviour уже корректно обрабатывает start==end (= не выставлять quote-offsets,
// оставить _replyTo как простой reply) — не правим. Иначе — добавляем guard:
fun applyQuote(start: Int, end: Int) {
    if (start == end) {
        // Simple reply: _replyTo сохраняется, quote offsets = null
    } else {
        // Quote reply: set quoteStart/End в _replyTo
    }
    _quotePickerVisible.value = false
    _quotePickerMessage.value = null
}
```

### `ChatDetailScreen`

```kotlin
val pickerVisible by viewModel.quotePickerVisible.collectAsState()
val pickerMessage by viewModel.quotePickerMessage.collectAsState()
// ... existing setup ...
if (pickerVisible && pickerMessage != null) {
    val msg = pickerMessage!!
    val persona = remember(msg.senderId) { repo.getPersona(msg.senderId) }
    val avatar = LocalBitmapCache.current.bitmapFor(persona?.avatarAsset)  // existing API
    val isMine = msg.senderId == currentUser.id
    val fullText = when (msg) {
        is Message.Text -> msg.body
        is Message.Media -> msg.caption ?: ""
        else -> ""
    }
    QuotePickerOverlay(
        message = msg,
        senderPersona = persona,
        senderAvatar = avatar,
        isMine = isMine,
        initialStart = 0,
        initialEnd = 0,
        onConfirm = { s, e -> viewModel.applyQuote(s, e) },
        onDismiss = { viewModel.cancelQuotePicker() },
        onCancelReply = { viewModel.cancelReply() },
    )
}
```

V3/V4 (Sheet/FullScreen) тоже получают новые параметры, но используют только `fullText` внутри `QuotePickerContent` (legacy behaviour). При необходимости — стилизуем их позже.

## Edge cases

1. **Длинное сообщение overflow preview** — `verticalScroll`, init scroll = `maxValue`. User может пролистать выше. Header остаётся sticky сверху (overlay).
2. **Media + caption Empty** — INITIAL-MINIMAL (2 пункта), нет BasicTextField, slide-анимация отсутствует.
3. **Voice/Link/CallMeet** — out of scope (context menu Reply для них не открывается). Архитектурно INITIAL-MINIMAL применим, когда добавим.
4. **Persona отсутствует** (бот или удалённый user) — sender name = `Persona.displayName` или fallback на `User.displayName`; avatar — initials с gradient (как в чате).
5. **Rotation / process death** — `MenuState` и `TextFieldValue.selection` через `rememberSaveable` (TextFieldValue.Saver есть из коробки).
6. **API < 31** — нет hardware blur, только dim 0.4 (чуть плотнее). Визуально приемлемо.
7. **MY-сообщение** — bubble справа, menu сдвинут к правому краю preview. Реализуется через `horizontalAlignment` в Column + asymmetric padding'и.
8. **Tap по тексту в INITIAL** — НЕ должен запускать селекцию. Решается тем, что BasicTextField получает фокус только при transition INITIAL→SELECTING. До этого — bubble визуально присутствует, но текст не интерактивен (можно дополнительно `pointerInput { detectTapGestures {} }` consume'ить тапы для надёжности).
9. **Empty `fullText` после фильтрации** — picker всё равно открывается с INITIAL-MINIMAL. Не блокируем.

## Тесты

- **Unit-тесты:** не требуются — UI-стилизация без новой бизнес-логики. Существующие тесты на `QuoteRangeMatch`, `QuoteSnapshot` остаются актуальными. Если в `applyQuote` понадобится guard на `start==end` — добавляется простой test-case в `SendMessageTest` (или соседнем).
- **Ручной QA через dev-toggle:** в Calls-табе переключаем на MODAL → проверяем:
  - Text-бабл от другого user'а → 3 пункта меню → выбор фрагмента → quote
  - Text-бабл от меня (MY) → menu справа, bubble справа
  - Media с caption → 3 пункта меню → выбор фрагмента → quote
  - Media без caption → 2 пункта меню → simple reply
  - «Отменить ответ» → reply context сброшен
  - System back из SELECTING → возврат в INITIAL без закрытия
  - System back из INITIAL → закрытие picker'а, reply сохранён
  - API 31+: визуально blur фона
  - API 26-30: визуально только dim (приемлемо)

## Что НЕ делаем в этой итерации (явно)

- Стилизацию V3 (Sheet) и V4 (FullScreen) — отдельные итерации с собственными Figma-макетами.
- Изменения в `:components` (BubblesView, MediaBubbleView) — preview рендерится Compose-only, AndroidView над `:components` НЕ используется (см. brainstorming выбор B1). Это позволяет обойтись без правок в соседний репо.
- Анимацию открытия/закрытия Modal'а — используем default Dialog behaviour.

## Связанные документы

- `docs/superpowers/specs/2026-05-26-quote-reply-design.md` — общий дизайн quote-reply (4 варианта picker'а).
- `docs/superpowers/plans/2026-05-26-quote-reply-implementation.md` — план общей реализации.
- `MessageBubblesFix.md` — план миграции TextView → non-editable EditText (не выполнен, для этой задачи не требуется).
