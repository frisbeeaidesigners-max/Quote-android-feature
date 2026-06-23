# V5 Reply/Link Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a two-tab SegmentedControl («Ответ» / «Ссылка») to the V5 fullscreen quote-picker. «Ответ» keeps current behavior; «Ссылка» shows a static `LinkBubbleView` whose reply block is driven by the quote state from «Ответ».

**Architecture:** Single Composable `QuoteV5FullScreenContent` adds `selectedTab` state plus a `snapshotRange` for cross-tab reply rendering. `PreviewArea` stays mounted on both tabs; on Tab 1 a static `LinkBubbleView` overlay is stacked above it inside the existing Box. Header title, description row, and BottomStrip variant swap via `selectedTab`. SegmentedControl is a floating overlay at `Alignment.TopCenter` inside PreviewArea.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose (BOM 2024.02.00), AndroidView wrappers over `SegmentedControlView` + `LinkBubbleView` from sibling `:components` (composite build).

**Spec:** `docs/superpowers/specs/2026-06-23-v5-reply-link-tabs-design.md`

## Global Constraints

- All edits live in `feature/chatdetail/.../quotepicker/QuoteV5FullScreenContent.kt` unless explicitly noted.
- No edits to `:components` (`../Components-android-template/`) — confirmed in spec.
- All UI text in Russian, exact strings from Figma (verbatim, no translation drift):
  - Tab labels: `"Ответ"`, `"Ссылка"`
  - Tab 1 header title: `"Оформление ссылки"`
  - Tab 1 description: `"Так будет выглядеть ваша ссылка после отправки"`
  - Tab 1 BottomStrip line 1: `"Прикрепленная ссылка"`
  - Tab 1 BottomStrip line 2 (mock URL): `"https://web.frisbee.live/im/2021316695"`
  - LinkBubble mock title: `"Суммаризация записи ВКС (аналог Plaud)"`
  - LinkBubble mock description: `"Рабочее пространство для обсуждения задач и обмена ключевыми обновлениями по текущему проекту"`
  - LinkBubble mock URL: `"https://web.frisbee.live/im/2021316695"`
  - LinkBubble mock label: `"Группа"`
  - LinkBubble mock time: `"10:15"`
- Branding & color: only `appBasic(...)`, `appSurface01(...)`, `appSurface02(...)`, and `brand.<x>ColorScheme(isDark)` calls. No hardcoded colors.
- Icon for Tab 1 BottomStrip: `"link-chain"` (verified present in `../IconsAndColorsLibrary-cross-template/icons/link-chain.svg`; canonical name preserved).
- Build command (Windows PowerShell):
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  $env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
  .\gradlew.bat :app:assembleDebug
  ```
- Install command (Windows PowerShell):
  ```powershell
  $env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
  & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
  ```
- Build + install MUST be separate commands per CLAUDE.md (`gradlew && adb install` causes APK lock races on Windows).
- Branch: `feature/quote-v5`. Each task ends with a commit.

---

## File Structure

**Modify:**
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt` — all changes

No new files. No `:components` changes. No test file (UI feature, no existing unit tests for picker; verification is manual smoke per task).

---

### Task 1: SegmentedControl overlay + tab state + header/description switching

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: existing `QuoteMenuState`, `HeaderHost`, `HeadersView.HeaderConfig.Custom`.
- Produces: `selectedTab: Int` state (0 = Ответ, 1 = Ссылка), accessible to subsequent tasks. Reads via `var selectedTab by rememberSaveable { mutableStateOf(0) }` at the top of `QuoteV5FullScreenContent`.

- [ ] **Step 1: Add imports for `SegmentedControlView` and `AndroidView`**

Open `QuoteV5FullScreenContent.kt` and add to the imports block (alphabetically sorted with neighbors):

```kotlin
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.segmentedcontrol.SegmentedControlView
```

- [ ] **Step 2: Add `selectedTab` state in the Composable**

In `QuoteV5FullScreenContent`, locate the line `var popoverOpen by rememberSaveable { mutableStateOf(true) }` (currently ~line 114). Add directly above it:

```kotlin
var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Ответ, 1 = Ссылка
```

- [ ] **Step 3: Make header title depend on `selectedTab`**

Locate the `val headerConfig = remember(menuState) { ... }` block (currently ~line 137). Replace it with:

