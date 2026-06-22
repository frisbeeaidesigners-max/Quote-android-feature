package com.example.template.core.data

import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.MessagePreview
import com.example.template.core.model.PreviewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryFilterTest {

    private fun chat(
        id: String,
        type: ChatType,
        spaceId: String?,
        timestamp: Long = 0L,
        pinned: Boolean = false,
    ) = Chat(
        id = id, type = type, title = id,
        avatar = AvatarSpec(AvatarType.Person, initials = id),
        spaceId = spaceId,
        lastMessage = MessagePreview("", PreviewKind.Text, timestamp),
        pinned = pinned,
    )

    @Test
    fun `filterP2P returns only P2P chats`() {
        val all = listOf(
            chat("1", ChatType.P2P, null),
            chat("2", ChatType.Group, "s1"),
            chat("3", ChatType.Channel, "s1"),
            chat("4", ChatType.P2P, null),
        )
        val p2p = MockRepositoryImpl.filterP2P(all)
        assertEquals(listOf("1", "4"), p2p.map { it.id })
    }

    @Test
    fun `filterSpace returns Group and Channel of given space`() {
        val all = listOf(
            chat("g1", ChatType.Group, "s1"),
            chat("c1", ChatType.Channel, "s1"),
            chat("g2", ChatType.Group, "s2"),
            chat("p", ChatType.P2P, null),
        )
        val s1 = MockRepositoryImpl.filterSpace(all, "s1")
        assertEquals(listOf("g1", "c1"), s1.map { it.id })
    }

    @Test
    fun `sortChats puts pinned on top and orders by lastMessage desc within group`() {
        val all = listOf(
            chat("a", ChatType.P2P, null, timestamp = 100L, pinned = false),
            chat("b", ChatType.P2P, null, timestamp = 300L, pinned = true),
            chat("c", ChatType.P2P, null, timestamp = 200L, pinned = false),
            chat("d", ChatType.P2P, null, timestamp = 400L, pinned = true),
            chat("e", ChatType.P2P, null, timestamp = 500L, pinned = false),
        )
        val sorted = MockRepositoryImpl.sortChats(all)
        // pinned (d>b), потом non-pinned (e>c>a)
        assertEquals(listOf("d", "b", "e", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun `sortChats promotes chat when its lastMessage timestamp grows`() {
        val initial = listOf(
            chat("a", ChatType.P2P, null, timestamp = 300L),
            chat("b", ChatType.P2P, null, timestamp = 200L),
            chat("c", ChatType.P2P, null, timestamp = 100L),
        )
        assertEquals(listOf("a", "b", "c"), MockRepositoryImpl.sortChats(initial).map { it.id })

        // Имитируем отправку в "c": его timestamp обогнал всех.
        val afterSend = initial.map {
            if (it.id == "c") it.copy(lastMessage = it.lastMessage.copy(timestamp = 400L)) else it
        }
        assertEquals(listOf("c", "a", "b"), MockRepositoryImpl.sortChats(afterSend).map { it.id })
    }
}
