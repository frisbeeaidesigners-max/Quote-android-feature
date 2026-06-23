# V5 Reply/Link Tabs — Design

**Дата:** 2026-06-23
**Ветка:** `feature/quote-v5` (продолжение)
**Базируется на:** `2026-06-22-quote-v5-design.md`
**Figma:** [Оформление ссылки — node 8247-643002](https://www.figma.com/design/BG1rJxwbv5FMpTKhPvEdFP/Сообщения-и-их-взаимодействие?node-id=8247-643002)

## Цель

Добавить в V5 quote-picker (fullscreen) переключатель из двух вкладок:

- **«Ответ»** — текущий V5-экран, где пользователь выбирает фрагмент цитаты или оставляет полный ответ. Поведение не меняется.
- **«Ссылка»** — новый mock-экран, демонстрирующий как итоговый ответ будет выглядеть в виде LinkBubble'а с reply-секцией. Read-only preview; единственная связь с «Ответ»-вкладкой — содержимое reply-блока внутри LinkBubble.

## Не входит в scope

- Никакая реальная функциональность стилизации ссылок. Вкладка «Ссылка» — заглушка с захардкоженными mock-данными LinkBubble.
- Popover «Общая информация / Главное сообщение» с макета на вкладке «Ссылка» — декоративный элемент Figma, не реализуется.
- A/B-toggle V4↔V5 не трогаем — он уже есть в Profile и работает.
- LinkBubble на вкладке «Ссылка» не tappable, без context-menu, без swipe-reply.

## Архитектура

### Состояние

В `QuoteV5FullScreenContent` добавляется:

```kotlin
var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Ответ, 1 = Ссылка
```

Существующее состояние (`menuState`, `popoverOpen`, `selectionRef`, `clearSelectionRef`) сохраняется. Свойство `selectedTab` участвует только в gating'е, не влияет на FSM `menuState`.

### Layout

Структура `Column` остаётся неизменной по порядку детей:

```
Column (root, fillMaxSize, appSurface01)
├── HeaderHost                              // title зависит от selectedTab
├── Row 48dp                                // description text, зависит от selectedTab
├── PreviewArea (Box, fill remaining)       // содержит и SegmentedControl, и контент вкладки
│   ├── (existing) pattern background + bubble area
│   ├── SegmentedControl (overlay, TopCenter, top=8dp)
│   └── tab content: selectable bubble | static LinkBubble
└── BottomStrip                             // зависит от selectedTab
```

Новый компонент — `SegmentedControl`, рисуется внутри PreviewArea через `Modifier.align(TopCenter).padding(top = 8.dp)` поверх pattern-фона. Ширина — по содержимому (intrinsic, ~152dp). По бокам 16dp gap (выполняется естественно — центр + intrinsic width на 375dp screen даёт ~112dp от краёв; на узких экранах при необходимости проседает безопасно).

PreviewArea вместо текущего `PreviewArea(...)`-композабла станет Box-обёрткой с двумя слоями:
1. tab-content (selectable bubble OR LinkBubble)
2. SegmentedControl поверх

### Переключение по вкладке

| Слой | Tab 0 «Ответ» | Tab 1 «Ссылка» |
|---|---|---|
| Header title | по `menuState` (текущее: «Ответ на сообщение» / «Ответ на цитату») | «Оформление ссылки» |
| Description text | «Вы можете процитировать фрагмент сообщения» | «Так будет выглядеть ваша ссылка после отправки» |
| PreviewArea content | существующий selectable bubble (PreviewArea с CustomSelectionController) | статичный `LinkBubbleView` (AndroidView) с mock-данными |
| BottomStrip | существующий — sender + reply preview + popover-icon | новый — иконка скрепки + «Прикрепленная ссылка» + URL (статика) |
| Popover FSM | активна как сейчас | не рендерится |

### LinkBubble (Tab «Ссылка»)

`AndroidView` обёртка над `LinkBubbleView` из `:components/bubbles`. Mock-данные захардкожены из Figma:

```kotlin
LinkBubbleView.configure(
    type = BubbleType.MY,
    title = "Суммаризация записи ВКС (аналог Plaud)",
    description = "Рабочее пространство для обсуждения задач и обмена ключевыми обновлениями по текущему проекту",
    url = "https://web.frisbee.live/im/2021316695",
    domain = "",
    labels = listOf("Группа"),
    time = "10:15",
    sendingState = SendingState.READ,
    previewImageBitmap = <one of media_*.jpg from BitmapCache, or null if unavailable>,
    // Reply-блок — динамика из vm-state ↓
    replySender = computedReplySender,
    replyText = computedReplyText,
    colorScheme = brand.linkBubbleColorScheme(isDark),
)
```

**Computed reply fields:**

```kotlin
val computedReplySender = if (isMine) "Вы"
    else senderPersona?.fullName?.takeIf { it.isNotEmpty() } ?: "Собеседник"

// Snapshot of the quote range — обновляется ТОЛЬКО когда menuState стабилизируется в
// INITIAL_WITH_QUOTE или сбрасывается обратно в INITIAL. Не дёргается во время SELECTING,
// иначе reply-текст в LinkBubble прыгал бы при каждом drag'е handle на Tab 0.
var snapshotRange by rememberSaveable {
    mutableStateOf(if (initialStart < initialEnd) initialStart to initialEnd else null)
}

LaunchedEffect(menuState) {
    when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE -> {
            val r = selectionRef.value?.invoke()
            if (r != null && r.first < r.last) snapshotRange = r.first to r.last
        }
        QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> {
            snapshotRange = null
        }
        QuoteMenuState.SELECTING -> Unit  // не трогаем — пользователь ещё выбирает
    }
}

val computedReplyText = snapshotRange?.let { (s, e) -> extractQuote(message, s, e) }
    ?: replyPreviewText(message)
```

Логика: snapshot обновляется только когда FSM в стабильном «выбор зафиксирован» состоянии (`INITIAL_WITH_QUOTE`). Во время `SELECTING` LinkBubble на Tab 1 не виден всё равно (пользователь на Tab 0), поэтому live-updates не нужны. На Tab 1 reply-блок читает только `snapshotRange` — независимо от того, смонтирована ли PreviewArea.

`extractQuote(message, start, end)` — helper: `(message as? Message.Text)?.body?.substring(start.coerceIn(0, len), end.coerceIn(0, len)) ?: replyPreviewText(message)`. Безопасные coerce — на случай stale snapshot после нетривиальных переходов.

### BottomStrip Variant 2 («Ссылка»)

Новый Composable `BottomStripLink()` параллельно текущему `BottomStrip()`. Структура:

```kotlin
Row (fillMaxWidth, appSurface01, drawBehind top-border 0.08 basic,
     padding start=16 end=8 top=8 bottom=4, heightIn(min=40)) {
    DsIconImage(name = "attach-link", 24dp, tint = appBasic(isDark, 0.55f))
    Spacer(12dp)
    Column {
        Text("Прикрепленная ссылка", accent color, body3M)
        Text("https://web.frisbee.live/im/2021316695", basic50, body4R)
    }
}
```

Иконка скрепки: имя проверяется в `app/src/main/assets-local/icons/` и `IconsAndColorsLibrary-cross-template/icons/`. Если канонической `attach-link.svg` нет — выбирается ближайшая (`attach.svg`, `link.svg` и т.д.) и фиксируется в impl-plan. Без popover, без tap-handler, без onClick.

### SegmentedControl

`AndroidView` обёртка над `SegmentedControlView` из `:components/segmentedcontrol`:

```kotlin
AndroidView(
    factory = { ctx -> SegmentedControlView(ctx) },
    update = { view ->
        view.configure(
            labels = listOf("Ответ", "Ссылка"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            colorScheme = brand.segmentedControlColorScheme(isDark),
        )
    },
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
        .widthIn(min = 152.dp),
)
```

Высота 32dp (intrinsic у SegmentedControlView: container с 2dp padding + 28dp inner = 32dp).

### Cross-tab side-effects

- **PreviewArea остаётся смонтированной всегда** (обе вкладки). На Tab 1 поверх неё рисуется LinkBubble overlay. Это даёт два преимущества: (1) при возврате на Tab 0 не пересоздаётся `SelectableEditText` и `CustomSelectionController` (без скачков и потери внутреннего state'а); (2) `selectionRef.value?.invoke()` остаётся живой ссылкой для финального Confirm.
- **Handles скрываются на Tab 1**: при `selectedTab = 1` дёргается `clearSelectionRef.value?.invoke()` через `LaunchedEffect(selectedTab)`. Это убирает native PopupWindow'ы handle/action-mode, которые иначе всплывают поверх LinkBubble overlay. Сам range при этом сохранён в `snapshotRange` (см. выше), так что reply-блок LinkBubble не теряет данные.
- **Popover Reply-таба**: при `selectedTab = 1` принудительно `popoverOpen = false` (тем же `LaunchedEffect`). На возврат к Tab 0 — обычное правило (`if (menuState == SELECTING) popoverOpen = true`).
- **Header кнопка «Сохранить»** работает одинаково на обеих вкладках — возвращает `snapshotRange ?: (0 to 0)`. Финальный Confirm не зависит от текущей вкладки.
- **BACK button** и ON_RESUME IME-hide — без изменений.

### Анимация переключения

`AnimatedContent` обёртка для:
- PreviewArea tab content (bubble vs LinkBubble) — fade 150мс
- BottomStrip (variant1 vs variant2) — fade 150мс
- Header title — без отдельной анимации (HeaderHost сам перенесёт текст; description-row — `Crossfade` 150мс)

SegmentedControl сам анимирует свой индикатор через `animateIndicatorTo` внутри `SegmentedControlView`.

## Затрагиваемые файлы

- `feature/chatdetail/.../quotepicker/QuoteV5FullScreenContent.kt` — основные изменения
- `app/src/main/assets-local/icons/attach-link.svg` (или эквивалент) — проверка наличия, при необходимости добавление

## `:components` правки

Не требуются. `SegmentedControlView` и `LinkBubbleView` уже предоставляют нужный API.

## Тестирование (manual smoke)

1. Открыть V5 picker для текстового сообщения. По умолчанию `selectedTab = 0`, выглядит как сейчас.
2. На Tab 0 выбрать фрагмент → `menuState = SELECTING / INITIAL_WITH_QUOTE`, BottomStrip-icon переключился на `reply-quote` (предыдущая фича).
3. Тапнуть «Ссылка» — header title меняется на «Оформление ссылки», description меняется, бабл сменяется на LinkBubble, reply-блок внутри LinkBubble содержит выбранный фрагмент, BottomStrip показывает «Прикрепленная ссылка + URL».
4. Тап «Ответ» — возврат в исходное состояние, selection-state сохранён (`menuState` не меняется при переключении).
5. На Tab 0 НЕ выбирать цитату → переключить на «Ссылка» → reply-блок LinkBubble показывает полный текст сообщения (как обычный ответ).
6. Сохранить с обеих вкладок — `onConfirm(start, end)` возвращает тот же выбор.
7. BACK на любой вкладке — закрывает picker, restore IME state.
8. Rotation на обеих вкладках — `selectedTab` сохраняется.

## Известные gotcha

- **IME при переключении вкладок**: `SelectableEditText` внутри PreviewArea остаётся смонтированной на Tab 1 (см. cross-tab side-effects). Если у неё focus — может всплыть IME. Решение: при `selectedTab = 1` дёрнуть `LocalFocusManager.clearFocus()` или прямой `editText.clearFocus()`. Точный механизм — в impl-plan.
- **LinkBubble previewImage**: при отсутствии загруженного `media_*.jpg` в `BitmapCache` на момент open'а picker'а — передаём `null`. Бабл рендерится без image preview, не критично для mock'а.
- **SegmentedControl поверх паттерн-фона**: `brand.segmentedControlColorScheme(isDark)` имеет полупрозрачный контейнер (`basicColor08`). Поверх паттерна это может оказаться плохо читаемым. Если так — в impl-plan поднимем альфу контейнера или применим лёгкий backdrop. Решение по фактическому виду на устройстве.
- **Handles re-show после возврата с Tab 1 → Tab 0**: после `clearSelectionRef` на Tab 1, при возврате на Tab 0 у `CustomSelectionController` нет selection range. `snapshotRange` сохранён в state, но handles не появятся пока пользователь не тапнет по баблу. Это OK для UX — пользователь видит цитату через `menuState = INITIAL_WITH_QUOTE` (header title, BottomStrip icon) и может либо подтвердить «Сохранить», либо изменить выбор тапом. Если потребуется автоматически восстанавливать selection при возврате — добавим `restoreSelectionRef` в impl-plan (правка в `CustomSelectionController` потребует согласия по правке `:components`).
