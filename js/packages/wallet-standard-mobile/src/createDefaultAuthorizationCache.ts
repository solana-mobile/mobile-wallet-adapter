import { Authorization, AuthorizationCache } from './wallet';
import base58 from 'bs58';

const CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';

export default function createDefaultAuthorizationCache(): AuthorizationCache {
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
                const parsed = JSON.parse(storage.getItem(CACHE_KEY) as string) as Authorization;
                if (parsed && parsed.accounts) {
                    const parsedAccounts = parsed.accounts.map((account) => {
                        return {
                            ...account,
                            publicKey: 'publicKey' in account
                                ? new Uint8Array(Object.values(account.publicKey)) // Rebuild publicKey for WalletAccount
                                : base58.decode(account.address), // Fallback, get publicKey from address
                        }
                    })
                    return { ...parsed, accounts: parsedAccounts };
                } else return parsed || undefined;
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorization: Authorization) {
            if (!storage) {
                return;
            }
            try {
                storage.setItem(CACHE_KEY, JSON.stringify(authorization));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}
