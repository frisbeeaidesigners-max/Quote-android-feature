package com.example.template.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

fun NavController.navigateToChatDetail(chatId: String) {
    navigate(NavRoute.ChatDetail(chatId).route)
}

fun NavController.navigateToTab(tab: TabRoute) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
