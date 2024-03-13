/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class MainActivityTestPhantom {
    val WINDOW_CHANGE_TIMEOUT = 15000L

    @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

    // Fake Wallet
//    val walletPackageName = "com.solana.mobilewalletadapter.fakewallet"
//    val walletDisplayName = "Fake Wallet"
//    val walletAuthorizeButton = By.res(walletPackageName, "btn_authorize")
//    val walletSignInButton = By.res(walletPackageName, "btn_sign_in")
//    val walletSignTransactionButton = By.res(walletPackageName, "btn_authorize")
//    val walletSimulateSendButton = By.res(walletPackageName, "btn_simulate_transactions_submitted")
//    val walletSendTransactionButton = walletSimulateSendButton//By.res(walletPackageName, "btn_send_transaction_to_cluster")

    // Phantom
    val walletPackageName = "app.phantom"
    val walletDisplayName = "Phantom"
    val walletAuthorizeButton = By.textContains("Connect")
    val walletSignTransactionButton = By.textContains("Approve")
    val walletSendTransactionButton: BySelector? = null

    @Test
    fun getCapabilities_isSuccessful() {
        onView(withId(R.id.btn_get_capabilities)).perform(click())
    }

    @Test
    fun authorize_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withId(R.id.btn_deauthorize)).check(matches(isClickable()))
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    // Phantom and Solflare Do not currently support sign in
//    @Test
//    fun signInWithSolana_isSuccessful() {
//        // given
//        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
//
//        // when
//        onView(withId(R.id.btn_sign_in)).perform(click())
//
//        handleWalletDisambiguationIfNecessary(uiDevice)
//
//        waitForWalletButton(uiDevice, walletSignInButton)
//            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)
//
//        waitForWalletComplete(uiDevice)
//
//        // then
//        onView(withId(R.id.btn_deauthorize)).check(matches(isClickable()))
//        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
//    }

    @Test
    fun combinedAuthorizeAndSignTx1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize_sign)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton).click()

        uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun combinedAuthorizeAndSignMsgAndSignTx1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize_sign_msg_txn)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton).click()

        uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)

        waitForWalletButton(uiDevice, walletSignTransactionButton).click()

        uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        waitForAndClickWalletButton(uiDevice, walletSendTransactionButton)
//        waitForWalletButton(uiDevice, walletSimulateSendButton)
//            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun authorizeAndSignTx1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun authorizeAndSignTx3_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x3)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun authorizeAndSignAndSendTx1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton).click()

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        waitForWalletButton(uiDevice, walletSimulateSendButton)
//            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)
        walletSendTransactionButton?.let {
            waitForWalletButton(uiDevice, walletSendTransactionButton)
                .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun authorizeAndSignTx1_V1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.spinner_txn_ver)).perform(click())
        onView(withText(R.string.string_txn_version_v0)).perform(click())
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun authorizeAndSignAndSendTx1_V1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.spinner_txn_ver)).perform(click())
        onView(withText(R.string.string_txn_version_v0)).perform(click())
        onView(withId(R.id.btn_authorize)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        waitForWalletComplete(uiDevice)

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        // specific to Phantom, phantom displays this "Transaction Expired Warning"
        // and gives an option for the user to ignore and proceed anyway
        if (walletPackageName == "app.phantom") {
            waitForWalletButton(uiDevice, By.textContains("proceed anyway")).click()
            uiDevice.waitForWindowUpdate(walletPackageName, WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletButton(uiDevice, walletSignTransactionButton).click()

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        waitForWalletButton(uiDevice, walletSimulateSendButton)
//            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)
        walletSendTransactionButton?.let {
            waitForWalletButton(uiDevice, walletSendTransactionButton)
                .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)
        }

        waitForWalletComplete(uiDevice)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    private fun handleWalletDisambiguationIfNecessary(uiDevice: UiDevice) {
        // Wait for either the wallet window, or the disambiguation window
        val o = uiDevice.wait(
            findObject(
                By.pkg(Pattern.compile("android|${Pattern.quote(walletPackageName)}"))
            ), WINDOW_CHANGE_TIMEOUT
        )
        if (o.applicationPackage == walletPackageName) {
            return
        }

        // Disambigation window is showing; select FakeWallet to continue testing
        uiDevice.findObject(By.res("android", "title"))!!.run {
            if (childCount > 1 && text.contains(walletDisplayName)) {
                // case 1: "Open with Fake Wallet" > click "Just once"
                uiDevice.findObject(By.res("android", "button_once"))
            } else if (text.contains(walletDisplayName)) {
                // case 2: "Open with:" > choose app from list > click "Just once"
                uiDevice.findObject(By.textContains(walletDisplayName)).click()
                uiDevice.findObject(By.res("android", "button_once"))
            } else {
                // case 2: "Open with Solflare/Phantom" > choose Fake Wallet app from list
                uiDevice.findObject(By.textContains(walletDisplayName))
            }
        }!!.clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)!!.also {
            // Wait for the wallet window to be available
            uiDevice.wait(findObject(By.pkg(walletPackageName)), WINDOW_CHANGE_TIMEOUT)!!
        }
    }

    private fun waitForWalletButton(uiDevice: UiDevice, buttonResName: String) =
        uiDevice.wait(findObject(By.res(walletPackageName, buttonResName)), WINDOW_CHANGE_TIMEOUT)!!

    private fun waitForWalletComplete(uiDevice: UiDevice) =
        activityScenarioRule.scenario.onActivity {
            uiDevice.wait(findObject(By.pkg(it.packageName)), WINDOW_CHANGE_TIMEOUT)!!
        }

    private fun waitForWalletButton(uiDevice: UiDevice, buttonSelector: BySelector) =
        uiDevice.wait(findObject(buttonSelector), WINDOW_CHANGE_TIMEOUT)
            ?: handlePossibleANRDialog(uiDevice).run {
                uiDevice.findObject(buttonSelector)
            }
    private fun handlePossibleANRDialog(uiDevice: UiDevice) {
        uiDevice.findObject(By.textContains("wait"))?.click()
    }

    private fun handlePossibleTransactionExpiredWarning(uiDevice: UiDevice) {
        uiDevice.findObject(By.textContains("proceed anyway"))?.click()
    }
}