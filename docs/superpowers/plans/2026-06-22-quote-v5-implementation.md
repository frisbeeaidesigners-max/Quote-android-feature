# V5 FullScreen Quote Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить второй полноэкранный quote-picker variant (V5) рядом с существующим V4, переключаемый через Switch в Profile. Логика, FSM, controller, IME полностью переиспользуются — меняется только layout (LEFT-aligned header, MessagePanel-strip + floating popover вместо inline menu rows).

**Architecture:** Hard fork V5-композабла с экстрактом разделяемых хелперов (`PreviewArea`, FSM→items маппинг, row-row) в `QuotePickerShared.kt`. Variant — `enum` в `:core:ui` + `CompositionLocal<MutableStateFlow<...>>`, holder в `AppContainer`, provider в `MainActivity`. Router в `QuotePickerFullScreen.kt` маршрутизирует на V4/V5 по variant'у.

**Tech Stack:** Jetpack Compose, AndroidView interop (`BackgroundPatternView`, `QuoteBubblePreview`, `HeadersView`), Material3 `Switch`, kotlinx `MutableStateFlow`, `staticCompositionLocalOf`.

**Spec:** `docs/superpowers/specs/2026-06-22-quote-v5-design.md`

## Global Constraints

- Не редактируем соседний репо `../android-components`. Все нужные enum-значения (`HeaderConfig.Custom.LeftButton.BACK`, `TitleAlignment.LEFT`) уже существуют — проверено в `HeadersView.kt:73-74`.
- Не модифицируем `ChatDetailViewModel`, `:core:data`, `:core:model`, `MessagePanelView`.
- Не вносим изменений в V4 поведение — V4 продолжает работать as-is после рефакторинга в Task 4.
- `:feature:profile` не должен напрямую зависеть от `:feature:chatdetail` (правило из CLAUDE.md). Поэтому `QuotePickerVariant` enum и `LocalQuotePickerVariant` живут в `:core:ui`.
- Дефолтный variant на старт процесса — `V4`. Persistence между запусками не требуется.
- Тестирование: Compose UI без unit-тестов. После каждого task — `./gradlew :app:assembleDebug` для проверки сборки. Manual smoke в финальном Task 13.

---

## File Structure

**Создать:**
- `core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt` — enum + `LocalQuotePickerVariant`.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerShared.kt` — экстракт из V4: `PreviewArea`, `FullScreenMenuItemSpec`, `MenuCallbacks`, `itemsForState`, `FullScreenMenuRow`, `replyPreviewText`, `FullScreenRobotoFontFamily`.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt` — V5 content composable.

**Изменить:**
- `app/src/main/java/com/example/template/AppContainer.kt` — добавить `quotePickerVariant: MutableStateFlow<QuotePickerVariant>`.
- `app/src/main/java/com/example/template/MainActivity.kt` — обернуть AppScaffold в `CompositionLocalProvider(LocalQuotePickerVariant provides ...)`; на picker-блоке прочитать variant и пробросить.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt` — удалить private `PreviewArea`, `FullScreenMenuItemSpec`, `FullScreenMenuRow`, `FullScreenRobotoFontFamily`, локальные `itemsForState`-блоки; импортировать из shared.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt` — добавить параметр `variant: QuotePickerVariant`, router.
- `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt` — добавить ProfileCard со Switch перед card'ом «Версия приложения».

**НЕ менять:**
- `:components` (вся библиотека дизайн-системы).
- `QuoteBubblePreview.kt`, `QuoteMenuState.kt`, `QuoteModalContent.kt`, `QuotePickerModal.kt`.
- `ChatDetailViewModel.kt` (variant читается на уровне MainActivity, VM ничего о нём не знает).

---

### Task 1: Создать `QuotePickerVariant` enum + `LocalQuotePickerVariant` в `:core:ui`

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt`

**Interfaces:**
- Produces: `enum class QuotePickerVariant { V4, V5 }` + `val LocalQuotePickerVariant: ProvidableCompositionLocal<MutableStateFlow<QuotePickerVariant>>`.

- [ ] **Step 1: Создать файл `QuotePickerVariant.kt`**

```kotlin
package com.example.template.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

enum class QuotePickerVariant { V4, V5 }

