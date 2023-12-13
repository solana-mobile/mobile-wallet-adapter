package com.solana.mobilewalletadapter.fakewallet

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.datetime.Iso8601DateTime
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.walletlib.scenario.TestScopeLowPowerMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {
    val WINDOW_CHANGE_TIMEOUT = 15000L
    val FAKEWALLET_PACKAGE = "com.solana.mobilewalletadapter.fakewallet"

    @get:Rule
    var activityScenarioRule: ActivityScenarioRule<MainActivity> = activityScenarioRule()

    @Before
    fun setup() {
        TestScopeLowPowerMode = false
    }

    @Test
    fun associationIntent_LaunchesAssociationFragment() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "associate")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.associate))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clientAuthRequest_LaunchesAuthorizationFragment() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val cluster = ProtocolContract.CLUSTER_TESTNET

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // trigger authorization from client
        localAssociation.start().get().run {
            authorize(identityUri, iconUri, identityName, cluster)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.authorize))
            .check(matches(isDisplayed()))
    }

    @Test
    fun authorizationFlow_SuccessfulAuthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val cluster = ProtocolContract.CLUSTER_TESTNET

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // trigger authorization from client
        val authorization = localAssociation.start().get().run {
            authorize(identityUri, iconUri, identityName, cluster)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // verify that we got an auth token (successful auth)
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
    }

    @Test
    fun authorizationFlow_DeclinedAuthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val cluster = ProtocolContract.CLUSTER_TESTNET

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // trigger authorization from client
        val authorization = localAssociation.start().get().run {
            authorize(identityUri, iconUri, identityName, cluster)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_decline))
            .check(matches(isDisplayed())).perform(click())

        val authResult = kotlin.runCatching { authorization.get() }.exceptionOrNull()

        // verify that we got an auth authorization error (declined auth)
        assertNotNull(authResult)
    }

    @Test
    fun authorizationFlow_SuccessfulSignIn() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET
        val signInPayload = SignInWithSolana.Payload(
            "test.com", "sign in statement"
        )

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        // trigger authorization from client
        val authorization = localAssociation.start().get().run {
            authorize(identityUri, iconUri, identityName, chain, null, null, null, signInPayload)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_sign_in))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // verify that we got an auth token (successful auth)
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
        assertTrue(authResult?.signInResult != null)
    }

    @Test
    fun lowPowerMode_NoWarningShown() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        TestScopeLowPowerMode = false

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "associate")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withText(R.string.low_power_mode_warning_title)).check(doesNotExist()).inRoot(isDialog())
    }

    @Test
    fun lowPowerMode_showsWarningIfNoConnection() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        TestScopeLowPowerMode = true

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "associate")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withText(R.string.low_power_mode_warning_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    // removed this test as we are currently not handling this case in walletlib - it will be
    // addressed in a future PR. Leaving the test commented here for future reference
    // see discussion here for more deets: https://github.com/solana-mobile/mobile-wallet-adapter/pull/363
//    @Test
//    fun lowPowerMode_ShowsWarningAfterInitialConnectionMade() {
//        // given
//        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
//
//        val identityUri = Uri.parse("https://test.com")
//        val iconUri = Uri.parse("favicon.ico")
//        val identityName = "Test"
//        val cluster = ProtocolContract.CLUSTER_TESTNET
//
//        TestScopeLowPowerMode = true
//
//        // simulate client side scenario
//        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
//        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
//            null,
//            localAssociation.port,
//            localAssociation.session
//        )
//
//        // when
//        ActivityScenario.launch<MainActivity>(associationIntent)
//
//        // trigger authorization from client
//        localAssociation.start().get().apply {
//            this.authorize(identityUri, iconUri, identityName, cluster)
//        }
//
//        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)
//
//        // click authorize button
//        onView(withId(R.id.btn_authorize))
//            .check(matches(isDisplayed())).perform(click())
//
//        // then
//        onView(withText(R.string.low_power_mode_warning_title))
//            .inRoot(isDialog())
//            .check(matches(isDisplayed()))
//    }
}