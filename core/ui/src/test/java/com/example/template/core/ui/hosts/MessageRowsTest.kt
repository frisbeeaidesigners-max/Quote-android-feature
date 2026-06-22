package com.example.template.core.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.Gender
import com.example.template.core.model.SystemKind
import androidx.compose.ui.unit.Dp

class MessageRowsTest {
    @Test
    fun `flattenToRows returns empty for empty messages`() {
        val result = flattenToRows(
            messages = emptyList(),
            now = 0L,
            personaByUserId = { null },
            formatDate = { "" },
        )
        assertEquals(emptyList<RowItem>(), result.rows)
        assertEquals(emptyMap<String, String>(), result.dateForRow)
    }

    @Test
    fun `flattenToRows emits DateRow then Bubble for single text message`() {
        val msg = Message.Text(
            id = "m-1", chatId = "c-1", senderId = "u-1",
            timestamp = 1715000000_000L, isMine = false, body = "hi",
        )
        val result = flattenToRows(
            messages = listOf(msg),
            now = 1715000000_000L,
            personaByUserId = { null },
            formatDate = { "Сегодня" },
        )
        assertEquals(2, result.rows.size)
        val dateRow = result.rows[0] as RowItem.SystemRow
        assertEquals("Сегодня", dateRow.text)
        assertTrue(dateRow.isDate)
        val bubble = result.rows[1] as RowItem.Bubble
        assertEquals("m-1", bubble.key)
    }

