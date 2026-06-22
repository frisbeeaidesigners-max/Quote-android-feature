package com.example.template.core.ui.hosts

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.audiopanel.AudioPanelView
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AvatarType
import com.example.template.core.model.ChatType
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.format.TimeFormatter

/**
 * Compose-обёртка над `AudioPanelView`. Подписана на текущее состояние плеера через
 * [playback] и драйвит панель: title (sender или «Сохраненные»), groupName (только для
 * Channel-чатов), playbackState (LOADING/PLAYING/PAUSED — повторяет gallery flow),
 * position, remainingTime, loadingProgress, speed. Колбэки уходят в
 * [VoicePlaybackController] — каждое действие на панели (play/pause, seek, speed, X)
 * бьёт ту же state-машину, что и баббл, и наоборот.
 *
 * Особенности по gallery preview:
 *  • onSeek: `view.seekTo(pos)` ПЕРЕД controller.seek(...) — даёт smooth animation
 *    playhead'а внутри панели (иначе позиция «прыгает»).
 *  • onSpeedClick: циклически переключает X1 → X1.5 → X2 → X1 (контроллер).
 *  • loadingProgress: 0..1 для спиннера во время LOADING-фазы.
 */
@Composable
fun AudioPanelHost(
    playback: VoicePlayback,
    repository: MessengerRepository,
    controller: VoicePlaybackController,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val colorScheme = remember(brand, isDark) { brand.audioPanelColorScheme(isDark) }

    // Title resolution. Saved-чат (chat.avatar.type == Self) — senderName = «Сохраненные»,
    // groupName скрыт. Channel — senderName = имя автора, groupName = название канала.
    // P2P/Group — senderName = имя автора, groupName скрыт.
    val (senderName, groupName) = remember(playback.chatId, playback.senderId) {
        val chat = repository.getChat(playback.chatId)
        // MY voice → senderId == currentUser.id. currentUser живёт в `current-user.json`
        // и НЕ дублируется в users.json → repository.personaForUser(senderId) для self
        // даст null (т.к. getUser(senderId) не находит). Резолвим self отдельно.
        val resolveSender: () -> String = {
            val current = repository.currentUser.value
            if (playback.senderId == current.id) current.name
            else repository.personaForUser(playback.senderId)?.fullName.orEmpty()
        }
        when {
            chat == null -> "" to null
            chat.avatar.type == AvatarType.Self -> "Сохраненные" to null
            chat.type == ChatType.Channel -> resolveSender() to chat.title
            else -> resolveSender() to null
        }
    }

    val remainingMs = (playback.durationMs * (1f - playback.position)).toLong()
    val remainingLabel = TimeFormatter.formatVoiceDuration(remainingMs)
    val panelState = when (playback.state) {
        VoicePlayback.State.LOADING -> AudioPanelView.PlaybackState.LOADING
        VoicePlayback.State.PLAYING -> AudioPanelView.PlaybackState.PLAYING
        VoicePlayback.State.PAUSED -> AudioPanelView.PlaybackState.PAUSED
    }
    val panelSpeed = when (playback.speed) {
        VoicePlayback.Speed.X1 -> AudioPanelView.PlaybackSpeed.X1
        VoicePlayback.Speed.X1_5 -> AudioPanelView.PlaybackSpeed.X1_5
        VoicePlayback.Speed.X2 -> AudioPanelView.PlaybackSpeed.X2
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AudioPanelView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        },
        update = { view ->
            view.configure(
                senderName = senderName,
                groupName = groupName,
                remainingTime = remainingLabel,
                playbackPosition = playback.position,
                loadingProgress = playback.loadingProgress,
                playbackState = panelState,
                playbackSpeed = panelSpeed,
                colorScheme = colorScheme,
            )
            // Плавность: `view.seekTo(pos)` дёргает внутренний 100ms ValueAnimator
            // в ProgressSeekView, который интерполирует playhead между state-апдейтами.
            // Делаем это и для PLAYING (анимация тика), и для PAUSED (без эффекта,
            // но безопасно). LOADING не вызываем — там нет progressSeekView.
            if (panelState != AudioPanelView.PlaybackState.LOADING) {
                view.seekTo(playback.position)
            }
            // play/pause: для toggle нужны те же данные, что использовал caller — у нас
            // они уже есть в playback. Логика state-машины внутри controller'а.
            view.onPlayPauseClick = {
                controller.toggle(
                    messageId = playback.messageId,
                    chatId = playback.chatId,
                    senderId = playback.senderId,
                    durationMs = playback.durationMs,
                )
            }
            view.onSpeedClick = { controller.cycleSpeed() }
            view.onCloseClick = { controller.dismiss() }
            view.onSeek = { pos ->
                // gallery pattern: seekTo() рисует анимированный transition playhead'а
                // внутри панели, ПОТОМ обновляем external state.
                view.seekTo(pos)
                controller.seek(playback.messageId, pos)
            }
        },
    )
}
