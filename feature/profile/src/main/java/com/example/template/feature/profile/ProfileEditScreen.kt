package com.example.template.feature.profile

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.components.button.ButtonType
import com.example.components.button.ButtonView
import com.example.components.designsystem.DSIcon
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.components.input.InputField
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.hosts.ButtonHost
import com.example.template.core.ui.theme.avatarColorSchemeForGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileEditScreen(viewModel: ProfileViewModel, onClose: () -> Unit) {
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

    val basic90 = Color(brand.basicColor90(isDark))
    val basic55 = Color(brand.basicColor55(isDark))
    val basic50 = Color(brand.basicColor50(isDark))
    val basic06 = Color(brand.basicColor06(isDark))
    val inputScheme = remember(brand, isDark) { brand.inputColorScheme(isDark) }
    // ButtonView color schemes для всех pill-кнопок: back/save/change/add — наследуемся
    // от brand.buttonColorScheme. Это сохраняет ripple + corner-radius + padding'и из ButtonView.
    val secondaryScheme = remember(brand, isDark) {
        brand.buttonColorScheme(ButtonType.SECONDARY, isDark)
    }
    val primaryScheme = remember(brand, isDark) {
        brand.buttonColorScheme(ButtonType.PRIMARY, isDark)
    }

    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(brand.backgroundBase(isDark)))
            .statusBarsPadding()
            .navigationBarsPadding()
            // Тап вне инпутов/кнопок — снимаем focus → IME уезжает. detectTapGestures работает
            // только на unconsumed-тапах, поэтому клик по InputField/ButtonView (которые
            // забирают ACTION_DOWN в child-сlot'е) сюда не доходит — гасить IME будут только
            // тапы по пустым областям/тексту.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .verticalScroll(rememberScrollState()),
    ) {
        // Top header: back (XS icon-only secondary unfilled — content basic55) + Save (XS text-only
        // primary unfilled — accent text). Оба через ButtonView, ripple/padding/radius — оттуда.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
        ) {
            ButtonHost(
                onClick = onClose,
                iconName = "back-ios",
                size = ButtonView.Size.XS,
                filled = false,
                colorScheme = secondaryScheme,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            ButtonHost(
                onClick = onClose,
                text = "Сохранить",
                size = ButtonView.Size.XS,
                filled = false,
                colorScheme = primaryScheme,
                textStyle = DSTypography.body3M,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Sub-header: big title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Редактировать профиль",
                style = DSTypography.title2B.toComposeTextStyle(),
                color = basic90,
                maxLines = 1,
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---- Личные данные ----
            Text(
                text = "Личные данные",
                style = DSTypography.title7R.toComposeTextStyle(),
                color = basic90,
                modifier = Modifier.fillMaxWidth(),
            )

            // info: avatar row + 4 fields
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // avatar + change button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AndroidView(
                        modifier = Modifier.size(80.dp),
                        factory = { AvatarView(it) },
                        update = { view ->
                            view.configure(
                                mode = AvatarView.AvatarMode.USER_GROUP,
                                type = avatarType,
                                size = AvatarView.AvatarSize.SIZE_80,
                                text = user.avatar.initials.orEmpty(),
                                image = avatarBitmap,
                                colorScheme = avatarScheme,
                            )
                        },
                    )
                    // S = 40dp pill, secondary filled — bg basicColor08 + content basicColor90.
                    ButtonHost(
                        onClick = {},
                        text = "Изменить",
                        size = ButtonView.Size.S,
                        filled = true,
                        colorScheme = secondaryScheme,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Local edit-state на каждое поле. Key'имся на user.id, чтобы при смене юзера
                    // (теоретически) состояния пересоздались, но локальные правки одного юзера сохранялись.
                    var firstName by remember(user.id) { mutableStateOf(user.firstName.orEmpty()) }
                    var lastName by remember(user.id) { mutableStateOf(user.lastName.orEmpty()) }
                    var middleName by remember(user.id) { mutableStateOf(user.middleName.orEmpty()) }
                    var jobTitle by remember(user.id) { mutableStateOf(user.jobTitle.orEmpty()) }
                    InputField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = "Имя",
                        colorScheme = inputScheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InputField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = "Фамилия",
                        colorScheme = inputScheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InputField(
                        value = middleName,
                        onValueChange = { middleName = it },
                        label = "Отчество",
                        colorScheme = inputScheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InputField(
                        value = jobTitle,
                        onValueChange = { jobTitle = it },
                        label = "Должность",
                        colorScheme = inputScheme,
                        hint = "Укажите вашу должность в компании",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- Контактные данные ----
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Контактные данные",
                        style = DSTypography.title7R.toComposeTextStyle(),
                        color = basic90,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "Другие участники видят эту информацию в вашем профиле.",
                    style = DSTypography.body5R.toComposeTextStyle(),
                    color = basic50,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Email block ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactSectionHeader(iconName = "invite", title = "Эл. почта", iconTint = basic55, titleColor = basic90)
                var email by remember(user.id) { mutableStateOf(user.email.orEmpty()) }
                InputField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Введите эл. почту",
                    colorScheme = inputScheme,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth(),
                )
                ButtonHost(
                    onClick = {},
                    text = "Добавить еще почту",
                    iconName = "plus",
                    size = ButtonView.Size.S,
                    filled = false,
                    colorScheme = primaryScheme,
                    textStyle = DSTypography.body1R,
                )
            }

            // Divider
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(basic06))

            // ---- Phone block ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactSectionHeader(iconName = "call", title = "Мобильный номер", iconTint = basic55, titleColor = basic90)
                InputField(
                    value = user.phone.orEmpty(),
                    onValueChange = {},
                    label = "Номер телефона",
                    colorScheme = inputScheme,
                    isEnabled = false,
                    hint = "Вы зарегистрированы под этим номером, поэтому его нельзя редактировать. Скрыть этот номер можно в настройках Конфиденциальности.",
                    modifier = Modifier.fillMaxWidth(),
                )
                ButtonHost(
                    onClick = {},
                    text = "Добавить еще номер телефона",
                    iconName = "plus",
                    size = ButtonView.Size.S,
                    filled = false,
                    colorScheme = primaryScheme,
                    textStyle = DSTypography.body1R,
                )
            }
        }
    }
}

@Composable
private fun ContactSectionHeader(iconName: String, title: String, iconTint: Color, titleColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditIcon(iconName, 24.dp, iconTint)
        Text(
            text = title,
            style = DSTypography.body1R.toComposeTextStyle(),
            color = titleColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditIcon(iconName: String, sizeDp: Dp, tint: Color) {
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
