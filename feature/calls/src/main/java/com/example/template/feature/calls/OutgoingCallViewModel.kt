package com.example.template.feature.calls

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.CallContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase оверлея звонка. `InCall.startElapsedMs` хранит SystemClock.elapsedRealtime() в момент
 * нажатия «Присоединиться» (или окончания ringing'а для P2P, см. [OutgoingCallViewModel.RINGING_DELAY_MS]) —
 * от этого считаем длительность.
 */
sealed class CallPhase {
    data object Lobby : CallPhase()
    data class InCall(val startElapsedMs: Long) : CallPhase()
}

/** Состояние трёх toggle-кнопок. Camera ON по умолчанию, mic OFF (mute-on-join), speaker ON. */
data class CallToggles(
    val camera: Boolean = true,
    val mic: Boolean = false,
    val speaker: Boolean = true,
)

class OutgoingCallViewModel(
    val context: CallContext,
    private val repository: MessengerRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow<CallPhase>(
        // P2P пропускает Lobby — звонок сразу в InCall (с ringing'ом на 600ms перед connect).
        if (context is CallContext.P2PCall) {
            CallPhase.InCall(startElapsedMs = SystemClock.elapsedRealtime())
        } else {
            CallPhase.Lobby
        },
    )
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()

    private val _toggles = MutableStateFlow(CallToggles())
    val toggles: StateFlow<CallToggles> = _toggles.asStateFlow()

    /** Тикает раз в секунду пока phase == InCall И callConnected. До connect — 0. */
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /**
     * true когда «трубку подняли» (имитация). Для P2P становится true через [RINGING_DELAY_MS] после mount'а.
     * Для Group/BrandMeet — после `onJoin()`. До connect статус-текст в UI пишет «Вызов...»,
     * а таймер не тикает.
     */
    private val _callConnected = MutableStateFlow(false)
    val callConnected: StateFlow<Boolean> = _callConnected.asStateFlow()

    /**
     * Камера counterpart-а (только для P2P). По умолчанию false — собеседник «не показывает» себя.
     * Переключается через [onToggleSomeoneCamera] (имитация через long-tap по аватарке/видео).
     * 4 комбинации (self.camera × someoneCameraOn) дают 4 layout-варианта в `P2PInCallContent`.
     */
    private val _someoneCameraOn = MutableStateFlow(false)
    val someoneCameraOn: StateFlow<Boolean> = _someoneCameraOn.asStateFlow()

    private var timerJob: Job? = null
    private var ringingJob: Job? = null

    init {
        if (context is CallContext.P2PCall) {
            // P2P стартуем со своей камерой OFF — звонящий обычно не сразу включает себя в кадр.
            _toggles.value = _toggles.value.copy(camera = false)
            ringingJob = viewModelScope.launch {
                delay(RINGING_DELAY_MS)
                val start = SystemClock.elapsedRealtime()
                _phase.value = CallPhase.InCall(startElapsedMs = start)
                _callConnected.value = true
                startTimer(start)
                // someoneCameraOn остаётся false — counterpart подключается без камеры.
                // Включить/выключить можно только long-tap'ом по аватарке/fullscreen-картинке.
            }
        }
    }

    fun onToggleCamera() {
        _toggles.value = _toggles.value.copy(camera = !_toggles.value.camera)
    }

    fun onToggleMic() {
        _toggles.value = _toggles.value.copy(mic = !_toggles.value.mic)
    }

    fun onToggleSpeaker() {
        _toggles.value = _toggles.value.copy(speaker = !_toggles.value.speaker)
    }

    /** Имитация: counterpart включает/выключает свою камеру. Триггер — long-tap по аватарке/видео. */
    fun onToggleSomeoneCamera() {
        _someoneCameraOn.value = !_someoneCameraOn.value
    }

    fun onJoin() {
        if (_phase.value !is CallPhase.Lobby) return
        val start = SystemClock.elapsedRealtime()
        _phase.value = CallPhase.InCall(startElapsedMs = start)
        _callConnected.value = true
        startTimer(start)
    }

    private fun startTimer(start: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _elapsedMs.value = SystemClock.elapsedRealtime() - start
                delay(1000L)
            }
        }
    }

    /** Вызывается на X в Lobby или на Collapse / swipe-down в InCall — без записи в history. */
    fun onDismissWithoutRecording() {
        timerJob?.cancel(); timerJob = null
        ringingJob?.cancel(); ringingJob = null
    }

    /** Вызывается на end-call (красная кнопка) в InCall — записываем durationMs. */
    fun onEndCall() {
        val phaseSnapshot = _phase.value
        val connected = _callConnected.value
        timerJob?.cancel(); timerJob = null
        ringingJob?.cancel(); ringingJob = null
        if (phaseSnapshot !is CallPhase.InCall) return
        // До connect (ringing) durationMs = 0 — звонок «не дошёл».
        val durationMs = if (connected) {
            SystemClock.elapsedRealtime() - phaseSnapshot.startElapsedMs
        } else {
            0L
        }
        viewModelScope.launch {
            repository.recordOutgoingCall(
                counterpartId = context.historyTarget.id,
                counterpartName = context.historyTarget.name,
                counterpartAvatar = context.historyTarget.avatar,
                durationMs = durationMs,
                isVideo = true,
                isGroupCall = context is CallContext.GroupCall,
            )
            val chatIdForBubble = when (val c = context) {
                is CallContext.P2PCall -> c.chatId
                is CallContext.GroupCall -> c.chatId
                is CallContext.BrandMeet -> null
            }
            if (chatIdForBubble != null) {
                repository.appendCallMeetMessage(
                    chatId = chatIdForBubble,
                    durationMs = durationMs,
                    isVideo = true,
                    isGroupCall = context is CallContext.GroupCall,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        ringingJob?.cancel()
    }

    companion object {
        /** Длительность «Вызов...» перед поднятием трубки в P2P. */
        const val RINGING_DELAY_MS = 2000L
    }
}
