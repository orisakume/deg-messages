package com.example.messages.feature.conversation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.core.database.ConversationMetadataEntity
import com.example.messages.data.sms.ContactDetails
import com.example.messages.core.database.MessageMetadataEntity
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.data.sms.SmsRecord
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryPage(val messages: List<SmsRecord> = emptyList(), val hasOlder: Boolean = false)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmsRepository,
) : ViewModel() {
    val threadId: Long = savedStateHandle.get<String>("threadId")?.toLongOrNull() ?: -1L
    val address: String = Uri.decode(savedStateHandle.get<String>("address").orEmpty())
    val recycleBinEnabled: Boolean get() = repository.isRecycleBinEnabled()
    private val mutableContactDetails = MutableStateFlow<ContactDetails?>(null)
    val contactDetails: StateFlow<ContactDetails?> = mutableContactDetails
    val conversationMetadata: StateFlow<ConversationMetadataEntity> = repository.observeConversationMetadata()
        .map { rows -> rows.firstOrNull { it.threadId == threadId } ?: ConversationMetadataEntity(threadId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationMetadataEntity(threadId))
    val textScale: StateFlow<Float> = conversationMetadata
        .map { it.textScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    private val historyLimit = MutableStateFlow(50)
    fun loadOlderMessages() { historyLimit.value += 50 }

    @OptIn(ExperimentalCoroutinesApi::class)
    val history = historyLimit.flatMapLatest { limit ->
        repository.observeThread(threadId, limit + 1).map { HistoryPage(it.takeLast(limit), it.size > limit) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryPage())
    val pinnedMessageIds: StateFlow<Set<Long>> = repository.observeMessageMetadata()
        .map { rows -> rows.filter(MessageMetadataEntity::pinned).map(MessageMetadataEntity::messageId).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch { if (threadId >= 0) repository.markThreadRead(threadId, true) }
        viewModelScope.launch { mutableContactDetails.value = repository.contactDetails(address) }
    }

    fun setMessagePinned(messageId: Long, pinned: Boolean) = viewModelScope.launch { repository.setMessagePinned(messageId, pinned) }
    suspend fun deleteMessages(messageIds: List<Long>): Int = repository.deleteMessages(messageIds)
    suspend fun latestMessageDate(): Long? = repository.loadThread(threadId, limit = 1).lastOrNull()?.date
    suspend fun expiredOtps() = repository.expiredOtpsInThread(threadId)
    suspend fun moveExpiredOtps(messages: List<OtpRecordEntity>): Int =
        messages.count { repository.deleteOtpMessage(it, requireRecovery = true) }
    suspend fun send(body: String, subscriptionId: Int, image: Uri? = null) =
        if (image == null) repository.sendSms(address, body, subscriptionId) else repository.sendAttachment(address, body, image, subscriptionId)
    suspend fun retry(message: SmsRecord) {
        val image = message.attachmentUri
        if (image == null) repository.retrySms(message.id)
        else repository.sendAttachment(address, message.body, Uri.parse(image), message.subscriptionId)
    }
    suspend fun setPinned(pinned: Boolean) = repository.setPinned(threadId, pinned)
    suspend fun setArchived(archived: Boolean) = repository.setArchived(threadId, archived)
    fun setTextScale(scale: Float) = viewModelScope.launch { repository.setTextScale(threadId, scale) }
    suspend fun setSpam(spam: Boolean) = repository.setSpam(threadId, spam)
    suspend fun blockSender() { repository.blockNumber(address); repository.setSpam(threadId, true) }
    suspend fun deleteConversation() = repository.deleteThreads(listOf(threadId))
    suspend fun subscriptions() = repository.activeSubscriptions()
}
