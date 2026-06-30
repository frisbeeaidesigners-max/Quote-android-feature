package com.example.template

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.template.core.data.MessengerRepository
import com.example.template.core.data.MockRepositoryImpl
import com.example.template.core.ui.AssetBitmapCache
import com.example.template.core.ui.QuotePickerStyle
import com.example.template.core.ui.hosts.VoicePlaybackController
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val repository: MessengerRepository = MockRepositoryImpl(appContext)
    val bitmapCache: AssetBitmapCache = AssetBitmapCache(appContext)
    // App-scoped — voice playback переживает закрытие чата и переключение табов.
    // AudioPanelHost рендерится и в ChatDetail (под чат-хедером), и в MainScaffold
    // (под главным хедером chat list / spaces) — оба подписаны на этот контроллер.
    val voicePlaybackController: VoicePlaybackController = VoicePlaybackController()

    /**
     * Стиль quote-picker'а. SegmentedControl в Profile переключает между FULLSCREEN
     * (inline overlay в Activity-окне) и MODAL_SWIPE/MODAL_STICKY (Compose Dialog).
     * In-process only — не персистится между запусками.
     */
    val quotePickerStyle: MutableStateFlow<QuotePickerStyle> =
        MutableStateFlow(QuotePickerStyle.FULLSCREEN)

    /**
     * Видимость link-вкладки в picker'е (Switch «Рендер ссылок» в Profile).
     * Default ON — на FULLSCREEN это значит V5-layout (с internal SegmentedControl «Ответ/Ссылка»),
     * на MODAL_SWIPE/MODAL_STICKY currently не влияет (placeholder — будут реализованы позже).
     */
    val linkRenderEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(true)

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
