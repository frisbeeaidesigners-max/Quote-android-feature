package com.example.template.core.model

/**
 * UI-вариант Modal quote-picker'а. Оба — Compose Dialog поверх ChatDetail.
 * Отличие — оформление нижнего Header'а внутри preview-Box:
 *  - [MODAL_DOTS] — Title/Description + индикатор-точки + горизонтальный свайп между
 *    вкладками «Ответ ↔ Ссылка»
 *  - [MODAL_BUTTONS] — Title/Description с круглыми кнопками-стрелками по бокам; свайп
 *    отключён, переключение только тапом по кнопкам
 *
 * Не путать с `core.ui.QuotePickerStyle` — FULLSCREEN сюда не входит, у него собственный
 * inline overlay (без Dialog'а).
 */
enum class QuoteVariant { MODAL_DOTS, MODAL_BUTTONS }
