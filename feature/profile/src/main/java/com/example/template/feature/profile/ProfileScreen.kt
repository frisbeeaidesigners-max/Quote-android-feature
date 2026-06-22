package com.example.template.feature.profile

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.components.button.ButtonType
import com.example.components.button.ButtonView
import com.example.components.designsystem.DSIcon
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.AppVersion
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.hosts.ButtonHost
import com.example.template.core.ui.theme.avatarColorSchemeForGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onClose: () -> Unit = {}, onEdit: () -> Unit = {}) {
    BackHandler(onBack = onClose)
    val user by viewModel.user.collectAsState()
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current

    val cacheVersion = bitmapCache.version.value
    val avatarBitmap: Bitmap? = remember(cacheVersion, user.avatar.imageAsset) {
        bitmapCache.get(user.avatar.imageAsset)
    }
    val avatarType = if (avatarBitmap != null) AvatarView.AvatarViewType.IMAGE else AvatarView.AvatarViewType.INITIALS
    val avatarScheme = remember(brand, isDark, user.avatar.gradientIndex) {
        avatarColorSchemeForGradient(brand, isDark, user.avatar.gradientIndex)
    }
    val pairs = remember(brand, isDark) { brand.avatarGradientPairs(isDark) }
    val avatarSaved = remember(brand, isDark) { brand.avatarColorScheme(isDark).savedGradientBottom }
    val groupBg = remember(brand, isDark) { Color(brand.basicColor04(isDark)) }
    val secondaryIcon = remember(brand, isDark) { Color(brand.basicColor55(isDark)) }
    // ButtonView color schemes — наследуемся от brand.buttonColorScheme и переопределяем фон/контент
    // по макету, чтобы кнопки физически были ButtonView (тот же ripple, padding'и, corner-radius).
    val headerBtnScheme = remember(brand, isDark) {
        brand.buttonColorScheme(ButtonType.SECONDARY, isDark)
    }
    val mainActionBtnScheme = remember(brand, isDark) {
        brand.buttonColorScheme(ButtonType.SECONDARY, isDark).copy(
            filledBackground = brand.basicColor06(isDark),
            filledContentColor = brand.basicColor90(isDark),
        )
    }
    fun palette(idx: Int): Color = Color(pairs[idx % pairs.size].bottom)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(brand.backgroundBase(isDark)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
        ) {
            ButtonHost(
                onClick = onClose,
                iconName = "close",
                size = ButtonView.Size.XS,
                filled = false,
                colorScheme = headerBtnScheme,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            ButtonHost(
                onClick = onEdit,
                iconName = "edit",
                size = ButtonView.Size.XS,
                filled = false,
                colorScheme = headerBtnScheme,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // User info: avatar + name + job title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AndroidView(
                    modifier = Modifier.size(160.dp),
                    factory = { AvatarView(it) },
                    update = { view ->
                        view.configure(
                            mode = AvatarView.AvatarMode.USER_GROUP,
                            type = avatarType,
                            size = AvatarView.AvatarSize.SIZE_160,
                            text = user.avatar.initials.orEmpty(),
                            image = avatarBitmap,
                            colorScheme = avatarScheme,
                        )
                    },
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = user.name,
                        style = DSTypography.title2B.toComposeTextStyle(),
                        color = appBasic(isDark, 0.9f),
                        textAlign = TextAlign.Center,
                    )
                    user.jobTitle?.let {
                        Text(
                            text = it,
                            style = DSTypography.body1R.toComposeTextStyle(),
                            color = appBasic(isDark, 0.5f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Main actions: Saved pill + Copy button — обе через ButtonView (size=L pill).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ButtonHost(
                    onClick = {},
                    text = "Сохранённые",
                    iconName = "saved",
                    size = ButtonView.Size.L,
                    filled = true,
                    colorScheme = mainActionBtnScheme,
                )
                ButtonHost(
                    onClick = {},
                    iconName = "copy-stroke",
                    size = ButtonView.Size.L,
                    filled = true,
                    colorScheme = mainActionBtnScheme,
                )
            }

            // Cards block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileCard(groupBg) {
                    user.email?.let { UserDetailRow("invite", it, isDark, iconTint = secondaryIcon) }
                    user.phone?.let { UserDetailRow("call", it, isDark, iconTint = secondaryIcon) }
                }

                Text(
                    text = "Настройки приложения",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = DSTypography.body4M.toComposeTextStyle(),
                    color = appBasic(isDark, 0.5f),
                )

                ProfileCard(groupBg) {
                    SettingsRow("unmute-stroke", palette(4), "Уведомления и звук", isDark)
                    SettingsRow("color", palette(1), "Оформление", isDark)
                    SettingsRow("cache-memory", palette(2), "Память", isDark)
                }
                ProfileCard(groupBg) {
                    SettingsRow("lock", palette(5), "Код-пароль", isDark)
                    SettingsRow("terms", palette(3), "Конфиденциальность", isDark)
                    SettingsRow("vpn-key", palette(7), "Двухфакторная аутентификация", isDark)
                    SettingsRow("desk-phone", palette(5), "Подключённые устройства", isDark)
                    SettingsRow("block", palette(10), "Чёрный список", isDark)
                    SettingsRow("earth", Color(avatarSaved), "Язык", isDark)
                }
                ProfileCard(groupBg) {
                    SettingsRow("help", palette(0), "Справка", isDark)
                    SettingsRow("info-outlined", palette(7), "Новости приложения", isDark)
                    SettingsRow(
                        iconName = "log-out",
                        iconBg = palette(6),
                        label = "Выход из приложения",
                        isDark = isDark,
                        textColor = Color(0xFFE06141),
                    )
                }

                ProfileCard(groupBg) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Версия приложения: $AppVersion",
                            style = DSTypography.body1R.toComposeTextStyle(),
                            color = appBasic(isDark, 0.5f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun UserDetailRow(
    iconName: String,
    text: String,
    isDark: Boolean,
    iconTint: Color,
    textAlpha: Float = 0.9f,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DsIcon(iconName, 24.dp, iconTint)
        Text(
            text = text,
            style = DSTypography.body1R.toComposeTextStyle(),
            color = appBasic(isDark, textAlpha),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsRow(
    iconName: String,
    iconBg: Color,
    label: String,
    isDark: Boolean,
    textColor: Color = appBasic(isDark, 0.9f),
) {
    // Ripple-цвет = basicColor55 на 10% alpha — то же значение, что у Secondary ButtonView'а
    // (см. ButtonView.kt: Color.argb(26, R, G, B) от unfilledContentColor = basicColor55).
    // Дефолтный material3 ripple был бы серым/чёрным с гораздо более высокой alpha — на светлом
    // фоне профиля это смотрелось слишком тёмно.
    val brand = LocalAppBrand.current
    val rippleColor = remember(brand, isDark) { Color(brand.basicColor55(isDark)) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = rememberRipple(color = rippleColor),
                onClick = {},
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            DsIcon(iconName, 20.dp, Color.White)
        }
        Text(
            text = label,
            style = DSTypography.body1R.toComposeTextStyle(),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun DsIcon(iconName: String, sizeDp: Dp, tint: Color) {
    val ctx = LocalContext.current
    var bitmap by remember(iconName, sizeDp) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(iconName, sizeDp) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, iconName, sizeDp.value) as? BitmapDrawable)?.bitmap
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Spacer(modifier = Modifier.size(sizeDp))
    }
}
