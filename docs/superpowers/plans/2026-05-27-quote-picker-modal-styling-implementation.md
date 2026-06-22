# Quote Picker — V2 Modal styling — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Стилизовать V2 (Modal) quote-picker под Figma — full-screen Dialog с hardware-blur фона, preview с pattern + Compose mini-bubble (Text/Media, SOMEONE/MY), iOS-like context menu с FSM из 3 состояний и slide-анимацией.

**Architecture:** Compose-only mini-bubble (BasicTextField для селекции) + `BackgroundPatternView` AndroidView для паттерна + Window-level blur (API 31+) с dim-fallback. FSM меню через `AnimatedContent` + `SizeTransform`. Колбэки picker'а расширяются: `onCancelReply` (отдельно от `onDismiss`). VM не меняется — существующего API хватает.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), Compose Compiler 1.5.10, AndroidView interop, `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` (API 31+).

**Spec:** `docs/superpowers/specs/2026-05-27-quote-picker-modal-styling-design.md`

---

## File Structure

**Create:**
- `core/ui/src/main/java/com/example/template/core/ui/AppColors.kt` — синтезированные basic/surface helpers (`appBasic`, `appSurface01`, `appSurface02`)
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteMenu.kt` — Compose-меню с FSM
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt` — Compose mini-bubble (Text + Media, MY/SOMEONE)
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt` — корневая раскладка Modal'а (preview + menu)

**Modify:**
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt` — расширение сигнатуры
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt` — полная переписка
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerSheet.kt` — принять новые параметры (ignore unused)
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt` — принять новые параметры (ignore unused)
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — передача message/persona/avatar/isMine + wiring `onCancelReply`

**Не трогаем:**
- `ChatDetailViewModel.kt` — существующего API хватает
- `QuotePickerContent.kt` — остаётся для V3/V4
- `:components` (BubblesView, MediaBubbleView и др.) — preview Compose-only

---

## Task 1: AppColors helpers

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/AppColors.kt`

Хелперы для синтезированных basic/surface цветов (per memory `feedback_app_isdark_ds_colors`: DSColors.basicNN читают system night-mode, поэтому для app-toggle темы синтезируем через Color.Black/White × alpha).

- [ ] **Step 1: Создать `AppColors.kt` со всеми тремя функциями**

```kotlin
package com.example.template.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Basic colors с альфой — синтезированные под app-isDark (LocalIsDark), а не системный night-mode.
 * DSColors.basicNN читают системный night-mode и для нашего app-toggle темы непригодны.
 */
fun appBasic(isDark: Boolean, alpha: Float): Color =
    (if (isDark) Color.White else Color.Black).copy(alpha = alpha)

/**
 * Surface 01 — фон поверх всего (header в preview, белый dialog-фон).
 * Light: чистый белый. Dark: тёмно-серый (значение подобрано визуально по существующим экранам).
 */
fun appSurface01(isDark: Boolean): Color =
    if (isDark) Color(0xFF1E1E1E) else Color.White

/**
 * Surface 02 — вторичная поверхность (фон iOS-like menu в quote picker).
 */
fun appSurface02(isDark: Boolean): Color =
    if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
```

- [ ] **Step 2: Проверить компиляцию**

```bash
./gradlew :core:ui:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/AppColors.kt
git commit -m "feat(core-ui): appBasic/appSurface01/appSurface02 helpers для quote picker"
```

---

## Task 2: Расширить сигнатуру QuotePickerOverlay + адаптеры

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerSheet.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

Цель этого таска: расширить API без визуальных изменений. После таска приложение собирается, V3/V4 работают как раньше, V2 — пока со старым Card-UI, но получает новые callbacks (которые не использует).

- [ ] **Step 1: Расширить `QuotePickerOverlay.kt`**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.LocalQuoteVariant

@Composable
fun QuotePickerOverlay(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    when (LocalQuoteVariant.current) {
        QuoteVariant.MODAL -> QuotePickerModal(
            message, senderPersona, senderAvatar, isMine,
            fullText, initialStart, initialEnd,
            onConfirm, onDismiss, onCancelReply,
        )
        QuoteVariant.SHEET -> QuotePickerSheet(
            fullText, initialStart, initialEnd, onConfirm, onDismiss,
        )
        QuoteVariant.FULLSCREEN -> QuotePickerFullScreen(
            fullText, initialStart, initialEnd, onConfirm, onDismiss,
        )
    }
}
```

V3/V4 пока не используют message/persona/avatar/isMine/onCancelReply — стилизуем их в отдельных итерациях.

- [ ] **Step 2: Обновить `QuotePickerModal.kt` — временно принять новые параметры, оставив старый Card-UI**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

@Composable
fun QuotePickerModal(
    @Suppress("UNUSED_PARAMETER") message: Message,
    @Suppress("UNUSED_PARAMETER") senderPersona: Persona?,
    @Suppress("UNUSED_PARAMETER") senderAvatar: Bitmap?,
    @Suppress("UNUSED_PARAMETER") isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCancelReply: () -> Unit,
) {
    // Временный UI — будет полностью заменён в Task 3+
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
        ) {
            QuotePickerContent(
                fullText = fullText,
                initialStart = initialStart,
                initialEnd = initialEnd,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    }
}
```