val LocalQuotePickerVariant = staticCompositionLocalOf<MutableStateFlow<QuotePickerVariant>> {
    error("LocalQuotePickerVariant not provided")
}
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/QuotePickerVariant.kt
git commit -m "feat(core-ui): add QuotePickerVariant enum + LocalQuotePickerVariant"
```

---

### Task 2: Добавить holder `quotePickerVariant` в AppContainer

**Files:**
- Modify: `app/src/main/java/com/example/template/AppContainer.kt`

**Interfaces:**
- Consumes: `QuotePickerVariant` from Task 1.
- Produces: `AppContainer.quotePickerVariant: MutableStateFlow<QuotePickerVariant>` — initial value `V4`.

- [ ] **Step 1: Добавить import + поле**

В файле `AppContainer.kt`:

Добавить импорты (сверху, к существующим):
```kotlin
import com.example.template.core.ui.QuotePickerVariant
import kotlinx.coroutines.flow.MutableStateFlow
```

После строки `val voicePlaybackController: VoicePlaybackController = VoicePlaybackController()` (~строка 17) добавить:

```kotlin

    /**
     * Dev-toggle для A/B-теста V4 vs V5 fullscreen quote-picker'а.
     * In-process only — переключение делается в ProfileScreen, не персистится.
     */
    val quotePickerVariant: MutableStateFlow<QuotePickerVariant> =
        MutableStateFlow(QuotePickerVariant.V4)
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/template/AppContainer.kt
git commit -m "feat(app): add quotePickerVariant MutableStateFlow to AppContainer"
```

---

### Task 3: Provide `LocalQuotePickerVariant` в `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`

**Interfaces:**
- Consumes: `AppContainer.quotePickerVariant` (Task 2), `LocalQuotePickerVariant` (Task 1).

- [ ] **Step 1: Найти место для CompositionLocalProvider**

Открыть `MainActivity.kt`, найти top-level CompositionLocalProvider блок где providится `LocalAppContainer`. Командно:

Run: `grep -n "CompositionLocalProvider\|LocalAppContainer\|LocalBitmapCache" "C:/Claude/test_project/Quote-android-feature/app/src/main/java/com/example/template/MainActivity.kt"`

Цель: найти top-level `CompositionLocalProvider(...) { ... }` где уже provid'ятся `LocalAppContainer`, `LocalBitmapCache`, `LocalAppBrand` и т.п. Это место, куда добавим ещё один provided value.

- [ ] **Step 2: Добавить provider**

Добавить импорт сверху:
```kotlin
import com.example.template.core.ui.LocalQuotePickerVariant
```

В `CompositionLocalProvider`-блоке добавить к списку provided values:
```kotlin
LocalQuotePickerVariant provides container.quotePickerVariant,
```

Если в файле текущий пример выглядит так:
```kotlin
CompositionLocalProvider(
    LocalAppContainer provides container,
    LocalBitmapCache provides container.bitmapCache,
    /* … другие … */
) { … }
```

То добавить `LocalQuotePickerVariant provides container.quotePickerVariant,` в этом же списке (порядок не важен).

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/template/MainActivity.kt
git commit -m "feat(app): provide LocalQuotePickerVariant in MainActivity"
```

---

