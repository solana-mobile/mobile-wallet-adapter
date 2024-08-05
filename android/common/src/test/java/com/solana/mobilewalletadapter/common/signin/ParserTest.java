package com.solana.mobilewalletadapter.common.signin;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import com.solana.mobilewalletadapter.common.util.Base58;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ParserTest {

    @Test
    public void testParseMinimalSIWSMessage() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj";

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
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageWithMultilineStatement() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nthis is a statement,\nwith multiple lines!";

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "this is a statement,\nwith multiple lines!",
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
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageWithOptionalFields() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
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
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageNoOptionalFields() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nI accept the ServiceOrg Terms of Service: https://service.org/tos" +
                "\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-01-11T11:15:23.000Z";

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
                null
        );

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageTimestampWithoutMicroseconds() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nI accept the ServiceOrg Terms of Service: https://service.org/tos" +
                "\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-09-30T16:25:24Z";

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-09-30T16:25:24Z",
                null,
                null,
                null,
                null
        );

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageNoStatement() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-01-11T11:15:23.000Z";

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
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

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }

    @Test
    public void testParseSIWSMessageFull() {
        // given
        String message = "service.org wants you to sign in with your Solana account:" +
                "\n43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj" +
                "\n\nI accept the ServiceOrg Terms of Service: https://service.org/tos" +
                "\n\nURI: https://service.org/login" +
                "\nVersion: 1" +
                "\nChain ID: 1" +
                "\nNonce: 32832457" +
                "\nIssued At: 2021-01-11T11:15:23.000Z" +
                "\nExpiration Time: 2022-01-11T11:15:23.000Z" +
                "\nNot Before: 2022-01-11T11:15:23.000Z" +
                "\nRequest ID: abcd1234" +
                "\nResources:" +
                "\n- ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu" +
                "\n- https://example.com/my-web2-claim.json";

        SignInWithSolana.Payload expectedPayload = new SignInWithSolana.Payload(
                "service.org",
                Base58.decode("43h6BNKzvoV43qBLje5dxn7vhcChZjVEAn8PQLZvMiqj"),
                "I accept the ServiceOrg Terms of Service: https://service.org/tos",
                Uri.parse("https://service.org/login"),
                "1",
                "1",
                "32832457",
                "2021-01-11T11:15:23.000Z",
                "2022-01-11T11:15:23.000Z",
                "2022-01-11T11:15:23.000Z",
                "abcd1234",
                new Uri[] {
                        Uri.parse("ipfs://Qme7ss3ARVgxv6rXqVPiikMJ8u2NLgmgszg13pYrDKEoiu"),
                        Uri.parse("https://example.com/my-web2-claim.json")
                }
        );

        // when
        SignInWithSolana.Payload payload = SignInWithSolana.Parser.parseMessage(message);

        // then
        assertEquals(expectedPayload, payload);
    }
}