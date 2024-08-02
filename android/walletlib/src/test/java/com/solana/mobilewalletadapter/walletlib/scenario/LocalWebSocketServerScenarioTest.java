package com.solana.mobilewalletadapter.walletlib.scenario;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class LocalWebSocketServerScenarioTest {

    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    @Test
    public void testLowPowerNoConnectionCallbackIsNotCalled() throws InterruptedException {
        // given
        int port = 1;
        byte[] publicKey = new byte[0];
        long noConnectionTimeout = 100L;

        Context context = ApplicationProvider.getApplicationContext();

        AuthIssuerConfig authConfig = new AuthIssuerConfig("Test");

        MobileWalletAdapterConfig config = new MobileWalletAdapterConfig(
                1,
                1,
                new Object[] { "legacy" },
                noConnectionTimeout,
                new String[] {}
        );

        CountDownLatch latch = new CountDownLatch(1);

        TestCallbacks lowPowerNoConnectionCallback = new TestCallbacks() {
            @Override
            public void onLowPowerAndNoConnection() {
                latch.countDown();
            }
        };

        PowerConfigProvider powerConfig = () -> false;
        WalletIconProvider iconProvider = () -> null;

        List<SessionProperties.ProtocolVersion> supportedVersions =
                List.of(SessionProperties.ProtocolVersion.LEGACY);

        // when
        new LocalWebSocketServerScenario(context, config, authConfig, lowPowerNoConnectionCallback,
                publicKey, port, powerConfig, supportedVersions, iconProvider).start();
        boolean lowPowerNoConnectionCallbackFired = latch.await(200, TimeUnit.MILLISECONDS);

        // then
        assertFalse(lowPowerNoConnectionCallbackFired);
    }

    @Test
    public void testLowPowerNoConnectionCallbackIsCalled() throws InterruptedException {
        // given
        int port = 1;
        byte[] publicKey = new byte[0];
        long noConnectionTimeout = 100L;

        Context context = ApplicationProvider.getApplicationContext();

        AuthIssuerConfig authConfig = new AuthIssuerConfig("Test");

        MobileWalletAdapterConfig config = new MobileWalletAdapterConfig(
                1,
                1,
                new Object[] { "legacy" },
                noConnectionTimeout,
                new String[] {}
        );

        CountDownLatch latch = new CountDownLatch(1);

        TestCallbacks lowPowerNoConnectionCallback = new TestCallbacks() {
            @Override
            public void onLowPowerAndNoConnection() {
                latch.countDown();
            }
        };

        PowerConfigProvider powerConfig = () -> true;
        WalletIconProvider iconProvider = () -> null;

        List<SessionProperties.ProtocolVersion> supportedVersions =
                List.of(SessionProperties.ProtocolVersion.LEGACY);

        // when
        new LocalWebSocketServerScenario(context, config, authConfig, lowPowerNoConnectionCallback,
                publicKey, port, powerConfig, supportedVersions, iconProvider).start();
        boolean lowPowerNoConnectionCallbackFired = latch.await(200, TimeUnit.MILLISECONDS);

        // then
        assertTrue(lowPowerNoConnectionCallbackFired);
    }

    protected class TestCallbacks implements LocalScenario.Callbacks {
        @Override
        public void onScenarioReady() {}
        @Override
        public void onScenarioServingClients() {}
        @Override
        public void onScenarioServingComplete() {}
        @Override
        public void onScenarioComplete() {}
        @Override
        public void onScenarioError() {}
        @Override
        public void onScenarioTeardownComplete() {}
        @Override
        public void onAuthorizeRequest(AuthorizeRequest request) {}
        @Override
        public void onReauthorizeRequest(ReauthorizeRequest request) {}
        @Override
        public void onSignTransactionsRequest(SignTransactionsRequest request) {}
        @Override
        public void onSignMessagesRequest(SignMessagesRequest request) {}
        @Override
        public void onSignAndSendTransactionsRequest(SignAndSendTransactionsRequest request) {}
        @Override
        public void onDeauthorizedEvent(DeauthorizedEvent event) {}
//        @Override
        public void onLowPowerAndNoConnection() {}
    }
}