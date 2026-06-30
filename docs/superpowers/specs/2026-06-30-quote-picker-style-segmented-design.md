# Quote Picker Style — SegmentedControl + «Рендер ссылок» switch — Design

**Дата:** 2026-06-30
**Ветка:** `main` → новая feature-ветка `feature/quote-picker-style-segmented`
**Источник для портирования Modal:** `../android-template-quote` (ветка `feature/quote-reply`, HEAD `1093776`)

## Цель

Расширить переключатель quote-picker'а в Profile с бинарного свитчера V4/V5 до:
1. **SegmentedControl с 3 стилями** picker'а: `Полноэкранный` / `Точки` / `Кнопки`.
2. **Switch «Рендер ссылок»** (default ON), который ортогонально контролирует видимость link-вкладки во всех трёх стилях.

Modal-варианты (`MODAL_DOTS` и `MODAL_BUTTONS`) — port из `android-template-quote` с тривиальной адаптацией.

## Что есть сейчас

`core/ui/.../QuotePickerVariant.kt`:
```kotlin
enum class QuotePickerVariant { V4, V5 }
val LocalQuotePickerVariant = staticCompositionLocalOf<MutableStateFlow<QuotePickerVariant>> { ... }
```

`AppContainer.quotePickerVariant: MutableStateFlow<QuotePickerVariant>` (default `V4`).

Profile содержит одну `ProfileCard` со свитчером «Полноэкранный пикер V5», тогглящим V4 ↔ V5. `MainActivity.QuotePickerFullScreen(variant = LocalQuotePickerVariant.current.value, ...)` диспатчит в `QuoteFullScreenContent` (V4) или `QuoteV5FullScreenContent` (V5).

В soседнем `android-template-quote`:
- `enum QuoteVariant { MODAL_DOTS, MODAL_BUTTONS }` в `:core:model`.
- 6 файлов в `feature/chatdetail/.../quotepicker/`: `QuotePickerModal`, `QuoteModalContent`, `QuoteModalSwipeFooter`, `QuoteModalButtonsHeader`, `QuoteModalLinkPreview`, `QuoteModalLinkPopoverCard`, плюс `QuoteModalShared` (только helpers `replyPreviewText/extractQuote`).
- Оба варианта — Compose Dialog поверх ChatDetail, отличаются footer-chrome внутри preview Box'а (dots+swipe vs arrow-buttons+tap).

## State-модель

