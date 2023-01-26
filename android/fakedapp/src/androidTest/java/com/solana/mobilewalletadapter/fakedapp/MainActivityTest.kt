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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        uiDevice.waitForWindowUpdate(null, 500)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x3)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        uiDevice.waitForWindowUpdate(null, 500)

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        uiDevice.findObject(By.textStartsWith("SEND TRANSACTION")).click()
        uiDevice.findObject(By.textStartsWith("SIMULATE SUBMITTED")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // back in dApp, click sign
        onView(withId(R.id.btn_sign_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

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

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // back in dApp, click sign
//        onView(withId(R.id.btn_request_airdrop)).perform(click())
        onView(withId(R.id.btn_sign_and_send_txn_x1)).perform(click())

        handleWalletDisambiguationIfNecessary(uiDevice)

        uiDevice.findObject(By.text("AUTHORIZE")).click()

        // send transaction to cluster is flaky and relies on a successful airdrop, which is
        // difficult with the public devnet/testnet RPCs. Will revisit this with a local validator!
//        uiDevice.findObject(By.textStartsWith("SEND TRANSACTION")).click()
        uiDevice.findObject(By.textStartsWith("SIMULATE SUBMITTED")).click()

        // then
        onView(withText(R.string.msg_request_succeeded)).check(matches(isDisplayed()))
    }

    private fun handleWalletDisambiguationIfNecessary(uiDevice: UiDevice) {
        uiDevice.findObjects(By.textContains("Fake Wallet")).forEach {
            if (it.text.startsWith("Open with")) {
                uiDevice.findObject(By.text("Just once")).click()
            } else if (it.isClickable) {
                it.click()
            }
        }

        uiDevice.waitForWindowUpdate(null, 500)
    }
}