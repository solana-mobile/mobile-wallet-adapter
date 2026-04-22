import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const AUTHORIZATION_CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';

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

import createDefaultAuthorizationResultCache from '../src/__forks__/react-native/createDefaultAuthorizationResultCache.js';

beforeEach(() => {
    resetAsyncStorage();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('react-native createDefaultAuthorizationResultCache fork', () => {
    it('persists, loads, and clears cached authorization results', async () => {
        const authorizationResult = {
            accounts: [
                {
                    address: 'address-1',
                    label: 'Primary',
                },
            ],
            auth_token: 'token',
            wallet_uri_base: 'https://example.test',
        };
        const cache = createDefaultAuthorizationResultCache();

        await expect(cache.set(authorizationResult as never)).resolves.toBeUndefined();
        await expect(cache.get()).resolves.toEqual(authorizationResult);
        await expect(cache.clear()).resolves.toBeUndefined();
        await expect(cache.get()).resolves.toBeUndefined();

        expect(asyncStorage.setItem).toHaveBeenCalledWith(AUTHORIZATION_CACHE_KEY, JSON.stringify(authorizationResult));
        expect(asyncStorage.removeItem).toHaveBeenCalledWith(AUTHORIZATION_CACHE_KEY);
    });

    it('returns undefined for invalid cached JSON', async () => {
        asyncStorage.getItem.mockResolvedValue('{');

        await expect(createDefaultAuthorizationResultCache().get()).resolves.toBeUndefined();
    });

    it('swallows AsyncStorage failures', async () => {
        const cache = createDefaultAuthorizationResultCache();

        asyncStorage.setItem.mockRejectedValueOnce(new Error('set failed'));
        asyncStorage.getItem.mockRejectedValueOnce(new Error('get failed'));
        asyncStorage.removeItem.mockRejectedValueOnce(new Error('clear failed'));

        await expect(cache.set({ auth_token: 'token' } as never)).resolves.toBeUndefined();
        await expect(cache.get()).resolves.toBeUndefined();
        await expect(cache.clear()).resolves.toBeUndefined();
    });
});