```kotlin
val headerConfig = remember(menuState, selectedTab) {
    val title = if (selectedTab == 1) {
        "Оформление ссылки"
    } else {
        when (menuState) {
            QuoteMenuState.INITIAL_WITH_QUOTE, QuoteMenuState.SELECTING -> "Ответ на цитату"
            QuoteMenuState.INITIAL, QuoteMenuState.INITIAL_MINIMAL -> "Ответ на сообщение"
        }
    }
    HeadersView.HeaderConfig.Custom(
        title = title,
        description = null,
        titleAlignment = HeadersView.HeaderConfig.Custom.TitleAlignment.LEFT,
        leftButton = HeadersView.HeaderConfig.Custom.LeftButton.BACK,
        rightButton = HeadersView.HeaderConfig.Custom.RightButton.TEXT,
        rightButtonType = HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
        rightButtonLabel = "Сохранить",
        onLeftClick = { onLeftClickLatest.value() },
        onRightClick = { onRightClickLatest.value() },
    )
}
```

- [ ] **Step 4: Make description text depend on `selectedTab`**

Find the `Row` rendering the description (currently ~lines 156-170, with `Text(text = "Вы можете процитировать фрагмент сообщения", ...)`). Replace just the `Text(...)` call inside with:

```kotlin
Text(
    text = if (selectedTab == 1) "Так будет выглядеть ваша ссылка после отправки"
           else "Вы можете процитировать фрагмент сообщения",
    style = DSTypography.body1R.toComposeTextStyle(),
    color = appBasic(isDark, 0.5f),
    maxLines = 1,
)
```

- [ ] **Step 5: Add SegmentedControl overlay inside PreviewArea Box**

Locate the existing `Box(modifier = Modifier.fillMaxWidth().weight(1f)...)` containing the `PreviewArea(...)` call and `AnimatedVisibility { PopoverCard(...) }` (currently ~lines 199-276). At the END of that Box, just BEFORE its closing brace and AFTER the AnimatedVisibility block, insert:

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
        .width(152.dp)
        .height(32.dp),
)
```

Note: `brand` is already available in scope (`val brand = LocalAppBrand.current` — currently inside `BottomStrip` only). Add `val brand = LocalAppBrand.current` at the top of `QuoteV5FullScreenContent` if not already at function scope. Check via Grep: if `LocalAppBrand` is only imported but not pulled at function scope, add the line near `val isDark = LocalIsDark.current` (~line 85).

- [ ] **Step 6: Build APK**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Install APK**

Run:
```powershell
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 8: Manual verify**

On device:
1. Enable V5 in Profile (Switch).
2. Open any text message → long-press → «Цитировать» → fullscreen picker opens.
3. SegmentedControl is visible at top of preview area, 8dp from top of preview area, centered, with two segments «Ответ» (selected) and «Ссылка».
4. Tap «Ссылка»: header title changes to «Оформление ссылки», description changes to «Так будет выглядеть ваша ссылка после отправки». BottomStrip and bubble area remain unchanged for now.
5. Tap «Ответ»: returns to original header title and description.

