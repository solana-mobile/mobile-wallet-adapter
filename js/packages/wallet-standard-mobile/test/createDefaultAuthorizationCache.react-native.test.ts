import { SOLANA_MAINNET_CHAIN } from '@solana/wallet-standard-chains';
import base58 from 'bs58';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Authorization } from '../src/wallet.js';

const AUTHORIZATION_CACHE_KEY = 'SolanaMobileWalletAdapterWalletStandardDefaultAuthorizationCache';
const DEFAULT_CAPABILITIES: Authorization['capabilities'] = {
    features: [],
    max_messages_per_request: 1,
    max_transactions_per_request: 1,
    supported_transaction_versions: [],
    supports_clone_authorization: false,
    supports_sign_and_send_transactions: false,
};

const { asyncStorage, resetAsyncStorage } = vi.hoisted(() => {
    let store = new Map<string, string>();

    return {
        asyncStorage: {
            getItem: vi.fn(async (key: string) => store.get(key) ?? null),
            removeItem: vi.fn(async (key: string) => {
                store.delete(key);
            }),
            setItem: vi.fn(async (key: string, value: string) => {
                store.set(key, value);
            }),
        },
        resetAsyncStorage: () => {
            store = new Map();
        },
    };
});

vi.mock('@react-native-async-storage/async-storage', () => ({
    default: asyncStorage,
}));

import createDefaultAuthorizationCache from '../src/__forks__/react-native/createDefaultAuthorizationCache.js';

beforeEach(() => {
    resetAsyncStorage();
});

afterEach(() => {
    asyncStorage.getItem.mockReset();
    asyncStorage.removeItem.mockReset();
    asyncStorage.setItem.mockReset();
    vi.restoreAllMocks();
});

describe('react-native createDefaultAuthorizationCache fork', () => {
    it('persists cached authorization, rehydrates serialized public keys, and clears the cache', async () => {
        const publicKey = Uint8Array.of(1, 2, 3);
        const authorization = createAuthorization(publicKey);
        const cache = createDefaultAuthorizationCache();

        await cache.set(authorization);

        const cachedAuthorization = await cache.get();

        expect(asyncStorage.setItem).toHaveBeenCalledWith(AUTHORIZATION_CACHE_KEY, JSON.stringify(authorization));
        expectAccountPublicKey(cachedAuthorization?.accounts[0], publicKey);
        expect(cachedAuthorization?.auth_token).toBe('token');
        expect(cachedAuthorization?.chain).toBe(SOLANA_MAINNET_CHAIN);

        await cache.clear();

        expect(asyncStorage.removeItem).toHaveBeenCalledWith(AUTHORIZATION_CACHE_KEY);
        await expect(cache.get()).resolves.toBeUndefined();
    });

    it('falls back to decoding public keys from account addresses', async () => {
        const publicKey = Uint8Array.of(7, 8, 9);

        asyncStorage.getItem.mockResolvedValue(
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

    it('returns cached objects that do not include accounts', async () => {
        const authorization = {
            auth_token: 'token',
            chain: SOLANA_MAINNET_CHAIN,
            wallet_uri_base: 'https://example.com',
        };

        asyncStorage.getItem.mockResolvedValue(JSON.stringify(authorization));

        await expect(createDefaultAuthorizationCache().get()).resolves.toEqual(authorization);
    });

    it('returns undefined for missing or invalid cached JSON', async () => {
        const cache = createDefaultAuthorizationCache();

        asyncStorage.getItem.mockResolvedValueOnce(null).mockResolvedValueOnce('{');

        await expect(cache.get()).resolves.toBeUndefined();
        await expect(cache.get()).resolves.toBeUndefined();
    });

    it('swallows AsyncStorage failures', async () => {
        const cache = createDefaultAuthorizationCache();

        asyncStorage.setItem.mockRejectedValueOnce(new Error('set failed'));
        asyncStorage.getItem.mockRejectedValueOnce(new Error('get failed'));
        asyncStorage.removeItem.mockRejectedValueOnce(new Error('clear failed'));

        await expect(cache.set(createAuthorization(Uint8Array.of(1, 2, 3)))).resolves.toBeUndefined();
        await expect(cache.get()).resolves.toBeUndefined();
        await expect(cache.clear()).resolves.toBeUndefined();
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