### Task 4: Экстракт shared-хелперов в `QuotePickerShared.kt`

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerShared.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`

**Interfaces:**
- Produces:
  - `internal fun PreviewArea(...)` — bubble + pattern + clip rects (signature идентична существующей private `PreviewArea` в `QuoteFullScreenContent.kt:225`).
  - `internal data class FullScreenMenuItemSpec(label: String, iconName: String, isDanger: Boolean = false, onClick: () -> Unit)`.
  - `internal data class MenuCallbacks(onSelectFragment: () -> Unit, onApply: () -> Unit, onCancelReply: () -> Unit, onBack: () -> Unit, onConfirmQuote: () -> Unit, onClearQuote: () -> Unit)`.
  - `internal fun itemsForState(state: QuoteMenuState, callbacks: MenuCallbacks): List<FullScreenMenuItemSpec>`.
  - `internal fun FullScreenMenuRow(item: FullScreenMenuItemSpec, paddingStart: Dp = 24.dp, paddingEnd: Dp = 16.dp)`.
  - `internal fun replyPreviewText(message: Message): String`.
  - `internal val FullScreenRobotoFontFamily: FontFamily`.
- Consumes: ничего нового.

- [ ] **Step 1: Создать `QuotePickerShared.kt` со всем экстрактом**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.designsystem.DSIcon
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class FullScreenMenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

internal data class MenuCallbacks(
    val onSelectFragment: () -> Unit,
    val onApply: () -> Unit,
    val onCancelReply: () -> Unit,
    val onBack: () -> Unit,
    val onConfirmQuote: () -> Unit,
    val onClearQuote: () -> Unit,
)

internal fun itemsForState(
    state: QuoteMenuState,
    cb: MenuCallbacks,
): List<FullScreenMenuItemSpec> = when (state) {
    QuoteMenuState.INITIAL -> listOf(
        FullScreenMenuItemSpec("Выбрать фрагмент", "quote-full", onClick = cb.onSelectFragment),
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
    QuoteMenuState.INITIAL_WITH_QUOTE -> listOf(
        FullScreenMenuItemSpec("Снять выделение", "cancel-quote", onClick = cb.onClearQuote),
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
    QuoteMenuState.SELECTING -> listOf(
        FullScreenMenuItemSpec("Назад", "back", onClick = cb.onBack),
        FullScreenMenuItemSpec("Цитировать фрагмент", "quote", onClick = cb.onConfirmQuote),
    )
    QuoteMenuState.INITIAL_MINIMAL -> listOf(
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
}

internal fun replyPreviewText(message: Message): String = when (message) {
    is Message.Text -> message.body
    is Message.Media -> message.caption?.takeIf { it.isNotEmpty() } ?: "Медиа"
    is Message.Voice -> "Голосовое сообщение"
    is Message.Link -> message.url
    is Message.CallMeet -> "Звонок"
    is Message.System -> ""
}

// Bundled Roboto TTF из :components — на MIUI FontWeight.Medium без явного font fallback'ится
// на Mi Sans Regular, и Medium-вес визуально не виден.
internal val FullScreenRobotoFontFamily: FontFamily = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

@Composable
internal fun FullScreenMenuRow(
    item: FullScreenMenuItemSpec,
    paddingStart: Dp = 24.dp,
    paddingEnd: Dp = 16.dp,
) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)
    val iconColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.55f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            )
            .padding(start = paddingStart, end = paddingEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DsIconImage(name = item.iconName, tint = iconColor, sizeDp = 24)
        Spacer(Modifier.width(24.dp))
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontFamily = FullScreenRobotoFontFamily,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
internal fun DsIconImage(name: String, tint: Color, sizeDp: Int = 24) {
    val ctx = LocalContext.current
    var bitmap by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(name) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, name, sizeDp.toFloat()) as? BitmapDrawable)?.bitmap
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Spacer(Modifier.size(sizeDp.dp))
    }
}

@Composable
internal fun PreviewArea(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    tvRef: MutableState<TextView?>,
    selectAllRef: MutableState<(() -> Unit)?>,
    selectionRef: MutableState<(() -> IntRange?)?>,
    clearSelectionRef: MutableState<(() -> Unit)?>,
    onSelectionStart: () -> Unit,
    onSelectionEnd: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = appSurface01(isDark)

    val previewHandleClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val previewMenuClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val density = LocalDensity.current
    val handleOverflowPx = with(density) { 24.dp.roundToPx() }

    Box(
        modifier = modifier
            .background(previewBg)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                val x = pos.x.toInt()
                val y = pos.y.toInt()
                val menuR = android.graphics.Rect(x, y, x + size.width, y + size.height)
                val handleR = android.graphics.Rect(x, y, x + size.width, y + size.height + handleOverflowPx)
                if (previewMenuClipRect.value != menuR) previewMenuClipRect.value = menuR
                if (previewHandleClipRect.value != handleR) previewHandleClipRect.value = handleR
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BackgroundPatternView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    configure(patternAsset, patternColorScheme)
                }
            },
            update = { v -> v.configure(patternAsset, patternColorScheme) },
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(message.id) {
            snapshotFlow { scrollState.maxValue }
                .first { it > 0 }
                .let { scrollState.scrollTo(it) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Spacer(Modifier.height(8.dp))
            QuoteBubblePreview(
                message = message,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = isMine,
                tvRef = tvRef,
                onQuoteFromActionMode = onConfirm,
                selectAllRef = selectAllRef,
                selectionRef = selectionRef,
                clearSelectionRef = clearSelectionRef,
                onSelectionStart = onSelectionStart,
                onSelectionEnd = onSelectionEnd,
                onCopyFromActionMode = onDismiss,
                initialSelectionStart = initialStart,
                initialSelectionEnd = initialEnd,
                previewScrollState = scrollState,
                previewClipRect = previewHandleClipRect.value,
                previewMenuClipRect = previewMenuClipRect.value,
                previewScrollInProgress = scrollState.isScrollInProgress,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

Sealed-when в `replyPreviewText` должен покрыть все ветки `Message` (Text / Media / Voice / Link / CallMeet / System). Если в проекте есть другие — добавить.

- [ ] **Step 2: Удалить дубли из `QuoteFullScreenContent.kt`**

Открыть `QuoteFullScreenContent.kt`. Удалить (с учётом импортов):
1. Private функцию `PreviewArea(...)` — строки ~224-331.
2. Private data class `FullScreenMenuItemSpec` — строки ~362-367.
3. Private функцию `FullScreenMenuRow(...)` — строки ~426-463.
4. Private функцию `DsIconImage(...)` — строки ~333-353.
5. Private val `FullScreenRobotoFontFamily` — строки ~357-360.
6. Внутри `FullScreenMenuRows`: заменить локальный `when (current) { ... }` блок на вызов `itemsForState(current, callbacks)` (см. ниже).
7. Удалить лишние импорты, ставшие неиспользуемыми (IDE-warnings подскажут).

В блоке `FullScreenMenuRows`, заменить местный мэппинг items на shared `itemsForState`. Заменить тело `FullScreenMenuRows`:

```kotlin
@Composable
private fun FullScreenMenuRows(
    state: QuoteMenuState,
    onSelectFragment: () -> Unit,
    onApply: () -> Unit,
    onCancelReply: () -> Unit,
    onBack: () -> Unit,
    onConfirmQuote: () -> Unit,
    onClearQuote: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val callbacks = MenuCallbacks(
        onSelectFragment = onSelectFragment,
        onApply = onApply,
        onCancelReply = onCancelReply,
        onBack = onBack,
        onConfirmQuote = onConfirmQuote,
        onClearQuote = onClearQuote,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .background(appSurface01(isDark))
            .padding(vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * dir } +
                    fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it * dir } +
                        fadeOut(tween(120)))
            },
            label = "FullScreenMenuFsm",
        ) { current ->
            val items = itemsForState(current, callbacks)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item -> FullScreenMenuRow(item) }
            }
        }
    }
}
```

(`paddingStart=24, paddingEnd=16` — это дефолтные значения у `FullScreenMenuRow` в shared, что соответствует V4-историческим значениям.)

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke V4 на эмуляторе**

Установить APK на эмулятор (если ещё не запущен):
```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

