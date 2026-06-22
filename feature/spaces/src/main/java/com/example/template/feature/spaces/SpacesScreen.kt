package com.example.template.feature.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.components.headers.HeadersView
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.components.EmptyState
import com.example.template.core.ui.components.SpaceSwitcherSheet
import com.example.template.core.ui.hosts.ChatListHost
import com.example.template.core.ui.hosts.HeaderHost

@Composable
fun SpacesScreen(
    viewModel: SpacesViewModel,
    onChatClick: (String) -> Unit,
    /** Слот между хедером и контентом (используется для AudioPanel из MainScaffold). */
    belowHeader: @Composable () -> Unit = {},
) {
    val spaces by viewModel.spaces.collectAsState()
    val current by viewModel.currentSpace.collectAsState()
    val chats by viewModel.chats.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val bitmapCache = LocalBitmapCache.current
    val cacheVersion by bitmapCache.version
    val workspaceImage = remember(bitmapCache, cacheVersion) { bitmapCache.get("images/workspace.jpg") }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Main(
                mode = HeadersView.HeaderConfig.Main.Mode.SPACES,
                title = current?.name.orEmpty(),
                image = workspaceImage,
                showSearch = true,
                searchPlaceholder = "Глобальный поиск",
                onGroupClick = { showSheet = true },
                onPlusClick = { },
            ),
        )
        belowHeader()
        if (chats.isEmpty()) {
            EmptyState("В этом пространстве нет чатов")
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

    if (showSheet) {
        SpaceSwitcherSheet(
            spaces = spaces,
            onSelect = { viewModel.setSpace(it) },
            onDismiss = { showSheet = false },
        )
    }
}
