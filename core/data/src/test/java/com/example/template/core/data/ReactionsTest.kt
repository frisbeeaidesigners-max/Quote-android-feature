package com.example.template.core.data

import com.example.template.core.model.Reaction
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionsTest {

    @Test
    fun `toggleReaction adds new stack when emoji absent`() {
        val before = emptyList<Reaction>()
        val after = applyToggle(before, "❤️")
        assertEquals(listOf(Reaction("❤️", count = 1, isMine = true)), after)
    }

    @Test
    fun `toggleReaction increments when emoji exists and not mine`() {
        val before = listOf(Reaction("👍", count = 3, isMine = false))
        val after = applyToggle(before, "👍")
        assertEquals(listOf(Reaction("👍", count = 4, isMine = true)), after)
    }

    @Test
    fun `toggleReaction decrements when emoji is mine`() {
        val before = listOf(Reaction("🎉", count = 2, isMine = true))
        val after = applyToggle(before, "🎉")
        assertEquals(listOf(Reaction("🎉", count = 1, isMine = false)), after)
    }

    @Test
    fun `toggleReaction removes stack when last mine count drops to zero`() {
        val before = listOf(Reaction("🥳", count = 1, isMine = true))
        val after = applyToggle(before, "🥳")
        assertEquals(emptyList<Reaction>(), after)
    }

    @Test
    fun `toggleReaction preserves order of other reactions`() {
        val before = listOf(
            Reaction("👌", count = 1, isMine = false),
            Reaction("👍", count = 2, isMine = false),
            Reaction("❤️", count = 5, isMine = false),
        )
        val after = applyToggle(before, "👍")
        assertEquals(
            listOf(
                Reaction("👌", count = 1, isMine = false),
                Reaction("👍", count = 3, isMine = true),
                Reaction("❤️", count = 5, isMine = false),
            ),
            after,
        )
    }

    private fun applyToggle(reactions: List<Reaction>, emoji: String): List<Reaction> =
        MockRepositoryImpl.toggleReactionList(reactions, emoji)
}