- [ ] **Step 9: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): V5 picker — add SegmentedControl tabs + per-tab header/description"
```

---

### Task 2: `snapshotRange` state + `extractQuote` helper

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: existing `menuState`, `selectionRef`, `initialStart`, `initialEnd`, `message`.
- Produces:
  - `snapshotRange: Pair<Int, Int>?` state via `rememberSaveable`. Holds last finalized (start, end) of the selected quote, or `null` when no quote is selected. Read by Task 3 (LinkBubble reply text).
  - `extractQuote(message: Message, start: Int, end: Int): String` — top-level private function returning the substring of message body, or `replyPreviewText(message)` if substring is invalid.
  - Confirm callbacks (`onRightClickLatest`, `onApply`, `onConfirmQuote`) now read `snapshotRange` with fallback to `selectionRef.value?.invoke()`.

- [ ] **Step 1: Add `extractQuote` helper at the bottom of the file**

At the very end of `QuoteV5FullScreenContent.kt`, after the last `}` of the last existing function, append:

```kotlin
private fun extractQuote(message: Message, start: Int, end: Int): String {
    val body = (message as? Message.Text)?.body ?: return replyPreviewText(message)
    if (body.isEmpty()) return replyPreviewText(message)
    val s = start.coerceIn(0, body.length)
    val e = end.coerceIn(s, body.length)
    return if (s < e) body.substring(s, e) else replyPreviewText(message)
}
```

- [ ] **Step 2: Add `snapshotRange` state and update effect**

In `QuoteV5FullScreenContent`, locate the existing `LaunchedEffect(menuState) { if (menuState == QuoteMenuState.SELECTING) popoverOpen = true }` block (currently ~line 116-118). Directly AFTER it, insert:

```kotlin
var snapshotRange by rememberSaveable {
    mutableStateOf(
        if (initialStart < initialEnd) initialStart to initialEnd else null
    )
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
        QuoteMenuState.SELECTING -> Unit
    }
}
```

- [ ] **Step 3: Make Confirm callbacks fall back to `snapshotRange`**

Find `onRightClickLatest` (~line 132-136). Replace its body with:

```kotlin
val onRightClickLatest = rememberUpdatedState(newValue = {
    val live = selectionRef.value?.invoke()
    val (s, e) = if (live != null && live.first < live.last) {
        live.first to live.last
    } else {
        snapshotRange ?: (0 to 0)
    }
    clearSelectionRef.value?.invoke()
    onConfirm(s, e)
})
```

Find the `onApply` callback inside `callbacks = MenuCallbacks(...)` (~line 242-246). Replace its body with:

```kotlin
onApply = {
    val live = selectionRef.value?.invoke()
    val (s, e) = if (live != null && live.first < live.last) {
        live.first to live.last
    } else {
        snapshotRange ?: (0 to 0)
    }
    clearSelectionRef.value?.invoke()
    onConfirm(s, e)
},
```

Find the `onConfirmQuote` callback (~line 255-259). Replace its body with:

```kotlin
onConfirmQuote = {
    val live = selectionRef.value?.invoke()
    val (s, e) = if (live != null && live.first < live.last) {
        live.first to live.last
    } else {
        snapshotRange ?: (0 to 0)
    }
    clearSelectionRef.value?.invoke()
    onConfirm(s, e)
},
```

- [ ] **Step 4: Build APK**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install APK**

```powershell
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 6: Manual verify (no visual change, regression check)**

1. Open V5 picker.
2. Pick a fragment via «Выбрать фрагмент», adjust handles, tap «Сохранить». Confirm reply attaches with the selected fragment as quote.
3. Open V5 picker again. Without picking — tap «Сохранить». Confirm reply attaches with the full message.
4. Open V5 picker, pick fragment, tap «Отменить ответ» (or BACK). Confirm no reply is attached and no crash.

