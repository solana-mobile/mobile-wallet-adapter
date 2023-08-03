package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeoutException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MobileWalletAdapterTest {

    lateinit var testDispatcher: TestDispatcher

    lateinit var mockProvider: AssociationScenarioProvider
    lateinit var mockClient: MobileWalletAdapterClient
    lateinit var sampleAuthResult: AuthorizationResult
    lateinit var sampleReauthResult: AuthorizationResult

    lateinit var sender: ActivityResultSender
    lateinit var mobileWalletAdapter: MobileWalletAdapter

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

        mockClient = mock {
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

        mockProvider = mock {
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

        mobileWalletAdapter = MobileWalletAdapter(
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `validate calling connect results in a failure if credentials are not provided first`() = runTest(testDispatcher) {
        val result = mobileWalletAdapter.connect(sender)

        assertIs<TransactionResult.Failure<Unit>>(result)
        assertTrue { result.e.message == "App identity credentials must be provided via the constructor to use the connect method." }
        assertTrue { result.successPayload == null }
    }

    @Test
    fun `validate calling connect is successful when credentials are provided`() = runTest(testDispatcher) {
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

        val result = mobileWalletAdapter.connect(sender)

        assertTrue { result is TransactionResult.Success<Unit> }
        assertTrue { result.successPayload is Unit }
        assertTrue { (result as TransactionResult.Success<Unit>).authResult == sampleAuthResult }
    }

    @Test
    fun `validate accessing authresult property without providing creds during transact throws an exception`() = runTest(testDispatcher) {
        val result = mobileWalletAdapter.transact(sender) {
            authorize(Uri.EMPTY, Uri.EMPTY, "name")

            "No auth result returned"
        }

        assertIs<TransactionResult.Success<String>>(result)
        assertTrue { result.successPayload is String }
        assertTrue { result.successPayload == "No auth result returned" }

        assertFailsWith(IllegalStateException::class) {
            val willNotUseMe = result.authResult
        }
    }

    @Test
    fun `validate calling transact without provided credentials does not attempt any authorization`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(0)).authorize(any(), any(), any(), any())
        verify(mockClient, times(0)).reauthorize(any(), any(), any(), any())

        assertTrue { result.successPayload == refString }
    }

    @Test
    fun `validate setting auth token or rpc cluster does not activate automatic auth or reauth`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"

        mobileWalletAdapter.transact(sender) { }

        verify(mockClient, times(0)).authorize(any(), any(), any(), any())

        mobileWalletAdapter.authToken = "SOMETOKENTHATWONTAFFECTANTYHING"
        mobileWalletAdapter.rpcCluster = RpcCluster.Devnet

        mobileWalletAdapter.connect(sender)
        val result2 = mobileWalletAdapter.transact(sender) {
            refString + "hi"
        }

        verify(mockClient, times(0)).authorize(any(), any(), any(), any())
        verify(mockClient, times(0)).reauthorize(any(), any(), any(), any())

        assertTrue { result2.successPayload == refString + "hi" }
    }

    @Test
    fun `validate providing credentials in the constructor results in an authorize call`() = runTest(testDispatcher) {
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

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any())
        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == sampleAuthResult }
    }

    @Test
    fun `validate providing credentials and an authToken before transact results in an reauthorize call`() = runTest(testDispatcher) {
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
    fun `validate providing credentials and an authToken then calling transact multiple times results in reauth each time`() = runTest(testDispatcher) {
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
    fun `validate the proper auth token is returned on when authenticating and re-authenticating`() = runTest(testDispatcher) {
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
    fun `validate changing the rpc cluster at runtime invalidates the auth token and calls authorize`() = runTest(testDispatcher) {
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
        mobileWalletAdapter.authToken = "SOMEAUTHTOKEN"

        assertTrue { mobileWalletAdapter.rpcCluster is RpcCluster.Devnet }

        mobileWalletAdapter.rpcCluster = RpcCluster.Testnet

        assertTrue { mobileWalletAdapter.authToken == null }
        assertTrue { mobileWalletAdapter.rpcCluster is RpcCluster.Testnet }

        mobileWalletAdapter.transact(sender) { }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any())
    }
}