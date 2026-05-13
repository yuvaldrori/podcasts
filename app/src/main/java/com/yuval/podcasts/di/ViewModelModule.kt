package com.yuval.podcasts.di

import com.yuval.podcasts.ui.viewmodel.DefaultMessageDelegate
import com.yuval.podcasts.ui.viewmodel.MessageDelegate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class ViewModelModule {

    @Binds
    @ViewModelScoped
    abstract fun bindMessageDelegate(
        defaultMessageDelegate: DefaultMessageDelegate
    ): MessageDelegate
}
