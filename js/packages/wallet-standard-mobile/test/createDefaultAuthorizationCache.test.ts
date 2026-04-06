// @vitest-environment jsdom

import { SOLANA_MAINNET_CHAIN } from '@solana/wallet-standard-chains';
import base58 from 'bs58';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import createDefaultAuthorizationCache from '../src/createDefaultAuthorizationCache.js';
import type { Authorization } from '../src/wallet.js';

const AUTHORIZATION_CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';
const DEFAULT_CAPABILITIES: Authorization['capabilities'] = {
    features: [],
    max_messages_per_request: 1,
    max_transactions_per_request: 1,
    supported_transaction_versions: [],
    supports_clone_authorization: false,
    supports_sign_and_send_transactions: false,
};

beforeEach(() => {
    window.localStorage.clear();
});

afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
});

describe('createDefaultAuthorizationCache', () => {
    it('falls back to decoding public keys from account addresses', async () => {
        const publicKey = Uint8Array.of(7, 8, 9);

        window.localStorage.setItem(
            AUTHORIZATION_CACHE_KEY,
            JSON.stringify({
                accounts: [
                    {
                        address: base58.encode(publicKey),
                        chains: [SOLANA_MAINNET_CHAIN],
                        features: [],
                        icon: 'data:image/svg+xml;base64,icon',
                        label: 'Primary',
                    },
                ],
                auth_token: 'token',
                capabilities: DEFAULT_CAPABILITIES,
                chain: SOLANA_MAINNET_CHAIN,
                wallet_uri_base: 'https://example.com',
            }),
        );

        const authorization = await createDefaultAuthorizationCache().get();

        expectAccountPublicKey(authorization?.accounts[0], publicKey);
    });

    it('persists cached authorization, rehydrates serialized public keys, and clears the cache', async () => {
        const publicKey = Uint8Array.of(1, 2, 3);

        const authorization = createAuthorization(publicKey);
        const cache = createDefaultAuthorizationCache();

        await cache.set(authorization);

        const cachedAuthorization = await cache.get();

        expect(window.localStorage.getItem(AUTHORIZATION_CACHE_KEY)).not.toBeNull();
        expectAccountPublicKey(cachedAuthorization?.accounts[0], publicKey);
        expect(cachedAuthorization?.auth_token).toBe('token');
        expect(cachedAuthorization?.chain).toBe(SOLANA_MAINNET_CHAIN);

        await cache.clear();

        expect(window.localStorage.getItem(AUTHORIZATION_CACHE_KEY)).toBeNull();
    });

    it('returns undefined for invalid cached JSON', async () => {
        window.localStorage.setItem(AUTHORIZATION_CACHE_KEY, '{');

        await expect(createDefaultAuthorizationCache().get()).resolves.toBeUndefined();
    });

    it('returns undefined when localStorage is unavailable', async () => {
        vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => {
            throw new Error('localStorage unavailable');
        });

        await expect(createDefaultAuthorizationCache().get()).resolves.toBeUndefined();
    });
});

function createAuthorization(publicKey: Uint8Array): Authorization {
    return {
        accounts: [
            {
                address: base58.encode(publicKey),
                chains: [SOLANA_MAINNET_CHAIN],
                features: [],
                icon: 'data:image/svg+xml;base64,icon',
                label: 'Primary',
                publicKey,
            },
        ],
        auth_token: 'token',
        capabilities: DEFAULT_CAPABILITIES,
        chain: SOLANA_MAINNET_CHAIN,
        wallet_uri_base: 'https://example.com',
    };
}

function expectAccountPublicKey(account: Authorization['accounts'][number] | undefined, expectedPublicKey: Uint8Array) {
    expect(account).toBeDefined();
    expect(account && 'publicKey' in account).toBe(true);
    if (!account || !('publicKey' in account)) {
        return;
    }
    expect(account.publicKey).toEqual(expectedPublicKey);
}
