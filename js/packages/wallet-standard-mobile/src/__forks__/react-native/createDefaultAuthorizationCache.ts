import { base58ToUint8Array } from '@solana-mobile/mobile-wallet-adapter-protocol/encoding';

import { Authorization, AuthorizationCache } from '../../wallet.js';
import { getAsyncStorage } from './getAsyncStorage.js';

const CACHE_KEY = 'SolanaMobileWalletAdapterWalletStandardDefaultAuthorizationCache';

/**
 * Creates the default {@link AuthorizationCache}, backed by
 * `@react-native-async-storage/async-storage`. That package is an optional peer dependency and is
 * resolved here, at creation: this throws a descriptive error when it is not installed or not
 * usable (such as when its native module has not been linked). Apps that cannot provide it should
 * supply their own `authorizationCache` instead.
 */
export function createDefaultAuthorizationCache(): AuthorizationCache {
    // Resolved outside the try/catch blocks below, which would otherwise swallow the diagnostic.
    const asyncStorage = getAsyncStorage('createDefaultAuthorizationCache');
    return {
        async clear() {
            try {
                await asyncStorage.removeItem(CACHE_KEY);
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async get() {
            try {
                const parsed = JSON.parse((await asyncStorage.getItem(CACHE_KEY)) as string) as Authorization;
                if (parsed && parsed.accounts) {
                    const parsedAccounts = parsed.accounts.map((account) => {
                        return {
                            ...account,
                            publicKey:
                                'publicKey' in account
                                    ? new Uint8Array(Object.values(account.publicKey)) // Rebuild publicKey for WalletAccount
                                    : base58ToUint8Array(account.address), // Fallback, get publicKey from address
                        };
                    });
                    return { ...parsed, accounts: parsedAccounts };
                } else return parsed || undefined;
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorizationResult: Authorization) {
            try {
                await asyncStorage.setItem(CACHE_KEY, JSON.stringify(authorizationResult));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}

/**
 * @deprecated Use {@link createDefaultAuthorizationCache} instead.
 */
export default createDefaultAuthorizationCache;
