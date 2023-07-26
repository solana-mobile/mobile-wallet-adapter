package com.solana.mobilewalletadapter.clientlib

import androidx.activity.ComponentActivity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MobileWalletAdapterTest {

    lateinit var sender: ActivityResultSender
    lateinit var mwa: MobileWalletAdapter
    lateinit var activity: ActivityController<ComponentActivity>

    @Before
    fun before() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create(null)

        sender = ActivityResultSender(activity.get())
        mwa = MobileWalletAdapter(
            ioDispatcher = StandardTestDispatcher()
        )
    }

    @Test
    fun runTest() = runTest() {
        mwa.transact(sender) {

        }
    }

}