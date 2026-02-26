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
import com.solana.mobilewalletadapter.clientlib.scenario.RemoteAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.RemoteAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.fakewallet.usecase.SolanaSigningUseCase
import com.solana.mobilewalletadapter.walletlib.scenario.TestScopeLowPowerMode
import com.solana.mobilewalletadapter.walletlib.transport.websockets.server.WebSocketReflectorServer
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import com.solana.transaction.toUnsignedTransaction
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

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
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

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
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.authorize))
            .check(matches(isDisplayed()))
    }

    @Test
    fun authorizationFlow_SuccessfulAuthorizationLegacy() {
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
    fun authorizationFlow_SuccessfulAuthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

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
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
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
    fun authorizationFlow_SuccessfulRemoteAuthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        // simulate remote reflector server
        val port = 8800 + Random.nextInt(0, 100)
        val server = WebSocketReflectorServer(port)
        server.init()

        // simulate client side scenario
        val hostAuthority = "localhost:$port"
        val remoteAssociation = RemoteAssociationScenario("ws", hostAuthority,
            Scenario.DEFAULT_CLIENT_TIMEOUT_MS) { scenario, reflectorId ->
            val associationIntent = RemoteAssociationIntentCreator.createAssociationIntent(
                null,
                scenario.hostAuthority,
                reflectorId,
                scenario.session
            )
            ActivityScenario.launch<MainActivity>(associationIntent)
        }

        // when
        // trigger authorization from client
        val authorization = remoteAssociation.start().get().run {
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()
        server.close()

        // verify that we got an auth token (successful auth)
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
    }

    @Test
    fun authorizationFlow_SuccessfulReauthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)
        val scenario = localAssociation.start().get()

        // trigger authorization from client
        val authorization = scenario.run {
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // trigger reauthorization from client
        val reauthorization = scenario.run {
            authorize(identityUri, iconUri, identityName, chain, authResult.authToken, null,
                authResult.accounts.map { it.publicKey }.toTypedArray(), null)
        }

        val reauthResult = reauthorization.get()

        // verify that we got a successful reauth
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
        assertEquals(authResult.authToken, reauthResult.authToken)
        reauthResult.accounts.forEachIndexed { i, aa ->
            assertArrayEquals(authResult.accounts[i].publicKey, aa.publicKey)
        }
    }

    @Test
    fun authorizationFlow_DeclinedAuthorization() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

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
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
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
    fun authorizationFlow_SuccessfulAuthorizeX3() {
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
        onView(withId(R.id.btn_authorize_x3))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // verify that we got an auth token (successful auth)
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
        assertTrue(authResult?.accounts?.size == 3)
    }

    @Test
    fun authorizationFlow_SuccessfulReauthorizeX3() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        // simulate client side scenario
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent)
        val scenario = localAssociation.start().get()

        // trigger authorization from client
        val authorization = scenario.run {
            authorize(identityUri, iconUri, identityName, chain, null, null, null, null)
        }

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        // then
        onView(withId(R.id.btn_authorize_x3))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // trigger reauthorization from client
        val reauthorization = scenario.run {
            authorize(identityUri, iconUri, identityName, chain, authResult.authToken, null,
                authResult.accounts.map { it.publicKey }.toTypedArray(), null)
        }

        val reauthResult = reauthorization.get()

        // verify that we got an auth token (successful auth)
        assertTrue(authResult?.authToken?.isNotEmpty() == true)
        assertEquals(3, authResult?.accounts?.size)
        assertEquals(authResult.authToken, reauthResult.authToken)
        assertEquals(3, reauthResult.accounts.size)
        reauthResult.accounts.forEachIndexed { i, aa ->
            assertArrayEquals(authResult.accounts[i].publicKey, aa.publicKey)
        }
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
        assertTrue(authResult.authToken.isNotEmpty())
        assertTrue(authResult.signInResult != null)
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(authResult.accounts.first().publicKey, 0))
        signer.update(authResult.signInResult!!.signedMessage, 0, authResult.signInResult!!.signedMessage.size)
        assertTrue(signer.verifySignature(authResult.signInResult!!.signature))
    }

    @Test
    fun authorizationFlow_SuccessfulReauthorizeSingleAccountMultiAuth() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identity1Uri = Uri.parse("https://test1.com")
        val identity1Name = "Test 1"
        val identity2Uri = Uri.parse("https://test2.com")
        val identity2Name = "Test 2"
        val iconUri = Uri.parse("favicon.ico")
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        // simulate client side scenarios
        val localAssociation1 = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent1 = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation1.port,
            localAssociation1.session
        )

        val localAssociation2 = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val associationIntent2 = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation2.port,
            localAssociation2.session
        )

        // when
        ActivityScenario.launch<MainActivity>(associationIntent1)

        // First, simulate client 1 authorizing
        // trigger authorization from client 1
        var mwaClient = localAssociation1.start().get()
        val authorization1 = mwaClient.authorize(identity1Uri, iconUri, identity1Name, chain,
            null, null, null, null)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val accounts = authorization1.get().accounts.map { it.publicKey }
        localAssociation1.close().get()

        // Now, authorize client 2 for the same account (publickey) that was used with client 1
        ActivityScenario.launch<MainActivity>(associationIntent2)

        // trigger authorization from client 2
        mwaClient = localAssociation2.start().get()
        val authorization2 = mwaClient.authorize(identity2Uri, iconUri, identity2Name, chain,
            null, null, accounts.toTypedArray(), null)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization2.get()

        // reauthorize - this is needed to trigger the auth lookup
        val reauthResult = mwaClient.authorize(identity2Uri, iconUri, identity2Name, chain,
            authResult.authToken, null, accounts.toTypedArray(), null).get()

        // then
        assertNotNull(reauthResult)
        assertTrue(reauthResult.authToken == authResult.authToken)
    }

    @Test
    fun signingFlow_SuccessfulSignMessagesMultiAccount() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        val messages = arrayOf("hello world 1!".encodeToByteArray(), "hello world 2!".encodeToByteArray())

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
        val scenario = localAssociation.start().get()
        val authorization = scenario.authorize(identityUri, iconUri, identityName, chain,
            null, null, null, null)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize_x3))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // trigger authorization from client
        val signMessagesFuture = scenario.signMessagesDetached(messages,
                authResult.accounts.map { it.publicKey }.toTypedArray())

        // then
        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "sign_payloads")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val signedPayloads = signMessagesFuture.get()

        // verify that we got all signatures
        assertTrue(signedPayloads.messages.size == messages.size)
        signedPayloads.messages.forEachIndexed { i, signedMessage ->
            assertArrayEquals(messages[i], signedMessage.message)
            assertTrue(signedMessage.signatures.size == 3)
            assertTrue(signedMessage.addresses.size == 3)
            signedMessage.addresses.zip(signedMessage.signatures).forEachIndexed { j, sm ->
                val address = sm.first
                val sig = sm.second
                assertArrayEquals(authResult.accounts[j].publicKey, address)
                val signer = Ed25519Signer()
                signer.init(false, Ed25519PublicKeyParameters(address, 0))
                signer.update(signedMessage.message, 0, signedMessage.message.size)
                assertTrue(signer.verifySignature(sig))
            }
        }
    }

    @Test
    fun signingFlow_SuccessfulSignTransactionsMultiAccount() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        val transactionCount = 2
        val memoProgramId = SolanaPublicKey.from("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")

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
        val scenario = localAssociation.start().get()
        val authorization = scenario.authorize(identityUri, iconUri, identityName, chain,
            null, null, null, null)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize_x3))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // build transaction
        val transactions = (0 until transactionCount).map {
            Message.Builder().apply {
                authResult.accounts.forEach {
                    addInstruction(
                        TransactionInstruction(
                            memoProgramId,
                            listOf(AccountMeta(SolanaPublicKey(it.publicKey), true, true)),
                            "hello world!".encodeToByteArray()
                        )
                    )
                }
                setRecentBlockhash(memoProgramId)
            }.build().toUnsignedTransaction()
        }

        // trigger authorization from client
        val signTransactionsFuture = scenario.signTransactions(transactions.map { it.serialize() }.toTypedArray())

        // then
        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "sign_payloads")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        val signedPayloadsResult = signTransactionsFuture.get()

        // verify that we got all signatures on each transaction
        assertTrue(signedPayloadsResult.signedPayloads.size == transactionCount)
        signedPayloadsResult.signedPayloads.forEachIndexed { i, txBytes ->
            val signedTransaction = Transaction.from(txBytes)
            val signedMessage = signedTransaction.message.serialize()
            assertArrayEquals(transactions[i].message.serialize(), signedMessage)
            assertTrue(signedTransaction.signatures.size == 3)
            signedTransaction.message.accounts.zip(signedTransaction.signatures).forEachIndexed { j, pair ->
                val address = pair.first.bytes
                val sig = pair.second
                assertArrayEquals(authResult.accounts[j].publicKey, address)
                val signer = Ed25519Signer()
                signer.init(false, Ed25519PublicKeyParameters(address, 0))
                signer.update(signedMessage, 0, signedMessage.size)
                assertTrue(signer.verifySignature(sig))
            }
        }
    }

    @Test
    fun signingFlow_SuccessfulSignAndSendTransactionsMultiAccount() {
        // given
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val identityUri = Uri.parse("https://test.com")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "Test"
        val chain = ProtocolContract.CHAIN_SOLANA_TESTNET

        val transactionCount = 2
        val memoProgramId = SolanaPublicKey.from("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")

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
        val scenario = localAssociation.start().get()
        val authorization = scenario.authorize(identityUri, iconUri, identityName, chain,
            null, null, null, null)

        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "authorize")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize_x3))
            .check(matches(isDisplayed())).perform(click())

        val authResult = authorization.get()

        // build transaction
        val transactions = (0 until transactionCount).map {
            Message.Builder().apply {
                authResult.accounts.forEach {
                    addInstruction(
                        TransactionInstruction(
                            memoProgramId,
                            listOf(AccountMeta(SolanaPublicKey(it.publicKey), true, true)),
                            "hello world!".encodeToByteArray()
                        )
                    )
                }
                setRecentBlockhash(memoProgramId)
            }.build().toUnsignedTransaction()
        }

        // trigger authorization from client
        val signAndSendFuture = scenario.signAndSendTransactions(
            transactions.map { it.serialize() }.toTypedArray(),
            null, null, null, null, null
        )

        // then
        uiDevice.wait(Until.hasObject(By.res(FAKEWALLET_PACKAGE, "sign_payloads")), WINDOW_CHANGE_TIMEOUT)

        onView(withId(R.id.btn_authorize))
            .check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.btn_simulate_transactions_submitted))
            .check(matches(isDisplayed())).perform(click())

        val signAndSendResult = signAndSendFuture.get()

        // verify that we got all signatures on each transaction
        assertTrue(signAndSendResult.signatures.size == transactionCount)
        signAndSendResult.signatures.forEachIndexed { i, signature ->
            assertTrue(signature.size == SolanaSigningUseCase.SIGNATURE_LEN)
            val signedMessage = transactions[i].message.serialize()
            val address = transactions[i].message.accounts.first().bytes
            assertArrayEquals(authResult.accounts[0].publicKey, address)
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(address, 0))
            signer.update(signedMessage, 0, signedMessage.size)
            assertTrue(signer.verifySignature(signature))
        }
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
        onView(withText(R.string.label_low_power_mode_warning)).check(doesNotExist()).inRoot(isDialog())
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
        onView(withText(R.string.label_low_power_mode_warning))
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