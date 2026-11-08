package com.example.messages.core.database

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** App-owned metadata database. Feature entities are introduced with their milestones. */
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "conversation_metadata")
data class ConversationMetadataEntity(
    @PrimaryKey val threadId: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val spam: Boolean = false,
    val textScale: Float = 1f,
)

@Entity(tableName = "message_metadata")
data class MessageMetadataEntity(
    @PrimaryKey val messageId: Long,
    val pinned: Boolean = false,
)

@Entity(tableName = "sms_send_tracking")
data class SmsSendTrackingEntity(
    @PrimaryKey val messageId: Long,
    val totalParts: Int,
    val completedParts: Int = 0,
    val hasFailure: Boolean = false,
)

@Entity(tableName = "otp_records")
data class OtpRecordEntity(
    @PrimaryKey val messageId: Long,
    val code: String,
    val sender: String,
    val detectedAt: Long,
    val expiresAt: Long? = null,
    val dismissed: Boolean = false,
    val copyCount: Int = 0,
) {
    companion object {
        const val EXPIRATION_MS = 24L * 60 * 60 * 1000
        const val MAX_COPIES = 4
    }
}

@Entity(tableName = "deleted_items", indices = [Index(value = ["originalMessageId"], unique = true)])
data class DeletedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMessageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    val status: Int,
    val subscriptionId: Int,
    val deletedAt: Long,
    val expiresAt: Long,
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val number: String,
    val createdAt: Long,
)

@Dao
interface DeletedItemDao {
    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<DeletedItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<DeletedItemEntity>)
    @Query("DELETE FROM deleted_items WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
    @Query("DELETE FROM deleted_items WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)
    @Query("SELECT * FROM deleted_items WHERE expiresAt <= :now")
    suspend fun expired(now: Long): List<DeletedItemEntity>
    @Query("DELETE FROM deleted_items")
    suspend fun clear()
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number)")
    suspend fun isBlocked(number: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(row: BlockedNumberEntity)
    @Query("DELETE FROM blocked_numbers WHERE number = :number")
    suspend fun unblock(number: String)
}

@Dao
interface OtpRecordDao {
    @Query("SELECT * FROM otp_records WHERE dismissed = 0 ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<OtpRecordEntity>>
    @Query("SELECT * FROM otp_records WHERE messageId = :messageId")
    suspend fun get(messageId: Long): OtpRecordEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: OtpRecordEntity)
    @Query("DELETE FROM otp_records WHERE messageId IN (:messageIds)")
    suspend fun delete(messageIds: List<Long>)
    @Query("DELETE FROM otp_records WHERE COALESCE(expiresAt, detectedAt + 86400000) <= :now")
    suspend fun deleteExpired(now: Long)
    @Query("UPDATE otp_records SET copyCount = copyCount + 1 WHERE messageId = :messageId")
    suspend fun incrementCopyCount(messageId: Long): Int
    @Query("DELETE FROM otp_records WHERE detectedAt < :cutoff")
    suspend fun deleteBefore(cutoff: Long)
    @Query("UPDATE otp_records SET dismissed = 1 WHERE messageId = :messageId")
    suspend fun dismiss(messageId: Long)
    @Query("UPDATE otp_records SET code = :code WHERE messageId = :messageId")
    suspend fun correct(messageId: Long, code: String)
    @Query("UPDATE otp_records SET messageId = :newId WHERE messageId = :oldId")
    suspend fun retarget(oldId: Long, newId: Long)
}

@Dao
interface ConversationMetadataDao {
    @Query("SELECT * FROM conversation_metadata")
    fun observeAll(): Flow<List<ConversationMetadataEntity>>

    @Query("SELECT * FROM conversation_metadata WHERE threadId = :threadId")
    suspend fun get(threadId: Long): ConversationMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: ConversationMetadataEntity)

    @Query("DELETE FROM conversation_metadata WHERE threadId IN (:threadIds)")
    suspend fun delete(threadIds: List<Long>)
}

@Dao
interface MessageMetadataDao {
    @Query("SELECT * FROM message_metadata")
    fun observeAll(): Flow<List<MessageMetadataEntity>>

    @Query("SELECT * FROM message_metadata WHERE messageId = :messageId")
    suspend fun get(messageId: Long): MessageMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: MessageMetadataEntity)

    @Query("DELETE FROM message_metadata WHERE messageId IN (:messageIds)")
    suspend fun delete(messageIds: List<Long>)
}

@Dao
abstract class SmsSendTrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: SmsSendTrackingEntity)

    @Query("SELECT * FROM sms_send_tracking WHERE messageId = :messageId")
    abstract suspend fun get(messageId: Long): SmsSendTrackingEntity?

    @Query("DELETE FROM sms_send_tracking WHERE messageId = :messageId")
    abstract suspend fun delete(messageId: Long)

    @Transaction
    open suspend fun recordPart(messageId: Long, succeeded: Boolean): SmsSendTrackingEntity? {
        val current = get(messageId) ?: return null
        val updated = current.copy(
            completedParts = current.completedParts + 1,
            hasFailure = current.hasFailure || !succeeded,
        )
        upsert(updated)
        return updated
    }
}

@Database(
    entities = [AppMetadataEntity::class, ConversationMetadataEntity::class, MessageMetadataEntity::class, SmsSendTrackingEntity::class, OtpRecordEntity::class, DeletedItemEntity::class, BlockedNumberEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun conversationMetadataDao(): ConversationMetadataDao
    abstract fun messageMetadataDao(): MessageMetadataDao
    abstract fun smsSendTrackingDao(): SmsSendTrackingDao
    abstract fun otpRecordDao(): OtpRecordDao
    abstract fun deletedItemDao(): DeletedItemDao
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversation_metadata (threadId INTEGER NOT NULL PRIMARY KEY, pinned INTEGER NOT NULL, archived INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS message_metadata (messageId INTEGER NOT NULL PRIMARY KEY, pinned INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sms_send_tracking (messageId INTEGER NOT NULL PRIMARY KEY, totalParts INTEGER NOT NULL, completedParts INTEGER NOT NULL, hasFailure INTEGER NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation_metadata ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS otp_records (messageId INTEGER NOT NULL PRIMARY KEY, code TEXT NOT NULL, sender TEXT NOT NULL, detectedAt INTEGER NOT NULL, expiresAt INTEGER, dismissed INTEGER NOT NULL DEFAULT 0)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation_metadata ADD COLUMN spam INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversation_metadata ADD COLUMN textScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, originalMessageId INTEGER NOT NULL, threadId INTEGER NOT NULL, address TEXT NOT NULL, body TEXT NOT NULL, date INTEGER NOT NULL, read INTEGER NOT NULL, type INTEGER NOT NULL, status INTEGER NOT NULL, subscriptionId INTEGER NOT NULL, deletedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_deleted_items_originalMessageId ON deleted_items(originalMessageId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS blocked_numbers (number TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE otp_records ADD COLUMN copyCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE otp_records SET expiresAt = MIN(COALESCE(expiresAt, detectedAt + 86400000), detectedAt + 86400000)")
            }
        }
    }
}
