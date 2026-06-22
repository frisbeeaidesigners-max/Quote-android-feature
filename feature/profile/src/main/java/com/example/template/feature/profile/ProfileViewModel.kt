package com.example.template.feature.profile

import androidx.lifecycle.ViewModel
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.CurrentUser
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel(repository: MessengerRepository) : ViewModel() {
    val user: StateFlow<CurrentUser> = repository.currentUser
}
