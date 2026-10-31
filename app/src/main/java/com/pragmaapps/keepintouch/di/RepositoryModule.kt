package com.pragmaapps.keepintouch.di

import com.pragmaapps.keepintouch.data.postcall.PostCallConfirmationNotifierImpl
import com.pragmaapps.keepintouch.data.repository.CallEventRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.ContactRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.DataManagementRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.DigestReminderRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.LogRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.PendingMatchRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.ScheduleCompletionRepositoryImpl
import com.pragmaapps.keepintouch.data.repository.ScheduleRepositoryImpl
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.DataManagementRepository
import com.pragmaapps.keepintouch.domain.repository.DigestReminderRepository
import com.pragmaapps.keepintouch.domain.repository.LogRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.PostCallConfirmationNotifier
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindScheduleCompletionRepository(impl: ScheduleCompletionRepositoryImpl): ScheduleCompletionRepository

    @Binds
    @Singleton
    abstract fun bindCallEventRepository(impl: CallEventRepositoryImpl): CallEventRepository

    @Binds
    @Singleton
    abstract fun bindPendingMatchRepository(impl: PendingMatchRepositoryImpl): PendingMatchRepository

    @Binds
    @Singleton
    abstract fun bindDataManagementRepository(impl: DataManagementRepositoryImpl): DataManagementRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    @Binds
    @Singleton
    abstract fun bindDigestReminderRepository(impl: DigestReminderRepositoryImpl): DigestReminderRepository

    @Binds
    @Singleton
    abstract fun bindPostCallConfirmationNotifier(impl: PostCallConfirmationNotifierImpl): PostCallConfirmationNotifier
}
