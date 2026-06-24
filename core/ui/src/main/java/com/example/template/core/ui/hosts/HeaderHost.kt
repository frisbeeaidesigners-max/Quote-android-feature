package com.example.template.core.ui.hosts

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.headers.HeadersView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun HeaderHost(
    config: HeadersView.HeaderConfig,
    modifier: Modifier = Modifier,
    // Override фона из брендовой схемы (по умолчанию = backgroundBase из DSBrand).
    // Используется V5 quote-picker'ом, где остальной экран — appSurface01 и
    // несовпадение цветов между header'ом и контентом бросается в глаза.
    backgroundColorOverride: Color? = null,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val colorScheme = remember(brand, isDark, backgroundColorOverride) {
        val base = brand.headersColorScheme(isDark)
        if (backgroundColorOverride != null) base.copy(backgroundColor = backgroundColorOverride.toArgb())
        else base
    }
    val viewRef = remember { mutableStateOf<HeadersView?>(null) }

    // configure() пересоздаёт View-иерархию (removeAllViews + new buttons), поэтому вызываем
    // только при реальной смене config/colorScheme. AndroidView.update иначе срабатывает на каждом
    // кадре и рушит тапы по кнопкам header'а (кнопка уничтожается между ACTION_DOWN и ACTION_UP).
    DisposableEffect(viewRef.value, config, colorScheme) {
        viewRef.value?.configure(config, colorScheme)
        onDispose {}
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            HeadersView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }.also { viewRef.value = it }
        },
        update = { /* no-op: configure через DisposableEffect выше */ },
    )
}
