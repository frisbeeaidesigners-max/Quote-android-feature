package com.example.template.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AppScaffold(
    content: @Composable () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(brand.backgroundBase(isDark)))
            .statusBarsPadding()
            .navigationBarsPadding(),
        // imePadding НЕ ставим тут: на каждом кадре анимации клавиатуры (90fps) inset
        // меняется → Compose релэйаутит весь Column. Под ним MainScaffold с 4 табами
        // и тяжёлыми ChatListItem AndroidView'ами — лишний CPU. IME-inset потребляется
        // в ChatDetail (это единственный экран с EditText'ом), там и поднимается панель.
    ) {
        content()
    }
}
