# Quote Picker Style — SegmentedControl + «Рендер ссылок» Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Расширить переключатель quote-picker'а в Profile до SegmentedControl с 3 стилями (FULLSCREEN/MODAL_DOTS/MODAL_BUTTONS) и ортогонального свитчера «Рендер ссылок»; портировать MODAL-варианты из соседнего `android-template-quote`.

**Architecture:** Два независимых CompositionLocal (`LocalQuotePickerStyle` + `LocalLinkRenderEnabled`) — оба `MutableStateFlow` в `AppContainer`. FULLSCREEN остаётся inline overlay'ем (`linkRender` маппится на внутренний `FullScreenVariant.V4/V5`); MODAL_DOTS/BUTTONS — Compose `Dialog` (port из sibling), `linkRender` гейтит footer-chrome и доступ к link-tab.

**Tech Stack:** Kotlin 1.9.22, Compose BOM 2024.02.00, Compose Compiler 1.5.10. `:components.SegmentedControlView` (XML через AndroidView). `MutableStateFlow` для in-process state.

## Global Constraints

- **CLAUDE.md:** правки в `../android-components` НЕ требуются. Plan их не предполагает.
- **NATO-кодовые имена брендов** наружу не торчат — здесь не задействованы.
- **Имена ассетов канонические** — не задействованы.
- **`:feature:*`** не зависит от других `:feature:*` (только через `:core:navigation`-routes). Соблюдается.
- **`:core:model`** без android-зависимостей — новый `QuoteVariant.kt` должен быть pure-Kotlin.
- **CRLF warnings** на Windows игнорируем.
- **Default «Рендер ссылок» = `true`** — это меняет default fullscreen-поведения с V4 (старый default) на V5 (новый default). Acceptable; пользователь подтвердил «по умолчанию ON».
- **Per-session persistence** — `MutableStateFlow`, без DataStore.
- **Spec:** `docs/superpowers/specs/2026-06-30-quote-picker-style-segmented-design.md`.

---

### Task 1: State refactor — `QuotePickerStyle` + `LinkRenderEnabled` + AppContainer + MainActivity providers + FULLSCREEN dispatch

**Files:**
- Replace: `core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt`
- Modify: `app/src/main/java/com/example/template/AppContainer.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt` (imports, providers, picker `when (style)` dispatch + IME gate + tap-barrier gate)
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`
- Modify: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt` (rebind existing switch к новому Local — UI пока не меняется, заменим в Task 2)

**Interfaces produced:**
- `enum QuotePickerStyle { FULLSCREEN, MODAL_DOTS, MODAL_BUTTONS }` в `com.example.template.core.ui`
- `LocalQuotePickerStyle: ProvidableCompositionLocal<MutableStateFlow<QuotePickerStyle>>`
- `LocalLinkRenderEnabled: ProvidableCompositionLocal<MutableStateFlow<Boolean>>`
- `AppContainer.quotePickerStyle: MutableStateFlow<QuotePickerStyle>` (default `FULLSCREEN`)
- `AppContainer.linkRenderEnabled: MutableStateFlow<Boolean>` (default `true`)
- `internal enum class FullScreenVariant { V4, V5 }` в `com.example.template.feature.chatdetail.quotepicker`
- `QuotePickerFullScreen(innerVariant: FullScreenVariant, ...)` — изменённая сигнатура

