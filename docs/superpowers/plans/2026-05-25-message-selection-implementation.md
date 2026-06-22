# Message Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement multi-message selection mode in chat detail screen (triggered by context menu's "Выбрать" item), with checkboxes left of each bubble, a custom header showing the count + "Отмена" button, and an action bar (Удалить · Переслать · Сохранить · Закрепить) replacing the message input panel.

**Architecture:** State lives in `ChatDetailViewModel` as a new `SelectionContext` (mutual-exclusive with reply/edit). UI in `ChatDetailScreen` swaps header and bottom slot based on `selection != null`. `MessageList` wraps each bubble row in `Row { animated Checkbox-cell ; existing Column }`. New repository method `forwardMessagesToSaved(messages)` handles "Сохранить". Forward/Pin are TBD stubs.

**Tech Stack:** Kotlin · Jetpack Compose · `androidx.compose.animation.core.animateDpAsState` · `:components/checkbox/CheckboxView` (AndroidView) · `:components/button/ButtonView` · `:components/headers/HeadersView.HeaderConfig.Custom` · `DSBrand.checkboxColorScheme(isDark)`.

**Spec:** `docs/superpowers/specs/2026-05-25-message-selection-design.md`

---

## File Structure

**Create:**
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/SelectionActionBar.kt`

**Modify:**
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` — add `forwardMessagesToSaved` to interface.
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` — implement `forwardMessagesToSaved` + pure-builder companion functions.
- `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt` — add tests for the new builder functions.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` — add `SelectionContext` + API + mutual exclusion + `SELECT` branch in `onMenuItem` + prune logic.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — observe `selection`, swap header config, swap bottom slot, `BackHandler`, IME dismiss, swap MessageList callbacks.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — new params, `Row{Checkbox; Column}` wrap, `animateDpAsState`, `SwipeToReplyItem(enabled = ...)`.

---

## Task 1: Repository — forwardMessagesToSaved (builder fns + tests)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt`

- [ ] **Step 1: Add failing tests in SendMessageTest.kt**

Append the following tests to `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt` (inside the `SendMessageTest` class, just before the closing brace):

