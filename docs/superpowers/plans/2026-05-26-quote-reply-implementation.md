# Quote Reply Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать partial quote-reply (ответ на выделенный фрагмент текста сообщения, как в Telegram) с 4 переключаемыми UX-вариантами вызова picker'а в одной debug-сборке. После QA финальный вариант остаётся, остальные 3 удаляются.

**Architecture:** Подход 2 — общее ядро (модель `ReplyPreview.quoteStart/End`, VM-state, кликабельный reply-block, `QuotePickerOverlay`, bubble-pulse highlight) + 4 picker-имплементации (INPLACE / MODAL / SHEET / FULLSCREEN). Variant хранится в `AppContainer.quoteVariant: MutableStateFlow<QuoteVariant>`, прокидывается через `LocalQuoteVariant: CompositionLocal`. Dev-toggle UI — в Calls-табе. View-walk в `BubblesView`/`MediaBubbleView`/`MessagePanelView` для доступа к internal TextView без правок `:components`.

**Tech Stack:** Kotlin · Jetpack Compose · `androidx.compose.foundation.text.BasicTextField` (selection state) · `androidx.compose.material3.ModalBottomSheet` · `android.text.Selection` + `android.view.ActionMode` (in-place selection) · `java.text.BreakIterator` (word boundary) · kotlinx-serialization.

**Spec:** `docs/superpowers/specs/2026-05-26-quote-reply-design.md`

---

## File Structure

**Create:**
- `core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt`
- `core/data/src/main/java/com/example/template/core/data/QuoteRangeMatch.kt`
- `core/data/src/test/java/com/example/template/core/data/QuoteSnapshotTest.kt`
- `core/data/src/test/java/com/example/template/core/data/QuoteRangeMatchTest.kt`
- `core/data/src/test/java/com/example/template/core/data/WordBoundaryTest.kt`
- `core/ui/src/main/java/com/example/template/core/ui/utils/BubbleTextProbe.kt`
- `core/ui/src/main/java/com/example/template/core/ui/utils/QuoteActionModeCallback.kt`
- `core/ui/src/main/java/com/example/template/core/ui/utils/WordBoundary.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerContent.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerSheet.kt`
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`

**Modify:**
- `core/model/src/main/java/com/example/template/core/model/Message.kt` — `ReplyPreview` + `quoteStart/End`.
- `app/src/main/java/com/example/template/AppContainer.kt` — `quoteVariant: MutableStateFlow<QuoteVariant>`.
- `app/src/main/java/com/example/template/MainActivity.kt` — `LocalQuoteVariant` provider, `QuotePickerOverlay`, highlight glue.
- `core/ui/src/main/java/com/example/template/core/ui/LocalAppBrand.kt` (or appropriate locals file) — `LocalQuoteVariant`.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt` — `onReplyClick` callback, view-walk wiring.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — V1 wiring + reply-block tap + highlight overlay.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` — quote state, methods, highlight.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — wiring.
- `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt` — dev-toggle section.

---

## Task 1: Extend ReplyPreview with quote offsets

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Message.kt:42-47`
- Create: `core/data/src/test/java/com/example/template/core/data/QuoteSnapshotTest.kt`

- [ ] **Step 1: Write failing test for serialization**

Create `core/data/src/test/java/com/example/template/core/data/QuoteSnapshotTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.ReplyPreview
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteSnapshotTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `full reply serializes without quote offsets`() {
        val rp = ReplyPreview(originalId = "m1", authorName = "Боб", text = "Полный текст")
        val s = json.encodeToString(rp)
        assertTrue("Should not contain quoteStart: $s", !s.contains("quoteStart"))
        assertTrue("Should not contain quoteEnd: $s", !s.contains("quoteEnd"))
    }

    @Test
    fun `quote reply roundtrips with offsets`() {
        val rp = ReplyPreview(
            originalId = "m1",
            authorName = "Боб",
            text = "лный",
            quoteStart = 2,
            quoteEnd = 6,
        )
        val s = json.encodeToString(rp)
        val back = json.decodeFromString<ReplyPreview>(s)
        assertEquals(rp, back)
        assertEquals(2, back.quoteStart)
        assertEquals(6, back.quoteEnd)
    }

    @Test
    fun `decoding legacy reply preview without offsets keeps nulls`() {
        val legacy = """{"originalId":"m1","authorName":"Боб","text":"привет"}"""
        val back = json.decodeFromString<ReplyPreview>(legacy)
        assertNull(back.quoteStart)
        assertNull(back.quoteEnd)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*QuoteSnapshotTest*"`
Expected: FAIL — compilation error на `quoteStart` / `quoteEnd` (поля ещё нет).

- [ ] **Step 3: Extend ReplyPreview**

Edit `core/model/src/main/java/com/example/template/core/model/Message.kt` строки 42-47 (заменить data class):

```kotlin
@Serializable
data class ReplyPreview(
    val originalId: String,
    val authorName: String,
    val text: String,
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*QuoteSnapshotTest*"`
Expected: PASS — все 3 теста зелёные.

- [ ] **Step 5: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Message.kt \
        core/data/src/test/java/com/example/template/core/data/QuoteSnapshotTest.kt
git commit -m "feat(quote): расширить ReplyPreview опциональными offsets фрагмента"
```

---

## Task 2: QuoteVariant enum + AppContainer + CompositionLocal

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt`
- Modify: `app/src/main/java/com/example/template/AppContainer.kt`
- Modify: `core/ui/src/main/java/com/example/template/core/ui/AppTheme.kt` (или там, где определены другие `LocalXxx`; см. репозиторий)
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`

- [ ] **Step 1: Создать enum QuoteVariant**

Create `core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt`:

```kotlin
package com.example.template.core.model

enum class QuoteVariant { INPLACE, MODAL, SHEET, FULLSCREEN }
```

- [ ] **Step 2: Добавить state в AppContainer**

В `app/src/main/java/com/example/template/AppContainer.kt` — найти класс `AppContainer`, добавить поле (после существующих `Bitmap`-кэшей / brand-state'ов):

```kotlin
val quoteVariant: MutableStateFlow<QuoteVariant> = MutableStateFlow(QuoteVariant.INPLACE)
```

Добавить импорты:
```kotlin
import com.example.template.core.model.QuoteVariant
import kotlinx.coroutines.flow.MutableStateFlow
```

- [ ] **Step 3: Добавить CompositionLocal**

Найти в `:core:ui` файл, где определены `LocalAppBrand` / `LocalIsDark` / `LocalBitmapCache` (вероятно `core/ui/src/main/java/com/example/template/core/ui/AppTheme.kt` или `Locals.kt`). Добавить рядом:

```kotlin
import com.example.template.core.model.QuoteVariant
import androidx.compose.runtime.compositionLocalOf

