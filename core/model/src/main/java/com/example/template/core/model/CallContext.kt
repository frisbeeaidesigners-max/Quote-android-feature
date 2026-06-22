package com.example.template.core.model

/**
 * Контекст исходящего звонка — задаёт title в хедере и кого вписать в историю звонков
 * после end-call. Создаётся в момент тапа на entry-point (Calls «Новая встреча» /
 * ChatHeader.Call) и передаётся в `MainActivity.callContext`.
 */
sealed class CallContext {
    abstract val title: String
    abstract val historyTarget: HistoryTarget

    /** Brand-meet из вкладки «Звонки» — title = "{Brand} Meet", chat-бабла нет. */
    data class BrandMeet(
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()

    /** P2P-чат — title = persona.fullName, chatId нужен для CallMeet-бабла. */
    data class P2PCall(
        val chatId: String,
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()

    /** Group/Channel — title = chat.title, chatId нужен для CallMeet-бабла. */
    data class GroupCall(
        val chatId: String,
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()
}

/**
 * Цель для записи в историю Calls. id/name/avatar — то, что попадает в [Call]
 * (`counterpartId`/`counterpartName`/`counterpartAvatar`).
 */
data class HistoryTarget(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
)
