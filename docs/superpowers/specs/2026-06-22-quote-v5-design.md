# V5 FullScreen Quote Picker — Design

**Дата:** 2026-06-22
**Ветка:** `feature/quote-v5` (предполагаемая; имя финализирует план)
**Variant:** `QuotePickerVariant.V5`
**Figma:** [Сообщения и их взаимодействие — node 8259-690174](https://www.figma.com/design/BG1rJxwbv5FMpTKhPvEdFP/Сообщения-и-их-взаимодействие?node-id=8259-690174)

## Цель

Добавить второй визуальный вариант полноэкранного quote-picker'а (V5) рядом с существующим V4, переключаемый dev-toggle'ом в Profile. Логика, FSM, controller, IME, replyContext — переиспользуются из V4 один-в-один. Меняется только layout (хедер + нижняя зона).

## Что есть сейчас (V4)

`QuoteFullScreenContent.kt` — fullscreen `Column { HeaderHost + PreviewArea + FullScreenMenuRows(136dp) }`. Header: `HeaderConfig.Custom` с `titleAlignment=BOTTOM`, `leftButton=CLOSE`, правая кнопка-текст «Применить». Bottom — 1-2 inline menu-row'а в фиксированной 136dp секции, контент по `QuoteMenuState`. См. `docs/superpowers/specs/2026-05-28-quote-picker-fullscreen-design.md`.

V4 остаётся variant'ом по умолчанию. V4-spec не пересматривается.

## Что меняется в V5 относительно V4

| Слой | V4 | V5 |
|---|---|---|
| Header alignment | `BOTTOM` (title + description вертикально-стек, горизонтально-центр) | `LEFT` (то же, но горизонтально по левому краю) |
| Header left button | `CLOSE` (`×`) | `BACK` (`<` — `ic-back-short`) |
| Header right button label | «Применить» | «Сохранить» |
| Bottom area | Inline `FullScreenMenuRows` 136dp | MessagePanel-strip 52dp (mock) + floating popover 250dp над ним |
| Popover visibility | n/a | Initial OPEN; toggle по `reply-setting-n`; auto-open при входе в SELECTING; close по tap-outside |
| Menu items (per FSM state) | identical | identical |
| FSM, controller, refs | используются | те же |
| IME / dialog flags | в MainActivity на уровне picker'а | те же (variant-агностично) |

## Архитектура файлов

### Новые

- `core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt` — enum + CompositionLocal, размещён в `:core:ui` (а не в `:feature:chatdetail`), потому что читать/писать его нужно из `:feature:profile`, а `:feature:*` друг от друга зависеть не могут. Pattern идентичен `LocalBitmapCache` / `LocalAppBrand`.
  ```kotlin
  enum class QuotePickerVariant { V4, V5 }

  val LocalQuotePickerVariant =
      staticCompositionLocalOf<MutableStateFlow<QuotePickerVariant>> {
          error("LocalQuotePickerVariant not provided")
      }
  ```
- `feature/chatdetail/.../quotepicker/QuoteV5FullScreenContent.kt` — composable c той же signature, что V4:
  ```kotlin
  @Composable
  fun QuoteV5FullScreenContent(
      message: Message, senderPersona: Persona?, senderAvatar: Bitmap?,
      isMine: Boolean, initialStart: Int, initialEnd: Int,
      onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit, onCancelReply: () -> Unit,
  )
  ```
- `feature/chatdetail/.../quotepicker/QuotePickerShared.kt` — экстракт из V4:
  - `internal fun PreviewArea(...)` — bubble + pattern + clip rects + scroll (≈100 строк, identical у V4 и V5).
  - `internal data class FullScreenMenuItemSpec(label, iconName, isDanger, onClick)`.
  - `internal fun itemsForState(state: QuoteMenuState, callbacks: MenuCallbacks): List<FullScreenMenuItemSpec>` — FSM→items маппинг (см. таблицу в §FSM).
  - `internal data class MenuCallbacks(onSelectFragment, onApply, onCancelReply, onBack, onConfirmQuote, onClearQuote)`.
  - `internal fun FullScreenMenuRow(item: FullScreenMenuItemSpec, paddingStart: Dp = 24.dp, paddingEnd: Dp = 16.dp, rowHeight: Dp = 48.dp)` — параметризуем horizontal padding (V4: start=24/end=16; V5: 16/16), всё остальное общее.
  - `internal fun replyPreviewText(message: Message): String` — текст для bottom strip'а в V5 (для Text → body, для Media → caption ?: «Фото»/«Видео», для Voice → «Голосовое сообщение»).
  - `internal val FullScreenRobotoFontFamily` — bundled Roboto fallback для MIUI.

### Изменённые

- `feature/chatdetail/.../quotepicker/QuoteFullScreenContent.kt` — удалить private `PreviewArea`, `FullScreenMenuItemSpec`, локальный `itemsForState`-блок и `FullScreenMenuRow`; импортировать из `QuotePickerShared.kt`. Поведение V4 не меняется.
- `feature/chatdetail/.../quotepicker/QuotePickerFullScreen.kt` — добавить параметр `variant: QuotePickerVariant`, маршрутизировать:
  ```kotlin
  when (variant) {
      QuotePickerVariant.V4 -> QuoteFullScreenContent(/* …existing params… */)
      QuotePickerVariant.V5 -> QuoteV5FullScreenContent(/* …existing params… */)
  }
  ```
  Остальная signature сохраняется как сейчас.
- `app/src/main/java/com/example/template/AppContainer.kt` — добавить holder:
  ```kotlin
  val quotePickerVariant: MutableStateFlow<QuotePickerVariant> =
      MutableStateFlow(QuotePickerVariant.V4)
  ```
  In-process only. Дефолт V4 на старт процесса.
- `app/.../MainActivity.kt` — две правки:
  1. Обернуть AppScaffold (вместе с overlay'ями profile/picker) в `CompositionLocalProvider(LocalQuotePickerVariant provides container.quotePickerVariant) { … }` — рядом с другими провайдерами (`LocalAppBrand`, `LocalBitmapCache` и т.д.).
  2. В блоке рендера picker'а (строка ~343) дочитать через CompositionLocal вместо прямого AppContainer-доступа:
     ```kotlin
     val variantFlow = LocalQuotePickerVariant.current
     val variant by variantFlow.collectAsState()
     QuotePickerFullScreen(variant = variant, /* … */)
     ```
- `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt` — добавить новую `ProfileCard` (см. §Toggle UI).

### НЕ трогаем

- `:components` — `HeaderConfig.Custom.LeftButton.BACK` и `TitleAlignment.LEFT` уже существуют (`HeadersView.kt:73-74`). Никаких правок в соседнем репо.
- `:core:data`, `:core:model`, `:core:ui`, `ChatDetailViewModel`, `:components/MessagePanelView`.
- IME-handling в MainActivity (`DisposableEffect(pickerVisible)`) — уже variant-агностичен.

## Layout (V5)

```
┌──────────────────────────────────────────┐
│ Status bar (system, не оверлэйим)        │
├──────────────────────────────────────────┤
│ Header / Default 56dp (appSurface01)     │  ← HeaderConfig.Custom
│ ⟨  Ответ на сообщение         Сохранить  │     titleAlignment=LEFT
├──────────────────────────────────────────┤
│ Description row 48dp (appSurface01)      │  ← description у того же HeaderConfig
│ Вы можете процитировать фрагмент сообщения│
├──────────────────────────────────────────┤
│ Preview area (weight 1f)                 │
│ ┌─ pattern background ─────────────────┐ │
│ │           ┌── bubble ──┐             │ │  ← PreviewArea (shared)
│ │           │ Алексей… │              │ │
│ │           │  …        │             │ │
│ │           └──────────┘              │ │
│ │                                      │ │
│ │  ┌── popover 250dp ──┐              │ │  ← floating, align=BottomStart
│ │  │ Выбрать фрагмент ⨐│              │ │     start=8dp, bottom=8dp
│ │  │ Отменить ответ   🗑│              │ │
│ │  └───────────────────┘              │ │
│ └──────────────────────────────────────┘ │
├──────────────────────────────────────────┤
│ Bottom strip 52dp (appSurface01)         │
│ ⟁  Алексей Гурин                         │  ← MessagePanel "Context toolbar" mock
│    Проверьте ещё НДС в последнем листе…   │     pure Compose Row
│ navigationBars padding                   │
└──────────────────────────────────────────┘
```

### 1. Header

`HeaderConfig.Custom`:

| Поле | Значение |
|---|---|
| `titleAlignment` | `TitleAlignment.LEFT` |
| `title` | `INITIAL`/`INITIAL_MINIMAL` → «Ответ на сообщение»; `INITIAL_WITH_QUOTE`/`SELECTING` → «Ответ на цитату» |
| `description` | «Вы можете процитировать фрагмент сообщения» |
| `leftButton` | `LeftButton.BACK` |
| `rightButton` | `RightButton.TEXT`, `rightButtonType = ButtonType.PRIMARY`, label «Сохранить» |
| `onLeftClick` | `{ clearSelectionRef.value?.invoke(); onDismiss() }` |
| `onRightClick` | `{ val r = selectionRef.value?.invoke(); clearSelectionRef.value?.invoke(); onConfirm(r?.first ?: 0, r?.last ?: 0) }` |

Лямбды через `rememberUpdatedState` + stable wrapper (тот же паттерн, что в V4 `QuoteFullScreenContent.kt:127-156`).

Если выяснится, что `HeaderConfig.Custom` с `titleAlignment=LEFT` не рендерит `description` (рисует только title), description можно вынести в отдельную 48dp `Row` ниже header'а — visually identical. Решение фиксируется в plan'е после быстрой проверки рендера.

### 2. Preview area

`PreviewArea(...)` из `QuotePickerShared.kt`. Параметры идентичны V4:

```kotlin
PreviewArea(
    message, senderPersona, senderAvatar, isMine, initialStart, initialEnd,
    tvRef, selectAllRef, selectionRef, clearSelectionRef,
    onSelectionStart = { if (menuState != SELECTING) menuState = SELECTING },
    onSelectionEnd = { /* same as V4 */ },
    onConfirm, onDismiss,
    modifier = Modifier.fillMaxSize(),
)
```

Pattern background, scrollable bubble, два clip-rect'а (handle/menu), auto-scroll-to-bottom — без изменений. Контейнер preview становится `Box(weight=1f)` (не `Column` с `weight=1f`, как у V4), чтобы можно было сверху наложить floating popover в `BottomStart`-align.

### 3. Bottom strip (52dp, Compose mock)

```kotlin
@Composable
private fun BottomStrip(
    senderName: String,
    previewText: String,
    popoverOpen: Boolean,
    onIconClick: () -> Unit,
) {
    val borderColor = appBasic(isDark, 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appSurface01(isDark))
            .drawBehind {
                // 1dp top border (нет встроенного Modifier.border(top=...))
                drawLine(borderColor, Offset(0f, 0f), Offset(size.width, 0f),
                         strokeWidth = 1.dp.toPx())
            }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),  // 40dp content + 8/4 padding = 52dp outer (Figma h-[52px], pb-[4] pt-[8])
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp).appClickable(onIconClick),
            contentAlignment = Alignment.Center) {
            if (popoverOpen) {
                Box(Modifier.size(36.dp).clip(CircleShape)
                    .background(appBasic(isDark, 0.08f)))
            }
            DsIconImage("reply-setting-n", appBasic(isDark, 0.55f), 24)
        }
        Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
            Text(senderName,
                 style = DSTypography.subhead4M.toComposeTextStyle(),
                 color = brand.accentColor(isDark),
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(previewText,
                 style = DSTypography.subhead2R.toComposeTextStyle(),
                 color = appBasic(isDark, 0.5f),
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
```

Spec по Figma (node 8259:690184 → `Message Pannel / Context toolbar`):
- bg = `var(--background/01-base, white)` → `appSurface01(isDark)`.
- top border 1dp `basic08`.
- padding `pl=16 pr=16 pt=8 pb=4`.
- icon `ic-reply-setting-n` 24dp, tint `basic55`; иконка в Box 36×36 (hit target), active state — Box 36dp с bg `basic08` + clip Circle (Figma: «icon background» 36dp при popover'е открытом).
- gap icon ↔ text = 12dp.
- name: Subhead 4-M 14sp/20sp lineHeight Roboto Medium, color `brand.accentColor(isDark)`.
- preview: Subhead 2-R 14sp/20sp Roboto Regular, color `basic50`.

`senderName` ← `senderPersona?.let { it.firstName + (it.lastName?.let { " $it" } ?: "") } ?: ""`.
`previewText` ← `replyPreviewText(message)` (shared helper).

### 4. Popover (250dp, floating)

```kotlin
@Composable
private fun PopoverCard(items: List<FullScreenMenuItemSpec>) {
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(appBasic(isDark, 0.08f))  // background/02-second ≈ basic08
            .padding(vertical = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                FullScreenMenuRow(
                    item,
                    paddingStart = 16.dp,
                    paddingEnd = 16.dp,
                    rowHeight = 48.dp,
                )
                if (i < items.lastIndex) {
                    Divider(thickness = 0.5.dp, color = appBasic(isDark, 0.08f))
                }
            }
        }
    }
}
```

Spec по Figma (`menu / android` 8247:642866 + `Android Quick Actions`):
- width 250dp, corner-radius 14dp, bg `background/02-second` (= basicColor08 в dark, basicColor04 в light — visually эквивалент; финализируется при рендере).
- inner vertical padding 2dp.
- row: 48dp h, padding `16x8`, label 15sp/20sp Roboto Regular, icon 24dp справа.
- divider 0.5dp `basic08` между row'ами.

Anchored в `Box(weight=1f)` preview-контейнера через `Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)`. 8dp bottom — safe-зона между popover'ом и strip'ом.

#### Анимация

```kotlin
AnimatedVisibility(
    visible = popoverOpen,
    enter = fadeIn(tween(150)) + slideInVertically(tween(220, FastOutSlowInEasing)) { it / 4 },
    exit = fadeOut(tween(120)),
) {
    PopoverCard(items = itemsForState(menuState, callbacks))
}
```

Смена контента popover'а на FSM-transition (внутри `PopoverCard` → `AnimatedContent`) — тот же direction-aware slide + fade, что у V4 `FullScreenMenuRows`:

```kotlin
AnimatedContent(
    targetState = menuState,
    transitionSpec = {
        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
        (slideInHorizontally(tween(220, FastOutSlowInEasing)) { it * dir } +
            fadeIn(tween(120))) togetherWith
            (slideOutHorizontally(tween(220, FastOutSlowInEasing)) { -it * dir } +
                fadeOut(tween(120)))
    },
    label = "V5PopoverFsm",
) { current -> /* items column */ }
```

### 5. Popover open/close (model B)

```kotlin
var popoverOpen by rememberSaveable { mutableStateOf(true) }  // initial OPEN

LaunchedEffect(menuState) {
    if (menuState == QuoteMenuState.SELECTING) popoverOpen = true  // auto-open
}
```

**Триггеры:**
- Tap по `reply-setting-n` icon в strip'е → `popoverOpen = !popoverOpen`.
- Tap по preview area вне popover'а (по pattern background) → `popoverOpen = false`.
  - Реализация: `Modifier.pointerInput { detectTapGestures { popoverOpen = false } }` на отдельном transparent `Box`, выложенном сразу над `BackgroundPatternView` AndroidView'ом. Bubble (с собственным `AndroidView` поверх) перехватывает свои тапы первым; handle'ы выделения popup'ами controller'а живут поверх всего — на них tap-detector преcнее не среагирует.
  - **Технический риск:** конкуренция за тапы между Compose pointerInput, AndroidView (`SelectableEditText` controller), и popup'ами selection-handle'ов. Если простой `detectTapGestures` оверлей не сработает корректно — fallback: повесить detector ТОЛЬКО на pattern-background-AndroidView через `View.setOnClickListener`. Решение принимается на этапе implementation.

В SELECTING tap-toggle по иконке остаётся работоспособным (можно закрыть popover); коммит цитаты доступен через top-right «Сохранить». Это сознательный trade-off в пользу простой и предсказуемой логики (одно правило toggle на все state'ы).

## FSM (без изменений)

| State | Условие | Popover items | Top-right |
|---|---|---|---|
| `INITIAL` | hasSelectableText && initialStart >= initialEnd | • Выбрать фрагмент (`quote-full`)<br>• Отменить ответ (`delete`, danger) | «Сохранить» (= reply без quote) |
| `INITIAL_WITH_QUOTE` | hasSelectableText && initialStart < initialEnd | • Снять выделение (`cancel-quote`)<br>• Отменить ответ (`delete`, danger) | «Сохранить» (коммит quote) |
| `SELECTING` | onSelectionStart triggered | • Назад (`back`)<br>• Цитировать фрагмент (`quote`) | «Сохранить» (коммит range) |
| `INITIAL_MINIMAL` | !hasSelectableText (Voice/Link/CallMeet) | • Отменить ответ (`delete`, danger) | «Сохранить» (reply без quote) |

Initial state init и transition-логика — те же, что у V4 (`QuoteFullScreenContent.kt:87-100, 170-183`). Менять `QuoteMenuState` enum не нужно.

## Toggle UI

### ProfileScreen — новая ProfileCard

Вставляется ПЕРЕД card'ом «Версия приложения» (последний блок внизу — `ProfileScreen.kt:246-261`). Идиоматично — dev-настройка отделена от продакт-секций, перед footer'ом.

```kotlin
val variantFlow = LocalQuotePickerVariant.current
val variant by variantFlow.collectAsState()

ProfileCard(groupBg) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Полноэкранный пикер V5",
            style = DSTypography.body1R.toComposeTextStyle(),
            color = appBasic(isDark, 0.9f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = variant == QuotePickerVariant.V5,
            onCheckedChange = {
                variantFlow.value =
                    if (it) QuotePickerVariant.V5 else QuotePickerVariant.V4
            },
        )
    }
}
```

`:feature:profile` уже зависит от `:core:ui` (для `LocalAppBrand`, `LocalBitmapCache` и др.) — добавление `LocalQuotePickerVariant` импорта не требует новых module-deps.

Switch — Material3 (`androidx.compose.material3.Switch`), цвета по умолчанию. Без `appClickable`-обёртки — у Switch свой ripple.

Не показываем `if (BuildConfig.DEBUG)`-гейт — это dev-сборка для прототипа.

### MainActivity wire

В блоке `if (chatVm != null) { … QuotePickerFullScreen(…) }` (строка ~343 в `MainActivity.kt`):

```kotlin
val variantFlow = LocalQuotePickerVariant.current
val variant by variantFlow.collectAsState()
QuotePickerFullScreen(
    variant = variant,
    message = originalMessage,
    /* остальные параметры как сейчас */
)
```

Variant читается на каждом recompose'е picker-блока. При переключении toggle'а в Profile уже открытого picker'а быть не может (profile-overlay невозможно достичь через picker-overlay, ChatDetail скрыт за picker'ом). Защитный snapshot всё равно делается — см. §Edge cases.

### Router QuotePickerFullScreen

```kotlin
@Composable
fun QuotePickerFullScreen(
    variant: QuotePickerVariant,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    when (variant) {
        QuotePickerVariant.V4 -> QuoteFullScreenContent(
            message, senderPersona, senderAvatar, isMine,
            initialStart, initialEnd, onConfirm, onDismiss, onCancelReply,
        )
        QuotePickerVariant.V5 -> QuoteV5FullScreenContent(
            message, senderPersona, senderAvatar, isMine,
            initialStart, initialEnd, onConfirm, onDismiss, onCancelReply,
        )
    }
}
```

## Edge cases

### Переключение variant'а с открытым picker'ом

Если пользователь успел открыть picker, потом дёрнул назад в Profile (но picker fullscreen overlay сейчас перекрывает MainScaffold — попасть в Profile реально невозможно), переключил toggle, и picker зачем-то перерекомпозился — `when (variant)` ререндерит другой content. Это сломает internal state (FSM, selection refs). На практике сценарий не достижим (Profile недоступен поверх picker'а), но защищаемся: capture variant внутри `remember(/* no key */)` при первом invocation'е picker'а:

```kotlin
val capturedVariant = remember { variant }  // snapshot при первой композиции
when (capturedVariant) { … }
```

Альтернатива — захват в MainActivity при `pickerVisible` rising-edge. Решение фиксируется в plan'е.

### IME / dialog flags

Уже обработаны в MainActivity (строки 280-328) — `STATE_ALWAYS_HIDDEN` softInputMode + `controller.hide(ime())` + ON_RESUME observer. Это variant-агностично. V5 ничего не добавляет.

### BackHandler

Внутри V5 content (как у V4) — `BackHandler { clearSelectionRef.value?.invoke(); onDismiss() }`. Очистка selection синхронно до dismiss обязательна — handle-popup'ы controller'а иначе подвисают (V4-комментарии `QuoteFullScreenContent.kt:108-115`).

### V5 popover при empty selectableText (`INITIAL_MINIMAL`)

Один row («Отменить ответ»). Popover уменьшается в высоте. Анимация `AnimatedContent` обрабатывает это через size-transform. По Figma не показан, но логика идентична V4.

## Что НЕ делаем (out of scope)

- Не персистим variant между запусками (in-process `MutableStateFlow`).
- Не показываем toggle в release-сборке отдельно — это dev-прототип.
- Не объединяем V4 и V5 в shared scaffold (подход 1B был отклонён в брейншторме — V4 не трогаем, кроме экстракта shared-helpers).
- Не добавляем animation на сам переход V4↔V5 — переключение применяется на следующем `openQuotePicker()`.
- Не модифицируем `:components` — все нужные enum-значения (`LeftButton.BACK`, `TitleAlignment.LEFT`) уже существуют.

## Acceptance criteria (manual)

1. Profile → flip toggle «Полноэкранный пикер V5» → open чат → long-press на text/media bubble → «Цитировать фрагмент в окне» → открывается V5 (LEFT header, `<` back, «Сохранить», popover, MessagePanel-strip снизу).
2. Toggle off → следующий вызов picker'а открывает V4 (без изменений).
3. V5: tap по `reply-setting-n` в strip'е → popover закрывается. Tap ещё раз → открывается. Active state иконки (36dp basic08 circle) синхронизирован с popover-visibility.
4. V5: tap по pattern background вне popover'а → popover закрывается.
5. V5: «Выбрать фрагмент» → FSM в SELECTING → popover остаётся открытым (auto-open), контент → «Назад / Цитировать фрагмент». Tap «Цитировать фрагмент» → commit range + dismiss picker. Reply в MessagePanel показывает quote.
6. V5: tap «Сохранить» в top-right в любом state'е работает идентично V4 «Применить».
7. V5: tap `<` в top-left или system back → dismiss picker без коммита.
8. V5 с Voice/Link/CallMeet message'ом (INITIAL_MINIMAL) → popover с одним row'ом «Отменить ответ».
9. V4-flow тестов из V4-spec не регрессирует.

## Open items для plan'а

- Verify, что `HeaderConfig.Custom` с `titleAlignment=LEFT` рендерит `description`. Если нет — рендерить description в отдельной 48dp Compose-row ниже `HeaderHost`.
- Verify подход к tap-outside в preview area: `pointerInput.detectTapGestures` на transparent overlay'е VS `setOnClickListener` на pattern-background View. Fallback решается в plan'е.
- Verify `basic08` vs `basic04` для popover bg — `background/02-second` визуально может быть либо одно, либо другое в зависимости от темы. Финализируется при сравнении со скрином в эмуляторе.
- Decide, где хранить captured variant snapshot — внутри V5 content'а или в MainActivity rising-edge'е.

## Связанные документы

- [V4 fullscreen quote-picker design](2026-05-28-quote-picker-fullscreen-design.md) — FSM, dialog properties, IME flags. V5 наследует.
- `CLAUDE.md` § «Реализовано → Quote-reply» — текущее состояние V4 в проде.