- [ ] **Step 7: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): V5 picker — add snapshotRange state + extractQuote helper"
```

---

### Task 3: LinkBubble overlay on Tab «Ссылка»

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: `selectedTab`, `snapshotRange`, `isMine`, `senderPersona`, `message`, `brand`, `isDark`. Reads `extractQuote(message, start, end)` and `replyPreviewText(message)`.
- Produces: visual swap of PreviewArea content on Tab 1 via overlay (PreviewArea stays mounted underneath). No new external symbols.

- [ ] **Step 1: Add imports for LinkBubbleView and AnimatedContent**

Add to imports block:

```kotlin
import androidx.compose.animation.AnimatedVisibility as ComposeAnimatedVisibility
import com.example.components.bubbles.LinkBubbleView
```

(`AnimatedVisibility` is already imported elsewhere — if so, omit the alias and use the existing import; the spec uses fade overlay, not slide.)

If `androidx.compose.animation.AnimatedVisibility` is NOT yet imported, add:

```kotlin
import androidx.compose.animation.AnimatedVisibility
```

- [ ] **Step 2: Add LinkBubble overlay inside PreviewArea Box**

Locate the Box currently holding `PreviewArea(...)` and `AnimatedVisibility { PopoverCard }` and (from Task 1) the SegmentedControl `AndroidView`. The current order inside the Box should be:
1. `PreviewArea(...)`
2. `val callbacks = MenuCallbacks(...)`
3. `AnimatedVisibility(visible = popoverOpen, ...) { PopoverCard(...) }`
4. SegmentedControl `AndroidView` (from Task 1)

Insert a NEW block BETWEEN (1) `PreviewArea(...)` and (2) `val callbacks = ...`:

```kotlin
AnimatedVisibility(
    visible = selectedTab == 1,
    enter = fadeIn(tween(150)),
    exit = fadeOut(tween(150)),
    modifier = Modifier.fillMaxSize(),
) {
    LinkBubbleOverlay(
        message = message,
        senderPersona = senderPersona,
        isMine = isMine,
        snapshotRange = snapshotRange,
    )
}
```

- [ ] **Step 3: Add the `LinkBubbleOverlay` Composable**

Append this function near other private composables in the file (e.g. after `BottomStrip` definition, before file end):

```kotlin
@Composable
private fun LinkBubbleOverlay(
    message: Message,
    senderPersona: Persona?,
    isMine: Boolean,
    snapshotRange: Pair<Int, Int>?,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val replySender = remember(isMine, senderPersona) {
        if (isMine) "Вы"
        else senderPersona?.fullName?.takeIf { it.isNotEmpty() } ?: "Собеседник"
    }
    val replyText = remember(snapshotRange, message) {
        snapshotRange?.let { (s, e) -> extractQuote(message, s, e) }
            ?: replyPreviewText(message)
    }
    val scheme = remember(brand, isDark) { brand.linkBubbleColorScheme(isDark) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brand.messageScreenBackground(isDark)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            factory = { ctx ->
                LinkBubbleView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { view ->
                view.configure(
                    type = LinkBubbleView.BubbleType.MY,
                    title = "Суммаризация записи ВКС (аналог Plaud)",
                    description = "Рабочее пространство для обсуждения задач и обмена ключевыми обновлениями по текущему проекту",
                    url = "https://web.frisbee.live/im/2021316695",
                    domain = "",
                    labels = listOf("Группа"),
                    time = "10:15",
                    sendingState = LinkBubbleView.SendingState.READ,
                    replySender = replySender,
                    replyText = replyText,
                    colorScheme = scheme,
                )
            },
        )
    }
}
```

If `brand.messageScreenBackground(isDark)` does not exist in the project (check via Grep), substitute with `appSurface01(isDark)`.

- [ ] **Step 4: Verify `brand.messageScreenBackground` exists**

Run:
```powershell
Get-ChildItem -Path "..\Components-android-template\components\src" -Recurse -Include "*.kt" | Select-String -Pattern "fun messageScreenBackground" -List | Select-Object Path,LineNumber
```

If output is empty, edit the `Box(modifier = ...background(...))` line in `LinkBubbleOverlay` to:

```kotlin
.background(appSurface01(isDark))
```

- [ ] **Step 5: Build APK**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Install APK**

```powershell
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 7: Manual verify**

1. Open V5 picker on a text message.
2. DON'T pick a quote. Tap «Ссылка»: LinkBubble appears in PreviewArea center. Reply block inside shows sender name + full message preview text.
3. Tap «Ответ». Pick a fragment. Tap «Ссылка»: reply block inside LinkBubble now shows the FRAGMENT, not the full text.
4. Tap «Ответ» again. Selection state of the bubble should still be visible (cyan handles or fragment-marker).
5. The LinkBubble shows: title «Суммаризация записи ВКС (аналог Plaud)», description, link label «Группа», time 10:15 with double-check icon (READ status).

