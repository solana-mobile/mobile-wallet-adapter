import { AuthorizationResult } from '@solana-mobile/mobile-wallet-adapter-protocol';
import { AuthorizationResultCache } from './wallet';
import { PublicKey } from '@solana/web3.js';

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
                const parsed = JSON.parse(storage.getItem(CACHE_KEY) as string) as AuthorizationResult;
                if (parsed && parsed.accounts) {
                    const parsedAccounts = parsed.accounts.map((account) => {
                        return {
                            ...account,
                            publicKey: 'publicKey' in account
                                ? new Uint8Array(Object.values(account.publicKey)) // Rebuild publicKey for WalletAccount
                                : new PublicKey(account.address).toBytes(), // Fallback, get publicKey from address
                        }
                    })
                    return { ...parsed, accounts: parsedAccounts }
                } else return parsed || undefined;
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