- [ ] **Step 3: Перевязать `ChatDetailScreen.kt` — найти существующий блок `if (currentReplyCtx != null && pickerVisible)` (около строки 380) и заменить**

Найти точно этот фрагмент:
```kotlin
    val currentReplyCtx = replyCtx
    if (currentReplyCtx != null && pickerVisible) {
        QuotePickerOverlay(
            fullText = currentReplyCtx.originalFullText,
            initialStart = currentReplyCtx.quoteStart ?: 0,
            initialEnd = currentReplyCtx.quoteEnd ?: currentReplyCtx.originalFullText.length,
            onConfirm = { start, end ->
                viewModel.setQuote(start, end)
                viewModel.dismissQuotePicker()
            },
            onDismiss = { viewModel.dismissQuotePicker() },
        )
    }
```

Заменить на:
```kotlin
    val currentReplyCtx = replyCtx
    if (currentReplyCtx != null && pickerVisible) {
        val originalMessage = remember(messages, currentReplyCtx.originalId) {
            messages.firstOrNull { it.id == currentReplyCtx.originalId }
        }
        if (originalMessage != null) {
            val senderPersona = remember(originalMessage.senderId) {
                viewModel.personaForUser(originalMessage.senderId)
            }
            val senderAvatar = remember(senderPersona?.avatarAsset, cacheVersion) {
                senderPersona?.avatarAsset?.let { bitmapCache.get(it) }
            }
            QuotePickerOverlay(
                message = originalMessage,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = originalMessage.isMine,
                fullText = currentReplyCtx.originalFullText,
                initialStart = currentReplyCtx.quoteStart ?: 0,
                initialEnd = currentReplyCtx.quoteEnd ?: currentReplyCtx.originalFullText.length,
                onConfirm = { start, end ->
                    if (start == end) viewModel.clearQuote()
                    else viewModel.setQuote(start, end)
                    viewModel.dismissQuotePicker()
                },
                onDismiss = { viewModel.dismissQuotePicker() },
                onCancelReply = { viewModel.dismissReplyContext() },
            )
        }
    }
```

Логика onConfirm:
- `start == end` (empty range, «Применить изменения» без выделения) → `clearQuote()` — возврат к full-reply, sans quote.
- `start < end` → `setQuote(start, end)` — quote применён.
- `onCancelReply` → `dismissReplyContext()` (existing — сбрасывает _replyContext + _quotePickerVisible).

- [ ] **Step 4: Аналогично адаптируем `QuotePickerSheet.kt` и `QuotePickerFullScreen.kt` — добавляем @Suppress параметры**

В обоих файлах оставляем сигнатуру как есть (они вызываются с уже извлечёнными параметрами в `QuotePickerOverlay`); никаких изменений в этих файлах НЕ требуется. Шаг no-op подтверждающий — пропускаем.

- [ ] **Step 5: Build check**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Если ошибка про `isMine` на `Message` — `Message.isMine` существует (есть `msg.isMine` в `ChatDetailViewModel.startEdit`). Если `personaForUser` сигнатура поменялась — проверь VM line 159.

- [ ] **Step 6: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "refactor(quote): расширить QuotePickerOverlay API под Modal V2 (message/persona/avatar/isMine/onCancelReply)"
```

---

## Task 3: Modal Dialog scaffold + hardware blur

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt`

Цель: заменить `Card + QuotePickerContent` на полный Dialog с FLAG_BLUR_BEHIND. Контент пока placeholder (пустой Surface) — добавим в Task 8.

