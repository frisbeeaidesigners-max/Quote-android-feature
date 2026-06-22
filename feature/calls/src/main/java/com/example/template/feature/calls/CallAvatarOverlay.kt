package com.example.template.feature.calls

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark

/**
 * Аватарка 120dp для off-state звонка. Рендерится поверх затемнённого backdrop'а
 * `CallVideoPlayer` — вызывается из `LobbyContent` / `InCallContent`, чтобы центрироваться
 * между header'ом и нижним рядом кнопок (а не по середине fillMaxSize, где зрительно
 * получалось смещение из-за асимметричной chrome'ы сверху/снизу).
 */
@Composable
internal fun CallAvatarOverlay(
    spec: AvatarSpec,
    modifier: Modifier = Modifier,
    sizeDp: Int = 120,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    @Suppress("UNUSED_VARIABLE")
    val cacheVersion = bitmapCache.version.value
    val gradientPairs = remember(brand, isDark) { brand.avatarGradientPairs(isDark) }
    val gradient = gradientPairs[spec.gradientIndex.coerceIn(0, gradientPairs.lastIndex)]
    val scheme = remember(brand, isDark, gradient) {
        brand.avatarColorScheme(isDark).copy(
            initialsGradientTop = gradient.top,
            initialsGradientBottom = gradient.bottom,
        )
    }
    val image = bitmapCache.get(spec.imageAsset)
    val type = when {
        spec.type == AvatarType.Self -> AvatarView.AvatarViewType.SAVED
        image != null -> AvatarView.AvatarViewType.IMAGE
        else -> AvatarView.AvatarViewType.INITIALS
    }
    AndroidView(
        modifier = modifier.size(sizeDp.dp),
        factory = { ctx -> AvatarView(ctx) },
        update = { v ->
            v.configure(
                mode = AvatarView.AvatarMode.USER_GROUP,
                type = type,
                size = AvatarView.AvatarSize.SIZE_120,
                text = spec.initials.orEmpty(),
                image = image,
                colorScheme = scheme,
            )
        },
    )
}