- [ ] **Step 1.1: Replace `core/ui/.../QuotePickerVariant.kt` файл.** Имя файла оставляем (для меньшего diff в commit'е), содержимое полностью заменяем:

```kotlin
package com.example.template.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

enum class QuotePickerStyle { FULLSCREEN, MODAL_DOTS, MODAL_BUTTONS }

val LocalQuotePickerStyle = staticCompositionLocalOf<MutableStateFlow<QuotePickerStyle>> {
    error("LocalQuotePickerStyle not provided")
}

val LocalLinkRenderEnabled = staticCompositionLocalOf<MutableStateFlow<Boolean>> {
    error("LocalLinkRenderEnabled not provided")
}
```

После этого `QuotePickerVariant` и `LocalQuotePickerVariant` перестают существовать — все ссылки (`ProfileScreen`, `MainActivity`, `AppContainer`, `QuotePickerFullScreen`) сломаются. Это нормально — починим в шагах 1.2-1.5.

- [ ] **Step 1.2: Modify `AppContainer.kt`.** Заменить импорт и поле:

```kotlin
// import com.example.template.core.ui.QuotePickerVariant
import com.example.template.core.ui.QuotePickerStyle
```

```kotlin
// БЫЛО:
// val quotePickerVariant: MutableStateFlow<QuotePickerVariant> =
//     MutableStateFlow(QuotePickerVariant.V4)

// СТАЛО:
/**
 * Стиль quote-picker'а. SegmentedControl в Profile переключает между FULLSCREEN
 * (inline overlay в Activity-окне) и MODAL_DOTS/MODAL_BUTTONS (Compose Dialog).
 * In-process only — не персистится между запусками.
 */
val quotePickerStyle: MutableStateFlow<QuotePickerStyle> =
    MutableStateFlow(QuotePickerStyle.FULLSCREEN)

/**
 * Видимость link-вкладки в picker'е (Switch «Рендер ссылок» в Profile).
 * Default ON — на FULLSCREEN это значит V5-layout (с internal SegmentedControl «Ответ/Ссылка»),
 * на MODAL_* — dots/buttons + link-tab включены.
 */
val linkRenderEnabled: MutableStateFlow<Boolean> =
    MutableStateFlow(true)
```

- [ ] **Step 1.3: Modify `feature/chatdetail/.../quotepicker/QuotePickerFullScreen.kt`.** Полная замена файла (внутренний enum + изменённая сигнатура диспатчера):

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

/**
 * Внутренний enum для дисптача FULLSCREEN-ветки. Не торчит наружу из feature/chatdetail
 * (state-модель `:core:ui` использует только `QuotePickerStyle.FULLSCREEN`).
 */
internal enum class FullScreenVariant { V4, V5 }

/**
 * V3 (Fullscreen) quote picker — inline Compose-overlay в MainActivity ВНЕ AppScaffold.
 * См. подробный комментарий в коммите feature/quote-v3 о причине ОТКАЗА от Compose Dialog
 * (MIUI: statusBarColor/navigationBarColor не применяются в собственном Window Dialog'а).
 *
 * BackHandler установлен ВНУТРИ контента — он имеет доступ к clearSelectionRef для
 * синхронного дисмисса handle-popup'ов.
 */
@Composable
fun QuotePickerFullScreen(
    innerVariant: FullScreenVariant,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
    draftText: String = "",
) {
    when (innerVariant) {
        FullScreenVariant.V4 -> QuoteFullScreenContent(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onCancelReply = onCancelReply,
        )
        FullScreenVariant.V5 -> QuoteV5FullScreenContent(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onCancelReply = onCancelReply,
            draftText = draftText,
        )
    }
}
```

- [ ] **Step 1.4: Modify `MainActivity.kt`.** Несколько изменений:

(a) Заменить импорт `LocalQuotePickerVariant` → 2 новых import:
```kotlin
// БЫЛО: import com.example.template.core.ui.LocalQuotePickerVariant
import com.example.template.core.ui.LocalQuotePickerStyle
import com.example.template.core.ui.LocalLinkRenderEnabled
import com.example.template.core.ui.QuotePickerStyle
import com.example.template.feature.chatdetail.quotepicker.FullScreenVariant
```

(b) В `CompositionLocalProvider` блоке (~line 82-87) заменить одну строку на две:
```kotlin
CompositionLocalProvider(
    LocalAppContainer provides container,
    LocalBitmapCache provides container.bitmapCache,
    LocalThemeToggle provides toggleTheme,
    LocalQuotePickerStyle provides container.quotePickerStyle,
    LocalLinkRenderEnabled provides container.linkRenderEnabled,
) {
```

(c) В блоке picker-рендера (~lines 286-389) гейтить весь IME-блок (`DisposableEffect(pickerVisible)`) и tap-barrier `Box` по `style == QuotePickerStyle.FULLSCREEN`. Заменить участок (примерно strings 295-388):

```kotlin
val styleFlow = LocalQuotePickerStyle.current
val style by styleFlow.collectAsState()
val linkRenderFlow = LocalLinkRenderEnabled.current
val linkRender by linkRenderFlow.collectAsState()

// IME-hide override применяется только когда picker = FULLSCREEN (inline overlay
// в Activity-окне). Modal-варианты — Dialog с собственным Window и
// FLAG_ALT_FOCUSABLE_IM, которые сохраняют IME-state для MessagePanel под ним.
DisposableEffect(pickerVisible, style) {
    if (pickerVisible && style == QuotePickerStyle.FULLSCREEN) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val wasImeVisible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
        val originalSoftInputMode = window.attributes.softInputMode
        val adjustBits = originalSoftInputMode and
            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        window.setSoftInputMode(
            adjustBits or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        controller.hide(WindowInsetsCompat.Type.ime())
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                controller.hide(WindowInsetsCompat.Type.ime())
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            window.setSoftInputMode(originalSoftInputMode)
            if (wasImeVisible) controller.show(WindowInsetsCompat.Type.ime())
        }
    } else {
        onDispose { }
    }
}
val cv = replyCtx
if (pickerVisible && cv != null) {
    val originalMessage = messages.firstOrNull { it.id == cv.originalId }
    LaunchedEffect(originalMessage) {
        if (originalMessage == null) chatVm.dismissQuotePicker()
    }
    if (originalMessage != null) {
        val cacheVersion by container.bitmapCache.version
        val senderPersona = remember(originalMessage.senderId) {
            chatVm.personaForUser(originalMessage.senderId)
        }
        val senderAvatar = remember(senderPersona?.avatarAsset, cacheVersion) {
            senderPersona?.avatarAsset?.let { container.bitmapCache.get(it) }
        }
        val draftText by chatVm.panelDraftText.collectAsState()
        when (style) {
            QuotePickerStyle.FULLSCREEN -> {
                val pickerBarrierInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = pickerBarrierInteraction,
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    QuotePickerFullScreen(
                        innerVariant = if (linkRender) FullScreenVariant.V5 else FullScreenVariant.V4,
                        message = originalMessage,
                        senderPersona = senderPersona,
                        senderAvatar = senderAvatar,
                        isMine = originalMessage.isMine,
                        initialStart = cv.quoteStart ?: 0,
                        initialEnd = cv.quoteEnd ?: 0,
                        draftText = draftText,
                        onConfirm = { start, end ->
                            if (start == end) chatVm.clearQuote()
                            else chatVm.setQuote(start, end)
                            chatVm.dismissQuotePicker()
                        },
                        onDismiss = { chatVm.dismissQuotePicker() },
                        onCancelReply = { chatVm.dismissReplyContext() },
                    )
                }
            }
            QuotePickerStyle.MODAL_DOTS, QuotePickerStyle.MODAL_BUTTONS -> {
                // TODO Task 4: подключить QuotePickerModalHost(style, linkRender, ...)
                // На данный момент Modal-стили выбираются в Profile, но picker не открывается.
            }
        }
    }
}
```

- [ ] **Step 1.5: Modify `feature/profile/.../ProfileScreen.kt`.** Только привязать существующий Switch к `linkRenderEnabled` (UI не меняется — это сделаем в Task 2). Заменить импорт и блок (lines 58-59 и 249-274):

```kotlin
// БЫЛО:
// import com.example.template.core.ui.LocalQuotePickerVariant
// import com.example.template.core.ui.QuotePickerVariant
// СТАЛО:
import com.example.template.core.ui.LocalLinkRenderEnabled
```

В ProfileCard (lines ~249-274) заменить:
```kotlin
ProfileCard(groupBg) {
    val linkRenderFlow = LocalLinkRenderEnabled.current
    val linkRender by linkRenderFlow.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Полноэкранный пикер V5",  // лейбл сменим в Task 2
            style = DSTypography.body1R.toComposeTextStyle(),
            color = appBasic(isDark, 0.9f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = linkRender,
            onCheckedChange = { linkRenderFlow.value = it },
        )
    }
}
```

- [ ] **Step 1.6: Build app.**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "${env:LOCALAPPDATA}\Android\Sdk"
./gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Если падает на unresolved reference — проверить, что все 5 файлов из шагов 1.1-1.5 обновлены.

- [ ] **Step 1.7: Manual smoke test.**

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Открыть приложение, открыть Profile, тогглнуть существующий Switch:
- Switch ON (default) → войти в чат, long-press по тексту сообщения, выбрать «Цитировать фрагмент» — должен открыться V5-fullscreen picker (с SegmentedControl «Ответ/Ссылка»).
- Switch OFF → тот же flow — должен открыться V4-fullscreen picker (без SegmentedControl).

Если default V5 не показывается на cold-open — проверить `linkRenderEnabled` default в AppContainer (`= true`).

- [ ] **Step 1.8: Commit.**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt \
        app/src/main/java/com/example/template/AppContainer.kt \
        app/src/main/java/com/example/template/MainActivity.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt \
        feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt
git commit -m "$(cat <<'EOF'
refactor(quote-picker): QuotePickerVariant → QuotePickerStyle + linkRenderEnabled

Заменить бинарный V4/V5 enum на (style, linkRender) ортогональную пару:
- enum QuotePickerStyle { FULLSCREEN, MODAL_DOTS, MODAL_BUTTONS }
- linkRenderEnabled: Boolean (default ON)
- FULLSCREEN dispatch использует linkRender для V4/V5 выбора через
  internal FullScreenVariant в feature/chatdetail
- MODAL_* пока no-op (Task 4)
- Profile Switch временно привязан к linkRender; UI обновим в Task 2

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Profile UI — `SegmentedControlView` + rename Switch label

**Files:**
- Modify: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt`