Открыть любой P2P-чат с текстовым сообщением → long-press на bubble → «Цитировать фрагмент в окне» → должен открыться V4 fullscreen picker (как до рефакторинга): centered title, «Применить» в правом верхнем углу, 1-2 inline menu-row'а снизу. Проверить все 4 FSM-state'а: INITIAL (без quote), INITIAL_WITH_QUOTE (с quote), SELECTING (после tap по тексту), INITIAL_MINIMAL (попробовать на Voice/Link bubble).

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerShared.kt feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt
git commit -m "refactor(chatdetail): extract shared helpers to QuotePickerShared.kt"
```

---

### Task 5: Скелет `QuoteV5FullScreenContent.kt`

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Produces: `@Composable fun QuoteV5FullScreenContent(message: Message, senderPersona: Persona?, senderAvatar: Bitmap?, isMine: Boolean, initialStart: Int, initialEnd: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit, onCancelReply: () -> Unit)`.

Скелет: создаём composable с правильной signature + FSM-state-init + refs + BackHandler + placeholder Box (чтобы компилировалось). Layout добавим в следующих task'ах.

- [ ] **Step 1: Создать файл `QuoteV5FullScreenContent.kt`**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appSurface01

@Composable
fun QuoteV5FullScreenContent(
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
    val isDark = LocalIsDark.current

    // FSM init — то же правило, что у V4 (QuoteFullScreenContent.kt:87-100).
    val hasSelectableText = when (message) {
        is Message.Text -> message.body.isNotEmpty()
        is Message.Media -> !message.caption.isNullOrEmpty()
        else -> false
    }
    var menuState by rememberSaveable {
        mutableStateOf(
            when {
                !hasSelectableText -> QuoteMenuState.INITIAL_MINIMAL
                initialStart < initialEnd -> QuoteMenuState.INITIAL_WITH_QUOTE
                else -> QuoteMenuState.INITIAL
            }
        )
    }

    // Selection refs — те же, что у V4 (QuoteFullScreenContent.kt:103-106).
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
    val clearSelectionRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler {
        clearSelectionRef.value?.invoke()
        onDismiss()
    }

    // Placeholder — заменим в следующих task'ах.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :feature:chatdetail:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): add QuoteV5FullScreenContent skeleton"
```

---

