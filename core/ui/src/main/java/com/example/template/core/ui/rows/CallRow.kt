package com.example.template.core.ui.rows

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarColorScheme
import com.example.components.avatar.AvatarView
import com.example.components.button.ButtonType
import com.example.components.button.ButtonView
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.dsIconPainter
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.model.Call
import com.example.template.core.model.CallType
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appClickable
import com.example.template.core.ui.format.TimeFormatter
import com.example.template.core.ui.hosts.ButtonHost

/**
 * Строка в списке вкладки «Звонки» по макету Figma. P2P-only: counterpart — всегда один
 * человек, аватарка через [AvatarView] (40dp, IMAGE/INITIALS — без online-badge'а; status
 * на этом экране не отображаем). Цвет имени переключается на danger при Missed, иконка-
 * сабтайтл подбирается по [CallType], справа — время и круглая call-кнопка через
 * [ButtonHost] (XS, SECONDARY-фон basic08, content tint опускаем до basic55).
 */
@Composable
fun CallRow(
    call: Call,
    persona: Persona?,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    // subscribe на bump version'ы кэша — bitmap'ы аватарок прогреваются в фоне.
    @Suppress("UNUSED_VARIABLE")
    val cacheVersion = bitmapCache.version.value

    // SECONDARY дефолтом — basicColor08 fill, basicColor90 content. По Figma: фон basicColor04,
    // tint иконки basicColor55. Через DSColors.basicNN(context) — не годится, она читает system
    // night-mode (см. feedback_app_isdark_ds_colors); basicColorNN(isDark) на брендe — то что надо.
    val buttonColors = remember(brand, isDark) {
        brand.buttonColorScheme(ButtonType.SECONDARY, isDark).copy(
            filledBackground = brand.basicColor04(isDark),
            filledContentColor = brand.basicColor55(isDark),
        )
    }
    val avatarSchemesByIndex: Map<Int, AvatarColorScheme> = remember(brand, isDark) {
        val pairs = brand.avatarGradientPairs(isDark)
        val base = brand.avatarColorScheme(isDark)
        (pairs.indices).associateWith { i ->
            base.copy(
                initialsGradientTop = pairs[i].top,
                initialsGradientBottom = pairs[i].bottom,
            )
        }
    }

    // basic-серия — через appBasic(isDark, alpha), не DSColors.basicNN (та реагирует на system
    // night-mode, у нас тема переключается на app-уровне).
    val nameColor = if (call.type == CallType.Missed) Color(brand.dangerDefault())
                    else appBasic(isDark, 0.9f)
    val subtitleColor = appBasic(isDark, 0.5f)
    val timeColor = appBasic(isDark, 0.4f)

    val image: Bitmap? = bitmapCache.get(persona?.avatarAsset)
    val initials = persona?.initials ?: call.counterpartAvatar.initials.orEmpty()
    val gradientIndex = (persona?.gradientIndex ?: call.counterpartAvatar.gradientIndex)
        .coerceAtLeast(0)
        .let { it % avatarSchemesByIndex.size.coerceAtLeast(1) }
    val avatarScheme = avatarSchemesByIndex[gradientIndex] ?: avatarSchemesByIndex.values.first()
    val avatarType =
        if (image != null) AvatarView.AvatarViewType.IMAGE else AvatarView.AvatarViewType.INITIALS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .appClickable(onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            modifier = Modifier.size(40.dp),
            factory = { ctx -> AvatarView(ctx) },
            update = { v ->
                v.configure(
                    mode = AvatarView.AvatarMode.USER_GROUP,
                    type = avatarType,
                    size = AvatarView.AvatarSize.SIZE_40,
                    text = initials,
                    image = image,
                    colorScheme = avatarScheme,
                )
            },
        )

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = call.counterpartName,
                color = nameColor,
                style = DSTypography.body3M.toComposeTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconPainter = dsIconPainter(callTypeIcon(call.type), sizeDp = 16f)
                if (iconPainter != null) {
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(subtitleColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = subtitleText(call),
                    color = subtitleColor,
                    style = DSTypography.body5R.toComposeTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = TimeFormatter.formatBubbleTime(call.timestamp),
            color = timeColor,
            style = DSTypography.body5R.toComposeTextStyle(),
        )

        Spacer(Modifier.width(16.dp))

        ButtonHost(
            onClick = onCallClick,
            iconName = "call-filled",
            size = ButtonView.Size.XS,
            filled = true,
            colorScheme = buttonColors,
            modifier = Modifier.size(36.dp),
        )
    }
}

private fun callTypeIcon(type: CallType): String = when (type) {
    CallType.Incoming -> "incoming-call-filled"
    CallType.Outgoing -> "outgoing-call-filled"
    CallType.Missed -> "end-call-filled"
}

private fun subtitleText(call: Call): String {
    val durationLabel = call.durationMs?.let { formatDuration(it) }
    return when (call.type) {
        CallType.Incoming -> if (durationLabel != null) "Входящий ($durationLabel)" else "Входящий"
        CallType.Outgoing -> if (durationLabel != null) "Исходящий ($durationLabel)" else "Исходящий"
        CallType.Missed -> "Пропущенный"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMin = (durationMs / 60_000L).coerceAtLeast(0L).toInt()
    return "$totalMin мин"
}
