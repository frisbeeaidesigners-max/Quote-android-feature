# V4 FullScreen Quote Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Стилизовать V4 (FullScreen) quote picker под Figma: header (back/title/Apply) + subtitle + full-width preview area (pattern + bubble) + full-width menu rows внизу.

**Architecture:** `QuotePickerFullScreen.kt` становится Dialog-обёрткой с тем же IME pattern что V2 Modal. `QuoteFullScreenContent.kt` (NEW) держит FSM state + refs + 4 секции layout. Переиспользует existing `QuoteBubblePreview` и `QuoteMenuState` — изменяется только rendering меню (full-width rows вместо floating-box V2).

**Tech Stack:** Jetpack Compose, AndroidView (BackgroundPatternView, QuoteBubblePreview wrapper), Material3 для primitive'ов, Dialog с DialogWindowProvider для IME flags.

**Spec:** `docs/superpowers/specs/2026-05-28-quote-picker-fullscreen-design.md`

---

## File Structure

**Создать:**
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt` — root content: Column { Header + Subtitle + PreviewArea + MenuRows }, FSM state, refs (tvRef, selectAllRef, selectionRef).

**Изменить:**
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt` — расширить signature (добавить message/persona/avatar/isMine/onCancelReply), заменить body на Dialog с IME flags + QuoteFullScreenContent.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt` — передать новые параметры в FULLSCREEN ветку.

**НЕ менять:**
- `QuoteBubblePreview.kt` — переиспользуется как есть.
- `QuoteMenu.kt` — остаётся для V2 (floating-box).
- `QuoteModalContent.kt`, `QuotePickerModal.kt`, `QuotePickerSheet.kt`, `QuotePickerContent.kt` — не трогаем.
- ViewModel, FSM логика, controller, integration.

---

### Task 1: Расширить QuotePickerFullScreen signature + добавить overlay wiring

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt`

Этот task расширяет signature без рендеринга — создаёт chassis на будущее. Body picker'а — временный Box, который позже заменим на `QuoteFullScreenContent`.

- [ ] **Step 1: Заменить body QuotePickerFullScreen.kt полностью**

Заменить ВСЁ содержимое файла на:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

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
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        @Suppress("UNUSED_VARIABLE") val density = LocalDensity.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            // IME state preserved across open/close: тот же pattern что в QuotePickerModal.
            // FLAG_ALT_FOCUSABLE_IM — Dialog получает focus, IME остаётся прикреплён к MessagePanel.
            // SOFT_INPUT_STATE_UNCHANGED — не меняем IME state при mount/dismiss.
            // ADJUST_RESIZE — окно ресайзится под IME, menu rows поднимаются выше клавы.
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            // Без blur — full-screen перекрывает всё. Без dim.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setDimAmount(0f)
            }
        }
        // TODO Task 2: заменить на QuoteFullScreenContent(...)
        Box(modifier = Modifier.fillMaxSize())
    }
}
```

Старые импорты (Material3 Surface/TopAppBar/Icon/IconButton/Close/Text, QuotePickerContent reference) удаляем — Task 1 их не использует.

- [ ] **Step 2: Обновить QuotePickerOverlay.kt — передать новые параметры в FULLSCREEN ветку**

В файле `QuotePickerOverlay.kt` найти строки:

```kotlin
        QuoteVariant.FULLSCREEN -> QuotePickerFullScreen(
            fullText, initialStart, initialEnd, onConfirm, onDismiss,
        )
```

Заменить на:

```kotlin
        QuoteVariant.FULLSCREEN -> QuotePickerFullScreen(
            message, senderPersona, senderAvatar, isMine,
            fullText, initialStart, initialEnd,
            onConfirm, onDismiss, onCancelReply,
        )
```

- [ ] **Step 3: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`. APK устанавливается на устройство.

- [ ] **Step 4: Проверить визуально — V4 открывается пустым full-screen**

В app переключиться на FULLSCREEN через dev-toggle (Calls → SegmentedControl → Fullscreen). Открыть chat → нажать на reply-icon у сообщения → должен открыться пустой full-screen Dialog.