```kotlin
@Test
fun `buildForwardedTextMessage carries body and metadata`() {
    val original = com.example.template.core.model.Message.Text(
        id = "m-source",
        chatId = "chat-original",
        senderId = "u-bob",
        timestamp = 1_000L,
        isMine = false,
        status = MessageStatus.READ,
        body = "привет",
    )
    val forwarded = MockRepositoryImpl.buildForwardedTextMessage(
        original = original,
        targetChatId = "chat-14",
        senderId = "u-me",
        timestamp = 9_999L,
    )
    assertEquals("fwd-9999", forwarded.id)
    assertEquals("chat-14", forwarded.chatId)
    assertEquals("u-me", forwarded.senderId)
    assertEquals(9_999L, forwarded.timestamp)
    assertEquals(true, forwarded.isMine)
    assertEquals(MessageStatus.READ, forwarded.status)
    assertEquals("привет", forwarded.body)
    assertEquals(null, forwarded.replyTo)
    assertEquals(emptyList<com.example.template.core.model.Reaction>(), forwarded.reactions)
}

@Test
fun `buildForwardedMediaMessage preserves attachments and caption`() {
    val att = com.example.template.core.model.MediaAttachment(
        placeholderColor = "#abc",
        type = com.example.template.core.model.AttachmentType.Photo,
    )
    val original = com.example.template.core.model.Message.Media(
        id = "m-src",
        chatId = "chat-original",
        senderId = "u-bob",
        timestamp = 1_000L,
        isMine = false,
        status = MessageStatus.READ,
        attachments = listOf(att),
        caption = "подпись",
    )
    val forwarded = MockRepositoryImpl.buildForwardedMediaMessage(
        original = original,
        targetChatId = "chat-14",
        senderId = "u-me",
        timestamp = 9_999L,
    )
    assertEquals("fwd-9999", forwarded.id)
    assertEquals("chat-14", forwarded.chatId)
    assertEquals("u-me", forwarded.senderId)
    assertEquals(true, forwarded.isMine)
    assertEquals(MessageStatus.READ, forwarded.status)
    assertEquals(listOf(att), forwarded.attachments)
    assertEquals("подпись", forwarded.caption)
    assertEquals(null, forwarded.replyTo)
}

@Test
fun `buildForwardedBatch preserves order and has monotonic ids`() {
    val a = com.example.template.core.model.Message.Text(
        id = "m-a", chatId = "src", senderId = "u-bob",
        timestamp = 1L, isMine = false, body = "A",
    )
    val b = com.example.template.core.model.Message.Text(
        id = "m-b", chatId = "src", senderId = "u-bob",
        timestamp = 2L, isMine = false, body = "B",
    )
    val out = MockRepositoryImpl.buildForwardedBatch(
        messages = listOf(a, b),
        targetChatId = "chat-14",
        senderId = "u-me",
        baseTimestamp = 100L,
    )
    assertEquals(2, out.size)
    assertEquals(listOf("fwd-100", "fwd-101"), out.map { it.id })
    assertEquals("A", (out[0] as com.example.template.core.model.Message.Text).body)
    assertEquals("B", (out[1] as com.example.template.core.model.Message.Text).body)
}

@Test
fun `buildForwardedBatch skips Voice Link CallMeet System`() {
    val text = com.example.template.core.model.Message.Text(
        id = "m1", chatId = "src", senderId = "u-bob",
        timestamp = 1L, isMine = false, body = "t",
    )
    val voice = com.example.template.core.model.Message.Voice(
        id = "m2", chatId = "src", senderId = "u-bob",
        timestamp = 2L, isMine = false, durationMs = 1000, waveform = emptyList(),
    )
    val link = com.example.template.core.model.Message.Link(
        id = "m3", chatId = "src", senderId = "u-bob",
        timestamp = 3L, isMine = false, url = "https://x", title = "t",
    )
    val callmeet = com.example.template.core.model.Message.CallMeet(
        id = "m4", chatId = "src", senderId = "u-bob",
        timestamp = 4L, isMine = false,
        callStatus = com.example.template.core.model.CallStatus.Answered,
        isVideo = false, isGroupCall = false,
    )
    val media = com.example.template.core.model.Message.Media(
        id = "m5", chatId = "src", senderId = "u-bob",
        timestamp = 5L, isMine = false,
        attachments = emptyList(), caption = "x",
    )
    val out = MockRepositoryImpl.buildForwardedBatch(
        messages = listOf(text, voice, link, callmeet, media),
        targetChatId = "chat-14",
        senderId = "u-me",
        baseTimestamp = 100L,
    )
    assertEquals(2, out.size)
    assertEquals(listOf("fwd-100", "fwd-101"), out.map { it.id })
    assertEquals(true, out[0] is com.example.template.core.model.Message.Text)
    assertEquals(true, out[1] is com.example.template.core.model.Message.Media)
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run from the repo root (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:testDebugUnitTest --console=plain
```
Expected: compile error (unresolved references `buildForwardedTextMessage`, `buildForwardedMediaMessage`, `buildForwardedBatch`).

- [ ] **Step 3: Add interface method to MessengerRepository**

In `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`, append inside the `interface MessengerRepository` body (after `editMediaMessage`, before `fun getUser`):

```kotlin
    suspend fun forwardMessagesToSaved(messages: List<Message>)
```

- [ ] **Step 4: Add companion builder functions in MockRepositoryImpl**

In `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`, inside the `companion object` block (after `mediaPreviewText` near the bottom), add:

```kotlin
        fun buildForwardedTextMessage(
            original: Message.Text,
            targetChatId: String,
            senderId: String,
            timestamp: Long,
        ): Message.Text = Message.Text(
            id = "fwd-$timestamp",
            chatId = targetChatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.READ,
            body = original.body,
        )

        fun buildForwardedMediaMessage(
            original: Message.Media,
            targetChatId: String,
            senderId: String,
            timestamp: Long,
        ): Message.Media = Message.Media(
            id = "fwd-$timestamp",
            chatId = targetChatId,
            senderId = senderId,
            timestamp = timestamp,
            isMine = true,
            status = MessageStatus.READ,
            attachments = original.attachments,
            caption = original.caption,
        )

        /**
         * Pure builder: переслать список сообщений в targetChatId. Voice / Link / CallMeet /
         * System пропускаются — для прототипа в Saved-чат улетают только Text/Media. id строятся
         * монотонно от `baseTimestamp` (+i по индексу принимаемого batch'а) — защита от коллизий
         * при быстром loop'е, когда System.currentTimeMillis() даёт одинаковое значение.
         */
        fun buildForwardedBatch(
            messages: List<Message>,
            targetChatId: String,
            senderId: String,
            baseTimestamp: Long,
        ): List<Message> {
            val out = mutableListOf<Message>()
            var nextTs = baseTimestamp
            for (m in messages) {
                when (m) {
                    is Message.Text -> {
                        out += buildForwardedTextMessage(m, targetChatId, senderId, nextTs)
                        nextTs++
                    }
                    is Message.Media -> {
                        out += buildForwardedMediaMessage(m, targetChatId, senderId, nextTs)
                        nextTs++
                    }
                    is Message.Voice, is Message.Link, is Message.CallMeet, is Message.System -> Unit
                }
            }
            return out
        }
```