### Task 6: Router в `QuotePickerFullScreen` + wire variant в MainActivity

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`

**Interfaces:**
- Consumes: `QuotePickerVariant` (Task 1), `LocalQuotePickerVariant` (Task 3), `QuoteV5FullScreenContent` (Task 5).
- Produces: `QuotePickerFullScreen(variant: QuotePickerVariant, …)` — extended signature.

- [ ] **Step 1: Прочитать текущий QuotePickerFullScreen.kt**

Сначала посмотреть весь файл:

Run: `cat "C:/Claude/test_project/Quote-android-feature/feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt"`

Понять текущую signature и body — нужен router'инг в body, signature расширяем.

- [ ] **Step 2: Расширить QuotePickerFullScreen.kt**

Добавить параметр `variant: QuotePickerVariant` в начало signature. Body заменить на `when (variant)`:

```kotlin
import com.example.template.core.ui.QuotePickerVariant

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
    // BackHandler установлен ВНУТРИ контента (QuoteFullScreenContent / QuoteV5FullScreenContent) —
    // он имеет доступ к clearSelectionRef для синхронного дисмисса handle-popup'ов.
    when (variant) {
        QuotePickerVariant.V4 -> QuoteFullScreenContent(
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
        QuotePickerVariant.V5 -> QuoteV5FullScreenContent(
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
    }
}
```

Сохранить ВСЕ остальные импорты файла (Message, Persona, Bitmap и т.д. — их не трогаем).

- [ ] **Step 3: Пробросить variant из MainActivity**

В `MainActivity.kt` найти блок `QuotePickerFullScreen(...)` (около строки 343):

```bash
grep -n "QuotePickerFullScreen(" "C:/Claude/test_project/Quote-android-feature/app/src/main/java/com/example/template/MainActivity.kt"
```

Перед вызовом добавить:
```kotlin
val variantFlow = LocalQuotePickerVariant.current
val variant by variantFlow.collectAsState()
```

В вызов добавить первым параметром `variant = variant`:
```kotlin
QuotePickerFullScreen(
    variant = variant,
    message = originalMessage,
    senderPersona = senderPersona,
    senderAvatar = senderAvatar,
    isMine = originalMessage.isMine,
    initialStart = cv.quoteStart ?: 0,
    initialEnd = cv.quoteEnd ?: 0,
    onConfirm = { start, end ->
        if (start == end) chatVm.clearQuote()
        else chatVm.setQuote(start, end)
        chatVm.dismissQuotePicker()
    },
    onDismiss = { chatVm.dismissQuotePicker() },
    onCancelReply = { chatVm.dismissReplyContext() },
)
```

Добавить нужные импорты в MainActivity:
```kotlin
import com.example.template.core.ui.LocalQuotePickerVariant
import androidx.compose.runtime.collectAsState
```

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke**

Установить APK:
```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

Открыть picker — должен показать V4 (default). Поведение V4 не должно отличаться от Task 4.

- [ ] **Step 6: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt app/src/main/java/com/example/template/MainActivity.kt
git commit -m "feat(chatdetail): route QuotePickerFullScreen by variant via LocalQuotePickerVariant"
```

---

### Task 7: Switch row в `ProfileScreen`

**Files:**
- Modify: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `LocalQuotePickerVariant` (Task 3), `QuotePickerVariant` (Task 1).

После этого task'а у нас есть рабочий toggle: переключаем в Profile → следующий вызов picker'а откроет V5 (пока placeholder Box из Task 5 — пустой экран без хрома). На следующих task'ах добавим реальный V5 layout итеративно с visual checkpoint'ами.

- [ ] **Step 1: Добавить импорты в `ProfileScreen.kt`**

К существующим импортам добавить:
```kotlin
import androidx.compose.material3.Switch
import com.example.template.core.ui.LocalQuotePickerVariant
import com.example.template.core.ui.QuotePickerVariant
```

- [ ] **Step 2: Добавить новую ProfileCard перед card'ом «Версия приложения»**

Найти в файле блок `ProfileCard(groupBg) { ... Версия приложения ... }` (~строка 246-261). СРАЗУ ПЕРЕД ним вставить:

```kotlin
            ProfileCard(groupBg) {
                val variantFlow = LocalQuotePickerVariant.current
                val variant by variantFlow.collectAsState()
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

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke**

```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

Открыть Profile → должен увидеть row «Полноэкранный пикер V5» со Switch'ом. Toggle ON → открыть picker → должен показать пустой экран (placeholder Box). Toggle OFF → picker снова V4.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt
git commit -m "feat(profile): add V5 quote picker toggle Switch"
```

---

### Task 8: V5 Header (HeaderConfig.Custom с LEFT alignment, BACK button, «Сохранить»)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: `HeaderHost`, `HeaderConfig.Custom`.

- [ ] **Step 1: Проверить, рендерит ли LEFT-alignment description**

Перед написанием нужно понять, как `HeadersView` обрабатывает `titleAlignment = LEFT` с непустым `description`. Открыть `../android-components/components/src/main/java/com/example/components/headers/HeadersView.kt`:

Run: `grep -n "description\|TitleAlignment.LEFT\|TitleAlignment.BOTTOM" "C:/Claude/test_project/android-components/components/src/main/java/com/example/components/headers/HeadersView.kt"`

Изучить, рендерится ли description при `LEFT` (например, в той же 56dp шапке двумя строками) или только при `BOTTOM`.

**Развилка:**
- (A) Если LEFT рендерит description — оставить description в HeaderConfig, не делать отдельный Row.
- (B) Если LEFT НЕ рендерит description — description рендерится отдельной 48dp Compose-Row под HeaderHost. См. Step 3 (вариант B).

Решение зафиксировать в комментарии в коде («HeadersView LEFT alignment рендерит description → один HeaderHost» или «HeadersView LEFT alignment description не рендерит → отдельная Row»).

- [ ] **Step 2: Добавить импорты в `QuoteV5FullScreenContent.kt`**

```kotlin
import androidx.compose.runtime.rememberUpdatedState
import com.example.components.headers.HeadersView
import com.example.template.core.ui.hosts.HeaderHost
```

- [ ] **Step 3: Заменить placeholder Box на Column с HeaderHost + (optional) Description Row + Spacer**

Внутри `Column(... statusBarsPadding ... navigationBarsPadding ...) { ... }` заменить:

```kotlin
        val onLeftClickLatest = rememberUpdatedState(newValue = {
            clearSelectionRef.value?.invoke()
            onDismiss()
        })
        val onRightClickLatest = rememberUpdatedState(newValue = {
            val range = selectionRef.value?.invoke()
            clearSelectionRef.value?.invoke()
            onConfirm(range?.first ?: 0, range?.last ?: 0)
        })
        val headerConfig = remember(menuState) {
            val title = when (menuState) {
                QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
                QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
            }
            HeadersView.HeaderConfig.Custom(
                title = title,
                description = "Вы можете процитировать фрагмент сообщения",
                titleAlignment = HeadersView.HeaderConfig.Custom.TitleAlignment.LEFT,
                leftButton = HeadersView.HeaderConfig.Custom.LeftButton.BACK,
                rightButton = HeadersView.HeaderConfig.Custom.RightButton.TEXT,
                rightButtonType = HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
                rightButtonLabel = "Сохранить",
                onLeftClick = { onLeftClickLatest.value() },
                onRightClick = { onRightClickLatest.value() },
            )
        }
        HeaderHost(config = headerConfig)

        // TODO в следующих task'ах: PreviewArea + BottomStrip + Popover.
        Box(modifier = Modifier.fillMaxSize())
```

**Если на Step 1 выяснилось — вариант (B) (LEFT не рендерит description):**

После `HeaderHost(config = headerConfig)` добавить отдельную Row:

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appSurface01(isDark))
                .padding(horizontal = 16.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Вы можете процитировать фрагмент сообщения",
                style = DSTypography.body1R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
            )
        }
