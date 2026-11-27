package com.example.messages.feature.conversation

import android.net.Uri
import android.telephony.SubscriptionManager
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryPage(val messages: List<SmsRecord>, val hasOlder: Boolean)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmsRepository,
) : ViewModel() {
    val threadId: Long = savedStateHandle.get<String>("threadId")?.toLongOrNull() ?: -1L
    val address: String = Uri.decode(savedStateHandle.get<String>("address").orEmpty())
    val recycleBinEnabled: Boolean get() = repository.isRecycleBinEnabled()
    private val mutableContactDetails = MutableStateFlow<ContactDetails?>(null)
    val contactDetails: StateFlow<ContactDetails?> = mutableContactDetails
    private val subscriptionRefresh = MutableStateFlow(0L)
    private val mutableSimSelection = MutableStateFlow(ConversationSimSelection(rememberedId = repository.savedConversationSim(threadId)))
    internal val simSelection = mutableSimSelection.asStateFlow()
    val conversationMetadata: StateFlow<ConversationMetadataEntity> = repository.observeConversationMetadata(threadId)
        .map { it ?: ConversationMetadataEntity(threadId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationMetadataEntity(threadId))
    val textScale: StateFlow<Float> = conversationMetadata
        .map { it.textScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    private val historyLimit = MutableStateFlow(50)
    fun loadOlderMessages() { historyLimit.value += 50 }

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<HistoryPage?> = historyLimit.flatMapLatest { limit ->
        repository.observeThread(threadId, limit + 1).map { HistoryPage(it.takeLast(limit), it.size > limit) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pinnedMessageIds: StateFlow<Set<Long>> = repository.observeMessageMetadata()
        .map { rows -> rows.filter(MessageMetadataEntity::pinned).map(MessageMetadataEntity::messageId).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            if (threadId >= 0) {
                history.filterNotNull().first()
                repository.markThreadRead(threadId, true)
            }
        }
        viewModelScope.launch { mutableContactDetails.value = repository.contactDetails(address) }
        viewModelScope.launch {
            subscriptionRefresh.flatMapLatest { repository.observeSubscriptions() }.collect { active ->
                val before = mutableSimSelection.value
                val after = before.withActiveSubscriptions(active, SubscriptionManager.getDefaultSmsSubscriptionId(), repository.hasSimPermission())
                if (after.rememberedId != before.rememberedId) after.rememberedId?.let { repository.saveConversationSim(threadId, it) }
                mutableSimSelection.value = after
            }
        }
    }

    internal fun refreshSubscriptions() { subscriptionRefresh.update { it + 1 } }

    internal fun selectedSubscriptionId(): Int? = mutableSimSelection.value.selected?.subscriptionId

    internal fun switchSim() {
        val selection = mutableSimSelection.value
        val next = selection.next() ?: return
        repository.saveConversationSim(threadId, next.subscriptionId)
        mutableSimSelection.value = selection.copy(rememberedId = next.subscriptionId)
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