- [ ] **Step 5: Run tests — they should pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, all tests pass (existing + 4 new).

- [ ] **Step 6: Implement forwardMessagesToSaved in the class body**

In `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`, after the `editMediaMessage` override (~line 266 region) and before `private fun updateReactions(...)`, add:

```kotlin
    override suspend fun forwardMessagesToSaved(messages: List<Message>) {
        val saved = _chats.value.firstOrNull {
            it.avatar.type == com.example.template.core.model.AvatarType.Self
        } ?: return
        // Прогреваем cache Self-чата так же, как loadMessages (чтобы append дальше попал
        // в существующий StateFlow, и его подписчики получили апдейт).
        val cache = messageCache.getOrPut(saved.id) {
            val initial = try {
                loadList<Message>("mock/messages/${saved.id}.json")
            } catch (_: java.io.FileNotFoundException) {
                emptyList()
            }
            MutableStateFlow(appendIncomingPreviewIfNeeded(saved.id, initial))
        }
        val mine = _currentUser.value
        val newMessages = buildForwardedBatch(
            messages = messages.sortedBy { it.timestamp },
            targetChatId = saved.id,
            senderId = mine.id,
            baseTimestamp = System.currentTimeMillis(),
        )
        if (newMessages.isEmpty()) return
        cache.value = cache.value + newMessages
        // Обновляем preview Self-чата по последнему добавленному сообщению.
        when (val last = newMessages.last()) {
            is Message.Text -> updateChatLastMessage(saved.id, last)
            is Message.Media -> updateChatLastMessage(saved.id, last)
            else -> Unit  // batch собран только из Text/Media по построению.
        }
    }
```

- [ ] **Step 7: Build core:data**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:data:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt
git commit -m "feat(core-data): add forwardMessagesToSaved repository method"
```

---

## Task 2: ViewModel — SelectionContext + API + mutual exclusion

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Add SelectionContext data class and StateFlow**

In `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`, find the `EditContext` block (the second `data class` near the bottom) and insert AFTER its `dismissEdit` / `saveEdit` functions, BEFORE the `private fun resolveAuthorName(...)` block, the following:

```kotlin
    /**
     * Mutual-exclusive с reply/edit третий контекст. `null` = режим выбора выключен.
     * `selectedIds` всегда непустое: `toggleSelection` auto-exit'ит в `null` когда множество
     * опустевает после снятия чекбокса. Начальный selection всегда содержит инициирующее
     * сообщение из меню (см. `startSelection`).
     */
    data class SelectionContext(val selectedIds: Set<String>)

    private val _selection = MutableStateFlow<SelectionContext?>(null)
    val selection: StateFlow<SelectionContext?> = _selection.asStateFlow()

    fun startSelection(initialMessageId: String) {
        _replyContext.value = null
        _editContext.value = null
        _selection.value = SelectionContext(setOf(initialMessageId))
    }

    fun toggleSelection(messageId: String) {
        val curr = _selection.value ?: return
        val newSet =
            if (messageId in curr.selectedIds) curr.selectedIds - messageId
            else curr.selectedIds + messageId
        _selection.value = if (newSet.isEmpty()) null else curr.copy(selectedIds = newSet)
    }

    fun exitSelection() {
        _selection.value = null
    }

    fun deleteSelected() {
        val ids = _selection.value?.selectedIds ?: return
        val msgs = _messages.value.filter { it.id in ids }
        if (msgs.any { !it.isMine }) return  // safety: UI also disables button
        viewModelScope.launch {
            ids.forEach { repository.deleteMessage(chatId, it) }
            _selection.value = null
        }
    }

    fun saveSelected() {
        val ids = _selection.value?.selectedIds ?: return
        val msgs = _messages.value
            .filter { it.id in ids }
            .sortedBy { it.timestamp }
        viewModelScope.launch {
            repository.forwardMessagesToSaved(msgs)
            _selection.value = null
        }
    }

    fun forwardSelectedTbd(showToast: (String) -> Unit) {
        showToast("Переслать — TBD")
        _selection.value = null
    }

    fun pinSelectedTbd(showToast: (String) -> Unit) {
        showToast("Закрепить — TBD")
        _selection.value = null
    }
