package com.example.template.feature.chatdetail.quotepicker

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSColors
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface02
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun QuoteModalLinkPopoverCard(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val isDark = LocalIsDark.current
    val items = listOf("Общая информация", "Главное сообщение")
    Box(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(appSurface02(isDark)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, label ->
                LinkPopoverMenuItem(
                    label = label,
                    isSelected = i == selectedIndex,
                    onClick = { onSelect(i) },
                )
                if (i < items.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = appBasic(isDark, 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkPopoverMenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val labelColor = appBasic(isDark, 0.9f)
    val successColor = remember(context) { Color(DSColors.success(context)) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                LinkPopoverDoneIcon(tint = successColor)
            }
        }
    }
}

@Composable
private fun LinkPopoverDoneIcon(tint: Color) {
    val ctx = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, "done", 24f) as? BitmapDrawable)?.bitmap
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
