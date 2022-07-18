package com.solanamobile.rxclientsample.di

import com.solana.mobilewalletadapter.clientlib.RxMobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@InstallIn(
    ViewModelComponent::class
)
@Module
class ClientSampleAppModule {

    @Provides
    fun providesMobileWalletAdapter(): RxMobileWalletAdapter {
        return RxMobileWalletAdapter(Scenario.DEFAULT_CLIENT_TIMEOUT_MS, null)
    }
}