```

- [ ] **Step 2: Wire mutual exclusion in startReply and startEdit**

In the same file, find `fun startReply(messageId: String)` (~line 101). Change the body so that selection is cleared on reply start:

Replace this current block:
```kotlin
    fun startReply(messageId: String) {
        val original = _messages.value.firstOrNull { it.id == messageId } ?: return
        _editContext.value = null   // взаимоисключение reply ↔ edit
        _replyContext.value = ReplyContext(
            originalId = original.id,
            authorName = resolveAuthorName(original),
            previewText = resolveMessagePreview(original),
        )
    }
```
with:
```kotlin
    fun startReply(messageId: String) {
        val original = _messages.value.firstOrNull { it.id == messageId } ?: return
        _editContext.value = null      // взаимоисключение reply ↔ edit
        _selection.value = null        // взаимоисключение reply ↔ selection
        _replyContext.value = ReplyContext(
            originalId = original.id,
            authorName = resolveAuthorName(original),
            previewText = resolveMessagePreview(original),
        )
    }
```

Find `fun startEdit(messageId: String)` (~line 157). Replace the line `_replyContext.value = null   // взаимоисключение reply ↔ edit` with two lines:
```kotlin
        _replyContext.value = null   // взаимоисключение reply ↔ edit
        _selection.value = null      // взаимоисключение selection ↔ edit
```

- [ ] **Step 3: Add prune logic in init**

Find the `init { ... }` block at the top of the class (~line 33-37):

```kotlin
    init {
        viewModelScope.launch {
            repository.loadMessages(chatId).collect { _messages.value = it }
        }
    }
```

Replace with:
```kotlin
    init {
        viewModelScope.launch {
            repository.loadMessages(chatId).collect { list ->
                _messages.value = list
                // Если в selection остались id, которых уже нет в messages (например, после
                // delete извне), выкидываем их. Пустое множество = выход из режима выбора.
                _selection.value?.let { sel ->
                    val aliveIds = list.mapTo(HashSet(list.size)) { it.id }
                    val pruned = sel.selectedIds.intersect(aliveIds)
                    _selection.value = if (pruned.isEmpty()) null
                                       else if (pruned.size == sel.selectedIds.size) sel
                                       else sel.copy(selectedIds = pruned)
                }
            }
        }
    }
```

- [ ] **Step 4: Add SELECT branch in onMenuItem**

Find `fun onMenuItem(item: ContextMenuView.Item, context: Context)` (~line 84). Add a new branch in the `when (item)` block, right before the `else -> Unit` line:

```kotlin
            ContextMenuView.Item.SELECT -> startSelection(st.messageId)
```

The full updated `when` block should read:
```kotlin
        when (item) {
            ContextMenuView.Item.COPY -> if (st.copyText.isNotEmpty()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("message", st.copyText))
            }
            ContextMenuView.Item.DELETE -> if (st.type == ContextMenuView.Type.MY) {
                viewModelScope.launch { repository.deleteMessage(chatId, st.messageId) }
            }
            ContextMenuView.Item.REPLY -> startReply(st.messageId)
            ContextMenuView.Item.EDIT -> startEdit(st.messageId)
            ContextMenuView.Item.SELECT -> startSelection(st.messageId)
            else -> Unit
        }
```

- [ ] **Step 5: Build feature:chatdetail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature:chatdetail:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(chatdetail): add SelectionContext to ChatDetailViewModel"
```

---

## Task 3: SelectionActionBar Composable

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/SelectionActionBar.kt`

- [ ] **Step 1: Create the SelectionActionBar.kt file**

Create `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/SelectionActionBar.kt` with the following content:

```kotlin
package com.example.template.feature.chatdetail

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import com.example.components.button.ButtonView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

/**
 * Action bar внизу экрана в режиме выбора сообщений. 4 ячейки во весь экран (weight 1f каждая):
 * Удалить · Переслать · Сохранить · Закрепить. Удалить дизейблится когда в выборе есть SOMEONE.
 * Переслать и Закрепить — стабы (Toast TBD), Сохранить — пересылает в Self-чат.
 *
 * Высота ~64dp, совпадает с MessagePanel в дефолтном виде. Фон — sheet-color бренда.
 */
@Composable
fun SelectionActionBar(
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onSave: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val sheetColor = remember(brand, isDark) { Color(brand.sheetBackground(isDark)) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sheetColor)
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionBarCell(
            iconName = "delete",
            label = "Удалить",
            enabled = deleteEnabled,
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth(0.25f)
                .height(64.dp),
        )
        ActionBarCell(
            iconName = "forward-stroke",
            label = "Переслать",
            enabled = true,
            onClick = onForward,
            modifier = Modifier
                .fillMaxWidth(0.333f)
                .height(64.dp),
        )
        ActionBarCell(
            iconName = "saved",
            label = "Сохранить",
            enabled = true,
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(64.dp),
        )
        ActionBarCell(
            iconName = "pin-stroke",
            label = "Закрепить",
            enabled = true,
            onClick = onPin,
            modifier = Modifier
                .fillMaxWidth(1f)
                .height(64.dp),
        )
    }
}

/**
 * Одна ячейка ActionBar: icon-only ButtonView из :components сверху + лейбл текста снизу.
 * Кликабельна вся ячейка (а не только сам ButtonView), чтобы pointer был большим.
 *
 * ButtonView рисуется через AndroidView; click слушатель ставится через `onClick` на View.
 * Внешний Column тоже clickable, но без ripple — ButtonView сам обеспечивает визуальный feedback.
 */
@Composable
private fun ActionBarCell(
    iconName: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(28.dp)) {
            AndroidView(
                factory = { ctx ->
                    ButtonView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        this.onClick = { onClick() }
                    }
                },
                update = { btn ->
                    btn.configure(
                        iconName = iconName,
                        size = ButtonView.Size.XS,
                        filled = false,
                        enabled = enabled,
                    )
                    btn.onClick = { onClick() }
                },
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) Color.Black else Color.Gray,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
```

- [ ] **Step 2: Build feature:chatdetail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature:chatdetail:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/SelectionActionBar.kt
git commit -m "feat(chatdetail): add SelectionActionBar composable"
```

---

## Task 4: MessageList — selection params + Checkbox cell

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: Add imports**

In `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`, the imports section (lines 3-58) needs three additions. Add the following imports (alphabetically wherever they fit):

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import com.example.components.checkbox.CheckboxView
```

- [ ] **Step 2: Add selection params to MessageList signature**

In the same file, find the `fun MessageList(...)` signature (around line 178). Add three new parameters right after the existing `onSwipeReply` parameter (which is the last one before the closing `)`):

Change from:
```kotlin
    onSwipeReply: (messageId: String) -> Unit = {},
) {
```
to:
```kotlin
    onSwipeReply: (messageId: String) -> Unit = {},
    selectionActive: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (messageId: String) -> Unit = {},
) {
```

- [ ] **Step 3: Add brand-aware checkbox color scheme remember**

In the same `MessageList` body, find the existing `val avatarSchemesByIndex: Map<Int, AvatarColorScheme> = remember(brand, isDark) { ... }` block (around line 207-216). Right BEFORE it, add:

```kotlin
    val checkboxScheme = remember(brand, isDark) { brand.checkboxColorScheme(isDark) }
```

- [ ] **Step 4: Wrap bubble row in Row{Checkbox-cell ; Column}**

Find the `is RowItem.Bubble -> { ... }` branch inside `items(reversed, key = ...)` (around line 267). Currently the body starts with `val msg = row.message` and ends with reactions+SwipeToReplyItem closures. We wrap the existing `SwipeToReplyItem { Column { ... } }` block inside a `Row { Box(checkbox); SwipeToReply Column }`.

Replace this current structure (locate by the start of `SwipeToReplyItem(`, around line 305):

```kotlin
                    SwipeToReplyItem(
                        messageId = msg.id,
                        enabled = msg is Message.Text || msg is Message.Media,
                        onTriggerReply = onSwipeReply,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPad)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onBubbleTap(msg.id) },
                    ) {
```

with:

