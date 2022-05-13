/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mobileWalletAdapterClientMutex = Mutex()

    suspend fun authorize(sender: StartActivityForResultSender) {
        localAssociateAndExecute(sender) { client ->
            try {
                val sem = Semaphore(1, 1);
                val future = client.authorizeAsync(Uri.parse("https://solana.com"),
                    Uri.parse("favicon.ico"),
                    "Solana",
                    setOf(PrivilegedMethod.SignTransaction)
                )
                future.notifyOnComplete { sem.release() }
                val result = try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause!!
                }
                Log.d(TAG, "Authorized: authToken=${result.authToken}, walletUriBase=${result.walletUriBase}")
            } catch (e: IOException) {
                Log.e(TAG, "IO error while sending authorize", e)
            } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
                if (e.code == ProtocolContract.ERROR_AUTHORIZATION_FAILED) {
                    Log.e(TAG, "Not authorized: ${e.message}");
                } else {
                    Log.e(TAG, "Remote exception for authorize: ${e.message}", e);
                }
            } catch (e: JsonRpc20Client.JsonRpc20Exception) {
                Log.e(TAG, "JSON-RPC client exception for authorize", e)
            } catch (e: TimeoutException) {
                Log.e(TAG, "Timed out while waiting for authorize result", e)
            }
        }
    }

    private suspend fun localAssociateAndExecute(
        sender: StartActivityForResultSender,
        uriPrefix: Uri? = null,
        action: (MobileWalletAdapterClient) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            mobileWalletAdapterClientMutex.withLock {
                val semConnectedOrFailed = Semaphore(1, 1)
                val semTerminated = Semaphore(1, 1)
                var mobileWalletAdapterClient: MobileWalletAdapterClient? = null
                val scenarioCallbacks = object : Scenario.Callbacks {
                    override fun onScenarioReady(client: MobileWalletAdapterClient) {
                        mobileWalletAdapterClient = client
                        semConnectedOrFailed.release()
                    }

                    override fun onScenarioError() = semConnectedOrFailed.release()
                    override fun onScenarioComplete() = semConnectedOrFailed.release()
                    override fun onScenarioTeardownComplete() = semTerminated.release()
                }

                val localAssociation = LocalAssociationScenario(
                    getApplication<Application>().mainLooper,
                    scenarioCallbacks,
                    uriPrefix
                )
                sender.startActivityForResult(localAssociation.createAssociationIntent())

                localAssociation.start()
                try {
                    withTimeout(ASSOCIATION_TIMEOUT_MS) {
                        semConnectedOrFailed.acquire()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Timed out waiting for local association to be ready", e)
                    // Let garbage collection deal with cleanup; if we timed out starting, we might
                    // hang if we attempt to close.
                    return@withLock
                }

                mobileWalletAdapterClient?.let { client -> action(client) }
                    ?: Log.e(TAG, "Local association not ready; skip requested action")

                localAssociation.close()
                try {
                    withTimeout(ASSOCIATION_TIMEOUT_MS) {
                        semTerminated.acquire()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Timed out waiting for local association to close", e)
                    return@withLock
                }
            }
        }
    }

    interface StartActivityForResultSender {
        fun startActivityForResult(intent: Intent)
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}