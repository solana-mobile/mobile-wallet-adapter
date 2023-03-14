package com.examplewallet

import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager

import com.facebook.react.PackageList
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactRootView

class MainActivity : ReactActivity() {
    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override protected  fun getMainComponentName() = "ExampleWallet"

    /**
     * Returns the instance of the [ReactActivityDelegate]. There the RootView is created and
     * you can specify the renderer you wish to use - the new renderer (Fabric) or the old renderer
     * (Paper).
     */
    override protected fun createReactActivityDelegate(): ReactActivityDelegate {
        return MainActivityDelegate(this, mainComponentName)
    }

    class MainActivityDelegate(val activity: ReactActivity?, mainComponentName: String?) :
        ReactActivityDelegate(activity, mainComponentName) {

        override protected fun createRootView(): ReactRootView {
            val reactRootView = ReactRootView(getContext())
            // If you opted-in for the New Architecture, we enable the Fabric Renderer.
            reactRootView.setIsFabric(BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)
            return reactRootView
        }

        // If you opted-in for the New Architecture, we enable Concurrent Root (i.e. React 18).
        // More on this on https://reactjs.org/blog/2022/03/29/react-v18.html
        override protected fun isConcurrentRootEnabled() = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
    }
}