```kotlin
                    val checkboxCellWidth by animateDpAsState(
                        targetValue = if (selectionActive) 48.dp else 0.dp,
                        animationSpec = tween(200),
                        label = "checkboxCell-${msg.id}",
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = topPad)) {
                        Box(
                            modifier = Modifier
                                .width(checkboxCellWidth)
                                .clickable(
                                    interactionSource = remember(msg.id) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onToggleSelection(msg.id) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checkboxCellWidth > 0.dp) {
                                AndroidView(
                                    factory = { ctx ->
                                        CheckboxView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                            )
                                        }
                                    },
                                    update = { v ->
                                        v.configure(
                                            shape = CheckboxView.Shape.CIRCLE,
                                            isChecked = msg.id in selectedIds,
                                            showText = false,
                                            colorScheme = checkboxScheme,
                                        )
                                    },
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SwipeToReplyItem(
                                messageId = msg.id,
                                enabled = !selectionActive && (msg is Message.Text || msg is Message.Media),
                                onTriggerReply = onSwipeReply,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onBubbleTap(msg.id) },
                            ) {
```

This pushes the existing bubble + reactions content into the new inner Column. We must also CLOSE the two new wrappers we opened. Find the END of the current `SwipeToReplyItem { ... }` block — it currently ends with two closing braces (`} // closes SwipeToReplyItem content` and likely a final `}` at line ~647). Add an additional closing brace for the new outer `Column(weight(1f))`, then add a closing brace for the new outer `Row`.

The final closing braces for this `is RowItem.Bubble` branch should read (replace the current ending):

From (current ending — around line 645-647):
```kotlin
                    } // closes Column wrapper
                    } // closes SwipeToReplyItem content
                }
```

to:
```kotlin
                    } // closes Column wrapper
                    } // closes SwipeToReplyItem content
                        } // closes outer Column(weight 1f) (selection mode)
                    } // closes outer Row (checkbox + content) (selection mode)
                }
```

(Note: indent depth may need adjustment — the goal is the new `Row` and inner `Column(weight=1f)` are correctly opened and closed.)

- [ ] **Step 5: Build core:ui**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :core:ui:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. If brace-counting got confused, fix the indent/closes and try again until it compiles.

- [ ] **Step 6: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(core-ui): add selection mode params (checkbox cell, swipe disable) to MessageList"
```

---

## Task 5: ChatDetailScreen — wire selection mode

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Add imports**

In `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`, add the following imports:

```kotlin
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
```

(`LaunchedEffect` already imported — keep one. `LocalContext` is likely already imported via some other import; check before duplicating.)

- [ ] **Step 2: Observe selection state**

Find the line `val editCtx by viewModel.editContext.collectAsState()` (around line 54). Add right after it:

```kotlin
    val selection by viewModel.selection.collectAsState()
    val selectionActive = selection != null
    val context = LocalContext.current
```

- [ ] **Step 3: Dismiss IME when entering selection**

After the existing `LaunchedEffect(panel, editCtx?.originalId) { ... }` block (around line 83-92), add:

```kotlin
    LaunchedEffect(selectionActive) {
        if (selectionActive) {
            panelRef.value?.dismissKeyboard()
        }
    }
```

- [ ] **Step 4: BackHandler for selection mode**

In the same composable, after `LaunchedEffect(selectionActive)`, add:

```kotlin
    BackHandler(enabled = selectionActive) {
        viewModel.exitSelection()
    }
```

- [ ] **Step 5: Replace HeaderHost with conditional config**

Find the existing `HeaderHost(config = HeadersView.HeaderConfig.Chat(...))` block (around line 165-185). Wrap it in an `if`/`else`:

Replace:
```kotlin
        HeaderHost(
            config = HeadersView.HeaderConfig.Chat(
                name = chat.title,
                subtitle = subtitleText,
                subtitleStyle = subtitleStyle,
                image = headerAvatar.image,
                avatarType = headerAvatar.type,
                avatarText = headerAvatar.text,
                avatarColorScheme = avatarScheme,
                showPinButton = chat.hasPinnedMessages,
                showBoardButton = true,
                showSearchButton = true,
                showCallButton = chat.type != ChatType.Channel,
                callButtonIconName = if (chat.type == ChatType.Group) "video-call" else "call",
                onBack = onBack,
                onPinClick = { },
                onBoardClick = { },
                onSearchClick = { },
                onCallClick = { },
            ),
        )