val LocalQuoteVariant = compositionLocalOf<QuoteVariant> { QuoteVariant.INPLACE }
```

- [ ] **Step 4: Прокинуть в MainActivity**

В `app/src/main/java/com/example/template/MainActivity.kt` найти место, где даются `CompositionLocalProvider`-привязки (вокруг `AppTheme` / в `setContent`). Добавить чтение state + provider:

```kotlin
val quoteVariant by appContainer.quoteVariant.collectAsState()
CompositionLocalProvider(LocalQuoteVariant provides quoteVariant) {
    // ... существующее содержимое AppTheme/AppScaffold ...
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/QuoteVariant.kt \
        app/src/main/java/com/example/template/AppContainer.kt \
        app/src/main/java/com/example/template/MainActivity.kt \
        core/ui/src/main/java/com/example/template/core/ui/
git commit -m "feat(quote): QuoteVariant enum + CompositionLocal + AppContainer state"
```

---

## Task 3: ReplyContext с quote-полями + setQuote/clearQuote

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt:155-160`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt:121-130, 276-...`

- [ ] **Step 1: Расширить ReplyContext + toSnapshot**

Заменить блок строк 155-157 (`data class ReplyContext`) в `ChatDetailViewModel.kt`:

```kotlin
data class ReplyContext(
    val originalId: String,
    val authorName: String,
    val originalFullText: String,
    val previewText: String,
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
) {
    fun toSnapshot() = ReplyPreview(
        originalId = originalId,
        authorName = authorName,
        text = previewText,
        quoteStart = quoteStart,
        quoteEnd = quoteEnd,
    )
}
```

- [ ] **Step 2: Обновить startReply (строка 121)**

Заменить тело `startReply` (строки 121-130):

```kotlin
fun startReply(messageId: String) {
    val original = _messages.value.firstOrNull { it.id == messageId } ?: return
    val fullText = resolveMessagePreview(original)
    _editContext.value = null
    _selection.value = null
    _replyContext.value = ReplyContext(
        originalId = original.id,
        authorName = resolveAuthorName(original),
        originalFullText = fullText,
        previewText = fullText,
        quoteStart = null,
        quoteEnd = null,
    )
}
```

- [ ] **Step 3: Добавить setQuote / clearQuote**

После `dismissReplyContext` (строка 174-176) добавить:

```kotlin
fun setQuote(start: Int, end: Int) {
    val curr = _replyContext.value ?: return
    if (start < 0 || end > curr.originalFullText.length || start >= end) return
    val fragment = curr.originalFullText.substring(start, end)
    _replyContext.value = curr.copy(
        previewText = fragment,
        quoteStart = start,
        quoteEnd = end,
    )
}

fun clearQuote() {
    val curr = _replyContext.value ?: return
    _replyContext.value = curr.copy(
        previewText = curr.originalFullText,
        quoteStart = null,
        quoteEnd = null,
    )
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(quote): ReplyContext с quote-offsets + setQuote/clearQuote"
```

---

## Task 4: VM — openQuotePicker / dismissQuotePicker + variant injection

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` (конструктор + методы)
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` (передача variantState из AppContainer)

- [ ] **Step 1: Добавить quoteVariant параметр в конструктор VM**

Найти конструктор `ChatDetailViewModel` в `ChatDetailViewModel.kt`. Добавить параметр:

```kotlin
class ChatDetailViewModel(
    private val repository: MessengerRepository,
    private val chatId: String,
    private val currentUserId: String,
    private val quoteVariant: StateFlow<QuoteVariant>,   // NEW
) : ViewModel() { ... }
```

Импорты:
```kotlin
import com.example.template.core.model.QuoteVariant
import kotlinx.coroutines.flow.StateFlow
```

- [ ] **Step 2: Добавить _quotePickerVisible state + openQuotePicker/dismissQuotePicker**

После блока с `_replyContext`/`replyContext` объявлениями (строки 159-160 в текущей версии) добавить:

```kotlin
private val _quotePickerVisible = MutableStateFlow(false)
val quotePickerVisible: StateFlow<Boolean> = _quotePickerVisible.asStateFlow()

fun openQuotePicker() {
    if (quoteVariant.value == QuoteVariant.INPLACE) return
    if (_replyContext.value == null) return
    _quotePickerVisible.value = true
}

fun dismissQuotePicker() {
    _quotePickerVisible.value = false
}
```

Также в `dismissReplyContext()` (строка 174) и `sendText`/`sendMedia` (строки 142, 149) добавить сброс picker'а в конце:

```kotlin
fun dismissReplyContext() {
    _replyContext.value = null
    _quotePickerVisible.value = false   // NEW
}

fun sendText(body: String) {
    if (body.isBlank()) return
    val reply = _replyContext.value?.toSnapshot()
    viewModelScope.launch { repository.sendTextMessage(chatId, body, replyTo = reply) }
    _replyContext.value = null
    _quotePickerVisible.value = false   // NEW
}
// аналогично для sendMedia
```

- [ ] **Step 3: Найти factory для VM и обновить**

В `ChatDetailScreen.kt` (или там, где создаётся `ChatDetailViewModel` — обычно через `viewModel(factory = ...)`) — найти factory, добавить передачу `appContainer.quoteVariant.asStateFlow()`:

```kotlin
val appContainer = (context.applicationContext as TemplateApp).appContainer
val viewModel: ChatDetailViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            ChatDetailViewModel(
                repository = appContainer.repository,
                chatId = chatId,
                currentUserId = appContainer.currentUserId,
                quoteVariant = appContainer.quoteVariant.asStateFlow(),  // NEW
            )
        }
    },
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): VM openQuotePicker + variant injection"
```

---

## Task 5: VM — startQuoteInPlace + highlightedMessageId

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Добавить startQuoteInPlace метод**

После метода `clearQuote` (добавленного в Task 3) дописать:

```kotlin
fun startQuoteInPlace(messageId: String, start: Int, end: Int) {
    startReply(messageId)
    setQuote(start, end)
}
```

- [ ] **Step 2: Добавить highlightedMessageId state + requestHighlight**

В нижней части класса VM (после остальных state-flow'ов) добавить:

```kotlin
private val _highlightedMessageId = MutableStateFlow<String?>(null)
val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

