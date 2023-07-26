package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
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
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class MobileWalletAdapterTest {

    lateinit var testDispatcher: TestDispatcher
    lateinit var mockProvider: AssociationScenarioProvider

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

        val mockSession = mock<MobileWalletAdapterSession> {
            on { encodedAssociationPublicKey } doAnswer { byteArrayOf() }
        }

        val mockClient = mock<MobileWalletAdapterClient>()
        val future = NotifyingCompletableFuture<MobileWalletAdapterClient>()

        val mockScenario = mock<LocalAssociationScenario> {
            on { start() } doAnswer {
                future.complete(mockClient)
                future
            }
            on { session } doReturn mockSession
            on { close() } doAnswer {
                val future = NotifyingCompletableFuture<Void>()
                future.complete(null)
                future
            }
        }

        mockProvider = mock {
            on { provideAssociationScenario(any()) } doReturn mockScenario
        }

        mobileWalletAdapter = MobileWalletAdapter(
            scenarioProvider = mockProvider,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun runTest() = runTest(testDispatcher) {
        val result = mobileWalletAdapter.connect(sender)

        assertIs<String>(result.successPayload)
    }

}