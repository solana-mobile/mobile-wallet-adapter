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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    val WINDOW_CHANGE_TIMEOUT = 10000L
    val FAKEWALLET_PACKAGE = "com.solana.mobilewalletadapter.fakewallet"

    @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

    val walletAuthorizeButton = "btn_authorize"
    val walletSendTransactionButton = "btn_send_transaction_to_cluster"
    val walletSimulateSendButton = "btn_simulate_transactions_submitted"

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

        // then
        onView(withId(R.id.btn_deauthorize)).check(matches(isClickable()))
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    @Test
    fun combinedAuthorizeAndSignTx1_isSuccessful() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // when
        onView(withId(R.id.btn_authorize_sign)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton).click()

        uiDevice.waitForWindowUpdate(FAKEWALLET_PACKAGE, WINDOW_CHANGE_TIMEOUT)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

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

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

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

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x3)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

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

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton).click()

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        waitForAndClickWalletButton(uiDevice, walletSendTransactionButton)
        waitForWalletButton(uiDevice, walletSimulateSendButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

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

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

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

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        waitForWalletButton(uiDevice, walletAuthorizeButton).click()

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        waitForAndClickWalletButton(uiDevice, walletSendTransactionButton)
        waitForWalletButton(uiDevice, walletSimulateSendButton)
            .clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    private fun handleWalletDisambiguationIfNecessary(uiDevice: UiDevice) {
        uiDevice.findObject(By.res("android", "title"))?.run {
            if (childCount > 1 && text.contains("Fake Wallet")) {
                // case 1: "Open with Fake Wallet" > click "Just once"
                uiDevice.findObject(By.res("android", "button_once"))
            } else if (text.contains("Fake Wallet")) {
                // case 2: "Open with:" > choose app from list > click "Just once"
                uiDevice.findObject(By.textContains("Fake Wallet")).click()
                uiDevice.findObject(By.res("android", "button_once"))
            } else {
                // case 2: "Open with Solflare/Phantom" > choose Fake Wallet app from list
                uiDevice.findObject(By.textContains("Fake Wallet"))
            }
        }?.clickAndWait(newWindow(), WINDOW_CHANGE_TIMEOUT)

        // case 4: only 1 wallet, no disambiguation dialog
    }

    private fun waitForWalletButton(uiDevice: UiDevice, buttonResName: String) =
        uiDevice.wait(findObject(By.res(FAKEWALLET_PACKAGE, buttonResName)), WINDOW_CHANGE_TIMEOUT)
}