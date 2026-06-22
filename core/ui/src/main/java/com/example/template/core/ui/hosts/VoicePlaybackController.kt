package com.example.template.core.ui.hosts

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-level holder для активного voice-плеера. Раньше state жил в ChatDetailViewModel
 * и убивался на закрытии чата — теперь переживает закрытие чата и переключение
 * между табами. Используется и MessageList'ом (для маппинга PlaybackState бабла),
 * и AudioPanelHost'ом (для отображения панели над списком чатов / пространств).
 *
 * Один активный плеер в приложении: тап на play другого бабла (даже в другом чате)
 * сбрасывает текущий. State-машина повторяет gallery preview AudioPanelView'а:
 *  - LOADING (450мс спиннер) → PLAYING
 *  - PLAYING → PAUSED (по тапу, position сохраняется)
 *  - PAUSED → PLAYING (если position > 0) ИЛИ LOADING+PLAYING (если position == 0)
 *  - PLAYING → state=null (доигралось до конца)
 *  - dismiss() — безусловный reset (X в AudioPanel)
 *
 * Lifecycle: AppContainer-scoped (живёт всю жизнь Application'а). [scope] не отменяется
 * — это норма для AppContainer-уровня; jobs отменяются руками в каждом transition'е.
 */
class VoicePlaybackController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _voicePlayback = MutableStateFlow<VoicePlayback?>(null)
    val voicePlayback: StateFlow<VoicePlayback?> = _voicePlayback.asStateFlow()

    /** Активный job — либо loading-спиннер, либо position-тикер. cancel перед стартом нового. */
    private var activeJob: Job? = null

    /**
     * Tap по play-кнопке бабла или AudioPanel. У нас баббл всегда стартует в DOWNLOADED
     * (реального download'а в mock нет), поэтому LOADING-фаза не фигурирует — переход
     * сразу в PLAYING. State.LOADING оставлен в enum для будущего: когда баббл будет
     * стартовать в NOT_DOWNLOADED, controller добавит сюда переход через LOADING.
     *
     *  • если ничего не играет / другой id → сразу PLAYING с position = 0
     *  • если LOADING (зарезервировано) → no-op
     *  • если PLAYING → PAUSED (position сохраняется)
     *  • если PAUSED → resume PLAYING с текущей position (с 0, если был доигран)
     */
    fun toggle(messageId: String, chatId: String, senderId: String, durationMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val cur = _voicePlayback.value
        when {
            cur == null || cur.messageId != messageId -> {
                val keepSpeed = cur?.speed ?: VoicePlayback.Speed.X1
                startFreshPlaying(messageId, chatId, senderId, safeDuration, speed = keepSpeed)
            }
            cur.state == VoicePlayback.State.LOADING -> Unit
            cur.state == VoicePlayback.State.PLAYING -> {
                activeJob?.cancel()
                _voicePlayback.value = cur.copy(state = VoicePlayback.State.PAUSED)
            }
            cur.state == VoicePlayback.State.PAUSED -> {
                _voicePlayback.value = cur.copy(state = VoicePlayback.State.PLAYING)
                startPositionTicker(messageId, safeDuration, fromPosition = cur.position, speed = cur.speed)
            }
        }
    }

    /**
     * Drag по waveform — компонент стреляет на ACTION_UP с position 0..1. Сохраняем
     * play/pause-state, рестартим position-ticker с новой точки (иначе он продолжит
     * по старому anchor'у и position тут же откатится).
     *
     * NB: для AudioPanelView вызов `view.seekTo(pos)` делает АНИМАЦИЮ playhead'а
     * внутри панели — мы это делаем в AudioPanelHost ПЕРЕД seek-ом в controller.
     */
    fun seek(messageId: String, position: Float) {
        val cur = _voicePlayback.value ?: return
        if (cur.messageId != messageId) return
        if (cur.state == VoicePlayback.State.LOADING) return  // во время загрузки seek не имеет смысла
        val clamped = position.coerceIn(0f, 1f)
        _voicePlayback.value = cur.copy(position = clamped)
        if (cur.state == VoicePlayback.State.PLAYING) {
            startPositionTicker(messageId, cur.durationMs, fromPosition = clamped, speed = cur.speed)
        }
    }

    /** Tap по 1X/1.5X/2X в AudioPanel — циклически переключаем. Если играем, рестартим тикер. */
    fun cycleSpeed() {
        val cur = _voicePlayback.value ?: return
        val newSpeed = cur.speed.next()
        _voicePlayback.value = cur.copy(speed = newSpeed)
        if (cur.state == VoicePlayback.State.PLAYING) {
            startPositionTicker(cur.messageId, cur.durationMs, fromPosition = cur.position, speed = newSpeed)
        }
    }

    /** Tap по X в AudioPanel. Безусловный reset. */
    fun dismiss() {
        activeJob?.cancel()
        _voicePlayback.value = null
    }

    /**
     * Удаление сообщения (System(MessageDeleted)-placeholder): если играем удалённый —
     * глушим. Зовётся из ChatDetailViewModel.init.collect.
     */
    fun pruneIfMessageGone(stillActiveMessageIds: Set<String>) {
        val cur = _voicePlayback.value ?: return
        if (cur.messageId !in stillActiveMessageIds) {
            dismiss()
        }
    }

    private fun startFreshPlaying(
        id: String,
        chatId: String,
        senderId: String,
        durationMs: Long,
        speed: VoicePlayback.Speed,
    ) {
        _voicePlayback.value = VoicePlayback(
            messageId = id,
            chatId = chatId,
            senderId = senderId,
            durationMs = durationMs,
            state = VoicePlayback.State.PLAYING,
            position = 0f,
            loadingProgress = 0f,
            speed = speed,
        )
        startPositionTicker(id, durationMs, fromPosition = 0f, speed = speed)
    }

    private fun startPositionTicker(
        id: String,
        durationMs: Long,
        fromPosition: Float,
        speed: VoicePlayback.Speed,
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            // Delta-based тикер: легче поддерживает смену speed на лету. Тик 33мс (30fps).
            // ChatDetail-баббл больше не пересчитывает всю configure() на каждом тике —
            // MessageList детектит «изменилась только playback-позиция» и идёт по lightweight-
            // пути `VoiceBubbleView.updatePlaybackPosition(...)`, который трогает только
            // waveform position + duration text. AudioPanel дополнительно сглаживает playhead
            // через `view.seekTo(pos)` — внутренний 100мс ValueAnimator интерполирует между
            // state-апдейтами визуально гладко.
            var lastMs = SystemClock.elapsedRealtime()
            while (true) {
                delay(33L)
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - lastMs).coerceAtLeast(1L)
                lastMs = now
                val cur = _voicePlayback.value
                if (cur == null || cur.messageId != id || cur.state != VoicePlayback.State.PLAYING) break
                val advance = elapsed.toFloat() / durationMs * cur.speed.multiplier
                val pos = (cur.position + advance).coerceIn(0f, 1f)
                _voicePlayback.value = cur.copy(position = pos)
                if (pos >= 1f) {
                    // Доигралось от начала до конца с учётом перемоток и speed — панель пропадает.
                    _voicePlayback.value = null
                    break
                }
            }
        }
    }
}