```

И добавить необходимые импорты (`Row`, `padding`, `height`, `Alignment`, `Text`, `DSTypography`, `toComposeTextStyle`, `appBasic`).

Соответственно, в HeaderConfig в варианте (B) `description = null`.

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke**

```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

Включить V5 toggle в Profile → открыть picker → должен показать V5 header: `<` слева, «Ответ на сообщение» слева, «Сохранить» в accent цвете справа. Под header'ом — subtitle «Вы можете процитировать фрагмент сообщения». Под subtitle'ом — пустой Box (placeholder для preview/popover). Top-right «Сохранить» должен закрывать picker. `<` слева — тоже dismiss.

- [ ] **Step 6: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): add V5 Header (LEFT alignment, BACK, Сохранить)"
```

---

### Task 9: V5 PreviewArea wire

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: `PreviewArea` from `QuotePickerShared.kt` (Task 4).

- [ ] **Step 1: Заменить placeholder Box на Box(weight=1f) с PreviewArea**

Заменить `Box(modifier = Modifier.fillMaxSize())` placeholder из Task 8 на:

```kotlin
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PreviewArea(
                message = message,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = isMine,
                initialStart = initialStart,
                initialEnd = initialEnd,
                tvRef = tvRef,
                selectAllRef = selectAllRef,
                selectionRef = selectionRef,
                clearSelectionRef = clearSelectionRef,
                onSelectionStart = {
                    if (menuState != QuoteMenuState.SELECTING) {
                        menuState = QuoteMenuState.SELECTING
                    }
                },
                onSelectionEnd = {
                    if (menuState == QuoteMenuState.SELECTING) {
                        menuState = if (initialStart < initialEnd) QuoteMenuState.INITIAL_WITH_QUOTE
                        else QuoteMenuState.INITIAL
                    }
                },
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
            // TODO Task 11: popover внутри этого Box'а с align=BottomStart.
        }
```

Добавить импорты:
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
```
(остальные уже должны быть).

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke**

```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

V5 picker → должен показать header + pattern background + scrollable bubble в нижней части preview area. Bubble должен быть тапабельный/выделяемым (text selection handles работают), скролл preview работает.

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): wire V5 PreviewArea with FSM transitions"
```

---

### Task 10: V5 BottomStrip (MessagePanel-mock 52dp)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: `replyPreviewText`, `DsIconImage` from `QuotePickerShared.kt` (Task 4).

В этом task'е добавляем visual strip с reply-target. Popover toggle на иконке ещё не работает (Task 11) — но Box-с-иконкой кликабельный, готов под будущий toggle.

- [ ] **Step 1: Добавить state `popoverOpen` (initial=true) и private composable `BottomStrip`**

В `QuoteV5FullScreenContent` ДО block'а с HeaderHost (но ПОСЛЕ FSM init) добавить:

```kotlin
    // Popover-state — initial OPEN. Полная open/close-логика — Task 11.
    var popoverOpen by rememberSaveable { mutableStateOf(true) }
```

В конце `Column` (после `Box(weight=1f) { PreviewArea(...) }`) добавить вызов:

```kotlin
        val senderName = remember(senderPersona) {
            buildString {
                append(senderPersona?.firstName.orEmpty())
                senderPersona?.lastName?.takeIf { it.isNotEmpty() }?.let { append(' '); append(it) }
            }.trim()
        }
        val previewText = remember(message) { replyPreviewText(message) }
        BottomStrip(
            senderName = senderName,
            previewText = previewText,
            popoverOpen = popoverOpen,
            onIconClick = { popoverOpen = !popoverOpen },
        )
```

В конец файла добавить private composable:

```kotlin
@Composable
private fun BottomStrip(
    senderName: String,
    previewText: String,
    popoverOpen: Boolean,
    onIconClick: () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val borderColor = appBasic(isDark, 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appSurface01(isDark))
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .appClickable(onClick = onIconClick),
            contentAlignment = Alignment.Center,
        ) {
            if (popoverOpen) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appBasic(isDark, 0.08f)),
                )
            }
            DsIconImage(name = "reply-setting-n", tint = appBasic(isDark, 0.55f), sizeDp = 24)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
        ) {
            Text(
                text = senderName,
                style = DSTypography.subhead4M.toComposeTextStyle(),
                color = Color(brand.accentColor(isDark)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText,
                style = DSTypography.subhead2R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

Добавить необходимые импорты в файл:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appClickable
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke**

```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