Expected: Чёрный/тёмный full-screen экран без контента. Кнопка system back закрывает picker.

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt
git commit -m "feat(quote/fullscreen): расширить signature + Dialog с IME flags

QuotePickerFullScreen теперь принимает message/persona/avatar/isMine/onCancelReply
(parity с V2 Modal). Body — заглушка Box, заменится в Task 2 на QuoteFullScreenContent.
Dialog properties: usePlatformDefaultWidth=false, decorFitsSystemWindows=false.
IME flags: FLAG_ALT_FOCUSABLE_IM + SOFT_INPUT_STATE_UNCHANGED + ADJUST_RESIZE."
```

---

### Task 2: Создать QuoteFullScreenContent skeleton — Column + 4 секции с placeholder'ами

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt` (вызвать новый content)

Создаём caркас content'а: Column из 4 частей (Header / Subtitle / PreviewArea / MenuRows). Каждая часть — `Box` с цветным фоном для визуальной проверки, заменится в последующих task'ах.

- [ ] **Step 1: Создать новый файл QuoteFullScreenContent.kt**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appSurface01

@Composable
fun QuoteFullScreenContent(
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
    @Suppress("UNUSED_VARIABLE") val brand = LocalAppBrand.current

    // FSM state — initial по тому же правилу что в QuoteModalContent.
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

    // Refs для QuoteBubblePreview (как в QuoteModalContent).
    @Suppress("UNUSED_VARIABLE") val tvRef = remember { mutableStateOf<TextView?>(null) }
    @Suppress("UNUSED_VARIABLE") val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    @Suppress("UNUSED_VARIABLE") val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header — Task 3
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.Red.copy(alpha = 0.2f)),
        ) { Text("HEADER (state=$menuState)") }

        // Subtitle — Task 4
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.Green.copy(alpha = 0.2f)),
        ) { Text("SUBTITLE") }

        // Preview area — Task 5
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Blue.copy(alpha = 0.2f)),
        ) { Text("PREVIEW (isMine=$isMine, sender=${senderPersona?.firstName})") }

        // Menu rows — Task 6
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color.Yellow.copy(alpha = 0.2f)),
        ) { Text("MENU (3 items in INITIAL)") }
    }
}
```

- [ ] **Step 2: Заменить заглушку Box в QuotePickerFullScreen.kt на вызов QuoteFullScreenContent**

В `QuotePickerFullScreen.kt` найти:

```kotlin
        // TODO Task 2: заменить на QuoteFullScreenContent(...)
        Box(modifier = Modifier.fillMaxSize())
