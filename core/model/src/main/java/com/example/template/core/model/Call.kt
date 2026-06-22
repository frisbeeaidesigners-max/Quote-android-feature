package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CallType { Incoming, Outgoing, Missed }

@Serializable
data class Call(
    val id: String,
    val type: CallType,
    val counterpartId: String,
    val counterpartName: String,
    val counterpartAvatar: AvatarSpec,
    val isVideo: Boolean = false,
    val isGroupCall: Boolean = false,
    val timestamp: Long,
    val durationMs: Long? = null,
)