```

with:
```kotlin
        if (selectionActive) {
            HeaderHost(
                config = HeadersView.HeaderConfig.Custom(
                    title = "Выбрано ${selection!!.selectedIds.size}",
                    titleAlignment = HeadersView.HeaderConfig.Custom.TitleAlignment.LEFT,
                    leftButton = HeadersView.HeaderConfig.Custom.LeftButton.HIDE,
                    rightButton = HeadersView.HeaderConfig.Custom.RightButton.TEXT,
                    rightButtonType = HeadersView.HeaderConfig.Custom.ButtonType.PRIMARY,
                    rightButtonLabel = "Отмена",
                    onRightClick = { viewModel.exitSelection() },
                ),
            )
        } else {
            HeaderHost(
                config = HeadersView.HeaderConfig.Chat(
                    name = chat.title,
                    subtitle = subtitleText,
                    subtitleStyle = subtitleStyle,
                    image = headerAvatar.image,
                    avatarType = headerAvatar.type,
                    avatarText = headerAvatar.text,
                    avatarColorScheme = avatarScheme,
                    showPinButton = chat.hasPinnedMessages,
                    showBoardButton = true,
                    showSearchButton = true,
                    showCallButton = chat.type != ChatType.Channel,
                    callButtonIconName = if (chat.type == ChatType.Group) "video-call" else "call",
                    onBack = onBack,
                    onPinClick = { },
                    onBoardClick = { },
                    onSearchClick = { },
                    onCallClick = { },
                ),
            )
        }
```

- [ ] **Step 6: Pass selection params and swap callbacks in MessageList call**

Find the `MessageList(...)` call inside the `Box { BackgroundPatternView; MessageList }` block (around line 199-219). Replace the entire call with:

```kotlin
            MessageList(
                messages = messages,
                modifier = Modifier.fillMaxSize(),
                personaByUserId = viewModel::personaForUser,
                isP2P = chat.type == ChatType.P2P,
                onBubbleTap = { id ->
                    if (selectionActive) {
                        viewModel.toggleSelection(id)
                    } else {
                        panelRef.value?.dismissKeyboard()
                        viewModel.openContextMenu(id)
                    }
                },
                onReactionTap = { id, emoji ->
                    if (selectionActive) viewModel.toggleSelection(id)
                    else viewModel.toggleReactionDirect(id, emoji)
                },
                onAddReactionTap = { id ->
                    if (selectionActive) {
                        viewModel.toggleSelection(id)
                    } else {
                        panelRef.value?.dismissKeyboard()
                        viewModel.openContextMenu(id)
                    }
                },
                onSwipeReply = viewModel::startReply,
                selectionActive = selectionActive,
                selectedIds = selection?.selectedIds ?: emptySet(),
                onToggleSelection = viewModel::toggleSelection,
            )
```

- [ ] **Step 7: Replace MessagePanelHost with conditional bottom slot**

Find the existing `MessagePanelHost(...)` call at the bottom (around line 221-231). Wrap it in `if`/`else`:

Replace:
```kotlin
        MessagePanelHost(
            onSendText = viewModel::sendText,
            onSendMedia = viewModel::sendMedia,
            replyContext = replyCtx?.let { ReplyDisplay(it.authorName, it.previewText) },
            onReplyClose = viewModel::dismissReplyContext,
            editContext = editDisplay,
            onEditClose = viewModel::dismissEdit,
            onSaveEdit = viewModel::saveEdit,
            modifier = Modifier.imePadding(),
            onPanelReady = { panelRef.value = it },
        )
