package com.example.template.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Reaction(val emoji: String, val count: Int, val isMine: Boolean = false)

@Serializable
enum class AttachmentType { Photo, Video, File }

@Serializable
data class MediaAttachment(
    val placeholderColor: String,
    val type: AttachmentType,
    val durationMs: Long? = null,
    // Транзиентные поля для рендера в MediaBubbleView: переживают только in-memory
    // сессию (через mock JSON не сохраняются). Нужны, когда сообщение отправляется
    // из MessagePanelHost — там уже есть готовый Bitmap-thumbnail и текстовая
    // длительность. При загрузке из mock JSON остаются null, бабл нарисуется
    // placeholder'ом по placeholderColor.
    // thumbnail хранится как `Any?` (а не `android.graphics.Bitmap`), чтобы
    // :core:model оставался pure-Kotlin модулем без android-зависимостей
    // (см. CLAUDE.md). Потребитель в :core:ui кастит к Bitmap.
    @Transient val thumbnail: Any? = null,
    @Transient val durationLabel: String? = null,
)

@Serializable
enum class CallStatus { Answered, Rejected, Missed, NoAnswer }

@Serializable
enum class SpanStyle { Bold, Italic, Underline, Strikethrough, Mono, Link }

@Serializable
enum class SystemKind { Joined, MessageDeleted }

@Serializable
data class TextSpan(val start: Int, val endExclusive: Int, val style: SpanStyle)

@Serializable
data class ReplyPreview(
    val originalId: String,
    val authorName: String,
    val text: String,
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
)

@Serializable
sealed class Message {
    abstract val id: String
    abstract val chatId: String
    abstract val senderId: String
    abstract val timestamp: Long
    abstract val isMine: Boolean
    abstract val status: MessageStatus
    abstract val reactions: List<Reaction>
    abstract val replyTo: ReplyPreview?

    @Serializable @SerialName("text")
    data class Text(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val body: String,
        val formatting: List<TextSpan> = emptyList(),
        val isEdited: Boolean = false,
    ) : Message()

    @Serializable @SerialName("media")
    data class Media(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val attachments: List<MediaAttachment>,
        val caption: String? = null,
        val isEdited: Boolean = false,
    ) : Message()

    @Serializable @SerialName("voice")
    data class Voice(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val durationMs: Long,
        val waveform: List<Int>,
        val transcription: String? = null,
    ) : Message()

    @Serializable @SerialName("link")
    data class Link(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val url: String,
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val body: String? = null,
    ) : Message()

    @Serializable @SerialName("callmeet")
    data class CallMeet(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val callStatus: CallStatus,
        val isVideo: Boolean,
        val isGroupCall: Boolean,
        val durationMs: Long? = null,
    ) : Message()

    @Serializable @SerialName("system")
    data class System(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean = false,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyTo: ReplyPreview? = null,
        val kind: SystemKind,
    ) : Message()
}
