package com.example.template.feature.chatdetail

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.NavRoute
import com.example.template.core.ui.hosts.VoicePlaybackController

// Legacy: переход на ChatDetail сейчас не через NavHost, а state-driven overlay в MainActivity
// (см. CLAUDE.md «Производительность» / «ChatDetail — state-driven overlay»). Файл оставлен на
// случай возврата к NavHost — поэтому конструктор VM сюда тоже надо тащить (voiceController).
fun NavGraphBuilder.chatDetailScreen(
    repository: MessengerRepository,
    voiceController: VoicePlaybackController,
    onBack: () -> Unit,
) {
    composable(NavRoute.ChatDetail.PATTERN) { entry ->
        val chatId = entry.arguments?.getString(NavRoute.ChatDetail.ARG_CHAT_ID)!!
        val vm = remember(chatId) { ChatDetailViewModel(chatId, repository, voiceController) }
        ChatDetailScreen(viewModel = vm, onBack = onBack)
    }
}