```

with:
```kotlin
        if (selectionActive) {
            val sel = selection!!
            val anySomeone = remember(sel.selectedIds, messages) {
                val byId = messages.associateBy { it.id }
                sel.selectedIds.any { id -> byId[id]?.isMine == false }
            }
            SelectionActionBar(
                deleteEnabled = !anySomeone,
                onDelete = { viewModel.deleteSelected() },
                onForward = {
                    viewModel.forwardSelectedTbd { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = { viewModel.saveSelected() },
                onPin = {
                    viewModel.pinSelectedTbd { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.imePadding(),
            )
        } else {
            MessagePanelHost(
                onSendText = viewModel::sendText,
                onSendMedia = viewModel::sendMedia,
                replyContext = replyCtx?.let { ReplyDisplay(it.authorName, it.previewText) },
                onReplyClose = viewModel::dismissReplyContext,
                editContext = editDisplay,
                onEditClose = viewModel::dismissEdit,
                onSaveEdit = viewModel::saveEdit,
                modifier = Modifier.imePadding(),
                onPanelReady = { panelRef.value = it },
            )
        }
```

- [ ] **Step 8: Build full app**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(chatdetail): wire selection mode UI (header, action bar, MessageList callbacks)"
```

---

## Task 6: Install + manual smoke test

**Files:** none (build/install + verification).

- [ ] **Step 1: Install to connected device**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug --console=plain
```
Expected: `BUILD SUCCESSFUL` and `Installed on N device(s)`.

- [ ] **Step 2: Manual smoke test on device**

In any chat, run through these scenarios. Report results back:

1. **Enter selection mode via context menu**
   - Open a chat with several messages (mix of MY and SOMEONE).
   - Long-press a SOMEONE-bubble → context menu opens → tap «Выбрать».
   - Expected: menu closes, header swaps to «Выбрано 1» + «Отмена», bottom panel swaps to ActionBar with 4 buttons (Удалить · Переслать · Сохранить · Закрепить). The SOMEONE-bubble has a checked circle checkbox left of it. Other rows have unchecked checkboxes. The checkbox-cell animation should be smooth (~200ms).
   - **«Удалить» should be disabled** (greyed out icon + grey label) because the initially-selected message is SOMEONE.

2. **Toggle by tapping rows**
   - Tap a MY-bubble: checkbox toggles on. Header counter changes to «Выбрано 2». Удалить still disabled (SOMEONE is in selection).
   - Tap the SOMEONE-bubble (the initial one): its checkbox toggles off. Counter «Выбрано 1». **Удалить becomes enabled** now (only MY remaining).
   - Tap the same MY-bubble again to deselect: counter would be 0 → **selection mode exits automatically**, header reverts to chat header, ActionBar replaced by MessagePanel.

3. **«Отмена» button exits**
   - Enter selection mode again (any message). Tap «Отмена» in header.
   - Expected: selection mode exits, normal chat UI restored.

4. **System back exits selection (not the chat)**
   - Enter selection mode. Press system back.
   - Expected: exits selection mode, chat detail stays open. Press back again — leaves chat.

5. **Системные сообщения не имеют чекбоксов**
   - Find a chat with date separators or Joined-system rows. Enter selection mode.
   - Expected: date/Joined rows show NO checkbox; tapping them does nothing.

6. **«Удалить» работает только для MY**
   - Select 2 MY-messages → tap Удалить → both disappear from list. Selection exits. Chat preview in chat list updates if last message changed.

7. **«Сохранить» — пересылка в Saved**
   - Select 1 MY-text + 1 MY-media → tap Сохранить → selection exits.
   - Open «Сохранённые» (chat-14) → at the bottom there should be 2 new messages copying the originals; both as MY, status READ.
   - Voice / Link / CallMeet in selection — should be silently skipped (saved chat не получает их копии).

8. **Forward и Pin — TBD toast**
   - Select any message → tap Переслать → toast «Переслать — TBD» появляется, selection exits.
   - Select again → tap Закрепить → toast «Закрепить — TBD», selection exits.

9. **Mutual exclusion с reply/edit**
   - Enter selection mode. Long-press a MY-bubble in normal mode (you'd need to exit first). The flow is: in selection mode, long-press should NOT open context menu (tap-toggle wins). To test mutual exclusion: exit selection, swipe-reply on a message → reply context shows in MessagePanel. Long-press a different message → Выбрать → expected: reply context disappears, selection starts.

10. **IME уходит при входе в selection**
    - Open chat, tap on input — клавиатура поднимается. Long-press a message → Выбрать.
    - Expected: клавиатура мгновенно скрывается, ActionBar занимает её место.

11. **Swipe-to-reply отключён в selection**
    - В режиме выбора попробуй свайпнуть бабл влево.
    - Expected: ничего не происходит (свайп не активирует reply).

- [ ] **Step 3: Final verification — no untracked code changes left**

```powershell
git status
```
Expected: только known untracked artifacts (`.claude/`, `build_output.*`, `compile_out.txt`), без modified в `feature/`, `core/`, `app/`.

---

## Summary of commits expected after plan completion

```
feat(core-data): add forwardMessagesToSaved repository method
feat(chatdetail): add SelectionContext to ChatDetailViewModel
feat(chatdetail): add SelectionActionBar composable
feat(core-ui): add selection mode params (checkbox cell, swipe disable) to MessageList
feat(chatdetail): wire selection mode UI (header, action bar, MessageList callbacks)
```

Plus the existing pre-plan commits (`fix(core-data): ...` from session start, `docs(specs): add message-selection design`).
