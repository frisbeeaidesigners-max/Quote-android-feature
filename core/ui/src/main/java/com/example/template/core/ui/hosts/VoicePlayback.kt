package com.example.template.core.ui.hosts

/**
 * Состояние активного voice-плеера. В приложении максимум один — поднят на
 * AppContainer-уровень в [VoicePlaybackController], переживает закрытие чата и
 * переключение между чат-листом / пространствами.
 *
 * Поведение state-машины повторяет gallery preview AudioPanelView'а: фаза LOADING
 * на холодном старте (спиннер ~450мс), затем PLAYING; PAUSED не сбрасывает position;
 * resume с position=0 снова идёт через LOADING (как «новое воспроизведение»), а
 * resume с position>0 — сразу в PLAYING.
 *
 * MessageList маппит [state] на [com.example.components.bubbles.VoiceBubbleView.PlaybackState]:
 *   • LOADING → DOWNLOADING (с loadingProgress для спиннера баббла)
 *   • PLAYING → PLAYING, PAUSED → PAUSED
 *   • другой messageId → DOWNLOADED (доступен play)
 *
 * AudioPanelHost маппит на [com.example.components.audiopanel.AudioPanelView.PlaybackState]
 * напрямую (имена совпадают 1:1).
 */
data class VoicePlayback(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val durationMs: Long,
    val state: State,
    /** 0..1, тикается из [VoicePlaybackController] с учётом [speed.multiplier]. */
    val position: Float = 0f,
    /** 0..1, прогресс LOADING-спиннера, только в state == LOADING. */
    val loadingProgress: Float = 0f,
    val speed: Speed = Speed.X1,
) {
    enum class State { LOADING, PLAYING, PAUSED }

    enum class Speed(val multiplier: Float, val label: String) {
        X1(1f, "1X"),
        X1_5(1.5f, "1.5X"),
        X2(2f, "2X");

        fun next(): Speed = entries[(ordinal + 1) % entries.size]
    }
}
