package com.solanamobile.rxclientsample.di

import android.content.Context
import android.content.SharedPreferences
import com.solana.mobilewalletadapter.clientlib.RxMobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@InstallIn(
    ViewModelComponent::class
)
@Module
class ClientSampleAppModule {

    @Provides
    fun providesSharedPrefs(@ApplicationContext ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences("sample_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    fun providesMobileWalletAdapter(): RxMobileWalletAdapter {
        return RxMobileWalletAdapter(Scenario.DEFAULT_CLIENT_TIMEOUT_MS, null)
    }
}