**Interfaces consumed:**
- `LocalQuotePickerStyle` (из Task 1)
- `LocalLinkRenderEnabled` (из Task 1)
- `QuotePickerStyle` enum (из Task 1)
- `com.example.components.segmentedcontrol.SegmentedControlView` — XML View с API `configure(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, colorScheme: SegmentedControlColorScheme)`. Поддерживает 2-3 сегмента (3 укладывается).

- [ ] **Step 2.1: Add imports в `ProfileScreen.kt`.**

```kotlin
import android.view.ViewGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import com.example.components.segmentedcontrol.SegmentedControlView
import com.example.template.core.ui.LocalQuotePickerStyle
import com.example.template.core.ui.QuotePickerStyle
```

- [ ] **Step 2.2: Заменить блок Profile-card с picker-настройками** (тот же блок, что в Step 1.5 — теперь в нём ДВЕ строки + divider):

```kotlin
ProfileCard(groupBg) {
    val styleFlow = LocalQuotePickerStyle.current
    val style by styleFlow.collectAsState()
    val linkRenderFlow = LocalLinkRenderEnabled.current
    val linkRender by linkRenderFlow.collectAsState()
    val segmentedScheme = remember(brand, isDark) {
        brand.segmentedControlColorScheme(isDark)
    }
    // Row 1: SegmentedControl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            factory = { ctx ->
                SegmentedControlView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { view ->
                view.configure(
                    labels = listOf("Полноэкранный", "Точки", "Кнопки"),
                    selectedIndex = style.ordinal,
                    onSelect = { idx ->
                        styleFlow.value = QuotePickerStyle.values()[idx]
                    },
                    colorScheme = segmentedScheme,
                )
            },
        )
    }
    // Divider 0.5dp basicColor08
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(appBasic(isDark, 0.08f)),
    )
    // Row 2: Switch «Рендер ссылок»
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Рендер ссылок",
            style = DSTypography.body1R.toComposeTextStyle(),
            color = appBasic(isDark, 0.9f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = linkRender,
            onCheckedChange = { linkRenderFlow.value = it },
        )
    }
}
```

