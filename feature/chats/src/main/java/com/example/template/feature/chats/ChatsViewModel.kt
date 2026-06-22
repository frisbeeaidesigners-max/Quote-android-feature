package com.example.template.feature.chats

import androidx.lifecycle.ViewModel
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Chat
import com.example.template.core.model.Persona
import com.example.template.core.model.User
import kotlinx.coroutines.flow.StateFlow

class ChatsViewModel(private val repository: MessengerRepository) : ViewModel() {
    val chats: StateFlow<List<Chat>> = repository.p2pChats
    fun userById(id: String): User? = repository.getUser(id)
    fun personaForUser(userId: String): Persona? = repository.personaForUser(userId)
    fun personaById(personaId: String): Persona? = repository.getPersona(personaId)
}
