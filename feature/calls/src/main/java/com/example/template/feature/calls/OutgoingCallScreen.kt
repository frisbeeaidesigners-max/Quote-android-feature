package com.example.template.feature.calls

import androidx.compose.runtime.Composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.CallContext

/**
 * Корневой композабл оверлея звонка. Тонкий диспетчер — выбирает экран по типу [context]:
 * - **P2PCall** → [P2POutgoingScreen]: пропускает Lobby, fullscreen fade-in, counterpart-аватарка
 *   в центре, 2s ringing → callConnected → таймер.
 * - **GroupCall / BrandMeet** → [LobbyInCallScreen]: Lobby (sheet) → InCall (slide-in справа)
 *   с параллельными анимациями между фазами.
 *
 * Каждый экран самостоятельно создаёт VM, Player, держит mount-флаги и координирует [onClose] —
 * никаких пересекающихся ветвей. MainActivity видит один публичный entry point.
 */
@Composable
fun OutgoingCallScreen(
    context: CallContext,
    selfAvatar: AvatarSpec,
    repository: MessengerRepository,
    onClose: () -> Unit,
) {
    if (context is CallContext.P2PCall) {
        P2POutgoingScreen(
            context = context,
            selfAvatar = selfAvatar,
            repository = repository,
            onClose = onClose,
        )
    } else {
        LobbyInCallScreen(
            context = context,
            selfAvatar = selfAvatar,
            repository = repository,
            onClose = onClose,
        )
    }
}