- [ ] **Step 2.3: Build & smoke-test.**

```bash
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Открыть Profile — должен быть один card с SegmentedControl (3 сегмента) сверху, тонкая разделительная линия, ниже строка «Рендер ссылок» со Switch'ом. Тапнуть на каждый сегмент → визуально активируется. Выбрать «Точки» или «Кнопки» — picker не откроется (Task 4). Вернуться на «Полноэкранный», протестировать Switch ON/OFF для V4/V5 — как в Task 1.

- [ ] **Step 2.4: Commit.**

```bash
git add feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt
git commit -m "$(cat <<'EOF'
feat(profile): SegmentedControl + «Рендер ссылок» switch

Заменить single switch «Полноэкранный пикер V5» на ProfileCard с:
- SegmentedControlView [Полноэкранный | Точки | Кнопки] сверху
- 0.5dp divider
- Switch «Рендер ссылок» снизу

Modal-варианты (Точки/Кнопки) пока выбираются, но picker не открывается —
ветка MODAL_* в MainActivity дисптачере no-op'нет до Task 4.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `QuoteVariant` enum в `:core:model`

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt`

- [ ] **Step 3.1: Create `core/model/.../QuoteVariant.kt`:**

```kotlin
package com.example.template.core.model

/**
 * UI-вариант Modal quote-picker'а. Оба — Compose Dialog поверх ChatDetail.
 * Отличие — оформление нижнего Header'а внутри preview-Box:
 *  - [MODAL_DOTS] — Title/Description + индикатор-точки + горизонтальный свайп между
 *    вкладками «Ответ ↔ Ссылка»
 *  - [MODAL_BUTTONS] — Title/Description с круглыми кнопками-стрелками по бокам; свайп
 *    отключён, переключение только тапом по кнопкам
 *
 * Не путать с `core.ui.QuotePickerStyle` — FULLSCREEN сюда не входит, у него собственный
 * inline overlay (без Dialog'а).
 */
enum class QuoteVariant { MODAL_DOTS, MODAL_BUTTONS }
```

- [ ] **Step 3.2: Build.** Only `:core:model` затрагивается:

```bash
./gradlew.bat :core:model:compileKotlin
```

Expected: `BUILD SUCCESSFUL` (компилится в pure-Kotlin JVM, без android-deps).

- [ ] **Step 3.3: Commit.**

```bash
git add core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt
git commit -m "$(cat <<'EOF'
feat(core/model): QuoteVariant enum для Modal-picker'а

MODAL_DOTS / MODAL_BUTTONS — внутренний enum feature/chatdetail Modal'а.
QuotePickerStyle.MODAL_* в :core:ui маппится сюда в QuotePickerModalHost.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Modal shell port — Dialog + Content (без footer'ов) + ModalHost + MainActivity wiring

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt` (copy)
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPreview.kt` (copy)
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPopoverCard.kt` (copy)
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt` (copy + stub footer)
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModalHost.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt` (заменить MODAL_* TODO на вызов QuotePickerModalHost)

**Interfaces consumed:** `QuoteVariant` (Task 3), `LocalQuotePickerStyle`/`LocalLinkRenderEnabled` (Task 1), `replyPreviewText`/`extractQuote` из существующего `QuotePickerShared.kt`, `QuoteBubblePreview.kt` с `restoreSelectionRef` (уже есть).

**Interfaces produced:**
- `@Composable fun QuotePickerModal(message, senderPersona, senderAvatar, isMine, fullText, initialStart, initialEnd, draftText, variant: QuoteVariant, linkRender: Boolean, onConfirm, onDismiss, onCancelReply)` — Dialog wrapper.
- `@Composable fun QuoteModalContent(...same..., variant, linkRender)` — основной content + linkRender гейт.
- `@Composable fun QuotePickerModalHost(style: QuotePickerStyle, linkRender: Boolean, message, ...)` — диспатчер style → QuoteVariant.

- [ ] **Step 4.1: Скопировать 3 файла один-в-один** из sibling'а:

```bash
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPreview.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPreview.kt
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPopoverCard.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPopoverCard.kt
```

Эти файлы используют `com.example.template.core.model.QuoteVariant` (создан в Task 3) и `replyPreviewText/extractQuote` из package `com.example.template.feature.chatdetail.quotepicker` (есть в нашем `QuotePickerShared.kt`). Никаких правок не требуется.

- [ ] **Step 4.2: Скопировать `QuoteModalContent.kt`** и пропатчить под `linkRender`:

```bash
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
```

Затем сделать 3 правки в скопированном файле:

**Правка 1 — сигнатура composable'а:** добавить `linkRender: Boolean` параметр (line ~65):

```kotlin
@Composable
fun QuoteModalContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    @Suppress("UNUSED_PARAMETER") fullText: String,
    initialStart: Int,
    initialEnd: Int,
    draftText: String,
    variant: QuoteVariant,
    linkRender: Boolean,    // ← НОВЫЙ параметр
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
```

**Правка 2 — force selectedTab=0 при OFF.** Сразу после строки `var selectedTab by rememberSaveable { mutableStateOf(0) }`, добавить:

```kotlin
// linkRender OFF → tab=0 принудительно. Defensive guard (Profile взаимоисключителен
// с открытым picker'ом, но если флаг flip'нется через программный путь — снепаем).
LaunchedEffect(linkRender) {
    if (!linkRender) selectedTab = 0
}
```

**Правка 3 — гейт footer-chrome.** Найти в файле блок `when (variant) { QuoteVariant.MODAL_DOTS -> QuoteModalSwipeFooter(...); QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(...) }` и обернуть в `if (linkRender)`. В Task 4 SwipeFooter/ButtonsHeader ещё не портированы — заменить ВРЕМЕННО на `Box {}` stub'ы. В Task 5/6 их вернём:

```kotlin
if (linkRender) {
    when (variant) {
        QuoteVariant.MODAL_DOTS -> Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            // TODO Task 5: QuoteModalSwipeFooter(...)
        }
        QuoteVariant.MODAL_BUTTONS -> Box(Modifier.fillMaxWidth()) {
            // TODO Task 6: QuoteModalButtonsHeader(...)
        }
    }
} else {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        // TODO Task 5: QuoteModalStaticFooter(menuState = menuState)
    }
}
```

**Также** в том же блоке, где геометрия (`previewCornerRadius/contentBottomPad/bottomContentReserve`) ветвится по `variant` — переписать под `(variant, linkRender)` логику:

```kotlin
// БЫЛО:
// val previewCornerRadius = if (variant == QuoteVariant.MODAL_DOTS) 24.dp else 34.dp
// val contentBottomPad = if (variant == QuoteVariant.MODAL_DOTS) 74.dp else 0.dp
// val bottomContentReserve = if (variant == QuoteVariant.MODAL_DOTS) 16.dp else 82.dp

