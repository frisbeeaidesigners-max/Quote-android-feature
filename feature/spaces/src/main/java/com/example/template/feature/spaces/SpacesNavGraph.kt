package com.example.template.feature.spaces

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.spacesScreen(
    repository: MessengerRepository,
    onChatClick: (String) -> Unit,
) {
    composable(TabRoute.Spaces.route) {
        val vm = remember { SpacesViewModel(repository) }
        SpacesScreen(viewModel = vm, onChatClick = onChatClick)
    }
}
