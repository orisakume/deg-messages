package com.example.messages.feature.otp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.core.database.OtpRecordDao
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val dao: OtpRecordDao,
    private val repository: SmsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val records: StateFlow<List<OtpRecordEntity>> = combine(dao.observeAll(), otpClock()) { rows, now -> rows.visibleAt(now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Historical indexing is useful on the OTP screen, but should not compete with inbox startup.
        viewModelScope.launch { repository.indexHistoricalOtpMessages() }
    }

    fun copy(messageId: Long, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Verification code", code))
        viewModelScope.launch {
            if (dao.incrementCopyCount(messageId) > 0 && shouldRemoveOtpAfterCopy(dao.get(messageId)?.copyCount ?: OtpRecordEntity.MAX_COPIES)) {
                dao.delete(listOf(messageId))
            }
        }
    }

    fun dismiss(messageId: Long) = viewModelScope.launch { dao.dismiss(messageId) }
    fun correct(messageId: Long, code: String) = viewModelScope.launch {
        val normalized = code.trim().uppercase()
        if (normalized.length in 4..10 && normalized.any(Char::isDigit)) dao.correct(messageId, normalized)
    }
}