// СТАЛО (linkRender OFF use MODAL_BUTTONS геометрию — static footer вертикально
// центрирован в 82dp reserve):
val previewCornerRadius = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 24.dp else 34.dp
val contentBottomPad = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 74.dp else 0.dp
val bottomContentReserve = if (linkRender && variant == QuoteVariant.MODAL_DOTS) 16.dp else 82.dp
```

**Также:** `val swipeEnabled = variant == QuoteVariant.MODAL_DOTS` — переписать на:
```kotlin
val swipeEnabled = linkRender && variant == QuoteVariant.MODAL_DOTS
```

- [ ] **Step 4.3: Update `QuotePickerModal.kt`** — у него уже есть `variant: QuoteVariant` в сигнатуре; нужно добавить `linkRender: Boolean` и прокинуть в `QuoteModalContent`. Открыть скопированный `QuotePickerModal.kt` и:

(a) В сигнатуре `fun QuotePickerModal(...)` добавить параметр `linkRender: Boolean` рядом с `variant`.

(b) В вызове `QuoteModalContent(...)` внутри Dialog'а — прокинуть `linkRender = linkRender`.

- [ ] **Step 4.4: Create `QuotePickerModalHost.kt`** — диспатчер `QuotePickerStyle → QuoteVariant`:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.QuotePickerStyle

/**
 * Хост Modal-picker'а. Маппит [QuotePickerStyle] (из :core:ui — наружу торчит для
 * Profile/AppContainer) в [QuoteVariant] (из :core:model — внутри Modal-кода).
 * Принимает только MODAL_DOTS/MODAL_BUTTONS; FULLSCREEN-диспатч в [QuotePickerFullScreen].
 */
@Composable
fun QuotePickerModalHost(
    style: QuotePickerStyle,
    linkRender: Boolean,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    draftText: String,
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val variant = when (style) {
        QuotePickerStyle.MODAL_DOTS -> QuoteVariant.MODAL_DOTS
        QuotePickerStyle.MODAL_BUTTONS -> QuoteVariant.MODAL_BUTTONS
        QuotePickerStyle.FULLSCREEN -> error(
            "QuotePickerModalHost called with FULLSCREEN style; use QuotePickerFullScreen instead"
        )
    }
    QuotePickerModal(
        message = message,
        senderPersona = senderPersona,
        senderAvatar = senderAvatar,
        isMine = isMine,
        fullText = fullText,
        initialStart = initialStart,
        initialEnd = initialEnd,
        draftText = draftText,
        variant = variant,
        linkRender = linkRender,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        onCancelReply = onCancelReply,
    )
}
```

- [ ] **Step 4.5: Modify `MainActivity.kt`** — заменить TODO-комментарий в Modal-ветке (см. Step 1.4 (c) item `QuotePickerStyle.MODAL_DOTS, QuotePickerStyle.MODAL_BUTTONS -> { /* TODO */ }`) на:

```kotlin
QuotePickerStyle.MODAL_DOTS, QuotePickerStyle.MODAL_BUTTONS -> {
    // Modal — Compose Dialog, тапы перехватываются в собственном Window;
    // tap-barrier Box и softInputMode-override НЕ применяются (см. DisposableEffect
    // выше, гейт на style == FULLSCREEN).
    QuotePickerModalHost(
        style = style,
        linkRender = linkRender,
        message = originalMessage,
        senderPersona = senderPersona,
        senderAvatar = senderAvatar,
        isMine = originalMessage.isMine,
        fullText = (originalMessage as? Message.Text)?.body
            ?: (originalMessage as? Message.Media)?.caption.orEmpty(),
        initialStart = cv.quoteStart ?: 0,
        initialEnd = cv.quoteEnd ?: 0,
        draftText = draftText,
        onConfirm = { start, end ->
            if (start == end) chatVm.clearQuote()
            else chatVm.setQuote(start, end)
            chatVm.dismissQuotePicker()
        },
        onDismiss = { chatVm.dismissQuotePicker() },
        onCancelReply = { chatVm.dismissReplyContext() },
    )
}
```

