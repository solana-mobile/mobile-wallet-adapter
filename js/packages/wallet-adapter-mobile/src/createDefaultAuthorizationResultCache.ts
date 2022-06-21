import { AuthorizationResult } from '@solana-mobile/mobile-wallet-adapter-protocol';
import { AuthorizationResultCache } from './adapter';

const CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';

export default function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    let storage: Storage | null | undefined;
    try {
        storage = window.localStorage;
        // eslint-disable-next-line no-empty
    } catch {}
    return {
        async clear() {
            if (!storage) {
                return;
            }
            try {
                storage.removeItem(CACHE_KEY);
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async get() {
            if (!storage) {
                return;
            }
            try {
                return (JSON.parse(storage.getItem(CACHE_KEY) as string) as AuthorizationResult) || undefined;
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorizationResult: AuthorizationResult) {
            if (!storage) {
                return;
            }
            try {
                storage.setItem(CACHE_KEY, JSON.stringify(authorizationResult));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}
