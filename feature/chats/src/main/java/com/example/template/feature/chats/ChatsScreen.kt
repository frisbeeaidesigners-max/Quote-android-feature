package com.example.template.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.components.headers.HeadersView
import com.example.template.core.ui.LocalThemeToggle
import com.example.template.core.ui.components.EmptyState
import com.example.template.core.ui.hosts.ChatListHost
import com.example.template.core.ui.hosts.HeaderHost

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onChatClick: (String) -> Unit,
    /** Слот между хедером и контентом (используется для AudioPanel из MainScaffold). */
    belowHeader: @Composable () -> Unit = {},
) {
    val chats by viewModel.chats.collectAsState()
    val toggleTheme = LocalThemeToggle.current
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Main(
                mode = HeadersView.HeaderConfig.Main.Mode.CHATS,
                title = "Чаты",
                showSearch = true,
                searchPlaceholder = "Глобальный поиск",
                onPlusClick = toggleTheme,
            ),
        )
        belowHeader()
        if (chats.isEmpty()) {
            EmptyState("Нет личных чатов")
        } else {
            ChatListHost(
                chats = chats,
                onChatClick = onChatClick,
                userLookup = viewModel::userById,
                personaLookup = viewModel::personaForUser,
                personaById = viewModel::personaById,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
