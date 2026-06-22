package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AvatarSpec(
    val type: AvatarType,
    val initials: String? = null,
    val gradientIndex: Int = 0,
    val imageAsset: String? = null,
)

@Serializable
enum class AvatarType { Person, Group, Channel, Workspace, Self }
