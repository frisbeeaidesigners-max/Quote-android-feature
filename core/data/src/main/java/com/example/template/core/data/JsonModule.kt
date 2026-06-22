package com.example.template.core.data

import kotlinx.serialization.json.Json

internal val AppJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