V5 picker → должен показать header + preview + bottom strip (52dp белый, top-border 1dp basic08, `reply-setting-n` icon с подложкой 36dp basic08 (т.к. popoverOpen=true initial), name+preview справа). Tap по иконке — должен toggling state (визуально подложка появляется/исчезает), но popover ещё не реализован (Task 11).

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): add V5 BottomStrip (MessagePanel context-toolbar mock)"
```

---

### Task 11: V5 PopoverCard + open/close logic (model B) + auto-open на SELECTING

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: `FullScreenMenuRow`, `FullScreenMenuItemSpec`, `MenuCallbacks`, `itemsForState` from `QuotePickerShared.kt` (Task 4).

В этом task'е добавляем floating popover в preview Box с AnimatedVisibility для open/close + AnimatedContent для смены FSM-items. Tap-outside (по preview-area вне popover'а) тоже закрывает popover. Auto-open в SELECTING обеспечивается через LaunchedEffect.

- [ ] **Step 1: Добавить LaunchedEffect для auto-open в SELECTING**

После `var popoverOpen by rememberSaveable...` добавить:

```kotlin
    LaunchedEffect(menuState) {
        if (menuState == QuoteMenuState.SELECTING) popoverOpen = true
    }
```

- [ ] **Step 2: Добавить tap-outside detector на Box(weight=1f)**

Изменить `Box(modifier = Modifier.fillMaxWidth().weight(1f)) { PreviewArea(...) }` на:

```kotlin
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(popoverOpen) {
                    if (popoverOpen) {
                        detectTapGestures(onTap = { popoverOpen = false })
                    }
                },
        ) {
            PreviewArea(
                /* … все параметры как раньше … */
            )
            // Popover слой — рисуется поверх preview.
            val callbacks = MenuCallbacks(
                onSelectFragment = {
                    selectAllRef.value?.invoke()
                    menuState = QuoteMenuState.SELECTING
                },
                onApply = {
                    val range = selectionRef.value?.invoke()
                    clearSelectionRef.value?.invoke()
                    onConfirm(range?.first ?: 0, range?.last ?: 0)
                },
                onCancelReply = {
                    clearSelectionRef.value?.invoke()
                    onCancelReply()
                },
                onBack = {
                    clearSelectionRef.value?.invoke()
                    menuState = QuoteMenuState.INITIAL
                },
                onConfirmQuote = {
                    val range = selectionRef.value?.invoke()
                    clearSelectionRef.value?.invoke()
                    onConfirm(range?.first ?: 0, range?.last ?: 0)
                },
                onClearQuote = {
                    clearSelectionRef.value?.invoke()
                    menuState = QuoteMenuState.INITIAL
                },
            )
            AnimatedVisibility(
                visible = popoverOpen,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp),
                enter = fadeIn(tween(150)) +
                    slideInVertically(tween(220, FastOutSlowInEasing)) { it / 4 },
                exit = fadeOut(tween(120)),
            ) {
                PopoverCard(state = menuState, callbacks = callbacks)
            }
        }