```

Заменить на:

```kotlin
        QuoteFullScreenContent(
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
```

Также удалить неиспользуемые импорты `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Modifier` если они больше не нужны (Box/fillMaxSize/Modifier остаются для Dialog'а — оставить). Также удалить параметр `fullText` если он больше не используется внутри QuotePickerFullScreen.

**Проверка**: в текущей версии `fullText` параметр был unused в Dialog body. Оставляем его в signature (`fullText: String`) потому что overlay передаёт его, но НЕ передаём в QuoteFullScreenContent (там не нужен — `message.body`/`message.caption` дают тот же текст).

- [ ] **Step 3: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Визуальная проверка**

Открыть V4 picker → видны 4 цветных секции (red HEADER / green SUBTITLE / blue PREVIEW (большая) / yellow MENU 160dp).

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt
git commit -m "feat(quote/fullscreen): skeleton QuoteFullScreenContent с FSM state + refs

Column из 4 цветных секций (Header/Subtitle/Preview/Menu) — placeholder'ы под
последующие task'и. FSM menuState + tvRef/selectAllRef/selectionRef уже на месте,
будут wire'нуты в Task 7. statusBarsPadding+navigationBarsPadding для full-screen."
```

---

### Task 3: Header (44dp)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`

Header = Row из трёх частей: left back-icon (36×36 button), center title (динамичный), right "Применить" (visible/hidden по state).

- [ ] **Step 1: Добавить imports в QuoteFullScreenContent.kt**

В начале файла, к существующим imports, добавить:

```kotlin
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.appBasic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: Заменить HEADER заглушку на FullScreenHeader composable**

В QuoteFullScreenContent.kt, найти секцию `// Header — Task 3` вместе с её `Box(...) { Text("HEADER (state=$menuState)") }`. Заменить на:

```kotlin
        FullScreenHeader(
            menuState = menuState,
            onDismiss = onDismiss,
            onApply = {
                val range = selectionRef.value?.invoke()
                onConfirm(range?.first ?: 0, range?.last ?: 0)
            },
        )
```

- [ ] **Step 3: Добавить FullScreenHeader composable + helper'ы в конец файла**

После закрывающей `}` функции `QuoteFullScreenContent`, добавить:

```kotlin
@Composable
private fun FullScreenHeader(
    menuState: QuoteMenuState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current
    val title = when (menuState) {
        QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
        QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
    }
    val showApply = menuState != QuoteMenuState.SELECTING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button 36×36
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            DsIconImage(name = "back", tint = appBasic(isDark, 0.9f), sizeDp = 24)
        }
        Spacer(Modifier.width(8.dp))
        // Title
        Text(
            text = title,
            color = appBasic(isDark, 0.9f),
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontFamily = FullScreenRobotoFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        // Apply (visible if not SELECTING)
        if (showApply) {
            Text(
                text = "Применить",
                color = Color(brand.accentColor(isDark)),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontFamily = FullScreenRobotoFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onApply)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DsIconImage(name: String, tint: Color, sizeDp: Int = 24) {
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

// Bundled Roboto TTF из :components — на MIUI FontWeight.Medium без явного font fallback'ится
// на Mi Sans Regular, и Medium-вес визуально не виден.
private val FullScreenRobotoFontFamily = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)
```

Note: `QuoteMenuState` уже импортирован из того же package (`QuoteMenu.kt`), отдельный import не нужен.

- [ ] **Step 4: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Визуальная проверка**

Открыть V4 picker → видна шапка: ⟨ (back arrow) + "Ответ на сообщение" + "Применить" (accent) в правом верху. Тап на ⟨ закрывает picker. Тап на "Применить" → reply без quote (потому что selection ещё не реализован = (0,0) → clearQuote).

- [ ] **Step 6: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt
git commit -m "feat(quote/fullscreen): header с back + dynamic title + Apply

Row 44dp: ⟨ back-icon (onClick=onDismiss всегда) + title (динамика по menuState:
\"Ответ на сообщение\" / \"Ответ на цитату\") + \"Применить\" (accent, hidden в SELECTING).
Helper DsIconImage переиспользует DSIcon.named pattern из QuoteMenu.kt."
```

---

### Task 4: Subtitle (44dp)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`

Статичный subtitle. Тривиальная задача.

- [ ] **Step 1: Заменить SUBTITLE заглушку на SubtitleRow**

В QuoteFullScreenContent.kt, найти секцию `// Subtitle — Task 4` вместе с её Box. Заменить на:

```kotlin
        SubtitleRow()
```

- [ ] **Step 2: Добавить SubtitleRow composable**

После `FullScreenHeader` composable, добавить:

```kotlin
@Composable
private fun SubtitleRow() {
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Вы можете процитировать фрагмент сообщения",
            color = appBasic(isDark, 0.5f),
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontFamily = FullScreenRobotoFontFamily,
            fontWeight = FontWeight.Normal,
        )
    }
}
```

- [ ] **Step 3: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Визуальная проверка**

Под header'ом виден subtitle "Вы можете процитировать фрагмент сообщения" (basic_50, 14sp).

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt
git commit -m "feat(quote/fullscreen): subtitle 44dp статичный

basic_50 14/16 Roboto Regular, padding-horizontal 16dp."
```

---

### Task 5: Preview area — pattern + bubble + refs wiring

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`

Box(weight 1f) с BackgroundPatternView + scrollable Column с QuoteBubblePreview (bottom-aligned). Refs (tvRef, selectAllRef, selectionRef) пробрасываются в preview.

- [ ] **Step 1: Добавить imports**

К существующим imports в QuoteFullScreenContent.kt добавить:

```kotlin
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import kotlinx.coroutines.flow.first
```

(`Spacer`, `padding` уже могли быть импортированы — Kotlin compiler покажет duplicate warning, удалить дубликаты).

- [ ] **Step 2: Заменить PREVIEW заглушку на PreviewArea**

В QuoteFullScreenContent.kt, найти секцию `// Preview area — Task 5` вместе с её Box. Заменить на:

```kotlin
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
            onConfirm = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
```

- [ ] **Step 3: Убрать `@Suppress("UNUSED_VARIABLE")` с tvRef/selectAllRef/selectionRef**

Теперь они used. В QuoteFullScreenContent, найти 3 строки:

```kotlin
    @Suppress("UNUSED_VARIABLE") val tvRef = remember { mutableStateOf<TextView?>(null) }
    @Suppress("UNUSED_VARIABLE") val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    @Suppress("UNUSED_VARIABLE") val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
```

Заменить на:

```kotlin
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
```

- [ ] **Step 4: Убрать `@Suppress("UNUSED_VARIABLE")` с brand**

`brand` теперь используется в PreviewArea для pattern. Найти:

```kotlin
    @Suppress("UNUSED_VARIABLE") val brand = LocalAppBrand.current
```

Заменить на:

```kotlin
    val brand = LocalAppBrand.current
```

- [ ] **Step 5: Добавить PreviewArea composable**

После `SubtitleRow` composable, добавить:

```kotlin
@Composable
private fun PreviewArea(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    tvRef: androidx.compose.runtime.MutableState<TextView?>,
    selectAllRef: androidx.compose.runtime.MutableState<(() -> Unit)?>,
    selectionRef: androidx.compose.runtime.MutableState<(() -> IntRange?)?>,
    onConfirm: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = Color(brand.messageScreenBackground(isDark))

    Box(modifier = modifier.background(previewBg)) {
        // Pattern background — AndroidView, full-width full-height.
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

        // Bottom-aligned scrollable bubble.
        val scrollState = rememberScrollState()
        LaunchedEffect(message.id) {
            snapshotFlow { scrollState.maxValue }
                .first { it > 0 }
                .let { scrollState.scrollTo(it) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
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
                initialSelectionStart = initialStart,
                initialSelectionEnd = initialEnd,
            )
        }
    }
}
```

- [ ] **Step 6: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Визуальная проверка**

Preview area показывает: brand pattern + бабл сообщения внизу (bottom-aligned). Если сообщение длинное — можно прокрутить вверх. Long-press на текст → выделение слова + handles (custom SelectionController). Top "Применить" с активной selection → закрывает picker и применяет quote. Header back ⟨ → закрывает picker.

- [ ] **Step 8: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt
git commit -m "feat(quote/fullscreen): preview area — pattern + scrollable bubble

Box(weight 1f) с BackgroundPatternView (full-width) и Column.verticalScroll.
QuoteBubblePreview bottom-aligned, refs (tvRef/selectAllRef/selectionRef) wire'нуты.
Long-press selection, top Apply, initial range restore работают через
переиспользованный QuoteBubblePreview без изменений."
```

---

### Task 6: Menu rows — FullScreenMenuRows composable

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt`

Заменяет 160dp yellow заглушку на актуальное меню. AnimatedContent для FSM transitions, full-width rows 48dp каждый, ripple на всю строку.

- [ ] **Step 1: Добавить imports**

```kotlin
import android.text.Selection
import android.text.Spannable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
```

- [ ] **Step 2: Заменить MENU заглушку на FullScreenMenuRows**

В QuoteFullScreenContent.kt, найти секцию `// Menu rows — Task 6` вместе с её Box. Заменить на:

```kotlin
        FullScreenMenuRows(
            state = menuState,
            onSelectFragment = {
                selectAllRef.value?.invoke()
                menuState = QuoteMenuState.SELECTING
            },
            onApply = {
                val range = selectionRef.value?.invoke()
                onConfirm(range?.first ?: 0, range?.last ?: 0)
            },
            onCancelReply = onCancelReply,
            onBack = {
                tvRef.value?.let { tv ->
                    val spannable = tv.text as? Spannable ?: return@let
                    Selection.removeSelection(spannable)
                }
                menuState = QuoteMenuState.INITIAL
            },
            onConfirmQuote = {
                val range = selectionRef.value?.invoke()
                onConfirm(range?.first ?: 0, range?.last ?: 0)
            },
            onClearQuote = {
                tvRef.value?.let { tv ->
                    val spannable = tv.text as? Spannable ?: return@let
                    Selection.removeSelection(spannable)
                    spannable.getSpans(0, spannable.length, android.text.style.BackgroundColorSpan::class.java)
                        .forEach { spannable.removeSpan(it) }
                }
                menuState = QuoteMenuState.INITIAL
            },
        )
```

- [ ] **Step 3: Добавить FullScreenMenuRows + FullScreenMenuRow composable'ы**

После `PreviewArea` composable, добавить:

```kotlin
private data class FullScreenMenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                    fadeOut(tween(120))) using
                SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) }
            },
            label = "FullScreenMenuFsm",
        ) { current ->
            val items: List<FullScreenMenuItemSpec> = when (current) {
                QuoteMenuState.INITIAL -> listOf(
                    FullScreenMenuItemSpec("Выбрать фрагмент", "quote-full", onClick = onSelectFragment),
                    FullScreenMenuItemSpec("Применить изменения", "check-outline", onClick = onApply),
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
                QuoteMenuState.INITIAL_WITH_QUOTE -> listOf(
                    FullScreenMenuItemSpec("Снять выделение", "cancel-quote", onClick = onClearQuote),
                    FullScreenMenuItemSpec("Применить изменения", "check-outline", onClick = onApply),
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
                QuoteMenuState.SELECTING -> listOf(
                    FullScreenMenuItemSpec("Назад", "back", onClick = onBack),
                    FullScreenMenuItemSpec("Цитировать фрагмент", "quote", onClick = onConfirmQuote),
                )
                QuoteMenuState.INITIAL_MINIMAL -> listOf(
                    FullScreenMenuItemSpec("Применить изменения", "check-outline", onClick = onApply),
                    FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item -> FullScreenMenuRow(item) }
            }
        }
    }
}

@Composable
private fun FullScreenMenuRow(item: FullScreenMenuItemSpec) {
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
            .padding(start = 24.dp, end = 16.dp),
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
```

- [ ] **Step 4: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Полная визуальная проверка acceptance criteria**

Open V4 picker → выполнить acceptance scenarios:

1. **INITIAL state**: 3 menu items — `⩘ Выбрать фрагмент` / `✓ Применить изменения` / `🗑 Отменить ответ` (danger red). Top header "Ответ на сообщение" + "Применить" (accent visible).
2. **«Выбрать фрагмент»** → selection (selectAll) + handles + floating Copy/Quote menu. Header "Ответ на цитату", Apply hidden. Bottom menu: `⟨ Назад` / `⩘ Цитировать фрагмент`.
3. **«Цитировать фрагмент»** → закрытие picker'а с current selection. Reply содержит фрагмент.
4. Re-open picker → INITIAL_WITH_QUOTE: подсветка фрагмента. Bottom menu: `cancel-quote / Снять выделение` / `✓ Применить изменения` / `🗑 Отменить ответ`.
5. **«Применить»** (header) и **«Применить изменения»** (bottom) → одинаково закрывают picker с current selection.
6. Header back ⟨ работает в любом state.
7. IME state — если был открыт перед picker'ом, остаётся открытым.
8. Switch brand (foxtrot→tango→etc) — pattern, accent, bubble цвета меняются.

- [ ] **Step 6: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteFullScreenContent.kt
git commit -m "feat(quote/fullscreen): menu rows + полная FSM логика (V4 готов)

FullScreenMenuRows: AnimatedContent (slide+fade+SizeTransform) для transitions между
4 FSM state'ами. Each row 48dp, full-width, padding-start 24dp, icon 24×24 + text 17/24
Roboto Regular, ripple Basic 0.08. Danger items #E06141. Wire'нуты все FSM callbacks
(selectFragment/apply/cancelReply/back/confirmQuote/clearQuote) — те же что V2."
```

---

### Task 7: Cleanup и финальный полный визуальный smoke-test

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`

Удалить неиспользуемые параметры, импорты, проверить cross-brand visual.

- [ ] **Step 1: Удалить unused fullText из QuotePickerFullScreen**

Если `fullText` не используется внутри QuotePickerFullScreen (а оно так — теперь content получает текст из `message.body`/`message.caption`), удалить параметр. Найти в `QuotePickerFullScreen.kt`:

```kotlin
    fullText: String,
```

Заменить на:

```kotlin
    @Suppress("UNUSED_PARAMETER") fullText: String,
```

Параметр оставляем в signature потому что overlay передаёт его — но помечаем как unused.

Альтернатива: убрать его из FULLSCREEN вызова в `QuotePickerOverlay.kt` тоже. По выбору исполнителя. Если убирать — изменить в overlay:

```kotlin
        QuoteVariant.FULLSCREEN -> QuotePickerFullScreen(
            message, senderPersona, senderAvatar, isMine,
            initialStart, initialEnd,
            onConfirm, onDismiss, onCancelReply,
        )
```

и в picker signature убрать `fullText: String,` строку.

**Рекомендую**: убрать полностью (cleaner). Параметр fullText был артефактом старого QuotePickerContent (BasicTextField).

- [ ] **Step 2: Сборка**

Run: `./gradlew :app:installDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Финальная проверка**

Запустить app, проверить:
- V4 (FULLSCREEN) — все acceptance criteria из спека (Task 6 Step 5).
- V2 (MODAL) — не сломали (full regression: open / select fragment / apply / re-open with quote / cancel).
- Switch между MODAL/FULLSCREEN через dev-toggle — оба работают.
- Brand switch (foxtrot/tango/sierra/kilo/love) — V4 цвета корректные для каждого бренда.

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt
git commit -m "chore(quote/fullscreen): удалить unused fullText из V4 signature

V4 получает текст через message.body/message.caption внутри QuoteFullScreenContent —
fullText был артефактом старого QuotePickerContent (BasicTextField)."
```

---

## Self-Review Summary

Прогнал план против спеки:

**Spec coverage:**
- ✅ Header (back/title/Apply) — Task 3.
- ✅ Subtitle 44dp — Task 4.
- ✅ Preview area (pattern + bubble + refs) — Task 5.
- ✅ Menu rows (4 FSM state'ов) — Task 6.
- ✅ Dialog properties + IME — Task 1.
- ✅ Расширение signature — Task 1.
- ✅ Update overlay — Task 1.
- ✅ Cleanup — Task 7.

**Type/name consistency:** `QuoteMenuState` — единый enum из `QuoteMenu.kt`, переиспользуется. `tvRef`/`selectAllRef`/`selectionRef` — те же типы что в `QuoteModalContent` (verified). `FullScreenMenuItemSpec` — private в новом файле, не конфликтует с `MenuItemSpec` из `QuoteMenu.kt`. `FullScreenRobotoFontFamily` — private (не конфликтует с `RobotoFontFamily` в `QuoteModalContent.kt`).

**No placeholders:** все code blocks complete, exact paths, expected outputs.

---

## Execution Notes

- Tests: нет — V4 это Compose UI поверх AndroidView. Существующие проектные тесты (StatusTransitions) не релевантны. Верификация чисто визуальная через `:app:installDebug` + acceptance scenarios на устройстве.
- Frequent commits: после каждого task'а (7 коммитов total).
- Build cycle: каждый task → `./gradlew :app:installDebug 2>&1 | tail -10` → визуальная проверка → commit.
- Не нужны изменения в `:components` — все нужные API (`selectMessageTextRange` etc) уже есть.
