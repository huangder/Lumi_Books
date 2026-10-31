package com.huangder.lumibooks.edition

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OssAccess @Inject constructor() : PremiumAccess {
    override val unlocked: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    override fun isUnlocked() = true
    override fun canAddRemoteSource(count: Int) = true
    override fun start() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OssAccessModule {
    @Binds abstract fun access(implementation: OssAccess): PremiumAccess
}
