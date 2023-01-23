package com.solana.mobilewalletadapter.fakewallet

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.walletlib.provider.TestScopeLowPowerMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    var activityScenarioRule = activityScenarioRule<MainActivity>()

    @Test
    fun associationIntent_LaunchesAssociationFragment() {
        // given
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // then
        onView(withId(R.id.associate))
            .check(matches(isDisplayed()))
    }

    @Test
    fun lowPowerMode_NoWarningShown() {
        // given
        TestScopeLowPowerMode = false
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // then
        onView(withText(R.string.low_power_mode_warning_title)).check(doesNotExist()).inRoot(isDialog())
    }

    @Test
    fun lowPowerMode_showsWarningIfNoConnection() {
        // given
        TestScopeLowPowerMode = true
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // then
        onView(withText(R.string.low_power_mode_warning_title)).inRoot(isDialog()).check(matches(isDisplayed()))
    }
}