- [ ] **Step 1: Переписать `QuotePickerModal.kt` целиком**

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
fun QuotePickerModal(
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
        val density = LocalDensity.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.attributes = window.attributes.apply {
                    blurBehindRadius = with(density) { 30.dp.roundToPx() }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setDimAmount(0.2f)
            } else {
                window.setDimAmount(0.4f)
            }
        }
        Box(Modifier.fillMaxSize()) {
            QuoteModalContent(
                message = message,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = isMine,
                fullText = fullText,
                initialStart = initialStart,
                initialEnd = initialEnd,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                onCancelReply = onCancelReply,
            )
        }
    }
}
```

Файл вызывает ещё не существующий `QuoteModalContent` — это будет stub в следующем шаге.

- [ ] **Step 2: Создать пустой `QuoteModalContent.kt` stub**

Create file `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt`:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

/**
 * Stub — будет заполнен в Task 8 (assembly).
 */
@Composable
fun QuoteModalContent(
    @Suppress("UNUSED_PARAMETER") message: Message,
    @Suppress("UNUSED_PARAMETER") senderPersona: Persona?,
    @Suppress("UNUSED_PARAMETER") senderAvatar: Bitmap?,
    @Suppress("UNUSED_PARAMETER") isMine: Boolean,
    @Suppress("UNUSED_PARAMETER") fullText: String,
    @Suppress("UNUSED_PARAMETER") initialStart: Int,
    @Suppress("UNUSED_PARAMETER") initialEnd: Int,
    @Suppress("UNUSED_PARAMETER") onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCancelReply: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Text("Modal stub — open + blur OK?")
        }
    }
}
```

- [ ] **Step 3: Install и проверить hardware blur**

```bash
./gradlew :app:installDebug
```

На устройстве: открыть чат → long-press на бабле → context menu → «Ответить» → тап по reply-блоку в message panel → откроется новый Modal (stub).

Expected:
- API 31+: фон за модалом должен быть **blured** (видно паттерн чата размыто).
- API 26-30: только dim (плотнее: 0.4).
- Tap-outside / system back → закрывает.

Если blur не виден на API 31+ устройстве — проверь `WindowManager.isCrossWindowBlurEnabled()` (некоторые OEM отключают; нормально). Если совсем не работает — отчитайся, я разберусь.

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
git commit -m "feat(quote/modal): Dialog scaffold с hardware blur (FLAG_BLUR_BEHIND) + dim fallback"
```

---

## Task 4: QuoteMenu component (FSM + slide animation)

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteMenu.kt`

Standalone Compose-меню — без интеграции в Modal пока. После таска компонент готов, в Task 8 его подключим.

- [ ] **Step 1: Создать `QuoteMenu.kt`**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface02
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Text

enum class QuoteMenuState { INITIAL, SELECTING, INITIAL_MINIMAL }

