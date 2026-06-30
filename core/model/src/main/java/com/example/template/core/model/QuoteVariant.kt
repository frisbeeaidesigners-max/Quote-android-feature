package com.example.template.core.model

/**
 * UI-вариант Modal quote-picker'а. Все — Compose Dialog поверх ChatDetail.
 *  - [MODAL_STICKY] — V1 sticky-header overlay внутри preview Box'а, без footer'а;
 *    при linkRender=ON под меню появляется segmented control «Ответ/Ссылка».
 *  - [MODAL_STICKY_2] — копия MODAL_STICKY (variant 2 в Profile, для последующих
 *    модификаций). Идентичная отрисовка.
 *  - [MODAL_BUTTONS] — floating pill с круглыми кнопками-стрелками по бокам (ON)
 *    или без них (OFF). Переключение tab'а тапом по стрелкам.
 *
 * Не путать с `core.ui.QuotePickerStyle` — FULLSCREEN сюда не входит, у него собственный
 * inline overlay (без Dialog'а).
 */
enum class QuoteVariant { MODAL_STICKY_2, MODAL_STICKY, MODAL_BUTTONS }
