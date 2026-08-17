/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.association;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NostrAssociationUriTest {

    private static final String VALID_PUBKEY = "aa".repeat(32);
    private static final String VALID_RELAY = "relay.example.com";
    private static final String VALID_ASSOCIATION = "dGVzdA";

    private static Uri buildNostrUri(String relay, String pubkey) {
        return buildNostrUri("local", relay, pubkey);
    }

    private static Uri buildNostrUri(String connectionType, String relay, String pubkey) {
        return Uri.parse("solana-wallet:/v1/associate/" + connectionType + "/nostr" +
                "?association=" + VALID_ASSOCIATION +
                "&relay=" + relay +
                "&pubkey=" + pubkey +
                "&v=v1");
    }

    @Test
    public void testParseValidNostrUri() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, VALID_PUBKEY);

        // when
        NostrAssociationUri result = new NostrAssociationUri(uri);

        // then
        assertEquals(VALID_RELAY, result.relayDomain);
        assertEquals(VALID_PUBKEY, result.dappNostrPubkey);
        assertNotNull(result.associationPublicKey);
    }

    @Test
    public void testParseViaAssociationUriFactory() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, VALID_PUBKEY);

        // when
        AssociationUri result = AssociationUri.parse(uri);

        // then
        assertNotNull(result);
        assertTrue(result instanceof NostrAssociationUri);
        NostrAssociationUri nostrUri = (NostrAssociationUri) result;
        assertEquals(VALID_RELAY, nostrUri.relayDomain);
        assertEquals(VALID_PUBKEY, nostrUri.dappNostrPubkey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWrongPathThrows() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local" +
                "?association=" + VALID_ASSOCIATION +
                "&relay=" + Uri.encode(VALID_RELAY) +
                "&pubkey=" + VALID_PUBKEY);

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMissingRelayThrows() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local/nostr" +
                "?association=" + VALID_ASSOCIATION +
                "&pubkey=" + VALID_PUBKEY);

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseEmptyRelayThrows() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local/nostr" +
                "?association=" + VALID_ASSOCIATION +
                "&relay=" +
                "&pubkey=" + VALID_PUBKEY);

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMissingPubkeyThrows() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local/nostr" +
                "?association=" + VALID_ASSOCIATION +
                "&relay=" + Uri.encode(VALID_RELAY));

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseShortPubkeyThrows() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, "aa".repeat(31));

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseLongPubkeyThrows() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, "aa".repeat(33));

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseUppercasePubkeyThrows() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, "AA".repeat(32));

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNonHexPubkeyThrows() {
        // given
        Uri uri = buildNostrUri(VALID_RELAY, "zz".repeat(32));

        // when
        new NostrAssociationUri(uri);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMissingAssociationTokenThrows() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local/nostr" +
                "?relay=" + Uri.encode(VALID_RELAY) +
                "&pubkey=" + VALID_PUBKEY);

        // when
        new NostrAssociationUri(uri);
    }

    @Test
    public void testLocalNostrUriHasLocalConnectionType() {
        // given
        Uri uri = buildNostrUri("local", VALID_RELAY, VALID_PUBKEY);

        // when
        NostrAssociationUri result = new NostrAssociationUri(uri);

        // then
        assertEquals(AssociationUri.ConnectionType.LOCAL, result.connectionType);
    }

    @Test
    public void testRemoteNostrUriHasRemoteConnectionType() {
        // given
        Uri uri = buildNostrUri("remote", VALID_RELAY, VALID_PUBKEY);

        // when
        NostrAssociationUri result = new NostrAssociationUri(uri);

        // then
        assertEquals(AssociationUri.ConnectionType.REMOTE, result.connectionType);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMissingLocalRemotePathThrows() {
        // given - path is just /v1/associate/nostr without local or remote
        Uri uri = Uri.parse("solana-wallet:/v1/associate/nostr" +
                "?association=" + VALID_ASSOCIATION +
                "&relay=" + VALID_RELAY +
                "&pubkey=" + VALID_PUBKEY +
                "&v=v1");

        // when
        new NostrAssociationUri(uri);
    }

    @Test
    public void testParseNonNostrUriReturnsCorrectType() {
        // given
        Uri uri = Uri.parse("solana-wallet:/v1/associate/local" +
                "?association=" + VALID_ASSOCIATION +
                "&port=49152");

        // when
        AssociationUri result = AssociationUri.parse(uri);

        // then
        assertNotNull(result);
        assertTrue(result instanceof LocalAssociationUri);
    }
}
