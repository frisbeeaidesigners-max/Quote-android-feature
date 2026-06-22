package com.example.template.core.navigation

sealed class TabRoute(val route: String) {
    data object Chats   : TabRoute("tab/chats")
    data object Spaces  : TabRoute("tab/spaces")
    data object Calls   : TabRoute("tab/calls")
    data object Profile : TabRoute("tab/profile")

    companion object {
        val all: List<TabRoute> by lazy { listOf(Chats, Spaces, Calls, Profile) }
    }
}