Добавить import: `import com.example.template.core.model.Message` (если ещё нет — `Message.Text/Media` уже в файле через ChatDetailViewModel-related code; проверить).

Добавить import: `import com.example.template.feature.chatdetail.quotepicker.QuotePickerModalHost`.

- [ ] **Step 4.6: Build.**

```bash
./gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Возможные ошибки и фиксы:
- `appSurface02 unresolved` → уже есть в `core/ui/.../AppColors.kt` (проверено заранее), импорт должен быть `com.example.template.core.ui.appSurface02`. Если в скопированном `QuoteModalLinkPopoverCard.kt` import формально другой — поправить.
- `brand.linkBubbleColorScheme unresolved` → должно быть в `:components` (используется в V5). Если упало — проверить, что метод существует с той же сигнатурой `(isDark: Boolean) -> LinkBubbleColorScheme`.
- `Spring`/`Animatable`-импорты → должны быть в кодекс'е Kotlin/Compose; если падает — проверить, что compile-зависимости feature:chatdetail включают compose animation.

- [ ] **Step 4.7: Manual smoke-test.**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

В Profile выбрать «Точки», убедиться что Switch «Рендер ссылок» = ON. В чате long-press → «Цитировать фрагмент» → должен открыться MODAL (Dialog) с preview-Box внутри (Google Meet LinkBubble НЕ показывается — мы на tab=0), вверху clip 34dp, ВНИЗУ footer-зона ~82dp пустая (это Task 5 stub).

Тапы вне модала — должен dismiss'нуться. Тап «Цитировать фрагмент» (если есть в menu) — должен apply'нуться. Click «Точки» vs «Кнопки» в Profile — пока визуально не отличается (footer пустой).

Switch OFF — поведение то же (footer пустой), tab всегда 0.

- [ ] **Step 4.8: Commit.**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPreview.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalLinkPopoverCard.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModalHost.kt \
        app/src/main/java/com/example/template/MainActivity.kt
git commit -m "$(cat <<'EOF'
feat(quote-picker/modal): port Dialog shell из android-template-quote

5 файлов из feature/chatdetail/quotepicker:
- QuotePickerModal — Dialog wrapper (blur/dim/IME)
- QuoteModalContent — основной content + linkRender гейт (Task 5/6 footer'ы добавят)
- QuoteModalLinkPreview — LinkBubble Google Meet mock
- QuoteModalLinkPopoverCard — popover «Общая информация / Главное сообщение»
- QuotePickerModalHost — диспатчер QuotePickerStyle → QuoteVariant

MainActivity ветка MODAL_DOTS/BUTTONS теперь дёргает Modal Dialog.
Footer-chrome (dots/buttons) пока stub-Box — портируется в Task 5 + 6.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Port `QuoteModalSwipeFooter` + extract `QuoteModalStaticFooter` + DOTS integration

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalSwipeFooter.kt` (copy from sibling, **+** extract `QuoteModalStaticFooter` в том же файле)
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt` (заменить Step 4.2 stub Box'ы на реальные вызовы)

**Interfaces consumed:** `QuoteMenuState` (уже есть), `LocalAppBrand`/`LocalIsDark`/`appBasic`.

**Interfaces produced:**
- `@Composable internal fun QuoteModalSwipeFooter(selectedTab: Int, menuState: QuoteMenuState, dragOffsetPx: () -> Float, onTabChange: (Int) -> Unit, modifier: Modifier)` — DOTS-вариант footer'а с горизонтальным свайпом
- `@Composable internal fun QuoteModalStaticFooter(menuState: QuoteMenuState, modifier: Modifier)` — OFF-state footer (только Title+Description центрированы)

- [ ] **Step 5.1: Copy `QuoteModalSwipeFooter.kt` из sibling'а:**

```bash
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalSwipeFooter.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalSwipeFooter.kt
```

- [ ] **Step 5.2: Add `QuoteModalStaticFooter` в тот же файл** (внизу, после `QuoteModalSwipeFooter`):

```kotlin
/**
 * Static footer для linkRender=OFF состояния Modal-picker'а. Только Title/Description
 * вертикально центрированы внутри 82dp reserve area, без dots/buttons/свайпа.
 *
 * Title зависит от menuState (миррор sibling SwipeFooter tab=0 логики):
 *  - INITIAL/INITIAL_MINIMAL → «Ответ на сообщение»
 *  - INITIAL_WITH_QUOTE/SELECTING → «Ответ на цитату»
 * Description константный: «Вы можете процитировать фрагмент сообщения».
 */
