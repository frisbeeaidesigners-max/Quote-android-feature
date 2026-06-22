package com.example.template.core.navigation

sealed class NavRoute(val route: String) {
    data object Main : NavRoute("main")
    data class ChatDetail(val chatId: String) : NavRoute("chat/$chatId") {
        companion object {
            const val PATTERN = "chat/{chatId}"
            const val ARG_CHAT_ID = "chatId"
        }
    }
}
