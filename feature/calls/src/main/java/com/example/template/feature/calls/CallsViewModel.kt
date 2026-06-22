package com.example.template.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Call
import com.example.template.core.model.Persona
import com.example.template.core.ui.format.TimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class CallsViewModel(private val repository: MessengerRepository) : ViewModel() {

    /**
     * Звонки, сгруппированные по дню (новее → старее). Лейбл секции — «Сегодня» / «Вчера»
     * / «12 мая» через [TimeFormatter.formatDateSeparator]. Внутри секции порядок —
     * новее сверху.
     */
    val sections: StateFlow<List<DaySection>> = repository.calls
        .map { groupByDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, groupByDay(repository.calls.value))

    fun personaForUser(userId: String): Persona? = repository.personaForUser(userId)

    fun currentUserState() = repository.currentUser

    private fun groupByDay(calls: List<Call>): List<DaySection> {
        if (calls.isEmpty()) return emptyList()
        val sorted = calls.sortedByDescending { it.timestamp }
        val now = System.currentTimeMillis()
        return sorted
            .groupBy { dayKey(it.timestamp) }
            .map { (_, group) ->
                DaySection(
                    label = TimeFormatter.formatDateSeparator(group.first().timestamp, now),
                    calls = group,
                )
            }
    }

    private fun dayKey(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

data class DaySection(
    val label: String,
    val calls: List<Call>,
)
