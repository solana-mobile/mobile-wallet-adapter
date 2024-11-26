package com.solanamobile.mobilewalletadapter.reactnative

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReadableMap

abstract class SolanaMobileWalletAdapterSpec
internal constructor(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {

    abstract fun startSession(config: ReadableMap?, promise: Promise)

    abstract fun invoke(method: String, params: ReadableMap?, promise: Promise)

    abstract fun endSession(promise: Promise)
}
