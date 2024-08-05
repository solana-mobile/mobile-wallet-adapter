package com.solana.mobilewalletadapter.common.signin;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import com.solana.mobilewalletadapter.common.util.Base58;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PayloadTest {

    @Test
    public void testPayloadPrepareMessage() {
        // given
        SignInWithSolana.Payload payload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-01-11T11:15:23.000Z",
                null,
                null,
                null,
                new Uri[] {
                        Uri.parse("ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu"),
                        Uri.parse("https://example.com/my-web2-claim.json")
                }
        );

        String expectedMessage = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nI accept the ServiceOrg Terms of Service: https://service.org/tos" +
                "\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-01-11T11:15:23.000Z" +
                "\nResources:" +
                "\n- ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu" +
                "\n- https://example.com/my-web2-claim.json";

        // when
        String message = payload.prepareMessage();

        // then
        assertEquals(expectedMessage, message);
    }

    @Test
    public void testPayloadPrepareMessageNoStatement() {
        // given
        SignInWithSolana.Payload payload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                null,
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-01-11T11:15:23.000Z",
                null,
                null,
                null,
                null
        );

        String expectedMessage = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-01-11T11:15:23.000Z";

        // when
        String message = payload.prepareMessage();

        // then
        assertEquals(expectedMessage, message);
    }

    @Test
    public void testPayloadPrepareMinimalMessage() {
        // given
        SignInWithSolana.Payload payload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        String expectedMessage = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nI accept the ServiceOrg Terms of Service: https://service.org/tos";

        // when
        String message = payload.prepareMessage();

        // then
        assertEquals(expectedMessage, message);
    }

    @Test
    public void testPayloadFromJson() throws JSONException {
        // given
        JSONObject payloadJson = new JSONObject(
                "{" +
                    "\"domain\": \"service.org\"," +
                    "\"address\": \"43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj\"," +
                    "\"statement\": \"I accept the ServiceOrg Terms of Service: https://service.org/tos\"," +
                    "\"uri\": \"https://service.org/login\"," +
                    "\"version\": \"1\"," +
                    "\"chainId\": 1," +
                    "\"nonce\": \"32832457\"," +
                    "\"issuedAt\": \"2021-01-11T11:15:23.000Z\"," +
                    "\"resources\": [\"ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu\", \"https://example.com/my-web2-claim.json\"]" +
                "}"
        );

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-01-11T11:15:23.000Z",
                null,
                null,
                null,
                new Uri[] {
                        Uri.parse("ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu"),
                        Uri.parse("https://example.com/my-web2-claim.json")
                }
        );

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Payload.fromJson(payloadJson);

        // then
        assertEquals(expectedPayload, payload);
    }
    @Test
    public void testPayloadFromMinimalJson() throws JSONException {
        // given
        JSONObject payloadJson = new JSONObject(
                "{" +
                    "\"domain\": \"service.org\"," +
                    "\"address\": \"43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj\"" +
                "}"
        );

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Payload.fromJson(payloadJson);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testPayloadToJson() throws JSONException {
        // given
        SignInWithSolana.Payload payload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-01-11T11:15:23.000Z",
                null,
                null,
                null,
                new Uri[] {
                        Uri.parse("ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu"),
                        Uri.parse("https://example.com/my-web2-claim.json")
                }
        );

        JSONObject expectedJson = new JSONObject(
                "{" +
                    "\"domain\": \"service.org\"," +
                    "\"address\": \"43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj\"," +
                    "\"statement\": \"I accept the ServiceOrg Terms of Service: https://service.org/tos\"," +
                    "\"uri\": \"https://service.org/login\"," +
                    "\"version\": \"1\"," +
                    "\"chainId\": \"1\"," +
                    "\"nonce\": \"32832457\"," +
                    "\"issuedAt\": \"2021-01-11T11:15:23.000Z\"," +
                    "\"resources\": [\"ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu\", \"https://example.com/my-web2-claim.json\"]" +
                "}"
        );

        // when
        JSONObject payloadJson = payload.toJson();

        // then
        assertEquals(expectedJson.toString(), payloadJson.toString());
    }
}