package com.solanamobile.mobilewalletadapter.reactnative

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.common.protocol.SessionProperties.ProtocolVersion
import com.solanamobile.mobilewalletadapter.reactnative.JSONSerializationUtils.convertJsonToMap
import com.solanamobile.mobilewalletadapter.reactnative.JSONSerializationUtils.convertMapToJson
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

class SolanaMobileWalletAdapterModule(reactContext: ReactApplicationContext) :
    SolanaMobileWalletAdapterSpec(reactContext), CoroutineScope {

    data class SessionState(
        val client: MobileWalletAdapterClient,
        val localAssociation: LocalAssociationScenario,
    )

    override val coroutineContext =
        Dispatchers.IO + CoroutineName("SolanaMobileWalletAdapterModuleScope") + SupervisorJob()

    companion object {
        const val NAME = "SolanaMobileWalletAdapter"
        private const val TAG = "SolanaMobileWalletAdapterModule"
        private const val ASSOCIATION_TIMEOUT_MS = 10000
        private const val CLIENT_TIMEOUT_MS = 90000
        private const val REQUEST_LOCAL_ASSOCIATION = 0
    }

    // Used to ensure that you can't start more than one session at a time.
    private val mutex: Mutex = Mutex()
    private var sessionState: SessionState? = null
    private var associationResultCallback: ((Int) -> Unit)? = null

    private val mActivityEventListener: ActivityEventListener =
        object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                if (requestCode == REQUEST_LOCAL_ASSOCIATION)
                    associationResultCallback?.invoke(resultCode)
            }
        }

    private val sessionBackgroundTaskConfig
        get() = HeadlessJsTaskConfig(
            "SolanaMobileWalletAdapterSessionBackgroundTask",
            Arguments.createMap(),
            0,
            true
        )

    init {
        reactContext.addActivityEventListener(mActivityEventListener)
    }

    override fun getName(): String {
        return NAME
    }

    @ReactMethod
    override fun startSession(config: ReadableMap?, promise: Promise): Unit {
        launch {
            mutex.lock()
            Log.d(TAG, "startSession with config $config")
            var sessionTaskId: Int? = null
            val headlessJsTaskContext = HeadlessJsTaskContext.getInstance(reactApplicationContext)
            val finishHeadlessTask = { taskId: Int? ->
                try {
                    if (taskId != null && headlessJsTaskContext.isTaskRunning(taskId)) {
                        headlessJsTaskContext.finishTask(taskId)
                    }
                    // fix for Expo 52/RN 0.72/0.73 where the older kotlin/gradle toolchain complains 
                    // about the above if statement being used as an expression. Explicitly returning 
                    // Unit here tells the compiler that the above if is not an expression
                    Unit
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to finish headless JS task", e)
                }
            }
            try {
                val uriPrefix = config?.getString("baseUri")?.let { Uri.parse(it) }
                val localAssociation =
                    LocalAssociationScenario(
                        CLIENT_TIMEOUT_MS,
                    )
                val intent =
                    LocalAssociationIntentCreator.createAssociationIntent(
                        uriPrefix,
                        localAssociation.port,
                        localAssociation.session
                    )
                withContext(Dispatchers.Main) {
                    sessionTaskId = headlessJsTaskContext.startTask(sessionBackgroundTaskConfig)
                }
                associationResultCallback = { resultCode ->
                    if (resultCode == Activity.RESULT_CANCELED) {
                        Log.d(TAG, "Local association cancelled by user, ending session")
                        promise.reject(
                            "Session not established: Local association cancelled by user",
                            LocalAssociationScenario.ConnectionFailedException(
                                "Local association cancelled by user"
                            )
                        )
                        localAssociation.close()
                    }

                    // stop the headless js task, regardless if the association was successful or not
                    finishHeadlessTask(sessionTaskId)
                }
                reactApplicationContext.currentActivity?.apply {
                    startActivityForResult(intent, REQUEST_LOCAL_ASSOCIATION)
                } ?: throw NullPointerException(
                    "Could not find a current activity from which to launch a local association"
                )
                val client = localAssociation.start()
                    .get(ASSOCIATION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                sessionState = SessionState(client, localAssociation)
                val sessionPropertiesMap: WritableMap = WritableNativeMap()
                sessionPropertiesMap.putString(
                    "protocol_version",
                    when (localAssociation.session.sessionProperties.protocolVersion) {
                        ProtocolVersion.LEGACY -> "legacy"
                        ProtocolVersion.V1 -> "v1"
                    }
                )
                promise.resolve(sessionPropertiesMap)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Found no installed wallet that supports the mobile wallet protocol", e)
                finishHeadlessTask(sessionTaskId)
                cleanup()
                promise.reject("ERROR_WALLET_NOT_FOUND", e)
            } catch (e: TimeoutException) {
                Log.e(TAG, "Timed out waiting for local association to be ready", e)
                finishHeadlessTask(sessionTaskId)
                cleanup()
                promise.reject("Timed out waiting for local association to be ready", e)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for local association to be ready", e)
                finishHeadlessTask(sessionTaskId)
                cleanup()
                promise.reject(e)
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed establishing local association with wallet", e.cause)
                finishHeadlessTask(sessionTaskId)
                cleanup()
                promise.reject(e)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start session", e)
                finishHeadlessTask(sessionTaskId)
                cleanup()
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    override fun invoke(method: String, params: ReadableMap?, promise: Promise): Unit =
        sessionState?.let {
            Log.d(TAG, "invoke `$method` with params $params")
            try {
                val result = it.client
                    .methodCall(method, convertMapToJson(params), CLIENT_TIMEOUT_MS)
                    .get() as JSONObject
                promise.resolve(convertJsonToMap(result))
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is JsonRpc20Client.JsonRpc20RemoteException) {
                    val userInfo = Arguments.createMap()
                    userInfo.putInt("jsonRpcErrorCode", cause.code)
                    promise.reject("JSON_RPC_ERROR", cause, userInfo)
                } else if (cause is TimeoutException) {
                    promise.reject("Timed out waiting for response", e)
                } else {
                    throw e
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to invoke `$method` with params $params", e)
                promise.reject(e)
            }
        } ?: throw NullPointerException(
            "Tried to invoke `$method` without an active session"
        )

    @ReactMethod
    override fun endSession(promise: Promise): Unit {
        sessionState?.let {
            launch {
                Log.d(TAG, "endSession")
                try {
                    it.localAssociation
                        .close()
                        .get(ASSOCIATION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    cleanup()
                    promise.resolve(true)
                } catch (e: TimeoutException) {
                    Log.e(TAG, "Timed out waiting for local association to close", e)
                    cleanup()
                    promise.reject("Failed to end session", e)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to end session", e)
                    cleanup()
                    promise.reject("Failed to end session", e)
                }
            }
        } ?: throw NullPointerException("Tried to end a session without an active session")
    }

    private fun cleanup() {
        sessionState = null
        associationResultCallback = null
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }
}
