package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MobileWalletAdapterTest {

    lateinit var testDispatcher: TestDispatcher

    lateinit var mockProvider: AssociationScenarioProvider
    lateinit var mockClient: MobileWalletAdapterClient
    lateinit var mockAuthResult: AuthorizationResult

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

        mockAuthResult = mock()
        mockClient = mock {
            on { authorize(any(), any(), any(), any()) } doAnswer {
                mock {
                    on { get() } doReturn mockAuthResult
                }
            }
            on { reauthorize(any(), any(), any(), any()) } doAnswer {
                mock {
                    on { get() } doReturn mockAuthResult
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
        assertTrue { result.successPayload == null }
    }

    @Test
    fun `validate calling connect is successful when credentials are provided`() = runTest(testDispatcher) {
        val creds = ConnectionCredentials(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
            rpcCluster = RpcCluster.Devnet
        )

        mobileWalletAdapter.provideCredentials(creds)

        val result = mobileWalletAdapter.connect(sender)

        assertTrue { result is TransactionResult.Success<Unit> }
        assertTrue { result.successPayload is Unit }
        assertTrue { (result as TransactionResult.Success<Unit>).authResult == mockAuthResult }
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
    fun `validate providing credentials before transact results in an authorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"

        val creds = ConnectionCredentials(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
            rpcCluster = RpcCluster.Devnet
        )

        mobileWalletAdapter.provideCredentials(creds)

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).authorize(any(), any(), any(), any())
        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == mockAuthResult }
    }

    @Test
    fun `validate providing credentials with authToken before transact results in an reauthorize call`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"

        val creds = ConnectionCredentials(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
            rpcCluster = RpcCluster.Devnet,
            authToken = "1234567890"
        )

        mobileWalletAdapter.provideCredentials(creds)

        val result = mobileWalletAdapter.transact(sender) {
            refString
        }

        verify(mockClient, times(1)).reauthorize(any(), any(), any(), any())

        assertTrue { result is TransactionResult.Success<String> }
        assertTrue { result.successPayload == refString }
        assertTrue { (result as TransactionResult.Success<String>).authResult == mockAuthResult }
    }

    @Test
    fun `validate providing credentials with an authToken and calling transact multiple times results in reauth each time`() = runTest(testDispatcher) {
        val refString = "Returning a string for validation"

        val creds = ConnectionCredentials(
            identityUri = Uri.EMPTY,
            iconUri = Uri.EMPTY,
            identityName = "Test App",
            rpcCluster = RpcCluster.Devnet,
            authToken = "1234567890"
        )

        mobileWalletAdapter.provideCredentials(creds)

        mobileWalletAdapter.transact(sender) { }
        val result = mobileWalletAdapter.transact(sender) { }

        verify(mockClient, times(2)).reauthorize(any(), any(), any(), any())

        assertTrue { result is TransactionResult.Success<Unit> }
        assertTrue { (result as TransactionResult.Success<Unit>).authResult == mockAuthResult }
    }
}