fun requestHighlight(messageId: String) {
    _highlightedMessageId.value = messageId
    viewModelScope.launch {
        kotlinx.coroutines.delay(1400L)
        if (_highlightedMessageId.value == messageId) {
            _highlightedMessageId.value = null
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(quote): startQuoteInPlace + highlightedMessageId"
```

---

## Task 6: Word boundary utility (BreakIterator) + tests

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/utils/WordBoundary.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/WordBoundaryTest.kt`

Утилита pure-JVM — для тестируемости положу в `:core:data` тесты, импорт-симметрия с реализацией в `:core:ui`. (Использую тот факт, что `:core:data` зависит от `:core:ui`? Если нет — переложить тест в `:core:ui/src/test/`. Проверь, есть ли `src/test/` в `:core:ui`; если нет — добавь `testDebugUnitTest` в `:core:ui/build.gradle.kts` через testOptions.)

**Альтернатива:** утилиту положить в `:core:data/utils` (тогда и тест там). Семантически она ничейная — но JVM-логика без android-зависимостей. Оптимально — в `:core:data` (доступно из VM и из MessageList через :core:ui-зависимость от :core:data, проверь по graph'у; обычно :core:ui зависит от :core:data).

**Plan-time decision:** размещаю в `:core:data` (BreakIterator — java-only, без android-зависимостей).

Переписываю Files:
- Create: `core/data/src/main/java/com/example/template/core/data/WordBoundary.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/WordBoundaryTest.kt`

- [ ] **Step 1: Написать тест**

Create `core/data/src/test/java/com/example/template/core/data/WordBoundaryTest.kt`:

```kotlin
package com.example.template.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordBoundaryTest {

    @Test
    fun `word at middle returns word range`() {
        // "Привет мир" — слово "мир" по offset 7-10
        val r = wordBoundaryAt("Привет мир", offset = 8)
        assertEquals(7..10, r)
    }

    @Test
    fun `word at start`() {
        val r = wordBoundaryAt("Привет мир", offset = 0)
        assertEquals(0..6, r)
    }

    @Test
    fun `word at space falls to adjacent word`() {
        // offset 6 — пробел. BreakIterator вернёт диапазон ближайшего слова.
        val r = wordBoundaryAt("Привет мир", offset = 6)
        assertEquals(true, r.first in 0..7 && r.last in 6..10)
    }

    @Test
    fun `offset out of range returns null`() {
        assertEquals(null, wordBoundaryAt("hi", offset = -1))
        assertEquals(null, wordBoundaryAt("hi", offset = 100))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*WordBoundaryTest*"`
Expected: FAIL — `wordBoundaryAt` ещё не определена.

- [ ] **Step 3: Написать имплементацию**

Create `core/data/src/main/java/com/example/template/core/data/WordBoundary.kt`:

```kotlin
package com.example.template.core.data

import java.text.BreakIterator

/**
 * Возвращает диапазон слова, к которому относится символ под `offset`,
 * или null если offset за пределами строки.
 *
 * Используется для V1 in-place quote-selection: long-press на бабле даёт
 * координаты, конвертируются в char-offset через TextView.getOffsetForPosition,
 * затем здесь определяется граница слова, и `TextView.setSelection(start, end)`
 * показывает handles на этом слове.
 */
fun wordBoundaryAt(text: String, offset: Int): IntRange? {
    if (offset < 0 || offset > text.length) return null
    val iter = BreakIterator.getWordInstance()
    iter.setText(text)
    val start = run {
        val p = iter.preceding(offset + 1)
        if (p == BreakIterator.DONE) 0 else p
    }
    val end = run {
        val f = iter.following(offset)
        if (f == BreakIterator.DONE) text.length else f
    }
    return start..end
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*WordBoundaryTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/WordBoundary.kt \
        core/data/src/test/java/com/example/template/core/data/WordBoundaryTest.kt
git commit -m "feat(quote): wordBoundaryAt utility + tests"
```

---

## Task 7: QuoteRangeMatch utility + tests

**Files:**
- Create: `core/data/src/main/java/com/example/template/core/data/QuoteRangeMatch.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/QuoteRangeMatchTest.kt`

- [ ] **Step 1: Написать тест**

Create `core/data/src/test/java/com/example/template/core/data/QuoteRangeMatchTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.ReplyPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteRangeMatchTest {

    @Test
    fun `full reply matches always when present`() {
        val snap = ReplyPreview("m1", "Боб", "что угодно")
        assertEquals(MatchResult.Match, matchesQuote("любой исходник", snap))
    }

    @Test
    fun `quote matches when substring equal`() {
        val snap = ReplyPreview("m1", "Боб", "ивет", quoteStart = 1, quoteEnd = 5)
        assertEquals(MatchResult.Match, matchesQuote("Привет, мир", snap))
    }

    @Test
    fun `quote mismatch when text edited`() {
        val snap = ReplyPreview("m1", "Боб", "ивет", quoteStart = 1, quoteEnd = 5)
        assertEquals(MatchResult.QuoteMismatch, matchesQuote("Прощай, мир", snap))
    }

    @Test
    fun `quote out of range counts as mismatch`() {
        val snap = ReplyPreview("m1", "Боб", "frag", quoteStart = 10, quoteEnd = 14)
        assertEquals(MatchResult.QuoteMismatch, matchesQuote("short", snap))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*QuoteRangeMatchTest*"`
Expected: FAIL — `matchesQuote` / `MatchResult` не определены.

- [ ] **Step 3: Написать имплементацию**

Create `core/data/src/main/java/com/example/template/core/data/QuoteRangeMatch.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.ReplyPreview

enum class MatchResult { Match, QuoteMismatch }

/**
 * Проверяет, что quote-фрагмент в reply-snapshot всё ещё корректно совпадает
 * с подстрокой оригинального текста. Используется для tap-to-jump: если
 * совпадает — подсветить, если нет — показать toast «Цитируемый фрагмент
 * не найден». Full-reply (без offsets) всегда Match.
 */
fun matchesQuote(originalText: String, snapshot: ReplyPreview): MatchResult {
    val start = snapshot.quoteStart ?: return MatchResult.Match
    val end = snapshot.quoteEnd ?: return MatchResult.Match
    if (start < 0 || end > originalText.length || start >= end) return MatchResult.QuoteMismatch
    val actual = originalText.substring(start, end)
    return if (actual == snapshot.text) MatchResult.Match else MatchResult.QuoteMismatch
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*QuoteRangeMatchTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/QuoteRangeMatch.kt \
        core/data/src/test/java/com/example/template/core/data/QuoteRangeMatchTest.kt
git commit -m "feat(quote): matchesQuote utility + tests"
```

---

## Task 8: Dev-toggle UI в Calls-табе

**Files:**
- Modify: `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt`

- [ ] **Step 1: Найти текущий CallsScreen**

Открыть `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt`. Понять, как сейчас устроена композиция (Column / LazyColumn).

- [ ] **Step 2: Добавить dev-секцию в начале содержимого**

В начало основного `Column`-а / `LazyColumn`-а (перед списком звонков) добавить:

```kotlin
@Composable
private fun QuoteVariantDevSection(
    current: QuoteVariant,
    onChange: (QuoteVariant) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "Quote variant (dev)",
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        QuoteVariant.values().forEach { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChange(v) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = v == current, onClick = { onChange(v) })
                Spacer(Modifier.width(8.dp))
                Text(v.name)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
```

- [ ] **Step 3: Передать state + callback из CallsScreen**

В `CallsScreen` composable получить `AppContainer`-flow и зовать setter:

```kotlin
val context = LocalContext.current
val appContainer = remember(context) {
    (context.applicationContext as TemplateApp).appContainer
}
val quoteVariant by appContainer.quoteVariant.collectAsState()

Column {
    QuoteVariantDevSection(
        current = quoteVariant,
        onChange = { appContainer.quoteVariant.value = it },
    )
    // ... существующее содержимое CallsScreen ...
}
```

Импорты:
```kotlin
import com.example.template.core.model.QuoteVariant
import com.example.template.TemplateApp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
```

- [ ] **Step 4: Install + manual verify**

Run:
```
./gradlew :app:installDebug -Pbrand=foxtrot
```
Ожидаемое поведение: вкладка «Звонки» открывается, сверху видна секция «Quote variant (dev)» с 4 RadioButton'ами, по умолчанию выбран INPLACE. Тап на другую опцию переключает state (визуально checked → выбранному).

- [ ] **Step 5: Commit**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt
git commit -m "feat(quote): dev-toggle секция в Calls-табе"
```

---

## Task 9: Reply-block в MessagePanel становится кликабельным

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt:34, 43-56, 96-118`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

Reply-block в `MessagePanelView` — внутренний View компонента. Доступа к OnClickListener из публичного API нет. Делаю через view-walk: после `update`-блока AndroidView в MessagePanelHost'е ищем TextView с текстом `replyContext.previewText`, поднимаемся к ближайшему `ViewGroup`-родителю и вешаем clickListener.

- [ ] **Step 1: Добавить onReplyClick в сигнатуру MessagePanelHost**

Изменить сигнатуру (строка 43-54), добавить параметр после `onReplyClose`:

```kotlin
@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (attachments: List<MediaAttachment>, caption: String?) -> Unit = { _, _ -> },
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    onReplyClick: () -> Unit = {},    // NEW
    editContext: EditDisplay? = null,
    onEditClose: () -> Unit = {},
    onSaveEdit: (text: String, attachments: List<MediaAttachment>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    labels: List<String> = DEFAULT_LABELS,
    onPanelReady: ((MessagePanelView) -> Unit)? = null,
)
```

- [ ] **Step 2: Wire view-walk в update-блоке**

Найти `AndroidView(... update = { ... })` для `MessagePanelView` в MessagePanelHost. Сразу после вызова `view.configure(config, colorScheme)` (или эквивалентного — найди в текущем коде) добавить:

```kotlin
// Reply-block clickability: view-walk через preview-TextView. Fragile,
// см. spec § Tap-on-reply-block. Перевешиваем listener на каждый update,
// т.к. MessagePanelView пересоздаёт внутренние views при reconfigure.
val preview = replyContext?.previewText
if (preview != null) {
    val tv = view.findMessageTextView(preview)
    val container = tv?.parent as? android.view.ViewGroup
    container?.setOnClickListener { onReplyClick() }
} else {
    // На всякий случай — снять listener, если reply пропал.
    // (не критично, View пересоздаётся, но защита)
}
```

Импорт:
```kotlin
import com.example.template.core.ui.utils.findMessageTextView
```

(функция `findMessageTextView` будет создана в Task 10; на этом шаге компиляция упадёт до Task 10 — это нормально, оставляем закоммитить вместе с probe в одной задаче. Альтернатива: закоммитить здесь stub без probe.)

**Plan-time correction:** перенесу wiring view-walk сюда уже после Task 10. В этой задаче — ТОЛЬКО сигнатура + wiring в ChatDetailScreen, без probe.

- [ ] **Step 2 (corrected): Только сигнатура + ChatDetailScreen wiring**

Изменить только сигнатуру (Step 1 выше). Пропускаем view-walk в `update`-блоке — добавим в Task 10. На этом шаге `onReplyClick` приходит сверху, но никуда внутри MessagePanelHost не передаётся (placeholder).

В `ChatDetailScreen.kt` найти вызов `MessagePanelHost(...)`. Добавить `onReplyClick = viewModel::openQuotePicker`:

```kotlin
MessagePanelHost(
    onSendText = viewModel::sendText,
    onSendMedia = viewModel::sendMedia,
    replyContext = replyContext?.let { ReplyDisplay(it.authorName, it.previewText) },
    onReplyClose = viewModel::dismissReplyContext,
    onReplyClick = viewModel::openQuotePicker,    // NEW
    editContext = editContext?.let { EditDisplay(...) },
    // ... остальные параметры ...
)
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL (без `findMessageTextView` пока компилируется, т.к. в Task 9 он не используется).

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): onReplyClick параметр в MessagePanelHost + wiring"
```

---

## Task 10: BubbleTextProbe — view-walk утилиты

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/utils/BubbleTextProbe.kt`
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt` (доделать view-walk из Task 9)

- [ ] **Step 1: Создать probe**

Create `core/ui/src/main/java/com/example/template/core/ui/utils/BubbleTextProbe.kt`:

```kotlin
package com.example.template.core.ui.utils

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Эвристический поиск TextView внутри View по совпадению текста.
 * Используется для:
 *  - V1 in-place quote (текст сообщения в BubblesView/MediaBubbleView),
 *  - reply-block clickability (preview-text в MessagePanelView и в reply-блоке внутри бабла).
 *
 * Fragility-нота: если `:components` поменяет внутреннюю структуру (например, разобьёт
 * текст на несколько TextView), эта функция вернёт null или не тот View. В этом случае
 * нужно согласовать с пользователем правку `:components` — добавить публичный API
 * (например `BubblesView.selectableTextView(): TextView`). Локальный hack предпочтительнее
 * по умолчанию — см. CLAUDE.md.
 */
fun View.findMessageTextView(messageText: String): TextView? {
    if (this is TextView && text?.toString() == messageText) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findMessageTextView(messageText)?.let { return it }
        }
    }
    return null
}

/**
 * Находит контейнер reply-блока внутри бабла: TextView с текстом sender'а,
 * поднимаемся к его View-родителю (ViewGroup). Используется для tap-to-jump.
 */
fun View.findReplyBlockContainer(replySenderText: String): View? {
    val tv = findMessageTextView(replySenderText) ?: return null
    return tv.parent as? View
}
```

- [ ] **Step 2: Доделать view-walk в MessagePanelHost (из Task 9)**

Открыть `MessagePanelHost.kt`. Найти `AndroidView(... update = { ... })`. Сразу после `view.configure(...)` добавить:

```kotlin
// Reply-block clickability через view-walk. Fragile — см. BubbleTextProbe.kt § note.
val previewText = replyContext?.previewText
if (previewText != null && previewText.isNotEmpty()) {
    val tv = view.findMessageTextView(previewText)
    val container = tv?.parent as? android.view.ViewGroup
    container?.setOnClickListener { onReplyClick() }
}
```

Импорт:
```kotlin
import com.example.template.core.ui.utils.findMessageTextView
```

- [ ] **Step 3: Install + manual verify**

Run:
```
./gradlew :app:installDebug -Pbrand=foxtrot
```
Ручной тест:
1. В Calls-табе переключить variant на MODAL.
2. Открыть чат, swipe-влево по любому Text-сообщению (или ctx-menu → «Ответить»).
3. В MessagePanel появляется reply-block с текстом оригинала.
4. Тап по reply-block'у — должен вызвать `openQuotePicker()`. Поскольку `QuotePickerOverlay` ещё не реализован (Task 14), визуально ничего не произойдёт — но `_quotePickerVisible` станет `true`.
5. Можно временно добавить лог в `openQuotePicker` (`android.util.Log.d("QUOTE", "open")`) и проверить через logcat: `adb logcat -s QUOTE:D`.

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/utils/BubbleTextProbe.kt \
        core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt
git commit -m "feat(quote): BubbleTextProbe + reply-block click wiring"
```

---

## Task 11: QuoteActionModeCallback

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/utils/QuoteActionModeCallback.kt`

- [ ] **Step 1: Создать callback**

Create `core/ui/src/main/java/com/example/template/core/ui/utils/QuoteActionModeCallback.kt`:

```kotlin
package com.example.template.core.ui.utils

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView

/**
 * Custom ActionMode.Callback для V1 in-place quote-selection.
 * Добавляет пункт «Цитировать» в floating-меню над выделенным текстом.
 * Системные Copy/Share/Select all остаются.
 *
 * Установка:
 *   tv.customSelectionActionModeCallback = QuoteActionModeCallback(tv) { start, end ->
 *       viewModel.startQuoteInPlace(messageId, start, end)
 *   }
 */
class QuoteActionModeCallback(
    private val tv: TextView,
    private val onQuote: (start: Int, end: Int) -> Unit,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_QUOTE_ID, 0, "Цитировать")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == MENU_QUOTE_ID) {
            val s = tv.selectionStart
            val e = tv.selectionEnd
            if (s in 0..tv.text.length && e in 0..tv.text.length && s < e) {
                onQuote(s, e)
            }
            mode.finish()
            return true
        }
        return false   // Copy/Share/Select all — обрабатывает система
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit

    companion object { const val MENU_QUOTE_ID = 1 }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :core:ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/utils/QuoteActionModeCallback.kt
git commit -m "feat(quote): QuoteActionModeCallback с пунктом «Цитировать»"
```

---

## Task 12: V1 wiring в MessageList

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt:223-263, 474-513`

V1 включается, когда:
- `LocalQuoteVariant.current == INPLACE`
- В selection ровно 1 message
- Этот message — Text или Media-with-caption

Логика:
1. Перехватить координаты long-press'а на бабле и сохранить в `remember`-state ключённое по messageId.
2. В `AndroidView.update`-блоке (BubblesView и MediaBubbleView) — если все три условия выполнены, найти TextView, конвертировать координаты в char-offset, определить word-boundary, выставить `setTextIsSelectable(true)` + `setSelection(start, end)` + `customSelectionActionModeCallback`.
3. Если variant != INPLACE или selection пустой/не-1 — `setTextIsSelectable(false)` чтобы handles не висели.

- [ ] **Step 1: Поправить long-press handler чтобы передавал Offset**

Найти `onBubbleLongPress` (строки 256-263 в текущей версии). Сейчас он принимает только `id`. Изменить на `(id, offset)`:

Поиск в `MessageList.kt`:
```kotlin
onLongPress = { 
    viewModel.startSelection(message.id)  // или похожее
}
```
Заменить на:
```kotlin
onLongPress = { offset ->
    onBubbleLongPress(message.id, offset)
}
```

И в верхнем уровне MessageList signature добавить `(String, Offset) -> Unit`:
```kotlin
fun MessageList(
    // ...
    onBubbleLongPress: (id: String, offset: Offset) -> Unit,
    // ...
)
```

В ChatDetailScreen.kt обновить вызов:
```kotlin
onBubbleLongPress = { id, offset -> 
    pendingQuoteAnchor.value = id to offset
    viewModel.startSelection(id)
},
```

- [ ] **Step 2: Состояние pendingQuoteAnchor**

В MessageList.kt — добавить state на верху Composable:

```kotlin
// V1: координаты long-press'а для определения слова под пальцем.
val pendingQuoteAnchor = remember { mutableStateOf<Pair<String, Offset>?>(null) }
```

Импорты:
```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
```

В ChatDetailScreen.kt — передать через параметр-callback (как написано в Step 1).

- [ ] **Step 3: V1 wiring в AndroidView.update (BubblesView)**

Найти AndroidView для `BubblesView` (строки 474-513). После `view.configure(...)` добавить:

```kotlin
// V1 — INPLACE quote selection
val variant = LocalQuoteVariant.current
val isSingleSelected = selectionIds?.let { it.size == 1 && message.id in it } ?: false
val isTextOrMedia = message is Message.Text || (message is Message.Media && !message.caption.isNullOrEmpty())

if (variant == QuoteVariant.INPLACE && isSingleSelected && isTextOrMedia) {
    val msgText = when (message) {
        is Message.Text -> message.body
        is Message.Media -> message.caption.orEmpty()
        else -> ""
    }
    val tv = view.findMessageTextView(msgText)
    if (tv != null) {
        tv.setTextIsSelectable(true)
        tv.customSelectionActionModeCallback = QuoteActionModeCallback(tv) { start, end ->
            onQuoteSelected(message.id, start, end)
        }
        val anchor = pendingQuoteAnchor.value
        if (anchor != null && anchor.first == message.id) {
            val bubbleLoc = IntArray(2); view.getLocationOnScreen(bubbleLoc)
            val tvLoc = IntArray(2); tv.getLocationOnScreen(tvLoc)
            val tvX = (bubbleLoc[0] + anchor.second.x) - tvLoc[0]
            val tvY = (bubbleLoc[1] + anchor.second.y) - tvLoc[1]
            val charOffset = try { tv.getOffsetForPosition(tvX, tvY) } catch (e: Exception) { -1 }
            val range = if (charOffset >= 0) wordBoundaryAt(msgText, charOffset) else null
            if (range != null) {
                tv.requestFocus()
                tv.setSelection(range.first, range.last)
            } else {
                tv.requestFocus()
                tv.setSelection(0, msgText.length)   // fallback selectAll
            }
            pendingQuoteAnchor.value = null   // consume anchor
        }
    }
} else {
    // Снять selectable, чтобы handles не висели в чужих режимах
    val msgText = when (message) {
        is Message.Text -> message.body
        is Message.Media -> message.caption.orEmpty()
        else -> ""
    }
    view.findMessageTextView(msgText)?.let { tv ->
        if (tv.isTextSelectable) {
            tv.setTextIsSelectable(false)
        }
    }
}
```

Импорты:
```kotlin
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.LocalQuoteVariant
import com.example.template.core.ui.utils.findMessageTextView
import com.example.template.core.ui.utils.QuoteActionModeCallback
import com.example.template.core.data.wordBoundaryAt
```

- [ ] **Step 4: Аналогичный wiring в MediaBubbleView**

Если MediaBubbleView рендерится в отдельном `AndroidView`-блоке — повторить тот же код с `msgText = message.caption`. Не дублировать через extracted helper, чтобы избежать передачи через MutableState (helper в update-блоке).

(Если структура иная — повторить в каждом AndroidView, обрабатывающем text-message-baked bubbles.)

- [ ] **Step 5: Добавить onQuoteSelected callback**

В MessageList signature — параметр:
```kotlin
onQuoteSelected: (id: String, start: Int, end: Int) -> Unit
```

В ChatDetailScreen.kt — передать `viewModel::startQuoteInPlace`:
```kotlin
onQuoteSelected = { id, start, end -> viewModel.startQuoteInPlace(id, start, end) },
```

- [ ] **Step 6: Install + manual verify**

Run: `./gradlew :app:installDebug -Pbrand=foxtrot`

Ручной тест:
1. Variant=INPLACE (default).
2. Открыть чат с Text-сообщениями.
3. Long-press на любом Text-бабле в области текста → multi-select bar появляется + handles на тексте на выбранном слове.
4. Drag handles — выделение меняется.
5. Floating menu должен показать «Цитировать» + системные Copy/Share.
6. Тап «Цитировать» → multi-select исчезает, MessagePanel показывает reply-block с цитатой.

- [ ] **Step 7: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): V1 in-place selection wiring в MessageList"
```

---

## Task 13: QuotePickerContent (shared)

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerContent.kt`

- [ ] **Step 1: Создать composable**

Create `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerContent.kt`:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Общий контент QuotePicker для всех 3 шкур (Modal/Sheet/FullScreen).
 * BasicTextField(readOnly=true) сохраняет нативные selection-handles и
 * отдаёт start/end через TextFieldValue.selection.
 */
@Composable
fun QuotePickerContent(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tfv by remember {
        mutableStateOf(
            TextFieldValue(
                text = fullText,
                selection = TextRange(initialStart.coerceIn(0, fullText.length), initialEnd.coerceIn(0, fullText.length)),
            )
        )
    }
    Column(modifier) {
        Text(
            "Выберите фрагмент для цитаты",
            style = LocalTextStyle.current,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(16.dp),
        )
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it },
            readOnly = true,
            textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(LocalContentColor.current),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Отмена") }
            Spacer(Modifier.height(0.dp).also {})
            Button(
                onClick = { onConfirm(tfv.selection.start, tfv.selection.end) },
                enabled = !tfv.selection.collapsed,
            ) { Text("Цитировать") }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :feature:chatdetail:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerContent.kt
git commit -m "feat(quote): QuotePickerContent — selectable preview на BasicTextField"
```

---

## Task 14: QuotePickerModal / Sheet / FullScreen (3 шкуры)

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt`
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerSheet.kt`
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt`

- [ ] **Step 1: QuotePickerModal**

Create:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun QuotePickerModal(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
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

- [ ] **Step 2: QuotePickerSheet**

Create:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotePickerSheet(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
```

- [ ] **Step 3: QuotePickerFullScreen**

Create:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotePickerFullScreen(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column {
                    TopAppBar(
                        title = { Text("Цитата") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        },
                    )
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
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :feature:chatdetail:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerModal.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerSheet.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerFullScreen.kt
git commit -m "feat(quote): три шкуры picker'а — Modal / Sheet / FullScreen"
```

---

## Task 15: QuotePickerOverlay + MainActivity wiring

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`

- [ ] **Step 1: QuotePickerOverlay**

Create:

```kotlin
package com.example.template.feature.chatdetail.quotepicker

import androidx.compose.runtime.Composable
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.LocalQuoteVariant

@Composable
fun QuotePickerOverlay(
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    when (LocalQuoteVariant.current) {
        QuoteVariant.INPLACE -> Unit
        QuoteVariant.MODAL -> QuotePickerModal(fullText, initialStart, initialEnd, onConfirm, onDismiss)
        QuoteVariant.SHEET -> QuotePickerSheet(fullText, initialStart, initialEnd, onConfirm, onDismiss)
        QuoteVariant.FULLSCREEN -> QuotePickerFullScreen(fullText, initialStart, initialEnd, onConfirm, onDismiss)
    }
}
```

- [ ] **Step 2: Wire в MainActivity**

В `app/src/main/java/com/example/template/MainActivity.kt` — найти место, где сейчас рендерится ChatDetail-overlay (поверх MainScaffold в Box). Добавить QuotePickerOverlay рядом, читая state из VM:

```kotlin
// ChatDetailViewModel сейчас живёт в feature:chatdetail — он создаётся через viewModel(...)
// внутри ChatDetailScreen, и снаружи (в MainActivity) к нему нет прямого доступа.
// Решение: подать picker visible state через ChatDetailScreen параметром.
```

**Plan-time correction:** Поскольку VM создаётся внутри ChatDetailScreen, проще держать QuotePickerOverlay внутри ChatDetailScreen (а не в MainActivity).

- [ ] **Step 2 (corrected): Wire внутри ChatDetailScreen**

В `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — добавить рендер picker'а на верхнем уровне Box (поверх MessageList + MessagePanel):

```kotlin
val replyContextState by viewModel.replyContext.collectAsState()
val pickerVisible by viewModel.quotePickerVisible.collectAsState()

val replyCtx = replyContextState
if (replyCtx != null && pickerVisible) {
    QuotePickerOverlay(
        fullText = replyCtx.originalFullText,
        initialStart = replyCtx.quoteStart ?: 0,
        initialEnd = replyCtx.quoteEnd ?: replyCtx.originalFullText.length,
        onConfirm = { start, end ->
            viewModel.setQuote(start, end)
            viewModel.dismissQuotePicker()
        },
        onDismiss = { viewModel.dismissQuotePicker() },
    )
}
```

Импорт:
```kotlin
import com.example.template.feature.chatdetail.quotepicker.QuotePickerOverlay
```

- [ ] **Step 3: Install + manual verify**

Run: `./gradlew :app:installDebug -Pbrand=foxtrot`

Ручной тест:
1. Variant=MODAL. Open chat → swipe-влево по сообщению → reply-block в панели → тап на reply-block → появляется Modal-диалог с selectable preview и кнопками «Отмена»/«Цитировать».
2. Тащим handles, тапаем «Цитировать» → reply-block обновляется с цитатой.
3. Variant=SHEET — тоже самое, но bottom-sheet снизу.
4. Variant=FULLSCREEN — полноэкранный диалог с TopAppBar.
5. Variant=INPLACE — тап на reply-block НИЧЕГО не делает (`openQuotePicker` early-return).

- [ ] **Step 4: Commit**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/quotepicker/QuotePickerOverlay.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): QuotePickerOverlay диспатч + wiring в ChatDetailScreen"
```

---

## Task 16: Tap-on-reply-block в бабле → scroll + toast

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Найти reply-block в бабле и повесить onClick**

В `MessageList.kt` в `AndroidView.update`-блоке `BubblesView` (после view-walk-логики V1) добавить, если `message.replyTo != null`:

```kotlin
val replyTo = message.replyTo
if (replyTo != null) {
    val replyBlockContainer = view.findReplyBlockContainer(replyTo.authorName)
    replyBlockContainer?.setOnClickListener {
        onReplyBlockTap(replyTo)
    }
}
```

В сигнатуре MessageList добавить:
```kotlin
onReplyBlockTap: (replyTo: ReplyPreview) -> Unit,
```

Импорт:
```kotlin
import com.example.template.core.ui.utils.findReplyBlockContainer
import com.example.template.core.model.ReplyPreview
```

Повторить тот же блок для MediaBubbleView (и других, которые рендерят reply-блок), либо вынести в общую функцию-extension.

- [ ] **Step 2: В ChatDetailScreen — scroll + toast**

Передать callback в MessageList:

```kotlin
val context = LocalContext.current
val lazyListState = rememberLazyListState()  // если уже есть — взять существующий
val coroutineScope = rememberCoroutineScope()

MessageList(
    // ... остальные параметры ...
    onReplyBlockTap = { replyTo ->
        val messages = viewModel.messages.value
        val originalIdx = messages.indexOfFirst { it.id == replyTo.originalId }
        if (originalIdx < 0) {
            Toast.makeText(context, "Сообщение удалено", Toast.LENGTH_SHORT).show()
            return@MessageList
        }
        val original = messages[originalIdx]
        val originalText = when (original) {
            is Message.Text -> original.body
            is Message.Media -> original.caption.orEmpty()
            else -> ""
        }
        coroutineScope.launch {
            // reverseLayout: индекс инвертирован, нужен .asReversed()-индекс
            val reversedIdx = messages.size - 1 - originalIdx
            lazyListState.animateScrollToItem(reversedIdx)
        }
        when (matchesQuote(originalText, replyTo)) {
            MatchResult.Match -> viewModel.requestHighlight(replyTo.originalId)
            MatchResult.QuoteMismatch ->
                Toast.makeText(context, "Цитируемый фрагмент не найден", Toast.LENGTH_SHORT).show()
        }
    },
)
```

Импорты:
```kotlin
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.template.core.data.MatchResult
import com.example.template.core.data.matchesQuote
```

- [ ] **Step 3: Install + manual verify**

Run: `./gradlew :app:installDebug -Pbrand=foxtrot`

Ручной тест:
1. Создать quote (любым из 4 вариантов), отправить.
2. Прокрутить чат подальше от оригинала.
3. Тапнуть на reply-блок в свежем сообщении → должно проскроллить к оригиналу.
4. Удалить оригинал (long-press → ctx-menu → Удалить), затем тап на reply-блок → toast «Сообщение удалено».
5. (Сложно проверить ручкой): отредактировать оригинал на отличающийся текст → тап на reply-блок → toast «Цитируемый фрагмент не найден».

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): tap-on-reply-block в бабле → scroll-to-original + toast"
```

---

## Task 17: Bubble-pulse highlight

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: Считать highlightedMessageId в MessageList**

В сигнатуре добавить:
```kotlin
highlightedMessageId: String?,
```

В ChatDetailScreen передать:
```kotlin
val highlighted by viewModel.highlightedMessageId.collectAsState()
MessageList(
    // ...
    highlightedMessageId = highlighted,
)
```

- [ ] **Step 2: Анимированный overlay на бабле**

Внутри `items(...)` блока MessageList, обернуть каждый бабл-`Box`:

```kotlin
val isHighlighted = message.id == highlightedMessageId
val highlightAlpha by animateFloatAsState(
    targetValue = if (isHighlighted) 0.25f else 0f,
    animationSpec = tween(durationMillis = if (isHighlighted) 200 else 1200),
    label = "bubblePulse",
)

Box {
    // существующий бабл рендер
    if (highlightAlpha > 0.001f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brand.accentColor(isDark).copy(alpha = highlightAlpha)),
        )
    }
}
```

Импорты:
```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
```

- [ ] **Step 3: Install + manual verify**

Run: `./gradlew :app:installDebug -Pbrand=foxtrot`

Ручной тест:
1. Цитата → отправить.
2. Прокрутить.
3. Тап на reply-блок → должен проскроллить + бабл-оригинал пульсирует accent-цветом ~1.4с.

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(quote): bubble-pulse highlight по requestHighlight"
```

---

## Task 18: Визуальный маркер quote в reply-block (иконка + кавычки)

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt`
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

Маркеры:
- **MessagePanel reply-block:** иконка кавычек слева от текста — но `ContextBlock.Reply` из `:components` не принимает иконку. Без правок `:components` — добавим префикс к `previewText` (передаваемому в `ReplyDisplay`).
- **Reply-block внутри бабла (`replyText` параметр `BubblesView.configure`):** тот же приём — префикс « … » передаваемый в `ReplyPreview.text` при отправке.

Удобнее всего: преобразовывать `previewText` при создании `ReplyDisplay` в ChatDetailScreen и при отправке snapshot'а — оба места читают `ReplyContext`.

- [ ] **Step 1: Хелпер форматирования**

В ChatDetailScreen.kt (top-level private fun) добавить:

```kotlin
private fun formatQuoteForDisplay(previewText: String, isQuote: Boolean): String =
    if (isQuote) "«$previewText»" else previewText
```

- [ ] **Step 2: Применить при создании ReplyDisplay**

Найти место, где `ReplyDisplay` строится из `replyContext`. Заменить:

```kotlin
replyContext = replyContextState?.let { ctx ->
    ReplyDisplay(
        authorName = ctx.authorName,
        previewText = formatQuoteForDisplay(ctx.previewText, ctx.quoteStart != null),
    )
},
```

- [ ] **Step 3: Применить при отправке (toSnapshot)**

В `ChatDetailViewModel.kt` в `ReplyContext.toSnapshot()` обновить:

```kotlin
fun toSnapshot() = ReplyPreview(
    originalId = originalId,
    authorName = authorName,
    text = if (quoteStart != null) "«$previewText»" else previewText,
    quoteStart = quoteStart,
    quoteEnd = quoteEnd,
)
```

**Важное соображение:** теперь `ReplyPreview.text` для quote содержит обрамляющие кавычки. Это означает, что в `matchesQuote(originalText, snapshot)` сравнение `actual == snapshot.text` сломается, т.к. `actual = originalText.substring(start, end)` БЕЗ кавычек.

Исправление matchesQuote — обернуть actual в кавычки тоже:

```kotlin
fun matchesQuote(originalText: String, snapshot: ReplyPreview): MatchResult {
    val start = snapshot.quoteStart ?: return MatchResult.Match
    val end = snapshot.quoteEnd ?: return MatchResult.Match
    if (start < 0 || end > originalText.length || start >= end) return MatchResult.QuoteMismatch
    val actualFragment = originalText.substring(start, end)
    val expectedRaw = snapshot.text.removeSurrounding("«", "»")
    return if (actualFragment == expectedRaw) MatchResult.Match else MatchResult.QuoteMismatch
}
```

И обновить `QuoteRangeMatchTest` соответственно — `expected` теперь обернут в кавычки в snapshot, но `actualFragment` без. Поменять fixture в тестах:

```kotlin
@Test
fun `quote matches when substring equal`() {
    val snap = ReplyPreview("m1", "Боб", "«ивет»", quoteStart = 1, quoteEnd = 5)
    assertEquals(MatchResult.Match, matchesQuote("Привет, мир", snap))
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS (включая обновлённые QuoteRangeMatchTest и нетронутые остальные).

- [ ] **Step 5: Install + manual verify**

Run: `./gradlew :app:installDebug -Pbrand=foxtrot`

Ручной тест:
1. Создать quote, отправить.
2. Сообщение-ответ в чате — reply-блок внутри бабла должен показывать `«фрагмент»` с кавычками.
3. Reply-блок в MessagePanel при цитировании — тоже с кавычками.
4. Tap-to-jump — должен работать (matchesQuote учитывает кавычки).

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt \
        core/data/src/main/java/com/example/template/core/data/QuoteRangeMatch.kt \
        core/data/src/test/java/com/example/template/core/data/QuoteRangeMatchTest.kt
git commit -m "feat(quote): «»-маркер для quote в reply-block (панель + бабл)"
```

---

## Task 19: Smoke test — все 4 варианта на устройстве

**Files:** —

- [ ] **Step 1: Финальная сборка**

```
./gradlew :app:installDebug -Pbrand=foxtrot
```

- [ ] **Step 2: QA-чеклист**

| # | Сценарий | Ожидание |
|---|----------|----------|
| 1 | Variant=INPLACE, long-press на Text-бабле | multi-select + handles на слове под пальцем, floating-меню «Цитировать» |
| 2 | Drag handles + «Цитировать» | reply-block в панели с цитатой `«фрагмент»` |
| 3 | Variant=INPLACE, long-press на Media с caption | handles на caption |
| 4 | Variant=INPLACE, long-press на Voice | только multi-select, без handles (нет text-блока) |
| 5 | Variant=MODAL, swipe-влево + тап на reply-block | Dialog с selectable preview, «Отмена» / «Цитировать» |
| 6 | Variant=MODAL, drag selection + «Цитировать» | reply-block обновился с substring |
| 7 | Variant=SHEET, тот же flow | ModalBottomSheet снизу |
| 8 | Variant=FULLSCREEN, тот же flow | полноэкранный Dialog с TopAppBar |
| 9 | Variant=INPLACE, ctx-menu → «Ответить», затем тап на reply-block | early-return — ничего не происходит (variant=INPLACE blocks picker) |
| 10 | Quote → send → tap на reply-блок в новом сообщении | scroll к оригиналу + bubble-pulse |
| 11 | Quote → send → удалить оригинал → tap | toast «Сообщение удалено» |
| 12 | Quote → send → edit оригинал → tap | toast «Цитируемый фрагмент не найден» |
| 13 | Bottom-tabs: вкладка «Звонки» | секция «Quote variant (dev)» сверху с 4 опциями |

- [ ] **Step 3: Если всё ок — без commit'а; иначе фиксы → отдельные коммиты**

---

## Self-Review (выполнено автором плана)

**Spec coverage:**

| Spec section | Task # |
|--------------|--------|
| `ReplyPreview.quoteStart/End` | 1 |
| `QuoteVariant` enum + CompositionLocal + AppContainer | 2 |
| VM `ReplyContext` + `setQuote/clearQuote` | 3 |
| VM `openQuotePicker/dismissQuotePicker` + variant injection | 4 |
| VM `startQuoteInPlace`, `highlightedMessageId` | 5 |
| `wordBoundaryAt` (BreakIterator) | 6 |
| `matchesQuote` + `MatchResult` | 7 |
| Dev-toggle UI в Calls | 8 |
| `MessagePanelHost.onReplyClick` + ChatDetailScreen wiring | 9 |
| `BubbleTextProbe` (findMessageTextView, findReplyBlockContainer) | 10 |
| `QuoteActionModeCallback` | 11 |
| V1 wiring в `MessageList` (capture coords, view-walk, ActionMode) | 12 |
| `QuotePickerContent` (BasicTextField) | 13 |
| Три шкуры picker'а (Modal/Sheet/FullScreen) | 14 |
| `QuotePickerOverlay` + ChatDetailScreen wiring | 15 |
| Tap-on-reply-block → scroll + toast'ы edge cases | 16 |
| Bubble-pulse highlight | 17 |
| «»-маркер для quote в reply-block | 18 |
| Manual QA по всем 4 вариантам | 19 |

Все требования спецификации покрыты задачами.

**Placeholder scan:** Я нашёл и поправил inline-неточности:
- В Task 6 первоначально упоминал тесты в `:core:ui/src/test/` — поправил на `:core:data` с обоснованием.
- В Task 15 Step 2 первоначально предложил wire в MainActivity — но VM создаётся внутри ChatDetailScreen; исправил на wiring внутри ChatDetailScreen.
- В Task 18 заметил, что обрамление в `«»` ломает `matchesQuote` — добавил обновление функции + теста в тот же task.

**Type consistency:** проверил — все типы и сигнатуры в плане согласованы:
- `QuoteVariant` (enum, 4 значения).
- `ReplyContext(originalId, authorName, originalFullText, previewText, quoteStart?, quoteEnd?)` — едино во всех задачах.
- `MatchResult.Match/QuoteMismatch` — двух-state enum.
- `QuoteActionModeCallback(tv, onQuote: (start, end) -> Unit)` — согласовано.
- `wordBoundaryAt(text, offset): IntRange?` — согласовано.
- `findMessageTextView(messageText): TextView?`, `findReplyBlockContainer(replySenderText): View?` — согласовано.

**Scope:** одна имплементация. После выбора финального варианта — отдельная задача (вне scope этого плана): удалить три неиспользованных picker-файла + enum-вариант + dev-toggle секцию + variant-инъекцию в VM, оставив только финальный вариант.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-26-quote-reply-implementation.md`. Два пути выполнения:

**1. Subagent-Driven (recommended)** — диспетчу свежий subagent на каждую задачу, ревью между задачами, быстрая итерация.

**2. Inline Execution** — выполняю задачи в этой же сессии через `superpowers:executing-plans`, batch с чекпойнтами.

Какой вариант?
