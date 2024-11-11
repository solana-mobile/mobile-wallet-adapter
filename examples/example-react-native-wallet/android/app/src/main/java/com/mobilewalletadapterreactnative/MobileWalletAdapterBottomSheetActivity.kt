package com.mobilewalletadapterreactnative

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactRootView
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MobileWalletAdapterBottomSheetActivity : ReactActivity() {

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override protected fun getMainComponentName() = "MobileWalletAdapterEntrypoint"

    override fun onCreate(savedInstanceState: Bundle?) {

        val windowLayoutParams = window.attributes

        windowLayoutParams.gravity = Gravity.BOTTOM
        windowLayoutParams.flags =
                windowLayoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND

        window.attributes = windowLayoutParams
        super.onCreate(null)
    }

    /**
     * Returns the instance of the [ReactActivityDelegate]. There the RootView is created and you
     * can specify the renderer you wish to use - the new renderer (Fabric) or the old renderer
     * (Paper).
     */
    override protected fun createReactActivityDelegate(): ReactActivityDelegate {
        return DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
    }
}
