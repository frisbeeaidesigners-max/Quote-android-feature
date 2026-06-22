package com.example.template.core.ui.theme

import com.example.components.avatar.AvatarColorScheme
import com.example.components.designsystem.DSBrand

/**
 * Returns AvatarColorScheme with the initials gradient set to the gradient at [gradientIndex]
 * (modulo the number of available pairs). The bot/saved gradients remain from the default scheme.
 */
fun avatarColorSchemeForGradient(brand: DSBrand, isDark: Boolean, gradientIndex: Int): AvatarColorScheme {
    val pairs = brand.avatarGradientPairs(isDark)
    val pair = pairs[gradientIndex.coerceAtLeast(0) % pairs.size]
    val base = brand.avatarColorScheme(isDark)
    return base.copy(
        initialsGradientTop = pair.top,
        initialsGradientBottom = pair.bottom,
    )
}
