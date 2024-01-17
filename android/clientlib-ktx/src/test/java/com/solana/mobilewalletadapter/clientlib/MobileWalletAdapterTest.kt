package com.solana.mobilewalletadapter.clientlib

import android.content.Intent
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeoutException
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MobileWalletAdapterTest {

    lateinit var testDispatcher: TestDispatcher

    lateinit var mockProvider: AssociationScenarioProvider
    lateinit var mockClient: MobileWalletAdapterClient
    lateinit var sampleAuthResult: AuthorizationResult
    lateinit var sampleReauthResult: AuthorizationResult
    lateinit var sample20AuthResult: AuthorizationResult

    lateinit var sender: ActivityResultSender
    lateinit var mobileWalletAdapter: MobileWalletAdapter

    val creds = ConnectionIdentity(
        identityUri = Uri.EMPTY,
        iconUri = Uri.EMPTY,
        identityName = "Test App",
    )

    private fun mockAssociationScenarioProvider(isMwa2: Boolean = false): AssociationScenarioProvider {
        val protocol = if (isMwa2) SessionProperties.ProtocolVersion.V1 else SessionProperties.ProtocolVersion.LEGACY

        return mock {
            on { provideAssociationScenario(any()) } doAnswer {
                mock {
                    on { start() } doAnswer {
                        mock<NotifyingCompletableFuture<MobileWalletAdapterClient>> {
                            on { get(any(), any()) } doReturn mockClient
                        }
                    }
                    on { session } doAnswer {
                        mock {
                            on { encodedAssociationPublicKey } doAnswer { byteArrayOf() }
                            on { sessionProperties } doAnswer { SessionProperties(protocol) }
                        }
                    }
                    on { close() } doAnswer {
                        val future = NotifyingCompletableFuture<Void>()
                        future.complete(null)
                        future
                    }
                }
            }
        }
    }

    @Before
    fun before() {
        testDispatcher = StandardTestDispatcher()

        sender = mock {
            onBlocking { startActivityForResult(any(), any()) } doAnswer { invocation ->
                val callback = invocation.arguments[1] as (Int) -> Unit
                callback(-1)
            }
        }

        sampleAuthResult = AuthorizationResult.create("AUTHRESULTTOKEN", byteArrayOf(), "Some Label", Uri.EMPTY)
        sampleReauthResult = AuthorizationResult.create("REAUTHRESULTTOKEN", byteArrayOf(), "Some Label", Uri.EMPTY)
        sample20AuthResult = AuthorizationResult.create("20AUTHTOKENRESULT", byteArrayOf(), "Some Label", Uri.EMPTY)

        mockClient = mock {
            on { authorize(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doAnswer {
                mock {
                    on { get() } doReturn sample20AuthResult
                }
            }
            on { authorize(any(), any(), any(), any()) } doAnswer {
                mock {
                    on { get() } doReturn sampleAuthResult
                }
            }
            on { reauthorize(any(), any(), any(), any()) } doAnswer {
                mock {
                    on { get() } doReturn sampleReauthResult
                }
            }
        }

        mockProvider = mockAssociationScenarioProvider()

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `validate calling connect is successful using default constructor`() = runTest(testDispatcher) {
        val result = mobileWalletAdapter.connect(sender)

        assertTrue { result is TransactionResult.Success<Unit> }
        assertTrue { result.successPayload is Unit }
        assertTrue { (result as TransactionResult.Success<Unit>).authResult == sampleAuthResult }
    }

    @Test
    fun `validate an MWA v1 session results in the v1 authorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"
        mockProvider = mockAssociationScenarioProvider()

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any())
        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == sampleAuthResult }
    }

    @Test
    fun `validate an MWA v2 session results in the v2 authorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"
        mockProvider = mockAssociationScenarioProvider(true)

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == sample20AuthResult }
    }

    @Test
    fun `validate for a v1 session providing an authToken before transact results in an reauthorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"
        val creds = ConnectionIdentity(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
        )

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
        mobileWalletAdapter.authToken = "ASAMPLETOKEN"

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).reauthorize(any(), any(), any(), any())

        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == sampleReauthResult }
    }

    @Test
    fun `validate for a v2 session providing an authToken before transact results in the authorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"
        mockProvider = mockAssociationScenarioProvider(true)

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
        mobileWalletAdapter.authToken = "ASAMPLETOKEN"

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())

        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == sample20AuthResult }
    }

    @Test
    fun `validate for a v1 session providing an authToken then calling transact multiple times results in reauth each time`() = runTest(testDispatcher) {
        val creds = ConnectionIdentity(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
        )

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
        mobileWalletAdapter.authToken = "ASAMPLETOKEN"

        mobileWalletAdapter.transact(sender) { }
        val result = mobileWalletAdapter.transact(sender) { }

        verify(mockClient, times(2)).reauthorize(any(), any(), any(), any())

        assertTrue { result is TransactionResult.Success<Unit> }
        assertTrue { (result as TransactionResult.Success<Unit>).authResult == sampleReauthResult }
    }

    @Test
    fun `validate relevant exception is caught when thrown from sender`() = runTest(testDispatcher) {
        sender = mock {
            onBlocking { startActivityForResult(any(), any()) } doAnswer { _ ->
                throw InterruptedException("hello")
            }
        }

        val result = mobileWalletAdapter.transact(sender) { }

        assertTrue { result is TransactionResult.Failure }
        assertTrue { (result as TransactionResult.Failure).e.message == "hello" }
    }

    @Test
    fun `validate relevant exception is caught when thrown transact operations`() = runTest(testDispatcher) {
        val result = mobileWalletAdapter.transact(sender) {
            throw TimeoutException("hello")
        }

        assertTrue { result is TransactionResult.Failure }
        assertTrue { (result as TransactionResult.Failure).e.message == "hello" }
    }

    @Test
    fun `validate relevant exception is caught when thrown generally`() = runTest(testDispatcher) {
        mockProvider = mock {
            on { provideAssociationScenario(any()) } doAnswer {
                throw CancellationException("err")
            }
        }

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.EMPTY,
                iconUri = Uri.EMPTY,
                identityName = "",
            ),
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )

        val result = mobileWalletAdapter.connect(sender)

        assertTrue { result is TransactionResult.Failure }
        assertTrue { (result as TransactionResult.Failure).e.message == "err"}
    }

    @Test
    fun `validate for a v1 session the proper auth token is returned on when authenticating and re-authenticating`() = runTest(testDispatcher) {
        val creds = ConnectionIdentity(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
        )

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )

        val result1 = mobileWalletAdapter.transact(sender) { }

        assertTrue { result1 is TransactionResult.Success<Unit> }
        assertTrue { (result1 as TransactionResult.Success<Unit>).authResult.authToken == sampleAuthResult.authToken }

        val result2 = mobileWalletAdapter.transact(sender) { }

        assertTrue { result2 is TransactionResult.Success<Unit> }
        assertTrue { (result2 as TransactionResult.Success<Unit>).authResult.authToken == sampleReauthResult.authToken }
    }

    @Test
    fun `validate for a v1 session changing the rpc cluster at runtime invalidates the auth token and calls authorize`() = runTest(testDispatcher) {
        mobileWalletAdapter.authToken = "SOMEAUTHTOKEN"

        assertTrue { mobileWalletAdapter.rpcCluster is RpcCluster.Devnet }

        mobileWalletAdapter.rpcCluster = RpcCluster.Testnet

        assertTrue { mobileWalletAdapter.authToken == null }
        assertTrue { mobileWalletAdapter.rpcCluster is RpcCluster.Testnet }

        mobileWalletAdapter.transact(sender) { }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any())
    }

    @Test
    fun `validate https wallet URI is used for the session`() = runTest(testDispatcher) {
        val walletUriBase = Uri.parse("https://mywallet.com/mwa")
        sampleAuthResult = AuthorizationResult.create("AUTHRESULTTOKEN", byteArrayOf(), "Some Label", walletUriBase)

        var intentUri: Uri? = null
        sender = mock {
            onBlocking { startActivityForResult(any(), any()) } doAnswer { invocation ->
                intentUri = (invocation.arguments.first() as Intent).data
                val callback = invocation.arguments[1] as (Int) -> Unit
                callback(-1)
            }
        }

        val creds = ConnectionIdentity(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
        )

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )

        val result1 = mobileWalletAdapter.connect(sender)

        assertTrue { result1 is TransactionResult.Success<Unit> }
        assertTrue { (result1 as TransactionResult.Success<Unit>).authResult.authToken == sampleAuthResult.authToken }
        assertTrue { intentUri?.scheme == "solana-wallet" }

        val result2 = mobileWalletAdapter.transact(sender) { }

        assertTrue { result2 is TransactionResult.Success<Unit> }
        assertTrue { (result2 as TransactionResult.Success<Unit>).authResult.authToken == sampleReauthResult.authToken }
        assertTrue { intentUri.toString().startsWith(walletUriBase.toString()) }
    }

    @Test
    fun `validate non https wallet URI is ignored`() = runTest(testDispatcher) {
        val walletUriBase = Uri.parse("http://mywallet.com/mwa")
        sampleAuthResult = AuthorizationResult.create("AUTHRESULTTOKEN", byteArrayOf(), "Some Label", walletUriBase)

        var intentUri: Uri? = null
        sender = mock {
            onBlocking { startActivityForResult(any(), any()) } doAnswer { invocation ->
                intentUri = (invocation.arguments.first() as Intent).data
                val callback = invocation.arguments[1] as (Int) -> Unit
                callback(-1)
            }
        }

        val creds = ConnectionIdentity(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
        )

        mobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = creds,
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )

        val result1 = mobileWalletAdapter.connect(sender)

        assertTrue { result1 is TransactionResult.Success<Unit> }
        assertTrue { (result1 as TransactionResult.Success<Unit>).authResult.authToken == sampleAuthResult.authToken }
        assertTrue { intentUri?.scheme == "solana-wallet" }

        val result2 = mobileWalletAdapter.transact(sender) { }

        assertTrue { result2 is TransactionResult.Success<Unit> }
        assertTrue { (result2 as TransactionResult.Success<Unit>).authResult.authToken == sampleReauthResult.authToken }
        assertTrue { intentUri?.scheme == "solana-wallet" }
    }

    @Test
    fun `validate for setting the rpcCluster property properly sets the blockchain property`() {
        mobileWalletAdapter.rpcCluster = RpcCluster.MainnetBeta

        assertTrue { mobileWalletAdapter.blockchain is Solana.Mainnet }

        mobileWalletAdapter.rpcCluster = RpcCluster.Devnet

        assertTrue { mobileWalletAdapter.blockchain is Solana.Devnet }

        mobileWalletAdapter.rpcCluster = RpcCluster.Testnet

        assertTrue { mobileWalletAdapter.blockchain is Solana.Testnet }
    }

    @Test
    fun `validate setting both cluster and blockchain values at runtime reset the auth token`() {
        mobileWalletAdapter.authToken = "SOMETOKENBYEBYE"

        mobileWalletAdapter.rpcCluster = RpcCluster.MainnetBeta

        assertTrue { mobileWalletAdapter.authToken == null }

        mobileWalletAdapter.authToken = "SOMETOKENBYEBYE"

        mobileWalletAdapter.blockchain = Solana.Testnet

        assertTrue { mobileWalletAdapter.authToken == null }
    }
}