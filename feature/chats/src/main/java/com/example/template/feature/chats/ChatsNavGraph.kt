package com.example.template.feature.chats

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.chatsScreen(
    repository: MessengerRepository,
    onChatClick: (String) -> Unit,
) {
    composable(TabRoute.Chats.route) {
        val vm = remember { ChatsViewModel(repository) }
        ChatsScreen(viewModel = vm, onChatClick = onChatClick)
    }
}
