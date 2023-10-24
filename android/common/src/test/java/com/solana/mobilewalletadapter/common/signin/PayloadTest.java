package com.solana.mobilewalletadapter.common.signin;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

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
                "43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj",
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                1,
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
}