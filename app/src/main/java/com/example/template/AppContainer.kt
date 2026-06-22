package com.example.template

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.template.core.data.MessengerRepository
import com.example.template.core.data.MockRepositoryImpl
import com.example.template.core.ui.AssetBitmapCache
import com.example.template.core.ui.hosts.VoicePlaybackController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val repository: MessengerRepository = MockRepositoryImpl(appContext)
    val bitmapCache: AssetBitmapCache = AssetBitmapCache(appContext)
    // App-scoped — voice playback переживает закрытие чата и переключение табов.
    // AudioPanelHost рендерится и в ChatDetail (под чат-хедером), и в MainScaffold
    // (под главным хедером chat list / spaces) — оба подписаны на этот контроллер.
    val voicePlaybackController: VoicePlaybackController = VoicePlaybackController()

    init {
        // Прогрев в фоне: декод 14 PNG-аватарок раньше блокировал Application.onCreate и
        // отодвигал первый кадр на сотни мс. Списки чатов открываются с initials, а аватарки
        // подъезжают по мере готовности — кэш observable, потребители подписаны на version.
        Thread({
            repository.personas.value.forEach { persona ->
                persona.avatarAsset?.let { bitmapCache.get(it) }
            }
        }, "AssetBitmapCache-prewarm").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
            start()
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
