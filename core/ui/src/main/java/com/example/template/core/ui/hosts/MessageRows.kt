package com.example.template.core.ui.hosts

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.template.core.model.Gender
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.SystemKind

internal sealed class RowItem {
    abstract val key: String
    abstract val timestamp: Long

    data class Bubble(val message: Message) : RowItem() {
        override val key: String get() = message.id
        override val timestamp: Long get() = message.timestamp
    }

    data class SystemRow(
        val text: String,
        override val timestamp: Long,
        override val key: String,
        val isDate: Boolean,
        /** id оригинального Message.System — null для date-разделителей. Используется
         *  для highlight'а: тап по reply-block на удалённое сообщение скроллит и
         *  подсвечивает именно этот placeholder. */
        val messageId: String? = null,
    ) : RowItem()
}

internal data class FlattenedRows(
    val rows: List<RowItem>,
    val dateForRow: Map<String, String>,
    val dateRowKeys: Set<String>,
)

internal enum class RowGroupPosition { SINGLE, FIRST, MIDDLE, LAST }

internal const val ROW_GROUP_TIME_THRESHOLD_MS = 5 * 60 * 1000L

internal fun rowSameGroup(a: Message, b: Message): Boolean =
    a.senderId == b.senderId &&
        a.isMine == b.isMine &&
        kotlin.math.abs(b.timestamp - a.timestamp) <= ROW_GROUP_TIME_THRESHOLD_MS

internal fun computeGroupPositions(rows: List<RowItem>): Map<String, RowGroupPosition> {
    val result = HashMap<String, RowGroupPosition>()
    for (i in rows.indices) {
        val cur = rows[i] as? RowItem.Bubble ?: continue
        var prevMessage: Message? = null
        for (j in i - 1 downTo 0) {
            when (val r = rows[j]) {
                is RowItem.SystemRow -> { prevMessage = null; break }
                is RowItem.Bubble -> { prevMessage = r.message; break }
            }
        }
        var nextMessage: Message? = null
        for (j in i + 1 until rows.size) {
            when (val r = rows[j]) {
                is RowItem.SystemRow -> { nextMessage = null; break }
                is RowItem.Bubble -> { nextMessage = r.message; break }
            }
        }
        val joinsPrev = prevMessage != null && rowSameGroup(prevMessage, cur.message)
        val joinsNext = nextMessage != null && rowSameGroup(cur.message, nextMessage)
        result[cur.message.id] = when {
            !joinsPrev && !joinsNext -> RowGroupPosition.SINGLE
            !joinsPrev && joinsNext -> RowGroupPosition.FIRST
            joinsPrev && joinsNext -> RowGroupPosition.MIDDLE
            else -> RowGroupPosition.LAST
        }
    }
    return result
}

internal fun flattenToRows(
    messages: List<Message>,
    now: Long,
    personaByUserId: (String) -> Persona?,
    formatDate: (Long) -> String,
): FlattenedRows {
    if (messages.isEmpty()) return FlattenedRows(emptyList(), emptyMap(), emptySet())
    val rows = mutableListOf<RowItem>()
    val dateForRow = HashMap<String, String>()
    val dateRowKeys = mutableSetOf<String>()
    var prevDayKey: Int? = null
    var currentDateLabel: String? = null
    val cal = java.util.Calendar.getInstance()
    for (msg in messages) {
        cal.timeInMillis = msg.timestamp
        val dayKey = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        if (dayKey != prevDayKey) {
            currentDateLabel = formatDate(msg.timestamp)
            val dateRow = RowItem.SystemRow(
                text = currentDateLabel,
                timestamp = msg.timestamp,
                key = "date-$dayKey",
                isDate = true,
            )
            rows.add(dateRow)
            dateForRow[dateRow.key] = currentDateLabel
            dateRowKeys.add(dateRow.key)
            prevDayKey = dayKey
        }
        val row: RowItem = when (msg) {
            is Message.System -> RowItem.SystemRow(
                text = renderSystemText(msg, personaByUserId),
                timestamp = msg.timestamp,
                key = "sys-${msg.id}",
                isDate = false,
                messageId = msg.id,
            )
            else -> RowItem.Bubble(msg)
        }
        rows.add(row)
        dateForRow[row.key] = currentDateLabel ?: ""
    }
    return FlattenedRows(rows, dateForRow, dateRowKeys)
}

internal fun renderSystemText(
    msg: Message.System,
    personaByUserId: (String) -> Persona?,
): String {
    val p = personaByUserId(msg.senderId)
    val name = p?.let { "${it.firstName} ${it.lastName}" } ?: "Кто-то"
    return when (msg.kind) {
        SystemKind.Joined -> {
            val verb = if (p?.gender == Gender.Female) "присоединилась" else "присоединился"
            "$name $verb к группе"
        }
        SystemKind.MessageDeleted -> {
            if (msg.isMine) "Вы удалили сообщение"
            else "Автор удалил(а) сообщение"
        }
    }
}

internal fun computeTopPadding(
    rows: List<RowItem>,
    groupPositions: Map<String, RowGroupPosition>,
): Map<String, Dp> {
    val map = HashMap<String, Dp>(rows.size)
    for (i in rows.indices) {
        val cur = rows[i]
        val prev = rows.getOrNull(i - 1)
        val pad: Dp = when {
            prev == null -> 0.dp
            cur is RowItem.SystemRow && prev is RowItem.SystemRow -> 4.dp
            cur is RowItem.SystemRow && prev is RowItem.Bubble -> 8.dp
            cur is RowItem.Bubble && prev is RowItem.SystemRow -> 8.dp
            cur is RowItem.Bubble && prev is RowItem.Bubble -> {
                val pos = groupPositions[cur.message.id]
                if (pos == RowGroupPosition.FIRST || pos == RowGroupPosition.SINGLE) 8.dp else 4.dp
            }
            else -> 0.dp
        }
        map[cur.key] = pad
    }
    return map
}
