package com.example.messages.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.data.sms.SmsConversation
import com.example.messages.data.sms.MessageSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class InboxViewModel @Inject constructor(private val repository: SmsRepository) : ViewModel() {
    val recycleBinEnabled: Boolean get() = repository.isRecycleBinEnabled()
    val conversations: StateFlow<List<SmsConversation>?> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val subscriptionRefresh = MutableStateFlow(0L)
    internal val subscriptions = subscriptionRefresh.flatMapLatest { repository.observeSubscriptions() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val query = MutableStateFlow<Pair<MessageSearch, Int?>>(MessageSearch() to null)
    val matches = query.debounce(200).flatMapLatest { (search, subscription) -> repository.observeSearch(search, subscription) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    internal fun search(value: MessageSearch, subscriptionId: Int?) { query.value = value to subscriptionId }
    internal fun refreshSubscriptions() { subscriptionRefresh.update { it + 1 } }
    internal suspend fun contactName(number: String): String = repository.contactDetails(number).displayName

    fun markThreadsRead(threadIds: List<Long>, read: Boolean) = viewModelScope.launch {
        repository.markThreadsRead(threadIds, read)
    }

    fun setPinned(threadId: Long, pinned: Boolean) = viewModelScope.launch { repository.setPinned(threadId, pinned) }
    fun setArchived(threadId: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(threadId, archived) }
    fun setMuted(threadId: Long, muted: Boolean) = viewModelScope.launch { repository.setMuted(threadId, muted) }
    fun deleteThreads(threadIds: List<Long>) = viewModelScope.launch { repository.deleteThreads(threadIds) }
}
