package com.example.messages.feature.otp

import com.example.messages.core.database.OtpRecordEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun otpClock(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(30_000L)
    }
}

internal fun OtpRecordEntity.isExpiredAt(now: Long): Boolean =
    (expiresAt ?: (detectedAt + OtpRecordEntity.EXPIRATION_MS)) <= now

internal fun List<OtpRecordEntity>.visibleAt(now: Long): List<OtpRecordEntity> =
    filterNot { it.isExpiredAt(now) }

internal fun shouldRemoveOtpAfterCopy(copyCount: Int): Boolean = copyCount >= OtpRecordEntity.MAX_COPIES
