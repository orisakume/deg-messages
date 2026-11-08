package com.example.messages.core.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.messages.core.database.MessagesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MessagesDatabase =
        Room.databaseBuilder(context, MessagesDatabase::class.java, "messages-metadata.db")
            .addMigrations(MessagesDatabase.MIGRATION_1_2, MessagesDatabase.MIGRATION_2_3, MessagesDatabase.MIGRATION_3_4, MessagesDatabase.MIGRATION_4_5, MessagesDatabase.MIGRATION_5_6)
            .build()

    @Provides
    fun provideConversationMetadataDao(database: MessagesDatabase) = database.conversationMetadataDao()

    @Provides
    fun provideMessageMetadataDao(database: MessagesDatabase) = database.messageMetadataDao()

    @Provides
    fun provideSmsSendTrackingDao(database: MessagesDatabase) = database.smsSendTrackingDao()

    @Provides
    fun provideOtpRecordDao(database: MessagesDatabase) = database.otpRecordDao()

    @Provides
    fun provideDeletedItemDao(database: MessagesDatabase) = database.deletedItemDao()

    @Provides
    fun provideBlockedNumberDao(database: MessagesDatabase) = database.blockedNumberDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("settings.preferences_pb") },
        )
}