    @Test
    fun `flattenToRows groups two messages of same day under one DateRow`() {
        val day = 1715000000_000L
        val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=day, isMine=false, body="a")
        val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=day + 60_000L, isMine=false, body="b")
        val r = flattenToRows(listOf(m1, m2), day, { null }, { "Сегодня" })
        assertEquals(3, r.rows.size)
        assertTrue(r.rows[0] is RowItem.SystemRow)
        assertEquals("m-1", (r.rows[1] as RowItem.Bubble).key)
        assertEquals("m-2", (r.rows[2] as RowItem.Bubble).key)
    }

    @Test
    fun `flattenToRows emits new DateRow on day boundary`() {
        val day1 = 1715000000_000L
        val day2 = day1 + 24 * 3600 * 1000L
        val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=day1, isMine=false, body="a")
        val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=day2, isMine=false, body="b")
        var calls = 0
        val r = flattenToRows(listOf(m1, m2), day2, { null }, { _ -> "Date-${calls++}" })
        assertEquals(4, r.rows.size)
        assertEquals("Date-0", (r.rows[0] as RowItem.SystemRow).text)
        assertEquals("m-1", (r.rows[1] as RowItem.Bubble).key)
        assertEquals("Date-1", (r.rows[2] as RowItem.SystemRow).text)
        assertEquals("m-2", (r.rows[3] as RowItem.Bubble).key)
    }

    @Test
    fun `renderSystemText returns female form for female persona`() {
        val persona = Persona(
            id = "pe-1", firstName = "Мария", lastName = "Климова",
            avatarAsset = null, gradientIndex = 1, gender = Gender.Female,
        )
        val msg = Message.System(
            id = "s-1", chatId = "c-1", senderId = "u-1",
            timestamp = 0L, kind = SystemKind.Joined,
        )
        val text = renderSystemText(msg) { if (it == "u-1") persona else null }
        assertEquals("Мария Климова присоединилась к группе", text)
    }

    @Test
    fun `renderSystemText returns male form for male persona`() {
        val persona = Persona(
            id = "pe-1", firstName = "Алексей", lastName = "Гурин",
            avatarAsset = null, gradientIndex = 8, gender = Gender.Male,
        )
        val msg = Message.System(
            id = "s-1", chatId = "c-1", senderId = "u-1",
            timestamp = 0L, kind = SystemKind.Joined,
        )
        val text = renderSystemText(msg) { persona }
        assertEquals("Алексей Гурин присоединился к группе", text)
    }

    @Test
    fun `renderSystemText falls back to neutral when persona unknown`() {
        val msg = Message.System(
            id = "s-1", chatId = "c-1", senderId = "u-unknown",
            timestamp = 0L, kind = SystemKind.Joined,
        )
        val text = renderSystemText(msg) { null }
        assertEquals("Кто-то присоединился к группе", text)
    }

    @Test
    fun `computeGroupPositions groups two close same-sender bubbles`() {
        val t = 1715000000_000L
        val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
        val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=t + 60_000L, isMine=false, body="b")
        val rows = listOf<RowItem>(RowItem.Bubble(m1), RowItem.Bubble(m2))
        val pos = computeGroupPositions(rows)
        assertEquals(RowGroupPosition.FIRST, pos["m-1"])
        assertEquals(RowGroupPosition.LAST, pos["m-2"])
    }

    @Test
    fun `computeGroupPositions treats SystemRow as breaker between same-sender bubbles`() {
        val t = 1715000000_000L
        val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
        val sys = RowItem.SystemRow(text="X joined", timestamp=t + 30_000L, key="sys-1", isDate=false)
        val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=t + 60_000L, isMine=false, body="b")
        val rows = listOf<RowItem>(RowItem.Bubble(m1), sys, RowItem.Bubble(m2))
        val pos = computeGroupPositions(rows)
        assertEquals(RowGroupPosition.SINGLE, pos["m-1"])
        assertEquals(RowGroupPosition.SINGLE, pos["m-2"])
    }

    @Test
    fun `computeGroupPositions does not group different senders`() {
        val t = 1715000000_000L
        val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
        val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-2", timestamp=t + 60_000L, isMine=false, body="b")
        val rows = listOf<RowItem>(RowItem.Bubble(m1), RowItem.Bubble(m2))
        val pos = computeGroupPositions(rows)
        assertEquals(RowGroupPosition.SINGLE, pos["m-1"])
        assertEquals(RowGroupPosition.SINGLE, pos["m-2"])
    }

    @Test
    fun `flattenToRows emits SystemRow for Message_System`() {
        val persona = Persona(
            id = "pe-1", firstName = "Мария", lastName = "Климова",
            avatarAsset = null, gradientIndex = 1, gender = Gender.Female,
        )
        val sysMsg = Message.System(
            id = "s-1", chatId = "c-1", senderId = "u-1",
            timestamp = 1715000000_000L, kind = SystemKind.Joined,
        )
        val r = flattenToRows(listOf(sysMsg), 1715000000_000L, { persona }, { "Сегодня" })
        assertEquals(2, r.rows.size)
        val sysRow = r.rows[1] as RowItem.SystemRow
        assertEquals("sys-s-1", sysRow.key)
        assertEquals("Мария Климова присоединилась к группе", sysRow.text)
        assertFalse(sysRow.isDate)
    }

    private fun bubble(id: String, sender: String, ts: Long): RowItem.Bubble =
        RowItem.Bubble(Message.Text(id=id, chatId="c", senderId=sender, timestamp=ts, isMine=false, body=""))
    private fun sysDate(key: String, ts: Long): RowItem.SystemRow =
        RowItem.SystemRow(text="", timestamp=ts, key=key, isDate=true)
    private fun sysJoin(key: String, ts: Long): RowItem.SystemRow =
        RowItem.SystemRow(text="", timestamp=ts, key=key, isDate=false)

    @Test
    fun `computeTopPadding returns zero for first row`() {
        val t = 1715000000_000L
        val sys = RowItem.SystemRow(text="d", timestamp=t, key="date-1", isDate=true)
        val pad = computeTopPadding(listOf(sys), emptyMap())
        assertEquals(0, pad["date-1"]?.value?.toInt())
    }

    @Test fun `topPadding SystemRow after SystemRow is 4dp`() {
        val rows = listOf(sysDate("a", 0L), sysJoin("b", 1L))
        val pad = computeTopPadding(rows, emptyMap())
        assertEquals(4, pad["b"]?.value?.toInt())
    }

    @Test fun `topPadding SystemRow after Bubble is 8dp`() {
        val rows = listOf(bubble("m1", "u1", 0L), sysJoin("s", 1L))
        val groupPos = computeGroupPositions(rows)
        val pad = computeTopPadding(rows, groupPos)
        assertEquals(8, pad["s"]?.value?.toInt())
    }

    @Test fun `topPadding Bubble after SystemRow is 8dp`() {
        val rows = listOf(sysDate("d", 0L), bubble("m1", "u1", 1L))
        val groupPos = computeGroupPositions(rows)
        val pad = computeTopPadding(rows, groupPos)
        assertEquals(8, pad["m1"]?.value?.toInt())
    }

    @Test fun `topPadding Bubble joining prev group is 4dp`() {
        val t = 1715000000_000L
        val rows = listOf(bubble("m1", "u1", t), bubble("m2", "u1", t + 60_000L))
        val groupPos = computeGroupPositions(rows)
        val pad = computeTopPadding(rows, groupPos)
        assertEquals(4, pad["m2"]?.value?.toInt())
    }

    @Test fun `topPadding Bubble starting new group is 8dp`() {
        val rows = listOf(bubble("m1", "u1", 0L), bubble("m2", "u2", 1L))
        val groupPos = computeGroupPositions(rows)
        val pad = computeTopPadding(rows, groupPos)
        assertEquals(8, pad["m2"]?.value?.toInt())
    }
}
