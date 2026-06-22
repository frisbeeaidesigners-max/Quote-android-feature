package com.example.template.core.ui.utils

import android.content.Context
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.widget.TextView

/**
 * SpanWatcher для tactile-tick'а в момент перехода selection из empty в non-empty.
 *
 * Срабатывает на любом изменении spans, проверяет current selection. Если перешли
 * empty→non-empty — вибрирует [performStrongHaptic]. Работает и для native long-press
 * (selectCurrentWord ставит SELECTION_START/END во время удержания пальца, до его
 * отпускания), и для программного Selection.setSelection / TextView.selectAll().
 */
private class SelectionHapticWatcher(private val context: Context) : SpanWatcher {
    private var hadSelection = false

    override fun onSpanAdded(text: Spannable, what: Any?, start: Int, end: Int) = check(text)
    override fun onSpanChanged(text: Spannable, what: Any?, ostart: Int, oend: Int, nstart: Int, nend: Int) = check(text)
    override fun onSpanRemoved(text: Spannable, what: Any?, start: Int, end: Int) = check(text)

    private fun check(text: Spannable) {
        val s = Selection.getSelectionStart(text)
        val e = Selection.getSelectionEnd(text)
        val nowHasSel = s >= 0 && e >= 0 && s != e
        if (nowHasSel && !hadSelection) {
            performStrongHaptic(context)
        }
        hadSelection = nowHasSel
    }
}

/**
 * Прицепляет [SelectionHapticWatcher] к Spannable текста TextView. Идемпотентна —
 * существующий watcher переустанавливается, не дублирует.
 *
 * Вызывать ПОСЛЕ `setMessageTextSelectable(true, ...)` (и любого re-configure, т.к.
 * `setText(...)` заменяет Spannable и старый watcher теряется).
 */
fun TextView.attachSelectionHapticWatcher() {
    val span = text as? Spannable ?: return
    span.getSpans(0, span.length, SelectionHapticWatcher::class.java).forEach { span.removeSpan(it) }
    span.setSpan(
        SelectionHapticWatcher(context),
        0, span.length,
        Spanned.SPAN_INCLUSIVE_INCLUSIVE,
    )
}
