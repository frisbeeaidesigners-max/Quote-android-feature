package com.example.template.core.ui.utils

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Эвристический поиск TextView внутри View по совпадению текста.
 * Используется для:
 *  - V1 in-place quote (текст сообщения в BubblesView/MediaBubbleView) — для получения
 *    ссылки на TextView, необходимой QuoteActionModeCallback.selectionStart/End. Сам флаг
 *    и callback устанавливаются через BubblesView/MediaBubbleView.setMessageTextSelectable(),
 *    а не view-walk'ом напрямую.
 *  - reply-block clickability в MessagePanelView (preview-text в панели).
 *
 * NOTE: reply-block click в bubble'ах (BubblesView / MediaBubbleView) больше НЕ использует
 * view-walk — он идёт через onReplyBlockClick параметр configure(). Функция
 * findReplyBlockContainer была удалена после перехода на :components public API.
 *
 * Fragility-нота: если :components поменяет внутреннюю структуру (например, разобьёт
 * текст на несколько TextView), эта функция вернёт null или не тот View. В этом случае
 * нужно согласовать с пользователем правку :components — добавить публичный API
 * (например BubblesView.selectableTextView(): TextView). Локальный hack предпочтительнее
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
