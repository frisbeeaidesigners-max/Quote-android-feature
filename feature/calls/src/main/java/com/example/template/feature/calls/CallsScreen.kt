package com.example.template.feature.calls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.toComposeTextStyle
import com.example.components.headers.HeadersView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.model.CallContext
import com.example.template.core.model.HistoryTarget
import com.example.template.core.ui.hosts.HeaderHost
import com.example.template.core.ui.rows.CallRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    brandCodename: String = "foxtrot",
    onStartCall: (CallContext) -> Unit = {},
) {
    val sections by viewModel.sections.collectAsState()
    val currentUser by viewModel.currentUserState().collectAsState()
    val isDark = LocalIsDark.current
    val brand = LocalAppBrand.current
    val screenBackground = Color(brand.backgroundBase(isDark))

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Main(
                mode = HeadersView.HeaderConfig.Main.Mode.CHATS,
                title = "Видеоконференции и звонки",
                showSearch = true,
                searchPlaceholder = "Поиск людей",
                // null → правый слот хедера пуст (в Figma кнопок справа от тайтла нет).
                onPlusClick = null,
            ),
        )
        // Action-плитки зафиксированы вне LazyColumn — при скролле списка остаются на месте.
        // top=8dp + bottomMargin=4dp у search-bar внутри HeaderHost дают 12dp визуального
        // зазора от строки поиска до плиток.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallActionTile(
                iconName = "video-call",
                label = "Новая встреча",
                onClick = {
                    val brandTitle = brandCodename.replaceFirstChar { it.uppercase() } + " Meet"
                    val target = HistoryTarget(
                        id = currentUser.id,
                        name = brandTitle,
                        avatar = currentUser.avatar,
                    )
                    onStartCall(CallContext.BrandMeet(title = brandTitle, historyTarget = target))
                },
                modifier = Modifier.weight(1f),
            )
            CallActionTile(
                iconName = "link-chain",
                label = "Создать ссылку",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Тот же низ, что у ChatListHost: 56dp pill BottomTabs + 24dp + 12dp воздуха.
            contentPadding = PaddingValues(bottom = 92.dp),
        ) {
            sections.forEach { section ->
                // stickyHeader: при скролле дата-сепаратор прилипает к верху списка, пока в
                // viewport не уедет следующий sticky-заголовок. Фон под заголовком закрашиваем
                // backgroundBase — иначе сквозь полупрозрачный appBasic просвечивают
                // строки звонков ниже.
                stickyHeader(key = "header-${section.label}") {
                    DateHeader(
                        label = section.label,
                        isDark = isDark,
                        background = screenBackground,
                    )
                }
                items(section.calls, key = { it.id }) { call ->
                    val persona = viewModel.personaForUser(call.counterpartId)
                    CallRow(
                        call = call,
                        persona = persona,
                        onClick = {},
                        onCallClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(label: String, isDark: Boolean, background: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .height(40.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = appBasic(isDark, 0.5f),
            style = DSTypography.subhead4M.toComposeTextStyle(),
        )
    }
}
