package com.example.template.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import java.util.concurrent.ConcurrentHashMap

interface BitmapCache {
    /**
     * Бампается каждый раз, когда в кэш попадает новая запись. Compose-потребители читают
     * `.value` в своём scope и автоматически перерисовываются, когда фоновый прогрев или
     * lazy-load докинул битмап. Без этого асинхронная загрузка не доезжала бы до UI —
     * списки чатов так и остались бы на initials до следующей независимой рекомпозиции.
     */
    val version: State<Int>

    fun get(path: String?): Bitmap?
}

object NoBitmapCache : BitmapCache {
    override val version: State<Int> = mutableIntStateOf(0)
    override fun get(path: String?): Bitmap? = null
}

class AssetBitmapCache(private val context: Context) : BitmapCache {
    // ConcurrentHashMap — get() вызывается и из main (ChatListHost/ChatDetailScreen),
    // и из фонового прогрева в AppContainer.
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val _version = mutableIntStateOf(0)
    override val version: State<Int> = _version

    override fun get(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        cache[path]?.let { return it }
        val loaded = runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        // putIfAbsent: если параллельный поток успел положить тот же ключ — оставляем его
        // битмап, а свой отдаём в утиль (GC). Избегаем сценария «два бимапа на один ключ».
        val winner = cache.putIfAbsent(path, loaded) ?: loaded
        if (winner === loaded) _version.value += 1
        return winner
    }
}

val LocalBitmapCache = compositionLocalOf<BitmapCache> { NoBitmapCache }
