package com.example.template.feature.calls

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.callsScreen(repository: MessengerRepository) {
    composable(TabRoute.Calls.route) {
        val vm = remember { CallsViewModel(repository) }
        CallsScreen(viewModel = vm)
    }
}
