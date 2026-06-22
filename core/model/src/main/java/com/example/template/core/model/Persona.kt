package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Gender { Male, Female }

@Serializable
data class Persona(
    val id: String,
    val firstName: String,
    val lastName: String,
    val avatarAsset: String? = null,
    val gradientIndex: Int = 0,
    val gender: Gender,
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val initials: String get() =
        (firstName.firstOrNull()?.toString().orEmpty() + lastName.firstOrNull()?.toString().orEmpty()).uppercase()
}