```

- [ ] **Step 3: Добавить private composable `PopoverCard`**

В конец файла добавить:

```kotlin
@Composable
private fun PopoverCard(
    state: QuoteMenuState,
    callbacks: MenuCallbacks,
) {
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(appBasic(isDark, 0.08f))
            .padding(vertical = 2.dp),
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(220, FastOutSlowInEasing)) { it * dir } +
                    fadeIn(tween(120))) togetherWith
                    (slideOutHorizontally(tween(220, FastOutSlowInEasing)) { -it * dir } +
                        fadeOut(tween(120)))
            },
            label = "V5PopoverFsm",
        ) { current ->
            val items = itemsForState(current, callbacks)
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    FullScreenMenuRow(
                        item = item,
                        paddingStart = 16.dp,
                        paddingEnd = 16.dp,
                    )
                    if (i < items.lastIndex) {
                        Divider(
                            thickness = 0.5.dp,
                            color = appBasic(isDark, 0.08f),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Добавить импорты**

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.ui.input.pointer.pointerInput
```

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual smoke (полный V5)**

```bash
./gradlew :app:installDebug -Pbrand=foxtrot
```

V5 picker → должен показать ВСЕ слои: header + preview + popover поверх preview (нижний-левый угол с 8dp margin'ами) + bottom strip. Initial state: popover OPEN с 2 items («Выбрать фрагмент» / «Отменить ответ»). Tap по иконке `reply-setting-n` → popover закрывается с fadeOut. Tap по pattern bg в preview area → popover закрывается. Tap по иконке снова → popover открывается. Tap «Выбрать фрагмент» → переход в SELECTING (handles появляются на тексте), popover автоматически открыт (если был закрыт — auto-open сработал), показывает «Назад / Цитировать фрагмент». Tap «Цитировать фрагмент» → commit + picker закрывается. Tap top-right «Сохранить» в SELECTING — тоже commit. Tap «Назад» в SELECTING — возврат в INITIAL.

Если tap-outside не закрывает popover при тапе по empty preview area, и при этом tap по баблу работает корректно — диагностика: проверить, что pointerInput с детектTapGestures повешен правильно. Если в Compose-режиме событие consume'ит scroll или pattern AndroidView — fallback: переместить detect на отдельный transparent `Box(Modifier.fillMaxSize())` под `PreviewArea`-блоком, но НАД pattern background. Это решение в Task 11 fallback'ом.

- [ ] **Step 7: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): add V5 popover with FSM transitions + tap-outside close"
```

---

### Task 12: Acceptance smoke test

**Files:** none (verification only)

Проверить каждый acceptance criterion из spec §Acceptance criteria. Этот task НЕ заканчивается commit'ом — это просто финальная проверка.

- [ ] **Criterion 1: Toggle V5 → open V5 chrome**

Profile → flip Switch «Полноэкранный пикер V5» → open P2P-чат → long-press на text bubble → «Цитировать фрагмент в окне» → должен открыться V5: LEFT header, `<` back, «Сохранить» в accent, popover с 2 items, bottom strip с reply-target.

- [ ] **Criterion 2: Toggle off → V4**

Снять Switch → open picker → должен открыться V4 (centered title, «Применить» правый верх, 1-2 inline menu row'а 136dp снизу).

- [ ] **Criterion 3: Tap reply-setting-n → toggle popover**

V5 open → tap по `reply-setting-n` в bottom strip → popover закрывается, active state иконки (36dp basic08 circle) исчезает. Tap снова → открывается, circle появляется.

- [ ] **Criterion 4: Tap pattern bg → close popover**

V5 open → tap по пустой области preview area (где видна pattern background) → popover закрывается.

- [ ] **Criterion 5: SELECTING flow**

V5 open → tap «Выбрать фрагмент» → FSM в SELECTING (handles на тексте), popover open с «Назад / Цитировать фрагмент». Tap «Цитировать фрагмент» → range commit, picker dismiss. Reply preview в MessagePanel показывает quote.

- [ ] **Criterion 6: Top-right Сохранить commits**

V5 в любом state → tap «Сохранить» в top-right → коммит range (в INITIAL/INITIAL_MINIMAL — без quote, в WITH_QUOTE/SELECTING — с quote).

- [ ] **Criterion 7: Top-left back + system back**

V5 → tap `<` в top-left → dismiss без коммита. V5 → system back button → dismiss без коммита.

- [ ] **Criterion 8: INITIAL_MINIMAL state (Voice/Link/CallMeet)**

Открыть picker на Voice message → V5 → должен показать popover с одним row «Отменить ответ» (без «Выбрать фрагмент»).

- [ ] **Criterion 9: V4 regression check**

Toggle off V5 → проверить ВСЕ 4 FSM-state'а V4 как в task 4 (INITIAL без quote, WITH_QUOTE с quote, SELECTING, MINIMAL на Voice). V4 не должен регрессировать после рефакторинга.

- [ ] **Если все criteria PASS — финальный commit (если есть незакоммиченные правки)**

```bash
git status
# если pristine — переходить к Step ниже; если есть мелкие правки — commit
```

- [ ] **Финальный housekeeping commit (опционально)**

Если по результатам smoke появились мелкие fixup'ы — закоммитить их. В противном случае task 12 — просто чек-лист без commit'ов.

---

## Возможные fallback'ы / risk-zoны (для эскалации к юзеру)

1. **HeaderConfig.Custom + LEFT alignment не рендерит description** — fallback в Task 8 Step 3 (B). Если ни того, ни другого не получается отрисовать — эскалировать к юзеру, обсудить либо правку в `:components` (с отдельной веткой в `../android-components` per CLAUDE.md), либо упрощение UI (например, отказаться от subtitle в V5 — но это уже не соответствует Figma и должно быть согласовано).

2. **Tap-outside не работает корректно** (popover не закрывается на тап вне, ИЛИ перехватывает tap'ы баббла нарушая text selection) — фолбек в Task 11 Step 6: переместить `pointerInput` на отдельный transparent overlay внутри `Box(weight=1f)`. Если и это не помогает — эскалация к юзеру (вариант C: отказаться от tap-outside-close, оставить только tap по иконке как способ закрыть popover).

3. **AnimatedContent в popover'е некорректно работает при transition SELECTING→INITIAL_WITH_QUOTE** (orderly размер popover'а может скачкообразно меняться) — если визуально некрасиво, обернуть PopoverCard в `Modifier.animateContentSize()`.

4. **HeadersView.HeaderConfig.Custom signature не имеет того поля, что я предполагаю** (например, `rightButtonLabel` vs `rightButtonText`) — посмотреть точный API V4 в `QuoteFullScreenContent.kt:144-154` и поправить под актуальные имена параметров.