@Composable
internal fun QuoteModalStaticFooter(
    menuState: QuoteMenuState,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val title = when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        else -> "Ответ на сообщение"
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = appBasic(isDark, 0.9f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Вы можете процитировать фрагмент сообщения",
            color = appBasic(isDark, 0.5f),
            fontFamily = RobotoFontFamilySwipe,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 5.3: Modify `QuoteModalContent.kt`** — заменить Step 4.2 stub Box'ы:

Заменить (в скопированном QuoteModalContent.kt) блок с двумя `Box {}`-stub'ами на:

```kotlin
if (linkRender) {
    when (variant) {
        QuoteVariant.MODAL_DOTS -> QuoteModalSwipeFooter(
            selectedTab = selectedTab,
            menuState = menuState,
            dragOffsetPx = { dragOffset.value },   // существующий Animatable в content'е
            onTabChange = { selectedTab = it },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        QuoteVariant.MODAL_BUTTONS -> Box(Modifier.fillMaxWidth()) {
            // TODO Task 6: QuoteModalButtonsHeader(...)
        }
    }
} else {
    QuoteModalStaticFooter(
        menuState = menuState,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}
```

**Важно:** проверить — у скопированного `QuoteModalContent` уже есть `dragOffset: Animatable<Float>` на уровне функции (sibling использует его для drag). Если нет — добавить:
```kotlin
val dragOffset = remember { Animatable(0f) }
```
И прокинуть в `QuoteModalSwipeFooter`. Также проверить, что свайп-pointer для DOTS живёт на КОРНЕ `QuoteModalContent` (sibling spec говорит «Gesture-state поднят на уровень QuoteModalContent») — если в sibling-скопированном коде свайп уже там, ничего не делать; иначе портировать `pointerInput { detectHorizontalDragGestures }` блок.

- [ ] **Step 5.4: Build & smoke-test.**

```bash
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Profile → выбрать «Точки», Switch ON. В чате → quote-picker открывает Modal с dots-footer внизу. Свайп влево по footer'у/preview'у → tab переключается на «Ссылка» (LinkBubble Google Meet mock). Свайп обратно — на «Ответ».

Switch OFF — dots-footer пропадает, отображается «Ответ на сообщение» / «Вы можете процитировать фрагмент сообщения», свайп не работает. Если есть pre-existing quote (long-press по тексту → «Цитировать фрагмент» → потом quote-picker открывается со снапшотом) — title меняется на «Ответ на цитату».

- [ ] **Step 5.5: Commit.**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalSwipeFooter.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
git commit -m "$(cat <<'EOF'
feat(quote-picker/modal): port SwipeFooter (DOTS) + StaticFooter

QuoteModalSwipeFooter — портирован один-в-один из android-template-quote;
QuoteModalStaticFooter — новый, для linkRender=OFF (только Title+Description
центрированы в 82dp reserve area).

QuoteModalContent dispatches:
  linkRender ON + MODAL_DOTS → SwipeFooter
  linkRender OFF             → StaticFooter
  MODAL_BUTTONS              → пока stub (Task 6)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Port `QuoteModalButtonsHeader` + BUTTONS integration

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalButtonsHeader.kt` (copy)
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt` (заменить Step 5.3 BUTTONS stub)

- [ ] **Step 6.1: Copy `QuoteModalButtonsHeader.kt`:**

```bash
cp ../android-template-quote/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalButtonsHeader.kt \
   feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalButtonsHeader.kt
```

- [ ] **Step 6.2: Modify `QuoteModalContent.kt`** — заменить BUTTONS stub:

```kotlin
QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(
    selectedTab = selectedTab,
    menuState = menuState,
    onTabChange = { selectedTab = it },
    onBack = if (selectedTab > 0) ({ selectedTab = 0 }) else null,
    modifier = Modifier.fillMaxWidth(),
)
```

Проверить сигнатуру скопированного `QuoteModalButtonsHeader` — точные имена параметров могут отличаться (в sibling возможна `onBack: (() -> Unit)?` без явного nullable-guard в content'е). Если sibling уже умеет внутри решать что button показывать по `selectedTab` — упростить вызов:
```kotlin
QuoteVariant.MODAL_BUTTONS -> QuoteModalButtonsHeader(
    selectedTab = selectedTab,
    menuState = menuState,
    onTabChange = { selectedTab = it },
    modifier = Modifier.fillMaxWidth(),
)
```

Открыть `QuoteModalButtonsHeader.kt` (скопированный), сверить сигнатуру, прокинуть правильные параметры.

- [ ] **Step 6.3: Build & smoke-test.**

```bash
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Profile → «Кнопки», Switch ON. Открыть quote-picker — modal с arrow-кнопками по бокам Title/Description. Тап на правую → tab=1 (LinkBubble). Тап на левую → tab=0.

Switch OFF — кнопки пропадают, остаётся static-footer (как в Task 5).

- [ ] **Step 6.4: Commit.**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalButtonsHeader.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
git commit -m "$(cat <<'EOF'
feat(quote-picker/modal): port ButtonsHeader (MODAL_BUTTONS вариант)

QuoteModalButtonsHeader — portirован один-в-один из android-template-quote.
Arrow-кнопки по бокам Title/Description, swipe отключён, переключение tab'ов
тапом по кнопкам.

linkRender=OFF → fallback на StaticFooter (как для MODAL_DOTS).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Финальная полировка + release smoke + CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md` (обновить секцию V5 picker → 3 стиля, описать новый Profile UI)

- [ ] **Step 7.1: Полный матричный smoke-test.** Все 6 комбинаций (style × linkRender):

| Style | linkRender | Ожидание |
|---|---|---|
| FULLSCREEN | ON | V5 fullscreen (internal SegmentedControl «Ответ/Ссылка») |
| FULLSCREEN | OFF | V4 fullscreen (без internal SegmentedControl) |
| MODAL_DOTS | ON | Dialog с dots-footer + swipe |
| MODAL_DOTS | OFF | Dialog со static-footer |
| MODAL_BUTTONS | ON | Dialog с arrow-buttons-footer |
| MODAL_BUTTONS | OFF | Dialog со static-footer (визуально как MODAL_DOTS OFF) |

Для каждой комбинации проверить:
- Открытие через long-press → «Цитировать фрагмент»
- Apply (выбор цитаты) → reply-block в MessagePanel показывает фрагмент
- Cancel reply → возврат в чат без reply
- Outside tap dismiss
- System back

- [ ] **Step 7.2: Update `CLAUDE.md`.** Найти секцию про V5 picker (поиск `quotePickerVariant`) и обновить:

(a) В секции «Реализовано:» (около строки про «V5 picker variant»):

```markdown
- 3-стиль Quote-picker через SegmentedControl в Profile + ortho «Рендер ссылок» switch:
  - `QuotePickerStyle.FULLSCREEN` — inline overlay в Activity-окне; `linkRender` гейтит
    V4 (без internal segmented) vs V5 (с «Ответ/Ссылка» SegmentedControl и LinkBubble tab'ом)
  - `QuotePickerStyle.MODAL_DOTS` — Compose Dialog поверх ChatDetail; dots+swipe между
    «Ответ»/«Ссылка»; `linkRender=false` скрывает chrome → static Title/Description footer
  - `QuotePickerStyle.MODAL_BUTTONS` — Compose Dialog; arrow-buttons по бокам Title/Description;
    swipe отключён, переключение тапом; `linkRender=false` → static footer
  - State в AppContainer: `quotePickerStyle: MutableStateFlow<QuotePickerStyle>` (default
    `FULLSCREEN`) + `linkRenderEnabled: MutableStateFlow<Boolean>` (default `true`)
  - 2 CompositionLocal'а: `LocalQuotePickerStyle` + `LocalLinkRenderEnabled` (см.
    `core/ui/.../QuotePickerVariant.kt` — файл хранит оба)
  - Dispatcher в MainActivity: `when (style) { FULLSCREEN -> QuotePickerFullScreen(innerVariant =
    if (linkRender) V5 else V4, ...); MODAL_* -> QuotePickerModalHost(style, linkRender, ...) }`
  - IME-hide override (softInputMode + Lifecycle ON_RESUME) + tap-barrier Box применяются
    ТОЛЬКО для FULLSCREEN; Modal Dialog имеет FLAG_ALT_FOCUSABLE_IM и своё Window
```

(b) Удалить старую строку про «V5 picker variant — второй стиль ... toggleable per-session через Switch в Profile. ... `LocalQuotePickerVariant` лежит на `AppContainer.quotePickerVariant`...» — заменена выше.

(c) В секции «Spec и план» добавить:
```markdown
- `docs/superpowers/specs/2026-06-30-quote-picker-style-segmented-design.md` — 3-стиль picker
- `docs/superpowers/plans/2026-06-30-quote-picker-style-segmented-implementation.md` — implementation plan
```

- [ ] **Step 7.3: Release build + install.**

```bash
./gradlew.bat :app:assembleRelease
```

Дождаться `BUILD SUCCESSFUL`. Затем:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Открыть приложение, повторить из Step 7.1 беглый smoke (1 раз для каждой строки таблицы — release build уже proguard'ленный, могут вылезти reflection/lambda-related регрессии).

- [ ] **Step 7.4: Commit.**

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(claude.md): отразить 3-стиль quote-picker + Рендер ссылок switch

Полная переработка секции V5 picker'а под новую модель (QuotePickerStyle +
linkRenderEnabled). Описать MODAL_DOTS/MODAL_BUTTONS варианты, диспатчер,
IME-gate для FULLSCREEN.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- [x] State model (enum, 2 Locals, AppContainer, providers) — Task 1
- [x] Profile UI (SegmentedControl + switch) — Task 2
- [x] FULLSCREEN dispatch (internal FullScreenVariant) — Task 1
- [x] `:core:model.QuoteVariant` — Task 3
- [x] Modal Dialog port (5 файлов) — Task 4
- [x] linkRender гейт в QuoteModalContent — Task 4
- [x] QuoteModalSwipeFooter (DOTS) + QuoteModalStaticFooter — Task 5
- [x] QuoteModalButtonsHeader — Task 6
- [x] IME-gate для FULLSCREEN-only — Task 1 (Step 1.4 (c))
- [x] tap-barrier Box для FULLSCREEN-only — Task 1 (Step 1.4 (c))
- [x] QuotePickerModalHost диспатчер — Task 4
- [x] Все 6 тестовых инвариантов — Task 7 матричный smoke
- [x] CLAUDE.md update — Task 7

**Placeholder scan:** «TODO Task N» комментарии остаются ТОЛЬКО до того таска, который их реализует — это часть incremental delivery, а не placeholder в финальном состоянии плана. После Task 6 все TODO исчезают.

**Type consistency:** `QuotePickerStyle` — везде из `:core:ui`; `QuoteVariant` — везде из `:core:model`; `FullScreenVariant` — internal в `feature/chatdetail/quotepicker/`. Сигнатуры `QuotePickerFullScreen(innerVariant = ...)`, `QuotePickerModalHost(style, linkRender, ...)`, `QuoteModalContent(..., variant, linkRender, ...)` согласованы между Tasks 1, 4, 5, 6.

**No spec gap detected.**
