package com.example.template.core.model

/**
 * UI-вариант Modal quote-picker'а. Оба — Compose Dialog поверх ChatDetail.
 * Отличие — оформление header'а внутри preview-Box:
 *  - [MODAL_SWIPE] — нижний footer Title/Description + индикатор-точки + горизонтальный свайп
 *    между вкладками «Ответ ↔ Ссылка». linkRender ON/OFF поведение пока совпадает (placeholder
 *    OFF-варианта будет реализован позже).
 *  - [MODAL_STICKY] — sticky header Title/Description поверх preview Box'а, без внешнего
 *    footer'а и без link-таба. linkRender ON/OFF поведение пока совпадает (placeholder
 *    ON-варианта будет реализован позже).
 *
 * Не путать с `core.ui.QuotePickerStyle` — FULLSCREEN сюда не входит, у него собственный
 * inline overlay (без Dialog'а).
 */
enum class QuoteVariant { MODAL_SWIPE, MODAL_STICKY }
