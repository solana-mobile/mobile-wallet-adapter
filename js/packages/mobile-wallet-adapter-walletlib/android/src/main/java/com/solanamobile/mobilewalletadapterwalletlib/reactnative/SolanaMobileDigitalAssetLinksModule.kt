package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import android.util.Log
import android.os.Build
import com.facebook.react.bridge.*
import com.solana.digitalassetlinks.AndroidAppPackageVerifier
import java.net.URI

class SolanaMobileDigitalAssetLinksModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext){
    
    // Sets the name of the module in React, accessible at ReactNative.NativeModules.SolanaMobileDigitalAssetLinks
    override fun getName() = "SolanaMobileDigitalAssetLinks"

    @ReactMethod
    fun getCallingPackage(promise: Promise) {
        promise.resolve(currentActivity?.callingPackage)
    }

    @ReactMethod
    fun verifyCallingPackage(clientIdentityUri: String, promise: Promise) {
        currentActivity?.callingPackage?.let { callingPackage -> 
            verifyPackage(callingPackage, clientIdentityUri, promise)
        } ?: run {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun verifyPackage(packageName: String, clientIdentityUri: String, promise: Promise) {
        val packageManager = reactContext.getPackageManager()
        val verifier = AndroidAppPackageVerifier(packageManager)
        val verified = try {
            verifier.verify(packageName, URI.create(clientIdentityUri))
        } catch (e: AndroidAppPackageVerifier.CouldNotVerifyPackageException) {
            Log.w(TAG, "Package verification failed for package=$packageName, clientIdentityUri=$clientIdentityUri")
            false
        }
        promise.resolve(verified)
    }

    @ReactMethod
    fun getCallingPackageUid(promise: Promise) {
        currentActivity?.callingPackage?.let { callingPackage -> 
            getUidForPackage(callingPackage, promise)
        } ?: run {
            promise.reject(Error("Cannot get UID for calling package: No calling package found"))
        }
    }

    @ReactMethod
    fun getUidForPackage(packageName: String, promise: Promise) {
        val packageManager = reactContext.getPackageManager()
        val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            packageManager.getPackageUid(packageName, 0)
        } else {
            packageManager.getApplicationInfo(packageName, 0).uid
        }
        promise.resolve(uid)
    }

    companion object {
        private val TAG = SolanaMobileDigitalAssetLinksModule::class.simpleName
    }
}