package com.example.template.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Chat
import com.example.template.core.model.Persona
import com.example.template.core.model.Space
import com.example.template.core.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SpacesViewModel(private val repository: MessengerRepository) : ViewModel() {
    val spaces: StateFlow<List<Space>> = repository.spaces

    val currentSpace: StateFlow<Space?> =
        repository.currentSpaceId
            .map { id -> repository.spaces.value.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<Chat>> =
        repository.currentSpaceId
            .flatMapLatest { id -> repository.spaceChats(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSpace(id: String) = repository.setCurrentSpace(id)

    fun userById(id: String): User? = repository.getUser(id)
    fun personaForUser(userId: String): Persona? = repository.personaForUser(userId)
    fun personaById(personaId: String): Persona? = repository.getPersona(personaId)
}
