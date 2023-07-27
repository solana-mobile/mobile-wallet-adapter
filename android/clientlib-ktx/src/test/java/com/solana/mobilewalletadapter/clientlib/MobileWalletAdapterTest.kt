package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
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

        mockClient = mock {
            on { authorize(any(), any(), any(), any()) } doAnswer {
                mock {
                    on { get() } doAnswer {
                        mock()
                    }
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

        //Validate result is successful
        //Validate payload is unit
        //validate auth result equals mock

        //Other tests can validate that this acutally happens
        verify(mockClient, times(1)).authorize(Uri.EMPTY, Uri.EMPTY, "Test App", "devnet")
    }
}