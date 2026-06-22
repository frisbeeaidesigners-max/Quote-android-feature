package com.example.template.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UserStatus {
    @Serializable @SerialName("online")
    data object Online : UserStatus()

    @Serializable @SerialName("offline")
    data object Offline : UserStatus()

    @Serializable @SerialName("last_seen")
    data class LastSeen(val timestamp: Long) : UserStatus()
}

@Serializable
enum class MessageStatus { NONE, SENDING, DELIVERED, READ, ERROR }
