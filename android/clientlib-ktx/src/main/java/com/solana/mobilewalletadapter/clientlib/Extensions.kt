package com.solana.mobilewalletadapter.clientlib

import android.util.Log
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun LocalAssociationScenario.begin(): MobileWalletAdapterClient {
    return try {
        start().get(MobileWalletAdapter.ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Log.w("LocalAssociationScenario", "Interrupted while waiting for local association to be ready")
        throw e
    } catch (e: TimeoutException) {
        Log.e("LocalAssociationScenario", "Timed out waiting for local association to be ready")
        throw e
    } catch (e: ExecutionException) {
        Log.e("LocalAssociationScenario", "Failed establishing local association with wallet", e.cause)
        throw e
    }
}

fun LocalAssociationScenario.end() {
    close().get(MobileWalletAdapter.ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}