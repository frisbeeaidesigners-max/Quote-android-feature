package com.example.template.feature.profile

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.profileScreen(repository: MessengerRepository) {
    composable(TabRoute.Profile.route) {
        val vm = remember { ProfileViewModel(repository) }
        ProfileScreen(viewModel = vm)
    }
}
