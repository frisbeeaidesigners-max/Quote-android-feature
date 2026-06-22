package com.example.template.core.ui.hosts

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.components.bottomtabs.BottomTabs
import com.example.components.bottomtabs.BottomTabsView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

private val TABS_ORDER = listOf(
    BottomTabsView.Tab.MESSAGES,
    BottomTabsView.Tab.SPACES,
    BottomTabsView.Tab.CALLS,
    BottomTabsView.Tab.PROFILE,
)

@Composable
fun BottomTabsHost(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    profileAvatar: Bitmap? = null,
    badgeCounts: List<Int> = listOf(0, 0, 0, 0),
    showBadges: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val tabsColors = remember(brand, isDark) { brand.bottomTabsColorScheme(isDark) }
    val avatarColors = remember(brand, isDark) { brand.avatarColorScheme(isDark) }
    val badgeColors = remember(brand, isDark) { brand.badgeColorScheme(isDark) }

    val selectedTab = TABS_ORDER.getOrElse(selectedTabIndex) { TABS_ORDER[0] }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BottomTabs(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                val index = TABS_ORDER.indexOf(tab).coerceAtLeast(0)
                onTabSelected(index)
            },
            showBadges = showBadges,
            badgeCounts = badgeCounts,
            avatarBitmap = profileAvatar,
            colorScheme = tabsColors,
            avatarColorScheme = avatarColors,
            badgeColorScheme = badgeColors,
        )
    }
}
