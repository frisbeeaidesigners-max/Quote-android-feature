package com.example.template.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.components.designsystem.DSBrand

val AppBrand: DSBrand = DSBrand.byCodename(BuildConfig.BRAND_CODENAME)
val AppBrandCodename: String = BuildConfig.BRAND_CODENAME
val AppVersion: String = BuildConfig.APP_VERSION

val LocalAppBrand = staticCompositionLocalOf<DSBrand> { error("AppBrand not provided") }
// isDark меняется в рантайме (toggle темы) → compositionLocalOf, не static.
val LocalIsDark = compositionLocalOf<Boolean> { error("IsDark not provided") }
