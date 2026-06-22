package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatType { P2P, Group, Channel }

@Serializable
enum class PreviewKind { Text, Group, Draft, Typing }

@Serializable
data class MessagePreview(
    val text: String,
    val kind: PreviewKind = PreviewKind.Text,
    val timestamp: Long,
    val ownStatus: MessageStatus = MessageStatus.NONE,
    val senderName: String? = null,
    val senderInitials: String? = null,
    val senderGradientIndex: Int = 0,
    val senderPersonaId: String? = null,
)

@Serializable
data class Chat(
    val id: String,
    val type: ChatType,
    val title: String,
    val avatar: AvatarSpec,
    val spaceId: String? = null,
    val participantIds: List<String> = emptyList(),
    val lastMessage: MessagePreview,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val mention: Boolean = false,
    val hasReaction: Boolean = false,
    val hasPinnedMessages: Boolean = false,
)
