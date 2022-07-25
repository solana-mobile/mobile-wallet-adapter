package com.solanamobile.mobilewalletadapter.reactnative

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solanamobile.mobilewalletadapter.reactnative.JSONSerializationUtils.convertJsonToMap
import com.solanamobile.mobilewalletadapter.reactnative.JSONSerializationUtils.convertMapToJson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SolanaMobileWalletAdapterModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), CoroutineScope {

    data class SessionState(
        val client: MobileWalletAdapterClient,
        val localAssociation: LocalAssociationScenario,
    )

    override val coroutineContext =
        Dispatchers.IO + CoroutineName("SolanaMobileWalletAdapterModuleScope") + SupervisorJob()

    companion object {
        private const val ASSOCIATION_TIMEOUT_MS = 10000
        private const val CLIENT_TIMEOUT_MS = 90000

        // Used to ensure that you can't start more than one session at a time.
        private val mutex: Mutex = Mutex()
        private var sessionState: SessionState? = null
    }

    override fun getName(): String {
        return "SolanaMobileWalletAdapter"
    }

    @ReactMethod
    fun startSession(config: ReadableMap?, promise: Promise) = launch {
        mutex.lock()
        Log.d(name, "startSession with config $config")
        try {
            val uriPrefix = config?.getString("baseUri")?.let { Uri.parse(it) }
            val localAssociation = LocalAssociationScenario(
                CLIENT_TIMEOUT_MS,
            )
            val intent = LocalAssociationIntentCreator.createAssociationIntent(
                uriPrefix,
                localAssociation.port,
                localAssociation.session
            )
            currentActivity?.startActivityForResult(intent, 0)
                ?: throw NullPointerException("Could not find a current activity from which to launch a local association")
            val client =
                localAssociation.start().get(ASSOCIATION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            sessionState = SessionState(client, localAssociation)
            promise.resolve(true)
        } catch (e: ActivityNotFoundException) {
            Log.e(name, "Found no installed wallet that supports the mobile wallet protocol", e)
            cleanup()
            promise.reject("ERROR_WALLET_NOT_FOUND", e)
        } catch (e: TimeoutException) {
            Log.e(name, "Timed out waiting for local association to be ready", e)
            cleanup()
            promise.reject("Timed out waiting for local association to be ready", e)
        } catch (e: InterruptedException) {
            Log.w(name, "Interrupted while waiting for local association to be ready", e)
            cleanup()
            promise.reject(e)
        } catch (e: ExecutionException) {
            Log.e(name, "Failed establishing local association with wallet", e.cause)
            cleanup()
            promise.reject(e)
        } catch (e: Throwable) {
            Log.e(name, "Failed to start session", e)
            cleanup()
            promise.reject(e)
        }
    }

    @ReactMethod
    fun invoke(method: String, params: ReadableMap, promise: Promise) = sessionState?.let {
        Log.d(name, "invoke `$method` with params $params")
        try {
            val result = it.client.methodCall(
                method,
                convertMapToJson(params),
                CLIENT_TIMEOUT_MS
            ).get() as JSONObject
            promise.resolve(convertJsonToMap(result))
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is JsonRpc20Client.JsonRpc20RemoteException) {
                val userInfo = Arguments.createMap()
                userInfo.putInt("jsonRpcErrorCode", cause.code)
                promise.reject("JSON_RPC_ERROR", cause, userInfo)
            } else {
                throw e
            }
        } catch (e: Throwable) {
            Log.e(name, "Failed to invoke `$method` with params $params", e)
            promise.reject(e)
        }
    } ?: throw NullPointerException("Tried to invoke `$method` without an active session")

    @ReactMethod
    fun endSession(promise: Promise) = sessionState?.let {
        launch {
            Log.d(name, "endSession")
            try {
                it.localAssociation.close()
                    .get(ASSOCIATION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                cleanup()
                promise.resolve(true)
            } catch (e: TimeoutException) {
                Log.e(name, "Timed out waiting for local association to close", e)
                cleanup()
                promise.reject("Failed to end session", e)
            } catch (e: Throwable) {
                Log.e(name, "Failed to end session", e)
                cleanup()
                promise.reject("Failed to end session", e)
            }
        }
    } ?: throw NullPointerException("Tried to end a session without an active session")

    private fun cleanup() {
        sessionState = null
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }
}
