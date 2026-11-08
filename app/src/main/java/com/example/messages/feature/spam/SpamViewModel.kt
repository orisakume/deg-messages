package com.example.messages.feature.spam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.core.database.BlockedNumberEntity
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SpamViewModel @Inject constructor(private val repository: SmsRepository) : ViewModel() {
    val conversations = repository.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blocked: StateFlow<List<BlockedNumberEntity>> = repository.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun restore(threadId: Long) = viewModelScope.launch { repository.setSpam(threadId, false) }
    fun delete(threadId: Long) = viewModelScope.launch { repository.deleteThreads(listOf(threadId)) }
    fun block(number: String) = viewModelScope.launch { repository.blockNumber(number) }
    fun unblock(number: String) = viewModelScope.launch { repository.unblockNumber(number) }
}
