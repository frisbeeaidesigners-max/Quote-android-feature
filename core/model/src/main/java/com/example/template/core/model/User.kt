package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val status: UserStatus = UserStatus.Offline,
    val personaId: String? = null,
)

@Serializable
data class CurrentUser(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val jobTitle: String? = null,
    val statusMessage: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val personaId: String? = null,
)