**Подход A — два независимых CompositionLocal** (выбран — Approach B с одним data-class'ом отброшен как многословный без выгоды).

`core/ui/.../QuotePickerStyle.kt` (замена `QuotePickerVariant.kt`):
```kotlin
enum class QuotePickerStyle { FULLSCREEN, MODAL_DOTS, MODAL_BUTTONS }

val LocalQuotePickerStyle = staticCompositionLocalOf<MutableStateFlow<QuotePickerStyle>> {
    error("LocalQuotePickerStyle not provided")
}
val LocalLinkRenderEnabled = staticCompositionLocalOf<MutableStateFlow<Boolean>> {
    error("LocalLinkRenderEnabled not provided")
}
```

`AppContainer`:
- `quotePickerVariant: MutableStateFlow<QuotePickerVariant>` → `quotePickerStyle: MutableStateFlow<QuotePickerStyle>` (default `FULLSCREEN`).
- Новое поле: `linkRenderEnabled: MutableStateFlow<Boolean>` (default `true`).

`MainActivity` CompositionLocalProvider:
```kotlin
CompositionLocalProvider(
    LocalQuotePickerStyle provides container.quotePickerStyle,
    LocalLinkRenderEnabled provides container.linkRenderEnabled,
    // ...остальные locals (LocalAppBrand, LocalBitmapCache, ...)
)
```

**Внутренний enum** для дисптача FULLSCREEN-ветки (V4 vs V5), живёт в `feature/chatdetail/.../quotepicker/`, **не** в `:core:ui`:
```kotlin
internal enum class FullScreenVariant { V4, V5 }
```

Старый `QuotePickerVariant` удаляется полностью.

**Persistence:** `MutableStateFlow` per-session (как существующий V5-toggle). DataStore-persistence не добавляется.

## Profile UI

`feature/profile/.../ProfileScreen.kt` — заменить существующую card со свитчером:

```
┌─ ProfileCard (groupBg) ──────────────────────────────┐
│  Row (min h=48, pad horiz=16, vert=8)                │
│    SegmentedControlView [Полноэкранный|Точки|Кнопки] │
│  Divider (0.5dp, appBasic(isDark, 0.08f))            │
│  Row (h=48, pad horiz=16)                            │
│    Text "Рендер ссылок"  weight(1)         Switch    │
└──────────────────────────────────────────────────────┘
```

- `SegmentedControlView` — через `AndroidView` (тот же паттерн, что V5 picker и MessagePanel). `colorScheme = brand.segmentedControlColorScheme(isDark)`. 3 сегмента: `Полноэкранный` / `Точки` / `Кнопки`. `selectedIndex = quotePickerStyle.ordinal`.
- Divider — 0.5dp, `appBasic(isDark, 0.08f)` — match существующих multi-row cards.
- Switch label: `«Рендер ссылок»` — `DSTypography.body1R.toComposeTextStyle()`, `appBasic(isDark, 0.9f)`.
- Switch — material3 без кастомных цветов (как сейчас).
- `onCheckedChange = { container.linkRenderEnabled.value = it }`.
- `onSegmentSelected = { idx -> container.quotePickerStyle.value = QuotePickerStyle.values()[idx] }`.

## Picker dispatch (MainActivity)

```kotlin
val style by container.quotePickerStyle.collectAsState()
val linkRender by container.linkRenderEnabled.collectAsState()

if (quotePickerVisible) {
    val ctx = quotePickerContext  // existing
    when (style) {
        QuotePickerStyle.FULLSCREEN -> {
            // inline overlay в Activity-окне; tap-barrier Box.clickable(indication=null){} остаётся
            QuotePickerFullScreen(
                innerVariant = if (linkRender) FullScreenVariant.V5 else FullScreenVariant.V4,
                message = ctx.message,
                ...,
            )
        }
        QuotePickerStyle.MODAL_DOTS, QuotePickerStyle.MODAL_BUTTONS -> {
            QuotePickerModalHost(
                style = style,
                linkRender = linkRender,
                message = ctx.message,
                ...,
            )
        }
    }
}
```

**Гейт IME-обработки по style:**
- Существующий `softInputMode`-override (`SOFT_INPUT_STATE_ALWAYS_HIDDEN` + `LifecycleObserver` ON_RESUME hide) — применяется **только когда `style == FULLSCREEN`**. Modal Dialog имеет свой `FLAG_ALT_FOCUSABLE_IM`, который сохраняет IME-state (нужно для UX перехода на «Ссылка» tab под Dialog).
- Гейт: `if (style == QuotePickerStyle.FULLSCREEN) { /* существующий IME-блок */ }`.

**Tap-barrier `Box.clickable(indication=null){}`** (защита от пропуска тапов в MessagePanel под picker'ом) — нужен только для FULLSCREEN. Dialog имеет своё Window — тапы перехватываются нативно.

**`QuotePickerFullScreen.kt`:**
- Параметр `variant: QuotePickerVariant` → `innerVariant: FullScreenVariant`.
- `when (innerVariant) { V4 -> QuoteFullScreenContent(...), V5 -> QuoteV5FullScreenContent(...) }` — логика без изменений.

**`QuotePickerModalHost.kt` (новый):**
- Принимает `style: QuotePickerStyle` (только MODAL_DOTS/BUTTONS), `linkRender: Boolean`, плюс те же поля что FULLSCREEN.
- Мапит `style` в `QuoteVariant` (новый enum в `:core:model`, ниже).
- Делегирует в портированный `QuotePickerModal` (Compose Dialog).

## Modal port

**Новый файл в `:core:model`:**
```kotlin
// core/model/src/main/java/.../QuoteVariant.kt
enum class QuoteVariant { MODAL_DOTS, MODAL_BUTTONS }
```

Почему отдельный enum (а не переиспользование `QuotePickerStyle`):
- `QuoteModalContent` живёт в `:feature:chatdetail`, зависящем от `:core:model` но не от `:core:ui`. Затягивать `:core:ui` ради enum'а нарушит сложившийся слоинг.
- `QuotePickerStyle.FULLSCREEN` в Modal-коде смысла не имеет.

**Копирование из `../android-template-quote/feature/chatdetail/.../quotepicker/`:**

| Source file | Target | Изменения |
|---|---|---|
| `QuotePickerModal.kt` | один-в-один | — |
| `QuoteModalContent.kt` | + правка | новый параметр `linkRender: Boolean` (см. ниже) |
| `QuoteModalSwipeFooter.kt` | + правка | extract `QuoteModalStaticFooter` для off-state |
| `QuoteModalButtonsHeader.kt` | один-в-один | — |
| `QuoteModalLinkPreview.kt` | один-в-один | — |
| `QuoteModalLinkPopoverCard.kt` | один-в-один | — |

**НЕ копируется:**

| Source file | Причина |
|---|---|
| `QuoteModalShared.kt` | `replyPreviewText` и `extractQuote` уже в `QuotePickerShared.kt` |
| `QuotePickerOverlay.kt` | роль играет новый `QuotePickerModalHost.kt` |

**`QuoteBubblePreview.kt`** в нашем проекте уже поддерживает опциональный `restoreSelectionRef` (использует V5) — менять не нужно.

## linkRender semantics

| Style | linkRender ON | linkRender OFF |
|---|---|---|
| FULLSCREEN | V5 picker (внутри SegmentedControl «Ответ/Ссылка») | V4 picker (без внутренней tab-структуры) |
| MODAL_DOTS | dots+swipe+link-tab видимы; `selectedTab` свободно переключается | dots/swipe скрыты; `selectedTab` залочен в 0; preview всегда quote |
| MODAL_BUTTONS | arrow-buttons+link-tab видимы; tap переключает tab | buttons скрыты; `selectedTab` залочен в 0; preview всегда quote |

`linkRender OFF` делает MODAL_DOTS и MODAL_BUTTONS визуально идентичными (только Title/Description footer). Это осознанный выбор пользователя — пара (style, linkRender) сохраняется независимо для гибкости.

**`QuoteModalContent` правка:**

```kotlin
@Composable
fun QuoteModalContent(
    ...,
    variant: QuoteVariant,
    linkRender: Boolean,    // ← НОВЫЙ
    ...,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // Hard-force tab=0 когда linkRender flip'нулся в OFF.
    LaunchedEffect(linkRender) {
        if (!linkRender) selectedTab = 0
    }

    // Preview Box clipRadius/contentBottomPad ветвятся по (variant, linkRender).
    // OFF-state (linkRender=false) использует ту же геометрию что MODAL_BUTTONS ON
    // (cornerRadius=34dp, contentBottomPad=0dp, bottomContentReserve=82dp): тот же
    // outer shell, но в footer-зоне 82dp рендерится QuoteModalStaticFooter (Title+Desc
    // вертикально центрирован) вместо buttons. Никаких новых констант — переиспользуем
    // существующие значения sibling'а.
    val previewCornerRadius = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 24.dp else 34.dp
    val contentBottomPad = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 74.dp else 0.dp
    val bottomContentReserve = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 16.dp else 82.dp

    AnimatedContent(targetState = selectedTab, ...) { tab ->
        if (tab == 0) QuoteBubblePreview(...) else QuoteModalLinkPreview(...)
    }

    AnimatedContent(targetState = selectedTab, ...) { tab ->
        if (tab == 0) QuoteMenu(...) else QuoteModalLinkPopoverCard(...)
    }

    if (linkRender) {
        when (variant) {
            QuoteVariant.MODAL_DOTS -> QuoteModalSwipeFooter(
                selectedTab = selectedTab,
                menuState = menuState,
                onTabChange = { selectedTab = it },
            )
            QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(
                selectedTab = selectedTab,
                menuState = menuState,
                onTabChange = { selectedTab = it },
                onBack = if (selectedTab > 0) ({ selectedTab = 0 }) else null,
            )
        }
    } else {
        QuoteModalStaticFooter(menuState = menuState)
    }
}
```

**`QuoteModalStaticFooter` (новый, экстракт из `QuoteModalSwipeFooter`):**
- Только центрированный Column с Title + Description.
- Title по `menuState`: `INITIAL/INITIAL_MINIMAL` → «Ответ на сообщение»; `INITIAL_WITH_QUOTE/SELECTING` → «Ответ на цитату».
- Description: «Вы можете процитировать фрагмент сообщения».
- Без `pointerInput`/`Animatable dragOffset`/`AnimatedContent`-swap/Dots.
- Живёт в `QuoteModalSwipeFooter.kt` как top-level composable (имя файла не меняем).

## Зависимости / blockers, которые проверю при имплементации

1. **`brand.linkBubbleColorScheme(isDark)`** — нужен для `QuoteModalLinkPreview`. V5 picker уже использует → есть в `:components`.
2. **`appSurface02(isDark)`** — нужен для `QuoteModalLinkPopoverCard`. Если отсутствует — добавить в `core/ui/.../AppColors.kt` рядом с `appSurface01`. Риск низкий.
3. **`SegmentedControlView` API** для Profile — проверю update-callback (`onSelectedIndexChange`-аналог) при имплементации.

## Правки в `:components` / `../android-components`

**Не требуются.** Все используемые компоненты (`SegmentedControlView`, `LinkBubbleView`, `BackgroundPatternView`) уже в библиотеке.

## Файлы — Create / Modify

| Файл | Действие |
|---|---|
| `core/model/src/main/java/.../QuoteVariant.kt` | **Create** — `enum QuoteVariant { MODAL_DOTS, MODAL_BUTTONS }` |
| `core/ui/src/main/java/.../QuotePickerStyle.kt` | **Replace** (был `QuotePickerVariant.kt`) — новый enum + 2 Local |
| `app/.../AppContainer.kt` | **Modify** — `quotePickerStyle` + `linkRenderEnabled` |
| `app/.../MainActivity.kt` | **Modify** — CompositionLocalProvider, dispatch `when (style)`, IME-гейт, tap-barrier гейт |
| `feature/profile/.../ProfileScreen.kt` | **Modify** — заменить switch-card на segmented+switch card |
| `feature/chatdetail/.../quotepicker/QuotePickerFullScreen.kt` | **Modify** — `variant: QuotePickerVariant` → `innerVariant: FullScreenVariant` (internal enum) |
| `feature/chatdetail/.../quotepicker/QuotePickerModalHost.kt` | **Create** — обёртка над `QuotePickerModal` со style→QuoteVariant маппингом |
| `feature/chatdetail/.../quotepicker/QuotePickerModal.kt` | **Create** (port) |
| `feature/chatdetail/.../quotepicker/QuoteModalContent.kt` | **Create** (port + `linkRender` гейт) |
| `feature/chatdetail/.../quotepicker/QuoteModalSwipeFooter.kt` | **Create** (port + extract `QuoteModalStaticFooter`) |
| `feature/chatdetail/.../quotepicker/QuoteModalButtonsHeader.kt` | **Create** (port) |
| `feature/chatdetail/.../quotepicker/QuoteModalLinkPreview.kt` | **Create** (port) |
| `feature/chatdetail/.../quotepicker/QuoteModalLinkPopoverCard.kt` | **Create** (port) |

## Тестовые инварианты

1. **Profile reflects state correctly:** на cold-open default'ы — `FULLSCREEN` сегмент активен, «Рендер ссылок» switch ON.
2. **FULLSCREEN × linkRender=ON** → открытие picker'а в чате показывает V5 layout (внутренний SegmentedControl «Ответ/Ссылка»).
3. **FULLSCREEN × linkRender=OFF** → открытие picker'а показывает V4 layout (без внутреннего segmented).
4. **MODAL_DOTS × linkRender=ON** → Dialog с dots-footer, swipe между tab=0/tab=1 работает.
5. **MODAL_DOTS × linkRender=OFF** → Dialog с static-footer (Title+Description), `selectedTab` залочен в 0, swipe-gesture не реагирует.
6. **MODAL_BUTTONS × linkRender=ON** → Dialog с arrow-buttons-footer, tap переключает tab.
7. **MODAL_BUTTONS × linkRender=OFF** → Dialog с static-footer, buttons скрыты.
8. **System back / outside tap / Cancel reply / Apply** — на любом стиле/состоянии работают как сейчас (sibling spec FSM-инварианты применимы напрямую к Modal'у).
9. **`LaunchedEffect(linkRender)` snap-to-tab=0** — defensive guard, не пользовательский сценарий (Profile взаимоисключителен с открытым picker'ом). Цель — детерминированность состояния если StateFlow flip'нется через программный путь (тесты, edge cases).

## Out of scope

- DataStore-persistence preferences (per-session как сейчас).
- Переключение стиля picker'а пока он открыт (через какой-либо UI вне Profile) — Profile взаимоисключителен с открытым picker'ом, реактивность mid-flight не дизайнерское требование.
- Изменение API `QuoteVariant` в sibling — наш `:core:model.QuoteVariant` independent (sibling code НЕ копируется в `:core:model`).
- Любые правки в `:components` / `../android-components`.
- Tap-on-dot переключение в `QuoteModalSwipeFooter` (только swipe; sibling-spec явно out of scope).
