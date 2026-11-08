package com.example.messages.feature.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messages.core.database.OtpRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OtpBadgeViewModel @Inject constructor(dao: OtpRecordDao) : ViewModel() {
    val count = combine(dao.observeAll(), otpClock()) { rows, now -> rows.visibleAt(now).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

}
