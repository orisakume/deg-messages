package com.example.messages.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.data.sms.SmsConversation
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(private val repository: SmsRepository) : ViewModel() {
    val recycleBinEnabled: Boolean get() = repository.isRecycleBinEnabled()
    val conversations: StateFlow<List<SmsConversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markThreadsRead(threadIds: List<Long>, read: Boolean) = viewModelScope.launch {
        repository.markThreadsRead(threadIds, read)
    }

    fun setPinned(threadId: Long, pinned: Boolean) = viewModelScope.launch { repository.setPinned(threadId, pinned) }
    fun setArchived(threadId: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(threadId, archived) }
    fun setMuted(threadId: Long, muted: Boolean) = viewModelScope.launch { repository.setMuted(threadId, muted) }
    fun deleteThreads(threadIds: List<Long>) = viewModelScope.launch { repository.deleteThreads(threadIds) }
}
