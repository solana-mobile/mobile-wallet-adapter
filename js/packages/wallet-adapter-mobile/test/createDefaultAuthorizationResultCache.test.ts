// @vitest-environment jsdom

import { type Authorization } from '@solana-mobile/wallet-standard-mobile';
import base58 from 'bs58';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import createDefaultAuthorizationResultCache from '../src/createDefaultAuthorizationResultCache.js';

const AUTHORIZATION_CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';
const SOLANA_MAINNET_CHAIN = 'solana:mainnet';
const DEFAULT_CAPABILITIES: Authorization['capabilities'] = {
    features: [],
    max_messages_per_request: 1,
    max_transactions_per_request: 1,
    supported_transaction_versions: [],
    supports_clone_authorization: false,
    supports_sign_and_send_transactions: false,
};

beforeEach(() => {
    installLocalStorage();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('createDefaultAuthorizationResultCache', () => {
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

        const authorization = await createDefaultAuthorizationResultCache().get();

        expectAccountPublicKey(authorization?.accounts[0], publicKey);
    });

    it('persists cached authorization, rehydrates serialized public keys, and clears the cache', async () => {
        const publicKey = Uint8Array.of(1, 2, 3);

        const authorization = createAuthorization(publicKey);
        const cache = createDefaultAuthorizationResultCache();

        await cache.set(authorization);

        const cachedAuthorization = await cache.get();

        expect(window.localStorage.getItem(AUTHORIZATION_CACHE_KEY)).not.toBeNull();
        expectAccountPublicKey(cachedAuthorization?.accounts[0], publicKey);
        expect(cachedAuthorization?.auth_token).toBe('token');
        expect(cachedAuthorization && 'chain' in cachedAuthorization ? cachedAuthorization.chain : undefined).toBe(
            SOLANA_MAINNET_CHAIN,
        );

        await cache.clear();

        expect(window.localStorage.getItem(AUTHORIZATION_CACHE_KEY)).toBeNull();
    });

    it('returns undefined for invalid cached JSON', async () => {
        window.localStorage.setItem(AUTHORIZATION_CACHE_KEY, '{');

        await expect(createDefaultAuthorizationResultCache().get()).resolves.toBeUndefined();
    });

    it('returns undefined when localStorage is unavailable', async () => {
        Object.defineProperty(window, 'localStorage', {
            configurable: true,
            get() {
                throw new Error('localStorage unavailable');
            },
        });

        await expect(createDefaultAuthorizationResultCache().get()).resolves.toBeUndefined();
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

function installLocalStorage() {
    let store = new Map<string, string>();

    Object.defineProperty(window, 'localStorage', {
        configurable: true,
        value: {
            clear() {
                store = new Map();
            },
            getItem(key: string) {
                return store.get(key) ?? null;
            },
            key(index: number) {
                return [...store.keys()][index] ?? null;
            },
            get length() {
                return store.size;
            },
            removeItem(key: string) {
                store.delete(key);
            },
            setItem(key: string, value: string) {
                store.set(key, value);
            },
        } satisfies Storage,
    });
}
