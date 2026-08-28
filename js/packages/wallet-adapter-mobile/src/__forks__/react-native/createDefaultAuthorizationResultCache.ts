import { AuthorizationResult } from '@solana-mobile/mobile-wallet-adapter-protocol';

import { AuthorizationResultCache } from '../../adapter.js';
import { getAsyncStorage } from './getAsyncStorage.js';

const CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';

/**
 * Creates the default {@link AuthorizationResultCache}, backed by
 * `@react-native-async-storage/async-storage`. That package is an optional peer dependency and is
 * resolved here, at creation: this throws a descriptive error when it is not installed or not
 * usable (such as when its native module has not been linked). Apps that cannot provide it should
 * supply their own `authorizationResultCache` instead.
 */
export function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    // Resolved outside the try/catch blocks below, which would otherwise swallow the diagnostic.
    const asyncStorage = getAsyncStorage('createDefaultAuthorizationResultCache');
    return {
        async clear() {
            try {
                await asyncStorage.removeItem(CACHE_KEY);
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async get() {
            try {
                return (
                    (JSON.parse((await asyncStorage.getItem(CACHE_KEY)) as string) as AuthorizationResult) || undefined
                );
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorizationResult: AuthorizationResult) {
            try {
                await asyncStorage.setItem(CACHE_KEY, JSON.stringify(authorizationResult));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}

/**
 * @deprecated Use {@link createDefaultAuthorizationResultCache} instead.
 */
export default createDefaultAuthorizationResultCache;
