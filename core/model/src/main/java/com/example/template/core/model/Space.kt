package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Space(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val accentIndex: Int = 0,
)
