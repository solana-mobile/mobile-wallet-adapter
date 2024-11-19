package com.solanamobile.mobilewalletadapter.reactnative

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import java.util.HashMap

class SolanaMobileWalletAdapterModulePackage : TurboReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return if (name == SolanaMobileWalletAdapterModule.NAME) {
            SolanaMobileWalletAdapterModule(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
            moduleInfos[SolanaMobileWalletAdapterModule.NAME] =
                    ReactModuleInfo(
                            SolanaMobileWalletAdapterModule.NAME,
                            SolanaMobileWalletAdapterModule.NAME,
                            false, // canOverrideExistingModule
                            false, // needsEagerInit
                            true, // hasConstants
                            false, // isCxxModule
                            true // isTurboModule
                    )
            moduleInfos
        }
    }
}
