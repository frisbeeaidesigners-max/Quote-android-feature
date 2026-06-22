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