- [ ] **Step 8: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): V5 picker — add LinkBubble overlay on Ссылка tab"
```

---

### Task 4: `BottomStripLink` composable + per-tab swap

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: existing `BottomStrip(...)` composable, `selectedTab`, `appSurface01`, `appBasic`, `brand`, `isDark`.
- Produces:
  - `BottomStripLink()` — new private Composable, no parameters (mock data hardcoded per Global Constraints).
  - Replaces the unconditional `BottomStrip(...)` call at the end of the Column with a `when (selectedTab)` swap.

- [ ] **Step 1: Add the `BottomStripLink` Composable**

Append this function in `QuoteV5FullScreenContent.kt`, immediately AFTER the existing `BottomStrip(...)` private function definition:

```kotlin
@Composable
private fun BottomStripLink() {
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
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            DsIconImage(name = "link-chain", tint = brand.accentColor(isDark), sizeDp = 24)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Прикрепленная ссылка",
                style = DSTypography.body3M.toComposeTextStyle(),
                color = brand.accentColor(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "https://web.frisbee.live/im/2021316695",
                style = DSTypography.body4R.toComposeTextStyle(),
                color = appBasic(isDark, 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 2: Verify `DSTypography.body3M` and `body4R` exist**

Run:
```powershell
Get-ChildItem -Path "..\Components-android-template\components\src" -Recurse -Include "*.kt" | Select-String -Pattern "body3M|body4R" -List | Select-Object Path -Unique
```

If either is not found, fall back to closest existing styles. Check what's available:
```powershell
Get-ChildItem -Path "..\Components-android-template\components\src" -Recurse -Include "*.kt" | Select-String -Pattern "val body\d" | Select-Object -ExpandProperty Line -Unique
```

Substitute with nearest size/weight (e.g. `body1R` for the URL line, `body1M` for the «Прикрепленная ссылка» line). Record the substitution in the commit message.

- [ ] **Step 3: Verify `brand.accentColor(isDark)` exists**

Run:
```powershell
Get-ChildItem -Path "..\Components-android-template\components\src" -Recurse -Include "*.kt" | Select-String -Pattern "fun accentColor" -List | Select-Object Path,LineNumber
```

If not found, search for the right accessor:
```powershell
Get-ChildItem -Path "..\Components-android-template\components\src" -Recurse -Include "*.kt" | Select-String -Pattern "accent" | Select-Object Line -Unique
```

Use the canonical accent accessor for the brand (e.g. `brand.accentColor(isDark)` or `brand.primaryAccent(isDark)`).

- [ ] **Step 4: Swap BottomStrip per tab**

Find the existing call at the end of the Column (~line 285-291):

```kotlin
BottomStrip(
    senderName = senderName,
    previewText = previewText,
    popoverOpen = popoverOpen,
    menuState = menuState,
    onIconClick = { popoverOpen = !popoverOpen },
)
```

Wrap it with an `AnimatedContent`:

```kotlin
AnimatedContent(
    targetState = selectedTab,
    transitionSpec = {
        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    },
    label = "v5BottomStrip",
) { tab ->
    if (tab == 1) {
        BottomStripLink()
    } else {
        BottomStrip(
            senderName = senderName,
            previewText = previewText,
            popoverOpen = popoverOpen,
            menuState = menuState,
            onIconClick = { popoverOpen = !popoverOpen },
        )
    }
}
```

- [ ] **Step 5: Build APK**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Install APK**

```powershell
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 7: Manual verify**

1. Open V5 picker. Tab «Ответ» — BottomStrip shows sender + reply preview text + popover-toggle icon (existing behavior).
2. Tap «Ссылка» — BottomStrip fades into `link-chain` icon + «Прикрепленная ссылка» (accent color) + URL `https://web.frisbee.live/im/2021316695` (basic50).
3. Tap «Ответ» — BottomStrip fades back to reply preview.
4. No tap on the link strip should have any effect (no popover, no ripple).

- [ ] **Step 8: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): V5 picker — add BottomStripLink for Ссылка tab, per-tab swap"
```

---

### Task 5: Cross-tab side-effects (popover reset + handles clear + focus clear)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt`

**Interfaces:**
- Consumes: existing `popoverOpen`, `clearSelectionRef`, `tvRef` states.
- Produces: a single `LaunchedEffect(selectedTab)` that performs cleanup on Tab 1 entry.

- [ ] **Step 1: Add `LaunchedEffect(selectedTab)` for Tab 1 cleanup**

Locate the existing `LaunchedEffect(menuState) { ... }` block (with `snapshotRange` update from Task 2). Directly AFTER it, insert:

```kotlin
LaunchedEffect(selectedTab) {
    if (selectedTab == 1) {
        clearSelectionRef.value?.invoke()
        popoverOpen = false
        tvRef.value?.clearFocus()
    }
}
```

- [ ] **Step 2: Build APK**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install APK**

```powershell
$env:ANDROID_HOME = "C:\Users\Grond\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`.

- [ ] **Step 4: Manual verify cross-tab effects**

1. Open V5 picker on a text message.
2. Pick a fragment (handles + action-bar visible on bubble).
3. Tap «Ссылка»: handles disappear instantly; popover (if it was open) is closed; IME is hidden if it was up.
4. Tap «Ответ»: PreviewArea visible again; popover is NOT auto-shown (only by user tap on BottomStrip icon); handles are NOT auto-restored (user must tap bubble to re-select). `snapshotRange` is preserved — tapping «Сохранить» still attaches the previously selected fragment.
5. Rotate device on Tab 1: stays on Tab 1, no crash.
6. Rotate device on Tab 0: stays on Tab 0, `snapshotRange` preserved.

- [ ] **Step 5: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "feat(chatdetail): V5 picker — clear handles/popover/focus on Ссылка tab entry"
```

---

### Task 6: Final smoke + polish

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt` (only if visual issues found)

**Interfaces:** none (verification + polish only).

- [ ] **Step 1: Full smoke per spec checklist**

Walk through the spec's «Тестирование (manual smoke)» section. For each item, verify on device:

1. Open V5 picker for a text message → `selectedTab = 0` by default, layout as before.
2. On Tab 0 pick a fragment → reply-icon switches to `reply-quote` (Task 0 prior fix).
3. Tap «Ссылка» → header «Оформление ссылки», description «Так будет выглядеть...», LinkBubble with reply containing the fragment, BottomStrip shows link strip.
4. Tap «Ответ» → returns. `snapshotRange` preserved.
5. Without quote → Tab «Ссылка» → reply block in LinkBubble shows full message preview.
6. «Сохранить» from either tab — same outcome (selected quote applied).
7. BACK from either tab — closes picker, IME state correct.
8. Rotate on either tab — no crash, state preserved.

- [ ] **Step 2: Verify SegmentedControl readability over pattern**

Visually check on both light & dark themes (toggle in Profile or system). If the SegmentedControl container is hard to see against the chat pattern, increase contrast in code:

Locate the SegmentedControl `AndroidView` (Task 1, Step 5). Wrap it with a Box that has a subtle backdrop:

```kotlin
Box(
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(appSurface02(isDark).copy(alpha = 0.85f)),
) {
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
        modifier = Modifier.width(152.dp).height(32.dp),
    )
}
```

Apply ONLY if readability is poor. If the default scheme reads fine over the pattern (likely on `appSurface01`-tinted areas around the bubble), skip this step.

- [ ] **Step 3: Verify icon `link-chain` resolves**

If on device the Tab 1 BottomStrip shows a blank/missing icon, sync the icon. The `link-chain.svg` is in `../IconsAndColorsLibrary-cross-template/icons/`. The build script's `syncIconsFromLibrary` task should have copied it to `app/src/main/assets/icons/link-chain.svg` during preBuild.

Verify:
```powershell
Test-Path "app\src\main\assets\icons\link-chain.svg"
```

If `False`, run a clean assemble:
```powershell
.\gradlew.bat :app:syncIconsFromLibrary :app:assembleDebug
```

Re-install and verify icon shows.

- [ ] **Step 4: Commit any polish changes (only if Steps 2 or 3 required edits)**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuoteV5FullScreenContent.kt
git commit -m "fix(chatdetail): V5 picker — polish SegmentedControl backdrop / icon sync"
```

If no edits required, no commit.

- [ ] **Step 5: Push to origin**

```powershell
git push origin feature/quote-v5
```

Expected: branch pushed, ready for review.

---

## Self-Review

**Spec coverage:**
- ✅ `selectedTab` state (Task 1)
- ✅ SegmentedControl overlay TopCenter padding 8dp (Task 1)
- ✅ Header title per tab (Task 1)
- ✅ Description text per tab (Task 1)
- ✅ `snapshotRange` + `LaunchedEffect(menuState)` capture rule (Task 2)
- ✅ `extractQuote` helper (Task 2)
- ✅ Confirm callbacks fall back to `snapshotRange` (Task 2)
- ✅ LinkBubble overlay (Task 3) with mock data and `replySender`/`replyText` from snapshot
- ✅ PreviewArea remains mounted; LinkBubble overlays it (Task 3 — overlay block inside the same Box)
- ✅ BottomStripLink composable + per-tab swap with AnimatedContent fade 150ms (Task 4)
- ✅ Cross-tab side-effects: `clearSelectionRef`, `popoverOpen = false`, `tvRef.clearFocus()` (Task 5)
- ✅ Manual smoke checklist (Task 6)
- ✅ Polish for SegmentedControl contrast + icon sync (Task 6)

**Placeholder scan:** No TBDs. The Task 4 substitution paths (`body3M`/`body4R`, `accentColor`) are explicit Grep verifications with concrete fallback rules.

**Type consistency:**
- `selectedTab: Int` — used identically in all tasks.
- `snapshotRange: Pair<Int, Int>?` — defined in Task 2, consumed by Task 3 (`LinkBubbleOverlay`) with destructuring `(s, e)`.
- `extractQuote(message, start, end): String` — defined in Task 2, called from Task 3.
- `LinkBubbleView.BubbleType.MY`, `LinkBubbleView.SendingState.READ` — match component API verified during spec phase.
- `SegmentedControlView.configure(labels, selectedIndex, onSelect, colorScheme)` — matches verified component API.
- `BottomStripLink()` — zero parameters, mock data hardcoded.
- `LinkBubbleOverlay(message, senderPersona, isMine, snapshotRange)` — defined in Task 3 with these exact params; called from Task 3 step 2 with same param names.