private data class MenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun QuoteMenu(
    state: QuoteMenuState,
    modifier: Modifier = Modifier,
    onSelectFragment: () -> Unit,
    onApply: () -> Unit,
    onCancelReply: () -> Unit,
    onBack: () -> Unit,
    onConfirmQuote: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val bg = appSurface02(isDark)

    Box(
        modifier = modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
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
            label = "QuoteMenuFsm",
        ) { current ->
            val items: List<MenuItemSpec> = when (current) {
                QuoteMenuState.INITIAL -> listOf(
                    MenuItemSpec("Выбрать фрагмент", "quote-s", onClick = onSelectFragment),
                    MenuItemSpec("Применить изменения", "check-outline", onClick = onApply),
                    MenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
                QuoteMenuState.SELECTING -> listOf(
                    MenuItemSpec("Назад", "back", onClick = onBack),
                    MenuItemSpec("Цитировать фрагмент", "quote-s", onClick = onConfirmQuote),
                )
                QuoteMenuState.INITIAL_MINIMAL -> listOf(
                    MenuItemSpec("Применить изменения", "check-outline", onClick = onApply),
                    MenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = onCancelReply),
                )
            }
            Column {
                items.forEachIndexed { idx, item ->
                    QuoteMenuItem(item)
                    if (idx < items.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(appBasic(isDark, 0.08f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteMenuItem(item: MenuItemSpec) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(180.dp),
        )
        QuoteMenuIcon(item.iconName, labelColor)
    }
}

@Composable
private fun QuoteMenuIcon(iconName: String, tint: Color) {
    val ctx = LocalContext.current
    var bitmap by remember(iconName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(iconName) {
        bitmap = DSIcon.named(ctx, iconName, 24, 24)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Spacer(Modifier.size(24.dp))
    }
}
```

**Заметки по API:**
- `DSIcon.named(context, name, w, h)` возвращает `Bitmap?`. Проверь сигнатуру в `:components/designsystem/DSIcon.kt` — если параметры другие, поправь вызов. Иконка может быть `null` если SVG не нашёлся в `app/src/main/assets/icons/`.
- Если `DSIcon.named` асинхронно дороже чем кажется — можно перейти на synchronous-load в Step 1 (вариация: вызвать `remember` без `LaunchedEffect`).

- [ ] **Step 2: Build check**

```bash
./gradlew :feature:chatdetail:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

Если `DSIcon.named` сигнатура не совпадает — посмотри `../android-components/components/src/main/java/com/example/components/designsystem/DSIcon.kt` и подправь.

- [ ] **Step 3: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteMenu.kt
git commit -m "feat(quote/modal): QuoteMenu — Compose-меню с FSM и slide-анимацией"
```

---

## Task 5: QuoteBubblePreview — Text (SOMEONE)

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt`

Compose-only mini-bubble для Text-сообщения SOMEONE (бабл слева, с avatar). Хранит свой `TextFieldValue` и колбэчит изменения наружу.

- [ ] **Step 1: Создать `QuoteBubblePreview.kt`**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.utils.TimeFormatter

/**
 * Compose mini-bubble для preview в QuotePickerModal.
 * Поддерживает Text (body) и Media (caption опционально). MY/SOMEONE через `isMine`.
 *
 * `tfv` — поднятый state, владеет вызывающий (QuoteModalContent). BasicTextField обновляет
 * через onTfvChange. `selectable=true` ⇒ можно тянуть handles; false ⇒ просто отображение.
 */
@Composable
fun QuoteBubblePreview(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    tfv: TextFieldValue,
    onTfvChange: (TextFieldValue) -> Unit,
    selectable: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val accent = Color(brand.accentColor(isDark))
    val selectionColors = remember(accent) {
        TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = 0.4f))
    }
    // Цвет фона бабла — для SOMEONE white-ish, для MY accent. Для MVP используем фикс из Figma:
    val bubbleBg = if (isMine) accent else Color.White
    val textColor = if (isMine) Color.White else appBasic(isDark, 0.9f)
    val timeColor = if (isMine) Color.White.copy(alpha = 0.7f) else appBasic(isDark, 0.5f)
    val senderColor = senderPersona?.let { resolveSenderColor(it.gradientIndex, isDark) } ?: accent

    val senderName = senderPersona?.let { "${it.firstName} ${it.lastName}".trim() }
        ?: if (isMine) "Вы" else "Собеседник"
    val time = remember(message.timestamp) { TimeFormatter.formatTime(message.timestamp) }

    Row(
        modifier = modifier.fillMaxWidth().padding(
            start = if (isMine) 32.dp else 40.dp,
            end = if (isMine) 32.dp else 32.dp,
        ),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMine) {
            BubbleAvatar(senderAvatar, senderPersona, senderColor)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .widthIn(max = 287.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleBg),
        ) {
            Column(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 8.dp)) {
                if (!isMine) {
                    Text(
                        text = senderName,
                        color = senderColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(0.dp))  // placeholder для gap, Spacer.size(0) — no-op; Figma gap=0 между header и content
                }
                // Контент — текст или media
                BubbleBody(
                    message = message,
                    tfv = tfv,
                    onTfvChange = onTfvChange,
                    textColor = textColor,
                    selectable = selectable,
                    focusRequester = focusRequester,
                    selectionColors = selectionColors,
                )
                Spacer(Modifier.size(0.dp))
                // Time pill — overlay внизу-справа
                Text(
                    text = time,
                    color = timeColor,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun BubbleAvatar(avatar: Bitmap?, persona: Persona?, fallbackColor: Color) {
    if (avatar != null) {
        Image(
            bitmap = avatar.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        // Initials fallback
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center,
        ) {
            val initials = persona?.let {
                (it.firstName.firstOrNull()?.toString().orEmpty() +
                    it.lastName.firstOrNull()?.toString().orEmpty()).ifEmpty { "?" }
            } ?: "?"
            Text(initials, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BubbleBody(
    message: Message,
    tfv: TextFieldValue,
    onTfvChange: (TextFieldValue) -> Unit,
    textColor: Color,
    selectable: Boolean,
    focusRequester: FocusRequester,
    selectionColors: TextSelectionColors,
) {
    // Task 5: только Text. Media — в Task 6.
    when (message) {
        is Message.Text -> {
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = tfv,
                    onValueChange = if (selectable) onTfvChange else { _: TextFieldValue -> },
                    readOnly = true,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(textColor),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        }
        else -> {
            // Заглушка для Media — рендерим caption через BasicTextField, грид добавим в Task 6.
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = tfv,
                    onValueChange = if (selectable) onTfvChange else { _: TextFieldValue -> },
                    readOnly = true,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(textColor),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
private fun resolveSenderColor(gradientIndex: Int, isDark: Boolean): Color {
    val brand = LocalAppBrand.current
    val pairs = remember(brand, isDark) { brand.avatarGradientPairs(isDark) }
    if (pairs.isEmpty()) return Color(brand.accentColor(isDark))
    val idx = gradientIndex.coerceAtLeast(0) % pairs.size
    return Color(pairs[idx].first)
}
```

**Заметки по API:**
- `brand.accentColor(isDark)` — `Int` (ARGB), оборачиваем в `Color(...)`.
- `brand.avatarGradientPairs(isDark)` — `List<Pair<Int, Int>>` (start/end ARGB). Используем `first` как sender-color (соответствует Figma `--avatarka/N`).
- `TimeFormatter.formatTime(timestamp)` — проверь, что хелпер существует в `core/utils` (используется в MessageList). Если другое имя — поправь импорт.
- `Persona.firstName / lastName` — проверь модель `:core:model/Persona.kt`. Если поле одно `fullName` — используй его. Реально в проекте `Persona.fullName` существует (см. `ChatDetailViewModel.resolveAuthorName`). Замени:
  ```kotlin
  val senderName = senderPersona?.fullName ?: if (isMine) "Вы" else "Собеседник"
  ```
  и в `BubbleAvatar` initials через `persona.initials` (тоже из модели).

После проверки `Persona` API исправь оба места перед компиляцией.

- [ ] **Step 2: Сверить API `Persona` и `TimeFormatter`, поправить названия полей**

```bash
grep -nE "val (fullName|initials|firstName|lastName|gradientIndex)" core/model/src/main/java/com/example/template/core/model/Persona.kt
grep -nE "fun (formatTime|formatTimestamp)" core/utils/src/main/java/com/example/template/utils/TimeFormatter.kt
```

Подставь корректные имена в код.

- [ ] **Step 3: Подключить QuoteBubblePreview во временный QuoteModalContent для визуальной проверки**

Edit `QuoteModalContent.kt` — заменить stub-content:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

@Composable
fun QuoteModalContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCancelReply: () -> Unit,
) {
    var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = fullText,
                selection = TextRange(initialStart.coerceIn(0, fullText.length), initialEnd.coerceIn(0, fullText.length)),
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 44.dp, start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column {
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .background(Color.DarkGray)  // временный фон preview — заменим в Task 8
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                QuoteBubblePreview(
                    message = message,
                    senderPersona = senderPersona,
                    senderAvatar = senderAvatar,
                    isMine = isMine,
                    tfv = tfv,
                    onTfvChange = { tfv = it },
                    selectable = true,
                    focusRequester = focusRequester,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Build + install + visual check**

```bash
./gradlew :app:installDebug
```

Открыть picker → видеть бабл текстового сообщения с avatar (SOMEONE). Текст можно выделить (handles появляются). Layout грубо соответствует Figma (без header'а, pattern, menu — это в Task 8).

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
git commit -m "feat(quote/modal): QuoteBubblePreview — Compose mini-bubble для Text SOMEONE с селекцией"
```

---

## Task 6: QuoteBubblePreview — Media

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt`

Поддержка `Message.Media`: грид attachment-картинок сверху, caption внизу (BasicTextField). Если caption empty — рендерим только грид (BasicTextField не создаём — на этом этапе FSM-меню в Task 8 решит INITIAL-MINIMAL).

- [ ] **Step 1: Заменить `BubbleBody` в `QuoteBubblePreview.kt`**

В `QuoteBubblePreview.kt` найти приватную функцию `BubbleBody` (см. код из Task 5) и заменить целиком:

```kotlin
@Composable
private fun BubbleBody(
    message: Message,
    tfv: TextFieldValue,
    onTfvChange: (TextFieldValue) -> Unit,
    textColor: Color,
    selectable: Boolean,
    focusRequester: FocusRequester,
    selectionColors: TextSelectionColors,
) {
    when (message) {
        is Message.Text -> BubbleTextField(tfv, onTfvChange, textColor, selectable, focusRequester, selectionColors)
        is Message.Media -> {
            Column {
                MediaGrid(message.attachments)
                if (message.caption?.isNotEmpty() == true) {
                    Spacer(Modifier.size(4.dp))
                    BubbleTextField(tfv, onTfvChange, textColor, selectable, focusRequester, selectionColors)
                }
            }
        }
        else -> {
            // Voice/Link/CallMeet/System — не достижимо (Reply для них не открывается).
            // Placeholder на случай будущего расширения.
            Text("(unsupported preview type)", color = textColor)
        }
    }
}

@Composable
private fun BubbleTextField(
    tfv: TextFieldValue,
    onTfvChange: (TextFieldValue) -> Unit,
    textColor: Color,
    selectable: Boolean,
    focusRequester: FocusRequester,
    selectionColors: TextSelectionColors,
) {
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = tfv,
            onValueChange = if (selectable) onTfvChange else { _: TextFieldValue -> },
            readOnly = true,
            textStyle = TextStyle(color = textColor, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(textColor),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

@Composable
private fun MediaGrid(attachments: List<MediaAttachment>) {
    // MVP: показываем первый attachment в формате image; для нескольких — 2×2 grid.
    val thumbnails: List<Bitmap?> = attachments.map { it.thumbnail as? Bitmap }
    when {
        thumbnails.isEmpty() -> Spacer(Modifier.size(0.dp))
        thumbnails.size == 1 -> {
            val bmp = thumbnails[0]
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .size(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Gray.copy(alpha = 0.3f)),
                )
            }
        }
        else -> {
            // 2×2 grid
            Column {
                thumbnails.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { bmp ->
                            Box(Modifier.weight(1f).padding(2.dp)) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Gray.copy(alpha = 0.3f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Добавь нужные импорты:
```kotlin
import androidx.compose.foundation.layout.Spacer
import com.example.template.core.model.MediaAttachment
```

- [ ] **Step 2: Build check**

```bash
./gradlew :feature:chatdetail:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. Если `MediaAttachment.thumbnail` имеет другой тип — проверь `:core:model/Message.kt` (per CLAUDE.md: `@Transient val thumbnail: Any?`, кастим через `as? Bitmap`).

- [ ] **Step 3: Install + visual check**

```bash
./gradlew :app:installDebug
```

Найти в чате Media-сообщение (с caption и без), запустить reply → picker. Убедиться:
- Media с caption: вверху thumbnail, внизу — caption-текст с возможностью селекции.
- Media без caption: только thumbnail, без текстового поля.

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt
git commit -m "feat(quote/modal): QuoteBubblePreview — Media поддержка (image grid + опц. caption)"
```

---

## Task 7: QuoteBubblePreview — MY orientation

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt`

Текущая реализация (Task 5/6) уже разрулена через `isMine` параметр: bubble справа, sender name скрыт, avatar скрыт, фон — accent. Проверим визуально и подтянем если что-то не сошлось.

- [ ] **Step 1: Verify alignment в существующем коде**

Открой `QuoteBubblePreview.kt`, проверь Row:
```kotlin
Row(
    modifier = modifier.fillMaxWidth().padding(
        start = if (isMine) 32.dp else 40.dp,
        end = if (isMine) 32.dp else 32.dp,
    ),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
)
```

Если такое выравнивание уже на месте — переходи к Step 2 для визуальной проверки. Иначе — поправь.

- [ ] **Step 2: Install + visual check**

```bash
./gradlew :app:installDebug
```

Сделать reply на собственное сообщение (MY) — Modal должен показать бабл справа, без avatar, фон = accent.

Известно: вход в picker для MY-сообщения возможен только через context menu «Ответить» (на MY-баблах оно есть).

- [ ] **Step 3: Commit (если были правки)**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteBubblePreview.kt
git commit -m "feat(quote/modal): QuoteBubblePreview — MY orientation проверена визуально"
```

Если правок нет — пропусти commit (Step 3 no-op).

---

## Task 8: QuoteModalContent — full assembly

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt`

Финальная сборка: preview (rounded-24 box с pattern + sticky header + scroll + bubble) + Spacer + QuoteMenu с правильным FSM-управлением.

- [ ] **Step 1: Полная переписка `QuoteModalContent.kt`**

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01

@Composable
fun QuoteModalContent(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current

    // FSM state
    var menuState by rememberSaveable {
        mutableStateOf(if (fullText.isEmpty()) QuoteMenuState.INITIAL_MINIMAL else QuoteMenuState.INITIAL)
    }
    // Selection state
    var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = fullText,
                selection = TextRange(initialStart.coerceIn(0, fullText.length), initialEnd.coerceIn(0, fullText.length)),
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val selectable = menuState == QuoteMenuState.SELECTING

    // Pattern и фон preview — brand-aware
    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = Color(brand.messageScreenBackground(isDark))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 44.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.CenterHorizontally,
    ) {
        // --- Preview ---
        Box(
            Modifier
                .width(351.dp)
                .height(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(previewBg)
                .padding(8.dp),
        ) {
            // Pattern background (AndroidView)
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

            // Scrollable bubble area (bottom-aligned by default)
            val scrollState = rememberScrollState()
            LaunchedEffect(message.id) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Bottom,
            ) {
                // Spacer для top-padding (header overlays — 60dp ~ header height)
                Spacer(Modifier.height(60.dp))
                QuoteBubblePreview(
                    message = message,
                    senderPersona = senderPersona,
                    senderAvatar = senderAvatar,
                    isMine = isMine,
                    tfv = tfv,
                    onTfvChange = { tfv = it },
                    selectable = selectable,
                    focusRequester = focusRequester,
                )
            }

            // Sticky header (overlay поверх scroll)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(appSurface01(isDark))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ответ на цитату",
                        color = appBasic(isDark, 0.9f),
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Вы можете процитировать фрагмент сообщения",
                        color = appBasic(isDark, 0.5f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- Menu ---
        QuoteMenu(
            state = menuState,
            modifier = Modifier,  // alignment управляется через Column outer horizontalAlignment
            onSelectFragment = {
                tfv = tfv.copy(selection = TextRange(0, fullText.length))
                menuState = QuoteMenuState.SELECTING
                // focusRequester будет вызван в LaunchedEffect ниже
            },
            onApply = {
                val sel = tfv.selection
                onConfirm(sel.start, sel.end)
            },
            onCancelReply = { onCancelReply() },
            onBack = {
                tfv = tfv.copy(selection = TextRange.Zero)
                menuState = QuoteMenuState.INITIAL
            },
            onConfirmQuote = {
                val sel = tfv.selection
                onConfirm(sel.start, sel.end)
            },
        )
    }

    // Запросить фокус при входе в SELECTING (handles появляются)
    LaunchedEffect(menuState) {
        if (menuState == QuoteMenuState.SELECTING) {
            focusRequester.requestFocus()
        }
    }
}
```

**Заметки:**
- `brand.messageScreenBackground(isDark)` — `Int`. Если такого метода нет — посмотри какой используется в ChatDetailScreen для фона message screen. Скорее всего такой метод есть на DSBrand (CLAUDE.md упоминает его).
- BackHandler для SELECTING-back (`tap-outside → back to INITIAL`) для MVP **не реализован** через перехват — система back пока закрывает Dialog целиком. Если хотим точно реализовать вариант (a) — добавить `BackHandler` внутри Dialog (требует обёрток над BackHandler в Dialog scope). Делаем в отдельном таске если визуально критично.
- Sticky header перекрывает верх scroll area. Spacer(60dp) — приблизительная высота header'а. На разных размерах шрифтов может проседать. Подкорректируй визуально (можно через `onSizeChanged` + `Modifier.padding(top = headerHeight)`).

- [ ] **Step 2: Build check**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. Если ошибка про `messageScreenBackground` — проверь имя метода на `DSBrand` (там же где `accentColor`, `backgroundPatternName`).

- [ ] **Step 3: Install + полный functional QA**

```bash
./gradlew :app:installDebug
```

Сценарии:
1. **Text SOMEONE:** открыть Modal, тапнуть «Выбрать фрагмент» — должен пойти slide вправо, handles появиться, весь текст выделен. Подвигать handles — selection меняется. «Назад» — slide влево, выделение снято. «Цитировать фрагмент» — Modal закрылся, в message panel виден reply с quote.
2. **Text + «Применить изменения» (без выделения):** Modal закрылся, reply без quote.
3. **«Отменить ответ»:** Modal закрылся, reply сброшен (message panel без reply-блока).
4. **Media + caption:** Modal показывает thumbnail сверху, caption внизу селектится.
5. **Media без caption:** 2 пункта меню (INITIAL_MINIMAL), нет «Выбрать фрагмент». Selection недоступен.
6. **MY:** бабл справа, без avatar, accent-фон. Menu прижат к правому краю (через `horizontalAlignment = End` на Column).
7. **API 31+:** виден blur фона.
8. **API < 31:** dim 0.4, без blur.
9. **Rotation:** state сохраняется (menuState, selection через rememberSaveable).

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteModalContent.kt
git commit -m "feat(quote/modal): full assembly — preview (pattern+header+scroll+bubble) + FSM menu"
```

---

## Task 9: Visual polish и финальный QA

**Files:** возможные правки в любом из:
- `QuoteBubblePreview.kt`
- `QuoteModalContent.kt`
- `AppColors.kt`

Точно не известны заранее — зависит от того, что не сошлось в визуальном QA после Task 8.

- [ ] **Step 1: Compare with Figma**

Открыть скриншоты:
- INITIAL (3 пункта): https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=150-303106
- SELECTING (2 пункта): https://www.figma.com/design/ACzbXY2qte1xXuNibYa6me/Quote?node-id=150-292855

Сравнить с приложением:
- Точные dark-значения surface01/surface02 (`AppColors.kt`) — подкорректировать если есть отклонения.
- Шрифты Roboto Medium/Regular — проверь что веса не съехали.
- Time pill — в Figma это абсолютный bottom-right overlay внутри бабла на спец фоне; в моём скетче — simple right-aligned Text. Если визуально критично — добавь Box(align=BottomEnd, padding(4,4)) с pill-фоном.
- Sender name color — Figma `#4BCBEC` (avatarka/0). Если у persona `gradientIndex=0` визуально получается другой — посмотри как BubblesView в `:components` маппит gradientIndex → sender color (может там не первый цвет пары, а вариация).
- Backdrop-blur меню (Figma 27.183px) — у нас отсутствует. Если визуально слишком плоско — добавь `Modifier.blur(20.dp)` на background `Box` меню (API 31+). Иначе оставь как есть — Window blur и так есть.

- [ ] **Step 2: Сделать точечные правки**

Внести точечные изменения. Описание правок будет зависеть от результатов Step 1. Если правок нет — переходи к Step 3.

- [ ] **Step 3: Финальный install и smoke-test**

```bash
./gradlew :app:installDebug
```

Прогнать сценарии 1-9 из Task 8 Step 3 ещё раз — убедиться что ничего не сломалось.

- [ ] **Step 4: Commit**

```bash
git add -p   # отобрать только релевантные правки
git commit -m "polish(quote/modal): визуальные правки по Figma"
```

Если правок нет — пропустить.

---

## Task 10: Обновить memory

**Files:**
- Modify: `C:/Users/Grond/.claude/projects/C--Claude-test-project-android-template/memory/project_quote_reply.md`
- Modify: `C:/Users/Grond/.claude/projects/C--Claude-test-project-android-template/memory/MEMORY.md` (если нужно)

Зафиксировать в memory, что V2 (Modal) стилизован.

- [ ] **Step 1: Прочесть текущий project_quote_reply.md**

```bash
cat "C:/Users/Grond/.claude/projects/C--Claude-test-project-android-template/memory/project_quote_reply.md"
```

- [ ] **Step 2: Обновить запись о статусе V2 Modal**

Edit `project_quote_reply.md`:
- В разделе «Готово на ветке `feature/quote-reply`» добавить запись про стилизацию V2 Modal (Figma 1:1, FSM меню с 3 состояниями, hardware blur, Compose mini-bubble).
- Убрать секцию «TODO следующего шага: стилизация V2/V3/V4 picker'ов» — обновить на «TODO: стилизация V3 Sheet и V4 FullScreen по отдельным Figma-макетам».
- Добавить ссылки на spec и plan этого таска.

---

## Self-Review

Прошёлся по spec, проверяя coverage:

| Spec section | Plan coverage |
|---|---|
| Цель: переписка V2 Modal под Figma | Task 3 (scaffold) + Task 8 (assembly) |
| FSM (INITIAL/SELECTING/INITIAL_MINIMAL) + переходы | Task 4 (Menu FSM) + Task 8 (state wiring) |
| Slide-animation между состояниями | Task 4 (AnimatedContent + slideInHorizontally) |
| Compose mini-bubble Text | Task 5 |
| Compose mini-bubble Media (с caption и без) | Task 6 |
| MY/SOMEONE раскладка | Task 5 (isMine в Row arrangement) + Task 7 (verify) + Task 8 (menu alignment через Column.horizontalAlignment) |
| Hardware blur + dim fallback | Task 3 (SideEffect + FLAG_BLUR_BEHIND) |
| onCancelReply колбэк | Task 2 (signature) + Task 8 (wired в QuoteMenu) |
| Pattern background через BackgroundPatternView | Task 8 (AndroidView + brand.backgroundPatternName) |
| Sticky preview header | Task 8 (Box.align(TopCenter) поверх scroll) |
| Scroll bottom-aligned | Task 8 (LaunchedEffect scrollTo(maxValue)) |
| DS-token mapping (appBasic, appSurface01/02) | Task 1 |
| Icons (back/check-outline/delete/quote-s) | Task 4 (через DSIcon.named) |
| Edge cases (длинный текст, MY align, rotation) | Task 5/8 (rememberSaveable) |
| API 26-30 fallback | Task 3 (else branch с setDimAmount(0.4f)) |
| VM не меняется | Verified: `setQuote`/`clearQuote`/`dismissReplyContext`/`dismissQuotePicker` уже покрывают всё |

**Placeholder scan:** Один риск — в Task 5 Step 1 я даю код с `persona.firstName/lastName`, Step 2 — корректирую на `persona.fullName`. Это нормальный refactor flow внутри Task, не placeholder.

В Task 9 (polish) Step 2 — «правки зависят от QA». Это не placeholder в смысле кода, а итеративный шаг с описанным методом (сравнение с Figma + точечная правка). Норма для визуального polish-таска.

**Type consistency:** проверил — `QuoteMenuState` enum используется одинаково в Task 4 (определение) и Task 8 (wiring). `TextFieldValue.Saver` используется и в Task 5 Step 3 и в Task 8 Step 1 (одинаково). Сигнатура `QuoteBubblePreview` consistent между Tasks 5/6/8.

План готов.
