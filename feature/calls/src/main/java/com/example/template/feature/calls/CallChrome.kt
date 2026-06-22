package com.example.template.feature.calls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.dsIconPainter
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.appClickable

/**
 * Общий header для всех call-экранов (Group/BrandMeet InCall, P2P InCall).
 *
 * Геометрия одинаковая: `statusBarsPadding()` + `padding(top=12dp, horizontal=8dp)` + height 48dp.
 * Center-колонка title+timer показывается, только когда хоть одно из них непусто; иначе там
 * Spacer.weight(1f) и иконки уезжают на края.
 *
 * **Зеркало правого кластера** (`Spacer(44dp)` после Collapse) включается только когда
 * [showFlip]=true: тогда справа flip(36)+gap(8)+share(36)=80dp, слева collapse(36)+spacer(44)=80dp,
 * центр-колонка попадает на абсолютную середину. Когда `showFlip=false` — справа только share,
 * слева только collapse, layout уже симметричен и mirror не нужен.
 *
 * Иконки красятся [iconTint] — для P2P caller подсовывает `white50` когда обе камеры выкл
 * (низкоконтрастный фон с аватаркой), `white100` когда любое видео активно.
 */
@Composable
internal fun CallHeader(
    onCollapse: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    timer: String? = null,
    showFlip: Boolean = true,
    onFlip: () -> Unit = {},
    iconTint: Color = Color.White,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, start = 8.dp, end = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(iconName = "collapse", onClick = onCollapse, tint = iconTint)
        if (showFlip) Spacer(Modifier.width(44.dp))

        val hasCenter = !title.isNullOrEmpty() || !timer.isNullOrEmpty()
        if (hasCenter) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!title.isNullOrEmpty()) {
                        Text(
                            text = title,
                            color = Color.White.copy(alpha = 0.9f),
                            style = DSTypography.body3M.toComposeTextStyle(),
                            maxLines = 1,
                        )
                    }
                    if (!timer.isNullOrEmpty()) {
                        Text(
                            text = timer,
                            color = Color.White.copy(alpha = 0.9f),
                            style = DSTypography.body5R.toComposeTextStyle(),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (showFlip) {
            CircleIconButton(iconName = "flip", onClick = onFlip, tint = iconTint)
            Spacer(Modifier.width(8.dp))
        }
        CircleIconButton(iconName = "share-android", onClick = onShare, tint = iconTint)
    }
}

/**
 * Нижний ряд из 5 кнопок (Camera / Speaker / Mic / Dots / End-call). Идентичный layout у всех
 * call-экранов. Caller должен передать [modifier] с `Modifier.align(Alignment.BottomCenter)`,
 * т.к. эта функция предполагается к вызову внутри BoxScope.
 *
 * Dots всегда `isActive = true` (приглушённая «ON»-стиль). Speaker рисуется иконкой `volume`
 * в обоих состояниях — в icons-library нет ic-speaker-muted.
 */
@Composable
internal fun CallBottomBar(
    toggles: CallToggles,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleMic: () -> Unit,
    onEndCall: () -> Unit,
    danger: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(
            iconName = if (toggles.camera) "video-on-call-filled" else "video-off-call-filled",
            isActive = toggles.camera,
            onClick = onToggleCamera,
        )
        ToggleButton(
            iconName = "volume",
            isActive = toggles.speaker,
            onClick = onToggleSpeaker,
        )
        ToggleButton(
            iconName = if (toggles.mic) "microphone-on" else "microphone-off",
            isActive = toggles.mic,
            onClick = onToggleMic,
        )
        ToggleButton(iconName = "dots", isActive = true, onClick = { /* no-op V1 */ })
        EndCallButton(background = danger, onClick = onEndCall)
    }
}

@Composable
internal fun CircleIconButton(
    iconName: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(1000.dp))
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 24f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        }
    }
}

/**
 * Toggle-кнопка нижнего ряда (Camera/Speaker/Mic/Dots).
 *
 * - **OFF** (выкл): bg white 80% + icon black 95% — яркий call-to-action state.
 * - **ON** (вкл): bg white 10% + icon white 90% — приглушённый, не отвлекает.
 */
@Composable
internal fun ToggleButton(
    iconName: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bgAlpha = if (isActive) 0.1f else 0.8f
    val iconColor = if (isActive) {
        Color.White.copy(alpha = 0.9f)
    } else {
        Color.Black.copy(alpha = 0.95f)
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 32f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(iconColor),
            )
        }
    }
}

@Composable
internal fun EndCallButton(background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter("call-ended-filled", sizeDp = 32f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

/** MM:SS форматирование длительности звонка для header'а. */
internal fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
