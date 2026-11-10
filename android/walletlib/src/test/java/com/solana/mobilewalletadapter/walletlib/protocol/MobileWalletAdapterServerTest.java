package com.solana.mobilewalletadapter.walletlib.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Looper;

import com.solana.mobilewalletadapter.common.ProtocolContract;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class MobileWalletAdapterServerTest {

    @Test
    public void getCapabilitiesReportsSignOffchainMessagesWhenNotConfigured() throws Exception {
        // given
        final String[] configuredFeatures = new String[] { ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS };

        // when
        final JSONArray features = getCapabilitiesFeatures(configuredFeatures);

        // then
        assertEquals(2, features.length());
        assertEquals(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS, features.getString(0));
        assertEquals(ProtocolContract.FEATURE_ID_SIGN_OFFCHAIN_MESSAGES, features.getString(1));
    }

    @Test
    public void getCapabilitiesDoesNotDuplicateSignOffchainMessagesWhenConfigured() throws Exception {
        // given
        final String[] configuredFeatures = new String[] { ProtocolContract.FEATURE_ID_SIGN_OFFCHAIN_MESSAGES };

        // when
        final JSONArray features = getCapabilitiesFeatures(configuredFeatures);

        // then
        assertEquals(1, features.length());
        assertEquals(ProtocolContract.FEATURE_ID_SIGN_OFFCHAIN_MESSAGES, features.getString(0));
    }

    private static JSONArray getCapabilitiesFeatures(String[] configuredFeatures) throws Exception {
        final MobileWalletAdapterConfig config = new MobileWalletAdapterConfig(
                1, 1, new Object[] { MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION }, 0L,
                configuredFeatures);
        // get_capabilities never reaches the method handlers
        final MobileWalletAdapterServer server =
                new MobileWalletAdapterServer(config, Looper.getMainLooper(), null);

        final byte[][] response = new byte[1][];
        server.receiverConnected(message -> response[0] = message);
        server.receiverMessageReceived(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"get_capabilities\",\"params\":{}}"
                        .getBytes(StandardCharsets.UTF_8));

        assertNotNull("no get_capabilities response sent", response[0]);
        final JSONObject result = new JSONObject(new String(response[0], StandardCharsets.UTF_8))
                .getJSONObject("result");
        return result.getJSONArray(ProtocolContract.RESULT_SUPPORTED_FEATURES);
    }
}
