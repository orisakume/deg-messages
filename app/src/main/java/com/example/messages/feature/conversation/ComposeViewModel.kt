package com.example.messages.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.data.sms.ContactCandidate
import com.example.messages.data.sms.SimOption
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComposeViewModel @Inject constructor(private val repository: SmsRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val contactRefreshVersion = MutableStateFlow(0)
    val contacts: StateFlow<List<ContactCandidate>> = combine(query, contactRefreshVersion) { value, version -> value to version }
        .debounce(120)
        .mapLatest { (value, _) -> repository.searchContacts(value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun subscriptions(): List<SimOption> = repository.activeSubscriptions()
    suspend fun send(address: String, body: String, subscriptionId: Int): Long {
        repository.sendSms(address, body, subscriptionId)
        return repository.findOrCreateThread(address)
    }
    fun updateContactQuery(value: String) { query.value = value }
    fun refreshContacts() { contactRefreshVersion.value += 1 }
}
