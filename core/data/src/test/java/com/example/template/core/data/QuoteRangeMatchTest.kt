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
        val snap = ReplyPreview("m1", "Боб", "ивет", quoteStart = 2, quoteEnd = 6)
        assertEquals(MatchResult.Match, matchesQuote("Привет, мир", snap))
    }

    @Test
    fun `quote mismatch when text edited`() {
        val snap = ReplyPreview("m1", "Боб", "ивет", quoteStart = 2, quoteEnd = 6)
        assertEquals(MatchResult.QuoteMismatch, matchesQuote("Прощай, мир", snap))
    }

    @Test
    fun `quote out of range counts as mismatch`() {
        val snap = ReplyPreview("m1", "Боб", "frag", quoteStart = 10, quoteEnd = 14)
        assertEquals(MatchResult.QuoteMismatch, matchesQuote("short", snap))
    }
}
