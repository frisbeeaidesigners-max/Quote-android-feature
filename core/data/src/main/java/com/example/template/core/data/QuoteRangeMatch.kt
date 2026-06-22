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
    val actualFragment = originalText.substring(start, end)
    return if (actualFragment == snapshot.text) MatchResult.Match else MatchResult.QuoteMismatch
}
